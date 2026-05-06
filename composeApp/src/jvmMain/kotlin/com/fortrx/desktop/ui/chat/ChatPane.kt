package com.fortrx.desktop.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fortrx.desktop.ui.components.SafetyNumberDialog
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

data class Bubble(val text: String, val mine: Boolean, val time: String)

@Composable
fun ChatPane(conversationId: String?, modifier: Modifier = Modifier) {
    if (conversationId == null) {
        Box(modifier.background(Color.White), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Fortrx", style = MaterialTheme.typography.displaySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                Text("Select a conversation to start chatting", color = Color.Gray)
            }
        }
        return
    }
    var draft by remember { mutableStateOf("") }
    var showVerify by remember { mutableStateOf(false) }
    val messages = remember(conversationId) { mutableStateListOf<Bubble>() }
    var contactName by remember(conversationId) { mutableStateOf<String?>(null) }
    var onlineState by remember(conversationId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val yellow = Color(0xFFFFC107)
    val lightGray = Color(0xFFF2F2F2)

    LaunchedEffect(conversationId) {
        val cid = conversationId?.toLongOrNull() ?: return@LaunchedEffect
        val pw = com.fortrx.Settings.storagePassword ?: return@LaunchedEffect
        while (true) {
            val contact = com.fortrx.storage.Db.getContact(cid)
            contactName = contact?.username
            onlineState = contact?.isOnline == true
            val stored = com.fortrx.storage.Db.listConversation(pw, cid)
            messages.clear()
            messages.addAll(stored.reversed().map { m ->
                Bubble(m.plaintext ?: "", m.direction == "outgoing", m.createdAt.takeLast(8).take(5))
            })
            kotlinx.coroutines.delay(2000)
        }
    }

    Column(modifier.background(Color.White)) {
        // ... (Header remains the same)
        // Header
        Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = lightGray
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            (contactName ?: conversationId).take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        contactName ?: "User $conversationId",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    )
                    Text(
                        if (onlineState) "Online" else "Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (onlineState) Color(0xFF4CAF50) else Color.Gray
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {}) { Icon(Icons.Default.Videocam, null, tint = Color.Gray) }
                IconButton(onClick = {}) { Icon(Icons.Default.Call, null, tint = Color.Gray) }
                IconButton(onClick = { showVerify = true }) { Icon(Icons.Default.VerifiedUser, null, tint = Color.Gray) }
            }
            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
        }

        // Messages
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp)) {
            item { Spacer(Modifier.height(16.dp)) }
            items(messages) { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
                    Surface(
                        color = if (m.mine) yellow else lightGray,
                        contentColor = Color.Black,
                        shape = RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = if (m.mine) 20.dp else 4.dp,
                            bottomEnd = if (m.mine) 4.dp else 20.dp
                        ),
                        modifier = Modifier.widthIn(max = 520.dp)
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(m.text, style = MaterialTheme.typography.bodyLarge)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.End)) {
                                Text(m.time, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                if (m.mine) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        // Composer
        Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {}) { Icon(Icons.Default.AttachFile, null, tint = Color.Gray) }
                TextField(
                    value = draft, 
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f), 
                    placeholder = { Text("Type here...", color = Color.Gray) },
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = lightGray,
                        unfocusedContainerColor = lightGray,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Spacer(Modifier.width(12.dp))
                IconButton(
                    onClick = {
                        if (draft.isNotBlank() && conversationId != null) {
                            scope.launch {
                                try {
                                    val pw = com.fortrx.Settings.storagePassword!!
                                    val myId = com.fortrx.Settings.myId ?: com.fortrx.network.AuthApi.getMe()["id"]?.jsonPrimitive?.long ?: 0L
                                    com.fortrx.services.MessagingService.sendText(pw, myId, conversationId.toLong(), draft)
                                    draft = ""
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp).background(if (draft.isNotBlank()) yellow else lightGray, CircleShape)
                ) {
                    Icon(
                        if (draft.isBlank()) Icons.Default.Mic else Icons.Default.Send,
                        null, 
                        tint = if (draft.isNotBlank()) Color.Black else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    if (showVerify && conversationId != null) {
        SafetyNumberDialog(
            otherUserId = conversationId.toLong(),
            otherUsername = contactName ?: "User $conversationId",
            onDismiss = { showVerify = false }
        )
    }
}
