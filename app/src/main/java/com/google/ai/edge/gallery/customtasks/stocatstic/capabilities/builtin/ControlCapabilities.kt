/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Tag
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.Capability
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.CapabilityCategory
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ExecutionContext
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.NodeResult
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ParamSpec
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.PortSpec
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ValueKind
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.asLong
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.asString
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class DelayCapability @Inject constructor() : Capability {
  override val id = "control.delay"
  override val label = "Esperar"
  override val description = "Pausa la ejecución durante N milisegundos."
  override val category = CapabilityCategory.CONTROL
  override val icon = Icons.Outlined.HourglassEmpty
  override val spriteId = "task_delay"
  override val params = listOf(
    ParamSpec("ms", "Milisegundos", ValueKind.DURATION_MS, default = JsonPrimitive(1000L)),
  )

  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    delay(config["ms"].asLong() ?: 1000L)
    return NodeResult.ok()
  }
}

/**
 * Evaluates a simple deterministic expression referencing the latest input or a variable and
 * routes through TRUE_BRANCH or FALSE_BRANCH edges. The engine inspects outputs["branch"] to
 * decide which successors to execute.
 *
 * Supported syntax (MVP): `input contains "foo"`, `input == "bar"`, `var.X > 10`, `input`.
 */
class BranchCapability @Inject constructor() : Capability {
  override val id = "control.branch"
  override val label = "Si / Entonces"
  override val description = "Evalúa una condición y elige rama verdadera o falsa."
  override val category = CapabilityCategory.CONTROL
  override val icon = Icons.Outlined.CallSplit
  override val spriteId = "task_branch"
  override val instantaneous = true
  override val outputs = listOf(PortSpec("true", "true"), PortSpec("false", "false"))
  override val params = listOf(
    ParamSpec("expr", "Expresión", ValueKind.EXPRESSION,
      default = JsonPrimitive("input == \"ok\"")),
  )

  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val expr = config["expr"].asString().orEmpty()
    val result = evalExpr(expr, ctx)
    return NodeResult.ok(mapOf("branch" to JsonPrimitive(result)))
  }

  private fun evalExpr(expr: String, ctx: ExecutionContext): Boolean {
    if (expr.isBlank()) return true
    val s = expr.trim()
    val input = ctx.inputs["in"].asString().orEmpty()
    // contains
    Regex("""^input\s+contains\s+"(.*)"$""").find(s)?.let { return input.contains(it.groupValues[1]) }
    Regex("""^input\s*==\s*"(.*)"$""").find(s)?.let { return input == it.groupValues[1] }
    Regex("""^var\.(\w+)\s*([<>]=?|==|!=)\s*(-?\d+(?:\.\d+)?)$""").find(s)?.let { m ->
      val v = ctx.variables[m.groupValues[1]].asString()?.toDoubleOrNull() ?: return false
      val num = m.groupValues[3].toDouble()
      return when (m.groupValues[2]) {
        "<" -> v < num; "<=" -> v <= num; ">" -> v > num; ">=" -> v >= num
        "==" -> v == num; "!=" -> v != num; else -> false
      }
    }
    if (s == "input") return input.isNotBlank()
    return false
  }
}

class SetVariableCapability @Inject constructor() : Capability {
  override val id = "control.set_var"
  override val label = "Guardar variable"
  override val description = "Asigna un valor (o el input) a una variable del flujo."
  override val category = CapabilityCategory.CONTROL
  override val icon = Icons.Outlined.Tag
  override val spriteId = "task_var"
  override val instantaneous = true
  override val params = listOf(
    ParamSpec("name", "Nombre", ValueKind.STRING, default = JsonPrimitive("x")),
    ParamSpec("value", "Valor (vacío = input)", ValueKind.STRING, required = false,
      default = JsonPrimitive("")),
  )

  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val name = config["name"].asString().orEmpty().ifBlank { "x" }
    val value = config["value"].asString().orEmpty()
      .ifBlank { ctx.inputs["in"].asString().orEmpty() }
    ctx.variables[name] = JsonPrimitive(value)
    return NodeResult.ok(mapOf("out" to JsonPrimitive(value)))
  }
}

