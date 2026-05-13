package com.fortrx.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.fortrx.FortrxClient
import com.fortrx.Settings
import com.fortrx.services.OnboardingService
import com.fortrx.storage.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsScreenModel(
    private val fortrxClient: FortrxClient,
    private val onboardingService: OnboardingService
) : ScreenModel {
    private val _username = MutableStateFlow(Settings.myUsername ?: "Unknown")
    val username: StateFlow<String> = _username

    private val _userId = MutableStateFlow(Settings.myId?.toString() ?: "N/A")
    val userId: StateFlow<String> = _userId

    private val _backupCode = MutableStateFlow("")
    val backupCode: StateFlow<String> = _backupCode

    fun generateAndShowBackupCode() {
        _backupCode.value = com.fortrx.services.BackupCode.generate()
    }

    fun clearBackupCode() {
        _backupCode.value = ""
    }

    fun logout() {
        fortrxClient.logout()
    }

    fun deleteAccount(onSuccess: () -> Unit) {
        screenModelScope.launch {
            try {
                onboardingService.deleteAccount(Settings.storagePassword ?: "")
                fortrxClient.logout()
                onSuccess()
            } catch (e: Exception) {
                // TODO: Show error
            }
        }
    }
}
