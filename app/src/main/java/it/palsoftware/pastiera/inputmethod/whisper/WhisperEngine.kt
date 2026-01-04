package it.palsoftware.pastiera.inputmethod.whisper

import android.content.Context
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer

/**
 * Whisper inference engine using TensorFlow Lite.
 * Handles model loading, audio processing, and transcription.
 */
class WhisperEngine(private val context: Context) {
    companion object {
        private const val TAG = "WhisperEngine"
        private const val WHISPER_SAMPLE_RATE = 16000
        private const val WHISPER_CHUNK_SIZE = 30 // seconds
        private const val MEL_BINS = 80
        private const val N_FFT = 400
        private const val HOP_LENGTH = 160
        
        // Special tokens
        private const val TOKEN_EOT = 50256 // End of text
        private const val TOKEN_TRANSCRIBE = 50358
        private const val TOKEN_TRANSLATE = 50357
        private const val TOKEN_NOTIMESTAMPS = 50363
    }

    private var interpreter: Interpreter? = null
    private var isInitialized = false
    private var isMultilingual = false
    private val whisperUtil = WhisperUtil(context)

    /**
     * Loads and initializes the Whisper model.
     */
    fun initialize(modelFile: File, vocabFile: File, isMultilingual: Boolean) {
        try {
            // Load model
            val modelBuffer = loadModelFile(modelFile)
            
            // Configure TensorFlow Lite interpreter
            val options = Interpreter.Options().apply {
                useXNNPACK = false // Cannot use XNNPACK with dynamic tensors
                numThreads = Runtime.getRuntime().availableProcessors()
                setCancellable(true)
            }
            
            interpreter = Interpreter(modelBuffer, options)
            this.isMultilingual = isMultilingual
            
            // Load vocabulary and filters
            whisperUtil.loadFiltersAndVocab(isMultilingual, vocabFile)
            
            isInitialized = true
            Log.d(TAG, "Whisper engine initialized: ${modelFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Whisper engine", e)
            isInitialized = false
            throw e
        }
    }

    /**
     * Loads model file into ByteBuffer.
     */
    private fun loadModelFile(modelFile: File): ByteBuffer {
        FileInputStream(modelFile).use { inputStream ->
            val fileChannel = inputStream.channel
            val startOffset = 0L
            val declaredLength = fileChannel.size()
            return fileChannel.map(
                java.nio.channels.FileChannel.MapMode.READ_ONLY,
                startOffset,
                declaredLength
            )
        }
    }

    /**
     * Processes audio from WhisperRecordBuffer and returns transcription.
     */
    fun processRecordBuffer(languageToken: Int = -1): WhisperResult? {
        if (!isInitialized) {
            Log.e(TAG, "Engine not initialized")
            return null
        }

        val samples = WhisperRecordBuffer.getSamples()
        if (samples == null || samples.isEmpty()) {
            Log.e(TAG, "No audio samples available")
            return null
        }

        try {
            val startTime = System.currentTimeMillis()
            
            // Calculate Mel spectrogram
            Log.d(TAG, "Calculating Mel spectrogram...")
            val melSpectrogram = getMelSpectrogram(samples)
            
            // Run inference
            Log.d(TAG, "Running inference...")
            val result = runInference(melSpectrogram, languageToken)
            
            val timeTaken = System.currentTimeMillis() - startTime
            Log.d(TAG, "Transcription completed in ${timeTaken}ms")
            
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio", e)
            return null
        }
    }

    /**
     * Calculates Mel spectrogram from audio samples.
     */
    private fun getMelSpectrogram(samples: FloatArray): FloatArray {
        val fixedInputSize = WHISPER_SAMPLE_RATE * WHISPER_CHUNK_SIZE
        val inputSamples = FloatArray(fixedInputSize)
        
        // Copy available samples, pad with zeros if needed
        val copyLength = minOf(samples.size, fixedInputSize)
        System.arraycopy(samples, 0, inputSamples, 0, copyLength)
        
        val cores = Runtime.getRuntime().availableProcessors()
        return whisperUtil.getMelSpectrogram(inputSamples, copyLength, cores)
    }

