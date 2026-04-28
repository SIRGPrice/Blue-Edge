// Copyright 2026 Blue Edge contributors.
//
// Swift wrapper around AppAuth-iOS — direct equivalent of the
// `net.openid.appauth` library used on Android for HuggingFace OAuth.

import Foundation
import UIKit
import AppAuth

@objc public final class BlueEdgeAuthBridge: NSObject {

  /// Held while a flow is in progress (per AppAuth-iOS contract).
  private var currentSession: OIDExternalUserAgentSession?

  @objc public override init() { super.init() }

  @objc public func authorize(clientId: String,
                              authEndpoint: String,
                              tokenEndpoint: String,
                              redirectUri: String,
                              scopes: [String],
                              presenting: UIViewController,
                              onSuccess: @escaping (String, String?, NSNumber?) -> Void,
                              onFailure: @escaping (NSError) -> Void) {
    guard let auth = URL(string: authEndpoint),
          let token = URL(string: tokenEndpoint),
          let redirect = URL(string: redirectUri) else {
      onFailure(NSError(domain: "BlueEdgeAuth", code: -10,
        userInfo: [NSLocalizedDescriptionKey: "Invalid OAuth URLs"]))
      return
    }
    let configuration = OIDServiceConfiguration(authorizationEndpoint: auth,
                                                tokenEndpoint: token)
    let request = OIDAuthorizationRequest(
      configuration: configuration,
      clientId: clientId,
      clientSecret: nil,
      scopes: scopes,
      redirectURL: redirect,
      responseType: OIDResponseTypeCode,
      additionalParameters: nil)

    self.currentSession = OIDAuthState.authState(
      byPresenting: request,
      presenting: presenting) { authState, error in
        if let error = error {
          onFailure(error as NSError); return
        }
        guard let access = authState?.lastTokenResponse?.accessToken else {
          onFailure(NSError(domain: "BlueEdgeAuth", code: -11,
            userInfo: [NSLocalizedDescriptionKey: "No access token"]))
          return
        }
        let refresh = authState?.lastTokenResponse?.refreshToken
        let exp = authState?.lastTokenResponse?.accessTokenExpirationDate?.timeIntervalSince1970
        onSuccess(access, refresh, exp.map { NSNumber(value: $0) })
      }
  }

  /// Forwards a redirect URL back to AppAuth from the SwiftUI scene delegate.
  @objc public func resume(with url: URL) -> Bool {
    return self.currentSession?.resumeExternalUserAgentFlow(with: url) ?? false
  }
}

