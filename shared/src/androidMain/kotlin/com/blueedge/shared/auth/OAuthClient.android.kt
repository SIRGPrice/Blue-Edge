/*
 * Copyright 2026 Blue Edge contributors.
 */
package com.blueedge.shared.auth

private class AndroidOAuthStub : OAuthClient {
  override suspend fun authorize(config: OAuthConfig): OAuthTokens =
    error("Wire net.openid.appauth from :app in Phase 2.")
  override suspend fun refresh(config: OAuthConfig, refreshToken: String): OAuthTokens =
    error("Wire net.openid.appauth from :app in Phase 2.")
  override fun signOut() {}
}

actual fun provideOAuthClient(): OAuthClient = AndroidOAuthStub()

