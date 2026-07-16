package it.palsoftware.pastiera.gif

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import it.palsoftware.pastiera.SettingsManager

class LocalMediaRepository(private val context: Context) {

    fun getSelectedFolderUri(): Uri? {
        val value = SettingsManager.getLocalMediaFolderUri(context)
        if (value.isBlank()) return null
        return runCatching { Uri.parse(value) }.getOrNull()
    }

    fun getItems(limit: Int = 48): List<KlipyGifResult> {
        val treeUri = getSelectedFolderUri() ?: return emptyList()
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return root.listFiles()
            .asSequence()
            .filter { it.isFile }
            .filter { file -> file.type?.startsWith("image/") == true }
            .sortedByDescending { it.lastModified() }
            .take(limit)
            .mapNotNull { file ->
                val uri = file.uri.toString()
                val mimeType = file.type ?: "image/jpeg"
                KlipyGifResult(
                    id = uri,
                    title = file.name ?: "Local media",
                    mediaType = KlipyMediaType.LOCAL,
                    previewUrl = uri,
                    gifUrl = uri,
                    mimeType = mimeType,
                    shareUrl = uri,
                    isLocal = true
                )
            }
            .toList()
    }
}
