package com.fortrx.storage

import com.fortrx.platform.debugLog
import com.fortrx.crypto.constantTimeStringEquals
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class StorageError(message: String, cause: Throwable? = null) : Exception(message, cause)

internal val FORMAT_V2_MAGIC: ByteArray = byteArrayOf(
    'F'.code.toByte(), 'R'.code.toByte(), 'X'.code.toByte(), 'E'.code.toByte(),
    'N'.code.toByte(), 'C'.code.toByte(), '2'.code.toByte(), 0x00,
)
internal val FORMAT_V3_MAGIC: ByteArray = byteArrayOf(
    'F'.code.toByte(), 'R'.code.toByte(), 'X'.code.toByte(), 'E'.code.toByte(),
    'N'.code.toByte(), 'C'.code.toByte(), '3'.code.toByte(), 0x00,
)

internal const val LEGACY_SALT_SIZE = 16
internal const val SALT_SIZE = 32
internal const val NONCE_SIZE = 12
internal const val GCM_TAG_SIZE = 16
internal const val PBKDF2_ITERATIONS = 480_000
internal const val ITEM_KDF_ITERATIONS = 100_000
internal const val KEY_SIZE_BYTES = 32

expect fun secureRandomBytes(size: Int): ByteArray
expect fun loadOrCreateMasterSalt(): ByteArray
expect fun pbkdf2Sha256(password: String, salt: ByteArray, iterations: Int, keyLen: Int): ByteArray
expect fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray
expect fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray): ByteArray

private object MasterKeyCache {
    @Volatile var masterKey: String? = null
    @Volatile var lastPassword: String? = null
}

private fun deriveKey(password: String, salt: ByteArray): ByteArray =
    pbkdf2Sha256(password, salt, PBKDF2_ITERATIONS, KEY_SIZE_BYTES)

@OptIn(ExperimentalEncodingApi::class)
private fun getMasterKey(password: String): String {
    val cached = MasterKeyCache.masterKey
    if (cached != null && MasterKeyCache.lastPassword != null &&
        constantTimeStringEquals(MasterKeyCache.lastPassword!!, password)) return cached // FIXED: Constant-Time Password Comparison
    
    debugLog("Deriving storage master key.")
    val salt = loadOrCreateMasterSalt() // FIXED: Static PBKDF2 Salt for Master Key
    val key = try {
        deriveKey(password, salt)
    } catch (e: Exception) {
        debugLog("Master key derivation failed.", e)
        throw e
    }
    val encoded = Base64.encode(key)
    MasterKeyCache.masterKey = encoded
    MasterKeyCache.lastPassword = password
    return encoded
}

internal fun initStorageCrypto(password: String) {
    getMasterKey(password)
}

internal fun encrypt(data: ByteArray, password: String): ByteArray {
    val masterKey = getMasterKey(password)
    val salt = secureRandomBytes(SALT_SIZE)
    val nonce = secureRandomBytes(NONCE_SIZE)
    
    val itemKey = try {
        pbkdf2Sha256(masterKey, salt, ITEM_KDF_ITERATIONS, KEY_SIZE_BYTES) // FIXED: 1-Iteration PBKDF2 for Per-Item Keys
    } catch (e: Exception) {
        debugLog("Item key derivation failed during encryption.", e)
        throw e
    }
    
    return FORMAT_V3_MAGIC + salt + nonce + aesGcmEncrypt(itemKey, nonce, data)
}

private fun startsWith(data: ByteArray, prefix: ByteArray): Boolean {
    if (data.size < prefix.size) return false
    for (i in prefix.indices) if (data[i] != prefix[i]) return false
    return true
}

internal fun decrypt(data: ByteArray, password: String): ByteArray = try {
    if (startsWith(data, FORMAT_V3_MAGIC)) {
        val masterKey = getMasterKey(password)
        val min = FORMAT_V3_MAGIC.size + SALT_SIZE + NONCE_SIZE + GCM_TAG_SIZE
        if (data.size < min) throw StorageError("Wrong password or corrupted file")
        var off = FORMAT_V3_MAGIC.size
        val salt = data.copyOfRange(off, off + SALT_SIZE); off += SALT_SIZE
        val nonce = data.copyOfRange(off, off + NONCE_SIZE); off += NONCE_SIZE
        val ct = data.copyOfRange(off, data.size)
        
        val itemKey = pbkdf2Sha256(masterKey, salt, ITEM_KDF_ITERATIONS, KEY_SIZE_BYTES) // FIXED: 1-Iteration PBKDF2 for Per-Item Keys
        aesGcmDecrypt(itemKey, nonce, ct)
    } else if (startsWith(data, FORMAT_V2_MAGIC)) {
        val min = FORMAT_V2_MAGIC.size + SALT_SIZE + NONCE_SIZE + GCM_TAG_SIZE
        if (data.size < min) throw StorageError("Wrong password or corrupted file")
        var off = FORMAT_V2_MAGIC.size
        val salt = data.copyOfRange(off, off + SALT_SIZE); off += SALT_SIZE
        val nonce = data.copyOfRange(off, off + NONCE_SIZE); off += NONCE_SIZE
        val ct = data.copyOfRange(off, data.size)
        aesGcmDecrypt(deriveKey(password, salt), nonce, ct)
    } else {
        val min = LEGACY_SALT_SIZE + NONCE_SIZE + GCM_TAG_SIZE
        if (data.size < min) throw StorageError("Wrong password or corrupted file")
        val salt = data.copyOfRange(0, LEGACY_SALT_SIZE)
        val nonce = data.copyOfRange(LEGACY_SALT_SIZE, LEGACY_SALT_SIZE + NONCE_SIZE)
        val ct = data.copyOfRange(LEGACY_SALT_SIZE + NONCE_SIZE, data.size)
        aesGcmDecrypt(deriveKey(password, salt), nonce, ct)
    }
} catch (e: StorageError) { throw e } catch (t: Throwable) {
    throw StorageError("Wrong password or corrupted file", t)
}
