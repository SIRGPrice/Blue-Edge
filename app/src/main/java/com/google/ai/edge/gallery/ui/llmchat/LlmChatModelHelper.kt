/*
 * Copyright 2025 Google LLC & Modifications Copyright 2026 SIRGPrice
 *
 * This file is part of Blue Edge: https://github.com/SIRGPrice/Blue-Edge,
 * a heavily modified fork of Google AI Edge Gallery: https://github.com/google-ai-edge/gallery
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ai.edge.gallery.ui.llmchat

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.gallery.common.cleanUpMediapipeTaskErrorMessage
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.DEFAULT_MAX_TOKEN
import com.google.ai.edge.gallery.data.DEFAULT_TOPK
import com.google.ai.edge.gallery.data.DEFAULT_TOPP
import com.google.ai.edge.gallery.data.DEFAULT_VISION_ACCELERATOR
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.runtime.CleanUpListener
import com.google.ai.edge.gallery.runtime.LlmModelHelper
import com.google.ai.edge.gallery.runtime.ResultListener
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolProvider
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "AGLlmChatModelHelper"

/**
 * Temperatura fija para todos los modelos.
 *
 * Es el mínimo práctico al que el sampler nativo de `liblitertlm_jni` produce una
 * distribución estable: por debajo de ~0.1 los logits se inflan (≥ 10×) y la softmax
 * colapsa hacia un único token, lo que puede provocar null deref en el sampler y
 * SIGSEGV durante el primer decode. Mantenemos este valor fijo (no configurable) para
 * que ningún modelo pueda forzar un valor inseguro.
 */
private const val FIXED_TEMPERATURE: Float = 0.1f

/**
 * Tiempo máximo (ms) que dejamos al JNI estar en silencio (sin emitir ni un solo
 * `onMessage`) antes de considerar que el prefill se ha colgado y forzar un cancel
 * + rebuild. Gemma-4-E4B con un prompt RAG de 8 chunks (~12 KB) tarda 20-40 s en un
 * device medio; 180 s nos da margen 5× y aún protege al usuario de quedarse atascado
 * indefinidamente. Solo aplica al **prefill** (antes del primer token); una vez que
 * empieza a stream, el watchdog se desactiva.
 */
private const val PREFILL_WATCHDOG_TIMEOUT_MS: Long = 180_000L

/**
 * Tiempo máximo (ms) que dejamos pasar entre dos `onMessage` consecutivos del JNI durante la
 * fase de **decode** (después del primer token). Si el runtime emite un token y luego se
 * silencia, la UI se quedaría con una respuesta a medias indefinidamente. 60 s es muy
 * generoso para tokens de Gemma/Llama incluso en CPU + prompts largos.
 */
private const val DECODE_WATCHDOG_TIMEOUT_MS: Long = 60_000L

/**
 * Periodo (ms) con el que el watchdog comprueba inactividad. Bajo para reaccionar rápido,
 * pero suficientemente alto para no añadir overhead.
 */
private const val WATCHDOG_TICK_MS: Long = 2_000L

/**
 * Scope de respaldo cuando el caller no proporciona uno (p.ej. [ActiveModelLlmRunner] desde
 * StoCATstic invoca `runInference` con `coroutineScope = null`). Sin scope no hay watchdog,
 * y entonces la inferencia podría colgarse indefinidamente. Usamos un scope persistente
 * para que el watchdog tenga garantía de ejecución.
 */
@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
private val watchdogFallbackScope: CoroutineScope = GlobalScope

data class LlmModelInstance(
  val engine: Engine,
  var conversation: Conversation,
  /**
   * Snapshot de los parámetros con los que se creó la `Conversation` actual. Necesario
   * para poder reconstruirla **sin** parámetros adicionales del caller — concretamente
   * cuando una sesión queda colgada tras un `cancelProcess()` y debemos rebuild el
   * `Conversation` antes del siguiente `sendMessageAsync` para evitar que el JNI
   * native se cuelgue en estados intermedios (síntomas: el prefill se queda al 99 %
   * para siempre, o termina sin emitir tokens). Ver [LlmChatModelHelper.runInference].
   */
  var lastConfig: ConversationParams,
  /**
   * Flag "la última generación se canceló a mitad de prefill/decode": el siguiente
   * `runInference` debe rebuild la `Conversation` antes de llamar a `sendMessageAsync`
   * para garantizar que el sampler y la KV cache no quedan en estado parcial.
   */
  @Volatile var needsRebuildBeforeNextSend: Boolean = false,
)

