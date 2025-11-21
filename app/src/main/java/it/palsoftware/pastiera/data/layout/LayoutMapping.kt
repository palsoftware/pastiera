package it.palsoftware.pastiera.data.layout

/**
 * Represents a single mapping between a physical key (KEYCODE) and the
 * lowercase/uppercase characters that should be produced, with optional
 * multi-tap tap-level overrides.
 */
data class TapMapping(
    val lowercase: String,
    val uppercase: String
)

data class LayoutMapping(
    val lowercase: String,
    val uppercase: String,
    val multiTapEnabled: Boolean = false,
    val taps: List<TapMapping> = emptyList()
)

val LayoutMapping.isRealMultiTap: Boolean
    get() = multiTapEnabled && taps.size > 1
