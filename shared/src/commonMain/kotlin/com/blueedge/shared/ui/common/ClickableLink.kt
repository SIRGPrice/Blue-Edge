/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Multiplatform mirror of `:app/.../ui/common/ClickableLink.kt`.
 * `LocalUriHandler` is multiplatform via Compose Multiplatform.
 */
package com.blueedge.shared.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.blueedge.shared.ui.theme.customColors

@Composable
fun buildTrackableUrlAnnotatedString(url: String, linkText: String): AnnotatedString {
  val uriHandler = LocalUriHandler.current
  return buildAnnotatedString {
    withLink(
      link = LinkAnnotation.Url(
        url = url,
        styles = TextLinkStyles(
          style = SpanStyle(
            color = MaterialTheme.customColors.linkColor,
            textDecoration = TextDecoration.Underline,
          ),
        ),
        linkInteractionListener = { uriHandler.openUri(url) },
      ),
    ) {
      append(linkText)
    }
  }
}

@Composable
fun ClickableLink(
  url: String,
  linkText: String,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  textAlign: TextAlign = TextAlign.Center,
) {
  val annotatedText = buildTrackableUrlAnnotatedString(url, linkText)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
    modifier = modifier,
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
    }
    Text(
      text = annotatedText,
      textAlign = textAlign,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(start = 6.dp),
    )
  }
}

