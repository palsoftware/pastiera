package it.palsoftware.pastiera.inputmethod.whisper

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import it.palsoftware.pastiera.inputmethod.whisper.UsageInfo

/**
 * Client for OpenRouter Audio Speech Recognition API.
 * Handles audio transcription via OpenRouter's multimodal models.
 */
class OpenRouterWhisperClient(private val apiKey: String) {
    
    private val TAG = "OpenRouterWhisperClient"
    private val API_BASE = "https://openrouter.ai/api/v1"
    private val TIMEOUT_MS = 60000 // 60 seconds for large uploads
    
    /**
     * Transcribes audio data using OpenRouter API
     */
    fun transcribeAudio(
        audioData: ByteArray,
        model: String = "google/gemini-2.5-flash",
        language: String? = null
    ): Result<WhisperResult> {
        return try {
            Log.d(TAG, "Starting transcription with model: $model")
            Log.d(TAG, "Raw audio data size: ${audioData.size} bytes")
            
            // Convert raw PCM16 to WAV format
            val wavAudioData = convertPcmToWav(audioData)
            Log.d(TAG, "WAV audio data size: ${wavAudioData.size} bytes")
            
            // Encode audio to base64
            val base64Audio = Base64.getEncoder().encodeToString(wavAudioData)
            Log.d(TAG, "Audio encoded to base64 (${base64Audio.length} chars)")
            
            // Audio format is now properly WAV
            val audioFormat = "wav"
            
            // Build request payload
            val prompt = buildPrompt(language)
            val payload = buildPayload(base64Audio, audioFormat, model, prompt)
            
            Log.d(TAG, "Sending request to OpenRouter API...")
            val response = sendRequest(payload)
            
            // Parse response
            val (transcribedText, usage) = parseResponse(response)
            
            Log.d(TAG, "Transcription successful: $transcribedText")
            Log.d(TAG, "Usage: $usage")
            
            Result.success(WhisperResult(
                text = transcribedText,
                language = language ?: "auto",
                confidence = 0.95f,
                model = model,
                promptTokens = usage?.promptTokens ?: 0,
                completionTokens = usage?.completionTokens ?: 0,
                costUsd = usage?.costUsd ?: 0.0
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Transcription error: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Converts raw PCM16 audio data to WAV format (16-bit, 16kHz mono)
     */
    private fun convertPcmToWav(pcmData: ByteArray): ByteArray {
        val sampleRate = 16000  // 16kHz
        val numChannels = 1      // Mono
        val bitDepth = 16        // 16-bit
        val byteRate = sampleRate * numChannels * (bitDepth / 8)
        val blockAlign = numChannels * (bitDepth / 8)
        
        val wavData = ByteArray(44 + pcmData.size)
        
        // WAV header (44 bytes)
        var offset = 0
        
        // "RIFF" chunk descriptor
        wavData[offset++] = 'R'.code.toByte()
        wavData[offset++] = 'I'.code.toByte()
        wavData[offset++] = 'F'.code.toByte()
        wavData[offset++] = 'F'.code.toByte()
        
        // File size - 8 (little-endian)
        val fileSize = 36 + pcmData.size
        wavData[offset++] = (fileSize and 0xFF).toByte()
        wavData[offset++] = ((fileSize shr 8) and 0xFF).toByte()
        wavData[offset++] = ((fileSize shr 16) and 0xFF).toByte()
        wavData[offset++] = ((fileSize shr 24) and 0xFF).toByte()
        
        // "WAVE" format
        wavData[offset++] = 'W'.code.toByte()
        wavData[offset++] = 'A'.code.toByte()
        wavData[offset++] = 'V'.code.toByte()
        wavData[offset++] = 'E'.code.toByte()
        
        // "fmt " subchunk
        wavData[offset++] = 'f'.code.toByte()
        wavData[offset++] = 'm'.code.toByte()
        wavData[offset++] = 't'.code.toByte()
        wavData[offset++] = ' '.code.toByte()
        
        // Subchunk1Size (16 for PCM)
        wavData[offset++] = 16
        wavData[offset++] = 0
        wavData[offset++] = 0
        wavData[offset++] = 0
        
        // Audio format (1 = PCM)
        wavData[offset++] = 1
        wavData[offset++] = 0
        
        // Number of channels
        wavData[offset++] = numChannels.toByte()
        wavData[offset++] = 0
        
        // Sample rate (little-endian)
        wavData[offset++] = (sampleRate and 0xFF).toByte()
        wavData[offset++] = ((sampleRate shr 8) and 0xFF).toByte()
        wavData[offset++] = ((sampleRate shr 16) and 0xFF).toByte()
        wavData[offset++] = ((sampleRate shr 24) and 0xFF).toByte()
        
        // Byte rate (little-endian)
        wavData[offset++] = (byteRate and 0xFF).toByte()
        wavData[offset++] = ((byteRate shr 8) and 0xFF).toByte()
        wavData[offset++] = ((byteRate shr 16) and 0xFF).toByte()
        wavData[offset++] = ((byteRate shr 24) and 0xFF).toByte()
        
        // Block align
        wavData[offset++] = blockAlign.toByte()
        wavData[offset++] = 0
        
        // Bits per sample
        wavData[offset++] = bitDepth.toByte()
        wavData[offset++] = 0
        
        // "data" subchunk
        wavData[offset++] = 'd'.code.toByte()
        wavData[offset++] = 'a'.code.toByte()
        wavData[offset++] = 't'.code.toByte()
        wavData[offset++] = 'a'.code.toByte()
        
        // Subchunk2Size (PCM data size)
        wavData[offset++] = (pcmData.size and 0xFF).toByte()
        wavData[offset++] = ((pcmData.size shr 8) and 0xFF).toByte()
        wavData[offset++] = ((pcmData.size shr 16) and 0xFF).toByte()
        wavData[offset++] = ((pcmData.size shr 24) and 0xFF).toByte()
        
        // Copy PCM data
        System.arraycopy(pcmData, 0, wavData, 44, pcmData.size)
        
        Log.d(TAG, "WAV header created: channels=$numChannels, sample_rate=$sampleRate, bits=$bitDepth")
        
        return wavData
    }
    
    /**
     * Builds the transcription prompt
     */
    private fun buildPrompt(language: String?): String {
        return if (language != null && language != "auto") {
            "Please transcribe this audio file. The audio is in $language. Return only the transcribed text."
        } else {
            "Please transcribe this audio file. Return only the transcribed text."
        }
    }
    
    /**
     * Builds the API request payload
     */
    private fun buildPayload(
        base64Audio: String,
        format: String,
        model: String,
        prompt: String
    ): JSONObject {
        val payload = JSONObject()
        
        payload.put("model", model)
        
        // Build messages
        val messages = JSONArray()
        val message = JSONObject()
        message.put("role", "user")
        
        val content = JSONArray()
        
        // Text content
        val textContent = JSONObject()
        textContent.put("type", "text")
        textContent.put("text", prompt)
        content.put(textContent)
        
        // Audio content
        val audioContent = JSONObject()
        audioContent.put("type", "input_audio")
        
        val inputAudio = JSONObject()
        inputAudio.put("data", base64Audio)
        inputAudio.put("format", format)
        audioContent.put("input_audio", inputAudio)
        
        content.put(audioContent)
        
        message.put("content", content)
        messages.put(message)
        
        payload.put("messages", messages)
        
        // Settings
        payload.put("stream", false)
        
        return payload
    }
    
    /**
     * Sends the request to OpenRouter API
     */
    private fun sendRequest(payload: JSONObject): String {
        val url = URL("$API_BASE/chat/completions")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("HTTP-Referer", "https://pastiera.app")
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            
            // Send payload
            connection.doOutput = true
            val outputStream = connection.outputStream
            outputStream.write(payload.toString().toByteArray())
            outputStream.flush()
            outputStream.close()
            
            // Read response
            val statusCode = connection.responseCode
            Log.d(TAG, "API response status: $statusCode")
            
            if (statusCode != HttpURLConnection.HTTP_OK) {
                val errorStream = connection.errorStream
                val errorText = errorStream?.bufferedReader().use { it?.readText() ?: "" }
                Log.e(TAG, "API Error ($statusCode): $errorText")
                throw Exception("OpenRouter API error: $statusCode - $errorText")
            }
            
            val inputStream = connection.inputStream
            val response = inputStream.bufferedReader().use { it.readText() }
            Log.d(TAG, "API response received (${response.length} chars)")
            
            return response
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * Parses the API response
     */
    private fun parseResponse(response: String): Pair<String, UsageInfo?> {
        try {
            val jsonResponse = JSONObject(response)
            
            // Check for errors
            if (jsonResponse.has("error")) {
                val error = jsonResponse.getJSONObject("error")
                val errorMsg = error.getString("message")
                throw Exception("API Error: $errorMsg")
            }
            
            // Extract text from response
            val choices = jsonResponse.getJSONArray("choices")
            if (choices.length() == 0) {
                throw Exception("No choices in response")
            }
            
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.getJSONObject("message")
            val content = message.getString("content")
            
            Log.d(TAG, "Extracted text: $content")
            
            // Extract usage information if available
            var usage: UsageInfo? = null
            if (jsonResponse.has("usage")) {
                val usageObj = jsonResponse.getJSONObject("usage")
                val promptTokens = usageObj.optInt("prompt_tokens", 0)
                val completionTokens = usageObj.optInt("completion_tokens", 0)
                Log.d(TAG, "Tokens - Prompt: $promptTokens, Completion: $completionTokens")
                
                usage = UsageInfo(
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    costUsd = 0.0  // Will be set by the manager if needed
                )
            }
            
            return Pair(content.trim(), usage)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing response: ${e.message}")
            throw e
        }
    }
}

