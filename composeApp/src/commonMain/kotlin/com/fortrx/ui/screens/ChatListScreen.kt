package com.fortrx.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.fortrx.platform.BackHandler
import com.fortrx.storage.Db

class ChatListScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<ChatListScreenModel>()
        val state by screenModel.state.collectAsState()
        
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            screenModel.effects.collect { effect ->
                when (effect) {
                    is ChatListScreenModel.Effect.ShowError -> {
                        snackbarHostState.showSnackbar(effect.message)
                    }
                }
            }
        }

        fun deactivateSearch() {
            screenModel.updateSearchQuery("")
            focusManager.clearFocus()
        }

        BackHandler(enabled = state.searchQuery.isNotEmpty()) {
            deactivateSearch()
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Fortrx",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )
                    },
                    actions = {
                        IconButton(onClick = { navigator.push(SettingsScreen()) }) {
                            Icon(
                                Icons.Default.Settings, 
                                contentDescription = "Settings", 
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(16.dp)
                    ) {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = { screenModel.updateSearchQuery(it) },
                            placeholder = { Text("Search...", color = Color.Gray) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color.Black
                            )
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SearchTypeChip(
                                selected = state.searchType == ChatListScreenModel.SearchType.MESSAGES,
                                icon = Icons.AutoMirrored.Filled.Chat,
                                label = "Messages",
                                onClick = { screenModel.updateSearchType(ChatListScreenModel.SearchType.MESSAGES) }
                            )
                            SearchTypeChip(
                                selected = state.searchType == ChatListScreenModel.SearchType.CONTACTS,
                                icon = Icons.Default.Group,
                                label = "Contacts",
                                onClick = { screenModel.updateSearchType(ChatListScreenModel.SearchType.CONTACTS) }
                            )
                            SearchTypeChip(
                                selected = state.searchType == ChatListScreenModel.SearchType.SERVER,
                                icon = Icons.Default.Language,
                                label = "Server",
                                onClick = { screenModel.updateSearchType(ChatListScreenModel.SearchType.SERVER) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (state.isSearching) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Black,
                        trackColor = Color.Transparent
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    if (state.searchType == ChatListScreenModel.SearchType.MESSAGES && state.searchQuery.isNotEmpty()) {
                        if (state.messageSearchResults.isEmpty()) {
                            item {
                                Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No messages found", color = Color.Gray)
                                }
                            }
                        }
                        items(state.messageSearchResults) { hit ->
                            MessageSearchItem(hit) {
                                navigator.push(ChatScreen(hit.contactId))
                            }
                        }
                    } else {
                        state.remoteSearchResult?.let { match ->
                            item {
                                Text(
                                    "GLOBAL SEARCH",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                                ChatListItem(match, screenModel) {
                                    navigator.push(ChatScreen(match.contactId))
                                }
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }

                        if (state.conversations.isNotEmpty() && state.remoteSearchResult != null) {
                            item {
                                Text(
                                    "CONVERSATIONS",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        items(state.filteredConversations) { row ->
                            ChatListItem(row, screenModel) {
                                navigator.push(ChatScreen(row.contactId))
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SearchTypeChip(
        selected: Boolean,
        icon: ImageVector,
        label: String,
        onClick: () -> Unit
    ) {
        val contentColor = if (selected) Color.White else Color.Gray
        val containerColor = if (selected) Color.Black else Color.Transparent

        Surface(
            onClick = onClick,
            color = containerColor,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = contentColor)
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor)
            }
        }
    }

    @Composable
    private fun MessageSearchItem(hit: Db.MessageSearchHit, onClick: () -> Unit) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text((hit.username ?: "U").take(1).uppercase(), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(hit.username ?: "User ${hit.contactId}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(hit.plaintext, style = MaterialTheme.typography.bodyMedium, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    hit.createdAt.substringAfter("T").take(5),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun ChatListItem(row: Db.ConversationOverview, screenModel: ChatListScreenModel, onClick: () -> Unit) {
        var showMenu by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val initial = (row.username ?: "U").first().uppercase()
                        Text(
                            initial,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            row.username ?: "User ${row.contactId}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (row.isOnline) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                        }
                        if (row.isPinned) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(12.dp),
                                tint = Color.Gray
                            )
                        }
                    }
                    Text(
                        row.lastMessagePreview ?: "No messages yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (row.unreadCount > 0) MaterialTheme.colorScheme.onBackground else Color.Gray,
                        fontWeight = if (row.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    row.lastMessageAt?.let { time ->
                        val displayTime = time.substringAfter("T").take(5)
                        Text(
                            displayTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (row.unreadCount > 0) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier.size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    row.unreadCount.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (row.isPinned) "Unpin Chat" else "Pin Chat") },
                    onClick = { screenModel.pinChat(row.contactId, !row.isPinned); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Silent") },
                    onClick = { /* TODO */ showMenu = false },
                    leadingIcon = { Icon(Icons.Default.NotificationsOff, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Delete Chat") },
                    onClick = { screenModel.deleteChat(row.contactId); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Delete Contact") },
                    onClick = { /* TODO */ showMenu = false },
                    leadingIcon = { Icon(Icons.Default.PersonRemove, contentDescription = null) },
                    colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
                )
            }
        }
    }
}
