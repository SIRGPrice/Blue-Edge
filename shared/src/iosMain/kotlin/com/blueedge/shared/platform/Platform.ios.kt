/*
 * Copyright 2026 Blue Edge contributors.
 */
package com.blueedge.shared.platform

import platform.UIKit.UIDevice

private class IosPlatform : Platform {
  override val kind = PlatformKind.IOS
  override val name = "iOS"
  override val osVersion: String = UIDevice.currentDevice.systemVersion
  override val supportsAICore: Boolean = false // No AICore on Apple platforms.
}

actual fun providePlatform(): Platform = IosPlatform()

