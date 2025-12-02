package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Spelling suggestion engine powered by SymSpell algorithm.
 * SymSpell provides fast, accurate spelling correction using symmetric delete algorithm.
 *
 * Uses Android's native spell checker to validate words, SymSpell for suggestions.
 * The dictionary is cached statically so it persists across IME recreations.
 */
class SymSpellEngine(
    private val context: Context,
    private val assets: AssetManager,
    private val debugLogging: Boolean = false
) {
    companion object {
        private const val TAG = "SymSpellEngine"
        // SymSpell 30k dictionary (fast loading, ~130KB)
        private const val DICTIONARY_PATH = "dictionaries/en_30k.fdic"

        // Static cache - survives IME recreations
        @Volatile
        private var cachedSpellChecker: SpellChecker? = null
        private val loadMutex = Mutex()

        @Volatile
        private var isLoaded: Boolean = false
    }

    // Android native spell checker for word validation
    private val textServicesManager = context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
    private var nativeSpellCheckerSession: SpellCheckerSession? = null

    val isReady: Boolean
        get() = isLoaded

    /**
     * Load the dictionary. Must be called from a background thread.
     * Dictionary is cached statically so subsequent calls are instant.
     */
    suspend fun loadDictionary() = withContext(Dispatchers.IO) {
        if (isLoaded) {
            // Initialize native spell checker if not already done
            initNativeSpellChecker()
            return@withContext
        }

        loadMutex.withLock {
            // Double-check after acquiring lock
            if (isLoaded) {
                initNativeSpellChecker()
                return@withContext
            }

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

                // Initialize native spell checker
                initNativeSpellChecker()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load dictionary", e)
            }
        }
    }

    /**
     * Initialize Android's native spell checker session.
     * Must be called on main thread or use Handler.
     */
    private fun initNativeSpellChecker() {
        if (nativeSpellCheckerSession != null || textServicesManager == null) return

        try {
            // Create a dummy listener since we'll use synchronous calls
            val listener = object : SpellCheckerSession.SpellCheckerSessionListener {
                override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {}
                override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {}
            }

            nativeSpellCheckerSession = textServicesManager.newSpellCheckerSession(
                null, // Use default locale
                Locale.getDefault(),
                listener,
                true // Referencing TextInfos for synchronous calls
            )

            if (debugLogging) {
                Log.d(TAG, "Native spell checker session initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize native spell checker", e)
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
     * Check if a word is in the dictionary using Android's native spell checker.
     * Falls back to SymSpell if native checker is unavailable.
     */
    fun isKnownWord(word: String): Boolean {
        if (!isReady || word.isBlank()) return false

        // Try using native Android spell checker first
        val session = nativeSpellCheckerSession
        if (session != null) {
            return try {
                val textInfo = TextInfo(word)
                val latch = CountDownLatch(1)
                val result = AtomicReference<Boolean>(false)

                // Use async API with callback
                val listener = object : SpellCheckerSession.SpellCheckerSessionListener {
                    override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {
                        if (results != null && results.isNotEmpty()) {
                            val info = results[0]
                            val isInDictionary = (info.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) != 0
                            result.set(isInDictionary)
                        }
                        latch.countDown()
                    }

                    override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
                        latch.countDown()
                    }
                }

                // Create temporary session for this check
                val tempSession = textServicesManager?.newSpellCheckerSession(
                    null,
                    Locale.getDefault(),
                    listener,
                    false
                )

                tempSession?.getSuggestions(textInfo, 5)

                // Wait up to 100ms for result
                val gotResult = latch.await(100, TimeUnit.MILLISECONDS)
                tempSession?.close()

                val isInDictionary = if (gotResult) result.get() else false

                if (debugLogging) {
                    Log.d(TAG, "isKnownWord('$word') -> $isInDictionary (native spell checker, gotResult=$gotResult)")
                }

                return isInDictionary
            } catch (e: Exception) {
                if (debugLogging) {
                    Log.w(TAG, "Native spell checker failed for '$word', falling back to SymSpell", e)
                }
                // Fall back to SymSpell check
                isKnownWordSymSpell(word)
            }
        }

        // Fallback to SymSpell if native checker unavailable
        return isKnownWordSymSpell(word)
    }

    /**
     * Fallback method using SymSpell to check if word exists.
     */
    private fun isKnownWordSymSpell(word: String): Boolean {
        val checker = cachedSpellChecker ?: return false

        return try {
            // Use lookup with edit distance 0 and check if we get an exact match
            val normalized = word.lowercase(Locale.getDefault())
            val suggestions = checker.lookup(normalized, Verbosity.Top, 0.0)

            // Check if any suggestion is an exact match (distance 0)
            val isKnown = suggestions.any { it.term == normalized && it.distance == 0.0 }

            if (debugLogging) {
                Log.d(TAG, "isKnownWord('$word') -> $isKnown (SymSpell fallback)")
            }

            isKnown
        } catch (e: Exception) {
            if (debugLogging) {
                Log.e(TAG, "Error checking if word is known: '$word'", e)
            }
            false
        }
    }
}
