package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.util.Log
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.suggestions.db.NGramEntity
import it.palsoftware.pastiera.core.suggestions.db.UserDictionaryDatabase
import it.palsoftware.pastiera.core.suggestions.db.UserWordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray

class UserDictionaryStore {

    data class UserEntry(
        val word: String,
        val frequency: Int,
        val lastUsed: Long
    )

    data class NGramEntry(
        val context: List<String>,
        val word: String,
        val frequency: Int,
        val lastUsed: Long
    )

    private val storeScope = CoroutineScope(Dispatchers.IO)

    fun loadUserEntries(context: Context): List<DictionaryEntry> {
        val db = UserDictionaryDatabase.getDatabase(context)
        val dao = db.userDictionaryDao()

        // Migration check
        val prefs = SettingsManager.getPreferences(context)
        if (prefs.contains(KEY_USER_DICTIONARY) || prefs.contains(KEY_USER_NGRAMS)) {
            migrateFromJson(context)
        }

        return runBlocking(Dispatchers.IO) {
            val words = dao.getAllWords()
            cache.clear()
            words.forEach { entity ->
                cache[entity.word] = UserEntry(entity.originalWord, entity.frequency, entity.lastUsed)
            }

            // Load NGrams as well
            loadNGramsFromDb(context)

            words.map {
                DictionaryEntry(
                    word = it.originalWord,
                    frequency = it.frequency,
                    source = SuggestionSource.USER
                )
            }
        }
    }

    private suspend fun loadNGramsFromDb(context: Context) {
        val dao = UserDictionaryDatabase.getDatabase(context).userDictionaryDao()
        val ngrams = dao.getAllNGrams()
        ngramCache.clear()
        ngramContextIndex.clear()
        ngrams.forEach { entity ->
            val contextList = entity.contextKey.split("|")
            val entry = NGramEntry(contextList, entity.word, entity.frequency, entity.lastUsed)
            val key = "${entity.contextKey}->${entity.word}"
            ngramCache[key] = entry
            ngramContextIndex.getOrPut(entity.contextKey) { mutableListOf() }.add(entry)
        }
        ngramContextIndex.values.forEach { it.sortByDescending { entry -> entry.frequency } }
    }

    private fun migrateFromJson(context: Context) {
        val prefs = SettingsManager.getPreferences(context)
        val db = UserDictionaryDatabase.getDatabase(context)
        val dao = db.userDictionaryDao()

        try {
            val wordJson = prefs.getString(KEY_USER_DICTIONARY, "[]") ?: "[]"
            val wordArr = JSONArray(wordJson)
            val wordEntities = mutableListOf<UserWordEntity>()
            for (i in 0 until wordArr.length()) {
                val obj = wordArr.getJSONObject(i)
                val originalWord = obj.getString(KEY_WORD)
                wordEntities.add(UserWordEntity(
                    word = originalWord.lowercase(),
                    originalWord = originalWord,
                    frequency = obj.optInt(KEY_FREQ, 1),
                    lastUsed = obj.optLong(KEY_LAST_USED, 0L)
                ))
            }

            val ngramJson = prefs.getString(KEY_USER_NGRAMS, "[]") ?: "[]"
            val ngramArr = JSONArray(ngramJson)
            val ngramEntities = mutableListOf<NGramEntity>()
            for (i in 0 until ngramArr.length()) {
                val obj = ngramArr.getJSONObject(i)
                val contextJson = obj.getJSONArray(KEY_CONTEXT)
                val contextList = mutableListOf<String>()
                for (j in 0 until contextJson.length()) {
                    contextList.add(contextJson.getString(j))
                }
                val contextKey = contextList.joinToString("|") { it.lowercase() }
                ngramEntities.add(NGramEntity(
                    contextKey = contextKey,
                    word = obj.getString(KEY_WORD),
                    frequency = obj.optInt(KEY_FREQ, 1),
                    lastUsed = obj.optLong(KEY_LAST_USED, 0L)
                ))
            }

            runBlocking(Dispatchers.IO) {
                dao.insertWords(wordEntities)
                dao.insertNGrams(ngramEntities)
            }

            // Clear old prefs
            prefs.edit()
                .remove(KEY_USER_DICTIONARY)
                .remove(KEY_USER_NGRAMS)
                .apply()
            
            Log.i(TAG, "Migrated user dictionary to Room successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during migration to Room", e)
        }
    }

    fun addNGram(context: Context, contextWords: List<String>, word: String, autoPersist: Boolean = true) {
        if (word.isBlank()) return
        val contextKey = contextWords.joinToString("|") { it.lowercase() }
        val key = "$contextKey->$word"
        val existing = ngramCache[key]
        val updated = if (existing != null) {
            existing.copy(frequency = existing.frequency + 1, lastUsed = System.currentTimeMillis())
        } else {
            NGramEntry(contextWords, word, 1, System.currentTimeMillis())
        }
        
        ngramCache[key] = updated
        
        // Update context index
        val bucket = ngramContextIndex.getOrPut(contextKey) { mutableListOf() }
        bucket.removeIf { it.word.equals(word, ignoreCase = true) }
        bucket.add(updated)
        bucket.sortByDescending { it.frequency }
        
        if (autoPersist) {
            storeScope.launch {
                val dao = UserDictionaryDatabase.getDatabase(context).userDictionaryDao()
                dao.insertNGrams(listOf(NGramEntity(contextKey, word, updated.frequency, updated.lastUsed)))
            }
        }
    }

