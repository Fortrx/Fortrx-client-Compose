package com.fortrx.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import com.fortrx.FortrxClient
import com.fortrx.Settings
import com.fortrx.storage.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsScreenModel(
    private val fortrxClient: FortrxClient
) : ScreenModel {
    private val _username = MutableStateFlow(Settings.myUsername ?: "Unknown")
    val username: StateFlow<String> = _username

    private val _userId = MutableStateFlow(Settings.myId?.toString() ?: "N/A")
    val userId: StateFlow<String> = _userId

    private val _backupCode = MutableStateFlow(SettingsStore.loadBackupCode() ?: "")
    val backupCode: StateFlow<String> = _backupCode

    fun logout() {
        fortrxClient.logout()
    }
}
