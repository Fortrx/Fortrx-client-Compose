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

data class ConversationPreview(val id: String, val title: String, val last: String, val time: String, val unread: Int = 0)

@Composable
fun ConversationList(
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                        time = s.lastMessageAt?.take(10) ?: "",
                        unread = s.unreadCount.toInt()
                    )
                })
            }
        }
    }

    var showNewChat by remember { mutableStateOf(false) }
    val filtered by remember(query) {
        derivedStateOf {
            items.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
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
                        Text(c.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
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
