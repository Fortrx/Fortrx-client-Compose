package com.fortrx.storage

import com.fortrx.Settings
import com.fortrx.crypto.RatchetState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SessionStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun saveSession(otherUserId: Long, state: RatchetState, password: String? = null) {
        val pw = password ?: Settings.storagePassword ?: throw StorageError("No storage password set")
        Db.saveSessionBlob(pw, otherUserId, json.encodeToString(state))
    }

    suspend fun loadSession(otherUserId: Long, password: String? = null): RatchetState? {
        val pw = password ?: Settings.storagePassword ?: return null
        val raw = Db.loadSessionBlob(pw, otherUserId) ?: return null
        return json.decodeFromString(raw)
    }

    suspend fun loadSessions(password: String? = null): Map<Long, RatchetState> {
        val pw = password ?: Settings.storagePassword ?: return emptyMap()
        return Db.loadSessionsMap(pw).mapValues { (_, raw) -> json.decodeFromString(raw) }
    }
}
