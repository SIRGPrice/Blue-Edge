/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Persists the set of world cells where the user has manually deleted a random decoration.
 *
 * Cells are packed as `Long` keys â€” the same `(cx shl 32) or (cy and 0xFFFFFFFF)` scheme the
 * renderer uses â€” and the whole set is written to `filesDir/stocatstic/decorations.json` on
 * every mutation. The [StateFlow] is collected by the scene so the tile disappears instantly
 * when the user confirms the deletion.
 */
@Singleton
class DecorationStore @Inject constructor(
  @ApplicationContext ctx: Context,
) {
  private val json = Json { prettyPrint = false; ignoreUnknownKeys = true; encodeDefaults = true }
  private val file: File =
    File(ctx.filesDir, "stocatstic").apply { mkdirs() }.let { File(it, "decorations.json") }

  private val _deleted = MutableStateFlow(load())
  val deleted: StateFlow<Set<Long>> = _deleted.asStateFlow()

  private fun load(): Set<Long> = runCatching {
    if (!file.exists()) emptySet()
    else json.decodeFromString(SetSerializer(Long.serializer()), file.readText())
  }.getOrDefault(emptySet())

  private fun persist(set: Set<Long>) {
    runCatching {
      file.writeText(json.encodeToString(SetSerializer(Long.serializer()), set))
    }
  }

  fun delete(cellX: Int, cellY: Int) {
    val key = (cellX.toLong() shl 32) or (cellY.toLong() and 0xFFFFFFFFL)
    val next = _deleted.value + key
    _deleted.value = next
    persist(next)
  }

  fun isDeleted(cellX: Int, cellY: Int): Boolean {
    val key = (cellX.toLong() shl 32) or (cellY.toLong() and 0xFFFFFFFFL)
    return _deleted.value.contains(key)
  }
}


