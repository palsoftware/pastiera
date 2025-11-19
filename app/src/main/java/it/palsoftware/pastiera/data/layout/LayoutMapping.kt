package it.palsoftware.pastiera.data.layout

/**
 * Represents a single mapping between a physical key (KEYCODE) and the
 * lowercase/uppercase characters that should be produced.
 */
data class LayoutMapping(
    val lowercase: Char,
    val uppercase: Char
)

