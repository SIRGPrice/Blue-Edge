/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
@file:Suppress("DEPRECATION")

package com.google.ai.edge.gallery.customtasks.stocatstic.capabilities.builtin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Abc
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Calculate as CalcIcon
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Http
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.core.net.toUri
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.Capability
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.CapabilityCategory
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ExecutionContext
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.NodeResult
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ParamSpec
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.ValueKind
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.asBool
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.asDouble
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.asLong
import com.google.ai.edge.gallery.customtasks.stocatstic.domain.asString
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * ================================================================================================
 *  SYSTEM / device helpers
 * ================================================================================================
 */

class ClipboardCopyCapability @Inject constructor() : Capability {
  override val id = "system.clipboard_copy"
  override val label = "Copiar al portapapeles"
  override val description = "Guarda un texto en el portapapeles del sistema."
  override val category = CapabilityCategory.DEVICE
  override val icon = Icons.Outlined.ContentCopy
  override val params = listOf(
    ParamSpec("text", "Texto", ValueKind.STRING, default = JsonPrimitive("")),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val text = config["text"].asString().orEmpty().ifBlank { ctx.inputs["in"].asString().orEmpty() }
    val cm = ctx.androidContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("StoCATstic", text))
    return NodeResult.ok(mapOf("out" to JsonPrimitive(text)))
  }
}

class ClipboardReadCapability @Inject constructor() : Capability {
  override val id = "system.clipboard_read"
  override val label = "Leer portapapeles"
  override val description = "Devuelve el texto actual del portapapeles como salida."
  override val category = CapabilityCategory.DEVICE
  override val icon = Icons.Outlined.ContentCopy
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val cm = ctx.androidContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx.androidContext)?.toString().orEmpty()
    return NodeResult.ok(mapOf("out" to JsonPrimitive(text)))
  }
}

class ToastCapability @Inject constructor() : Capability {
  override val id = "device.toast"
  override val label = "Mostrar toast"
  override val description = "Muestra un mensaje flotante en la parte inferior de la pantalla."
  override val category = CapabilityCategory.DEVICE
  override val icon = Icons.Outlined.TextFields
  override val params = listOf(
    ParamSpec("text", "Texto", ValueKind.STRING, default = JsonPrimitive("")),
    ParamSpec("long", "Duración larga", ValueKind.BOOL, default = JsonPrimitive(false)),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val text = config["text"].asString().orEmpty().ifBlank { ctx.inputs["in"].asString().orEmpty() }
    val long = config["long"].asBool() ?: false
    withContext(Dispatchers.Main) {
      Toast.makeText(ctx.androidContext, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
    return NodeResult.ok()
  }
}


class RingerModeCapability @Inject constructor() : Capability {
  override val id = "device.ringer_mode"
  override val label = "Modo de timbre"
  override val description = "Cambia el modo del teléfono (normal/vibración/silencio)."
  override val category = CapabilityCategory.DEVICE
  override val icon = Icons.Outlined.VolumeUp
  override val params = listOf(
    ParamSpec("mode", "Modo", ValueKind.ENUM,
      enumValues = listOf("normal", "vibration", "silent"),
      default = JsonPrimitive("normal")),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val mode = config["mode"].asString().orEmpty()
    val am = ctx.androidContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    am.ringerMode = when (mode) {
      "vibration" -> AudioManager.RINGER_MODE_VIBRATE
      "silent" -> AudioManager.RINGER_MODE_SILENT
      else -> AudioManager.RINGER_MODE_NORMAL
    }
    return NodeResult.ok()
  }
}

class BatteryReadCapability @Inject constructor() : Capability {
  override val id = "device.battery"
  override val label = "Leer batería"
  override val description = "Devuelve el porcentaje de batería y si está cargando."
  override val category = CapabilityCategory.DEVICE
  override val icon = Icons.Outlined.BatteryFull
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val bm = ctx.androidContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val lvl = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    val charging = bm.isCharging
    return NodeResult.ok(mapOf(
      "out" to JsonPrimitive(lvl.toString()),
      "charging" to JsonPrimitive(charging),
    ))
  }
}

/*
 * ================================================================================================
 *  SETTINGS quick-access intents (Android's official "open settings" APIs)
 * ================================================================================================
 */
abstract class SettingsIntentCapability protected constructor(
  private val action: String,
) : Capability {
  override val category = CapabilityCategory.INTENT
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    return runCatching {
      ctx.androidContext.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      NodeResult.ok()
    }.getOrElse { NodeResult.fail(it.message ?: "error ajustes") }
  }
}

