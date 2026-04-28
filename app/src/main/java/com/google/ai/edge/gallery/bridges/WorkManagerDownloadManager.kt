/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Real `DownloadManager` implementation for Android. Translates shared
 * `DownloadRequest`s into `WorkRequest`s scheduled with WorkManager and
 * surfaces progress as a `Flow<DownloadStatus>` by observing `WorkInfo`.
 *
 * Registered with `AndroidBridgeRegistry` from `GalleryApplication`.
 */
package com.google.ai.edge.gallery.bridges

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.blueedge.shared.download.DownloadManager
import com.blueedge.shared.download.DownloadRequest
import com.blueedge.shared.download.DownloadStatus
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.channels.awaitClose

class WorkManagerDownloadManager(
  private val appContext: Context,
) : DownloadManager {

  private val idToUuid = ConcurrentHashMap<String, UUID>()

  override fun enqueue(request: DownloadRequest): Flow<DownloadStatus> {
    val workManager = WorkManager.getInstance(appContext)
    val data = Data.Builder()
      .putString(SIMPLE_DOWNLOAD_KEY_URL, request.url)
      .putString(SIMPLE_DOWNLOAD_KEY_DEST, request.destinationPath)
      .apply { request.authHeader?.let { putString(SIMPLE_DOWNLOAD_KEY_AUTH, it) } }
      .build()
    val work = OneTimeWorkRequestBuilder<SimpleDownloadWorker>()
      .setInputData(data)
      .addTag(TAG_PREFIX + request.id)
      .build()
    idToUuid[request.id] = work.id
    workManager.enqueue(work)

    return callbackFlow {
      val live = workManager.getWorkInfoByIdLiveData(work.id)
      val observer = androidx.lifecycle.Observer<WorkInfo?> { info ->
        if (info == null) return@Observer
        val status = info.toDownloadStatus()
        trySend(status)
        if (info.state.isFinished) {
          idToUuid.remove(request.id)
          channel.close()
        }
      }
      // LiveData must be observed on the main thread.
      val main = androidx.core.os.HandlerCompat.createAsync(android.os.Looper.getMainLooper())
      main.post { live.observeForever(observer) }
      awaitClose {
        main.post { live.removeObserver(observer) }
      }
    }.distinctUntilChanged()
  }

  override fun cancel(id: String) {
    idToUuid.remove(id)?.let { uuid ->
      WorkManager.getInstance(appContext).cancelWorkById(uuid)
    }
  }

  private fun WorkInfo.toDownloadStatus(): DownloadStatus = when (state) {
    WorkInfo.State.SUCCEEDED -> {
      val out = outputData.getString(SIMPLE_DOWNLOAD_OUTPUT_PATH).orEmpty()
      DownloadStatus.Completed(out)
    }
    WorkInfo.State.FAILED -> {
      val message = outputData.getString(SIMPLE_DOWNLOAD_OUTPUT_ERROR) ?: "Download failed"
      DownloadStatus.Failed(message)
    }
    WorkInfo.State.CANCELLED -> DownloadStatus.Failed("Cancelled")
    else -> {
      val bytes = progress.getLong(SIMPLE_DOWNLOAD_PROGRESS_BYTES, 0L)
      val total = progress.getLong(SIMPLE_DOWNLOAD_PROGRESS_TOTAL, 0L)
      DownloadStatus.InProgress(bytesDownloaded = bytes, totalBytes = total)
    }
  }

  companion object {
    private const val TAG_PREFIX = "blueedge.download."
  }
}


