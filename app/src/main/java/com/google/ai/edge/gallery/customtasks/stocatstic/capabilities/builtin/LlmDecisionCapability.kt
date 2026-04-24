/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.Capability
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.CapabilityCategory
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ExecutionContext
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.NodeResult
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ParamSpec
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.PortSpec
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ValueKind
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.asString
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.LlmRunner
import javax.inject.Inject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Special multi-modal AI task. Unlike every other capability — which exposes a flat list of simple
 * form fields — this one declares a single [ValueKind.SPECIAL] parameter so the inspector UI opens
 * a dedicated screen ("Configurar IA…") where the user composes the full rich config.
 *
 * Config schema (nested JsonObject under `config`):
 * ```
 * {
 *   "prompt": "...",              // supports {{input}} {{var.X}} {{sender}} {{body}}
 *   "includeInputText": true,     // append upstream text even if template doesn't reference it
 *   "images": ["content://..."],  // list of IMAGE_REFs
 *   "audio":  ["content://..."],  // list of AUDIO_REFs
 *   "useUpstreamAttachment": true // also pull ctx.inputs["attachmentUri"]
 * }
 * ```
 * Output: a single text string in `out`, fully compatible with every downstream task.
 */
class LlmDecisionCapability @Inject constructor(
  private val runner: LlmRunner,
) : Capability {
  override val id = "ai.llm"
  override val label = "Decidir con IA"
  override val description =
    "Tarea especial de IA multimodal: prompt + imágenes + audio → texto. " +
      "Compatible con cualquier tarea previa/posterior."
  override val category = CapabilityCategory.AI
  override val icon = Icons.Outlined.AutoAwesome
  override val spriteId = "task_llm"
  override val params = listOf(
    ParamSpec(
      key = "config",
      label = "Configuración de IA",
      kind = ValueKind.SPECIAL,
      required = false,
      default = JsonObject(emptyMap()),
      help = "Prompt, imágenes y audio multimodal.",
    ),
  )
  override val outputs = listOf(PortSpec("out", "salida", ValueKind.STRING))

  override suspend fun execute(ctx: ExecutionContext, rawConfig: JsonObject): NodeResult {
    val cfg = (rawConfig["config"] as? JsonObject) ?: rawConfig  // back-compat
    val rawPrompt = cfg["prompt"].asString().orEmpty()
    val includeInput = (cfg["includeInputText"] as? JsonPrimitive)
      ?.contentOrNull?.toBooleanStrictOrNull() ?: true
    val useUpstreamAttachment = (cfg["useUpstreamAttachment"] as? JsonPrimitive)
      ?.contentOrNull?.toBooleanStrictOrNull() ?: true
    val prompt = interpolate(rawPrompt, ctx, includeInput)

    val imageUris = (cfg["images"] as? JsonArray).orEmpty()
      .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
      .toMutableList()
    val audioUris = (cfg["audio"] as? JsonArray).orEmpty()
      .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
      .toMutableList()

    if (useUpstreamAttachment) {
      ctx.inputs["attachmentUri"].asString()?.takeIf { it.isNotBlank() }?.let { uri ->
        val lower = uri.lowercase()
        if (listOf(".mp3", ".m4a", ".wav", ".aac", ".amr", ".ogg").any { lower.endsWith(it) }
          || lower.contains("voicemail")) audioUris += uri else imageUris += uri
      }
    }

    val bitmaps = imageUris.mapNotNull { decodeBitmap(ctx, it) }
    val audio = audioUris.mapNotNull { readBytes(ctx, it) }

    return runCatching {
      val answer = runner.generate(prompt, bitmaps, audio)
      NodeResult.ok(mapOf("out" to JsonPrimitive(answer)))
    }.getOrElse { NodeResult.fail(it.message ?: "error IA") }
  }

  private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

  private fun interpolate(template: String, ctx: ExecutionContext, includeInput: Boolean): String {
    val input = ctx.inputs["in"].asString()
      ?: ctx.inputs["out"].asString()
      ?: ctx.inputs["body"].asString()
      ?: ""
    val sender = ctx.inputs["sender"].asString().orEmpty()
    val body = ctx.inputs["body"].asString().orEmpty()
    var s = template
      .replace("{{input}}", input)
      .replace("{{sender}}", sender)
      .replace("{{body}}", body)
    Regex("""\{\{var\.(\w+)}}""").findAll(s).toList().forEach { m ->
      s = s.replace(m.value, ctx.variables[m.groupValues[1]].asString().orEmpty())
    }
    if (includeInput && !template.contains("{{input}}") && input.isNotBlank() && s.isNotBlank()) {
      s = "$s\n\n---\n$input"
    }
    return s
  }

  private fun decodeBitmap(ctx: ExecutionContext, uriStr: String): Bitmap? = runCatching {
    val uri = Uri.parse(uriStr)
    ctx.androidContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
  }.getOrNull()

  private fun readBytes(ctx: ExecutionContext, uriStr: String): ByteArray? = runCatching {
    val uri = Uri.parse(uriStr)
    ctx.androidContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
  }.getOrNull()
}

