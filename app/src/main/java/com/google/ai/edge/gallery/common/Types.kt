/*
 * Copyright 2025 Google LLC
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

package com.google.ai.edge.gallery.common

import androidx.compose.ui.graphics.Color

data class Classification(val label: String, val score: Float, val color: Color)

data class JsonObjAndTextContent<T>(val jsonObj: T, val textContent: String)

class AudioClip(val audioData: ByteArray, val sampleRate: Int)

open class AgentAction(val name: AgentActionName)

class ToolProgressAgentAction(
  val label: String,
  val inProgress: Boolean,
  val addItemTitle: String = "",
  val addItemDescription: String = "",
  val customData: Any? = null,
) : AgentAction(name = AgentActionName.TOOL_PROGRESS)

enum class AgentActionName() {
  TOOL_PROGRESS,
}