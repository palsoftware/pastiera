package it.palsoftware.pastiera.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.data.layout.LayoutFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneOffset

object BackupManager {
    private const val TAG = "BackupManager"

    suspend fun createBackup(context: Context, targetUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val workingDir = File(context.cacheDir, "backup_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val prefsDir = File(workingDir, "prefs").apply { mkdirs() }
            val filesDir = File(workingDir, "files").apply { mkdirs() }

            val prefComponents = PreferencesBackupHelper.dumpSharedPreferences(context, prefsDir)
            val fileComponents = FileBackupHelper.snapshotInternalFiles(context, filesDir)
            val components = (prefComponents + fileComponents).sorted()

            val metadata = BackupMetadata(
                versionCode = BuildConfig.VERSION_CODE,
                versionName = BuildConfig.VERSION_NAME,
                timestamp = OffsetDateTime.now(ZoneOffset.UTC).toString(),
                components = components
            )
            File(workingDir, "backup_meta.json").writeText(metadata.toJsonString())

            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                ZipHelper.zip(workingDir, output)
            } ?: return@withContext BackupResult.Failure("Unable to open target destination")

            BackupResult.Success(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            BackupResult.Failure(e.message ?: "Backup failed")
        } finally {
            workingDir.deleteRecursively()
        }
    }
}

sealed class BackupResult {
    data class Success(val metadata: BackupMetadata) : BackupResult()
    data class Failure(val reason: String) : BackupResult()
}

data class PreferencesRestoreSummary(
    val appliedKeys: List<String>,
    val skippedKeys: List<String>
)

data class FileRestoreSummary(
    val restoredFiles: List<String>,
    val skippedFiles: List<String>
)

object PreferencesBackupHelper {
    private const val TAG = "PreferencesBackup"

    fun dumpSharedPreferences(context: Context, destinationDir: File): List<String> {
        val sharedPrefsDir = File(context.dataDir, "shared_prefs")
        if (!sharedPrefsDir.exists()) {
            return emptyList()
        }

        val components = mutableListOf<String>()
        sharedPrefsDir.listFiles { file -> file.extension == "xml" }?.forEach { file ->
            val prefName = file.name.removeSuffix(".xml")
            val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            val prefsJson = buildPreferencesJson(prefName, prefs)
            val outFile = File(destinationDir, "$prefName.json")
            outFile.writeText(prefsJson.toString(2))
            components.add("prefs/${outFile.name}")
        }
        return components
    }

    private fun buildPreferencesJson(prefName: String, prefs: SharedPreferences): JSONObject {
        val json = JSONObject()
        json.put("name", prefName)
        val entries = JSONObject()
        prefs.all.forEach { (key, value) ->
            val prefValue = PreferenceValue.fromAny(value) ?: return@forEach
            entries.put(key, prefValue.toJson())
        }
        json.put("entries", entries)
        return json
    }

    fun readPreferencesFromBackup(prefsDir: File): Map<String, Map<String, PreferenceValue>> {
        if (!prefsDir.exists() || !prefsDir.isDirectory) {
            return emptyMap()
        }
        val result = mutableMapOf<String, Map<String, PreferenceValue>>()
        prefsDir.listFiles { file -> file.extension == "json" }?.forEach { file ->
            try {
                val content = file.readText()
                val json = JSONObject(content)
                val entriesJson = json.optJSONObject("entries") ?: JSONObject()
                val entries = mutableMapOf<String, PreferenceValue>()
                val keys = entriesJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val valueJson = entriesJson.optJSONObject(key) ?: continue
                    val prefValue = PreferenceValue.fromJson(valueJson) ?: continue
                    entries[key] = prefValue
                }
                result[file.nameWithoutExtension] = entries
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed prefs backup file: ${file.name}", e)
            }
        }
        return result
    }

    fun restorePreferences(
        context: Context,
        backedUpPrefs: Map<String, Map<String, PreferenceValue>>
    ): PreferencesRestoreSummary {
        val applied = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        backedUpPrefs.forEach { (prefName, entries) ->
            val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            val currentKeys = prefs.all.keys
            val editor = prefs.edit()

            entries.forEach { (key, value) ->
                val expectedType = PreferenceSchemas.expectedType(prefName, key)
                val recognized = PreferenceSchemas.isRecognized(prefName, key, currentKeys)
                if (!recognized) {
                    Log.w(TAG, "Ignoring unknown preference key $key for $prefName")
                    skipped.add("$prefName:$key")
                    return@forEach
                }

                val coerced = value.coerceTo(expectedType)
                if (coerced == null) {
                    skipped.add("$prefName:$key")
                    return@forEach
                }

                when (coerced.type) {
                    PreferenceValueType.BOOLEAN -> editor.putBoolean(key, coerced.value as Boolean)
                    PreferenceValueType.INT -> editor.putInt(key, (coerced.value as Number).toInt())
                    PreferenceValueType.LONG -> editor.putLong(key, (coerced.value as Number).toLong())
                    PreferenceValueType.FLOAT -> editor.putFloat(key, (coerced.value as Number).toFloat())
                    PreferenceValueType.STRING -> editor.putString(key, coerced.value?.toString())
                    PreferenceValueType.STRING_SET -> {
                        val setValue = (coerced.value as? Set<*>)?.mapNotNull { it?.toString() }?.toSet()
                        if (setValue != null) {
                            editor.putStringSet(key, setValue)
                        } else {
                            skipped.add("$prefName:$key")
                        }
                    }
                }
                applied.add("$prefName:$key")
            }
            editor.apply()
        }

        return PreferencesRestoreSummary(appliedKeys = applied, skippedKeys = skipped)
    }
}

