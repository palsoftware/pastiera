package it.palsoftware.pastiera.emoji

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.TextView
import it.palsoftware.pastiera.SettingsManager
import java.io.File

object CustomEmojiFontManager {
    private const val TAG = "CustomEmojiFont"
    private const val DIR_NAME = "custom_emoji_font"
    private const val FONT_FILE_NAME = "emoji-font.ttf"

    @Volatile
    private var cachedPath: String? = null

    @Volatile
    private var cachedTypeface: Typeface? = null

    fun importFont(context: Context, uri: Uri): String {
        val appContext = context.applicationContext
        val displayName = resolveDisplayName(appContext, uri)
        val outDir = File(appContext.filesDir, DIR_NAME).apply { mkdirs() }
        val outFile = File(outDir, FONT_FILE_NAME)

        appContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected font" }
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        // Validate before saving the setting, otherwise the picker would repeatedly fail.
        Typeface.createFromFile(outFile)
        cachedPath = outFile.absolutePath
        cachedTypeface = null
        SettingsManager.setEmojiPickerCustomFont(appContext, outFile.absolutePath, displayName)
        return displayName
    }

    fun getTypeface(context: Context): Typeface? {
        if (!SettingsManager.getEmojiPickerCustomFontEnabled(context)) return null
        val path = SettingsManager.getEmojiPickerCustomFontPath(context)
        if (path.isBlank()) return null
        cachedTypeface?.let { existing ->
            if (cachedPath == path) return existing
        }
        return runCatching {
            Typeface.createFromFile(File(path))
        }.onFailure { error ->
            Log.w(TAG, "Failed to load custom emoji font", error)
        }.getOrNull()?.also { loaded ->
            cachedPath = path
            cachedTypeface = loaded
        }
    }

    fun clearCache() {
        cachedPath = null
        cachedTypeface = null
    }

    fun applyToTextView(
        context: Context,
        textView: TextView,
        emoji: String,
        fallbackTypeface: Typeface,
        systemTextSizeSp: Float,
        customTextSizeSp: Float
    ) {
        val typeface = getTypeface(context)
        val displayText = displayTextForCustomTypeface(emoji)
        if (typeface != null && displayText != null) {
            textView.text = displayText
            textView.typeface = typeface
            textView.textSize = customTextSizeSp
        } else {
            textView.text = emoji
            textView.typeface = fallbackTypeface
            textView.textSize = systemTextSizeSp
        }
    }

    /**
     * Converted Apple emoji fonts often render complex sequences as separate glyphs
     * inside Android TextView. Let Android handle those sequences instead.
     */
    fun canUseCustomTypeface(emoji: String): Boolean {
        return displayTextForCustomTypeface(emoji) != null
    }

    private fun displayTextForCustomTypeface(emoji: String): String? {
        if (emoji.indexOf('\u200D') >= 0) return null // ZWJ sequences
        val normalized = emoji
            .replace("\uFE0E", "")
            .replace("\uFE0F", "")
        if (normalized.isBlank()) return null
        var index = 0
        var codePointCount = 0
        while (index < normalized.length) {
            val codePoint = normalized.codePointAt(index)
            if (codePoint in 0x1F3FB..0x1F3FF) return null // skin tone modifiers
            codePointCount += 1
            if (codePointCount > 1) return null // flags and other multi-codepoint sequences
            index += Character.charCount(codePoint)
        }
        return if (codePointCount == 1) normalized else null
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    val name = cursor.getString(index)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Custom emoji font"
    }
}