class OpenBluetoothSettingsCapability @Inject constructor() :
  SettingsIntentCapability(Settings.ACTION_BLUETOOTH_SETTINGS) {
  override val id = "intent.bluetooth_settings"
  override val label = "Ajustes Bluetooth"
  override val description = "Abre la pantalla de ajustes Bluetooth."
  override val icon = Icons.Outlined.Bluetooth
}

class OpenLocationSettingsCapability @Inject constructor() :
  SettingsIntentCapability(Settings.ACTION_LOCATION_SOURCE_SETTINGS) {
  override val id = "intent.location_settings"
  override val label = "Ajustes Ubicación"
  override val description = "Abre los ajustes de localización."
  override val icon = Icons.Outlined.LocationOn
}

class OpenAirplaneModeSettingsCapability @Inject constructor() :
  SettingsIntentCapability(Settings.ACTION_AIRPLANE_MODE_SETTINGS) {
  override val id = "intent.airplane_settings"
  override val label = "Modo avión"
  override val description = "Abre los ajustes de modo avión."
  override val icon = Icons.Outlined.PowerSettingsNew
}

class OpenSoundSettingsCapability @Inject constructor() :
  SettingsIntentCapability(Settings.ACTION_SOUND_SETTINGS) {
  override val id = "intent.sound_settings"
  override val label = "Ajustes de sonido"
  override val description = "Abre los ajustes de sonido del dispositivo."
  override val icon = Icons.Outlined.VolumeUp
}

class OpenDisplaySettingsCapability @Inject constructor() :
  SettingsIntentCapability(Settings.ACTION_DISPLAY_SETTINGS) {
  override val id = "intent.display_settings"
  override val label = "Ajustes pantalla"
  override val description = "Abre los ajustes de pantalla (brillo, tamaño…)."
  override val icon = Icons.Outlined.Brightness6
}

/*
 * ================================================================================================
 *  MEDIA / camera intents
 * ================================================================================================
 */
class LaunchCameraCapability @Inject constructor() : Capability {
  override val id = "media.camera"
  override val label = "Abrir cámara"
  override val description = "Lanza la app de cámara."
  override val category = CapabilityCategory.INTENT
  override val icon = Icons.Outlined.PhotoCamera
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { ctx.androidContext.startActivity(i); NodeResult.ok() }
      .getOrElse { NodeResult.fail(it.message ?: "sin cámara") }
  }
}


/*
 * ================================================================================================
 *  COMMUNICATION (dialer / SMS / email / share / maps)
 * ================================================================================================
 */
