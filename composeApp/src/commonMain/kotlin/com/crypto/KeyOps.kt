package com.fortrx.crypto

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Port of `client/crypto/keys.py`. */
@OptIn(ExperimentalEncodingApi::class)
object KeyOps {
    data class IdentityKeypair(
        val dhPrivate: ByteArray, val dhPublic: ByteArray,
        val signingPrivate: ByteArray, val signingPublic: ByteArray,
    )

    fun generateIdentityKeypair(seed: ByteArray? = null): IdentityKeypair {
        val dh = CryptoPrimitives.x25519Generate(seed)
        val sign = CryptoPrimitives.ed25519Generate(seed)
        return IdentityKeypair(dh.privateKey, dh.publicKey, sign.privateKey, sign.publicKey)
    }

    data class SignedPrekey(val privateKey: ByteArray, val publicKey: ByteArray, val signature: ByteArray)

    fun generateSignedPrekey(signingPrivate: ByteArray): SignedPrekey {
        val pre = CryptoPrimitives.x25519Generate()
        val sig = CryptoPrimitives.ed25519Sign(signingPrivate, encodeCurvePublicKey(pre.publicKey))
        return SignedPrekey(pre.privateKey, pre.publicKey, sig)
    }

    fun verifySignedPrekey(signingPublic: ByteArray, prekeyPublic: ByteArray, signature: ByteArray): Boolean {
        // Try both prefixed and raw forms (matches Python behavior).
        val msgs = listOf(encodeCurvePublicKey(prekeyPublic), prekeyPublic)
        return msgs.any { CryptoPrimitives.ed25519Verify(signingPublic, it, signature) }
    }

    fun verifyKyberPrekey(signingPublic: ByteArray, kyberPublic: ByteArray, signature: ByteArray): Boolean {
        val msgs = listOf(encodeMlkemPublicKey(kyberPublic), kyberPublic)
        return msgs.any { CryptoPrimitives.ed25519Verify(signingPublic, it, signature) }
    }

    data class OneTimePrekey(val privateKey: ByteArray, val publicKey: ByteArray)

    fun generateOneTimePrekeys(count: Int = 10): List<OneTimePrekey> = List(count) {
        val kp = CryptoPrimitives.x25519Generate()
        OneTimePrekey(kp.privateKey, kp.publicKey)
    }

    fun encodePublicKey(raw: ByteArray): String = Base64.encode(raw)
    fun decodePublicKey(b64: String): ByteArray = Base64.decode(b64)

    data class KyberPrekey(val publicKey: ByteArray, val privateKey: ByteArray, val signature: ByteArray)

    fun generateKyberPrekey(signingPrivate: ByteArray): KyberPrekey {
        val kp = CryptoPrimitives.kyberGenerate()
        val sig = CryptoPrimitives.ed25519Sign(signingPrivate, encodeMlkemPublicKey(kp.publicKey))
        require(sig.size == 64) { "unexpected signature size" }
        return KyberPrekey(kp.publicKey, kp.privateKey, sig)
    }
}
