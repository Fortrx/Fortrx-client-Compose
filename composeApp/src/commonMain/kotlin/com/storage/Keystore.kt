package com.fortrx.storage

import com.fortrx.Settings
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
        println("Keystore: Saving keys for user $userId. JSON length: ${keys.toString().length}")
        Db.saveKeys(pw, userId, keys.toString())
        println("Keystore: Keys saved successfully.")
    }

    suspend fun loadKeys(password: String? = null, userId: Long? = null): JsonObject? {
        val pw = password ?: Settings.storagePassword ?: run {
            println("Keystore: FAILED to load keys: No storage password available")
            return null
        }
        println("Keystore: Loading keys from DB (userId=$userId)")
        val raw = Db.loadKeys(pw, userId)
        if (raw == null) {
            println("Keystore: No keys found in DB for user $userId")
            return null
        }
        println("Keystore: Keys loaded successfully. JSON length: ${raw.length}")
        return try {
            json.parseToJsonElement(raw).jsonObject
        } catch (e: Exception) {
            println("Keystore: FAILED to parse keys JSON: ${e.message}")
            null
        }
    }

    fun keysExist(): Boolean = Db.keysExist()
}
