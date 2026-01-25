package it.palsoftware.pastiera.core.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymSpellTest {

    @Test
    fun testExactMatch() {
        val symSpell = SymSpell()
        symSpell.addWord("hallo", 100)
        
        val results = symSpell.lookup("hallo")
        assertEquals(1, results.size)
        assertEquals("hallo", results[0].term)
        assertEquals(0, results[0].distance)
        assertEquals(100, results[0].frequency)
    }

    @Test
    fun testBasicTypo_Substitution() {
        val symSpell = SymSpell()
        symSpell.addWord("hallo", 100)
        
        // 'hxllo' (Substitution: a -> x)
        val results = symSpell.lookup("hxllo")
        assertTrue(results.any { it.term == "hallo" && it.distance == 1 })
    }

    @Test
    fun testBasicTypo_Deletion() {
        val symSpell = SymSpell()
        symSpell.addWord("hallo", 100)
        
        // 'hllo' (Deletion: a)
        val results = symSpell.lookup("hllo")
        assertTrue(results.any { it.term == "hallo" && it.distance == 1 })
    }

    @Test
    fun testBasicTypo_Insertion() {
        val symSpell = SymSpell()
        symSpell.addWord("hallo", 100)
        
        // 'haallo' (Insertion: a)
        val results = symSpell.lookup("haallo")
        assertTrue(results.any { it.term == "hallo" && it.distance == 1 })
    }

    @Test
    fun testFrequencyRanking() {
        val symSpell = SymSpell()
        symSpell.addWord("hallo", 10)
        symSpell.addWord("halle", 100)
        
        // 'hallx' is distance 1 to both. 'halle' has higher frequency.
        val results = symSpell.lookup("hallx")
        assertEquals(2, results.size)
        assertEquals("halle", results[0].term)
        assertEquals("hallo", results[1].term)
    }

    @Test
    fun testDamerauLevenshtein_Transposition() {
        val symSpell = SymSpell()
        symSpell.addWord("hallo", 100)
        
        // 'halol' (Transposition: l and o)
        val results = symSpell.lookup("halol")
        assertTrue("Sollte 'hallo' finden via Transposition", results.any { it.term == "hallo" && it.distance == 1 })
    }

    @Test
    fun testPrefixLogic_LongWords() {
        // Default prefixLength is 7
        val symSpell = SymSpell(prefixLength = 7)
        val longWord = "donaudampfschiff"
        symSpell.addWord(longWord, 100)
        
        // Tippfehler im Suffix (außerhalb des Präfixes)
        // 'donaudampfschixf' -> 'donaudampfschiff'
        val typoSuffix = "donaudampfschixf"
        val resultsSuffix = symSpell.lookup(typoSuffix)
        assertTrue("Sollte Tippfehler im Suffix korrigieren", resultsSuffix.any { it.term == longWord })

        // Tippfehler im Präfix
        // 'donaxdampfschiff' -> 'donaudampfschiff'
        val typoPrefix = "donaxdampfschiff"
        val resultsPrefix = symSpell.lookup(typoPrefix)
        assertTrue("Sollte Tippfehler im Präfix korrigieren", resultsPrefix.any { it.term == longWord })
    }

    @Test
    fun testLoadSerialized() {
        val symSpell = SymSpell(maxEditDistance = 2, prefixLength = 7)
        
        // Simuliere serialisierte Daten
        val terms = mapOf("apfel" to 50, "birne" to 40)
        val deletesMap = mapOf(
            "apfel" to listOf("apfel"),
            "apfe" to listOf("apfel"),
            "apfl" to listOf("apfel"),
            "birne" to listOf("birne"),
            "birn" to listOf("birne")
        )
        
        symSpell.loadSerialized(terms, deletesMap)
        
        // Prüfe ob 'apfel' gefunden wird (exakt)
        val exact = symSpell.lookup("apfel")
        assertEquals(1, exact.size)
        assertEquals("apfel", exact[0].term)
        
        // Prüfe ob 'apfe' korrigiert wird (Distanz 1)
        val typo = symSpell.lookup("apfl")
        assertTrue(typo.any { it.term == "apfel" })
        
        // Prüfe ob 'birne' existiert
        assertTrue(symSpell.lookup("birne").any { it.term == "birne" })
    }

    @Test
    fun testEmptyInput() {
        val symSpell = SymSpell()
        symSpell.addWord("hallo", 100)
        
        assertTrue(symSpell.lookup("").isEmpty())
    }

    @Test
    fun testMaxEditDistance() {
        val symSpell = SymSpell(maxEditDistance = 2)
        symSpell.addWord("hallo", 100)
        
        // Distanz 2: 'hxllo' -> 'hallo' (Substitution 1), 'hxxlo' -> 'hallo' (Substitution 2)
        assertTrue(symSpell.lookup("hxxlo").any { it.term == "hallo" && it.distance == 2 })
        
        // Distanz 3: 'hxxxo' -> 'hallo' (Sollte nicht gefunden werden bei max=2)
        assertTrue(symSpell.lookup("hxxxo").none { it.term == "hallo" })
    }

    @Test
    fun testShortWords() {
        val symSpell = SymSpell(maxEditDistance = 2)
        symSpell.addWord("io", 100)
        symSpell.addWord("il", 100)
        
        // 'i' should find 'io' and 'il' (distance 1)
        val results = symSpell.lookup("i")
        assertTrue(results.any { it.term == "io" })
        assertTrue(results.any { it.term == "il" })
    }
}

