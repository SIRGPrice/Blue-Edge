/*
 * Copyright 2026 BlueEdge contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.google.ai.edge.gallery.ui.common.chat.rag

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AGChatAttachments"

/**
 * Orchestrates the document-attachment / RAG pipeline end-to-end.
 *
 * Flow per send:
 * 1. [MessageInputText] calls [stage] with the documents the user attached (permanent +
 *    temporary) plus the "memoria" toggle.
 * 2. [LlmChatScreen] calls [augmentPromptForSend] right before invoking the LLM. That call:
 *    - ingests permanent docs into [persistentStore]
 *    - builds an ephemeral [TemporaryRagStore] for the temporary docs
 *    - applies the rules:
 *        a) if there is any temporary doc       -> temporary RAG must be queried
 *        b) if there is any permanent doc OR forceMemory -> persistent RAG must be queried
 *        c) if both apply, query both and merge
 *    - returns a new prompt with the retrieved context prepended in a clearly delimited
 *      block so the model can distinguish it from the user's own message.
 * 3. The ephemeral store is closed as soon as the call returns.
 */
@Singleton
class ChatAttachmentsCoordinator @Inject constructor(
  @ApplicationContext private val appContext: Context,
  val persistentStore: PersistentRagStore,
) {

  /** Per-conversation pending staging, keyed by model name (same key used for messages). */
  private val staged: ConcurrentHashMap<String, PendingRagStaging> = ConcurrentHashMap()

  /** Called by the UI whenever the attachment set changes so chips / flags stay in sync. */
  fun stage(conversationKey: String, staging: PendingRagStaging) {
    if (staging.documents.isEmpty() && !staging.forceMemory) {
      staged.remove(conversationKey)
    } else {
      staged[conversationKey] = staging
    }
  }

  /** Read-only snapshot used by the UI to render chips / indicators. */
  fun peek(conversationKey: String): PendingRagStaging =
    staged[conversationKey] ?: PendingRagStaging(documents = emptyList(), forceMemory = false)

  /** Clear without consuming. Used when the user cancels / resets. */
  fun clear(conversationKey: String) {
    staged.remove(conversationKey)
  }

  /**
   * Perform ingestion + retrieval and return the augmented prompt to feed the model.
   * If there is nothing staged, returns [userPrompt] unchanged.
   *
   * This is `suspend` because it performs SQLite and stream IO. It MUST be called from a
   * coroutine started by the screen/view-model right before `generateResponse`.
   */
  suspend fun augmentPromptForSend(
    conversationKey: String,
    userPrompt: String,
    topKPerStore: Int = 6,
    maxContextCharsPerStore: Int = 6_000,
  ): RagAugmentationResult {
    val pending = staged.remove(conversationKey)
      ?: return RagAugmentationResult(augmentedPrompt = userPrompt)

    val permanentDocs = pending.documents.filter { it.scope == AttachmentScope.PERSISTENT }
    val temporaryDocs = pending.documents.filter { it.scope == AttachmentScope.TEMPORARY }

    val mustUseTemporary = temporaryDocs.isNotEmpty()
    val mustUsePersistent = permanentDocs.isNotEmpty() || pending.forceMemory

    // ---- 1. Ingest ---------------------------------------------------------
    val permanentIngested = ingestInto(persistentStore, permanentDocs)

    val temporaryStore: TemporaryRagStore? = if (mustUseTemporary) TemporaryRagStore() else null
    val temporaryIngested = if (temporaryStore != null) {
      ingestInto(temporaryStore, temporaryDocs)
    } else emptyList()

    // ---- 2. Query ----------------------------------------------------------
    val retrievedHits = mutableListOf<RagHit>()
    try {
      if (mustUseTemporary && temporaryStore != null) {
        retrievedHits += temporaryStore.query(userPrompt, topKPerStore)
      }
      if (mustUsePersistent && persistentStore.hasAnyContent()) {
        retrievedHits += persistentStore.query(userPrompt, topKPerStore)
      }
    } finally {
      temporaryStore?.close()
    }

    // ---- 3. Build the final prompt ----------------------------------------
    val augmented = buildAugmentedPrompt(
      userPrompt = userPrompt,
      hits = retrievedHits,
      maxContextCharsPerStore = maxContextCharsPerStore,
      mustUseTemporary = mustUseTemporary,
      mustUsePersistent = mustUsePersistent,
    )

    return RagAugmentationResult(
      augmentedPrompt = augmented,
      persistentDocumentsIngested = permanentIngested,
      temporaryDocumentsIngested = temporaryIngested,
      retrievedHits = retrievedHits,
      usedPersistent = mustUsePersistent,
      usedTemporary = mustUseTemporary,
    )
  }

  // ---------------------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------------------

  private suspend fun ingestInto(
    store: RagStore,
    docs: List<DocumentAttachment>,
  ): List<RagDocumentRef> {
    if (docs.isEmpty()) return emptyList()
    val inputs = withContext(Dispatchers.IO) {
      docs.mapNotNull { doc ->
        try {
          val text = DocumentTextExtractor.extract(appContext, doc)
          if (text.isBlank()) {
            Log.w(TAG, "Document '${doc.displayName}' produced no text; skipping.")
            return@mapNotNull null
          }
          val chunks = Chunker.chunk(text)
          if (chunks.isEmpty()) return@mapNotNull null
          RagDocumentInput(
            id = DocumentTextExtractor.stableId(appContext, doc),
            displayName = doc.displayName,
            chunks = chunks,
          )
        } catch (t: Throwable) {
          Log.e(TAG, "Failed to extract '${doc.displayName}'", t)
          null
        }
      }
    }
    if (inputs.isEmpty()) return emptyList()
    return store.ingest(inputs)
  }

  private fun buildAugmentedPrompt(
    userPrompt: String,
    hits: List<RagHit>,
    maxContextCharsPerStore: Int,
    mustUseTemporary: Boolean,
    mustUsePersistent: Boolean,
  ): String {
    if (hits.isEmpty()) return userPrompt

    val persistentHits = hits.filter { it.origin == RagOrigin.PERSISTENT }
    val temporaryHits = hits.filter { it.origin == RagOrigin.TEMPORARY }

    val builder = StringBuilder()
    builder.append("You have been provided with retrieved context from the user's own attached documents.\n")
    builder.append("Use ONLY this context to answer when it is relevant. If the context does not cover the question, say so explicitly.\n\n")

    if (mustUseTemporary && temporaryHits.isNotEmpty()) {
      builder.append(
        sectionFor(
          header = "TEMPORARY CONTEXT (valid only for this question)",
          hits = temporaryHits,
          budget = maxContextCharsPerStore,
        )
      )
      builder.append('\n')
    }
    if (mustUsePersistent && persistentHits.isNotEmpty()) {
      builder.append(
        sectionFor(
          header = "PERSISTENT MEMORY (from previously attached documents)",
          hits = persistentHits,
          budget = maxContextCharsPerStore,
        )
      )
      builder.append('\n')
    }

    builder.append("USER QUESTION:\n")
    builder.append(userPrompt)
    return builder.toString()
  }

  private fun sectionFor(header: String, hits: List<RagHit>, budget: Int): String {
    val sb = StringBuilder()
    sb.append("=== ").append(header).append(" ===\n")
    var used = 0
    for (hit in hits) {
      val block = buildString {
        append("[Source: ").append(hit.displayName).append("]\n")
        append(hit.chunk.trim())
        append('\n')
      }
      if (used + block.length > budget && used > 0) break
      sb.append(block)
      used += block.length
    }
    return sb.toString()
  }
}

/** What happened during a single send. Useful for UI feedback and logging. */
data class RagAugmentationResult(
  val augmentedPrompt: String,
  val persistentDocumentsIngested: List<RagDocumentRef> = emptyList(),
  val temporaryDocumentsIngested: List<RagDocumentRef> = emptyList(),
  val retrievedHits: List<RagHit> = emptyList(),
  val usedPersistent: Boolean = false,
  val usedTemporary: Boolean = false,
)

