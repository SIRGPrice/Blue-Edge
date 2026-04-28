/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Default OAuth configuration for HuggingFace. The clientId is wired to the
 * Blue Edge OAuth app registered with HuggingFace; redirectUri matches the
 * `appAuthRedirectScheme` used by `:app/build.gradle.kts` (Android) and the
 * `CFBundleURLSchemes` declared in `iosApp/BlueEdge/Info.plist` (iOS).
 *
 * If the app distributor wants to swap to a different OAuth client (e.g.,
 * for a forked deployment) only this file needs to change.
 */
package com.blueedge.shared.auth

object HuggingFaceOAuth {
  /** OAuth client id registered with HuggingFace. */
  const val CLIENT_ID = "blueedge"

  const val AUTH_ENDPOINT = "https://huggingface.co/oauth/authorize"
  const val TOKEN_ENDPOINT = "https://huggingface.co/oauth/token"

  /**
   * Custom URL scheme. Both Android (`AndroidManifest`/`appAuthRedirectScheme`)
   * and iOS (`CFBundleURLSchemes`) must declare this scheme.
   */
  const val REDIRECT_URI = "blueedge-huggingface://oauth/callback"

  val SCOPES: List<String> = listOf("read-repos", "inference-api")

  fun defaultConfig(): OAuthConfig = OAuthConfig(
    clientId = CLIENT_ID,
    authEndpoint = AUTH_ENDPOINT,
    tokenEndpoint = TOKEN_ENDPOINT,
    redirectUri = REDIRECT_URI,
    scopes = SCOPES,
  )
}

