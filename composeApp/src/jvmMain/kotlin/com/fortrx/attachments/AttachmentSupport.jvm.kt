package com.fortrx.attachments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.fortrx.network.AttachmentApi
import com.fortrx.services.TransferPhase
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import java.awt.Desktop
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.imageio.ImageIO
import kotlinx.serialization.json.jsonPrimitive
import javax.swing.JFileChooser
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val BUFFER_SIZE = 64 * 1024

private fun attachmentRoot(): File =
    File(System.getProperty("user.home"), ".fortrx/attachments").apply { mkdirs() }

private fun localFile(localFileName: String): File = File(attachmentRoot(), localFileName)

private fun uniqueLocalName(originalName: String): String {
    val extension = originalName.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".$it" } ?: ""
    return UUID.randomUUID().toString() + extension
}

private fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun encryptToFile(source: File, destination: File, key: ByteArray, nonce: ByteArray, onProgress: ((Long, Long) -> Unit)? = null) {
    val totalSize = source.length()
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    var processed = 0L
    
    FileInputStream(source).use { input ->
        FileOutputStream(destination).use { output ->
            val cipherOutput = CipherOutputStream(output, cipher)
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                cipherOutput.write(buffer, 0, read)
                processed += read
                onProgress?.invoke(processed, totalSize)
            }
            cipherOutput.flush()
            cipherOutput.close()
        }
    }
}

