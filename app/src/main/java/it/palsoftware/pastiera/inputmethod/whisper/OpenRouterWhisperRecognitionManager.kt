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
import it.palsoftware.pastiera.inputmethod.whisper.UsageStatsManager
import it.palsoftware.pastiera.inputmethod.whisper.TranscriptionStats

/**
 * Manages OpenRouter Audio Speech Recognition.
 * Supports multiple audio models from OpenRouter API.
 * Provides seamless integration with model selection and pricing display.
 */
class OpenRouterWhisperRecognitionManager(
    private val context: Context,
    private val inputConnectionProvider: () -> InputConnection?,
    private val onError: ((String) -> Unit)? = null,
    private val onRecognitionStateChanged: ((Boolean) -> Unit)? = null,
    private val shouldDisableAutoCapitalize: () -> Boolean = { false },
    private val onAudioLevelChanged: ((Float) -> Unit)? = null
) {
    companion object {
        private const val TAG = "OpenRouterWhisperMgr"
    }

    private var openRouterClient: OpenRouterWhisperClient? = null
    private var whisperRecorder: WhisperRecorder? = null
    private var isRecognizing = false
    private var isProcessing = false

    /**
     * Checks if OpenRouter API is configured properly
     */
    fun isAvailable(): Boolean {
        val apiKey = SettingsManager.getOpenRouterApiKey(context)
        return apiKey.isNotEmpty()
    }

    /**
     * Formats text according to standard auto-capitalization rules
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
     * Inserts recognized text into the input connection
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
     * Initializes the OpenRouter client
     */
    private fun ensureOpenRouterClient(): Boolean {
        if (openRouterClient != null) {
            return true
        }

        try {
            val apiKey = SettingsManager.getOpenRouterApiKey(context)
            if (apiKey.isEmpty()) {
                Log.e(TAG, "OpenRouter API key not configured")
                onError?.invoke(context.getString(R.string.openai_error_api_key_missing))
                return false
            }

            openRouterClient = OpenRouterWhisperClient(apiKey)
            Log.d(TAG, "OpenRouter client initialized")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing OpenRouter client", e)
            onError?.invoke(context.getString(R.string.openai_error_initialization))
            return false
        }
    }

    /**
     * Starts OpenRouter Whisper speech recognition
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

        // Ensure client is initialized
        if (!ensureOpenRouterClient()) {
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
                        // Update audio level feedback
                        onAudioLevelChanged?.invoke(10f)
                    }

                    override fun onRecordingDone() {
                        Log.d(TAG, "Recording done, starting transcription via OpenRouter API")
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

            Log.d(TAG, "OpenRouter Whisper recognition started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting OpenRouter Whisper recognition", e)
            isRecognizing = false
            onRecognitionStateChanged?.invoke(false)
            onError?.invoke(context.getString(R.string.openai_error_generic))
        }
    }

    /**
     * Starts transcription after recording is complete
     */
    private fun startTranscription() {
        if (isProcessing) {
            Log.w(TAG, "Already processing")
            return
        }

        isProcessing = true

        // No toast - auto-stop is more intuitive
        Log.d(TAG, "Processing audio via OpenRouter API...")

        // Run transcription in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get the recorded audio buffer
                val audioByteBuffer = WhisperRecordBuffer.getOutputBuffer()
                if (audioByteBuffer == null || audioByteBuffer.isEmpty()) {
                    Log.w(TAG, "No audio buffer")
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        isRecognizing = false
                        onRecognitionStateChanged?.invoke(false)
                        onError?.invoke(context.getString(R.string.speech_recognition_error_no_match))
                    }
                    return@launch
                }

                Log.d(TAG, "Audio buffer size: ${audioByteBuffer.size} bytes")

                // Get selected model and language
                val model = SettingsManager.getOpenRouterModel(context)
                var language = SettingsManager.getOpenRouterLanguage(context).takeIf { it.isNotEmpty() }
                
                // Fall back to system language if not set
                if (language.isNullOrEmpty()) {
                    language = java.util.Locale.getDefault().language
                    Log.d(TAG, "Using system language: $language")
                }
                
                Log.d(TAG, "Calling OpenRouter API with model=$model, language=$language")
                Log.d(TAG, "Audio file size: ${audioByteBuffer.size} bytes")

                // Call OpenRouter API
                val result = openRouterClient?.transcribeAudio(
                    audioByteBuffer,
                    model,
                    language
                )

                withContext(Dispatchers.Main) {
                    stopSpeechRecognition()
                    
                    if (result?.isSuccess == true) {
                        val transcriptionResult = result.getOrNull()
                        if (transcriptionResult != null && transcriptionResult.text.isNotEmpty()) {
                            Log.d(TAG, "Transcription successful: '${transcriptionResult.text}'")
                            
                            // Save usage statistics
                            try {
                                val statsManager = UsageStatsManager(context)
                                // Count words by counting spaces + 1 (if text is not empty)
                                val wordCount = if (transcriptionResult.text.isEmpty()) {
                                    0
                                } else {
                                    transcriptionResult.text.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
                                }
                                // Audio duration: audioByteBuffer size / (16000 Hz * 2 bytes per sample)
                                val audioLengthMs = audioByteBuffer?.size?.toLong()?.times(1000L)?.div(32000L) ?: 0L
                                
                                val stat = TranscriptionStats(
                                    id = java.util.UUID.randomUUID().toString(),
                                    engine = "openrouter",
                                    model = transcriptionResult.model,
                                    timestamp = java.time.LocalDateTime.now().toString(),
                                    audioLengthMs = audioLengthMs,
                                    textLength = transcriptionResult.text.length,
                                    wordCount = wordCount,
                                    costUsd = transcriptionResult.costUsd,
                                    successFul = true
                                )
                                statsManager.addStat(stat)
                                
                                // Also save per-model breakdown
                                val prefs = context.getSharedPreferences("usage_stats", android.content.Context.MODE_PRIVATE)
                                val modelKey = "model_${stat.model}".replace("/", "_").replace("-", "_")
                                val currentCount = prefs.getLong("${modelKey}_count", 0L)
                                val currentWords = prefs.getLong("${modelKey}_words", 0L)
                                val currentCost = prefs.getFloat("${modelKey}_cost", 0f)
                                
                                prefs.edit().apply {
                                    putLong("${modelKey}_count", currentCount + 1)
                                    putLong("${modelKey}_words", currentWords + wordCount)
                                    putFloat("${modelKey}_cost", (currentCost + stat.costUsd).toFloat())
                                    apply()
                                }
                                
                                Log.d(TAG, "Stats saved - Model: ${stat.model}, Words: $wordCount, WPM: ${"%.1f".format(stat.getWordsPerMinute())}, Cost: $${stat.costUsd}")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error saving stats: ${e.message}")
                            }
                            
                            insertRecognizedText(transcriptionResult.text)
                        } else {
                            Log.w(TAG, "No transcription result")
                            onError?.invoke(context.getString(R.string.speech_recognition_error_no_match))
                        }
                    } else {
                        val errorMsg = result?.exceptionOrNull()?.message ?: "Unknown error"
                        Log.e(TAG, "Transcription failed: $errorMsg")
                        onError?.invoke(context.getString(R.string.openai_error_generic))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during transcription", e)
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    isRecognizing = false
                    onRecognitionStateChanged?.invoke(false)
                    onError?.invoke(context.getString(R.string.openai_error_generic))
                }
            }
        }
    }

    /**
     * Stops recognition if active
     */
    fun stopRecognition() {
        whisperRecorder?.stop()
        isRecognizing = false
        isProcessing = false
        onRecognitionStateChanged?.invoke(false)
        Log.d(TAG, "OpenRouter Whisper recognition stopped")
    }

    /**
     * Stops speech recognition (internal)
     */
    private fun stopSpeechRecognition() {
        whisperRecorder?.stop()
        isRecognizing = false
        isProcessing = false
        onRecognitionStateChanged?.invoke(false)
    }

    /**
     * Releases all resources
     */
    fun destroy() {
        whisperRecorder?.release()
        whisperRecorder = null
        WhisperRecordBuffer.clear()
        isRecognizing = false
        isProcessing = false
        Log.d(TAG, "OpenRouter Whisper recognition manager destroyed")
    }
}

