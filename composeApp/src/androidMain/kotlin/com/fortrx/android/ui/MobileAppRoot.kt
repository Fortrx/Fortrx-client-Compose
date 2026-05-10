package com.fortrx.android.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.pm.ApplicationInfo
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.FileProvider
import androidx.compose.ui.unit.dp
import com.fortrx.FortrxClient
import com.fortrx.crypto.RatchetState
import com.fortrx.messages.AttachmentPayload
import com.fortrx.messages.ChatPayload
import com.fortrx.messages.ChatPayloadCodec
import com.fortrx.messages.MAX_INLINE_ATTACHMENT_BYTES
import com.fortrx.services.BackupCode
import com.fortrx.services.MessagingService
import com.fortrx.services.OnboardingService
import com.fortrx.services.VerificationService
import com.fortrx.storage.Db
import com.fortrx.storage.Keystore
import com.fortrx.storage.SettingsStore
import com.fortrx.shared.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private enum class Screen { Loading, Onboarding, ChatList, Chat, Settings }

private data class MobileConversationRow(
    val key: String,
    val id: Long,
    val title: String,
    val preview: String,
    val timeLabel: String,
    val unreadCount: Int,
    val isOnline: Boolean,
    val isSearchResult: Boolean = false,
    val resultLabel: String? = null,
    val isLocalConversation: Boolean = true,
)

private data class ConversationThemeState(
    val wallpaperUri: String? = null,
    val wallpaperDim: Float = 0.22f,
    val bubbleOpacity: Float = 0.94f,
)

@Composable
fun MobileAppRoot() {
    var screen by rememberSaveable { mutableStateOf(Screen.Loading) }
    var current by rememberSaveable { mutableStateOf<Long?>(null) }

    BackHandler(enabled = screen == Screen.Chat || screen == Screen.Settings) {
        when (screen) {
            Screen.Chat, Screen.Settings -> screen = Screen.ChatList
            else -> Unit
        }
    }

    LaunchedEffect(Unit) {
        val savedPw = runCatching { SettingsStore.loadStoragePassword() }.getOrNull()
        val savedId = runCatching { SettingsStore.loadMyId() }.getOrNull()
        if (savedPw != null && savedId != null) {
            com.fortrx.Settings.storagePassword = savedPw
            com.fortrx.Settings.myId = savedId
        }

        if (FortrxClient.tryAutoLogin()) {
            screen = Screen.ChatList
        } else {
            screen = Screen.Onboarding
        }
    }

    val colorScheme = androidx.compose.material3.lightColorScheme(
        primary = Color(0xFFFFC107),
        onPrimary = Color.Black,
        secondary = Color(0xFF212121),
        surface = Color.White,
        background = Color.White,
        surfaceVariant = Color(0xFFF4F4F4),
        onSurfaceVariant = Color(0xFF5F6368),
        error = Color(0xFFD32F2F),
    )

    MaterialTheme(colorScheme = colorScheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (screen) {
                Screen.Loading -> SplashScreen()
                Screen.Onboarding -> MobileOnboardingScreen(onDone = { screen = Screen.ChatList })
                Screen.ChatList -> ChatListScreen(
                    onOpen = { current = it; screen = Screen.Chat },
                    onSettings = { screen = Screen.Settings },
                )
                Screen.Chat -> ChatScreen(
                    id = current ?: return@Surface,
                    onBack = { screen = Screen.ChatList },
                )
                Screen.Settings -> MobileSettingsScreen(
                    onBack = { screen = Screen.ChatList },
                    onLogout = { screen = Screen.Onboarding },
                )
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "Fortrx logo",
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp)),
            )
            Spacer(Modifier.height(18.dp))
            Text("FORTRX", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        }
    }
}

private enum class OnboardingMode { Login, Register, Restore }

@Composable
private fun MobileOnboardingScreen(onDone: () -> Unit) {
    var mode by remember { mutableStateOf(OnboardingMode.Login) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var backupPhrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val canSubmit = username.isNotBlank() &&
        password.length >= 8 &&
        (mode != OnboardingMode.Register || email.isNotBlank()) &&
        (mode != OnboardingMode.Restore || BackupCode.isValid(backupPhrase)) &&
        !loading

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Text(
                "Private messaging\nbuilt for real-time\nconversations",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = Color.Black,
            )
            Spacer(Modifier.height(32.dp))

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it; error = null },
                label = { Text("Username") },
                singleLine = true,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )

            if (mode == OnboardingMode.Register) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; error = null },
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )
            }

            if (mode == OnboardingMode.Restore) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = backupPhrase,
                    onValueChange = { backupPhrase = it; error = null },
                    label = { Text("Backup Phrase (30-36 digits)") },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = { Text("Password") },
                singleLine = true,
                enabled = !loading,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            when (mode) {
                                OnboardingMode.Register -> {
                                    val result = OnboardingService.register(username.trim(), email.trim(), password)
                                    showBackupDialog = result.backupCode
                                }
                                OnboardingMode.Login -> {
                                    OnboardingService.login(username.trim(), password)
                                }
                                OnboardingMode.Restore -> {
                                    OnboardingService.restore(username.trim(), password, backupPhrase.trim())
                                }
                            }
                            FortrxClient.startSyncEngine(password)
                            if (showBackupDialog == null) onDone()
                        } catch (t: Throwable) {
                            error = t.message ?: "Authentication failed"
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Text(
                        when (mode) {
                            OnboardingMode.Register -> "Create account"
                            OnboardingMode.Restore -> "Restore account"
                            else -> "Log in"
                        },
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(
                    onClick = {
                        mode = if (mode == OnboardingMode.Login) OnboardingMode.Register else OnboardingMode.Login
                        error = null
                    },
                ) {
                    Text(
                        if (mode == OnboardingMode.Login) "Sign up" else "Log in",
                        color = Color.Gray,
                    )
                }
                if (mode != OnboardingMode.Restore) {
                    TextButton(onClick = { mode = OnboardingMode.Restore; error = null }) {
                        Text("Restore from backup", color = Color.Gray)
                    }
                }
            }
        }
    }

    showBackupDialog?.let { code ->
        AlertDialog(
            onDismissRequest = { showBackupDialog = null; onDone() },
            title = { Text("Backup Phrase") },
            text = {
                Column {
                    Text("This is your unique recovery code. Write it down! You'll need it to log in on other devices or restore your account.")
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            BackupCode.format(code),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showBackupDialog = null; onDone() }) {
                    Text("I've written it down")
                }
            }
        )
    }
}

