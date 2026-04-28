/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Concrete `AndroidBridges` registered with `AndroidBridgeRegistry` at
 * `GalleryApplication.onCreate`. Wires `:app`-side implementations into
 * the shared `expect`/`actual` factories.
 *
 * Both LLM and download bridges are real Android implementations. The LLM
 * engine adapts the shared KMP API to the existing `LlmModelHelper` runtime;
 * downloads are backed by WorkManager.
 */
package com.google.ai.edge.gallery.bridges

import android.content.Context
import com.blueedge.shared.android.bridges.AndroidBridges
import com.blueedge.shared.download.DownloadManager
import com.blueedge.shared.runtime.LlmEngine

class AppBridges(private val appContext: Context) : AndroidBridges {
  override val llmEngineFactory: (() -> LlmEngine) = {
    AndroidSharedLlmEngine(appContext)
  }
  override val downloadManagerFactory: (() -> DownloadManager) = {
    WorkManagerDownloadManager(appContext)
  }
}

