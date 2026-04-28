/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Centralised application-context holder. The Android host (or :app) must
 * call `AndroidContext.install(applicationContext)` once at startup so
 * shared `actual` providers can resolve a Context without leaking it across
 * orientation changes.
 */
package com.blueedge.shared.platform

import android.annotation.SuppressLint
import android.content.Context

object AndroidContext {
  @SuppressLint("StaticFieldLeak")
  @Volatile private var ctx: Context? = null

  fun install(applicationContext: Context) {
    ctx = applicationContext.applicationContext
  }

  val appContext: Context
    get() = checkNotNull(ctx) {
      "AndroidContext not initialised. Call AndroidContext.install(applicationContext) " +
        "from your Application.onCreate()."
    }
}

