package com.fortrx.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.fortrx.FortrxClient
import com.fortrx.Settings
import com.fortrx.services.MessagingService
import com.fortrx.storage.Db
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ChatListScreenModel(
    private val messagingService: MessagingService,
    private val fortrxClient: FortrxClient
) : ScreenModel {
    
    val conversations: StateFlow<List<Db.ConversationOverview>> = 
        Db.listConversationOverviewsFlow(Settings.storagePassword ?: "")
            .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun logout() {
        fortrxClient.logout()
    }
}
