/*
 * Copyright 2026 SIRGPrice and Blue Edge contributors
 * Part of Blue Edge, a heavily modified app fork based on Google AI Edge Gallery.
 * Upstream project originally published by Google LLC:
 * https://github.com/google-ai-edge/gallery
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.common.chat.rag

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "AGPermanentDocStore"
private const val DB_NAME = "permanent_documents.db"
private const val DB_VERSION = 3

private const val TABLE = "permanent_docs"
private const val COL_ID = "id"
private const val COL_NAME = "display_name"
private const val COL_TEXT = "full_text"
private const val COL_LEN = "text_length"
private const val COL_ADDED = "added_at_ms"

/** Tabla normal (no FTS) que contiene cada chunk de cada documento adjuntado.
 *  Guardamos los tokens pre-normalizados para acelerar el scoring BM25 en runtime
 *  (ver [Bm25Search]). No usamos `fts5` porque algunos firmwares Android (Samsung
 *  One UI 7 incluido) recortan SQLite y el módulo no está disponible. */
private const val TABLE_CHUNKS = "doc_chunks"
private const val CHUNK_DOC_ID = "doc_id"
private const val CHUNK_IDX = "chunk_idx"
private const val CHUNK_CONTENT = "content"
private const val CHUNK_TOKENS = "tokens"
private const val CHUNK_TOKEN_COUNT = "token_count"

/**
 * Simple on-disk store for documents that the user has marked as PERMANENT. Holds the
 * extracted plain text in full so it can be inlined verbatim into the prompt. Replaces
 * the previous FTS / chunk / RAG infrastructure with a flat key/value table.
 */
class PermanentDocumentStore(context: Context) {

