package com.fortrx.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.fortrx.platform.AndroidContextHolder

actual object SettingsStore {
    private const val PREFS_NAME = "fortrx_secure_prefs"
    private const val FALLBACK_PREFS_NAME = "fortrx_secure_prefs_fallback"
    private const val KEY_PASSWORD = "storage_password"
    private const val KEY_MY_ID = "my_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_BACKUP_CODE = "backup_code"
    private const val KEY_DEVICE_ID = "device_id"

    private val prefs: SharedPreferences by lazy {
        val context = AndroidContextHolder.appContext
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        runCatching {
            createEncryptedPrefs(context, masterKeyAlias)
        }.recoverCatching {
            // Recover from corrupted encrypted prefs instead of crashing at app start.
            context.deleteSharedPreferences(PREFS_NAME)
            createEncryptedPrefs(context, masterKeyAlias)
        }.getOrElse {
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun createEncryptedPrefs(context: Context, masterKeyAlias: String): SharedPreferences =
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

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

    actual fun saveUsername(username: String) {
        prefs.edit().putString(KEY_USERNAME, username).apply()
    }

    actual fun loadUsername(): String? = prefs.getString(KEY_USERNAME, null)

    actual fun saveBackupCode(code: String) {
        prefs.edit().putString(KEY_BACKUP_CODE, code).apply()
    }

    actual fun loadBackupCode(): String? = prefs.getString(KEY_BACKUP_CODE, null)

    actual fun saveDeviceId(deviceId: String) {
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }

    actual fun loadDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }

    actual fun clear() {
        prefs.edit().clear().apply()
    }
}