class DialCapability @Inject constructor() : Capability {
  override val id = "comm.dial"
  override val label = "Marcar teléfono"
  override val description = "Abre el marcador con un número (no llama automáticamente)."
  override val category = CapabilityCategory.INTENT
  override val icon = Icons.Outlined.Call
  override val params = listOf(
    ParamSpec("number", "Número", ValueKind.STRING, default = JsonPrimitive("")),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val n = config["number"].asString().orEmpty()
    if (n.isBlank()) return NodeResult.fail("Número vacío")
    val i = Intent(Intent.ACTION_DIAL, "tel:$n".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.androidContext.startActivity(i)
    return NodeResult.ok()
  }
}

class SmsComposeCapability @Inject constructor() : Capability {
  override val id = "comm.sms"
  override val label = "Preparar SMS"
  override val description = "Abre la app de SMS con destinatario y cuerpo precargados."
  override val category = CapabilityCategory.INTENT
  override val icon = Icons.Outlined.Sms
  override val params = listOf(
    ParamSpec("to", "Para", ValueKind.STRING, default = JsonPrimitive("")),
    ParamSpec("body", "Mensaje", ValueKind.STRING, default = JsonPrimitive("")),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val to = config["to"].asString().orEmpty()
    val body = config["body"].asString().orEmpty()
      .ifBlank { ctx.inputs["in"].asString().orEmpty() }
    val i = Intent(Intent.ACTION_VIEW, "smsto:$to".toUri())
      .putExtra("sms_body", body)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.androidContext.startActivity(i)
    return NodeResult.ok()
  }
}

class EmailComposeCapability @Inject constructor() : Capability {
  override val id = "comm.email"
  override val label = "Preparar correo"
  override val description = "Abre el cliente de correo con destinatario, asunto y cuerpo."
  override val category = CapabilityCategory.INTENT
  override val icon = Icons.Outlined.Email
  override val params = listOf(
    ParamSpec("to", "Para", ValueKind.STRING, default = JsonPrimitive("")),
    ParamSpec("subject", "Asunto", ValueKind.STRING, default = JsonPrimitive("")),
    ParamSpec("body", "Cuerpo", ValueKind.STRING, default = JsonPrimitive("")),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val to = config["to"].asString().orEmpty()
    val subject = config["subject"].asString().orEmpty()
    val body = config["body"].asString().orEmpty()
      .ifBlank { ctx.inputs["in"].asString().orEmpty() }
    val i = Intent(Intent.ACTION_SENDTO, "mailto:$to".toUri())
      .putExtra(Intent.EXTRA_SUBJECT, subject)
      .putExtra(Intent.EXTRA_TEXT, body)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.androidContext.startActivity(i)
    return NodeResult.ok()
  }
}

class ShareTextCapability @Inject constructor() : Capability {
  override val id = "comm.share"
  override val label = "Compartir texto"
  override val description = "Abre el selector nativo para compartir un texto."
  override val category = CapabilityCategory.INTENT
  override val icon = Icons.Outlined.Share
  override val params = listOf(
    ParamSpec("text", "Texto", ValueKind.STRING, default = JsonPrimitive("")),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val t = config["text"].asString().orEmpty().ifBlank { ctx.inputs["in"].asString().orEmpty() }
    val i = Intent(Intent.ACTION_SEND).setType("text/plain")
      .putExtra(Intent.EXTRA_TEXT, t)
    val chooser = Intent.createChooser(i, "Compartir").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.androidContext.startActivity(chooser)
    return NodeResult.ok()
  }
}

class MapsSearchCapability @Inject constructor() : Capability {
  override val id = "comm.maps"
  override val label = "Buscar en mapas"
  override val description = "Abre Google Maps con una búsqueda por texto o coordenadas."
  override val category = CapabilityCategory.INTENT
  override val icon = Icons.Outlined.Map
  override val params = listOf(
    ParamSpec("query", "Consulta", ValueKind.STRING, default = JsonPrimitive("")),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val q = config["query"].asString().orEmpty().ifBlank { ctx.inputs["in"].asString().orEmpty() }
    if (q.isBlank()) return NodeResult.fail("Consulta vacía")
    val i = Intent(Intent.ACTION_VIEW, "geo:0,0?q=${java.net.URLEncoder.encode(q, "UTF-8")}".toUri())
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.androidContext.startActivity(i)
    return NodeResult.ok()
  }
}

class WebSearchCapability @Inject constructor() : Capability {
  override val id = "comm.web_search"
  override val label = "Buscar en la web"
  override val description = "Abre el navegador con una búsqueda en Google."
  override val category = CapabilityCategory.INTENT
  override val icon = Icons.Outlined.Language
  override val params = listOf(
    ParamSpec("query", "Consulta", ValueKind.STRING, default = JsonPrimitive("")),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val q = config["query"].asString().orEmpty().ifBlank { ctx.inputs["in"].asString().orEmpty() }
    if (q.isBlank()) return NodeResult.fail("Consulta vacía")
    val url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(q, "UTF-8")
    val i = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.androidContext.startActivity(i)
    return NodeResult.ok()
  }
}

/*
 * ================================================================================================
 *  CALENDAR / ALARM
 * ================================================================================================
 */
class CalendarAddEventCapability @Inject constructor() : Capability {
  override val id = "calendar.add"
  override val label = "Añadir evento"
  override val description = "Abre la app de calendario con un nuevo evento precargado."
  override val category = CapabilityCategory.INTENT
  override val icon = Icons.Outlined.CalendarMonth
  override val params = listOf(
    ParamSpec("title", "Título", ValueKind.STRING, default = JsonPrimitive("")),
    ParamSpec("description", "Descripción", ValueKind.STRING, required = false, default = JsonPrimitive("")),
    ParamSpec("durationMinutes", "Duración (min)", ValueKind.INT, required = false,
      default = JsonPrimitive(30L)),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val title = config["title"].asString().orEmpty().ifBlank { ctx.inputs["in"].asString().orEmpty() }
    val desc = config["description"].asString().orEmpty()
    val dur = (config["durationMinutes"].asLong() ?: 30L).coerceAtLeast(5L)
    val now = System.currentTimeMillis()
    val i = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
      .putExtra(CalendarContract.Events.TITLE, title)
      .putExtra(CalendarContract.Events.DESCRIPTION, desc)
      .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, now)
      .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, now + dur * 60_000L)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.androidContext.startActivity(i)
    return NodeResult.ok()
  }
}

