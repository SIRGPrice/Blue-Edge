/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Real iOS LLM engine. Forwards calls to `BlueEdgeLlmBridge.swift` via the
 * Kotlin protocol declared in `ios/bridges/Bridges.kt`. Stream is bridged to
 * a Kotlin `Flow` so the rest of the app stays platform-agnostic.
 */
package com.blueedge.shared.runtime

import com.blueedge.shared.ios.bridges.IosBridgeRegistry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private class IosMediaPipeLlmEngine : LlmEngine {

  override suspend fun isAvailable(descriptor: LlmModelDescriptor): Boolean =
    IosBridgeRegistry.current != null

  override suspend fun load(descriptor: LlmModelDescriptor) {
    IosBridgeRegistry.require().llm.load(
      modelPath = descriptor.modelPath,
      maxTokens = 4096,
      preferGpu = descriptor.preferGpu,
    )
  }

  override fun generate(
    prompt: String,
    config: LlmGenerationConfig,
    images: List<ByteArray>,
  ): Flow<LlmEvent> = callbackFlow {
    val bridge = IosBridgeRegistry.require().llm
    bridge.generate(
      prompt = prompt,
      onToken = { token -> trySend(LlmEvent.Token(token)) },
      onError = { msg -> trySend(LlmEvent.Error(msg)); close() },
      onDone = { trySend(LlmEvent.Done); close() },
    )
    awaitClose { /* MediaPipe handles stream lifetime via close(). */ }
  }

  override suspend fun close() {
    IosBridgeRegistry.current?.llm?.close()
  }
}

actual fun createLlmEngine(): LlmEngine = IosMediaPipeLlmEngine()

