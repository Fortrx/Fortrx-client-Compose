package com.fortrx.platform

import androidx.compose.runtime.Composable

@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)

expect object SecureRandomBytes {
    fun nextBytes(n: Int): ByteArray
}

expect object Pbkdf2 {
    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int, lenBytes: Int): ByteArray
}

expect object AesGcm {
    fun encrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray
    fun decrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray
}

expect object X25519 {
    fun generateKeypair(): Pair<ByteArray, ByteArray> // (priv, pub)
    fun sharedSecret(priv: ByteArray, peerPub: ByteArray): ByteArray
}

expect object Ed25519 {
    fun generateKeypair(): Pair<ByteArray, ByteArray>
    fun sign(priv: ByteArray, msg: ByteArray): ByteArray
    fun verify(pub: ByteArray, msg: ByteArray, sig: ByteArray): Boolean
}

expect object PlatformClock {
    fun nowIso(): String
}

expect fun getPlatformName(): String

expect fun initPlatform(context: Any? = null)

expect fun isDebugRuntime(): Boolean

expect fun readRuntimeEnv(name: String): String?
