package com.fortrx.services

import com.fortrx.crypto.KeyOps
import com.fortrx.crypto.Pqxdh
import com.fortrx.crypto.Ratchet
import com.fortrx.crypto.RatchetState
import com.fortrx.crypto.SealedSender
import com.fortrx.crypto.X3dh
import com.fortrx.network.AuthApi
import com.fortrx.network.KeysApi
import com.fortrx.network.MessagesApi
import com.fortrx.network.PresenceApi
import com.fortrx.storage.Db
import com.fortrx.storage.Keystore
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Port of `client/services/messaging.py`.
 *
 * Holds the high-level send/receive flow that ties the ratchet, the
 * sealed-sender envelope, the messages API, and local persistence together.
 *
 * Sessions are persisted as JSON-encoded [RatchetState] under
 * [Db.saveSessionBlob] / [Db.loadSessionBlob], encrypted with the storage
 * password. The caller (UI/CLI) must hold the unlocked storage password and
 * the local user id from a previous login.
 */
@OptIn(ExperimentalEncodingApi::class)
object MessagingService {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val keyBundleCache = mutableMapOf<Long, JsonObject>()

    private fun JsonObject.stringOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.bytesOrNull(key: String): ByteArray? =
        stringOrNull(key)?.takeIf { it.isNotEmpty() }?.let(Base64::decode)

    private fun JsonObject.requireBytes(key: String): ByteArray =
        bytesOrNull(key) ?: error("Missing $key")

    private fun JsonObject.oneTimePrekeys(): List<JsonObject> =
        (this["one_time_prekeys"]?.jsonArray ?: this["otks"]?.jsonArray)
            ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            .orEmpty()

    private fun findOneTimePrekeyPrivate(keys: JsonObject, otpkPublicB64: String?): ByteArray? {
        if (otpkPublicB64.isNullOrEmpty()) return null
        for (entry in keys.oneTimePrekeys()) {
            if (entry.stringOrNull("public") == otpkPublicB64) {
                return entry.bytesOrNull("private")
            }
        }
        return null
    }

    private suspend fun upsertContact(userId: Long) {
        val user = runCatching { AuthApi.getUser(userId) }.getOrNull()
        Db.upsertContact(userId, user?.stringOrNull("username"), null)
    }

