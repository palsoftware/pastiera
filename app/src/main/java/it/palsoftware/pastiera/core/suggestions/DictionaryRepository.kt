package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.content.res.AssetManager
import org.json.JSONArray
import android.util.Log
import android.os.Looper
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

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
    
    val isLoadStarted: Boolean
        get() = loadStarted
    private val tag = "DictionaryRepo"
    private val debugLogging: Boolean = debugLogging

    fun loadIfNeeded() {
        if (isReady) return
        // Must not run on main thread
        if (Looper.myLooper() == Looper.getMainLooper()) return
        synchronized(this) {
            if (isReady || loadStarted) return
            loadStarted = true
            
            val startTime = System.currentTimeMillis()
            
            // Try to load serialized format first (much faster)
            val serializedPath = "common/dictionaries_serialized/${baseLocale.language}_base.dict"
            val loadedSerialized = loadSerializedFromAssets(serializedPath)
            
            if (loadedSerialized) {
                // Serialized format already populated the indices
                val loadTime = System.currentTimeMillis() - startTime
                Log.i(tag, "✓ Loaded SERIALIZED dictionary (.dict) in ${loadTime}ms - normalizedIndex=${normalizedIndex.size} prefixCache=${prefixCache.size}")
            } else {
                // Fallback to JSON format
                val mainEntries = loadFromAssets("common/dictionaries/${baseLocale.language}_base.json")
                if (debugLogging) {
                    Log.d(tag, "Loaded JSON dictionary: ${mainEntries.size} entries")
                }
                if (mainEntries.isNotEmpty()) {
                    index(mainEntries)
                }
                val loadTime = System.currentTimeMillis() - startTime
                Log.i(tag, "✗ Loaded JSON dictionary (fallback) in ${loadTime}ms - normalizedIndex=${normalizedIndex.size} prefixCache=${prefixCache.size}")
            }
            
            // Always add user entries
            val userEntries = userDictionaryStore.loadUserEntries(context)
            if (userEntries.isNotEmpty()) {
                index(userEntries, keepExisting = true)
            }
            
            isReady = true
        }
    }

    fun ensureLoadScheduled(background: () -> Unit) {
        if (isReady || loadStarted) return
        background()
    }

    fun refreshUserEntries() {
        // Ensure dictionary base is loaded first (if not already)
        if (!isReady && !loadStarted) {
            loadIfNeeded()
        }
        // Refresh user entries - this works even if base dictionary is still loading
        // because index() with keepExisting=true will merge with existing entries
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

    /**
     * Gets the frequency of an exact word (case-insensitive match).
     * Returns the maximum frequency if multiple entries exist (e.g., different sources).
     * Returns 0 if the word doesn't exist.
     */
    fun getExactWordFrequency(word: String): Int {
        if (!isReady) return 0
        val normalized = normalize(word)
        val bucket = normalizedIndex[normalized] ?: return 0
        // Find exact match (case-insensitive) and return max frequency
        return bucket
            .filter { it.word.equals(word, ignoreCase = true) }
            .maxOfOrNull { it.frequency } ?: 0
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

    /**
     * Attempts to load dictionary from serialized format (.dict file).
     * Returns true if successful, false otherwise (fallback to JSON).
     */
    private fun loadSerializedFromAssets(path: String): Boolean {
        return try {
            val serializedString = assets.open(path).bufferedReader().use { it.readText() }
            val json = Json {
                ignoreUnknownKeys = true
            }
            val index = json.decodeFromString<DictionaryIndex>(serializedString)
            
            // Populate indices directly from serialized data
            index.normalizedIndex.forEach { (normalized, entries) ->
                normalizedIndex[normalized] = entries.map { it.toDictionaryEntry() }.toMutableList()
            }
            
            index.prefixCache.forEach { (prefix, entries) ->
                prefixCache[prefix] = entries.map { it.toDictionaryEntry() }.toMutableList()
            }
            
            true
        } catch (e: Exception) {
            if (debugLogging) {
                Log.d(tag, "Serialized dictionary not found or invalid: $path, falling back to JSON", e)
            }
            false
        }
    }

    /**
     * Loads dictionary from JSON format (fallback).
     */
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
        } catch (e: Exception) {
            if (debugLogging) Log.e(tag, "Failed to load dictionary from assets: $path", e)
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
        // Remove combining marks (accents) explicitly - same logic as SuggestionEngine
        val withoutAccents = normalized.replace("\\p{Mn}".toRegex(), "")
        // Keep only Unicode letters (supports Latin, Cyrillic, Greek, Arabic, Chinese, etc.)
        // Removes: punctuation, numbers, spaces, emoji, symbols
        return withoutAccents.replace("[^\\p{L}]".toRegex(), "")
    }
}
