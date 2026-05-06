package com.fortrx.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fortrx.services.OnboardingService
import com.fortrx.services.BackupCode
import kotlinx.coroutines.launch

// Internal components to reduce nesting and make it more abstract
@Composable
private fun OnboardingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled
    )
}

private enum class Mode { Welcome, Register, Login, Restore }

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var mode by remember { mutableStateOf(Mode.Welcome) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var backupPhrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var backupCodeToShow by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.widthIn(max = 460.dp).padding(24.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Fortrx", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(4.dp))
                Text("Private messaging", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))

                when (mode) {
                    Mode.Welcome -> {
                        Button(onClick = { mode = Mode.Register }, modifier = Modifier.fillMaxWidth()) { Text("Create account") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { mode = Mode.Login }, modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { mode = Mode.Restore }, modifier = Modifier.fillMaxWidth()) { Text("Restore from backup phrase") }
                    }
                    else -> {
                        if (error != null) {
                            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                        }
                        
                        OnboardingTextField(username, { username = it; error = null }, "Username", !loading)
                        
                        if (mode == Mode.Register) {
                            Spacer(Modifier.height(8.dp))
                            OnboardingTextField(email, { email = it; error = null }, "Email", !loading)
                        }
                        
                        if (mode == Mode.Restore) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = backupPhrase,
                                onValueChange = { backupPhrase = it; error = null },
                                label = { Text("Backup Phrase") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !loading
                            )
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        OnboardingTextField(
                            password, 
                            { password = it; error = null }, 
                            if (mode == Mode.Register) "Create Password" else "Password", 
                            !loading, 
                            isPassword = true
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        if (loading) {
                            CircularProgressIndicator()
                        } else {
                            Button(
                                onClick = {
                                    scope.launch {
                                        loading = true; error = null
                                        try {
                                            when (mode) {
                                                Mode.Register -> {
                                                    val result = OnboardingService.register(username, email, password)
                                                    backupCodeToShow = result.backupCode
                                                    com.fortrx.FortrxClient.startSyncEngine(password)
                                                }
                                                Mode.Login -> {
                                                    OnboardingService.login(username, password)
                                                    com.fortrx.FortrxClient.startSyncEngine(password)
                                                    onDone()
                                                }
                                                Mode.Restore -> {
                                                    OnboardingService.restore(username, password, backupPhrase)
                                                    com.fortrx.FortrxClient.startSyncEngine(password)
                                                    onDone()
                                                }
                                                else -> {}
                                            }
                                        } catch (e: Throwable) {
                                            error = e.message ?: "Action failed"
                                        } finally {
                                            loading = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = username.isNotBlank() && password.length >= 8 &&
                                        (mode != Mode.Register || email.isNotBlank()) &&
                                        (mode != Mode.Restore || BackupCode.isValid(backupPhrase))
                            ) {
                                Text(when (mode) {
                                    Mode.Register -> "Create account"
                                    Mode.Login -> "Sign in"
                                    Mode.Restore -> "Restore"
                                    else -> ""
                                })
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { mode = Mode.Welcome }, enabled = !loading) { Text("Back") }
                    }
                }
            }
        }
    }
    backupCodeToShow?.let { code ->
        com.fortrx.desktop.ui.components.BackupCodeDialog(code = code, onConfirm = { backupCodeToShow = null; onDone() })
    }
}