/** Parámetros con los que se construyó una `Conversation`. */
@OptIn(ExperimentalApi::class)
data class ConversationParams(
  val supportImage: Boolean,
  val supportAudio: Boolean,
  val systemInstruction: Contents?,
  val tools: List<ToolProvider>,
  val enableConversationConstrainedDecoding: Boolean,
)

object LlmChatModelHelper : LlmModelHelper {
  // Indexed by model name.
  private val cleanUpListeners: MutableMap<String, CleanUpListener> = mutableMapOf()

  @OptIn(ExperimentalApi::class) // opt-in experimental flags
  override fun initialize(
    context: Context,
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
    onDone: (String) -> Unit,
    systemInstruction: Contents?,
    tools: List<ToolProvider>,
    enableConversationConstrainedDecoding: Boolean,
    coroutineScope: CoroutineScope?,
  ) {
    // Prepare options.
    val maxTokens =
      model.getIntConfigValue(key = ConfigKeys.MAX_TOKENS, defaultValue = DEFAULT_MAX_TOKEN)
    val topK = model.getIntConfigValue(key = ConfigKeys.TOPK, defaultValue = DEFAULT_TOPK)
    val topP = model.getFloatConfigValue(key = ConfigKeys.TOPP, defaultValue = DEFAULT_TOPP)
    // Temperatura no configurable: ver constante FIXED_TEMPERATURE.
    val temperature = FIXED_TEMPERATURE
    val accelerator =
      model.getStringConfigValue(key = ConfigKeys.ACCELERATOR, defaultValue = Accelerator.GPU.label)
    val visionAccelerator =
      model.getStringConfigValue(
        key = ConfigKeys.VISION_ACCELERATOR,
        defaultValue = DEFAULT_VISION_ACCELERATOR.label,
      )
    val visionBackend =
      when (visionAccelerator) {
        Accelerator.CPU.label -> Backend.CPU()
        Accelerator.GPU.label -> Backend.GPU()
        Accelerator.NPU.label ->
          Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
        else -> Backend.GPU()
      }
    val shouldEnableImage = supportImage
    val shouldEnableAudio = supportAudio
    val preferredBackend =
      when (accelerator) {
        Accelerator.CPU.label -> Backend.CPU()
        Accelerator.GPU.label -> Backend.GPU()
        Accelerator.NPU.label ->
          Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
        else -> Backend.CPU()
      }
    Log.d(TAG, "Preferred backend: $preferredBackend")

    val modelPath = model.getPath(context = context)
    val sysInstrChars = systemInstruction?.toString()?.length ?: 0
    Log.i(
      "BlueEdgePerf",
      "LlmChatModelHelper.initialize model=${model.name} maxNumTokens=$maxTokens accel=$accelerator " +
        "image=$shouldEnableImage audio=$shouldEnableAudio sysInstrChars=$sysInstrChars",
    )
    val engineConfig =
      EngineConfig(
        modelPath = modelPath,
        backend = preferredBackend,
        visionBackend = if (shouldEnableImage) visionBackend else null,
        audioBackend = if (shouldEnableAudio) Backend.CPU() else null,
        maxNumTokens = maxTokens,
        cacheDir =
          if (modelPath.startsWith("/data/local/tmp"))
            context.getExternalFilesDir(null)?.absolutePath
          else null,
      )

    // Create an instance of LiteRT LM engine and conversation.
    try {
      val engine = Engine(engineConfig)
      engine.initialize()

      ExperimentalFlags.enableConversationConstrainedDecoding =
        enableConversationConstrainedDecoding
      val conversation =
        engine.createConversation(
          ConversationConfig(
            samplerConfig =
              if (preferredBackend is Backend.NPU) {
                null
              } else {
                SamplerConfig(
                  topK = topK,
                  topP = topP.toDouble(),
                  temperature = temperature.toDouble(),
                )
              },
            systemInstruction = systemInstruction,
            tools = tools,
          )
        )
      ExperimentalFlags.enableConversationConstrainedDecoding = false
      model.instance = LlmModelInstance(
        engine = engine,
        conversation = conversation,
        lastConfig = ConversationParams(
          supportImage = shouldEnableImage,
          supportAudio = shouldEnableAudio,
          systemInstruction = systemInstruction,
          tools = tools,
          enableConversationConstrainedDecoding = enableConversationConstrainedDecoding,
        ),
      )
    } catch (e: Exception) {
      onDone(cleanUpMediapipeTaskErrorMessage(e.message ?: "Unknown error"))
      return
    }
    onDone("")
  }

