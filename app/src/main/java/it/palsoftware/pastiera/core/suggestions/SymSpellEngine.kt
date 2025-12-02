package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
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

    // Main thread handler for spell checker operations
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Initialize Android's native spell checker session.
     * Must be called on main thread.
     */
    private fun initNativeSpellChecker() {
        if (nativeSpellCheckerSession != null || textServicesManager == null) return

        // Must run on main thread
        val initRunnable = Runnable {
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

        if (Looper.myLooper() == Looper.getMainLooper()) {
            initRunnable.run()
        } else {
            mainHandler.post(initRunnable)
        }
    }

    /**
     * Get spelling suggestions for a word.
     *
     * @param word The word to check
     * @param maxSuggestions Maximum number of suggestions to return
     * @return List of suggestions sorted by relevance (keyboard-aware)
     */
    fun suggest(word: String, maxSuggestions: Int = 3): List<SuggestionResult> {
        if (!isReady || word.isBlank() || word.length < 2) {
            return emptyList()
        }

        val checker = cachedSpellChecker ?: return emptyList()

        return try {
            // Get more candidates than needed for re-ranking
            val candidateCount = maxSuggestions * 3
            val suggestions = checker.lookup(word.lowercase(Locale.getDefault()), Verbosity.Closest, 2.0)
                .take(candidateCount)
                .map { item ->
                    SuggestionResult(
                        candidate = item.term,
                        distance = item.distance.toInt(),
                        score = item.frequency,
                        source = SuggestionSource.MAIN
                    )
                }

            // Re-rank using keyboard proximity
            val reRanked = KeyboardProximity.reRankSuggestions(word, suggestions)
                .take(maxSuggestions)

            if (debugLogging) {
                Log.d(TAG, "suggest('$word') -> ${reRanked.map { "${it.candidate}" }} (keyboard-aware)")
            }

            reRanked
        } catch (e: Exception) {
            Log.e(TAG, "Error getting suggestions for '$word'", e)
            emptyList()
        }
    }

    /**
     * Check if a word is correctly spelled using Android's native spell checker.
     * Falls back to SymSpell if native checker is unavailable.
     *
     * @param word The word to check
     * @return true if the word is spelled correctly, false if misspelled
     */
    fun isKnownWord(word: String): Boolean {
        if (word.isBlank()) return true // Empty words are "correct"

        // Try native spell checker first (more comprehensive dictionary)
        val nativeResult = isKnownWordNative(word)
        if (nativeResult != null) {
            if (debugLogging) {
                Log.d(TAG, "isKnownWord('$word') -> $nativeResult (native spell checker)")
            }
            return nativeResult
        }

        // Fall back to SymSpell if native checker unavailable
        if (!isReady) return true // Assume correct if not ready
        return isKnownWordSymSpell(word)
    }

    /**
     * Check spelling using Android's native SpellCheckerSession.
     * @return true if spelled correctly, false if misspelled, null if unavailable
     */
    private fun isKnownWordNative(word: String): Boolean? {
        if (textServicesManager == null) return null

        // Use a synchronous approach with CountDownLatch
        val resultRef = AtomicReference<Boolean?>(null)
        val latch = CountDownLatch(1)

        val checkRunnable = Runnable {
            try {
                val listener = object : SpellCheckerSession.SpellCheckerSessionListener {
                    override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {
                        if (results != null && results.isNotEmpty()) {
                            val info = results[0]
                            // RESULT_ATTR_IN_THE_DICTIONARY means word is correct
                            // RESULT_ATTR_LOOKS_LIKE_TYPO means word is misspelled
                            val isCorrect = (info.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) != 0
                            val isTypo = (info.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO) != 0

                            // Word is known if it's in dictionary OR if it's not flagged as a typo
                            resultRef.set(isCorrect || !isTypo)

                            if (debugLogging) {
                                Log.d(TAG, "Native spell check for '$word': inDict=$isCorrect, typo=$isTypo, attrs=${info.suggestionsAttributes}")
                            }
                        }
                        latch.countDown()
                    }

                    override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
                        latch.countDown()
                    }
                }

                // Create a new session for this check (must be on main thread)
                val checkSession = textServicesManager.newSpellCheckerSession(
                    null,
                    Locale.getDefault(),
                    listener,
                    true
                )

                if (checkSession == null) {
                    latch.countDown()
                    return@Runnable
                }

                // Request spell check
                checkSession.getSuggestions(TextInfo(word), 0)

                // The callback will be invoked and close the latch
                // We need to close the session after getting results
                // Note: This is handled asynchronously via the callback
            } catch (e: Exception) {
                if (debugLogging) {
                    Log.e(TAG, "Error in native spell check for '$word'", e)
                }
                latch.countDown()
            }
        }

        // Run on main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            checkRunnable.run()
        } else {
            mainHandler.post(checkRunnable)
        }

        // Wait for result with timeout
        return try {
            val completed = latch.await(150, TimeUnit.MILLISECONDS)
            if (!completed) {
                if (debugLogging) {
                    Log.d(TAG, "Native spell check timed out for '$word'")
                }
                null
            } else {
                resultRef.get()
            }
        } catch (e: Exception) {
            if (debugLogging) {
                Log.e(TAG, "Error waiting for spell check result for '$word'", e)
            }
            null
        }
    }

    /**
     * Fallback method using SymSpell to check if word exists.
     */
    private fun isKnownWordSymSpell(word: String): Boolean {
        val checker = cachedSpellChecker ?: return true // Assume correct if no checker

        return try {
            // Use lookup with edit distance 0 and check if we get an exact match
            val normalized = word.lowercase(Locale.getDefault())
            val suggestions = checker.lookup(normalized, Verbosity.Top, 0.0)

            // Check if any suggestion is an exact match (distance 0)
            val isKnown = suggestions.any { it.term == normalized && it.distance == 0.0 }

            if (debugLogging) {
                val sugList = suggestions.take(3).joinToString { "${it.term}:${it.distance}" }
                Log.d(TAG, "isKnownWord('$word') -> $isKnown (SymSpell fallback, suggestions: $sugList)")
            }

            isKnown
        } catch (e: Exception) {
            if (debugLogging) {
                Log.e(TAG, "Error checking if word is known: '$word'", e)
            }
            true // Assume correct on error
        }
    }
}
