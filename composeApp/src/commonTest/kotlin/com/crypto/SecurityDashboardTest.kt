package com.crypto

import com.fortrx.crypto.CryptoPrimitives
import com.fortrx.crypto.Ratchet
import com.fortrx.crypto.RatchetState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.*

@OptIn(ExperimentalEncodingApi::class)
class SecurityDashboardTest {

    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private val ad = "dashboard_ad".encodeToByteArray()
    private val sharedSecret = CryptoPrimitives.randomBytes(32)
    private val bobInitialKp = CryptoPrimitives.x25519Generate()

    private fun inspectState(label: String, state: RatchetState) {
        println("--- SECURITY DASHBOARD: $label ---")
        println("Root Key: ${Base64.encode(state.rootKey)}")
        println("Send Count: ${state.sendCount}")
        println("Recv Count: ${state.recvCount}")
        println("Previous Send Count: ${state.previousSendCount}")
        println("DH Sending Public: ${Base64.encode(state.dhSendingPublic)}")
        println("DH Remote Public: ${state.dhRemotePublic?.let { Base64.encode(it) } ?: "null"}")
        println("Skipped Keys Count: ${state.skippedMessageKeys.size}")
        
        // Check invariants
        assertTrue(state.rootKey.size == 32)
        assertTrue(state.dhSendingPublic.size == 32)
        if (state.dhRemotePublic != null) assertTrue(state.dhRemotePublic!!.size == 32)
        
        println("--- END DASHBOARD ---")
    }

    @Test
    fun testProtocolHealthCheck() {
        var alice = Ratchet.initSender(sharedSecret, bobInitialKp.publicKey)
        var bob = Ratchet.initReceiver(sharedSecret, bobInitialKp.privateKey, bobInitialKp.publicKey)
        
        // Simulate some traffic
        repeat(5) { i ->
            val (nextAlice, header, ct) = Ratchet.encrypt(alice, "Alice-$i".encodeToByteArray(), ad)
            alice = nextAlice
            val (nextBob, _) = Ratchet.decrypt(bob, header, ct, ad)
            bob = nextBob
        }
        
        repeat(3) { i ->
            val (nextBob, header, ct) = Ratchet.encrypt(bob, "Bob-$i".encodeToByteArray(), ad)
            bob = nextBob
            val (nextAlice, _) = Ratchet.decrypt(alice, header, ct, ad)
            alice = nextAlice
        }

        inspectState("Alice Final State", alice)
        inspectState("Bob Final State", bob)
        
        // In a healthy state with all messages received, skipped keys should be 0
        assertEquals(0, alice.skippedMessageKeys.size)
        assertEquals(0, bob.skippedMessageKeys.size)
        
        // Root keys should eventually sync (or be derived from same path)
        // Note: In Double Ratchet, Alice and Bob have same Root Key after a DH ratchet step.
        assertContentEquals(alice.rootKey, bob.rootKey)
    }

    @Test
    fun testStateSerializationConsistency() {
        val alice = Ratchet.initSender(sharedSecret, bobInitialKp.publicKey)
        val serialized = json.encodeToString(alice)
        val deserialized = json.decodeFromString<RatchetState>(serialized)
        
        assertContentEquals(alice.rootKey, deserialized.rootKey)
        assertContentEquals(alice.dhSendingPublic, deserialized.dhSendingPublic)
        assertEquals(alice.sendCount, deserialized.sendCount)
    }
}
