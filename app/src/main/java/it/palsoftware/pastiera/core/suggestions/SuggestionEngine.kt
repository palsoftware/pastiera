package it.palsoftware.pastiera.core.suggestions

import kotlin.math.min
import java.text.Normalizer
import java.util.Locale

data class SuggestionResult(
    val candidate: String,
    val distance: Int,
    val score: Double,
    val source: SuggestionSource
)

class SuggestionEngine(
    private val repository: DictionaryRepository,
    private val locale: Locale = Locale.ITALIAN
) {

    private val normalizeRegex = "[^a-z]".toRegex()
    private val accentCache: MutableMap<String, String> = mutableMapOf()

    fun suggest(
        currentWord: String,
        limit: Int = 3,
        includeAccentMatching: Boolean = true
    ): List<SuggestionResult> {
        if (currentWord.isBlank()) return emptyList()
        val normalizedWord = normalize(currentWord)
        val candidates = repository.lookupByPrefix(normalizedWord)
            .ifEmpty { repository.allCandidates() }

        val scored = mutableListOf<SuggestionResult>()
        for (entry in candidates) {
            val normalizedCandidate = normalize(entry.word)
            val distance = boundedLevenshtein(normalizedWord, normalizedCandidate, 2)
            if (distance < 0) continue

            val accentDistance = if (includeAccentMatching) {
                boundedLevenshtein(normalizedWord, stripAccents(normalizedCandidate), 2)
            } else distance

            val effectiveDistance = min(distance, accentDistance)
            val distanceScore = 1.0 / (1 + effectiveDistance)
            val frequencyScore = entry.frequency / 10_000.0
            val sourceBoost = if (entry.source == SuggestionSource.USER) 2.0 else 1.0
            val score = (distanceScore + frequencyScore) * sourceBoost
            scored.add(
                SuggestionResult(
                    candidate = entry.word,
                    distance = effectiveDistance,
                    score = score,
                    source = entry.source
                )
            )
        }

        return scored
            .sortedWith(
                compareBy<SuggestionResult> { it.distance }
                    .thenByDescending { it.score }
                    .thenBy { it.candidate.length }
            )
            .take(limit)
    }

    private fun boundedLevenshtein(a: String, b: String, maxDistance: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > maxDistance) return -1
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            var minRow = dp[0]
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + cost
                )
                prev = temp
                minRow = min(minRow, dp[j])
            }
            if (minRow > maxDistance) return -1
        }
        return if (dp[b.length] <= maxDistance) dp[b.length] else -1
    }

    private fun normalize(word: String): String {
        return stripAccents(word.lowercase(locale))
            .replace(normalizeRegex, "")
    }

    private fun stripAccents(input: String): String {
        return accentCache.getOrPut(input) {
            Normalizer.normalize(input, Normalizer.Form.NFD)
                .replace("\\p{Mn}".toRegex(), "")
        }
    }
}
