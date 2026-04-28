/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Cross-platform LLM inference abstraction. Replaces direct dependencies on
 * `litertlm`, `mlkit-genai-prompt`, AICore and TFLite that were Android-only.
 *
 * Backing implementations:
 *  - Android (`androidMain`): bridges to existing LiteRT-LM / MLKit GenAI /
 *    AICore / TFLite helpers that already live in :app.
 *  - iOS (`iosMain`): backed by MediaPipe Tasks GenAI (LLM Inference) and
 *    TensorFlow Lite Swift, integrated through Kotlin/Native cinterop.
 */
package com.blueedge.shared.runtime

import kotlinx.coroutines.flow.Flow

data class LlmGenerationConfig(
  val maxTokens: Int = 1024,
  val temperature: Float = 0.8f,
  val topK: Int = 40,
  val topP: Float = 0.95f,
  val randomSeed: Int = 0,
)

data class LlmModelDescriptor(
  /** Absolute path or URI to the model bundle (.task / .tflite / .bin). */
  val modelPath: String,
  /** Optional companion files (LoRA adapters, tokenizer overrides, etc.). */
  val accessoryPaths: List<String> = emptyList(),
  val preferGpu: Boolean = true,
  val supportsImages: Boolean = false,
  val supportsAudio: Boolean = false,
)

sealed interface LlmEvent {
  data class Token(val text: String) : LlmEvent
  data class Error(val message: String, val cause: Throwable? = null) : LlmEvent
  data object Done : LlmEvent
}

interface LlmEngine {
  /** Returns true if this engine implementation is available on the current device. */
  suspend fun isAvailable(descriptor: LlmModelDescriptor): Boolean

  /** Loads the model into memory. Must be called before [generate]. */
  suspend fun load(descriptor: LlmModelDescriptor)

  /** Streams tokens for the given prompt. */
  fun generate(
    prompt: String,
    config: LlmGenerationConfig = LlmGenerationConfig(),
    images: List<ByteArray> = emptyList(),
  ): Flow<LlmEvent>

  /** Releases native resources. */
  suspend fun close()
}

/** Factory resolved through Koin per platform. */
expect fun createLlmEngine(): LlmEngine

