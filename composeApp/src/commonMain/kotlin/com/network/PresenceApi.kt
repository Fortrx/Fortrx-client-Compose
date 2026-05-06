package com.fortrx.network

import com.fortrx.Settings
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

object PresenceApi {
    suspend fun heartbeat(sessionId: String): JsonObject {
        val res = Api.client.post(Settings.serverUrl + "/presence/heartbeat") {
            contentType(ContentType.Application.Json)
            header("X-Client-Session", sessionId)
            setBody(buildJsonObject {})
        }
        return try {
            Api.raiseForStatus(res, "presence_heartbeat"); Api.jsonObject(res)
        } catch (e: FortrxApiError) {
            if (e.statusCode == 404) buildJsonObject {
                put("status", "unsupported"); put("ttl_seconds", 0)
            } else throw e
        }
    }

    suspend fun fetchPresenceContacts(): List<JsonObject> {
        val res = Api.getRequest("/presence/contacts")
        return try {
            Api.raiseForStatus(res, "fetch_presence_contacts")
            Api.json.parseToJsonElement(res.bodyAsText()).jsonArray.map { it.jsonObject }
        } catch (e: FortrxApiError) {
            if (e.statusCode == 404) emptyList() else throw e
        }
    }
}
