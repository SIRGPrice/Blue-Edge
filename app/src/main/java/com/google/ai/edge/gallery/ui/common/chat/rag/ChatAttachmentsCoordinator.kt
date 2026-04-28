/*
 * Copyright 2026 BlueEdge contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.google.ai.edge.gallery.ui.common.chat.rag

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.compositionLocalOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AGChatAttachments"

/**
 * Maximum total characters of document context that we will inline into a single prompt.
 * Beyond this threshold, document text is truncated (with a visible marker) so the model
 * input does not blow past its context window.
 */
private const val DEFAULT_MAX_TOTAL_CONTEXT_CHARS = 200_000

/** Top-K chunks devueltos por la consulta BM25. Suficiente para que la respuesta sea
 *  precisa sin volar el contexto del modelo. */
private const val DEFAULT_RETRIEVAL_TOP_K = 8

/** Fallback máximo por documento cuando BM25 no encuentra hits útiles. Antes se
 * volvía al texto completo (hasta 200k chars), lo que hacía irregular el prefill:
 * unas veces tardaba minutos, otras se quedaba al 99 %, y tras cancelarlo el runtime
 * native quedaba en estado dudoso. Mantener el fallback pequeño hace el coste estable. */
private const val FALLBACK_CHARS_PER_DOCUMENT = 6_000

/** Tamaño aproximado de cada chunk en chars (~400 tokens SentencePiece). */
private const val CHUNK_TARGET_CHARS = 1500

/** Solapamiento entre chunks consecutivos en chars (~50 tokens). */
private const val CHUNK_OVERLAP_CHARS = 200

/** Prefijo de doc_id para chunks de documentos TEMPORARY. Permite borrarlos en bloque
 *  tras consumirlos sin afectar a los permanentes. */
private const val TEMP_DOC_ID_PREFIX = "tmp:"

/** Duración mínima visible de la fase INDEXING para que el usuario llegue a ver la
 * ruedita de procesado incluso cuando el documento ya estaba cacheado/chunkado. */
private const val MIN_DOCUMENT_PROCESSING_MS = 500L

/** Status of the background extraction pipeline for a single attached document. */
enum class DocumentIndexingStatus {
  INDEXING,
  READY,
  FAILED,
}

/**
 * Orchestrates document attachments end-to-end with a *no-RAG* pipeline:
 *
 * 1. As soon as the user attaches a document, [preIngest] kicks off, in background:
 *    - text extraction (PDFBox / DOCX / etc.) on [Dispatchers.IO]
 *    - caching of the extracted text by stable id
 *    - for PERSISTENT docs: persisting the full text into [permanentStore].
 *
 * 2. [stage] tracks the latest "what is staged for this conversation" snapshot so the UI
 *    can render chips and so [augmentPromptForSend] knows what to consume.
 *
 * 3. When the user hits send, [augmentPromptForSend] simply concatenates the full text of
 *    every staged document into the prompt as plain context, then appends the user's
 *    question.
 */
