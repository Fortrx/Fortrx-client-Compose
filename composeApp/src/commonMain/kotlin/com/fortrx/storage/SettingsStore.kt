package com.fortrx.storage

/**
 * Interface for persisting sensitive settings like the storage password and user ID.
 */
expect object SettingsStore {
    fun saveStoragePassword(password: String)
    fun loadStoragePassword(): String?
    fun saveMyId(id: Long)
    fun loadMyId(): Long?
    fun clear()
}
