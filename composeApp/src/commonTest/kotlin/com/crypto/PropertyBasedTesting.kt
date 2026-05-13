package com.crypto

import com.fortrx.crypto.CryptoPrimitives
import com.fortrx.crypto.Ratchet
import com.fortrx.crypto.RatchetState
import kotlin.random.Random
import kotlin.test.*

class PropertyBasedTesting {

    private val sharedSecret = CryptoPrimitives.randomBytes(32)
    private val bobInitialKp = CryptoPrimitives.x25519Generate()

    private fun setupAliceBob(): Pair<RatchetState, RatchetState> {
        val alice = Ratchet.initSender(sharedSecret, bobInitialKp.publicKey)
        val bob = Ratchet.initReceiver(sharedSecret, bobInitialKp.privateKey, bobInitialKp.publicKey)
        return alice to bob
    }

    @Test
    fun testRandomRoundTripInvariants() {
        val (alice, bob) = setupAliceBob()
        var currentAlice = alice
        var currentBob = bob
        
        val random = Random(42) // Deterministic seed for reproducibility
        
        repeat(100) { i ->
            val msgSize = random.nextInt(1, 1024)
            val msg = random.nextBytes(msgSize)
            
            val adSize = random.nextInt(0, 128)
            val ad = random.nextBytes(adSize)
            
            // Randomly choose who sends
            if (random.nextBoolean()) {
                // Alice -> Bob
                val (nextA, header, ct) = Ratchet.encrypt(currentAlice, msg, ad)
                currentAlice = nextA
                val (nextB, pt) = Ratchet.decrypt(currentBob, header, ct, ad)
                currentBob = nextB
                assertContentEquals(msg, pt, "Failed at iteration $i (Alice -> Bob)")
            } else {
                // Bob -> Alice
                // Ensure Bob has a sending key first (must have received at least one message)
                if (currentBob.sendingChainKey != null) {
                    val (nextB, header, ct) = Ratchet.encrypt(currentBob, msg, ad)
                    currentBob = nextB
                    val (nextA, pt) = Ratchet.decrypt(currentAlice, header, ct, ad)
                    currentAlice = nextA
                    assertContentEquals(msg, pt, "Failed at iteration $i (Bob -> Alice)")
                }
            }
        }
    }

    @Test
    fun testRandomSkipInvariants() {
        val (alice, bob) = setupAliceBob()
        var currentAlice = alice
        var currentBob = bob
        
        val random = Random(123)
        
        repeat(10) { epoch ->
            val batchSize = random.nextInt(1, 20)
            val messages = mutableListOf<Pair<ByteArray, ByteArray>>()
            val contents = mutableListOf<ByteArray>()
            val ads = mutableListOf<ByteArray>()
            
            repeat(batchSize) {
                val msg = "Epoch-$epoch-Msg-${random.nextInt()}".encodeToByteArray()
                val ad = "Ad-${random.nextInt()}".encodeToByteArray()
                val (nextA, h, c) = Ratchet.encrypt(currentAlice, msg, ad)
                currentAlice = nextA
                messages.add(h to c)
                contents.add(msg)
                ads.add(ad)
            }
            
            // Randomly pick some to decrypt in random order
            val indices = (0 until batchSize).toList().shuffled(random)
            for (idx in indices) {
                val (h, c) = messages[idx]
                val (nextB, pt) = Ratchet.decrypt(currentBob, h, c, ads[idx])
                currentBob = nextB
                assertContentEquals(contents[idx], pt, "Failed at epoch $epoch, index $idx")
            }
            
            // Trigger a DH ratchet step from Bob to keep things interesting
            val (nextB, hB, cB) = Ratchet.encrypt(currentBob, "ping".encodeToByteArray(), "ad".encodeToByteArray())
            currentBob = nextB
            val (nextA, _) = Ratchet.decrypt(currentAlice, hB, cB, "ad".encodeToByteArray())
            currentAlice = nextA
        }
    }
}
