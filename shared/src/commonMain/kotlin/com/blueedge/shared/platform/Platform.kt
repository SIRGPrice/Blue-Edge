/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Platform identification and cross-platform constants exposed to common code.
 * Each platform provides its own `actual` implementation under `androidMain`
 * or `iosMain`.
 */
package com.blueedge.shared.platform

enum class PlatformKind { ANDROID, IOS }

interface Platform {
  val kind: PlatformKind
  val name: String
  val osVersion: String
  /** True if the platform exposes Google AICore / Gemini Nano APIs. */
  val supportsAICore: Boolean
}

expect fun providePlatform(): Platform

