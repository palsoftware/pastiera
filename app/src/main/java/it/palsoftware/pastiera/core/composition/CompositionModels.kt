package it.palsoftware.pastiera.core.composition

internal enum class CompositionInputSource { PHYSICAL, SOFTWARE }

internal data class NormalizedCompositionStroke(
    val jamo: Char,
    val physicalKeyCode: Int? = null,
    val source: CompositionInputSource = CompositionInputSource.PHYSICAL,
    val shiftedByUser: Boolean = false
)

internal data class CompositionSnapshot(
    val sessionId: Long,
    val revision: Long,
    val layoutId: String,
    val renderedText: String,
    val strokeCount: Int
)

internal sealed interface ComposerUpdate {
    data object NotHandled : ComposerUpdate
    data class Replace(val snapshot: CompositionSnapshot) : ComposerUpdate
}

internal interface TextComposer {
    val isActive: Boolean
    val snapshot: CompositionSnapshot?
    val strokeCount: Int
    val needsInternalSplit: Boolean

    fun accept(stroke: NormalizedCompositionStroke): ComposerUpdate
    fun backspace(): ComposerUpdate
    fun reset()
}
