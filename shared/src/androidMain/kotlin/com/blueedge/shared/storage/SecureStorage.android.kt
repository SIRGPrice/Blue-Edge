/*
 * Copyright 2026 Blue Edge contributors.
 */
package com.blueedge.shared.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.blueedge.shared.platform.AndroidContext

private class EncryptedPrefsStorage(context: Context) : SecureStorage {
  private val prefs = run {
    val masterKey = MasterKey.Builder(context)
      .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
      .build()
    EncryptedSharedPreferences.create(
      context,
      "blueedge_secure",
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  }

  override fun putString(key: String, value: String) {
    prefs.edit().putString(key, value).apply()
  }
  override fun getString(key: String): String? = prefs.getString(key, null)
  override fun remove(key: String) { prefs.edit().remove(key).apply() }
  override fun clear() { prefs.edit().clear().apply() }
}

actual fun provideSecureStorage(): SecureStorage =
  EncryptedPrefsStorage(AndroidContext.appContext)

