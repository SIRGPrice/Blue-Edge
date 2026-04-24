/*
 * Copyright 2026 BlueEdge contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.google.ai.edge.gallery.ui.common.chat.rag

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** On-disk RAG store. Used for "persistent" document attachments and "memoria" queries. */
class PersistentRagStore(context: Context) : RagStore by FtsRagStore(
  dbFactory = {
    val dir = File(context.filesDir, "rag").apply { mkdirs() }
    val path = File(dir, "persistent_rag.db").absolutePath
    SQLiteDatabase.openOrCreateDatabase(path, null)
  },
  origin = RagOrigin.PERSISTENT,
)

/** In-memory RAG store. A fresh instance is created and discarded per request. */
class TemporaryRagStore : RagStore by FtsRagStore(
  dbFactory = { SQLiteDatabase.create(null) /* :memory: */ },
  origin = RagOrigin.TEMPORARY,
)

