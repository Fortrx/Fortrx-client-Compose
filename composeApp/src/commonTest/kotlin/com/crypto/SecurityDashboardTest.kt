package com.crypto

import com.fortrx.crypto.CryptoPrimitives
import com.fortrx.crypto.Ratchet
import com.fortrx.crypto.RatchetState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class RatchetStateContractTest {

    private val json = Json { encodeDefaults = true }
    private val ad = "dashboard_ad".encodeToByteArray()
    private val sharedSecret = CryptoPrimitives.randomBytes(32)
    private val bobInitialKp = CryptoPrimitives.x25519Generate()

    @Test
    fun stateRemainsHealthyAfterBidirectionalTraffic() {
        var alice = Ratchet.initSender(sharedSecret, bobInitialKp.publicKey)
        var bob = Ratchet.initReceiver(sharedSecret, bobInitialKp.privateKey, bobInitialKp.publicKey)

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

        assertEquals(32, alice.rootKey.size)
        assertEquals(32, bob.rootKey.size)
        assertEquals(32, alice.dhSendingPublic.size)
        assertEquals(32, bob.dhSendingPublic.size)
        assertNotNull(alice.dhRemotePublic)
        assertNotNull(bob.dhRemotePublic)
        assertEquals(0, alice.skippedMessageKeys.size)
        assertEquals(0, bob.skippedMessageKeys.size)
    }

    @Test
    fun stateSerializationPreservesCoreFields() {
        val alice = Ratchet.initSender(sharedSecret, bobInitialKp.publicKey)
        val serialized = json.encodeToString(alice)
        val deserialized = json.decodeFromString<RatchetState>(serialized)

        assertContentEquals(alice.rootKey, deserialized.rootKey)
        assertContentEquals(alice.dhSendingPublic, deserialized.dhSendingPublic)
        assertEquals(alice.sendCount, deserialized.sendCount)
        assertTrue(deserialized.skippedMessageKeys.isEmpty())
    }
}
