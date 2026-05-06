package com.fortrx.desktop.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LeftRail(onSettings: () -> Unit, onLogout: () -> Unit) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        IconButton(onClick = {}) { Icon(Icons.Default.Person, contentDescription = "Profile") }
        IconButton(onClick = {}) { Icon(Icons.Default.ChatBubble, contentDescription = "Chats") }
        IconButton(onClick = {}) { Icon(Icons.Default.Devices, contentDescription = "Linked devices") }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        IconButton(onClick = onLogout) { Icon(Icons.Default.Logout, contentDescription = "Logout") }
        Spacer(Modifier.height(12.dp))
    }
}
