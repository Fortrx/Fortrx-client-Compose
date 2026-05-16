package com.fortrx.services

import com.fortrx.Settings
import com.fortrx.crypto.KeyOps
import com.fortrx.crypto.hkdfDerive
import com.fortrx.network.AuthApi
import com.fortrx.network.KeysApi
import com.fortrx.network.Api
import com.fortrx.platform.debugLog
import com.fortrx.platform.SecureRandomBytes
import com.fortrx.storage.Db
import com.fortrx.storage.Keystore
import com.fortrx.storage.SettingsStore
import com.fortrx.storage.TokenStore
import io.ktor.client.request.delete
import io.ktor.client.request.header
import kotlinx.serialization.json.*

class OnboardingService(
    private val messagingService: MessagingService,
    private val errorService: ErrorService
) {
    private val json = Json { ignoreUnknownKeys = true }

    data class OnboardingResult(val token: String)

    private data class LocalKeyMaterial(
        val identity: KeyOps.IdentityKeypair,
        val signedPre: KeyOps.SignedPrekey,
        val oneTimePrekeys: List<KeyOps.OneTimePrekey>,
        val kyberPre: KeyOps.KyberPrekey?,
    )

    private fun generateLocalKeyMaterial(seed: ByteArray): LocalKeyMaterial {
        debugLog("Generating local key material.")
        
        // Derive specific seeds for different key roles
        val identitySeed = hkdfDerive(seed, "identity-v1".encodeToByteArray(), byteArrayOf(), 32)
        val signedPreSeed = hkdfDerive(seed, "signed-prekey-v1".encodeToByteArray(), byteArrayOf(), 32)
        val otkBaseSeed = hkdfDerive(seed, "one-time-prekeys-v1".encodeToByteArray(), byteArrayOf(), 32)
        val kyberSeed = hkdfDerive(seed, "kyber-prekey-v1".encodeToByteArray(), byteArrayOf(), 32)

        val identity = try {
            KeyOps.generateIdentityKeypair(identitySeed)
        } catch (e: Exception) {
            debugLog("Identity key generation failed.", e)
            throw e
        }

        val signedPre = KeyOps.generateSignedPrekey(identity.signingPrivate, signedPreSeed)
        val oneTimePrekeys = KeyOps.generateOneTimePrekeys(20, otkBaseSeed)

        val kyberPre = runCatching { 
            KeyOps.generateKyberPrekey(identity.signingPrivate, kyberSeed) 
        }.onFailure {
            debugLog("Optional Kyber prekey generation failed.", it)
        }.getOrNull()

        return LocalKeyMaterial(identity, signedPre, oneTimePrekeys, kyberPre)
    }

    suspend fun register(username: String, email: String, password: String): OnboardingResult {
        messagingService.resetCaches()
        val normalizedUsername = username.trim()
        // 1. Register on server
        val regBody = AuthApi.register(normalizedUsername, email, password)
        val userId = regBody["id"]?.jsonPrimitive?.longOrNull ?: throw Exception("Registration failed: no user id")
        Settings.myId = userId
        Settings.myUsername = normalizedUsername

        // 2. Login to get token
        val session = AuthApi.login(normalizedUsername, password)
        
        // 3. Initialize DB
        Settings.storagePassword = password
        Db.open(password, userId)
        TokenStore.saveSession(session, password)
        SettingsStore.saveStoragePassword(password)
        SettingsStore.saveMyId(userId)
        SettingsStore.saveUsername(normalizedUsername)
        SettingsStore.saveDeviceId(session.deviceId ?: "")
        Settings.myDeviceId = session.deviceId

        // 4. Generate fresh local key material for this device.
        val seed = SecureRandomBytes.nextBytes(32)
        val keyMaterial = generateLocalKeyMaterial(seed)

        // 5. Upload bundle
        uploadKeyBundle(keyMaterial)

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
        
        return OnboardingResult(session.accessToken)
    }

    suspend fun login(username: String, password: String): OnboardingResult {
        messagingService.resetCaches()
        val normalizedUsername = username.trim()
        debugLog("Starting login flow.")
        val session = AuthApi.login(normalizedUsername, password)
        Settings.storagePassword = password
        Api.setSession(session)
        
        val me = AuthApi.getMe()
        val userId = me["id"]?.jsonPrimitive?.longOrNull ?: 0L
        val resolvedUsername = me["username"]?.jsonPrimitive?.contentOrNull ?: normalizedUsername
        Settings.myId = userId
        Settings.myUsername = resolvedUsername

        Db.open(password, userId)
        TokenStore.saveSession(session, password)
        SettingsStore.saveStoragePassword(password)
        SettingsStore.saveMyId(userId)
        SettingsStore.saveUsername(resolvedUsername)
        SettingsStore.saveDeviceId(session.deviceId ?: "")

        Settings.storagePassword = password
        Settings.myId = userId
        Settings.myUsername = resolvedUsername
        Settings.myDeviceId = session.deviceId

        val existingKeys = Keystore.loadKeys(password, userId)
        if (existingKeys == null) {
            debugLog("Local keys are missing; waiting for explicit archive restore or fresh setup.")
        } else {
            debugLog("Existing local keys detected.")
        }

        return OnboardingResult(session.accessToken)
    }

    suspend fun deleteAccount(password: String) {
        val reauthToken = AuthApi.reauth(password)
        val response = Api.client.delete("${Settings.serverUrl}/account") {
            header("X-Reauth", reauthToken)
        }
        Api.raiseForStatus(response, "delete_account")
    }

    /**
     * Performs a forced re-initialization of the client.
     * Wipes local state, logs in, and re-generates/re-uploads keys.
     */
    suspend fun bootstrapForce(username: String, password: String): OnboardingResult {
        debugLog("Starting bootstrap reset.")
        Db.deleteDatabase() // Reset local DB
        
        val result = login(username, password)
        debugLog("Bootstrap reset complete.")
        return result
    }

    suspend fun restore(username: String, password: String): OnboardingResult {
        return login(username, password)
    }

    private suspend fun uploadKeyBundle(keyMaterial: LocalKeyMaterial) {
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
        put("signed_pre_id", 1) // Alias for server-side compatibility if needed

        put("otks", buildJsonObject {
            otks.forEach { otk ->
                put(KeyOps.encodePublicKey(otk.publicKey), KeyOps.encodePublicKey(otk.privateKey))
            }
        })

        kyberPre?.let {
            put("kyber_prekey_public", KeyOps.encodePublicKey(it.publicKey))
            put("kyber_prekey_private", KeyOps.encodePublicKey(it.privateKey))
            put("kyber_prekey_sig", KeyOps.encodePublicKey(it.signature))
        }
    }
}
