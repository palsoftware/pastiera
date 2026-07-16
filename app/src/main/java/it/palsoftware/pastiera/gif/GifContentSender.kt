package it.palsoftware.pastiera.gif

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class GifContentSender(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    companion object {
        private const val GIF_MIME_TYPE = "image/gif"
        private const val PNG_MIME_TYPE = "image/png"
    }

    fun fallbackLink(result: KlipyGifResult): String {
        if (result.isLocal) return ""
        return if (result.shareUrl.isNotBlank()) result.shareUrl else result.gifUrl
    }

    fun supportsMediaCommit(editorInfo: EditorInfo?, result: KlipyGifResult): Boolean {
        val mimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo ?: return false)
        if (result.mimeType.startsWith("image/", ignoreCase = true)) {
            return mimeTypes.any { mime ->
                mime.equals("image/*", ignoreCase = true) ||
                    mime.startsWith("image/", ignoreCase = true)
            }
        }
        return mimeTypes.any { mime ->
            mime.equals(result.mimeType, ignoreCase = true) ||
                (result.mimeType == GIF_MIME_TYPE && mime.equals(GIF_MIME_TYPE, ignoreCase = true)) ||
                mime.equals("image/*", ignoreCase = true)
        }
    }

    fun prepareMedia(result: KlipyGifResult): PreparedGifContent {
        val sharedDir = File(context.cacheDir, "shared_media").apply { mkdirs() }
        val safeId = result.id.ifBlank { result.gifUrl.hashCode().toString() }.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val preparedMimeType = preparedMimeType(result)
        val extension = extensionForMimeType(preparedMimeType)
        val outputFile = File(sharedDir, "$safeId.$extension")
        if (!outputFile.exists() || outputFile.length() == 0L) {
            writeMediaToFile(result, outputFile, preparedMimeType)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.gifprovider", outputFile)
        return PreparedGifContent(
            uri = uri,
            description = ClipDescription(result.title.ifBlank { "Media" }, arrayOf(preparedMimeType)),
            linkUri = fallbackLink(result).takeIf { it.isNotBlank() }?.let(Uri::parse)
        )
    }

    fun commitPreparedGif(
        preparedGifContent: PreparedGifContent,
        inputConnection: InputConnection,
        editorInfo: EditorInfo
    ): Boolean {
        val contentInfo = InputContentInfoCompat(
            preparedGifContent.uri,
            preparedGifContent.description,
            preparedGifContent.linkUri
        )
        return InputConnectionCompat.commitContent(
            inputConnection,
            editorInfo,
            contentInfo,
            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
            null
        )
    }

    fun insertFallbackLink(result: KlipyGifResult, inputConnection: InputConnection?) {
        val fallback = fallbackLink(result)
        if (fallback.isBlank()) return
        if (inputConnection != null) {
            inputConnection.commitText(fallback, 1)
        } else {
            copyFallbackLink(result)
        }
    }

    fun copyFallbackLink(result: KlipyGifResult) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val fallback = fallbackLink(result)
        if (fallback.isBlank()) return
        clipboard.setPrimaryClip(ClipData.newPlainText("${result.mediaType.displayName.dropLast(1)} link", fallback))
    }

    fun hasTextFallback(result: KlipyGifResult): Boolean = fallbackLink(result).isNotBlank()

    private fun extensionForMimeType(mimeType: String): String {
        return when (mimeType.lowercase()) {
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/png" -> "png"
            else -> "jpg"
        }
    }

    private fun preparedMimeType(result: KlipyGifResult): String {
        return if (result.isLocal && !result.mimeType.equals(GIF_MIME_TYPE, ignoreCase = true)) {
            PNG_MIME_TYPE
        } else {
            result.mimeType
        }
    }

    private fun writeMediaToFile(result: KlipyGifResult, outputFile: File, preparedMimeType: String) {
        if (result.isLocal && preparedMimeType == PNG_MIME_TYPE) {
            transcodeLocalImageToPng(result.gifUrl, outputFile)
            return
        }
        downloadToFile(result.gifUrl, outputFile)
    }

    private fun transcodeLocalImageToPng(url: String, outputFile: File) {
        val uri = Uri.parse(url)
        val bitmap = decodeLocalBitmap(uri) ?: throw IllegalStateException("Unable to decode local media uri")
        outputFile.outputStream().use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IllegalStateException("Unable to transcode local media uri")
            }
        }
    }

    private fun decodeLocalBitmap(uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }
    }

    private fun downloadToFile(url: String, outputFile: File) {
        if (url.startsWith("content://")) {
            val uri = Uri.parse(url)
            context.contentResolver.openInputStream(uri)?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Unable to open local media uri")
            return
        }
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("GIF download failed: ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("GIF download returned no body")
            outputFile.outputStream().use { output ->
                body.byteStream().copyTo(output)
            }
        }
    }
}

data class PreparedGifContent(
    val uri: Uri,
    val description: ClipDescription,
    val linkUri: Uri?
)
