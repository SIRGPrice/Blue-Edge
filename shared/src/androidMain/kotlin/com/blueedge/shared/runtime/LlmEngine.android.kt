/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Android-side LLM engine. Phase 1 returns a stub that delegates to the
 * existing :app `LlmChatModelHelper` / `LlmModelHelper` / `AICoreModelHelper`.
 * The actual bridge is wired in :app via Koin once that module is migrated.
 */
package com.blueedge.shared.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private class AndroidLlmEngineStub : LlmEngine {
  override suspend fun isAvailable(descriptor: LlmModelDescriptor): Boolean = true
  override suspend fun load(descriptor: LlmModelDescriptor) { /* wired from :app */ }
  override fun generate(
    prompt: String,
    config: LlmGenerationConfig,
    images: List<ByteArray>,
  ): Flow<LlmEvent> = flow {
    emit(LlmEvent.Error("Android LLM bridge not yet wired in :app. " +
      "See com.blueedge.shared.runtime.LlmEngine for the migration plan."))
    emit(LlmEvent.Done)
  }
  override suspend fun close() {}
}

actual fun createLlmEngine(): LlmEngine = AndroidLlmEngineStub()

