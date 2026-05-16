package com.fortrx.platform

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberSaveBytesLauncher(
    onSaved: (String) -> Unit,
    onError: (String) -> Unit,
): SaveBytesLauncher {
    val latestSaved = rememberUpdatedState(onSaved)
    val latestError = rememberUpdatedState(onError)
    val pending = remember { arrayOfNulls<Any>(2) }
    val context = AndroidContextHolder.appContext
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        val name = pending[0] as? String ?: "document"
        val bytes = pending[1] as? ByteArray ?: ByteArray(0)
        if (uri == null) {
            latestError.value("Save cancelled.")
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(bytes)
            } ?: error("Could not open save location.")
            latestSaved.value(uri.toString())
        }.onFailure { latestError.value(it.message ?: "Could not save file.") }
    }
    return remember(launcher) {
        SaveBytesLauncher { defaultFileName, bytes ->
            pending[0] = defaultFileName
            pending[1] = bytes
            launcher.launch(defaultFileName)
        }
    }
}

@Composable
actual fun rememberOpenBytesLauncher(
    onOpened: (BinaryDocument) -> Unit,
    onError: (String) -> Unit,
): OpenBytesLauncher {
    val latestOpened = rememberUpdatedState(onOpened)
    val latestError = rememberUpdatedState(onError)
    val context = AndroidContextHolder.appContext
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            latestError.value("Open cancelled.")
            return@rememberLauncherForActivityResult
        }
        runCatching {
            val resolver = context.contentResolver
            val name = runCatching {
                resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (index >= 0) cursor.getString(index) else null
                        } else null
                    }
            }.getOrNull() ?: "document"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read file.")
            latestOpened.value(BinaryDocument(name, bytes))
        }.onFailure { latestError.value(it.message ?: "Could not open file.") }
    }
    return remember(launcher) {
        OpenBytesLauncher { launcher.launch(arrayOf("*/*")) }
    }
}
