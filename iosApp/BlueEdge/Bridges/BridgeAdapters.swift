// Copyright 2026 Blue Edge contributors.
//
// Swift adapters that conform to the Kotlin protocols declared in
// `shared/src/iosMain/.../bridges/Bridges.kt`. Kotlin/Native exports those
// interfaces as Objective-C protocols (`BlueEdgeSharedLlmBridgeIos`,
// `BlueEdgeSharedAuthBridgeIos`, `BlueEdgeSharedDownloadBridgeIos`) inside
// the generated framework header — Swift implements them and forwards calls
// to the concrete `BlueEdgeLlmBridge` / `BlueEdgeAuthBridge` /
// `BlueEdgeDownloadBridge` defined in the sibling files.

import Foundation
import UIKit
import BlueEdgeShared

// MARK: - LLM

final class LlmBridgeAdapter: NSObject, BlueEdgeSharedLlmBridgeIos {

  private let impl = BlueEdgeLlmBridge()

  func loadModelPath(_ modelPath: String,
                     maxTokens: Int32,
                     preferGpu: Bool) {
    do {
      try impl.load(modelPath: modelPath,
                    maxTokens: maxTokens,
                    preferGpu: preferGpu)
    } catch {
      NSLog("LlmBridgeAdapter.load failed: \(error)")
    }
  }

  func generatePrompt(_ prompt: String,
                      onToken: @escaping (String) -> Void,
                      onError: @escaping (String) -> Void,
                      onDone:  @escaping () -> Void) {
    impl.generate(prompt: prompt,
                  onToken: onToken,
                  onError: { err in onError(err.localizedDescription) },
                  onDone:  onDone)
  }

  func reset() { impl.reset() }
  func close() { impl.close() }
}

// MARK: - Auth

final class AuthBridgeAdapter: NSObject, BlueEdgeSharedAuthBridgeIos {

  static let shared = AuthBridgeAdapter()
  private let impl = BlueEdgeAuthBridge()

  func authorizeClientId(_ clientId: String,
                         authEndpoint: String,
                         tokenEndpoint: String,
                         redirectUri: String,
                         scopes: [String],
                         onSuccess: @escaping (String, String?, KotlinDouble?) -> Void,
                         onFailure: @escaping (String) -> Void) {
    guard let presenter = Self.topViewController() else {
      onFailure("No presenting UIViewController"); return
    }
    impl.authorize(clientId: clientId,
                   authEndpoint: authEndpoint,
                   tokenEndpoint: tokenEndpoint,
                   redirectUri: redirectUri,
                   scopes: scopes,
                   presenting: presenter,
                   onSuccess: { access, refresh, exp in
                     let kExp = exp.map { KotlinDouble(double: $0.doubleValue) }
                     onSuccess(access, refresh, kExp)
                   },
                   onFailure: { err in onFailure(err.localizedDescription) })
  }

  func resumeAuth(with url: URL) -> Bool { impl.resume(with: url) }

  private static func topViewController() -> UIViewController? {
    let scene = UIApplication.shared.connectedScenes
      .compactMap { $0 as? UIWindowScene }.first
    var top = scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController
    while let presented = top?.presentedViewController { top = presented }
    return top
  }
}

// MARK: - Background downloads

final class DownloadBridgeAdapter: NSObject, BlueEdgeSharedDownloadBridgeIos {

  func enqueueId(_ id: String,
                 url: String,
                 destinationPath: String,
                 authHeader: String?,
                 onProgress: @escaping (Int64, Int64) -> Void,
                 onCompletion: @escaping (String?, String?) -> Void) {
    BlueEdgeDownloadBridge.shared.enqueue(
      id: id,
      url: url,
      destinationPath: destinationPath,
      authHeader: authHeader,
      onProgress: onProgress,
      onCompletion: { path, err in
        onCompletion(path, err?.localizedDescription)
      })
  }

  func cancelId(_ id: String) {
    BlueEdgeDownloadBridge.shared.cancel(id: id)
  }
}

