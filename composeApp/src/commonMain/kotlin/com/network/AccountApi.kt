package com.fortrx.network

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
object AccountApi {
    suspend fun registerBackupCode(username: String, code: String) {
        val normalized = code.filter { it.isDigit() }
        val salt = com.fortrx.platform.SecureRandomBytes.nextBytes(16)
        val prehash = com.fortrx.storage.pbkdf2Sha256(
            normalized,
            salt,
            200_000,
            32,
        )
        val payload = buildJsonObject {
            put("backup_code_hash", kotlin.io.encoding.Base64.encode(prehash))
            put("salt", kotlin.io.encoding.Base64.encode(salt))
        }
        val response = Api.postJson("/account/backup-code/register", payload)
        Api.raiseForStatus(response, "backup_code_register")
    }
}
