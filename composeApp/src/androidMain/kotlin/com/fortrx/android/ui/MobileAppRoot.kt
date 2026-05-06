package com.fortrx.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fortrx.FortrxClient
import com.fortrx.services.OnboardingService
import com.fortrx.services.MessagingService
import com.fortrx.services.VerificationService
import com.fortrx.services.BackupCode
import kotlinx.coroutines.launch
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonPrimitive

private enum class Screen { Onboarding, ChatList, Chat, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileAppRoot() {
    var screen by remember { mutableStateOf(Screen.Onboarding) }
    var current by remember { mutableStateOf<String?>(null) }
    
    val mengobrolColorScheme = lightColorScheme(
        primary = Color(0xFFFFC107),
        onPrimary = Color.Black,
        secondary = Color(0xFF212121),
        surface = Color(0xFFFFFFFF),
        background = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF2F2F2),
        onSurfaceVariant = Color.DarkGray,
        error = Color(0xFFD32F2F)
    )

    MaterialTheme(
        colorScheme = mengobrolColorScheme,
        typography = Typography()
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (screen) {
                Screen.Onboarding -> MobileOnboardingScreen(onDone = { screen = Screen.ChatList })
                Screen.ChatList -> ChatListScreen(
                    onOpen = { current = it; screen = Screen.Chat },
                    onSettings = { screen = Screen.Settings }
                )
                Screen.Chat -> ChatScreen(id = current ?: "", onBack = { screen = Screen.ChatList })
                Screen.Settings -> MobileSettingsScreen(onBack = { screen = Screen.ChatList }, onLogout = { screen = Screen.Onboarding })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MobileOnboardingScreen(onDone: () -> Unit) {
    var isRegister by remember { mutableStateOf(false) }
    var u by remember { mutableStateOf("") }
    var e by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pLengthOk = p.length >= 8
    val uNotEmpty = u.isNotBlank()
    val eNotEmpty = !isRegister || e.isNotBlank()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "The best secure\nmessaging in the\nworld with a fun\nconcept",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
            ),
            color = Color.Black
        )
        
        Spacer(Modifier.height(48.dp))
        
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = u,
            onValueChange = { u = it; error = null },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.LightGray
            )
        )
        
        if (isRegister) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = e,
                onValueChange = { e = it; error = null },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.LightGray
                )
            )
        }
        
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = p,
            onValueChange = { p = it; error = null },
            label = { Text("Storage password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.LightGray
            )
        )
        
        Spacer(Modifier.height(48.dp))
        
        if (loading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Button(
                onClick = {
                    scope.launch {
                        loading = true; error = null
                        try {
                            if (isRegister) OnboardingService.register(u, e, p)
                            else OnboardingService.login(u, p)
                            FortrxClient.startSyncEngine(p)
                            onDone()
                        } catch (t: Throwable) {
                            error = t.message ?: "Action failed"
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                enabled = uNotEmpty && pLengthOk && eNotEmpty,
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Text(
                        if (isRegister) "Create account" else "Let's chat now",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    )
                    Surface(
                        shape = CircleShape,
                        color = Color.Black,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { isRegister = !isRegister; error = null }) {
                    Text(
                        if (isRegister) "Already have an account? Sign in" else "Need an account? Register",
                        color = Color.DarkGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ChatListScreen(onOpen: (String) -> Unit, onSettings: () -> Unit) {
    var items by remember { mutableStateOf(emptyList<com.fortrx.storage.Db.ConversationSummary>()) }
    val scope = rememberCoroutineScope()
    val pw = com.fortrx.Settings.storagePassword

    LaunchedEffect(Unit) {
        if (pw != null) {
            while (true) {
                items = com.fortrx.storage.Db.listConversationSummaries(pw)
                kotlinx.coroutines.delay(3000)
            }
        }
    }

    var showNewChat by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { 
            TopAppBar(
                title = { 
                    Text(
                        "Messages", 
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    ) 
                }, 
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, null) }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            ) 
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewChat = true },
                containerColor = Color.Black,
                contentColor = Color.White,
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("New Chat")
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().background(Color.White)) {
            items(items) { summary ->
                val id = summary.contactId.toString()
                val title = com.fortrx.storage.Db.getContact(summary.contactId)?.username ?: "User $id"
                Column {
                    ListItem(
                        modifier = Modifier.clickable { onOpen(id) }.padding(vertical = 4.dp),
                        leadingContent = {
                            Surface(
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                                color = Color(0xFFEEEEEE)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        title.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                        color = Color.Black
                                    )
                                }
                            }
                        },
                        headlineContent = { 
                            Text(
                                title, 
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            ) 
                        },
                        supportingContent = { 
                            Text(summary.lastMessagePreview ?: "", maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = Color.Gray) 
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(summary.lastMessageAt?.takeLast(8)?.take(5) ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Spacer(Modifier.height(4.dp))
                                if (summary.unreadCount > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape,
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(summary.unreadCount.toString(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Color.Black)
                                        }
                                    }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.White)
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color(0xFFEEEEEE))
                }
            }
        }
    }

    if (showNewChat) {
        var usernameInput by remember { mutableStateOf("") }
        var usernameError by remember { mutableStateOf<String?>(null) }
        var usernameLoading by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showNewChat = false },
            title = { Text("New Chat") },
            text = {
                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = { usernameInput = it; usernameError = null },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = usernameError != null,
                    supportingText = { usernameError?.let { Text(it) } }
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (usernameInput.isNotBlank() && !usernameLoading) {
                        scope.launch {
                            usernameLoading = true
                            try {
                                val user = MessagingService.getUserByUsername(usernameInput.trim())
                                val id = user["id"]?.jsonPrimitive?.content ?: error("Missing user id")
                                onOpen(id)
                                showNewChat = false
                            } catch (e: Exception) {
                                usernameError = e.message ?: "Username not found"
                            } finally {
                                usernameLoading = false
                            }
                        }
                    }
                }, enabled = usernameInput.isNotBlank() && !usernameLoading) {
                    Text(if (usernameLoading) "Loading..." else "Start")
                }
            },
            dismissButton = { TextButton(onClick = { showNewChat = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ChatScreen(id: String, onBack: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    val msgs = remember(id) { mutableStateListOf<com.fortrx.storage.Db.StoredMessage>() }
    val scope = rememberCoroutineScope()
    val pw = com.fortrx.Settings.storagePassword
    var showVerify by remember { mutableStateOf(false) }
    var contactName by remember(id) { mutableStateOf("User $id") }
    var onlineState by remember(id) { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (pw != null) {
            while (true) {
                val contact = com.fortrx.storage.Db.getContact(id.toLong())
                contactName = contact?.username ?: "User $id"
                onlineState = contact?.isOnline == true
                val stored = com.fortrx.storage.Db.listConversation(pw, id.toLong())
                msgs.clear()
                msgs.addAll(stored.reversed())
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = Color(0xFFEEEEEE)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(contactName.take(1).uppercase(), style = MaterialTheme.typography.titleSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(contactName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                            Text(
                                if (onlineState) "Online" else "Offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (onlineState) Color(0xFF4CAF50) else Color.Gray
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBackIosNew, null, modifier = Modifier.size(20.dp)) } },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Default.Videocam, null) }
                    IconButton(onClick = {}) { Icon(Icons.Default.Call, null) }
                    IconButton(onClick = { showVerify = true }) { Icon(Icons.Default.VerifiedUser, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            ) 
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(Color.White)) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                reverseLayout = false
            ) {
                item { Spacer(Modifier.height(16.dp)) }
                items(msgs) { m ->
                    val mine = m.direction == "outgoing"
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
                    ) {
                        Surface(
                            color = if (mine) MaterialTheme.colorScheme.primary else Color(0xFFF2F2F2),
                            shape = RoundedCornerShape(
                                topStart = 20.dp, 
                                topEnd = 20.dp, 
                                bottomStart = if (mine) 20.dp else 4.dp, 
                                bottomEnd = if (mine) 4.dp else 20.dp
                            )
                        ) { 
                            Text(
                                m.plaintext ?: "", 
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Black
                            ) 
                        }
                    }
                }
            }
            Surface(
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {}) { Icon(Icons.Default.Add, null, tint = Color.Gray) }
                    TextField(
                        value = draft, 
                        onValueChange = { draft = it }, 
                        modifier = Modifier.weight(1f), 
                        placeholder = { Text("Type here...", color = Color.Gray) },
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF2F2F2),
                            unfocusedContainerColor = Color(0xFFF2F2F2),
                            disabledContainerColor = Color(0xFFF2F2F2),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (draft.isNotBlank() && pw != null) {
                                scope.launch {
                                    try {
                                        val myId = com.fortrx.Settings.myId ?: com.fortrx.network.AuthApi.getMe()["id"]?.jsonPrimitive?.long ?: 0L
                                        MessagingService.sendText(pw, myId, id.toLong(), draft)
                                        draft = ""
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(48.dp).background(if (draft.isNotBlank()) MaterialTheme.colorScheme.primary else Color(0xFFF2F2F2), CircleShape)
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
    }

    if (showVerify) {
        var safetyNumber by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            if (pw != null) {
                safetyNumber = VerificationService.getSafetyNumber(pw, id.toLong()).safetyNumber
            }
        }
        AlertDialog(
            onDismissRequest = { showVerify = false },
            title = { Text("Safety Number") },
            text = { Text(safetyNumber ?: "Loading...") },
            confirmButton = {
                Button(onClick = {
                    if (safetyNumber != null) VerificationService.markVerified(id.toLong(), safetyNumber!!)
                    showVerify = false
                }) { Text("Verify") }
            },
            dismissButton = { TextButton(onClick = { showVerify = false }) { Text("Close") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun MobileSettingsScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    var showBackup by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            ) 
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(16.dp), 
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Privacy & Security", 
                style = MaterialTheme.typography.titleMedium, 
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            SettingsItem(
                title = "Backup Phrase", 
                subtitle = "View your recovery phrase", 
                icon = Icons.Default.Key,
                onClick = { showBackup = BackupCode.generate() }
            )
            
            SettingsItem(
                title = "Storage Password", 
                subtitle = "Change your local encryption password", 
                icon = Icons.Default.Lock,
                onClick = {}
            )
            
            SettingsItem(
                title = "Linked Devices", 
                subtitle = "Manage your other active devices", 
                icon = Icons.Default.Devices,
                onClick = {}
            )

            SettingsItem(
                title = "Purge Inbox",
                subtitle = "Drain server-side messages",
                icon = Icons.Default.CleaningServices,
                onClick = {
                    val pw = com.fortrx.Settings.storagePassword
                    if (pw != null) {
                        scope.launch {
                            try {
                                val myId = com.fortrx.Settings.myId ?: com.fortrx.network.AuthApi.getMe()["id"]?.jsonPrimitive?.long ?: 0L
                                MessagingService.purgeInbox(pw, myId, true)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Text(
                "Account", 
                style = MaterialTheme.typography.titleMedium, 
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            SettingsItem(
                title = "Log Out", 
                subtitle = "Sign out from this device", 
                icon = Icons.AutoMirrored.Filled.Logout,
                onClick = onLogout
            )
            
            SettingsItem(
                title = "Delete Account", 
                subtitle = "Permanently wipe all data", 
                icon = Icons.Default.DeleteForever,
                isError = true,
                onClick = { confirmDelete = true }
            )
        }
    }
    
    showBackup?.let { code ->
        AlertDialog(
            onDismissRequest = { showBackup = null },
            confirmButton = { Button(onClick = { showBackup = null }) { Text("Got it") } },
            title = { Text("Your Backup Phrase") },
            text = { 
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        BackupCode.format(code), 
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        )
    }
    
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = { 
                Button(
                    onClick = { confirmDelete = false; onLogout() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Forever") } 
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            title = { Text("Are you sure?") },
            text = { Text("This action cannot be undone. All your messages and keys will be permanently deleted.") }
        )
    }
}

@Composable
private fun SettingsItem(
    title: String, 
    subtitle: String, 
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    isError: Boolean = false,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        ListItem(
            headlineContent = { 
                Text(
                    title, 
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                ) 
            },
            supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
            leadingContent = { 
                Icon(
                    icon, 
                    contentDescription = null,
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ) 
            },
            trailingContent = { Icon(Icons.Default.ChevronRight, null) }
        )
    }
}
