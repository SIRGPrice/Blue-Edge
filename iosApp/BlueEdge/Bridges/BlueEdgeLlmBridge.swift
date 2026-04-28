// Copyright 2026 Blue Edge contributors.
//
// Swift wrapper around MediaPipe Tasks GenAI's `LlmInference`. This is the
// iOS equivalent of the Android `litertlm`/`mlkit-genai-prompt` runtime used
// by `app/src/main/java/com/google/ai/edge/gallery/runtime/LlmModelHelper.kt`.
//
// The class is exposed as `@objc` so it appears in the auto-generated
// Objective-C umbrella header that Kotlin/Native imports, allowing
// `IosMediaPipeLlmEngine` (in :shared/iosMain) to call it without manual
// cinterop definitions for MediaPipe types.

import Foundation
import MediaPipeTasksGenAI
import MediaPipeTasksGenAIC

@objc public final class BlueEdgeLlmBridge: NSObject {

  private var inference: LlmInference?
  private var session: LlmInference.Session?

  @objc public override init() { super.init() }

  /// Loads the model at the given absolute filesystem path. `.task` bundles
  /// produced by MediaPipe Model Maker / Gemma releases are supported.
  @objc public func load(modelPath: String,
                         maxTokens: Int32,
                         preferGpu: Bool) throws {
    let opts = LlmInference.Options(modelPath: modelPath)
    opts.maxTokens = Int(maxTokens)
    if preferGpu { opts.preferredBackend = .gpu }
    self.inference = try LlmInference(options: opts)
    // Session is created lazily per generation so each request can apply
    // its own sampling parameters (temperature/topK/topP/seed).
    self.session = nil
  }

  /// Streams generated tokens. `onToken` is invoked on the MediaPipe callback
  /// queue; the caller is responsible for hopping to the desired thread.
  @objc public func generate(prompt: String,
                             temperature: Float,
                             topK: Int32,
                             topP: Float,
                             randomSeed: Int32,
                             onToken: @escaping (String) -> Void,
                             onError: @escaping (NSError) -> Void,
                             onDone:  @escaping () -> Void) {
    guard let inference = self.inference else {
      onError(NSError(domain: "BlueEdge", code: -1,
                      userInfo: [NSLocalizedDescriptionKey: "Model not loaded"]))
      return
    }
    do {
      // Re-create session per generation so sampling parameters take effect.
      let sessionOpts = LlmInference.Session.Options()
      sessionOpts.temperature = temperature
      sessionOpts.topK = Int(topK)
      sessionOpts.topP = topP
      sessionOpts.randomSeed = Int(randomSeed)
      let session = try LlmInference.Session(llmInference: inference, options: sessionOpts)
      self.session = session

      try session.addQueryChunk(inputText: prompt)
      try session.generateResponseAsync(progress: { partial, error in
        if let error = error {
          onError(error as NSError); return
        }
        if let partial = partial { onToken(partial) }
      }, completion: {
        onDone()
      })
    } catch {
      onError(error as NSError)
    }
  }

  @objc public func reset() {
    if let inference = self.inference {
      self.session = try? LlmInference.Session(llmInference: inference)
    }
  }

  @objc public func close() {
    self.session = nil
    self.inference = nil
  }
}

