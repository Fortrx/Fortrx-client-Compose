package com.fortrx.attachments

import androidx.compose.runtime.Composable
import com.fortrx.services.TransferPhase

data class PickedAttachment(
    val localFileName: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val thumbnailBase64: String? = null,
)

data class UploadedAttachment(
    val attachmentId: String,
    val localFileName: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val mediaKeyBase64: String,
    val nonceBase64: String,
    val thumbnailBase64: String? = null,
)

data class DownloadedAttachment(
    val localFileName: String,
    val savedLocation: String?,
)

class AttachmentPickerLauncher internal constructor(
    private val launchBlock: (Array<String>) -> Unit,
) {
    fun launch(mimeTypes: Array<String> = arrayOf("*/*")) = launchBlock(mimeTypes)
}

@Composable
expect fun rememberBitmapFromBase64(base64: String?): androidx.compose.ui.graphics.ImageBitmap?

@Composable
expect fun rememberBitmapFromFile(localFileName: String?): androidx.compose.ui.graphics.ImageBitmap?

@Composable
expect fun rememberAttachmentPicker(
    onPicked: (List<PickedAttachment>) -> Unit,
    onError: (String) -> Unit,
): AttachmentPickerLauncher

@Composable
expect fun rememberGalleryPermissionHandler(onPermissionGranted: () -> Unit): () -> Unit

expect object AttachmentPlatform {
    suspend fun uploadAttachment(
        recipientId: Long,
        attachment: PickedAttachment,
        ttlSeconds: Long? = null,
        onProgress: ((Long, Long) -> Unit)? = null,
    ): UploadedAttachment

    suspend fun downloadAttachment(
        attachmentId: String,
        fileName: String,
        mimeType: String,
        mediaKeyBase64: String,
        nonceBase64: String,
        expectedSha256: String?,
        expectedSizeBytes: Long? = null,
        onProgress: ((TransferPhase, Long, Long?) -> Unit)? = null,
    ): DownloadedAttachment

    fun openAttachment(localFileName: String, mimeType: String)

    fun saveAttachmentToDevice(localFileName: String, fileName: String, mimeType: String): String?

    fun shareFile(localFileName: String, fileName: String, mimeType: String)

    suspend fun deleteRemote(attachmentId: String)
}
