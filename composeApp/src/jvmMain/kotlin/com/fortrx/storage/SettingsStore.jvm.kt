package com.fortrx.storage

import java.util.prefs.Preferences

actual object SettingsStore {
    private const val KEY_PASSWORD = "storage_password"
    private const val KEY_MY_ID = "my_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_BACKUP_CODE = "backup_code"
    private val prefs: Preferences by lazy { Preferences.userRoot().node("com.fortrx.client") }

    actual fun saveStoragePassword(password: String) {
        prefs.put(KEY_PASSWORD, password)
    }

    actual fun loadStoragePassword(): String? {
        return prefs.get(KEY_PASSWORD, null)
    }

    actual fun saveMyId(id: Long) {
        prefs.putLong(KEY_MY_ID, id)
    }

    actual fun loadMyId(): Long? {
        val id = prefs.getLong(KEY_MY_ID, -1L)
        return if (id == -1L) null else id
    }

    actual fun saveUsername(username: String) {
        prefs.put(KEY_USERNAME, username)
    }

    actual fun loadUsername(): String? = prefs.get(KEY_USERNAME, null)

    actual fun saveBackupCode(code: String) {
        prefs.put(KEY_BACKUP_CODE, code)
    }

    actual fun loadBackupCode(): String? = prefs.get(KEY_BACKUP_CODE, null)

    actual fun clear() {
        prefs.clear()
    }
}
