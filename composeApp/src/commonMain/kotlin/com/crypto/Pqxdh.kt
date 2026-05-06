package com.fortrx.crypto

/** Port of `client/crypto/pqxdh.py` — composes X3DH with an ML-KEM-768 KEM. */
object Pqxdh {
    data class SenderResult(val sharedSecret: ByteArray, val ekPublic: ByteArray, val kemCiphertext: ByteArray)

    fun sender(ikAPriv: ByteArray, ikBPub: ByteArray, spkBPub: ByteArray, opkBPub: ByteArray?,
        kyberPub: ByteArray): SenderResult {
        val ek = CryptoPrimitives.x25519Generate()
        val dh1 = CryptoPrimitives.x25519Diffie(ikAPriv, spkBPub)
        val dh2 = CryptoPrimitives.x25519Diffie(ek.privateKey, ikBPub)
        val dh3 = CryptoPrimitives.x25519Diffie(ek.privateKey, spkBPub)
        var dhParts = dh1 + dh2 + dh3
        if (opkBPub != null) dhParts += CryptoPrimitives.x25519Diffie(ek.privateKey, opkBPub)
        
        val (ct, ss) = CryptoPrimitives.kyberEncapsulate(kyberPub)
        val combined = deriveX3dhKeyMaterial(dhParts + ss, PQXDH_INFO)
        return SenderResult(combined, ek.publicKey, ct)
    }

    fun receiver(ikBPriv: ByteArray, spkBPriv: ByteArray, ikAPub: ByteArray, ekAPub: ByteArray,
        opkBPriv: ByteArray?, kyberPriv: ByteArray, kemCiphertext: ByteArray): ByteArray {
        val dh1 = CryptoPrimitives.x25519Diffie(spkBPriv, ikAPub)
        val dh2 = CryptoPrimitives.x25519Diffie(ikBPriv, ekAPub)
        val dh3 = CryptoPrimitives.x25519Diffie(spkBPriv, ekAPub)
        var dhParts = dh1 + dh2 + dh3
        if (opkBPriv != null) dhParts += CryptoPrimitives.x25519Diffie(opkBPriv, ekAPub)
        
        val ss = CryptoPrimitives.kyberDecapsulate(kyberPriv, kemCiphertext)
        return deriveX3dhKeyMaterial(dhParts + ss, PQXDH_INFO)
    }
}
