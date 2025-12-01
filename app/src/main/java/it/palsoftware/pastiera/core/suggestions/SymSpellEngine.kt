package it.palsoftware.pastiera.core.suggestions

import android.content.res.AssetManager
import android.util.Log
import com.darkrockstudios.symspellkt.api.SpellChecker
import com.darkrockstudios.symspellkt.common.SpellCheckSettings
import com.darkrockstudios.symspellkt.common.Verbosity
import com.darkrockstudios.symspell.fdic.loadFdicFile
import com.darkrockstudios.symspellkt.impl.SymSpell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Spelling suggestion engine powered by SymSpell algorithm.
 * SymSpell provides fast, accurate spelling correction using symmetric delete algorithm.
 *
 * The dictionary is cached statically so it persists across IME recreations.
 */
class SymSpellEngine(
    private val assets: AssetManager,
    private val debugLogging: Boolean = false
) {
    companion object {
        private const val TAG = "SymSpellEngine"
        // SymSpell 20k dictionary with texting abbreviations (fast loading)
        private const val DICTIONARY_PATH = "dictionaries/en_aosp_20k.fdic"

        // Static cache - survives IME recreations
        @Volatile
        private var cachedSpellChecker: SpellChecker? = null
        private val loadMutex = Mutex()

        @Volatile
        private var isLoaded: Boolean = false
    }

    val isReady: Boolean
        get() = isLoaded

    /**
     * Load the dictionary. Must be called from a background thread.
     * Dictionary is cached statically so subsequent calls are instant.
     */
    suspend fun loadDictionary() = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext

        loadMutex.withLock {
            // Double-check after acquiring lock
            if (isLoaded) return@withContext

            try {
                val settings = SpellCheckSettings(
                    maxEditDistance = 2.0,
                    prefixLength = 7,
                    verbosity = Verbosity.Closest
                )

                val checker = SymSpell(settings)

                // Load dictionary from fdic binary format (70% faster than txt)
                val startTime = System.currentTimeMillis()

                val dictBytes = assets.open(DICTIONARY_PATH).use { it.readBytes() }
                checker.dictionary.loadFdicFile(dictBytes)

                val loadTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Dictionary loaded in ${loadTime}ms (20k words)")

                cachedSpellChecker = checker
                isLoaded = true

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load dictionary", e)
            }
        }
    }

    /**
     * Get spelling suggestions for a word.
     *
     * @param word The word to check
     * @param maxSuggestions Maximum number of suggestions to return
     * @return List of suggestions sorted by relevance
     */
    fun suggest(word: String, maxSuggestions: Int = 3): List<SuggestionResult> {
        if (!isReady || word.isBlank() || word.length < 2) {
            return emptyList()
        }

        val checker = cachedSpellChecker ?: return emptyList()

        return try {
            // Third param is maxEditDistance (must be <= settings.maxEditDistance of 2.0)
            // Use Verbosity.Closest to get best matches, then take maxSuggestions
            val suggestions = checker.lookup(word.lowercase(Locale.getDefault()), Verbosity.Closest, 2.0)
                .take(maxSuggestions)

            if (debugLogging) {
                Log.d(TAG, "suggest('$word') -> ${suggestions.map { "${it.term}:${it.distance}" }}")
            }

            suggestions.map { item ->
                SuggestionResult(
                    candidate = item.term,
                    distance = item.distance.toInt(),
                    score = item.frequency,
                    source = SuggestionSource.MAIN
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting suggestions for '$word'", e)
            emptyList()
        }
    }

    /**
     * Check if a word is in the dictionary.
     */
    fun isKnownWord(word: String): Boolean {
        if (!isReady || word.isBlank()) return false

        val checker = cachedSpellChecker ?: return false

        return try {
            // Use edit distance 0 to check for exact matches only
            val suggestions = checker.lookup(word.lowercase(Locale.getDefault()), Verbosity.Closest, 0.0)
            suggestions.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
