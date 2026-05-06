package com.fortrx.platform

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.fortrx.db.FortrxDb
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object AndroidContextHolder {
    lateinit var appContext: Context
}

actual class DriverFactory {
    actual fun createDriver(passphrase: String): SqlDriver =
        AndroidSqliteDriver(FortrxDb.Schema, AndroidContextHolder.appContext, "fortrx.db")
}

actual object SecureRandomBytes {
    private val rng = SecureRandom()
    actual fun nextBytes(n: Int): ByteArray = ByteArray(n).also { rng.nextBytes(it) }
}

actual object Pbkdf2 {
    actual fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int, lenBytes: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, lenBytes * 8)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}

actual object AesGcm {
    actual fun encrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad.isNotEmpty()) c.updateAAD(aad)
        return c.doFinal(plaintext)
    }
    actual fun decrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad.isNotEmpty()) c.updateAAD(aad)
        return c.doFinal(ciphertext)
    }
}

actual object X25519 {
    actual fun generateKeypair(): Pair<ByteArray, ByteArray> {
        val priv = X25519PrivateKeyParameters(SecureRandom())
        return priv.encoded to priv.generatePublicKey().encoded
    }
    actual fun sharedSecret(priv: ByteArray, peerPub: ByteArray): ByteArray {
        val out = ByteArray(32)
        X25519PrivateKeyParameters(priv, 0).generateSecret(X25519PublicKeyParameters(peerPub, 0), out, 0)
        return out
    }
}

actual object Ed25519 {
    actual fun generateKeypair(): Pair<ByteArray, ByteArray> {
        val priv = Ed25519PrivateKeyParameters(SecureRandom())
        return priv.encoded to priv.generatePublicKey().encoded
    }
    actual fun sign(priv: ByteArray, msg: ByteArray): ByteArray {
        val s = Ed25519Signer().apply { init(true, Ed25519PrivateKeyParameters(priv, 0)) }
        s.update(msg, 0, msg.size)
        return s.generateSignature()
    }
    actual fun verify(pub: ByteArray, msg: ByteArray, sig: ByteArray): Boolean {
        val v = Ed25519Signer().apply { init(false, Ed25519PublicKeyParameters(pub, 0)) }
        v.update(msg, 0, msg.size)
        return v.verifySignature(sig)
    }
}
