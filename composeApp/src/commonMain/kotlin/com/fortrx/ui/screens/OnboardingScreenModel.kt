package com.fortrx.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.fortrx.FortrxClient
import com.fortrx.services.BackupArchiveService
import com.fortrx.services.OnboardingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OnboardingScreenModel(
    private val onboardingService: OnboardingService,
    private val fortrxClient: FortrxClient,
    private val backupArchiveService: BackupArchiveService,
) : ScreenModel {
    private val _uiState = MutableStateFlow<OnboardingState>(OnboardingState.Idle)
    val uiState: StateFlow<OnboardingState> = _uiState

    sealed class OnboardingState {
        object Idle : OnboardingState()
        object Loading : OnboardingState()
        object Success : OnboardingState()
        data class Error(val message: String) : OnboardingState()
    }

    fun register(username: String, email: String, password: String) {
        executeOnboarding(password) { onboardingService.register(username.lowercase().trim(), email, password) }
    }

    fun login(username: String, password: String) {
        executeOnboarding(password) { onboardingService.login(username.lowercase().trim(), password) }
    }

    fun restore(username: String, password: String, documentName: String, bytes: ByteArray, code: String) {
        screenModelScope.launch {
            _uiState.value = OnboardingState.Loading
            try {
                onboardingService.restore(username.lowercase().trim(), password)
                backupArchiveService.restoreBackupArchive(documentName, bytes, code)
                fortrxClient.startSyncEngine(password)
                _uiState.value = OnboardingState.Success
            } catch (t: Throwable) {
                _uiState.value = OnboardingState.Error(t.message ?: "Authentication failed")
            }
        }
    }

    private fun executeOnboarding(password: String, block: suspend () -> OnboardingService.OnboardingResult) {
        screenModelScope.launch {
            _uiState.value = OnboardingState.Loading
            try {
                block()
                fortrxClient.startSyncEngine(password)
                _uiState.value = OnboardingState.Success
            } catch (t: Throwable) {
                _uiState.value = OnboardingState.Error(t.message ?: "Authentication failed")
            }
        }
    }
    
    fun resetState() {
        _uiState.value = OnboardingState.Idle
    }
}
