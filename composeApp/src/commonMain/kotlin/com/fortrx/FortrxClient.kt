package com.fortrx

import com.fortrx.network.Api
import com.fortrx.platform.debugLog
import com.fortrx.services.OnboardingService
import com.fortrx.services.MessagingService
import com.fortrx.services.SyncEngine
import com.fortrx.platform.getPlatformName
import com.fortrx.storage.Db
import com.fortrx.storage.SettingsStore
import com.fortrx.storage.TokenStore
import com.fortrx.platform.startBackgroundSync
import com.fortrx.platform.stopBackgroundSync
import kotlinx.coroutines.*
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonPrimitive

/**
 * The main "abstract" entry point for the Fortrx core logic.
 * Consolidates services and provides a unified interface for the UI.
 */
class FortrxClient(
    val onboarding: OnboardingService,
    val messaging: MessagingService
) {
    private var clientScope = CoroutineScope(SupervisorJob() + Dispatchers.Default) // FIXED: Replace GlobalScope in FortrxClient
    private var syncEngine: SyncEngine? = null

    fun isSyncRunning(): Boolean = syncEngine != null
    
    /**
     * Attempts to auto-initialize the client using saved credentials.
     * Returns true if successful (user is logged in and DB is open).
     */
    suspend fun tryAutoLogin(): Boolean = withContext(Dispatchers.Default) {
        val password = SettingsStore.loadStoragePassword() ?: return@withContext false
        val userId = SettingsStore.loadMyId()
        return@withContext try {
            Db.open(password, userId)
            if (TokenStore.loadAndSetToken(password)) {
                Settings.storagePassword = password
                Settings.myId = userId
                Settings.myUsername = SettingsStore.loadUsername()
                Settings.myDeviceId = SettingsStore.loadDeviceId()
                startSyncEngine(password)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun tryAutoLogin(password: String, userId: Long? = null): Boolean = withContext(Dispatchers.Default) {
        return@withContext try {
            Db.open(password, userId)
            if (TokenStore.loadAndSetToken(password)) {
                Settings.storagePassword = password
                Settings.myId = userId
                Settings.myUsername = SettingsStore.loadUsername()
                Settings.myDeviceId = SettingsStore.loadDeviceId()
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
        clientScope.launch { // FIXED: Replace GlobalScope in FortrxClient
            try {
                val me = com.fortrx.network.AuthApi.getMe()
                val userId = me["id"]?.jsonPrimitive?.long ?: return@launch
                Settings.myUsername = me["username"]?.jsonPrimitive?.contentOrNull
                Settings.myId = userId
                val stableDeviceId = Settings.myDeviceId ?: SettingsStore.loadDeviceId()
                val sessionId = stableDeviceId ?: "${getPlatformName()}-${userId}"
                syncEngine = SyncEngine(userId, sessionId, password, messaging)
                syncEngine?.start { }
                startBackgroundSync()
            } catch (e: Exception) {
                debugLog("Sync engine startup failed.", e)
            }
        }
    }

    fun stopSyncEngine() {
        syncEngine?.stop()
        syncEngine = null
        stopBackgroundSync()
    }

    fun restartSyncEngine(password: String) {
        stopSyncEngine()
        startSyncEngine(password)
    }

    fun logout() {
        stopSyncEngine()
        clientScope.cancel() // FIXED: Replace GlobalScope in FortrxClient
        clientScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        messaging.resetCaches()
        TokenStore.deleteSession()
        Db.close()
        Api.setSession(null)
        Settings.storagePassword = null
        Settings.myId = null
        Settings.myUsername = null
        Settings.myDeviceId = null
        SettingsStore.clear()
    }
}
