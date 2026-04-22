/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.customtasks.stocatstic.engine

import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.gallery.ui.llmchat.LlmModelInstance
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Abstracts LLM generation so capabilities don't depend on litert-lm directly. */
interface LlmRunner {
  fun isReady(): Boolean
  suspend fun generate(prompt: String): String
}

/**
 * Default runner wired to the StoCATstic task's currently initialized model.
 *
 * The task sets [activeModel] whenever the user picks a model in the top bar; capabilities simply
 * ask the runner to generate. If no model is loaded the runner reports not-ready and the
 * [LlmDecisionCapability] node fails gracefully (user sees an inline warning).
 */
@Singleton
class ActiveModelLlmRunner @Inject constructor() : LlmRunner {
  @Volatile var activeModel: Model? = null

  override fun isReady(): Boolean = activeModel?.instance is LlmModelInstance

  override suspend fun generate(prompt: String): String {
    val model = activeModel ?: error("No model selected")
    return suspendCancellableCoroutine { cont ->
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
        images = emptyList(),
        audioClips = emptyList(),
        coroutineScope = null,
        extraContext = null,
      )
    }
  }
}

