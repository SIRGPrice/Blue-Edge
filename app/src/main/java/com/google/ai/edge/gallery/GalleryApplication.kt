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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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

    // Load saved theme.
    ThemeSettings.themeOverride.value = dataStoreRepository.readTheme()

    // Observe the whole-process lifecycle so the model lifecycle manager can decide whether to
    // keep the model loaded in background (workflow running) or unload it on exit.
    ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onStart(owner: LifecycleOwner) {
        appLifecycleProvider.isAppInForeground = true
        modelLifecycleManager.onAppForegrounded()
      }
      override fun onStop(owner: LifecycleOwner) {
        appLifecycleProvider.isAppInForeground = false
        modelLifecycleManager.onAppBackgrounded()
      }
    })
  }
}

