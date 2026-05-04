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

package com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin

import android.app.RemoteInput
import android.content.Intent
import android.telephony.SmsManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.CallMissed
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ForwardToInbox
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Voicemail
import androidx.core.os.bundleOf
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.Capability
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.CapabilityCategory
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ExecutionContext
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.MatchMode
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.NodeResult
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ParamSpec
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.PortSpec
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ValueKind
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.asString
import com.google.ai.edge.gallery.customtasks.stocatstic.data.DangerousActionKind
import com.google.ai.edge.gallery.customtasks.stocatstic.data.logDangerousAction
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.ReactiveEvent
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.ReactiveEventBus
import com.google.ai.edge.gallery.customtasks.stocatstic.engine.matchesSender
import com.google.ai.edge.gallery.customtasks.stocatstic.reactive.MessagingApps
import com.google.ai.edge.gallery.customtasks.stocatstic.reactive.AutomationController
import com.google.ai.edge.gallery.customtasks.stocatstic.reactive.ReplyActionCache
import javax.inject.Inject
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// =========================================================================================
// Shared helpers
// =========================================================================================

/** Standardised params for every "Esperar ..." task. */
private fun matcherParams(senderLabel: String, help: String = ""): List<ParamSpec> = listOf(
  ParamSpec(
    key = "mode",
    label = "Filtro",
    kind = ValueKind.ENUM,
    default = JsonPrimitive(MatchMode.ANY.name),
    enumValues = MatchMode.entries.map { it.name },
    help = "ANY = cualquiera, ONE = uno concreto, LIST = varios",
  ),
  ParamSpec(
    key = "senders",
    label = senderLabel,
    kind = ValueKind.STRING_LIST,
    required = false,
    default = JsonArray(emptyList()),
    help = help,
  ),
)

