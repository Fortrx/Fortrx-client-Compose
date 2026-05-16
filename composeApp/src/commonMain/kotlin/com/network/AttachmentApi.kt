package com.fortrx.network

import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object AttachmentApi {
    suspend fun beginUpload(
        recipientId: Long,
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        sha256: String,
        ttlSeconds: Long? = null,
        body: Any,
    ): JsonObject {
        val response = Api.client.post("${Api.baseUrl}/attachments/upload") {
            header("X-Recipient-Id", recipientId)
            header("X-File-Name", fileName)
            header("X-Mime-Type", mimeType)
            header("X-Size-Bytes", sizeBytes)
            header("X-Sha256", sha256)
            ttlSeconds?.let { header("X-Ttl-Seconds", it) }
            contentType(ContentType.Application.OctetStream)
            setBody(body)
        }
        Api.raiseForStatus(response, "attachment_upload")
        return Api.jsonObject(response)
    }

    suspend fun download(attachmentId: String): HttpResponse {
        val response = Api.client.prepareGet("${Api.baseUrl}/attachments/$attachmentId/download").execute()
        Api.raiseForStatus(response, "attachment_download")
        return response
    }

    suspend fun ack(attachmentId: String): JsonObject {
        val response = Api.client.post("${Api.baseUrl}/attachments/$attachmentId/ack")
        Api.raiseForStatus(response, "attachment_ack")
        return Api.jsonObject(response)
    }

    suspend fun deleteRemote(attachmentId: String) {
        val response = Api.client.delete("${Api.baseUrl}/attachments/$attachmentId")
        if (!response.status.isSuccess()) {
            Api.raiseForStatus(response, "attachment_delete")
        }
    }

    fun responseHeader(response: HttpResponse, name: String): String? =
        response.headers[name]?.takeIf { it.isNotBlank() }
}
