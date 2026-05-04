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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * A dynamically typed value flowing between nodes. Kept as [JsonElement] so it survives
 * serialization without class info and can be rendered/inspected uniformly.
 */
typealias DynValue = JsonElement

/** Parameter type descriptor exposed by a capability to the editor. */
@Serializable
enum class ValueKind {
  STRING, INT, LONG, DOUBLE, BOOL, DURATION_MS, TIME_OF_DAY, URI, PACKAGE, ENUM, EXPRESSION, JSON,
  /** List of strings (phone numbers, emails, IDs, ...). Rendered as a chip editor. */
  STRING_LIST,
  /** Reference to a device contact picked through ContactsContract. */
  CONTACT_PICK,
  /** Picker for an installed application package name. */
  APP_PACKAGE_PICK,
  /** Reference (content:// or file://) to an image attachment. */
  IMAGE_REF,
  /** Reference (content:// or file://) to an audio attachment. */
  AUDIO_REF,
  /** Reference to the output of another node: `{nodeId, port}`. Rendered as an upstream picker. */
  INPUT_REF,
  /** Marks that the capability requires its own dedicated configuration screen. */
  SPECIAL,
}

/**
 * Generic "one / several specific / any" selector shared by every reactive capability
 * (Esperar SMS/WhatsApp/Telegram/Discord/Email/llamada...). Persisted as a String in
 * [WorkflowNode.config] and complemented by a `STRING_LIST` parameter with the actual values.
 */
@Serializable
enum class MatchMode { ANY, ONE, LIST }

/** Schema for a single parameter the inspector will render as a form field. */
@Serializable
data class ParamSpec(
  val key: String,
  val label: String,
  val kind: ValueKind,
  val required: Boolean = true,
  val default: DynValue? = null,
  val enumValues: List<String> = emptyList(),
  val help: String = "",
)

/** Static port descriptor (inputs/outputs). */
@Serializable
data class PortSpec(val id: String, val label: String, val kind: ValueKind = ValueKind.JSON)

fun DynValue?.asString(): String? = (this as? JsonPrimitive)?.contentOrNull()
fun DynValue?.asLong(): Long? = (this as? JsonPrimitive)?.longOrNull
fun DynValue?.asDouble(): Double? = (this as? JsonPrimitive)?.doubleOrNull
fun DynValue?.asBool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

private fun JsonPrimitive.contentOrNull(): String? = if (isString) content else content
