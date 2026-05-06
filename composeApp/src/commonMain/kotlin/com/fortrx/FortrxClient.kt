package com.fortrx

import com.fortrx.network.Api
import com.fortrx.services.OnboardingService
import com.fortrx.services.MessagingService
import com.fortrx.services.SyncEngine
import com.fortrx.storage.Db
import com.fortrx.storage.TokenStore
import kotlinx.coroutines.launch
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
    
    /**
     * Attempts to auto-initialize the client using saved credentials.
     * Returns true if successful (user is logged in and DB is open).
     */
    fun tryAutoLogin(password: String): Boolean {
        return try {
            Db.open(password)
            if (TokenStore.loadAndSetToken(password)) {
                Settings.storagePassword = password
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
                Settings.myId = userId
                syncEngine = SyncEngine(userId, "desktop-session", password)
                syncEngine?.start { }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun logout() {
        syncEngine?.stop()
        syncEngine = null
        TokenStore.deleteToken()
        Db.close()
        Api.setToken(null)
        Settings.storagePassword = null
        Settings.myId = null
    }
}
