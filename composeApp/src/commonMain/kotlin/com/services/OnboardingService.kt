package com.fortrx.services

import com.fortrx.Settings
import com.fortrx.crypto.KeyOps
import com.fortrx.network.AuthApi
import com.fortrx.network.KeysApi
import com.fortrx.network.Api
import com.fortrx.storage.Db
import com.fortrx.storage.Keystore
import com.fortrx.storage.SettingsStore
import com.fortrx.storage.TokenStore
import io.ktor.client.request.delete
import io.ktor.client.request.header
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
        println("OnboardingService: Generating local key material from seed (${seed.size} bytes)")
        val identity = try {
            KeyOps.generateIdentityKeypair(seed)
        } catch (e: Exception) {
            println("OnboardingService: FAILED to generate identity keypair: ${e.message}")
            throw e
        }
        println("OnboardingService: Identity keypair generated")
        
        val signedPre = KeyOps.generateSignedPrekey(identity.signingPrivate)
        println("OnboardingService: Signed prekey generated")
        
        val oneTimePrekeys = KeyOps.generateOneTimePrekeys(20)
        println("OnboardingService: One-time prekeys generated (20)")
        
        val kyberPre = runCatching { 
            KeyOps.generateKyberPrekey(identity.signingPrivate) 
        }.onFailure {
            println("OnboardingService: Kyber prekey generation failed (optional): ${it.message}")
        }.getOrNull()

        return LocalKeyMaterial(identity, signedPre, oneTimePrekeys, kyberPre)
    }

    suspend fun register(username: String, email: String, password: String): RegistrationResult {
        MessagingService.resetCaches()
        val normalizedUsername = username.trim()
        // 1. Register on server
        val regBody = AuthApi.register(normalizedUsername, email, password)
        val userId = regBody["id"]?.jsonPrimitive?.longOrNull ?: throw Exception("Registration failed: no user id")
        Settings.myId = userId
        Settings.myUsername = normalizedUsername

        // 2. Login to get token
        val token = AuthApi.login(normalizedUsername, password)
        
        // 3. Initialize DB
        Settings.storagePassword = password
        Db.open(password, userId)
        TokenStore.saveToken(token, password)
        SettingsStore.saveStoragePassword(password)
        SettingsStore.saveMyId(userId)
        SettingsStore.saveUsername(normalizedUsername)

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
        Db.clearSessions()
        Keystore.saveKeys(keysJson, password)
        SettingsStore.saveBackupCode(backupCode)
        
        return RegistrationResult(token, backupCode)
    }

    suspend fun login(username: String, password: String, backupPhrase: String? = null): String {
        MessagingService.resetCaches()
        val normalizedUsername = username.trim()
        println("OnboardingService: Logging in user $normalizedUsername")
        val token = AuthApi.login(normalizedUsername, password)
        Settings.storagePassword = password
        Api.setToken(token) // Temporarily set token to fetch 'me'
        
        val me = AuthApi.getMe()
        val userId = me["id"]?.jsonPrimitive?.longOrNull ?: 0L
        val resolvedUsername = me["username"]?.jsonPrimitive?.contentOrNull ?: normalizedUsername
        Settings.myId = userId
        Settings.myUsername = resolvedUsername
        println("OnboardingService: User ID resolved to $userId")
        
        Db.open(password, userId)
        TokenStore.saveToken(token, password)
        SettingsStore.saveStoragePassword(password)
        SettingsStore.saveMyId(userId)
        SettingsStore.saveUsername(resolvedUsername)

        Settings.storagePassword = password
        Settings.myId = userId
        Settings.myUsername = resolvedUsername

        if (backupPhrase != null) {
            println("OnboardingService: Reconstructing keys from backup phrase (FORCE INIT)")
            val seed = BackupCode.deriveSeed(backupPhrase)
            val keyMaterial = generateLocalKeyMaterial(seed)
            val keysJson = buildKeysJson(
                userId,
                keyMaterial.identity,
                keyMaterial.signedPre,
                keyMaterial.oneTimePrekeys,
                keyMaterial.kyberPre,
            )
            Db.clearSessions()
            Keystore.saveKeys(keysJson, password)
            SettingsStore.saveBackupCode(backupPhrase)
            
            // Force upload bundle so the server knows we've restored/reset our keys
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
                println("OnboardingService: Keys saved and bundle uploaded to server")
            } catch (e: Exception) {
                println("OnboardingService: WARNING: Could not upload bundle during forced login: ${e.message}")
            }
        } else {
            println("OnboardingService: No backup phrase provided, checking if keys already exist")
            val existingKeys = Keystore.loadKeys(password, userId)
            val storedBackupCode = SettingsStore.loadBackupCode()
            if (existingKeys == null) {
                println("OnboardingService: Keys missing locally; waiting for explicit restore or fresh setup")
            } else if (storedBackupCode.isNullOrBlank()) {
                println("OnboardingService: Backup phrase missing on this device, preserving existing identity and sessions")
            } else {
                println("OnboardingService: Keys already exist in keystore")
            }
        }
        
        return token
    }

    suspend fun deleteAccount(password: String) {
        val reauthToken = AuthApi.reauth(password)
        val response = Api.client.delete("${Settings.serverUrl}/account") {
            header("X-Reauth", reauthToken)
        }
        Api.raiseForStatus(response, "delete_account")
        com.fortrx.FortrxClient.logout()
    }

    /**
     * Performs a forced re-initialization of the client.
     * Wipes local state, logs in, and re-generates/re-uploads keys.
     */
    suspend fun bootstrapForce(username: String, password: String, backupPhrase: String): String {
        println("OnboardingService: STARTING BOOTSTRAP FORCE")
        com.fortrx.FortrxClient.logout()
        Db.deleteDatabase() // Reset local DB
        
        val token = login(username, password, backupPhrase)
        println("OnboardingService: BOOTSTRAP FORCE COMPLETE")
        return token
    }

    /**
     * Restores an existing account using a username, password, and backup phrase.
     * Reconstructs the identity keys from the backup phrase.
     */
    suspend fun restore(username: String, password: String, backupPhrase: String): String {
        MessagingService.resetCaches()
        val normalizedUsername = username.trim()
        val token = AuthApi.login(normalizedUsername, password)
        val me = AuthApi.getMe()
        val userId = me["id"]?.jsonPrimitive?.longOrNull ?: throw Exception("Restore failed: could not fetch user info")
        val resolvedUsername = me["username"]?.jsonPrimitive?.contentOrNull ?: normalizedUsername
        Settings.myId = userId
        Settings.myUsername = resolvedUsername

        Settings.storagePassword = password
        Db.open(password, userId)
        TokenStore.saveToken(token, password)
        SettingsStore.saveStoragePassword(password)
        SettingsStore.saveMyId(userId)
        SettingsStore.saveUsername(resolvedUsername)

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
        Db.clearSessions()
        Keystore.saveKeys(keysJson, password)
        SettingsStore.saveBackupCode(backupPhrase)

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
