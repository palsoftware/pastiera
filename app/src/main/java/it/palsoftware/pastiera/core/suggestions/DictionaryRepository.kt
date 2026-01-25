package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import android.os.Looper
import org.json.JSONArray
import java.util.concurrent.CancellationException
import kotlin.coroutines.coroutineContext
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.pow

interface DictionaryRepository {
    val isReady: Boolean
    val isLoadStarted: Boolean
    suspend fun loadIfNeeded()
    suspend fun refreshUserEntries()
    fun addUserEntryQuick(word: String)
    fun removeUserEntry(word: String)
    fun markUsed(word: String)
    fun effectiveFrequency(entry: DictionaryEntry): Int
    fun getExactWordFrequency(word: String): Int
    fun lookupByPrefixMerged(prefix: String, maxSize: Int): List<DictionaryEntry>
    fun symSpellLookup(term: String, maxSuggestions: Int): List<SymSpell.SuggestItem>
    fun bestEntryForNormalized(normalized: String): DictionaryEntry?
    fun topByNormalized(normalized: String, limit: Int = 5): List<DictionaryEntry>
    fun isKnownWord(word: String): Boolean
    fun ensureLoadScheduled(background: () -> Unit)
}

/**
 * Loads and indexes lightweight dictionaries from assets and merges them with the user dictionary.
 */
