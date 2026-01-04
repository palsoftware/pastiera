package it.palsoftware.pastiera.inputmethod.whisper

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Helper for debugging audio capture issues.
 * Saves recorded audio to external cache directory for inspection.
 */
object AudioDebugHelper {
    private const val TAG = "AudioDebugHelper"
    private const val DEBUG_AUDIO_DIR = "whisper_debug_audio"

    /**
     * Saves current audio buffer to a WAV file for debugging
     * Returns the path to the saved file, or null if failed
     */
    fun saveAudioDebug(
        context: Context,
        description: String = ""
    ): String? {
        return try {
            val audioBytes = WhisperRecordBuffer.getOutputBuffer() ?: run {
                Log.w(TAG, "No audio buffer to save")
                return null
            }

            if (audioBytes.isEmpty()) {
                Log.w(TAG, "Audio buffer is empty")
                return null
            }

            // Create debug directory
            val debugDir = File(context.cacheDir, DEBUG_AUDIO_DIR)
            debugDir.mkdirs()

            // Create timestamped filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "audio_${timestamp}${if (description.isNotEmpty()) "_$description" else ""}.wav"
            val outputFile = File(debugDir, filename)

            // Write WAV file
            val success = WavAudioWriter.writeWavFile(audioBytes, outputFile)
            
            if (success) {
                Log.i(TAG, "Audio saved to: ${outputFile.absolutePath}")
                Log.i(TAG, "File size: ${outputFile.length()} bytes")
                WavAudioWriter.logAudioStats(audioBytes)
                outputFile.absolutePath
            } else {
                Log.e(TAG, "Failed to write audio file")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving audio debug", e)
            null
        }
    }

    /**
     * Lists all saved debug audio files
     */
    fun listDebugAudioFiles(context: Context): List<File> {
        return try {
            val debugDir = File(context.cacheDir, DEBUG_AUDIO_DIR)
            if (!debugDir.exists()) {
                return emptyList()
            }
            debugDir.listFiles()?.filter { it.extension == "wav" }?.sortedByDescending { it.lastModified() } 
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error listing debug audio files", e)
            emptyList()
        }
    }

    /**
     * Clears all debug audio files
     */
    fun clearDebugAudioFiles(context: Context) {
        try {
            val debugDir = File(context.cacheDir, DEBUG_AUDIO_DIR)
            if (debugDir.exists()) {
                debugDir.deleteRecursively()
                Log.i(TAG, "Debug audio files cleared")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing debug audio files", e)
        }
    }

    /**
     * Gets the directory path for debug audio files
     */
    fun getDebugAudioDir(context: Context): File {
        return File(context.cacheDir, DEBUG_AUDIO_DIR)
    }
}


