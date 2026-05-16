package com.fortrx.attachments

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.fortrx.network.AttachmentApi
import com.fortrx.platform.AndroidContextHolder
import com.fortrx.services.TransferPhase
import io.ktor.http.content.OutgoingContent
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
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
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val BUFFER_SIZE = 64 * 1024

private fun attachmentRoot(): File =
    File(AndroidContextHolder.appContext.filesDir, "attachments").apply { mkdirs() }

private fun uniqueLocalName(originalName: String): String {
    val extension = originalName.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".$it" } ?: ""
    return UUID.randomUUID().toString() + extension
}

private fun localFile(localFileName: String): File {
    val file = File(localFileName)
    if (file.isAbsolute && file.exists()) return file
    // Handle both / and \ for cross-platform paths if they ever end up in the DB
    val nameOnly = localFileName.substringAfterLast('/').substringAfterLast('\\')
    return File(attachmentRoot(), nameOnly)
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
    return try {
        val bitmap = when {
            mimeType.startsWith("image/") -> {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, options)
                options.inSampleSize = calculateInSampleSize(options, 256, 256)
                options.inJustDecodeBounds = false
                BitmapFactory.decodeFile(file.absolutePath, options)
            }
            mimeType.startsWith("video/") -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ThumbnailUtils.createVideoThumbnail(file, Size(256, 256), null)
                } else {
                    @Suppress("DEPRECATION")
                    ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
                }
            }
            else -> null
        } ?: return null

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        Base64.encode(outputStream.toByteArray())
    } catch (e: Exception) {
        null
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.outHeight to options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

@OptIn(ExperimentalEncodingApi::class)
@Composable
actual fun rememberBitmapFromBase64(base64: String?): ImageBitmap? {
    return remember(base64) {
        if (base64 == null) return@remember null
        try {
            val bytes = Base64.decode(base64)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            bitmap.asImageBitmap()
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
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
actual fun rememberAttachmentPicker(
    onPicked: (List<PickedAttachment>) -> Unit,
    onError: (String) -> Unit,
): AttachmentPickerLauncher {
    val context = AndroidContextHolder.appContext
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        try {
            val results = mutableListOf<PickedAttachment>()
            for (uri in uris) {
                val resolver = context.contentResolver
                var displayName = "attachment.bin"
                var sizeBytes = -1L
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
                        if (sizeIndex >= 0) sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
                
                // Better MimeType detection
                var mimeType = resolver.getType(uri) ?: "application/octet-stream"
                val extension = displayName.substringAfterLast(".", "").lowercase()
                if (mimeType == "application/octet-stream" || mimeType == "application/x-download") {
                    mimeType = when (extension) {
                        "pdf" -> "application/pdf"
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        "mp4" -> "video/mp4"
                        "doc", "docx" -> "application/msword"
                        "xls", "xlsx" -> "application/vnd.ms-excel"
                        else -> mimeType
                    }
                }

                val localName = uniqueLocalName(displayName)
                val target = localFile(localName)
                
                var copied = 0L
                resolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            copied += read
                        }
                    }
                } ?: continue

                if (copied == 0L) {
                    target.delete()
                    continue
                }

                val thumb = createThumbnail(target, mimeType)

                results.add(
                    PickedAttachment(
                        localFileName = localName,
                        fileName = displayName,
                        mimeType = mimeType,
                        sizeBytes = if (sizeBytes > 0) sizeBytes else copied,
                        thumbnailBase64 = thumb,
                    )
                )
            }
            onPicked(results)
        } catch (t: Throwable) {
            onError(t.message ?: "Could not attach files.")
        }
    }
    return remember(launcher) {
        AttachmentPickerLauncher { mimeTypes -> launcher.launch(mimeTypes) }
    }
}

