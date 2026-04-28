// Copyright 2026 Blue Edge contributors.
//
// Document picker bridge — presents a `UIDocumentPickerViewController` so the
// user can import a `.task` / `.tflite` / `.bin` model from Files / iCloud
// Drive / a third-party provider into the app's `Documents/models` directory.
//
// Once the user picks one or more files we copy them into the models dir and
// invoke `onResult` with the resulting absolute paths.

import Foundation
import UIKit
import UniformTypeIdentifiers

@objc public final class BlueEdgeModelImportBridge: NSObject, UIDocumentPickerDelegate {

  public static let shared = BlueEdgeModelImportBridge()
  private var pendingCompletion: (([String]) -> Void)?

  /// Presents the picker on the top-most view controller. Always invokes
  /// `onResult` exactly once (with an empty list on cancel/error).
  @objc public func pickAndImport(onResult: @escaping ([String]) -> Void) {
    guard let presenter = Self.topViewController() else {
      onResult([]); return
    }

    self.pendingCompletion = onResult

    let types: [UTType] = [
      UTType(filenameExtension: "task")    ?? .data,
      UTType(filenameExtension: "tflite")  ?? .data,
      UTType(filenameExtension: "bin")     ?? .data,
      UTType(filenameExtension: "litertlm") ?? .data,
      .data,
    ]
    let picker = UIDocumentPickerViewController(forOpeningContentTypes: types, asCopy: true)
    picker.allowsMultipleSelection = true
    picker.shouldShowFileExtensions = true
    picker.delegate = self
    presenter.present(picker, animated: true)
  }

  // MARK: - UIDocumentPickerDelegate

  public func documentPicker(_ controller: UIDocumentPickerViewController,
                             didPickDocumentsAt urls: [URL]) {
    let imported: [String] = urls.compactMap { src in
      copyIntoModelsDir(src: src)
    }
    pendingCompletion?(imported)
    pendingCompletion = nil
  }

  public func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
    pendingCompletion?([])
    pendingCompletion = nil
  }

  // MARK: - Filesystem

  private func copyIntoModelsDir(src: URL) -> String? {
    let fm = FileManager.default
    guard let docs = fm.urls(for: .documentDirectory, in: .userDomainMask).first else { return nil }
    let modelsDir = docs.appendingPathComponent("models", isDirectory: true)
    try? fm.createDirectory(at: modelsDir, withIntermediateDirectories: true)

    // Picker with `asCopy: true` already copied to a tmp inbox path; we need
    // a security-scoped resource to read that location.
    let needsScope = src.startAccessingSecurityScopedResource()
    defer { if needsScope { src.stopAccessingSecurityScopedResource() } }

    let dst = modelsDir.appendingPathComponent(src.lastPathComponent)
    if fm.fileExists(atPath: dst.path) {
      try? fm.removeItem(at: dst)
    }
    do {
      try fm.copyItem(at: src, to: dst)
      return dst.path
    } catch {
      NSLog("BlueEdgeModelImportBridge.copy failed: \(error)")
      return nil
    }
  }

  private static func topViewController() -> UIViewController? {
    let scene = UIApplication.shared.connectedScenes
      .compactMap { $0 as? UIWindowScene }.first
    var top = scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController
    while let presented = top?.presentedViewController { top = presented }
    return top
  }
}

