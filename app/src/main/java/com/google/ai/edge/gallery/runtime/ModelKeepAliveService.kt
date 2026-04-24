/*
 * Copyright 2026 Blue Edge.
 * Licensed under the Apache License, Version 2.0.
 */
package com.google.ai.edge.gallery.runtime
import com.google.ai.edge.gallery.R
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

