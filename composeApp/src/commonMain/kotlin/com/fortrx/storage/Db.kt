package com.fortrx.storage

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import com.fortrx.messages.ChatPayload
import com.fortrx.messages.ChatPayloadCodec
import com.fortrx.platform.debugLog
import com.fortrx.Settings
import com.fortrx.db.FortrxDb
import com.fortrx.platform.PlatformClock
import com.fortrx.services.TimeFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

expect fun createSqlDriver(dbFilePath: String, storagePassword: String): SqlDriver
expect fun deleteDatabaseFile(dbName: String)
expect fun migrateIfNeeded(driver: SqlDriver)

private fun nowIso(): String = PlatformClock.nowIso()
private fun isDuplicateServerMessageError(t: Throwable): Boolean {
    val message = t.message?.lowercase() ?: return false
    return "messages.server_message_id" in message ||
        "server_message_id" in message && "unique" in message ||
        "sqlite_constraint_unique" in message ||
        "code 2067" in message
}

object Db {
    @Volatile private var driver: SqlDriver? = null
    @Volatile private var _database: FortrxDb? = null

    val database: FortrxDb
        get() = _database ?: throw StorageError("Database is not open. Call Db.open(password) first.")

    fun open(password: String, userId: Long? = null) {
        if (_database != null) return
        if (password.isEmpty()) throw StorageError("No storage password set")
        try {
            initStorageCrypto(password)
            val dbName = if (userId != null) "fortrx_$userId.db" else Settings.dbFilePath
            val drv = createSqlDriver(dbName, password)
            migrateIfNeeded(drv)
            driver = drv
            _database = FortrxDb(drv)
        } catch (t: Throwable) {
            throw StorageError("Could not open local storage: ${t.message}", t)
        }
    }

    fun close() {
        driver?.close()
        driver = null
        _database = null
    }

    /**
     * Deletes the local database file for the current or specified user.
     * WARNING: This wipes all messages, sessions, and contacts locally.
     */
    fun deleteDatabase(userId: Long? = null) {
        close()
        val dbName = if (userId != null) "fortrx_$userId.db" else Settings.dbFilePath
        debugLog("Deleting local database file $dbName.")
        deleteDatabaseFile(dbName)
    }

    private val q get() = database.fortrxQueries

    // tokens
    suspend fun saveSecret(password: String, name: String, value: String) = withContext(Dispatchers.Default) {
        q.upsertToken(name, encrypt(value.encodeToByteArray(), password))
    }
    suspend fun loadSecret(password: String, name: String): String? = withContext(Dispatchers.Default) {
        q.selectToken(name).executeAsOneOrNull()?.let { decrypt(it, password).decodeToString() }
    }
    fun deleteSecret(name: String) = q.deleteToken(name)
    suspend fun saveToken(password: String, token: String) = withContext(Dispatchers.Default) {
        q.upsertToken("auth", encrypt(token.encodeToByteArray(), password))
    }
    suspend fun loadToken(password: String): String? = withContext(Dispatchers.Default) {
        q.selectToken("auth").executeAsOneOrNull()?.let { decrypt(it, password).decodeToString() }
    }
    fun deleteToken() = q.deleteToken("auth")

    fun saveMetadata(key: String, value: String) = q.upsertMetadata(key, value)
    fun loadMetadata(key: String): String? = q.selectMetadata(key).executeAsOneOrNull()
    fun deleteMetadata(key: String) = q.deleteMetadata(key)

    // private keys
    suspend fun saveKeys(password: String, userId: Long, keysJson: String) = withContext(Dispatchers.Default) {
        debugLog("Saving encrypted key material.")
        q.upsertPrivateKeys(userId, encrypt(keysJson.encodeToByteArray(), password), PlatformClock.nowIso())
    }
    suspend fun loadKeys(password: String, userId: Long?): String? = withContext(Dispatchers.Default) {
        val blob = if (userId != null) q.selectPrivateKeysForUser(userId).executeAsOneOrNull()
        else q.selectAnyPrivateKeys().executeAsOneOrNull()?.payload
        
        if (blob == null) {
            debugLog("No local key material found.")
            return@withContext null
        }
        
        try {
            decrypt(blob, password).decodeToString()
        } catch (e: Exception) {
            debugLog("Decrypting local key material failed.", e)
            null
        }
    }
    fun keysExist(): Boolean = q.countPrivateKeys().executeAsOne() > 0

