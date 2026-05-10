package com.fortrx

import com.fortrx.network.Api
import com.fortrx.services.OnboardingService
import com.fortrx.services.MessagingService
import com.fortrx.services.SyncEngine
import com.fortrx.platform.getPlatformName
import com.fortrx.storage.Db
import com.fortrx.storage.SettingsStore
import com.fortrx.storage.TokenStore
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonPrimitive

/**
 * The main "abstract" entry point for the Fortrx core logic.
 * Consolidates services and provides a unified interface for the UI.
 */
object FortrxClient {
    val onboarding = OnboardingService
    val messaging = MessagingService
    private var syncEngine: SyncEngine? = null

    fun isSyncRunning(): Boolean = syncEngine != null
    
    /**
     * Attempts to auto-initialize the client using saved credentials.
     * Returns true if successful (user is logged in and DB is open).
     */
    suspend fun tryAutoLogin(): Boolean {
        val password = SettingsStore.loadStoragePassword() ?: return false
        val userId = SettingsStore.loadMyId()
        return try {
            Db.open(password, userId)
            if (TokenStore.loadAndSetToken(password)) {
                Settings.storagePassword = password
                Settings.myId = userId
                Settings.myUsername = SettingsStore.loadUsername()
                startSyncEngine(password)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun tryAutoLogin(password: String, userId: Long? = null): Boolean {
        return try {
            Db.open(password, userId)
            if (TokenStore.loadAndSetToken(password)) {
                Settings.storagePassword = password
                Settings.myId = userId
                Settings.myUsername = SettingsStore.loadUsername()
                SettingsStore.saveStoragePassword(password)
                if (userId != null) SettingsStore.saveMyId(userId)
                startSyncEngine(password)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun startSyncEngine(password: String) {
        if (syncEngine != null) return
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        kotlinx.coroutines.GlobalScope.launch {
            try {
                val me = com.fortrx.network.AuthApi.getMe()
                val userId = me["id"]?.jsonPrimitive?.long ?: return@launch
                Settings.myUsername = me["username"]?.jsonPrimitive?.contentOrNull
                Settings.myId = userId
                val sessionId = "${getPlatformName()}-${userId}"
                syncEngine = SyncEngine(userId, sessionId, password)
                syncEngine?.start { }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopSyncEngine() {
        syncEngine?.stop()
        syncEngine = null
    }

    fun restartSyncEngine(password: String) {
        stopSyncEngine()
        startSyncEngine(password)
    }

    fun logout() {
        stopSyncEngine()
        MessagingService.resetCaches()
        TokenStore.deleteToken()
        Db.close()
        Api.setToken(null)
        Settings.storagePassword = null
        Settings.myId = null
        Settings.myUsername = null
        SettingsStore.clear()
    }
}
