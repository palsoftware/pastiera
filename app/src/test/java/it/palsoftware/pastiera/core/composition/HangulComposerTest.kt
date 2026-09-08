package it.palsoftware.pastiera.core.composition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HangulComposerTest {
    private fun compose(text: String): HangulComposer {
        val composer = HangulComposer()
        text.forEach { composer.accept(NormalizedCompositionStroke(it)) }
        return composer
    }

    private fun rendered(text: String): String = compose(text).snapshot?.renderedText.orEmpty()

    @Test fun basicWords() {
        assertEquals("ㄱ", rendered("ㄱ"))
        assertEquals("가", rendered("ㄱㅏ"))
        assertEquals("간", rendered("ㄱㅏㄴ"))
        assertEquals("한글", rendered("ㅎㅏㄴㄱㅡㄹ"))
        assertEquals("안녕하세요", rendered("ㅇㅏㄴㄴㅕㅇㅎㅏㅅㅔㅇㅛ"))
        assertEquals("과", rendered("ㄱㅗㅏ"))
        assertEquals("의", rendered("ㅇㅡㅣ"))
        assertEquals("값", rendered("ㄱㅏㅂㅅ"))
        assertEquals("갑사", rendered("ㄱㅏㅂㅅㅏ"))
        assertEquals("가나", rendered("ㄱㅏㄴㅏ"))
    }

    @Test fun noImplicitIeungOrDoubleConsonant() {
        assertEquals("ㅏ", rendered("ㅏ"))
        assertEquals("ㅏㅓ", rendered("ㅏㅓ"))
        assertEquals("ㄱㄱ", rendered("ㄱㄱ"))
        assertEquals("가ㄸ", rendered("ㄱㅏㄸ"))
    }

    @Test fun backspaceReplaysPhysicalStrokes() {
        val composer = compose("ㄱㅏㅂㅅ")
        val expected = listOf("갑", "가", "ㄱ", "")
        expected.forEach { value ->
            assertTrue(composer.isActive)
            assertEquals(value, (composer.backspace() as ComposerUpdate.Replace).snapshot.renderedText)
        }
        assertEquals(ComposerUpdate.NotHandled, composer.backspace())
    }

    @Test fun splitFinalAndMoveToNextSyllable() {
        assertEquals("고", rendered("ㄱㅗㅏ".dropLast(1)))
        assertEquals("한그", rendered("ㅎㅏㄴㄱㅡ"))
        assertEquals("달가", rendered("ㄷㅏㄹㄱㅏ"))
        assertEquals("값", rendered("ㄱㅏㅂㅅ"))
    }

    @Test fun shiftedJamoAreSingleStrokes() {
        val composer = compose("ㄲㅏ")
        assertEquals("까", composer.snapshot?.renderedText)
        assertEquals("ㄲ", (composer.backspace() as ComposerUpdate.Replace).snapshot.renderedText)
    }

    @Test fun unsupportedInputIsNotHandled() {
        val composer = HangulComposer()
        assertEquals(ComposerUpdate.NotHandled, composer.accept(NormalizedCompositionStroke('a')))
        assertEquals(0, composer.strokeCount)
    }

    @Test fun boundedRunSignalsInternalSplitWithoutDroppingState() {
        val composer = HangulComposer(maxStrokes = 2)
        composer.accept(NormalizedCompositionStroke('ㄱ'))
        composer.accept(NormalizedCompositionStroke('ㅏ'))
        assertTrue(composer.needsInternalSplit)
        assertEquals(ComposerUpdate.NotHandled, composer.accept(NormalizedCompositionStroke('ㄴ')))
        composer.beginNewRun()
        assertEquals("ㄴ", (composer.accept(NormalizedCompositionStroke('ㄴ')) as ComposerUpdate.Replace).snapshot.renderedText)
    }
}
