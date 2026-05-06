package com.fortrx.network

import com.fortrx.Settings
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class FortrxApiError(val statusCode: Int, val detail: String, val context: String = "")
    : Exception("[$statusCode] $context: $detail")

expect fun httpEngineFactory(): HttpClientEngineFactory<*>

object Api {
    val baseUrl: String get() = Settings.serverUrl
    val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    @Volatile private var token: String? = null
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

    suspend fun getRequest(endpoint: String): HttpResponse =
        client.get(Settings.serverUrl + endpoint)

    suspend fun postJson(endpoint: String, body: JsonElement): HttpResponse =
        client.post(Settings.serverUrl + endpoint) {
            contentType(ContentType.Application.Json); setBody(body)
        }

    suspend fun postForm(endpoint: String, form: Map<String, String>,
        extraHeaders: Map<String, String> = emptyMap()): HttpResponse =
        client.post(Settings.serverUrl + endpoint) {
            contentType(ContentType.Application.FormUrlEncoded)
            extraHeaders.forEach { (k, v) -> header(k, v) }
            setBody(form.entries.joinToString("&") { (k, v) ->
                k.encodeURLParam() + "=" + v.encodeURLParam()
            })
        }

    suspend fun deleteRequest(endpoint: String): HttpResponse =
        client.delete(Settings.serverUrl + endpoint)

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
