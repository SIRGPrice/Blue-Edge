/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.google.ai.edge.gallery.common

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistencia y clasificación del último crash de proceso. Se invoca desde el
 * `Thread.UncaughtExceptionHandler` global (ver `GalleryApplication`) y desde
 * cualquier `try/catch` que quiera registrar un fallo recuperable.
 *
 * Diseño:
 *  - El reporte se guarda en un único fichero (`last_crash.txt`) dentro del
 *    almacenamiento interno (`filesDir`), de modo que en el siguiente arranque
 *    podamos leerlo y mostrar un diálogo con causa + pasos de recuperación.
 *  - **Nunca** lanza excepciones: cualquier fallo de I/O se traga silenciosamente
 *    para no convertir el handler de crashes en una nueva fuente de crashes.
 *  - Clasifica la causa raíz para sugerir una resolución legible al usuario.
 */
object CrashReporter {

  private const val TAG = "BlueEdgeCrash"
  /** Public alias of [TAG] so that the public-API inline [safe] helper can reference it. */
  const val LOG_TAG: String = "BlueEdgeCrash"
  private const val FILE_NAME = "last_crash.txt"
  private const val MAX_FILE_BYTES = 256 * 1024 // 256 KB es más que suficiente

  /** Categorías de crash, cada una con un texto humano de causa y resolución. */
  enum class Category(val title: String, val reason: String, val resolution: String) {
    OUT_OF_MEMORY(
      title = "Memoria insuficiente",
      reason =
        "El proceso se quedó sin memoria, probablemente al cargar un modelo grande, " +
          "imágenes muy pesadas o un contexto de conversación demasiado largo.",
      resolution =
        "1) Cierra otras apps en segundo plano.\n" +
          "2) Usa un modelo más pequeño (p.ej. variantes 2B/3B) o reduce el tamaño máximo de tokens.\n" +
          "3) Borra el chat actual o reinicia la conversación.\n" +
          "4) Reinicia la app.",
    ),
    NATIVE_CRASH(
      title = "Error en el motor de inferencia",
      reason =
        "El runtime nativo del LLM falló (acceso inválido a memoria o estado interno corrupto), " +
          "normalmente tras cancelar una respuesta o usar un modelo incompatible con el acelerador seleccionado.",
      resolution =
        "1) Vuelve a intentarlo: la app reconstruirá la conversación automáticamente.\n" +
          "2) Si persiste, cambia el acelerador en Configuración (CPU ↔ GPU ↔ NPU).\n" +
          "3) Reinicia la app o reinstala el modelo.",
    ),
    IO(
      title = "Error de almacenamiento",
      reason =
        "No se pudo leer o escribir un fichero (modelo, caché o ajustes). El almacenamiento puede " +
          "estar lleno, el fichero corrupto o sin permiso de acceso.",
      resolution =
        "1) Comprueba que tienes espacio libre en el dispositivo.\n" +
          "2) Borra el modelo desde el gestor de modelos y vuelve a descargarlo.\n" +
          "3) Concede los permisos solicitados a la app.",
    ),
    NETWORK(
      title = "Error de red",
      reason =
        "Una operación de red falló (descarga de modelo, lista de modelos o llamada remota). La conexión " +
          "puede haberse interrumpido o el servidor estar inaccesible.",
      resolution =
        "1) Comprueba tu conexión Wi-Fi o de datos.\n" +
          "2) Reintenta la descarga o la operación.\n" +
          "3) Si descargabas un modelo, púlsalo de nuevo: se reanudará desde donde se cortó.",
    ),
    PERMISSION(
      title = "Permiso denegado",
      reason =
        "La app necesita un permiso del sistema (almacenamiento, cámara, notificaciones, accesibilidad…) " +
          "que actualmente no tiene concedido.",
      resolution =
        "1) Abre Ajustes del sistema → Apps → BlueEdge → Permisos.\n" +
          "2) Concede el permiso requerido.\n" +
          "3) Vuelve a la app y reintenta la acción.",
    ),
    GENERIC(
      title = "Error inesperado",
      reason =
        "Se produjo un error que no pudo recuperarse. Hemos guardado un informe completo para que puedas revisarlo.",
      resolution =
        "1) Reinicia la app.\n" +
          "2) Si el problema se repite, copia el informe y compártelo con el desarrollador.\n" +
          "3) Como último recurso, borra los datos de la app desde Ajustes del sistema.",
    ),
  }

