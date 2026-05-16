package com.fortrx.services

enum class TransferPhase {
    UPLOADING,
    DOWNLOADING,
    DECRYPTING,
    SAVING,
    COMPLETED,
    FAILED,
}

data class TransferProgress(
    val phase: TransferPhase,
    val transferredBytes: Long = 0L,
    val totalBytes: Long? = null,
    val message: String? = null,
    val failureMessage: String? = null,
) {
    val percent: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { (transferredBytes.toFloat() / it).coerceIn(0f, 1f) }

    val isTerminal: Boolean
        get() = phase == TransferPhase.COMPLETED || phase == TransferPhase.FAILED
}
