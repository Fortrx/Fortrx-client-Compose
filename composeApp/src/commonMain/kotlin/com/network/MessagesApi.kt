package com.fortrx.network

import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

object MessagesApi {
    suspend fun sendMessage(recipientId: Long, sealedBlob: String, messageNumber: Long,
        ttlSeconds: Long? = null): JsonObject {
        require(recipientId > 0) { "Recipient id must be positive" }
        val body = buildJsonObject {
            put("recipient_id", recipientId); put("sealed_blob", sealedBlob)
            put("message_number", messageNumber)
            ttlSeconds?.let { put("ttl_seconds", it) }
        }
        val res = Api.postJson("/messages/send", body)
        Api.raiseForStatus(res, "send_message"); return Api.jsonObject(res)
    }

    suspend fun fetchInbox(): List<JsonObject> {
        val res = Api.getRequest("/messages/inbox")
        Api.raiseForStatus(res, "fetch_inbox")
        return Api.json.parseToJsonElement(res.bodyAsText()).jsonArray.map { it.jsonObject }
    }

    suspend fun fetchConversation(otherUserId: Long): List<JsonObject> {
        require(otherUserId > 0) { "User id must be positive" }
        val res = Api.getRequest("/messages/conversation/$otherUserId")
        Api.raiseForStatus(res, "fetch_conversation")
        return Api.json.parseToJsonElement(res.bodyAsText()).jsonArray.map { it.jsonObject }
    }

    suspend fun confirmDelivery(messageId: Long): JsonObject {
        require(messageId > 0) { "Message id must be positive" }
        val res = Api.deleteRequest("/messages/$messageId/confirm")
        Api.raiseForStatus(res, "confirm_delivery"); return Api.jsonObject(res)
    }
}