class SetAlarmCapability @Inject constructor() : Capability {
  override val id = "clock.alarm"
  override val label = "Poner alarma"
  override val description = "Crea una alarma en la app de reloj a una hora concreta."
  override val category = CapabilityCategory.INTENT
  override val icon = Icons.Outlined.Alarm
  override val params = listOf(
    ParamSpec("hour", "Hora", ValueKind.INT, default = JsonPrimitive(8L)),
    ParamSpec("minute", "Minuto", ValueKind.INT, default = JsonPrimitive(0L)),
    ParamSpec("message", "Etiqueta", ValueKind.STRING, required = false, default = JsonPrimitive("")),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val h = (config["hour"].asLong() ?: 8L).toInt().coerceIn(0, 23)
    val m = (config["minute"].asLong() ?: 0L).toInt().coerceIn(0, 59)
    val msg = config["message"].asString().orEmpty()
    val i = Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
      .putExtra(android.provider.AlarmClock.EXTRA_HOUR, h)
      .putExtra(android.provider.AlarmClock.EXTRA_MINUTES, m)
      .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, msg)
      .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.androidContext.startActivity(i)
    return NodeResult.ok()
  }
}

class SetTimerCapability @Inject constructor() : Capability {
  override val id = "clock.timer"
  override val label = "Iniciar temporizador"
  override val description = "Arranca un temporizador del sistema de N segundos."
  override val category = CapabilityCategory.INTENT
  override val icon = Icons.Outlined.Timer
  override val params = listOf(
    ParamSpec("seconds", "Segundos", ValueKind.INT, default = JsonPrimitive(60L)),
    ParamSpec("message", "Etiqueta", ValueKind.STRING, required = false, default = JsonPrimitive("")),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val s = (config["seconds"].asLong() ?: 60L).toInt().coerceAtLeast(1)
    val i = Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
      .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, s)
      .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, config["message"].asString().orEmpty())
      .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.androidContext.startActivity(i)
    return NodeResult.ok()
  }
}

/*
 * ================================================================================================
 *  TEXT helpers
 * ================================================================================================
 */
class TextFormatCapability @Inject constructor() : Capability {
  override val id = "text.format"
  override val label = "Formatear texto"
  override val description = "Sustituye {input} y {var.nombre} en una plantilla."
  override val category = CapabilityCategory.CONTROL
  override val icon = Icons.Outlined.Abc
  override val params = listOf(
    ParamSpec("template", "Plantilla", ValueKind.STRING, default = JsonPrimitive("Hola {input}")),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val tpl = config["template"].asString().orEmpty()
    var out = tpl.replace("{input}", ctx.inputs["in"].asString().orEmpty())
    Regex("""\{var\.(\w+)}""").findAll(tpl).forEach { m ->
      out = out.replace(m.value, ctx.variables[m.groupValues[1]].asString().orEmpty())
    }
    return NodeResult.ok(mapOf("out" to JsonPrimitive(out)))
  }
}

/*
 * ================================================================================================
 *  MATH / random
 * ================================================================================================
 */
