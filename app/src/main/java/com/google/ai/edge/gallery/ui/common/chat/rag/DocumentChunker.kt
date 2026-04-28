/*
 * Copyright 2026 BlueEdge contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.google.ai.edge.gallery.ui.common.chat.rag

/**
 * Tiny, dependency-free chunker that splits a document's plain text into overlapping
 * chunks suitable for keyword retrieval (BM25 over SQLite FTS5).
 *
 * Strategy:
 *  1. Split the text into "paragraphs" using blank-line boundaries.
 *  2. Greedily pack paragraphs into chunks of ~[targetChars] chars.
 *  3. If a single paragraph is larger than [targetChars], split it by sentence
 *     boundaries (`.`, `!`, `?`, `\n`).
 *  4. If a single sentence is still larger than [targetChars], hard-split by char count.
 *  5. Each chunk overlaps with the previous one by [overlapChars] chars (taken from the
 *     tail of the previous chunk) so that information at chunk boundaries is reachable
 *     by both neighbouring chunks.
 *
 * The defaults (1500 / 200) yield chunks of roughly 400 tokens with 50 tokens of
 * overlap for SentencePiece tokenizers, which is a sweet spot for BM25 retrieval over
 * documents up to a few thousand pages.
 */
object DocumentChunker {

  data class Chunk(val index: Int, val text: String)

  fun chunk(
    fullText: String,
    targetChars: Int = 1500,
    overlapChars: Int = 200,
  ): List<Chunk> {
    if (fullText.isBlank()) return emptyList()
    val pieces = splitIntoPieces(fullText, targetChars)
    if (pieces.isEmpty()) return emptyList()

    val out = ArrayList<Chunk>()
    val current = StringBuilder()
    var idx = 0

    fun flush() {
      val text = current.toString().trim()
      if (text.isNotEmpty()) out.add(Chunk(idx++, text))
      current.setLength(0)
    }

    for (piece in pieces) {
      // If adding this piece would overshoot the target *and* we already have content,
      // emit the current chunk first.
      if (current.isNotEmpty() && current.length + piece.length + 1 > targetChars) {
        flush()
        // Seed the next chunk with the tail of the previous one to provide overlap.
        if (overlapChars > 0 && out.isNotEmpty()) {
          val prev = out.last().text
          val tail = prev.takeLast(overlapChars.coerceAtMost(prev.length))
          current.append(tail)
          if (!tail.endsWith('\n')) current.append('\n')
        }
      }
      current.append(piece)
      if (!piece.endsWith('\n')) current.append('\n')
    }
    if (current.isNotEmpty()) flush()
    return out
  }

  /**
   * Decomposes [text] into pieces that are guaranteed to be ≤ [targetChars] long.
   * Tries paragraph → sentence → hard-split in that order so that we keep semantic
   * units together when possible.
   */
  private fun splitIntoPieces(text: String, targetChars: Int): List<String> {
    val out = ArrayList<String>()
    val paragraphs = text.split(Regex("\\n\\s*\\n"))
    for (p in paragraphs) {
      val trimmed = p.trim()
      if (trimmed.isEmpty()) continue
      if (trimmed.length <= targetChars) {
        out.add(trimmed)
      } else {
        out.addAll(splitParagraph(trimmed, targetChars))
      }
    }
    return out
  }

  private fun splitParagraph(paragraph: String, targetChars: Int): List<String> {
    // Split on sentence boundaries while keeping the delimiters attached to the
    // preceding sentence.
    val sentences = ArrayList<String>()
    val current = StringBuilder()
    for (ch in paragraph) {
      current.append(ch)
      if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
        val s = current.toString().trim()
        if (s.isNotEmpty()) sentences.add(s)
        current.setLength(0)
      }
    }
    if (current.isNotEmpty()) {
      val s = current.toString().trim()
      if (s.isNotEmpty()) sentences.add(s)
    }

    val out = ArrayList<String>()
    val buf = StringBuilder()
    for (s in sentences) {
      if (s.length > targetChars) {
        if (buf.isNotEmpty()) {
          out.add(buf.toString().trim())
          buf.setLength(0)
        }
        // Hard split this very long sentence.
        var i = 0
        while (i < s.length) {
          val end = minOf(i + targetChars, s.length)
          out.add(s.substring(i, end))
          i = end
        }
      } else if (buf.length + s.length + 1 > targetChars) {
        out.add(buf.toString().trim())
        buf.setLength(0)
        buf.append(s)
      } else {
        if (buf.isNotEmpty()) buf.append(' ')
        buf.append(s)
      }
    }
    if (buf.isNotEmpty()) out.add(buf.toString().trim())
    return out
  }

  /**
   * Sanitiza una pregunta del usuario para que sea una expresión MATCH válida en FTS5.
   * Estrategia: extraer tokens alfanuméricos de longitud ≥ 2 (con tildes), descartar
   * stopwords muy frecuentes, escapar cada token entre comillas dobles y unirlos con
   * `OR`. Devuelve null si no queda ningún término utilizable.
   */
  fun buildFtsMatchQuery(userQuery: String): String? {
    val tokens = tokenize(userQuery)
    if (tokens.isEmpty()) return null
    return tokens.take(24).joinToString(separator = " OR ") { "\"${it.replace("\"", "")}\"*" }
  }

  /**
   * Normaliza un texto a una lista de tokens canónicos:
   *  - lowercase
   *  - fold de diacríticos (`é → e`, `ñ → n`, etc.) vía `Normalizer.NFD`
   *  - split por cualquier carácter no alfanumérico
   *  - longitud mínima 2
   *  - filtrado de stopwords frecuentes ES + EN
   *
   * Es el tokenizador canónico tanto para el indexado de chunks como para la query
   * en runtime — así garantizamos que ambos lados usan el mismo vocabulario.
   */
  fun tokenize(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    // NFD descompone "é" → "e" + combining-acute; luego eliminamos los combining marks.
    val folded = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
      .replace(Regex("\\p{Mn}+"), "")
      .lowercase()
    return folded
      .split(Regex("[^\\p{L}\\p{N}]+"))
      .asSequence()
      .filter { it.length >= 2 }
      .filter { it !in STOPWORDS_ES_EN }
      .toList()
  }

  /** Stopwords muy frecuentes en español + inglés. Lista corta a propósito (BM25 ya
   * penaliza palabras frecuentes); solo evitamos las que añaden ruido sin valor. */
  private val STOPWORDS_ES_EN: Set<String> = setOf(
    "de", "la", "el", "en", "y", "a", "los", "las", "un", "una", "que", "del", "al",
    "es", "se", "lo", "por", "con", "para", "no", "su", "sus", "como", "este", "esta",
    "estos", "estas", "ese", "esa", "esos", "esas", "pero", "mas", "ya", "muy",
    "the", "of", "and", "to", "in", "is", "it", "you", "that", "he", "was", "for",
    "on", "are", "as", "with", "his", "they", "be", "at", "this", "have", "from",
    "or", "by", "an", "we", "my", "your", "me",
  )
}

