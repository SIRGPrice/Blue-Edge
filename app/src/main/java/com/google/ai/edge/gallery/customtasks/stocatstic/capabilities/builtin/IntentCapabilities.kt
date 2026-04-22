/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin

import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Send
import androidx.core.net.toUri
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.Capability
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.CapabilityCategory
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ExecutionContext
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.NodeResult
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ParamSpec
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ValueKind
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.asString
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Launches an app by package name (uses main launcher intent). */
class OpenAppCapability @Inject constructor() : Capability {
  override val id = "intent.open_app"
  override val label = "Abrir app"
  override val description = "Abre una app instalada por su nombre de paquete."
  override val category = CapabilityCategory.INTENT
  override val icon = Icons.Outlined.Apps
  override val spriteId = "task_app"
  override val params = listOf(
    ParamSpec("package", "Paquete", ValueKind.PACKAGE, default = JsonPrimitive("")),
  )

  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val pkg = config["package"].asString().orEmpty()
    if (pkg.isBlank()) return NodeResult.fail("Paquete vacío")
    val intent = ctx.androidContext.packageManager.getLaunchIntentForPackage(pkg)
      ?: return NodeResult.fail("App no instalada: $pkg")
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.androidContext.startActivity(intent)
    return NodeResult.ok()
  }
}

/** Opens an URL (http/geo/mailto/tel) via ACTION_VIEW. */
class ViewUriCapability @Inject constructor() : Capability {
  override val id = "intent.view_uri"
  override val label = "Abrir URL"
  override val description = "Ejecuta ACTION_VIEW sobre una URI (web, tel:, mailto:, geo:, etc.)."
  override val category = CapabilityCategory.INTENT
  override val icon = Icons.Outlined.OpenInNew
  override val spriteId = "task_url"
  override val params = listOf(
    ParamSpec("uri", "URI", ValueKind.URI, default = JsonPrimitive("https://")),
  )

  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val uri = config["uri"].asString().orEmpty()
    if (uri.isBlank()) return NodeResult.fail("URI vacía")
    return runCatching {
      val i = Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      ctx.androidContext.startActivity(i)
      NodeResult.ok()
    }.getOrElse { NodeResult.fail(it.message ?: "error intent") }
  }
}

/** Generic Intent dispatcher with action + optional uri + optional package. */
class GenericIntentCapability @Inject constructor() : Capability {
  override val id = "intent.generic"
  override val label = "Intent personalizado"
  override val description = "Envía un Intent arbitrario (action + data + package opcional)."
  override val category = CapabilityCategory.INTENT
  override val icon = Icons.Outlined.Send
  override val spriteId = "task_intent"
  override val params = listOf(
    ParamSpec("action", "Action", ValueKind.STRING,
      default = JsonPrimitive(Intent.ACTION_VIEW)),
    ParamSpec("data", "Data URI", ValueKind.URI, required = false,
      default = JsonPrimitive("")),
    ParamSpec("package", "Package (opcional)", ValueKind.PACKAGE, required = false,
      default = JsonPrimitive("")),
    ParamSpec("extraText", "EXTRA_TEXT", ValueKind.STRING, required = false,
      default = JsonPrimitive("")),
  )

  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val action = config["action"].asString().orEmpty().ifBlank { Intent.ACTION_VIEW }
    val data = config["data"].asString().orEmpty()
    val pkg = config["package"].asString().orEmpty()
    val text = config["extraText"].asString().orEmpty()
    return runCatching {
      val i = Intent(action)
      if (data.isNotBlank()) i.data = data.toUri()
      if (pkg.isNotBlank()) i.setPackage(pkg)
      if (text.isNotBlank()) i.putExtra(Intent.EXTRA_TEXT, text)
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      ctx.androidContext.startActivity(i)
      NodeResult.ok()
    }.getOrElse { NodeResult.fail(it.message ?: "error intent") }
  }
}

/** Opens Wi-Fi settings panel. */
class OpenWifiSettingsCapability @Inject constructor() : Capability {
  override val id = "intent.wifi_settings"
  override val label = "Ajustes Wi-Fi"
  override val description = "Abre la pantalla de ajustes Wi-Fi."
  override val category = CapabilityCategory.INTENT
  override val icon = Icons.Outlined.OpenInNew
  override val spriteId = "task_wifi"

  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val i = Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.androidContext.startActivity(i)
    return NodeResult.ok()
  }
}

