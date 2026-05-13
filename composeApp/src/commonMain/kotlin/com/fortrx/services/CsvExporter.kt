package com.fortrx.services

import com.fortrx.storage.Db

object CsvExporter {
    fun exportMessages(messages: List<Db.StoredMessage>): String {
        val sb = StringBuilder()
        sb.append("ID,Sender,Recipient,Direction,Status,CreatedAt,Plaintext\n")
        messages.forEach { msg ->
            sb.append("${msg.id},")
            sb.append("${msg.senderId ?: ""},")
            sb.append("${msg.recipientId ?: ""},")
            sb.append("${msg.direction},")
            sb.append("${msg.status},")
            sb.append("${msg.createdAt},")
            sb.append("\"${(msg.plaintext ?: "").replace("\"", "\"\"")}\"\n")
        }
        return sb.toString()
    }
}
