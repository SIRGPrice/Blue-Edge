/*
 * Copyright 2026 Blue Edge contributors.
 *
 * iOS `actual` for `PlatformWebView`. Wraps `WKWebView` inside `UIKitView`.
 * Navigation interception is exposed via `WKNavigationDelegate.decidePolicy`.
 */
@file:OptIn(ExperimentalForeignApi::class)

package com.blueedge.shared.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

@Composable
actual fun PlatformWebView(
  content: WebContent,
  modifier: Modifier,
  onUrlOpen: (url: String) -> Boolean,
) {
  val webView = remember {
    WKWebView(frame = platform.CoreGraphics.CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = WKWebViewConfiguration())
  }
  val delegate = remember {
    object : NSObject(), WKNavigationDelegateProtocol {
      override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
      ) {
        val url = decidePolicyForNavigationAction.request.URL?.absoluteString
        if (url != null && onUrlOpen(url)) {
          decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
        } else {
          decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
        }
      }
    }
  }
  webView.navigationDelegate = delegate
  UIKitView(
    factory = { webView },
    modifier = modifier,
    update = { view ->
      when (content) {
        is WebContent.Html -> view.loadHTMLString(
          content.html,
          baseURL = content.baseUrl?.let { NSURL.URLWithString(it) },
        )
        is WebContent.Url -> NSURL.URLWithString(content.url)?.let {
          view.loadRequest(NSURLRequest(uRL = it))
        }
      }
    },
  )
}

