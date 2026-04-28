/*
 * Copyright 2026 Blue Edge contributors.
 */
package com.blueedge.shared.platform

import android.os.Build

private class AndroidPlatform : Platform {
  override val kind = PlatformKind.ANDROID
  override val name = "Android"
  override val osVersion: String = "API ${Build.VERSION.SDK_INT}"
  override val supportsAICore: Boolean = Build.VERSION.SDK_INT >= 31
}

actual fun providePlatform(): Platform = AndroidPlatform()

