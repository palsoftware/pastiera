package it.palsoftware.pastiera.inputmethod.composition

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.core.composition.ComposerUpdate
import it.palsoftware.pastiera.core.composition.HangulComposer
import it.palsoftware.pastiera.core.composition.HangulJamo
import it.palsoftware.pastiera.core.composition.NormalizedCompositionStroke
import it.palsoftware.pastiera.core.composition.CompositionSnapshot

/** Android adapter around the pure composer; it never rewrites surrounding editor text. */
internal class ImeCompositionCoordinator(
    private val onCompositionStarted: () -> Unit = {},
    private val onCompositionFinished: (CompositionFinishReason) -> Unit = {}
) {
    private var layoutId: String? = null
    private var composer: HangulComposer? = null
    private var editorSessionId = 0L
    private var editGeneration = 0L
    private val expectations = ArrayDeque<SelectionExpectation>()
    private val acknowledgedExpectations = ArrayDeque<SelectionExpectation>()
    private var composingStart: Int? = null
    private var nativeRangeObserved = false
    private var lastAcknowledgedLength: Int? = null
    private var commitOnly = false

    val isActive: Boolean get() = composer?.isActive == true
    val snapshot: CompositionSnapshot? get() = composer?.snapshot

    fun startEditorSession() {
        editorSessionId++
        editGeneration = 0L
        commitOnly = false
        resetWithoutEditorMutation()
        composer = null
    }

    fun selectLayout(layout: String?) {
        if (layout == layoutId && composer != null) return
        resetWithoutEditorMutation()
        layoutId = layout
        composer = if (LayoutCapabilitiesRegistry.forLayout(layout).composerKind == ComposerKind.HANGUL) {
            HangulComposer(layoutId = layout ?: "korean_2set", sessionId = editorSessionId)
        } else null
    }

    fun handleMappedStroke(
        inputConnection: InputConnection?,
        stroke: NormalizedCompositionStroke,
        consumeShiftOneShot: () -> Unit = {}
    ): Boolean {
        val activeComposer = composer ?: return false
        val ic = inputConnection ?: return false
        if (!HangulJamo.isSupported(stroke.jamo)) return false
        if (commitOnly) {
            val success = safely { ic.commitText(stroke.jamo.toString(), 1) }
            if (success) consumeShiftOneShot()
            return success
        }
        if (activeComposer.needsInternalSplit && !splitRun(ic, activeComposer)) return false
        val wasInactive = !activeComposer.isActive
        if (wasInactive) composingStart = readSelectionStart(ic)
        return when (val update = activeComposer.accept(stroke)) {
            ComposerUpdate.NotHandled -> false
            is ComposerUpdate.Replace -> {
                val success = applySnapshot(ic, update.snapshot)
                if (success) {
                    if (wasInactive) onCompositionStarted()
                    consumeShiftOneShot()
                } else {
                    // A rejected first write can degrade to plain jamo for this editor.
                    // A rejected replacement must never commit the stale full run.
                    val finished = wasInactive || safely { ic.finishComposingText() }
                    resetWithoutEditorMutation()
                    if (!wasInactive) onCompositionFinished(CompositionFinishReason.BOUNDARY)
                    if (!finished) return false
                    commitOnly = true
                    val committed = safely { ic.commitText(stroke.jamo.toString(), 1) }
                    if (committed) consumeShiftOneShot()
                    return committed
                }
                success
            }
        }
    }

    private fun splitRun(ic: InputConnection, activeComposer: HangulComposer): Boolean {
        // Avoid exposing the temporary finished region to selection callbacks.
        safely { ic.beginBatchEdit() }
        try {
            // Keep the open syllable (and its original keystrokes) editable. The editor
            // already contains the entire run: finish it, then reclaim only the suffix.
            val split = activeComposer.internalSplit() ?: return false
            val start = composingStart ?: readSelectionStart(ic)?.minus(split.fullText.length)
            if (start != null) {
                if (!safely { ic.finishComposingText() } ||
                    !safely { ic.setComposingRegion(start + split.prefix.length, start + split.fullText.length) }) {
                    resetWithoutEditorMutation()
                    onCompositionFinished(CompositionFinishReason.MAX_LENGTH_INTERNAL_SPLIT)
                    return false
                }
                clearTracking()
                composingStart = start + split.prefix.length
                nativeRangeObserved = true
                expectations.addLast(SelectionExpectation(editorSessionId, ++editGeneration,
                    activeComposer.snapshot!!.renderedText.length))
            } else {
                // Some editors implement composing but no extracted selection. Replace
                // the owned range with its stable prefix, then restore the editable tail.
                val suffix = activeComposer.snapshot!!
                if (!safely { ic.commitText(split.prefix, 1) }) {
                    safely { ic.finishComposingText() }
                    resetWithoutEditorMutation()
                    onCompositionFinished(CompositionFinishReason.MAX_LENGTH_INTERNAL_SPLIT)
                    return false
                }
                clearTracking()
                if (!applySnapshot(ic, suffix)) {
                    safely { ic.commitText(suffix.renderedText, 1) }
                    resetWithoutEditorMutation()
                    onCompositionFinished(CompositionFinishReason.MAX_LENGTH_INTERNAL_SPLIT)
                    return false
                }
            }
            return true
        } finally {
            safely { ic.endBatchEdit() }
        }
    }

    fun handleBackspace(inputConnection: InputConnection?): Boolean {
        val activeComposer = composer ?: return false
        if (!activeComposer.isActive || inputConnection == null) return false
        return when (val update = activeComposer.backspace()) {
            ComposerUpdate.NotHandled -> false
            is ComposerUpdate.Replace -> {
                val success = applySnapshot(inputConnection, update.snapshot)
                if (!success || update.snapshot.renderedText.isEmpty()) {
                    safely { inputConnection.finishComposingText() }
                    resetWithoutEditorMutation()
                    onCompositionFinished(CompositionFinishReason.BOUNDARY)
                }
                success
            }
        }
    }

    fun clearCurrentRun(inputConnection: InputConnection?): Boolean {
        if (!isActive) return false
        val cleared = safely { inputConnection?.setComposingText("", 1) ?: false }
        safely { inputConnection?.finishComposingText() ?: false }
        resetWithoutEditorMutation()
        onCompositionFinished(CompositionFinishReason.TRACKPAD_DELETE_WORD)
        return cleared
    }

    fun finish(inputConnection: InputConnection?, reason: CompositionFinishReason): Boolean {
        if (!isActive) return false
        val result = safely { inputConnection?.finishComposingText() ?: false }
        resetWithoutEditorMutation()
        onCompositionFinished(reason)
        return result
    }

    private fun clearTracking() {
        expectations.clear()
        acknowledgedExpectations.clear()
        composingStart = null
        nativeRangeObserved = false
        lastAcknowledgedLength = null
    }

    fun resetWithoutEditorMutation() {
        composer?.reset()
        clearTracking()
    }

    fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ): SelectionUpdateDisposition {
        if (!isActive) return SelectionUpdateDisposition.NOT_TRACKED
        val hasRange = candidatesStart >= 0 && candidatesEnd >= candidatesStart
        if (newSelStart != newSelEnd || (!hasRange && nativeRangeObserved)) {
            return externalChange()
        }
        if (!hasRange && composingStart == null && oldSelStart == newSelStart && oldSelEnd == newSelEnd) {
            return externalChange()
        }
        val start = composingStart ?: if (hasRange) candidatesStart else minOf(oldSelStart, oldSelEnd)
        // Match final positions, not each individual write's relative cursor delta.
        // The newest matching generation acknowledges all coalesced earlier writes.
        val match = expectations.lastOrNull {
            it.editorSessionId == editorSessionId &&
                newSelStart == start + it.composingLength &&
                (!hasRange || (candidatesStart == start && candidatesEnd == newSelStart))
        }
        if (match != null) {
            composingStart = start
            nativeRangeObserved = nativeRangeObserved || hasRange
            lastAcknowledgedLength = match.composingLength
            while (expectations.firstOrNull()?.let { it.editGeneration <= match.editGeneration } == true) {
                acknowledgedExpectations.addLast(expectations.removeFirst())
                while (acknowledgedExpectations.size > 16) acknowledgedExpectations.removeFirst()
            }
            return SelectionUpdateDisposition.OWN_COMPOSING_UPDATE
        }
        if (hasRange && candidatesStart == start && candidatesEnd == newSelStart &&
            (newSelStart == start + (lastAcknowledgedLength ?: -1) ||
                acknowledgedExpectations.any { start + it.composingLength == newSelStart })) {
            return SelectionUpdateDisposition.OWN_COMPOSING_UPDATE
        }
        if (hasRange || oldSelStart != newSelStart || oldSelEnd != newSelEnd) {
            return externalChange()
        }
        return SelectionUpdateDisposition.NOT_TRACKED
    }

    private fun externalChange(): SelectionUpdateDisposition {
        resetWithoutEditorMutation()
        onCompositionFinished(CompositionFinishReason.SELECTION_CHANGE)
        return SelectionUpdateDisposition.EXTERNAL_CHANGE
    }

    private fun applySnapshot(ic: InputConnection, snapshot: CompositionSnapshot): Boolean {
        val expectation = SelectionExpectation(editorSessionId, ++editGeneration, snapshot.renderedText.length)
        expectations.addLast(expectation)
        while (expectations.size > 128) expectations.removeFirst()
        val success = try {
            ic.beginBatchEdit()
            ic.setComposingText(snapshot.renderedText, 1)
        } catch (_: Exception) {
            false
        } finally {
            safely { ic.endBatchEdit() }
        }
        if (!success) expectations.remove(expectation)
        return success
    }

    private fun readSelectionStart(ic: InputConnection): Int? = try {
        ic.getExtractedText(ExtractedTextRequest(), 0)?.let {
            minOf(it.selectionStart, it.selectionEnd).takeIf { position -> position >= 0 }?.plus(it.startOffset)
        }
    } catch (_: Exception) { null }

    private inline fun safely(action: () -> Boolean): Boolean = try { action() } catch (_: Exception) { false }
}

internal data class SelectionExpectation(
    val editorSessionId: Long,
    val editGeneration: Long,
    val composingLength: Int
)
