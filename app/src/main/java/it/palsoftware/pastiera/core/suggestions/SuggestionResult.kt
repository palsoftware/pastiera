package it.palsoftware.pastiera.core.suggestions

data class SuggestionResult(
    val candidate: String,
    val distance: Int,
    val score: Double,
    val source: SuggestionSource
)
