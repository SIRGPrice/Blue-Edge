/*
 * Copyright 2026 BlueEdge contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.google.ai.edge.gallery.ui.common.chat.rag

import android.net.Uri

/**
 * Scope that determines which RAG store a document goes into.
 *
 * - [PERSISTENT] documents are ingested into the on-disk persistent RAG, survive across
 *   sessions and are always available to future prompts when the persistent RAG is queried.
 * - [TEMPORARY] documents are ingested into an ephemeral in-memory RAG that lives only for
 *   the duration of a single request and is discarded right after the answer is produced.
 */
enum class AttachmentScope {
  PERSISTENT,
  TEMPORARY,
}

/**
 * A user-picked document waiting to be turned into text and ingested into the appropriate
 * RAG store. Its bytes are NOT read until the user hits "send", and even then the pipeline
 * only materialises the textual representation (no binary payload is carried around).
 */
data class DocumentAttachment(
  val uri: Uri,
  val displayName: String,
  val sizeBytes: Long,
  val mimeType: String?,
  val scope: AttachmentScope,
)

/**
 * Result of turning a [DocumentAttachment] into text + chunks. Produced by
 * [DocumentTextExtractor] and passed to the RAG stores.
 */
data class RagDocumentInput(
  /** Stable id derived from display name + content hash. */
  val id: String,
  val displayName: String,
  /** Ordered list of textual chunks ready to be indexed. */
  val chunks: List<String>,
)

/** Metadata about a document that currently lives in a RAG store. */
data class RagDocumentRef(
  val id: String,
  val displayName: String,
  val chunkCount: Int,
  val ingestedAtMs: Long,
)

/** A retrieved chunk with its matching score. */
data class RagHit(
  val docId: String,
  val displayName: String,
  val chunk: String,
  /** Lower is better (bm25 from FTS5). */
  val score: Double,
  val origin: RagOrigin,
)

enum class RagOrigin {
  PERSISTENT,
  TEMPORARY,
}

/**
 * Snapshot of everything the user has staged for a single send operation.
 *
 * - [documents] is the full list of pending attachments (both scopes).
 * - [forceMemory] is the "Adjuntar memoria" flag, which by itself triggers the persistent
 *   RAG even if no persistent document is being attached in this request.
 */
data class PendingRagStaging(
  val documents: List<DocumentAttachment>,
  val forceMemory: Boolean,
)

