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
        // SymSpell 80k dictionary in fdic format (fast loading, ~595KB)
        private const val DICTIONARY_PATH = "dictionaries/en_80k.fdic"

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
                // Use default settings like the SymSpellKt sample app
                val checker = SymSpell()

                // Load dictionary from fdic binary format (faster than text)
                val startTime = System.currentTimeMillis()
                val dictBytes = assets.open(DICTIONARY_PATH).use { it.readBytes() }
                checker.dictionary.loadFdicFile(dictBytes)

                val loadTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Dictionary loaded in ${loadTime}ms, ${checker.dictionary.wordCount} words")


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
     * Determine the edit type between input and suggestion.
     */
    private enum class EditType { DELETE, SUBSTITUTE, INSERT, OTHER }

    private fun getEditType(input: String, suggestion: String): EditType {
        val inputLen = input.length
        val suggestionLen = suggestion.length

        return when {
            suggestionLen == inputLen - 1 -> EditType.DELETE      // suggestion is shorter (user typed extra char)
            suggestionLen == inputLen -> EditType.SUBSTITUTE       // same length (substitution)
            suggestionLen == inputLen + 1 -> EditType.INSERT       // suggestion is longer (user missed a char)
            else -> EditType.OTHER
        }
    }

    /**
     * Check if input has adjacent duplicate letters that could be a typo.
     * e.g., "helllo" has "lll", "bookk" has "kk"
     */
    private fun hasAdjacentDuplicates(word: String): Boolean {
        for (i in 0 until word.length - 1) {
            if (word[i] == word[i + 1]) {
                return true
            }
        }
        return false
    }

    /**
     * Check if suggestion "fixes" a duplicate letter issue in the input.
     * e.g., input "teech" (has 'ee') → suggestion "teach" (has 'ea') = fixes duplicate
     *       input "teech" (has 'ee') → suggestion "teeth" (has 'ee') = keeps duplicate
     */
    private fun fixesDuplicateLetter(input: String, suggestion: String): Boolean {
        if (input.length != suggestion.length) return false

        // Find where input has adjacent duplicates
        for (i in 0 until input.length - 1) {
            if (input[i] == input[i + 1]) {
                // Check if suggestion breaks this duplicate
                if (i < suggestion.length - 1 && suggestion[i] != suggestion[i + 1]) {
                    return true
                }
            }
        }
        return false
    }

    // QWERTY keyboard layout with row and column positions
    private val keyboardPositions: Map<Char, Pair<Int, Int>> = mapOf(
        // Row 0
        'q' to (0 to 0), 'w' to (0 to 1), 'e' to (0 to 2), 'r' to (0 to 3), 't' to (0 to 4),
        'y' to (0 to 5), 'u' to (0 to 6), 'i' to (0 to 7), 'o' to (0 to 8), 'p' to (0 to 9),
        // Row 1 (offset by 0.5 on real keyboard, we use column + 0.5 effect via distance calc)
        'a' to (1 to 0), 's' to (1 to 1), 'd' to (1 to 2), 'f' to (1 to 3), 'g' to (1 to 4),
        'h' to (1 to 5), 'j' to (1 to 6), 'k' to (1 to 7), 'l' to (1 to 8),
        // Row 2
        'z' to (2 to 0), 'x' to (2 to 1), 'c' to (2 to 2), 'v' to (2 to 3), 'b' to (2 to 4),
        'n' to (2 to 5), 'm' to (2 to 6)
    )

    /**
     * Calculate keyboard distance between two characters.
     * Returns a value where lower = closer keys.
     * Returns null if either character is not on the keyboard map.
     */
    private fun keyboardDistance(c1: Char, c2: Char): Double? {
        val pos1 = keyboardPositions[c1.lowercaseChar()] ?: return null
        val pos2 = keyboardPositions[c2.lowercaseChar()] ?: return null
        val rowDiff = (pos1.first - pos2.first).toDouble()
        val colDiff = (pos1.second - pos2.second).toDouble()
        return kotlin.math.sqrt(rowDiff * rowDiff + colDiff * colDiff)
    }

    /**
     * Check if a substitution involves adjacent/nearby keys (likely typo)
     * vs distant keys (unlikely typo).
     * Returns true if all substituted characters are within 2 keys of each other.
     */
    private fun isNearbySubstitution(input: String, suggestion: String): Boolean {
        if (input.length != suggestion.length) return true // Not a substitution

        for (i in input.indices) {
            if (input[i].lowercaseChar() != suggestion[i].lowercaseChar()) {
                val dist = keyboardDistance(input[i], suggestion[i])
                // If distance is > 2.5 keys, it's a distant substitution (unlikely typo)
                if (dist != null && dist > 2.5) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Check if a substitution involves truly adjacent keys (directly touching).
     * Adjacent keys have distance <= 1.1 (same row or diagonal neighbors).
     * Used to determine if substitute should beat insert in ranking.
     */
    private fun isAdjacentSubstitution(input: String, suggestion: String): Boolean {
        if (input.length != suggestion.length) return false // Not a substitution

        for (i in input.indices) {
            if (input[i].lowercaseChar() != suggestion[i].lowercaseChar()) {
                val dist = keyboardDistance(input[i], suggestion[i])
                // Only truly adjacent keys (distance ~1.0) count
                // h-m is 1.41 which is NOT adjacent, h-n is 1.0 which IS adjacent
                if (dist == null || dist > 1.15) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Get spelling suggestions for a word.
     * Priority: Insert > Substitute > Delete (only if duplicates exist)
     *
     * @param word The word to check
     * @param maxSuggestions Maximum number of suggestions to return
     * @return List of suggestions prioritizing inserts, then substitutes
     */
    fun suggest(word: String, maxSuggestions: Int = 3): List<SuggestionResult> {
        if (!isReady || word.isBlank() || word.length < 2) {
            return emptyList()
        }

        val checker = cachedSpellChecker ?: return emptyList()

        return try {
            val normalizedWord = word.lowercase(Locale.getDefault())
            val allSuggestions = checker.lookup(normalizedWord, Verbosity.All, 2.0)

            // Group by edit type
            val byEditType = allSuggestions
                .groupBy { getEditType(normalizedWord, it.term) }

            if (debugLogging) {
                Log.d(TAG, "byEditType for '$normalizedWord': INSERT=${byEditType[EditType.INSERT]?.take(3)?.map { it.term }}, SUB=${byEditType[EditType.SUBSTITUTE]?.take(3)?.map { it.term }}, DEL=${byEditType[EditType.DELETE]?.take(3)?.map { it.term }}")
            }

            // Get best (highest frequency) from each category, filtering by distance 1 first
            val dist1Insert = byEditType[EditType.INSERT]?.filter { it.distance <= 1.0 }?.maxByOrNull { it.frequency }

            // For substitutes: filter out distant key substitutions, prefer adjacent keys
            val dist1Substitutes = byEditType[EditType.SUBSTITUTE]
                ?.filter { it.distance <= 1.0 }
                ?.filter { isNearbySubstitution(normalizedWord, it.term) }  // Filter out distant keys like m→w

            // Prefer adjacent key substitutes (dist <= 1.15) over non-adjacent
            // e.g., "hust" -> "just" (h->j adjacent) over "must" (h->m not adjacent)
            val adjacentSubstitutes = dist1Substitutes?.filter { isAdjacentSubstitution(normalizedWord, it.term) }
            val nonAdjacentSubstitutes = dist1Substitutes?.filter { !isAdjacentSubstitution(normalizedWord, it.term) }

            val dist1Substitute = if (hasAdjacentDuplicates(normalizedWord) && dist1Substitutes != null) {
                // First try to find one that fixes a duplicate letter (from adjacent first, then non-adjacent)
                adjacentSubstitutes?.filter { fixesDuplicateLetter(normalizedWord, it.term) }?.maxByOrNull { it.frequency }
                    ?: nonAdjacentSubstitutes?.filter { fixesDuplicateLetter(normalizedWord, it.term) }?.maxByOrNull { it.frequency }
                    ?: adjacentSubstitutes?.maxByOrNull { it.frequency }
                    ?: nonAdjacentSubstitutes?.maxByOrNull { it.frequency }
            } else {
                // Prefer adjacent substitutes, fall back to non-adjacent
                adjacentSubstitutes?.maxByOrNull { it.frequency }
                    ?: nonAdjacentSubstitutes?.maxByOrNull { it.frequency }
            }

            val dist1Delete = if (hasAdjacentDuplicates(normalizedWord)) {
                byEditType[EditType.DELETE]?.filter { it.distance <= 1.0 }?.maxByOrNull { it.frequency }
            } else {
                null
            }

            // Fallback to distance 2 if no distance 1 options
            val bestInsert = dist1Insert ?: byEditType[EditType.INSERT]?.maxByOrNull { it.frequency }
            // Also filter and prefer adjacent key substitutions in fallback
            val allSubstitutes = byEditType[EditType.SUBSTITUTE]
                ?.filter { isNearbySubstitution(normalizedWord, it.term) }
            val allAdjacentSubs = allSubstitutes?.filter { isAdjacentSubstitution(normalizedWord, it.term) }
            val allNonAdjacentSubs = allSubstitutes?.filter { !isAdjacentSubstitution(normalizedWord, it.term) }
            val bestSubstitute = dist1Substitute ?: if (hasAdjacentDuplicates(normalizedWord) && allSubstitutes != null) {
                allAdjacentSubs?.filter { fixesDuplicateLetter(normalizedWord, it.term) }?.maxByOrNull { it.frequency }
                    ?: allNonAdjacentSubs?.filter { fixesDuplicateLetter(normalizedWord, it.term) }?.maxByOrNull { it.frequency }
                    ?: allAdjacentSubs?.maxByOrNull { it.frequency }
                    ?: allNonAdjacentSubs?.maxByOrNull { it.frequency }
            } else {
                allAdjacentSubs?.maxByOrNull { it.frequency }
                    ?: allNonAdjacentSubs?.maxByOrNull { it.frequency }
            }
            val bestDelete = dist1Delete ?: if (hasAdjacentDuplicates(normalizedWord)) {
                byEditType[EditType.DELETE]?.maxByOrNull { it.frequency }
            } else {
                null
            }

            // Priority: distance 1 options first, ordered by edit type
            // Insert first UNLESS substitute is truly adjacent keys (distance <= 1.15)
            // "hight" -> prefer "height" (insert) over "might" (h-m not adjacent at 1.41)
            // But "thr" -> could prefer "the" (substitute r->e, adjacent) if available
            val substituteIsAdjacent = dist1Substitute?.let {
                isAdjacentSubstitution(normalizedWord, it.term)
            } ?: false

            val dist1Options = if (substituteIsAdjacent && dist1Substitute != null) {
                // Substitute involves adjacent keys - could be either, use frequency
                listOfNotNull(dist1Insert, dist1Substitute, dist1Delete)
                    .sortedByDescending { it.frequency }
            } else {
                // Substitute NOT adjacent - prefer insert over substitute
                listOfNotNull(dist1Insert, dist1Substitute, dist1Delete)
            }
            val dist2Options = listOfNotNull(bestInsert, bestSubstitute, bestDelete)
                .filter { it !in dist1Options }

            val suggestions = (dist1Options + dist2Options)
                .take(maxSuggestions)
                .map { item ->
                    SuggestionResult(
                        candidate = item.term,
                        distance = item.distance.toInt(),
                        score = item.frequency,
                        source = SuggestionSource.MAIN
                    )
                }

            if (debugLogging) {
                Log.d(TAG, "suggest('$word') -> ${suggestions.map { "${it.candidate}" }}")
            }

            suggestions
        } catch (e: Exception) {
            Log.e(TAG, "Error getting suggestions for '$word'", e)
            emptyList()
        }
    }

    /**
     * Check if a word is correctly spelled using SymSpell dictionary.
     * A word is known if it exists with edit distance 0 in the dictionary.
     *
     * @param word The word to check
     * @return true if the word is spelled correctly, false if misspelled
     */
    fun isKnownWord(word: String): Boolean {
        if (word.isBlank()) return true // Empty words are "correct"
        if (!isReady) return true // Assume correct if dictionary not ready

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
