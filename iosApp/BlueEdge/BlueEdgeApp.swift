// Copyright 2026 Blue Edge contributors.
//
// SwiftUI host that embeds the shared Compose Multiplatform UI exposed by
// the `BlueEdgeShared` framework produced by `:shared`.
//
// The Swift bridges defined under `Bridges/` implement Kotlin protocols
// declared in `shared/src/iosMain/.../bridges/Bridges.kt`. They are passed to
// `BlueEdgeRoot.start(bridges:)` once at launch so the shared code can call
// MediaPipe / AppAuth / URLSession transparently.

import SwiftUI
import UIKit
import BlueEdgeShared

@main
struct BlueEdgeApp: App {
  @UIApplicationDelegateAdaptor(BlueEdgeAppDelegate.self) private var appDelegate

  init() {
    // Hand the bridges to the Kotlin/Native side. Must happen before any
    // composable that resolves an LlmEngine / OAuthClient / DownloadManager.
    let bridges = BlueEdgeIosBridges(
      llm:      LlmBridgeAdapter(),
      auth:     AuthBridgeAdapter.shared,
      download: DownloadBridgeAdapter()
    )
    BlueEdgeRoot.shared.start(bridges: bridges)
  }

  var body: some Scene {
    WindowGroup {
      ComposeViewControllerRepresentable()
        .ignoresSafeArea()
        .onOpenURL { url in
          // Forward HuggingFace OAuth redirects back to AppAuth-iOS.
          _ = AuthBridgeAdapter.shared.resumeAuth(with: url)
        }
    }
  }
}

private struct ComposeViewControllerRepresentable: UIViewControllerRepresentable {
  func makeUIViewController(context: Context) -> UIViewController {
    BlueEdgeRoot.shared.controller()
  }
  func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// MARK: - AppDelegate (background URLSession events)

final class BlueEdgeAppDelegate: NSObject, UIApplicationDelegate {
  func application(_ application: UIApplication,
                   handleEventsForBackgroundURLSession identifier: String,
                   completionHandler: @escaping () -> Void) {
    // Wake up the background URLSession singleton so it can deliver pending
    // download events; the system will replay urlSessionDidFinishEvents.
    BlueEdgeDownloadBridge.shared.handleEvents(completionHandler: completionHandler)
  }
}

