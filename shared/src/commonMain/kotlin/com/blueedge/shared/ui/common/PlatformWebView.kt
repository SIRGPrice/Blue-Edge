/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Cross-platform WebView surface backed by `WebView` on Android and
 * `WKWebView` on iOS. Use this whenever the shared UI needs to render
 * arbitrary HTML or load a URL — replaces the Android-only
 * `:app/.../ui/common/GalleryWebView.kt` once consumers migrate.
 */
package com.blueedge.shared.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

sealed interface WebContent {
  /** Loads raw HTML text. */
  data class Html(val html: String, val baseUrl: String? = null) : WebContent
  /** Loads a remote or local URL. */
  data class Url(val url: String) : WebContent
}

/**
 * Renders [content] inside a native web view.
 *
 * @param onUrlOpen invoked when the user taps an anchor; return `true` to
 *   intercept the navigation, `false` to let the platform handle it.
 */
@Composable
expect fun PlatformWebView(
  content: WebContent,
  modifier: Modifier = Modifier,
  onUrlOpen: (url: String) -> Boolean = { false },
)

