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
                      temperature: Float,
                      topK: Int32,
                      topP: Float,
                      randomSeed: Int32,
                      onToken: @escaping (String) -> Void,
                      onError: @escaping (String) -> Void,
                      onDone:  @escaping () -> Void) {
    impl.generate(prompt: prompt,
                  temperature: temperature,
                  topK: topK,
                  topP: topP,
                  randomSeed: randomSeed,
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

// MARK: - Model import (UIDocumentPickerViewController)

final class ModelImportBridgeAdapter: NSObject, BlueEdgeSharedModelImportBridgeIos {
  func pickAndImport(onResult: @escaping ([String]) -> Void) {
    BlueEdgeModelImportBridge.shared.pickAndImport(onResult: onResult)
  }
}

// MARK: - Audio recorder (AVAudioRecorder)

final class AudioRecorderBridgeAdapter: NSObject, BlueEdgeSharedAudioRecorderBridgeIos {
  func startSampleRate(_ sampleRate: Int32, bitRate: Int32) {
    do {
      try BlueEdgeAudioRecorderBridge.shared.start(sampleRate: sampleRate, bitRate: bitRate)
    } catch {
      NSLog("AudioRecorderBridgeAdapter.start failed: \(error)")
    }
  }

  func stop() -> KotlinByteArray {
    let data = BlueEdgeAudioRecorderBridge.shared.stop()
    return data.toKotlinByteArray()
  }

  func cancel() { BlueEdgeAudioRecorderBridge.shared.cancel() }
}

// MARK: - Audio player (AVAudioEngine)

final class AudioPlayerBridgeAdapter: NSObject, BlueEdgeSharedAudioPlayerBridgeIos {
  func playPcm16Mono(_ pcm16Mono: KotlinByteArray,
                     sampleRate: Int32,
                     onProgress: @escaping (KotlinFloat) -> Void,
                     onFinished: @escaping () -> Void) {
    let data = pcm16Mono.toData()
    do {
      try BlueEdgeAudioPlayerBridge.shared.play(
        pcm16Mono: data,
        sampleRate: sampleRate,
        onProgress: { p in onProgress(KotlinFloat(float: p)) },
        onFinished: onFinished)
    } catch {
      NSLog("AudioPlayerBridgeAdapter.play failed: \(error)")
      onFinished()
    }
  }

  func stop() { BlueEdgeAudioPlayerBridge.shared.stop() }
}

// MARK: - KotlinByteArray <-> Data helpers

private extension KotlinByteArray {
  func toData() -> Data {
    var bytes = [Int8](repeating: 0, count: Int(self.size))
    for i in 0..<Int(self.size) { bytes[i] = self.get(index: Int32(i)) }
    return bytes.withUnsafeBytes { Data($0) }
  }
}

private extension Data {
  func toKotlinByteArray() -> KotlinByteArray {
    let arr = KotlinByteArray(size: Int32(self.count))
    self.enumerated().forEach { idx, byte in
      arr.set(index: Int32(idx), value: Int8(bitPattern: byte))
    }
    return arr
  }
}

