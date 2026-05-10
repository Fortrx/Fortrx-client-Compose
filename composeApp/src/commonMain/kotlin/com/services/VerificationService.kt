package com.fortrx.services

import com.fortrx.crypto.Fingerprint
import com.fortrx.network.KeysApi
import com.fortrx.storage.Db
import com.fortrx.storage.Keystore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object VerificationService {
    
    suspend fun getSafetyNumber(password: String, otherUserId: Long): Fingerprint.SafetyNumber = withContext(Dispatchers.Default) {
        val keys = Keystore.loadKeys(password) ?: error("Local keys missing")
        val myId = keys["user_id"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0
        val myIk = Base64.decode(keys["dh_public"]?.jsonPrimitive?.content ?: "")
        
        val bundle = KeysApi.fetchKeyBundle(otherUserId)
        val theirIk = bundle.requireBytes("identity_key")
        
        Fingerprint.generateSafetyNumber(myId, myIk, otherUserId.toInt(), theirIk)
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.bytesOrNull(key: String): ByteArray? =
        stringOrNull(key)?.takeIf { it.isNotEmpty() }?.let(Base64::decode)

    private fun JsonObject.requireBytes(key: String): ByteArray =
        bytesOrNull(key) ?: error("Missing $key")
    
    fun isVerified(userId: Long): Boolean = Db.isVerified(userId)
    
    fun markVerified(userId: Long, safetyNumber: String) {
        Db.saveVerification(userId, safetyNumber)
    }
}
