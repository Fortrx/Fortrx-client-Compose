package com.fortrx.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.fortrx.messages.ChatPayload
import com.fortrx.messages.ChatPayloadCodec
import com.fortrx.storage.Db
import com.fortrx.ui.components.SafetyNumberDialog
import fortrxclient.composeapp.generated.resources.Res
import fortrxclient.composeapp.generated.resources.bg_chats_doodle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.core.parameter.parametersOf

data class ChatScreen(val contactId: Long) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<ChatScreenModel> { parametersOf(contactId) }
        val state by screenModel.state.collectAsState()
        
        var text by remember { mutableStateOf("") }
        var showMenu by remember { mutableStateOf(false) }
        var isSearchingUI by remember { mutableStateOf(false) }
        var showEmojiPicker by remember { mutableStateOf(false) }
        var showAttachmentPicker by remember { mutableStateOf(false) }
        var showVerifyDialog by remember { mutableStateOf(false) }

        val listState = rememberLazyListState()
        val contactName = state.contact?.username ?: "User $contactId"
        val clipboardManager = LocalClipboardManager.current
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            screenModel.effects.collect { effect ->
                when (effect) {
                    is ChatScreenModel.Effect.ShowError -> {
                        snackbarHostState.showSnackbar(effect.message)
                    }
                }
            }
        }

        LaunchedEffect(state.messages.size) {
            if (state.messages.isNotEmpty()) {
                listState.animateScrollToItem(0)
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (isSearchingUI) {
                    TopAppBar(
                        title = {
                            TextField(
                                value = state.searchQuery,
                                onValueChange = { screenModel.updateSearchQuery(it) },
                                placeholder = { Text("Search in chat...", color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { isSearchingUI = false; screenModel.updateSearchQuery("") }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(contactName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (state.isVerified) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.Verified,
                                            contentDescription = "Verified",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                if (state.contact?.isOnline == true) {
                                    Text("Online", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { navigator.pop() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Search") },
                                    onClick = { isSearchingUI = true; showMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Refresh") },
                                    onClick = { screenModel.refreshInbox(); showMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Verify Identity") },
                                    onClick = { showVerifyDialog = true; showMenu = false },
                                    leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export CSV") },
                                    onClick = { 
                                        val csv = screenModel.exportToCsv()
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(csv))
                                        showMenu = false 
                                    },
                                    leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Chat") },
                                    onClick = { screenModel.deleteChat(); navigator.pop(); showMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            },
            bottomBar = {
                Column {
                    if (showEmojiPicker) {
                        EmojiPickerView(onEmojiSelected = { text += it; showEmojiPicker = false })
                    }
                    if (showAttachmentPicker) {
                        AttachmentPickerView(
                            onFilePicked = { name, mime, bytes ->
                                screenModel.sendAttachment(name, mime, bytes)
                                showAttachmentPicker = false
                            },
                            onDismiss = { showAttachmentPicker = false }
                        )
                    }
                    Surface(tonalElevation = 2.dp) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .imePadding(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showAttachmentPicker = !showAttachmentPicker; showEmojiPicker = false }) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Multimedia", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { showEmojiPicker = !showEmojiPicker; showAttachmentPicker = false }) {
                                Icon(Icons.Default.EmojiEmotions, contentDescription = "Emoji", tint = if (showEmojiPicker) MaterialTheme.colorScheme.primary else Color.Gray)
                            }
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Type a message...") },
                                shape = RoundedCornerShape(24.dp),
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            FloatingActionButton(
                                onClick = {
                                    if (text.isNotBlank()) {
                                        screenModel.sendMessage(text)
                                        text = ""
                                        showEmojiPicker = false
                                        showAttachmentPicker = false
                                    }
                                },
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                val showScrollDown by remember {
                    derivedStateOf { listState.firstVisibleItemIndex > 2 }
                }
                if (showScrollDown) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch { listState.animateScrollToItem(0) }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Scroll to bottom", modifier = Modifier.size(18.dp))
                    }
                }
            }
        ) { padding ->
            if (showVerifyDialog && state.contact != null) {
                SafetyNumberDialog(
                    otherUserId = state.contact!!.userId,
                    otherUsername = state.contact!!.username ?: "User",
                    onDismiss = { showVerifyDialog = false }
                )
            }
            
            Box(Modifier.padding(padding).fillMaxSize()) {
                Image(
                    painter = painterResource(Res.drawable.bg_chats_doodle),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.1f
                )
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        MessageBubble(msg, screenModel, state.selectedMessageIds.contains(msg.id))
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun MessageBubble(msg: Db.StoredMessage, screenModel: ChatScreenModel, isSelected: Boolean) {
        val rawText = msg.plaintext ?: ""
        val isMine = msg.direction == "outgoing"
        val createdAt = msg.createdAt
        val payload = remember(rawText) { ChatPayloadCodec.decode(rawText) }
        var showMenu by remember { mutableStateOf(false) }
        
        val bubbleShape = if (isMine) {
            RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp)
        } else {
            RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)
        }

        val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
        val color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                   else if (isMine) MaterialTheme.colorScheme.primary 
                   else MaterialTheme.colorScheme.surfaceVariant
        val textColor = if (isMine && !isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .combinedClickable(
                    onClick = { if (screenModel.state.value.selectedMessageIds.isNotEmpty()) screenModel.toggleMessageSelection(msg.id) },
                    onLongClick = { showMenu = true }
                ),
            contentAlignment = alignment
        ) {
            Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                Surface(
                    color = color,
                    shape = bubbleShape,
                    shadowElevation = if (isSelected) 4.dp else 1.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (msg.isPinned) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(12.dp).align(Alignment.End),
                                tint = if (isMine && !isSelected) Color.White.copy(alpha = 0.7f) else Color.Gray
                            )
                        }
                        if (payload is ChatPayload.Attachment) {
                            AttachmentView(payload.attachment)
                            if (payload.attachment.fileName.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                        
                        val displayText = when (payload) {
                            is ChatPayload.Text -> payload.text
                            is ChatPayload.Attachment -> "" // Handled by AttachmentView
                        }
                        
                        if (displayText.isNotEmpty()) {
                            Text(
                                text = displayText,
                                color = textColor,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                val time = formatTime(createdAt)
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(if (msg.isPinned) "Unpin" else "Pin") },
                    onClick = { screenModel.pinMessage(msg.id, !msg.isPinned); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.PushPin, null) }
                )
                DropdownMenuItem(
                    text = { Text("Select") },
                    onClick = { screenModel.toggleMessageSelection(msg.id); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.CheckCircle, null) }
                )
                DropdownMenuItem(
                    text = { Text("Forward") },
                    onClick = { 
                        screenModel.toggleMessageSelection(msg.id)
                        showMenu = false 
                    },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, null) }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { 
                        screenModel.toggleMessageSelection(msg.id)
                        screenModel.deleteSelectedMessages()
                        showMenu = false 
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
                )
            }
        }
    }

    private fun formatTime(isoString: String): String {
        return try {
            val timePart = isoString.substringAfter("T").take(5)
            if (!timePart.contains(":")) return isoString.take(16).replace("T", " ")
            val parts = timePart.split(":")
            val hours = parts[0].toInt()
            val minutes = parts[1]
            val suffix = if (hours >= 12) "PM" else "AM"
            val h = if (hours % 12 == 0) 12 else hours % 12
            "$h:$minutes $suffix"
        } catch (_: Exception) {
            isoString.take(16).replace("T", " ")
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun EmojiPickerView(onEmojiSelected: (String) -> Unit) {
        val emojis = listOf("😀", "😂", "😍", "👍", "🔥", "❤️", "🙌", "✨", "🤔", "😎", "😢", "🎉")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 4.dp,
            shadowElevation = 8.dp
        ) {
            Box(modifier = Modifier.height(120.dp).padding(8.dp)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    emojis.forEach { emoji ->
                        TextButton(
                            onClick = { onEmojiSelected(emoji) },
                            modifier = Modifier.size(40.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(emoji, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AttachmentPickerView(
        onFilePicked: (String, String, ByteArray) -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Send Attachment") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Photo (Mock)") },
                        leadingContent = { Icon(Icons.Default.Photo, null) },
                        modifier = Modifier.clickable { 
                            onFilePicked("photo.jpg", "image/jpeg", "fake_image_data".encodeToByteArray())
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Document (Mock)") },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null) },
                        modifier = Modifier.clickable { 
                            onFilePicked("notes.pdf", "application/pdf", "fake_pdf_data".encodeToByteArray())
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }

    @Composable
    private fun AttachmentView(attachment: com.fortrx.messages.AttachmentPayload) {
        if (ChatPayloadCodec.isImage(attachment)) {
            Surface(
                modifier = Modifier
                    .sizeIn(maxWidth = 240.dp, maxHeight = 240.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        attachment.fileName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        ChatPayloadCodec.formatSize(attachment.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(attachment.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(ChatPayloadCodec.formatSize(attachment.sizeBytes), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}
