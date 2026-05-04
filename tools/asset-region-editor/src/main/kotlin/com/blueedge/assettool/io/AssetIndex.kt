/*
 * Copyright 2026 SIRGPrice
 *
 * This file is part of Blue Edge: https://github.com/SIRGPrice/Blue-Edge
 *
 * Licensed under the Blue Edge Custom License 1.0.
 * You may not use this file except in compliance with that license.
 * GitHub may host, cache, display, and facilitate collaboration on this file
 * as required by the GitHub Terms of Service.
 * See the repository root: BLUE_EDGE_CUSTOM_LICENSE.md
 */
package com.blueedge.assettool.io

import java.io.File

object AssetIndex {
  /** Recursive list of PNG files under [RepoPaths.scanRoot], sorted by relative path. */
  fun listPngs(): List<File> {
    val root = RepoPaths.scanRoot
    if (!root.isDirectory) return emptyList()
    return root.walkTopDown()
      .filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
      .sortedBy { RepoPaths.relativeAssetPath(it) }
      .toList()
  }
}

