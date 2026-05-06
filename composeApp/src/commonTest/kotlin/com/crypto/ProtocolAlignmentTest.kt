package com.crypto

import com.fortrx.crypto.*
import kotlin.test.Test
import kotlin.test.assertContentEquals

class ProtocolAlignmentTest {

    @Test
    fun testX3dhAlignment() {
        val aliceIk = CryptoPrimitives.x25519Generate()
        val bobIk = CryptoPrimitives.x25519Generate()
        val bobSpk = CryptoPrimitives.x25519Generate()
        val bobOpk = CryptoPrimitives.x25519Generate()

        val senderRes = X3dh.sender(
            ikAPriv = aliceIk.privateKey,
            ikBPub = bobIk.publicKey,
            spkBPub = bobSpk.publicKey,
            opkBPub = bobOpk.publicKey
        )

        val receiverSecret = X3dh.receiver(
            ikBPriv = bobIk.privateKey,
            spkBPriv = bobSpk.privateKey,
            ikAPub = aliceIk.publicKey,
            ekAPub = senderRes.ekPublic,
            opkBPriv = bobOpk.privateKey
        )

        assertContentEquals(senderRes.sharedSecret, receiverSecret)

        // Manual derivation to match Python's test_x3dh_matches_signal_style_kdf_construction
        val dh1 = CryptoPrimitives.x25519Diffie(bobSpk.privateKey, aliceIk.publicKey)
        val dh2 = CryptoPrimitives.x25519Diffie(bobIk.privateKey, senderRes.ekPublic)
        val dh3 = CryptoPrimitives.x25519Diffie(bobSpk.privateKey, senderRes.ekPublic)
        val dh4 = CryptoPrimitives.x25519Diffie(bobOpk.privateKey, senderRes.ekPublic)
        
        val manual = deriveX3dhKeyMaterial(dh1 + dh2 + dh3 + dh4, X3DH_INFO)
        assertContentEquals(senderRes.sharedSecret, manual)
    }

    @Test
    fun testPqxdhAlignment() {
        val aliceIk = CryptoPrimitives.x25519Generate()
        val bobIk = CryptoPrimitives.x25519Generate()
        val bobSpk = CryptoPrimitives.x25519Generate()
        val bobOpk = CryptoPrimitives.x25519Generate()
        val bobKyber = CryptoPrimitives.kyberGenerate()

        val senderRes = Pqxdh.sender(
            ikAPriv = aliceIk.privateKey,
            ikBPub = bobIk.publicKey,
            spkBPub = bobSpk.publicKey,
            opkBPub = bobOpk.publicKey,
            kyberPub = bobKyber.publicKey
        )

        val receiverSecret = Pqxdh.receiver(
            ikBPriv = bobIk.privateKey,
            spkBPriv = bobSpk.privateKey,
            ikAPub = aliceIk.publicKey,
            ekAPub = senderRes.ekPublic,
            opkBPriv = bobOpk.privateKey,
            kyberPriv = bobKyber.privateKey,
            kemCiphertext = senderRes.kemCiphertext
        )

        assertContentEquals(senderRes.sharedSecret, receiverSecret)

        // Manual derivation
        val dh1 = CryptoPrimitives.x25519Diffie(bobSpk.privateKey, aliceIk.publicKey)
        val dh2 = CryptoPrimitives.x25519Diffie(bobIk.privateKey, senderRes.ekPublic)
        val dh3 = CryptoPrimitives.x25519Diffie(bobSpk.privateKey, senderRes.ekPublic)
        val dh4 = CryptoPrimitives.x25519Diffie(bobOpk.privateKey, senderRes.ekPublic)
        val kemSecret = CryptoPrimitives.kyberDecapsulate(bobKyber.privateKey, senderRes.kemCiphertext)
        
        val manual = deriveX3dhKeyMaterial(dh1 + dh2 + dh3 + dh4 + kemSecret, PQXDH_INFO)
        assertContentEquals(senderRes.sharedSecret, manual)
    }
}