@Composable
private fun ChatListScreen(onOpen: (Long) -> Unit, onSettings: () -> Unit) {
    val pw = com.fortrx.Settings.storagePassword
    val items = remember { mutableStateListOf<MobileConversationRow>() }
    val selectedConversationIds = remember { mutableStateListOf<Long>() }
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var remoteMatch by remember { mutableStateOf<MobileConversationRow?>(null) }
    var messageHits by remember { mutableStateOf<List<MobileConversationRow>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = query.isNotBlank() || selectedConversationIds.isNotEmpty()) {
        when {
            selectedConversationIds.isNotEmpty() -> selectedConversationIds.clear()
            query.isNotBlank() -> query = ""
        }
    }

    LaunchedEffect(pw) {
        if (pw == null) return@LaunchedEffect
        Db.listConversationOverviewsFlow(pw).collect { rows ->
            items.clear()
            items.addAll(
                rows.map { row ->
                    MobileConversationRow(
                        key = "conversation-${row.contactId}",
                        id = row.contactId,
                        title = row.username ?: "User ${row.contactId}",
                        preview = row.lastMessagePreview.orEmpty(),
                        timeLabel = formatConversationTime(row.lastMessageAt),
                        unreadCount = row.unreadCount.toInt(),
                        isOnline = row.isOnline,
                    )
                }
            )
        }
    }

    LaunchedEffect(query, pw) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            remoteMatch = null
            messageHits = emptyList()
            searchLoading = false
            return@LaunchedEffect
        }
        searchLoading = true
        delay(250)
        remoteMatch = if (shouldAttemptRemoteUsernameLookup(trimmed)) {
            runCatching {
                val user = MessagingService.getUserByUsername(trimmed)
                val userId = user["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@runCatching null
                MobileConversationRow(
                    key = "server-$userId",
                    id = userId,
                    title = user["username"]?.jsonPrimitive?.contentOrNull ?: trimmed,
                    preview = "Start a new conversation",
                    timeLabel = "",
                    unreadCount = 0,
                    isOnline = Db.getContact(userId)?.isOnline == true,
                    isSearchResult = true,
                    resultLabel = "server",
                    isLocalConversation = items.any { it.id == userId },
                )
            }.getOrNull()
        } else {
            null
        }
        messageHits = if (pw == null) {
            emptyList()
        } else {
            Db.searchMessages(pw, trimmed).map { hit ->
                MobileConversationRow(
                    key = "message-${hit.messageId}",
                    id = hit.contactId,
                    title = hit.username ?: "User ${hit.contactId}",
                    preview = hit.plaintext,
                    timeLabel = formatConversationTime(hit.createdAt),
                    unreadCount = 0,
                    isOnline = Db.getContact(hit.contactId)?.isOnline == true,
                    isSearchResult = true,
                    resultLabel = "message",
                )
            }
        }
        searchLoading = false
    }

    val filteredLocal = remember(query, items.toList()) {
        if (query.isBlank()) items.toList()
        else items.filter {
            it.title.contains(query.trim(), ignoreCase = true) ||
                it.preview.contains(query.trim(), ignoreCase = true)
        }
    }
    val renderedRows = remember(filteredLocal, remoteMatch, messageHits) {
        buildList {
            remoteMatch?.takeIf { remote ->
                filteredLocal.none { it.id == remote.id }
            }?.let(::add)
            messageHits.forEach { hit ->
                add(hit)
            }
            addAll(filteredLocal)
        }
    }

    Scaffold(
        topBar = {
            HomeHeader(
                selectedCount = selectedConversationIds.size,
                onSettings = onSettings,
                onClearSelection = { selectedConversationIds.clear() },
                onDeleteSelected = { showDeleteConfirm = true },
            )
        },
        bottomBar = {
            HomeSearchDock(
                query = query,
                onQueryChange = { query = it },
            )
        }
    ) { padding ->
        when {
            renderedRows.isEmpty() && query.isNotBlank() && searchLoading -> {
                Box(
                    Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            renderedRows.isEmpty() && query.isNotBlank() -> {
                Box(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No username matched", style = MaterialTheme.typography.titleMedium)
                        Text("Try the exact username you want to message.", color = Color.Gray)
                    }
                }
            }

            renderedRows.isEmpty() -> {
                Box(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No conversations yet", style = MaterialTheme.typography.titleMedium)
                        Text("Use the search bar below to find contacts and messages.", color = Color.Gray)
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .background(Color.White),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(renderedRows, key = { it.key }) { row ->
                        ConversationRow(
                            row = row,
                            selected = row.id in selectedConversationIds,
                            onClick = {
                                if (selectedConversationIds.isNotEmpty() && row.isLocalConversation) {
                                    if (row.id in selectedConversationIds) selectedConversationIds.remove(row.id)
                                    else selectedConversationIds.add(row.id)
                                } else {
                                    Db.markConversationViewed(row.id)
                                    onOpen(row.id)
                                }
                            },
                            onLongClick = {
                                if (row.isLocalConversation && row.id !in selectedConversationIds) {
                                    selectedConversationIds.add(row.id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (selectedConversationIds.size == 1) "Delete chat?" else "Delete chats?") },
            text = {
                Text(
                    if (selectedConversationIds.size == 1) {
                        "This removes the selected local conversation from this device."
                    } else {
                        "This removes ${selectedConversationIds.size} local conversations from this device."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        Db.deleteConversations(selectedConversationIds.toList())
                        selectedConversationIds.clear()
                        showDeleteConfirm = false
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun HomeHeader(
    selectedCount: Int,
    onSettings: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    Surface(color = Color.White) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedCount > 0) {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
                Text(
                    "$selectedCount selected",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDeleteSelected) {
                    Icon(Icons.Default.Delete, null)
                }
            } else {
                Image(
                    painter = painterResource(id = R.drawable.app_icon),
                    contentDescription = "Fortrx logo",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("FORTRX", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("Secure conversations", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, null)
                }
            }
        }
    }
}

@Composable
private fun HomeSearchDock(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Search contacts and messages") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF8F8F8),
                    unfocusedContainerColor = Color(0xFFF8F8F8),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color(0xFFE5E5E5),
                ),
            )
        }
    }
}

@Composable
private fun ConversationRow(
    row: MobileConversationRow,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column {
        ListItem(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(vertical = 4.dp),
            leadingContent = {
                Box {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = Color(0xFFEEEEEE),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                row.title.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.Black,
                            )
                        }
                    }
                    if (row.isOnline) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(14.dp),
                            color = Color(0xFF30D158),
                            shape = CircleShape,
                        ) {}
                    }
                }
            },
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    if (row.isSearchResult && row.resultLabel != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            row.resultLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF0A84FF),
                        )
                    }
                }
            },
            supportingContent = {
                Text(
                    row.preview,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                )
            },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End) {
                    if (row.timeLabel.isNotBlank()) {
                        Text(row.timeLabel, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    if (row.unreadCount > 0) {
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier.size(22.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    row.unreadCount.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.Black,
                                )
                            }
                        }
                    }
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = if (selected) Color(0xFFFFF3C4) else Color.White
            ),
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color(0xFFEEEEEE))
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalEncodingApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(id: Long, onBack: () -> Unit) {
    val pw = com.fortrx.Settings.storagePassword ?: return
    val messages = remember(id) { mutableStateListOf<Db.StoredMessage>() }
    val selectedMessageIds = remember(id) { mutableStateListOf<Long>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var draft by rememberSaveable(id) { mutableStateOf("") }
    var selectedAttachment by remember { mutableStateOf<AttachmentPayload?>(null) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClearConversationConfirm by remember { mutableStateOf(false) }
    var showVerify by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var isChatSearchVisible by remember(id) { mutableStateOf(false) }
    var chatSearchQuery by rememberSaveable(id) { mutableStateOf("") }
    var chatSearchResults by remember(id) { mutableStateOf<List<Db.StoredMessage>>(emptyList()) }
    var contactName by remember(id) { mutableStateOf("User $id") }
    var onlineState by remember(id) { mutableStateOf(false) }
    var verifiedState by remember(id) { mutableStateOf(VerificationService.isVerified(id)) }
    var themeState by remember(id) { mutableStateOf(ConversationThemeState()) }
    var pendingScrollToBottom by remember(id) { mutableStateOf(false) }
    var hasPositionedInitialMessage by remember(id) { mutableStateOf(false) }
    var pendingUnreadAtBottom by remember(id) { mutableIntStateOf(0) }
    val composerInteraction = remember { MutableInteractionSource() }
    val isComposerFocused by composerInteraction.collectIsFocusedAsState()
    val displayedMessages = if (isChatSearchVisible && chatSearchQuery.isNotBlank()) chatSearchResults else messages
    val wallpaperImage = rememberWallpaperImage(context, themeState.wallpaperUri)
    val outgoingBubbleColor = MaterialTheme.colorScheme.primary.copy(alpha = themeState.bubbleOpacity)
    val incomingBubbleColor = Color(0xFFF2F2F2).copy(alpha = themeState.bubbleOpacity)

    val shouldAutoFollow by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems <= 1) return@derivedStateOf true
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= totalItems - 2
        }
    }

    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        selectedAttachment = runCatching { readAttachmentFromUri(context, uri) }.getOrElse {
            attachmentError = it.message ?: "Could not load attachment"
            null
        }
    }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val updatedTheme = themeState.copy(wallpaperUri = uri.toString())
        themeState = updatedTheme
        ConversationThemeStore.save(context, id, updatedTheme)
    }

    BackHandler {
        when {
            showThemeDialog -> showThemeDialog = false
            showProfile -> showProfile = false
            selectedMessageIds.isNotEmpty() -> selectedMessageIds.clear()
            isChatSearchVisible -> {
                isChatSearchVisible = false
                chatSearchQuery = ""
                chatSearchResults = emptyList()
            }
            else -> onBack()
        }
    }

    LaunchedEffect(id) {
        Db.contactFlow(id).collect { contact ->
            contactName = contact?.username ?: "User $id"
            onlineState = contact?.isOnline == true
        }
    }

    LaunchedEffect(id) {
        themeState = ConversationThemeStore.load(context, id)
    }

    LaunchedEffect(id, pw) {
        Db.listConversationFlow(pw, id).collect { stored ->
            val oldIds = messages.mapTo(linkedSetOf()) { it.id }
            val newMessages = stored.reversed()
            val incomingAdded = newMessages.count { it.direction == "incoming" && it.id !in oldIds }
            val followBottom = !hasPositionedInitialMessage || (!isChatSearchVisible && shouldAutoFollow)
            messages.clear()
            messages.addAll(newMessages)
            selectedMessageIds.removeAll { selectedId -> stored.none { it.id == selectedId } }
            if (messages.isEmpty()) {
                pendingUnreadAtBottom = 0
                return@collect
            }
            if (!isChatSearchVisible && followBottom) {
                pendingScrollToBottom = true
            } else if (incomingAdded > 0) {
                pendingUnreadAtBottom += incomingAdded
            }
        }
    }

    LaunchedEffect(id, pw, chatSearchQuery, isChatSearchVisible, messages.size) {
        if (!isChatSearchVisible) {
            chatSearchResults = emptyList()
            return@LaunchedEffect
        }
        val trimmed = chatSearchQuery.trim()
        if (trimmed.isBlank()) {
            chatSearchResults = emptyList()
            return@LaunchedEffect
        }
        delay(150)
        chatSearchResults = Db.searchConversationMessages(pw, id, trimmed)
    }

    LaunchedEffect(messages.size, pendingScrollToBottom, isChatSearchVisible) {
        if (isChatSearchVisible || !pendingScrollToBottom || messages.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(messages.lastIndex)
        Db.markConversationViewed(id)
        hasPositionedInitialMessage = true
        pendingUnreadAtBottom = 0
        pendingScrollToBottom = false
    }

    LaunchedEffect(shouldAutoFollow, messages.size, isChatSearchVisible) {
        if (isChatSearchVisible || !shouldAutoFollow || messages.isEmpty()) return@LaunchedEffect
        Db.markConversationViewed(id)
        pendingUnreadAtBottom = 0
    }

    LaunchedEffect(isComposerFocused, messages.size, isChatSearchVisible) {
        if (!isComposerFocused || isChatSearchVisible || messages.isEmpty()) return@LaunchedEffect
        listState.animateScrollToItem(messages.lastIndex)
        Db.markConversationViewed(id)
        pendingUnreadAtBottom = 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selectedMessageIds.isNotEmpty()) {
                        Text("${selectedMessageIds.size} selected", fontWeight = FontWeight.Bold)
                    } else {
                        Column(modifier = Modifier.clickable { showProfile = true }) {
                            Text(contactName, fontWeight = FontWeight.Bold)
                            Text(
                                if (onlineState) "Online" else "Offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (onlineState) Color(0xFF30D158) else Color.Gray,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedMessageIds.isNotEmpty()) selectedMessageIds.clear() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    if (selectedMessageIds.isNotEmpty()) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, null)
                        }
                    } else {
                        IconButton(onClick = { isChatSearchVisible = !isChatSearchVisible; if (!isChatSearchVisible) chatSearchQuery = "" }) {
                            Icon(Icons.Default.Search, null)
                        }
                        IconButton(onClick = { showThemeDialog = true }) {
                            Icon(Icons.Default.Palette, null)
                        }
                        IconButton(onClick = { showVerify = true }) {
                            Icon(Icons.Default.VerifiedUser, null, tint = if (verifiedState) Color(0xFF2E7D32) else Color.Gray)
                        }
                    }
                },
            )
        }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
        ) {
            wallpaperImage?.let { image ->
                Image(
                    bitmap = image,
                    contentDescription = "Chat wallpaper",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (themeState.wallpaperUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = themeState.wallpaperDim))
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                if (isChatSearchVisible) {
                    Surface(
                        color = Color.White.copy(alpha = if (themeState.wallpaperUri != null) 0.94f else 1f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            OutlinedTextField(
                                value = chatSearchQuery,
                                onValueChange = { chatSearchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search in this chat") },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                shape = RoundedCornerShape(20.dp),
                            )
                            if (chatSearchQuery.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (chatSearchResults.isEmpty()) "No local matches" else "${chatSearchResults.size} local matches",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.Gray,
                                )
                            }
                        }
                    }
                }

                if (attachmentError != null) {
                    Text(
                        attachmentError!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (displayedMessages.isEmpty() && isChatSearchVisible && chatSearchQuery.isNotBlank()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No local matches in this chat", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(displayedMessages, key = { it.id }) { message ->
                                val mine = message.direction == "outgoing"
                                val selected = message.id in selectedMessageIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (selectedMessageIds.isNotEmpty()) {
                                                    if (selected) selectedMessageIds.remove(message.id) else selectedMessageIds.add(message.id)
                                                } else {
                                                    openPayload(context, message.plaintext)
                                                }
                                            },
                                            onLongClick = {
                                                if (selected) selectedMessageIds.remove(message.id) else selectedMessageIds.add(message.id)
                                            },
                                        ),
                                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                                ) {
                                    Surface(
                                        color = when {
                                            selected -> Color(0xFFFFE082)
                                            mine -> outgoingBubbleColor
                                            else -> incomingBubbleColor
                                        },
                                        shape = RoundedCornerShape(
                                            topStart = 20.dp,
                                            topEnd = 20.dp,
                                            bottomStart = if (mine) 20.dp else 4.dp,
                                            bottomEnd = if (mine) 4.dp else 20.dp,
                                        ),
                                        modifier = Modifier.widthIn(max = 280.dp),
                                    ) {
                                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                            MessageBubbleBody(message.plaintext)
                                            Spacer(Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.align(Alignment.End),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    text = formatMessageTime(message.createdAt),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray,
                                                )
                                                if (mine) {
                                                    Spacer(Modifier.width(4.dp))
                                                    Icon(
                                                        imageVector = when (message.status) {
                                                            "sending" -> Icons.Default.Schedule
                                                            "sent" -> Icons.Default.Done
                                                            "error" -> Icons.Default.Error
                                                            else -> Icons.Default.Done
                                                        },
                                                        contentDescription = null,
                                                        modifier = Modifier.size(12.dp),
                                                        tint = when (message.status) {
                                                            "error" -> Color.Red
                                                            "sending" -> Color.Gray
                                                            else -> Color.Gray
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (pendingUnreadAtBottom > 0 && !shouldAutoFollow && !isChatSearchVisible) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp)
                                .clickable {
                                    scope.launch {
                                        listState.animateScrollToItem(messages.lastIndex)
                                        Db.markConversationViewed(id)
                                        pendingUnreadAtBottom = 0
                                    }
                                },
                            color = Color.Black,
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (pendingUnreadAtBottom == 1) "1 new message" else "${pendingUnreadAtBottom} new messages",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Jump down", color = Color(0xFFFFC107), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                selectedAttachment?.let { attachment ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        color = Color(0xFFF7F7F7).copy(alpha = themeState.bubbleOpacity),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (ChatPayloadCodec.isImage(attachment)) Icons.Default.Image else Icons.Default.InsertDriveFile,
                                null,
                                tint = Color(0xFF0A84FF),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(attachment.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text(
                                    ChatPayloadCodec.formatSize(attachment.sizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                )
                            }
                            IconButton(onClick = { selectedAttachment = null }) {
                                Icon(Icons.Default.Delete, null, tint = Color.Gray)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Surface(
                    color = Color.White.copy(alpha = if (themeState.wallpaperUri != null) 0.94f else 1f),
                    modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { attachmentPicker.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Default.AttachFile, null, tint = Color.Gray)
                        }
                        TextField(
                            value = draft,
                            onValueChange = { draft = it; attachmentError = null },
                            interactionSource = composerInteraction,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Type here...") },
                            minLines = 1,
                            maxLines = 4,
                            shape = RoundedCornerShape(28.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF2F2F2).copy(alpha = themeState.bubbleOpacity),
                                unfocusedContainerColor = Color(0xFFF2F2F2).copy(alpha = themeState.bubbleOpacity),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        val canSend = draft.isNotBlank() || selectedAttachment != null
                        IconButton(
                            onClick = {
                                if (!canSend) return@IconButton
                                scope.launch {
                                    try {
                                        val attachment = selectedAttachment
                                        if (attachment != null) {
                                            MessagingService.sendAttachment(id, attachment)
                                            selectedAttachment = null
                                        } else {
                                            MessagingService.sendText(id, draft)
                                            draft = ""
                                        }
                                        attachmentError = null
                                    } catch (t: Throwable) {
                                        attachmentError = t.message ?: "Could not send message"
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(if (canSend) MaterialTheme.colorScheme.primary else Color(0xFFF2F2F2), CircleShape),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                null,
                                tint = if (canSend) Color.Black else Color.Gray,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (selectedMessageIds.size == 1) "Delete message?" else "Delete messages?") },
            text = {
                Text(
                    if (selectedMessageIds.size == 1) {
                        "This removes the selected message from local history on this device."
                    } else {
                        "This removes ${selectedMessageIds.size} selected messages from local history on this device."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        Db.deleteMessages(pw, selectedMessageIds.toList(), id)
                        selectedMessageIds.clear()
                        showDeleteConfirm = false
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showClearConversationConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConversationConfirm = false },
            title = { Text("Clear local chat?") },
            text = { Text("This removes all local messages with $contactName on this device.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        Db.deleteConversation(id)
                        selectedMessageIds.clear()
                        showClearConversationConfirm = false
                        showProfile = false
                    }
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConversationConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showProfile) {
        var menuExpanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showProfile = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(contactName, fontWeight = FontWeight.Bold)
                        Text("User ID: $id", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, null)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Search in chat") },
                                onClick = {
                                    menuExpanded = false
                                    showProfile = false
                                    isChatSearchVisible = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Chat theme") },
                                onClick = {
                                    menuExpanded = false
                                    showThemeDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Clear all chats") },
                                onClick = {
                                    menuExpanded = false
                                    showClearConversationConfirm = true
                                },
                            )
                        }
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (onlineState) "Currently online" else "Currently offline", color = if (onlineState) Color(0xFF2E7D32) else Color.Gray)
                    Text(
                        if (verifiedState) "Safety number verified on this device." else "Safety number has not been verified on this device.",
                        color = Color.Gray,
                    )
                    Text("Open the three-dot menu for chat search, chat theme, or clearing local history.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showProfile = false }) { Text("Close") }
            },
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Chat theme") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (themeState.wallpaperUri == null) "No wallpaper selected yet." else "Wallpaper is stored locally for this chat.",
                        color = Color.Gray,
                    )
                    Button(onClick = { wallpaperPicker.launch(arrayOf("image/*")) }) {
                        Text(if (themeState.wallpaperUri == null) "Choose wallpaper" else "Change wallpaper")
                    }
                    if (themeState.wallpaperUri != null) {
                        TextButton(onClick = {
                            val updatedTheme = themeState.copy(wallpaperUri = null)
                            themeState = updatedTheme
                            ConversationThemeStore.save(context, id, updatedTheme)
                        }) {
                            Text("Remove wallpaper")
                        }
                    }
                    Text("Wallpaper dim", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = themeState.wallpaperDim,
                        onValueChange = { value ->
                            val updatedTheme = themeState.copy(wallpaperDim = value)
                            themeState = updatedTheme
                            ConversationThemeStore.save(context, id, updatedTheme)
                        },
                        valueRange = 0f..0.65f,
                    )
                    Text("Bubble opacity", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = themeState.bubbleOpacity,
                        onValueChange = { value ->
                            val updatedTheme = themeState.copy(bubbleOpacity = value)
                            themeState = updatedTheme
                            ConversationThemeStore.save(context, id, updatedTheme)
                        },
                        valueRange = 0.7f..1f,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Done") }
            },
        )
    }

    if (showVerify) {
        var safetyNumber by remember { mutableStateOf<com.fortrx.crypto.Fingerprint.SafetyNumber?>(null) }
        LaunchedEffect(id) {
            safetyNumber = VerificationService.getSafetyNumber(pw, id)
        }
        AlertDialog(
            onDismissRequest = { showVerify = false },
            title = { Text("Safety number") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Compare this number with the other person offline or in person before marking the session as verified.")
                    Text(safetyNumber?.safetyNumber ?: "Loading...")
                }
            },
            confirmButton = {
                Button(onClick = {
                    safetyNumber?.let { VerificationService.markVerified(id, it.safetyNumber) }
                    verifiedState = true
                    showVerify = false
                }) { Text("Verify") }
            },
            dismissButton = { TextButton(onClick = { showVerify = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun MessageBubbleBody(raw: String?) {
    when (val payload = ChatPayloadCodec.decode(raw)) {
        is ChatPayload.Text -> Text(
            payload.text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (containsLink(payload.text)) Color(0xFF0A84FF) else Color.Black,
        )
        is ChatPayload.Attachment -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ChatPayloadCodec.isImage(payload.attachment)) Icons.Default.Image else Icons.Default.InsertDriveFile,
                    null,
                    tint = Color.Black,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(payload.attachment.fileName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        ChatPayloadCodec.formatSize(payload.attachment.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray,
                    )
                }
            }
        }
    }
}

private fun shouldAttemptRemoteUsernameLookup(query: String): Boolean {
    val trimmed = query.trim()
    return trimmed.length in 2..64 && !trimmed.contains('/')
}

private fun formatConversationTime(raw: String?): String {
    val dateTime = raw?.let(::parseLocalDateTime) ?: return ""
    val now = kotlinx.datetime.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return if (dateTime.date == now.date) {
        formatClock(dateTime.hour, dateTime.minute)
    } else {
        "${dateTime.date.dayOfMonth}/${dateTime.date.monthNumber}"
    }
}

private fun formatMessageTime(raw: String?): String {
    val dateTime = raw?.let(::parseLocalDateTime) ?: return raw.orEmpty()
    return formatClock(dateTime.hour, dateTime.minute)
}

private fun parseLocalDateTime(raw: String) =
    runCatching {
        Instant.parse(raw).toLocalDateTime(TimeZone.currentSystemDefault())
    }.getOrNull()

private fun formatClock(hour: Int, minute: Int): String {
    val h = if (hour % 12 == 0) 12 else hour % 12
    val ampm = if (hour < 12) "AM" else "PM"
    return "$h:${minute.toString().padStart(2, '0')} $ampm"
}

private object ConversationThemeStore {
    private const val PREFS_NAME = "fortrx_chat_theme"

    fun load(context: android.content.Context, contactId: Long): ConversationThemeState {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        return ConversationThemeState(
            wallpaperUri = prefs.getString("wallpaper_$contactId", null),
            wallpaperDim = prefs.getFloat("wallpaper_dim_$contactId", 0.22f),
            bubbleOpacity = prefs.getFloat("bubble_alpha_$contactId", 0.94f),
        )
    }

    fun save(context: android.content.Context, contactId: Long, state: ConversationThemeState) {
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("wallpaper_$contactId", state.wallpaperUri)
            .putFloat("wallpaper_dim_$contactId", state.wallpaperDim)
            .putFloat("bubble_alpha_$contactId", state.bubbleOpacity)
            .apply()
    }
}

@Composable
private fun rememberWallpaperImage(context: android.content.Context, wallpaperUri: String?): ImageBitmap? {
    var image by remember(wallpaperUri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(context, wallpaperUri) {
        image = if (wallpaperUri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(wallpaperUri))?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    return image
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileSettingsScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    val isDevBuild = remember(context) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    var revealBackup by remember { mutableStateOf(false) }
    var backupCode by remember { mutableStateOf<String?>(null) }
    var deletePassword by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restoreCode by remember { mutableStateOf("") }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var backupBusy by remember { mutableStateOf(false) }
    var devDiagnostics by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val password = com.fortrx.Settings.storagePassword ?: return@launch
            val userId = com.fortrx.Settings.myId ?: return@launch
            backupBusy = true
            error = null
            try {
                val code = backupCode ?: BackupCode.generate().also {
                    SettingsStore.saveBackupCode(it)
                    backupCode = it
                }
                exportLocalBackup(context, uri, password, userId, code)
            } catch (t: Throwable) {
                error = t.message ?: "Backup failed"
            } finally {
                backupBusy = false
            }
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        restoreUri = uri
        restoreCode = ""
        showRestoreDialog = uri != null
    }

    LaunchedEffect(Unit) {
        backupCode = runCatching { SettingsStore.loadBackupCode() }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFF7F7F7)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup phrase", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "This phrase restores your identity on a new device. Keep it private.",
                        color = Color.Gray,
                    )
                    Text(
                        when {
                            backupCode == null -> "No backup phrase is stored on this device."
                            revealBackup -> backupCode?.let(BackupCode::format).orEmpty()
                            else -> "Tap reveal to view your saved backup phrase."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (revealBackup && backupCode != null) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { revealBackup = !revealBackup }, enabled = backupCode != null) {
                            Text(if (revealBackup) "Hide" else "Reveal")
                        }
                        TextButton(
                            onClick = {
                                val userId = com.fortrx.Settings.myId ?: return@TextButton
                                exportBackupLauncher.launch("fortrx-backup-$userId.zip")
                            },
                            enabled = !backupBusy,
                        ) {
                            Text(if (backupBusy) "Backing up..." else "Backup zip")
                        }
                        TextButton(
                            onClick = { importBackupLauncher.launch(arrayOf("application/zip", "*/*")) },
                            enabled = !backupBusy,
                        ) {
                            Text("Restore zip")
                        }
                    }
                }
            }

            Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFF7F7F7)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Incoming messages generate alerts while the app sync engine is active.", color = Color.Gray)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, null, tint = Color(0xFF0A84FF))
                        Spacer(Modifier.width(8.dp))
                        Text("Foreground sync notification removed")
                    }
                }
            }

            if (isDevBuild) {
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFF7F7F7)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Developer diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Inspect local keys and ratchet state in the dev build only.", color = Color.Gray)
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val password = com.fortrx.Settings.storagePassword ?: return@launch
                                    val userId = com.fortrx.Settings.myId ?: return@launch
                                    devDiagnostics = buildDeveloperDiagnostics(password, userId)
                                }
                            },
                        ) {
                            Text("Open diagnostics")
                        }
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    FortrxClient.logout()
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF2F2F2)),
            ) {
                Text("Log out", color = Color.Black)
            }

            Button(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE)),
            ) {
                Text("Delete account", color = Color(0xFFD32F2F))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter your password to confirm permanent account deletion.")
                    OutlinedTextField(
                        value = deletePassword,
                        onValueChange = { deletePassword = it; error = null },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            deleting = true
                            try {
                                OnboardingService.deleteAccount(deletePassword)
                                onLogout()
                            } catch (t: Throwable) {
                                error = t.message ?: "Delete failed"
                            } finally {
                                deleting = false
                                showDeleteDialog = false
                                deletePassword = ""
                            }
                        }
                    },
                    enabled = deletePassword.isNotBlank() && !deleting,
                ) {
                    if (deleting) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = {
                showRestoreDialog = false
                restoreUri = null
            },
            title = { Text("Restore backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select the 30-36 digit backup code that was saved with the zip.")
                    OutlinedTextField(
                        value = restoreCode,
                        onValueChange = { restoreCode = it.filter(Char::isDigit).take(36) },
                        label = { Text("Backup code") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val password = com.fortrx.Settings.storagePassword ?: return@launch
                            val userId = com.fortrx.Settings.myId ?: return@launch
                            val uri = restoreUri ?: return@launch
                            backupBusy = true
                            error = null
                            try {
                                restoreLocalBackup(context, uri, restoreCode.trim(), password, userId)
                                backupCode = SettingsStore.loadBackupCode()
                                revealBackup = false
                                showRestoreDialog = false
                                restoreUri = null
                            } catch (t: Throwable) {
                                error = t.message ?: "Restore failed"
                            } finally {
                                backupBusy = false
                            }
                        }
                    },
                    enabled = restoreCode.length in 30..36 && !backupBusy,
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestoreDialog = false
                    restoreUri = null
                }) { Text("Cancel") }
            },
        )
    }

    if (devDiagnostics != null) {
        AlertDialog(
            onDismissRequest = { devDiagnostics = null },
            title = { Text("Developer diagnostics") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(devDiagnostics!!, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { devDiagnostics = null }) { Text("Close") }
            },
        )
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun readAttachmentFromUri(context: android.content.Context, uri: Uri): AttachmentPayload {
    val resolver = context.contentResolver
    val meta = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else "attachment"
            val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
            name to size
        } else {
            "attachment" to -1L
        }
    } ?: ("attachment" to -1L)

    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Could not read attachment")
    require(bytes.isNotEmpty()) { "Attachment is empty" }
    require(bytes.size <= MAX_INLINE_ATTACHMENT_BYTES) {
        "Attachment must be ${ChatPayloadCodec.formatSize(MAX_INLINE_ATTACHMENT_BYTES)} or smaller"
    }
    val mimeType = resolver.getType(uri) ?: "application/octet-stream"
    return AttachmentPayload(
        fileName = meta.first,
        mimeType = mimeType,
        sizeBytes = bytes.size,
        dataBase64 = Base64.encode(bytes),
    )
}

