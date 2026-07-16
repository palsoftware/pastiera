package it.palsoftware.pastiera.gif

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
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
    }

    fun fallbackLink(result: KlipyGifResult): String = if (result.shareUrl.isNotBlank()) result.shareUrl else result.gifUrl

    fun supportsGifCommit(editorInfo: EditorInfo?): Boolean {
        val mimeTypes = EditorInfoCompat.getContentMimeTypes(editorInfo ?: return false)
        return mimeTypes.any { mime ->
            mime.equals(GIF_MIME_TYPE, ignoreCase = true) || mime.equals("image/*", ignoreCase = true)
        }
    }

    fun prepareGif(result: KlipyGifResult): PreparedGifContent {
        val sharedDir = File(context.cacheDir, "shared_gifs").apply { mkdirs() }
        val safeId = result.id.ifBlank { result.gifUrl.hashCode().toString() }.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val outputFile = File(sharedDir, "$safeId.gif")
        if (!outputFile.exists() || outputFile.length() == 0L) {
            downloadToFile(result.gifUrl, outputFile)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.gifprovider", outputFile)
        return PreparedGifContent(
            uri = uri,
            description = ClipDescription(result.title.ifBlank { "GIF" }, arrayOf(GIF_MIME_TYPE)),
            linkUri = Uri.parse(fallbackLink(result))
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
        clipboard.setPrimaryClip(ClipData.newPlainText("GIF link", fallback))
    }

    private fun downloadToFile(url: String, outputFile: File) {
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
    val linkUri: Uri
)
