package com.crypto

import com.fortrx.crypto.CryptoPrimitives
import com.fortrx.crypto.Ratchet
import com.fortrx.crypto.RatchetState
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class, ExperimentalCoroutinesApi::class)
class HarshAdversarialTest {

    private val ad = "harsh_ad".encodeToByteArray()
    private val sharedSecret = CryptoPrimitives.randomBytes(32)
    private val bobInitialKp = CryptoPrimitives.x25519Generate()

    private fun setupAliceBob(): Pair<RatchetState, RatchetState> {
        val alice = Ratchet.initSender(sharedSecret, bobInitialKp.publicKey)
        val bob = Ratchet.initReceiver(sharedSecret, bobInitialKp.privateKey, bobInitialKp.publicKey)
        return alice to bob
    }

    @Test
    fun testKeyExhaustionBoundaries() {
        val (alice, bob) = setupAliceBob()
        var currentAlice = alice

        // Skip exactly 1000 messages
        repeat(1000) {
            val (nextAlice, _, _) = Ratchet.encrypt(currentAlice, "msg".encodeToByteArray(), ad)
            currentAlice = nextAlice
        }
        
        // This message should be at n=1000 (if start at 0)
        val (finalAlice, header, ct) = Ratchet.encrypt(currentAlice, "boundary".encodeToByteArray(), ad)
        
        // Bob tries to decrypt. Since n=1000 and recvCount=0, skipMessageKeys(bob, 1000) is called.
        // until = 1000, recvCount = 0. 0 + 1000 < 1000 is false. OK.
        val (_, pt) = Ratchet.decrypt(bob, header, ct, ad)
        assertContentEquals("boundary".encodeToByteArray(), pt)

        // Now try 1001
        val (tooFarAlice, headerTooFar, ctTooFar) = Ratchet.encrypt(finalAlice, "too far".encodeToByteArray(), ad)
        assertFailsWith<IllegalStateException> {
            Ratchet.decrypt(bob, headerTooFar, ctTooFar, ad)
        }
    }

    @Test
    fun testDeepReorderingAcrossEpochs() {
        var (alice, bob) = setupAliceBob()
        
        // Epoch 1: Alice sends 5 messages
        val epoch1 = mutableListOf<Pair<ByteArray, ByteArray>>()
        repeat(5) { i ->
            val (nextA, h, c) = Ratchet.encrypt(alice, "E1-$i".encodeToByteArray(), ad)
            alice = nextA
            epoch1.add(h to c)
        }
        
        // Bob receives nothing yet.
        
        // Epoch 2: Bob sends a message to Alice (requires Bob to have received something first to get sending key)
        // Wait, Bob needs to receive at least one message to trigger DH ratchet and get a sending chain key.
        val (firstH, firstC) = epoch1[0]
        val (bob1, _) = Ratchet.decrypt(bob, firstH, firstC, ad)
        bob = bob1
        
        // Bob sends 1 message to Alice
        val (bob2, hBob, cBob) = Ratchet.encrypt(bob, "Bob-E2".encodeToByteArray(), ad)
        bob = bob2
        
        // Alice receives Bob's message -> Alice performs DH Ratchet Step
        val (alice1, _) = Ratchet.decrypt(alice, hBob, cBob, ad)
        alice = alice1
        
        // Epoch 3: Alice sends 5 more messages
        val epoch3 = mutableListOf<Pair<ByteArray, ByteArray>>()
        repeat(5) { i ->
            val (nextA, h, c) = Ratchet.encrypt(alice, "E3-$i".encodeToByteArray(), ad)
            alice = nextA
            epoch3.add(h to c)
        }
        
        // Now Bob receives the VERY LAST message from Epoch 3
        val (lastH, lastC) = epoch3.last()
        val (bob3, ptLast) = Ratchet.decrypt(bob, lastH, lastC, ad)
        assertContentEquals("E3-4".encodeToByteArray(), ptLast)
        
        // Now Bob receives a LATE message from Epoch 1
        val (lateH, lateC) = epoch1[2]
        val (bob4, ptLate) = Ratchet.decrypt(bob3, lateH, lateC, ad)
        assertContentEquals("E1-2".encodeToByteArray(), ptLate)
        
        // Verify we can still decrypt the rest
        val (midH, midC) = epoch3[1]
        val (_, ptMid) = Ratchet.decrypt(bob4, midH, midC, ad)
        assertContentEquals("E3-1".encodeToByteArray(), ptMid)
    }

    @Test
    fun testConcurrencyChaos() = runTest {
        val (alice, bob) = setupAliceBob()
        val aliceState = alice
        var currentBob = bob
        
        val count = 100
        val payloads = mutableListOf<Pair<ByteArray, ByteArray>>()
        
        // Standard encryption (sequential)
        var tempAlice = aliceState
        repeat(count) {
            val (nextA, h, c) = Ratchet.encrypt(tempAlice, "chaos".encodeToByteArray(), ad)
            tempAlice = nextA
            payloads.add(h to c)
        }
        
        // Parallel decryption (Stress test)
        // Note: Ratchet.decrypt returns a NEW state. If we do it in parallel, we need to be careful.
        // This test actually checks if the PURE functions remain robust or if there are shared state issues.
        // Since Ratchet is an 'object' and functions are pure-ish (they take state and return new state),
        // it should be safe.
        
        val results = mutableListOf<Deferred<ByteArray>>()
        coroutineScope {
            for (p in payloads) {
                results.add(async(Dispatchers.Default) {
                    // Each one starts from the SAME bob state initially? 
                    // No, that won't work for non-skipped messages if they aren't the first one.
                    // Let's have Bob receive the LAST message first to populate skipped keys,
                    // then decrypt the rest in parallel.
                    
                    val (header, ct) = p
                    val (_, pt) = Ratchet.decrypt(currentBob, header, ct, ad)
                    pt
                })
            }
        }
        
        // In the scenario above, each 'decrypt' call was starting from 'currentBob'.
        // This simulates multiple threads trying to decrypt the SAME message (or different ones)
        // from the SAME baseline state.
        
        for (res in results) {
            assertContentEquals("chaos".encodeToByteArray(), res.await())
        }
    }

    @Test
    fun testProtocolDesyncRecoveryAttempt() {
        val (alice, bob) = setupAliceBob()
        
        // Alice sends 1, Bob receives.
        val (a1, h1, c1) = Ratchet.encrypt(alice, "M1".encodeToByteArray(), ad)
        val (b1, _) = Ratchet.decrypt(bob, h1, c1, ad)
        
        // Bob sends 1, Alice receives.
        val (b2, h2, c2) = Ratchet.encrypt(b1, "M2".encodeToByteArray(), ad)
        val (a2, _) = Ratchet.decrypt(a1, h2, c2, ad)
        
        // DESYNC: Bob thinks he sent M2 and is on state b2.
        // But Alice somehow loses state and goes back to a1.
        // Now Alice tries to decrypt M2 again.
        val (a1Again, pt2) = Ratchet.decrypt(a1, h2, c2, ad)
        assertContentEquals("M2".encodeToByteArray(), pt2)
        
        // Now they should be back in sync? 
        // a1Again should be equivalent to a2.
        
        val (a3, h3, c3) = Ratchet.encrypt(a1Again, "M3".encodeToByteArray(), ad)
        val (b3, pt3) = Ratchet.decrypt(b2, h3, c3, ad)
        assertContentEquals("M3".encodeToByteArray(), pt3)
    }
}
