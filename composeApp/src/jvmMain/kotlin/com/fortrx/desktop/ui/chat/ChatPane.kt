package com.fortrx.desktop.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.fortrx.desktop.ui.components.SafetyNumberDialog
import com.fortrx.messages.AttachmentPayload
import com.fortrx.messages.ChatPayloadCodec
import com.fortrx.services.MessagingService
import fortrxclient.composeapp.generated.resources.Res
import fortrxclient.composeapp.generated.resources.bg_chats_doodle
import org.jetbrains.compose.resources.painterResource as composePainterResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.koin.compose.koinInject
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class Bubble(val id: Long, val text: String, val mine: Boolean, val time: String)

@OptIn(ExperimentalEncodingApi::class)
@Composable
fun ChatPane(
    conversationId: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val messagingService = koinInject<MessagingService>()
    
    if (conversationId == null) {
        Box(modifier.background(Color.White), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Fortrx", style = MaterialTheme.typography.displaySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                Text("Select a conversation to start chatting", color = Color.Gray)
            }
        }
        return
    }
    var draft by rememberSaveable(conversationId) { mutableStateOf("") }
    var showVerify by remember { mutableStateOf(false) }
    var isSearchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedAttachment by remember { mutableStateOf<AttachmentPayload?>(null) }
    val messages = remember(conversationId) { mutableStateListOf<Bubble>() }
    val listState = rememberLazyListState()
    var contactName by remember(conversationId) { mutableStateOf<String?>(null) }
    var onlineState by remember(conversationId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val yellow = Color(0xFFFFC107)
    val lightGray = Color(0xFFF2F2F2)

    val displayedMessages = if (isSearchVisible && searchQuery.isNotBlank()) {
        messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
    } else {
        messages
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(conversationId) {
        val cid = conversationId.toLongOrNull() ?: return@LaunchedEffect
        val pw = com.fortrx.Settings.storagePassword ?: return@LaunchedEffect
        
        val contact = com.fortrx.storage.Db.getContact(cid)
        contactName = contact?.username
        onlineState = contact?.isOnline == true
        
        com.fortrx.storage.Db.listConversationFlow(pw, cid).collect { stored ->
            messages.clear()
            messages.addAll(stored.reversed().map { m ->
                Bubble(m.id, m.plaintext ?: "", m.direction == "outgoing", m.createdAt.takeLast(8).take(5))
            })
        }
    }

    Column(modifier.background(Color.White)) {
        Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.Gray)
                    }
                    Spacer(Modifier.width(4.dp))
                }
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
                IconButton(onClick = { isSearchVisible = !isSearchVisible }) { Icon(Icons.Default.Search, null, tint = Color.Gray) }
                IconButton(onClick = { showVerify = true }) { Icon(Icons.Default.VerifiedUser, null, tint = Color.Gray) }
            }
            if (isSearchVisible) {
                Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                        placeholder = { Text("Search messages...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            IconButton(onClick = { searchQuery = ""; isSearchVisible = false }) {
                                Icon(Icons.Default.Close, null)
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Image(
                painter = composePainterResource(Res.drawable.bg_chats_doodle),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.4f
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(displayedMessages, key = { it.id }) { m ->
                    var showMenu by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
                        Box {
                            Surface(
                                color = (if (m.mine) yellow else lightGray).copy(alpha = 0.9f),
                                contentColor = Color.Black,
                                shape = RoundedCornerShape(
                                    topStart = 20.dp,
                                    topEnd = 20.dp,
                                    bottomStart = if (m.mine) 20.dp else 4.dp,
                                    bottomEnd = if (m.mine) 4.dp else 20.dp
                                ),
                                modifier = Modifier.widthIn(max = 520.dp)
                                    .clickable { showMenu = true }
                            ) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                    val payload = ChatPayloadCodec.decode(m.text)
                                    when (payload) {
                                        is com.fortrx.messages.ChatPayload.Text -> Text(payload.text, style = MaterialTheme.typography.bodyLarge)
                                        is com.fortrx.messages.ChatPayload.Attachment -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(if (ChatPayloadCodec.isImage(payload.attachment)) Icons.Default.Image else Icons.Default.InsertDriveFile, null)
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    Text(payload.attachment.fileName, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                                    Text(ChatPayloadCodec.formatSize(payload.attachment.sizeBytes), style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.End)) {
                                        Text(m.time, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                        if (m.mine) {
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                                        }
                                    }
                                }
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Delete message") },
                                    onClick = {
                                        scope.launch {
                                            val pw = com.fortrx.Settings.storagePassword!!
                                            com.fortrx.storage.Db.deleteMessages(pw, listOf(m.id), conversationId.toLong())
                                            showMenu = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectedAttachment != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                color = Color(0xFFF7F7F7),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AttachFile, null)
                    Spacer(Modifier.width(8.dp))
                    Text(selectedAttachment!!.fileName, modifier = Modifier.weight(1f))
                    IconButton(onClick = { selectedAttachment = null }) { Icon(Icons.Default.Close, null) }
                }
            }
        }

        Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    val path = openFileDialog()
                    if (path != null) {
                        val file = File(path)
                        val bytes = file.readBytes()
                        selectedAttachment = AttachmentPayload(
                            fileName = file.name,
                            mimeType = "application/octet-stream", // Simple fallback
                            sizeBytes = bytes.size,
                            dataBase64 = Base64.encode(bytes)
                        )
                    }
                }) { Icon(Icons.Default.AttachFile, null, tint = Color.Gray) }
                TextField(
                    value = draft, 
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f), 
                    placeholder = { Text("Type here...", color = Color.Gray) },
                    minLines = 1,
                    maxLines = 4,
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
                        if ((draft.isNotBlank() || selectedAttachment != null) && conversationId != null) {
                            scope.launch {
                                try {
                                    val cid = conversationId.toLong()
                                    if (selectedAttachment != null) {
                                        messagingService.sendAttachment(cid, selectedAttachment!!)
                                        selectedAttachment = null
                                    } else {
                                        messagingService.sendText(cid, draft)
                                        draft = ""
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp).background(if (draft.isNotBlank() || selectedAttachment != null) yellow else lightGray, CircleShape)
                ) {
                    Icon(
                        if (draft.isBlank() && selectedAttachment == null) Icons.Default.Mic else Icons.Default.Send,
                        null, 
                        tint = if (draft.isNotBlank() || selectedAttachment != null) Color.Black else Color.Gray,
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

private fun openFileDialog(): String? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Select File", java.awt.FileDialog.LOAD)
    dialog.isVisible = true
    return if (dialog.file != null) dialog.directory + dialog.file else null
}