private fun containsLink(text: String): Boolean =
    "(https?://\\S+)".toRegex(RegexOption.IGNORE_CASE).containsMatchIn(text)

@OptIn(ExperimentalEncodingApi::class)
private fun openPayload(context: android.content.Context, raw: String?) {
    when (val payload = ChatPayloadCodec.decode(raw)) {
        is ChatPayload.Text -> {
            val match = "(https?://\\S+)".toRegex(RegexOption.IGNORE_CASE).find(payload.text)?.value ?: return
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(match)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
        is ChatPayload.Attachment -> openAttachment(context, payload.attachment)
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun openAttachment(context: android.content.Context, attachment: AttachmentPayload) {
    val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
    val target = File(sharedDir, attachment.fileName.ifBlank { "attachment" })
    target.writeBytes(Base64.decode(attachment.dataBase64))
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, attachment.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        val fallback = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(fallback)
    }
}

private fun dbNameForUser(userId: Long): String = "fortrx_$userId.db"

private suspend fun exportLocalBackup(
    context: android.content.Context,
    outputUri: Uri,
    password: String,
    userId: Long,
    backupCode: String,
) {
    val dbName = dbNameForUser(userId)
    val dbFile = context.getDatabasePath(dbName)
    require(dbFile.exists()) { "No local history was found to back up." }

    FortrxClient.stopSyncEngine()
    Db.close()
    try {
        context.contentResolver.openOutputStream(outputUri)?.use { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("metadata.txt"))
                zip.write(
                    buildString {
                        appendLine("userId=$userId")
                        appendLine("dbName=$dbName")
                        appendLine("backupCode=$backupCode")
                    }.encodeToByteArray()
                )
                zip.closeEntry()

                zip.putNextEntry(ZipEntry("database.db"))
                dbFile.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        } ?: error("Could not create backup file")
    } finally {
        Db.open(password, userId)
        FortrxClient.startSyncEngine(password)
    }
}

private suspend fun restoreLocalBackup(
    context: android.content.Context,
    zipUri: Uri,
    backupCode: String,
    password: String,
    userId: Long,
) {
    require(backupCode.length in 30..36) { "Backup code must be 30-36 digits" }
    val dbName = dbNameForUser(userId)
    val dbFile = context.getDatabasePath(dbName)
    val tempDb = File(context.cacheDir, "restore-$dbName")
    var expectedCode: String? = null

    context.contentResolver.openInputStream(zipUri)?.use { input ->
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when (entry.name) {
                    "metadata.txt" -> {
                        val metadata = zip.readBytes().decodeToString()
                        expectedCode = metadata.lineSequence()
                            .firstOrNull { it.startsWith("backupCode=") }
                            ?.substringAfter("=")
                            ?.trim()
                    }
                    "database.db" -> {
                        tempDb.outputStream().use { zip.copyTo(it) }
                    }
                }
                zip.closeEntry()
            }
        }
    } ?: error("Could not read backup file")

    require(expectedCode == backupCode) { "Backup code did not match this zip" }
    require(tempDb.exists()) { "Backup zip did not contain a database" }

    FortrxClient.stopSyncEngine()
    Db.close()
    try {
        dbFile.parentFile?.mkdirs()
        tempDb.copyTo(dbFile, overwrite = true)
        SettingsStore.saveBackupCode(backupCode)
        MessagingService.resetCaches()
        Db.open(password, userId)
    } finally {
        tempDb.delete()
        FortrxClient.startSyncEngine(password)
    }
}

private suspend fun buildDeveloperDiagnostics(password: String, userId: Long): String {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val keys = Keystore.loadKeys(password, userId)?.toString() ?: "No local key material"
    val sessions = Db.loadSessionsMap(password)
    val sessionSummary = if (sessions.isEmpty()) {
        "No ratchet sessions"
    } else {
        sessions.entries.joinToString("\n\n") { (contactId, stateJson) ->
            val state = runCatching { json.decodeFromString(RatchetState.serializer(), stateJson) }.getOrNull()
            buildString {
                appendLine("contactId=$contactId")
                if (state != null) {
                    appendLine("sendCount=${state.sendCount}")
                    appendLine("recvCount=${state.recvCount}")
                    appendLine("previousSendCount=${state.previousSendCount}")
                    appendLine("hasSendingChain=${state.sendingChainKey != null}")
                    appendLine("hasRecvChain=${state.recvChainKey != null}")
                    appendLine("skippedKeys=${state.skippedMessageKeys.size}")
                }
                appendLine("rawSession=$stateJson")
            }
        }
    }
    return buildString {
        appendLine("== local_keys ==")
        appendLine(keys)
        appendLine()
        appendLine("== ratchet_sessions ==")
        appendLine(sessionSummary)
    }
}
