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

    fun saveKeys(keys: JsonObject, password: String? = null) {
        val pw = password ?: Settings.storagePassword ?: throw StorageError("No storage password set")
        val userId = keys["user_id"]?.jsonPrimitive?.longOrNull ?: 0L
        Db.saveKeys(pw, userId, keys.toString())
    }

    fun loadKeys(password: String? = null, userId: Long? = null): JsonObject? {
        val pw = password ?: Settings.storagePassword ?: return null
        val raw = Db.loadKeys(pw, userId) ?: return null
        return json.parseToJsonElement(raw).jsonObject
    }

    fun keysExist(): Boolean = Db.keysExist()
}
