/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.engine

import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.runtime.ModelLifecycleManager
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock

/** Abstracts LLM generation so capabilities don't depend on litert-lm directly. */
interface LlmRunner {
  fun isReady(): Boolean
  suspend fun generate(prompt: String): String = generate(prompt, emptyList(), emptyList())
  /**
   * Multimodal generation. [images] is a list of [android.graphics.Bitmap] and [audioClips] a
   * list of raw PCM/WAV byte arrays; both are passed through to `LlmChatModelHelper.runInference`.
   * The concrete runner decides whether the active model actually supports each modality.
   */
  suspend fun generate(
    prompt: String,
    images: List<android.graphics.Bitmap>,
    audioClips: List<ByteArray>,
  ): String
}

/**
 * Default runner wired to the StoCATstic task's currently initialized model.
 *
 * On-demand: if the model isn't loaded yet (app in background, first workflow trigger), the
 * [ModelLifecycleManager] will auto-load it under WORKFLOW ownership. Generation calls are
 * serialized across chat and workflow via [ModelLifecycleManager.generationMutex] so the shared
 * conversation object isn't used by two callers concurrently.
 */
@Singleton
class ActiveModelLlmRunner @Inject constructor(
  private val lifecycleManager: ModelLifecycleManager,
) : LlmRunner {
  @Volatile var activeModel: Model? = null
    set(value) {
      field = value
      lifecycleManager.registerActiveModel(value)
    }

  override fun isReady(): Boolean = activeModel?.instance is LlmModelInstance

  override suspend fun generate(
    prompt: String,
    images: List<android.graphics.Bitmap>,
    audioClips: List<ByteArray>,
  ): String {
    // Wait for the model; auto-loads for WORKFLOW if needed.
    lifecycleManager.awaitReady()
    val model = activeModel ?: error("No model selected")
    return lifecycleManager.generationMutex.withLock {
      suspendCancellableCoroutine { cont ->
        val sb = StringBuilder()
        LlmChatModelHelper.runInference(
          model = model,
          input = prompt,
          resultListener = { partial, done, _ ->
            sb.append(partial)
            if (done && cont.isActive) cont.resume(sb.toString().trim())
          },
          cleanUpListener = { },
          onError = { msg -> if (cont.isActive) cont.resumeWithException(RuntimeException(msg)) },
          images = images,
          audioClips = audioClips,
          coroutineScope = null,
          extraContext = null,
        )
      }
    }
  }
}