    // sessions
    suspend fun saveSessionBlob(password: String, otherUserId: Long, stateJson: String) = withContext(Dispatchers.Default) {
        q.upsertSession(otherUserId, encrypt(stateJson.encodeToByteArray(), password), PlatformClock.nowIso())
    }
    suspend fun loadSessionBlob(password: String, otherUserId: Long): String? = withContext(Dispatchers.Default) {
        q.selectSession(otherUserId).executeAsOneOrNull()?.let { decrypt(it, password).decodeToString() }
    }
    suspend fun loadSessionsMap(password: String): Map<Long, String> = withContext(Dispatchers.Default) {
        q.selectAllSessions().executeAsList().associate { it.contact_id to decrypt(it.payload, password).decodeToString() }
    }
    fun deleteSessionBlob(otherUserId: Long) = q.deleteSession(otherUserId)
    fun clearSessions() = q.deleteAllSessions()

    // verifications
    fun saveVerification(userId: Long, safetyNumber: String) =
        q.upsertVerification(userId, safetyNumber, PlatformClock.nowIso())
    fun deleteVerification(userId: Long) =
        q.deleteVerification(userId)
    fun loadVerifications(): Map<Long, String> =
        q.selectAllVerifications().executeAsList().associate { it.contact_id to it.safety_number }
    fun isVerified(userId: Long): Boolean =
        q.selectVerification(userId).executeAsOneOrNull() != null

    fun isVerifiedFlow(userId: Long): Flow<Boolean> {
        return q.selectVerificationFlow(userId).asFlow().mapToList(Dispatchers.Default).map { it.isNotEmpty() }
    }

    // contacts
    fun upsertContact(userId: Long, username: String?, isOnline: Boolean?) {
        val existing = getContact(userId)
        val onlineValue = when (isOnline) {
            true -> 1L
            false -> 0L
            null -> if (existing?.isOnline == true) 1L else 0L
        }
        val lastSeen = when {
            isOnline == true -> PlatformClock.nowIso()
            isOnline == false -> existing?.lastSeenAt
            else -> existing?.lastSeenAt
        }
        q.upsertContact(userId, username, onlineValue, lastSeen)
    }

    data class StoredContact(
        val userId: Long,
        val username: String?,
        val isOnline: Boolean,
        val lastSeenAt: String?,
    )

    @Serializable
    data class BackupMetadataRecord(
        val key: String,
        val value: String,
    )

    @Serializable
    data class BackupSecretRecord(
        val name: String,
        val value: String,
    )

    @Serializable
    data class BackupPrivateKeyRecord(
        val userId: Long,
        val payloadJson: String,
    )

    @Serializable
    data class BackupSessionRecord(
        val contactId: Long,
        val payloadJson: String,
    )

    @Serializable
    data class BackupVerificationRecord(
        val contactId: Long,
        val safetyNumber: String,
        val verifiedAt: String,
    )

    @Serializable
    data class BackupContactRecord(
        val userId: Long,
        val username: String?,
        val isOnline: Boolean,
        val lastSeenAt: String?,
    )

    @Serializable
    data class BackupConversationStateRecord(
        val contactId: Long,
        val lastViewedAt: String?,
        val isPinned: Boolean,
    )

    @Serializable
    data class BackupMessageRecord(
        val id: Long,
        val serverMessageId: Long?,
        val contactId: Long,
        val direction: String,
        val senderId: Long?,
        val recipientId: Long?,
        val messageNumber: Long?,
        val plaintext: String,
        val createdAt: String,
        val status: String,
        val isPinned: Boolean,
        val forwardedFromId: Long?,
    )

    @Serializable
    data class BackupArchiveState(
        val metadata: List<BackupMetadataRecord>,
        val secrets: List<BackupSecretRecord>,
        val privateKeys: List<BackupPrivateKeyRecord>,
        val sessions: List<BackupSessionRecord>,
        val verifications: List<BackupVerificationRecord>,
        val contacts: List<BackupContactRecord>,
        val conversationStates: List<BackupConversationStateRecord>,
        val messages: List<BackupMessageRecord>,
    )

    data class ImportArchiveStats(
        val importedMessages: Int,
        val importedAttachments: Int,
    )

