/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Android `actual` for `PlatformWebView`. Wraps a `android.webkit.WebView`
 * inside `AndroidView`. Intentionally minimal: callers extend through the
 * `onUrlOpen` callback. JavaScript is enabled to keep parity with the
 * existing `:app` `GalleryWebView`.
 */
package com.blueedge.shared.ui.common

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PlatformWebView(
  content: WebContent,
  modifier: Modifier,
  onUrlOpen: (url: String) -> Boolean,
) {
  AndroidView(
    modifier = modifier,
    factory = { ctx ->
      WebView(ctx).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = object : WebViewClient() {
          override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?,
          ): Boolean {
            val url = request?.url?.toString() ?: return false
            return onUrlOpen(url)
          }
        }
      }
    },
    update = { view ->
      when (content) {
        is WebContent.Html -> view.loadDataWithBaseURL(
          content.baseUrl,
          content.html,
          "text/html",
          "UTF-8",
          null,
        )
        is WebContent.Url -> view.loadUrl(content.url)
      }
    },
  )
}

