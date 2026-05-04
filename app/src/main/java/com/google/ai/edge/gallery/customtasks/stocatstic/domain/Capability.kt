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

package com.google.ai.edge.gallery.customtasks.stocatstic.domain

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.json.JsonObject

/** Result of executing a single node. */
data class NodeResult(
  val success: Boolean,
  val outputs: Map<String, DynValue> = emptyMap(),
  val message: String = "",
) {
  companion object {
    fun ok(outputs: Map<String, DynValue> = emptyMap()) = NodeResult(true, outputs)
    fun fail(message: String) = NodeResult(false, emptyMap(), message)
  }
}

/** Context handed to capabilities at execution time. */
interface ExecutionContext {
  val androidContext: Context
  /** Values produced by predecessor nodes keyed by input port id. */
  val inputs: Map<String, DynValue>
  /** Workflow-scoped mutable variable bag. */
  val variables: MutableMap<String, DynValue>
  fun log(message: String)
}

/** Grouping shown in the editor palette. */
enum class CapabilityCategory(val label: String, val color: Color) {
  TRIGGER("Disparadores", Color(0xFFF2B84B)),
  DEVICE("Dispositivo", Color(0xFF64B5F6)),
  INTENT("Apps / Intents", Color(0xFFBA68C8)),
  CONTROL("Control", Color(0xFF81C784)),
  AI("IA", Color(0xFFF06292)),
  NOTIFY("Avisos", Color(0xFFFFB74D)),
}

/**
 * Describes and implements a node type.
 *
 * A capability is the single extension point: adding a new node type means adding a class that
 * implements this interface and registering it in [CapabilityRegistry].
 */
interface Capability {
  val id: String
  val label: String
  val description: String
  val category: CapabilityCategory
  val icon: ImageVector
  /** Android permissions that must be granted before this capability can run. */
  val requiredPermissions: List<String> get() = emptyList()
  val params: List<ParamSpec> get() = emptyList()
  val inputs: List<PortSpec> get() = listOf(PortSpec("in", "in"))
  val outputs: List<PortSpec> get() = listOf(PortSpec("out", "out"))
  /** Sprite id drawn on the scene when the cat executes this node (see SpriteRegistry). */
  val spriteId: String get() = "task_generic"
  /**
   * When `true` the capability executes so quickly that rendering a full "work" animation
   * on the scene would just produce a flicker. The character will then walk THROUGH the
   * task's cell towards the next task without stopping. Purely cosmetic — execution
   * semantics are unchanged.
   */
  val instantaneous: Boolean get() = false

  suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult
}