    fun getNGramsForContext(contextWords: List<String>): List<NGramEntry> {
        val contextKey = contextWords.joinToString("|") { it.lowercase() }
        val cached = ngramContextIndex[contextKey]
        if (cached != null) return cached
        
        // This is a bit tricky since getNGramsForContext is called synchronously in SuggestionEngine.
        // We'll return an empty list if not in cache, or we could use runBlocking (not ideal).
        // However, loadUserEntries should have populated the cache.
        return emptyList()
    }

    fun addWord(context: Context, word: String, autoPersist: Boolean = true) {
        val cacheKey = word.lowercase()
        val entry = cache[cacheKey]
        val updated = if (entry != null) {
            entry.copy(frequency = entry.frequency + 1, lastUsed = System.currentTimeMillis())
        } else {
            UserEntry(word, 1, System.currentTimeMillis())
        }
        cache[cacheKey] = updated
        
        if (autoPersist) {
            storeScope.launch {
                val dao = UserDictionaryDatabase.getDatabase(context).userDictionaryDao()
                dao.insertWords(listOf(UserWordEntity(cacheKey, word, updated.frequency, updated.lastUsed)))
            }
        }
    }

    fun persistManually(context: Context) {
        // For mass imports, we use a single transaction
        val wordsToInsert = cache.map { (key, entry) -> 
            UserWordEntity(key, entry.word, entry.frequency, entry.lastUsed) 
        }
        
        val ngramsToInsert = ngramCache.map { (key, entry) ->
            val contextKey = entry.context.joinToString("|") { it.lowercase() }
            NGramEntity(contextKey, entry.word, entry.frequency, entry.lastUsed)
        }

        storeScope.launch {
            val dao = UserDictionaryDatabase.getDatabase(context).userDictionaryDao()
            dao.insertWords(wordsToInsert)
            dao.insertNGrams(ngramsToInsert)
            Log.i(TAG, "Mass persisted ${wordsToInsert.size} words and ${ngramsToInsert.size} ngrams")
        }
    }

    fun removeWord(context: Context, word: String) {
        val cacheKey = word.lowercase()
        cache.remove(cacheKey)
        storeScope.launch {
            val dao = UserDictionaryDatabase.getDatabase(context).userDictionaryDao()
            dao.deleteWord(cacheKey)
        }
    }

    fun updateWord(context: Context, oldWord: String, newWord: String) {
        val oldKey = oldWord.lowercase()
        val newKey = newWord.lowercase()
        val existing = cache[oldKey] ?: return
        cache.remove(oldKey)
        val updated = existing.copy(word = newWord, lastUsed = System.currentTimeMillis())
        cache[newKey] = updated
        
        storeScope.launch {
            val dao = UserDictionaryDatabase.getDatabase(context).userDictionaryDao()
            dao.deleteWord(oldKey)
            dao.insertWords(listOf(UserWordEntity(newKey, newWord, updated.frequency, updated.lastUsed)))
        }
    }

    fun markUsed(context: Context, word: String) {
        val cacheKey = word.lowercase()
        cache[cacheKey]?.let {
            val updated = it.copy(lastUsed = System.currentTimeMillis(), frequency = it.frequency + 1)
            cache[cacheKey] = updated
            storeScope.launch {
                val dao = UserDictionaryDatabase.getDatabase(context).userDictionaryDao()
                dao.insertWords(listOf(UserWordEntity(cacheKey, it.word, updated.frequency, updated.lastUsed)))
            }
        }
    }

    fun getSnapshot(): List<UserEntry> = cache.values.sortedByDescending { it.lastUsed }

    fun resetAll(context: Context) {
        cache.clear()
        ngramCache.clear()
        ngramContextIndex.clear()
        storeScope.launch {
            val dao = UserDictionaryDatabase.getDatabase(context).userDictionaryDao()
            dao.resetAll()
        }
    }

    companion object {
        private const val TAG = "UserDictionaryStore"
        private const val KEY_USER_DICTIONARY = "user_dictionary_entries"
        private const val KEY_USER_NGRAMS = "user_ngram_entries"
        private const val KEY_WORD = "w"
        private const val KEY_CONTEXT = "c"
        private const val KEY_FREQ = "f"
        private const val KEY_LAST_USED = "u"
        private val cache: MutableMap<String, UserEntry> = mutableMapOf()
        private val ngramCache: MutableMap<String, NGramEntry> = mutableMapOf()
        private val ngramContextIndex: MutableMap<String, MutableList<NGramEntry>> = mutableMapOf()
    }
}
