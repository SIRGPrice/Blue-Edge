/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.Capability
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.CapabilityCategory
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ExecutionContext
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.NodeResult
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ParamSpec
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ValueKind
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.asString
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.LlmRunner
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * On-device LLM node. The prompt can reference `{{input}}` (upstream output) and `{{var.X}}`
 * variables. Its textual completion becomes this node's output, feeding downstream nodes exactly
 * like any other capability — the model is a first-class programmable citizen of the workflow.
 */
class LlmDecisionCapability @Inject constructor(
  private val runner: LlmRunner,
) : Capability {
  override val id = "ai.llm"
  override val label = "Decidir con IA"
  override val description =
    "Ejecuta el modelo IA local con un prompt. La respuesta se pasa al siguiente nodo."
  override val category = CapabilityCategory.AI
  override val icon = Icons.Outlined.AutoAwesome
  override val spriteId = "task_llm"
  override val params = listOf(
    ParamSpec(
      key = "prompt",
      label = "Prompt (usa {{input}} y {{var.X}})",
      kind = ValueKind.STRING,
      default = JsonPrimitive("Resume brevemente: {{input}}"),
    ),
  )

  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val rawPrompt = config["prompt"].asString().orEmpty()
    val prompt = interpolate(rawPrompt, ctx)
    if (!runner.isReady()) return NodeResult.fail("Modelo no inicializado")
    return runCatching {
      val answer = runner.generate(prompt)
      NodeResult.ok(mapOf("out" to JsonPrimitive(answer)))
    }.getOrElse { NodeResult.fail(it.message ?: "error IA") }
  }

  private fun interpolate(template: String, ctx: ExecutionContext): String {
    var s = template.replace("{{input}}", ctx.inputs["in"].asString().orEmpty())
    Regex("""\{\{var\.(\w+)}}""").findAll(s).toList().forEach { m ->
      s = s.replace(m.value, ctx.variables[m.groupValues[1]].asString().orEmpty())
    }
    return s
  }
}

