/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Multiplatform mirror of `:app/.../ui/common/EmptyState.kt`.
 * Replaces `@StringRes` placeholders with plain `String` values resolved
 * by callers (each platform looks them up however it likes — string keys,
 * `compose.resources`, raw literals).
 */
package com.blueedge.shared.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class EmptyStateButtonConfig(
  val buttonLabel: String,
  val buttonIcon: ImageVector? = null,
  val onButtonClick: () -> Unit = {},
  val extraContent: @Composable () -> Unit = {},
)

@Composable
fun EmptyState(
  icon: ImageVector,
  title: String,
  description: String,
  buttonConfig: EmptyStateButtonConfig? = null,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier.padding(horizontal = 48.dp),
  ) {
    Icon(
      icon,
      contentDescription = null,
      modifier = Modifier.size(56.dp),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      title,
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
    )
    Text(
      description,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    if (buttonConfig != null) {
      Box {
        Button(
          contentPadding = SMALL_BUTTON_CONTENT_PADDING,
          onClick = buttonConfig.onButtonClick,
        ) {
          if (buttonConfig.buttonIcon != null) {
            Icon(
              buttonConfig.buttonIcon,
              contentDescription = null,
              modifier = Modifier.padding(end = 8.dp).size(20.dp),
            )
          }
          Text(buttonConfig.buttonLabel)
        }
        buttonConfig.extraContent()
      }
    }
  }
}

