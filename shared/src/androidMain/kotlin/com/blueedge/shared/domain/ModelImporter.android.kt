/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Android does not present an in-app file picker for now; users add models
 * by writing to the app-specific external files dir directly (visible from
 * the Files app on Android 11+). This actual is therefore a safe no-op.
 */
package com.blueedge.shared.domain

private class AndroidNoopModelImporter : ModelImporter {
  override val isSupported: Boolean = false
  override suspend fun pickAndImport(): List<String> = emptyList()
}

actual fun provideModelImporter(): ModelImporter = AndroidNoopModelImporter()

