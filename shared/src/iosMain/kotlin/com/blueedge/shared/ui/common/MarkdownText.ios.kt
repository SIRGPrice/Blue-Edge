/*
 * Copyright 2026 Blue Edge contributors.
 *
 * iOS `actual` for `MarkdownText`. Lightweight fallback that strips the
 * most common Markdown markers and renders plain `Text`. A future revision
 * can swap this with `richtext-ui-material3` once it exposes a Kotlin/Native
 * artifact, or with a custom `AnnotatedString` builder.
 */
package com.blueedge.shared.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

@Composable
actual fun MarkdownText(
  text: String,
  modifier: Modifier,
  smallFontSize: Boolean,
  textColor: Color,
  linkColor: Color,
) {
  val resolvedText = defaultMarkdownTextColor(textColor)
  val fontSize =
    if (smallFontSize) MaterialTheme.typography.bodyMedium.fontSize
    else MaterialTheme.typography.bodyLarge.fontSize
  Text(
    text = stripMarkdown(text),
    modifier = modifier,
    color = resolvedText,
    style = TextStyle(
      fontSize = fontSize,
      lineHeight = fontSize * if (smallFontSize) 1.4f else 1.5f,
      letterSpacing = 0.2.sp,
    ),
  )
}

/** Crude Markdown-to-plaintext stripper. Keeps semantics readable in the iOS fallback. */
private fun stripMarkdown(input: String): String {
  var s = input
  // Code fences and inline code.
  s = Regex("```[\\s\\S]*?```").replace(s) { it.value.trim('`').trim() }
  s = Regex("`([^`]*)`").replace(s, "$1")
  // Bold / italics / strikethrough.
  s = Regex("\\*\\*([^*]+)\\*\\*").replace(s, "$1")
  s = Regex("__([^_]+)__").replace(s, "$1")
  s = Regex("\\*([^*]+)\\*").replace(s, "$1")
  s = Regex("_([^_]+)_").replace(s, "$1")
  s = Regex("~~([^~]+)~~").replace(s, "$1")
  // Headers.
  s = Regex("(?m)^#{1,6}\\s+").replace(s, "")
  // Links: [label](url) -> label (url)
  s = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)").replace(s, "$1 ($2)")
  // List bullets.
  s = Regex("(?m)^\\s*[-*+]\\s+").replace(s, "• ")
  return s
}

