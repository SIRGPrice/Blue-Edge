/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Generic CoroutineWorker that powers the shared `DownloadManager` on
 * Android. Unlike the existing `DownloadWorker` (which is tightly coupled
 * to the model-download UI flow with proto-defined keys, ZIP unpacking,
 * notifications, etc.), this worker performs a plain URL → file download
 * with periodic progress reports.
 *
 * Designed to be invoked exclusively through `WorkManagerDownloadManager`;
 * end users should not interact with it directly.
 */
package com.google.ai.edge.gallery.bridges

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val SIMPLE_DOWNLOAD_KEY_URL = "blueedge.download.url"
internal const val SIMPLE_DOWNLOAD_KEY_DEST = "blueedge.download.dest"
internal const val SIMPLE_DOWNLOAD_KEY_AUTH = "blueedge.download.auth"

internal const val SIMPLE_DOWNLOAD_PROGRESS_BYTES = "blueedge.download.bytes"
internal const val SIMPLE_DOWNLOAD_PROGRESS_TOTAL = "blueedge.download.total"
internal const val SIMPLE_DOWNLOAD_OUTPUT_PATH = "blueedge.download.output"
internal const val SIMPLE_DOWNLOAD_OUTPUT_ERROR = "blueedge.download.error"

class SimpleDownloadWorker(
  context: Context,
  params: WorkerParameters,
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val url = inputData.getString(SIMPLE_DOWNLOAD_KEY_URL)
      ?: return@withContext Result.failure(error("missing url"))
    val dest = inputData.getString(SIMPLE_DOWNLOAD_KEY_DEST)
      ?: return@withContext Result.failure(error("missing destination"))
    val auth = inputData.getString(SIMPLE_DOWNLOAD_KEY_AUTH)

    val destFile = File(dest)
    destFile.parentFile?.mkdirs()
    val tmp = File("${dest}.part")

    runCatching {
      val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 30_000
        readTimeout = 30_000
        if (!auth.isNullOrBlank()) setRequestProperty("Authorization", auth)
      }
      val total = connection.contentLengthLong.coerceAtLeast(0)
      connection.inputStream.use { input ->
        tmp.outputStream().use { output ->
          val buffer = ByteArray(64 * 1024)
          var read: Int
          var downloaded = 0L
          var lastTs = 0L
          while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
            downloaded += read
            val now = System.currentTimeMillis()
            if (now - lastTs > 200L) {
              setProgress(
                Data.Builder()
                  .putLong(SIMPLE_DOWNLOAD_PROGRESS_BYTES, downloaded)
                  .putLong(SIMPLE_DOWNLOAD_PROGRESS_TOTAL, total)
                  .build(),
              )
              lastTs = now
            }
          }
        }
      }
      if (destFile.exists()) destFile.delete()
      tmp.renameTo(destFile)
    }.fold(
      onSuccess = {
        Result.success(
          Data.Builder().putString(SIMPLE_DOWNLOAD_OUTPUT_PATH, destFile.absolutePath).build(),
        )
      },
      onFailure = { e ->
        Log.e("BlueEdgeDl", "SimpleDownloadWorker failed for $url", e)
        runCatching { tmp.delete() }
        Result.failure(error(e.message ?: e.javaClass.simpleName))
      },
    )
  }

  private fun error(message: String): Data =
    Data.Builder().putString(SIMPLE_DOWNLOAD_OUTPUT_ERROR, message).build()
}

