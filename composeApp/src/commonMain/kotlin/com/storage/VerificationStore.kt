package com.fortrx.storage

object VerificationStore {
    fun loadVerifications(): Map<Long, String> = Db.loadVerifications()
    fun saveVerification(userId: Long, safetyNumber: String) = Db.saveVerification(userId, safetyNumber)
    fun isVerified(userId: Long): Boolean = Db.isVerified(userId)
}
