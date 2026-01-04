package it.palsoftware.pastiera.inputmethod.whisper

import android.util.Log
import ai.onnxruntime.*
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.max

/**
 * Transcribes audio using Whisper ONNX models.
 * Implements the complete 5-step pipeline from RTranslator:
 * 1. Initializer (Audio → Mel-Spectrogram)
 * 2. Encoder (Mel → Hidden States)
 * 3. Cache Initializer
 * 4. Decoder Loop (Token Generation with KV-Cache)
 * 5. Detokenizer (Tokens → Text)
 */
class WhisperTranscriber(private val onnxManager: WhisperOnnxManager) {
    
    private val TAG = "WhisperTranscriber"
    
    // Token IDs from Whisper spec
    private val START_TOKEN_ID = 50258
    private val TRANSCRIBE_TOKEN_ID = 50359
    private val TRANSLATE_TOKEN_ID = 50358
    private val NO_TIMESTAMPS_TOKEN_ID = 50363
    private val EOS_TOKEN_ID = 50257
    private val MAX_TOKENS = 445
    private val MAX_TOKENS_PER_SECOND = 30
    
    // Language codes (same as RTranslator)
    private val LANGUAGES = arrayOf(
        "en", "zh", "de", "es", "ru", "ko", "fr", "ja", "pt", "tr",
        "pl", "ca", "nl", "ar", "sv", "it", "id", "hi", "fi", "vi",
        "he", "uk", "el", "ms", "cs", "ro", "da", "hu", "ta", "no",
        "th", "ur", "hr", "bg", "lt", "la", "mi", "ml", "cy", "sk",
        "te", "fa", "lv", "bn", "sr", "az", "sl", "kn", "et", "mk",
        "br", "eu", "is", "hy", "ne", "mn", "bs", "kk", "sq", "sw",
        "gl", "mr", "pa", "si", "km", "sn", "yo", "so", "af", "oc",
        "ka", "be", "tg", "sd", "gu", "am", "yi", "lo", "uz", "fo",
        "ht", "ps", "tk", "nn", "mt", "sa", "lb", "my", "bo", "tl",
        "mg", "as", "tt", "haw", "ln", "ha", "ba", "jw", "su", "yue"
    )
    
    suspend fun transcribe(
        audioData: FloatArray,
        languageCode: String = "de"
    ): Result<String> = try {
        Log.d(TAG, "Starting transcription for $languageCode (${audioData.size} samples)")
        
        val env = onnxManager.getOnnxEnv() ?: return Result.failure(Exception("ONNX Environment not initialized"))
        val startTime = System.currentTimeMillis()
        
        // Step 1: Initializer (Audio PCM → Mel-Spectrogram)
        Log.d(TAG, "[1/5] Initializer: Audio → Mel-Spectrogram")
        val floatBuffer = FloatBuffer.wrap(audioData)
        val audioTensor = OnnxTensor.createTensor(env, floatBuffer, longArrayOf(1, audioData.size.toLong()))
        
        val initOutputs = onnxManager.getInitSession()!!.run(
            mapOf("audio_pcm" to audioTensor)
        )
        val melSpectrogram = initOutputs.get(0) as OnnxTensor
        audioTensor.close()
        
        // Step 2: Encoder (Mel-Spectrogram → Hidden States)
        Log.d(TAG, "[2/5] Encoder: Mel → Hidden States")
        val encoderOutputs = onnxManager.getEncoderSession()!!.run(
            mapOf("input_features" to melSpectrogram)
        )
        val encoderHiddenStates = encoderOutputs.get(0) as OnnxTensor
        melSpectrogram.close()
        
        // Step 3: Cache Initializer
        Log.d(TAG, "[3/5] Cache Initializer")
        val cacheInitInputs = mapOf("encoder_hidden_states" to encoderHiddenStates)
        val cacheInitOutputs = onnxManager.getCacheInitSession()!!.run(cacheInitInputs)
        
        // Step 4: Decoder Loop (Token Generation)
        Log.d(TAG, "[4/5] Decoder Loop: Token Generation")
        val tokens = decoderLoop(env, languageCode, encoderHiddenStates, cacheInitOutputs)
        
        // Step 5: Detokenizer (Tokens → Text)
        Log.d(TAG, "[5/5] Detokenizer: Tokens → Text")
        val finalText = detokenize(env, tokens)
        
        val processingTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "✅ Transcription complete in ${processingTime}ms")
        Log.d(TAG, "Result: '$finalText'")
        
