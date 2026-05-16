package com.fortrx.network

import com.fortrx.Settings
import com.fortrx.platform.debugLog
import com.fortrx.platform.getPlatformName
import com.fortrx.platform.isDebugRuntime
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
import io.ktor.http.Url
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
    val baseUrl: String get() = normalizedBaseUrl()
    val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    @Volatile private var token: String? = null
    @Volatile private var deviceId: String? = null
    private val authRefreshMutex = Mutex()
    fun setToken(value: String?) { token = value }
    fun getToken(): String? = token
    fun setSession(session: AuthSession?) {
        token = session?.accessToken
        deviceId = session?.deviceId
        Settings.myDeviceId = session?.deviceId
    }

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
                this@Api.deviceId?.let { header("X-Device-Id", it) }
            }
        }
    }

    private suspend fun rawGetRequest(endpoint: String): HttpResponse {
        debugNetwork("GET $endpoint")
        return client.get(endpointUrl(endpoint)).also { debugNetwork("GET $endpoint -> ${it.status.value}") }
    }

    private suspend fun rawPostJson(endpoint: String, body: JsonElement): HttpResponse {
        debugNetwork("POST $endpoint")
        return client.post(endpointUrl(endpoint)) {
            contentType(ContentType.Application.Json); setBody(body)
        }.also { debugNetwork("POST $endpoint -> ${it.status.value}") }
    }

    private suspend fun rawPostForm(endpoint: String, form: Map<String, String>,
        extraHeaders: Map<String, String> = emptyMap()): HttpResponse {
        debugNetwork("POST_FORM $endpoint")
        return client.post(endpointUrl(endpoint)) {
            contentType(ContentType.Application.FormUrlEncoded)
            extraHeaders.forEach { (k, v) -> header(k, v) }
            setBody(form.entries.joinToString("&") { (k, v) ->
                k.encodeURLParam() + "=" + v.encodeURLParam()
            })
        }.also { debugNetwork("POST_FORM $endpoint -> ${it.status.value}") }
    }

    private suspend fun rawDeleteRequest(endpoint: String): HttpResponse {
        debugNetwork("DELETE $endpoint")
        return client.delete(endpointUrl(endpoint)).also { debugNetwork("DELETE $endpoint -> ${it.status.value}") }
    }

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
        val password = Settings.storagePassword ?: SettingsStore.loadStoragePassword()
        val session = TokenStore.loadSession(password)
        if (session?.refreshToken.isNullOrBlank()) {
            val username = Settings.myUsername ?: SettingsStore.loadUsername()
            if (username.isNullOrBlank() || password.isNullOrBlank()) return false
            return authRefreshMutex.withLock {
                if (!token.isNullOrBlank() && token != previousToken) return@withLock true
                val refreshed = runCatching {
                    AuthApi.login(username, password)
                }.getOrNull() ?: return@withLock false
                setSession(refreshed)
                try {
                    TokenStore.saveSession(refreshed, password)
                } catch (_: Throwable) {
                }
                Settings.myUsername = username
                true
            }
        }

        return authRefreshMutex.withLock {
            if (!token.isNullOrBlank() && token != previousToken) return@withLock true

            val refreshedSession = runCatching {
                AuthApi.refresh(session!!.refreshToken!!)
            }.getOrNull() ?: return@withLock false
            setSession(refreshedSession)
            Settings.myDeviceId = refreshedSession.deviceId ?: Settings.myDeviceId
            try {
                TokenStore.saveSession(refreshedSession, password)
            } catch (_: Throwable) {
            }
            true
        }
    }

    private suspend fun executeWithAuthRetry(
        endpoint: String,
        allowAuthRetry: Boolean,
        call: suspend () -> HttpResponse,
    ): HttpResponse = executeWithAuthRetryFlow(
        originalToken = token,
        allowAuthRetry = allowAuthRetry,
        call = call,
        shouldRetry = { it.status.shouldRetryAuth() },
        refresh = ::refreshAccessTokenIfPossible,
    )

    fun normalizedBaseUrl(rawUrl: String = Settings.serverUrl, allowInsecureLocal: Boolean = isDebugRuntime()): String {
        val candidate = rawUrl.trim().trimEnd('/')
        require(candidate.isNotEmpty()) { "Server URL is required" }
        val parsed = Url(candidate)
        val scheme = parsed.protocol.name.lowercase()
        require(scheme == "https" || scheme == "http") { "Server URL must use http or https" }
        require(parsed.encodedPath.isEmpty() || parsed.encodedPath == "/") { "Server URL must not include a path" }
        require(parsed.parameters.isEmpty()) { "Server URL must not include query parameters" }
        require(parsed.fragment.isEmpty()) { "Server URL must not include a fragment" }
        if (scheme == "http" && !(allowInsecureLocal && isLocalDevelopmentHost(parsed.host))) {
            throw IllegalArgumentException("Cleartext HTTP is only allowed for local development")
        }
        return candidate
    }

    fun websocketBaseUrl(baseUrl: String = normalizedBaseUrl()): String = when {
        baseUrl.startsWith("https://") -> baseUrl.replaceFirst("https://", "wss://")
        baseUrl.startsWith("http://") -> baseUrl.replaceFirst("http://", "ws://")
        else -> error("Unsupported base URL: $baseUrl")
    }

    private fun endpointUrl(endpoint: String): String {
        require(endpoint.startsWith("/")) { "Endpoint must start with '/': $endpoint" }
        return baseUrl + endpoint
    }

    private fun debugNetwork(message: String) {
        if (isDebugRuntime()) debugLog("[fortrx-network] $message")
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

internal fun isLocalDevelopmentHost(host: String): Boolean =
    host.equals("localhost", ignoreCase = true) ||
        host == "127.0.0.1" ||
        host == "10.0.2.2"

internal suspend fun <T> executeWithAuthRetryFlow(
    originalToken: String?,
    allowAuthRetry: Boolean,
    call: suspend () -> T,
    shouldRetry: (T) -> Boolean,
    refresh: suspend (String?) -> Boolean,
): T {
    var response = call()
    if (!allowAuthRetry || !shouldRetry(response)) return response
    if (!refresh(originalToken)) return response
    response = call()
    return response
}
