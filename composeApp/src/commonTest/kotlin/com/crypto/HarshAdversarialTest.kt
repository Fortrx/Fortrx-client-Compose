package com.crypto

import com.fortrx.crypto.CryptoPrimitives
import com.fortrx.crypto.Ratchet
import com.fortrx.crypto.RatchetState
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class, ExperimentalCoroutinesApi::class)
class RatchetBoundaryTest {

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

        repeat(1000) {
            val (nextAlice, _, _) = Ratchet.encrypt(currentAlice, "msg".encodeToByteArray(), ad)
            currentAlice = nextAlice
        }

        val (finalAlice, header, ct) = Ratchet.encrypt(currentAlice, "boundary".encodeToByteArray(), ad)
        val (_, pt) = Ratchet.decrypt(bob, header, ct, ad)
        assertContentEquals("boundary".encodeToByteArray(), pt)

        val (tooFarAlice, headerTooFar, ctTooFar) = Ratchet.encrypt(finalAlice, "too far".encodeToByteArray(), ad)
        assertFailsWith<IllegalStateException> {
            Ratchet.decrypt(bob, headerTooFar, ctTooFar, ad)
        }
    }

    @Test
    fun testDeepReorderingAcrossEpochs() {
        var (alice, bob) = setupAliceBob()

        val epoch1 = mutableListOf<Pair<ByteArray, ByteArray>>()
        repeat(5) { i ->
            val (nextA, h, c) = Ratchet.encrypt(alice, "E1-$i".encodeToByteArray(), ad)
            alice = nextA
            epoch1.add(h to c)
        }

        val (firstH, firstC) = epoch1[0]
        val (bob1, _) = Ratchet.decrypt(bob, firstH, firstC, ad)
        bob = bob1

        val (bob2, hBob, cBob) = Ratchet.encrypt(bob, "Bob-E2".encodeToByteArray(), ad)
        bob = bob2

        val (alice1, _) = Ratchet.decrypt(alice, hBob, cBob, ad)
        alice = alice1

        val epoch3 = mutableListOf<Pair<ByteArray, ByteArray>>()
        repeat(5) { i ->
            val (nextA, h, c) = Ratchet.encrypt(alice, "E3-$i".encodeToByteArray(), ad)
            alice = nextA
            epoch3.add(h to c)
        }

        val (lastH, lastC) = epoch3.last()
        val (bob3, ptLast) = Ratchet.decrypt(bob, lastH, lastC, ad)
        assertContentEquals("E3-4".encodeToByteArray(), ptLast)

        val (lateH, lateC) = epoch1[2]
        val (bob4, ptLate) = Ratchet.decrypt(bob3, lateH, lateC, ad)
        assertContentEquals("E1-2".encodeToByteArray(), ptLate)

        val (midH, midC) = epoch3[1]
        val (_, ptMid) = Ratchet.decrypt(bob4, midH, midC, ad)
        assertContentEquals("E3-1".encodeToByteArray(), ptMid)
    }

    @Test
    fun testConcurrencyChaos() = runTest {
        val (alice, bob) = setupAliceBob()
        val aliceState = alice
        val currentBob = bob

        val count = 20
        val payloads = mutableListOf<Pair<ByteArray, ByteArray>>()

        var tempAlice = aliceState
        repeat(count) {
            val (nextA, h, c) = Ratchet.encrypt(tempAlice, "chaos".encodeToByteArray(), ad)
            tempAlice = nextA
            payloads.add(h to c)
        }

        val results = mutableListOf<Deferred<ByteArray>>()
        coroutineScope {
            for (p in payloads) {
                results.add(async(Dispatchers.Default) {
                    val (header, ct) = p
                    val (_, pt) = Ratchet.decrypt(currentBob, header, ct, ad)
                    pt
                })
            }
        }
        
        for (res in results) {
            assertContentEquals("chaos".encodeToByteArray(), res.await())
        }
    }

    @Test
    fun testProtocolDesyncRecoveryAttempt() {
        val (alice, bob) = setupAliceBob()

        val (a1, h1, c1) = Ratchet.encrypt(alice, "M1".encodeToByteArray(), ad)
        val (b1, _) = Ratchet.decrypt(bob, h1, c1, ad)

        val (b2, h2, c2) = Ratchet.encrypt(b1, "M2".encodeToByteArray(), ad)
        val (a2, _) = Ratchet.decrypt(a1, h2, c2, ad)

        val (a1Again, pt2) = Ratchet.decrypt(a1, h2, c2, ad)
        assertContentEquals("M2".encodeToByteArray(), pt2)

        val (a3, h3, c3) = Ratchet.encrypt(a1Again, "M3".encodeToByteArray(), ad)
        val (b3, pt3) = Ratchet.decrypt(b2, h3, c3, ad)
        assertContentEquals("M3".encodeToByteArray(), pt3)
    }
}
