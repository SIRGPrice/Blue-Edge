/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Markdown rendering surface. Android delegates to the existing
 * `compose-richtext` Markdown renderer (already a dep of `:app`); iOS uses
 * a lightweight fallback (plain `Text`) until a Markdown library compatible
 * with Compose Multiplatform on Apple targets is wired in.
 */
package com.blueedge.shared.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.blueedge.shared.ui.theme.customColors

/**
 * Renders Markdown text with platform-native styling.
 *
 * @param textColor falls back to `MaterialTheme.colorScheme.onSurface` when [Color.Unspecified].
 * @param linkColor falls back to `MaterialTheme.customColors.linkColor` when [Color.Unspecified].
 */
@Composable
expect fun MarkdownText(
  text: String,
  modifier: Modifier = Modifier,
  smallFontSize: Boolean = false,
  textColor: Color = Color.Unspecified,
  linkColor: Color = Color.Unspecified,
)

@Composable
internal fun defaultMarkdownTextColor(textColor: Color): Color =
  if (textColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else textColor

@Composable
internal fun defaultMarkdownLinkColor(linkColor: Color): Color =
  if (linkColor == Color.Unspecified) MaterialTheme.customColors.linkColor else linkColor



