package com.fortrx.services

import com.fortrx.Settings
import com.fortrx.crypto.KeyOps
import com.fortrx.network.AuthApi
import com.fortrx.network.KeysApi
import com.fortrx.storage.Db
import com.fortrx.storage.Keystore
import com.fortrx.storage.TokenStore
import kotlinx.serialization.json.*

object OnboardingService {
    private val json = Json { ignoreUnknownKeys = true }

    data class RegistrationResult(val token: String, val backupCode: String)

    private data class LocalKeyMaterial(
        val identity: KeyOps.IdentityKeypair,
        val signedPre: KeyOps.SignedPrekey,
        val oneTimePrekeys: List<KeyOps.OneTimePrekey>,
        val kyberPre: KeyOps.KyberPrekey?,
    )

    private fun generateLocalKeyMaterial(seed: ByteArray): LocalKeyMaterial {
        val identity = KeyOps.generateIdentityKeypair(seed)
        val signedPre = KeyOps.generateSignedPrekey(identity.signingPrivate)
        val oneTimePrekeys = KeyOps.generateOneTimePrekeys(20)
        val kyberPre = runCatching { KeyOps.generateKyberPrekey(identity.signingPrivate) }.getOrNull()
        return LocalKeyMaterial(identity, signedPre, oneTimePrekeys, kyberPre)
    }

    suspend fun register(username: String, email: String, password: String): RegistrationResult {
        // 1. Register on server
        val regBody = AuthApi.register(username, email, password)
        val userId = regBody["id"]?.jsonPrimitive?.longOrNull ?: throw Exception("Registration failed: no user id")
        Settings.myId = userId

        // 2. Login to get token
        val token = AuthApi.login(username, password)
        
        // 3. Initialize DB
        Settings.storagePassword = password
        Db.open(password)
        TokenStore.saveToken(token, password)

        // 4. Generate keys from a fresh backup code
        val backupCode = BackupCode.generate()
        val seed = BackupCode.deriveSeed(backupCode)
        val keyMaterial = generateLocalKeyMaterial(seed)

        // 5. Upload bundle
        KeysApi.uploadKeyBundle(
            identityKey = KeyOps.encodePublicKey(keyMaterial.identity.dhPublic),
            signingPublic = KeyOps.encodePublicKey(keyMaterial.identity.signingPublic),
            signedPrekey = KeyOps.encodePublicKey(keyMaterial.signedPre.publicKey),
            signedPrekeySignature = KeyOps.encodePublicKey(keyMaterial.signedPre.signature),
            prekeyId = 1,
            oneTimePrekeys = keyMaterial.oneTimePrekeys.map { KeyOps.encodePublicKey(it.publicKey) },
            kyberPrekeyPublic = keyMaterial.kyberPre?.let { KeyOps.encodePublicKey(it.publicKey) },
            kyberPrekeySignature = keyMaterial.kyberPre?.let { KeyOps.encodePublicKey(it.signature) }
        )

        // 6. Save keys locally
        val keysJson = buildKeysJson(
            userId,
            keyMaterial.identity,
            keyMaterial.signedPre,
            keyMaterial.oneTimePrekeys,
            keyMaterial.kyberPre,
        )
        Keystore.saveKeys(keysJson, password)
        
        return RegistrationResult(token, backupCode)
    }

