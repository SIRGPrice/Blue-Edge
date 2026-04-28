/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Background download abstraction (replaces WorkManager on Android and uses
 * URLSession background sessions on iOS).
 */
package com.blueedge.shared.download

import kotlinx.coroutines.flow.Flow

data class DownloadRequest(
  val id: String,
  val url: String,
  val destinationPath: String,
  val authHeader: String? = null,
)

sealed interface DownloadStatus {
  data class InProgress(val bytesDownloaded: Long, val totalBytes: Long) : DownloadStatus
  data class Completed(val filePath: String) : DownloadStatus
  data class Failed(val message: String) : DownloadStatus
}

interface DownloadManager {
  fun enqueue(request: DownloadRequest): Flow<DownloadStatus>
  fun cancel(id: String)
}

expect fun provideDownloadManager(): DownloadManager

