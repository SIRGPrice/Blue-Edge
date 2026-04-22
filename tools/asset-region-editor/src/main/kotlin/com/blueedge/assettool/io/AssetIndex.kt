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

