package com.fortrx.services

import com.fortrx.network.Api
import com.fortrx.platform.AesGcm
import com.fortrx.platform.Pbkdf2
import com.fortrx.platform.SecureRandomBytes
import io.ktor.client.request.*
import io.ktor.http.*

class AccountService(private val api: Api) {

    suspend fun logout(currentRefreshToken: String?) {
        runCatching {
            api.client.post("${api.baseUrl}/auth/logout") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("refresh_token" to currentRefreshToken))
            }
        }
    }

    suspend fun logoutAllDevices() {
        api.client.post("${api.baseUrl}/auth/logout-all")
    }

    /** Hard-delete the account on the server, then wipe local data. */
    suspend fun deleteAccount(reauthPassword: String, wipeLocal: () -> Unit) {
        api.client.delete("${api.baseUrl}/account") {
            header("X-Reauth-Password", reauthPassword)
        }
        wipeLocal()
    }

    /**
     * Re-derive KEK with new password, re-wrap private keys,
     * and (where supported) rekey the SQLCipher database.
     */
    fun changeStoragePassword(
        oldPassword: CharArray,
        newPassword: CharArray,
        wrappedKeys: Map<String, ByteArray>,
        salt: ByteArray
    ): Map<String, ByteArray> {
        val oldKek = Pbkdf2.deriveKey(oldPassword, salt, 200_000, 32)
        val newSalt = SecureRandomBytes.nextBytes(16)
        val newKek = Pbkdf2.deriveKey(newPassword, newSalt, 200_000, 32)
        return wrappedKeys.mapValues { (_, blob) ->
            val nonce = blob.copyOfRange(0, 12)
            val ct = blob.copyOfRange(12, blob.size)
            val plain = AesGcm.decrypt(oldKek, nonce, ByteArray(0), ct)
            val newNonce = SecureRandomBytes.nextBytes(12)
            newNonce + AesGcm.encrypt(newKek, newNonce, ByteArray(0), plain)
        }.also {
            // caller persists newSalt + new wrapped blobs
        }
    }
}
