package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import org.json.JSONObject

/**
 * Handles auto-correction of accents, apostrophes, and contractions.
 * Automatically corrects common patterns when space or punctuation is pressed.
 * Supports undo with backspace (only as first action after correction).
 * Preserves original capitalization (Cos e → Cos'è, not cos'è).
 */
object AutoCorrector {
    private const val TAG = "AutoCorrector"

    private val corrections = mutableMapOf<String, Map<String, String>>()

    /**
     * Information about the last applied correction.
     * The correction remains undoable until a key is pressed (except backspace).
     */
    data class LastCorrection(
        val originalWord: String,
        val correctedWord: String,
        val correctionLength: Int // Length of the inserted corrected word
    )

    // Track last applied correction (null if none or if accepted)
    private var lastCorrection: LastCorrection? = null

    // Track words that have been rejected (undone with backspace)
    // These words won't be corrected until the user modifies the text
    private val rejectedWords = mutableSetOf<String>()

    // Track custom languages loaded from external files
    private val customLanguages = mutableSetOf<String>()

    /**
     * Loads auto-correction rules from JSON files per language.
     * Files must be in common/autocorrect folder with name: auto_corrections_{locale}.json
     * Example: common/autocorrect/auto_corrections_it.json, common/autocorrect/auto_corrections_en.json
     * Also supports loading custom JSON files.
     */
    fun loadCorrections(assets: AssetManager, context: Context? = null) {
        try {
            corrections.clear()
            customLanguages.clear()

            // List of supported languages by default
            val standardLocales = listOf("it", "en", "es", "fr", "de", "pl", "x-pastiera")

            for (locale in standardLocales) {
                try {
                    // First load custom corrections (if they exist)
                    if (context != null) {
                        val customCorrections = it.palsoftware.pastiera.SettingsManager.getCustomAutoCorrections(context, locale)
                        if (customCorrections.isNotEmpty()) {
                            // Load custom corrections
                            val customJson = correctionsToJson(customCorrections)
                            loadCorrectionsFromJson(locale, customJson)
                            // Don't add standard languages to customLanguages - these are just modifications, not new languages
                            Log.d(TAG, "Loaded ${customCorrections.size} custom corrections for locale: $locale")
                            continue // Skip loading default file
                        }
                    }
                    
                    // If no customizations, load default file
                    val fileName = "common/autocorrect/auto_corrections_$locale.json"
                    val jsonString = assets.open(fileName).bufferedReader().use { it.readText() }
                    loadCorrectionsFromJson(locale, jsonString)
                } catch (e: Exception) {
                    // File not found or parsing error - ignore this language
                    Log.d(TAG, "No correction file found for locale: $locale")
                }
            }

            // Remove any standard languages from customLanguages (shouldn't be there, but for safety)
            customLanguages.removeAll(standardLocales)
            
            // Also load additional custom languages (non-standard)
            if (context != null) {
                try {
                    val prefs = it.palsoftware.pastiera.SettingsManager.getPreferences(context)
                    val allPrefs = prefs.all
                    
                    // Search for all keys starting with "auto_correct_custom_"
                    for ((key, value) in allPrefs) {
                        if (key.startsWith("auto_correct_custom_") && value is String) {
                            val languageCode = key.removePrefix("auto_correct_custom_")
                            
                            // Skip standard languages (already loaded above)
                            if (languageCode !in standardLocales) {
                                try {
                                    val customCorrections = it.palsoftware.pastiera.SettingsManager.getCustomAutoCorrections(context, languageCode)
                                    if (customCorrections.isNotEmpty()) {
                                        val customJson = correctionsToJson(customCorrections)
                                        loadCorrectionsFromJson(languageCode, customJson)
                                        customLanguages.add(languageCode)
                                        Log.d(TAG, "Loaded ${customCorrections.size} corrections for custom language: $languageCode")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error loading custom language $languageCode", e)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading custom languages", e)
                }
            }
            
            Log.d(TAG, "Total languages loaded: ${corrections.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading corrections", e)
        }
    }
    
    /**
     * Converts a map of corrections to JSON string.
     */
    private fun correctionsToJson(corrections: Map<String, String>): String {
        val jsonObject = JSONObject()
        corrections.forEach { (key, value) ->
            jsonObject.put(key, value)
        }
        return jsonObject.toString()
    }

    /**
     * Loads corrections from a custom JSON file.
     * @param locale The language code (e.g. "it", "en", "custom1")
     * @param jsonString The JSON file content
     */
    fun loadCustomCorrections(locale: String, jsonString: String) {
        try {
            loadCorrectionsFromJson(locale, jsonString)
            // Add to customLanguages only if not a standard language
            val standardLocales = listOf("it", "en", "es", "fr", "de", "pl", "x-pastiera")
            if (locale !in standardLocales) {
                customLanguages.add(locale)
            }
            Log.d(TAG, "Loaded custom corrections for locale: $locale")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading custom corrections for $locale", e)
        }
    }

    /**
     * Loads corrections from a JSON string.
     * Ignores the special "__name" field that contains the language name.
     * Always adds the locale to corrections map, even if empty, so it appears in available languages.
     */
    private fun loadCorrectionsFromJson(locale: String, jsonString: String) {
        val jsonObject = JSONObject(jsonString)
        val correctionMap = mutableMapOf<String, String>()

        // JSON file contains an object with keys that are words to correct
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            // Skip special name field
            if (key != "__name") {
                val value = jsonObject.getString(key)
                correctionMap[key] = value
            }
        }

        // Always add locale to corrections map, even if empty, so it appears in available languages
        corrections[locale] = correctionMap
        Log.d(TAG, "Loaded ${correctionMap.size} corrections for locale: $locale")
    }

    /**
     * Gets all available languages (including custom ones).
     */
    fun getAllAvailableLanguages(): Set<String> {
        return corrections.keys.toSet()
    }

    /**
     * Gets only custom languages.
     */
    fun getCustomLanguages(): Set<String> {
        return customLanguages.toSet()
    }

    /**
     * Gets current locale based on device language.
     */
    private fun getCurrentLocale(context: Context): String {
        val locale = context.resources.configuration.locales[0]
        return locale.language.lowercase()
    }

    /**
     * Gets all supported locales.
     */
    fun getSupportedLocales(): Set<String> {
        return corrections.keys.toSet()
    }

    /**
     * Applies original capitalization to corrected word.
     * Correctly handles special characters at the beginning (apostrophes, etc.).
     *
     * @param originalWord The original word (e.g. "Cos", "CASA", "cos")
     * @param correctedWord The corrected word in lowercase (e.g. "cos'è", "casa")
     * @return The corrected word with preserved capitalization (e.g. "Cos'è", "CASA", "cos'è")
     */
    private fun applyCapitalization(originalWord: String, correctedWord: String): String {
        if (originalWord.isEmpty() || correctedWord.isEmpty()) {
            return correctedWord
        }

        val originalLower = originalWord.lowercase()
        val correctedLower = correctedWord.lowercase()

        // If original word was all uppercase, apply uppercase to entire correction
        if (originalWord == originalWord.uppercase() && originalWord.any { it.isLetter() }) {
            // Find first alphabetic letter in correction (might be after apostrophes)
            val firstLetterIndex = correctedWord.indexOfFirst { it.isLetter() }
            if (firstLetterIndex >= 0) {
                val beforeLetter = correctedWord.substring(0, firstLetterIndex)
                val fromLetter = correctedWord.substring(firstLetterIndex)
                return beforeLetter + fromLetter.uppercase()
            }
            return correctedWord.uppercase()
        }

        // If first letter of original word was uppercase, capitalize first letter of correction
        if (originalWord.isNotEmpty() && originalWord[0].isUpperCase()) {
            // Find first alphabetic letter in correction (might be after apostrophes/punctuation)
            val firstLetterIndex = correctedWord.indexOfFirst { it.isLetter() }
            if (firstLetterIndex >= 0) {
                val beforeLetter = correctedWord.substring(0, firstLetterIndex)
                val firstLetter = correctedWord[firstLetterIndex]
                val afterLetter = correctedWord.substring(firstLetterIndex + 1)

                // Capitalize first alphabetic letter
                return beforeLetter + firstLetter.uppercaseChar() + afterLetter
            }
        }

        // Otherwise, keep correction in lowercase
        return correctedWord
    }

    /**
     * Checks if a word should be corrected.
     * @param word The word to check (without trailing spaces)
     * @param locale The locale to use (e.g. "it", "en"). If null, uses device locale.
     * @param context The context to verify enabled languages
     * @return The corrected word with preserved capitalization, or null if no correction.
     */
    fun getCorrection(word: String, locale: String? = null, context: Context? = null): String? {
        val targetLocale = locale ?: (context?.let { getCurrentLocale(it) } ?: "en")

        // Verify if language is enabled
        if (context != null) {
            val enabledLanguages = it.palsoftware.pastiera.SettingsManager.getAutoCorrectEnabledLanguages(context)
            if (enabledLanguages.isNotEmpty() && !enabledLanguages.contains(targetLocale)) {
                // Language not enabled, try fallback only if "en" is enabled
                if (enabledLanguages.contains("en") && targetLocale != "en") {
                    // Try "en" as fallback
                    corrections["en"]?.let { enCorrections ->
                        val wordLower = word.lowercase()
                        enCorrections[wordLower]?.let { correction ->
                            return applyCapitalization(word, correction)
                        }
                    }
                }
                return null
            }
        }

        // Search for correction using lowercase word (JSON keys are lowercase)
        val wordLower = word.lowercase()

        // Try specific locale first
        corrections[targetLocale]?.let { localeCorrections ->
            localeCorrections[wordLower]?.let { correction ->
                // Applica la capitalizzazione originale alla correzione
                return applyCapitalization(word, correction)
            }
        }

        // Fallback: try "en" if target locale has no matches
        if (targetLocale != "en") {
            // Verify if "en" is enabled (if we have context)
            if (context == null || it.palsoftware.pastiera.SettingsManager.isAutoCorrectLanguageEnabled(context, "en")) {
                corrections["en"]?.let { enCorrections ->
                    enCorrections[wordLower]?.let { correction ->
                        // Apply original capitalization to correction
                        return applyCapitalization(word, correction)
                    }
                }
            }
        }

        return null
    }

    /**
     * Processes text before cursor and applies corrections if needed.
     * Supports both single words and patterns with spaces (e.g. "cos e" → "cos'è").
     * @param textBeforeCursor The text before cursor
     * @param locale The locale to use
     * @param context The context to get locale if not specified
     * @return Pair<wordToReplace, correctedWord> if there's a correction, null otherwise
     */
    fun processText(
        textBeforeCursor: CharSequence?,
        locale: String? = null,
        context: Context? = null
    ): Pair<String, String>? {
        if (textBeforeCursor == null || textBeforeCursor.isEmpty()) {
            return null
        }

        val text = textBeforeCursor.toString()
        var endIndex = text.length

        // Ignore spaces and punctuation at the end
        val boundarySet = it.palsoftware.pastiera.core.Punctuation.BOUNDARY
        while (endIndex > 0 && (text[endIndex - 1].isWhitespace() ||
                                text[endIndex - 1] in boundarySet)) {
            endIndex--
        }

        if (endIndex == 0) {
            return null
        }

        // Get enabled languages if we have context
        val enabledLanguages = if (context != null) {
            it.palsoftware.pastiera.SettingsManager.getAutoCorrectEnabledLanguages(context)
        } else {
            emptySet<String>()
        }
        
        // If there are specific enabled languages, use those, otherwise use all available languages
        val languagesToSearch = if (enabledLanguages.isNotEmpty()) {
            enabledLanguages
        } else {
            corrections.keys.toSet()
        }
        
        // If no languages available, exit
        if (languagesToSearch.isEmpty()) {
            return null
        }

        // First, try to search for patterns that include spaces (e.g. "cos e")
        // Search up to 3 "words" (separated by spaces) before cursor
        // This allows finding patterns like "cos e", "qual e", etc.
        for (maxWords in 2 downTo 1) {
            var currentEnd = endIndex
            var wordsFound = 0
            var startIndex = currentEnd

            // Find start of maxWords word sequence
            while (startIndex > 0 && wordsFound < maxWords) {
                // Go back until finding space or punctuation
                var tempIndex = startIndex - 1
                while (tempIndex > 0 && !text[tempIndex - 1].isWhitespace() &&
                       text[tempIndex - 1] !in boundarySet) {
                    tempIndex--
                }

                if (tempIndex < startIndex) {
                    wordsFound++
                    if (wordsFound < maxWords) {
                        // Go back past space to find next word
                        while (tempIndex > 0 && (text[tempIndex - 1].isWhitespace() ||
                                                 text[tempIndex - 1] in boundarySet)) {
                            tempIndex--
                        }
                        startIndex = tempIndex
                    } else {
                        startIndex = tempIndex
                    }
                } else {
                    break
                }
            }

            if (startIndex < currentEnd && wordsFound == maxWords) {
                // Extract sequence (may contain spaces if maxWords > 1)
                val sequence = text.substring(startIndex, currentEnd).trim()
                if (sequence.isNotEmpty()) {
                    // Check if this sequence has been rejected
                    val sequenceLower = sequence.lowercase()
                    if (rejectedWords.contains(sequenceLower)) {
                        Log.d(TAG, "Sequence '$sequence' has been rejected, don't correct")
                        continue // Try with fewer words
                    }

                    // Check if there's a correction for this sequence in one of the enabled languages
                    for (lang in languagesToSearch) {
                        val correction = getCorrection(sequence, lang, context)
                        if (correction != null) {
                            Log.d(TAG, "Found correction for multi-word sequence: '$sequence' → '$correction' (language: $lang)")
                            return Pair(sequence, correction)
                        }
                    }
                }
            }
        }

        // If we didn't find patterns with spaces, search for a single word
        var startIndex = endIndex
        while (startIndex > 0 && !text[startIndex - 1].isWhitespace() &&
               text[startIndex - 1] !in boundarySet) {
            startIndex--
        }

        if (startIndex >= endIndex) {
            return null
        }

        val word = text.substring(startIndex, endIndex)
        if (word.isEmpty()) {
            return null
        }

        // Check if this word has been rejected (undone with backspace)
        val wordLower = word.lowercase()
        if (rejectedWords.contains(wordLower)) {
            Log.d(TAG, "Word '$word' has been rejected, don't correct")
            return null
        }

        // Check if there's a correction for the single word in one of the enabled languages
        for (lang in languagesToSearch) {
            val correction = getCorrection(word, lang, context)
            if (correction != null) {
                Log.d(TAG, "Found correction for word: '$word' → '$correction' (language: $lang)")
                return Pair(word, correction)
            }
        }

        return null
    }

    /**
     * Records an applied correction.
     * The correction remains undoable until a key is pressed (except backspace).
     * @param originalWord The original word
     * @param correctedWord The corrected word
     */
    fun recordCorrection(originalWord: String, correctedWord: String) {
        lastCorrection = LastCorrection(
            originalWord = originalWord,
            correctedWord = correctedWord,
            correctionLength = correctedWord.length
        )
        Log.d(TAG, "Correction recorded: '$originalWord' → '$correctedWord'")
    }

    /**
     * Gets last correction if still undoable.
     * @return Correction information if it can be undone, null otherwise
     */
    fun getLastCorrection(): LastCorrection? {
        return lastCorrection
    }

    /**
     * Accepts last correction (called when a key other than backspace is pressed).
     * After acceptance, the correction can no longer be undone.
     */
    fun acceptLastCorrection() {
        if (lastCorrection != null) {
            Log.d(TAG, "Correction accepted: '${lastCorrection!!.correctedWord}'")
            lastCorrection = null
        }
    }

    /**
     * Undoes last correction.
     * After undo, the correction can no longer be undone.
     * The original word is added to the rejected words list
     * to avoid immediately re-proposing the same correction.
     * @return Information about undone correction, null if there was no correction
     */
    fun undoLastCorrection(): LastCorrection? {
        val correction = lastCorrection
        if (correction != null) {
            Log.d(TAG, "Correction undone: '${correction.correctedWord}' → '${correction.originalWord}'")

            // Add original word to rejected words list
            // (use lowercase for comparison, so it works regardless of capitalization)
            rejectedWords.add(correction.originalWord.lowercase())
            Log.d(TAG, "Word '${correction.originalWord}' added to rejected list")

            lastCorrection = null
            return correction
        }
        return null
    }

    /**
     * Resets the rejected words list.
     * Called when user types a new character (not backspace),
     * indicating they may have modified the text and thus rejected corrections
     * might no longer be valid.
     */
    fun clearRejectedWords() {
        if (rejectedWords.isNotEmpty()) {
            Log.d(TAG, "Reset rejected words list (${rejectedWords.size} words)")
            rejectedWords.clear()
        }
    }

}

