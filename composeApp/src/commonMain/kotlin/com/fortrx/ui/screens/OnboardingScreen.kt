package com.fortrx.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.fortrx.services.BackupCode
import com.fortrx.platform.BinaryDocument
import com.fortrx.platform.rememberOpenBytesLauncher
import fortrxclient.composeapp.generated.resources.Res
import fortrxclient.composeapp.generated.resources.bg_home_steampunk
import org.jetbrains.compose.resources.painterResource

enum class OnboardingMode { Login, Register, Restore }

class OnboardingScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<OnboardingScreenModel>()
        val uiState by screenModel.uiState.collectAsState()

        var mode by remember { mutableStateOf(OnboardingMode.Login) }
        var username by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var backupCode by remember { mutableStateOf("") }
        var selectedBackup by remember { mutableStateOf<BinaryDocument?>(null) }
        val backupPicker = rememberOpenBytesLauncher(
            onOpened = { selectedBackup = it; screenModel.resetState() },
            onError = { screenModel.resetState() }
        )

        val loading = uiState is OnboardingScreenModel.OnboardingState.Loading
        val error = (uiState as? OnboardingScreenModel.OnboardingState.Error)?.message

        val canSubmit = username.isNotBlank() &&
                password.length >= 8 &&
                (mode != OnboardingMode.Register || email.isNotBlank()) &&
                (mode != OnboardingMode.Restore || (selectedBackup != null && BackupCode.isValid(backupCode))) &&
                !loading

        LaunchedEffect(uiState) {
            if (uiState is OnboardingScreenModel.OnboardingState.Success) {
                navigator.replaceAll(ChatListScreen())
            }
        }

        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(Res.drawable.bg_home_steampunk),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.1f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFF4EEE2).copy(alpha = 0.96f),
                                Color(0xFFE4D6BE).copy(alpha = 0.9f),
                                Color(0xFFD9C6A5).copy(alpha = 0.94f)
                            )
                        )
                    )
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                ) {
                    Text(
                        "Private messaging\nbuilt for real-time\nconversations",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(32.dp))

                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it.lowercase(); screenModel.resetState() },
                        label = { Text("Username") },
                        singleLine = true,
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        )
                    )

                    if (mode == OnboardingMode.Register) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; screenModel.resetState() },
                            label = { Text("Email") },
                            singleLine = true,
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            )
                        )
                    }

                    if (mode == OnboardingMode.Restore) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { backupPicker.launch() },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(selectedBackup?.displayName ?: "Choose backup zip")
                        }
                        if (selectedBackup != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Selected: ${selectedBackup!!.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = backupCode,
                            onValueChange = { backupCode = it; screenModel.resetState() },
                            label = { Text("Backup code (30-36 digits)") },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            )
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; screenModel.resetState() },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !loading,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        )
                    )

                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            when (mode) {
                                OnboardingMode.Register -> screenModel.register(username.trim(), email.trim(), password)
                                OnboardingMode.Login -> screenModel.login(username.trim(), password)
                                OnboardingMode.Restore -> selectedBackup?.let {
                                    screenModel.restore(username.trim(), password, it.displayName, it.bytes, backupCode.trim())
                                }
                            }
                        },
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(
                                when (mode) {
                                    OnboardingMode.Register -> "Create account"
                                    OnboardingMode.Restore -> "Restore account"
                                    else -> "Log in"
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton(
                            onClick = {
                                mode = if (mode == OnboardingMode.Login) OnboardingMode.Register else OnboardingMode.Login
                                screenModel.resetState()
                            },
                        ) {
                            Text(
                                if (mode == OnboardingMode.Login) "Sign up" else "Log in",
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (mode != OnboardingMode.Restore) {
                            TextButton(onClick = { mode = OnboardingMode.Restore; screenModel.resetState() }) {
                                Text("Restore from backup", color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
