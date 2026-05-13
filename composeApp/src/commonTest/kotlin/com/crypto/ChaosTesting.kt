package com.crypto

import com.fortrx.crypto.CryptoPrimitives
import com.fortrx.crypto.Ratchet
import com.fortrx.crypto.RatchetState
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class RatchetFailureCaseTest {

    private val ad = "ad".encodeToByteArray()
    private val sharedSecret = CryptoPrimitives.randomBytes(32)
    private val bobInitialKp = CryptoPrimitives.x25519Generate()

    private fun setupAliceBob(): Pair<RatchetState, RatchetState> {
        val alice = Ratchet.initSender(sharedSecret, bobInitialKp.publicKey)
        val bob = Ratchet.initReceiver(sharedSecret, bobInitialKp.privateKey, bobInitialKp.publicKey)
        return alice to bob
    }

    @Test
    fun testMalformedCiphertext() {
        val (alice, bob) = setupAliceBob()
        val pt = "Sensitive data".encodeToByteArray()
        val (_, header, ct) = Ratchet.encrypt(alice, pt, ad)

        // Corrupt one byte of ciphertext
        val corruptedCt = ct.copyOf()
        corruptedCt[corruptedCt.size - 1] = (corruptedCt.last().toInt() xor 0xFF).toByte()

        assertFails {
            Ratchet.decrypt(bob, header, corruptedCt, ad)
        }
    }

    @Test
    fun testMalformedHeader() {
        val (alice, bob) = setupAliceBob()
        val pt = "Sensitive data".encodeToByteArray()
        val (_, header, ct) = Ratchet.encrypt(alice, pt, ad)

        // Header is JSON. Let's make it invalid JSON.
        val corruptedHeader = header.copyOfRange(0, header.size - 2) // Truncate

        assertFails {
            Ratchet.decrypt(bob, corruptedHeader, ct, ad)
        }
    }

    @Test
    fun testWrongAssociatedData() {
        val (alice, bob) = setupAliceBob()
        val pt = "Sensitive data".encodeToByteArray()
        val (_, header, ct) = Ratchet.encrypt(alice, pt, ad)

        assertFails {
            Ratchet.decrypt(bob, header, ct, "wrong_ad".encodeToByteArray())
        }
    }

    @Test
    fun testFuzzDecryptInputs() {
        val (_, bob) = setupAliceBob()
        
        repeat(100) {
            val randomHeader = CryptoPrimitives.randomBytes(64)
            val randomCt = CryptoPrimitives.randomBytes(128)
            val randomAd = CryptoPrimitives.randomBytes(16)
            
            try {
                Ratchet.decrypt(bob, randomHeader, randomCt, randomAd)
            } catch (_: Exception) {
                // Exceptions are expected, but it should not crash the process or hang
            }
        }
    }

    @Test
    fun testPartialPersistenceFailureSimulation() {
        val (alice, bob) = setupAliceBob()

        val pt1 = "Message 1".encodeToByteArray()
        val (alice1, header1, ct1) = Ratchet.encrypt(alice, pt1, ad)

        val (_, decrypted1) = Ratchet.decrypt(bob, header1, ct1, ad)
        assertContentEquals(pt1, decrypted1)

        val pt2 = "Message 2".encodeToByteArray()
        val (alice2, header2, ct2) = Ratchet.encrypt(alice1, pt2, ad)

        val (bob2, decrypted2) = Ratchet.decrypt(bob, header2, ct2, ad)
        assertContentEquals(pt2, decrypted2)

        val (_, decrypted1Again) = Ratchet.decrypt(bob2, header1, ct1, ad)
        assertContentEquals(pt1, decrypted1Again)
    }
}
