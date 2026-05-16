package com.fortrx.storage

/**
 * Interface for persisting sensitive settings like the storage password and user ID.
 */
expect object SettingsStore {
    fun saveStoragePassword(password: String)
    fun loadStoragePassword(): String?
    fun saveMyId(id: Long)
    fun loadMyId(): Long?
    fun saveUsername(username: String)
    fun loadUsername(): String?
    fun saveBackupCode(code: String)
    fun loadBackupCode(): String?
    fun saveDeviceId(deviceId: String)
    fun loadDeviceId(): String?
    fun clear()
}
