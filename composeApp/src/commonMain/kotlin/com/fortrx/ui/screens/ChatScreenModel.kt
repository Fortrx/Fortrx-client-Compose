package com.fortrx.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.fortrx.FortrxClient
import com.fortrx.Settings
import com.fortrx.attachments.AttachmentPlatform
import com.fortrx.attachments.PickedAttachment
import com.fortrx.services.CsvExporter
import com.fortrx.services.MessagingService
import com.fortrx.services.TransferProgress
import com.fortrx.messages.AttachmentPayload
import com.fortrx.messages.ChatPayload
import com.fortrx.messages.ChatPayloadCodec
import com.fortrx.storage.Db
import com.fortrx.platform.PlatformClock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

class ChatScreenModel(
    private val contactId: Long,
    private val messagingService: MessagingService,
    private val fortrxClient: FortrxClient
) : ScreenModel {

    data class State(
        val messages: List<Db.StoredMessage> = emptyList(),
        val contact: Db.StoredContact? = null,
        val isVerified: Boolean = false,
        val hasIdentityWarning: Boolean = false,
        val searchQuery: String = "",
        val selectedMessageIds: Set<Long> = emptySet(),
        val isRefreshing: Boolean = false,
        val downloadProgress: Map<Long, TransferProgress> = emptyMap(),
        val hasMore: Boolean = true
    )

    data class CsvExportDocument(
        val fileName: String,
        val bytes: ByteArray,
    )

    sealed interface Effect {
        data class ShowError(val message: String) : Effect
    }

    private val _searchQuery = MutableStateFlow("")
    private val _selectedMessageIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isRefreshing = MutableStateFlow(false)
    private val _messageLimit = MutableStateFlow(50L)
    private val _hasIdentityWarning = MutableStateFlow(messagingService.hasIdentityWarning(contactId))
    
    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val messagesFlow = combine(
        _messageLimit.flatMapLatest { lim ->
            Db.listConversationFlow(Settings.storagePassword ?: "", contactId, limit = lim)
        },
        _searchQuery
    ) { list, query ->
        if (query.isBlank()) list
        else list.filter { it.plaintext?.contains(query, ignoreCase = true) == true }
    }

    private val contactFlow = Db.contactFlow(contactId)
    private val verifiedFlow = Db.isVerifiedFlow(contactId)

    val state: StateFlow<State> = combine(
        messagesFlow,
        contactFlow,
        verifiedFlow,
        _hasIdentityWarning,
        _searchQuery,
        _selectedMessageIds,
        _isRefreshing,
        messagingService.downloadProgress,
        _messageLimit
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val messages = args[0] as List<Db.StoredMessage>
        val limit = args[8] as Long
        State(
            messages = messages,
            contact = args[1] as Db.StoredContact?,
            isVerified = args[2] as Boolean,
            hasIdentityWarning = args[3] as Boolean,
            searchQuery = args[4] as String,
            selectedMessageIds = args[5] as Set<Long>,
            isRefreshing = args[6] as Boolean,
            downloadProgress = args[7] as Map<Long, TransferProgress>,
            hasMore = messages.size >= limit
        )
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), State())

    init {
        markAsViewed()
        ensureSyncRunning()
        refreshInbox()
    }

    private fun ensureSyncRunning() {
        if (!fortrxClient.isSyncRunning()) {
            val pw = Settings.storagePassword ?: return
            fortrxClient.startSyncEngine(pw)
        }
    }

    fun refreshInbox() {
        screenModelScope.launch {
            val pw = Settings.storagePassword ?: return@launch
            val myId = Settings.myId ?: return@launch
            _isRefreshing.value = true
            try {
                messagingService.fetchAndStoreInbox(pw, myId)
            } catch (e: Exception) {
                _effects.send(Effect.ShowError("Refresh failed: ${e.message}"))
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleMessageSelection(messageId: Long) {
        _selectedMessageIds.update { if (it.contains(messageId)) it - messageId else it + messageId }
    }

    fun clearSelection() {
        _selectedMessageIds.value = emptySet()
    }

    fun deleteSelectedMessages() {
        screenModelScope.launch {
            try {
                val ids = _selectedMessageIds.value
                if (ids.isEmpty()) return@launch
                val pw = Settings.storagePassword ?: return@launch
                Db.deleteMessages(pw, ids, contactId)
                clearSelection()
            } catch (e: Exception) {
                _effects.send(Effect.ShowError("Failed to delete messages: ${e.message}"))
            }
        }
    }

    fun forwardSelectedMessages(targetContactId: Long) {
        screenModelScope.launch {
            try {
                val ids = _selectedMessageIds.value
                if (ids.isEmpty()) return@launch
                ids.forEach { msgId ->
                    messagingService.forwardMessage(msgId, targetContactId)
                }
                clearSelection()
            } catch (e: Exception) {
                _effects.send(Effect.ShowError("Failed to forward messages: ${e.message}"))
            }
        }
    }

    fun deleteChat() {
        screenModelScope.launch {
            try {
                messagingService.deleteChat(contactId)
            } catch (e: Exception) {
                _effects.send(Effect.ShowError("Failed to delete chat: ${e.message}"))
            }
        }
    }

    fun pinMessage(messageId: Long, isPinned: Boolean) {
        screenModelScope.launch {
            try {
                messagingService.pinMessage(messageId, isPinned)
            } catch (e: Exception) {
                _effects.send(Effect.ShowError("Failed to pin message: ${e.message}"))
            }
        }
    }

    fun pinChat(isPinned: Boolean) {
        screenModelScope.launch {
            try {
                messagingService.pinChat(contactId, isPinned)
            } catch (e: Exception) {
                _effects.send(Effect.ShowError("Failed to pin chat: ${e.message}"))
            }
        }
    }

    fun sendMessage(text: String) {
        screenModelScope.launch {
            try {
                messagingService.sendText(contactId, text)
            } catch (e: Exception) {
                _effects.send(Effect.ShowError("Failed to send message: ${e.message}"))
            }
        }
    }

    fun sendAttachment(fileName: String, mimeType: String, bytes: ByteArray) {
        error("Deprecated attachment entrypoint")
    }

    fun sendAttachment(attachment: PickedAttachment) {
        screenModelScope.launch {
            val password = Settings.storagePassword ?: return@launch
            val initialPayload = AttachmentPayload(
                attachmentId = "",
                fileName = attachment.fileName,
                mimeType = attachment.mimeType,
                sizeBytes = attachment.sizeBytes,
                sha256 = "",
                mediaKeyBase64 = "",
                nonceBase64 = "",
                thumbnailBase64 = attachment.thumbnailBase64,
                localFileName = attachment.localFileName,
            )
            
            val localId = try {
                Db.saveOutgoingMessage(
                    password = password,
                    serverMessageId = null,
                    contactId = contactId,
                    recipientId = contactId,
                    messageNumber = null,
                    plaintext = ChatPayloadCodec.encodeAttachment(initialPayload),
                    createdAt = PlatformClock.nowIso(),
                    expiresAt = null,
                    status = "uploading",
                    previewText = ChatPayloadCodec.previewText(initialPayload)
                )
            } catch (e: Exception) {
                _effects.send(Effect.ShowError("Failed to start upload: ${e.message}"))
                return@launch
            }

            try {
                val uploaded = AttachmentPlatform.uploadAttachment(contactId, attachment) { uploaded, total ->
                    messagingService.reportUploadProgress(localId, uploaded, total)
                }
                val finalPayload = initialPayload.copy(
                    attachmentId = uploaded.attachmentId,
                    sha256 = uploaded.sha256,
                    mediaKeyBase64 = uploaded.mediaKeyBase64,
                    nonceBase64 = uploaded.nonceBase64,
                )
                messagingService.sendAttachment(contactId, finalPayload, localMessageId = localId)
                messagingService.clearTransferProgress(localId)
            } catch (e: Exception) {
                messagingService.clearTransferProgress(localId)
                Db.updateMessageStatus(localId, "error")
                _effects.send(Effect.ShowError("Failed to send attachment: ${e.message}"))
            }
        }
    }

    fun downloadAttachment(messageId: Long) {
        screenModelScope.launch {
            try {
                messagingService.downloadAttachment(messageId)
            } catch (e: Exception) {
                _effects.send(Effect.ShowError("Failed to download attachment: ${e.message}"))
            }
        }
    }

    fun openAttachment(localFileName: String, mimeType: String) {
        runCatching { AttachmentPlatform.openAttachment(localFileName, mimeType) }
            .onFailure { error -> screenModelScope.launch { _effects.send(Effect.ShowError("Failed to open attachment: ${error.message}")) } }
    }

    fun saveAttachmentToDevice(localFileName: String, fileName: String, mimeType: String) {
        val savedLocation = runCatching {
            AttachmentPlatform.saveAttachmentToDevice(localFileName, fileName, mimeType)
        }.getOrNull()
        if (savedLocation == null) {
            screenModelScope.launch { _effects.send(Effect.ShowError("Failed to save attachment to device.")) }
        }
    }

    fun shareAttachment(localFileName: String, fileName: String, mimeType: String) {
        runCatching {
            AttachmentPlatform.shareFile(localFileName, fileName, mimeType)
        }.onFailure { error ->
            screenModelScope.launch { _effects.send(Effect.ShowError("Failed to share file: ${error.message}")) }
        }
    }

    fun retryAttachmentUpload(messageId: Long) {
        screenModelScope.launch {
            val password = Settings.storagePassword ?: return@launch
            val message = Db.getMessage(password, messageId) ?: return@launch
            val payload = ChatPayloadCodec.decode(message.plaintext)
            val attachmentPayload = (payload as? ChatPayload.Attachment)?.attachment ?: return@launch
            val localFileName = attachmentPayload.localFileName ?: return@launch
            
            val attachment = PickedAttachment(
                localFileName = localFileName,
                fileName = attachmentPayload.fileName,
                mimeType = attachmentPayload.mimeType,
                sizeBytes = attachmentPayload.sizeBytes,
                thumbnailBase64 = attachmentPayload.thumbnailBase64
            )

            Db.updateMessageStatus(messageId, "uploading")

            try {
                val uploaded = AttachmentPlatform.uploadAttachment(contactId, attachment) { uploaded, total ->
                    messagingService.reportUploadProgress(messageId, uploaded, total)
                }
                val finalPayload = attachmentPayload.copy(
                    attachmentId = uploaded.attachmentId,
                    sha256 = uploaded.sha256,
                    mediaKeyBase64 = uploaded.mediaKeyBase64,
                    nonceBase64 = uploaded.nonceBase64,
                )
                messagingService.sendAttachment(contactId, finalPayload, localMessageId = messageId)
            } catch (e: Exception) {
                Db.updateMessageStatus(messageId, "error")
                _effects.send(Effect.ShowError("Failed to retry attachment upload: ${e.message}"))
            }
        }
    }

    fun acknowledgeIdentityChange() {
        messagingService.clearIdentityWarning(contactId)
        _hasIdentityWarning.value = false
    }

    fun loadMoreMessages() {
        if (state.value.hasMore && !_isRefreshing.value) {
            _messageLimit.update { it + 50 }
        }
    }

    fun exportToCsvDocument(): CsvExportDocument {
        val name = (state.value.contact?.username ?: "chat-$contactId")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val csv = CsvExporter.exportMessages(state.value.messages)
        return CsvExportDocument(
            fileName = "$name.csv",
            bytes = csv.encodeToByteArray(),
        )
    }
    
    fun markAsViewed() {
        Db.markConversationViewed(contactId)
    }
}
