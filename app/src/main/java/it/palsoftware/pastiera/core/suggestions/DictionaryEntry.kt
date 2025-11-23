package it.palsoftware.pastiera.core.suggestions

data class DictionaryEntry(
    val word: String,
    val frequency: Int,
    val source: SuggestionSource = SuggestionSource.MAIN
)

enum class SuggestionSource {
    MAIN,
    USER
}
