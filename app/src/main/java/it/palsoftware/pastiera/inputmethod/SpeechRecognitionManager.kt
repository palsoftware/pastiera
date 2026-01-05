package it.palsoftware.pastiera.inputmethod

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.core.content.ContextCompat
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.AutoCapitalizeHelper
import java.util.Locale

/**
 * Manages speech recognition using SpeechRecognizer.
 * Handles initialization, recognition, and text insertion with real-time updates.
 */
class SpeechRecognitionManager(
    private val context: Context,
    private val inputConnectionProvider: () -> InputConnection?,
    private val onError: ((String) -> Unit)? = null,
    private val onRecognitionStateChanged: ((Boolean) -> Unit)? = null,
    private val shouldDisableAutoCapitalize: () -> Boolean = { false },
    private val onAudioLevelChanged: ((Float) -> Unit)? = null
) {
    companion object {
        private const val TAG = "SpeechRecognitionMgr"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isComposingPartialText: Boolean = false

    /**
     * Normalizes punctuation words (e.g., "punto" -> ".") in recognized text.
     */
    private fun normalizePunctuationWords(text: String): String {
        var normalized = text
        
        // Italian punctuation words to symbols
        val punctuationMap = mapOf(
            " punto " to ". ",
            " punto" to ".",
            "punto " to ". ",
            " virgola " to ", ",
            " virgola" to ",",
            "virgola " to ", ",
            " punto e virgola " to "; ",
            " punto e virgola" to ";",
            " punto e virgola" to "; ",
            " due punti " to ": ",
            " due punti" to ":",
            "due punti " to ": ",
            " punto interrogativo " to "? ",
            " punto interrogativo" to "?",
            " punto interrogativo" to "? ",
            " punto esclamativo " to "! ",
            " punto esclamativo" to "!",
            " punto esclamativo" to "! "
        )
        
        // Replace in order of length (longer first to avoid partial matches)
        val sortedEntries = punctuationMap.entries.sortedByDescending { it.key.length }
        for ((word, symbol) in sortedEntries) {
            normalized = normalized.replace(word, symbol, ignoreCase = true)
        }
        
        return normalized
    }

    /**
     * Formats text according to standard auto-capitalization rules (first letter and after period).
     * Uses AutoCapitalizeHelper to check if capitalization should be applied.
     */
    private fun formatTextWithAutoCapitalization(text: String): String {
        if (text.isEmpty()) return text
        
        val inputConnection = inputConnectionProvider() ?: return text
        
        // Check if we should disable auto-capitalization
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
        
        // Capitalize after sentence-ending punctuation (., !, ?)
        if (SettingsManager.getAutoCapitalizeAfterPeriod(context)) {
            // Pattern: trova . ! o ? seguito da spazio e una lettera minuscola
            formatted = formatted.replace(Regex("([.!?]\\s+)([a-z])")) { matchResult ->
                matchResult.groupValues[1] + matchResult.groupValues[2].uppercase()
            }
        }
        
        return formatted
    }

    /**
     * Ensures SpeechRecognizer is initialized with a RecognitionListener.
     */
    private fun ensureSpeechRecognizer() {
        if (speechRecognizer == null) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "Speech recognition is not available on this device")
                return
            }
            
            // Create SpeechRecognizer - let system find the best available service
            Log.d(TAG, "Creating SpeechRecognizer instance")
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)?.apply {
                Log.d(TAG, "SpeechRecognizer created, now setting listener")
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "Speech recognition ready for speech")
                        // Reset composing state for new recognition session
                        isComposingPartialText = false
                        // Notify that recognition is active (hint will be shown by the UI)
                        onRecognitionStateChanged?.invoke(true)
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "Speech recognition: beginning of speech")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Update UI feedback based on audio level
                        onAudioLevelChanged?.invoke(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                        // Optional
                    }

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "Speech recognition: end of speech detected")
                        // The system automatically detected silence after speech
                        // onResults() will be called next with the final recognition result
                    }

                    override fun onError(error: Int) {
                        val errorMessage = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO - Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT - Other client side errors"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS - Insufficient permissions"
                            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK - Network related errors"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT - Network operation timed out"
                            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH - No recognition result matched"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY - RecognitionService busy"
                            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER - Server sends error status"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT - No speech input"
                            else -> "UNKNOWN_ERROR($error)"
                        }
                        Log.w(TAG, "Speech recognition error: $errorMessage")
                        
                        // Only ignore truly non-critical errors (no speech detected)
                        val isIgnorableError = error in listOf(
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        )
                        
                        Log.d(TAG, "Error is ignorable: $isIgnorableError, showing toast: ${!isIgnorableError}")
                        
                        if (!isIgnorableError) {
                            // Notify that recognition is finished
                            onRecognitionStateChanged?.invoke(false)
                            
                            // Clear partial text on error
                            if (isComposingPartialText) {
                                clearPartialText()
                            }
                            
                            // Show user-friendly error message
                            Handler(Looper.getMainLooper()).post {
                                val userMessage = when (error) {
                                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> context.getString(R.string.speech_recognition_error_permission)
                                    SpeechRecognizer.ERROR_NETWORK -> context.getString(R.string.speech_recognition_error_network)
                                    else -> context.getString(R.string.speech_recognition_error_generic)
                                }
                                Log.e(TAG, "Showing error toast: $userMessage")
                                Toast.makeText(context, userMessage, Toast.LENGTH_SHORT).show()
                                onError?.invoke(userMessage)
                            }
                        } else {
                            // For non-critical errors, just log them without showing to user
                            Log.d(TAG, "Ignorable error, no toast shown: $errorMessage")
                        }
                    }

                    override fun onResults(results: Bundle) {
                        // Notify that recognition is finished
                        onRecognitionStateChanged?.invoke(false)
                        
                        // Destroy and reset SpeechRecognizer for next session
                        // Set to null FIRST to prevent race conditions if user clicks mic again immediately
                        val recognizerToDestroy = speechRecognizer
                        speechRecognizer = null
                        Log.d(TAG, "SpeechRecognizer set to null, destroying instance to prepare for next session")
                        recognizerToDestroy?.destroy()
                        
                        val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val confidenceScores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                        
                        Log.d(TAG, "Speech recognition results: ${matches?.size ?: 0} matches")
                        matches?.forEachIndexed { index, match ->
                            val confidence = confidenceScores?.getOrNull(index)
                            Log.d(TAG, "  Result[$index]: '$match' (confidence: $confidence)")
                        }
                        
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotEmpty()) {
                            val normalizedText = normalizePunctuationWords(text)
                            // Apply auto-capitalization rules
                            val formattedText = formatTextWithAutoCapitalization(normalizedText)
                            Log.d(TAG, "Using recognized text: '$formattedText' (original: '$text', normalized: '$normalizedText')")
                            // Replace partial text with final formatted text
                            replacePartialWithFinalText(formattedText)
                        } else {
                            // Clear partial text if no final result
                            if (isComposingPartialText) {
                                clearPartialText()
                            }
                            Log.w(TAG, "No text recognized")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partialText = matches?.firstOrNull() ?: ""
                        
                        if (partialText.isNotEmpty()) {
                            Log.d(TAG, "Speech recognition partial results: '$partialText'")
                            // Insert/update partial text in real-time
                            updatePartialSpeechText(partialText)
                        } else {
                            Log.d(TAG, "Speech recognition partial results: none")
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {
                        // Optional
                    }
                })
            }
            Log.d(TAG, "SpeechRecognizer initialized")
        }
    }

    /**
     * Updates the input field with partial speech recognition results in real-time.
     * Uses setComposingText to show text as "being composed" which can be updated seamlessly.
     * Applies basic capitalization (first letter only) to partial text.
     */
    private fun updatePartialSpeechText(text: String) {
        Handler(Looper.getMainLooper()).post {
            val inputConnection = inputConnectionProvider() ?: return@post
            
            try {
                // Apply basic capitalization to partial text (only first letter, not sentence endings)
                var formatted = text
                if (formatted.isNotEmpty() && !shouldDisableAutoCapitalize()) {
                    val shouldCapitalizeFirst = AutoCapitalizeHelper.shouldAutoCapitalizeAtCursor(
                        context = context,
                        inputConnection = inputConnection,
                        shouldDisableAutoCapitalize = shouldDisableAutoCapitalize()
                    ) && SettingsManager.getAutoCapitalizeFirstLetter(context)
                    
                    if (shouldCapitalizeFirst) {
                        formatted = formatted.replaceFirstChar { 
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) 
                            else it.toString() 
                        }
                    }
                }
                
                // Use setComposingText to show partial text as "being composed"
                // Offset 0 replaces any existing composing text
                inputConnection.setComposingText(formatted, 0)
                isComposingPartialText = true
                Log.d(TAG, "Partial text updated (composing): '$formatted'")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating partial text", e)
            }
        }
    }

    /**
     * Replaces partial composing text with the final normalized result.
     * Adds spacing rules:
     * - Always adds a space at the end
     * - Adds a space at the beginning if the text before cursor ends with a letter
     */
    private fun replacePartialWithFinalText(finalText: String) {
        Handler(Looper.getMainLooper()).post {
            val inputConnection = inputConnectionProvider() ?: return@post
            
            try {
                var textToCommit = finalText
                
                // Check if we need to add a space at the beginning
                // Read a reasonable amount of text before cursor to check context
                val textBeforeCursor = inputConnection.getTextBeforeCursor(10, 0)
                if (textBeforeCursor != null && textBeforeCursor.isNotEmpty()) {
                    val lastChar = textBeforeCursor.last()
                    // If the last character before cursor is a letter, add space before
                    if (lastChar.isLetter()) {
                        textToCommit = " $textToCommit"
                        Log.d(TAG, "Added space before text (previous char was letter: '$lastChar')")
                    }
                }
                
                // Always add a space at the end
                textToCommit += " "
                
                // If we're composing partial text, replace it directly with final text using setComposingText + commit
                if (isComposingPartialText) {
                    // First set the final text as composing text (this replaces the partial text)
                    inputConnection.setComposingText(textToCommit, 1)
                    // Then finish composing to commit it (this commits the final text and removes composing state)
                    inputConnection.finishComposingText()
                    isComposingPartialText = false
                    Log.d(TAG, "Final text committed (replaced partial): '$textToCommit'")
                } else {
                    // No partial text, just insert the final text
                    inputConnection.commitText(textToCommit, 1)
                    Log.d(TAG, "Final text inserted: '$textToCommit'")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error replacing with final text", e)
            }
        }
    }

    /**
     * Clears any partial composing text.
     */
    private fun clearPartialText() {
        Handler(Looper.getMainLooper()).post {
            val inputConnection = inputConnectionProvider() ?: return@post
            
            try {
                if (isComposingPartialText) {
                    inputConnection.finishComposingText()
                    isComposingPartialText = false
                    Log.d(TAG, "Partial text cleared")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing partial text", e)
            }
        }
    }

    /**
     * Starts voice input using SpeechRecognizer.
     */
    fun startRecognition() {
        // Check if RECORD_AUDIO permission is granted
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            onError?.invoke(context.getString(R.string.speech_recognition_error_permission))
            return
        }
        
        ensureSpeechRecognizer()
        
        if (speechRecognizer == null) {
            Log.e(TAG, "Cannot start speech recognition: SpeechRecognizer not available")
            onError?.invoke(context.getString(R.string.speech_recognition_error_not_available))
            return
        }
        
        try {
            // Get device locale for better recognition
            val locale = context.resources.configuration.locales[0]
            val languageTag = locale?.language?.let { lang ->
                val country = locale.country
                if (country.isNotEmpty()) "$lang-$country" else lang
            } ?: "it-IT"
            
            Log.d(TAG, "Device locale: ${locale?.displayName}, using language tag: $languageTag")
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.speech_recognition_prompt))
            }
            
            Log.d(TAG, "Starting speech recognition with language: $languageTag")
            Log.d(TAG, "SpeechRecognizer state before startListening: ${if (speechRecognizer != null) "exists" else "null"}")
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "Speech recognition started via SpeechRecognizer")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting speech recognition - permission denied", e)
            onError?.invoke(context.getString(R.string.speech_recognition_error_permission))
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start speech recognition", e)
            onError?.invoke(context.getString(R.string.speech_recognition_error_generic))
        }
    }

    /**
     * Stops voice input if active.
     */
    fun stopRecognition() {
        speechRecognizer?.stopListening()
        Log.d(TAG, "Speech recognition stopped")
    }

    /**
     * Destroys the SpeechRecognizer instance.
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        // Clear any partial text
        if (isComposingPartialText) {
            clearPartialText()
        }
        Log.d(TAG, "SpeechRecognizer destroyed")
    }
}

