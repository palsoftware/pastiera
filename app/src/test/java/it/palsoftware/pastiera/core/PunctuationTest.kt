package it.palsoftware.pastiera.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PunctuationTest {

    @Test
    fun testIsWordBoundary_Whitespace() {
        assertTrue(Punctuation.isWordBoundary(' '))
        assertTrue(Punctuation.isWordBoundary('\n'))
        assertTrue(Punctuation.isWordBoundary('\t'))
    }

    @Test
    fun testIsWordBoundary_StandardPunctuation() {
        assertTrue(Punctuation.isWordBoundary('.'))
        assertTrue(Punctuation.isWordBoundary(','))
        assertTrue(Punctuation.isWordBoundary('!'))
        assertTrue(Punctuation.isWordBoundary('?'))
        assertTrue(Punctuation.isWordBoundary(';'))
        assertTrue(Punctuation.isWordBoundary(':'))
    }

    @Test
    fun testIsWordBoundary_LettersAndDigits() {
        assertFalse(Punctuation.isWordBoundary('a'))
        assertFalse(Punctuation.isWordBoundary('Z'))
        assertFalse(Punctuation.isWordBoundary('5'))
        assertFalse(Punctuation.isWordBoundary('ü')) // Unicode letters
    }

    @Test
    fun testIsWordBoundary_ApostropheLogic() {
        // Apostroph in der Mitte eines Wortes (z.B. l'amico)
        // ch='\'', prev='l' -> prevIsWord=true -> returns false (kein Boundary)
        assertFalse("Apostroph nach einem Buchstaben sollte kein Boundary sein", 
            Punctuation.isWordBoundary('\'', prev = 'l'))

        // Apostroph am Anfang eines Wortes (z.B. 'hallo)
        // ch='\'', prev=' ' -> prevIsWord=false -> returns true (Boundary)
        assertTrue("Apostroph nach Leerzeichen sollte ein Boundary sein", 
            Punctuation.isWordBoundary('\'', prev = ' '))
        
        // Verschiedene Apostroph-Typen
        assertFalse("Typographischer Apostroph sollte wie Standard behandelt werden",
            Punctuation.isWordBoundary('’', prev = 'd'))
    }

    @Test
    fun testIsWordBoundary_BracketsAndSymbols() {
        assertTrue(Punctuation.isWordBoundary('('))
        assertTrue(Punctuation.isWordBoundary(']'))
        assertTrue(Punctuation.isWordBoundary('/'))
        assertTrue(Punctuation.isWordBoundary('\\'))
        assertTrue(Punctuation.isWordBoundary('"'))
    }
}

