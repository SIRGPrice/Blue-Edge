/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Maps platform-agnostic `iconKey` strings (used by [Task] and friends) to
 * Material Icons Extended `ImageVector`s. The registry lives in commonMain so
 * Android and iOS render the same iconography without per-platform mappings.
 *
 * Add new keys here as new tasks/categories are introduced.
 */
package com.blueedge.shared.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.EmojiNature
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.ui.graphics.vector.ImageVector

/** Resolves [key] to a Material icon, falling back to [Icons.Rounded.Apps]. */
fun iconFor(key: String?): ImageVector = when (key) {
  "chat" -> Icons.Rounded.Chat
  "auto_fix", "auto_fix_high" -> Icons.Rounded.AutoFixHigh
  "image" -> Icons.Rounded.Image
  "mic", "audio" -> Icons.Rounded.Mic
  "smart_toy", "agent" -> Icons.Rounded.SmartToy
  "emoji_nature", "garden" -> Icons.Rounded.EmojiNature
  else -> Icons.Rounded.Apps
}

