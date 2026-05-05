/*
 * Copyright 2026 SIRGPrice
 *
 * This file is part of Blue Edge: https://github.com/SIRGPrice/Blue-Edge
 *
 * Licensed under the Blue Edge Custom License 1.0.
 * You may not use this file except in compliance with that license.
 * GitHub may host, cache, display, and facilitate collaboration on this file
 * as required by the GitHub Terms of Service.
 * See the repository root: LICENSE.md
 */
package com.blueedge.assettool.io

import java.io.File

/** Resolves repo-relative paths starting from the current working directory. */
object RepoPaths {

  /** Climb up from [start] until we find settings.gradle.kts. */
  fun findRepoRoot(start: File = File(System.getProperty("user.dir"))): File {
    var cur: File? = start.absoluteFile
    while (cur != null) {
      if (File(cur, "settings.gradle.kts").isFile) return cur
      cur = cur.parentFile
    }
    error("Could not locate repo root (settings.gradle.kts) from ${start.absolutePath}")
  }

  val repoRoot: File by lazy { findRepoRoot() }

  /** Android assets root where all PNGs live. */
  val assetsRoot: File get() = File(repoRoot, "app/src/main/assets")

  /** Scan scope for the editor (avoid listing huge unrelated asset trees). */
  val scanRoot: File get() = File(assetsRoot, "stocatstic")

  /** JSON catalog file (source of truth). */
  val regionsJson: File
    get() = File(repoRoot, "tools/asset-regions/asset_regions.json")

  /** Generated Kotlin output. */
  val generatedKt: File
    get() = File(
      repoRoot,
      "app/src/main/java/com/google/ai/edge/gallery/customtasks/stocatstic/ui/assets/AssetCatalogs.generated.kt"
    )

  /** Converts an absolute asset path into the "stocatstic/..." form stored in JSON. */
  fun relativeAssetPath(absolute: File): String {
    val rel = absolute.absoluteFile.relativeTo(assetsRoot.absoluteFile).invariantSeparatorsPath
    require(!rel.startsWith("..")) { "File $absolute is not inside assets root" }
    return rel
  }

  fun resolveAsset(relative: String): File =
    File(assetsRoot, relative.replace('/', File.separatorChar))
}

