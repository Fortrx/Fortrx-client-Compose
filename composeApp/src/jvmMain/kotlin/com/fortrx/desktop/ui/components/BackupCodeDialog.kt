package com.fortrx.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fortrx.services.BackupCode

@Composable
fun BackupCodeDialog(code: String, onConfirm: () -> Unit) {
    var confirmed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Save your backup phrase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Write this down. It is the only way to recover your account on a new device.")
                Surface(tonalElevation = 2.dp) {
                    Text(BackupCode.format(code), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(confirmed, { confirmed = it })
                    Text("I've written down my backup phrase")
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = confirmed) { Text("Continue") } }
    )
}
