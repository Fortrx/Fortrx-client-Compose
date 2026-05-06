package com.crypto

import com.fortrx.crypto.CryptoPrimitives
import com.fortrx.crypto.SealedSender
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SealedSenderTest {

    @Test
    fun testSealedSenderV2RoundTrip() {
        val recipientKp = CryptoPrimitives.x25519Generate()
        val senderKp = CryptoPrimitives.x25519Generate()
        
        val senderId = 7L
        val ciphertext = "top secret".encodeToByteArray()
        val headerJson = "{\"n\":0,\"pn\":0}"
        
        val envelope = SealedSender.seal(
            senderId = senderId,
            senderIkPrivate = senderKp.privateKey,
            senderIkPublic = senderKp.publicKey,
            recipientIkPublic = recipientKp.publicKey,
            ciphertext = ciphertext,
            headerJson = headerJson
        )
        
        val opened = SealedSender.open(
            recipientIkPrivate = recipientKp.privateKey,
            recipientIkPublic = recipientKp.publicKey,
            envelope = envelope.blob
        )
        
        assertEquals(senderId, opened.senderId)
        assertContentEquals(ciphertext, opened.ciphertext)
        assertEquals(headerJson, opened.headerJson)
        assertContentEquals(senderKp.publicKey, opened.senderIkPublic)
    }
}