private fun decryptToFile(source: File, destination: File, key: ByteArray, nonce: ByteArray, onProgress: ((Long, Long) -> Unit)? = null) {
    val totalSize = source.length()
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    var processed = 0L
    
    FileInputStream(source).use { input ->
        CipherInputStream(input, cipher).use { cipherInput ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = cipherInput.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    processed += read
                    onProgress?.invoke(processed, totalSize)
                }
            }
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun createThumbnail(file: File, mimeType: String): String? {
    if (!mimeType.startsWith("image/")) return null
    return try {
        val originalImage = ImageIO.read(file) ?: return null
        val targetWidth = 512
        val targetHeight = (originalImage.height * (targetWidth.toDouble() / originalImage.width)).toInt()
        val resizedImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH)
        val bufferedImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val g2d = bufferedImage.createGraphics()
        g2d.drawImage(resizedImage, 0, 0, null)
        g2d.dispose()

        val outputStream = ByteArrayOutputStream()
        ImageIO.write(bufferedImage, "jpg", outputStream)
        Base64.encode(outputStream.toByteArray())
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalEncodingApi::class)
@Composable
actual fun rememberBitmapFromBase64(base64: String?): ImageBitmap? {
    return remember(base64) {
        if (base64 == null) return@remember null
        try {
            val bytes = Base64.decode(base64)
            val bufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
            bufferedImage.toComposeImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
actual fun rememberBitmapFromFile(localFileName: String?): ImageBitmap? {
    return remember(localFileName) {
        if (localFileName == null) return@remember null
        try {
            val file = localFile(localFileName)
            if (!file.exists()) return@remember null
            val bufferedImage = ImageIO.read(file)
            bufferedImage.toComposeImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
actual fun rememberAttachmentPicker(
    onPicked: (List<PickedAttachment>) -> Unit,
    onError: (String) -> Unit,
): AttachmentPickerLauncher = remember {
    AttachmentPickerLauncher {
        try {
            val chooser = JFileChooser().apply { isMultiSelectionEnabled = true }
            val result = chooser.showOpenDialog(null)
            if (result != JFileChooser.APPROVE_OPTION) return@AttachmentPickerLauncher
            val selectedFiles = chooser.selectedFiles
            if (selectedFiles.isEmpty()) return@AttachmentPickerLauncher
            
            val results = selectedFiles.map { selected ->
                val localName = uniqueLocalName(selected.name)
                val target = localFile(localName)
                selected.inputStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
                val mimeType = java.nio.file.Files.probeContentType(selected.toPath()) ?: "application/octet-stream"
                val thumb = createThumbnail(target, mimeType)
                PickedAttachment(
                    localFileName = localName,
                    fileName = selected.name,
                    mimeType = mimeType,
                    sizeBytes = target.length(),
                    thumbnailBase64 = thumb,
                )
            }
            onPicked(results)
        } catch (t: Throwable) {
            onError(t.message ?: "Could not attach files.")
        }
    }
}

@Composable
actual fun rememberGalleryPermissionHandler(onPermissionGranted: () -> Unit): () -> Unit {
    return { onPermissionGranted() }
}

actual object AttachmentPlatform {
    @OptIn(ExperimentalEncodingApi::class)
    actual suspend fun uploadAttachment(
        recipientId: Long,
        attachment: PickedAttachment,
        ttlSeconds: Long?,
        onProgress: ((Long, Long) -> Unit)?,
    ): UploadedAttachment {
        val source = localFile(attachment.localFileName)
        require(source.exists()) { "Attachment file is missing from local storage." }

        // 20% encryption, 80% upload
        val encryptionWeight = 0.2f
        val uploadWeight = 0.8f
        val totalSize = source.length()

        val mediaKey = ByteArray(32).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val encryptedFile = File.createTempFile("fortrx-upload-", ".bin")
        
        encryptToFile(source, encryptedFile, mediaKey, nonce) { processed, total ->
            if (total > 0) {
                val p = (processed.toFloat() / total) * encryptionWeight
                onProgress?.invoke((p * total).toLong(), total)
            }
        }
        
        val sha256 = sha256Hex(encryptedFile)
        val uploadSize = encryptedFile.length()
        try {
            val response = AttachmentApi.beginUpload(
                recipientId = recipientId,
                fileName = attachment.fileName,
                mimeType = attachment.mimeType,
                sizeBytes = uploadSize,
                sha256 = sha256,
                ttlSeconds = ttlSeconds,
                body = object : OutgoingContent.WriteChannelContent() {
                    override val contentType = io.ktor.http.ContentType.Application.OctetStream
                    override suspend fun writeTo(channel: io.ktor.utils.io.ByteWriteChannel) {
                        var uploaded = 0L
                        encryptedFile.inputStream().use { input ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                channel.writeFully(buffer, 0, read)
                                uploaded += read
                                if (uploadSize > 0) {
                                    val p = encryptionWeight + ((uploaded.toFloat() / uploadSize) * uploadWeight)
                                    onProgress?.invoke((p * uploadSize).toLong(), uploadSize)
                                }
                            }
                        }
                    }
                },
            )
            val attachmentId = response["attachment_id"]?.jsonPrimitive?.content ?: error("Missing attachment id")
            return UploadedAttachment(
                attachmentId = attachmentId,
                localFileName = attachment.localFileName,
                fileName = attachment.fileName,
                mimeType = attachment.mimeType,
                sizeBytes = attachment.sizeBytes,
                sha256 = sha256,
                mediaKeyBase64 = Base64.encode(mediaKey),
                nonceBase64 = Base64.encode(nonce),
                thumbnailBase64 = attachment.thumbnailBase64,
            )
        } finally {
            encryptedFile.delete()
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    actual suspend fun downloadAttachment(
        attachmentId: String,
        fileName: String,
        mimeType: String,
        mediaKeyBase64: String,
        nonceBase64: String,
        expectedSha256: String?,
        expectedSizeBytes: Long?,
        onProgress: ((TransferPhase, Long, Long?) -> Unit)?,
    ): DownloadedAttachment {
        val response = AttachmentApi.download(attachmentId)
        val encryptedFile = File.createTempFile("fortrx-download-", ".bin")
        val digest = MessageDigest.getInstance("SHA-256")
        val expected = expectedSha256?.lowercase()
        val contentLength = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
        
        // Phase 1: 80% download, Phase 2: 20% decryption/processing
        val downloadWeight = 0.8f
        val processingWeight = 0.2f
        
        var downloadedBytes = 0L
        try {
            encryptedFile.outputStream().use { output ->
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = channel.readAvailable(buffer, 0, BUFFER_SIZE)
                    if (read == -1) break
                    if (read > 0) {
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (contentLength > 0) {
                            val weightedProgress = (downloadedBytes.toFloat() / contentLength) * downloadWeight
                            onProgress?.invoke(TransferPhase.DOWNLOADING, (weightedProgress * contentLength).toLong(), contentLength)
                        } else {
                            onProgress?.invoke(TransferPhase.DOWNLOADING, downloadedBytes, expectedSizeBytes)
                        }
                    }
                }
            }
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            if (expected != null && expected.isNotBlank() && actualSha != expected) {
                error("Downloaded attachment failed integrity validation.")
            }
            val localName = uniqueLocalName(fileName)
            val destination = localFile(localName)
            
            decryptToFile(
                source = encryptedFile,
                destination = destination,
                key = Base64.decode(mediaKeyBase64),
                nonce = Base64.decode(nonceBase64),
                onProgress = { processed, total ->
                    if (contentLength > 0) {
                        val decryptProgress = (processed.toFloat() / total) * processingWeight
                        val finalProgress = downloadWeight + decryptProgress
                        onProgress?.invoke(TransferPhase.DECRYPTING, (finalProgress * contentLength).toLong(), contentLength)
                    }
                }
            )
            return DownloadedAttachment(localFileName = localName, savedLocation = null)
        } finally {
            encryptedFile.delete()
        }
    }

    actual fun openAttachment(localFileName: String, mimeType: String) {
        val file = localFile(localFileName)
        if (file.exists()) {
            Desktop.getDesktop().open(file)
        }
    }

    actual fun saveAttachmentToDevice(localFileName: String, fileName: String, mimeType: String): String? {
        val source = localFile(localFileName)
        if (!source.exists()) return null
        val downloads = File(System.getProperty("user.home"), "Downloads").apply { mkdirs() }
        val target = File(downloads, fileName)
        source.copyTo(target, overwrite = true)
        return target.absolutePath
    }

    actual fun shareFile(localFileName: String, fileName: String, mimeType: String) {
        // Shared to system via desktop open or clipboard?
        // For now, open it so user can share from their viewer
        openAttachment(localFileName, mimeType)
    }

    actual suspend fun deleteRemote(attachmentId: String) {
        AttachmentApi.deleteRemote(attachmentId)
    }
}
