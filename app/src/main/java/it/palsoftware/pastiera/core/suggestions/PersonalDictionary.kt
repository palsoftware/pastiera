package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Represents a personal dictionary entry.
 * @param word The word to match (case-insensitive matching)
 * @param replacement The replacement text (null means don't autocorrect this word)
 */
data class PersonalEntry(
    val word: String,
    val replacement: String?
)

/**
 * Manages the user's personal dictionary of custom word replacements.
 * Entries can either:
 * - Replace a word with a specific replacement (e.g., "teh" -> "the", "ngl" -> "NGL")
 * - Prevent autocorrection of a word (replacement is null)
 *
 * Words are stored in SharedPreferences and persist across app restarts.
 */
class PersonalDictionary(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    // Map from lowercase word to PersonalEntry
    private val _entries = MutableStateFlow<Map<String, PersonalEntry>>(loadEntries())
    val entries: StateFlow<Map<String, PersonalEntry>> = _entries.asStateFlow()

    /**
     * Add a word with a replacement to the personal dictionary.
     * @param word The word to match (will be matched case-insensitively)
     * @param replacement The replacement text (case-sensitive, used as-is)
     */
    fun addEntry(word: String, replacement: String?) {
        val trimmedWord = word.trim()
        val trimmedReplacement = replacement?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmedWord.isNotEmpty()) {
            val key = trimmedWord.lowercase()
            val entry = PersonalEntry(trimmedWord, trimmedReplacement)
            val updated = _entries.value + (key to entry)
            _entries.value = updated
            saveEntries(updated)
        }
    }

    /**
     * Add a word that should not be autocorrected.
     */
    fun addWord(word: String) {
        addEntry(word, null)
    }

    /**
     * Remove a word from the personal dictionary.
     */
    fun removeWord(word: String) {
        val key = word.trim().lowercase()
        val updated = _entries.value - key
        _entries.value = updated
        saveEntries(updated)
    }

    /**
     * Check if a word is in the personal dictionary.
     */
    fun contains(word: String): Boolean {
        return _entries.value.containsKey(word.lowercase())
    }

    /**
     * Get the replacement for a word, or null if no replacement is defined.
     * Returns the PersonalEntry if the word exists in the dictionary.
     */
    fun getEntry(word: String): PersonalEntry? {
        return _entries.value[word.lowercase()]
    }

    /**
     * Get all entries in the personal dictionary, sorted alphabetically by word.
     */
    fun getAllEntries(): List<PersonalEntry> {
        return _entries.value.values.sortedBy { it.word.lowercase() }
    }

    /**
     * Clear all entries from the personal dictionary.
     */
    fun clear() {
        _entries.value = emptyMap()
        prefs.edit().remove(KEY_ENTRIES).apply()
    }

    private fun loadEntries(): Map<String, PersonalEntry> {
        val entriesString = prefs.getString(KEY_ENTRIES, "") ?: ""
        if (entriesString.isEmpty()) {
            // Migrate from old format if exists
            val migrated = migrateOldFormat()
            if (migrated.isNotEmpty()) return migrated

            // First run: seed with default abbreviations
            return seedDefaultAbbreviations()
        }

        return buildMap {
            entriesString.split(ENTRY_DELIMITER).filter { it.isNotEmpty() }.forEach { line ->
                val parts = line.split(WORD_REPLACEMENT_DELIMITER, limit = 2)
                val word = parts[0]
                val replacement = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
                if (word.isNotEmpty()) {
                    put(word.lowercase(), PersonalEntry(word, replacement))
                }
            }
        }
    }

    /**
     * Seeds the personal dictionary with common abbreviations on first run.
     * These abbreviations will not be autocorrected to other words.
     */
    private fun seedDefaultAbbreviations(): Map<String, PersonalEntry> {
        val abbreviations = listOf(
            // Common internet/texting abbreviations
            "lol", "lmao", "rofl", "omg", "wtf", "wth",
            "idk", "idc",
            "btw", "fyi", "imo", "imho", "tbh", "ngl", "smh",
            "brb", "bbl", "gtg", "g2g", "ttyl",
            "asap", "eta", "rn", "atm",
            "irl", "iirc", "afaik",
            "dm", "pm", "hmu",
            "ty", "thx", "np", "yw",
            "jk", "fr",
            "aka", "etc", "vs",
            "ofc",
            "pls", "plz", "bc", "tho", "thru",
            "bf", "gf", "bff",
            "omw",
            "fwiw", "icymi",
            "tldr",
            "gg", "afk",
            "nvm"
        )

        val entries = buildMap {
            abbreviations.forEach { abbr ->
                put(abbr.lowercase(), PersonalEntry(abbr, null))
            }
        }

        // Save the seeded entries
        saveEntries(entries)

        return entries
    }

    private fun migrateOldFormat(): Map<String, PersonalEntry> {
        // Check for old format (just words separated by newlines)
        val oldWordsString = prefs.getString(KEY_WORDS_OLD, "") ?: ""
        if (oldWordsString.isEmpty()) return emptyMap()

        val entries = buildMap {
            oldWordsString.split("\n").filter { it.isNotEmpty() }.forEach { word ->
                put(word.lowercase(), PersonalEntry(word, null))
            }
        }

        // Save in new format and remove old key
        if (entries.isNotEmpty()) {
            saveEntries(entries)
            prefs.edit().remove(KEY_WORDS_OLD).apply()
        }

        return entries
    }

    private fun saveEntries(entries: Map<String, PersonalEntry>) {
        val entriesString = entries.values.joinToString(ENTRY_DELIMITER) { entry ->
            if (entry.replacement != null) {
                "${entry.word}$WORD_REPLACEMENT_DELIMITER${entry.replacement}"
            } else {
                entry.word
            }
        }
        prefs.edit().putString(KEY_ENTRIES, entriesString).apply()
    }

    // Legacy property for backward compatibility with UI
    val words: StateFlow<Set<String>>
        get() = MutableStateFlow(_entries.value.keys).asStateFlow()

    companion object {
        private const val PREFS_NAME = "personal_dictionary"
        private const val KEY_ENTRIES = "entries_v2"
        private const val KEY_WORDS_OLD = "words"
        private const val ENTRY_DELIMITER = "\n"
        private const val WORD_REPLACEMENT_DELIMITER = "\t"

        @Volatile
        private var instance: PersonalDictionary? = null

        fun getInstance(context: Context): PersonalDictionary {
            return instance ?: synchronized(this) {
                instance ?: PersonalDictionary(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