class MathCapability @Inject constructor() : Capability {
  override val id = "math.eval"
  override val label = "Calcular"
  override val description = "Evalúa una expresión aritmética simple (+,-,*,/, paréntesis)."
  override val category = CapabilityCategory.CONTROL
  override val icon = Icons.Outlined.Calculate
  override val params = listOf(
    ParamSpec("expr", "Expresión", ValueKind.EXPRESSION, default = JsonPrimitive("1+1")),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val expr = config["expr"].asString().orEmpty()
      .replace("{input}", ctx.inputs["in"].asString().orEmpty())
    val v = runCatching { evalSimpleArith(expr) }.getOrNull()
      ?: return NodeResult.fail("Expresión inválida")
    return NodeResult.ok(mapOf("out" to JsonPrimitive(v)))
  }
}

class CompareCapability @Inject constructor() : Capability {
  override val id = "logic.compare"
  override val label = "Comparar números"
  override val description = "Compara el input con un número y decide la rama."
  override val category = CapabilityCategory.CONTROL
  override val icon = Icons.Outlined.Percent
  override val outputs = listOf(
    com.google.ai.edge.gallery.customtasks.stocatstic.domain.PortSpec("true", "true"),
    com.google.ai.edge.gallery.customtasks.stocatstic.domain.PortSpec("false", "false"),
  )
  override val params = listOf(
    ParamSpec("op", "Operador", ValueKind.ENUM,
      enumValues = listOf("==", "!=", "<", "<=", ">", ">="),
      default = JsonPrimitive(">")),
    ParamSpec("value", "Valor", ValueKind.DOUBLE, default = JsonPrimitive(0.0)),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val a = ctx.inputs["in"].asString()?.toDoubleOrNull() ?: 0.0
    val b = config["value"].asDouble() ?: 0.0
    val r = when (config["op"].asString()) {
      "==" -> a == b; "!=" -> a != b
      "<" -> a < b; "<=" -> a <= b
      ">=" -> a >= b
      else -> a > b
    }
    return NodeResult.ok(mapOf("branch" to JsonPrimitive(r)))
  }
}

class RandomNumberCapability @Inject constructor() : Capability {
  override val id = "random.number"
  override val label = "Número aleatorio"
  override val description = "Genera un entero aleatorio entre min y max (inclusivos)."
  override val category = CapabilityCategory.CONTROL
  override val icon = Icons.Outlined.Casino
  override val params = listOf(
    ParamSpec("min", "Mínimo", ValueKind.INT, default = JsonPrimitive(0L)),
    ParamSpec("max", "Máximo", ValueKind.INT, default = JsonPrimitive(100L)),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val lo = (config["min"].asLong() ?: 0L)
    val hi = (config["max"].asLong() ?: 100L)
    val (a, b) = if (lo <= hi) lo to hi else hi to lo
    val v = (a..b).random()
    return NodeResult.ok(mapOf("out" to JsonPrimitive(v.toString())))
  }
}

class RandomChoiceCapability @Inject constructor() : Capability {
  override val id = "random.choice"
  override val label = "Elegir al azar"
  override val description = "Devuelve un elemento al azar de una lista separada por '|'."
  override val category = CapabilityCategory.CONTROL
  override val icon = Icons.Outlined.Casino
  override val params = listOf(
    ParamSpec("choices", "Opciones (a|b|c)", ValueKind.STRING, default = JsonPrimitive("sí|no")),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val list = config["choices"].asString().orEmpty().split("|").filter { it.isNotBlank() }
    val pick = if (list.isEmpty()) "" else list.random()
    return NodeResult.ok(mapOf("out" to JsonPrimitive(pick)))
  }
}

class CoinFlipCapability @Inject constructor() : Capability {
  override val id = "random.coin"
  override val label = "Cara o cruz"
  override val description = "50/50, emite rama true/false como un condicional."
  override val category = CapabilityCategory.CONTROL
  override val icon = Icons.Outlined.Casino
  override val outputs = listOf(
    com.google.ai.edge.gallery.customtasks.stocatstic.domain.PortSpec("true", "cara"),
    com.google.ai.edge.gallery.customtasks.stocatstic.domain.PortSpec("false", "cruz"),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val r = (0..1).random() == 1
    return NodeResult.ok(mapOf("branch" to JsonPrimitive(r)))
  }
}

/*
 * ================================================================================================
 *  TIME / DATE
 * ================================================================================================
 */