    /**
     * Runs TensorFlow Lite inference on Mel spectrogram.
     */
    private fun runInference(melSpectrogram: FloatArray, languageToken: Int): WhisperResult {
        val interpreter = this.interpreter ?: throw IllegalStateException("Interpreter not initialized")
        
        // Get input and output tensors
        val inputTensor = interpreter.getInputTensor(0)
        val outputTensor = interpreter.getOutputTensor(0)
        
        // Prepare input buffer
        val inputSize = inputTensor.shape()[0] * inputTensor.shape()[1] * inputTensor.shape()[2] * Float.SIZE_BYTES / 8
        val inputBuffer = ByteBuffer.allocateDirect(inputSize).apply {
            order(ByteOrder.nativeOrder())
            melSpectrogram.forEach { putFloat(it) }
            rewind()
        }
        
        // Prepare output buffer
        val outputBuffer = TensorBuffer.createFixedSize(outputTensor.shape(), DataType.FLOAT32)
        
        // Select appropriate signature based on language token
        val signatureKey = when {
            languageToken != -1 && "serving_transcribe_lang" in interpreter.signatureKeys -> "serving_transcribe_lang"
            "serving_transcribe" in interpreter.signatureKeys -> "serving_transcribe"
            else -> "serving_default"
        }
        
        Log.d(TAG, "Using signature: $signatureKey")
        
        // Prepare inputs
        val inputsMap = mutableMapOf<String, Any>()
        val inputs = interpreter.getSignatureInputs(signatureKey)
        inputsMap[inputs[0]] = inputBuffer
        
        // Add language token if using serving_transcribe_lang
        if (signatureKey == "serving_transcribe_lang" && inputs.size > 1) {
            val langTokenBuffer = IntBuffer.allocate(1).apply {
                put(languageToken)
                rewind()
            }
            inputsMap[inputs[1]] = langTokenBuffer
            Log.d(TAG, "Using language token: $languageToken")
        }
        
        // Prepare outputs
        val outputsMap = mutableMapOf<String, Any>()
        val outputs = interpreter.getSignatureOutputs(signatureKey)
        outputsMap[outputs[0]] = outputBuffer.buffer
        
        // Run inference
        try {
            interpreter.runSignature(inputsMap, outputsMap, signatureKey)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            return WhisperResult("", "", 0f)
        }
        
        // Decode output tokens to text
        return decodeTokens(outputBuffer)
    }

    /**
     * Decodes output tokens from the model into text.
     */
    private fun decodeTokens(outputBuffer: TensorBuffer): WhisperResult {
        val tokens = mutableListOf<Int>()
        var detectedLanguage = ""
        val outputLen = outputBuffer.intArray.size
        
        Log.d(TAG, "Decoding ${outputLen} tokens")
        
        outputBuffer.buffer.rewind()
        
        for (i in 0 until outputLen) {
            val token = outputBuffer.buffer.int
            
            if (token == TOKEN_EOT) {
                Log.d(TAG, "End of text token reached at position $i")
                break
            }
            
            // Handle special tokens
            when {
                token == TOKEN_TRANSCRIBE -> {
                    Log.d(TAG, "Transcription task detected")
                }
                token == TOKEN_TRANSLATE -> {
                    Log.d(TAG, "Translation task detected")
                }
                token in 50259..50357 -> {
                    // Language token
                    detectedLanguage = whisperUtil.getLanguageFromToken(token)
                    Log.d(TAG, "Detected language: $detectedLanguage (token $token)")
                }
                token < TOKEN_EOT -> {
                    // Regular text token
                    tokens.add(token)
                }
                else -> {
                    Log.d(TAG, "Skipping special token: $token")
                }
            }
        }
        
        // Convert tokens to text
        val textBytes = mutableListOf<Byte>()
        for (token in tokens) {
            val wordBytes = whisperUtil.getWordFromToken(token)
            textBytes.addAll(wordBytes.toList())
        }
        
        val text = String(textBytes.toByteArray(), Charsets.UTF_8).trim()
        Log.d(TAG, "Decoded text: '$text'")
        
        return WhisperResult(text, detectedLanguage, 1.0f)
    }

    /**
     * Releases all resources.
     */
    fun deinitialize() {
        interpreter?.apply {
            setCancelled(true)
            close()
        }
        interpreter = null
        isInitialized = false
        Log.d(TAG, "Whisper engine deinitialized")
    }

    fun isInitialized(): Boolean = isInitialized
}

