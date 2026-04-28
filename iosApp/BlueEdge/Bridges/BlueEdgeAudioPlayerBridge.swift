// Copyright 2026 Blue Edge contributors.
//
// AVAudioEngine bridge for PCM16 mono playback. Mirrors the Android
// `AudioTrack` implementation under `androidMain`.

import Foundation
import AVFoundation

@objc public final class BlueEdgeAudioPlayerBridge: NSObject {

  public static let shared = BlueEdgeAudioPlayerBridge()

  private let engine = AVAudioEngine()
  private let player = AVAudioPlayerNode()
  private var attached = false

  /// Plays raw PCM16 mono bytes at [sampleRate]. `onProgress` reports a
  /// 0..1 fraction; `onFinished` is invoked once when playback completes.
  @objc public func play(pcm16Mono: Data,
                         sampleRate: Int32,
                         onProgress: @escaping (Float) -> Void,
                         onFinished: @escaping () -> Void) throws {
    if !attached {
      engine.attach(player)
      attached = true
    }
    let format = AVAudioFormat(commonFormat: .pcmFormatInt16,
                               sampleRate: Double(sampleRate),
                               channels: 1,
                               interleaved: true)!
    engine.connect(player, to: engine.mainMixerNode, format: format)
    try engine.start()

    let frameCount = AVAudioFrameCount(pcm16Mono.count / 2)
    guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frameCount) else {
      throw NSError(domain: "BlueEdge", code: -1,
        userInfo: [NSLocalizedDescriptionKey: "PCM buffer allocation failed"])
    }
    buffer.frameLength = frameCount
    pcm16Mono.withUnsafeBytes { raw in
      if let dst = buffer.int16ChannelData?.pointee, let src = raw.bindMemory(to: Int16.self).baseAddress {
        dst.update(from: src, count: Int(frameCount))
      }
    }

    let total = Float(frameCount)
    player.scheduleBuffer(buffer, at: nil, options: .interrupts) {
      DispatchQueue.main.async { onFinished() }
    }
    player.play()

    // Drive a coarse progress timer; AVAudioPlayerNode.lastRenderTime is the
    // more accurate path but we keep this simple for the MVP.
    let durationMs = Int(Double(frameCount) / Double(sampleRate) * 1000.0)
    if durationMs > 0 {
      DispatchQueue.global().async {
        let steps = max(1, durationMs / 100)
        for i in 1...steps {
          Thread.sleep(forTimeInterval: 0.1)
          let p = Float(i) / Float(steps)
          DispatchQueue.main.async { onProgress(min(p, 1.0)) }
        }
      }
    }
  }

  @objc public func stop() {
    player.stop()
    engine.stop()
  }
}

