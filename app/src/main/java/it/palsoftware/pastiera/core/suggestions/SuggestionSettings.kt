package it.palsoftware.pastiera.core.suggestions

data class SuggestionSettings(
    val suggestionsEnabled: Boolean = true,
    val accentMatching: Boolean = true,
    val autoReplaceOnSpaceEnter: Boolean = false,
    val maxAutoReplaceDistance: Int = 1,
    val maxSuggestions: Int = 3
)