    /**
     * Send a text message. Auto-initiates a handshake if no session exists.
     */
    suspend fun sendText(
        storagePassword: String,
        senderId: Long,
        recipientId: Long,
        plaintext: String,
        ttlSeconds: Long? = null,
    ): JsonObject {
        if (senderId == recipientId) {
            val createdAt = kotlinx.datetime.Clock.System.now().toString()
            val me = runCatching { whoAmI() }.getOrNull()
            Db.upsertContact(senderId, me?.stringOrNull("username"), null)
            Db.saveOutgoingMessage(
                password = storagePassword,
                serverMessageId = null,
                contactId = recipientId,
                recipientId = recipientId,
                messageNumber = null,
                plaintext = plaintext,
                createdAt = createdAt,
                expiresAt = null,
                status = "local",
            )
            return buildJsonObject {
                put("id", JsonNull)
                put("recipient_id", recipientId)
                put("created_at", createdAt)
                put("transport", "local")
            }
        }

        // 1. Ensure session exists
        if (Db.loadSessionBlob(storagePassword, recipientId) == null) {
            initOutgoingSession(storagePassword, recipientId)
        }

        // 2. Load session.
        val stateJson = Db.loadSessionBlob(storagePassword, recipientId)
            ?: error("No ratchet session for $recipientId")
        val state = json.decodeFromString(RatchetState.serializer(), stateJson)

        val keys = Keystore.loadKeys(storagePassword) ?: error("Local keys missing")
        val ikAPriv = keys.requireBytes("dh_private")
        val ikAPub = keys.requireBytes("dh_public")
        val ikBPub = state.recipientIkPublic ?: error("Missing recipient identity key")

        // 3. Encrypt plaintext under the ratchet.
        val ad = com.fortrx.crypto.encodeIdentityAssociatedData(ikAPub, ikBPub)
        
        val headerUpdates = if (state.sendCount == 0) {
            // First message in session carries X3DH bootstrap in header
            buildJsonObject {
                put("x3dh", buildJsonObject {
                    put("ek_public", Base64.encode(state.x3dhEkPublic ?: byteArrayOf()))
                    put("ik_public", Base64.encode(state.x3dhIkPublic ?: byteArrayOf()))
                    put("otpk_used", state.x3dhOtpkUsed)
                    put("prekey_id", state.x3dhPrekeyId)
                    put("is_pqxdh", state.x3dhIsPqxdh)
                    state.x3dhOtpkPublic?.let { put("otpk_public", Base64.encode(it)) }
                    state.x3dhKyberCt?.let { put("kyber_ciphertext", Base64.encode(it)) }
                })
            }
        } else null

        val (newState, header, ciphertext) = Ratchet.encrypt(state, plaintext.encodeToByteArray(), ad, headerUpdates)

        // 4. Wrap in a sealed-sender envelope.
        val envelope = SealedSender.seal(
            senderId = senderId,
            senderIkPrivate = ikAPriv,
            senderIkPublic = ikAPub,
            recipientIkPublic = ikBPub,
            ciphertext = ciphertext,
            headerJson = header.decodeToString()
        )
        val sealedB64 = Base64.encode(envelope.blob)

        // 5. POST to the server.
        val response = MessagesApi.sendMessage(
            recipientId = recipientId,
            sealedBlob = sealedB64,
            messageNumber = newState.sendCount.toLong() - 1L,
            ttlSeconds = ttlSeconds,
        )

        // 6. Persist updated session + outgoing message locally.
        Db.saveSessionBlob(storagePassword, recipientId, json.encodeToString(RatchetState.serializer(), newState))
        val serverMessageId = response["id"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: response["message_id"]?.jsonPrimitive?.content?.toLongOrNull()
        val createdAt = response["created_at"]?.jsonPrimitive?.content ?: kotlinx.datetime.Clock.System.now().toString()
        val expiresAt = response["expires_at"]?.jsonPrimitive?.content
        runCatching { upsertContact(recipientId) }
        Db.saveOutgoingMessage(
            password = storagePassword,
            serverMessageId = serverMessageId,
            contactId = recipientId,
            recipientId = recipientId,
            messageNumber = newState.sendCount.toLong() - 1L,
            plaintext = plaintext,
            createdAt = createdAt,
            expiresAt = expiresAt,
            status = "sent",
        )
        return response
    }

    /**
     * Pull all pending envelopes from the server, decrypt, persist, and
     * return a normalized list for the UI/CLI: { id, sender_id, body, created_at }.
     */
    suspend fun fetchAndStoreInbox(storagePassword: String, selfUserId: Long): List<JsonObject> {
        val raw = MessagesApi.fetchInbox()
        val out = mutableListOf<JsonObject>()
        for (env in raw) {
            val sealedB64 = env["sealed_blob"]?.jsonPrimitive?.content ?: continue
            val serverMessageId = env["id"]?.jsonPrimitive?.content?.toLongOrNull()
            val createdAt = env["created_at"]?.jsonPrimitive?.content ?: kotlinx.datetime.Clock.System.now().toString()
            val expiresAt = env["expires_at"]?.jsonPrimitive?.content
            if (serverMessageId != null && Db.messageExists(serverMessageId)) {
                runCatching { MessagesApi.confirmDelivery(serverMessageId) }
                continue
            }

            val keys = Keystore.loadKeys(storagePassword) ?: continue
            val ikBPriv = keys.requireBytes("dh_private")
            val ikBPub = keys.requireBytes("dh_public")

            val opened = try {
                SealedSender.open(ikBPriv, ikBPub, Base64.decode(sealedB64))
            } catch (_: Throwable) { continue }

            var sessionJson = Db.loadSessionBlob(storagePassword, opened.senderId)
            val headerObj = runCatching { json.parseToJsonElement(opened.headerJson).jsonObject }.getOrNull()
            val x3dh = runCatching { headerObj?.get("x3dh")?.jsonObject }.getOrNull()

            if (sessionJson == null && x3dh != null) {
                val bootstrapped = runCatching {
                    bootstrapReceiver(storagePassword, opened.senderId, opened.senderIkPublic, x3dh)
                }.getOrNull()
                if (bootstrapped != null) {
                    Db.saveSessionBlob(
                        storagePassword,
                        opened.senderId,
                        json.encodeToString(RatchetState.serializer(), bootstrapped)
                    )
                    sessionJson = Db.loadSessionBlob(storagePassword, opened.senderId)
                }
            }

            if (sessionJson == null) continue

            val state = json.decodeFromString(RatchetState.serializer(), sessionJson)
            val ad = com.fortrx.crypto.encodeIdentityAssociatedData(opened.senderIkPublic, ikBPub)
            val decrypted = try {
                Ratchet.decrypt(state, opened.headerJson.encodeToByteArray(), opened.ciphertext, ad)
            } catch (_: Throwable) {
                if (x3dh == null) continue
                val recoveredState = runCatching {
                    bootstrapReceiver(storagePassword, opened.senderId, opened.senderIkPublic, x3dh)
                }.getOrNull() ?: continue
                runCatching {
                    Ratchet.decrypt(recoveredState, opened.headerJson.encodeToByteArray(), opened.ciphertext, ad)
                }.getOrNull() ?: continue
            }

            val (newState, ptBytes) = decrypted
            val plaintext = ptBytes.decodeToString()
            Db.saveSessionBlob(storagePassword, opened.senderId,
                json.encodeToString(RatchetState.serializer(), newState))
            runCatching { upsertContact(opened.senderId) }
            Db.saveIncomingMessage(
                password = storagePassword,
                serverMessageId = serverMessageId,
                contactId = opened.senderId,
                senderId = opened.senderId,
                messageNumber = null,
                plaintext = plaintext,
                sealedBlob = null,
                createdAt = createdAt,
                expiresAt = expiresAt,
                status = "delivered",
            )
            serverMessageId?.let { runCatching { MessagesApi.confirmDelivery(it) } }

            out += buildJsonObject {
                put("server_message_id", serverMessageId ?: -1L)
                put("sender_id", opened.senderId)
                put("body", plaintext)
                put("created_at", createdAt)
            }
        }
        return out
    }

    suspend fun whoAmI(): JsonObject = AuthApi.getMe()

    suspend fun getUser(userId: Long): JsonObject {
        val user = AuthApi.getUser(userId)
        Db.upsertContact(userId, user.stringOrNull("username"), null)
        return user
    }

    suspend fun getUserByUsername(username: String): JsonObject {
        Db.getContactByUsername(username)?.let { cached ->
            return buildJsonObject {
                put("id", cached.userId)
                cached.username?.let { put("username", it) }
            }
        }
        val user = AuthApi.getUserByUsername(username)
        val userId = user["id"]?.jsonPrimitive?.longOrNull ?: error("Missing user id")
        Db.upsertContact(userId, user.stringOrNull("username"), null)
        return user
    }

    suspend fun refreshPresenceCache(storagePassword: String): List<JsonObject> {
        val contacts = PresenceApi.fetchPresenceContacts()
        contacts.forEach { contact ->
            val contactId = contact["user_id"]?.jsonPrimitive?.longOrNull ?: return@forEach
            Db.upsertContact(
                contactId,
                contact.stringOrNull("username"),
                contact["is_online"]?.jsonPrimitive?.booleanOrNull,
            )
        }
        return contacts
    }

    suspend fun purgeInbox(storagePassword: String, selfUserId: Long, force: Boolean): Int {
        val synced = fetchAndStoreInbox(storagePassword, selfUserId)
        val remaining = MessagesApi.fetchInbox()
        var purged = synced.size
        if (force) {
            for (env in remaining) {
                val id = env["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: continue
                try {
                    MessagesApi.confirmDelivery(id)
                    purged++
                } catch (_: Exception) {}
            }
        }
        return purged
    }

    private suspend fun initOutgoingSession(password: String, recipientId: Long) {
        val bundle = keyBundleCache[recipientId] ?: KeysApi.fetchKeyBundle(recipientId).also {
            keyBundleCache[recipientId] = it
        }
        val keys = Keystore.loadKeys(password) ?: error("Local keys missing")

        val ikAPriv = keys.requireBytes("dh_private")
        val ikAPub = keys.requireBytes("dh_public")
        val ikBPub = bundle.requireBytes("identity_key")
        val signingPub = bundle.requireBytes("signing_public")
        val spkBPub = bundle.requireBytes("signed_prekey")
        val spkSig = bundle.requireBytes("signed_prekey_signature")
        val opkBPub = bundle.bytesOrNull("one_time_prekey")
        val kyberPub = bundle.bytesOrNull("kyber_prekey_public")
        val kyberSig = bundle.bytesOrNull("kyber_prekey_signature")

        check(KeyOps.verifySignedPrekey(signingPub, spkBPub, spkSig)) {
            "Signed prekey signature invalid"
        }

        val (sharedSecret, ekPublic, kyberCt) = if (kyberPub != null) {
            check(kyberSig != null && KeyOps.verifyKyberPrekey(signingPub, kyberPub, kyberSig)) {
                "Kyber signature invalid"
            }
            val res = Pqxdh.sender(ikAPriv, ikBPub, spkBPub, opkBPub, kyberPub)
            Triple(res.sharedSecret, res.ekPublic, res.kemCiphertext)
        } else {
            val res = X3dh.sender(ikAPriv, ikBPub, spkBPub, opkBPub)
            Triple(res.sharedSecret, res.ekPublic, null)
        }

        val state = Ratchet.initSender(sharedSecret, spkBPub)
        state.x3dhEkPublic = ekPublic
        state.x3dhIkPublic = ikAPub
        state.x3dhOtpkUsed = opkBPub != null
        state.x3dhOtpkPublic = opkBPub
        state.x3dhPrekeyId = bundle["prekey_id"]?.jsonPrimitive?.content?.toInt() ?: 1
        state.x3dhIsPqxdh = kyberPub != null
        state.x3dhKyberCt = kyberCt
        state.recipientIkPublic = ikBPub

        Db.saveSessionBlob(password, recipientId, json.encodeToString(RatchetState.serializer(), state))
    }

    private fun bootstrapReceiver(password: String, senderId: Long, senderIk: ByteArray, x3dh: JsonObject): RatchetState {
        val keys = Keystore.loadKeys(password) ?: error("Local keys missing")
        val ikBPriv = keys.requireBytes("dh_private")
        val spkBPriv = keys.bytesOrNull("signed_prekey_private") ?: keys.requireBytes("signed_pre_private")
        val spkBPub = keys.bytesOrNull("signed_prekey_public") ?: keys.requireBytes("signed_pre_public")

        val ikAPub = senderIk
        val ekAPub = x3dh.requireBytes("ek_public")
        val isPq = x3dh["is_pqxdh"]?.jsonPrimitive?.booleanOrNull == true

        val otpkBPriv = if (x3dh["otpk_used"]?.jsonPrimitive?.booleanOrNull == true) {
            findOneTimePrekeyPrivate(keys, x3dh.stringOrNull("otpk_public"))
        } else null

        val sharedSecret = if (isPq) {
            val kyberPriv = keys.bytesOrNull("kyber_prekey_private") ?: keys.requireBytes("kyber_pre_private")
            val ct = x3dh.requireBytes("kyber_ciphertext")
            Pqxdh.receiver(ikBPriv, spkBPriv, ikAPub, ekAPub, otpkBPriv, kyberPriv, ct)
        } else {
            X3dh.receiver(ikBPriv, spkBPriv, ikAPub, ekAPub, otpkBPriv)
        }

        val state = Ratchet.initReceiver(sharedSecret, spkBPriv, spkBPub)
        state.recipientIkPublic = ikAPub
        return state
    }
}
