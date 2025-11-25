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

    private val normalizeRegex = "[^a-z]".toRegex()
    private val accentCache: MutableMap<String, String> = mutableMapOf()
    private val tag = "SuggestionEngine"
    private val wordNormalizeCache: MutableMap<String, String> = mutableMapOf()
    private var lastNormalized: String = ""
    private var lastBucket: List<DictionaryEntry> = emptyList()

    fun suggest(
        currentWord: String,
        limit: Int = 3,
        includeAccentMatching: Boolean = true
    ): List<SuggestionResult> {
        if (currentWord.isBlank()) return emptyList()
        if (!repository.isReady) return emptyList()
        val normalizedWord = normalize(currentWord)
        // Require at least 2 characters to avoid heavy buckets on large dictionaries.
        if (normalizedWord.length < 2) return emptyList()

        val prefixKey = normalizedWord.take(4)

        val candidates: List<DictionaryEntry> = when {
            lastNormalized.isNotEmpty() &&
                normalizedWord.startsWith(lastNormalized) &&
                normalizedWord.length == lastNormalized.length + 1 -> {
                // Extend typed prefix by 1: filter previous bucket.
                lastBucket.filter { normalizeCached(it.word).startsWith(normalizedWord) }
            }
            lastNormalized.length > normalizedWord.length &&
                lastNormalized.startsWith(normalizedWord) -> {
                // Backspace/shorter prefix: reload bucket for new prefix.
                repository.lookupByPrefix(normalizedWord)
            }
            else -> {
                // Fresh lookup for the current prefix.
                repository.lookupByPrefix(normalizedWord)
            }
        }

        if (debugLogging) {
            Log.d(tag, "suggest '$currentWord' normalized='$normalizedWord' candidates=${candidates.size}")
        }

        lastNormalized = normalizedWord
        lastBucket = candidates

        if (debugLogging) {
            Log.d(tag, "suggest '$currentWord' normalized='$normalizedWord' candidates=${candidates.size}")
        }

        val scored = mutableListOf<SuggestionResult>()
        for (entry in candidates) {
            val normalizedCandidate = normalizeCached(entry.word)
            val distance = if (normalizedCandidate.startsWith(normalizedWord)) {
                0 // treat prefix match as perfect to surface completions early
            } else {
                boundedLevenshtein(normalizedWord, normalizedCandidate, 2)
            }
            if (distance < 0) continue

            val accentDistance = if (includeAccentMatching) {
                val normalizedNoAccent = stripAccents(normalizedCandidate)
                if (normalizedNoAccent.startsWith(normalizedWord)) {
                    0
                } else {
                    boundedLevenshtein(normalizedWord, normalizedNoAccent, 2)
                }
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
