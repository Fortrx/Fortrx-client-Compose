package com.fortrx.services

import com.fortrx.crypto.KeyOps
import com.fortrx.crypto.Pqxdh
import com.fortrx.crypto.Ratchet
import com.fortrx.crypto.RatchetState
import com.fortrx.crypto.SealedSender
import com.fortrx.crypto.X3dh
import com.fortrx.services.ErrorService
import com.fortrx.messages.AttachmentPayload
import com.fortrx.messages.ChatPayload
import com.fortrx.messages.ChatPayloadCodec
import com.fortrx.Settings
import com.fortrx.network.AuthApi
import com.fortrx.network.AttachmentApi
import com.fortrx.network.KeysApi
import com.fortrx.network.MessagesApi
import com.fortrx.network.PresenceApi
import com.fortrx.platform.debugLog
import com.fortrx.platform.PlatformClock
import com.fortrx.storage.Db
import com.fortrx.storage.Keystore
import com.fortrx.storage.PlatformFileStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
class MessagingService(private val errorService: ErrorService) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessionCache = mutableMapOf<Long, RatchetState>()
    private val sendMutexes = mutableMapOf<Long, Mutex>()
    private val sendMutexMapLock = Mutex()
    private val activeDownloads = mutableMapOf<Long, Deferred<Unit>>()
    private val activeDownloadsLock = Mutex()
    private var cachedKeys: JsonObject? = null
    
    private val _downloadProgress = MutableStateFlow<Map<Long, TransferProgress>>(emptyMap())
    val downloadProgress = _downloadProgress.asStateFlow()

    fun reportUploadProgress(messageId: Long, uploaded: Long, total: Long) {
        _downloadProgress.update {
            it + (messageId to TransferProgress(
                phase = TransferPhase.UPLOADING,
                transferredBytes = uploaded,
                totalBytes = total,
                message = "Uploading",
            ))
        }
    }

    private fun updateTransferProgress(
        messageId: Long,
        phase: TransferPhase,
        transferredBytes: Long = 0L,
        totalBytes: Long? = null,
        message: String? = null,
        failureMessage: String? = null,
    ) {
        _downloadProgress.update {
            it + (messageId to TransferProgress(
                phase = phase,
                transferredBytes = transferredBytes,
                totalBytes = totalBytes,
                message = message,
                failureMessage = failureMessage,
            ))
        }
    }

    fun clearTransferProgress(messageId: Long) {
        serviceScope.launch {
            delay(1200)
            _downloadProgress.update { current -> current - messageId }
        }
    }

    private fun identityVersionKey(contactId: Long) = "identity_version:$contactId"
    private fun trustWarningKey(contactId: Long) = "trust_warning:$contactId"

    private suspend fun getSendMutex(recipientId: Long): Mutex = sendMutexMapLock.withLock {
        sendMutexes.getOrPut(recipientId) { Mutex() }
    }

    fun resetCaches() {
        sessionCache.clear()
        cachedKeys = null
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.bytesOrNull(key: String): ByteArray? =
        stringOrNull(key)?.takeIf { it.isNotEmpty() }?.let(Base64::decode)

    private fun JsonObject.requireBytes(key: String): ByteArray =
        bytesOrNull(key) ?: error("Missing $key")

    private fun JsonObject.oneTimePrekeys(): List<JsonObject> {
        val otksArr = (this["one_time_prekeys"] ?: this["otks"]) ?: return emptyList()
        
        // Handle array format
        if (otksArr is JsonArray) {
            return otksArr.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
        }
        
        // Handle map format (public -> private)
        if (otksArr is JsonObject) {
            return otksArr.entries.map { (pub, priv) ->
                buildJsonObject {
                    put("public", pub)
                    put("private", priv.jsonPrimitive.content)
                }
            }
        }
        
        return emptyList()
    }

    private fun findOneTimePrekeyPrivate(keys: JsonObject, otpkPublicB64: String?): ByteArray? {
        if (otpkPublicB64.isNullOrEmpty()) return null
        
        // 1. Try the 'otks' map format (public -> private)
        keys["otks"]?.jsonObject?.get(otpkPublicB64)?.jsonPrimitive?.contentOrNull?.let { 
            return Base64.decode(it) 
        }
        
        // 2. Fallback to searching the list format if it exists
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
     * Send a text message using settings-cached credentials.
     */
    suspend fun sendText(recipientId: Long, plaintext: String, ttlSeconds: Long? = null, localMessageId: Long? = null, attachmentId: String? = null): JsonObject {
        val pw = com.fortrx.Settings.storagePassword ?: error("MessagingService: No storage password cached in Settings")
        val myId = com.fortrx.Settings.myId ?: error("MessagingService: No user id cached in Settings")
        debugLog("Sending encrypted message.")
        return try {
            sendText(pw, myId, recipientId, plaintext, ttlSeconds, localMessageId, attachmentId)
        } catch (e: Exception) {
            debugLog("Message send failed.", e)
            errorService.reportError("Failed to send message: ${e.message}")
            throw e
        }
    }

    suspend fun sendAttachment(
        recipientId: Long,
        attachment: AttachmentPayload,
        ttlSeconds: Long? = null,
        localMessageId: Long? = null,
    ): JsonObject = sendText(
        recipientId = recipientId,
        plaintext = ChatPayloadCodec.encodeAttachment(attachment),
        ttlSeconds = ttlSeconds,
        localMessageId = localMessageId,
        attachmentId = attachment.attachmentId
    )

    fun hasIdentityWarning(contactId: Long): Boolean =
        Db.loadMetadata(trustWarningKey(contactId)) == "changed"

    fun clearIdentityWarning(contactId: Long) {
        Db.deleteMetadata(trustWarningKey(contactId))
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
        localMessageId: Long? = null,
        attachmentId: String? = null,
    ): JsonObject = getSendMutex(recipientId).withLock {
        withContext(Dispatchers.Default) {
            if (senderId == recipientId) {
            val createdAt = PlatformClock.nowIso()
            val me = runCatching { whoAmI() }.getOrNull()
            Db.upsertContact(senderId, me?.stringOrNull("username"), null)
            val previewText = ChatPayloadCodec.previewText(plaintext)
            if (localMessageId != null) {
                Db.updateMessageStatus(localMessageId, "sent")
                Db.rewriteMessagePlaintext(storagePassword, localMessageId, plaintext)
            } else {
                Db.saveOutgoingMessage(
                    password = storagePassword,
                    serverMessageId = null,
                    contactId = recipientId,
                    recipientId = recipientId,
                    messageNumber = null,
                    plaintext = plaintext,
                    createdAt = createdAt,
                    expiresAt = null,
                    status = "sent",
                    previewText = previewText,
                )
            }
            return@withContext buildJsonObject {
                put("id", JsonNull)
                put("recipient_id", recipientId)
                put("created_at", createdAt)
                put("transport", "local")
            }
        }

        // 1. Ensure session exists
        if (Db.loadSessionBlob(storagePassword, recipientId) == null) {
            initOutgoingSession(storagePassword, senderId, recipientId)
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

        // 5. Save locally as "sending" first
        val createdAtLocal = PlatformClock.nowIso()
        val previewText = ChatPayloadCodec.previewText(plaintext)
        val finalLocalId = if (localMessageId != null) {
            Db.updateMessageStatus(localMessageId, "sending")
            Db.rewriteMessagePlaintext(storagePassword, localMessageId, plaintext)
            localMessageId
        } else {
            Db.saveOutgoingMessage(
                password = storagePassword,
                serverMessageId = null,
                contactId = recipientId,
                recipientId = recipientId,
                messageNumber = newState.sendCount.toLong() - 1L,
                plaintext = plaintext,
                createdAt = createdAtLocal,
                expiresAt = null,
                status = "sending",
                previewText = previewText,
            )
        }

        // 6. POST to the server.
        val response = try {
            MessagesApi.sendMessage(
                recipientId = recipientId,
                sealedBlob = sealedB64,
                messageNumber = newState.sendCount.toLong() - 1L,
                ttlSeconds = ttlSeconds,
                attachmentId = attachmentId
            )
        } catch (e: Exception) {
            Db.updateMessageStatus(finalLocalId, "error")
            throw e
        }

        // 7. Persist updated session + update message status locally.
        Db.saveSessionBlob(storagePassword, recipientId, json.encodeToString(RatchetState.serializer(), newState))
        sessionCache[recipientId] = newState
        val serverMessageId = response["id"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: response["message_id"]?.jsonPrimitive?.content?.toLongOrNull()
        val createdAt = response["created_at"]?.jsonPrimitive?.content ?: createdAtLocal
        
        runCatching { upsertContact(recipientId) }
        Db.updateMessageStatus(finalLocalId, "sent", serverMessageId)

        response
    } }

    /**
     * Pull all pending envelopes from the server, decrypt, persist, and
     * return a normalized list for the UI/CLI: { id, sender_id, body, created_at }.
     */
    suspend fun fetchAndStoreInbox(storagePassword: String, selfUserId: Long): List<JsonObject> = withContext(Dispatchers.Default) {
        val raw = try { MessagesApi.fetchInbox() } catch (e: Exception) { return@withContext emptyList() }
        if (raw.isEmpty()) return@withContext emptyList()
        
        val out = mutableListOf<JsonObject>()
        val keys = cachedKeys ?: Keystore.loadKeys(storagePassword, selfUserId)?.also { cachedKeys = it } 
            ?: return@withContext emptyList()
            
        val ikBPriv = keys.requireBytes("dh_private")
        val ikBPub = keys.requireBytes("dh_public")

        for (env in raw) {
            val sealedB64 = env["sealed_blob"]?.jsonPrimitive?.content ?: continue
            val serverMessageId = env["id"]?.jsonPrimitive?.content?.toLongOrNull()
            val createdAt = env["created_at"]?.jsonPrimitive?.content ?: PlatformClock.nowIso()
            val expiresAt = env["expires_at"]?.jsonPrimitive?.content
            if (serverMessageId != null && Db.messageExists(serverMessageId)) {
                runCatching { MessagesApi.confirmDelivery(serverMessageId) }
                continue
            }

            val opened = try {
                SealedSender.open(ikBPriv, ikBPub, Base64.decode(sealedB64))
            } catch (_: Throwable) { continue }

            var state = sessionCache[opened.senderId] ?: run {
                val blob = Db.loadSessionBlob(storagePassword, opened.senderId)
                blob?.let { json.decodeFromString(RatchetState.serializer(), it) }
            }
            
            val headerObj = runCatching { json.parseToJsonElement(opened.headerJson).jsonObject }.getOrNull()
            val x3dh = runCatching { headerObj?.get("x3dh")?.jsonObject }.getOrNull()

            if (state == null && x3dh != null) {
                state = runCatching {
                    bootstrapReceiver(storagePassword, opened.senderId, opened.senderIkPublic, x3dh)
                }.getOrNull()
            }

            if (state == null) continue

            val ad = com.fortrx.crypto.encodeIdentityAssociatedData(opened.senderIkPublic, ikBPub)
            val decrypted = try {
                Ratchet.decrypt(state, opened.headerBytes, opened.ciphertext, ad)
            } catch (_: Throwable) {
                if (x3dh == null) {
                    sessionCache.remove(opened.senderId)
                    cachedKeys = null
                    Db.deleteSessionBlob(opened.senderId)
                    continue
                }
                val recoveredState = runCatching {
                    bootstrapReceiver(storagePassword, opened.senderId, opened.senderIkPublic, x3dh)
                }.getOrNull() ?: continue
                runCatching {
                    Ratchet.decrypt(recoveredState, opened.headerBytes, opened.ciphertext, ad)
                }.getOrNull() ?: continue
            }

            val (newState, ptBytes) = decrypted
            val plaintext = ptBytes.decodeToString()
            val previewText = ChatPayloadCodec.previewText(plaintext)
            
            getSendMutex(opened.senderId).withLock {
                sessionCache[opened.senderId] = newState
                Db.saveSessionBlob(storagePassword, opened.senderId,
                    json.encodeToString(RatchetState.serializer(), newState))
            }

            runCatching { upsertContact(opened.senderId) }
            val localId = Db.saveIncomingMessage(
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
                previewText = previewText,
            )
            
            // Auto-download media
            val payload = ChatPayloadCodec.decode(plaintext)
            if (payload is com.fortrx.messages.ChatPayload.Attachment && localId != null) {
                val attachment = payload.attachment
                if (attachment.mimeType.startsWith("image/") || attachment.mimeType.startsWith("video/")) {
                    serviceScope.launch {
                        runCatching { downloadAttachment(localId) }
                    }
                }
            }

            serverMessageId?.let { runCatching { MessagesApi.confirmDelivery(it) } }

            out += buildJsonObject {
                put("server_message_id", serverMessageId ?: -1L)
                put("sender_id", opened.senderId)
                put("body", plaintext)
                put("created_at", createdAt)
            }
        }
        out
    }

    suspend fun whoAmI(): JsonObject = AuthApi.getMe()

    suspend fun getUser(userId: Long): JsonObject = withContext(Dispatchers.Default) {
        val user = AuthApi.getUser(userId)
        Db.upsertContact(userId, user.stringOrNull("username"), null)
        user
    }

    suspend fun getUserByUsername(username: String): JsonObject = withContext(Dispatchers.Default) {
        Db.getContactByUsername(username)?.let { cached ->
            return@withContext buildJsonObject {
                put("id", cached.userId)
                cached.username?.let { put("username", it) }
            }
        }
        val user = AuthApi.getUserByUsername(username)
        val userId = user["id"]?.jsonPrimitive?.longOrNull ?: error("Missing user id")
        Db.upsertContact(userId, user.stringOrNull("username"), null)
        user
    }

    suspend fun refreshPresenceCache(storagePassword: String): List<JsonObject> = withContext(Dispatchers.Default) {
        val contacts = PresenceApi.fetchPresenceContacts()
        contacts.forEach { contact ->
            val contactId = contact["user_id"]?.jsonPrimitive?.longOrNull ?: return@forEach
            Db.upsertContact(
                contactId,
                contact.stringOrNull("username"),
                contact["is_online"]?.jsonPrimitive?.booleanOrNull,
            )
        }
        contacts
    }

    suspend fun purgeInbox(storagePassword: String, selfUserId: Long, force: Boolean): Int = withContext(Dispatchers.Default) {
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
        purged
    }

    suspend fun deleteMessage(contactId: Long, messageId: Long) {
        val password = Settings.storagePassword ?: return
        Db.getMessage(password, messageId)?.let { cleanupAttachmentAssets(it.plaintext) }
        Db.deleteMessage(password, messageId, contactId)
    }

    suspend fun deleteChat(contactId: Long) {
        val password = Settings.storagePassword ?: return
        Db.listConversationAll(password, contactId).forEach { cleanupAttachmentAssets(it.plaintext) }
        Db.deleteConversation(contactId)
    }

    fun pinMessage(messageId: Long, isPinned: Boolean) {
        Db.updateMessagePinned(messageId, isPinned)
    }

    fun pinChat(contactId: Long, isPinned: Boolean) {
        Db.updateConversationPinned(contactId, isPinned)
    }

    suspend fun forwardMessage(messageId: Long, targetContactId: Long) {
        val password = Settings.storagePassword ?: return
        val msg = Db.getMessage(password, messageId) ?: return
        val text = msg.plaintext ?: return
        sendText(targetContactId, text)
    }

    suspend fun downloadAttachment(messageId: Long) {
        val existing = activeDownloadsLock.withLock { activeDownloads[messageId] }
        if (existing != null) {
            existing.await()
            return
        }

        val deferred = activeDownloadsLock.withLock {
            activeDownloads[messageId] ?: serviceScope.async {
                performAttachmentDownload(messageId)
            }.also { activeDownloads[messageId] = it }
        }

        try {
            deferred.await()
        } finally {
            activeDownloadsLock.withLock {
                if (activeDownloads[messageId] == deferred) {
                    activeDownloads.remove(messageId)
                }
            }
        }
    }

    private suspend fun performAttachmentDownload(messageId: Long) {
        val password = Settings.storagePassword ?: return
        val message = Db.getMessage(password, messageId) ?: return
        val payload = ChatPayloadCodec.decode(message.plaintext)
        val attachment = (payload as? ChatPayload.Attachment)?.attachment ?: return
        if (attachment.attachmentId.isBlank()) return

        updateTransferProgress(
            messageId = messageId,
            phase = TransferPhase.DOWNLOADING,
            transferredBytes = 0L,
            totalBytes = attachment.sizeBytes,
            message = "Downloading",
        )
        Db.updateMessageStatus(messageId, "downloading")

        try {
            val downloaded = com.fortrx.attachments.AttachmentPlatform.downloadAttachment(
                attachmentId = attachment.attachmentId,
                fileName = attachment.fileName,
                mimeType = attachment.mimeType,
                mediaKeyBase64 = attachment.mediaKeyBase64,
                nonceBase64 = attachment.nonceBase64,
                expectedSha256 = attachment.sha256,
                expectedSizeBytes = attachment.sizeBytes,
                onProgress = { phase, transferred, total ->
                    updateTransferProgress(
                        messageId = messageId,
                        phase = phase,
                        transferredBytes = transferred,
                        totalBytes = total ?: attachment.sizeBytes,
                        message = phase.name.lowercase().replaceFirstChar(Char::titlecase),
                    )
                },
            )
            updateTransferProgress(
                messageId = messageId,
                phase = TransferPhase.SAVING,
                transferredBytes = attachment.sizeBytes,
                totalBytes = attachment.sizeBytes,
                message = "Saving",
            )
            val updatedPayload = attachment.copy(
                localFileName = downloaded.localFileName,
                downloadedAt = PlatformClock.nowIso(),
            )
            Db.rewriteMessagePlaintext(password, messageId, ChatPayloadCodec.encodeAttachment(updatedPayload))

            val isMedia = attachment.mimeType.startsWith("image/") || attachment.mimeType.startsWith("video/")
            val isDocument = attachment.mimeType.contains("pdf") ||
                attachment.mimeType.contains("document") ||
                attachment.mimeType.contains("sheet") ||
                attachment.mimeType.contains("presentation") ||
                attachment.mimeType.contains("text/")
            val isAudio = attachment.mimeType.startsWith("audio/")
            if (isMedia || isDocument || isAudio || attachment.mimeType == "application/octet-stream") {
                runCatching {
                    com.fortrx.attachments.AttachmentPlatform.saveAttachmentToDevice(
                        downloaded.localFileName,
                        attachment.fileName,
                        attachment.mimeType
                    )
                }
            }
            runCatching { AttachmentApi.ack(attachment.attachmentId) }
            com.fortrx.attachments.AttachmentPlatform.deleteRemote(attachment.attachmentId)
            updateTransferProgress(
                messageId = messageId,
                phase = TransferPhase.COMPLETED,
                transferredBytes = attachment.sizeBytes,
                totalBytes = attachment.sizeBytes,
                message = "Completed",
            )
            Db.updateMessageStatus(messageId, "delivered")
        } catch (e: Exception) {
            updateTransferProgress(
                messageId = messageId,
                phase = TransferPhase.FAILED,
                totalBytes = attachment.sizeBytes,
                message = "Failed",
                failureMessage = e.message,
            )
            Db.updateMessageStatus(messageId, "error")
            throw e
        } finally {
            clearTransferProgress(messageId)
        }
    }

    private suspend fun initOutgoingSession(password: String, senderId: Long, recipientId: Long) {
        debugLog("Initializing outgoing session.")
        val bundle = try {
            KeysApi.fetchKeyBundle(recipientId)
        } catch (e: Exception) {
            debugLog("Fetching recipient key bundle failed.", e)
            throw e
        }

        val remoteIdentityVersion = bundle["identity_version"]?.jsonPrimitive?.longOrNull
        val previousIdentityVersion = Db.loadMetadata(identityVersionKey(recipientId))?.toLongOrNull()
        if (remoteIdentityVersion != null) {
            Db.saveMetadata(identityVersionKey(recipientId), remoteIdentityVersion.toString())
            if (previousIdentityVersion != null && previousIdentityVersion != remoteIdentityVersion) {
                Db.deleteVerification(recipientId)
                Db.saveMetadata(trustWarningKey(recipientId), "changed")
                Db.deleteSessionBlob(recipientId)
                sessionCache.remove(recipientId)
            }
        }
        
        val keys = Keystore.loadKeys(password, senderId) ?: run {
            debugLog("Loading local keys for outgoing session failed.")
            error("Local keys missing")
        }

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
        sessionCache[recipientId] = state
    }

    private suspend fun cleanupAttachmentAssets(plaintext: String?) {
        val attachment = (ChatPayloadCodec.decode(plaintext) as? ChatPayload.Attachment)?.attachment ?: return
        attachment.localFileName?.let { PlatformFileStorage.deleteFile(it) }
        if (attachment.attachmentId.isNotBlank()) {
            runCatching { com.fortrx.attachments.AttachmentPlatform.deleteRemote(attachment.attachmentId) }
        }
    }

    private suspend fun bootstrapReceiver(password: String, senderId: Long, senderIk: ByteArray, x3dh: JsonObject): RatchetState {
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
