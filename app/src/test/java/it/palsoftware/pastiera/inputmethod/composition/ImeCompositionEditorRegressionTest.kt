package it.palsoftware.pastiera.inputmethod.composition

import android.text.Selection
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import it.palsoftware.pastiera.core.composition.NormalizedCompositionStroke
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImeCompositionEditorRegressionTest {
    private open class Editor : BaseInputConnection(View(RuntimeEnvironment.getApplication()), true) {
        var rejectComposing = false
        var rejectCommit = false
        var throwComposing = false
        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (throwComposing) throw IllegalStateException("Editor disconnected")
            return !rejectComposing && super.setComposingText(text, newCursorPosition)
        }
        override fun commitText(text: CharSequence?, newCursorPosition: Int) =
            !rejectCommit && super.commitText(text, newCursorPosition)
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int) = ExtractedText().apply {
            text = editable.toString()
            startOffset = 0
            selectionStart = Selection.getSelectionStart(editable)
            selectionEnd = Selection.getSelectionEnd(editable)
        }
    }
    private fun coordinator() = ImeCompositionCoordinator().apply {
        startEditorSession()
        selectLayout("korean_2set")
    }
    private fun type(c: ImeCompositionCoordinator, e: BaseInputConnection, text: String) {
        text.forEach { assertTrue(c.handleMappedStroke(e, NormalizedCompositionStroke(it))) }
    }
    private fun acknowledge(c: ImeCompositionCoordinator, e: BaseInputConnection, old: Int): SelectionUpdateDisposition {
        val cursor = Selection.getSelectionStart(e.editable)
        return c.onUpdateSelection(old, old, cursor, cursor,
            BaseInputConnection.getComposingSpanStart(e.editable!!),
            BaseInputConnection.getComposingSpanEnd(e.editable!!))
    }

    @Test fun finalDeletePreservesCommittedPrefixAndEndsOwnership() {
        val e = Editor(); val c = coordinator()
        e.commitText("prefix ", 1)
        type(c, e, "ㄱㅏㅂㅅ")
        listOf("갑", "가", "ㄱ", "").forEach {
            assertTrue(c.handleBackspace(e))
            assertEquals("prefix $it", e.editable.toString())
        }
        assertFalse(c.isActive)
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(e.editable!!))
        assertEquals(7, Selection.getSelectionStart(e.editable))
        assertFalse(c.handleBackspace(e))
    }

    @Test fun replacingSelectionAndCoalescingUpdatesKeepsNewSyllableOwned() {
        val e = Editor(); val c = coordinator()
        e.commitText("prefix replace tail", 1)
        e.setSelection(7, 14)
        type(c, e, "ㄱㅏㄴㅏ")
        assertEquals(SelectionUpdateDisposition.OWN_COMPOSING_UPDATE, c.onUpdateSelection(7,14,9,9,7,9))
        type(c, e, "ㄴ")
        assertEquals("prefix 가난 tail", e.editable.toString())
    }

    @Test fun lostRangeWithAndWithoutPendingWritesCannotReplayStaleRun() {
        for (pending in listOf(false, true)) {
            val e = Editor(); val c = coordinator()
            type(c, e, "ㄱ")
            assertEquals(SelectionUpdateDisposition.OWN_COMPOSING_UPDATE, acknowledge(c,e,0))
            type(c, e, "ㅏ")
            if (!pending) acknowledge(c,e,1)
            e.finishComposingText()
            assertEquals(SelectionUpdateDisposition.EXTERNAL_CHANGE, acknowledge(c,e,1))
            assertFalse(c.isActive)
            type(c, e, "ㄴ")
            assertEquals("가ㄴ", e.editable.toString())
            assertTrue(c.handleBackspace(e))
            assertEquals("가", e.editable.toString())
        }
    }

    @Test fun unexpectedRangeAtSameCursorReleasesOwnership() {
        val e = Editor(); val c = coordinator()
        type(c,e,"ㄱㅏㄴㅏ")
        acknowledge(c,e,0)
        e.setComposingRegion(1,2)
        assertEquals(SelectionUpdateDisposition.EXTERNAL_CHANGE, acknowledge(c,e,2))
        assertFalse(c.isActive)
    }

    @Test fun editorWithoutCandidateRangesCanAcknowledgeAbsoluteSelection() {
        val e = Editor(); val c = coordinator()
        type(c,e,"ㄱㅏㄴㅏ")
        assertEquals(SelectionUpdateDisposition.OWN_COMPOSING_UPDATE,c.onUpdateSelection(0,0,2,2,-1,-1))
        type(c,e,"ㄴ")
        assertEquals("가난",e.editable.toString())
    }

    @Test fun rejectedComposingFallsBackOnEveryStrokeAndResetsInNewEditor() {
        val e = Editor().apply { rejectComposing = true }; val c = coordinator()
        var consumedShift = 0
        "ㄱㅏㄴ".forEach { assertTrue(c.handleMappedStroke(e,NormalizedCompositionStroke(it)) { consumedShift++ }) }
        assertEquals("ㄱㅏㄴ",e.editable.toString())
        assertEquals(3,consumedShift)
        assertFalse(c.isActive)
        c.startEditorSession(); c.selectLayout("korean_2set")
        val next = Editor()
        type(c,next,"ㄱㅏ")
        assertEquals("가",next.editable.toString())
        assertTrue(c.isActive)
    }

    @Test fun rejectedAndThrowingWritesDoNotConsumeKeyOrShiftWhenCommitAlsoFails() {
        for (throws in listOf(false,true)) {
            val e = Editor().apply { rejectComposing = true; rejectCommit = true; throwComposing = throws }
            val c = coordinator()
            var shiftConsumed = false
            assertFalse(c.handleMappedStroke(e,NormalizedCompositionStroke('ㄱ')) { shiftConsumed = true })
            assertFalse(shiftConsumed)
            assertFalse(c.isActive)
            assertEquals("",e.editable.toString())
        }
    }

    @Test fun failedReplacementCommitsOnlyNewStrokeAndKeepsPreviousText() {
        val e = Editor(); val c = coordinator()
        type(c,e,"ㄱㅏ")
        e.rejectComposing = true
        type(c,e,"ㄴ")
        assertEquals("가ㄴ",e.editable.toString())
        assertFalse(c.isActive)
    }

    @Test fun failedDeleteLeavesTextAndAllowsCallerFallback() {
        val e = Editor(); val c = coordinator()
        type(c,e,"ㄱ")
        e.rejectComposing = true
        assertFalse(c.handleBackspace(e))
        assertFalse(c.isActive)
        assertEquals("ㄱ",e.editable.toString())
    }

    @Test fun splitBoundariesPreserveCompoundVowelsFinalsAndCarryWithDeletion() {
        val tails = listOf("ㄱㅗㅏ", "ㄱㅏㅂㅅ", "ㄱㅏㅂㅅㅏ", "ㄱㅗㅏㄴ", "ㄱㅏㄴㅏ")
        for (padding in 123..128) for (tail in tails) {
            val e = Editor(); val c = coordinator()
            val prefix = "ㅏ".repeat(padding)
            type(c,e,prefix+tail)
            val reference = it.palsoftware.pastiera.core.composition.HangulComposer(maxStrokes = 1000)
            (prefix+tail).forEach { reference.accept(NormalizedCompositionStroke(it)) }
            assertEquals("padding=$padding tail=$tail", reference.snapshot?.renderedText,e.editable.toString())
            repeat(tail.length) {
                assertTrue(c.handleBackspace(e))
                reference.backspace()
                assertEquals(reference.snapshot?.renderedText,e.editable.toString())
            }
        }
    }

    @Test fun delayedAcknowledgedCallbackDoesNotInterruptNewerComposition() {
        val e = Editor(); val c = coordinator()
        type(c,e,"ㄱ")
        acknowledge(c,e,0)
        type(c,e,"ㅏㄴㅏ")
        acknowledge(c,e,1)
        assertEquals(SelectionUpdateDisposition.OWN_COMPOSING_UPDATE,
            c.onUpdateSelection(0,0,1,1,0,1))
        type(c,e,"ㄴ")
        assertEquals("가난",e.editable.toString())
    }

    @Test fun newEditorIgnoresOldCallbacksUntilItOwnsText() {
        val e = Editor(); val c = coordinator()
        type(c,e,"ㄱㅏ")
        c.startEditorSession(); c.selectLayout("korean_2set")
        assertEquals(SelectionUpdateDisposition.NOT_TRACKED,c.onUpdateSelection(0,0,1,1,0,1))
        assertFalse(c.isActive)
    }
}
