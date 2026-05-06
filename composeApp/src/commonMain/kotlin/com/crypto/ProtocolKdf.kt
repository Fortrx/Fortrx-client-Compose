package com.fortrx.crypto

/**
 * Port of `client/crypto/protocol_kdf.py`.
 * Encoding helpers + HKDF (HKDF impl uses `expect hmacSha256`).
 */

val CURVE25519_KEY_PREFIX: ByteArray = byteArrayOf(0x05)
val MLKEM768_KEY_PREFIX: ByteArray = byteArrayOf(0x08)
val CURVE25519_F: ByteArray = ByteArray(32) { 0xFF.toByte() }
const val SHA256_HASH_LEN = 32
val ZERO_SALT_256: ByteArray = ByteArray(SHA256_HASH_LEN)
val X3DH_INFO: ByteArray = "Fortrx".encodeToByteArray()
val PQXDH_INFO: ByteArray = "Fortrx_CURVE25519_SHA-256_ML-KEM-768".encodeToByteArray()

/** Pure-Kotlin HKDF on top of `CryptoPrimitives.hmacSha256`. RFC 5869. */
fun hkdfDerive(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
    val effectiveSalt = if (salt.isEmpty()) ByteArray(SHA256_HASH_LEN) else salt
    val prk = CryptoPrimitives.hmacSha256(effectiveSalt, ikm)
    val n = (length + SHA256_HASH_LEN - 1) / SHA256_HASH_LEN
    require(n <= 255) { "HKDF length too large" }
    val output = ByteArray(length)
    var t = ByteArray(0)
    var pos = 0
    for (i in 1..n) {
        val data = t + info + byteArrayOf(i.toByte())
        t = CryptoPrimitives.hmacSha256(prk, data)
        val take = minOf(t.size, length - pos)
        t.copyInto(output, pos, 0, take)
        pos += take
    }
    return output
}

fun deriveX3dhKeyMaterial(keyMaterial: ByteArray, info: ByteArray): ByteArray =
    hkdfDerive(CURVE25519_F + keyMaterial, ZERO_SALT_256, info, 32)

fun encodeCurvePublicKey(pub: ByteArray): ByteArray = CURVE25519_KEY_PREFIX + pub
fun encodeMlkemPublicKey(pub: ByteArray): ByteArray = MLKEM768_KEY_PREFIX + pub

fun encodeIdentityAssociatedData(senderIk: ByteArray, recipientIk: ByteArray): ByteArray {
    val sender = encodeCurvePublicKey(senderIk)
    val recipient = encodeCurvePublicKey(recipientIk)
    val len = sender.size
    return byteArrayOf(((len ushr 8) and 0xFF).toByte(), (len and 0xFF).toByte()) + sender + recipient
}
