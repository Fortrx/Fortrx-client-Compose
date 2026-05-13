package com.fortrx.crypto

import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.*
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.pqc.crypto.mlkem.*
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val rng = SecureRandom()

private val initBC = lazy {
    if (Security.getProvider("BC") == null) {
        Security.addProvider(BouncyCastleProvider())
    }
}

actual object CryptoPrimitives {
    init { initBC.value }

    actual fun x25519Generate(seed: ByteArray?): KeyPair {
        val priv = if (seed != null) {
            val s = if (seed.size == 32) seed else sha256(seed)
            X25519PrivateKeyParameters(s, 0)
        } else {
            val gen = X25519KeyPairGenerator()
            gen.init(X25519KeyGenerationParameters(rng))
            gen.generateKeyPair().private as X25519PrivateKeyParameters
        }
        return KeyPair(priv.encoded, priv.generatePublicKey().encoded)
    }
    actual fun x25519Diffie(privateKey: ByteArray, peerPublic: ByteArray): ByteArray {
        val priv = X25519PrivateKeyParameters(privateKey, 0)
        val pub = X25519PublicKeyParameters(peerPublic, 0)
        val res = ByteArray(32)
        priv.generateSecret(pub, res, 0)
        return res
    }
    actual fun ed25519Generate(seed: ByteArray?): KeyPair {
        val priv = if (seed != null) {
            val s = if (seed.size == 32) seed else sha256(seed)
            Ed25519PrivateKeyParameters(s, 0)
        } else {
            val gen = Ed25519KeyPairGenerator()
            gen.init(Ed25519KeyGenerationParameters(rng))
            gen.generateKeyPair().private as Ed25519PrivateKeyParameters
        }
        return KeyPair(priv.encoded, priv.generatePublicKey().encoded)
    }
    actual fun ed25519Sign(privateKey: ByteArray, message: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKey, 0))
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }
    actual fun ed25519Verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        val signer = Ed25519Signer()
        signer.init(false, Ed25519PublicKeyParameters(publicKey, 0))
        signer.update(message, 0, message.size)
        return signer.verifySignature(signature)
    }
    actual fun kyberGenerate(seed: ByteArray?): KeyPair {
        val gen = MLKEMKeyPairGenerator()
        if (seed != null) {
            val fixedRng = object : SecureRandom() {
                private var pos = 0
                override fun nextBytes(bytes: ByteArray) {
                    for (i in bytes.indices) {
                        bytes[i] = seed[pos % seed.size]
                        pos++
                    }
                }
            }
            gen.init(MLKEMKeyGenerationParameters(fixedRng, MLKEMParameters.ml_kem_768))
        } else {
            gen.init(MLKEMKeyGenerationParameters(rng, MLKEMParameters.ml_kem_768))
        }
        val pair = gen.generateKeyPair()
        val priv = pair.private as MLKEMPrivateKeyParameters
        val pub = pair.public as MLKEMPublicKeyParameters
        return KeyPair(priv.encoded, pub.encoded)
    }
    actual fun kyberEncapsulate(publicKey: ByteArray): Pair<ByteArray, ByteArray> {
        val pub = MLKEMPublicKeyParameters(MLKEMParameters.ml_kem_768, publicKey)
        val gen = MLKEMGenerator(rng)
        val secret = gen.generateEncapsulated(pub)
        return Pair(secret.encapsulation, secret.secret)
    }
    actual fun kyberDecapsulate(privateKey: ByteArray, ciphertext: ByteArray): ByteArray {
        val priv = MLKEMPrivateKeyParameters(MLKEMParameters.ml_kem_768, privateKey)
        val ext = MLKEMExtractor(priv)
        return ext.extractSecret(ciphertext)
    }

    actual fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        aad?.let { cipher.updateAAD(it) }
        return cipher.doFinal(plaintext)
    }
    actual fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        aad?.let { cipher.updateAAD(it) }
        return cipher.doFinal(ciphertextAndTag)
    }

    actual fun aesCtr(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding", "BC")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), javax.crypto.spec.IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256", "BC")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    actual fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
    actual fun sha512(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").digest(data)
    actual fun randomBytes(size: Int): ByteArray = ByteArray(size).also { rng.nextBytes(it) }
}