        // Cleanup
        encoderOutputs.close()
        encoderHiddenStates.close()
        cacheInitOutputs.close()
        
        Result.success(finalText.trim())
    } catch (e: Exception) {
        Log.e(TAG, "Transcription error: ${e.message}", e)
        Result.failure(e)
    }
    
    /**
     * Executes the decoder loop to generate tokens.
     * This is the main iterative part (similar to RTranslator).
     */
    private fun decoderLoop(
        env: OrtEnvironment,
        languageCode: String,
        encoderOutput: OnnxTensor,
        cacheInitResult: OrtSession.Result
    ): IntArray {
        val generatedTokens = mutableListOf<Int>()
        val maxTokens = (max(audioSamplesFromEncoder(encoderOutput), 1) / 16000) * MAX_TOKENS_PER_SECOND
        val effectiveMaxTokens = minOf(maxTokens, MAX_TOKENS)
        
        Log.d(TAG, "Decoder: maxTokens=$effectiveMaxTokens")
        
        // Get language token ID
        val languageID = getLanguageID(languageCode)
        Log.d(TAG, "Language: $languageCode → ID: $languageID")
        
        // Initial tokens
        val initialTokens = intArrayOf(
            START_TOKEN_ID,
            languageID,
            TRANSCRIBE_TOKEN_ID,  // Not translate, just transcribe
            NO_TIMESTAMPS_TOKEN_ID
        )
        
        var decoderResult: OrtSession.Result? = cacheInitResult
        var isFirstIteration = true
        var tokenCount = 0
        
        while (tokenCount < effectiveMaxTokens) {
            var inputToken: Int
            var inputIDsTensor: OnnxTensor
            
            if (tokenCount < 4) {
                // Use initial tokens
                inputToken = initialTokens[tokenCount]
                inputIDsTensor = convertIntToTensor(env, intArrayOf(inputToken))
            } else {
                // Use previously generated token
                inputToken = generatedTokens.last()
                inputIDsTensor = convertIntToTensor(env, intArrayOf(inputToken))
            }
            
            // Check for EOS
            if (inputToken == EOS_TOKEN_ID) {
                Log.d(TAG, "EOS token reached at position $tokenCount")
                break
            }
            
            // Prepare decoder input
            val decoderInputMap = mutableMapOf<String, OnnxTensor>()
            decoderInputMap["input_ids"] = inputIDsTensor
            
            // Add KV-Cache (from previous iteration or init)
            try {
                for (i in 0 until 12) {
                    decoderInputMap["past_key_values.$i.decoder.key"] = 
                        if (isFirstIteration) {
                            createEmptyTensor(env, longArrayOf(1, 12, 0, 64))
                        } else {
                            decoderResult!!.get("present.$i.decoder.key").get() as OnnxTensor
                        }
                    
                    decoderInputMap["past_key_values.$i.decoder.value"] = 
                        if (isFirstIteration) {
                            createEmptyTensor(env, longArrayOf(1, 12, 0, 64))
                        } else {
                            decoderResult!!.get("present.$i.decoder.value").get() as OnnxTensor
                        }
                    
                    decoderInputMap["past_key_values.$i.encoder.key"] = 
                        cacheInitResult.get("present.$i.encoder.key").get() as OnnxTensor
                    
                    decoderInputMap["past_key_values.$i.encoder.value"] = 
                        cacheInitResult.get("present.$i.encoder.value").get() as OnnxTensor
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error setting KV-Cache at step $tokenCount: ${e.message}")
            }
            
            // Run decoder
            val oldResult = decoderResult
            decoderResult = onnxManager.getDecoderSession()!!.run(decoderInputMap)
            
            // Extract logits and get max token
            val logits = (decoderResult.get("logits") as OnnxTensor).getValue() as FloatArray
            val nextToken = getMaxTokenIndex(logits)
            
            if (nextToken != EOS_TOKEN_ID) {
                generatedTokens.add(nextToken)
            }
            
            // Cleanup
            inputIDsTensor.close()
            if (oldResult != null && tokenCount > 0) {
                try {
                    oldResult.close()
                } catch (e: Exception) {
                    Log.d(TAG, "Error closing result: ${e.message}")
                }
            }
            
            isFirstIteration = false
            tokenCount++
            
            if (tokenCount % 50 == 0) {
                Log.d(TAG, "Decoder progress: $tokenCount tokens")
            }
        }
        
        Log.d(TAG, "Decoder finished: $tokenCount iterations, ${generatedTokens.size} tokens")
        return generatedTokens.toIntArray()
    }
    
    /**
     * Converts int array to tensor for decoder input.
     */
    private fun convertIntToTensor(env: OrtEnvironment, values: IntArray): OnnxTensor {
        return OnnxTensor.createTensor(env, IntBuffer.wrap(values), longArrayOf(1, 1))
    }
    
    /**
     * Creates empty tensor for KV-Cache.
     */
    private fun createEmptyTensor(env: OrtEnvironment, shape: LongArray): OnnxTensor {
        val flat_length = shape.fold(1L) { acc, d -> acc * d }
        val buffer = java.nio.ByteBuffer.allocateDirect((flat_length * 4).toInt()).asFloatBuffer()
        return OnnxTensor.createTensor(env, buffer, shape)
    }
    
    /**
     * Gets the index of the maximum value in the logits array.
     */
    private fun getMaxTokenIndex(logits: FloatArray): Int {
        var maxIdx = 0
        var maxVal = logits[0]
        for (i in 1 until logits.size) {
            if (logits[i] > maxVal) {
                maxVal = logits[i]
                maxIdx = i
            }
        }
        return maxIdx
    }
    
    /**
     * Estimates audio length from encoder output shape.
     */
    private fun audioSamplesFromEncoder(tensor: OnnxTensor): Int {
        return try {
            val value = tensor.getValue() as Array<*>
            when {
                value.isNotEmpty() && value[0] is Array<*> -> {
                    val arr = value[0] as Array<*>
                    arr.size * 160  // Approximate: each frame = 160 samples
                }
                else -> 16000  // Default to 1 second
            }
        } catch (e: Exception) {
            16000
        }
    }
    
    /**
     * Detokenizes the generated token sequence to text.
     */
    private fun detokenize(env: OrtEnvironment, tokens: IntArray): String {
        return try {
            Log.d(TAG, "Detokenizing ${tokens.size} tokens")
            
            val tensorInput = OnnxTensor.createTensor(
                env,
                IntBuffer.wrap(tokens),
                longArrayOf(1, 1, tokens.size.toLong())
            )
            
            val detokenizerInputs = mapOf("sequences" to tensorInput)
            val detokenizerOutput = onnxManager.getDetokenizerSession()!!.run(detokenizerInputs)
            
            val textArray = detokenizerOutput.get(0).getValue() as Array<Array<String>>
            val rawText = textArray[0][0]
            
            tensorInput.close()
            detokenizerOutput.close()
            
            correctText(rawText)
        } catch (e: Exception) {
            Log.e(TAG, "Detokenization error: ${e.message}", e)
            ""
        }
    }
    
    /**
     * Post-processes transcribed text (remove timestamps, capitalize, etc).
     */
    private fun correctText(text: String): String {
        var corrected = text
        
        // Remove timestamps like <|0.00|>
        corrected = corrected.replace(Regex("<\\|[^>]*\\|> "), "")
        
        // Trim whitespace
        corrected = corrected.trim()
        
        if (corrected.length >= 2) {
            // Capitalize first letter
            if (corrected[0].isLowerCase()) {
                corrected = corrected[0].uppercaseChar() + corrected.substring(1)
            }
            
            // Remove ellipsis
            corrected = corrected.replace("...", "")
        }
        
        return corrected
    }
    
    /**
     * Maps language code to Whisper token ID.
     */
    private fun getLanguageID(languageCode: String): Int {
        val code = languageCode.lowercase()
        for (i in LANGUAGES.indices) {
            if (LANGUAGES[i] == code) {
                return START_TOKEN_ID + i + 1
            }
        }
        // Default: auto-detect (no language token)
        return -1
    }
}
