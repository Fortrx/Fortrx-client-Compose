package com.fortrx.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.fortrx.FortrxClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainScreenModel(
    private val fortrxClient: FortrxClient
) : ScreenModel {
    sealed class State {
        object Loading : State()
        object Onboarding : State()
        object ChatList : State()
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state

    init {
        checkAuth()
    }

    private fun checkAuth() {
        screenModelScope.launch {
            if (fortrxClient.tryAutoLogin()) {
                _state.value = State.ChatList
            } else {
                _state.value = State.Onboarding
            }
        }
    }
}
