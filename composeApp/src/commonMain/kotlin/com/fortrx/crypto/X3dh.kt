package com.fortrx.crypto

/** Port of `client/crypto/x3dh.py`. */
object X3dh {
    data class SenderResult(val sharedSecret: ByteArray, val ekPublic: ByteArray)

    fun sender(ikAPriv: ByteArray, ikBPub: ByteArray, spkBPub: ByteArray, opkBPub: ByteArray?): SenderResult {
        val ek = CryptoPrimitives.x25519Generate()
        val dh1 = CryptoPrimitives.x25519Diffie(ikAPriv, spkBPub)
        val dh2 = CryptoPrimitives.x25519Diffie(ek.privateKey, ikBPub)
        val dh3 = CryptoPrimitives.x25519Diffie(ek.privateKey, spkBPub)
        var combined = dh1 + dh2 + dh3
        if (opkBPub != null) combined += CryptoPrimitives.x25519Diffie(ek.privateKey, opkBPub)
        return SenderResult(deriveX3dhKeyMaterial(combined, X3DH_INFO), ek.publicKey)
    }

    fun receiver(ikBPriv: ByteArray, spkBPriv: ByteArray, ikAPub: ByteArray, ekAPub: ByteArray,
        opkBPriv: ByteArray?): ByteArray {
        val dh1 = CryptoPrimitives.x25519Diffie(spkBPriv, ikAPub)
        val dh2 = CryptoPrimitives.x25519Diffie(ikBPriv, ekAPub)
        val dh3 = CryptoPrimitives.x25519Diffie(spkBPriv, ekAPub)
        var combined = dh1 + dh2 + dh3
        if (opkBPriv != null) combined += CryptoPrimitives.x25519Diffie(opkBPriv, ekAPub)
        return deriveX3dhKeyMaterial(combined, X3DH_INFO)
    }
}
