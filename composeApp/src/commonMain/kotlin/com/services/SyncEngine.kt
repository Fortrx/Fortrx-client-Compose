package com.fortrx.services

import com.fortrx.network.PresenceApi
import com.fortrx.network.WsClient
import com.fortrx.storage.Db
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Port of `client/services/daemon.py`.
 *
 * On Desktop/Android: launch [start] in a long-lived service.
 * On iOS: lifecycle is platform-controlled (foreground only); call [start]
 * when entering foreground and [stop] when backgrounded.
 */
class SyncEngine(
    private val userId: Long,
    private val sessionId: String,
    private val storagePassword: String,
) {
    private var scope: CoroutineScope? = null
    private var job: Job? = null

    fun start(onMessage: suspend (JsonObject) -> Unit) {
        if (job?.isActive == true) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        job = s.launch {
            launch { initialSync() }
            launch { heartbeatLoop() }
            launch {
                WsClient.listen(userId) { event ->
                    handleEvent(event)
                    onMessage(event)
                }
            }
        }
    }

    fun stop() {
        scope?.cancel(); scope = null; job = null
    }

    private suspend fun heartbeatLoop() {
        while (scope?.isActive == true) {
            try { PresenceApi.heartbeat(sessionId) } catch (_: Throwable) { /* tolerate */ }
            delay(20_000)
        }
    }

    private suspend fun initialSync() {
        try { MessagingService.fetchAndStoreInbox(storagePassword, userId) } catch (_: Throwable) { /* ignore */ }
        try { MessagingService.refreshPresenceCache(storagePassword) } catch (_: Throwable) { /* ignore */ }
    }

    private suspend fun handleEvent(event: JsonObject) {
        when (event["type"]?.jsonPrimitive?.contentOrNull) {
            "message_available" -> MessagingService.fetchAndStoreInbox(storagePassword, userId)
            "presence_changed" -> {
                val contactId = event["user_id"]?.jsonPrimitive?.longOrNull ?: return
                Db.upsertContact(
                    contactId,
                    event["username"]?.jsonPrimitive?.contentOrNull,
                    event["is_online"]?.jsonPrimitive?.booleanOrNull,
                )
            }
            "sync_hint" -> {
                MessagingService.fetchAndStoreInbox(storagePassword, userId)
                if (event["refresh_presence"]?.jsonPrimitive?.booleanOrNull == true) {
                    MessagingService.refreshPresenceCache(storagePassword)
                }
            }
        }
    }
}
