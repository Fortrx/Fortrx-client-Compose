package com.fortrx.services

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
            sb.append("${msg.id},")
            sb.append("${msg.senderId ?: ""},")
            sb.append("${msg.recipientId ?: ""},")
            sb.append("${msg.direction},")
            sb.append("${msg.status},")
            sb.append("${msg.createdAt},")
            
            // Escape double quotes and wrap in double quotes
            val content = (msg.plaintext ?: "")
                .replace("\"", "\"\"")
            sb.append("\"$content\"\n")
        }
        return sb.toString()
    }
}
