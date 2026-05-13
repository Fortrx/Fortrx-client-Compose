package com.crypto

import com.fortrx.crypto.CryptoPrimitives
import com.fortrx.crypto.SealedSender
import kotlin.test.*

class SealedSenderSecurityTest {

    private val aliceIk = CryptoPrimitives.x25519Generate()
    private val bobIk = CryptoPrimitives.x25519Generate()
    private val senderId = 123L
    private val ciphertext = "inner_ciphertext".encodeToByteArray()
    private val headerJson = """{"n": 0}"""

    @Test
    fun testSealedSenderRoundTrip() {
        val envelope = SealedSender.seal(
            senderId, aliceIk.privateKey, aliceIk.publicKey,
            bobIk.publicKey, ciphertext, headerJson
        )
        
        val opened = SealedSender.open(bobIk.privateKey, bobIk.publicKey, envelope.blob)
        
        assertEquals(senderId, opened.senderId)
        assertContentEquals(ciphertext, opened.ciphertext)
        assertEquals(headerJson, opened.headerJson)
        assertContentEquals(aliceIk.publicKey, opened.senderIkPublic)
    }

    @Test
    fun testModifiedEphemeralPublic() {
        val envelope = SealedSender.seal(
            senderId, aliceIk.privateKey, aliceIk.publicKey,
            bobIk.publicKey, ciphertext, headerJson
        )
        
        val blob = envelope.blob.copyOf()
        // Offset 4 is ephemeral public key
        blob[4] = (blob[4].toInt() xor 0xFF).toByte()
        
        assertFails {
            SealedSender.open(bobIk.privateKey, bobIk.publicKey, blob)
        }
    }

    @Test
    fun testModifiedInnerCiphertext() {
        val envelope = SealedSender.seal(
            senderId, aliceIk.privateKey, aliceIk.publicKey,
            bobIk.publicKey, ciphertext, headerJson
        )
        
        val blob = envelope.blob.copyOf()
        // Last 32 bytes is s_mac. Let's modify s_ciphertext which is before s_mac.
        val sMacSize = 32
        blob[blob.size - sMacSize - 1] = (blob[blob.size - sMacSize - 1].toInt() xor 0xFF).toByte()
        
        assertFails {
            SealedSender.open(bobIk.privateKey, bobIk.publicKey, blob)
        }
    }

    @Test
    fun testWrongRecipientKey() {
        val envelope = SealedSender.seal(
            senderId, aliceIk.privateKey, aliceIk.publicKey,
            bobIk.publicKey, ciphertext, headerJson
        )
        
        val malloryIk = CryptoPrimitives.x25519Generate()
        
        assertFails {
            SealedSender.open(malloryIk.privateKey, malloryIk.publicKey, envelope.blob)
        }
    }
}
