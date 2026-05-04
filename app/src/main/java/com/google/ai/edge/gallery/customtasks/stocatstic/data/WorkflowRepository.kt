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
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.Workflow
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Flat-file JSON repository. Each workflow is a separate file in `filesDir/stocatstic/` to keep
 * reads/writes scoped and make migrations trivial. No Room dependency required.
 */
@Singleton
class WorkflowRepository @Inject constructor(
  @ApplicationContext private val ctx: Context,
) {
  private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
  private val dir: File = File(ctx.filesDir, "stocatstic").apply { mkdirs() }
  private val _workflows = MutableStateFlow<List<Workflow>>(loadAll())
  val workflows: StateFlow<List<Workflow>> = _workflows.asStateFlow()

  private fun fileFor(id: String) = File(dir, "$id.json")

  private fun loadAll(): List<Workflow> = dir.listFiles { f -> f.extension == "json" }
    ?.mapNotNull { runCatching { json.decodeFromString(Workflow.serializer(), it.readText()) }.getOrNull() }
    ?.sortedByDescending { it.updatedAt } ?: emptyList()

  fun refresh() { _workflows.value = loadAll() }

  fun get(id: String): Workflow? = _workflows.value.firstOrNull { it.id == id }

  fun save(workflow: Workflow): Workflow {
    val updated = workflow.copy(updatedAt = System.currentTimeMillis())
    fileFor(updated.id).writeText(json.encodeToString(Workflow.serializer(), updated))
    _workflows.value = _workflows.value.filter { it.id != updated.id } + updated
    _workflows.value = _workflows.value.sortedByDescending { it.updatedAt }
    return updated
  }

  fun delete(id: String) {
    fileFor(id).delete()
    _workflows.value = _workflows.value.filter { it.id != id }
  }
}

