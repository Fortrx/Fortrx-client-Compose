package com.fortrx.platform

import androidx.compose.runtime.Composable

data class BinaryDocument(
    val displayName: String,
    val bytes: ByteArray,
)

class SaveBytesLauncher internal constructor(
    private val launchBlock: (String, ByteArray) -> Unit,
) {
    fun launch(defaultFileName: String, bytes: ByteArray) = launchBlock(defaultFileName, bytes)
}

class OpenBytesLauncher internal constructor(
    private val launchBlock: () -> Unit,
) {
    fun launch() = launchBlock()
}

@Composable
expect fun rememberSaveBytesLauncher(
    onSaved: (String) -> Unit,
    onError: (String) -> Unit,
): SaveBytesLauncher

@Composable
expect fun rememberOpenBytesLauncher(
    onOpened: (BinaryDocument) -> Unit,
    onError: (String) -> Unit,
): OpenBytesLauncher
