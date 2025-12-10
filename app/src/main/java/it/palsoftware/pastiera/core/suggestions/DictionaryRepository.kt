package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.content.res.AssetManager
import org.json.JSONArray
import android.util.Log
import android.os.Looper
import java.util.concurrent.CancellationException
import kotlin.coroutines.coroutineContext
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

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

    companion object {
        // Avoid concurrent heavy loads across repositories to reduce memory spikes.
        private val loadMutex = Mutex()
    }

    private val prefixCache: MutableMap<String, MutableList<DictionaryEntry>> = mutableMapOf()
    private val normalizedIndex: MutableMap<String, MutableList<DictionaryEntry>> = mutableMapOf()
    @Volatile private var symSpell: SymSpell? = null
    @Volatile private var symSpellBuilt: Boolean = false
    @Volatile var isReady: Boolean = false
        private set
    @Volatile private var loadStarted: Boolean = false
    
    val isLoadStarted: Boolean
        get() = loadStarted
    private val tag = "DictionaryRepo"
    private val debugLogging: Boolean = debugLogging

    suspend fun loadIfNeeded() {
        if (isReady) return
        // Must not run on main thread
        if (Looper.myLooper() == Looper.getMainLooper()) return
        coroutineContext.ensureActive()
        loadMutex.withLock {
            if (isReady) return
            synchronized(this) {
                if (isReady || loadStarted) return
                loadStarted = true
            }
            try {
                val startTime = System.currentTimeMillis()
                
                // Try to load serialized format first (much faster)
                coroutineContext.ensureActive()
                val localFile = File(context.filesDir, "dictionaries_serialized/${baseLocale.language}_base.dict")
                val serializedPath = "common/dictionaries_serialized/${baseLocale.language}_base.dict"
                val loadedSerialized = if (localFile.exists()) {
                    loadSerializedFromFile(localFile)
                } else {
                    loadSerializedFromAssets(serializedPath)
                }
                
                if (loadedSerialized) {
                    // Serialized format already populated the indices
                    val loadTime = System.currentTimeMillis() - startTime
                    Log.i(tag, "Loaded SERIALIZED dictionary (.dict) in ${loadTime}ms - normalizedIndex=${normalizedIndex.size} prefixCache=${prefixCache.size}")
                } else {
                    // Fallback to JSON format
                    val mainEntries = loadFromAssets("common/dictionaries/${baseLocale.language}_base.json")
                    coroutineContext.ensureActive()
                    if (debugLogging) {
                        Log.d(tag, "Loaded JSON dictionary: ${mainEntries.size} entries")
                    }
                    if (mainEntries.isNotEmpty()) {
                        index(mainEntries)
                    }
                    val loadTime = System.currentTimeMillis() - startTime
                    Log.i(tag, "Loaded JSON dictionary (fallback) in ${loadTime}ms - normalizedIndex=${normalizedIndex.size} prefixCache=${prefixCache.size}")
                }
                
                coroutineContext.ensureActive()
                // Optional default user entries (editable copy stored in app files dir, fallback to asset)
                val defaultUserEntries = loadUserDefaults()
                if (defaultUserEntries.isNotEmpty()) {
                    index(defaultUserEntries, keepExisting = true)
                }

                // Always add user entries
                val userEntries = userDictionaryStore.loadUserEntries(context)
                if (userEntries.isNotEmpty()) {
                    index(userEntries, keepExisting = true)
                }

                coroutineContext.ensureActive()
                buildSymSpell()
                
                isReady = true
            } catch (ce: CancellationException) {
                synchronized(this) { loadStarted = false }
                throw ce
            } catch (oom: OutOfMemoryError) {
                Log.e(tag, "OutOfMemory while loading dictionary; clearing and falling back", oom)
                synchronized(this) { loadStarted = false }
                prefixCache.clear()
                normalizedIndex.clear()
                symSpell = null
                symSpellBuilt = false
                isReady = false
                // Rethrow to let caller handle failure
                throw oom
            }
        }
    }

    fun ensureLoadScheduled(background: () -> Unit) {
        if (isReady || loadStarted) return
        background()
    }

    suspend fun refreshUserEntries() {
        coroutineContext.ensureActive()
        // Ensure dictionary base is loaded first (if not already)
        if (!isReady && !loadStarted) {
            loadIfNeeded()
            if (!isReady) return
        }
        coroutineContext.ensureActive()
        // Refresh defaults and user entries - this works even if base dictionary is still loading
        // because index() with keepExisting=true will merge with existing entries
        val defaultUserEntries = loadUserDefaults()
        val userEntries = userDictionaryStore.loadUserEntries(context)
        // Remove stale USER entries no longer present, then re-add current defaults/users
        purgeUserEntries(userEntries)
        if (defaultUserEntries.isNotEmpty()) {
            index(defaultUserEntries, keepExisting = true)
        }
        index(userEntries, keepExisting = true)
        // Rebuild SymSpell to drop removed entries
        coroutineContext.ensureActive()
        symSpellBuilt = false
        buildSymSpell()
    }

    fun addUserEntry(word: String) {
        addUserEntryQuick(word)
    }

    /**
     * Lightweight add: updates persistent store, then merges a single USER entry
     * into the in-memory indices and SymSpell without rebuilding everything.
     */
    fun addUserEntryQuick(word: String) {
        userDictionaryStore.addWord(context, word)
        // Find latest frequency from snapshot; default to 1
        val freq = userDictionaryStore.getSnapshot()
            .firstOrNull { it.word.equals(word, ignoreCase = true) }
            ?.frequency ?: 1
        val entry = DictionaryEntry(word, freq, SuggestionSource.USER)
        index(listOf(entry), keepExisting = true)
        addToSymSpell(listOf(entry))
    }

    fun removeUserEntry(word: String) {
        userDictionaryStore.removeWord(context, word)
        // Caller should refresh asynchronously; keep legacy path noop here.
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

    /**
     * Returns a merged list of candidates from the most specific prefix bucket down to the
     * single-letter bucket, stopping when maxSize is reached. This helps capture common
     * transpositions (e.g., "teh" -> "the", "caio" -> "ciao") that would otherwise live
     * under a different prefix.
     */
    fun lookupByPrefixMerged(prefix: String, maxSize: Int): List<DictionaryEntry> {
        if (!isReady || prefix.isBlank()) return emptyList()
        val normalizedPrefix = normalize(prefix)
        val maxPrefixLength = normalizedPrefix.length.coerceAtMost(cachePrefixLength)
        val seen = LinkedHashMap<String, DictionaryEntry>()

        for (length in maxPrefixLength downTo 1) {
            val bucket = prefixCache[normalizedPrefix.take(length)] ?: continue
            for (entry in bucket) {
                val key = entry.word.lowercase(baseLocale)
                if (!seen.containsKey(key)) {
                    seen[key] = entry
                    if (seen.size >= maxSize) {
                        return seen.values.toList()
                    }
                }
            }
        }

        return seen.values.toList()
    }

    fun symSpellLookup(term: String, maxSuggestions: Int): List<SymSpell.SuggestItem> {
        val engine = symSpell ?: return emptyList()
        return engine.lookup(term, maxSuggestions)
    }

    fun bestEntryForNormalized(normalized: String): DictionaryEntry? {
        return normalizedIndex[normalized]?.maxByOrNull { it.frequency }
    }

    fun allCandidates(): List<DictionaryEntry> {
        if (!isReady) return emptyList()
        return normalizedIndex.values.flatten()
    }

    /**
     * Attempts to load dictionary from serialized format (.dict file).
     * Returns true if successful, false otherwise (fallback to JSON).
     */
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun loadSerializedFromAssets(path: String): Boolean {
        Log.i(tag, "Attempting to load serialized dictionary from assets: $path")
        return try {
            coroutineContext.ensureActive()
            assets.open(path).use { input ->
                decodeSerializedDictionary(input)
            }
            true
        } catch (e: java.io.FileNotFoundException) {
            Log.w(tag, "Serialized dictionary file not found: $path - ${e.message}")
            false
        } catch (e: SerializationException) {
            Log.e(tag, "Failed to deserialize dictionary from $path: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(tag, "Error loading serialized dictionary from $path: ${e.javaClass.simpleName} - ${e.message}", e)
            false
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun loadSerializedFromFile(file: File): Boolean {
        Log.i(tag, "Attempting to load serialized dictionary from file: ${file.absolutePath}")
        return try {
            coroutineContext.ensureActive()
            FileInputStream(file).use { input ->
                decodeSerializedDictionary(input)
            }
            true
        } catch (e: SerializationException) {
            Log.e(tag, "Failed to deserialize dictionary from file: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(tag, "Error loading serialized dictionary from file: ${e.javaClass.simpleName} - ${e.message}", e)
            false
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun decodeSerializedDictionary(input: InputStream) {
        val json = Json { ignoreUnknownKeys = true }
        val index = json.decodeFromStream<DictionaryIndex>(input)
        Log.i(tag, "Deserialized dictionary: normalizedIndex=${index.normalizedIndex.size}, prefixCache=${index.prefixCache.size}")

        normalizedIndex.clear()
        prefixCache.clear()

        index.normalizedIndex.forEach { (normalized, entries) ->
            normalizedIndex[normalized] = entries.map { it.toDictionaryEntry() }.toMutableList()
        }

        index.prefixCache.forEach { (prefix, entries) ->
            prefixCache[prefix] = entries.map { it.toDictionaryEntry() }.toMutableList()
        }

        if (index.symDeletes != null && index.symMeta != null) {
            val engine = SymSpell(
                maxEditDistance = index.symMeta.maxEditDistance,
                prefixLength = index.symMeta.prefixLength
            )
            val termFrequencies = index.normalizedIndex.mapValues { (_, entries) ->
                entries.maxOfOrNull { it.frequency } ?: 0
            }
            val prefixToTerms = mutableMapOf<String, MutableList<String>>()
            termFrequencies.keys.forEach { term ->
                val prefix = term.take(index.symMeta.prefixLength.coerceAtMost(term.length))
                val list = prefixToTerms.getOrPut(prefix) { mutableListOf() }
                list.add(term)
            }
            val expandedDeletes = mutableMapOf<String, MutableList<String>>()
            index.symDeletes.forEach { (deleteKey, terms) ->
                val targets = LinkedHashSet<String>()
                terms.forEach { t ->
                    if (termFrequencies.containsKey(t)) {
                        targets.add(t)
                    } else {
                        prefixToTerms[t]?.let { targets.addAll(it) }
                    }
                }
                if (targets.isNotEmpty()) {
                    expandedDeletes[deleteKey] = targets.toMutableList()
                }
            }
            engine.loadSerialized(termFrequencies, expandedDeletes)
            symSpell = engine
            symSpellBuilt = true
            Log.i(tag, "Loaded precomputed SymSpell deletes: ${expandedDeletes.size} keys")
        }

        Log.i(tag, "Successfully populated indices from serialized format")
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
    
    /**
     * Loads optional default user entries from a writable file, falling back to the asset.
     * Format: [{ "w": "word", "f": 10 }, ...]
     */
    private fun loadUserDefaults(): List<DictionaryEntry> {
        val fileName = "user_defaults.json"
        val file = context.getFileStreamPath(fileName)
        
        // Ensure we have a local editable copy if asset exists
        if (!file.exists()) {
            try {
                assets.open("common/dictionaries/$fileName").use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (_: Exception) {
                // No asset; leave file absent
            }
        }
        
        return try {
            val jsonString = if (file.exists()) {
                file.readText()
            } else {
                assets.open("common/dictionaries/$fileName").bufferedReader().use { it.readText() }
            }
            val jsonArray = JSONArray(jsonString)
            buildList {
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val word = obj.getString("w")
                    val freq = obj.optInt("f", 1)
                    add(DictionaryEntry(word, freq, SuggestionSource.USER))
                }
            }
        } catch (e: Exception) {
            if (debugLogging) Log.d(tag, "No default user dictionary at $fileName (${e.javaClass.simpleName}: ${e.message})")
            emptyList()
        }
    }

    private fun index(entries: List<DictionaryEntry>, keepExisting: Boolean = false) {
        if (!keepExisting) {
            prefixCache.clear()
            normalizedIndex.clear()
            symSpellBuilt = false
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

    private fun purgeUserEntries(currentUserEntries: List<DictionaryEntry>) {
        if (!isReady && !loadStarted) return
        val keepSet = currentUserEntries.map { it.word.lowercase(baseLocale) }.toSet()
        // Remove USER entries not in keepSet
        normalizedIndex.forEach { (_, list) ->
            val iterator = list.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.source == SuggestionSource.USER &&
                    !keepSet.contains(entry.word.lowercase(baseLocale))
                ) {
                    iterator.remove()
                }
            }
        }
        // Rebuild prefixCache from current normalizedIndex
        prefixCache.clear()
        normalizedIndex.forEach { (normalized, list) ->
            val maxPrefixLength = normalized.length.coerceAtMost(cachePrefixLength)
            for (length in 1..maxPrefixLength) {
                val prefix = normalized.take(length)
                val prefixList = prefixCache.getOrPut(prefix) { mutableListOf() }
                prefixList.addAll(list)
            }
        }
        prefixCache.values.forEach { it.sortByDescending { entry -> entry.frequency } }
    }

    private fun buildSymSpell() {
        if (symSpellBuilt && symSpell != null) return
        val engine = SymSpell(maxEditDistance = 2, prefixLength = cachePrefixLength)
        normalizedIndex.forEach { (normalized, entries) ->
            val best = entries.maxByOrNull { it.frequency } ?: return@forEach
            engine.addWord(normalized, best.frequency)
        }
        symSpell = engine
        symSpellBuilt = true
    }

    private fun addToSymSpell(entries: List<DictionaryEntry>) {
        val engine = symSpell ?: run {
            buildSymSpell()
            symSpell ?: return
        }
        entries.forEach { entry ->
            val normalized = normalize(entry.word)
            engine.addWord(normalized, entry.frequency)
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
