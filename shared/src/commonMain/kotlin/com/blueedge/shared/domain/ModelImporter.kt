/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Cross-platform model file importer. Android relies on the user dropping
 * files via adb / Files into the external storage directory exposed by
 * `ModelStorage.baseModelsDir`, so the actual is a no-op there. On iOS the
 * actual delegates to a Swift `UIDocumentPickerViewController` bridge that
 * copies the picked files into `Documents/models` and returns the resulting
 * absolute paths.
 */
package com.blueedge.shared.domain

interface ModelImporter {
  /**
   * Presents a system file picker (when supported) and returns the absolute
   * paths of the files that were copied into the platform model storage
   * directory. Empty list means "user cancelled" or "not supported on this
   * platform".
   */
  suspend fun pickAndImport(): List<String>

  /** Whether this platform actually presents a picker to the user. */
  val isSupported: Boolean
}

expect fun provideModelImporter(): ModelImporter

