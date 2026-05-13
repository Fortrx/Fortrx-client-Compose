package com.fortrx.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.fortrx.FortrxClient
import com.fortrx.services.OnboardingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OnboardingScreenModel(
    private val onboardingService: OnboardingService,
    private val fortrxClient: FortrxClient
) : ScreenModel {
    private val _uiState = MutableStateFlow<OnboardingState>(OnboardingState.Idle)
    val uiState: StateFlow<OnboardingState> = _uiState

    sealed class OnboardingState {
        object Idle : OnboardingState()
        object Loading : OnboardingState()
        data class Success(val backupCode: String?) : OnboardingState()
        data class Error(val message: String) : OnboardingState()
    }

    fun register(username: String, email: String, password: String) {
        executeOnboarding(password) { onboardingService.register(username.lowercase().trim(), email, password) }
    }

    fun login(username: String, password: String) {
        executeOnboarding(password) { onboardingService.login(username.lowercase().trim(), password) }
    }

    fun restore(username: String, password: String, backupPhrase: String) {
        executeOnboarding(password) { onboardingService.restore(username.lowercase().trim(), password, backupPhrase) }
    }

    private fun executeOnboarding(password: String, block: suspend () -> OnboardingService.OnboardingResult) {
        screenModelScope.launch {
            _uiState.value = OnboardingState.Loading
            try {
                val result = block()
                fortrxClient.startSyncEngine(password)
                _uiState.value = OnboardingState.Success(result.backupCode)
            } catch (t: Throwable) {
                _uiState.value = OnboardingState.Error(t.message ?: "Authentication failed")
            }
        }
    }
    
    fun resetState() {
        _uiState.value = OnboardingState.Idle
    }
}