class AndroidDictionaryRepository(
    private val context: Context,
    private val assets: AssetManager,
    private val userDictionaryStore: UserDictionaryStore,
    private val baseLocale: Locale = Locale.ITALIAN,
    private val cachePrefixLength: Int = 4,
    debugLogging: Boolean = false
) : DictionaryRepository {

    companion object {
        // Avoid concurrent heavy loads across repositories to reduce memory spikes.
        private val loadMutex = Mutex()
        
        /**
         * Checks if a dictionary file exists for the given locale.
         * Returns true if a dictionary is found (serialized format) in assets or custom folder.
         * This is a lightweight check that doesn't require loading the dictionary.
         */
        fun hasDictionaryForLocale(context: Context, locale: Locale): Boolean {
            return hasDictionaryForLocale(context, locale.language)
        }
        
        /**
         * Checks if a dictionary file exists for the given language code.
         * Returns true if a dictionary is found (serialized format) in assets or custom folder.
         */
        fun hasDictionaryForLocale(context: Context, languageCode: String): Boolean {
            return try {
                val langCode = languageCode.lowercase()
                val assets = context.assets
                
                // Check serialized dictionaries from assets
                try {
                    val serializedFiles = assets.list("common/dictionaries_serialized")
                    serializedFiles?.forEach { fileName ->
                        if (fileName == "${langCode}_base.dict") {
                            return true
                        }
                    }
                } catch (e: Exception) {
                    // If serialized directory doesn't exist, continue
                }

                // Check custom/imported serialized dictionaries in app storage
                try {
                    val localDir = File(context.filesDir, "dictionaries_serialized/custom")
                    val localFiles = localDir.listFiles { file ->
                        file.isFile && file.name == "${langCode}_base.dict"
                    }
                    if (!localFiles.isNullOrEmpty()) {
                        return true
                    }
                } catch (e: Exception) {
                    // Ignore errors
                }
                
                false
            } catch (e: Exception) {
                false
            }
        }
    }

    private val prefixCache: MutableMap<String, MutableList<DictionaryEntry>> = mutableMapOf()
    private val normalizedIndex: MutableMap<String, MutableList<DictionaryEntry>> = mutableMapOf()
    @Volatile private var symSpell: SymSpell? = null
    @Volatile private var symSpellBuilt: Boolean = false
    @Volatile override var isReady: Boolean = false
        internal set
    @Volatile private var loadStarted: Boolean = false
    
    override val isLoadStarted: Boolean
        get() = loadStarted
    private val tag = "DictionaryRepo"
    private val debugLogging: Boolean = debugLogging
    private val maxRawFrequency = 255.0
    private val scaledFrequencyMax = 1600.0

    override suspend fun loadIfNeeded() {
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

                // Move legacy local dictionaries into the custom folder to isolate imports.
                migrateLegacyLocalDictionaries()

                // Determine paths for custom and bundled dictionaries
                val customDir = File(context.filesDir, "dictionaries_serialized/custom").apply { mkdirs() }
                val customFile = File(customDir, "${baseLocale.language}_base.dict")
                coroutineContext.ensureActive()
                val serializedPath = "common/dictionaries_serialized/${baseLocale.language}_base.dict"
                val loadedSerialized = when {
                    customFile.exists() -> {
                        Log.i(tag, "Loading CUSTOM dictionary from ${customFile.absolutePath}")
                        loadSerializedFromFile(customFile)
                    }
                    else -> {
                        Log.i(tag, "Loading BUNDLED dictionary from assets: $serializedPath")
                        loadSerializedFromAssets(serializedPath)
                    }
                }

                if (!loadedSerialized) {
                    Log.e(tag, "Failed to load serialized dictionary for locale=${baseLocale.language}")
                    synchronized(this) { loadStarted = false }
                    return
                }

                val loadTime = System.currentTimeMillis() - startTime
                Log.i(tag, "Loaded dictionary (.dict) in ${loadTime}ms - normalizedIndex=${normalizedIndex.size} prefixCache=${prefixCache.size}")

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

    override fun ensureLoadScheduled(background: () -> Unit) {
        if (isReady || loadStarted) return
        background()
    }

    override suspend fun refreshUserEntries() {
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
    override fun addUserEntryQuick(word: String) {
        userDictionaryStore.addWord(context, word)
        // Find latest frequency from snapshot; default to 1
        val freq = userDictionaryStore.getSnapshot()
            .firstOrNull { it.word.equals(word, ignoreCase = true) }
            ?.frequency ?: 1
        val entry = DictionaryEntry(word, freq, SuggestionSource.USER)
        index(listOf(entry), keepExisting = true)
        addToSymSpell(listOf(entry))
    }

    override fun removeUserEntry(word: String) {
        userDictionaryStore.removeWord(context, word)
        // Caller should refresh asynchronously; keep legacy path noop here.
    }

    override fun markUsed(word: String) {
        userDictionaryStore.markUsed(context, word)
    }

    override fun isKnownWord(word: String): Boolean {
        if (!isReady) return false
        val normalized = normalize(word)
        return normalizedIndex[normalized]?.isNotEmpty() == true
    }

    /**
     * Applies a non-linear scaling to compact dictionary frequencies (0–255) to
     * restore a meaningful range for ranking and SymSpell. The exponent (<1)
     * boosts mid/high values without making low values explode.
     */
    override fun effectiveFrequency(entry: DictionaryEntry): Int {
        val raw = entry.frequency.coerceAtLeast(0).coerceAtMost(maxRawFrequency.toInt())
        val normalized = raw / maxRawFrequency
        val scaled = (normalized.pow(0.75) * scaledFrequencyMax).toInt()
        return scaled.coerceAtLeast(1)
    }

    /**
     * Gets the frequency of an exact word (case-insensitive match).
     * Returns the maximum frequency if multiple entries exist (e.g., different sources).
     * Returns 0 if the word doesn't exist.
     */
    override fun getExactWordFrequency(word: String): Int {
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
    override fun lookupByPrefixMerged(prefix: String, maxSize: Int): List<DictionaryEntry> {
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

    override fun symSpellLookup(term: String, maxSuggestions: Int): List<SymSpell.SuggestItem> {
        val engine = symSpell ?: return emptyList()
        return engine.lookup(term, maxSuggestions)
    }

    override fun bestEntryForNormalized(normalized: String): DictionaryEntry? {
        return normalizedIndex[normalized]?.maxByOrNull { effectiveFrequency(it) }
    }

    fun allCandidates(): List<DictionaryEntry> {
        if (!isReady) return emptyList()
        return normalizedIndex.values.flatten()
    }

    /**
     * Returns top entries for a normalized term, sorted by frequency.
     * Useful for single-character inputs to surface multiple variants (e.g., accented).
     */
    override fun topByNormalized(normalized: String, limit: Int): List<DictionaryEntry> {
        if (!isReady) return emptyList()
        return normalizedIndex[normalized]
            ?.sortedByDescending { effectiveFrequency(it) }
            ?.take(limit)
            .orEmpty()
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
        val bytes = input.readBytes()
        val sizeKb = bytes.size / 1024
        
        // Auto-detect format: JSON starts with '{' (0x7B), CBOR with other bytes
        val isJson = bytes.isNotEmpty() && bytes[0] == '{'.code.toByte()
        Log.i(tag, "Dictionary format detected: ${if (isJson) "JSON (legacy)" else "CBOR"} - size: ${sizeKb} KB")
        
        val startTime = System.currentTimeMillis()
        val index = if (isJson) {
            // Legacy JSON format
            val json = Json { ignoreUnknownKeys = true }
            json.decodeFromString<DictionaryIndex>(bytes.decodeToString())
        } else {
            // CBOR format (faster)
            Cbor.decodeFromByteArray<DictionaryIndex>(bytes)
        }
        val parseTime = System.currentTimeMillis() - startTime
        Log.i(tag, "Deserialized dictionary in ${parseTime}ms: normalizedIndex=${index.normalizedIndex.size}, prefixCache=${index.prefixCache.size}")

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
                entries.maxOfOrNull { effectiveFrequency(it.toDictionaryEntry()) } ?: 0
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

        // Re-sort caches using the runtime effective frequency scaling.
        sortCachesByEffectiveFrequency()
        Log.i(tag, "Successfully populated indices from serialized format")
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

    internal fun index(entries: List<DictionaryEntry>, keepExisting: Boolean = false) {
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

        sortCachesByEffectiveFrequency()
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
        sortCachesByEffectiveFrequency()
    }

    private fun buildSymSpell() {
        if (symSpellBuilt && symSpell != null) return
        val engine = SymSpell(maxEditDistance = 2, prefixLength = cachePrefixLength)
        normalizedIndex.forEach { (normalized, entries) ->
            val best = entries.maxByOrNull { effectiveFrequency(it) } ?: return@forEach
            engine.addWord(normalized, effectiveFrequency(best))
        }
        symSpell = engine
        symSpellBuilt = true
    }

    /**
     * Move legacy dictionaries from the root local directory into the custom folder.
     * This isolates user-imported dictionaries from bundled assets.
     */
    private fun migrateLegacyLocalDictionaries() {
        val rootDir = File(context.filesDir, "dictionaries_serialized")
        val customDir = File(rootDir, "custom").apply { mkdirs() }
        if (!rootDir.exists() || rootDir == customDir) return

        rootDir.listFiles()?.forEach { file ->
            if (file.isDirectory && file.name == "custom") return@forEach
            if (file.isFile && file.extension == "dict") {
                val dest = File(customDir, file.name)
                if (!dest.exists()) {
                    val moved = file.renameTo(dest)
                    Log.i(tag, "Migrating legacy dictionary ${file.name} to custom folder: success=$moved")
                    if (!moved) {
                        file.copyTo(dest, overwrite = false)
                        file.delete()
                    }
                } else {
                    Log.i(tag, "Removing duplicate legacy dictionary ${file.name}")
                    file.delete()
                }
            }
        }
    }

    private fun addToSymSpell(entries: List<DictionaryEntry>) {
        val engine = symSpell ?: run {
            buildSymSpell()
            symSpell ?: return
        }
        entries.forEach { entry ->
            val normalized = normalize(entry.word)
            engine.addWord(normalized, effectiveFrequency(entry))
        }
    }

    internal fun normalize(word: String): String {
        val normalized = Normalizer.normalize(word.lowercase(baseLocale), Normalizer.Form.NFD)
        // Remove combining marks (accents) explicitly - same logic as SuggestionEngine
        val withoutAccents = normalized.replace("\\p{Mn}".toRegex(), "")
        // Keep only Unicode letters (supports Latin, Cyrillic, Greek, Arabic, Chinese, etc.)
        // Removes: punctuation, numbers, spaces, emoji, symbols
        return withoutAccents.replace("[^\\p{L}]".toRegex(), "")
    }

    internal fun sortCachesByEffectiveFrequency() {
        prefixCache.values.forEach { list ->
            list.sortByDescending { effectiveFrequency(it) }
        }
        normalizedIndex.values.forEach { list ->
            list.sortByDescending { effectiveFrequency(it) }
        }
    }
}
