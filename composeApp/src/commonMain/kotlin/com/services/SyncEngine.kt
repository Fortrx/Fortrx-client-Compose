package com.fortrx.services

import com.fortrx.messages.ChatPayloadCodec
import com.fortrx.platform.NotificationBridge
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
        var delayMs = 20_000L
        while (scope?.isActive == true) {
            try {
                val response = PresenceApi.heartbeat(sessionId)
                val ttlSeconds = response["ttl_seconds"]?.jsonPrimitive?.longOrNull ?: 0L
                delayMs = if (ttlSeconds > 0) {
                    (ttlSeconds / 2).coerceIn(10L, 20L) * 1000L
                } else {
                    60_000L
                }
            } catch (_: Throwable) {
                delayMs = 20_000L
            }
            delay(delayMs)
        }
    }

    private suspend fun initialSync() {
        try { MessagingService.fetchAndStoreInbox(storagePassword, userId) } catch (_: Throwable) { /* ignore */ }
        try { MessagingService.refreshPresenceCache(storagePassword) } catch (_: Throwable) { /* ignore */ }
    }

    private suspend fun handleEvent(event: JsonObject) {
        try {
            when (event["type"]?.jsonPrimitive?.contentOrNull) {
                "message_available" -> {
                    val synced = MessagingService.fetchAndStoreInbox(storagePassword, userId)
                    synced.forEach { incoming ->
                        val senderId = incoming["sender_id"]?.jsonPrimitive?.longOrNull ?: return@forEach
                        val sender = Db.getContact(senderId)?.username ?: "New message"
                        val preview = ChatPayloadCodec.previewText(incoming["body"]?.jsonPrimitive?.contentOrNull)
                        NotificationBridge.showIncomingMessage(sender, preview)
                    }
                }
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
