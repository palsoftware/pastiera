package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.content.res.AssetManager
import org.json.JSONArray
import android.util.Log
import android.os.Looper
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
    private val cachePrefixLength: Int = 4,
    debugLogging: Boolean = false
) {

    private val prefixCache: MutableMap<String, MutableList<DictionaryEntry>> = mutableMapOf()
    private val normalizedIndex: MutableMap<String, MutableList<DictionaryEntry>> = mutableMapOf()
    @Volatile var isReady: Boolean = false
        private set
    @Volatile private var loadStarted: Boolean = false
    private val tag = "DictionaryRepo"
    private val debugLogging: Boolean = debugLogging

    fun loadIfNeeded() {
        if (isReady) return
        // Must not run on main thread
        if (Looper.myLooper() == Looper.getMainLooper()) return
        synchronized(this) {
            if (isReady || loadStarted) return
            loadStarted = true
            val mainEntries = loadFromAssets("common/dictionaries/${baseLocale.language}_base.json")
            val userEntries = userDictionaryStore.loadUserEntries(context)
            if (debugLogging) {
                Log.d(tag, "loadIfNeeded main=${mainEntries.size} user=${userEntries.size}")
            }
            index(mainEntries + userEntries)
            isReady = true
        }
    }

    fun ensureLoadScheduled(background: () -> Unit) {
        if (isReady || loadStarted) return
        background()
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
        if (!isReady) return false
        val normalized = normalize(word)
        return normalizedIndex[normalized]?.isNotEmpty() == true
    }

    fun lookupByPrefix(prefix: String): List<DictionaryEntry> {
        if (!isReady || prefix.isBlank()) return emptyList()
        val normalizedPrefix = normalize(prefix)
        val maxPrefixLength = normalizedPrefix.length.coerceAtMost(cachePrefixLength)

        for (length in maxPrefixLength downTo 1) {
            val bucket = prefixCache[normalizedPrefix.take(length)]
            if (!bucket.isNullOrEmpty()) {
                return bucket
            }
        }
        return emptyList()
    }

    fun allCandidates(): List<DictionaryEntry> {
        if (!isReady) return emptyList()
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
            if (debugLogging) Log.e(tag, "Failed to load dictionary from assets: $path")
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

            val maxPrefixLength = normalized.length.coerceAtMost(cachePrefixLength)
            for (length in 1..maxPrefixLength) {
                val prefix = normalized.take(length)
                val prefixList = prefixCache.getOrPut(prefix) { mutableListOf() }
                prefixList.add(entry)
            }
        }

        prefixCache.values.forEach { list -> list.sortByDescending { it.frequency } }
        if (debugLogging) {
            Log.d(tag, "index built: normalizedIndex=${normalizedIndex.size} prefixCache=${prefixCache.size}")
        }
    }

    private fun normalize(word: String): String {
        val normalized = Normalizer.normalize(word.lowercase(baseLocale), Normalizer.Form.NFD)
        return normalized.replace("[^a-z]".toRegex(), "")
    }
}
