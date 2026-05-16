package com.fortrx.crypto

/** Port of `client/crypto/fingerprint.py` — pure Kotlin, uses `CryptoPrimitives.sha512`. */
object Fingerprint {
    fun computeKeyFingerprint(publicKey: ByteArray, userId: Long): ByteArray {
        val userIdBytes = byteArrayOf(
            ((userId ushr 56) and 0xFF).toByte(),
            ((userId ushr 48) and 0xFF).toByte(),
            ((userId ushr 40) and 0xFF).toByte(),
            ((userId ushr 32) and 0xFF).toByte(),
            ((userId ushr 24) and 0xFF).toByte(),
            ((userId ushr 16) and 0xFF).toByte(),
            ((userId ushr 8) and 0xFF).toByte(),
            (userId and 0xFF).toByte(),
        ) // FIXED: Safety Number Fingerprint Uses 32-bit User ID
        val hashMaterial = publicKey + userIdBytes
        var result = hashMaterial
        repeat(5200) { result = CryptoPrimitives.sha512(result + hashMaterial) }
        return result.copyOfRange(0, 30)
    }

    fun fingerprintToString(fp: ByteArray): String {
        val chunks = mutableListOf<String>()
        var i = 0
        while (i < 30) {
            val chunk = fp.copyOfRange(i, i + 5)
            var num = 0L
            for (b in chunk) num = (num shl 8) or (b.toLong() and 0xFF)
            chunks += (num % 100000L).toString().padStart(5, '0')
            i += 5
        }
        return chunks.joinToString(" ")
    }

    data class SafetyNumber(
        val safetyNumber: String,
        val yourFingerprint: String,
        val theirFingerprint: String,
    )

    fun generateSafetyNumber(localId: Long, localIk: ByteArray, remoteId: Long, remoteIk: ByteArray): SafetyNumber {
        val local = computeKeyFingerprint(localIk, localId)
        val remote = computeKeyFingerprint(remoteIk, remoteId)
        val combined = if (localId < remoteId) local + remote else remote + local
        val material = CryptoPrimitives.sha512(combined).copyOfRange(0, 30)
        return SafetyNumber(
            safetyNumber = fingerprintToString(material),
            yourFingerprint = fingerprintToString(local),
            theirFingerprint = fingerprintToString(remote),
        )
    }
}
