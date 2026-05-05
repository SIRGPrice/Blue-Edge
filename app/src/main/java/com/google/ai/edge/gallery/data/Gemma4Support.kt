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
package com.google.ai.edge.gallery.data

const val SUPPORTED_GEMMA4_MODEL_FAMILY = "Gemma 4 E2B/E4B"

private val NON_ALPHANUMERIC_MODEL_NAME_REGEX = Regex("[^a-z0-9]+")

fun isSupportedGemma4EdgeModelName(name: String?): Boolean {
  if (name.isNullOrBlank()) {
    return false
  }

  val normalized =
    name
      .substringBeforeLast('.')
      .lowercase()
      .replace(NON_ALPHANUMERIC_MODEL_NAME_REGEX, " ")
      .trim()

  if (!normalized.contains("gemma")) {
    return false
  }

  val hasGemma4Marker = normalized.contains("gemma4") || Regex("\\b4\\b").containsMatchIn(normalized)
  val hasSupportedSizeMarker = normalized.contains("e2b") || normalized.contains("e4b")

  return hasGemma4Marker && hasSupportedSizeMarker
}

