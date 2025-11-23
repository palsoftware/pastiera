package it.palsoftware.pastiera.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object RestoreManager {
    private const val TAG = "RestoreManager"

    suspend fun restore(context: Context, sourceUri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val workingDir = File(context.cacheDir, "restore_${System.currentTimeMillis()}").apply { mkdirs() }
        val extractedDir = File(workingDir, "unzipped").apply { mkdirs() }

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                ZipHelper.unzip(input, extractedDir)
            } ?: return@withContext RestoreResult.Failure("Unable to open source backup")

            val metadata = BackupMetadata.fromFile(File(extractedDir, "backup_meta.json"))
            val prefsDir = File(extractedDir, "prefs")
            val filesDir = File(extractedDir, "files")

            val prefsData = PreferencesBackupHelper.readPreferencesFromBackup(prefsDir)
            val fileSummary = FileBackupHelper.restoreFiles(context, filesDir)
            val prefsSummary = PreferencesBackupHelper.restorePreferences(context, prefsData)

            RestoreResult.Success(
                metadata = metadata,
                preferencesSummary = prefsSummary,
                fileSummary = fileSummary
            )
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            RestoreResult.Failure(e.message ?: "Restore failed")
        } finally {
            extractedDir.deleteRecursively()
            workingDir.deleteRecursively()
        }
    }
}

sealed class RestoreResult {
    data class Success(
        val metadata: BackupMetadata?,
        val preferencesSummary: PreferencesRestoreSummary,
        val fileSummary: FileRestoreSummary
    ) : RestoreResult()

    data class Failure(val reason: String) : RestoreResult()
}
