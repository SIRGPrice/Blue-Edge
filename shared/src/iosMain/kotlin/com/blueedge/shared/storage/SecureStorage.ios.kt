/*
 * Copyright 2026 Blue Edge contributors.
 *
 * iOS Keychain-backed secure storage.
 */
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package com.blueedge.shared.storage

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*

private const val SERVICE = "com.blueedge.shared"

private class KeychainStorage : SecureStorage {

  override fun putString(key: String, value: String) {
    delete(key)
    val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
    val query = mutableMapOf<Any?, Any?>(
      kSecClass to kSecClassGenericPassword,
      kSecAttrService to SERVICE,
      kSecAttrAccount to key,
      kSecValueData to data,
    )
    SecItemAdd(query as CFDictionaryRef, null)
  }

  override fun getString(key: String): String? {
    memScoped {
      val result = alloc<CFTypeRefVar>()
      val query = mutableMapOf<Any?, Any?>(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to SERVICE,
        kSecAttrAccount to key,
        kSecReturnData to kCFBooleanTrue,
        kSecMatchLimit to kSecMatchLimitOne,
      )
      val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
      if (status != errSecSuccess) return null
      val data = result.value as? NSData ?: return null
      return NSString.create(data, NSUTF8StringEncoding) as String?
    }
  }

  override fun remove(key: String) { delete(key) }

  override fun clear() {
    val query = mutableMapOf<Any?, Any?>(
      kSecClass to kSecClassGenericPassword,
      kSecAttrService to SERVICE,
    )
    SecItemDelete(query as CFDictionaryRef)
  }

  private fun delete(key: String) {
    val query = mutableMapOf<Any?, Any?>(
      kSecClass to kSecClassGenericPassword,
      kSecAttrService to SERVICE,
      kSecAttrAccount to key,
    )
    SecItemDelete(query as CFDictionaryRef)
  }
}

actual fun provideSecureStorage(): SecureStorage = KeychainStorage()

