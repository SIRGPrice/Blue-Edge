/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Real iOS OAuth client. Delegates to `BlueEdgeAuthBridge.swift` (AppAuth-iOS).
 */
package com.blueedge.shared.auth

import com.blueedge.shared.ios.bridges.IosBridgeRegistry
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private class IosOAuthClient : OAuthClient {

  override suspend fun authorize(config: OAuthConfig): OAuthTokens =
    suspendCancellableCoroutine { cont ->
      IosBridgeRegistry.require().auth.authorize(
        clientId = config.clientId,
        authEndpoint = config.authEndpoint,
        tokenEndpoint = config.tokenEndpoint,
        redirectUri = config.redirectUri,
        scopes = config.scopes,
        onSuccess = { access, refresh, exp ->
          cont.resume(OAuthTokens(access, refresh, exp?.toLong()))
        },
        onFailure = { msg -> cont.resumeWithException(RuntimeException(msg)) },
      )
    }

  override suspend fun refresh(config: OAuthConfig, refreshToken: String): OAuthTokens =
    // AppAuth-iOS handles refresh transparently; for an explicit refresh
    // we re-invoke the authorisation endpoint (Phase 2 will wire the
    // dedicated refresh-token grant once the bridge exposes it).
    authorize(config)

  override fun signOut() { /* tokens are stored by callers in SecureStorage */ }
}

actual fun provideOAuthClient(): OAuthClient = IosOAuthClient()