    fun getContact(userId: Long): StoredContact? =
        q.selectContact(userId).executeAsOneOrNull()?.let { row ->
            StoredContact(
                userId = row.user_id,
                username = row.username,
                isOnline = row.is_online != 0L,
                lastSeenAt = row.last_seen_at,
            )
        }

    fun getContactByUsername(username: String): StoredContact? =
        q.selectContactByUsername(username).executeAsOneOrNull()?.let { row ->
            StoredContact(
                userId = row.user_id,
                username = row.username,
                isOnline = row.is_online != 0L,
                lastSeenAt = row.last_seen_at,
            )
        }

    // messages
    fun messageExists(serverMessageId: Long): Boolean =
        q.existsServerMessage(serverMessageId).executeAsOne()

    suspend fun saveIncomingMessage(password: String, serverMessageId: Long?, contactId: kotlin.Long, senderId: Long?,
        messageNumber: Long?, plaintext: String?, sealedBlob: ByteArray?, createdAt: String,
        expiresAt: String?, status: String, previewText: String? = plaintext): Long? = withContext(Dispatchers.Default) {
        
        val slimmedPt = ChatPayloadCodec.slimForStorage(plaintext)
        val pt = slimmedPt?.let { encrypt(it.encodeToByteArray(), password) }
        val preview = (previewText ?: plaintext)?.let { encrypt(it.encodeToByteArray(), password) }

        try {
            database.transactionWithResult {
                if (serverMessageId != null && q.existsServerMessage(serverMessageId).executeAsOne()) return@transactionWithResult null
                q.insertMessage(serverMessageId, contactId, "incoming", senderId, null, messageNumber,
                    pt, sealedBlob, createdAt, PlatformClock.nowIso(), expiresAt, status, 0, null)
                val id = q.lastInsertId().executeAsOne()
                val unread = q.countUnreadMessages(contactId).executeAsOne()
                q.upsertConversationSummary(contactId, id, createdAt, null, preview, "incoming", status, unread, 0)
                id
            }
        } catch (t: Throwable) {
            if (serverMessageId != null && isDuplicateServerMessageError(t)) return@withContext null
            throw t
        }
    }
    
    suspend fun saveOutgoingMessage(password: String, serverMessageId: Long?, contactId: Long, recipientId: Long?,
        messageNumber: Long?, plaintext: String, createdAt: String, expiresAt: String?, status: String,
        previewText: String = plaintext): Long = withContext(Dispatchers.Default) {
        
        val slimmedPt = ChatPayloadCodec.slimForStorage(plaintext)
        val pt = encrypt((slimmedPt ?: plaintext).encodeToByteArray(), password)
        val preview = encrypt(previewText.encodeToByteArray(), password)
        
        database.transactionWithResult {
            if (serverMessageId != null && q.existsServerMessage(serverMessageId).executeAsOne()) {
                q.lastInsertId().executeAsOne() // Should not happen for outgoing
            } else {
                q.insertMessage(serverMessageId, contactId, "outgoing", null, recipientId, messageNumber,
                    pt, null, createdAt, null, expiresAt, status, 0, null)
                val id = q.lastInsertId().executeAsOne()
                q.upsertConversationSummary(contactId, id, createdAt, null, preview, "outgoing", status, 0, 0)
                id
            }
        }
    }

    fun updateMessageStatus(id: Long, status: String, serverMessageId: Long? = null) {
        q.updateMessageStatus(status, serverMessageId, id)
    }

    data class ConversationSummary(
        val contactId: Long,
        val lastMessageAt: String?,
        val lastMessagePreview: String?,
        val lastDirection: String?,
        val unreadCount: Long,
        val isPinned: Boolean
    )

    suspend fun listConversationSummaries(password: String, limit: Long = 50): List<ConversationSummary> = withContext(Dispatchers.Default) {
        q.selectConversationSummaries(limit).executeAsList().map { r ->
            ConversationSummary(
                r.contact_id,
                r.last_message_at,
                r.last_message_preview?.let { decrypt(it, password).decodeToString() },
                r.last_direction,
                r.unread_count,
                r.is_pinned != 0L
            )
        }.sortedWith(compareByDescending<ConversationSummary> { it.isPinned }
            .thenByDescending { TimeFormats.sortEpochMillis(it.lastMessageAt) }
            .thenBy { it.contactId })
    }

