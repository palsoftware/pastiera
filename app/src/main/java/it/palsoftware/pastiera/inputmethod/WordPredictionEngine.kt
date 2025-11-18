package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * Word prediction engine that manages multiple language dictionaries.
 * Uses Trie data structures for efficient prefix matching and word suggestions.
 */
object WordPredictionEngine {
    private const val TAG = "WordPredictionEngine"
    private const val ASSETS_PREDICTION_PATH = "common/predictions"

    // Map of language code to Trie
    private val languageTries = mutableMapOf<String, Trie>()

    // Track which languages have been loaded
    private val loadedLanguages = mutableSetOf<String>()

    // Supported languages (matching Pastiera's auto-correct languages)
    private val supportedLanguages = setOf("en", "it", "es", "fr", "de", "pl")

    /**
     * Initialize the prediction engine by loading default language (English).
     * Call this on service startup.
     */
    fun initialize(context: Context) {
        Log.d(TAG, "Initializing WordPredictionEngine")
        // Load English by default as it's the most common
        loadLanguage(context, "en")
    }

    /**
     * Load a specific language dictionary into memory.
     * @param context Application context
     * @param languageCode Language code (e.g., "en", "it", "es")
     * @return true if loaded successfully, false otherwise
     */
    fun loadLanguage(context: Context, languageCode: String): Boolean {
        if (!supportedLanguages.contains(languageCode)) {
            Log.w(TAG, "Unsupported language: $languageCode")
            return false
        }

        if (loadedLanguages.contains(languageCode)) {
            Log.d(TAG, "Language already loaded: $languageCode")
            return true
        }

        try {
            val trie = Trie()
            val filename = "word_list_$languageCode.txt.gz"
            val fullPath = "$ASSETS_PREDICTION_PATH/$filename"

            Log.d(TAG, "Loading word list: $fullPath")

            val inputStream = try {
                context.assets.open(fullPath)
            } catch (e: Exception) {
                // Try without .gz extension
                val altFilename = "word_list_$languageCode.txt"
                val altPath = "$ASSETS_PREDICTION_PATH/$altFilename"
                Log.d(TAG, "Gzipped file not found, trying: $altPath")
                context.assets.open(altPath)
            }

            val reader = BufferedReader(
                InputStreamReader(
                    if (fullPath.endsWith(".gz")) GZIPInputStream(inputStream) else inputStream
                )
            )

            var wordCount = 0
            reader.useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        // Format: word,frequency or just word
                        val parts = trimmed.split(",")
                        val word = parts[0].trim()
                        val frequency = if (parts.size > 1) {
                            parts[1].trim().toIntOrNull() ?: 100
                        } else {
                            100 // Default frequency
                        }

                        if (word.isNotEmpty()) {
                            trie.insert(word, frequency)
                            wordCount++
                        }
                    }
                }
            }

            languageTries[languageCode] = trie
            loadedLanguages.add(languageCode)

            Log.i(TAG, "Loaded $wordCount words for language: $languageCode")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load language $languageCode: ${e.message}", e)
            return false
        }
    }

    /**
     * Unload a language dictionary from memory to save RAM.
     * @param languageCode Language code to unload
     */
    fun unloadLanguage(languageCode: String) {
        languageTries.remove(languageCode)
        loadedLanguages.remove(languageCode)
        Log.d(TAG, "Unloaded language: $languageCode")
    }

    /**
     * Get word suggestions for a given prefix.
     * @param prefix The text to get suggestions for
     * @param languageCode Language code for suggestions (e.g., "en")
     * @param maxResults Maximum number of suggestions (default: 3)
     * @return List of suggested words, sorted by frequency
     */
    fun getSuggestions(
        prefix: String,
        languageCode: String,
        maxResults: Int = 3
    ): List<String> {
        if (prefix.isBlank() || prefix.length < 2) {
            // Don't show suggestions for very short prefixes
            return emptyList()
        }

        // Load language if not already loaded
        val trie = languageTries[languageCode] ?: run {
            Log.d(TAG, "Language not loaded, attempting to load: $languageCode")
            return emptyList()
        }

        return trie.findWordsWithPrefix(prefix, maxResults)
    }

    /**
     * Get suggestions for the current text, automatically detecting the word to complete.
     * @param text Full text before cursor
     * @param languageCode Language code
     * @param maxResults Maximum number of suggestions
     * @return List of suggested words
     */
    fun getSuggestionsForText(
        text: String,
        languageCode: String,
        maxResults: Int = 3
    ): List<String> {
        // Extract the last word being typed
        val lastWord = extractLastWord(text)

        if (lastWord.isBlank()) {
            return emptyList()
        }

        return getSuggestions(lastWord, languageCode, maxResults)
    }

    /**
     * Extract the last word from text (word being currently typed).
     * @param text Text before cursor
     * @return The last word being typed, or empty string
     */
    private fun extractLastWord(text: String): String {
        if (text.isBlank()) return ""

        // Find the last word boundary (space, punctuation, etc.)
        val wordBoundaryRegex = Regex("[\\s\\p{Punct}]")
        val lastBoundaryIndex = text.lastIndexOfAny(wordBoundaryRegex.pattern.toCharArray())

        return if (lastBoundaryIndex >= 0) {
            text.substring(lastBoundaryIndex + 1).trim()
        } else {
            text.trim()
        }
    }

    /**
     * Check if a word exists in the dictionary.
     * @param word Word to check
     * @param languageCode Language code
     * @return true if word exists
     */
    fun isValidWord(word: String, languageCode: String): Boolean {
        val trie = languageTries[languageCode] ?: return false
        return trie.search(word)
    }

    /**
     * Get list of currently loaded languages.
     */
    fun getLoadedLanguages(): Set<String> {
        return loadedLanguages.toSet()
    }

    /**
     * Get list of supported languages.
     */
    fun getSupportedLanguages(): Set<String> {
        return supportedLanguages
    }

    /**
     * Clear all loaded dictionaries (useful for memory management).
     */
    fun clearAll() {
        languageTries.clear()
        loadedLanguages.clear()
        Log.d(TAG, "Cleared all dictionaries")
    }
}
