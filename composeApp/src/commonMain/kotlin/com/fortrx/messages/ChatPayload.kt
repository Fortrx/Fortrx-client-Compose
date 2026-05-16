package com.fortrx.messages

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

const val MAX_STREAMED_ATTACHMENT_BYTES: Long = 2L * 1024L * 1024L * 1024L

@Serializable
data class AttachmentPayload(
    val attachmentId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val mediaKeyBase64: String,
    val nonceBase64: String,
    val thumbnailBase64: String? = null,
    val localFileName: String? = null,
    val downloadedAt: String? = null,
)

sealed interface ChatPayload {
    data class Text(val text: String) : ChatPayload
    data class Attachment(val attachment: AttachmentPayload) : ChatPayload
}

@OptIn(ExperimentalEncodingApi::class)
object ChatPayloadCodec {
    private const val PREFIX = "fortrx:payload:v2:"
    private const val LEGACY_PREFIX = "fortrx:payload:v1:"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class Envelope(
        val kind: String,
        val attachment: AttachmentPayload? = null,
    )

    @Serializable
    private data class LegacyAttachmentPayload(
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Int,
        val dataBase64: String,
        val localFileName: String? = null,
    )

    @Serializable
    private data class LegacyEnvelope(
        val kind: String,
        val attachment: LegacyAttachmentPayload? = null,
    )

    fun encodeAttachment(attachment: AttachmentPayload): String {
        require(attachment.sizeBytes in 1..MAX_STREAMED_ATTACHMENT_BYTES) {
            "Attachment must be between 1 byte and $MAX_STREAMED_ATTACHMENT_BYTES bytes"
        }
        val envelope = Envelope(kind = "attachment", attachment = attachment)
        val bytes = json.encodeToString(Envelope.serializer(), envelope).encodeToByteArray()
        return PREFIX + Base64.encode(bytes)
    }

    fun decode(raw: String?): ChatPayload {
        if (raw.isNullOrEmpty()) return ChatPayload.Text("")
        if (raw.startsWith(PREFIX)) {
            val decoded = runCatching {
                Base64.decode(raw.removePrefix(PREFIX)).decodeToString()
            }.getOrNull() ?: return ChatPayload.Text(raw)
            val envelope = runCatching {
                json.decodeFromString(Envelope.serializer(), decoded)
            }.getOrNull() ?: return ChatPayload.Text(raw)
            return when (envelope.kind) {
                "attachment" -> envelope.attachment?.let { ChatPayload.Attachment(it) } ?: ChatPayload.Text(raw)
                else -> ChatPayload.Text(raw)
            }
        }
        if (raw.startsWith(LEGACY_PREFIX)) {
            val decoded = runCatching {
                Base64.decode(raw.removePrefix(LEGACY_PREFIX)).decodeToString()
            }.getOrNull() ?: return ChatPayload.Text(raw)
            val envelope = runCatching {
                json.decodeFromString(LegacyEnvelope.serializer(), decoded)
            }.getOrNull() ?: return ChatPayload.Text(raw)
            val attachment = envelope.attachment ?: return ChatPayload.Text(raw)
            return ChatPayload.Attachment(
                AttachmentPayload(
                    attachmentId = "",
                    fileName = attachment.fileName,
                    mimeType = attachment.mimeType,
                    sizeBytes = attachment.sizeBytes.toLong(),
                    sha256 = "",
                    mediaKeyBase64 = "",
                    nonceBase64 = "",
                    localFileName = attachment.localFileName,
                )
            )
        }
        return ChatPayload.Text(raw)
    }

    fun slimForStorage(raw: String?): String? = raw

    fun previewText(raw: String?): String = when (val payload = decode(raw)) {
        is ChatPayload.Text -> payload.text
        is ChatPayload.Attachment -> previewText(payload.attachment)
    }

    fun previewText(attachment: AttachmentPayload): String =
        if (attachment.mimeType.startsWith("image/")) {
            "Photo: ${attachment.fileName}"
        } else {
            "Attachment: ${attachment.fileName}"
        }

    fun formatSize(sizeBytes: Long): String {
        val bytes = sizeBytes.toDouble()
        return when {
            bytes >= 1024.0 * 1024.0 * 1024.0 -> {
                val gb = bytes / (1024.0 * 1024.0 * 1024.0)
                "${((gb * 10).toInt() / 10.0)} GB"
            }
            bytes >= 1024.0 * 1024.0 -> {
                val mb = bytes / (1024.0 * 1024.0)
                "${((mb * 10).toInt() / 10.0)} MB"
            }
            bytes >= 1024.0 -> {
                val kb = bytes / 1024.0
                "${((kb * 10).toInt() / 10.0)} KB"
            }
            else -> "$sizeBytes B"
        }
    }

    fun isImage(attachment: AttachmentPayload): Boolean = attachment.mimeType.startsWith("image/")
}
