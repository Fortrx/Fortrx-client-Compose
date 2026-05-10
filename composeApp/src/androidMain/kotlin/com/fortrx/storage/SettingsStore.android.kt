package com.fortrx.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.fortrx.platform.AndroidContextHolder

actual object SettingsStore {
    private const val PREFS_NAME = "fortrx_secure_prefs"
    private const val KEY_PASSWORD = "storage_password"
    private const val KEY_MY_ID = "my_id"

    private val prefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            AndroidContextHolder.appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    actual fun saveStoragePassword(password: String) {
        prefs.edit().putString(KEY_PASSWORD, password).apply()
    }

    actual fun loadStoragePassword(): String? {
        return prefs.getString(KEY_PASSWORD, null)
    }

    actual fun saveMyId(id: Long) {
        prefs.edit().putLong(KEY_MY_ID, id).apply()
    }

    actual fun loadMyId(): Long? {
        val id = prefs.getLong(KEY_MY_ID, -1L)
        return if (id == -1L) null else id
    }

    actual fun clear() {
        prefs.edit().clear().apply()
    }
}
