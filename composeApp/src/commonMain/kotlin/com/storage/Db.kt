package com.fortrx.storage

import app.cash.sqldelight.db.SqlDriver
import com.fortrx.Settings
import com.fortrx.db.FortrxDb
import kotlinx.datetime.Clock

expect fun createSqlDriver(dbFilePath: String, storagePassword: String): SqlDriver

private fun nowIso(): String = kotlinx.datetime.Clock.System.now().toString()

object Db {
    @Volatile private var driver: SqlDriver? = null
    @Volatile private var _database: FortrxDb? = null

    val database: FortrxDb
        get() = _database ?: throw StorageError("Database is not open. Call Db.open(password) first.")

    fun open(password: String) {
        if (_database != null) return
        if (password.isEmpty()) throw StorageError("No storage password set")
        try {
            val drv = createSqlDriver(Settings.dbFilePath, password)
            FortrxDb.Schema.create(drv)
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

    private val q get() = database.fortrxQueries

    // tokens
    fun saveToken(password: String, token: String) =
        q.upsertToken("auth", encrypt(token.encodeToByteArray(), password))
    fun loadToken(password: String): String? =
        q.selectToken("auth").executeAsOneOrNull()?.let { decrypt(it, password).decodeToString() }
    fun deleteToken() = q.deleteToken("auth")

    // private keys
    fun saveKeys(password: String, userId: Long, keysJson: String) =
        q.upsertPrivateKeys(userId, encrypt(keysJson.encodeToByteArray(), password), nowIso())
    fun loadKeys(password: String, userId: Long?): String? {
        val blob = if (userId != null) q.selectPrivateKeysForUser(userId).executeAsOneOrNull()
        else q.selectAnyPrivateKeys().executeAsOneOrNull()?.payload
        return blob?.let { decrypt(it, password).decodeToString() }
    }
    fun keysExist(): Boolean = q.countPrivateKeys().executeAsOne() > 0

    // sessions
    fun saveSessionBlob(password: String, otherUserId: Long, stateJson: String) =
        q.upsertSession(otherUserId, encrypt(stateJson.encodeToByteArray(), password), nowIso())
    fun loadSessionBlob(password: String, otherUserId: Long): String? =
        q.selectSession(otherUserId).executeAsOneOrNull()?.let { decrypt(it, password).decodeToString() }
    fun loadSessionsMap(password: String): Map<Long, String> =
        q.selectAllSessions().executeAsList().associate { it.contact_id to decrypt(it.payload, password).decodeToString() }

    // verifications
    fun saveVerification(userId: Long, safetyNumber: String) =
        q.upsertVerification(userId, safetyNumber, nowIso())
    fun loadVerifications(): Map<Long, String> =
        q.selectAllVerifications().executeAsList().associate { it.contact_id to it.safety_number }
    fun isVerified(userId: Long): Boolean =
        q.selectVerification(userId).executeAsOneOrNull() != null

    // contacts
    fun upsertContact(userId: Long, username: String?, isOnline: Boolean?) {
        val existing = getContact(userId)
        val onlineValue = when (isOnline) {
            true -> 1L
            false -> 0L
            null -> if (existing?.isOnline == true) 1L else 0L
        }
        val lastSeen = when {
            isOnline == true -> nowIso()
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

    fun saveIncomingMessage(password: String, serverMessageId: Long?, contactId: kotlin.Long, senderId: Long?,
        messageNumber: Long?, plaintext: String?, sealedBlob: ByteArray?, createdAt: String,
        expiresAt: String?, status: String) {
        val pt = plaintext?.let { encrypt(it.encodeToByteArray(), password) }
        database.transaction {
            q.insertMessage(serverMessageId, contactId, "incoming", senderId, null, messageNumber,
                pt, sealedBlob, createdAt, nowIso(), expiresAt, status)
            val unread = q.countUnreadMessages(contactId).executeAsOne()
            q.upsertConversationSummary(contactId, null, createdAt, null, pt, "incoming", status, unread)
        }
    }
    fun saveOutgoingMessage(password: String, serverMessageId: Long?, contactId: Long, recipientId: Long?,
        messageNumber: Long?, plaintext: String, createdAt: String, expiresAt: String?, status: String) {
        val pt = encrypt(plaintext.encodeToByteArray(), password)
        database.transaction {
            q.insertMessage(serverMessageId, contactId, "outgoing", null, recipientId, messageNumber,
                pt, null, createdAt, null, expiresAt, status)
            q.upsertConversationSummary(contactId, null, createdAt, null, pt, "outgoing", status, 0)
        }
    }

    data class ConversationSummary(
        val contactId: Long,
        val lastMessageAt: String?,
        val lastMessagePreview: String?,
        val lastDirection: String?,
        val unreadCount: Long
    )

    fun listConversationSummaries(password: String, limit: Long = 50): List<ConversationSummary> =
        q.selectConversationSummaries(limit).executeAsList().map { r ->
            ConversationSummary(
                r.contact_id,
                r.last_message_at,
                r.last_message_preview?.let { decrypt(it, password).decodeToString() },
                r.last_direction,
                r.unread_count
            )
        }

    data class StoredMessage(val id: Long, val serverMessageId: Long?, val contactId: Long,
        val direction: String, val senderId: Long?, val recipientId: Long?, val messageNumber: Long?,
        val plaintext: String?, val createdAt: String, val status: String)

    fun listConversation(password: String, contactId: Long, limit: Long = 100, before: String? = null) =
        q.selectConversation(contactId, before, limit).executeAsList().map { r ->
            StoredMessage(r.id, r.server_message_id, r.contact_id, r.direction, r.sender_id,
                r.recipient_id, r.message_number,
                r.plaintext?.let { decrypt(it, password).decodeToString() },
                r.created_at, r.status)
        }

    fun markConversationViewed(contactId: Long) = q.markConversationViewed(nowIso(), contactId)
    fun markAllConversationsViewed() = q.markAllConversationsViewed(nowIso())
}
