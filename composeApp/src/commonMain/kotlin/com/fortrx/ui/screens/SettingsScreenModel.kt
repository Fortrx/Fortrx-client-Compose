package com.fortrx.ui.screens

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.fortrx.FortrxClient
import com.fortrx.Settings
import com.fortrx.services.BackupArchiveService
import com.fortrx.services.BackupExportResult
import com.fortrx.services.OnboardingService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsScreenModel(
    private val fortrxClient: FortrxClient,
    private val onboardingService: OnboardingService,
    private val backupArchiveService: BackupArchiveService,
) : ScreenModel {
    sealed interface Effect {
        data class ShowMessage(val message: String) : Effect
        data class SaveBackup(val result: BackupExportResult) : Effect
    }

    private val _username = MutableStateFlow(Settings.myUsername ?: "Unknown")
    val username: StateFlow<String> = _username

    private val _userId = MutableStateFlow(Settings.myId?.toString() ?: "N/A")
    val userId: StateFlow<String> = _userId

    private val _backupCode = MutableStateFlow<String?>(null)
    val backupCode: StateFlow<String?> = _backupCode

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun exportBackup() {
        screenModelScope.launch {
            _isBusy.value = true
            try {
                val result = backupArchiveService.createBackupArchive()
                _effects.send(Effect.SaveBackup(result))
            } catch (e: Exception) {
                _effects.send(Effect.ShowMessage("Could not create backup: ${e.message}"))
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun revealBackupCode(code: String) {
        screenModelScope.launch {
            _backupCode.value = code
            kotlinx.coroutines.delay(30_000)
            if (_backupCode.value == code) {
                _backupCode.value = null
            }
        }
    }

    fun clearBackupCode() {
        _backupCode.value = null
    }

    fun restoreBackup(documentName: String, bytes: ByteArray, code: String) {
        screenModelScope.launch {
            _isBusy.value = true
            try {
                val password = Settings.storagePassword ?: error("Storage is locked.")
                fortrxClient.stopSyncEngine()
                val result = backupArchiveService.restoreBackupArchive(documentName, bytes, code)
                fortrxClient.restartSyncEngine(password)
                _effects.send(Effect.ShowMessage("Imported ${result.importedMessages} messages and ${result.importedAttachments} attachments."))
            } catch (e: Exception) {
                val password = Settings.storagePassword
                if (!password.isNullOrBlank()) {
                    fortrxClient.restartSyncEngine(password)
                }
                _effects.send(Effect.ShowMessage("Restore failed: ${e.message}"))
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun logout() {
        fortrxClient.logout()
    }

    fun deleteAccount(password: String, onSuccess: () -> Unit) {
        screenModelScope.launch {
            try {
                onboardingService.deleteAccount(password)
                fortrxClient.logout()
                onSuccess()
            } catch (e: Exception) {
                _effects.send(Effect.ShowMessage("Delete account failed: ${e.message}"))
            }
        }
    }
}
