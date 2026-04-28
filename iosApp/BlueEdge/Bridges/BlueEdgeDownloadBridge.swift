// Copyright 2026 Blue Edge contributors.
//
// URLSession-backed background download bridge — iOS equivalent of the
// Android `WorkManager` + `DownloadWorker` pipeline.
//
// Transfers continue while the app is suspended thanks to
// `URLSessionConfiguration.background(withIdentifier:)`. The system relaunches
// the app in the background to deliver completion events; the AppDelegate
// must forward `application(_:handleEventsForBackgroundURLSession:completionHandler:)`
// to `BlueEdgeDownloadBridge.shared.handleEvents`.

import Foundation

@objc public final class BlueEdgeDownloadBridge: NSObject, URLSessionDownloadDelegate {

  @objc public static let shared = BlueEdgeDownloadBridge()

  private lazy var session: URLSession = {
    let cfg = URLSessionConfiguration.background(withIdentifier: "com.blueedge.download")
    cfg.isDiscretionary = false
    cfg.sessionSendsLaunchEvents = true
    return URLSession(configuration: cfg, delegate: self, delegateQueue: nil)
  }()

  private var progress: [String: (Int64, Int64) -> Void] = [:]
  private var completion: [String: (String?, NSError?) -> Void] = [:]
  private var destinations: [String: URL] = [:]
  private var pendingSystemHandler: (() -> Void)?

  @objc public func enqueue(id: String,
                            url: String,
                            destinationPath: String,
                            authHeader: String?,
                            onProgress: @escaping (Int64, Int64) -> Void,
                            onCompletion: @escaping (String?, NSError?) -> Void) {
    guard let u = URL(string: url) else {
      onCompletion(nil, NSError(domain: "BlueEdgeDownload", code: -20,
        userInfo: [NSLocalizedDescriptionKey: "Invalid URL"]))
      return
    }
    var req = URLRequest(url: u)
    if let h = authHeader { req.setValue(h, forHTTPHeaderField: "Authorization") }
    let task = session.downloadTask(with: req)
    task.taskDescription = id
    progress[id] = onProgress
    completion[id] = onCompletion
    destinations[id] = URL(fileURLWithPath: destinationPath)
    task.resume()
  }

  @objc public func cancel(id: String) {
    session.getAllTasks { tasks in
      tasks.first { $0.taskDescription == id }?.cancel()
    }
  }

  @objc public func handleEvents(completionHandler: @escaping () -> Void) {
    self.pendingSystemHandler = completionHandler
  }

  // MARK: - URLSessionDownloadDelegate

  public func urlSession(_ session: URLSession,
                         downloadTask: URLSessionDownloadTask,
                         didWriteData bytesWritten: Int64,
                         totalBytesWritten: Int64,
                         totalBytesExpectedToWrite: Int64) {
    if let id = downloadTask.taskDescription, let cb = progress[id] {
      cb(totalBytesWritten, totalBytesExpectedToWrite)
    }
  }

  public func urlSession(_ session: URLSession,
                         downloadTask: URLSessionDownloadTask,
                         didFinishDownloadingTo location: URL) {
    guard let id = downloadTask.taskDescription,
          let dst = destinations[id] else { return }
    let fm = FileManager.default
    try? fm.createDirectory(at: dst.deletingLastPathComponent(),
                            withIntermediateDirectories: true)
    try? fm.removeItem(at: dst)
    do {
      try fm.moveItem(at: location, to: dst)
      completion[id]?(dst.path, nil)
    } catch {
      completion[id]?(nil, error as NSError)
    }
    cleanup(id: id)
  }

  public func urlSession(_ session: URLSession,
                         task: URLSessionTask,
                         didCompleteWithError error: Error?) {
    guard let id = task.taskDescription else { return }
    if let error = error { completion[id]?(nil, error as NSError) }
    cleanup(id: id)
  }

  public func urlSessionDidFinishEvents(forBackgroundURLSession session: URLSession) {
    DispatchQueue.main.async {
      self.pendingSystemHandler?()
      self.pendingSystemHandler = nil
    }
  }

  private func cleanup(id: String) {
    progress.removeValue(forKey: id)
    completion.removeValue(forKey: id)
    destinations.removeValue(forKey: id)
  }
}

