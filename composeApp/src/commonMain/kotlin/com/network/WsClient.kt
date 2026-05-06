package com.fortrx.network

import com.fortrx.Settings
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

object WsClient {
    private fun wsUrl(): String = when {
        Settings.serverUrl.startsWith("https://") -> Settings.serverUrl.replaceFirst("https://", "wss://")
        Settings.serverUrl.startsWith("http://") -> Settings.serverUrl.replaceFirst("http://", "ws://")
        else -> Settings.serverUrl
    }

    suspend fun listen(userId: Long, token: String? = null,
        onMessage: suspend (JsonObject) -> Unit): Unit = withContext(Dispatchers.Default) {
        val authToken = token ?: Api.getToken() ?: throw IllegalStateException("No token, login first.")
        var retry = 2_000L
        while (isActive) {
            try {
                Api.client.webSocket(
                    urlString = "${wsUrl()}/ws/$userId",
                    request = { header(HttpHeaders.Authorization, "Bearer $authToken") },
                ) {
                    retry = 2_000L
                    coroutineScope {
                        val ka = launch { keepalive(this@webSocket) }
                        try {
                            for (frame in incoming) {
                                if (frame !is Frame.Text) continue
                                val raw = frame.readText()
                                if (raw == "\"pong\"" || raw == "pong") continue
                                val parsed = runCatching { Api.json.parseToJsonElement(raw) }.getOrNull() ?: continue
                                if (parsed !is JsonObject) continue
                                onMessage(parsed)
                            }
                        } finally { ka.cancel() }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Throwable) { /* retry */ }
            delay(retry); retry = (retry * 2).coerceAtMost(10_000L)
        }
    }

    private suspend fun keepalive(session: DefaultClientWebSocketSession, intervalMs: Long = 30_000L) {
        while (true) {
            delay(intervalMs)
            try { session.send("ping") } catch (_: Throwable) { break }
        }
    }
}
