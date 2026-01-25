package it.palsoftware.pastiera.core.suggestions

import java.text.Normalizer
import java.util.Locale
import kotlin.math.pow

class FakeDictionaryRepository : DictionaryRepository {
    override var isReady: Boolean = false
    override var isLoadStarted: Boolean = false
    
    private val entries = mutableListOf<DictionaryEntry>()
    private val baseLocale = Locale.ITALIAN

    fun addTestEntry(word: String, frequency: Int, source: SuggestionSource = SuggestionSource.MAIN) {
        entries.add(DictionaryEntry(word, frequency, source))
    }

    override suspend fun loadIfNeeded() {
        isLoadStarted = true
        isReady = true
    }

    override suspend fun refreshUserEntries() {
        // No-op for fake
    }

    override fun addUserEntryQuick(word: String) {
        addTestEntry(word, 1, SuggestionSource.USER)
    }

    override fun removeUserEntry(word: String) {
        entries.removeAll { it.word.equals(word, ignoreCase = true) && it.source == SuggestionSource.USER }
    }

    override fun markUsed(word: String) {
        // No-op for fake
    }

    override fun effectiveFrequency(entry: DictionaryEntry): Int {
        val raw = entry.frequency.coerceAtLeast(0).coerceAtMost(255)
        val normalized = raw / 255.0
        val scaled = (normalized.pow(0.75) * 1600.0).toInt()
        return scaled.coerceAtLeast(1)
    }

    override fun getExactWordFrequency(word: String): Int {
        return entries
            .filter { it.word.equals(word, ignoreCase = true) }
            .maxOfOrNull { it.frequency } ?: 0
    }

    override fun lookupByPrefixMerged(prefix: String, maxSize: Int): List<DictionaryEntry> {
        val normalizedPrefix = normalize(prefix)
        return entries
            .filter { normalize(it.word).startsWith(normalizedPrefix) }
            .sortedByDescending { effectiveFrequency(it) }
            .take(maxSize)
    }

    override fun symSpellLookup(term: String, maxSuggestions: Int): List<SymSpell.SuggestItem> {
        val normalizedTerm = normalize(term)
        return entries
            .map { entry ->
                val dist = levenshtein(normalizedTerm, normalize(entry.word))
                SymSpell.SuggestItem(normalize(entry.word), dist, effectiveFrequency(entry))
            }
            .filter { it.distance <= 2 }
            .sortedWith(compareBy({ it.distance }, { -it.frequency }))
            .distinctBy { it.term }
            .take(maxSuggestions)
    }

    override fun bestEntryForNormalized(normalized: String): DictionaryEntry? {
        return entries
            .filter { normalize(it.word) == normalized }
            .maxByOrNull { effectiveFrequency(it) }
    }

    override fun topByNormalized(normalized: String, limit: Int): List<DictionaryEntry> {
        return entries
            .filter { normalize(it.word) == normalized }
            .sortedByDescending { effectiveFrequency(it) }
            .take(limit)
    }

    override fun isKnownWord(word: String): Boolean {
        val normalized = normalize(word)
        return entries.any { normalize(it.word) == normalized }
    }

    override fun ensureLoadScheduled(background: () -> Unit) {
        if (!isLoadStarted) {
            background()
        }
    }

    private fun normalize(word: String): String {
        val normalized = Normalizer.normalize(word.lowercase(baseLocale), Normalizer.Form.NFD)
        val withoutAccents = normalized.replace("\\p{Mn}".toRegex(), "")
        return withoutAccents.replace("[^\\p{L}]".toRegex(), "")
    }

    private fun levenshtein(s: String, t: String): Int {
        if (s == t) return 0
        if (s.isEmpty()) return t.length
        if (t.isEmpty()) return s.length

        val v0 = IntArray(t.length + 1) { it }
        val v1 = IntArray(t.length + 1)

        for (i in s.indices) {
            v1[0] = i + 1
            for (j in t.indices) {
                val cost = if (s[i] == t[j]) 0 else 1
                v1[j + 1] = minOf(v1[j] + 1, v0[j + 1] + 1, v0[j] + cost)
            }
            for (j in v0.indices) v0[j] = v1[j]
        }
        return v0[t.length]
    }
}

