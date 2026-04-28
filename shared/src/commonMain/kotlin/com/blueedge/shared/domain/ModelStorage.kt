/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Filesystem path resolver — replaces `Model.getPath(Context, ...)` from
 * the Android-only data layer.
 */
package com.blueedge.shared.domain

data class ModelFile(
  val name: String,
  val absolutePath: String,
  val sizeInBytes: Long,
  val isDirectory: Boolean,
)

interface ModelStorage {
  /** Absolute path where downloaded model bundles are stored. */
  val baseModelsDir: String

  /** Resolves the absolute on-disk path for [model]'s default file. */
  fun resolvePath(model: Model, fileName: String = model.downloadFileName): String

  /** Lists first-level files/directories under [baseModelsDir]. */
  fun listModelFiles(): List<ModelFile>
}

expect fun provideModelStorage(): ModelStorage

