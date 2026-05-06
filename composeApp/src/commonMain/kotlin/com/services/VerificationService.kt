package com.fortrx.services

import com.fortrx.crypto.Fingerprint
import com.fortrx.network.KeysApi
import com.fortrx.storage.Db
import com.fortrx.storage.Keystore
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object VerificationService {
    
    suspend fun getSafetyNumber(password: String, otherUserId: Long): Fingerprint.SafetyNumber {
        val keys = Keystore.loadKeys(password) ?: error("Local keys missing")
        val myId = keys["user_id"]?.toString()?.toInt() ?: 0
        val myIk = Base64.decode(keys["dh_public"]?.jsonPrimitive?.content ?: "")
        
        val bundle = KeysApi.fetchKeyBundle(otherUserId)
        val theirIk = Base64.decode(bundle["identity_key"]?.jsonPrimitive?.content ?: "")
        
        return Fingerprint.generateSafetyNumber(myId, myIk, otherUserId.toInt(), theirIk)
    }
    
    fun isVerified(userId: Long): Boolean = Db.isVerified(userId)
    
    fun markVerified(userId: Long, safetyNumber: String) {
        Db.saveVerification(userId, safetyNumber)
    }
}
