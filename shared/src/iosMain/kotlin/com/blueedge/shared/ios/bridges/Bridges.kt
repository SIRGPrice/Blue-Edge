/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Bridge protocols implemented by Swift on the iOS host
 * (iosApp/BlueEdge/Bridges/Blue*Bridge.swift).
 *
 * This decoupling pattern keeps the `:shared` Gradle build platform-agnostic
 * (no `kotlin("native.cocoapods")` required, so Android contributors on
 * Windows/Linux can keep building without Xcode/CocoaPods). The Swift side
 * conforms to these Kotlin interfaces — Kotlin/Native exposes them as
 * Objective-C protocols in the generated framework header.
 */
package com.blueedge.shared.ios.bridges

/** Implemented by `BlueEdgeLlmBridge.swift`. */
interface LlmBridgeIos {
  fun load(modelPath: String, maxTokens: Int, preferGpu: Boolean)
  fun generate(
    prompt: String,
    temperature: Float,
    topK: Int,
    topP: Float,
    randomSeed: Int,
    onToken: (String) -> Unit,
    onError: (String) -> Unit,
    onDone: () -> Unit,
  )
  fun reset()
  fun close()
}

/** Implemented by `BlueEdgeAuthBridge.swift`. */
interface AuthBridgeIos {
  fun authorize(
    clientId: String,
    authEndpoint: String,
    tokenEndpoint: String,
    redirectUri: String,
    scopes: List<String>,
    onSuccess: (accessToken: String, refreshToken: String?, expiresAtSeconds: Double?) -> Unit,
    onFailure: (String) -> Unit,
  )
}

/** Implemented by `BlueEdgeDownloadBridge.swift`. */
interface DownloadBridgeIos {
  fun enqueue(
    id: String,
    url: String,
    destinationPath: String,
    authHeader: String?,
    onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    onCompletion: (filePath: String?, errorMessage: String?) -> Unit,
  )
  fun cancel(id: String)
}

/** Implemented by `BlueEdgeModelImportBridge.swift`. */
interface ModelImportBridgeIos {
  /**
   * Presents a `UIDocumentPickerViewController`, copies the picked files
   * into the platform models directory and invokes [onResult] with the list
   * of resulting absolute paths (empty if the user cancelled).
   */
  fun pickAndImport(onResult: (List<String>) -> Unit)
}

/** Aggregate handed once from Swift to Kotlin at app launch. */
data class BlueEdgeIosBridges(
  val llm: LlmBridgeIos,
  val auth: AuthBridgeIos,
  val download: DownloadBridgeIos,
  val modelImport: ModelImportBridgeIos? = null,
)