@Singleton
class ChatAttachmentsCoordinator @Inject constructor(
  @ApplicationContext private val appContext: Context,
  val permanentStore: PermanentDocumentStore,
) {

  private val staged: ConcurrentHashMap<String, PendingRagStaging> = ConcurrentHashMap()

  private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val pending: ConcurrentHashMap<String, Deferred<ExtractedDoc?>> = ConcurrentHashMap()

  /**
   * Cache of extracted plain text keyed by stableId. Populated by [preIngest] and
   * consumed by [augmentPromptForSend] so the source file is never re-read at send time.
   */
  private val textCache = LruCache<String, String>(32)

  private val _indexingStates = MutableStateFlow<Map<String, DocumentIndexingStatus>>(emptyMap())
  val indexingStates: StateFlow<Map<String, DocumentIndexingStatus>> = _indexingStates.asStateFlow()

  private val _indexingProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
  val indexingProgress: StateFlow<Map<String, Float>> = _indexingProgress.asStateFlow()

  /**
   * Eventos "el usuario ha pulsado un documento permanente para adjuntarlo al próximo
   * mensaje". El input de chat (MessageInputText) los colecciona y los añade a su lista
   * local de `pickedDocuments` para que aparezcan como chip y se envíen en el siguiente
   * send. Replay = 0 porque solo importa el evento mientras el input está vivo.
   */
  private val _permanentAttachEvents = MutableSharedFlow<DocumentAttachment>(
    replay = 0,
    extraBufferCapacity = 8,
  )
  val permanentAttachEvents: SharedFlow<DocumentAttachment> = _permanentAttachEvents.asSharedFlow()

  /** Open the persistent store eagerly and clean up legacy RAG databases. */
  fun prewarm() {
    backgroundScope.launch {
      try {
        permanentStore.deleteLegacyDatabases(appContext)
      } catch (t: Throwable) {
        Log.w(TAG, "Legacy db cleanup failed", t)
      }
      try {
        permanentStore.prewarm()
      } catch (t: Throwable) {
        Log.w(TAG, "Permanent store prewarm failed", t)
      }
    }
  }

  /** Kick off extraction (and, for permanent docs, persistence) in the background. */
  fun preIngest(documents: List<DocumentAttachment>) {
    if (documents.isEmpty()) return
    for (doc in documents) {
      val key = doc.uri.toString()
      if (pending.containsKey(key)) continue
      updateIndexingState(key, DocumentIndexingStatus.INDEXING)
      updateProgress(key, 0.03f)
      val deferred = backgroundScope.async { runPipeline(doc, key) }
      pending[key] = deferred
    }
  }

  fun stage(conversationKey: String, staging: PendingRagStaging) {
    if (staging.documents.isEmpty()) {
      staged.remove(conversationKey)
    } else {
      staged[conversationKey] = staging
    }
    if (staging.documents.isNotEmpty()) preIngest(staging.documents)
  }

  fun peek(conversationKey: String): PendingRagStaging =
    staged[conversationKey] ?: PendingRagStaging(documents = emptyList())

  fun clear(conversationKey: String) {
    staged.remove(conversationKey)
  }

  fun forget(uri: String) {
    pending.remove(uri)?.cancel()
    _indexingStates.value = _indexingStates.value - uri
    _indexingProgress.value = _indexingProgress.value - uri
  }

  /**
   * El usuario ha tocado un documento permanente en el panel. Construye una
   * [DocumentAttachment] sintética sin URI viva (esquema `bluedge-permanent://<id>`),
   * precarga el texto en `textCache` desde el store, marca el indexado como READY al
   * 100 % y emite el evento para que el input de chat lo añada como chip. El siguiente
   * send funcionará exactamente igual que si se hubiera elegido vía "Adjuntar documento
   * permanente".
   */
  suspend fun emitPermanentAttach(ref: PermanentDocumentRef) {
    val startedAt = System.currentTimeMillis()
    val text = try {
      permanentStore.getText(ref.id)
    } catch (t: Throwable) {
      Log.e(TAG, "Failed to load permanent doc text for click-attach: ${ref.id}", t)
      null
    } ?: run {
      Log.w(TAG, "Permanent doc ${ref.id} has no stored text; skipping click-attach")
      return
    }
    val syntheticUri = Uri.parse("bluedge-permanent://${ref.id}")
    val key = syntheticUri.toString()
    textCache.put(ref.id, text)
    val attachment = DocumentAttachment(
      uri = syntheticUri,
      displayName = ref.displayName,
      sizeBytes = text.length.toLong(),
      mimeType = "text/plain",
      scope = AttachmentScope.PERSISTENT,
    )
    updateIndexingState(key, DocumentIndexingStatus.INDEXING)
    updateProgress(key, 0.08f)
    pending[key] = backgroundScope.async<ExtractedDoc?> {
      updateProgress(key, 0.35f)
      // Asegurar que el doc está chunkado (puede haberse añadido en una v1 del store).
      if (!permanentStore.hasChunks(ref.id)) {
        updateProgress(key, 0.65f)
        val chunks = DocumentChunker.chunk(text, CHUNK_TARGET_CHARS, CHUNK_OVERLAP_CHARS)
        if (chunks.isNotEmpty()) permanentStore.upsertChunks(ref.id, chunks)
      }
      delayUntilMinProcessingTime(startedAt, key)
      updateProgress(key, 1f)
      updateIndexingState(key, DocumentIndexingStatus.READY)
      ExtractedDoc(ref.id, ref.displayName, AttachmentScope.PERSISTENT, text)
    }
    _permanentAttachEvents.emit(attachment)
  }

  /**
   * Build the augmented prompt to feed the model. If nothing is staged, returns
   * [userPrompt] unchanged.
   */
  suspend fun augmentPromptForSend(
    conversationKey: String,
    userPrompt: String,
    maxTotalContextChars: Int = DEFAULT_MAX_TOTAL_CONTEXT_CHARS,
  ): RagAugmentationResult {
    val tStart = System.currentTimeMillis()
    val pendingStaging = staged.remove(conversationKey)
      ?: run {
        Log.i("BlueEdgePerf", "augmentPromptForSend: nothing staged for '$conversationKey' (passthrough)")
        return RagAugmentationResult(augmentedPrompt = userPrompt)
      }

    Log.i(
      "BlueEdgePerf",
      "augmentPromptForSend: staged docs=${pendingStaging.documents.size}",
    )

    // ---- 1. Wait for extraction of every staged doc ----------------------------------
    val extracted: List<ExtractedDoc> = pendingStaging.documents
      .map { doc ->
        val key = doc.uri.toString()
        val task = pending[key] ?: backgroundScope.async { runPipeline(doc, key) }
          .also { pending[key] = it }
        backgroundScope.async {
          try {
            task.await()
          } catch (t: Throwable) {
            Log.e(TAG, "Extraction failed for '${doc.displayName}'", t)
            null
          }
        }
      }
      .awaitAll()
      .filterNotNull()
    val tAfterExtract = System.currentTimeMillis()
    Log.i("BlueEdgePerf", "augment: extraction wait took ${tAfterExtract - tStart} ms (extracted=${extracted.size})")

    // ---- 2. BM25 retrieval over chunks (the actual RAG step) ------------------------
    val docIdsForRetrieval = extracted.map { docIdForRetrieval(it) }
    val queryTokens = DocumentChunker.tokenize(userPrompt)
    val retrievedHits: List<PermanentDocumentStore.ChunkHit> = if (queryTokens.isNotEmpty()) {
      try {
        permanentStore.searchChunks(
          queryTokens = queryTokens,
          docIds = docIdsForRetrieval,
          topK = DEFAULT_RETRIEVAL_TOP_K,
        )
      } catch (t: Throwable) {
        Log.w(TAG, "BM25 retrieval failed; falling back to full-text inlining", t)
        emptyList()
      }
    } else emptyList()
    val tAfterRetrieve = System.currentTimeMillis()
    Log.i(
      "BlueEdgePerf",
      "augment: BM25 retrieval took ${tAfterRetrieve - tAfterExtract} ms — queryTokens=${queryTokens.size} hits=${retrievedHits.size}",
    )

    // Map docId → display name so we can label each retrieved chunk in the prompt.
    val docIdToName: Map<String, String> = extracted.associate { docIdForRetrieval(it) to it.displayName }

    // ---- 3. Build the final prompt --------------------------------------------------
    val attachmentSections: List<PromptSection> = if (retrievedHits.isNotEmpty()) {
      // Group hits by doc and order chunks within a doc by their original index for
      // human readability. We keep BM25 ordering across docs (the most relevant doc
      // appears first).
      val grouped = LinkedHashMap<String, MutableList<PermanentDocumentStore.ChunkHit>>()
      for (hit in retrievedHits) {
        grouped.getOrPut(hit.docId) { ArrayList() }.add(hit)
      }
      grouped.map { (docId, hits) ->
        val sortedHits = hits.sortedBy { it.chunkIdx }
        val joined = sortedHits.joinToString(separator = "\n…\n") { it.text }
        PromptSection(
          displayName = docIdToName[docId] ?: docId,
          text = joined,
        )
      }
    } else {
      // Fallback: no hits (or BM25 failed). No volvemos a inyectar el documento
      // completo: eso es exactamente lo que hacía que el prompt se quedara en 99 %
      // durante minutos. Damos una muestra inicial acotada para mantener latencia
      // predecible y permitir que el usuario reformule una pregunta más específica.
      extracted.map {
        val fallbackText = if (it.text.length > FALLBACK_CHARS_PER_DOCUMENT) {
          it.text.take(FALLBACK_CHARS_PER_DOCUMENT) + "\n[...context fallback truncated; ask a more specific question for precise retrieval...]\n"
        } else {
          it.text
        }
        PromptSection(displayName = it.displayName, text = fallbackText)
      }
    }

    val augmented = buildAugmentedPrompt(
      userPrompt = userPrompt,
      attachmentSections = attachmentSections,
      maxTotalContextChars = maxTotalContextChars,
    )
    val tEnd = System.currentTimeMillis()
    Log.i(
      "BlueEdgePerf",
      "augment: TOTAL ${tEnd - tStart} ms — userChars=${userPrompt.length} augmentedChars=${augmented.length} (added=${augmented.length - userPrompt.length}) usedRetrieval=${retrievedHits.isNotEmpty()}",
    )

    // ---- 4. Cleanup -----------------------------------------------------------------
    for (doc in pendingStaging.documents) {
      pending.remove(doc.uri.toString())
    }
    // Borrar chunks temporales (su uso se limita a este send).
    for (doc in extracted.filter { it.scope == AttachmentScope.TEMPORARY }) {
      try { permanentStore.removeChunks(TEMP_DOC_ID_PREFIX + doc.stableId) } catch (_: Throwable) {}
    }

    return RagAugmentationResult(
      augmentedPrompt = augmented,
      attachedDocuments = attachmentSections.map { it.displayName },
    )
  }

  // --------------------------------------------------------------------------------------
  // Private helpers
  // --------------------------------------------------------------------------------------

  private suspend fun runPipeline(doc: DocumentAttachment, uriKey: String): ExtractedDoc? {
    val startedAt = System.currentTimeMillis()
    return try {
      updateProgress(uriKey, 0.05f)
      val stableId = withContext(Dispatchers.IO) {
        DocumentTextExtractor.stableId(appContext, doc)
      }
      updateProgress(uriKey, 0.15f)

      // Fast path 1: in-memory cache.
      val cached = textCache.get(stableId)
      if (cached != null) {
        if (doc.scope == AttachmentScope.PERSISTENT && !permanentStore.has(stableId)) {
          permanentStore.upsert(stableId, doc.displayName, cached)
        }
        ensureChunked(doc, stableId, cached, progressKey = uriKey)
        delayUntilMinProcessingTime(startedAt, uriKey)
        updateProgress(uriKey, 1f)
        updateIndexingState(uriKey, DocumentIndexingStatus.READY)
        return ExtractedDoc(stableId, doc.displayName, doc.scope, cached)
      }

      // Fast path 2: permanent doc already stored on disk → skip extraction.
      if (doc.scope == AttachmentScope.PERSISTENT) {
        val saved = permanentStore.getText(stableId)
        if (saved != null) {
          textCache.put(stableId, saved)
          ensureChunked(doc, stableId, saved, progressKey = uriKey)
          delayUntilMinProcessingTime(startedAt, uriKey)
          updateProgress(uriKey, 1f)
          updateIndexingState(uriKey, DocumentIndexingStatus.READY)
          return ExtractedDoc(stableId, doc.displayName, doc.scope, saved)
        }
      }

      // Otherwise: extract from source.
      val text = withContext(Dispatchers.IO) {
        DocumentTextExtractor.extract(appContext, doc) { pageFraction ->
          // Reservamos el tramo 0.15→0.75 para extracción; 0.75→0.95 para chunking.
          updateProgress(uriKey, 0.15f + 0.60f * pageFraction.coerceIn(0f, 1f))
        }
      }
      if (text.isBlank()) {
        Log.w(TAG, "Document '${doc.displayName}' produced no text; skipping.")
        updateIndexingState(uriKey, DocumentIndexingStatus.FAILED)
        return null
      }
      textCache.put(stableId, text)
      updateProgress(uriKey, 0.78f)

      if (doc.scope == AttachmentScope.PERSISTENT) {
        permanentStore.upsert(stableId, doc.displayName, text)
      }

      // Chunking + FTS5 indexing → fase final del pipeline.
      ensureChunked(doc, stableId, text, progressKey = uriKey)

      delayUntilMinProcessingTime(startedAt, uriKey)
      updateProgress(uriKey, 1f)
      updateIndexingState(uriKey, DocumentIndexingStatus.READY)
      ExtractedDoc(stableId, doc.displayName, doc.scope, text)
    } catch (t: Throwable) {
      Log.e(TAG, "Pipeline failed for '${doc.displayName}'", t)
      updateIndexingState(uriKey, DocumentIndexingStatus.FAILED)
      null
    }
  }

  /**
   * Asegura que el documento [doc] tiene sus chunks indexados en la tabla FTS5 del
   * `permanentStore`. Para documentos PERSISTENT solo re-chunkamos si no había chunks
   * previos (idempotente). Para TEMPORARY siempre se vuelven a indexar bajo un
   * `doc_id` con prefijo "tmp:" que se borra tras cada send.
   */
  private suspend fun ensureChunked(
    doc: DocumentAttachment,
    stableId: String,
    fullText: String,
    progressKey: String? = null,
  ) {
    val docId = if (doc.scope == AttachmentScope.PERSISTENT) stableId
                else TEMP_DOC_ID_PREFIX + stableId
    val needsChunking = doc.scope == AttachmentScope.TEMPORARY ||
      !permanentStore.hasChunks(docId)
    if (!needsChunking) {
      progressKey?.let { updateProgress(it, 0.95f) }
      return
    }
    progressKey?.let { updateProgress(it, 0.82f) }
    val chunks = DocumentChunker.chunk(fullText, CHUNK_TARGET_CHARS, CHUNK_OVERLAP_CHARS)
    if (chunks.isEmpty()) {
      Log.w(TAG, "Chunker produced 0 chunks for '${doc.displayName}'")
      return
    }
    progressKey?.let { updateProgress(it, 0.88f) }
    permanentStore.upsertChunks(docId, chunks)
    progressKey?.let { updateProgress(it, 0.95f) }
  }

  private suspend fun delayUntilMinProcessingTime(startedAtMs: Long, uriKey: String) {
    val elapsed = System.currentTimeMillis() - startedAtMs
    val remaining = MIN_DOCUMENT_PROCESSING_MS - elapsed
    if (remaining > 0L) {
      updateProgress(uriKey, 0.98f)
      delay(remaining)
    }
  }

  /** Devuelve el `doc_id` con el que un [ExtractedDoc] aparece en la tabla FTS5. */
  private fun docIdForRetrieval(d: ExtractedDoc): String =
    if (d.scope == AttachmentScope.PERSISTENT) d.stableId else TEMP_DOC_ID_PREFIX + d.stableId

  private fun updateIndexingState(uriKey: String, status: DocumentIndexingStatus) {
    _indexingStates.value = _indexingStates.value + (uriKey to status)
  }

  private fun updateProgress(uriKey: String, progress: Float) {
    val current = _indexingProgress.value[uriKey] ?: 0f
    if (progress > current) {
      _indexingProgress.value = _indexingProgress.value + (uriKey to progress)
    }
  }

  private fun buildAugmentedPrompt(
    userPrompt: String,
    attachmentSections: List<PromptSection>,
    maxTotalContextChars: Int,
  ): String {
    if (attachmentSections.isEmpty()) return userPrompt

    val builder = StringBuilder()
    var remaining = maxTotalContextChars

    builder.append("ATTACHMENTS:\n")
    remaining = appendSections(builder, attachmentSections, remaining)
    builder.append('\n')

    builder.append("USER QUESTION:\n")
    builder.append(userPrompt)
    return builder.toString()
  }

  private fun appendSections(
    out: StringBuilder,
    sections: List<PromptSection>,
    budget: Int,
  ): Int {
    if (sections.isEmpty() || budget <= 0) return budget
    var remaining = budget
    val perSectionShare = (budget / sections.size).coerceAtLeast(512)
    for (section in sections) {
      if (remaining <= 0) break
      val header = "[${section.displayName}]\n"
      if (remaining <= header.length) break
      out.append(header)
      remaining -= header.length

      val cap = minOf(perSectionShare, remaining)
      val body = section.text
      if (body.length <= cap) {
        out.append(body)
        if (!body.endsWith('\n')) out.append('\n')
        remaining -= body.length + (if (body.endsWith('\n')) 0 else 1)
      } else {
        val truncMarker = "\n[...truncated]\n"
        val keep = (cap - truncMarker.length).coerceAtLeast(0)
        out.append(body, 0, keep)
        out.append(truncMarker)
        remaining -= cap
      }
    }
    return remaining
  }

  private data class ExtractedDoc(
    val stableId: String,
    val displayName: String,
    val scope: AttachmentScope,
    val text: String,
  )

  private data class PromptSection(val displayName: String, val text: String)
}

/** What happened during a single send. Useful for UI feedback and logging. */
data class RagAugmentationResult(
  val augmentedPrompt: String,
  val attachedDocuments: List<String> = emptyList(),
)

/**
 * Composition local that exposes the singleton [ChatAttachmentsCoordinator] to anything
 * inside the chat tree (composite message body, document chip, etc.) so it can subscribe
 * to per-document indexing status / progress without having to thread the coordinator
 * through every composable.
 */
val LocalChatAttachments = compositionLocalOf<ChatAttachmentsCoordinator?> { null }