  private val helper = object : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE $TABLE (
          $COL_ID TEXT PRIMARY KEY NOT NULL,
          $COL_NAME TEXT NOT NULL,
          $COL_TEXT TEXT NOT NULL,
          $COL_LEN INTEGER NOT NULL,
          $COL_ADDED INTEGER NOT NULL
        )
        """.trimIndent(),
      )
      createChunksTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
      // Cualquier versión anterior puede haber dejado una tabla `doc_chunks`
      // a medio crear (la v2 intentó FTS5 y crasheaba en devices sin ese módulo).
      // Borrar y recrear como tabla normal es seguro: los chunks se vuelven a
      // generar lazy en el próximo attach.
      try { db.execSQL("DROP TABLE IF EXISTS $TABLE_CHUNKS") } catch (_: Throwable) {}
      createChunksTable(db)
    }

    private fun createChunksTable(db: SQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS $TABLE_CHUNKS (
          $CHUNK_DOC_ID TEXT NOT NULL,
          $CHUNK_IDX INTEGER NOT NULL,
          $CHUNK_CONTENT TEXT NOT NULL,
          $CHUNK_TOKENS TEXT NOT NULL,
          $CHUNK_TOKEN_COUNT INTEGER NOT NULL,
          PRIMARY KEY ($CHUNK_DOC_ID, $CHUNK_IDX)
        )
        """.trimIndent(),
      )
      db.execSQL(
        "CREATE INDEX IF NOT EXISTS idx_${TABLE_CHUNKS}_doc ON $TABLE_CHUNKS($CHUNK_DOC_ID)",
      )
    }
  }

  private val mutex = Mutex()

  /** Drop any legacy RAG SQLite databases produced by previous versions of the app. */
  fun deleteLegacyDatabases(context: Context) {
    try {
      val candidates = listOf("rag_persistent.db", "rag_persistent.db-journal", "rag_persistent.db-wal", "rag_persistent.db-shm")
      val dbDir: File? = context.applicationContext.getDatabasePath("rag_persistent.db")?.parentFile
      if (dbDir != null && dbDir.isDirectory) {
        for (name in candidates) {
          val f = File(dbDir, name)
          if (f.exists()) {
            if (f.delete()) Log.i(TAG, "Removed legacy RAG db file: ${f.name}")
          }
        }
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Legacy RAG db cleanup failed", t)
    }
  }

  suspend fun prewarm() = withContext(Dispatchers.IO) {
    mutex.withLock { helper.writableDatabase }
    Unit
  }

  /** Insert or replace a document by its stable id. */
  suspend fun upsert(id: String, displayName: String, fullText: String) = withContext(Dispatchers.IO) {
    mutex.withLock {
      val db = helper.writableDatabase
      val values = ContentValues().apply {
        put(COL_ID, id)
        put(COL_NAME, displayName)
        put(COL_TEXT, fullText)
        put(COL_LEN, fullText.length)
        put(COL_ADDED, System.currentTimeMillis())
      }
      db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
    Unit
  }

  /** Whether the store already has a row for [id]. */
  suspend fun has(id: String): Boolean = withContext(Dispatchers.IO) {
    mutex.withLock {
      val db = helper.readableDatabase
      db.query(TABLE, arrayOf(COL_ID), "$COL_ID = ?", arrayOf(id), null, null, null, "1").use { c ->
        c.moveToFirst()
      }
    }
  }

  /** Return the full text of [id] or null if not present. */
  suspend fun getText(id: String): String? = withContext(Dispatchers.IO) {
    mutex.withLock {
      val db = helper.readableDatabase
      db.query(TABLE, arrayOf(COL_TEXT), "$COL_ID = ?", arrayOf(id), null, null, null, "1").use { c ->
        if (c.moveToFirst()) c.getString(0) else null
      }
    }
  }

  /** List every saved document, newest first. */
  suspend fun list(): List<PermanentDocumentRef> = withContext(Dispatchers.IO) {
    mutex.withLock {
      val db = helper.readableDatabase
      db.query(
        TABLE,
        arrayOf(COL_ID, COL_NAME, COL_LEN, COL_ADDED),
        null, null, null, null,
        "$COL_ADDED DESC",
      ).use { c ->
        val out = ArrayList<PermanentDocumentRef>(c.count)
        while (c.moveToNext()) {
          out.add(
            PermanentDocumentRef(
              id = c.getString(0),
              displayName = c.getString(1),
              textLength = c.getInt(2),
              addedAtMs = c.getLong(3),
            )
          )
        }
        out
      }
    }
  }


  suspend fun remove(id: String) = withContext(Dispatchers.IO) {
    mutex.withLock {
      val db = helper.writableDatabase
      db.delete(TABLE, "$COL_ID = ?", arrayOf(id))
      db.delete(TABLE_CHUNKS, "$CHUNK_DOC_ID = ?", arrayOf(id))
    }
    Unit
  }

  suspend fun clearAll() = withContext(Dispatchers.IO) {
    mutex.withLock {
      val db = helper.writableDatabase
      db.delete(TABLE, null, null)
      db.delete(TABLE_CHUNKS, null, null)
    }
    Unit
  }

  /** Whether the store currently contains any document. */
  suspend fun hasAnyContent(): Boolean = withContext(Dispatchers.IO) {
    mutex.withLock {
      val db = helper.readableDatabase
      db.rawQuery("SELECT 1 FROM $TABLE LIMIT 1", null).use { it.moveToFirst() }
    }
  }

  // ---------------------------------------------------------------------------
  // FTS5 chunk index — BM25 retrieval
  // ---------------------------------------------------------------------------

  /** True if [docId] already has chunks indexed in FTS5. */
  suspend fun hasChunks(docId: String): Boolean = withContext(Dispatchers.IO) {
    mutex.withLock {
      val db = helper.readableDatabase
      db.rawQuery(
        "SELECT 1 FROM $TABLE_CHUNKS WHERE $CHUNK_DOC_ID = ? LIMIT 1",
        arrayOf(docId),
      ).use { it.moveToFirst() }
    }
  }

  /**
   * Replace every chunk indexed for [docId] with [chunks]. Atomic: deletes the
   * existing rows and re-inserts in a single transaction.
   */
  suspend fun upsertChunks(docId: String, chunks: List<DocumentChunker.Chunk>) =
    withContext(Dispatchers.IO) {
      mutex.withLock {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
          db.delete(TABLE_CHUNKS, "$CHUNK_DOC_ID = ?", arrayOf(docId))
          val stmt = db.compileStatement(
            "INSERT INTO $TABLE_CHUNKS ($CHUNK_DOC_ID, $CHUNK_IDX, $CHUNK_CONTENT, $CHUNK_TOKENS, $CHUNK_TOKEN_COUNT) " +
              "VALUES (?, ?, ?, ?, ?)",
          )
          for (c in chunks) {
            val tokens = DocumentChunker.tokenize(c.text)
            stmt.bindString(1, docId)
            stmt.bindLong(2, c.index.toLong())
            stmt.bindString(3, c.text)
            stmt.bindString(4, tokens.joinToString(" "))
            stmt.bindLong(5, tokens.size.toLong())
            stmt.executeInsert()
            stmt.clearBindings()
          }
          db.setTransactionSuccessful()
        } finally {
          db.endTransaction()
        }
      }
      Unit
    }

  /** Remove every chunk row for [docId]. */
  suspend fun removeChunks(docId: String) = withContext(Dispatchers.IO) {
    mutex.withLock {
      val db = helper.writableDatabase
      db.delete(TABLE_CHUNKS, "$CHUNK_DOC_ID = ?", arrayOf(docId))
    }
    Unit
  }

  data class ChunkHit(val docId: String, val chunkIdx: Int, val text: String, val score: Double)

  /**
   * Loads every chunk belonging to [docIds] and runs BM25 scoring in memory using
   * [queryTokens]. Returns up to [topK] chunks ordered by relevance (best first).
   *
   * BM25 (Okapi) with k1 = 1.5, b = 0.75 — defaults that match Lucene's tuning. We
   * compute IDF / avgdl over the *selected sub-corpus* (only the docs the user is
   * actually attaching to this send), so scoring is local and cheap even with
   * thousands of chunks: typical send sees < 50 ms.
   */
  suspend fun searchChunks(
    queryTokens: List<String>,
    docIds: Collection<String>,
    topK: Int = 8,
  ): List<ChunkHit> = withContext(Dispatchers.IO) {
    if (docIds.isEmpty() || queryTokens.isEmpty()) return@withContext emptyList()
    mutex.withLock {
      val db = helper.readableDatabase
      val placeholders = docIds.joinToString(",") { "?" }
      val sql = """
        SELECT $CHUNK_DOC_ID, $CHUNK_IDX, $CHUNK_CONTENT, $CHUNK_TOKENS, $CHUNK_TOKEN_COUNT
        FROM $TABLE_CHUNKS
        WHERE $CHUNK_DOC_ID IN ($placeholders)
      """.trimIndent()
      val rows = ArrayList<Bm25Search.Row>()
      try {
        db.rawQuery(sql, docIds.toTypedArray()).use { c ->
          while (c.moveToNext()) {
            rows.add(
              Bm25Search.Row(
                docId = c.getString(0),
                chunkIdx = c.getInt(1),
                content = c.getString(2),
                tokens = c.getString(3).split(' ').filter { it.isNotEmpty() },
                tokenCount = c.getInt(4),
              ),
            )
          }
        }
      } catch (t: Throwable) {
        Log.w(TAG, "Failed to load chunks for retrieval", t)
        return@withLock emptyList<ChunkHit>()
      }
      Bm25Search.score(queryTokens, rows, topK)
    }
  }
}
