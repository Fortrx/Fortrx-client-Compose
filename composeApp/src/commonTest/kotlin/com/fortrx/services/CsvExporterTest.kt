package com.fortrx.services

import com.fortrx.messages.AttachmentPayload
import com.fortrx.messages.ChatPayloadCodec
import com.fortrx.storage.Db
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CsvExporterTest {
    @Test
    fun exportMessages_skipsAttachmentOnlyRows() {
        val textMessage = Db.StoredMessage(
            id = 1L,
            serverMessageId = null,
            contactId = 7L,
            direction = "incoming",
            senderId = 7L,
            recipientId = 11L,
            messageNumber = null,
            plaintext = "hello from fortrx",
            createdAt = "2026-05-14T10:00:00",
            status = "delivered",
            isPinned = false,
            forwardedFromId = null,
        )
        val attachmentMessage = Db.StoredMessage(
            id = 2L,
            serverMessageId = null,
            contactId = 7L,
            direction = "incoming",
            senderId = 7L,
            recipientId = 11L,
            messageNumber = null,
            plaintext = ChatPayloadCodec.encodeAttachment(
                AttachmentPayload(
                    attachmentId = "att-1",
                    fileName = "report.pdf",
                    mimeType = "application/pdf",
                    sizeBytes = 128L,
                    sha256 = "abc",
                    mediaKeyBase64 = "key",
                    nonceBase64 = "nonce",
                )
            ),
            createdAt = "2026-05-14T10:01:00",
            status = "delivered",
            isPinned = false,
            forwardedFromId = null,
        )

        val csv = CsvExporter.exportMessages(listOf(textMessage, attachmentMessage))

        assertTrue(csv.contains("hello from fortrx"))
        assertFalse(csv.contains("report.pdf"))
        assertFalse(csv.contains("att-1"))
    }
}
