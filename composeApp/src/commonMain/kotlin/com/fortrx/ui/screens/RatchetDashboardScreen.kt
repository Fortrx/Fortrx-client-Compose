package com.fortrx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.fortrx.Settings
import com.fortrx.crypto.RatchetState
import com.fortrx.storage.Db
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlin.io.encoding.ExperimentalEncodingApi

class RatchetDashboardScreen(val contactId: Long) : Screen {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalEncodingApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        var sessionBlob by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(contactId) {
            sessionBlob = Db.loadSessionBlob(Settings.storagePassword ?: "", contactId)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Security Dashboard", fontWeight = FontWeight.ExtraBold) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        ) { padding ->
            val blob = sessionBlob
            if (blob == null) {
                Box(
                    Modifier.padding(padding).fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Lock, 
                            contentDescription = null, 
                            modifier = Modifier.size(48.dp), 
                            tint = Color.Gray
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("No active session found for this contact.", color = Color.Gray)
                    }
                }
                return@Scaffold
            }

            val state = remember(blob) {
                try {
                    Json { ignoreUnknownKeys = true }.decodeFromString<RatchetState>(blob)
                } catch (_: Exception) {
                    null
                }
            }

            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    "Double Ratchet Protocol",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "State overview for contact #$contactId",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(Modifier.height(24.dp))

                if (state != null) {
                    // Status Header
                    SessionStatusCard(state)

                    Spacer(Modifier.height(16.dp))

                    // Message Counters
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CounterCard("Sent", state.sendCount, Modifier.weight(1f), MaterialTheme.colorScheme.primaryContainer)
                        CounterCard("Received", state.recvCount, Modifier.weight(1f), MaterialTheme.colorScheme.secondaryContainer)
                    }

                    Spacer(Modifier.height(16.dp))

                    // Keys and Parameters
                    RatchetCard("Handshake Parameters") {
                        ParameterRow("Root Key", state.rootKey.toHex(8))
                        ParameterRow("DH Public", state.dhSendingPublic.toHex(8))
                        ParameterRow("Remote DH", state.dhRemotePublic?.toHex(8) ?: "Unknown")
                        ParameterRow("Skipped Keys", state.skippedMessageKeys.size.toString())
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    RatchetCard("Advanced (Raw Data)") {
                        Text(
                            blob,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            maxLines = 5
                        )
                    }
                } else {
                    Text("Error parsing session state.", color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(24.dp))
                InfoBox("Double Ratchet provides end-to-end encryption with perfect forward secrecy and post-compromise security.")
            }
        }
    }

    @Composable
    private fun SessionStatusCard(state: RatchetState) {
        val isActive = state.dhRemotePublic != null
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (isActive) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (isActive) Color(0xFF4CAF50) else Color(0xFFFF9800))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (isActive) "Encrypted Session Active" else "Handshake Incomplete",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color(0xFF2E7D32) else Color(0xFFE65100)
                )
            }
        }
    }

    @Composable
    private fun CounterCard(label: String, count: Int, modifier: Modifier = Modifier, containerColor: Color) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(count.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    @Composable
    private fun ParameterRow(label: String, value: String) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
        }
    }

    @Composable
    private fun InfoBox(text: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Info, 
                contentDescription = null, 
                modifier = Modifier.size(20.dp), 
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    private fun RatchetCard(title: String, content: @Composable () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }

    private fun ByteArray.toHex(limit: Int = -1): String {
        val hex = joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
        return if (limit > 0 && hex.length > limit) hex.take(limit) + "..." else hex
    }
}
