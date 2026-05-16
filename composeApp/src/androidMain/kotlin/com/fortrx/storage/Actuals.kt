package com.fortrx.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.fortrx.db.FortrxDb
import com.fortrx.platform.AndroidContextHolder
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

actual fun createSqlDriver(dbFilePath: String, storagePassword: String): SqlDriver {
    return AndroidSqliteDriver(FortrxDb.Schema, AndroidContextHolder.appContext, dbFilePath)
}

actual fun deleteDatabaseFile(dbName: String) {
    AndroidContextHolder.appContext.deleteDatabase(dbName)
}

actual fun migrateIfNeeded(driver: SqlDriver) {
    // AndroidSqliteDriver handles creation and migration in its constructor via FortrxDb.Schema.
}

actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

actual fun loadOrCreateMasterSalt(): ByteArray {
    val masterKey = MasterKey.Builder(AndroidContextHolder.appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    val prefs = EncryptedSharedPreferences.create(
        AndroidContextHolder.appContext,
        "fortrx_master_salt_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    val saltB64 = prefs.getString("master_key_salt", null)
    if (saltB64 != null) {
        return android.util.Base64.decode(saltB64, android.util.Base64.DEFAULT)
    }
    val newSalt = secureRandomBytes(32)
    prefs.edit().putString("master_key_salt", android.util.Base64.encodeToString(newSalt, android.util.Base64.DEFAULT)).apply()
    return newSalt
} // FIXED: Static PBKDF2 Salt for Master Key

actual fun pbkdf2Sha256(password: String, salt: ByteArray, iterations: Int, keyLen: Int): ByteArray {
    val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyLen * 8)
    return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
}

actual fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    return cipher.doFinal(plaintext)
}

actual fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertextAndTag: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    return cipher.doFinal(ciphertextAndTag)
}
