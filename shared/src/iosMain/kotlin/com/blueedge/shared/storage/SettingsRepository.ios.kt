/*
 * Copyright 2026 Blue Edge contributors.
 *
 * iOS `actual` for `provideSettings()`. Backed by NSUserDefaults.
 */
package com.blueedge.shared.storage

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

actual fun provideSettings(): Settings =
  NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)

