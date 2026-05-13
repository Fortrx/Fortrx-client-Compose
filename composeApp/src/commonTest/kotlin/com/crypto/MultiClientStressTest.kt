package com.crypto

import com.fortrx.crypto.CryptoPrimitives
import com.fortrx.crypto.Ratchet
import com.fortrx.crypto.RatchetState
import kotlin.test.*

class MultiClientStressTest {

    private val ad = "stress_test_ad".encodeToByteArray()
    private val sharedSecret = CryptoPrimitives.randomBytes(32)

    data class Client(val id: String, var state: RatchetState)
    data class NetworkPacket(val senderId: String, val recipientId: String, val header: ByteArray, val ct: ByteArray)

    @Test
    fun testMultiClientIntermittentNetwork() {
        val aliceIk = CryptoPrimitives.x25519Generate()
        val bobIk = CryptoPrimitives.x25519Generate()
        
        var aliceState = Ratchet.initSender(sharedSecret, bobIk.publicKey)
        var bobState = Ratchet.initReceiver(sharedSecret, bobIk.privateKey, bobIk.publicKey)
        
        val networkQueue = mutableListOf<NetworkPacket>()
        val receivedByAlice = mutableListOf<String>()
        val receivedByBob = mutableListOf<String>()

        val messageCount = 50
        
        // 1. Generate many messages from both sides
        repeat(messageCount) { i ->
            // Alice sends to Bob
            val msgA = "Alice-$i"
            val (nextA, headerA, ctA) = Ratchet.encrypt(aliceState, msgA.encodeToByteArray(), ad)
            aliceState = nextA
            networkQueue.add(NetworkPacket("Alice", "Bob", headerA, ctA))
            
            // Bob sends to Alice
            // Note: Bob can only send after he has a sending chain key, which he gets after his first DH ratchet step.
            // In this test, let's assume Bob receives the first message immediately to bootstrap.
            if (i == 0) {
                val first = networkQueue.removeAt(0)
                val (nextB, ptB) = Ratchet.decrypt(bobState, first.header, first.ct, ad)
                bobState = nextB
                receivedByBob.add(ptB.decodeToString())
            }
            
            val msgB = "Bob-$i"
            val (nextB, headerB, ctB) = Ratchet.encrypt(bobState, msgB.encodeToByteArray(), ad)
            bobState = nextB
            networkQueue.add(NetworkPacket("Bob", "Alice", headerB, ctB))
        }

        // 2. Shuffle the network queue (Adversarial network)
        networkQueue.shuffle()

        // 3. Process the shuffled queue
        while (networkQueue.isNotEmpty()) {
            val packet = networkQueue.removeAt(0)
            if (packet.recipientId == "Bob") {
                val (nextB, ptB) = Ratchet.decrypt(bobState, packet.header, packet.ct, ad)
                bobState = nextB
                receivedByBob.add(ptB.decodeToString())
            } else {
                val (nextA, ptA) = Ratchet.decrypt(aliceState, packet.header, packet.ct, ad)
                aliceState = nextA
                receivedByAlice.add(ptA.decodeToString())
            }
        }

        // 4. Verify all messages were eventually received correctly
        assertEquals(messageCount, receivedByAlice.size + 1) // +1 because we skipped one Alice message processing to bootstrap Bob
        assertEquals(messageCount, receivedByBob.size)
        
        // Check if all "Alice-i" and "Bob-i" are present
        for (i in 0 until messageCount) {
            val expectedA = "Alice-$i"
            val expectedB = "Bob-$i"
            assertTrue(receivedByBob.contains(expectedA), "Bob should have received $expectedA")
            assertTrue(receivedByAlice.contains(expectedB), "Alice should have received $expectedB")
        }
    }
}
