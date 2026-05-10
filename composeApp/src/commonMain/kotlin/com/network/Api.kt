package com.fortrx.network

import com.fortrx.Settings
import com.fortrx.storage.SettingsStore
import com.fortrx.storage.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FortrxApiError(val statusCode: Int, val detail: String, val context: String = "")
    : Exception("[$statusCode] $context: $detail")

expect fun httpEngineFactory(): HttpClientEngineFactory<*>

object Api {
    val baseUrl: String get() = Settings.serverUrl
    val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    @Volatile private var token: String? = null
    private val authRefreshMutex = Mutex()
    fun setToken(value: String?) { token = value }
    fun getToken(): String? = token

    val client: HttpClient by lazy {
        HttpClient(httpEngineFactory()) {
            install(ContentNegotiation) { json(this@Api.json) }
            install(WebSockets)
            install(HttpTimeout) {
                val ms = Settings.requestTimeoutSeconds * 1000
                requestTimeoutMillis = ms; connectTimeoutMillis = ms; socketTimeoutMillis = ms
            }
            defaultRequest {
                this@Api.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }
        }
    }

    private suspend fun rawGetRequest(endpoint: String): HttpResponse =
        client.get(Settings.serverUrl + endpoint)

    private suspend fun rawPostJson(endpoint: String, body: JsonElement): HttpResponse =
        client.post(Settings.serverUrl + endpoint) {
            contentType(ContentType.Application.Json); setBody(body)
        }

    private suspend fun rawPostForm(endpoint: String, form: Map<String, String>,
        extraHeaders: Map<String, String> = emptyMap()): HttpResponse =
        client.post(Settings.serverUrl + endpoint) {
            contentType(ContentType.Application.FormUrlEncoded)
            extraHeaders.forEach { (k, v) -> header(k, v) }
            setBody(form.entries.joinToString("&") { (k, v) ->
                k.encodeURLParam() + "=" + v.encodeURLParam()
            })
        }

    private suspend fun rawDeleteRequest(endpoint: String): HttpResponse =
        client.delete(Settings.serverUrl + endpoint)

    suspend fun getRequest(endpoint: String, allowAuthRetry: Boolean = true): HttpResponse =
        executeWithAuthRetry(endpoint, allowAuthRetry) { rawGetRequest(endpoint) }

    suspend fun postJson(endpoint: String, body: JsonElement, allowAuthRetry: Boolean = true): HttpResponse =
        executeWithAuthRetry(endpoint, allowAuthRetry) { rawPostJson(endpoint, body) }

    suspend fun postForm(
        endpoint: String,
        form: Map<String, String>,
        extraHeaders: Map<String, String> = emptyMap(),
        allowAuthRetry: Boolean = true,
    ): HttpResponse = executeWithAuthRetry(endpoint, allowAuthRetry) { rawPostForm(endpoint, form, extraHeaders) }

    suspend fun deleteRequest(endpoint: String, allowAuthRetry: Boolean = true): HttpResponse =
        executeWithAuthRetry(endpoint, allowAuthRetry) { rawDeleteRequest(endpoint) }

    suspend fun raiseForStatus(response: HttpResponse, context: String = "") {
        if (response.status.isSuccess()) return
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val detail = runCatching {
            json.parseToJsonElement(text).jsonObject["detail"]?.jsonPrimitive?.content
        }.getOrNull() ?: text
        throw FortrxApiError(response.status.value, detail, context)
    }

    suspend fun jsonObject(response: HttpResponse): JsonObject =
        json.parseToJsonElement(response.bodyAsText()).jsonObject

    suspend fun ensureAuthenticatedToken(): String? {
        return token ?: run {
            if (refreshAccessTokenIfPossible(null)) token else null
        }
    }

    suspend fun refreshAccessTokenIfPossible(previousToken: String? = token): Boolean {
        val username = Settings.myUsername ?: SettingsStore.loadUsername()
        val password = Settings.storagePassword ?: SettingsStore.loadStoragePassword()
        if (username.isNullOrBlank() || password.isNullOrBlank()) return false

        return authRefreshMutex.withLock {
            if (!token.isNullOrBlank() && token != previousToken) return@withLock true

            val response = runCatching {
                rawPostForm(
                    endpoint = "/auth/login",
                    form = mapOf("username" to username, "password" to password),
                )
            }.getOrNull() ?: return@withLock false

            if (!response.status.isSuccess()) return@withLock false
            val body = runCatching { jsonObject(response) }.getOrNull() ?: return@withLock false
            val refreshedToken = body["access_token"]?.jsonPrimitive?.contentOrNull ?: return@withLock false
            setToken(refreshedToken)
            Settings.myUsername = username
            try {
                TokenStore.saveToken(password, refreshedToken)
            } catch (_: Throwable) {
                // Keep the in-memory token even if secure persistence fails.
            }
            true
        }
    }

    private suspend inline fun executeWithAuthRetry(
        endpoint: String,
        allowAuthRetry: Boolean,
        crossinline call: suspend () -> HttpResponse,
    ): HttpResponse {
        val originalToken = token
        var response = call()
        if (!allowAuthRetry || !response.status.shouldRetryAuth()) return response
        if (!refreshAccessTokenIfPossible(originalToken)) return response
        response = call()
        return response
    }
}

fun String.encodeURLParam(): String = buildString {
    val hex = "0123456789ABCDEF"
    for (c in this@encodeURLParam) when {
        c.isLetterOrDigit() || c == '-' || c == '_' || c == '.' || c == '~' -> append(c)
        c == ' ' -> append('+')
        else -> for (b in c.toString().encodeToByteArray()) {
            val v = b.toInt() and 0xFF
            append('%'); append(hex[v ushr 4]); append(hex[v and 0xF])
        }
    }
}

private fun io.ktor.http.HttpStatusCode.shouldRetryAuth(): Boolean = value == 401 || value == 403
