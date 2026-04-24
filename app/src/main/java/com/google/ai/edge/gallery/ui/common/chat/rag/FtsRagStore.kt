/*
 * Copyright 2026 BlueEdge contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.google.ai.edge.gallery.ui.common.chat.rag

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "AGFtsRagStore"

/**
 * Shared FTS5-backed implementation of [RagStore].
 *
 * The persistent variant opens a file database; the temporary variant opens `:memory:`.
 * Both share the same schema:
 *
 * ```
 * rag_docs(id TEXT PRIMARY KEY, display_name TEXT, ingested_at INTEGER, chunk_count INTEGER)
 * rag_chunks  FTS5(doc_id UNINDEXED, display_name UNINDEXED, content, tokenize='unicode61')
 * ```
 *
 * Retrieval uses `bm25(rag_chunks)` (lower = better).
 */
internal class FtsRagStore(
  private val dbFactory: () -> SQLiteDatabase,
  override val origin: RagOrigin,
) : RagStore {

  private val mutex = Mutex()
  @Volatile private var db: SQLiteDatabase? = null
  @Volatile private var closed = false

  private suspend fun database(): SQLiteDatabase = withContext(Dispatchers.IO) {
    mutex.withLock {
      check(!closed) { "RagStore $origin has been closed." }
      val existing = db
      if (existing != null && existing.isOpen) return@withLock existing
      val opened = dbFactory()
      bootstrapSchema(opened)
      db = opened
      opened
    }
  }

  private fun bootstrapSchema(database: SQLiteDatabase) {
    database.execSQL(
      """
      CREATE TABLE IF NOT EXISTS rag_docs(
        id TEXT PRIMARY KEY,
        display_name TEXT NOT NULL,
        ingested_at INTEGER NOT NULL,
        chunk_count INTEGER NOT NULL DEFAULT 0
      )
      """.trimIndent()
    )
    database.execSQL(
      """
      CREATE VIRTUAL TABLE IF NOT EXISTS rag_chunks USING fts5(
        doc_id UNINDEXED,
        display_name UNINDEXED,
        content,
        tokenize = 'unicode61'
      )
      """.trimIndent()
    )
  }

  override suspend fun ingest(documents: List<RagDocumentInput>): List<RagDocumentRef> {
    if (documents.isEmpty()) return emptyList()
    val database = database()
    return withContext(Dispatchers.IO) {
      val refs = mutableListOf<RagDocumentRef>()
      database.beginTransaction()
      try {
        for (doc in documents) {
          // Remove any previous ingestion of the same document id (idempotent).
          database.delete("rag_chunks", "doc_id = ?", arrayOf(doc.id))
          database.delete("rag_docs", "id = ?", arrayOf(doc.id))

          val now = System.currentTimeMillis()
          database.execSQL(
            "INSERT INTO rag_docs(id, display_name, ingested_at, chunk_count) VALUES(?,?,?,?)",
            arrayOf<Any>(doc.id, doc.displayName, now, doc.chunks.size),
          )

          val chunkStatement = database.compileStatement(
            "INSERT INTO rag_chunks(doc_id, display_name, content) VALUES(?,?,?)"
          )
          try {
            for (chunk in doc.chunks) {
              chunkStatement.clearBindings()
              chunkStatement.bindString(1, doc.id)
              chunkStatement.bindString(2, doc.displayName)
              chunkStatement.bindString(3, chunk)
              chunkStatement.executeInsert()
            }
          } finally {
            chunkStatement.close()
          }

          refs.add(
            RagDocumentRef(
              id = doc.id,
              displayName = doc.displayName,
              chunkCount = doc.chunks.size,
              ingestedAtMs = now,
            )
          )
        }
        database.setTransactionSuccessful()
      } finally {
        database.endTransaction()
      }
      refs
    }
  }

  override suspend fun query(query: String, topK: Int): List<RagHit> {
    if (topK <= 0) return emptyList()
    val matchExpression = buildMatchExpression(query) ?: return emptyList()
    val database = database()
    return withContext(Dispatchers.IO) {
      val hits = mutableListOf<RagHit>()
      try {
        database.rawQuery(
          """
          SELECT doc_id, display_name, content, bm25(rag_chunks) AS score
          FROM rag_chunks
          WHERE rag_chunks MATCH ?
          ORDER BY score ASC
          LIMIT ?
          """.trimIndent(),
          arrayOf(matchExpression, topK.toString()),
        ).use { cursor ->
          while (cursor.moveToNext()) {
            hits.add(
              RagHit(
                docId = cursor.getString(0),
                displayName = cursor.getString(1),
                chunk = cursor.getString(2),
                score = cursor.getDouble(3),
                origin = origin,
              )
            )
          }
        }
      } catch (t: Throwable) {
        Log.w(TAG, "FTS5 query failed for origin=$origin; falling back to LIKE", t)
        hits.addAll(likeFallback(database, query, topK))
      }
      hits
    }
  }

  override suspend fun listDocuments(): List<RagDocumentRef> {
    val database = database()
    return withContext(Dispatchers.IO) {
      val refs = mutableListOf<RagDocumentRef>()
      database.rawQuery(
        "SELECT id, display_name, chunk_count, ingested_at FROM rag_docs ORDER BY ingested_at DESC",
        null,
      ).use { cursor ->
        while (cursor.moveToNext()) {
          refs.add(
            RagDocumentRef(
              id = cursor.getString(0),
              displayName = cursor.getString(1),
              chunkCount = cursor.getInt(2),
              ingestedAtMs = cursor.getLong(3),
            )
          )
        }
      }
      refs
    }
  }

  override suspend fun remove(docId: String) {
    val database = database()
    withContext(Dispatchers.IO) {
      database.beginTransaction()
      try {
        database.delete("rag_chunks", "doc_id = ?", arrayOf(docId))
        database.delete("rag_docs", "id = ?", arrayOf(docId))
        database.setTransactionSuccessful()
      } finally {
        database.endTransaction()
      }
    }
  }

  override suspend fun clearAll() {
    val database = database()
    withContext(Dispatchers.IO) {
      database.beginTransaction()
      try {
        database.delete("rag_chunks", null, null)
        database.delete("rag_docs", null, null)
        database.setTransactionSuccessful()
      } finally {
        database.endTransaction()
      }
    }
  }

  override suspend fun hasAnyContent(): Boolean {
    val database = database()
    return withContext(Dispatchers.IO) {
      database.rawQuery("SELECT COUNT(*) FROM rag_docs", null).use { cursor ->
        cursor.moveToFirst() && cursor.getLong(0) > 0L
      }
    }
  }

  override suspend fun close() {
    withContext(Dispatchers.IO) {
      mutex.withLock {
        try { db?.close() } catch (_: Throwable) {}
        db = null
        closed = true
      }
    }
  }

  // -----------------------------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------------------------

  private fun buildMatchExpression(query: String): String? {
    val tokens = query
      .lowercase()
      .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
      .split(Regex("\\s+"))
      .filter { it.isNotBlank() && it.length >= 2 }
      .take(12)
    if (tokens.isEmpty()) return null
    // FTS5 requires quoting tokens with prefix match. OR them to increase recall.
    return tokens.joinToString(" OR ") { "\"$it\"*" }
  }

  private fun likeFallback(database: SQLiteDatabase, query: String, topK: Int): List<RagHit> {
    val tokens = query
      .lowercase()
      .split(Regex("\\s+"))
      .filter { it.length >= 3 }
      .take(6)
    if (tokens.isEmpty()) return emptyList()

    val where = tokens.joinToString(" OR ") { "lower(content) LIKE ?" }
    val args = tokens.map { "%$it%" }.toTypedArray()
    val hits = mutableListOf<RagHit>()
    database.rawQuery(
      "SELECT doc_id, display_name, content FROM rag_chunks WHERE $where LIMIT ?",
      args + topK.toString(),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        hits.add(
          RagHit(
            docId = cursor.getString(0),
            displayName = cursor.getString(1),
            chunk = cursor.getString(2),
            score = 1000.0,
            origin = origin,
          )
        )
      }
    }
    return hits
  }
}


