package it.palsoftware.pastiera.core.suggestions

import kotlinx.serialization.Serializable

/**
 * Serialized dictionary index structure.
 * Contains pre-built normalized index and prefix cache for fast loading.
 */
@Serializable
data class DictionaryIndex(
    val normalizedIndex: Map<String, List<SerializableDictionaryEntry>>,
    val prefixCache: Map<String, List<SerializableDictionaryEntry>>,
    val ngrams: Map<String, List<SerializableNGramEntry>>? = null,
    val symDeletes: Map<String, List<String>>? = null,
    val symMeta: SymSpellMeta? = null
)

/**
 * Serializable version of NGramEntry.
 */
@Serializable
data class SerializableNGramEntry(
    val context: List<String>,
    val word: String,
    val frequency: Int
)

/**
 * Serializable version of DictionaryEntry.
 * Uses Int for source instead of enum for serialization compatibility.
 */
@Serializable
data class SerializableDictionaryEntry(
    val word: String,
    val frequency: Int,
    val source: Int // 0 = MAIN, 1 = USER
)

@Serializable
data class SymSpellMeta(
    val maxEditDistance: Int,
    val prefixLength: Int
)

/**
 * Converts DictionaryEntry to SerializableDictionaryEntry.
 */
fun DictionaryEntry.toSerializable(): SerializableDictionaryEntry {
    return SerializableDictionaryEntry(
        word = this.word,
        frequency = this.frequency,
        source = this.source.ordinal
    )
}

/**
 * Converts SerializableDictionaryEntry to DictionaryEntry.
 */
fun SerializableDictionaryEntry.toDictionaryEntry(): DictionaryEntry {
    return DictionaryEntry(
        word = this.word,
        frequency = this.frequency,
        source = SuggestionSource.values()[this.source]
    )
}

