package com.fortrx.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.fortrx.services.BackupCode
import com.fortrx.platform.BinaryDocument
import com.fortrx.platform.rememberOpenBytesLauncher
import com.fortrx.platform.rememberSaveBytesLauncher
import kotlinx.coroutines.launch

class SettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<SettingsScreenModel>()
        val username by screenModel.username.collectAsState()
        val userId by screenModel.userId.collectAsState()
        val backupCode by screenModel.backupCode.collectAsState()
        val isBusy by screenModel.isBusy.collectAsState()

        var showLogoutDialog by remember { mutableStateOf(false) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        var pendingBackupCode by remember { mutableStateOf<String?>(null) }
        var pendingRestoreFile by remember { mutableStateOf<BinaryDocument?>(null) }
        var restoreCode by remember { mutableStateOf("") }
        var showRestoreDialog by remember { mutableStateOf(false) }
        var deletePassword by remember { mutableStateOf("") }
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val saveLauncher = rememberSaveBytesLauncher(
            onSaved = {
                pendingBackupCode?.let(screenModel::revealBackupCode)
                pendingBackupCode = null
            },
            onError = {
                pendingBackupCode = null
                scope.launch { snackbarHostState.showSnackbar(it) }
            }
        )
        val openLauncher = rememberOpenBytesLauncher(
            onOpened = {
                pendingRestoreFile = it
                showRestoreDialog = true
            },
            onError = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }
        )
        
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                MediumTopAppBar(
                    title = { 
                        Text(
                            "Settings", 
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            LaunchedEffect(Unit) {
                screenModel.effects.collect { effect ->
                    when (effect) {
                        is SettingsScreenModel.Effect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                        is SettingsScreenModel.Effect.SaveBackup -> {
                            pendingBackupCode = effect.result.code
                            saveLauncher.launch(effect.result.fileName, effect.result.archiveBytes)
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
            ) {
                // Profile Section
                ProfileHeader(username, userId)

                Spacer(Modifier.height(24.dp))

                // Security Section
                SectionHeader("SECURITY")
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = "Backup Code",
                        subtitle = "Shown briefly after a backup is saved",
                        onClick = {},
                    )
                    
                    AnimatedVisibility(
                        visible = !backupCode.isNullOrBlank(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(16.dp)
                        ) {
                            Text(
                                "Recovery Code:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                BackupCode.format(backupCode ?: ""),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    SettingsItem(
                        icon = Icons.Default.Download,
                        title = "Create Encrypted Backup",
                        subtitle = "Export messages, media, keys, and local state to a zip",
                        onClick = { screenModel.exportBackup() }
                    )
                    SettingsItem(
                        icon = Icons.Default.UploadFile,
                        title = "Restore from Backup",
                        subtitle = "Import a backup zip and merge it into this device",
                        onClick = { openLauncher.launch() }
                    )
                    
                    var mediaVisible by remember { mutableStateOf(false) } // TODO: Sync with Settings
                    SettingsItem(
                        icon = Icons.Default.PhotoLibrary,
                        title = "Media Visibility",
                        subtitle = "Show media in phone gallery",
                        trailing = {
                            Switch(
                                checked = mediaVisible,
                                onCheckedChange = { mediaVisible = it }
                            )
                        },
                        onClick = { mediaVisible = !mediaVisible }
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Account Section
                SectionHeader("ACCOUNT")
                SettingsCard {
                    SettingsItem(
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        title = "Log Out",
                        subtitle = "Sign out of this device",
                        onClick = { showLogoutDialog = true }
                    )
                    SettingsItem(
                        icon = Icons.Default.Delete,
                        title = "Delete Account",
                        subtitle = "Permanently remove your account",
                        contentColor = MaterialTheme.colorScheme.error,
                        onClick = { showDeleteDialog = true }
                    )
                }

                Spacer(Modifier.height(32.dp))
                
                Text(
                    "Fortrx v1.0.0",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = { Text("Log Out?", fontWeight = FontWeight.Bold) },
                text = { Text("You will need your password and a backup archive plus code if local data is cleared.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showLogoutDialog = false
                            screenModel.logout()
                            navigator.replaceAll(MainScreen())
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Log Out", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Account?", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("This action is permanent and will delete all your data from our servers. You cannot undo this.")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = deletePassword,
                            onValueChange = { deletePassword = it },
                            singleLine = true,
                            label = { Text("Confirm password") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            screenModel.deleteAccount(deletePassword) {
                                deletePassword = ""
                                navigator.replaceAll(OnboardingScreen())
                            }
                        },
                        enabled = deletePassword.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Everything", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false; deletePassword = "" }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showRestoreDialog && pendingRestoreFile != null) {
            AlertDialog(
                onDismissRequest = {
                    showRestoreDialog = false
                    restoreCode = ""
                    pendingRestoreFile = null
                },
                title = { Text("Restore Backup") },
                text = {
                    Column {
                        Text("Selected file: ${pendingRestoreFile!!.displayName}")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = restoreCode,
                            onValueChange = { restoreCode = it },
                            label = { Text("36-digit backup code") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isBusy,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val file = pendingRestoreFile ?: return@Button
                            screenModel.restoreBackup(file.displayName, file.bytes, restoreCode)
                            showRestoreDialog = false
                            restoreCode = ""
                            pendingRestoreFile = null
                        },
                        enabled = BackupCode.isValid(restoreCode) && !isBusy
                    ) {
                        Text("Restore")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showRestoreDialog = false
                        restoreCode = ""
                        pendingRestoreFile = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    @Composable
    private fun ProfileHeader(username: String, userId: String) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color.Black, Color(0xFF333333))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    username.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                username,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )
            
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    "ID: $userId",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun SectionHeader(title: String) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }

    @Composable
    private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
        ElevatedCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(content = content)
        }
    }

    @Composable
    private fun SettingsItem(
        icon: ImageVector,
        title: String,
        subtitle: String,
        contentColor: Color = MaterialTheme.colorScheme.onSurface,
        trailing: @Composable (() -> Unit)? = null,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = contentColor.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold, 
                    color = contentColor
                )
                Text(
                    subtitle, 
                    style = MaterialTheme.typography.bodySmall, 
                    color = Color.Gray
                )
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}
