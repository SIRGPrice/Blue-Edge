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
 * Cells are packed as `Long` keys — the same `(cx shl 32) or (cy and 0xFFFFFFFF)` scheme the
 * renderer uses — and the whole set is written to `filesDir/stocatstic/decorations.json` on
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

