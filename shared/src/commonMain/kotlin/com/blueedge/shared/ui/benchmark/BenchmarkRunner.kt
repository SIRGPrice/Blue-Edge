/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Cross-platform benchmark runner. Measures prefill latency (time-to-first-
 * token), decode time and output token count for a given `LlmModelDescriptor`.
 * Backed by the platform `LlmEngine` (LiteRT-LM on Android, MediaPipe on iOS).
 */
package com.blueedge.shared.ui.benchmark

import com.blueedge.shared.runtime.LlmEngine
import com.blueedge.shared.runtime.LlmEvent
import com.blueedge.shared.runtime.LlmGenerationConfig
import com.blueedge.shared.runtime.LlmModelDescriptor
import kotlinx.coroutines.flow.collect
import kotlinx.datetime.Clock

class BenchmarkRunner(private val engine: LlmEngine) {
  /**
   * Loads [descriptor] (if not already), runs [prompt] under [config] and
   * returns prefill/decode/token metrics. Throws on engine errors.
   */
  suspend fun run(
    descriptor: LlmModelDescriptor,
    prompt: String = DEFAULT_PROMPT,
    config: LlmGenerationConfig = LlmGenerationConfig(maxTokens = 128),
  ): BenchmarkSummary {
    if (!engine.isAvailable(descriptor)) {
      error("LlmEngine reports the model is not available on this device.")
    }
    engine.load(descriptor)

    val startMs = nowMs()
    var firstTokenMs: Long? = null
    var lastTokenMs: Long = startMs
    var tokens = 0
    var failure: Throwable? = null

    engine.generate(prompt, config).collect { event ->
      when (event) {
        is LlmEvent.Token -> {
          val now = nowMs()
          if (firstTokenMs == null) firstTokenMs = now
          lastTokenMs = now
          tokens += 1
        }
        is LlmEvent.Error -> {
          failure = event.cause ?: IllegalStateException(event.message)
        }
        is LlmEvent.Done -> {
          lastTokenMs = nowMs()
        }
      }
    }
    failure?.let { throw it }

    val firstTok = firstTokenMs ?: lastTokenMs
    val prefill = (firstTok - startMs).coerceAtLeast(0L)
    val decode = (lastTokenMs - firstTok).coerceAtLeast(0L)
    return BenchmarkSummary(
      prefillMs = prefill,
      decodeMs = decode,
      outputTokens = tokens,
    )
  }

  private fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

  companion object {
    const val DEFAULT_PROMPT = "Write a short paragraph about the ocean at sunrise."
  }
}

