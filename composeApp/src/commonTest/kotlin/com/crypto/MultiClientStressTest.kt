package com.crypto

import com.fortrx.crypto.CryptoPrimitives
import com.fortrx.crypto.Ratchet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiClientStressTest {

    private val ad = "stress_test_ad".encodeToByteArray()
    private val sharedSecret = CryptoPrimitives.randomBytes(32)

    class NetworkPacket(val recipientId: String, val header: ByteArray, val ct: ByteArray)

    @Test
    fun testMessagesSurviveIntermittentBidirectionalDelivery() {
        val bobIk = CryptoPrimitives.x25519Generate()
        
        var aliceState = Ratchet.initSender(sharedSecret, bobIk.publicKey)
        var bobState = Ratchet.initReceiver(sharedSecret, bobIk.privateKey, bobIk.publicKey)
        
        val networkQueue = mutableListOf<NetworkPacket>()
        val receivedByAlice = mutableListOf<String>()
        val receivedByBob = mutableListOf<String>()

        val messageCount = 50
        
        repeat(messageCount) { i ->
            val msgA = "Alice-$i"
            val (nextA, headerA, ctA) = Ratchet.encrypt(aliceState, msgA.encodeToByteArray(), ad)
            aliceState = nextA
            networkQueue.add(NetworkPacket("Bob", headerA, ctA))

            if (i == 0) {
                val first = networkQueue.removeAt(0)
                val (nextB, ptB) = Ratchet.decrypt(bobState, first.header, first.ct, ad)
                bobState = nextB
                receivedByBob.add(ptB.decodeToString())
            }
            
            val msgB = "Bob-$i"
            val (nextB, headerB, ctB) = Ratchet.encrypt(bobState, msgB.encodeToByteArray(), ad)
            bobState = nextB
            networkQueue.add(NetworkPacket("Alice", headerB, ctB))
        }

        networkQueue.shuffle()

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

        assertEquals(messageCount, receivedByAlice.size)
        assertEquals(messageCount, receivedByBob.size)

        for (i in 0 until messageCount) {
            val expectedA = "Alice-$i"
            val expectedB = "Bob-$i"
            assertTrue(receivedByBob.contains(expectedA), "Bob should have received $expectedA")
            assertTrue(receivedByAlice.contains(expectedB), "Alice should have received $expectedB")
        }
    }
}
