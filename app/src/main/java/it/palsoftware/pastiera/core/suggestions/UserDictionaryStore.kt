package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import it.palsoftware.pastiera.SettingsManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class UserDictionaryStore {

    data class UserEntry(
        val word: String,
        val frequency: Int,
        val lastUsed: Long
    )

    private fun prefs(context: Context): SharedPreferences = SettingsManager.getPreferences(context)

    fun loadUserEntries(context: Context): List<DictionaryEntry> {
        return try {
            val json = prefs(context).getString(KEY_USER_DICTIONARY, "[]") ?: "[]"
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val word = obj.getString(KEY_WORD)
                    val frequency = obj.optInt(KEY_FREQ, 1)
                    val lastUsed = obj.optLong(KEY_LAST_USED, 0L)
                    add(
                        DictionaryEntry(
                            word = word,
                            frequency = frequency,
                            source = SuggestionSource.USER
                        )
                    )
                    cache[word.lowercase(Locale.getDefault())] = UserEntry(word, frequency, lastUsed)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user dictionary", e)
            emptyList()
        }
    }

    fun addWord(context: Context, word: String) {
        val normalized = word.lowercase(Locale.getDefault())
        val entry = cache[normalized]
        val updated = if (entry != null) {
            entry.copy(frequency = entry.frequency + 1, lastUsed = System.currentTimeMillis())
        } else {
            UserEntry(word, 1, System.currentTimeMillis())
        }
        cache[normalized] = updated
        persist(context)
    }

    fun removeWord(context: Context, word: String) {
        cache.remove(word.lowercase(Locale.getDefault()))
        persist(context)
    }

    fun markUsed(context: Context, word: String) {
        val normalized = word.lowercase(Locale.getDefault())
        cache[normalized]?.let {
            cache[normalized] = it.copy(lastUsed = System.currentTimeMillis(), frequency = it.frequency + 1)
            persist(context)
        }
    }

    fun getSnapshot(): List<UserEntry> = cache.values.sortedByDescending { it.lastUsed }

    private fun persist(context: Context) {
        try {
            val array = JSONArray()
            cache.values.forEach { entry ->
                val obj = JSONObject()
                obj.put(KEY_WORD, entry.word)
                obj.put(KEY_FREQ, entry.frequency)
                obj.put(KEY_LAST_USED, entry.lastUsed)
                array.put(obj)
            }
            prefs(context).edit().putString(KEY_USER_DICTIONARY, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to persist user dictionary", e)
        }
    }

    companion object {
        private const val TAG = "UserDictionaryStore"
        private const val KEY_USER_DICTIONARY = "user_dictionary_entries"
        private const val KEY_WORD = "w"
        private const val KEY_FREQ = "f"
        private const val KEY_LAST_USED = "u"
        private val cache: MutableMap<String, UserEntry> = mutableMapOf()
    }
}