  @OptIn(ExperimentalApi::class) // opt-in experimental flags
  override fun resetConversation(
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
    systemInstruction: Contents?,
    tools: List<ToolProvider>,
    enableConversationConstrainedDecoding: Boolean,
  ) {
    try {
      Log.d(TAG, "Resetting conversation for model '${model.name}'")

      val instance = model.instance as LlmModelInstance? ?: return
      instance.conversation.close()

      val engine = instance.engine
      val topK = model.getIntConfigValue(key = ConfigKeys.TOPK, defaultValue = DEFAULT_TOPK)
      val topP = model.getFloatConfigValue(key = ConfigKeys.TOPP, defaultValue = DEFAULT_TOPP)
      val temperature = FIXED_TEMPERATURE
      val shouldEnableImage = supportImage
      val shouldEnableAudio = supportAudio
      Log.d(TAG, "Enable image: $shouldEnableImage, enable audio: $shouldEnableAudio")

      val accelerator =
        model.getStringConfigValue(
          key = ConfigKeys.ACCELERATOR,
          defaultValue = Accelerator.GPU.label,
        )
      ExperimentalFlags.enableConversationConstrainedDecoding =
        enableConversationConstrainedDecoding
      val newConversation =
        engine.createConversation(
          ConversationConfig(
            samplerConfig =
              if (accelerator == Accelerator.NPU.label) {
                null
              } else {
                SamplerConfig(
                  topK = topK,
                  topP = topP.toDouble(),
                  temperature = temperature.toDouble(),
                )
              },
            systemInstruction = systemInstruction,
            tools = tools,
          )
        )
      ExperimentalFlags.enableConversationConstrainedDecoding = false
      instance.conversation = newConversation
      instance.lastConfig = ConversationParams(
        supportImage = shouldEnableImage,
        supportAudio = shouldEnableAudio,
        systemInstruction = systemInstruction,
        tools = tools,
        enableConversationConstrainedDecoding = enableConversationConstrainedDecoding,
      )
      instance.needsRebuildBeforeNextSend = false

      Log.d(TAG, "Resetting done")
    } catch (e: Exception) {
      Log.d(TAG, "Failed to reset conversation", e)
    }
  }

  /**
   * Recrea la [Conversation] usando los parámetros guardados en
   * [LlmModelInstance.lastConfig]. Usado cuando una generación previa fue cancelada
   * y queremos garantizar un estado native limpio antes del siguiente send sin pedir
   * al caller que vuelva a pasar todos los parámetros (system prompt, tools, etc.).
   */
  @OptIn(ExperimentalApi::class)
  private fun rebuildConversationFromLastConfig(model: Model) {
    val instance = model.instance as? LlmModelInstance ?: return
    val cfg = instance.lastConfig
    Log.i(
      "BlueEdgePerf",
      "rebuildConversationFromLastConfig model=${model.name} (post-cancel safety reset)",
    )
    resetConversation(
      model = model,
      supportImage = cfg.supportImage,
      supportAudio = cfg.supportAudio,
      systemInstruction = cfg.systemInstruction,
      tools = cfg.tools,
      enableConversationConstrainedDecoding = cfg.enableConversationConstrainedDecoding,
    )
  }

  override fun cleanUp(model: Model, onDone: () -> Unit) {
    if (model.instance == null) {
      return
    }

    val instance = model.instance as LlmModelInstance

    try {
      instance.conversation.close()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to close the conversation: ${e.message}")
    }

    try {
      instance.engine.close()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to close the engine: ${e.message}")
    }

    val onCleanUp = cleanUpListeners.remove(model.name)
    if (onCleanUp != null) {
      onCleanUp()
    }
    model.instance = null

    onDone()
    Log.d(TAG, "Clean up done.")
  }

