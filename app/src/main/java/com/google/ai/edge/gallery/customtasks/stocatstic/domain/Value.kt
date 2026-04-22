/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
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
enum class ValueKind { STRING, INT, LONG, DOUBLE, BOOL, DURATION_MS, TIME_OF_DAY, URI, PACKAGE, ENUM, EXPRESSION, JSON }

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

