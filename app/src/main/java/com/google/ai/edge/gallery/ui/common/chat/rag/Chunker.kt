/*
 * Copyright 2026 BlueEdge contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.google.ai.edge.gallery.ui.common.chat.rag

/**
 * Turns a long piece of extracted text into overlapping chunks suitable for retrieval.
 * Strategy:
 * 1. Prefer splitting at blank lines / line breaks / sentence terminators.
 * 2. Keep each chunk under [maxChunkChars] characters.
 * 3. Overlap consecutive chunks by [overlapChars] characters so context at the boundary
 *    is not lost during retrieval.
 */
object Chunker {

  private const val DEFAULT_MAX_CHARS = 900
  private const val DEFAULT_OVERLAP_CHARS = 150
  private val BREAK_PRIORITY = listOf("\n\n", "\n", ". ", "? ", "! ", "; ", ", ", " ")

  fun chunk(
    text: String,
    maxChunkChars: Int = DEFAULT_MAX_CHARS,
    overlapChars: Int = DEFAULT_OVERLAP_CHARS,
  ): List<String> {
    if (text.isBlank()) return emptyList()
    val cleaned = text.trim()
    if (cleaned.length <= maxChunkChars) return listOf(cleaned)

    val chunks = mutableListOf<String>()
    var start = 0
    while (start < cleaned.length) {
      val targetEnd = minOf(cleaned.length, start + maxChunkChars)
      val end = if (targetEnd >= cleaned.length) {
        targetEnd
      } else {
        findBreakpoint(cleaned, start, targetEnd) ?: targetEnd
      }
      val piece = cleaned.substring(start, end).trim()
      if (piece.isNotEmpty()) chunks.add(piece)
      if (end >= cleaned.length) break
      start = maxOf(end - overlapChars, start + 1)
    }
    return chunks
  }

  private fun findBreakpoint(text: String, start: Int, hardEnd: Int): Int? {
    // Prefer breaking at a "nice" boundary close to hardEnd, but never earlier than 60 %
    // of the window to avoid shrinking chunks to almost nothing.
    val minEnd = start + ((hardEnd - start) * 0.6).toInt().coerceAtLeast(1)
    for (separator in BREAK_PRIORITY) {
      val idx = text.lastIndexOf(separator, hardEnd - 1)
      if (idx in minEnd..hardEnd) return idx + separator.length
    }
    return null
  }
}


