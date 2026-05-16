package com.fortrx.storage

import com.fortrx.platform.AndroidContextHolder
import java.io.File
import java.util.UUID

actual object PlatformFileStorage {
    private val rootDir: File by lazy {
        File(AndroidContextHolder.appContext.filesDir, "attachments").apply { mkdirs() }
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
