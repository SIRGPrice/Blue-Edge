/*
 * Copyright 2026 Blue Edge contributors.
 *
 * iOS background download manager. Forwards to `BlueEdgeDownloadBridge.swift`
 * (URLSession with `.background` configuration), so transfers continue while
 * the app is suspended — equivalent of WorkManager on Android.
 */
package com.blueedge.shared.download

import com.blueedge.shared.ios.bridges.IosBridgeRegistry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private class IosDownloadManager : DownloadManager {

  override fun enqueue(request: DownloadRequest): Flow<DownloadStatus> = callbackFlow {
    val bridge = IosBridgeRegistry.require().download
    bridge.enqueue(
      id = request.id,
      url = request.url,
      destinationPath = request.destinationPath,
      authHeader = request.authHeader,
      onProgress = { downloaded, total ->
        trySend(DownloadStatus.InProgress(downloaded, total))
      },
      onCompletion = { filePath, errorMessage ->
        if (errorMessage != null) {
          trySend(DownloadStatus.Failed(errorMessage))
        } else if (filePath != null) {
          trySend(DownloadStatus.Completed(filePath))
        }
        close()
      },
    )
    awaitClose { bridge.cancel(request.id) }
  }

  override fun cancel(id: String) {
    IosBridgeRegistry.current?.download?.cancel(id)
  }
}

actual fun provideDownloadManager(): DownloadManager = IosDownloadManager()

