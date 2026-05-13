package com.fortrx.services

import com.fortrx.platform.SecureRandomBytes

/**
 * Generates a Signal-style numeric backup phrase used to recover an account.
 * Length is between 30 and 36 digits, formatted in groups of four.
 */
object BackupCode {

    /** Generate a fresh backup code. */
    fun generate(digits: Int = 32): String {
        require(digits in 30..36) { "Enter backup code" }
        val bytes = SecureRandomBytes.nextBytes(digits)
        val sb = StringBuilder(digits)
        for (b in bytes) {
            sb.append((b.toInt() and 0xFF) % 10)
        }
        return sb.toString()
    }

    /** Display form: groups of four separated by hyphens. */
    fun format(code: String): String =
        code.chunked(4).joinToString("-")

    fun normalize(input: String): String =
        input.filter { it.isDigit() }

    fun isValid(input: String): Boolean {
        val n = normalize(input)
        return n.length in 30..36
    }

    /** Derives a 32-byte seed from the numeric backup phrase for key restoration. */
    fun deriveSeed(code: String): ByteArray {
        val normalized = normalize(code)
        val input = if (normalized.length >= 30) normalized else code.padEnd(32, '0').take(32)
        // Use PBKDF2 to turn the numeric string into a high-entropy 32-byte seed.
        // We use a fixed salt for derivation from the backup phrase.
        val salt = "fortrx-backup-salt".encodeToByteArray()
        return com.fortrx.storage.pbkdf2Sha256(input, salt, 100_000, 32)
    }
}
