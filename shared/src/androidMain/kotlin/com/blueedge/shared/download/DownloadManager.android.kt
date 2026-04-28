/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Android download manager stub. Will be wired to WorkManager via the
 * existing `worker/DownloadWorker.kt` in :app once that module migrates.
 */
package com.blueedge.shared.download

import com.blueedge.shared.android.bridges.AndroidBridgeRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private class AndroidDownloadManagerStub : DownloadManager {
  override fun enqueue(request: DownloadRequest): Flow<DownloadStatus> = flow {
    emit(DownloadStatus.Failed("Android DownloadManager bridge wired in :app (Phase 2)."))
  }
  override fun cancel(id: String) {}
}

actual fun provideDownloadManager(): DownloadManager =
  AndroidBridgeRegistry.downloadManager() ?: AndroidDownloadManagerStub()

