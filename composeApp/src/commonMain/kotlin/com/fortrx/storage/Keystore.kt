package com.fortrx.storage

import com.fortrx.Settings
import com.fortrx.platform.debugLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object Keystore {
    private val json = Json { ignoreUnknownKeys = true }

    fun hasKyberKeys(keys: JsonObject): Boolean {
        val v = keys["kyber_prekey_public"] ?: return false
        return v !is JsonNull
    }

    suspend fun saveKeys(keys: JsonObject, password: String? = null) {
        val pw = password ?: Settings.storagePassword ?: throw StorageError("No storage password set")
        val userId = keys["user_id"]?.jsonPrimitive?.longOrNull ?: 0L
        debugLog("Persisting local key material.")
        Db.saveKeys(pw, userId, keys.toString())
    }

    suspend fun loadKeys(password: String? = null, userId: Long? = null): JsonObject? {
        val pw = password ?: Settings.storagePassword ?: run {
            debugLog("No storage password available for key loading.")
            return null
        }
        val raw = Db.loadKeys(pw, userId)
        if (raw == null) {
            debugLog("No persisted key material found.")
            return null
        }
        return try {
            json.parseToJsonElement(raw).jsonObject
        } catch (e: Exception) {
            debugLog("Parsing persisted key material failed.", e)
            null
        }
    }

    fun keysExist(): Boolean = Db.keysExist()

    suspend fun getOrGenerateBackupSalt(password: String): ByteArray {
        val keys = loadKeys(password) ?: return com.fortrx.platform.SecureRandomBytes.nextBytes(32)
        val saltHex = keys["backup_kdf_salt"]?.jsonPrimitive?.contentOrNull
        if (saltHex != null) {
            return saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        val newSalt = com.fortrx.platform.SecureRandomBytes.nextBytes(32)
        val updatedKeys = buildJsonObject {
            keys.forEach { (k, v) -> put(k, v) }
            put("backup_kdf_salt", newSalt.joinToString("") { "%02x".format(it) })
        }
        saveKeys(updatedKeys, password)
        return newSalt
    } // FIXED: Hardcoded Backup Salt
}