/** Reads the [MatchMode] + allowed list out of a node config. */
private fun readMatcher(config: JsonObject): Pair<MatchMode, List<String>> {
  val mode = runCatching {
    MatchMode.valueOf(config["mode"].asString().orEmpty())
  }.getOrDefault(MatchMode.ANY)
  val list = (config["senders"] as? JsonArray).orEmpty().mapNotNull {
    (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
  }
  return mode to list
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

/** Shared output schema: `sender`, `body`, `timestamp`, `attachmentUri`, `out` (= body). */
private fun reactiveOutputs(ev: ReactiveEvent): Map<String, kotlinx.serialization.json.JsonElement> =
  mapOf(
    "sender" to JsonPrimitive(ev.sender),
    "body" to JsonPrimitive(ev.body),
    "timestamp" to JsonPrimitive(ev.timestamp),
    "attachmentUri" to JsonPrimitive(ev.attachmentUri.orEmpty()),
    "packageName" to JsonPrimitive(ev.packageName.orEmpty()),
    "out" to JsonPrimitive(ev.body),
  )

/** Ports produced by every reactive wait capability. */
private val REACTIVE_OUTPUT_PORTS = listOf(
  PortSpec("sender", "remitente", ValueKind.STRING),
  PortSpec("body", "cuerpo", ValueKind.STRING),
  PortSpec("timestamp", "ts", ValueKind.LONG),
  PortSpec("attachmentUri", "adjunto", ValueKind.URI),
  PortSpec("out", "salida", ValueKind.STRING),
)

// =========================================================================================
// 1) Esperar SMS
// =========================================================================================

class WaitSmsCapability @Inject constructor() : Capability {
  override val id = "trigger.sms"
  override val label = "Esperar SMS"
  override val description = "Espera un SMS de cualquier número, uno concreto o varios concretos."
  override val category = CapabilityCategory.TRIGGER
  override val icon = Icons.Outlined.Sms
  override val spriteId = "task_generic"
  override val requiredPermissions = listOf(
    android.Manifest.permission.RECEIVE_SMS,
    android.Manifest.permission.READ_SMS,
  )
  override val params = matcherParams("Números permitidos", "Ej. +34 600 000 000")
  override val outputs = REACTIVE_OUTPUT_PORTS

  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val bus = ReactiveEventBus.shared ?: return NodeResult.fail("Bus reactivo no inicializado")
    val (mode, allowed) = readMatcher(config)
    val ev = bus.events.filterIsInstance<ReactiveEvent.Sms>()
      .first { matchesSender(it.sender, mode, allowed) }
    return NodeResult.ok(reactiveOutputs(ev))
  }
}

// =========================================================================================
// 2-3-5) Esperar WhatsApp / Telegram / Discord (notification-based)
// =========================================================================================

abstract class WaitNotificationCapability(
  private val packages: List<String>,
) : Capability {
  override val category = CapabilityCategory.TRIGGER
  override val requiredPermissions =
    listOf(android.Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
  override val outputs = REACTIVE_OUTPUT_PORTS

  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val bus = ReactiveEventBus.shared ?: return NodeResult.fail("Bus reactivo no inicializado")
    val (mode, allowed) = readMatcher(config)
    val ev = bus.events.filterIsInstance<ReactiveEvent.Notification>()
      .first { e ->
        (e.packageName in packages) && matchesSender(e.sender, mode, allowed)
      }
    return NodeResult.ok(reactiveOutputs(ev))
  }
}

class WaitWhatsAppCapability @Inject constructor()
  : WaitNotificationCapability(MessagingApps.WHATSAPP_PKGS) {
  override val id = "trigger.whatsapp"
  override val label = "Esperar WhatsApp"
  override val description = "Espera un mensaje de WhatsApp de cualquiera, uno o varios concretos."
  override val icon = Icons.Outlined.Chat
  override val params = matcherParams("Remitentes permitidos")
}

class WaitTelegramCapability @Inject constructor()
  : WaitNotificationCapability(MessagingApps.TELEGRAM_PKGS) {
  override val id = "trigger.telegram"
  override val label = "Esperar Telegram"
  override val description = "Espera un mensaje de Telegram de cualquiera, uno o varios concretos."
  override val icon = Icons.Outlined.Send
  override val params = matcherParams("Remitentes permitidos")
}

class WaitDiscordCapability @Inject constructor()
  : WaitNotificationCapability(MessagingApps.DISCORD_PKGS) {
  override val id = "trigger.discord"
  override val label = "Esperar Discord"
  override val description = "Espera un mensaje de Discord de cualquiera, una o varias personas."
  override val icon = Icons.Outlined.Message
  override val params = matcherParams("Usuarios permitidos")
}

// =========================================================================================
// 4) Esperar correo (Gmail / Outlook / Thunderbird / Yahoo!)
// =========================================================================================

class WaitEmailCapability @Inject constructor() : Capability {
  override val id = "trigger.email"
  override val label = "Esperar correo"
  override val description =
    "Espera un correo de cualquiera, uno o varios concretos en Gmail, Outlook, Thunderbird o Yahoo!."
  override val category = CapabilityCategory.TRIGGER
  override val icon = Icons.Outlined.Email
  override val requiredPermissions =
    listOf(android.Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
  override val params = listOf(
    ParamSpec(
      key = "providers",
      label = "Clientes de correo",
      kind = ValueKind.STRING_LIST,
      required = true,
      default = JsonArray(MessagingApps.EMAIL_PKGS.map { JsonPrimitive(it) }),
      help = "Paquetes aceptados (Gmail, Outlook, Yahoo!, Thunderbird).",
    ),
  ) + matcherParams("Remitentes permitidos", "Direcciones o nombres")
  override val outputs = REACTIVE_OUTPUT_PORTS

  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val bus = ReactiveEventBus.shared ?: return NodeResult.fail("Bus reactivo no inicializado")
    val providers = (config["providers"] as? JsonArray).orEmpty()
      .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
      .ifEmpty { MessagingApps.EMAIL_PKGS }
    val (mode, allowed) = readMatcher(config)
    val ev = bus.events.filterIsInstance<ReactiveEvent.Notification>()
      .first { e -> (e.packageName in providers) && matchesSender(e.sender, mode, allowed) }
    return NodeResult.ok(reactiveOutputs(ev))
  }
}

// =========================================================================================
// 6) Responder SMS (directo vía SmsManager)
// =========================================================================================

class ReplySmsCapability @Inject constructor() : Capability {
  override val id = "action.reply.sms"
  override val label = "Responder SMS"
  override val description =
    "Envía un SMS al remitente detectado por el nodo anterior (o al número indicado)."
  override val category = CapabilityCategory.INTENT
  override val icon = Icons.Outlined.Reply
  override val requiredPermissions = listOf(android.Manifest.permission.SEND_SMS)
  override val params = listOf(
    ParamSpec("to", "Destinatario (vacío = remitente entrante)", ValueKind.STRING,
      required = false, default = JsonPrimitive("")),
    ParamSpec("body", "Cuerpo (vacío = salida del nodo anterior)", ValueKind.STRING,
      required = false, default = JsonPrimitive("")),
  )

  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val to = config["to"].asString().orEmpty().ifBlank { ctx.inputs["sender"].asString().orEmpty() }
      .ifBlank { ctx.inputs["in"].asString().orEmpty() }
    val body = config["body"].asString().orEmpty().ifBlank { ctx.inputs["in"].asString().orEmpty() }
    if (to.isBlank()) return NodeResult.fail("Sin destinatario")
    if (body.isBlank()) return NodeResult.fail("Sin cuerpo")
    return runCatching {
      @Suppress("DEPRECATION")
      val sm = SmsManager.getDefault()
      val parts = sm.divideMessage(body)
      sm.sendMultipartTextMessage(to, null, parts, null, null)
      logDangerousAction(DangerousActionKind.SMS, to, body, success = true)
      NodeResult.ok(mapOf("out" to JsonPrimitive(body)))
    }.getOrElse {
      logDangerousAction(DangerousActionKind.SMS, to, body, success = false, message = it.message.orEmpty())
      NodeResult.fail(it.message ?: "error SMS")
    }
  }
}

