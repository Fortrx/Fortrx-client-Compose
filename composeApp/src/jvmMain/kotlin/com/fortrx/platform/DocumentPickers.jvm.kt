package com.fortrx.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File
import javax.swing.JFileChooser

@Composable
actual fun rememberSaveBytesLauncher(
    onSaved: (String) -> Unit,
    onError: (String) -> Unit,
): SaveBytesLauncher = remember {
    SaveBytesLauncher { defaultFileName, bytes ->
        runCatching {
            val chooser = JFileChooser().apply { selectedFile = File(defaultFileName) }
            val result = chooser.showSaveDialog(null)
            if (result != JFileChooser.APPROVE_OPTION) error("Save cancelled.")
            chooser.selectedFile.writeBytes(bytes)
            onSaved(chooser.selectedFile.absolutePath)
        }.onFailure { onError(it.message ?: "Could not save file.") }
    }
}

@Composable
actual fun rememberOpenBytesLauncher(
    onOpened: (BinaryDocument) -> Unit,
    onError: (String) -> Unit,
): OpenBytesLauncher = remember {
    OpenBytesLauncher {
        runCatching {
            val chooser = JFileChooser()
            val result = chooser.showOpenDialog(null)
            if (result != JFileChooser.APPROVE_OPTION) error("Open cancelled.")
            val file = chooser.selectedFile ?: error("No file selected.")
            onOpened(BinaryDocument(file.name, file.readBytes()))
        }.onFailure { onError(it.message ?: "Could not open file.") }
    }
}