class CurrentTimeCapability @Inject constructor() : Capability {
  override val id = "time.now"
  override val label = "Hora actual"
  override val description = "Emite la hora actual formateada (por defecto HH:mm:ss)."
  override val category = CapabilityCategory.CONTROL
  override val icon = Icons.Outlined.Schedule
  override val params = listOf(
    ParamSpec("format", "Formato", ValueKind.STRING, default = JsonPrimitive("HH:mm:ss")),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val fmt = config["format"].asString().orEmpty().ifBlank { "HH:mm:ss" }
    val s = runCatching { SimpleDateFormat(fmt, Locale.getDefault()).format(Date()) }
      .getOrElse { Date().toString() }
    return NodeResult.ok(mapOf("out" to JsonPrimitive(s)))
  }
}

class WeekdayCapability @Inject constructor() : Capability {
  override val id = "time.weekday"
  override val label = "Día de la semana"
  override val description = "Emite el día de la semana (lunes..domingo)."
  override val category = CapabilityCategory.CONTROL
  override val icon = Icons.Outlined.CalendarMonth
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val s = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
    return NodeResult.ok(mapOf("out" to JsonPrimitive(s)))
  }
}

/*
 * ================================================================================================
 *  NETWORK
 * ================================================================================================
 */
class HttpGetCapability @Inject constructor() : Capability {
  override val id = "net.http_get"
  override val label = "HTTP GET"
  override val description = "Descarga la respuesta de una URL (texto, hasta 64KB)."
  override val category = CapabilityCategory.AI
  override val icon = Icons.Outlined.Http
  override val params = listOf(
    ParamSpec("url", "URL", ValueKind.URI, default = JsonPrimitive("https://")),
    ParamSpec("timeoutMs", "Timeout (ms)", ValueKind.DURATION_MS,
      required = false, default = JsonPrimitive(8000L)),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val url = config["url"].asString().orEmpty()
    if (url.isBlank()) return NodeResult.fail("URL vacía")
    val timeout = (config["timeoutMs"].asLong() ?: 8000L).coerceIn(1000L, 60000L)
    val body = withTimeoutOrNull(timeout) {
      withContext(Dispatchers.IO) {
        runCatching {
          val c = URL(url).openConnection() as HttpURLConnection
          c.connectTimeout = timeout.toInt(); c.readTimeout = timeout.toInt()
          c.inputStream.bufferedReader().use { it.readText().take(64 * 1024) }
        }.getOrElse { return@withContext "error: ${it.message}" }
      }
    } ?: return NodeResult.fail("Timeout")
    return NodeResult.ok(mapOf("out" to JsonPrimitive(body)))
  }
}

class PingCapability @Inject constructor() : Capability {
  override val id = "net.ping"
  override val label = "Ping"
  override val description = "Comprueba si un host responde HTTP (devuelve código o 0)."
  override val category = CapabilityCategory.AI
  override val icon = Icons.Outlined.NetworkCheck
  override val outputs = listOf(
    com.google.ai.edge.gallery.customtasks.stocatstic.domain.PortSpec("true", "ok"),
    com.google.ai.edge.gallery.customtasks.stocatstic.domain.PortSpec("false", "ko"),
  )
  override val params = listOf(
    ParamSpec("url", "URL", ValueKind.URI, default = JsonPrimitive("https://www.google.com")),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val url = config["url"].asString().orEmpty()
    val code = withContext(Dispatchers.IO) {
      runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5000; c.readTimeout = 5000
        c.requestMethod = "HEAD"
        c.responseCode
      }.getOrDefault(0)
    }
    val ok = code in 200..399
    return NodeResult.ok(mapOf("branch" to JsonPrimitive(ok), "out" to JsonPrimitive(code.toString())))
  }
}

/*
 * ================================================================================================
 *  FLOW CONTROL extras
 * ================================================================================================
 */

