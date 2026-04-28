/*
 * Copyright 2026 Blue Edge contributors.
 *
 * Cross-platform secure key-value storage. Backed by EncryptedSharedPreferences
 * on Android and Keychain Services on iOS.
 */
package com.blueedge.shared.storage

interface SecureStorage {
  fun putString(key: String, value: String)
  fun getString(key: String): String?
  fun remove(key: String)
  fun clear()
}

expect fun provideSecureStorage(): SecureStorage

