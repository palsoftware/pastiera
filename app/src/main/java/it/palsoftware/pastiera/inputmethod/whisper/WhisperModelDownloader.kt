package it.palsoftware.pastiera.inputmethod.whisper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Downloads Whisper models from Hugging Face.
 */
class WhisperModelDownloader(private val context: Context) {
    companion object {
        private const val TAG = "WhisperModelDownloader"
        private const val MODELS_DIR = "whisper_models"
        private const val CONNECT_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 300L // 5 minutes for large downloads
    }

    interface DownloadListener {
        fun onProgress(bytesDownloaded: Long, totalBytes: Long)
        fun onComplete(file: File)
        fun onError(error: String)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Gets the models directory, creating it if needed.
     */
    private fun getModelsDir(): File {
        val dir = File(context.getExternalFilesDir(null), MODELS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Checks if a model is downloaded and extracted.
     * For DocWolle ONNX models: checks for ONNX files in the models directory.
     */
    fun isModelDownloaded(model: WhisperModel): Boolean {
        val modelsDir = getModelsDir()
        if (!modelsDir.exists()) {
            Log.d(TAG, "Models directory doesn't exist: ${modelsDir.absolutePath}")
            return false
        }
        
        val modelFiles = modelsDir.listFiles() ?: return false
        
        // For ONNX models: check if there are extracted ONNX files
        val hasOnnxFiles = modelFiles.any { it.extension == "onnx" }
        
        if (!hasOnnxFiles) {
            Log.d(TAG, "No ONNX files found. Model not downloaded.")
            return false
        }
        
        Log.d(TAG, "Model '${model.displayName}' is downloaded. Found ${modelFiles.count { it.extension == "onnx" }} ONNX files")
        return true
    }

    /**
     * Gets the vocab file for a model.
     */
    private fun getVocabFile(model: WhisperModel): File {
        val vocabFileName = if (model.isMultilingual) {
            "filters_vocab_multilingual.bin"
        } else {
            "filters_vocab_en.bin"
        }
        return File(getModelsDir(), vocabFileName)
    }

    /**
     * Gets the total size of downloaded ONNX model files.
     */
    fun getDownloadedSize(model: WhisperModel): Long {
        val modelsDir = getModelsDir()
        val modelFiles = modelsDir.listFiles() ?: return 0L
        
        // Sum size of all ONNX files (they're the actual models)
        return modelFiles.filter { it.extension == "onnx" }.sumOf { it.length() }
    }

    /**
     * Downloads a Whisper model and its vocab file.
     */
    suspend fun downloadModel(
        model: WhisperModel,
        listener: DownloadListener? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting download of ${model.displayName}")

            val modelsDir = getModelsDir()
            val modelFile = File(modelsDir, model.fileName)
            val vocabFile = getVocabFile(model)

            // Download vocab file if not present
            if (!vocabFile.exists() || vocabFile.length() == 0L) {
                val vocabUrl = if (model.isMultilingual) {
                    WhisperModelUrls.VOCAB_MULTILINGUAL_URL
                } else {
                    WhisperModelUrls.VOCAB_EN_URL
                }
                
                Log.d(TAG, "Downloading vocab file from $vocabUrl")
                downloadFile(vocabUrl, vocabFile, null) // Vocab is small, no progress
            }

            // Download model file
            val modelUrl = WhisperModelUrls.getDownloadUrl(model)
            Log.d(TAG, "Downloading model from $modelUrl")
            
            // If URL ends with .zip, download and extract it
            if (modelUrl.endsWith(".zip")) {
                val zipFile = File(modelsDir, "${model.fileName}.zip")
                downloadFile(modelUrl, zipFile, listener)
                
                Log.d(TAG, "Extracting ZIP file...")
                extractZipFile(zipFile, modelsDir)
                zipFile.delete() // Clean up ZIP after extraction
                Log.d(TAG, "Extraction complete")
            } else {
                // Direct ONNX file download
                downloadFile(modelUrl, modelFile, listener)
            }

            Log.d(TAG, "Download complete: ${modelFile.absolutePath}")
            Result.success(modelFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model", e)
            listener?.onError(e.message ?: "Download failed")
            Result.failure(e)
        }
    }

    /**
     * Downloads a file from a URL with progress tracking.
     */
    private fun downloadFile(
        url: String,
        targetFile: File,
        listener: DownloadListener?
    ) {
        Log.d(TAG, "Attempting to download from: $url")
        Log.d(TAG, "Target file: ${targetFile.absolutePath}")
        
        val request = Request.Builder()
            .url(url)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Response code: ${response.code}")
                
                if (!response.isSuccessful) {
                    val errorMsg = "Download failed: HTTP ${response.code} - ${response.message}"
                    Log.e(TAG, errorMsg)
                    throw Exception(errorMsg)
                }

                val body = response.body
                if (body == null) {
                    val errorMsg = "Empty response body from server"
                    Log.e(TAG, errorMsg)
                    throw Exception(errorMsg)
                }
                
                val contentLength = body.contentLength()
                Log.d(TAG, "Content length: $contentLength bytes (${contentLength / (1024 * 1024)} MB)")

                // Create temp file for atomic write
                val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
                Log.d(TAG, "Writing to temp file: ${tempFile.absolutePath}")
                
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    body.byteStream().use { input ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            
                            listener?.onProgress(totalBytesRead, contentLength)
                            
                            // Log progress every 10 MB
                            if (totalBytesRead % (10 * 1024 * 1024) == 0L) {
                                Log.d(TAG, "Downloaded: ${totalBytesRead / (1024 * 1024)} MB")
                            }
                        }
                    }
                    Log.d(TAG, "Download complete: $totalBytesRead bytes written")
                }

                // Verify download
                if (contentLength > 0 && tempFile.length() != contentLength) {
                    tempFile.delete()
                    val errorMsg = "Download incomplete: ${tempFile.length()} of $contentLength bytes"
                    Log.e(TAG, errorMsg)
                    throw Exception(errorMsg)
                }

                // Move temp file to target
                if (targetFile.exists()) {
                    Log.d(TAG, "Deleting existing file: ${targetFile.absolutePath}")
                    targetFile.delete()
                }
                val renamed = tempFile.renameTo(targetFile)
                if (!renamed) {
                    val errorMsg = "Failed to rename temp file to target file"
                    Log.e(TAG, errorMsg)
                    throw Exception(errorMsg)
                }
                Log.d(TAG, "File successfully saved to: ${targetFile.absolutePath}")

                listener?.onComplete(targetFile)
            }
        } catch (e: java.net.UnknownHostException) {
            val errorMsg = "Network error: Cannot resolve host. Check internet connection."
            Log.e(TAG, errorMsg, e)
            throw Exception(errorMsg, e)
        } catch (e: java.net.SocketTimeoutException) {
            val errorMsg = "Network timeout. Check internet connection."
            Log.e(TAG, errorMsg, e)
            throw Exception(errorMsg, e)
        } catch (e: java.io.IOException) {
            val errorMsg = "Network I/O error: ${e.message}"
            Log.e(TAG, errorMsg, e)
            throw Exception(errorMsg, e)
        }
    }

    /**
     * Extracts a ZIP file containing ONNX models.
     * DocWolle's models are packaged in ZIP format.
     */
    private fun extractZipFile(zipFile: File, targetDir: File) {
        Log.d(TAG, "Extracting ZIP file: ${zipFile.name}")
        
        ZipInputStream(zipFile.inputStream()).use { zipInput ->
            var zipEntry = zipInput.nextEntry
            
            while (zipEntry != null) {
                val entryName = zipEntry.name
                
                // Skip directories
                if (zipEntry.isDirectory) {
                    zipEntry = zipInput.nextEntry
                    continue
                }
                
                // Extract only .onnx and .bin files
                if (entryName.endsWith(".onnx") || entryName.endsWith(".bin")) {
                    // Extract just the filename (ignore directory structure in ZIP)
                    val fileName = entryName.substringAfterLast('/')
                    val outputFile = File(targetDir, fileName)
                    
                    Log.d(TAG, "Extracting: $entryName -> ${outputFile.name}")
                    
                    outputFile.outputStream().use { fileOutput ->
                        zipInput.copyTo(fileOutput)
                    }
                    
                    Log.d(TAG, "Extracted: ${outputFile.name} (${outputFile.length()} bytes)")
                }
                
                zipEntry = zipInput.nextEntry
            }
        }
        
        Log.d(TAG, "ZIP extraction complete")
    }

    /**
     * Deletes a downloaded model.
     */
    fun deleteModel(model: WhisperModel): Boolean {
        return try {
            val modelFile = File(getModelsDir(), model.fileName)
            val deleted = if (modelFile.exists()) modelFile.delete() else true
            
            // Note: Don't delete vocab files as they might be shared
            // between models (multilingual vocab is shared)
            
            Log.d(TAG, "Deleted model: ${model.displayName}")
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting model", e)
            false
        }
    }

    /**
     * Gets total size of all downloaded models.
     */
    fun getTotalDownloadedSize(): Long {
        val modelsDir = getModelsDir()
        return modelsDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}