class CounterIncrementCapability @Inject constructor() : Capability {
  override val id = "control.counter"
  override val label = "Contador"
  override val description = "Incrementa una variable numérica del flujo."
  override val category = CapabilityCategory.CONTROL
  override val icon = Icons.Outlined.Functions
  override val params = listOf(
    ParamSpec("name", "Nombre", ValueKind.STRING, default = JsonPrimitive("count")),
    ParamSpec("step", "Paso", ValueKind.INT, default = JsonPrimitive(1L)),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val name = config["name"].asString().orEmpty().ifBlank { "count" }
    val step = (config["step"].asLong() ?: 1L)
    val cur = ctx.variables[name].asString()?.toLongOrNull() ?: 0L
    val next = cur + step
    ctx.variables[name] = JsonPrimitive(next)
    return NodeResult.ok(mapOf("out" to JsonPrimitive(next.toString())))
  }
}

class NoopCapability @Inject constructor() : Capability {
  override val id = "control.passthrough"
  override val label = "Pasar input"
  override val description = "No hace nada; reemite el input tal cual."
  override val category = CapabilityCategory.CONTROL
  override val icon = Icons.Outlined.Loop
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    val s = ctx.inputs["in"].asString().orEmpty()
    return NodeResult.ok(mapOf("out" to JsonPrimitive(s)))
  }
}

class FailCapability @Inject constructor() : Capability {
  override val id = "control.fail"
  override val label = "Forzar fallo"
  override val description = "Detiene la rama con un mensaje de error (útil como stop)."
  override val category = CapabilityCategory.CONTROL
  override val icon = Icons.Outlined.Gavel
  override val params = listOf(
    ParamSpec("message", "Mensaje", ValueKind.STRING, default = JsonPrimitive("detenido")),
  )
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult {
    return NodeResult.fail(config["message"].asString().orEmpty())
  }
}

class SuccessCapability @Inject constructor() : Capability {
  override val id = "control.success"
  override val label = "Marcar éxito"
  override val description = "Nodo final; siempre reporta éxito y termina la rama."
  override val category = CapabilityCategory.CONTROL
  override val icon = Icons.Outlined.CheckCircle
  override val outputs = emptyList<com.google.ai.edge.gallery.customtasks.stocatstic.domain.PortSpec>()
  override suspend fun execute(ctx: ExecutionContext, config: JsonObject): NodeResult = NodeResult.ok()
}

/*
 * ================================================================================================
 *  Helpers
 * ================================================================================================
 */
/** Minimalist arithmetic evaluator used by [MathCapability]. Supports +, -, *, /, parentheses. */
private fun evalSimpleArith(input: String): String {
  val tokens = tokenize(input)
  val (value, next) = parseExpr(tokens, 0)
  check(next == tokens.size) { "unexpected token at $next" }
  return if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
private fun tokenize(s: String): List<String> {
  val out = ArrayList<String>()
  var i = 0
  while (i < s.length) {
    val c = s[i]
    when {
      c.isWhitespace() -> i++
      c in "+-*/()" -> { out += c.toString(); i++ }
      c.isDigit() || c == '.' -> {
        var j = i
        while (j < s.length && (s[j].isDigit() || s[j] == '.')) j++
        out += s.substring(i, j); i = j
      }
      else -> error("unexpected char $c")
    }
  }
  return out
}
private fun parseExpr(t: List<String>, i0: Int): Pair<Double, Int> {
  var (l, i) = parseTerm(t, i0)
  while (i < t.size && (t[i] == "+" || t[i] == "-")) {
    val op = t[i]; val (r, j) = parseTerm(t, i + 1)
    l = if (op == "+") l + r else l - r; i = j
  }
  return l to i
}
private fun parseTerm(t: List<String>, i0: Int): Pair<Double, Int> {
  var (l, i) = parseFactor(t, i0)
  while (i < t.size && (t[i] == "*" || t[i] == "/")) {
    val op = t[i]; val (r, j) = parseFactor(t, i + 1)
    l = if (op == "*") l * r else l / r; i = j
  }
  return l to i
}
private fun parseFactor(t: List<String>, i0: Int): Pair<Double, Int> {
  if (i0 >= t.size) error("eof")
  return when (val tok = t[i0]) {
    "(" -> { val (v, j) = parseExpr(t, i0 + 1); check(t[j] == ")"); v to (j + 1) }
    "-" -> { val (v, j) = parseFactor(t, i0 + 1); -v to j }
    "+" -> parseFactor(t, i0 + 1)
    else -> tok.toDouble() to (i0 + 1)
  }
}