    suspend fun login(username: String, password: String): String {
        val token = AuthApi.login(username, password)
        Settings.storagePassword = password
        Db.open(password)
        TokenStore.saveToken(token, password)
        
        try {
            val me = AuthApi.getMe()
            Settings.myId = me["id"]?.jsonPrimitive?.longOrNull
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return token
    }

    /**
     * Restores an existing account using a username, password, and backup phrase.
     * Reconstructs the identity keys from the backup phrase.
     */
    suspend fun restore(username: String, password: String, backupPhrase: String): String {
        val token = AuthApi.login(username, password)
        val me = AuthApi.getMe()
        val userId = me["id"]?.jsonPrimitive?.longOrNull ?: throw Exception("Restore failed: could not fetch user info")
        Settings.myId = userId

        Settings.storagePassword = password
        Db.open(password)
        TokenStore.saveToken(token, password)

        // Derive keys from backup phrase
        val seed = BackupCode.deriveSeed(backupPhrase)
        val keyMaterial = generateLocalKeyMaterial(seed)

        val keysJson = buildKeysJson(
            userId,
            keyMaterial.identity,
            keyMaterial.signedPre,
            keyMaterial.oneTimePrekeys,
            keyMaterial.kyberPre,
        )
        Keystore.saveKeys(keysJson, password)

        // Re-upload the complete bundle so a restored client can receive new sessions immediately.
        try {
            KeysApi.uploadKeyBundle(
                identityKey = KeyOps.encodePublicKey(keyMaterial.identity.dhPublic),
                signingPublic = KeyOps.encodePublicKey(keyMaterial.identity.signingPublic),
                signedPrekey = KeyOps.encodePublicKey(keyMaterial.signedPre.publicKey),
                signedPrekeySignature = KeyOps.encodePublicKey(keyMaterial.signedPre.signature),
                prekeyId = 1,
                oneTimePrekeys = keyMaterial.oneTimePrekeys.map { KeyOps.encodePublicKey(it.publicKey) },
                kyberPrekeyPublic = keyMaterial.kyberPre?.let { KeyOps.encodePublicKey(it.publicKey) },
                kyberPrekeySignature = keyMaterial.kyberPre?.let { KeyOps.encodePublicKey(it.signature) }
            )
        } catch (e: Throwable) { /* ignore upload errors during restore if already exist */ }

        return token
    }

    private fun buildKeysJson(
        userId: Long,
        identity: KeyOps.IdentityKeypair,
        signedPre: KeyOps.SignedPrekey,
        otks: List<KeyOps.OneTimePrekey>,
        kyberPre: KeyOps.KyberPrekey?
    ) = buildJsonObject {
        put("user_id", userId)
        put("dh_private", KeyOps.encodePublicKey(identity.dhPrivate))
        put("dh_public", KeyOps.encodePublicKey(identity.dhPublic))
        put("signing_private", KeyOps.encodePublicKey(identity.signingPrivate))
        put("signing_public", KeyOps.encodePublicKey(identity.signingPublic))
        put("signed_prekey_private", KeyOps.encodePublicKey(signedPre.privateKey))
        put("signed_prekey_public", KeyOps.encodePublicKey(signedPre.publicKey))
        put("signed_prekey_signature", KeyOps.encodePublicKey(signedPre.signature))
        put("prekey_id", 1)
        put("signed_pre_private", KeyOps.encodePublicKey(signedPre.privateKey))
        put("signed_pre_public", KeyOps.encodePublicKey(signedPre.publicKey))
        put("signed_pre_id", 1)
        put("one_time_prekeys", buildJsonArray {
            otks.forEach { otk ->
                add(
                    buildJsonObject {
                        put("private", KeyOps.encodePublicKey(otk.privateKey))
                        put("public", KeyOps.encodePublicKey(otk.publicKey))
                    }
                )
            }
        })
        put("otks", buildJsonObject {
            otks.forEach { otk ->
                put(KeyOps.encodePublicKey(otk.publicKey), KeyOps.encodePublicKey(otk.privateKey))
            }
        })
        kyberPre?.let {
            put("kyber_prekey_public", KeyOps.encodePublicKey(it.publicKey))
            put("kyber_prekey_private", KeyOps.encodePublicKey(it.privateKey))
            put("kyber_prekey_sig", KeyOps.encodePublicKey(it.signature))
            put("kyber_pre_private", KeyOps.encodePublicKey(it.privateKey))
            put("kyber_pre_public", KeyOps.encodePublicKey(it.publicKey))
            put("kyber_pre_signature", KeyOps.encodePublicKey(it.signature))
        }
    }
}
