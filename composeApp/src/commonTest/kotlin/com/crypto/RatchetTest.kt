package com.crypto

import com.fortrx.crypto.CryptoPrimitives
import com.fortrx.crypto.Ratchet
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class RatchetTest {

    @Test
    fun testRatchetRoundTrip() {
        val sharedSecret = CryptoPrimitives.randomBytes(32)
        val bobInitialKp = CryptoPrimitives.x25519Generate()
        
        val alice = Ratchet.initSender(sharedSecret, bobInitialKp.publicKey)
        val bob = Ratchet.initReceiver(sharedSecret, bobInitialKp.privateKey, bobInitialKp.publicKey)
        
        val plaintext1 = "Hello Bob!".encodeToByteArray()
        val ad = "ad".encodeToByteArray()
        
        val (alice1, header1, ct1) = Ratchet.encrypt(alice, plaintext1, ad)
        val (bob1, pt1) = Ratchet.decrypt(bob, header1, ct1, ad)
        
        assertContentEquals(plaintext1, pt1)
        
        val plaintext2 = "Hi Alice!".encodeToByteArray()
        val (bob2, header2, ct2) = Ratchet.encrypt(bob1, plaintext2, ad)
        val (alice2, pt2) = Ratchet.decrypt(alice1, header2, ct2, ad)
        
        assertContentEquals(plaintext2, pt2)
    }

    @Test
    fun testSkippedKeys() {
        val sharedSecret = CryptoPrimitives.randomBytes(32)
        val bobInitialKp = CryptoPrimitives.x25519Generate()
        
        val alice = Ratchet.initSender(sharedSecret, bobInitialKp.publicKey)
        val bob = Ratchet.initReceiver(sharedSecret, bobInitialKp.privateKey, bobInitialKp.publicKey)
        
        val ad = "ad".encodeToByteArray()
        
        // Alice sends 3 messages
        val (alice1, header1, ct1) = Ratchet.encrypt(alice, "m1".encodeToByteArray(), ad)
        val (alice2, header2, ct2) = Ratchet.encrypt(alice1, "m2".encodeToByteArray(), ad)
        val (alice3, header3, ct3) = Ratchet.encrypt(alice2, "m3".encodeToByteArray(), ad)
        
        // Bob receives m3 first
        val (bob1, pt3) = Ratchet.decrypt(bob, header3, ct3, ad)
        assertContentEquals("m3".encodeToByteArray(), pt3)
        assertEquals(2, bob1.skippedMessageKeys.size)
        
        // Bob receives m1
        val (bob2, pt1) = Ratchet.decrypt(bob1, header1, ct1, ad)
        assertContentEquals("m1".encodeToByteArray(), pt1)
        assertEquals(1, bob2.skippedMessageKeys.size)
        
        // Bob receives m2
        val (bob3, pt2) = Ratchet.decrypt(bob2, header2, ct2, ad)
        assertContentEquals("m2".encodeToByteArray(), pt2)
        assertEquals(0, bob3.skippedMessageKeys.size)
    }
}
