package com.fortrx.services

import com.fortrx.Settings
import com.fortrx.messages.ChatPayload
import com.fortrx.messages.ChatPayloadCodec
import com.fortrx.platform.PlatformClock
import com.fortrx.storage.Db
import com.fortrx.storage.Keystore
import com.fortrx.storage.PlatformFileStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

interface BackupArchiveService {
    suspend fun createBackupArchive(): BackupExportResult
    suspend fun restoreBackupArchive(documentName: String, archiveBytes: ByteArray, code: String): BackupRestoreResult
}

data class BackupExportResult(
    val fileName: String,
    val archiveBytes: ByteArray,
    val code: String,
)

data class BackupRestoreResult(
    val importedMessages: Int,
    val importedAttachments: Int,
)

@Serializable
private data class BackupManifest(
    val schemaVersion: Int,
    val ownerUserId: Long,
    val ownerUsername: String?,
    val createdAt: String,
    val saltBase64: String,
    val entries: List<BackupManifestEntry>,
    val counts: BackupCounts,
)

@Serializable
private data class BackupManifestEntry(
    val path: String,
    val nonceBase64: String,
    val plainSize: Long,
    val cipherSize: Long,
    val sha256: String,
)

@Serializable
private data class BackupCounts(
    val contacts: Int,
    val messages: Int,
    val attachments: Int,
)

private data class PreparedEntry(
    val manifestEntry: BackupManifestEntry,
    val encryptedBytes: ByteArray,
)

