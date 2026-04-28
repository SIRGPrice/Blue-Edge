/*
 * Copyright 2025 Google LLC
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

package com.google.ai.edge.gallery

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.ai.edge.gallery.common.CrashReporter
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.runtime.ModelLifecycleManager
import com.google.ai.edge.gallery.ui.theme.ThemeSettings
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GalleryApplication : Application() {

  @Inject lateinit var dataStoreRepository: DataStoreRepository
  @Inject lateinit var modelLifecycleManager: ModelLifecycleManager
  @Inject lateinit var appLifecycleProvider: AppLifecycleProvider

  override fun onCreate() {
    super.onCreate()

    // Install a global crash logger BEFORE anything else so any uncaught exception
    // (Compose, coroutines on Default/IO via JobCancellationException -> rethrow,
    // native callbacks…) leaves a full stack trace in logcat with the tag
    // "BlueEdgeCrash" right before the process dies. Without this, Android may kill
    // the process and the only Logcat line you see is "Process … crashed" with no
    // root cause.
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      try {
        Log.e(
          "BlueEdgeCrash",
          "UNCAUGHT on thread '${thread.name}' (id=${thread.id}): ${throwable.javaClass.name}: ${throwable.message}",
          throwable,
        )
        // Walk the cause chain manually too — some Android log truncations swallow
        // the chained causes that Log.e prints, so we dump them explicitly.
        var cause: Throwable? = throwable.cause
        var depth = 1
        while (cause != null && depth < 8) {
          Log.e("BlueEdgeCrash", "  caused by [$depth]: ${cause.javaClass.name}: ${cause.message}", cause)
          cause = cause.cause
          depth++
        }
        // Persistir el reporte para mostrarlo al usuario en el siguiente arranque
        // con causa + pasos de resolución (ver CrashRecoveryDialog).
        CrashReporter.persist(applicationContext, thread, throwable)
      } catch (_: Throwable) {
        // Never let the crash handler itself crash.
      }
      // Hand off to the default handler so the OS still kills the process and
      // shows the standard "App keeps stopping" dialog (otherwise we'd silently
      // swallow the crash and leave the UI in a half-broken state).
      previous?.uncaughtException(thread, throwable)
    }
    Log.i("BlueEdgePerf", "GalleryApplication.onCreate — crash handler installed")

    // Load saved theme. Si DataStore está corrupto o el disco no responde, no debemos
    // tumbar el arranque entero: registramos el fallo y seguimos con tema por defecto.
    try {
      ThemeSettings.themeOverride.value = dataStoreRepository.readTheme()
    } catch (t: Throwable) {
      Log.e("BlueEdgeCrash", "readTheme failed; using default theme", t)
      CrashReporter.persist(applicationContext, Thread.currentThread(), t)
    }

    // Observe the whole-process lifecycle so the model lifecycle manager can decide whether to
    // keep the model loaded in background (workflow running) or unload it on exit.
    try {
      ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
          try {
            appLifecycleProvider.isAppInForeground = true
            modelLifecycleManager.onAppForegrounded()
          } catch (t: Throwable) {
            Log.e("BlueEdgeCrash", "onAppForegrounded failed", t)
          }
        }
        override fun onStop(owner: LifecycleOwner) {
          try {
            appLifecycleProvider.isAppInForeground = false
            modelLifecycleManager.onAppBackgrounded()
          } catch (t: Throwable) {
            Log.e("BlueEdgeCrash", "onAppBackgrounded failed", t)
          }
        }
      })
    } catch (t: Throwable) {
      Log.e("BlueEdgeCrash", "Failed to register ProcessLifecycle observer", t)
    }
  }
}
