package com.fortrx.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fortrx.services.VerificationService
import kotlinx.coroutines.launch

@Composable
fun SafetyNumberDialog(
    otherUserId: Long,
    otherUsername: String,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var safetyNumber by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val pw = com.fortrx.Settings.storagePassword

    LaunchedEffect(otherUserId) {
        if (pw != null) {
            try {
                val res = VerificationService.getSafetyNumber(pw, otherUserId)
                safetyNumber = res.safetyNumber
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify with $otherUsername") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Compare these numbers with your contact to verify the encryption.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                if (loading) {
                    CircularProgressIndicator()
                } else {
                    Surface(
                        color = Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            safetyNumber ?: "Failed to load",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (safetyNumber != null) {
                    VerificationService.markVerified(otherUserId, safetyNumber!!)
                }
                onDismiss()
            }) {
                Text("Numbers Match")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