  data class Report(
    val category: Category,
    val timestamp: String,
    val threadName: String,
    val exceptionClass: String,
    val message: String?,
    val fullStack: String,
  )

  /**
   * Clasifica un throwable en una [Category] siguiendo heurísticas sobre el tipo
   * de excepción y el contenido del mensaje + stack.
   */
  fun classify(t: Throwable): Category {
    var cur: Throwable? = t
    val seen = HashSet<Throwable>()
    while (cur != null && seen.add(cur)) {
      val cls = cur.javaClass.name
      val msg = cur.message ?: ""
      when {
        cur is OutOfMemoryError -> return Category.OUT_OF_MEMORY
        cur is SecurityException -> return Category.PERMISSION
        cls.contains("UnknownHost") ||
          cls.contains("SocketTimeout") ||
          cls.contains("ConnectException") ||
          cls.contains("SSL") ||
          cls.contains("HttpException") -> return Category.NETWORK
        cur is java.io.IOException -> return Category.IO
        cls.contains("UnsatisfiedLinkError") ||
          msg.contains("SIGSEGV", ignoreCase = true) ||
          msg.contains("native crash", ignoreCase = true) ||
          msg.contains("liblitertlm", ignoreCase = true) ||
          msg.contains("mediapipe", ignoreCase = true) -> return Category.NATIVE_CRASH
      }
      cur = cur.cause
    }
    return Category.GENERIC
  }

  /** Persistir el crash a disco. Nunca lanza. */
  fun persist(context: Context, thread: Thread?, throwable: Throwable) {
    try {
      val sw = StringWriter()
      val pw = PrintWriter(sw)
      val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
      pw.println("== BlueEdge crash report ==")
      pw.println("timestamp: $ts")
      pw.println("thread: ${thread?.name ?: "<unknown>"} (id=${thread?.id ?: -1})")
      pw.println("device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, sdk ${Build.VERSION.SDK_INT})")
      pw.println("category: ${classify(throwable).name}")
      pw.println("exception: ${throwable.javaClass.name}: ${throwable.message}")
      pw.println("--- stack ---")
      throwable.printStackTrace(pw)
      var cause: Throwable? = throwable.cause
      var depth = 1
      val seen = HashSet<Throwable>()
      while (cause != null && depth < 8 && seen.add(cause)) {
        pw.println("--- caused by [$depth] ---")
        cause.printStackTrace(pw)
        cause = cause.cause
        depth++
      }
      pw.flush()
      val text = sw.toString().take(MAX_FILE_BYTES)
      val f = File(context.filesDir, FILE_NAME)
      f.writeText(text)
    } catch (_: Throwable) {
      // El handler de crashes nunca debe crashear.
    }
  }

  /** Lee el último reporte persistido, o `null` si no hay ninguno. */
  fun readLast(context: Context): Report? {
    return try {
      val f = File(context.filesDir, FILE_NAME)
      if (!f.exists() || f.length() == 0L) return null
      val text = f.readText()
      val lines = text.lines()
      fun field(name: String): String =
        lines.firstOrNull { it.startsWith("$name:") }?.substringAfter("$name:")?.trim().orEmpty()
      val ts = field("timestamp")
      val threadLine = field("thread")
      val categoryName = field("category")
      val exception = field("exception")
      val excClass = exception.substringBefore(":").trim()
      val excMsg = exception.substringAfter(":", "").trim().ifEmpty { null }
      val cat = runCatching { Category.valueOf(categoryName) }.getOrDefault(Category.GENERIC)
      Report(
        category = cat,
        timestamp = ts,
        threadName = threadLine,
        exceptionClass = excClass.ifEmpty { "<unknown>" },
        message = excMsg,
        fullStack = text,
      )
    } catch (t: Throwable) {
      Log.w(TAG, "readLast failed", t)
      null
    }
  }

  /** Borra el reporte persistido (tras mostrarlo al usuario). */
  fun clear(context: Context) {
    try {
      File(context.filesDir, FILE_NAME).delete()
    } catch (_: Throwable) {
    }
  }

  /**
   * Helper para rodear bloques arbitrarios. Si el bloque lanza, se persiste el
   * fallo y se devuelve `null` (el caller decide cómo seguir).
   */
  inline fun <T> safe(context: Context, tag: String = LOG_TAG, block: () -> T): T? {
    return try {
      block()
    } catch (t: Throwable) {
      Log.e(tag, "safe() caught", t)
      persist(context, Thread.currentThread(), t)
      null
    }
  }
}



