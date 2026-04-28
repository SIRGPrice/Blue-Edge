// Copyright 2026 Blue Edge contributors.
//
// AVAudioRecorder bridge — records to a temporary AAC/M4A file and returns
// the encoded bytes to the shared `AudioRecorder.start/stop` API. Mirrors
// the Android `MediaRecorder`-based implementation under `androidMain`.

import Foundation
import AVFoundation

@objc public final class BlueEdgeAudioRecorderBridge: NSObject, AVAudioRecorderDelegate {

  public static let shared = BlueEdgeAudioRecorderBridge()

  private var recorder: AVAudioRecorder?
  private var fileURL: URL?

  @objc public func start(sampleRate: Int32, bitRate: Int32) throws {
    let session = AVAudioSession.sharedInstance()
    try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
    try session.setActive(true)

    let tmp = FileManager.default.temporaryDirectory
      .appendingPathComponent("blueedge-record-\(UUID().uuidString).m4a")
    let settings: [String: Any] = [
      AVFormatIDKey:             kAudioFormatMPEG4AAC,
      AVSampleRateKey:           Double(sampleRate),
      AVNumberOfChannelsKey:     1,
      AVEncoderAudioQualityKey:  AVAudioQuality.medium.rawValue,
      AVEncoderBitRateKey:       Int(bitRate),
    ]
    let rec = try AVAudioRecorder(url: tmp, settings: settings)
    rec.delegate = self
    if !rec.record() { throw NSError(domain: "BlueEdge", code: -1,
      userInfo: [NSLocalizedDescriptionKey: "AVAudioRecorder failed to start"]) }
    self.recorder = rec
    self.fileURL = tmp
  }

  /// Stops recording and returns the encoded bytes (m4a). Empty on error.
  @objc public func stop() -> Data {
    recorder?.stop()
    let data: Data
    if let url = fileURL { data = (try? Data(contentsOf: url)) ?? Data() }
    else { data = Data() }
    if let url = fileURL { try? FileManager.default.removeItem(at: url) }
    recorder = nil
    fileURL = nil
    try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    return data
  }

  @objc public func cancel() {
    recorder?.stop()
    if let url = fileURL { try? FileManager.default.removeItem(at: url) }
    recorder = nil
    fileURL = nil
    try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
  }
}

