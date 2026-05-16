package com.fortrx.desktop.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fortrx.desktop.ui.components.NewChatDialog
import com.fortrx.services.MessagingService
import com.fortrx.services.TimeFormats
import kotlinx.coroutines.delay
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.koin.compose.koinInject

data class ConversationPreview(
    val id: String,
    val title: String,
    val last: String,
    val time: String,
    val unread: Int = 0,
    val isSearchResult: Boolean = false,
    val resultLabel: String? = null
)

@Composable
fun ConversationList(
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val messagingService = koinInject<MessagingService>()
    var query by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf<ConversationPreview>() }
    val yellow = Color(0xFFFFC107)

    LaunchedEffect(Unit) {
        val pw = com.fortrx.Settings.storagePassword
        if (pw != null) {
            com.fortrx.storage.Db.listConversationSummariesFlow(pw).collect { summaries ->
                items.clear()
                items.addAll(summaries.map { s ->
                    val contact = com.fortrx.storage.Db.getContact(s.contactId)
                    ConversationPreview(
                        id = s.contactId.toString(),
                        title = contact?.username ?: "User ${s.contactId}",
                        last = s.lastMessagePreview ?: "",
                        time = TimeFormats.formatListDate(s.lastMessageAt),
                        unread = s.unreadCount.toInt()
                    )
                })
            }
        }
    }

    var showNewChat by remember { mutableStateOf(false) }
    var remoteMatch by remember { mutableStateOf<ConversationPreview?>(null) }
    var searchLoading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            remoteMatch = null
            return@LaunchedEffect
        }
        searchLoading = true
        delay(300)
        remoteMatch = runCatching {
            val user = messagingService.getUserByUsername(trimmed)
            val userId = user["id"]?.jsonPrimitive?.longOrNull ?: return@runCatching null
            if (items.any { it.id == userId.toString() }) return@runCatching null
            
            ConversationPreview(
                id = userId.toString(),
                title = user["username"]?.jsonPrimitive?.contentOrNull ?: trimmed,
                last = "Start a new conversation",
                time = "",
                unread = 0,
                isSearchResult = true,
                resultLabel = "Server"
            )
        }.getOrNull()
        searchLoading = false
    }

    val filtered by remember(query, remoteMatch) {
        derivedStateOf {
            val local = items.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
            if (remoteMatch != null) listOf(remoteMatch!!) + local else local
        }
    }

    Column(modifier.background(Color.White)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
                placeholder = { Text("Search", color = Color.Gray) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFFEEEEEE),
                    focusedBorderColor = yellow,
                    unfocusedContainerColor = Color(0xFFF9F9F9),
                    focusedContainerColor = Color.White
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { showNewChat = true },
                modifier = Modifier.size(48.dp).background(yellow, CircleShape)
            ) {
                Icon(Icons.Default.Add, null, tint = androidx.compose.ui.graphics.Color.Black)
            }
        }
        if (searchLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = yellow)
        }
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No conversations yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Start a new chat to see it here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { c ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (c.id == selected) Color(0xFFFFF9E6) else Color.White)
                        .clickable { onSelect(c.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = Color(0xFFEEEEEE)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                c.title.first().toString(), 
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.Black
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                            if (c.isSearchResult) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    c.resultLabel ?: "Global",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF0A84FF),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            c.last,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(c.time, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        if (c.unread > 0) {
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                color = yellow,
                                shape = CircleShape,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(c.unread.toString(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.Black)
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color(0xFFEEEEEE))
                }
            }
        }
    }

    if (showNewChat) {
        NewChatDialog(
            onDismiss = { showNewChat = false },
            onStart = { onSelect(it.toString()) }
        )
    }
}