// =========================================================================================
// 7-8-9-10) Responder WhatsApp / Telegram / Discord / Email (RemoteInput cache)
// =========================================================================================

abstract class ReplyViaNotificationCapability(
  private val packages: List<String>,
  private val kind: DangerousActionKind,
  private val emailFallback: Boolean = false,
) : Capability {
  override val category = CapabilityCategory.INTENT
  override val requiredPermissions =
    listOf(android.Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
  override val params = listOf(
    ParamSpec("body", "Cuerpo (vacío = salida anterior)", ValueKind.STRING,
      required = false, default = JsonPrimitive("")),
  )

  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val body = config["body"].asString().orEmpty().ifBlank { ctx.inputs["in"].asString().orEmpty() }
    if (body.isBlank()) return NodeResult.fail("Sin cuerpo")
    val convKey = ctx.inputs["conversationKey"].asString().orEmpty()
    val sender = ctx.inputs["sender"].asString().orEmpty()
    val entry = (if (convKey.isNotBlank()) ReplyActionCache.get(convKey) else null)
      ?: packages.firstNotNullOfOrNull {
        ReplyActionCache.findLatestFor(it, sender.takeIf { s -> s.isNotBlank() })
      }
      ?: packages.firstNotNullOfOrNull { ReplyActionCache.findLatestFor(it, null) }

    // Preferred path: reuse the live notification's RemoteInput (no app launch).
    if (entry != null) {
      return runCatching {
        val intent = Intent()
        val bundle = bundleOf(entry.remoteInput.resultKey to body)
        RemoteInput.addResultsToIntent(arrayOf(entry.remoteInput), intent, bundle)
        entry.pendingIntent.send(ctx.androidContext, 0, intent)
        logDangerousAction(kind, sender, body, success = true, message = "via RemoteInput")
        NodeResult.ok(mapOf("out" to JsonPrimitive(body)))
      }.getOrElse {
        logDangerousAction(kind, sender, body, success = false, message = it.message.orEmpty())
        NodeResult.fail(it.message ?: "error al responder")
      }
    }

    // Fallback A (email only): native compose intent.
    if (emailFallback && sender.isNotBlank()) {
      val i = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:$sender"))
        .putExtra(Intent.EXTRA_TEXT, body)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      ctx.androidContext.startActivity(i)
      logDangerousAction(kind, sender, body, success = true, message = "mailto: fallback")
      return NodeResult.ok(mapOf("out" to JsonPrimitive(body)))
    }

    // Fallback B: Accessibility-driven automation (open the app and click send).
    val targetPackage = packages.first()
    val ok = runCatching {
      AutomationController.sendMessage(ctx.androidContext, targetPackage, sender, body)
    }.getOrDefault(false)
    logDangerousAction(kind, sender, body, success = ok,
      message = if (ok) "accessibility fallback" else "accessibility fallback failed")
    return if (ok) NodeResult.ok(mapOf("out" to JsonPrimitive(body)))
      else NodeResult.fail(
        "No hay respuesta rápida activa y no se pudo automatizar la app. " +
          "Activa el servicio de accesibilidad de StoCATstic en Ajustes.",
      )
  }
}

