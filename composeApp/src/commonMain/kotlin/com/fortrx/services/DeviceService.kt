package com.fortrx.services

import com.fortrx.network.Api
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class LinkedDevice(
    val id: String,
    val name: String,
    val created_at: Long,
    val last_seen: Long?,
    val current: Boolean = false
)

@Serializable
data class PairingStart(
    val pairing_token: String,
    val numeric_code: String,
    val expires_at: Long,
    val pairing_uri: String,
)

@Serializable
data class PairingCompleteRequest(
    val pairing_token: String,
    val code: String,
    val identity_pub: String,
    val device_name: String,
)

@Serializable
data class PairingCompleteResponse(
    val access_token: String,
    val refresh_token: String,
    val token_type: String = "bearer",
    val device_id: String,
    val access_expires_at: Long? = null,
    val refresh_expires_at: Long? = null,
)

class DeviceService(private val api: Api = Api) {
    suspend fun list(): List<LinkedDevice> {
        val response = api.getRequest("/devices")
        api.raiseForStatus(response, "list_devices")
        return api.json.decodeFromString(response.bodyAsText())
    }

    suspend fun revoke(id: String) {
        val response = api.deleteRequest("/devices/$id")
        api.raiseForStatus(response, "revoke_device")
    }

    suspend fun startLinking(): PairingStart {
        val response = api.postJson(
            endpoint = "/devices/link/start",
            body = api.json.parseToJsonElement("{}"),
        )
        api.raiseForStatus(response, "start_device_linking")
        return api.json.decodeFromString(response.bodyAsText())
    }

    suspend fun completeLinking(
        pairingToken: String,
        code: String,
        identityPublicKey: String,
        deviceName: String,
    ): PairingCompleteResponse {
        val payload = PairingCompleteRequest(
            pairing_token = pairingToken,
            code = code,
            identity_pub = identityPublicKey,
            device_name = deviceName,
        )
        val response = api.postJson(
            endpoint = "/devices/link/complete",
            body = api.json.parseToJsonElement(api.json.encodeToString(payload)),
        )
        api.raiseForStatus(response, "complete_device_linking")
        return api.json.decodeFromString(response.bodyAsText())
    }
}
