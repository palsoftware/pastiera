package it.palsoftware.pastiera.inputmethod.whisper

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * OpenAI Whisper API Client for remote speech-to-text transcription.
 * Supports GPT-4o transcribe, GPT-4o mini transcribe, and Whisper-1 models.
 */
class OpenAiWhisperClient(
    private val apiKey: String
) {
    companion object {
        private const val TAG = "OpenAiWhisperClient"
        private const val BASE_URL = "https://api.openai.com/v1"
        private const val TRANSCRIPTIONS_ENDPOINT = "$BASE_URL/audio/transcriptions"
        private const val REQUEST_TIMEOUT_SECONDS = 120L
    }

    data class TranscriptionResult(
        val text: String,
        val language: String? = null,
        val usage: TokenUsage? = null
    )

    data class TokenUsage(
        val inputTokens: Int,
        val outputTokens: Int,
        val totalTokens: Int
    )

    enum class Model(val id: String) {
        GPT_4O_TRANSCRIBE("gpt-4o-transcribe"),
        GPT_4O_MINI_TRANSCRIBE("gpt-4o-mini-transcribe"),
        WHISPER_1("whisper-1")
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Transcribes audio file using OpenAI Whisper API
     */
    suspend fun transcribeAudio(
        audioFile: File,
        model: String = Model.GPT_4O_TRANSCRIBE.id,
        language: String? = null,
        prompt: String? = null,
        temperature: Float = 0f
    ): Result<TranscriptionResult> {
        return try {
            if (!audioFile.exists()) {
                return Result.failure(Exception("Audio file not found: ${audioFile.absolutePath}"))
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/wav; charset=utf-8".toMediaType())
                )
                .addFormDataPart("model", model)
                .addFormDataPart("response_format", "json")
                
            // Add optional parameters
            if (language != null && language.isNotEmpty()) {
                requestBody.addFormDataPart("language", language)
            }
            if (prompt != null && prompt.isNotEmpty()) {
                requestBody.addFormDataPart("prompt", prompt)
            }
            if (temperature > 0) {
                requestBody.addFormDataPart("temperature", temperature.toString())
            }

            val request = Request.Builder()
                .url(TRANSCRIPTIONS_ENDPOINT)
                .header("Authorization", "Bearer $apiKey")
                .post(requestBody.build())
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Log.e(TAG, "API error: ${response.code} - $errorBody")
                    return Result.failure(Exception("API error: ${response.code} - $errorBody"))
                }

                val responseBody = response.body?.string()
                    ?: return Result.failure(Exception("Empty response body"))

                val jsonResponse = JSONObject(responseBody)
                val text = jsonResponse.getString("text")
                
                // Parse optional fields
                val language = if (jsonResponse.has("language")) {
                    jsonResponse.getString("language")
                } else null

                val usage = if (jsonResponse.has("usage")) {
                    val usageObj = jsonResponse.getJSONObject("usage")
                    TokenUsage(
                        inputTokens = usageObj.optInt("input_tokens", 0),
                        outputTokens = usageObj.optInt("output_tokens", 0),
                        totalTokens = usageObj.optInt("total_tokens", 0)
                    )
                } else null

                Log.d(TAG, "Transcription successful: '$text' (language: $language)")
                Result.success(TranscriptionResult(text, language, usage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            Result.failure(e)
        }
    }

    /**
     * Validates the API key by making a minimal request
     */
    suspend fun validateApiKey(): Result<Boolean> {
        return try {
            if (apiKey.isEmpty()) {
                return Result.failure(Exception("API key is empty"))
            }

            // Create a minimal test request
            val testBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", Model.WHISPER_1.id)
                .addFormDataPart("response_format", "json")
                .build()

            val request = Request.Builder()
                .url(TRANSCRIPTIONS_ENDPOINT)
                .header("Authorization", "Bearer $apiKey")
                .post(testBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                // 400 Bad Request is expected (no file), but 401 means auth failed
                val isValid = response.code != 401
                Log.d(TAG, "API key validation: ${if (isValid) "valid" else "invalid"} (${response.code})")
                Result.success(isValid)
            }
        } catch (e: Exception) {
            Log.e(TAG, "API key validation failed", e)
            Result.failure(e)
        }
    }
}

