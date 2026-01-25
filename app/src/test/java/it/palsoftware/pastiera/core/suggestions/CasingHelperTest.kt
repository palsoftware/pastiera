package it.palsoftware.pastiera.core.suggestions

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CasingHelperTest {

    @Test
    fun testApplyCasing_StandardTransformations() {
        // Kleinschreibung beibehalten
        assertEquals("apple", CasingHelper.applyCasing("apple", "app"))
        
        // Title Case übernehmen
        assertEquals("Apple", CasingHelper.applyCasing("apple", "App"))
        
        // All Caps übernehmen
        assertEquals("APPLE", CasingHelper.applyCasing("apple", "APP"))
    }

    @Test
    fun testApplyCasing_DictionaryPriority() {
        // Binnengroßschreibung im Wörterbuch muss erhalten bleiben (z.B. McCartney)
        // Wenn der User klein schreibt, aber das Dict ein spezielles Casing hat
        assertEquals("McCartney", CasingHelper.applyCasing("McCartney", "mcc"))
        
        // Spezialfälle wie iPhone
        assertEquals("iPhone", CasingHelper.applyCasing("iPhone", "iph"))
        
        // Wenn der User aber ALLES groß schreibt, sollte auch das Dict-Wort groß werden
        assertEquals("MCCARTNEY", CasingHelper.applyCasing("McCartney", "MCC"))
    }

    @Test
    fun testApplyCasing_ForceLeadingCapital() {
        // Erster Buchstabe groß, auch wenn User klein schreibt (z.B. Satzanfang)
        assertEquals("Apple", CasingHelper.applyCasing("apple", "app", forceLeadingCapital = true))
        
        // Sollte auch bei bereits groß geschriebenen Wörterbuch-Einträgen funktionieren
        assertEquals("McCartney", CasingHelper.applyCasing("McCartney", "mcc", forceLeadingCapital = true))
    }

    @Test
    fun testApplyCasing_EdgeCases() {
        // Leere Strings
        assertEquals("", CasingHelper.applyCasing("", "abc"))
        assertEquals("apple", CasingHelper.applyCasing("apple", ""))
        
        // Keine Buchstaben (Zahlen/Symbole)
        assertEquals("123", CasingHelper.applyCasing("123", "12"))
        
        // Ein-Buchstaben-Wörter (Prüfung der allUpper Logik)
        assertEquals("A", CasingHelper.applyCasing("a", "A"))
        assertEquals("a", CasingHelper.applyCasing("a", "a"))
    }

    @Test
    fun testApplyCasing_PunctuationInOriginal() {
        // Apostrophe im Original sollten ignoriert werden für die Casing-Entscheidung
        // l'am -> l'amico (klein)
        assertEquals("l'amico", CasingHelper.applyCasing("l'amico", "l'am"))
        
        // L'am -> L'amico (groß)
        assertEquals("L'amico", CasingHelper.applyCasing("l'amico", "L'am"))
    }
}