  override fun stopResponse(model: Model) {
    val instance = model.instance as? LlmModelInstance ?: return
    try {
      instance.conversation.cancelProcess()
    } catch (t: Throwable) {
      Log.w(TAG, "cancelProcess threw — will rebuild conversation before next send", t)
    }
    // Tras cancelar, el `Conversation` native puede quedar en un estado intermedio
    // (KV cache parcial, sampler con logits a medio aplicar). El siguiente
    // `sendMessageAsync` arrancando desde ese estado se cuelga indefinidamente o
    // produce respuestas truncadas. Marcamos un flag para forzar un rebuild limpio
    // antes del próximo send.
    instance.needsRebuildBeforeNextSend = true
    Log.i(
      "BlueEdgePerf",
      "stopResponse: cancelProcess issued; needsRebuildBeforeNextSend=true (model=${model.name})",
    )
  }

  override fun runInference(
    model: Model,
    input: String,
    resultListener: ResultListener,
    cleanUpListener: CleanUpListener,
    onError: (message: String) -> Unit,
    images: List<Bitmap>,
    audioClips: List<ByteArray>,
    coroutineScope: CoroutineScope?,
    extraContext: Map<String, String>?,
  ) {
    val tEnter = System.currentTimeMillis()
    val instance = model.instance as? LlmModelInstance
    if (instance == null) {
      Log.e(
        "BlueEdgePerf",
        "runInference called but model.instance is null/wrong type (model=${model.name}, raw=${model.instance?.javaClass?.name})",
      )
      onError("LlmModelInstance is not initialized.")
      return
    }
    // Defensive: si la generación anterior se canceló, rebuild la `Conversation`
    // antes de tocar nada para garantizar un estado native limpio.
    if (instance.needsRebuildBeforeNextSend) {
      Log.i(
        "BlueEdgePerf",
        "runInference: previous generation was cancelled — rebuilding conversation before send",
      )
      rebuildConversationFromLastConfig(model)
      if (instance.needsRebuildBeforeNextSend) {
        onError("No se pudo reiniciar la conversación tras cancelar la generación anterior.")
        return
      }
    }
    // Snapshot of what we are about to feed the native runtime. If the process dies right
    // after this line, we know exactly what input crashed it.
    Log.i(
      "BlueEdgePerf",
      "runInference: ENTER model=${model.name} engine=${instance.engine.javaClass.simpleName} " +
        "conv=${instance.conversation.javaClass.simpleName} " +
        "inputChars=${input.length} hash=${input.hashCode()} preview=\"" +
        input.take(120).replace('\n', ' ') + "\"",
    )

    // Set listener.
    if (!cleanUpListeners.containsKey(model.name)) {
      cleanUpListeners[model.name] = cleanUpListener
    }

    val conversation = instance.conversation

    val contents = mutableListOf<Content>()
    var totalImageBytes = 0
    val tImg = System.currentTimeMillis()
    for (image in images) {
      val png = image.toPngByteArray()
      totalImageBytes += png.size
      contents.add(Content.ImageBytes(png))
    }
    val imgEncodeMs = System.currentTimeMillis() - tImg
    var totalAudioBytes = 0
    for (audioClip in audioClips) {
      totalAudioBytes += audioClip.size
      contents.add(Content.AudioBytes(audioClip))
    }
    // add the text after image and audio for the accurate last token
    if (input.trim().isNotEmpty()) {
      contents.add(Content.Text(input))
    }
    Log.i(
      "BlueEdgePerf",
      "runInference: prepared contents in ${System.currentTimeMillis() - tEnter} ms — " +
        "textChars=${input.length} images=${images.size} (${totalImageBytes}B PNG, encode=${imgEncodeMs}ms) " +
        "audioClips=${audioClips.size} (${totalAudioBytes}B)",
    )

    val tSend = System.currentTimeMillis()
    val waitingForFirstNativeCallback = AtomicBoolean(true)
    // `finalized` garantiza que el flujo de respuesta sólo termina UNA vez: ya sea por
    // onDone, por onError, por watchdog o por excepción síncrona. Cualquier callback
    // posterior se ignora silenciosamente para no añadir burbujas vacías ni reabrir la UI.
    val finalized = AtomicBoolean(false)
    // Marca temporal del último callback nativo recibido. El watchdog la consulta en
    // cada tick para detectar tanto silencios de prefill como atascos de decode.
    val lastCallbackAtMs = AtomicLong(System.currentTimeMillis())
    val watchdogScope: CoroutineScope = coroutineScope ?: watchdogFallbackScope
    val watchdogJob: Job = watchdogScope.launch {
      while (isActive && !finalized.get()) {
        delay(WATCHDOG_TICK_MS)
        if (finalized.get()) break
        val now = System.currentTimeMillis()
        val silenceMs = now - lastCallbackAtMs.get()
        val isPrefill = waitingForFirstNativeCallback.get()
        val limit = if (isPrefill) PREFILL_WATCHDOG_TIMEOUT_MS else DECODE_WATCHDOG_TIMEOUT_MS
        if (silenceMs >= limit) {
          if (!finalized.compareAndSet(false, true)) break
          val phase = if (isPrefill) "PREFILL" else "DECODE"
          Log.w(
            "BlueEdgePerf",
            "runInference WATCHDOG[$phase]: ${silenceMs}ms sin callbacks (limit=${limit}ms) → " +
              "cancelando (model=${model.name})",
          )
          try { instance.conversation.cancelProcess() } catch (_: Throwable) {}
          instance.needsRebuildBeforeNextSend = true
          // SOLO una salida: errorListener libera la UI (setInProgress(false), elimina el
          // bubble de loading vía handleError, muestra el error). NO disparamos también
          // resultListener("", true) para no insertar un bubble vacío al final.
          val msg = if (isPrefill) {
            "Tiempo de espera agotado: el modelo no respondió en " +
              "${PREFILL_WATCHDOG_TIMEOUT_MS / 1000}s. Inténtalo con un prompt más corto."
          } else {
            "El modelo dejó de generar tokens durante " +
              "${DECODE_WATCHDOG_TIMEOUT_MS / 1000}s. Respuesta cancelada."
          }
          onError(msg)
          break
        }
      }
    }
    try {
      conversation.sendMessageAsync(
        Contents.of(contents),
        object : MessageCallback {
          override fun onMessage(message: Message) {
            if (finalized.get()) return
            lastCallbackAtMs.set(System.currentTimeMillis())
            if (waitingForFirstNativeCallback.compareAndSet(true, false)) {
              Log.i(
                "BlueEdgePerf",
                "runInference: FIRST onMessage from native runtime after ${System.currentTimeMillis() - tSend} ms",
              )
            }
            resultListener(message.toString(), false, message.channels["thought"])
          }

          override fun onDone() {
            if (!finalized.compareAndSet(false, true)) return
            watchdogJob.cancel()
            Log.i(
              "BlueEdgePerf",
              "runInference: native onDone after ${System.currentTimeMillis() - tSend} ms",
            )
            resultListener("", true, null)
          }

          override fun onError(throwable: Throwable) {
            if (!finalized.compareAndSet(false, true)) return
            watchdogJob.cancel()
            if (throwable is CancellationException) {
              Log.i(TAG, "The inference is cancelled.")
              resultListener("", true, null)
            } else {
              Log.e(TAG, "onError", throwable)
              // Cualquier error native deja la conversación en estado dudoso → marcamos
              // rebuild para el siguiente send.
              instance.needsRebuildBeforeNextSend = true
              onError("Error: ${throwable.message}")
            }
          }
        },
        extraContext ?: emptyMap(),
      )
    } catch (t: Throwable) {
      if (finalized.compareAndSet(false, true)) {
        watchdogJob.cancel()
        // sendMessageAsync occasionally bubbles up native errors as Java exceptions before
        // ever calling onError. Log them BEFORE rethrowing so we have a Kotlin stack trace
        // even when the runtime then escalates to SIGABRT/SIGSEGV.
        Log.e("BlueEdgeCrash", "sendMessageAsync threw synchronously", t)
        instance.needsRebuildBeforeNextSend = true
        onError("Error launching inference: ${t.message}")
      }
      return
    }
    Log.i(
      "BlueEdgePerf",
      "runInference: sendMessageAsync returned in ${System.currentTimeMillis() - tSend} ms (waiting for native callbacks)",
    )
  }

  private fun Bitmap.toPngByteArray(): ByteArray {
    val stream = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
  }
}
