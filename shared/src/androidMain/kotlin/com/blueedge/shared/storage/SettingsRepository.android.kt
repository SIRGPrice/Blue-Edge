/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Android `actual` for `provideSettings()`. Backed by SharedPreferences via
 * `multiplatform-settings`. The shared `AndroidContext` must be installed
 * before this is called.
 */
package com.blueedge.shared.storage

import com.blueedge.shared.platform.AndroidContext
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

private const val PREFS_NAME = "blueedge.shared.settings"

actual fun provideSettings(): Settings {
  val prefs = AndroidContext.appContext.getSharedPreferences(PREFS_NAME, 0)
  return SharedPreferencesSettings(prefs)
}

