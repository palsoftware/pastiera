package it.palsoftware.pastiera.core.suggestions

import kotlin.math.min
import java.text.Normalizer
import java.util.Locale
import android.util.Log

data class SuggestionResult(
    val candidate: String,
    val distance: Int,
    val score: Double,
    val source: SuggestionSource,
    val kind: SuggestionKind = SuggestionKind.CURRENT_WORD
)

enum class SuggestionKind {
    CURRENT_WORD,
    NEXT_WORD,
    STARTER_WORD
}

class SuggestionEngine(
    private val repository: DictionaryRepository,
    private val locale: Locale = Locale.ITALIAN,
    private val debugLogging: Boolean = false
) {

    private val accentCache: MutableMap<String, String> = mutableMapOf()
    private val tag = "SuggestionEngine"
    private val wordNormalizeCache: MutableMap<String, String> = mutableMapOf()

    // Keyboard layout positions - built dynamically based on layout type
    private var keyboardPositions: Map<Char, Pair<Int, Int>> = buildKeyboardPositions("qwerty")

    private fun isPreferredUserEntry(source: SuggestionSource): Boolean {
        return source == SuggestionSource.USER || source == SuggestionSource.DEFAULT_USER
    }

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

    private data class ApostropheSplit(val prefix: String, val root: String)

    private fun normalizeApostrophes(input: String): String {
        return WordNormalization.normalizeApostrophes(input)
    }

    private fun recomposeApostropheCandidate(
        split: ApostropheSplit,
        candidate: String
    ): String? {
        val prefix = split.prefix
        val normalizedCandidate = normalizeApostrophes(candidate)
        val hasApostrophe = normalizedCandidate.contains('\'')
        val matchesPrefix = normalizedCandidate.length >= prefix.length &&
            normalizedCandidate.substring(0, prefix.length).equals(prefix, ignoreCase = true)

        val rootPart = when {
            matchesPrefix -> candidate.substring(prefix.length)
            hasApostrophe -> return null // don't mix different apostrophe prefixes
            else -> candidate
        }
        val recasedRoot = CasingHelper.applyCasing(rootPart, split.root, forceLeadingCapital = false)
        return prefix + recasedRoot
    }

    /**
     * Split a word with a single apostrophe into prefix (with apostrophe) and root.
     * Language-agnostic: only checks structure/length, not locale lists.
     */
    private fun splitApostropheWord(word: String): ApostropheSplit? {
        val normalized = normalizeApostrophes(word)
        val apostropheCount = normalized.count { it == '\'' }
        if (apostropheCount != 1) return null
        val idx = normalized.indexOf('\'')
        if (idx <= 0 || idx >= normalized.lastIndex) return null

        val prefix = normalized.substring(0, idx + 1)
        val root = normalized.substring(idx + 1)
        val prefixRaw = prefix.dropLast(1) // remove apostrophe

        val isPrefixOk = prefixRaw.isNotEmpty() &&
                isSupportedApostrophePrefix(prefixRaw) &&
                prefixRaw.all { it.isLetter() }
        val isRootOk = root.length >= 3 && root.all { it.isLetter() }
        return if (isPrefixOk && isRootOk) ApostropheSplit(prefix, root) else null
    }

    private fun isSupportedApostrophePrefix(prefix: String): Boolean {
        if (prefix.length <= 3) return true
        return prefix.lowercase(Locale.ROOT) in setOf(
            "dall",
            "dell",
            "nell",
            "sull",
            "coll",
            "quell",
            "quest"
        )
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

        // Apostrophe branch: split and suggest on the root to avoid over-corrections.
        val apostropheSplit = splitApostropheWord(currentWord)
        if (apostropheSplit != null) {
            val rootResults = suggestInternal(
                currentWord = apostropheSplit.root,
                limit = (limit * 2).coerceAtMost(12),
                includeAccentMatching = includeAccentMatching,
                useKeyboardProximity = useKeyboardProximity,
                useEditTypeRanking = useEditTypeRanking
            )
            val filtered = rootResults
                .filter { it.distance <= 1 } // stay conservative on apostrophated forms
                .take(limit * 2)
            val recomposed = filtered.mapNotNull { res ->
                val candidate = recomposeApostropheCandidate(apostropheSplit, res.candidate) ?: return@mapNotNull null
                res.copy(candidate = candidate)
            }.take(limit)
            if (recomposed.isNotEmpty()) {
                return recomposed
            }
        }

        return suggestInternal(
            currentWord = currentWord,
            limit = limit,
            includeAccentMatching = includeAccentMatching,
            useKeyboardProximity = useKeyboardProximity,
            useEditTypeRanking = useEditTypeRanking
        )
    }

    private fun suggestInternal(
        currentWord: String,
        limit: Int,
        includeAccentMatching: Boolean,
        useKeyboardProximity: Boolean,
        useEditTypeRanking: Boolean
    ): List<SuggestionResult> {
        val normalizedWord = normalize(currentWord)
        val normalizedWordBare = normalizedWord.replace("'", "")
        val inputLen = normalizedWord.length
        // Require at least 1 character to start suggesting.
        if (inputLen < 1) return emptyList()
        // Force prefix completions: take frequent words that start with the input (distance 0)
        // Filter out very rare words to avoid suggesting obscure completions
        // Never suggest the exact word the user has already typed
        val minFrequencyForPrefixSuggestion = if (inputLen <= 2) {
            300 // Very high threshold for short inputs
        } else if (inputLen == 3) {
            250 // High threshold for 3-char inputs
        } else if (inputLen == 4) {
            200 // High threshold for 4-char inputs
        } else {
            150 // Medium threshold for longer inputs
        }
        val completions = repository.lookupByPrefixMerged(normalizedWord, maxSize = 200)
            .filter {
                val norm = normalizeCached(it.word)
                val meetsFrequency = isPreferredUserEntry(it.source) || repository.effectiveFrequency(it) >= minFrequencyForPrefixSuggestion
                // Only show words that are longer (actual completions) and meet frequency threshold
                // Exception: USER dictionary words are always included regardless of frequency
                norm.startsWith(normalizedWord) && it.word.length > currentWord.length && meetsFrequency
            }

        // SymSpell lookup on normalized input (skip for single-char to avoid noise)
        // Reduce SymSpell suggestions to prioritize prefix matches
        val symResultsPrimary = if (inputLen == 1) {
            emptyList()
        } else if (inputLen <= 3) {
            // For short inputs, heavily prioritize prefix matches over edit distance
            repository.symSpellLookup(normalizedWord, maxSuggestions = limit * 2)
        } else {
            repository.symSpellLookup(normalizedWord, maxSuggestions = limit * 4)
        }
        val symResultsAccent = if (includeAccentMatching && inputLen > 1) {
            val normalizedAccentless = stripAccents(normalizedWord)
            if (normalizedAccentless != normalizedWord) {
                repository.symSpellLookup(normalizedAccentless, maxSuggestions = limit * 2)
            } else emptyList()
        } else emptyList()

        val allSymResults = (symResultsPrimary + symResultsAccent)
        val elisionPrefixEntries = if (inputLen == 1) {
            repository.lookupByPrefixMerged("${normalizedWord}'", maxSize = 80)
        } else emptyList()
        val leadingChar = currentWord.firstOrNull()
        val shortElisionEntries = if (inputLen == 1 && leadingChar != null) {
            elisionPrefixEntries.filter { entry ->
                val word = entry.word
                word.length in 2..3 &&
                    word.getOrNull(0)?.equals(leadingChar, ignoreCase = true) == true &&
                    word.getOrNull(1) == '\''
            }
        } else emptyList()
        val seen = HashSet<String>(limit * 3)
        val top = ArrayList<SuggestionResult>(limit)

        // Comparator with three-tier priority: USER words > prefix completions > edit-distance
        val comparator = Comparator<SuggestionResult> { a, b ->
            val aIsUser = isPreferredUserEntry(a.source)
            val bIsUser = isPreferredUserEntry(b.source)

            // User dictionary words ALWAYS rank highest
            if (aIsUser && !bIsUser) return@Comparator -1
            if (!aIsUser && bIsUser) return@Comparator 1

            // Use normalized versions to check prefix matches (allows accented variants)
            val aNormCandidate = normalizeCached(a.candidate)
            val bNormCandidate = normalizeCached(b.candidate)
            // For single-char input: prefix includes accented variants (same normalized form, different original)
            // For longer inputs: prefix means actual completions (longer words)
            val aIsPrefix = if (inputLen == 1) {
                aNormCandidate == normalizedWord && a.candidate != currentWord
            } else {
                aNormCandidate.startsWith(normalizedWord) && a.candidate.length > currentWord.length
            }
            val bIsPrefix = if (inputLen == 1) {
                bNormCandidate == normalizedWord && b.candidate != currentWord
            } else {
                bNormCandidate.startsWith(normalizedWord) && b.candidate.length > currentWord.length
            }

            // Prefix completions rank higher than edit-distance suggestions
            if (aIsPrefix && !bIsPrefix) return@Comparator -1
            if (!aIsPrefix && bIsPrefix) return@Comparator 1

            // Same tier - use normal ranking (distance, score, length)
            val d = a.distance.compareTo(b.distance)
            if (d != 0) return@Comparator d
            val scoreCmp = b.score.compareTo(a.score)
            if (scoreCmp != 0) return@Comparator scoreCmp
            a.candidate.length.compareTo(b.candidate.length)
        }

        fun consider(
            term: String,
            distance: Int,
            frequency: Int,
            isForcedPrefix: Boolean = false,
            overrideCandidates: List<DictionaryEntry>? = null
        ) {
            // For very short inputs, avoid suggesting single-char tokens unless exact
            if (inputLen <= 2 && term.length == 1 && term != normalizedWord) return
            if (inputLen <= 2 && distance > 1) return

            // Filter out rare words for prefix suggestions (completions)
            // Exception: Don't filter when overrideCandidates is provided (e.g., user dictionary words)
            val isPrefix = term.startsWith(normalizedWord) && term.length > normalizedWord.length
            val minFrequency = if (inputLen <= 2) {
                150 // Threshold for short inputs
            } else if (inputLen == 3) {
                100 // Threshold for 3-char inputs
            } else if (inputLen == 4) {
                80 // Threshold for 4-char inputs
            } else {
                60 // Threshold for longer inputs
            }
            if (isPrefix && frequency < minFrequency && overrideCandidates == null) {
                return // Skip rare prefix completions
            }

            // Apply keyboard proximity filtering when enabled
            if (useKeyboardProximity && distance > 0) {
                val editType = getEditType(normalizedWord, term)
                // Filter out distant substitutions (unlikely typos)
                if (editType == EditType.SUBSTITUTE && !isNearbySubstitution(normalizedWord, term)) {
                    return
                }
            }

            val isSingleCharInput = inputLen == 1
            val candidateList: List<DictionaryEntry> = overrideCandidates ?: when {
                isSingleCharInput -> repository.topByNormalized(term, limit = 5)
                distance == 0 -> repository.topByNormalized(term, limit = 3)
                else -> listOfNotNull(repository.bestEntryForNormalized(term))
            }
            if (candidateList.isEmpty()) return

            candidateList.forEach { entry ->
                val candidateLen = entry.word.length
                val normCandidate = normalizeCached(entry.word)

                // Never suggest the exact same word (exact match, case-sensitive)
                // This allows suggesting accented variants (e.g., "perche" → "perché")
                // and capitalization variants (e.g., "mario" → "Mario")
                if (entry.word == currentWord) {
                    return@forEach
                }

                val effectiveFreq = repository.effectiveFrequency(entry)

                // Filter prefix completions by their ACTUAL frequency, not SymSpell boosted frequency
                // Exception: Never filter user dictionary words
                val isActualPrefix = normCandidate.startsWith(normalizedWord) && entry.word.length > currentWord.length
                if (isActualPrefix && !isPreferredUserEntry(entry.source)) {
                    val minFreqForCandidate = if (inputLen <= 2) {
                        150
                    } else if (inputLen == 3) {
                        100
                    } else if (inputLen == 4) {
                        80
                    } else {
                        60
                    }
                    if (entry.frequency < minFreqForCandidate) {
                        return@forEach
                    }
                }

                val hasAccent = stripAccents(entry.word) != entry.word
                val hasDigit = entry.word.any { it.isDigit() }
                val hasSymbol = entry.word.any { !it.isLetterOrDigit() && it != '\'' }
                val isSameBaseLetter = entry.word.equals(currentWord, ignoreCase = true)
                val isShortElision = candidateLen in 2..3 &&
                        entry.word.length >= 2 &&
                        entry.word[0].equals(currentWord.firstOrNull() ?: ' ', ignoreCase = true) &&
                        entry.word.getOrNull(1) == '\''
                val isPrefix = normCandidate.startsWith(normalizedWord)
                val isActualCompletion = isPrefix && entry.word.length > currentWord.length

                // Filter out capitalized words for prefix completions when input is lowercase
                // (likely proper nouns like "Hardy" when typing "hard")
                // Exception: Never filter user dictionary words
                val inputIsLowercase = currentWord.firstOrNull()?.isLowerCase() == true
                val candidateIsCapitalized = entry.word.firstOrNull()?.isUpperCase() == true
                if (isActualCompletion && inputIsLowercase && candidateIsCapitalized && !isPreferredUserEntry(entry.source)) {
                    return@forEach // Skip capitalized prefix completions when user typed lowercase
                }

                val bareCandidate = normCandidate.replace("'", "")
                val distanceScore = 1.0 / (1 + distance)
                val isCompletion = isActualCompletion
                val prefixBonus = when {
                    // Avoid boosting completions when input is a single character
                    inputLen == 1 && isForcedPrefix -> 0.0
                    inputLen <= 2 && isForcedPrefix -> 2.0
                    inputLen <= 2 && isCompletion -> 1.8
                    inputLen <= 2 && isPrefix -> 1.5
                    isForcedPrefix -> 5.0  // Strongly boost prefix matches
                    isCompletion -> 4.0    // Strongly boost completions
                    isPrefix -> 3.0        // Boost any prefix match
                    else -> 0.0
                }
                val frequencyScore = (effectiveFreq / 1_600.0)
                val sourceBoost = if (isPreferredUserEntry(entry.source)) 5.0 else 1.0
                val accentBonus = if (isSingleCharInput && candidateLen == 1 && hasAccent) 0.8 else 0.0
                val accentSameLengthBonus = if (!isSingleCharInput && candidateLen == currentWord.length && hasAccent) 0.4 else 0.0
                val baseLetterMalus = if (isSingleCharInput && candidateLen == 1 && !hasAccent && isSameBaseLetter) -2.0 else 0.0
                val elisionBonus = when {
                    // Strongly boost single-letter inputs that can expand to "<letter>'"
                    isSingleCharInput && isShortElision && candidateLen == 2 -> 1.00
                    isSingleCharInput && isShortElision -> 0.55
                    else -> 0.0
                }
                val lengthPenalty = if (isSingleCharInput && candidateLen > 2) -0.2 * (candidateLen - 2) else 0.0
                // Small preference for lengths close to the current word; penalize large gaps.
                val lenDiff = kotlin.math.abs(candidateLen - currentWord.length)
                val lengthSimilarityBonus = when {
                    lenDiff == 0 -> 0.35
                    lenDiff == 1 -> 0.2
                    lenDiff == 2 -> 0.05
                    else -> -0.15 * kotlin.math.min(lenDiff, 4)
                }
                // Strong malus for numeric/symbolic candidates, especially when already correcting a typo.
                val containsSpecialChars = hasDigit || hasSymbol
                val numericMalus = when {
                    containsSpecialChars && distance > 0 && inputLen <= 2 -> -4.0
                    containsSpecialChars && distance > 0 -> -2.2
                    hasDigit && inputLen <= 2 -> -3.0
                    hasDigit -> -1.5
                    hasSymbol && inputLen <= 2 -> -1.2
                    hasSymbol -> -0.6
                    else -> 0.0
                }
                val completionLengthPenalty = if (isCompletion && currentWord.length >= 4 && (candidateLen - currentWord.length) >= 3) -0.35 else 0.0
                val sameRootBonus = if (distance == 1 && bareCandidate == normalizedWordBare) 0.25 else 0.0

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

                val score = (
                    distanceScore +
                        frequencyScore +
                        prefixBonus +
                        editTypeBonus +
                        accentBonus +
                        accentSameLengthBonus +
                        baseLetterMalus +
                        elisionBonus +
                        lengthPenalty +
                        lengthSimilarityBonus +
                        numericMalus +
                        completionLengthPenalty +
                        sameRootBonus
                    ) * sourceBoost
                val key = entry.word.lowercase(locale)
                if (!seen.add(key)) return@forEach
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
        }

        // For single-character input, explicitly surface normalized variants (accented and base).
        if (inputLen == 1) {
            consider(
                term = normalizedWord,
                distance = 0,
                frequency = repository.getExactWordFrequency(currentWord),
                isForcedPrefix = false
            )
        }

        // Consider completions first to surface them even if SymSpell returns other close words
        for (entry in completions) {
            val norm = normalizeCached(entry.word)
            consider(norm, 0, entry.frequency, isForcedPrefix = true, overrideCandidates = listOf(entry))
        }

        // Ensure short elisions like "l'" are considered even if absent from the dictionary
        for (entry in shortElisionEntries) {
            val norm = normalizeCached(entry.word)
            consider(
                term = norm,
                distance = 0,
                frequency = entry.frequency,
                isForcedPrefix = true
            )
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
        return WordNormalization.normalizeForSuggestion(word, locale)
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