@OptIn(ExperimentalEncodingApi::class)
class DefaultBackupArchiveService(
    private val messagingService: MessagingService,
) : BackupArchiveService {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun createBackupArchive(): BackupExportResult = withContext(Dispatchers.Default) {
        val password = Settings.storagePassword ?: error("Storage is locked.")
        val ownerUserId = Settings.myId ?: error("Missing current user id.")
        val ownerUsername = Settings.myUsername
        val code = BackupCode.generate(36)
        val salt = com.fortrx.platform.SecureRandomBytes.nextBytes(16)
        val masterKey = BackupCode.deriveArchiveKey(code, salt)

        val state = Db.exportArchiveState(password)
        coroutineScope {
            state.messages.mapNotNull { message ->
                val payload = ChatPayloadCodec.decode(message.plaintext)
                val attachment = (payload as? ChatPayload.Attachment)?.attachment ?: return@mapNotNull null
                if (!attachment.localFileName.isNullOrBlank() || attachment.attachmentId.isBlank()) return@mapNotNull null
                async {
                    messagingService.downloadAttachment(message.id)
                }
            }.awaitAll()
        }

        val refreshedState = Db.exportArchiveState(password)
        val missingAttachmentNames = refreshedState.messages.mapNotNull { message ->
            val payload = ChatPayloadCodec.decode(message.plaintext)
            val attachment = (payload as? ChatPayload.Attachment)?.attachment ?: return@mapNotNull null
            if (attachment.attachmentId.isBlank() || !attachment.localFileName.isNullOrBlank()) return@mapNotNull null
            attachment.fileName
        }
        if (missingAttachmentNames.isNotEmpty()) {
            error("Backup could not include all attachments: ${missingAttachmentNames.joinToString()}")
        }

        val attachmentNames = refreshedState.messages.mapNotNull { message ->
            val payload = ChatPayloadCodec.decode(message.plaintext)
            (payload as? ChatPayload.Attachment)?.attachment?.localFileName
        }.distinct()
        val logicalPayload = json.encodeToString(refreshedState).encodeToByteArray()
        val entries = linkedMapOf<String, ByteArray>()
        val preparedEntries = mutableListOf<PreparedEntry>()

        preparedEntries += prepareEncryptedEntry(
            path = "payload/app-state.enc",
            plaintext = logicalPayload,
            masterKey = masterKey,
        )

        var attachmentCount = 0
        refreshedState.messages.forEach { message ->
            val payload = ChatPayloadCodec.decode(message.plaintext)
            val attachment = (payload as? ChatPayload.Attachment)?.attachment ?: return@forEach
            val localFileName = attachment.localFileName ?: return@forEach
            val bytes = PlatformFileStorage.readFile(localFileName) ?: return@forEach
            preparedEntries += prepareEncryptedEntry(
                path = "attachments/$localFileName.enc",
                plaintext = bytes,
                masterKey = masterKey,
            )
            attachmentCount++
        }

        preparedEntries.forEach { prepared ->
            entries[prepared.manifestEntry.path] = prepared.encryptedBytes
        }

        val manifest = BackupManifest(
            schemaVersion = 1,
            ownerUserId = ownerUserId,
            ownerUsername = ownerUsername,
            createdAt = PlatformClock.nowIso(),
            saltBase64 = Base64.encode(salt),
            entries = preparedEntries.map { it.manifestEntry },
            counts = BackupCounts(
                contacts = refreshedState.contacts.size,
                messages = refreshedState.messages.size,
                attachments = attachmentCount,
            ),
        )
        entries["manifest.json"] = json.encodeToString(manifest).encodeToByteArray()

        BackupExportResult(
            fileName = "fortrx-backup-${ownerUserId}-${manifest.createdAt.replace(':', '-')}.zip",
            archiveBytes = ArchiveZip.zip(entries),
            code = code,
        )
    }

    override suspend fun restoreBackupArchive(
        documentName: String,
        archiveBytes: ByteArray,
        code: String,
    ): BackupRestoreResult = withContext(Dispatchers.Default) {
        val password = Settings.storagePassword ?: error("Storage is locked.")
        val ownerUserId = Settings.myId ?: error("Missing current user id.")

        val archive = ArchiveZip.unzip(archiveBytes)
        val manifestBytes = archive["manifest.json"] ?: error("Backup manifest is missing.")
        val manifest = json.decodeFromString<BackupManifest>(manifestBytes.decodeToString())
        if (manifest.ownerUserId != ownerUserId) {
            error("This backup belongs to a different account.")
        }

        val masterKey = BackupCode.deriveArchiveKey(code, Base64.decode(manifest.saltBase64))
        val payloadEntry = manifest.entries.firstOrNull { it.path == "payload/app-state.enc" }
            ?: error("Backup state entry is missing.")
        val payloadBytes = archive[payloadEntry.path] ?: error("Backup state data is missing.")
        val stateBytes = decryptEntry(payloadEntry, payloadBytes, masterKey)
        val state = json.decodeFromString<Db.BackupArchiveState>(stateBytes.decodeToString())

        val attachmentBytes = buildMap {
            manifest.entries.filter { it.path.startsWith("attachments/") }.forEach { entry ->
                val encryptedBytes = archive[entry.path] ?: return@forEach
                val attachmentName = entry.path.removePrefix("attachments/").removeSuffix(".enc")
                put(attachmentName, decryptEntry(entry, encryptedBytes, masterKey))
            }
        }

        val importStats = Db.importArchiveState(
            password = password,
            state = state,
            attachmentBytes = attachmentBytes,
            importKeysIfMissing = !Db.keysExist() && Keystore.loadKeys(password, ownerUserId) == null,
        )
        BackupRestoreResult(
            importedMessages = importStats.importedMessages,
            importedAttachments = importStats.importedAttachments,
        )
    }

    private fun prepareEncryptedEntry(
        path: String,
        plaintext: ByteArray,
        masterKey: ByteArray,
    ): PreparedEntry {
        val nonce = com.fortrx.platform.SecureRandomBytes.nextBytes(12)
        val ciphertext = com.fortrx.platform.AesGcm.encrypt(masterKey, nonce, path.encodeToByteArray(), plaintext)
        val manifestEntry = BackupManifestEntry(
            path = path,
            nonceBase64 = Base64.encode(nonce),
            plainSize = plaintext.size.toLong(),
            cipherSize = ciphertext.size.toLong(),
            sha256 = sha256Hex(plaintext),
        )
        return PreparedEntry(manifestEntry, ciphertext)
    }

    private fun decryptEntry(
        entry: BackupManifestEntry,
        encryptedBytes: ByteArray,
        masterKey: ByteArray,
    ): ByteArray {
        val plaintext = com.fortrx.platform.AesGcm.decrypt(
            masterKey,
            Base64.decode(entry.nonceBase64),
            entry.path.encodeToByteArray(),
            encryptedBytes,
        )
        val sha = sha256Hex(plaintext)
        if (sha != entry.sha256) {
            error("Backup entry ${entry.path} failed validation.")
        }
        return plaintext
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
