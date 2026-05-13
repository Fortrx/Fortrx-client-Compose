package com.fortrx.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.fortrx.db.FortrxDb
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

actual fun createSqlDriver(dbFilePath: String, storagePassword: String): SqlDriver {
    return JdbcSqliteDriver("jdbc:sqlite:$dbFilePath")
}

actual fun deleteDatabaseFile(dbName: String) {
    File(dbName).delete()
}

actual fun migrateIfNeeded(driver: SqlDriver) {
    val version = getDbVersion(driver)
    if (version == 0L) {
        FortrxDb.Schema.create(driver)
        setDbVersion(driver, FortrxDb.Schema.version)
    } else if (version < FortrxDb.Schema.version) {
        FortrxDb.Schema.migrate(driver, version, FortrxDb.Schema.version)
        setDbVersion(driver, FortrxDb.Schema.version)
    }
}

private fun getDbVersion(driver: SqlDriver): Long {
    return driver.executeQuery(null, "PRAGMA user_version;", { cursor ->
        val version = if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L
        QueryResult.Value(version)
    }, 0).value
}

private fun setDbVersion(driver: SqlDriver, version: Long) {
    driver.execute(null, "PRAGMA user_version = $version;", 0)
}

actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

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
