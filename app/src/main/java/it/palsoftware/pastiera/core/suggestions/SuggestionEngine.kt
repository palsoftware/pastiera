package it.palsoftware.pastiera.core.suggestions

import kotlin.math.min
import java.text.Normalizer
import java.util.Locale
import android.util.Log

data class SuggestionResult(
    val candidate: String,
    val distance: Int,
    val score: Double,
    val source: SuggestionSource
)

class SuggestionEngine(
    private val repository: DictionaryRepository,
    private val locale: Locale = Locale.ITALIAN,
    private val debugLogging: Boolean = false
) {

    // Keep only Unicode letters (supports Latin, Cyrillic, Greek, Arabic, Chinese, etc.)
    // Removes: punctuation, numbers, spaces, emoji, symbols
    private val normalizeRegex = "[^\\p{L}]".toRegex()
    private val accentCache: MutableMap<String, String> = mutableMapOf()
    private val tag = "SuggestionEngine"
    private val wordNormalizeCache: MutableMap<String, String> = mutableMapOf()

    // Keyboard layout positions - built dynamically based on layout type
    private var keyboardPositions: Map<Char, Pair<Int, Int>> = buildKeyboardPositions("qwerty")

    /**
     * Build character-to-position map for a given keyboard layout.
     * Physical key positions match the actual Pastiera compact keyboard layout:
     * - Row 0: Q W E R T Y U I O P
     * - Row 1: A S D F G H J K L
     * - Row 2: Z X C V [space] B N M
     */
    private fun buildKeyboardPositions(layout: String): Map<Char, Pair<Int, Int>> {
        // Physical key positions (row, column) for compact keyboard with split bottom row
        val physicalPositions = mapOf(
            // Row 0 (top letter row): Q W E R T Y U I O P
            "KEYCODE_Q" to (0 to 0), "KEYCODE_W" to (0 to 1), "KEYCODE_E" to (0 to 2),
            "KEYCODE_R" to (0 to 3), "KEYCODE_T" to (0 to 4), "KEYCODE_Y" to (0 to 5),
            "KEYCODE_U" to (0 to 6), "KEYCODE_I" to (0 to 7), "KEYCODE_O" to (0 to 8),
            "KEYCODE_P" to (0 to 9),
            // Row 1 (home row): A S D F G H J K L
            "KEYCODE_A" to (1 to 0), "KEYCODE_S" to (1 to 1), "KEYCODE_D" to (1 to 2),
            "KEYCODE_F" to (1 to 3), "KEYCODE_G" to (1 to 4), "KEYCODE_H" to (1 to 5),
            "KEYCODE_J" to (1 to 6), "KEYCODE_K" to (1 to 7), "KEYCODE_L" to (1 to 8),
            // Row 2 (bottom row left): Z X C V
            "KEYCODE_Z" to (2 to 0), "KEYCODE_X" to (2 to 1), "KEYCODE_C" to (2 to 2),
            "KEYCODE_V" to (2 to 3),
            // Row 2 (bottom row right, after spacebar): B N M
            "KEYCODE_B" to (2 to 6), "KEYCODE_N" to (2 to 7), "KEYCODE_M" to (2 to 8)
        )

        // Map keycodes to characters for each layout
        val layoutMappings = when (layout.lowercase()) {
            "qwerty" -> mapOf(
                "KEYCODE_Q" to 'q', "KEYCODE_W" to 'w', "KEYCODE_E" to 'e', "KEYCODE_R" to 'r',
                "KEYCODE_T" to 't', "KEYCODE_Y" to 'y', "KEYCODE_U" to 'u', "KEYCODE_I" to 'i',
                "KEYCODE_O" to 'o', "KEYCODE_P" to 'p', "KEYCODE_A" to 'a', "KEYCODE_S" to 's',
                "KEYCODE_D" to 'd', "KEYCODE_F" to 'f', "KEYCODE_G" to 'g', "KEYCODE_H" to 'h',
                "KEYCODE_J" to 'j', "KEYCODE_K" to 'k', "KEYCODE_L" to 'l', "KEYCODE_Z" to 'z',
                "KEYCODE_X" to 'x', "KEYCODE_C" to 'c', "KEYCODE_V" to 'v', "KEYCODE_B" to 'b',
                "KEYCODE_N" to 'n', "KEYCODE_M" to 'm'
            )
            "azerty" -> mapOf(
                "KEYCODE_Q" to 'a', "KEYCODE_W" to 'z', "KEYCODE_E" to 'e', "KEYCODE_R" to 'r',
                "KEYCODE_T" to 't', "KEYCODE_Y" to 'y', "KEYCODE_U" to 'u', "KEYCODE_I" to 'i',
                "KEYCODE_O" to 'o', "KEYCODE_P" to 'p', "KEYCODE_A" to 'q', "KEYCODE_S" to 's',
                "KEYCODE_D" to 'd', "KEYCODE_F" to 'f', "KEYCODE_G" to 'g', "KEYCODE_H" to 'h',
                "KEYCODE_J" to 'j', "KEYCODE_K" to 'k', "KEYCODE_L" to 'l', "KEYCODE_Z" to 'w',
                "KEYCODE_X" to 'x', "KEYCODE_C" to 'c', "KEYCODE_V" to 'v', "KEYCODE_B" to 'b',
                "KEYCODE_N" to 'n', "KEYCODE_M" to 'm'
            )
            "qwertz" -> mapOf(
                "KEYCODE_Q" to 'q', "KEYCODE_W" to 'w', "KEYCODE_E" to 'e', "KEYCODE_R" to 'r',
                "KEYCODE_T" to 't', "KEYCODE_Y" to 'z', "KEYCODE_U" to 'u', "KEYCODE_I" to 'i',
                "KEYCODE_O" to 'o', "KEYCODE_P" to 'p', "KEYCODE_A" to 'a', "KEYCODE_S" to 's',
                "KEYCODE_D" to 'd', "KEYCODE_F" to 'f', "KEYCODE_G" to 'g', "KEYCODE_H" to 'h',
                "KEYCODE_J" to 'j', "KEYCODE_K" to 'k', "KEYCODE_L" to 'l', "KEYCODE_Z" to 'y',
                "KEYCODE_X" to 'x', "KEYCODE_C" to 'c', "KEYCODE_V" to 'v', "KEYCODE_B" to 'b',
                "KEYCODE_N" to 'n', "KEYCODE_M" to 'm'
            )
            else -> return buildKeyboardPositions("qwerty") // Fallback to QWERTY
        }

        // Build character to position map
        return layoutMappings.mapNotNull { (keycode, char) ->
            physicalPositions[keycode]?.let { position ->
                char to position
            }
        }.toMap()
    }

    /**
     * Update the keyboard layout for proximity calculations.
     */
    fun setKeyboardLayout(layout: String) {
        keyboardPositions = buildKeyboardPositions(layout)
    }

    private enum class EditType { DELETE, SUBSTITUTE, INSERT, OTHER }

    /**
     * Determine the edit type between input and suggestion.
     */
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

    /**
     * Calculate keyboard distance between two characters.
     */
    private fun keyboardDistance(c1: Char, c2: Char): Double? {
        val pos1 = keyboardPositions[c1.lowercaseChar()] ?: return null
        val pos2 = keyboardPositions[c2.lowercaseChar()] ?: return null
        val rowDiff = (pos1.first - pos2.first).toDouble()
        val colDiff = (pos1.second - pos2.second).toDouble()
        return kotlin.math.sqrt(rowDiff * rowDiff + colDiff * colDiff)
    }

    /**
     * Check if input and suggestion differ by a simple adjacent character transposition.
     * E.g., "teh" ↔ "the", "hte" ↔ "the", "thier" ↔ "their"
     */
    private fun isTransposition(input: String, suggestion: String): Boolean {
        if (input.length != suggestion.length) return false

        var diffCount = 0
        var firstDiffIndex = -1

        for (i in input.indices) {
            if (input[i].lowercaseChar() != suggestion[i].lowercaseChar()) {
                if (diffCount == 0) {
                    firstDiffIndex = i
                }
                diffCount++
            }
        }

        // Must have exactly 2 differences
        if (diffCount != 2) return false

        // Differences must be adjacent positions
        val secondDiffIndex = firstDiffIndex + 1
        if (secondDiffIndex >= input.length) return false

        // Check if characters are swapped
        return input[firstDiffIndex].lowercaseChar() == suggestion[secondDiffIndex].lowercaseChar() &&
               input[secondDiffIndex].lowercaseChar() == suggestion[firstDiffIndex].lowercaseChar()
    }

    /**
     * Check if a substitution involves adjacent/nearby keys (likely typo).
     * Transpositions are always considered nearby regardless of key distance.
     */
    private fun isNearbySubstitution(input: String, suggestion: String): Boolean {
        if (input.length != suggestion.length) return true // Not a substitution

        // Transpositions (adjacent character swaps) are always considered nearby typos
        if (isTransposition(input, suggestion)) {
            return true
        }

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
     * Transpositions are always considered adjacent substitutions.
     */
    private fun isAdjacentSubstitution(input: String, suggestion: String): Boolean {
        if (input.length != suggestion.length) return false // Not a substitution

        // Transpositions are always considered "adjacent" for ranking purposes
        if (isTransposition(input, suggestion)) {
            return true
        }

        for (i in input.indices) {
            if (input[i].lowercaseChar() != suggestion[i].lowercaseChar()) {
                val dist = keyboardDistance(input[i], suggestion[i])
                // Only truly adjacent keys (distance ~1.0) count
                if (dist == null || dist > 1.15) {
                    return false
                }
            }
        }
        return true
    }

    fun suggest(
        currentWord: String,
        limit: Int = 3,
        includeAccentMatching: Boolean = true,
        useKeyboardProximity: Boolean = true,
        useEditTypeRanking: Boolean = true
    ): List<SuggestionResult> {
        if (currentWord.isBlank()) return emptyList()
        if (!repository.isReady) return emptyList()
        val normalizedWord = normalize(currentWord)
        // Require at least 1 character to start suggesting.
        if (normalizedWord.length < 1) return emptyList()

        // SymSpell lookup on normalized input
        val symResultsPrimary = repository.symSpellLookup(normalizedWord, maxSuggestions = limit * 8)
        val symResultsAccent = if (includeAccentMatching) {
            val normalizedAccentless = stripAccents(normalizedWord)
            if (normalizedAccentless != normalizedWord) {
                repository.symSpellLookup(normalizedAccentless, maxSuggestions = limit * 4)
            } else emptyList()
        } else emptyList()

        val allSymResults = (symResultsPrimary + symResultsAccent)
        // Force prefix completions: take frequent words that start with the input (distance 0)
        val completions = repository.lookupByPrefixMerged(normalizedWord, maxSize = 120)
            .filter {
                val norm = normalizeCached(it.word)
                norm.startsWith(normalizedWord) && it.word.length > currentWord.length
            }

        val seen = HashSet<String>(limit * 3)
        val top = ArrayList<SuggestionResult>(limit)
        val inputLen = normalizedWord.length
        val comparator = Comparator<SuggestionResult> { a, b ->
            val d = a.distance.compareTo(b.distance)
            if (d != 0) return@Comparator d
            val scoreCmp = b.score.compareTo(a.score)
            if (scoreCmp != 0) return@Comparator scoreCmp
            a.candidate.length.compareTo(b.candidate.length)
        }

        fun consider(term: String, distance: Int, frequency: Int, isForcedPrefix: Boolean = false) {
            // For very short inputs, avoid suggesting single-char tokens unless exact
            if (inputLen <= 2 && term.length == 1 && term != normalizedWord) return
            if (inputLen <= 2 && distance > 1) return

            // Apply keyboard proximity filtering when enabled
            if (useKeyboardProximity && distance > 0) {
                val editType = getEditType(normalizedWord, term)
                // Filter out distant substitutions (unlikely typos)
                if (editType == EditType.SUBSTITUTE && !isNearbySubstitution(normalizedWord, term)) {
                    return
                }
            }

            val entry = repository.bestEntryForNormalized(term) ?: DictionaryEntry(term, frequency, SuggestionSource.MAIN)
            val isPrefix = entry.word.startsWith(currentWord, ignoreCase = true)
            val distanceScore = 1.0 / (1 + distance)
            val isCompletion = isPrefix && entry.word.length > currentWord.length
            val prefixBonus = when {
                isForcedPrefix -> 1.5
                isCompletion -> 1.2
                isPrefix -> 0.8
                else -> 0.0
            }
            val frequencyScore = (entry.frequency / 2_000.0)
            val sourceBoost = if (entry.source == SuggestionSource.USER) 5.0 else 1.0

            // Apply edit type ranking when enabled
            var editTypeBonus = 0.0
            if (useEditTypeRanking && distance > 0) {
                val editType = getEditType(normalizedWord, term)
                editTypeBonus = when (editType) {
                    EditType.INSERT -> 0.5  // User missed a character - higher boost
                    EditType.SUBSTITUTE -> {
                        // Adjacent key substitutions get higher boost
                        if (useKeyboardProximity && isAdjacentSubstitution(normalizedWord, term)) {
                            0.4
                        } else {
                            0.2
                        }
                    }
                    EditType.DELETE -> {
                        // Only boost deletes if input has duplicate letters
                        if (hasAdjacentDuplicates(normalizedWord) && fixesDuplicateLetter(normalizedWord, term)) {
                            0.3
                        } else if (hasAdjacentDuplicates(normalizedWord)) {
                            0.1
                        } else {
                            0.0  // Don't suggest deletes for non-duplicate inputs
                        }
                    }
                    EditType.OTHER -> 0.0
                }
            }

            val score = (distanceScore + frequencyScore + prefixBonus + editTypeBonus) * sourceBoost
            val key = entry.word.lowercase(locale)
            if (!seen.add(key)) return
            val suggestion = SuggestionResult(
                candidate = entry.word,
                distance = distance,
                score = score,
                source = entry.source
            )

            if (top.size < limit) {
                top.add(suggestion)
                top.sortWith(comparator)
            } else if (comparator.compare(suggestion, top.last()) < 0) {
                top.add(suggestion)
                top.sortWith(comparator)
                while (top.size > limit) top.removeAt(top.lastIndex)
            }
        }

        // Consider completions first to surface them even if SymSpell returns other close words
        for (entry in completions) {
            val norm = normalizeCached(entry.word)
            consider(norm, 0, entry.frequency, isForcedPrefix = true)
        }

        for (item in allSymResults) {
            consider(item.term, item.distance, item.frequency)
        }

        return top
    }

    private fun boundedLevenshtein(a: String, b: String, maxDistance: Int): Int {
        // Optimal String Alignment distance (Damerau-Levenshtein with adjacent transpositions cost=1)
        if (kotlin.math.abs(a.length - b.length) > maxDistance) return -1
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            var minRow = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(
                    prev[j] + 1,      // deletion
                    curr[j - 1] + 1,  // insertion
                    prev[j - 1] + cost // substitution
                )

                if (i > 1 && j > 1 &&
                    a[i - 1] == b[j - 2] &&
                    a[i - 2] == b[j - 1]
                ) {
                    // adjacent transposition
                    value = min(value, prev[j - 2] + 1)
                }

                curr[j] = value
                minRow = min(minRow, value)
            }

            if (minRow > maxDistance) return -1
            // swap arrays
            for (k in 0..b.length) {
                val tmp = prev[k]
                prev[k] = curr[k]
                curr[k] = tmp
            }
        }
        return if (prev[b.length] <= maxDistance) prev[b.length] else -1
    }

    private fun normalize(word: String): String {
        return stripAccents(word.lowercase(locale))
            .replace(normalizeRegex, "")
    }

    private fun normalizeCached(word: String): String {
        return wordNormalizeCache.getOrPut(word) { normalize(word) }
    }

    private fun stripAccents(input: String): String {
        return accentCache.getOrPut(input) {
            Normalizer.normalize(input, Normalizer.Form.NFD)
                .replace("\\p{Mn}".toRegex(), "")
        }
    }
}