    data class ConversationOverview(
        val contactId: Long,
        val lastMessageAt: String?,
        val lastMessagePreview: String?,
        val lastDirection: String?,
        val unreadCount: Long,
        val isPinned: Boolean,
        val username: String?,
        val isOnline: Boolean,
        val lastSeenAt: String?,
    )

    data class StoredMessage(val id: Long, val serverMessageId: Long?, val contactId: Long,
        val direction: String, val senderId: Long?, val recipientId: Long?, val messageNumber: Long?,
        val plaintext: String?, val createdAt: String, val status: String, val isPinned: Boolean,
        val forwardedFromId: Long?)

    data class MessageSearchHit(
        val messageId: Long,
        val contactId: Long,
        val username: String?,
        val plaintext: String,
        val createdAt: String,
    )

    suspend fun listConversation(password: String, contactId: Long, limit: Long = 100, before: String? = null) = withContext(Dispatchers.Default) {
        q.selectConversation(contactId, before, limit).executeAsList().map { r ->
            StoredMessage(r.id, r.server_message_id, r.contact_id, r.direction, r.sender_id,
                r.recipient_id, r.message_number,
                r.plaintext?.let { decrypt(it, password).decodeToString() },
                r.created_at, r.status, r.is_pinned != 0L, r.forwarded_from_id)
        }.sortedWith(compareByDescending<StoredMessage> { it.isPinned }
            .thenByDescending { TimeFormats.sortEpochMillis(it.createdAt) }
            .thenByDescending { it.id })
    }

    suspend fun searchMessages(password: String, query: String, limit: Long = 20, scanLimit: Long = 400): List<MessageSearchHit> =
        withContext(Dispatchers.Default) {
            val needle = query.trim()
            if (needle.isEmpty()) return@withContext emptyList()
            q.selectMessagesForSearch(scanLimit).executeAsList().mapNotNull { row ->
                val plaintext = row.plaintext?.let { decrypt(it, password).decodeToString() } ?: return@mapNotNull null
                if (!plaintext.contains(needle, ignoreCase = true)) return@mapNotNull null
                MessageSearchHit(
                    messageId = row.id,
                    contactId = row.contact_id,
                    username = row.username,
                    plaintext = plaintext,
                    createdAt = row.created_at,
                )
            }.sortedWith(compareByDescending<MessageSearchHit> { TimeFormats.sortEpochMillis(it.createdAt) }
                .thenByDescending { it.messageId })
                .take(limit.toInt())
        }

    suspend fun searchConversationMessages(
        password: String,
        contactId: Long,
        query: String,
        limit: Long = 40,
        scanLimit: Long = 400,
    ): List<StoredMessage> = withContext(Dispatchers.Default) {
        val needle = query.trim()
        if (needle.isEmpty()) return@withContext emptyList()
        q.selectConversation(contactId, null, scanLimit).executeAsList().mapNotNull { row ->
            val plaintext = row.plaintext?.let { decrypt(it, password).decodeToString() } ?: return@mapNotNull null
            if (!plaintext.contains(needle, ignoreCase = true)) return@mapNotNull null
            StoredMessage(
                id = row.id,
                serverMessageId = row.server_message_id,
                contactId = row.contact_id,
                direction = row.direction,
                senderId = row.sender_id,
                recipientId = row.recipient_id,
                messageNumber = row.message_number,
                plaintext = plaintext,
                createdAt = row.created_at,
                status = row.status,
                isPinned = row.is_pinned != 0L,
                forwardedFromId = row.forwarded_from_id
            )
        }.sortedWith(compareByDescending<StoredMessage> { TimeFormats.sortEpochMillis(it.createdAt) }
            .thenByDescending { it.id })
            .take(limit.toInt())
            .reversed()
    }

    fun markConversationViewed(contactId: Long) = q.markConversationViewed(nowIso(), contactId)
    fun markAllConversationsViewed() = q.markAllConversationsViewed(nowIso())

    suspend fun deleteMessage(password: String, messageId: Long, contactId: Long) = withContext(Dispatchers.Default) {
        deleteMessages(password, listOf(messageId), contactId)
    }

