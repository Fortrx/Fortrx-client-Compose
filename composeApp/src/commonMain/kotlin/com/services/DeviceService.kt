package com.fortrx.services

import com.fortrx.network.Api
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class LinkedDevice(
    val id: String,
    val name: String,
    val created_at: String,
    val last_seen: String?,
    val current: Boolean = false
)

@Serializable
data class PairingStart(val pairing_token: String, val numeric_code: String, val expires_at: String)

class DeviceService(private val api: Api) {
    suspend fun list(): List<LinkedDevice> =
        api.client.get("${api.baseUrl}/devices").body()

    suspend fun revoke(id: String) {
        api.client.delete("${api.baseUrl}/devices/$id")
    }

    suspend fun startLinking(): PairingStart =
        api.client.post("${api.baseUrl}/devices/link/start").body()

    suspend fun completeLinking(code: String, identityBundle: Map<String, String>): LinkedDevice =
        api.client.post("${api.baseUrl}/devices/link/complete") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("code" to code, "bundle" to identityBundle))
        }.body()
}