object FileBackupHelper {
    private const val TAG = "FileBackupHelper"
    private val knownJsonFiles = setOf("ctrl_key_mappings.json")
    private val knownJsonDirectories = setOf("keyboard_layouts")

    fun snapshotInternalFiles(context: Context, destinationDir: File): List<String> {
        val base = context.filesDir
        if (!base.exists()) {
            return emptyList()
        }

        val components = mutableListOf<String>()
        base.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val relative = file.toRelativeString(base).replace("\\", "/")
                val target = File(destinationDir, relative)
                target.parentFile?.mkdirs()
                file.copyTo(target, overwrite = true)
                components.add("files/$relative")
            }
        return components
    }

    fun restoreFiles(context: Context, extractedFilesDir: File): FileRestoreSummary {
        val restored = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        if (!extractedFilesDir.exists()) {
            ensureDefaults(context)
            return FileRestoreSummary(restoredFiles = restored, skippedFiles = skipped)
        }

        val targetRoot = context.filesDir
        val backups = mutableListOf<Pair<File, File>>()

        try {
            extractedFilesDir.walkTopDown()
                .filter { it.isFile }
                .forEach { source ->
                    val relative = source.toRelativeString(extractedFilesDir).replace("\\", "/")
                    val isKnown = isKnownPath(relative)
                    if (!isKnown) {
                        Log.w(TAG, "Unknown file in backup, copying as-is: $relative")
                    }

                    if (source.extension.equals("json", ignoreCase = true) && !isJsonValid(source)) {
                        Log.w(TAG, "Skipping invalid JSON file from backup: $relative")
                        skipped.add(relative)
                        return@forEach
                    }

                    val target = File(targetRoot, relative)
                    target.parentFile?.mkdirs()
                    if (target.exists()) {
                        val backupFile = File.createTempFile("restore_backup_", ".bak", context.cacheDir)
                        target.copyTo(backupFile, overwrite = true)
                        backups.add(target to backupFile)
                    }
                    source.copyTo(target, overwrite = true)
                    restored.add(relative)
                }
        } catch (e: Exception) {
            backups.reversed().forEach { (target, backup) ->
                runCatching { backup.copyTo(target, overwrite = true) }
            }
            throw e
        } finally {
            backups.forEach { (_, backup) -> backup.delete() }
            ensureDefaults(context)
        }

        return FileRestoreSummary(restoredFiles = restored, skippedFiles = skipped)
    }

    private fun isKnownPath(relative: String): Boolean {
        val normalized = relative.removePrefix("./")
        if (knownJsonFiles.contains(normalized)) {
            return true
        }
        return knownJsonDirectories.any { dir ->
            normalized == dir || normalized.startsWith("$dir/")
        }
    }

    private fun isJsonValid(file: File): Boolean {
        return try {
            val text = file.readText()
            val trimmed = text.trim()
            if (trimmed.startsWith("[")) {
                org.json.JSONArray(trimmed)
            } else {
                JSONObject(trimmed)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureDefaults(context: Context) {
        try {
            LayoutFileStore.getLayoutsDirectory(context)
            SettingsManager.initializeNavModeMappingsFile(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to ensure default config files", e)
        }
    }
}
