/*
 * Copyright 2026 SIRGPrice and Blue Edge contributors
 * Part of Blue Edge, a heavily modified app fork based on Google AI Edge Gallery.
 * Upstream project originally published by Google LLC:
 * https://github.com/google-ai-edge/gallery
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.runtime
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.ai.edge.gallery.R
/**
 * Tiny foreground service that keeps the app process alive (and therefore the loaded LLM in RAM)
 * while there are pending StoCATstic AI workflows. Started by [ModelLifecycleManager] when the app
 * goes to background with `workflowRefs > 0`, stopped on foreground return or when refs hit zero.
 */
class ModelKeepAliveService : Service() {
  override fun onBind(intent: Intent?): IBinder? = null
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    ensureChannel()
    val n: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("Blue Edge - flujo IA en curso")
      .setContentText("Se mantiene el modelo cargado para los flujos activos.")
      .setSmallIcon(R.drawable.graph_1_24px)
      .setOngoing(true)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      startForeground(NOTIF_ID, n)
    }
    return START_STICKY
  }
  private fun ensureChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (nm.getNotificationChannel(CHANNEL_ID) == null) {
      nm.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Flujos IA", NotificationManager.IMPORTANCE_LOW).apply {
          description = "Mantiene el modelo cargado mientras hay flujos IA activos."
        }
      )
    }
  }
  companion object {
    private const val CHANNEL_ID = "blue_edge_model_keepalive"
    private const val NOTIF_ID = 0x1337
  }
}
