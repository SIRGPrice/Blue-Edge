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

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Vibration
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.Capability
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.CapabilityCategory
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ExecutionContext
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.NodeResult
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ParamSpec
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ValueKind
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.asBool
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.asLong
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.asString
import javax.inject.Inject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.resume

class FlashlightCapability @Inject constructor() : Capability {
  override val id = "device.flashlight"
  override val label = "Linterna"
  override val description = "Enciende o apaga la linterna trasera."
  override val category = CapabilityCategory.DEVICE
  override val icon = Icons.Outlined.FlashlightOn
  override val spriteId = "task_flashlight"
  override val params = listOf(
    ParamSpec("on", "Encender", ValueKind.BOOL, default = kotlinx.serialization.json.JsonPrimitive(true)),
  )

  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val on = config["on"].asBool() ?: true
    return runCatching {
      val cm = ctx.androidContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
      val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it)
        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        ?: return NodeResult.fail("No hay linterna")
      cm.setTorchMode(id, on)
      NodeResult.ok()
    }.getOrElse { NodeResult.fail(it.message ?: "error linterna") }
  }
}

class VibrateCapability @Inject constructor() : Capability {
  override val id = "device.vibrate"
  override val label = "Vibrar"
  override val description = "Hace vibrar el dispositivo."
  override val category = CapabilityCategory.DEVICE
  override val icon = Icons.Outlined.Vibration
  override val spriteId = "task_vibrate"
  override val params = listOf(
    ParamSpec("ms", "Duración (ms)", ValueKind.DURATION_MS,
      default = kotlinx.serialization.json.JsonPrimitive(300L)),
  )

  @Suppress("DEPRECATION")
  @SuppressLint("MissingPermission")
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val ms = (config["ms"].asLong() ?: 300L).coerceAtLeast(1L)
    return runCatching {
      val vib: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (ctx.androidContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
          as VibratorManager).defaultVibrator
      } else {
        @Suppress("DEPRECATION")
        ctx.androidContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
      }
      if (!vib.hasVibrator()) {
        android.util.Log.w("StoCATstic", "Vibrate: device has no vibrator")
        return@runCatching NodeResult.fail("Este dispositivo no tiene vibrador")
      }
      android.util.Log.i("StoCATstic", "Vibrate: ms=$ms")
      val effect = VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val attrs = android.os.VibrationAttributes.Builder()
          .setUsage(android.os.VibrationAttributes.USAGE_TOUCH)
          .build()
        vib.vibrate(effect, attrs)
      } else {
        vib.vibrate(effect)
      }
      NodeResult.ok()
    }.getOrElse {
      android.util.Log.e("StoCATstic", "Vibrate failed", it)
      NodeResult.fail(it.message ?: "error vibración")
    }
  }
}

class NotifyCapability @Inject constructor() : Capability {
  override val id = "notify.push"
  override val label = "Notificar"
  override val description = "Publica una notificación en la barra."
  override val category = CapabilityCategory.NOTIFY
  override val icon = Icons.Outlined.Notifications
  override val spriteId = "task_notify"
  override val requiredPermissions = listOf("android.permission.POST_NOTIFICATIONS")
  override val params = listOf(
    ParamSpec("title", "Título", ValueKind.STRING,
      default = kotlinx.serialization.json.JsonPrimitive("StoCATstic")),
    ParamSpec("body", "Mensaje", ValueKind.STRING,
      default = kotlinx.serialization.json.JsonPrimitive("")),
    ParamSpec("alarm", "Alarma (sonora)", ValueKind.BOOL,
      default = kotlinx.serialization.json.JsonPrimitive(false)),
  )

  @SuppressLint("MissingPermission")
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val title = config["title"].asString() ?: "StoCATstic"
    val body = config["body"].asString() ?: ctx.inputs["in"]?.toString().orEmpty()
    val alarm = config["alarm"].asBool() ?: false
    val channelId = if (alarm) CHAN_ALARM else CHAN_DEFAULT
    val nm = ctx.androidContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (nm.getNotificationChannel(channelId) == null) {
      nm.createNotificationChannel(NotificationChannel(channelId,
        if (alarm) "StoCATstic Alarmas" else "StoCATstic",
        if (alarm) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT))
    }
    val n = NotificationCompat.Builder(ctx.androidContext, channelId)
      .setContentTitle(title)
      .setContentText(body)
      .setSmallIcon(R.drawable.graph_1_24px)
      .setPriority(if (alarm) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
      .setAutoCancel(true)
      .build()
    return runCatching {
      NotificationManagerCompat.from(ctx.androidContext)
        .notify((System.currentTimeMillis() and 0xFFFFFFF).toInt(), n)
      NodeResult.ok()
    }.getOrElse { NodeResult.fail(it.message ?: "error notificación") }
  }

  companion object {
    const val CHAN_DEFAULT = "stocatstic_default"
    const val CHAN_ALARM = "stocatstic_alarm"
  }
}

class TtsCapability @Inject constructor() : Capability {
  override val id = "device.tts"
  override val label = "Hablar"
  override val description = "Sintetiza texto a voz (TTS)."
  override val category = CapabilityCategory.DEVICE
  override val icon = Icons.Outlined.RecordVoiceOver
  override val spriteId = "task_tts"
  override val params = listOf(
    ParamSpec("text", "Texto", ValueKind.STRING, default = kotlinx.serialization.json.JsonPrimitive("")),
  )

  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val text = config["text"].asString().orEmpty().ifBlank { ctx.inputs["in"].asString().orEmpty() }
    if (text.isBlank()) return NodeResult.fail("Texto vacío")
    return suspendCancellableCoroutine { cont ->
      var tts: TextToSpeech? = null
      tts = TextToSpeech(ctx.androidContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
          tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "stocatstic_utt")
          // Shutdown after short delay - naive but good enough for MVP
          Thread {
            Thread.sleep(100L + text.length * 70L)
            tts?.shutdown()
            if (cont.isActive) cont.resume(NodeResult.ok())
          }.start()
        } else {
          if (cont.isActive) cont.resume(NodeResult.fail("TTS init=$status"))
        }
      }
      cont.invokeOnCancellation { tts?.shutdown() }
    }
  }
}

