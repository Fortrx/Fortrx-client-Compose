package com.fortrx.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.fortrx.FortrxClient
import com.fortrx.Settings
import com.fortrx.services.MessagingService
import com.fortrx.storage.Db
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class ChatListScreenModel(
    private val messagingService: MessagingService,
    private val fortrxClient: FortrxClient
) : ScreenModel {

    enum class SearchType { MESSAGES, CONTACTS, SERVER }

    data class State(
        val conversations: List<Db.ConversationOverview> = emptyList(),
        val filteredConversations: List<Db.ConversationOverview> = emptyList(),
        val searchQuery: String = "",
        val searchType: SearchType = SearchType.CONTACTS,
        val messageSearchResults: List<Db.MessageSearchHit> = emptyList(),
        val isSearching: Boolean = false,
        val remoteSearchResult: Db.ConversationOverview? = null,
        val selectedContactIds: Set<Long> = emptySet(),
        val error: String? = null
    )

    sealed interface Effect {
        data class ShowError(val message: String) : Effect
    }

    private val _searchQuery = MutableStateFlow("")
    private val _searchType = MutableStateFlow(SearchType.CONTACTS)
    private val _selectedContactIds = MutableStateFlow<Set<Long>>(emptySet())
    
    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val conversationsFlow = Db.listConversationOverviewsFlow(Settings.storagePassword ?: "")
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val remoteSearchFlow: Flow<Pair<Db.ConversationOverview?, Boolean>> = 
        combine(_searchQuery, _searchType) { q, t -> q to t }
            .debounce(500)
            .flatMapLatest { (query, type) ->
                val trimmed = query.trim()
                if (type != SearchType.SERVER || trimmed.length < 3) {
                    return@flatMapLatest flowOf(null to false)
                }

                val localMatch = conversationsFlow.value.any { it.username?.equals(trimmed, ignoreCase = true) == true }
                if (localMatch) return@flatMapLatest flowOf(null to false)

                flow<Pair<Db.ConversationOverview?, Boolean>> {
                    emit(null to true) // Loading
                    try {
                        val user = messagingService.getUserByUsername(trimmed)
                        val userId = user["id"]?.jsonPrimitive?.longOrNull
                        if (userId != null && !conversationsFlow.value.any { it.contactId == userId }) {
                            val match = Db.ConversationOverview(
                                contactId = userId,
                                lastMessageAt = null,
                                lastMessagePreview = "Start a new conversation",
                                lastDirection = null,
                                unreadCount = 0,
                                isPinned = false,
                                username = user["username"]?.jsonPrimitive?.contentOrNull ?: trimmed,
                                isOnline = false,
                                lastSeenAt = null
                            )
                            emit(match to false)
                        } else {
                            emit(null to false)
                        }
                    } catch (e: Exception) {
                        emit(null to false)
                    }
                }
            }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val messageSearchFlow = combine(_searchQuery, _searchType) { query, type -> query to type }
        .debounce(300)
        .flatMapLatest { (query, type) ->
            if (type == SearchType.MESSAGES && query.length >= 2) {
                flow { emit(Db.searchMessages(Settings.storagePassword ?: "", query, 50, 400)) }
            } else flowOf(emptyList())
        }

    val state: StateFlow<State> = combine(
        conversationsFlow,
        _searchQuery,
        _searchType,
        _selectedContactIds,
        remoteSearchFlow,
        messageSearchFlow
    ) { args: Array<Any> ->
        @Suppress("UNCHECKED_CAST")
        val conversations = args[0] as List<Db.ConversationOverview>
        val query = args[1] as String
        val type = args[2] as SearchType
        @Suppress("UNCHECKED_CAST")
        val selected = args[3] as Set<Long>
        @Suppress("UNCHECKED_CAST")
        val remote = args[4] as Pair<Db.ConversationOverview?, Boolean>
        @Suppress("UNCHECKED_CAST")
        val messageHits = args[5] as List<Db.MessageSearchHit>

        val filtered = if (query.isBlank() || type != SearchType.CONTACTS) conversations
        else conversations.filter { it.username?.contains(query, ignoreCase = true) == true || it.contactId.toString().contains(query) }

        State(
            conversations = conversations,
            filteredConversations = filtered,
            searchQuery = query,
            searchType = type,
            messageSearchResults = messageHits,
            isSearching = remote.second,
            remoteSearchResult = remote.first,
            selectedContactIds = selected
        )
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), State())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSearchType(type: SearchType) {
        _searchType.value = type
    }

    fun toggleContactSelection(contactId: Long) {
        _selectedContactIds.update { if (it.contains(contactId)) it - contactId else it + contactId }
    }

    fun clearSelection() {
        _selectedContactIds.value = emptySet()
    }

    fun deleteSelectedChats() {
        screenModelScope.launch {
            try {
                val ids = _selectedContactIds.value
                if (ids.isEmpty()) return@launch
                Db.deleteConversations(ids)
                clearSelection()
            } catch (e: Exception) {
                _effects.send(Effect.ShowError("Failed to delete chats: ${e.message}"))
            }
        }
    }

    fun deleteChat(contactId: Long) {
        screenModelScope.launch {
            try {
                messagingService.deleteChat(contactId)
            } catch (e: Exception) {
                _effects.send(Effect.ShowError("Failed to delete chat: ${e.message}"))
            }
        }
    }

    fun pinChat(contactId: Long, isPinned: Boolean) {
        screenModelScope.launch {
            try {
                messagingService.pinChat(contactId, isPinned)
            } catch (e: Exception) {
                _effects.send(Effect.ShowError("Failed to pin chat: ${e.message}"))
            }
        }
    }

    fun logout() {
        fortrxClient.logout()
    }
}
