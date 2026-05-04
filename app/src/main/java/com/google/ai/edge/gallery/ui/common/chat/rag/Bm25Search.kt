/*
 * Copyright 2026 SIRGPrice and Blue Edge contributors
 * Part of Blue Edge, a heavily modified app fork based on Google AI Edge Gallery.
 * Upstream project originally published by Google LLC:
 * https://github.com/google-ai-edge/gallery
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.common.chat.rag

import kotlin.math.ln

/**
 * BM25 (Okapi) en Kotlin puro. Sustituye al `bm25(...)` de SQLite FTS5 — necesario
 * porque algunas builds de SQLite del firmware Android no incluyen el módulo `fts5`.
 *
 * Características:
 *  - Parámetros estándar: k1 = 1.5, b = 0.75 (defaults Lucene).
 *  - IDF y `avgdl` calculados sobre el sub-corpus pasado a [score] (no sobre toda la
 *    base de datos), porque queremos rankear los chunks de los docs que el usuario
 *    está adjuntando ahora mismo.
 *  - Single pass: O(N · |Q|) donde N = chunks del sub-corpus, |Q| = tokens de la
 *    query. Para N ≈ 10k y |Q| ≈ 10 → ~10 ms en un Pixel 7 mid-range.
 */
object Bm25Search {

  private const val K1 = 1.5
  private const val B = 0.75

  /** Una fila/chunk del sub-corpus a evaluar. [tokens] ya está pre-normalizado por
   *  [DocumentChunker.tokenize]. */
  data class Row(
    val docId: String,
    val chunkIdx: Int,
    val content: String,
    val tokens: List<String>,
    val tokenCount: Int,
  )

  fun score(
    queryTokens: List<String>,
    rows: List<Row>,
    topK: Int,
  ): List<PermanentDocumentStore.ChunkHit> {
    if (queryTokens.isEmpty() || rows.isEmpty()) return emptyList()
    val n = rows.size
    val avgdl = rows.sumOf { it.tokenCount }.toDouble() / n.coerceAtLeast(1)

    // ---- 1. Document frequency (df) por término único de la query --------------
    val uniqueQueryTokens = queryTokens.toSet()
    val df = HashMap<String, Int>(uniqueQueryTokens.size)
    for (row in rows) {
      // Set de tokens del chunk para contar presencia (no frecuencia) por chunk.
      val present = row.tokens.toHashSet()
      for (t in uniqueQueryTokens) {
        if (t in present) df.merge(t, 1) { a, _ -> a + 1 }
      }
    }

    // ---- 2. IDF por término único ---------------------------------------------
    // Fórmula clásica BM25: ln((N - df + 0.5) / (df + 0.5) + 1).
    val idf = HashMap<String, Double>(uniqueQueryTokens.size)
    for (t in uniqueQueryTokens) {
      val dft = df[t] ?: 0
      val v = ln((n - dft + 0.5) / (dft + 0.5) + 1.0)
      // Solo conservamos términos que aparecen en al menos 1 chunk; los demás no
      // contribuyen y nos ahorramos el bucle interno.
      if (dft > 0) idf[t] = v
    }
    if (idf.isEmpty()) return emptyList()

    // ---- 3. Score por chunk ---------------------------------------------------
    // Multiplicador BM25 común a cada chunk: k1 * (1 - b + b * dl/avgdl).
    val scored = ArrayList<Pair<Row, Double>>(rows.size)
    for (row in rows) {
      // Term frequencies del chunk (solo nos interesan los tokens de la query).
      val tfMap = HashMap<String, Int>()
      for (t in row.tokens) {
        if (t in idf) tfMap.merge(t, 1) { a, _ -> a + 1 }
      }
      if (tfMap.isEmpty()) continue
      val dl = row.tokenCount.toDouble()
      val norm = K1 * (1 - B + B * (dl / (if (avgdl > 0) avgdl else 1.0)))
      var s = 0.0
      for ((t, tf) in tfMap) {
        val idfT = idf[t] ?: continue
        s += idfT * (tf * (K1 + 1)) / (tf + norm)
      }
      if (s > 0) scored.add(row to s)
    }

    // ---- 4. Top-K -------------------------------------------------------------
    return scored
      .sortedByDescending { it.second }
      .take(topK.coerceAtLeast(1))
      .map { (row, s) ->
        PermanentDocumentStore.ChunkHit(
          docId = row.docId,
          chunkIdx = row.chunkIdx,
          text = row.content,
          score = s,
        )
      }
  }
}

