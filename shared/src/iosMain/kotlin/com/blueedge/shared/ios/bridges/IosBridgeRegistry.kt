/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Process-global holder for the bridge instances Swift hands over at launch.
 * Set once in `BlueEdgeRoot.start(...)`, then read by `actual` providers.
 */
package com.blueedge.shared.ios.bridges

internal object IosBridgeRegistry {
  @Volatile var current: BlueEdgeIosBridges? = null
  fun require(): BlueEdgeIosBridges = checkNotNull(current) {
    "BlueEdgeIosBridges not initialised. Call BlueEdgeRoot.start(bridges:) from Swift."
  }
}

