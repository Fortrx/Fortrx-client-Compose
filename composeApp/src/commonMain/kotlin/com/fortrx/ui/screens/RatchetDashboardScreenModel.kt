package com.fortrx.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.fortrx.Settings
import com.fortrx.crypto.RatchetState
import com.fortrx.storage.Db
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class RatchetDashboardScreenModel(val contactId: Long) : ScreenModel {

    data class State(
        val sessionBlob: String? = null,
        val ratchetState: RatchetState? = null,
        val isLoading: Boolean = true,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadData()
    }

    fun loadData() {
        screenModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val password = Settings.storagePassword
            if (password == null) {
                _state.update { it.copy(isLoading = false, error = "Storage password not set.") }
                return@launch
            }

            try {
                val blob = Db.loadSessionBlob(password, contactId)
                if (blob == null) {
                    _state.update { it.copy(isLoading = false, sessionBlob = null, ratchetState = null) }
                } else {
                    val ratchet = try {
                        json.decodeFromString<RatchetState>(blob)
                    } catch (e: Exception) {
                        null
                    }
                    _state.update { it.copy(isLoading = false, sessionBlob = blob, ratchetState = ratchet) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Failed to load session: ${e.message}") }
            }
        }
    }
}
