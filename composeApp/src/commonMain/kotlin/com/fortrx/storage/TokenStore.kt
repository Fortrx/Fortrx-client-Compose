package com.fortrx.storage

import com.fortrx.Settings
import com.fortrx.network.AuthSession
import com.fortrx.network.Api

object TokenStore {
    suspend fun saveToken(token: String, password: String? = null) {
        val pw = password ?: Settings.storagePassword
            ?: throw StorageError("A storage password is required to save the token securely.")
        Db.saveSecret(pw, "auth", token)
    }
    suspend fun loadToken(password: String? = null): String? {
        val pw = password ?: Settings.storagePassword ?: return null
        return Db.loadSecret(pw, "auth")
    }
    suspend fun saveSession(session: AuthSession, password: String? = null) {
        val pw = password ?: Settings.storagePassword
            ?: throw StorageError("A storage password is required to save the session securely.")
        Db.saveSecret(pw, "auth", session.accessToken)
        session.refreshToken?.let { Db.saveSecret(pw, "refresh", it) } ?: Db.deleteSecret("refresh")
        session.deviceId?.let { Db.saveSecret(pw, "device_id", it) } ?: Db.deleteSecret("device_id")
        session.accessExpiresAt?.let { Db.saveSecret(pw, "access_expires_at", it.toString()) } ?: Db.deleteSecret("access_expires_at")
        session.refreshExpiresAt?.let { Db.saveSecret(pw, "refresh_expires_at", it.toString()) } ?: Db.deleteSecret("refresh_expires_at")
        session.deviceId?.let(SettingsStore::saveDeviceId)
    }
    suspend fun loadSession(password: String? = null): AuthSession? {
        val pw = password ?: Settings.storagePassword ?: return null
        val access = Db.loadSecret(pw, "auth") ?: return null
        return AuthSession(
            accessToken = access,
            refreshToken = Db.loadSecret(pw, "refresh"),
            deviceId = Db.loadSecret(pw, "device_id") ?: SettingsStore.loadDeviceId(),
            accessExpiresAt = Db.loadSecret(pw, "access_expires_at")?.toLongOrNull(),
            refreshExpiresAt = Db.loadSecret(pw, "refresh_expires_at")?.toLongOrNull(),
        )
    }
    fun deleteToken() = Db.deleteSecret("auth")
    fun deleteSession() {
        Db.deleteSecret("auth")
        Db.deleteSecret("refresh")
        Db.deleteSecret("device_id")
        Db.deleteSecret("access_expires_at")
        Db.deleteSecret("refresh_expires_at")
        SettingsStore.saveDeviceId("")
    }
    suspend fun loadAndSetToken(password: String? = null): Boolean {
        val session = loadSession(password) ?: return false
        Api.setSession(session)
        return true
    }
}
