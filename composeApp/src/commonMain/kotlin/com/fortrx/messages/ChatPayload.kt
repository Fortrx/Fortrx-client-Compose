package com.fortrx.messages

import com.fortrx.storage.PlatformFileStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// Inline attachments are still encoded inside the encrypted message body.
// This keeps the transport simple, but it means the practical ceiling is
// much lower than a true upload/download attachment pipeline.
const val MAX_INLINE_ATTACHMENT_BYTES: Int = 100 * 1024 * 1024

@Serializable
data class AttachmentPayload(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Int,
    val dataBase64: String,
    val localFileName: String? = null,
)

sealed interface ChatPayload {
    data class Text(val text: String) : ChatPayload
    data class Attachment(val attachment: AttachmentPayload) : ChatPayload
}

@OptIn(ExperimentalEncodingApi::class)
object ChatPayloadCodec {
    private const val PREFIX = "fortrx:payload:v1:"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class Envelope(
        val kind: String,
        val attachment: AttachmentPayload? = null,
    )

    fun encodeAttachment(attachment: AttachmentPayload): String {
        require(attachment.sizeBytes in 1..MAX_INLINE_ATTACHMENT_BYTES) {
            "Attachment must be between 1 byte and $MAX_INLINE_ATTACHMENT_BYTES bytes"
        }
        val envelope = Envelope(kind = "attachment", attachment = attachment)
        val bytes = json.encodeToString(Envelope.serializer(), envelope).encodeToByteArray()
        return PREFIX + Base64.encode(bytes)
    }

    fun decode(raw: String?): ChatPayload {
        if (raw.isNullOrEmpty()) return ChatPayload.Text("")
        if (!raw.startsWith(PREFIX)) return ChatPayload.Text(raw)

        val decoded = runCatching {
            Base64.decode(raw.removePrefix(PREFIX)).decodeToString()
        }.getOrNull() ?: return ChatPayload.Text(raw)

        val envelope = runCatching {
            json.decodeFromString(Envelope.serializer(), decoded)
        }.getOrNull() ?: return ChatPayload.Text(raw)

        return when (envelope.kind) {
            "attachment" -> {
                val att = envelope.attachment ?: return ChatPayload.Text(raw)
                // If the data is missing but we have a local file reference, load it.
                if (att.dataBase64.isEmpty() && att.localFileName != null) {
                    val bytes = PlatformFileStorage.readFile(att.localFileName)
                    if (bytes != null) {
                        return ChatPayload.Attachment(att.copy(dataBase64 = Base64.encode(bytes)))
                    }
                }
                ChatPayload.Attachment(att)
            }
            else -> ChatPayload.Text(raw)
        }
    }

    /**
     * If the payload contains a large attachment, saves it to disk and returns
     * a version of the payload with the data removed but a local file reference added.
     */
    fun slimForStorage(raw: String?): String? {
        if (raw == null || !raw.startsWith(PREFIX)) return raw
        val payload = decode(raw)
        if (payload is ChatPayload.Attachment && payload.attachment.dataBase64.isNotEmpty()) {
            val bytes = Base64.decode(payload.attachment.dataBase64)
            val localFile = PlatformFileStorage.saveFile(bytes)
            val slimmed = payload.attachment.copy(dataBase64 = "", localFileName = localFile)
            return encodeAttachment(slimmed)
        }
        return raw
    }

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

    fun formatSize(sizeBytes: Int): String {
        val bytes = sizeBytes.toLong()
        return when {
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    fun isImage(attachment: AttachmentPayload): Boolean = attachment.mimeType.startsWith("image/")
}
