/*
 * Copyright 2026 BlueEdge contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.google.ai.edge.gallery.ui.common.chat.rag

/**
 * Abstraction over a RAG chunk store. Implementations are either:
 * - [PersistentRagStore]: on-disk SQLite/FTS5, survives process restarts.
 * - [TemporaryRagStore]: in-memory SQLite/FTS5, lives only for one request.
 *
 * All methods are safe to call from any thread; implementations perform their work on
 * IO dispatchers.
 */
interface RagStore {

  /** Where this store's hits should be tagged as coming from, for observability. */
  val origin: RagOrigin

  /**
   * Ingest one or more documents. Idempotent: re-ingesting a document with the same id
   * replaces its chunks.
   */
  suspend fun ingest(documents: List<RagDocumentInput>): List<RagDocumentRef>

  /**
   * Retrieve up to [topK] chunks that best match [query].
   *
   * Matching uses FTS5 + bm25 ranking. Noise tokens are stripped and remaining words become
   * prefix OR matches so short queries still surface relevant chunks.
   */
  suspend fun query(query: String, topK: Int): List<RagHit>

  /** List every document currently stored. */
  suspend fun listDocuments(): List<RagDocumentRef>

  /** Remove a document (and all its chunks). No-op if the id is unknown. */
  suspend fun remove(docId: String)

  /** Purge everything. */
  suspend fun clearAll()

  /** Close the underlying database. Idempotent. */
  suspend fun close()

  /** Whether this store currently holds any chunks. Cheap call. */
  suspend fun hasAnyContent(): Boolean
}


