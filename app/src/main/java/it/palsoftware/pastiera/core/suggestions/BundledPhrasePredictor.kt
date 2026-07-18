package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class BundledPhrasePredictor(
    context: Context,
    private val locale: Locale = Locale.getDefault(),
    private val isValidWord: ((String) -> Boolean)? = null
) {
    private val appContext = context.applicationContext

    companion object {
        private const val PREDICTION_ASSET_PREFIX = "common/predictions"
        private val normalizeRegex = Regex("[^\\p{L}']")
        private val cache = ConcurrentHashMap<String, PhraseData>()
        private val emptyPhraseData = PhraseData(emptyMap(), emptyMap(), emptyList())
    }

    fun predictContextual(previousWord: String, previousPreviousWord: String?, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        val data = loadData()
        if (data === emptyPhraseData) return emptyList()

        val previous = normalize(previousWord) ?: return emptyList()
        val previousPrevious = normalize(previousPreviousWord)
        val results = linkedSetOf<String>()

        if (previousPrevious != null) {
            data.trigrams["$previousPrevious $previous"]
                ?.asSequence()
                ?.filter(::isPlausibleWord)
                ?.forEach(results::add)
        }
        data.bigrams[previous]
            ?.asSequence()
            ?.filter(::isPlausibleWord)
            ?.forEach(results::add)

        return results.take(limit)
    }

    fun fallbackWords(limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        return loadData().fallback
            .asSequence()
            .filter(::isPlausibleWord)
            .take(limit)
            .toList()
    }

    private fun loadData(): PhraseData {
        val language = locale.language.lowercase(Locale.ROOT)
        if (language != "en") return emptyPhraseData
        return cache.getOrPut(language) {
            val path = "$PREDICTION_ASSET_PREFIX/${language}_next_words.json"
            runCatching {
                appContext.assets.open(path).bufferedReader().use { reader ->
                    val root = JSONObject(reader.readText())
                    PhraseData(
                        bigrams = parseMap(root.optJSONObject("bigrams")),
                        trigrams = parseMap(root.optJSONObject("trigrams")),
                        fallback = parseArray(root.optJSONArray("fallback"))
                    )
                }
            }.getOrElse {
                emptyPhraseData
            }
        }
    }

    private fun parseMap(source: JSONObject?): Map<String, List<String>> {
        if (source == null) return emptyMap()
        return source.keys().asSequence().associateWith { key ->
            parseArray(source.optJSONArray(key))
        }
    }

    private fun parseArray(source: JSONArray?): List<String> {
        if (source == null) return emptyList()
        return buildList {
            for (index in 0 until source.length()) {
                source.optString(index)
                    .takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }
    }

    private fun normalize(word: String?): String? {
        if (word.isNullOrBlank()) return null
        val normalized = word
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('ʼ', '\'')
            .lowercase(locale)
            .replace(normalizeRegex, "")
            .trim('\'')
        return normalized.takeIf { it.isNotBlank() }
    }

    private fun isPlausibleWord(word: String): Boolean {
        val normalized = normalize(word) ?: return false
        if (normalized.length < 2) return false
        return isValidWord?.invoke(normalized) ?: true
    }

    private data class PhraseData(
        val bigrams: Map<String, List<String>>,
        val trigrams: Map<String, List<String>>,
        val fallback: List<String>
    )
}