@Composable
actual fun rememberGalleryPermissionHandler(onPermissionGranted: () -> Unit): () -> Unit {
    val context = AndroidContextHolder.appContext
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            onPermissionGranted()
        }
    }

    return {
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            else -> {
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            onPermissionGranted()
        } else {
            launcher.launch(permissions)
        }
    }
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

        // Phase weights: 20% encryption, 80% upload
        val encryptionWeight = 0.2f
        val uploadWeight = 0.8f
        val totalSize = source.length()

        val mediaKey = ByteArray(32).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val encryptedFile = File.createTempFile("fortrx-upload-", ".bin", AndroidContextHolder.appContext.cacheDir)
        
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
        val encryptedFile = File.createTempFile("fortrx-download-", ".bin", AndroidContextHolder.appContext.cacheDir)
        val expected = expectedSha256?.lowercase()
        val digest = MessageDigest.getInstance("SHA-256")
        
        // Use attachment size from headers or fallback to a reasonable estimate if missing
        val contentLength = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
        
        // We divide progress into 2 phases: 70% download, 30% decryption/processing
        val downloadWeight = 0.7f
        val processingWeight = 0.3f
        
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
                            val total = expectedSizeBytes ?: encryptedFile.length().takeIf { it > 0 }
                            onProgress?.invoke(TransferPhase.DOWNLOADING, downloadedBytes, total)
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
            val encryptedSize = encryptedFile.length()
            
            decryptToFile(
                source = encryptedFile,
                destination = destination,
                key = Base64.decode(mediaKeyBase64),
                nonce = Base64.decode(nonceBase64),
                onProgress = { processed, total ->
                    val totalToUse = if (contentLength > 0) contentLength else (if (total > 0) total else encryptedSize)
                    if (totalToUse > 0) {
                        val decryptProgress = (processed.toFloat() / total) * processingWeight
                        val finalProgress = downloadWeight + decryptProgress
                        onProgress?.invoke(TransferPhase.DECRYPTING, (finalProgress * totalToUse).toLong(), totalToUse)
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
        if (!file.exists()) {
            // Try to find it in the attachments root if it was saved with a different path logic
            val fallback = File(attachmentRoot(), localFileName.substringAfterLast("/"))
            if (!fallback.exists()) {
                throw IllegalStateException("File not found: $localFileName. Please try downloading it again.")
            }
            return openAttachment(fallback.absolutePath, mimeType)
        }
        
        val context = AndroidContextHolder.appContext
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to create file provider URI: ${e.message}")
        }
        
        // Force PDF mime if extension matches
        val finalMime = when {
            localFileName.lowercase().endsWith(".pdf") || file.name.lowercase().endsWith(".pdf") -> "application/pdf"
            localFileName.lowercase().endsWith(".jpg") || localFileName.lowercase().endsWith(".jpeg") -> "image/jpeg"
            localFileName.lowercase().endsWith(".png") -> "image/png"
            localFileName.lowercase().endsWith(".mp4") -> "video/mp4"
            else -> mimeType
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, finalMime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            val chooser = Intent.createChooser(intent, "Open with")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            // Fallback to generic viewer if specific mime fails
            try {
                val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(genericIntent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) {
                throw IllegalStateException("No app found to open this file type ($finalMime): ${e2.message}")
            }
        }
    }

    actual fun saveAttachmentToDevice(localFileName: String, fileName: String, mimeType: String): String? {
        val file = localFile(localFileName)
        if (!file.exists()) return null
        val context = AndroidContextHolder.appContext
        val resolver = context.contentResolver

        val collection = when {
            mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Files.getContentUri("external")
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                val folder = when {
                    mimeType.startsWith("image/") -> "Pictures/fortrx"
                    mimeType.startsWith("video/") -> "Movies/fortrx"
                    else -> "Download/fortrx"
                }
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            }
        }

        val uri = try {
            resolver.insert(collection, values)
        } catch (e: Exception) {
            null
        } ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri.toString()
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    actual fun shareFile(localFileName: String, fileName: String, mimeType: String) {
        val file = localFile(localFileName)
        if (!file.exists()) return
        val context = AndroidContextHolder.appContext
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share File")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    actual suspend fun deleteRemote(attachmentId: String) {
        AttachmentApi.deleteRemote(attachmentId)
    }
}
