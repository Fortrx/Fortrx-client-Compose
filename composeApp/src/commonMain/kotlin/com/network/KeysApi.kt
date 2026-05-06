package com.fortrx.network

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put

object KeysApi {
    suspend fun uploadKeyBundle(
        identityKey: String, signingPublic: String, signedPrekey: String,
        signedPrekeySignature: String, prekeyId: Int, oneTimePrekeys: List<String>,
        kyberPrekeyPublic: String? = null, kyberPrekeySignature: String? = null,
    ): JsonObject {
        val body = buildJsonObject {
            put("identity_key", identityKey)
            put("signing_public", signingPublic)
            put("signed_prekey", signedPrekey)
            put("signed_prekey_signature", signedPrekeySignature)
            put("prekey_id", prekeyId)
            put("one_time_prekeys", buildJsonArray { oneTimePrekeys.forEach { add(it) } })
            kyberPrekeyPublic?.let { put("kyber_prekey_public", it) }
            kyberPrekeySignature?.let { put("kyber_prekey_signature", it) }
        }
        val res = Api.postJson("/keys/upload", body)
        Api.raiseForStatus(res, "upload_key_bundle"); return Api.jsonObject(res)
    }

    suspend fun fetchKeyBundle(userId: Long): JsonObject {
        val res = Api.getRequest("/keys/$userId")
        Api.raiseForStatus(res, "fetch_key_bundle"); return Api.jsonObject(res)
    }
}
