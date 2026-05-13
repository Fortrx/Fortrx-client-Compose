package com.fortrx.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import com.fortrx.services.OnboardingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class OnboardingScreenModel(
    private val onboardingService: OnboardingService
) : ScreenModel {
    private val _uiState = MutableStateFlow<OnboardingState>(OnboardingState.Idle)
    val uiState: StateFlow<OnboardingState> = _uiState

    sealed class OnboardingState {
        object Idle : OnboardingState()
        object Loading : OnboardingState()
        data class Success(val backupCode: String?) : OnboardingState()
        data class Error(val message: String) : OnboardingState()
    }

    // Add methods for login, register, restore
}
