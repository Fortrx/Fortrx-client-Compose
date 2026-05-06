package com.fortrx.crypto

/**
 * Platform crypto primitives required by the protocol layer.
 * Port targets:
 *  - JVM/Desktop, Android: BouncyCastle
 *  - iOS: CryptoKit (X25519/Ed25519/SHA), CommonCrypto (HMAC), liboqs (Kyber/ML-KEM-768)
 */

data class KeyPair(val privateKey: ByteArray, val publicKey: ByteArray)

expect object CryptoPrimitives {
    /** X25519 keypair generation; raw 32-byte keys. If seed is provided, derivation is deterministic. */
    fun x25519Generate(seed: ByteArray? = null): KeyPair
    /** X25519 ECDH; returns 32-byte shared secret. */
    fun x25519Diffie(privateKey: ByteArray, peerPublic: ByteArray): ByteArray

    /** Ed25519 keypair generation; raw 32-byte private + 32-byte public. If seed is provided, derivation is deterministic. */
    fun ed25519Generate(seed: ByteArray? = null): KeyPair
    /** Ed25519 sign; returns 64-byte signature. */
    fun ed25519Sign(privateKey: ByteArray, message: ByteArray): ByteArray
    /** Ed25519 verify. */
    fun ed25519Verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean

    /** ML-KEM-768 (Kyber) keypair. */
    fun kyberGenerate(): KeyPair
    /** ML-KEM-768 encapsulate -> (ciphertext, sharedSecret 32B). */
    fun kyberEncapsulate(publicKey: ByteArray): Pair<ByteArray, ByteArray>
    /** ML-KEM-768 decapsulate -> sharedSecret 32B. */
    fun kyberDecapsulate(privateKey: ByteArray, ciphertext: ByteArray): ByteArray

    /** AES-256-GCM with optional AAD; returns ciphertext||tag. */
    fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray?): ByteArray
    fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray?): ByteArray

    /** AES-256-CTR encryption/decryption (symmetric). */
    fun aesCtr(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray

    /** HMAC-SHA256. */
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray

    /** SHA-256 / SHA-512. */
    fun sha256(data: ByteArray): ByteArray
    fun sha512(data: ByteArray): ByteArray

    /** Cryptographically secure random bytes. */
    fun randomBytes(size: Int): ByteArray
}
