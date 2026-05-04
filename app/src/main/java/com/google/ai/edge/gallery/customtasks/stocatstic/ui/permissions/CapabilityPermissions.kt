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

package com.google.ai.edge.gallery.customtasks.stocatstic.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.Capability
import com.google.ai.edge.gallery.customtasks.stocatstic.reactive.AssistantAccessibilityService
import com.google.ai.edge.gallery.customtasks.stocatstic.reactive.MessagingApps

/**
 * Capability-driven permission helper. A [Capability] declares the runtime permissions it needs
 * in [Capability.requiredPermissions]; some tasks additionally require **special access** screens
 * — the Notification Listener toggle, the Accessibility toggle, or the default-SMS handler —
 * which cannot be granted through the standard runtime permission prompt and must instead be
 * directed to the appropriate system settings activity.
 *
 * This helper transparently handles both cases:
 *
 *   * For plain dangerous permissions, a `RequestMultiplePermissions` contract is launched.
 *   * For special-access permissions, a dialog is shown offering to open the relevant settings.
 *
 * Once the user grants everything, [onGranted] is invoked. If the user declines, [onGranted]
 * is NOT invoked and the caller is expected to abort the "add task" flow.
 */
@Composable
fun rememberCapabilityPermissionRequester(
  onDenied: () -> Unit = {},
  onGranted: (Capability) -> Unit,
): (Capability) -> Unit {
  val context = androidx.compose.ui.platform.LocalContext.current
  var pendingCap by remember { mutableStateOf<Capability?>(null) }
  var specialDialog by remember { mutableStateOf<SpecialAccessRequirement?>(null) }

  val runtimeLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions(),
  ) { grants ->
    val cap = pendingCap ?: return@rememberLauncherForActivityResult
    val allGranted = grants.values.all { it }
    if (!allGranted) {
      pendingCap = null
      onDenied()
      return@rememberLauncherForActivityResult
    }
    // After runtime permissions, continue with special access if any.
    val special = specialAccessFor(cap, context)
    if (special != null) {
      specialDialog = special
    } else {
      pendingCap = null
      onGranted(cap)
    }
  }

  // Dialog for special-access (Notification Listener, Accessibility, default SMS handler).
  specialDialog?.let { req ->
    AlertDialog(
      onDismissRequest = { specialDialog = null; pendingCap = null; onDenied() },
      title = { Text(req.title) },
      text = { Text(req.message) },
      confirmButton = {
        TextButton(onClick = {
          context.startActivity(req.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
          val cap = pendingCap
          specialDialog = null
          pendingCap = null
          if (cap != null) onGranted(cap)   // best-effort: continue; real grant happens later.
        }) { Text("Abrir ajustes") }
      },
      dismissButton = {
        TextButton(onClick = {
          specialDialog = null; pendingCap = null; onDenied()
        }) { Text("Cancelar") }
      },
    )
  }

  return request@{ cap ->
    val runtimePerms = cap.requiredPermissions
      .filter { it !in SPECIAL_ACCESS_PERMISSIONS }
      .filter { !isGranted(context, it) }

    val special = specialAccessFor(cap, context)

    when {
      runtimePerms.isEmpty() && special == null -> onGranted(cap)
      runtimePerms.isNotEmpty() -> {
        pendingCap = cap
        runtimeLauncher.launch(runtimePerms.toTypedArray())
      }
      else -> {
        pendingCap = cap
        specialDialog = special
      }
    }
  }
}

private fun isGranted(ctx: Context, perm: String): Boolean =
  ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

/** Permissions that cannot be requested at runtime — they are granted via settings screens. */
private val SPECIAL_ACCESS_PERMISSIONS = setOf(
  Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
  Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
)

private data class SpecialAccessRequirement(
  val title: String,
  val message: String,
  val intent: Intent,
)

/** Compute the first pending special-access requirement for [cap], if any. */
private fun specialAccessFor(cap: Capability, ctx: Context): SpecialAccessRequirement? {
  // Notification listener — required by every trigger.* / action.reply.* except pure SMS.
  val needsListener = Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE in cap.requiredPermissions
  if (needsListener && !isNotificationListenerEnabled(ctx)) {
    return SpecialAccessRequirement(
      title = "Acceso a notificaciones",
      message = "Para detectar y responder a mensajes de WhatsApp/Telegram/Discord o correo, " +
        "StoCATstic necesita que actives el acceso a notificaciones en Ajustes. Solo se hace una vez.",
      intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
    )
  }
  // Accessibility — required by the action.reply.{whatsapp,telegram,discord} automation fallback.
  val needsAccessibility = cap.id in ACCESSIBILITY_CAPABILITIES
  if (needsAccessibility && !AssistantAccessibilityService.isEnabled(ctx)) {
    return SpecialAccessRequirement(
      title = "Servicio de accesibilidad",
      message = "Para responder automáticamente cuando la notificación haya caducado, " +
        "StoCATstic necesita el servicio de accesibilidad. Se usa solo para enviar los " +
        "mensajes que configures en tus flujos.",
      intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
    )
  }
  return null
}

private val ACCESSIBILITY_CAPABILITIES = setOf(
  "action.reply.whatsapp",
  "action.reply.telegram",
  "action.reply.discord",
)

private fun isNotificationListenerEnabled(ctx: Context): Boolean {
  val enabled = Settings.Secure.getString(
    ctx.contentResolver, "enabled_notification_listeners",
  ).orEmpty()
  val component = ctx.packageName +
    "/com.google.ai.edge.gallery.customtasks.stocatstic.reactive.AssistantNotificationListenerService"
  return enabled.split(':').any { it.equals(component, ignoreCase = true) }
}

