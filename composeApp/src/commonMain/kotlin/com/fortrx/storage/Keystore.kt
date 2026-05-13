package com.fortrx.storage

import com.fortrx.Settings
import com.fortrx.platform.debugLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

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
}
