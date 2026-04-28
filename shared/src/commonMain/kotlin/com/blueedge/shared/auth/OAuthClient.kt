/*
 * Copyright 2026 Blue Edge contributors.
 *
 * OAuth abstraction for HuggingFace authentication.
 *  - Android: net.openid.appauth (already used in :app).
 *  - iOS:     AppAuth-iOS via cinterop / Swift bridge.
 */
package com.blueedge.shared.auth

data class OAuthConfig(
  val clientId: String,
  val authEndpoint: String,
  val tokenEndpoint: String,
  val redirectUri: String,
  val scopes: List<String>,
)

data class OAuthTokens(
  val accessToken: String,
  val refreshToken: String?,
  val expiresAtEpochSeconds: Long?,
)

interface OAuthClient {
  suspend fun authorize(config: OAuthConfig): OAuthTokens
  suspend fun refresh(config: OAuthConfig, refreshToken: String): OAuthTokens
  fun signOut()
}

expect fun provideOAuthClient(): OAuthClient

