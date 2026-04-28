/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Adapter from the shared KMP `LlmEngine` abstraction to the existing Android
 * LiteRT-LM helper stack (`LlmChatModelHelper` / `AICoreModelHelper`).
 *
 * This intentionally lives in `:app` because that is where litertlm, MLKit
 * GenAI, AICore and the legacy `Model` type live. `:shared` only sees the
 * stable `com.blueedge.shared.runtime.LlmEngine` interface through
 * `AndroidBridgeRegistry`.
 */
package com.google.ai.edge.gallery.bridges

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.blueedge.shared.runtime.LlmEngine
import com.blueedge.shared.runtime.LlmEvent
import com.blueedge.shared.runtime.LlmGenerationConfig
import com.blueedge.shared.runtime.LlmModelDescriptor
import com.google.ai.edge.gallery.data.Accelerator
import com.google.ai.edge.gallery.data.ConfigKeys
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.NumberSliderConfig
import com.google.ai.edge.gallery.data.RuntimeType
import com.google.ai.edge.gallery.data.SegmentedButtonConfig
import com.google.ai.edge.gallery.data.ValueType
import com.google.ai.edge.gallery.runtime.runtimeHelper
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

class AndroidSharedLlmEngine(
  context: Context,
) : LlmEngine {
  private val appContext = context.applicationContext
  @Volatile private var model: Model? = null

  override suspend fun isAvailable(descriptor: LlmModelDescriptor): Boolean {
    if (descriptor.modelPath.isBlank()) return false
    // Absolute file paths are the common case for the shared API. If callers
    // pass a content:// or file:// URI, defer validation to the runtime helper.
    return runCatching {
      val path = descriptor.modelPath
      if (path.contains("://")) true else File(path).exists()
    }.getOrDefault(false)
  }

  override suspend fun load(descriptor: LlmModelDescriptor) {
    val synthetic = descriptor.toLegacyModel()
    synthetic.preProcess()
    val helper = synthetic.runtimeHelper
    suspendCancellableCoroutine { continuation ->
      try {
        helper.initialize(
          context = appContext,
          model = synthetic,
          supportImage = descriptor.supportsImages,
          supportAudio = descriptor.supportsAudio,
          onDone = { error ->
            if (continuation.isActive) {
              if (error.isBlank()) {
                model = synthetic
                continuation.resume(Unit)
              } else {
                continuation.resumeWithException(IllegalStateException(error))
              }
            }
          },
        )
      } catch (t: Throwable) {
        if (continuation.isActive) continuation.resumeWithException(t)
      }
      continuation.invokeOnCancellation {
        runCatching { helper.cleanUp(synthetic) {} }
      }
    }
  }

  override fun generate(
    prompt: String,
    config: LlmGenerationConfig,
    images: List<ByteArray>,
  ): Flow<LlmEvent> = callbackFlow {
    val current = model
    if (current == null) {
      trySend(LlmEvent.Error("Android shared LLM engine has not loaded a model yet."))
      trySend(LlmEvent.Done)
      close()
      return@callbackFlow
    }

    val helper = current.runtimeHelper
    val completed = AtomicBoolean(false)
    val bitmaps = images.mapNotNull { bytes ->
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    try {
      helper.runInference(
        model = current,
        input = prompt,
        resultListener = { partialResult, done, _ ->
          if (done) {
            if (completed.compareAndSet(false, true)) {
              trySend(LlmEvent.Done)
              close()
            }
          } else if (partialResult.isNotEmpty()) {
            trySend(LlmEvent.Token(partialResult))
          }
        },
        cleanUpListener = {},
        onError = { message ->
          if (completed.compareAndSet(false, true)) {
            trySend(LlmEvent.Error(message))
            trySend(LlmEvent.Done)
            close()
          }
        },
        images = bitmaps,
      )
    } catch (t: Throwable) {
      if (completed.compareAndSet(false, true)) {
        trySend(LlmEvent.Error(t.message ?: t.javaClass.simpleName, t))
        trySend(LlmEvent.Done)
        close(t)
      }
    }

    awaitClose {
      if (!completed.get()) {
        runCatching { helper.stopResponse(current) }
          .onFailure { Log.w(TAG, "stopResponse failed", it) }
      }
    }
  }

  override suspend fun close() {
    val current = model ?: return
    model = null
    current.runtimeHelper.cleanUp(current) {}
  }

  private fun LlmModelDescriptor.toLegacyModel(): Model {
    val file = File(modelPath)
    val safeName = buildString {
      append("shared_")
      append((file.nameWithoutExtension.ifBlank { "model" }).replace(Regex("[^a-zA-Z0-9]"), "_"))
      append('_')
      append(modelPath.hashCode().toUInt().toString(16))
    }
    val accelerator = if (preferGpu) Accelerator.GPU else Accelerator.CPU
    return Model(
      name = safeName,
      displayName = file.name.ifBlank { safeName },
      configs = listOf(
        NumberSliderConfig(
          key = ConfigKeys.MAX_TOKENS,
          sliderMin = 1f,
          sliderMax = 8192f,
          defaultValue = max(1, DEFAULT_SHARED_MAX_TOKENS).toFloat(),
          valueType = ValueType.INT,
        ),
        SegmentedButtonConfig(
          key = ConfigKeys.ACCELERATOR,
          defaultValue = accelerator.label,
          options = listOf(Accelerator.CPU.label, Accelerator.GPU.label),
        ),
        SegmentedButtonConfig(
          key = ConfigKeys.VISION_ACCELERATOR,
          defaultValue = Accelerator.GPU.label,
          options = listOf(Accelerator.CPU.label, Accelerator.GPU.label),
        ),
      ),
      downloadFileName = file.name.ifBlank { "model.bin" },
      isLlm = true,
      runtimeType = RuntimeType.LITERT_LM,
      localModelFilePathOverride = modelPath,
      llmSupportImage = supportsImages,
      llmSupportAudio = supportsAudio,
      accelerators = listOf(Accelerator.CPU, Accelerator.GPU),
      visionAccelerator = Accelerator.GPU,
    )
  }

  companion object {
    private const val TAG = "AndroidSharedLlmEngine"
    private const val DEFAULT_SHARED_MAX_TOKENS = 1024
  }
}