class ReplyWhatsAppCapability @Inject constructor()
  : ReplyViaNotificationCapability(MessagingApps.WHATSAPP_PKGS, DangerousActionKind.WHATSAPP) {
  override val id = "action.reply.whatsapp"
  override val label = "Responder WhatsApp"
  override val description =
    "Responde a WhatsApp. Prefiere la respuesta rápida de la notificación; " +
      "si no está disponible, abre la app y automatiza el envío (requiere servicio de accesibilidad)."
  override val icon = Icons.Outlined.Reply
}

class ReplyTelegramCapability @Inject constructor()
  : ReplyViaNotificationCapability(MessagingApps.TELEGRAM_PKGS, DangerousActionKind.TELEGRAM) {
  override val id = "action.reply.telegram"
  override val label = "Responder Telegram"
  override val description =
    "Responde a Telegram. Prefiere la respuesta rápida de la notificación; " +
      "si no, abre la app y automatiza el envío (requiere accesibilidad)."
  override val icon = Icons.Outlined.Reply
}

class ReplyDiscordCapability @Inject constructor()
  : ReplyViaNotificationCapability(MessagingApps.DISCORD_PKGS, DangerousActionKind.DISCORD) {
  override val id = "action.reply.discord"
  override val label = "Responder Discord"
  override val description =
    "Responde a Discord. Prefiere la respuesta rápida de la notificación; " +
      "si no, abre la app y automatiza el envío (requiere accesibilidad)."
  override val icon = Icons.Outlined.Reply
}

class ReplyEmailCapability @Inject constructor()
  : ReplyViaNotificationCapability(MessagingApps.EMAIL_PKGS, DangerousActionKind.EMAIL, emailFallback = true) {
  override val id = "action.reply.email"
  override val label = "Responder correo"
  override val description =
    "Responde al último correo usando la acción de la notificación (Gmail/Outlook/Yahoo!/Thunderbird). " +
      "Si la notificación caducó, abre un intent mailto: como alternativa."
  override val icon = Icons.Outlined.ForwardToInbox
}

// =========================================================================================
// 11) Esperar llamada perdida + voicemail
// =========================================================================================

class WaitMissedCallCapability @Inject constructor() : Capability {
  override val id = "trigger.missed_call"
  override val label = "Esperar llamada perdida"
  override val description =
    "Espera una llamada perdida (opcionalmente solo cuando la pantalla está apagada). " +
      "Si hay buzón de voz adjunto, emite su URI para que un nodo de IA lo procese."
  override val category = CapabilityCategory.TRIGGER
  override val icon = Icons.Outlined.CallMissed
  override val spriteId = "task_generic"
  override val requiredPermissions = listOf(
    android.Manifest.permission.READ_CALL_LOG,
    android.Manifest.permission.READ_PHONE_STATE,
  )
  override val params = matcherParams("Números permitidos") + listOf(
    ParamSpec(
      key = "onlyWhenScreenOff",
      label = "Solo si el usuario no estaba mirando",
      kind = ValueKind.BOOL,
      default = JsonPrimitive(true),
      help = "Si está activado, ignora llamadas perdidas con la pantalla encendida.",
    ),
  )
  override val outputs = REACTIVE_OUTPUT_PORTS + PortSpec("voicemailUri", "buzón", ValueKind.AUDIO_REF)

  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val bus = ReactiveEventBus.shared ?: return NodeResult.fail("Bus reactivo no inicializado")
    val (mode, allowed) = readMatcher(config)
    val onlyOff = (config["onlyWhenScreenOff"] as? JsonPrimitive)?.contentOrNull
      ?.toBooleanStrictOrNull() ?: true
    val ev = bus.events.filterIsInstance<ReactiveEvent.CallMissed>()
      .first { e ->
        matchesSender(e.sender, mode, allowed) && (!onlyOff || e.wasScreenOff)
      }
    return NodeResult.ok(
      reactiveOutputs(ev) + ("voicemailUri" to JsonPrimitive(ev.attachmentUri.orEmpty())),
    )
  }
}

