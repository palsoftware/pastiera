package it.palsoftware.pastiera.core.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SuggestionEngineTest {

    private val locale = Locale.ITALIAN
    private val fakeRepo = FakeDictionaryRepository()
    private val engine = SuggestionEngine(fakeRepo, locale = locale)

    @Test
    fun testIsReadyCheck() {
        fakeRepo.addTestEntry("hallo", 100)
        fakeRepo.isReady = false
        
        val results = engine.suggest("hallo")
        assertTrue("Sollte leere Liste zurückgeben, wenn Repo nicht bereit ist", results.isEmpty())
        
        fakeRepo.isReady = true
        val resultsReady = engine.suggest("hall") // Prefix search
        assertTrue("Sollte Ergebnisse liefern, wenn Repo bereit ist", resultsReady.isNotEmpty())
    }

    @Test
    fun testKeyboardProximity_QWERTY() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("hallo", 200)
        engine.setKeyboardLayout("qwerty")

        // 'hsllo' (S ist neben A -> Distanz 1.0)
        val resultsNear = engine.suggest("hsllo")
        val scoreNear = resultsNear.find { it.candidate == "hallo" }?.score ?: 0.0

        // 'hmllo' (M ist weit weg von A -> Distanz ~8.0)
        val resultsFar = engine.suggest("hmllo")
        val scoreFar = resultsFar.find { it.candidate == "hallo" }?.score ?: 0.0

        assertTrue("Nahgelegene Taste ($scoreNear) sollte höheren Score haben als ferne Taste ($scoreFar)", scoreNear > scoreFar)
    }

    @Test
    fun testProximityFiltering() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("hallo", 200)
        engine.setKeyboardLayout("qwerty")

        // 'hmllo' -> 'hallo' ist eine Substitution von 'm' für 'a'. 
        // Distanz ist > 2.5, sollte also gefiltert werden, wenn proximity aktiv ist.
        val results = engine.suggest("hmllo", useKeyboardProximity = true)
        assertTrue("Sollte weit entfernte Substitution 'm' -> 'a' filtern", results.none { it.candidate == "hallo" })
        
        // Ohne Proximity-Filterung sollte es gefunden werden
        val resultsNoFilter = engine.suggest("hmllo", useKeyboardProximity = false)
        assertTrue("Sollte ohne Proximity-Filterung gefunden werden", resultsNoFilter.any { it.candidate == "hallo" })
    }

    @Test
    fun testAccentMatching() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("perché", 200)
        
        // User tippt "perche" (ohne Akzent)
        val results = engine.suggest("perche", includeAccentMatching = true)
        assertEquals("Sollte 'perché' als Top-Vorschlag finden", "perché", results.firstOrNull()?.candidate)
    }

    @Test
    fun testUserDictionaryRanking() {
        fakeRepo.isReady = true
        // "hallo" im Hauptwörterbuch
        fakeRepo.addTestEntry("hallo", 100, SuggestionSource.MAIN)
        // "hallx" im User-Wörterbuch
        fakeRepo.addTestEntry("hallx", 100, SuggestionSource.USER)
        
        val results = engine.suggest("hall")
        
        assertEquals("User-Wort sollte an erster Stelle stehen", "hallx", results.firstOrNull()?.candidate)
        assertEquals("User-Wort sollte als USER gekennzeichnet sein", SuggestionSource.USER, results.first().source)
    }

    @Test
    fun testDynamicLayoutSwitch() {
        fakeRepo.isReady = true
        fakeRepo.addTestEntry("apple", 200)
        
        // QWERTY: A is (1,0), Q is (0,0) -> Distance 1.0 (Nearby)
        engine.setKeyboardLayout("qwerty")
        val resultsQwerty = engine.suggest("qpple")
        assertTrue("QWERTY: qpple sollte apple finden (Q neben A)", resultsQwerty.any { it.candidate == "apple" })

        // AZERTY: A is (0,0), Q is (1,0) -> Hier sind A und Q auch nebeneinander, aber vertauscht.
        // Let's take 'q' and 'w'.
        // QWERTY: Q(0,0), W(0,1) -> Distance 1.0
        // AZERTY: A(0,0), Z(0,1) -> Q is at (1,0), W is at (0,1) -> Distance sqrt(1^2 + 1^2) = 1.41
        
        fakeRepo.addTestEntry("queen", 200)
        
        // QWERTY: 'w' statt 'q' -> 'ween' -> 'queen'
        engine.setKeyboardLayout("qwerty")
        val scoreQwerty = engine.suggest("ween").find { it.candidate == "queen" }?.score ?: 0.0
        
        // AZERTY: 'w' (0,1) ist weit weg von 'a' (0,0), aber 'q' ist bei AZERTY 'a'.
        // In AZERTY mapping: "KEYCODE_Q" -> 'a', "KEYCODE_W" -> 'z', "KEYCODE_A" -> 'q'
        // Physical Key Q (0,0) -> 'a'
        // Physical Key W (0,1) -> 'z'
        // Physical Key A (1,0) -> 'q'
        // So in AZERTY, 'a' (0,0) and 'z' (0,1) are neighbors.
        // 'q' (1,0) and 'a' (0,0) are neighbors.
        
        engine.setKeyboardLayout("azerty")
        // 'a' statt 'q' -> 'aueen' (in AZERTY ist 'a' an Position (0,0), 'q' an Position (1,0))
        val resultsAzerty = engine.suggest("aueen")
        assertTrue("AZERTY: aueen sollte queen finden", resultsAzerty.any { it.candidate == "queen" })
    }

    @Test
    fun testDynamicDownloadSimulation() {
        fakeRepo.isReady = true
        
        // Zuerst leer
        assertTrue(engine.suggest("hallo").isEmpty())
        
        // Dynamisch "runterladen"
        fakeRepo.addTestEntry("hallo", 200)
        
        val results = engine.suggest("hall")
        assertEquals(1, results.size)
        assertEquals("hallo", results[0].candidate)
    }
}

