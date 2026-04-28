/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Process-global holder for Android-side bridge implementations injected by
 * `:app` at startup (mirror of `IosBridgeRegistry`).
 *
 * `:app` is the only place that depends on litertlm / MLKit GenAI / AICore /
 * WorkManager. The shared module exposes typed factories (`createLlmEngine`,
 * `provideDownloadManager`) whose Android `actual` implementations look up
 * the registered bridge first and fall back to a stub if `:app` did not
 * register one yet. This keeps the CI invariant
 * `:app:assembleDebug :shared:assembleDebug` green at every commit.
 */
package com.blueedge.shared.android.bridges

import com.blueedge.shared.download.DownloadManager
import com.blueedge.shared.runtime.LlmEngine

interface AndroidBridges {
  /** Optional real LLM engine. When null the shared stub is used. */
  val llmEngineFactory: (() -> LlmEngine)?
  /** Optional real download manager. When null the shared stub is used. */
  val downloadManagerFactory: (() -> DownloadManager)?
}

object AndroidBridgeRegistry {
  @Volatile private var current: AndroidBridges? = null

  fun install(bridges: AndroidBridges) { current = bridges }

  fun llmEngine(): LlmEngine? = current?.llmEngineFactory?.invoke()
  fun downloadManager(): DownloadManager? = current?.downloadManagerFactory?.invoke()
}

