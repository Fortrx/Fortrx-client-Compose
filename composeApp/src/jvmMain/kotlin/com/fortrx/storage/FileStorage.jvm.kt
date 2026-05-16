package com.fortrx.storage

import java.io.File
import java.util.UUID

actual object PlatformFileStorage {
    private val rootDir: File by lazy {
        File(System.getProperty("user.home"), ".fortrx/attachments").apply { mkdirs() }
    }

    actual fun saveFile(bytes: ByteArray): String {
        val fileName = UUID.randomUUID().toString() + ".dat"
        File(rootDir, fileName).writeBytes(bytes)
        return fileName
    }

    actual fun readFile(localFileName: String): ByteArray? {
        val file = File(rootDir, localFileName)
        return if (file.exists()) file.readBytes() else null
    }

    actual fun writeNamedFile(localFileName: String, bytes: ByteArray) {
        File(rootDir, localFileName).writeBytes(bytes)
    }

    actual fun deleteFile(localFileName: String) {
        File(rootDir, localFileName).delete()
    }
}
