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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.fortrx.attachments.rememberAttachmentPicker
import com.fortrx.messages.ChatPayload
import com.fortrx.messages.ChatPayloadCodec
import com.fortrx.services.LongMessagePreviewFormatter
import com.fortrx.platform.rememberSaveBytesLauncher
import com.fortrx.services.TimeFormats
import com.fortrx.services.TransferProgress
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
        val scope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        var pendingAttachments by remember { mutableStateOf<List<com.fortrx.attachments.PickedAttachment>>(emptyList()) }
        val saveBytesLauncher = rememberSaveBytesLauncher(
            onSaved = { scope.launch { snackbarHostState.showSnackbar("Saved export.") } },
            onError = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
        )
        val attachmentPicker = rememberAttachmentPicker(
            onPicked = { picked -> pendingAttachments = picked },
            onError = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
        )

        LaunchedEffect(Unit) {
            com.fortrx.Settings.currentlyOpenContactId = contactId
            screenModel.effects.collect { effect ->
                when (effect) {
                    is ChatScreenModel.Effect.ShowError -> {
                        snackbarHostState.showSnackbar(effect.message)
                    }
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                if (com.fortrx.Settings.currentlyOpenContactId == contactId) {
                    com.fortrx.Settings.currentlyOpenContactId = null
                }
            }
        }

        var lastMessageId by remember { mutableStateOf<Long?>(null) }
        LaunchedEffect(state.messages) {
            val latestId = state.messages.firstOrNull()?.id
            if (latestId != null && latestId != lastMessageId) {
                if (lastMessageId == null) {
                    listState.scrollToItem(0)
                } else if (listState.firstVisibleItemIndex <= 1) {
                    listState.animateScrollToItem(0)
                }
                lastMessageId = latestId
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
                                    text = { Text("Double Ratchet Dashboard") },
                                    onClick = { navigator.push(RatchetDashboardScreen(contactId)); showMenu = false },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export CSV") },
                                    onClick = { 
                                        val document = screenModel.exportToCsvDocument()
                                        saveBytesLauncher.launch(document.fileName, document.bytes)
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
                            onPickPhotos = {
                                attachmentPicker.launch(arrayOf("image/*"))
                                showAttachmentPicker = false
                            },
                            onPickVideos = {
                                attachmentPicker.launch(arrayOf("video/*"))
                                showAttachmentPicker = false
                            },
                            onPickDocuments = {
                                attachmentPicker.launch(arrayOf("*/*"))
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

            if (pendingAttachments.isNotEmpty()) {
                AttachmentPreviewDialog(
                    attachments = pendingAttachments,
                    onSend = {
                        pendingAttachments.forEach { screenModel.sendAttachment(it) }
                        pendingAttachments = emptyList()
                    },
                    onCancel = { pendingAttachments = emptyList() }
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
                
                val groupedMessages = remember(state.messages) { groupMessages(state.messages) }

                LaunchedEffect(listState, groupedMessages.size) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                        .collect { lastVisibleIndex ->
                            if (lastVisibleIndex != null && lastVisibleIndex >= groupedMessages.size - 5 && state.hasMore) {
                                screenModel.loadMoreMessages()
                            }
                        }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    if (state.hasIdentityWarning) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "This contact changed devices or keys. Verify the new session before trusting it.",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(onClick = { screenModel.acknowledgeIdentityChange() }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        reverseLayout = true,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(groupedMessages, key = { it.first().id }) { group ->
                            if (group.size == 1) {
                                MessageBubble(group.first(), screenModel, state.selectedMessageIds.contains(group.first().id), state.downloadProgress[group.first().id])
                            } else {
                                MediaGroupBubble(group, screenModel, state.selectedMessageIds, state.downloadProgress)
                            }
                        }

                        if (state.hasMore) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun groupMessages(messages: List<Db.StoredMessage>): List<List<Db.StoredMessage>> {
        if (messages.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<Db.StoredMessage>>()
        var currentGroup = mutableListOf<Db.StoredMessage>()
        
        messages.forEach { msg ->
            val payload = ChatPayloadCodec.decode(msg.plaintext)
            val isMedia = payload is ChatPayload.Attachment && (ChatPayloadCodec.isImage(payload.attachment) || payload.attachment.mimeType.startsWith("video/"))
            
            if (currentGroup.isEmpty()) {
                currentGroup.add(msg)
            } else {
                val prevMsg = currentGroup.last()
                val prevPayload = ChatPayloadCodec.decode(prevMsg.plaintext)
                val prevIsMedia = prevPayload is ChatPayload.Attachment && (ChatPayloadCodec.isImage(prevPayload.attachment) || prevPayload.attachment.mimeType.startsWith("video/"))
                
                // Group if both are media, same direction, same contact, and close in time (e.g. 1 min)
                if (isMedia && prevIsMedia && msg.direction == prevMsg.direction && msg.contactId == prevMsg.contactId) {
                    currentGroup.add(msg)
                } else {
                    groups.add(currentGroup)
                    currentGroup = mutableListOf(msg)
                }
            }
        }
        if (currentGroup.isNotEmpty()) groups.add(currentGroup)
        return groups
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun MediaGroupBubble(
        group: List<Db.StoredMessage>,
        screenModel: ChatScreenModel,
        selectedMessageIds: Set<Long>,
        downloadProgress: Map<Long, TransferProgress>
    ) {
        val firstMsg = group.first()
        val isMine = firstMsg.direction == "outgoing"
        val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
        val bubbleShape = if (isMine) RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp) else RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)
        
        Box(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            contentAlignment = alignment
        ) {
            Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                Surface(
                    color = if (group.any { selectedMessageIds.contains(it.id) }) MaterialTheme.colorScheme.primaryContainer else if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = bubbleShape,
                    shadowElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(4.dp)) {
                        val maxToDisplay = 4
                        val displayList = group.take(maxToDisplay)
                        val remaining = group.size - maxToDisplay
                        
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            // Row 1
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                val row1 = if (displayList.size == 1) displayList else displayList.take(2)
                                row1.forEach { msg ->
                                    MediaItem(msg, screenModel, selectedMessageIds, size = if (displayList.size == 1) 240.dp else 120.dp, progress = downloadProgress[msg.id])
                                }
                            }
                            // Row 2
                            if (displayList.size > 2) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    val row2 = displayList.drop(2)
                                    row2.forEachIndexed { index, msg ->
                                        MediaItem(
                                            msg, 
                                            screenModel, 
                                            selectedMessageIds, 
                                            size = 120.dp,
                                            overlayText = if (index == 1 && remaining > 0) "+$remaining" else null,
                                            progress = downloadProgress[msg.id]
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    TimeFormats.formatChatTime(firstMsg.createdAt),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun MessageBubble(msg: Db.StoredMessage, screenModel: ChatScreenModel, isSelected: Boolean, progress: TransferProgress? = null) {
        val rawText = msg.plaintext ?: ""
        val isMine = msg.direction == "outgoing"
        val createdAt = msg.createdAt
        val payload = remember(rawText) { ChatPayloadCodec.decode(rawText) }
        var showMenu by remember { mutableStateOf(false) }
        
        val savePermissionHandler = if (payload is ChatPayload.Attachment && !payload.attachment.localFileName.isNullOrBlank()) {
            com.fortrx.attachments.rememberGalleryPermissionHandler {
                screenModel.saveAttachmentToDevice(
                    payload.attachment.localFileName!!,
                    payload.attachment.fileName,
                    payload.attachment.mimeType
                )
            }
        } else null

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
                            AttachmentView(
                                msg.id, 
                                payload.attachment, 
                                screenModel, 
                                msg.status, 
                                isMine,
                                progress
                            )
                            if (payload.attachment.fileName.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                        
                        val displayText = when (payload) {
                            is ChatPayload.Text -> payload.text
                            is ChatPayload.Attachment -> "" // Handled by AttachmentView
                        }
                        
                        if (displayText.isNotEmpty()) {
                            ExpandableMessageText(
                                text = displayText,
                                color = textColor,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                val time = TimeFormats.formatChatTime(createdAt)
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (payload is ChatPayload.Attachment && !payload.attachment.localFileName.isNullOrBlank()) {
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            screenModel.shareAttachment(
                                payload.attachment.localFileName!!,
                                payload.attachment.fileName,
                                payload.attachment.mimeType
                            )
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Share, null) }
                    )
                }
                if (savePermissionHandler != null && !isMine) {
                    DropdownMenuItem(
                        text = { Text("Save to Gallery") },
                        onClick = {
                            savePermissionHandler()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Save, null) }
                    )
                }
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

    @Composable
    private fun ExpandableMessageText(text: String, color: Color, style: TextStyle) {
        var expanded by remember(text) { mutableStateOf(false) }
        val preview = remember(text, expanded) { LongMessagePreviewFormatter.format(text, expanded) }

        LinkifiedText(
            text = preview.visibleText,
            color = color,
            style = style
        )
        if (preview.isCollapsible) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(if (expanded) "Read less" else "Read more")
            }
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
        onPickPhotos: () -> Unit,
        onPickVideos: () -> Unit,
        onPickDocuments: () -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Send Attachment") },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text("Photo") },
                        leadingContent = { Icon(Icons.Default.Photo, null) },
                        modifier = Modifier.clickable(onClick = onPickPhotos)
                    )
                    ListItem(
                        headlineContent = { Text("Video") },
                        leadingContent = { Icon(Icons.Default.Videocam, null) },
                        modifier = Modifier.clickable(onClick = onPickVideos)
                    )
                    ListItem(
                        headlineContent = { Text("Document") },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null) },
                        modifier = Modifier.clickable(onClick = onPickDocuments)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }

    @Composable
    private fun AttachmentView(
        messageId: Long, 
        attachment: com.fortrx.messages.AttachmentPayload, 
        screenModel: ChatScreenModel, 
        status: String = "delivered", 
        isMine: Boolean = false,
        progress: TransferProgress? = null
    ) {
        val thumbnail = com.fortrx.attachments.rememberBitmapFromBase64(attachment.thumbnailBase64)
        val fullImage = com.fortrx.attachments.rememberBitmapFromFile(attachment.localFileName)
        val isMedia = ChatPayloadCodec.isImage(attachment) || attachment.mimeType.startsWith("video/")
        val isDownloaded = !attachment.localFileName.isNullOrBlank()

        if (isMedia) {
            Surface(
                modifier = Modifier
                    .sizeIn(maxWidth = 240.dp, maxHeight = 320.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (isDownloaded) {
                            Modifier.clickable { screenModel.openAttachment(attachment.localFileName!!, attachment.mimeType) }
                        } else Modifier
                    ),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val displayBitmap = fullImage ?: thumbnail
                    if (displayBitmap != null) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                bitmap = displayBitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )
                            if (attachment.mimeType.startsWith("video/")) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        modifier = Modifier.size(32.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        Icon(
                            if (attachment.mimeType.startsWith("video/")) Icons.Default.Videocam else Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            attachment.fileName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    
                    if (!isDownloaded || status == "error" || status == "uploading" || status == "downloading") {
                        AttachmentActions(messageId, attachment, screenModel, status, progress)
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = isDownloaded) {
                        screenModel.openAttachment(attachment.localFileName!!, attachment.mimeType)
                    },
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getFileIcon(attachment.mimeType, attachment.fileName),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                attachment.fileName, 
                                style = MaterialTheme.typography.bodyMedium, 
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                ChatPayloadCodec.formatSize(attachment.sizeBytes), 
                                style = MaterialTheme.typography.labelSmall, 
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    
                    if (isDownloaded && status == "delivered") {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap to open",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    if (!isDownloaded || status == "error" || status == "uploading" || status == "downloading") {
                        AttachmentActions(messageId, attachment, screenModel, status, progress)
                    }
                }
            }
        }
    }

    @Composable
    private fun AttachmentActions(
        messageId: Long,
        attachment: com.fortrx.messages.AttachmentPayload,
        screenModel: ChatScreenModel,
        status: String = "delivered",
        progress: TransferProgress? = null
    ) {
        if (status == "uploading" || status == "downloading") {
            Column(
                modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val label = if (status == "uploading") "Uploading...." else "Downloading...."
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                
                if (progress != null) {
                    val downloaded = progress.transferredBytes
                    val total = progress.totalBytes
                    val percent = progress.percent
                    
                    if (percent != null) {
                        LinearProgressIndicator(
                            progress = { percent },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF4CAF50),
                            trackColor = Color.LightGray.copy(alpha = 0.4f)
                        )
                        val percentText = "${(percent * 100).toInt()}% (${ChatPayloadCodec.formatSize(downloaded)} / ${ChatPayloadCodec.formatSize(total ?: 0L)})"
                        Text(percentText, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.5.sp), color = Color(0xFF4CAF50), fontWeight = FontWeight.ExtraBold)
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF4CAF50),
                            trackColor = Color.LightGray.copy(alpha = 0.4f)
                        )
                        Text(ChatPayloadCodec.formatSize(downloaded), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = Color(0xFF4CAF50), fontWeight = FontWeight.ExtraBold)
                    }
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF4CAF50),
                        trackColor = Color.LightGray.copy(alpha = 0.4f)
                    )
                }
            }
        } else if (status == "error") {
            val msg = remember { screenModel.state.value.messages.find { it.id == messageId } }
            val isMine = msg?.direction == "outgoing"
            
            TextButton(
                onClick = { 
                    if (isMine) screenModel.retryAttachmentUpload(messageId)
                    else screenModel.downloadAttachment(messageId)
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Retry")
            }
        } else if (attachment.localFileName.isNullOrBlank()) {
            val permissionHandler = com.fortrx.attachments.rememberGalleryPermissionHandler {
                screenModel.downloadAttachment(messageId)
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = { permissionHandler() },
                enabled = attachment.attachmentId.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Download")
            }
        }
    }

    @Composable
    private fun AttachmentPreviewDialog(
        attachments: List<com.fortrx.attachments.PickedAttachment>,
        onSend: () -> Unit,
        onCancel: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Send ${if (attachments.size > 1) "${attachments.size} attachments" else "attachment"}?") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(attachments) { attachment ->
                        val thumbnail = com.fortrx.attachments.rememberBitmapFromBase64(attachment.thumbnailBase64)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(4.dp)
                        ) {
                            if (thumbnail != null) {
                                Image(
                                    bitmap = thumbnail,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = getFileIcon(attachment.mimeType, attachment.fileName),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(attachment.fileName, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                                Text(ChatPayloadCodec.formatSize(attachment.sizeBytes), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = onSend) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        )
    }

    private fun getFileIcon(mimeType: String, fileName: String): androidx.compose.ui.graphics.vector.ImageVector {
        return when {
            mimeType.startsWith("image/") -> Icons.Default.Image
            mimeType.startsWith("video/") -> Icons.Default.Videocam
            fileName.endsWith(".pdf", ignoreCase = true) || mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
            fileName.endsWith(".xls", ignoreCase = true) || fileName.endsWith(".xlsx", ignoreCase = true) || mimeType.contains("excel") || mimeType.contains("spreadsheet") -> Icons.Default.TableChart
            fileName.endsWith(".doc", ignoreCase = true) || fileName.endsWith(".docx", ignoreCase = true) || mimeType.contains("word") -> Icons.Default.Description
            else -> Icons.AutoMirrored.Filled.InsertDriveFile
        }
    }

    @Composable
    private fun LinkifiedText(text: String, color: Color, style: TextStyle) {
        val urlRegex = "(https?://[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/[\\w- ./?%&=]*)?)".toRegex()
        val phoneRegex = "(\\+?\\d{1,3}[- ]?)?\\(?\\d{3}\\)?[- ]?\\d{3}[- ]?\\d{4}".toRegex()
        
        val annotatedString = buildAnnotatedString {
            var lastMatchEnd = 0
            val allMatches = (urlRegex.findAll(text) + phoneRegex.findAll(text)).sortedBy { it.range.first }
            
            allMatches.forEach { match ->
                append(text.substring(lastMatchEnd, match.range.first))
                pushStringAnnotation(tag = if (match.value.matches(urlRegex)) "URL" else "PHONE", annotation = match.value)
                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                    append(match.value)
                }
                pop()
                lastMatchEnd = match.range.last + 1
            }
            append(text.substring(lastMatchEnd))
        }

        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        
        androidx.compose.foundation.text.ClickableText(
            text = annotatedString,
            style = style.copy(color = color),
            onClick = { offset ->
                annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset).firstOrNull()?.let {
                    uriHandler.openUri(it.item)
                }
                annotatedString.getStringAnnotations(tag = "PHONE", start = offset, end = offset).firstOrNull()?.let {
                    uriHandler.openUri("tel:${it.item.filter { char -> char.isDigit() || char == '+' }}")
                }
            }
        )
        
        // Link Preview (simplified)
        val firstUrl = urlRegex.find(text)?.value
        if (firstUrl != null) {
            LinkPreview(firstUrl)
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun MediaItem(
        msg: Db.StoredMessage,
        screenModel: ChatScreenModel,
        selectedMessageIds: Set<Long>,
        size: androidx.compose.ui.unit.Dp,
        overlayText: String? = null,
        progress: TransferProgress? = null
    ) {
        val payload = ChatPayloadCodec.decode(msg.plaintext) as ChatPayload.Attachment
        val isSelected = selectedMessageIds.contains(msg.id)
        val thumbnail = com.fortrx.attachments.rememberBitmapFromBase64(payload.attachment.thumbnailBase64)
        val fullImage = com.fortrx.attachments.rememberBitmapFromFile(payload.attachment.localFileName)
        val displayBitmap = fullImage ?: thumbnail
        
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.1f))
                .combinedClickable(
                    onClick = { 
                        if (selectedMessageIds.isNotEmpty()) screenModel.toggleMessageSelection(msg.id)
                        else if (!payload.attachment.localFileName.isNullOrBlank()) screenModel.openAttachment(payload.attachment.localFileName!!, payload.attachment.mimeType)
                        else screenModel.downloadAttachment(msg.id)
                    },
                    onLongClick = { screenModel.toggleMessageSelection(msg.id) }
                )
        ) {
            if (displayBitmap != null) {
                Image(
                    bitmap = displayBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            if (payload.attachment.mimeType.startsWith("video/")) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.align(Alignment.Center).size(size / 3), tint = Color.White)
            }
            if (isSelected) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.align(Alignment.Center), tint = Color.White)
                }
            }
            if (overlayText != null) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                    Text(overlayText, color = Color.White, style = MaterialTheme.typography.titleLarge)
                }
            }
            // If not downloaded, show a small download icon overlay
            if (payload.attachment.localFileName.isNullOrBlank() && msg.status != "downloading") {
                Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)) {
                    Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(16.dp).padding(2.dp))
                }
            }
            if (msg.status == "downloading") {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (progress?.percent != null) {
                            CircularProgressIndicator(
                                progress = { progress.percent ?: 0f },
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                                color = Color.White
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                                color = Color.White
                            )
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        Surface(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                                Text(
                                    progress?.message ?: "Downloading...",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                )
                                if (progress != null) {
                                    val downloaded = progress.transferredBytes
                                    val total = progress.totalBytes
                                    val progressText = if (total != null && total > 0) {
                                        "${ChatPayloadCodec.formatSize(downloaded)} / ${ChatPayloadCodec.formatSize(total)}"
                                    } else {
                                        ChatPayloadCodec.formatSize(downloaded)
                                    }
                                    Text(
                                        progressText,
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun LinkPreview(url: String) {
        // In a real app, you'd fetch metadata from the URL.
        // For now, we'll show a simple UI card.
        Surface(
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(url.substringAfter("://").take(30) + "...", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    Text("Tap to visit link", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}
