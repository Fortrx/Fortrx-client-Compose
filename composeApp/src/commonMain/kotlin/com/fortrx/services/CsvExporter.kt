package com.fortrx.services

import com.fortrx.messages.ChatPayload
import com.fortrx.messages.ChatPayloadCodec
import com.fortrx.storage.Db

object CsvExporter {
    /**
     * Exports a list of messages to a CSV string.
     * Includes a UTF-8 Byte Order Mark (BOM) for better compatibility with Excel.
     */
    fun exportMessages(messages: List<Db.StoredMessage>): String {
        val sb = StringBuilder()
        // Add UTF-8 BOM
        sb.append('\uFEFF')
        
        // Header
        sb.append("MessageID,SenderID,RecipientID,Direction,Status,Timestamp,MessageContent\n")
        
        messages.forEach { msg ->
            val payload = ChatPayloadCodec.decode(msg.plaintext)
            val textPayload = payload as? ChatPayload.Text ?: return@forEach
            sb.append("${msg.id},")
            sb.append("${msg.senderId ?: ""},")
            sb.append("${msg.recipientId ?: ""},")
            sb.append("${msg.direction},")
            sb.append("${msg.status},")
            sb.append("${msg.createdAt},")
            
            sb.append(csvSafeCell(textPayload.text))
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun csvSafeCell(value: String): String {
        val dangerous = setOf('=', '+', '-', '@', '\t', '\r', '\n')
        val escaped = value.replace("\"", "\"\"")
        val prefixed = if (escaped.isNotEmpty() && escaped[0] in dangerous) "'$escaped" else escaped
        return "\"$prefixed\""
    } // FIXED: CSV Formula Injection
}
