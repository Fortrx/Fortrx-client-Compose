package com.fortrx.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.fortrx.FortrxClient
import com.fortrx.Settings
import com.fortrx.services.CsvExporter
import com.fortrx.services.MessagingService
import com.fortrx.storage.Db
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatScreenModel(
    private val contactId: Long,
    private val messagingService: MessagingService,
    private val fortrxClient: FortrxClient
) : ScreenModel {

    data class State(
        val messages: List<Db.StoredMessage> = emptyList(),
        val contact: Db.StoredContact? = null,
        val isVerified: Boolean = false,
        val searchQuery: String = "",
        val selectedMessageIds: Set<Long> = emptySet(),
        val isRefreshing: Boolean = false
    )

    sealed interface Effect {
        data class ShowError(val message: String) : Effect
    }

    private val _searchQuery = MutableStateFlow("")
    private val _selectedMessageIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isRefreshing = MutableStateFlow(false)
    
    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val messagesFlow = combine(
        Db.listConversationFlow(Settings.storagePassword ?: "", contactId),
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
        _searchQuery,
        _selectedMessageIds,
        _isRefreshing
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        State(
            messages = args[0] as List<Db.StoredMessage>,
            contact = args[1] as Db.StoredContact?,
            isVerified = args[2] as Boolean,
            searchQuery = args[3] as String,
            selectedMessageIds = args[4] as Set<Long>,
            isRefreshing = args[5] as Boolean
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
        screenModelScope.launch {
            try {
                val payload = com.fortrx.messages.AttachmentPayload(
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = bytes.size,
                    dataBase64 = kotlin.io.encoding.Base64.encode(bytes)
                )
                messagingService.sendAttachment(contactId, payload)
            } catch (e: Exception) {
                _effects.send(Effect.ShowError("Failed to send attachment: ${e.message}"))
            }
        }
    }

    fun exportToCsv(): String {
        return CsvExporter.exportMessages(state.value.messages)
    }
    
    fun markAsViewed() {
        Db.markConversationViewed(contactId)
    }
}
