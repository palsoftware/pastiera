package it.palsoftware.pastiera.inputmethod.whisper

import android.Manifest
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.core.content.ContextCompat
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.AutoCapitalizeHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Manages Whisper-based speech recognition.
 * Provides an API similar to SpeechRecognitionManager for seamless integration.
 */
class WhisperRecognitionManager(
    private val context: Context,
    private val inputConnectionProvider: () -> InputConnection?,
    private val onError: ((String) -> Unit)? = null,
    private val onRecognitionStateChanged: ((Boolean) -> Unit)? = null,
    private val shouldDisableAutoCapitalize: () -> Boolean = { false },
    private val onAudioLevelChanged: ((Float) -> Unit)? = null
) {
    companion object {
        private const val TAG = "WhisperRecognitionMgr"
        private const val MODELS_DIR = "whisper_models"
    }

    private var whisperEngine: WhisperEngine? = null
    private var whisperRecorder: WhisperRecorder? = null
    private var isRecognizing = false
    private var isProcessing = false

    /**
     * Checks if Whisper is available (model is downloaded).
     */
    fun isAvailable(): Boolean {
        val selectedModel = SettingsManager.getWhisperModel(context)
        val modelFile = getModelFile(selectedModel)
        val vocabFile = getVocabFile(selectedModel)
        return modelFile.exists() && vocabFile.exists()
    }

    /**
     * Gets the model file for the selected Whisper model.
     */
    private fun getModelFile(model: WhisperModel): File {
        val modelsDir = File(context.getExternalFilesDir(null), MODELS_DIR)
        return File(modelsDir, model.fileName)
    }

    /**
     * Gets the vocab file for the selected Whisper model.
     */
    private fun getVocabFile(model: WhisperModel): File {
        val modelsDir = File(context.getExternalFilesDir(null), MODELS_DIR)
        val vocabFileName = if (model.isMultilingual) {
            "filters_vocab_multilingual.bin"
        } else {
            "filters_vocab_en.bin"
        }
        return File(modelsDir, vocabFileName)
    }

    /**
     * Formats text according to standard auto-capitalization rules.
     */
    private fun formatTextWithAutoCapitalization(text: String): String {
        if (text.isEmpty()) return text
        
        val inputConnection = inputConnectionProvider() ?: return text
        
        if (shouldDisableAutoCapitalize()) {
            return text
        }
        
        var formatted = text
        
        // Capitalize first letter if needed
        val shouldCapitalizeFirst = AutoCapitalizeHelper.shouldAutoCapitalizeAtCursor(
            context = context,
            inputConnection = inputConnection,
            shouldDisableAutoCapitalize = shouldDisableAutoCapitalize()
        ) && SettingsManager.getAutoCapitalizeFirstLetter(context)
        
        if (shouldCapitalizeFirst && formatted.isNotEmpty()) {
            formatted = formatted.replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) 
                else it.toString() 
            }
        }
        
        // Capitalize after sentence-ending punctuation
        if (SettingsManager.getAutoCapitalizeAfterPeriod(context)) {
            formatted = formatted.replace(Regex("([.!?]\\s+)([a-z])")) { matchResult ->
                matchResult.groupValues[1] + matchResult.groupValues[2].uppercase()
            }
        }
        
        return formatted
    }

    /**
     * Inserts recognized text into the input connection.
     */
    private fun insertRecognizedText(text: String) {
        Handler(Looper.getMainLooper()).post {
            val inputConnection = inputConnectionProvider() ?: return@post
            
            try {
                var textToCommit = formatTextWithAutoCapitalization(text)
                
                // Add spacing rules
                val textBeforeCursor = inputConnection.getTextBeforeCursor(10, 0)
                if (textBeforeCursor != null && textBeforeCursor.isNotEmpty()) {
                    val lastChar = textBeforeCursor.last()
                    if (lastChar.isLetter()) {
                        textToCommit = " $textToCommit"
                    }
                }
                
                // Always add space at the end
                textToCommit += " "
                
                inputConnection.commitText(textToCommit, 1)
                Log.d(TAG, "Inserted recognized text: '$textToCommit'")
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting text", e)
            }
        }
    }

    /**
     * Initializes the Whisper engine with the selected model.
     */
    private fun ensureWhisperEngine(): Boolean {
        if (whisperEngine?.isInitialized() == true) {
            return true
        }

        try {
            val selectedModel = SettingsManager.getWhisperModel(context)
            val modelFile = getModelFile(selectedModel)
            val vocabFile = getVocabFile(selectedModel)

            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found: ${modelFile.absolutePath}")
                onError?.invoke(context.getString(R.string.whisper_error_model_not_found))
                return false
            }

            if (!vocabFile.exists()) {
                Log.e(TAG, "Vocab file not found: ${vocabFile.absolutePath}")
                onError?.invoke(context.getString(R.string.whisper_error_model_not_found))
                return false
            }

            whisperEngine = WhisperEngine(context)
            whisperEngine?.initialize(modelFile, vocabFile, selectedModel.isMultilingual)
            
            Log.d(TAG, "Whisper engine initialized with model: ${selectedModel.displayName}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Whisper engine", e)
            onError?.invoke(context.getString(R.string.whisper_error_initialization))
            return false
        }
    }

    /**
     * Starts Whisper speech recognition.
     */
    fun startRecognition() {
        // Check RECORD_AUDIO permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            onError?.invoke(context.getString(R.string.speech_recognition_error_permission))
            return
        }

        if (isRecognizing) {
            Log.w(TAG, "Already recognizing")
            return
        }

        // Ensure engine is initialized
        if (!ensureWhisperEngine()) {
            return
        }

        try {
            isRecognizing = true
            onRecognitionStateChanged?.invoke(true)

            // Initialize recorder
            whisperRecorder = WhisperRecorder(context).apply {
                setListener(object : WhisperRecorder.RecorderListener {
                    override fun onRecordingStarted() {
                        Log.d(TAG, "Recording started")
                    }

                    override fun onRecording() {
                        // Update audio level feedback (simplified)
                        onAudioLevelChanged?.invoke(10f)
                    }

                    override fun onRecordingDone() {
                        Log.d(TAG, "Recording done, starting transcription")
                        onAudioLevelChanged?.invoke(-20f)
                        startTranscription()
                    }

                    override fun onRecordingError(error: String) {
                        Log.e(TAG, "Recording error: $error")
                        isRecognizing = false
                        onRecognitionStateChanged?.invoke(false)
                        onError?.invoke(error)
                    }
                })
                
                initVad()
                start()
            }

            Log.d(TAG, "Whisper recognition started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Whisper recognition", e)
            isRecognizing = false
            onRecognitionStateChanged?.invoke(false)
            onError?.invoke(context.getString(R.string.whisper_error_generic))
        }
    }

    /**
     * Starts transcription after recording is complete.
     */
    private fun startTranscription() {
        if (isProcessing) {
            Log.w(TAG, "Already processing")
            return
        }

        isProcessing = true

        // Show "processing" toast
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, R.string.whisper_processing, Toast.LENGTH_SHORT).show()
        }

        // Run transcription in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get language token for better recognition
                val languageToken = getLanguageToken()
                
                // Process audio
                val result = whisperEngine?.processRecordBuffer(languageToken)
                
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    isRecognizing = false
                    onRecognitionStateChanged?.invoke(false)

                    if (result != null && result.text.isNotEmpty()) {
                        Log.d(TAG, "Transcription result: '${result.text}' (language: ${result.language})")
                        insertRecognizedText(result.text)
                    } else {
                        Log.w(TAG, "No transcription result")
                        onError?.invoke(context.getString(R.string.speech_recognition_error_no_match))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during transcription", e)
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    isRecognizing = false
                    onRecognitionStateChanged?.invoke(false)
                    onError?.invoke(context.getString(R.string.whisper_error_generic))
                }
            }
        }
    }

    /**
     * Gets language token based on device locale.
     */
    private fun getLanguageToken(): Int {
        val locale = context.resources.configuration.locales[0]
        val languageCode = locale?.language ?: "auto"
        
        // Map language codes to Whisper tokens (50259+)
        return when (languageCode) {
            "en" -> 50259
            "de" -> 50261
            "es" -> 50262
            "fr" -> 50265
            "it" -> 50274
            "pl" -> 50269
            else -> -1 // Auto-detect
        }
    }

    /**
     * Stops recognition if active.
     */
    fun stopRecognition() {
        whisperRecorder?.stop()
        isRecognizing = false
        onRecognitionStateChanged?.invoke(false)
        Log.d(TAG, "Whisper recognition stopped")
    }

    /**
     * Releases all resources.
     */
    fun destroy() {
        whisperRecorder?.release()
        whisperRecorder = null
        whisperEngine?.deinitialize()
        whisperEngine = null
        WhisperRecordBuffer.clear()
        isRecognizing = false
        isProcessing = false
        Log.d(TAG, "Whisper recognition manager destroyed")
    }
}

