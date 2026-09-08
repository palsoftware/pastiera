package it.palsoftware.pastiera.inputmethod.composition

internal enum class ComposerKind { NONE, HANGUL }

internal data class BuiltInLayoutCapabilities(
    val composerKind: ComposerKind,
    val supportsAutoCapitalization: Boolean,
    val capsLockAffectsOutput: Boolean
)

internal object LayoutCapabilitiesRegistry {
    fun forLayout(layoutId: String?): BuiltInLayoutCapabilities = when (layoutId) {
        "korean_2set" -> BuiltInLayoutCapabilities(ComposerKind.HANGUL, false, false)
        else -> BuiltInLayoutCapabilities(ComposerKind.NONE, true, true)
    }
}
