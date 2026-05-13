package com.crypto

import com.fortrx.crypto.CryptoPrimitives
import com.fortrx.crypto.Ratchet
import com.fortrx.crypto.RatchetState
import kotlin.test.*
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class AdvancedRatchetTest {

    private val ad = "associated_data".encodeToByteArray()
    private val sharedSecret = CryptoPrimitives.randomBytes(32)
    private val bobInitialKp = CryptoPrimitives.x25519Generate()

    private fun setupAliceBob(): Pair<RatchetState, RatchetState> {
        val alice = Ratchet.initSender(sharedSecret, bobInitialKp.publicKey)
        val bob = Ratchet.initReceiver(sharedSecret, bobInitialKp.privateKey, bobInitialKp.publicKey)
        return alice to bob
    }

    @Test
    fun testChainKeyEvolution() {
        val (alice, bob) = setupAliceBob()
        
        // Initial state: Alice has sending CK, Bob has none until first message
        assertNotNull(alice.sendingChainKey)
        assertNull(bob.recvChainKey)

        val pt = "Message".encodeToByteArray()
        val (alice1, header, ct) = Ratchet.encrypt(alice, pt, ad)
        
        // Chain key should have changed
        assertFalse(alice.sendingChainKey!!.contentEquals(alice1.sendingChainKey!!))
        
        val (bob1, decrypted) = Ratchet.decrypt(bob, header, ct, ad)
        assertContentEquals(pt, decrypted)
        
        // Bob should now have a receiving chain key
        assertNotNull(bob1.recvChainKey)
    }

    @Test
    fun testLargeGapSkippedKeys() {
        var (alice, bob) = setupAliceBob()
        
        val messages = List(10) { "Msg $it".encodeToByteArray() }
        val payloads = messages.map { m ->
            val (nextAlice, header, ct) = Ratchet.encrypt(alice, m, ad)
            alice = nextAlice
            header to ct
        }

        // Receive only the last one
        val (lastHeader, lastCt) = payloads.last()
        val (bob1, ptLast) = Ratchet.decrypt(bob, lastHeader, lastCt, ad)
        assertContentEquals(messages.last(), ptLast)
        
        // Bob should have 9 skipped keys
        assertEquals(9, bob1.skippedMessageKeys.size)
        
        // Receive the first one
        val (firstHeader, firstCt) = payloads.first()
        val (bob2, ptFirst) = Ratchet.decrypt(bob1, firstHeader, firstCt, ad)
        assertContentEquals(messages.first(), ptFirst)
        assertEquals(8, bob2.skippedMessageKeys.size)
    }

    @Test
    fun testReplayAttackProtection() {
        val (alice, bob) = setupAliceBob()
        
        val pt = "Secret".encodeToByteArray()
        val (_, header, ct) = Ratchet.encrypt(alice, pt, ad)
        
        // First decryption works
        val (bob1, pt1) = Ratchet.decrypt(bob, header, ct, ad)
        assertContentEquals(pt, pt1)
        
        // Second decryption (replay) should fail because the key is deleted from skipped (if it was there)
        // or the chain has moved past it.
        // In the current implementation, if it's not in skipped and not a "new" message, it might try to advance the chain.
        // Let's see how it behaves.
        
        assertFails {
            Ratchet.decrypt(bob1, header, ct, ad)
        }
    }

    @Test
    fun testKeyExhaustionBoundary() {
        // Test that we can handle many messages without crashing, up to MAX_SKIP
        var (alice, bob) = setupAliceBob()
        
        // Skip 100 messages
        repeat(100) {
            val (nextAlice, _, _) = Ratchet.encrypt(alice, "skip".encodeToByteArray(), ad)
            alice = nextAlice
        }
        
        val (_, header, ct) = Ratchet.encrypt(alice, "final".encodeToByteArray(), ad)
        val (_, pt) = Ratchet.decrypt(bob, header, ct, ad)
        assertContentEquals("final".encodeToByteArray(), pt)
    }

    @Test
    fun testOutOrderMessages() {
        var (alice, bob) = setupAliceBob()
        val count = 5
        val payloads = mutableListOf<Pair<ByteArray, ByteArray>>()
        val contents = mutableListOf<ByteArray>()
        
        repeat(count) { i ->
            val msg = "Msg $i".encodeToByteArray()
            contents.add(msg)
            val (nextAlice, header, ct) = Ratchet.encrypt(alice, msg, ad)
            alice = nextAlice
            payloads.add(header to ct)
        }
        
        // Shuffle and decrypt
        val indices = (0 until count).toList().shuffled()
        var currentBob = bob
        for (i in indices) {
            val (header, ct) = payloads[i]
            val (nextBob, pt) = Ratchet.decrypt(currentBob, header, ct, ad)
            currentBob = nextBob
            assertContentEquals(contents[i], pt)
        }
        assertEquals(0, currentBob.skippedMessageKeys.size)
    }
}
