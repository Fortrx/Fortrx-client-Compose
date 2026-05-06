package com.fortrx.crypto

import kotlin.test.*

class CryptoPrimitivesTest {

    @Test
    fun testX25519() {
        val alice = CryptoPrimitives.x25519Generate()
        val bob = CryptoPrimitives.x25519Generate()

        val secretA = CryptoPrimitives.x25519Diffie(alice.privateKey, bob.publicKey)
        val secretB = CryptoPrimitives.x25519Diffie(bob.privateKey, alice.publicKey)

        assertContentEquals(secretA, secretB)
        assertEquals(32, secretA.size)
    }

    @Test
    fun testEd25519() {
        val kp = CryptoPrimitives.ed25519Generate()
        val message = "Hello, Fortrx!".encodeToByteArray()
        val signature = CryptoPrimitives.ed25519Sign(kp.privateKey, message)

        assertEquals(64, signature.size)
        assertTrue(CryptoPrimitives.ed25519Verify(kp.publicKey, message, signature))
        assertFalse(CryptoPrimitives.ed25519Verify(kp.publicKey, "Wrong message".encodeToByteArray(), signature))
    }

    @Test
    fun testKyber() {
        val kp = CryptoPrimitives.kyberGenerate()
        // ML-KEM-768 sizes
        assertEquals(1184, kp.publicKey.size)
        assertEquals(2400, kp.privateKey.size)

        val (ciphertext, secretEncaps) = CryptoPrimitives.kyberEncapsulate(kp.publicKey)
        assertEquals(1088, ciphertext.size)
        assertEquals(32, secretEncaps.size)

        val secretDecaps = CryptoPrimitives.kyberDecapsulate(kp.privateKey, ciphertext)
        assertContentEquals(secretEncaps, secretDecaps)
    }

    @Test
    fun testAesGcm() {
        val key = CryptoPrimitives.randomBytes(32)
        val nonce = CryptoPrimitives.randomBytes(12)
        val plaintext = "Secret message".encodeToByteArray()
        val aad = "Associated data".encodeToByteArray()

        val ciphertextAndTag = CryptoPrimitives.aesGcmEncrypt(key, nonce, plaintext, aad)
        // Tag is 16 bytes, so ciphertextAndTag size is plaintext.size + 16
        assertEquals(plaintext.size + 16, ciphertextAndTag.size)

        val decrypted = CryptoPrimitives.aesGcmDecrypt(key, nonce, ciphertextAndTag, aad)
        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun testAesCtr() {
        val key = CryptoPrimitives.randomBytes(32)
        val iv = CryptoPrimitives.randomBytes(16)
        val data = "Data to encrypt".encodeToByteArray()

        val encrypted = CryptoPrimitives.aesCtr(key, iv, data)
        assertEquals(data.size, encrypted.size)

        val decrypted = CryptoPrimitives.aesCtr(key, iv, encrypted)
        assertContentEquals(data, decrypted)
    }

    @Test
    fun testHmacSha256() {
        val key = "key".encodeToByteArray()
        val data = "data".encodeToByteArray()
        val hmac = CryptoPrimitives.hmacSha256(key, data)
        assertEquals(32, hmac.size)

        // Check against known result if possible, or just consistency
        val hmac2 = CryptoPrimitives.hmacSha256(key, data)
        assertContentEquals(hmac, hmac2)
    }

    @Test
    fun testSha() {
        val data = "test".encodeToByteArray()
        assertEquals(32, CryptoPrimitives.sha256(data).size)
        assertEquals(64, CryptoPrimitives.sha512(data).size)
    }
}
