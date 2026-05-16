package com.fortrx.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fortrx.services.MessagingService
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.koin.compose.koinInject

@Composable
fun NewChatDialog(
    onDismiss: () -> Unit,
    onStart: (Long) -> Unit
) {
    val messagingService = koinInject<MessagingService>()
    var usernameText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start New Conversation") },
        text = {
            Column {
                Text("Enter the username of the contact you want to message.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = usernameText,
                    onValueChange = { usernameText = it; error = null },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } }
                )
            }
        },
        confirmButton = {
            Button(
                enabled = usernameText.isNotBlank() && !loading,
                onClick = {
                    val username = usernameText.trim()
                    scope.launch {
                        loading = true
                        try {
                            val user = messagingService.getUserByUsername(username)
                            val id = user["id"]?.jsonPrimitive?.long
                                ?: error("Missing user id")
                            onStart(id)
                            onDismiss()
                        } catch (e: Exception) {
                            error = "User not found or error: ${e.message}"
                        } finally {
                            loading = false
                        }
                    }
                }
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Start Chat")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
