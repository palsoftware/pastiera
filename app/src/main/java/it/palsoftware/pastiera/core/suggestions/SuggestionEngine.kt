package it.palsoftware.pastiera.core.suggestions

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

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

    private val wordNormalizeCache: MutableMap<String, String> = mutableMapOf()
    private var keyboardPositions: Map<Char, Pair<Int, Int>> = buildKeyboardPositions("qwerty")

    private fun isPreferredUserEntry(source: SuggestionSource): Boolean {
        return source == SuggestionSource.USER || source == SuggestionSource.DEFAULT_USER
    }

    private fun normalize(input: String): String {
        return WordNormalization.normalizeApostrophes(input.lowercase(locale).trim())
    }

    private fun normalizeCached(word: String): String {
        return wordNormalizeCache.getOrPut(word) {
            normalize(word)
        }
    }

    private fun stripAccents(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    private fun buildKeyboardPositions(layout: String): Map<Char, Pair<Int, Int>> {
        val physicalPositions = mapOf(
            "KEYCODE_Q" to (0 to 0), "KEYCODE_W" to (0 to 1), "KEYCODE_E" to (0 to 2),
            "KEYCODE_R" to (0 to 3), "KEYCODE_T" to (0 to 4), "KEYCODE_Y" to (0 to 5),
            "KEYCODE_U" to (0 to 6), "KEYCODE_I" to (0 to 7), "KEYCODE_O" to (0 to 8),
            "KEYCODE_P" to (0 to 9),
            "KEYCODE_A" to (1 to 0), "KEYCODE_S" to (1 to 1), "KEYCODE_D" to (1 to 2),
            "KEYCODE_F" to (1 to 3), "KEYCODE_G" to (1 to 4), "KEYCODE_H" to (1 to 5),
            "KEYCODE_J" to (1 to 6), "KEYCODE_K" to (1 to 7), "KEYCODE_L" to (1 to 8),
            "KEYCODE_Z" to (2 to 0), "KEYCODE_X" to (2 to 1), "KEYCODE_C" to (2 to 2),
            "KEYCODE_V" to (2 to 3), "KEYCODE_B" to (2 to 6), "KEYCODE_N" to (2 to 7),
            "KEYCODE_M" to (2 to 8)
        )

        val layoutMappings = when (layout.lowercase(Locale.ROOT)) {
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
            else -> return buildKeyboardPositions("qwerty")
        }

        return layoutMappings.mapNotNull { (keycode, char) ->
            physicalPositions[keycode]?.let { position -> char to position }
        }.toMap()
    }

    fun setKeyboardLayout(layout: String) {
        keyboardPositions = buildKeyboardPositions(layout)
    }

    private enum class EditType { DELETE, SUBSTITUTE, INSERT, OTHER }

    private fun getEditType(input: String, suggestion: String): EditType {
        val inputLen = input.length
        val suggestionLen = suggestion.length
        return when {
            suggestionLen == inputLen - 1 -> EditType.DELETE
            suggestionLen == inputLen -> EditType.SUBSTITUTE
            suggestionLen == inputLen + 1 -> EditType.INSERT
            else -> EditType.OTHER
        }
    }

    private fun hasAdjacentDuplicates(word: String): Boolean {
        for (i in 0 until word.length - 1) {
            if (word[i] == word[i + 1]) return true
        }
        return false
    }

    private fun fixesDuplicateLetter(input: String, suggestion: String): Boolean {
        if (input.length != suggestion.length) return false
        for (i in 0 until input.length - 1) {
            if (input[i] == input[i + 1]) {
                if (i < suggestion.length - 1 && suggestion[i] != suggestion[i + 1]) {
                    return true
                }
            }
        }
        return false
    }

    private fun keyboardDistance(c1: Char, c2: Char): Double? {
        val pos1 = keyboardPositions[c1.lowercaseChar()] ?: return null
        val pos2 = keyboardPositions[c2.lowercaseChar()] ?: return null
        val rowDiff = (pos1.first - pos2.first).toDouble()
        val colDiff = (pos1.second - pos2.second).toDouble()
        return sqrt(rowDiff * rowDiff + colDiff * colDiff)
    }

    private fun isTransposition(input: String, suggestion: String): Boolean {
        if (input.length != suggestion.length) return false
        var diffCount = 0
        var firstDiffIndex = -1

        for (i in input.indices) {
            if (input[i].lowercaseChar() != suggestion[i].lowercaseChar()) {
                if (diffCount == 0) firstDiffIndex = i
                diffCount++
            }
        }
        if (diffCount != 2) return false
        val secondDiffIndex = firstDiffIndex + 1
        if (secondDiffIndex >= input.length) return false

        return input[firstDiffIndex].lowercaseChar() == suggestion[secondDiffIndex].lowercaseChar() &&
                input[secondDiffIndex].lowercaseChar() == suggestion[firstDiffIndex].lowercaseChar()
    }

    private fun isNearbySubstitution(input: String, suggestion: String): Boolean {
        if (input.length != suggestion.length) return true
        if (isTransposition(input, suggestion)) return true

        for (i in input.indices) {
            if (input[i].lowercaseChar() != suggestion[i].lowercaseChar()) {
                val dist = keyboardDistance(input[i], suggestion[i])
                if (dist != null && dist > 2.5) return false
            }
        }
        return true
    }

    private fun isAdjacentSubstitution(input: String, suggestion: String): Boolean {
        if (input.length != suggestion.length) return false
        if (isTransposition(input, suggestion)) return true

        for (i in input.indices) {
            if (input[i].lowercaseChar() != suggestion[i].lowercaseChar()) {
                val dist = keyboardDistance(input[i], suggestion[i])
                if (dist == null || dist > 1.15) return false
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
            hasApostrophe -> return null
            else -> candidate
        }
        val recasedRoot = CasingHelper.applyCasing(rootPart, split.root, forceLeadingCapital = false)
        return prefix + recasedRoot
    }

    private fun splitApostropheWord(word: String): ApostropheSplit? {
        val normalized = normalizeApostrophes(word)
        val apostropheCount = normalized.count { it == '\'' }
        if (apostropheCount != 1) return null
        val idx = normalized.indexOf('\'')
        if (idx <= 0 || idx >= normalized.lastIndex) return null

        val prefix = normalized.substring(0, idx + 1)
        val root = normalized.substring(idx + 1)
        val prefixRaw = prefix.dropLast(1)

        val isPrefixOk = prefixRaw.isNotEmpty() &&
                isSupportedApostrophePrefix(prefixRaw) &&
                prefixRaw.all { it.isLetter() }
        val isRootOk = root.length >= 3 && root.all { it.isLetter() }
        return if (isPrefixOk && isRootOk) ApostropheSplit(prefix, root) else null
    }

    private fun isSupportedApostrophePrefix(prefix: String): Boolean {
        if (prefix.length <= 3) return true
        return prefix.lowercase(Locale.ROOT) in setOf(
            "dall", "dell", "nell", "sull", "coll", "quell", "quest"
        )
    }

    fun suggest(
        currentWord: String,
        limit: Int = 3,
        includeAccentMatching: Boolean = true,
        useKeyboardProximity: Boolean = true,
        useEditTypeRanking: Boolean = true
    ): List<SuggestionResult> {
        if (currentWord.isBlank() || !repository.isReady) return emptyList()

        val apostropheSplit = splitApostropheWord(currentWord)
        if (apostropheSplit != null) {
            val rootResults = suggestInternal(
                currentWord = apostropheSplit.root,
                limit = (limit * 2).coerceAtMost(12),
                includeAccentMatching = includeAccentMatching,
                useKeyboardProximity = useKeyboardProximity,
                useEditTypeRanking = useEditTypeRanking
            )
            val filtered = rootResults.filter { it.distance <= 1 }.take(limit * 2)
            val recomposed = filtered.mapNotNull { res ->
                val candidate = recomposeApostropheCandidate(apostropheSplit, res.candidate) ?: return@mapNotNull null
                res.copy(candidate = candidate)
            }.take(limit)
            if (recomposed.isNotEmpty()) return recomposed
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
        if (inputLen < 1) return emptyList()

        val minFrequencyForPrefixSuggestion = when {
            inputLen <= 2 -> 300
            inputLen == 3 -> 250
            inputLen == 4 -> 200
            else -> 150
        }

        val completions = repository.lookupByPrefixMerged(normalizedWord, maxSize = 200)
            .filter {
                val norm = normalizeCached(it.word)
                val meetsFrequency = isPreferredUserEntry(it.source) ||
                        repository.effectiveFrequency(it) >= minFrequencyForPrefixSuggestion
                norm.startsWith(normalizedWord) && it.word.length > currentWord.length && meetsFrequency
            }

        val symResultsPrimary = when {
            inputLen == 1 -> emptyList()
            inputLen <= 3 -> repository.symSpellLookup(normalizedWord, maxSuggestions = limit * 2)
            else -> repository.symSpellLookup(normalizedWord, maxSuggestions = limit * 4)
        }

        val symResultsAccent = if (includeAccentMatching && inputLen > 1) {
            val normalizedAccentless = stripAccents(normalizedWord)
            if (normalizedAccentless != normalizedWord) {
                repository.symSpellLookup(normalizedAccentless, maxSuggestions = limit * 2)
            } else emptyList()
        } else emptyList()

        val allSymResults = symResultsPrimary + symResultsAccent
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

        val seen = HashSet<String>(limit * 4)
        val candidatesPool = ArrayList<SuggestionResult>()

        val comparator = Comparator<SuggestionResult> { a, b ->
            val aIsUser = isPreferredUserEntry(a.source)
            val bIsUser = isPreferredUserEntry(b.source)

            if (aIsUser && !bIsUser) return@Comparator -1
            if (!aIsUser && bIsUser) return@Comparator 1

            val aNormCandidate = normalizeCached(a.candidate)
            val bNormCandidate = normalizeCached(b.candidate)
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

            if (aIsPrefix && !bIsPrefix) return@Comparator -1
            if (!aIsPrefix && bIsPrefix) return@Comparator 1

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
            if (inputLen <= 2 && term.length == 1 && term != normalizedWord) return
            if (inputLen <= 2 && distance > 1) return

            val isPrefix = term.startsWith(normalizedWord) && term.length > normalizedWord.length
            val minFrequency = when {
                inputLen <= 2 -> 150
                inputLen == 3 -> 100
                inputLen == 4 -> 80
                else -> 60
            }
            if (isPrefix && frequency < minFrequency && overrideCandidates == null) return

            if (useKeyboardProximity && distance > 0) {
                val editType = getEditType(normalizedWord, term)
                if (editType == EditType.SUBSTITUTE && !isNearbySubstitution(normalizedWord, term)) return
            }

            val candidateList: List<DictionaryEntry> = overrideCandidates ?: when {
                inputLen == 1 -> repository.topByNormalized(term, limit = 5)
                distance == 0 -> repository.topByNormalized(term, limit = 3)
                else -> listOfNotNull(repository.bestEntryForNormalized(term))
            }
            if (candidateList.isEmpty()) return

            candidateList.forEach { entry ->
                if (entry.word == currentWord) return@forEach

                val candidateLen = entry.word.length
                val normCandidate = normalizeCached(entry.word)
                val effectiveFreq = repository.effectiveFrequency(entry)

                val isActualPrefix = normCandidate.startsWith(normalizedWord) && entry.word.length > currentWord.length
                if (isActualPrefix && !isPreferredUserEntry(entry.source)) {
                    val minFreqForCandidate = when {
                        inputLen <= 2 -> 150
                        inputLen == 3 -> 100
                        inputLen == 4 -> 80
                        else -> 60
                    }
                    if (entry.frequency < minFreqForCandidate) return@forEach
                }

                val hasAccent = stripAccents(entry.word) != entry.word
                val hasDigit = entry.word.any { it.isDigit() }
                val hasSymbol = entry.word.any { !it.isLetterOrDigit() && it != '\'' }
                val isSameBaseLetter = entry.word.equals(currentWord, ignoreCase = true)
                val isShortElision = candidateLen in 2..3 &&
                        entry.word.length >= 2 &&
                        entry.word[0].equals(currentWord.firstOrNull() ?: ' ', ignoreCase = true) &&
                        entry.word.getOrNull(1) == '\''

                val inputIsLowercase = currentWord.firstOrNull()?.isLowerCase() == true
                val candidateIsCapitalized = entry.word.firstOrNull()?.isUpperCase() == true
                if (isActualPrefix && inputIsLowercase && candidateIsCapitalized && !isPreferredUserEntry(entry.source)) {
                    return@forEach
                }

                val bareCandidate = normCandidate.replace("'", "")
                val distanceScore = 1.0 / (1 + distance)
                val prefixBonus = when {
                    inputLen == 1 && isForcedPrefix -> 0.0
                    inputLen <= 2 && isForcedPrefix -> 2.0
                    inputLen <= 2 && isActualPrefix -> 1.8
                    inputLen <= 2 && isPrefix -> 1.5
                    isForcedPrefix -> 5.0
                    isActualPrefix -> 4.0
                    isPrefix -> 3.0
                    else -> 0.0
                }
                val frequencyScore = (effectiveFreq / 1_600.0)
                val sourceBoost = if (isPreferredUserEntry(entry.source)) 5.0 else 1.0
                val accentBonus = if (inputLen == 1 && candidateLen == 1 && hasAccent) 0.8 else 0.0
                val accentSameLengthBonus = if (inputLen > 1 && candidateLen == currentWord.length && hasAccent) 0.4 else 0.0
                val baseLetterMalus = if (inputLen == 1 && candidateLen == 1 && !hasAccent && isSameBaseLetter) -2.0 else 0.0
                val elisionBonus = when {
                    inputLen == 1 && isShortElision && candidateLen == 2 -> 1.00
                    inputLen == 1 && isShortElision -> 0.55
                    else -> 0.0
                }
                val lengthPenalty = if (inputLen == 1 && candidateLen > 2) -0.2 * (candidateLen - 2) else 0.0
                val lenDiff = abs(candidateLen - currentWord.length)
                val lengthSimilarityBonus = when (lenDiff) {
                    0 -> 0.35
                    1 -> 0.2
                    2 -> 0.05
                    else -> -0.15 * min(lenDiff, 4)
                }

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
                val completionLengthPenalty = if (isActualPrefix && currentWord.length >= 4 && (candidateLen - currentWord.length) >= 3) -0.35 else 0.0
                val sameRootBonus = if (distance == 1 && bareCandidate == normalizedWordBare) 0.25 else 0.0

                var editTypeBonus = 0.0
                if (useEditTypeRanking && distance > 0) {
                    val editType = getEditType(normalizedWord, term)
                    editTypeBonus = when (editType) {
                        EditType.INSERT -> 0.5
                        EditType.SUBSTITUTE -> {
                            if (useKeyboardProximity && isAdjacentSubstitution(normalizedWord, term)) 0.4 else 0.2
                        }
                        EditType.DELETE -> {
                            if (hasAdjacentDuplicates(normalizedWord) && fixesDuplicateLetter(normalizedWord, term)) 0.3
                            else if (hasAdjacentDuplicates(normalizedWord)) 0.1
                            else 0.0
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
                if (seen.add(key)) {
                    candidatesPool.add(
                        SuggestionResult(
                            candidate = entry.word,
                            distance = distance,
                            score = score,
                            source = entry.source
                        )
                    )
                }
            }
        }

        if (inputLen == 1) {
            consider(
                term = normalizedWord,
                distance = 0,
                frequency = repository.getExactWordFrequency(currentWord),
                isForcedPrefix = false
            )
        }

        for (entry in completions) {
            val norm = normalizeCached(entry.word)
            consider(norm, 0, entry.frequency, isForcedPrefix = true, overrideCandidates = listOf(entry))
        }

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

        return candidatesPool
            .sortedWith(comparator)
            .take(limit)
    }

    private fun boundedLevenshtein(a: String, b: String, maxDistance: Int): Int {
        if (abs(a.length - b.length) > maxDistance) return -1

        val lenA = a.length
        val lenB = b.length

        var prev = IntArray(lenB + 1) { it }
        var curr = IntArray(lenB + 1)

        for (i in 1..lenA) {
            curr[0] = i
            var minDistanceInRow = curr[0]

            for (j in 1..lenB) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var minVal = min(curr[j - 1] + 1, prev[j] + 1)
                minVal = min(minVal, prev[j - 1] + cost)

                // Check for Damerau-Levenshtein transposition
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    minVal = min(minVal, prev[j - 2] + cost)
                }

                curr[j] = minVal
                if (minVal < minDistanceInRow) {
                    minDistanceInRow = minVal
                }
            }

            if (minDistanceInRow > maxDistance) return -1

            val temp = prev
            prev = curr
            curr = temp
        }

        val finalDist = prev[lenB]
        return if (finalDist <= maxDistance) finalDist else -1
    }
}