    suspend fun getMessage(password: String, messageId: Long): StoredMessage? = withContext(Dispatchers.Default) {
        q.selectMessageById(messageId).executeAsOneOrNull()?.let { r ->
             StoredMessage(r.id, r.server_message_id, r.contact_id, r.direction, r.sender_id,
                r.recipient_id, r.message_number,
                r.plaintext?.let { decrypt(it, password).decodeToString() },
                r.created_at, r.status, r.is_pinned != 0L, r.forwarded_from_id)
        }
    }

    suspend fun rewriteMessagePlaintext(password: String, messageId: Long, plaintext: String) = withContext(Dispatchers.Default) {
        q.updateMessagePlaintext(encrypt(plaintext.encodeToByteArray(), password), messageId)
    }

    suspend fun deleteMessages(password: String, messageIds: Collection<Long>, contactId: Long) = withContext(Dispatchers.Default) {
        if (messageIds.isEmpty()) return@withContext
        database.transaction {
            messageIds.forEach(q::deleteMessageById)
            refreshConversationSummary(password, contactId)
        }
    }

    suspend fun deleteConversation(contactId: Long) = withContext(Dispatchers.Default) {
        database.transaction {
            q.deleteConversationMessages(contactId)
            q.deleteConversationSummary(contactId)
        }
    }

    suspend fun listConversationAll(password: String, contactId: Long): List<StoredMessage> = withContext(Dispatchers.Default) {
        q.selectConversationAll(contactId).executeAsList().map { r ->
            StoredMessage(
                id = r.id,
                serverMessageId = r.server_message_id,
                contactId = r.contact_id,
                direction = r.direction,
                senderId = r.sender_id,
                recipientId = r.recipient_id,
                messageNumber = r.message_number,
                plaintext = r.plaintext?.let { decrypt(it, password).decodeToString() },
                createdAt = r.created_at,
                status = r.status,
                isPinned = r.is_pinned != 0L,
                forwardedFromId = r.forwarded_from_id,
            )
        }
    }

    suspend fun deleteConversations(contactIds: Collection<Long>) = withContext(Dispatchers.Default) {
        if (contactIds.isEmpty()) return@withContext
        database.transaction {
            contactIds.forEach { contactId ->
                q.deleteConversationMessages(contactId)
                q.deleteConversationSummary(contactId)
            }
        }
    }

