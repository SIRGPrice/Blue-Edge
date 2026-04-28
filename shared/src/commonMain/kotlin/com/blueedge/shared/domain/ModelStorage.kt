/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Filesystem path resolver — replaces `Model.getPath(Context, ...)` from
 * the Android-only data layer.
 */
package com.blueedge.shared.domain

interface ModelStorage {
  /** Absolute path where downloaded model bundles are stored. */
  val baseModelsDir: String

  /** Resolves the absolute on-disk path for [model]'s default file. */
  fun resolvePath(model: Model, fileName: String = model.downloadFileName): String
}

expect fun provideModelStorage(): ModelStorage

