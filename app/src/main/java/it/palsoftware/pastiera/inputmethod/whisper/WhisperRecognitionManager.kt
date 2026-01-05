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
 * Manages Whisper-based speech recognition using ONNX Runtime.
 * Uses DocWolle's optimized ONNX models from HuggingFace.
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
        private const val TAG = "WhisperOnnxRecognitionMgr"
    }

    private var onnxManager: WhisperOnnxManager? = null
    private var transcriber: WhisperTranscriber? = null
    private var whisperRecorder: WhisperRecorder? = null
    private var isRecognizing = false
    private var isProcessing = false

    /**
     * Checks if Whisper ONNX models are available.
     */
    fun isAvailable(): Boolean {
        // Check if models are downloaded
        val selectedModel = SettingsManager.getWhisperModel(context)
        val downloader = WhisperModelDownloader(context)
        return downloader.isModelDownloaded(selectedModel)
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
     * Initializes ONNX Manager and Transcriber.
     */
    private fun ensureOnnxManager(): Boolean {
        if (onnxManager != null && transcriber != null) {
            return true
        }

        try {
            // Check if models are actually downloaded
            if (!areModelsDownloaded()) {
                Log.e(TAG, "Whisper models not downloaded yet")
                onError?.invoke(context.getString(R.string.whisper_error_model_not_found))
                return false
            }
            
            onnxManager = WhisperOnnxManager.getInstance(context)
            val initResult = onnxManager!!.initialize()
            
            if (!initResult.isSuccess) {
                Log.e(TAG, "Failed to initialize ONNX Manager: ${initResult.exceptionOrNull()?.message}")
                onError?.invoke(context.getString(R.string.whisper_error_model_not_found))
                return false
            }
            
            transcriber = WhisperTranscriber(onnxManager!!)
            Log.d(TAG, "ONNX Manager and Transcriber initialized")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ONNX Manager", e)
            onError?.invoke(context.getString(R.string.whisper_error_initialization))
            return false
        }
    }
    
    /**
     * Checks if Whisper models are downloaded.
     */
    private fun areModelsDownloaded(): Boolean {
        val selectedModel = SettingsManager.getWhisperModel(context)
        val downloader = WhisperModelDownloader(context)
        
        val isDownloaded = downloader.isModelDownloaded(selectedModel)
        Log.d(TAG, "Model $selectedModel downloaded: $isDownloaded")
        
        return isDownloaded
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

        // Ensure ONNX Manager is initialized
        if (!ensureOnnxManager()) {
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
                        onAudioLevelChanged?.invoke(10f)
                    }

                    override fun onRecordingDone() {
                        Log.d(TAG, "Recording done, starting ONNX transcription")
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

            Log.d(TAG, "Whisper ONNX recognition started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Whisper ONNX recognition", e)
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
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Get the recorded audio buffer (ByteArray with WAV data)
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

                // Convert ByteArray (WAV) to FloatArray (PCM samples)
                val audioBuffer = convertWavToFloatArray(audioByteBuffer)

                // Get selected language (use OpenAI language setting as default)
                val languageCode = SettingsManager.getOpenAiLanguage(context)
                
                Log.d(TAG, "Calling WhisperTranscriber with language=$languageCode")

                // Call ONNX Transcriber
                val result = transcriber?.transcribe(audioBuffer, languageCode)

                withContext(Dispatchers.Main) {
                    isProcessing = false
                    isRecognizing = false
                    onRecognitionStateChanged?.invoke(false)

                    if (result?.isSuccess == true) {
                        val text = result.getOrNull()
                        if (!text.isNullOrEmpty()) {
                            Log.d(TAG, "Transcription successful: '$text'")
                            insertRecognizedText(text)
                        } else {
                            Log.w(TAG, "Empty transcription result")
                            onError?.invoke(context.getString(R.string.speech_recognition_error_no_match))
                        }
                    } else {
                        val errorMsg = result?.exceptionOrNull()?.message ?: "Unknown error"
                        Log.e(TAG, "Transcription failed: $errorMsg")
                        onError?.invoke(context.getString(R.string.whisper_error_generic))
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
     * Stops recognition if active.
     */
    fun stopRecognition() {
        whisperRecorder?.stop()
        isRecognizing = false
        onRecognitionStateChanged?.invoke(false)
        Log.d(TAG, "Whisper ONNX recognition stopped")
    }

    /**
     * Converts WAV ByteArray to PCM FloatArray.
     * Assumes 16-bit PCM, mono, 16kHz sample rate.
     */
    private fun convertWavToFloatArray(wavData: ByteArray): FloatArray {
        // Skip WAV header (44 bytes for standard WAV file)
        val dataStartIdx = 44
        if (wavData.size <= dataStartIdx) {
            return FloatArray(0)
        }

        // Extract 16-bit PCM samples
        val numSamples = (wavData.size - dataStartIdx) / 2
        val floatArray = FloatArray(numSamples)

        var floatIndex = 0
        var byteIndex = dataStartIdx

        while (byteIndex < wavData.size - 1) {
            // Convert 16-bit little-endian to float (-1.0 to 1.0)
            val loByte = wavData[byteIndex].toInt() and 0xFF
            val hiByte = (wavData[byteIndex + 1].toInt() and 0xFF) shl 8

            val sample = (hiByte or loByte).toShort().toFloat() / 32768.0f
            floatArray[floatIndex] = sample

            floatIndex++
            byteIndex += 2
        }

        Log.d(TAG, "Converted WAV ${wavData.size} bytes to ${floatArray.size} float samples")
        return floatArray
    }

    /**
     * Releases all resources.
     */
    fun destroy() {
        whisperRecorder?.release()
        whisperRecorder = null
        WhisperRecordBuffer.clear()
        isRecognizing = false
        isProcessing = false
        Log.d(TAG, "Whisper ONNX recognition manager destroyed")
    }
}
