package com.fortrx.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.fortrx.FortrxClient
import com.fortrx.Settings
import com.fortrx.services.MessagingService
import com.fortrx.storage.Db
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatScreenModel(
    private val contactId: Long,
    private val messagingService: MessagingService,
    private val fortrxClient: FortrxClient
) : ScreenModel {

    val messages: StateFlow<List<Db.StoredMessage>> =
        Db.listConversationFlow(Settings.storagePassword ?: "", contactId)
            .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun sendMessage(text: String) {
        screenModelScope.launch {
            try {
                messagingService.sendText(contactId, text)
            } catch (e: Exception) {
                // Error handled by messagingService reporting to ErrorService
            }
        }
    }
    
    fun markAsViewed() {
        Db.markConversationViewed(contactId)
    }
}
