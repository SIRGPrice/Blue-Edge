/*
 * Copyright 2026 Blue Edge contributors.
 *
 * iOS file importer — delegates to the Swift UIDocumentPickerViewController
 * bridge (BlueEdgeModelImportBridge.swift). The bridge is responsible for
 * presenting the picker, copying the picked files into Documents/models
 * and returning the absolute paths.
 */
package com.blueedge.shared.domain

import com.blueedge.shared.ios.bridges.IosBridgeRegistry
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private class IosModelImporter : ModelImporter {
  override val isSupported: Boolean
    get() = IosBridgeRegistry.current?.modelImport != null

  override suspend fun pickAndImport(): List<String> {
    val bridge = IosBridgeRegistry.current?.modelImport ?: return emptyList()
    return suspendCancellableCoroutine { continuation ->
      bridge.pickAndImport(
        onResult = { paths -> if (continuation.isActive) continuation.resume(paths) },
      )
    }
  }
}

actual fun provideModelImporter(): ModelImporter = IosModelImporter()

