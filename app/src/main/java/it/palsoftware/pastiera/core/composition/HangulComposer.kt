package it.palsoftware.pastiera.core.composition

/** Pure Kotlin, bounded full-run two-set Hangul composer. */
internal class HangulComposer(
    private val layoutId: String = "korean_2set",
    private val maxStrokes: Int = 128,
    sessionId: Long = 1L
) : TextComposer {
    private var session = sessionId
    private var revision = 0L
    private val strokes = mutableListOf<NormalizedCompositionStroke>()
    private var currentSnapshot: CompositionSnapshot? = null

    override val isActive: Boolean get() = strokes.isNotEmpty()
    override val snapshot: CompositionSnapshot? get() = currentSnapshot
    override val strokeCount: Int get() = strokes.size
    override val needsInternalSplit: Boolean get() = strokes.size >= maxStrokes

    override fun accept(stroke: NormalizedCompositionStroke): ComposerUpdate {
        if (!HangulJamo.isSupported(stroke.jamo) || needsInternalSplit) return ComposerUpdate.NotHandled
        strokes += stroke
        return replaceSnapshot()
    }

    override fun backspace(): ComposerUpdate {
        if (strokes.isEmpty()) return ComposerUpdate.NotHandled
        strokes.removeAt(strokes.lastIndex)
        if (strokes.isEmpty()) {
            revision++
            currentSnapshot = null
            return ComposerUpdate.Replace(CompositionSnapshot(session, revision, layoutId, "", 0))
        }
        return replaceSnapshot()
    }

    override fun reset() {
        strokes.clear()
        currentSnapshot = null
        session++
        revision = 0L
    }

    /** Retain enough original strokes to include the complete open syllable.
     * A split is allowed only where independently replaying both parts is identical.
     * Confirmed prefix deletion subsequently uses the editor's ordinary DEL policy.
     */
    fun internalSplit(): InternalSplit? {
        val fullText = currentSnapshot?.renderedText ?: return null
        for (cut in (strokes.size - 8) downTo 1) {
            val prefix = render(strokes.take(cut).map { it.jamo })
            val suffix = render(strokes.drop(cut).map { it.jamo })
            if (prefix + suffix == fullText) {
                strokes.subList(0, cut).clear()
                replaceSnapshot()
                return InternalSplit(prefix, fullText)
            }
        }
        return null
    }

    data class InternalSplit(val prefix: String, val fullText: String)

    fun beginNewRun() {
        strokes.clear()
        currentSnapshot = null
        session++
        revision = 0L
    }

    private fun replaceSnapshot(): ComposerUpdate.Replace {
        revision++
        val rendered = render(strokes.map { it.jamo })
        val snapshot = CompositionSnapshot(session, revision, layoutId, rendered, strokes.size)
        currentSnapshot = snapshot
        return ComposerUpdate.Replace(snapshot)
    }

    private sealed interface Unit {
        data class C(val value: Char) : Unit
        data class V(val value: Char) : Unit
        data class S(val initial: Char, val medial: Char, val final: Char? = null) : Unit
    }

    private fun render(input: List<Char>): String {
        val output = mutableListOf<String>()
        var current: Unit? = null

        fun flush() {
            current?.let {
                output += when (it) {
                    is Unit.C -> it.value.toString()
                    is Unit.V -> it.value.toString()
                    is Unit.S -> HangulJamo.compose(it.initial, it.medial, it.final).toString()
                }
            }
            current = null
        }

        input.forEach { jamo ->
            if (HangulJamo.isConsonant(jamo)) {
                when (val state = current) {
                    null -> current = Unit.C(jamo)
                    is Unit.C -> { flush(); current = Unit.C(jamo) }
                    is Unit.V -> { flush(); current = Unit.C(jamo) }
                    is Unit.S -> if (state.final == null) {
                        val final = HangulJamo.finalIndex[jamo]
                        if (final != null && final > 0) current = state.copy(final = jamo) else { flush(); current = Unit.C(jamo) }
                    } else {
                        val combined = HangulJamo.combineFinal(state.final, jamo)
                        if (combined != null) current = state.copy(final = combined) else { flush(); current = Unit.C(jamo) }
                    }
                }
            } else {
                when (val state = current) {
                    null -> current = Unit.V(jamo)
                    is Unit.C -> if (HangulJamo.initialIndex[state.value] != null) current = Unit.S(state.value, jamo) else { flush(); current = Unit.V(jamo) }
                    is Unit.V -> HangulJamo.combineMedial(state.value, jamo)?.let { current = Unit.V(it) } ?: run { flush(); current = Unit.V(jamo) }
                    is Unit.S -> if (state.final == null) {
                        HangulJamo.combineMedial(state.medial, jamo)?.let { current = state.copy(medial = it) } ?: run { flush(); current = Unit.V(jamo) }
                    } else {
                        val split = HangulJamo.splitFinal(state.final)
                        if (split != null) {
                            flushUnit(output, state.copy(final = split.first)); current = Unit.S(split.second, jamo)
                        } else {
                            flushUnit(output, state.copy(final = null)); current = Unit.S(state.final, jamo)
                        }
                    }
                }
            }
        }
        flush()
        return output.joinToString("")
    }

    private fun flushUnit(output: MutableList<String>, unit: Unit.S) {
        output += HangulJamo.compose(unit.initial, unit.medial, unit.final).toString()
    }
}
