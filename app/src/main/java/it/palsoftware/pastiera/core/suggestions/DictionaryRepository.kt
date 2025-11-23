package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.content.res.AssetManager
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale

/**
 * Loads and indexes lightweight dictionaries from assets and merges them with the user dictionary.
 */
class DictionaryRepository(
    private val context: Context,
    private val assets: AssetManager,
    private val userDictionaryStore: UserDictionaryStore,
    private val baseLocale: Locale = Locale.ITALIAN,
    private val cachePrefixLength: Int = 3
) {

    private val prefixCache: MutableMap<String, List<DictionaryEntry>> = mutableMapOf()
    private val normalizedIndex: MutableMap<String, MutableList<DictionaryEntry>> = mutableMapOf()
    private var loaded = false

    fun loadIfNeeded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val mainEntries = loadFromAssets("common/dictionaries/${baseLocale.language}_base.json")
            index(mainEntries + userDictionaryStore.loadUserEntries(context))
            loaded = true
        }
    }

    fun refreshUserEntries() {
        val userEntries = userDictionaryStore.loadUserEntries(context)
        index(userEntries, keepExisting = true)
    }

    fun addUserEntry(word: String) {
        userDictionaryStore.addWord(context, word)
        refreshUserEntries()
    }

    fun removeUserEntry(word: String) {
        userDictionaryStore.removeWord(context, word)
        refreshUserEntries()
    }

    fun markUsed(word: String) {
        userDictionaryStore.markUsed(context, word)
    }

    fun isKnownWord(word: String): Boolean {
        loadIfNeeded()
        val normalized = normalize(word)
        return normalizedIndex[normalized]?.isNotEmpty() == true
    }

    fun lookupByPrefix(prefix: String): List<DictionaryEntry> {
        loadIfNeeded()
        if (prefix.isBlank()) return emptyList()
        val normalizedPrefix = normalize(prefix).take(cachePrefixLength)
        return prefixCache[normalizedPrefix] ?: emptyList()
    }

    fun allCandidates(): List<DictionaryEntry> {
        loadIfNeeded()
        return normalizedIndex.values.flatten()
    }

    private fun loadFromAssets(path: String): List<DictionaryEntry> {
        return try {
            val jsonString = assets.open(path).bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            buildList {
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val word = obj.getString("w")
                    val freq = obj.optInt("f", 1)
                    add(DictionaryEntry(word, freq, SuggestionSource.MAIN))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun index(entries: List<DictionaryEntry>, keepExisting: Boolean = false) {
        if (!keepExisting) {
            prefixCache.clear()
            normalizedIndex.clear()
        }

        entries.forEach { entry ->
            val normalized = normalize(entry.word)
            val bucket = normalizedIndex.getOrPut(normalized) { mutableListOf() }
            bucket.removeAll { it.word.equals(entry.word, ignoreCase = true) && it.source == entry.source }
            bucket.add(entry)

            val prefix = normalized.take(cachePrefixLength)
            val prefixList = prefixCache.getOrPut(prefix) { mutableListOf() }
            prefixList.add(entry)
        }

        prefixCache.values.forEach { list -> list.sortByDescending { it.frequency } }
    }

    private fun normalize(word: String): String {
        val normalized = Normalizer.normalize(word.lowercase(baseLocale), Normalizer.Form.NFD)
        return normalized.replace("[^a-z]".toRegex(), "")
    }
}