    fun listConversationSummariesFlow(password: String, limit: Long = 50): Flow<List<ConversationSummary>> {
        return q.selectConversationSummaries(limit).asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { r ->
                ConversationSummary(
                    r.contact_id,
                    r.last_message_at,
                    r.last_message_preview?.let { decrypt(it, password).decodeToString() },
                    r.last_direction,
                    r.unread_count,
                    r.is_pinned != 0L
                )
            }.sortedWith(compareByDescending<ConversationSummary> { it.isPinned }
                .thenByDescending { TimeFormats.sortEpochMillis(it.lastMessageAt) }
                .thenBy { it.contactId })
        }
    }

    fun listConversationOverviewsFlow(password: String, limit: Long = 50): Flow<List<ConversationOverview>> {
        return q.selectConversationOverviews(limit).asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { r ->
                ConversationOverview(
                    contactId = r.contact_id,
                    lastMessageAt = r.last_message_at,
                    lastMessagePreview = r.last_message_preview?.let { decrypt(it, password).decodeToString() },
                    lastDirection = r.last_direction,
                    unreadCount = r.unread_count,
                    isPinned = r.is_pinned != 0L,
                    username = r.username,
                    isOnline = (r.is_online ?: 0L) != 0L,
                    lastSeenAt = r.last_seen_at,
                )
            }.sortedWith(compareByDescending<ConversationOverview> { it.isPinned }
                .thenByDescending { TimeFormats.sortEpochMillis(it.lastMessageAt) }
                .thenBy { it.contactId })
        }
    }

    fun contactFlow(userId: Long): Flow<StoredContact?> {
        return q.selectContactFlow(userId).asFlow().mapToList(Dispatchers.Default).map { list ->
            list.firstOrNull()?.let { row ->
                StoredContact(
                    userId = row.user_id,
                    username = row.username,
                    isOnline = row.is_online != 0L,
                    lastSeenAt = row.last_seen_at,
                )
            }
        }
    }

    fun listConversationFlow(password: String, contactId: Long, limit: Long = 100): Flow<List<StoredMessage>> {
        return q.selectConversation(contactId, null, limit).asFlow().mapToList(Dispatchers.Default).map { list ->
            list.map { r ->
                StoredMessage(r.id, r.server_message_id, r.contact_id, r.direction, r.sender_id,
                    r.recipient_id, r.message_number,
                    r.plaintext?.let { decrypt(it, password).decodeToString() },
                    r.created_at, r.status, r.is_pinned != 0L, r.forwarded_from_id)
            }.sortedWith(compareByDescending<StoredMessage> { it.isPinned }
                .thenByDescending { TimeFormats.sortEpochMillis(it.createdAt) }
                .thenByDescending { it.id })
        }
    }

    suspend fun exportArchiveState(password: String): BackupArchiveState = withContext(Dispatchers.Default) {
        BackupArchiveState(
            metadata = q.selectAllMetadata().executeAsList().map { BackupMetadataRecord(it.key, it.value_) },
            secrets = q.selectAllTokens().executeAsList().mapNotNull { row ->
                runCatching { decrypt(row.value_, password).decodeToString() }.getOrNull()?.let { BackupSecretRecord(row.name, it) }
            },
            privateKeys = q.selectAllPrivateKeys().executeAsList().mapNotNull { row ->
                runCatching { decrypt(row.payload, password).decodeToString() }.getOrNull()?.let { BackupPrivateKeyRecord(row.user_id, it) }
            },
            sessions = q.selectAllSessions().executeAsList().mapNotNull { row ->
                runCatching { decrypt(row.payload, password).decodeToString() }.getOrNull()?.let { BackupSessionRecord(row.contact_id, it) }
            },
            verifications = q.selectAllVerifications().executeAsList().map {
                BackupVerificationRecord(it.contact_id, it.safety_number, it.verified_at)
            },
            contacts = q.selectAllContacts().executeAsList().map {
                BackupContactRecord(
                    userId = it.user_id,
                    username = it.username,
                    isOnline = it.is_online != 0L,
                    lastSeenAt = it.last_seen_at,
                )
            },
            conversationStates = q.selectAllConversationSummaries().executeAsList().map {
                BackupConversationStateRecord(
                    contactId = it.contact_id,
                    lastViewedAt = it.last_viewed_at,
                    isPinned = it.is_pinned != 0L,
                )
            },
            messages = q.selectAllMessages().executeAsList().mapNotNull { row ->
                val plaintext = row.plaintext?.let { decrypt(it, password).decodeToString() } ?: return@mapNotNull null
                BackupMessageRecord(
                    id = row.id,
                    serverMessageId = row.server_message_id,
                    contactId = row.contact_id,
                    direction = row.direction,
                    senderId = row.sender_id,
                    recipientId = row.recipient_id,
                    messageNumber = row.message_number,
                    plaintext = plaintext,
                    createdAt = row.created_at,
                    status = row.status,
                    isPinned = row.is_pinned != 0L,
                    forwardedFromId = row.forwarded_from_id,
                )
            },
        )
    }

    suspend fun importArchiveState(
        password: String,
        state: BackupArchiveState,
        attachmentBytes: Map<String, ByteArray>,
        importKeysIfMissing: Boolean,
    ): ImportArchiveStats = withContext(Dispatchers.Default) {
        val existingFingerprintsByContact = mutableMapOf<Long, MutableSet<String>>()
        val importedAttachmentNames = mutableSetOf<String>()
        var importedMessages = 0

        database.transaction {
            state.contacts.forEach { contact ->
                q.upsertContact(
                    contact.userId,
                    contact.username,
                    if (contact.isOnline) 1L else 0L,
                    contact.lastSeenAt,
                )
            }

            state.metadata.forEach { metadata ->
                q.upsertMetadata(metadata.key, metadata.value)
            }

            state.verifications.forEach { verification ->
                q.upsertVerification(verification.contactId, verification.safetyNumber, verification.verifiedAt)
            }

            if (importKeysIfMissing) {
                state.privateKeys.forEach { key ->
                    q.upsertPrivateKeys(key.userId, encrypt(key.payloadJson.encodeToByteArray(), password), PlatformClock.nowIso())
                }
                state.sessions.forEach { session ->
                    q.upsertSession(session.contactId, encrypt(session.payloadJson.encodeToByteArray(), password), PlatformClock.nowIso())
                }
            }

            state.messages.sortedWith(compareBy<BackupMessageRecord> { TimeFormats.sortEpochMillis(it.createdAt) }.thenBy { it.contactId }).forEach { message ->
                if (message.serverMessageId != null && q.existsServerMessage(message.serverMessageId).executeAsOne()) {
                    return@forEach
                }
                val fingerprint = messageFingerprint(
                    contactId = message.contactId,
                    direction = message.direction,
                    createdAt = message.createdAt,
                    plaintext = message.plaintext,
                )
                val existingFingerprints = existingFingerprintsByContact.getOrPut(message.contactId) {
                    q.selectConversationAll(message.contactId).executeAsList().mapNotNull { row ->
                        row.plaintext?.let { decrypt(it, password).decodeToString() }?.let { plaintext ->
                            messageFingerprint(row.contact_id, row.direction, row.created_at, plaintext)
                        }
                    }.toMutableSet()
                }
                if (existingFingerprints.contains(fingerprint)) {
                    return@forEach
                }

                val finalPlaintext = restoreAttachmentPayload(message.plaintext, attachmentBytes, importedAttachmentNames)
                q.insertMessage(
                    message.serverMessageId,
                    message.contactId,
                    message.direction,
                    message.senderId,
                    message.recipientId,
                    message.messageNumber,
                    encrypt(finalPlaintext.encodeToByteArray(), password),
                    null,
                    message.createdAt,
                    null,
                    null,
                    message.status,
                    if (message.isPinned) 1L else 0L,
                    message.forwardedFromId,
                )
                existingFingerprints += fingerprint
                importedMessages++
            }

            state.conversationStates.forEach { conversation ->
                if (conversation.isPinned) {
                    q.updateConversationPinned(1L, conversation.contactId)
                }
                conversation.lastViewedAt?.let { q.updateConversationViewedAt(it, conversation.contactId) }
            }
        }

        state.secrets.forEach { secret ->
            q.upsertToken(secret.name, encrypt(secret.value.encodeToByteArray(), password))
        }

        state.conversationStates.forEach { conversation ->
            refreshConversationSummary(password, conversation.contactId)
        }

        ImportArchiveStats(
            importedMessages = importedMessages,
            importedAttachments = importedAttachmentNames.size,
        )
    }

    fun updateMessagePinned(messageId: Long, isPinned: Boolean) {
        q.updateMessagePinned(if (isPinned) 1L else 0L, messageId)
    }

    fun updateConversationPinned(contactId: Long, isPinned: Boolean) {
        q.updateConversationPinned(if (isPinned) 1L else 0L, contactId)
    }

    private fun refreshConversationSummary(password: String, contactId: Long) {
        val latest = q.selectConversation(contactId, null, 1).executeAsOneOrNull()
        if (latest == null) {
            q.deleteConversationSummary(contactId)
            return
        }

        val latestPlaintext = latest.plaintext?.let { decrypt(it, password).decodeToString() }.orEmpty()
        val preview = encrypt(ChatPayloadCodec.previewText(latestPlaintext).encodeToByteArray(), password)
        val unread = q.countUnreadMessages(contactId).executeAsOne()
        q.upsertConversationSummary(
            contactId,
            latest.id,
            latest.created_at,
            null,
            preview,
            latest.direction,
            latest.status,
            unread,
            latest.is_pinned
        )
    }

    private fun messageFingerprint(contactId: Long, direction: String, createdAt: String, plaintext: String): String =
        listOf(contactId.toString(), direction, createdAt, plaintext).joinToString("|")

    private fun restoreAttachmentPayload(
        plaintext: String,
        attachmentBytes: Map<String, ByteArray>,
        importedAttachmentNames: MutableSet<String>,
    ): String {
        val payload = ChatPayloadCodec.decode(plaintext)
        val attachmentPayload = (payload as? ChatPayload.Attachment)?.attachment ?: return plaintext
        val localFileName = attachmentPayload.localFileName ?: return plaintext
        val bytes = attachmentBytes[localFileName] ?: return plaintext
        if (PlatformFileStorage.readFile(localFileName) == null) {
            PlatformFileStorage.writeNamedFile(localFileName, bytes)
            importedAttachmentNames += localFileName
        }
        return ChatPayloadCodec.encodeAttachment(attachmentPayload)
    }
}
