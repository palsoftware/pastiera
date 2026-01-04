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
 * Manages OpenAI Whisper API-based speech recognition.
 * Provides high-quality transcription via OpenAI's cloud infrastructure.
 * Provides an API similar to WhisperRecognitionManager for seamless integration.
 */
class OpenAiWhisperRecognitionManager(
    private val context: Context,
    private val inputConnectionProvider: () -> InputConnection?,
    private val onError: ((String) -> Unit)? = null,
    private val onRecognitionStateChanged: ((Boolean) -> Unit)? = null,
    private val shouldDisableAutoCapitalize: () -> Boolean = { false },
    private val onAudioLevelChanged: ((Float) -> Unit)? = null
) {
    companion object {
        private const val TAG = "OpenAiWhisperRecognitionMgr"
        private const val TEMP_AUDIO_FILE = "openai_temp_audio.wav"
    }

    private var openAiClient: OpenAiWhisperClient? = null
    private var whisperRecorder: WhisperRecorder? = null
    private var isRecognizing = false
    private var isProcessing = false

    /**
     * Checks if OpenAI API is configured properly
     */
    fun isAvailable(): Boolean {
        val apiKey = SettingsManager.getOpenAiApiKey(context)
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
     * Initializes the OpenAI client
     */
    private fun ensureOpenAiClient(): Boolean {
        if (openAiClient != null) {
            return true
        }

        try {
            val apiKey = SettingsManager.getOpenAiApiKey(context)
            if (apiKey.isEmpty()) {
                Log.e(TAG, "OpenAI API key not configured")
                onError?.invoke(context.getString(R.string.openai_error_api_key_missing))
                return false
            }

            openAiClient = OpenAiWhisperClient(apiKey)
            Log.d(TAG, "OpenAI client initialized")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing OpenAI client", e)
            onError?.invoke(context.getString(R.string.openai_error_initialization))
            return false
        }
    }

    /**
     * Starts OpenAI Whisper speech recognition
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
        if (!ensureOpenAiClient()) {
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
                        Log.d(TAG, "Recording done, starting transcription via OpenAI API")
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

            Log.d(TAG, "OpenAI Whisper recognition started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting OpenAI Whisper recognition", e)
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
        Log.d(TAG, "Processing audio via OpenAI API...")

        // Run transcription in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get the recorded audio buffer
                val audioBuffer = WhisperRecordBuffer.getOutputBuffer()
                if (audioBuffer == null || audioBuffer.isEmpty()) {
                    Log.w(TAG, "No audio buffer")
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        isRecognizing = false
                        onRecognitionStateChanged?.invoke(false)
                        onError?.invoke(context.getString(R.string.speech_recognition_error_no_match))
                    }
                    return@launch
                }

                // Save audio to temporary file with proper WAV format
                val tempAudioFile = File(context.cacheDir, TEMP_AUDIO_FILE)
                WavAudioWriter.writeWavFile(audioBuffer, tempAudioFile)
                
                // Log audio statistics for debugging
                WavAudioWriter.logAudioStats(audioBuffer)

                // Get API parameters from settings
                val model = SettingsManager.getOpenAiModel(context)
                var language = SettingsManager.getOpenAiLanguage(context).takeIf { it.isNotEmpty() } ?: ""
                
                // Fall back to system language if not set
                if (language.isEmpty()) {
                    language = java.util.Locale.getDefault().language
                    Log.d(TAG, "Using system language: $language")
                }
                
                val prompt = SettingsManager.getOpenAiPrompt(context).takeIf { it.isNotEmpty() }
                val temperature = SettingsManager.getOpenAiTemperature(context)

                Log.d(TAG, "Calling OpenAI API with model=$model, language=$language, prompt=${prompt?.take(50)}...")
                Log.d(TAG, "Audio file: ${tempAudioFile.absolutePath} (${tempAudioFile.length()} bytes)")

                // Call OpenAI API
                val result = openAiClient?.transcribeAudio(
                    tempAudioFile,
                    model,
                    language,
                    prompt,
                    temperature
                )

                withContext(Dispatchers.Main) {
                    isProcessing = false
                    isRecognizing = false
                    onRecognitionStateChanged?.invoke(false)

                    if (result?.isSuccess == true) {
                        val transcriptionResult = result.getOrNull()
                        if (transcriptionResult != null && transcriptionResult.text.isNotEmpty()) {
                            Log.d(TAG, "Transcription successful: '${transcriptionResult.text}'")
                            
                            // Save usage statistics
                            try {
                                val statsManager = UsageStatsManager(context)
                                // Count words by splitting on whitespace
                                val wordCount = if (transcriptionResult.text.isEmpty()) {
                                    0
                                } else {
                                    transcriptionResult.text.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
                                }
                                // Audio duration: file size / (16000 Hz * 2 bytes per sample)
                                val recordingDurationMs = tempAudioFile.length() * 1000L / 32000L
                                
                                val stat = TranscriptionStats(
                                    id = java.util.UUID.randomUUID().toString(),
                                    engine = "openai",
                                    model = SettingsManager.getOpenAiModel(context),
                                    timestamp = java.time.LocalDateTime.now().toString(),
                                    audioLengthMs = recordingDurationMs,
                                    textLength = transcriptionResult.text.length,
                                    wordCount = wordCount,
                                    costUsd = 0.0,  // TODO: Fetch from OpenAI Usage API
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
                                
                                Log.d(TAG, "Stats saved - Model: ${stat.model}, Words: $wordCount, WPM: ${"%.1f".format(stat.getWordsPerMinute())}, Duration: ${recordingDurationMs}ms")
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
                        onError?.invoke(context.getString(R.string.openai_error_transcription, errorMsg))
                    }

                    // Clean up temporary file
                    tempAudioFile.delete()
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
        onRecognitionStateChanged?.invoke(false)
        Log.d(TAG, "OpenAI Whisper recognition stopped")
    }

    /**
     * Releases all resources
     */
    fun destroy() {
        whisperRecorder?.release()
        whisperRecorder = null
        openAiClient = null
        WhisperRecordBuffer.clear()
        isRecognizing = false
        isProcessing = false
        Log.d(TAG, "OpenAI Whisper recognition manager destroyed")
    }
}

