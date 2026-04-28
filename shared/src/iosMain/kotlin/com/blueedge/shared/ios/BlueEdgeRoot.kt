/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Bridge entry point exposed to SwiftUI through the generated Objective-C
 * framework header. Swift calls `BlueEdgeRoot().controller()` to get a
 * `UIViewController` it can embed.
 */
package com.blueedge.shared.ios

import androidx.compose.ui.window.ComposeUIViewController
import com.blueedge.shared.ios.bridges.BlueEdgeIosBridges
import com.blueedge.shared.ios.bridges.IosBridgeRegistry
import com.blueedge.shared.ui.BlueEdgeApp
import platform.UIKit.UIViewController

object BlueEdgeRoot {
  /**
   * Called once from Swift before instantiating the Compose UI. The Swift
   * host passes its `BlueEdgeLlmBridge`, `BlueEdgeAuthBridge` and
   * `BlueEdgeDownloadBridge` instances wrapped in [BlueEdgeIosBridges].
   */
  fun start(bridges: BlueEdgeIosBridges) {
    IosBridgeRegistry.current = bridges
  }

  fun controller(): UIViewController = ComposeUIViewController { BlueEdgeApp() }
}

