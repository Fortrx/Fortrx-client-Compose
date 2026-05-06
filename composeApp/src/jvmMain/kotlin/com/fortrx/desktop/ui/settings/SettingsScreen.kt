package com.fortrx.desktop.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fortrx.services.BackupCode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                listOf("Profile", "Privacy", "Linked devices", "Account").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            Box(Modifier.padding(24.dp)) {
                when (tab) {
                    0 -> ProfileTab()
                    1 -> PrivacyTab()
                    2 -> LinkedDevicesTab()
                    3 -> AccountTab(onLogout = onClose)
                }
            }
        }
    }
}

@Composable private fun ProfileTab() {
    Column { Text("Profile", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(12.dp)); Text("Display name, avatar, status — coming soon.") }
}

@Composable private fun PrivacyTab() {
    var oldP by remember { mutableStateOf("") }; var newP by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }
    var showBackup by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Change storage password", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(oldP, { oldP = it }, label = { Text("Current password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        OutlinedTextField(newP, { newP = it }, label = { Text("New password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        OutlinedTextField(confirm, { confirm = it }, label = { Text("Confirm new password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Button(onClick = { /* call AccountService.changeStoragePassword */ }, enabled = newP.length >= 8 && newP == confirm) { Text("Update password") }

        Divider(Modifier.padding(vertical = 12.dp))

        Text("Backup phrase", style = MaterialTheme.typography.titleMedium)
        Text("Used to restore your identity on a new device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { showBackup = BackupCode.generate() }) { Text("Generate / show backup phrase") }
    }
    showBackup?.let { code ->
        AlertDialog(
            onDismissRequest = { showBackup = null },
            confirmButton = { TextButton(onClick = { showBackup = null }) { Text("I've written it down") } },
            title = { Text("Backup phrase") },
            text = { Text(BackupCode.format(code), style = MaterialTheme.typography.headlineSmall) }
        )
    }
}

@Composable private fun LinkedDevicesTab() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Linked devices", style = MaterialTheme.typography.titleLarge)
        ListItem(headlineContent = { Text("This device — Desktop") }, supportingContent = { Text("Active now") })
        ListItem(headlineContent = { Text("Phone") }, supportingContent = { Text("Last seen 2h ago") }, trailingContent = { TextButton(onClick = {}) { Text("Revoke") } })
        Spacer(Modifier.height(12.dp))
        Button(onClick = { /* link new */ }) { Text("Link a new device") }
    }
}

@Composable private fun AccountTab(onLogout: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Account", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = onLogout) { Text("Log out") }
        Button(onClick = { confirmDelete = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete account") }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onLogout() }) { Text("Delete forever") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            title = { Text("Delete account?") },
            text = { Text("This wipes all messages, keys, and devices. It cannot be undone.") }
        )
    }
}
