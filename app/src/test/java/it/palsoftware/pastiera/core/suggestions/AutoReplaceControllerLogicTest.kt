package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import it.palsoftware.pastiera.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import it.palsoftware.pastiera.inputmethod.AutoCorrector
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoReplaceControllerLogicTest {

    @Test
    fun testSplitApostropheWord() {
        // Italienische Elisionen
        // "l'amico" -> prefix="l'", root="amico"
        val split1 = AutoReplaceController.splitApostropheWord("l'amico")
        assertNotNull("l'amico sollte gesplittet werden", split1)
        assertEquals("l'", split1!!.prefix)
        assertEquals("amico", split1.root)

        // Zu kurz (root < 3)
        val split2 = AutoReplaceController.splitApostropheWord("l'a")
        assertNull("Root zu kurz, sollte null sein", split2)

        // Kein Apostroph
        val split3 = AutoReplaceController.splitApostropheWord("hallo")
        assertNull(split3)
        
        // Typographischer Apostroph
        val split4 = AutoReplaceController.splitApostropheWord("l’amico")
        assertNotNull("l’amico sollte gesplittet werden", split4)
        assertEquals("l'", split4!!.prefix)

        val split5 = AutoReplaceController.splitApostropheWord("dell'amico")
        assertNotNull("dell'amico sollte gesplittet werden", split5)
        assertEquals("dell'", split5!!.prefix)
        assertEquals("amico", split5.root)

        val split6 = AutoReplaceController.splitApostropheWord("nell'amico")
        assertNotNull("nell'amico sollte gesplittet werden", split6)
        assertEquals("nell'", split6!!.prefix)
        assertEquals("amico", split6.root)

        val split7 = AutoReplaceController.splitApostropheWord("rock'nroll")
        assertNull("Beliebige längere Präfixe sollten nicht als Elision behandelt werden", split7)
    }

    @Test
    fun testIsAccentOnlyVariant() {
        // "perche" vs "perché" -> true
        val res1 = AutoReplaceController.isAccentOnlyVariant("perche", "perché")
        assertEquals(true, res1)

        // Ligature variant should also be considered equivalent ("oeil" vs "œil")
        val resLigature = AutoReplaceController.isAccentOnlyVariant("oeil", "œil")
        assertEquals(true, resLigature)

        // "hallo" vs "halle" -> false (anderer Buchstabe)
        val res2 = AutoReplaceController.isAccentOnlyVariant("hallo", "halle")
        assertEquals(false, res2)
        
        // Identisch -> false (laut Implementierung)
        val res3 = AutoReplaceController.isAccentOnlyVariant("hallo", "hallo")
        assertEquals(false, res3)
    }

    @Test
    fun testStripAccents() {
        assertEquals("perche", AutoReplaceController.stripAccents("perché"))
        assertEquals("a", AutoReplaceController.stripAccents("á"))
        assertEquals("hallo", AutoReplaceController.stripAccents("hallo"))
    }

    @Test
    fun safeAutoReplaceRequiresCurrentWordEditDistanceCandidate() {
        val settings = SuggestionSettings(maxAutoReplaceDistance = 1)

        assertEquals(
            false,
            AutoReplaceController.isSafeAutoReplaceCandidate(
                input = "ich",
                lookupWord = "ich",
                candidate = SuggestionResult("bin", 0, 1.0, SuggestionSource.USER, SuggestionKind.NEXT_WORD),
                settings = settings,
                isOrthographicVariant = false
            )
        )

        assertEquals(
            false,
            AutoReplaceController.isSafeAutoReplaceCandidate(
                input = "hal",
                lookupWord = "hal",
                candidate = SuggestionResult("hallo", 0, 1.0, SuggestionSource.MAIN, SuggestionKind.CURRENT_WORD),
                settings = settings,
                isOrthographicVariant = false
            )
        )
    }

    @Test
    fun safeAutoReplaceAllowsRepeatedLetterInsertionButRejectsMorphology() {
        val settings = SuggestionSettings(maxAutoReplaceDistance = 1)

        assertEquals(
            true,
            AutoReplaceController.isSafeAutoReplaceCandidate(
                input = "wil",
                lookupWord = "wil",
                candidate = SuggestionResult("will", 1, 1.0, SuggestionSource.MAIN),
                settings = settings,
                isOrthographicVariant = false
            )
        )

        assertEquals(
            false,
            AutoReplaceController.isSafeAutoReplaceCandidate(
                input = "kaputte",
                lookupWord = "kaputte",
                candidate = SuggestionResult("kaputt", 1, 1.0, SuggestionSource.MAIN),
                settings = settings,
                isOrthographicVariant = false
            )
        )
    }

    @Test
    fun safeAutoReplaceRejectsAcronymCandidateForLowercaseInput() {
        val settings = SuggestionSettings(maxAutoReplaceDistance = 1)

        assertEquals(
            false,
            AutoReplaceController.isSafeAutoReplaceCandidate(
                input = "idk",
                lookupWord = "idk",
                candidate = SuggestionResult("IFK", 1, 1.0, SuggestionSource.MAIN),
                settings = settings,
                isOrthographicVariant = false
            )
        )
    }

    @Test
    fun safeAutoReplaceRejectsProperNounTypoForLowercaseInput() {
        val settings = SuggestionSettings(maxAutoReplaceDistance = 1)

        assertEquals(
            false,
            AutoReplaceController.isSafeAutoReplaceCandidate(
                input = "hallo",
                lookupWord = "hallo",
                candidate = SuggestionResult("Halle", 1, 1.0, SuggestionSource.MAIN),
                settings = settings,
                isOrthographicVariant = false
            )
        )
    }

    @Test
    fun safeAutoReplaceRejectsSameLengthFirstLetterSubstitution() {
        val settings = SuggestionSettings(maxAutoReplaceDistance = 1)

        assertEquals(
            false,
            AutoReplaceController.isSafeAutoReplaceCandidate(
                input = "ding",
                lookupWord = "ding",
                candidate = SuggestionResult("fing", 1, 1.0, SuggestionSource.MAIN),
                settings = settings,
                isOrthographicVariant = false
            )
        )

        assertEquals(
            true,
            AutoReplaceController.isSafeAutoReplaceCandidate(
                input = "fihg",
                lookupWord = "fihg",
                candidate = SuggestionResult("fing", 1, 1.0, SuggestionSource.MAIN),
                settings = settings,
                isOrthographicVariant = false
            )
        )
    }

    @Test
    fun safeAutoReplaceAllowsCaseOnlyVariant() {
        val settings = SuggestionSettings(maxAutoReplaceDistance = 1)

        assertEquals(true, AutoReplaceController.isCaseOnlyVariant("problem", "Problem"))
        assertEquals(
            true,
            AutoReplaceController.isSafeAutoReplaceCandidate(
                input = "problem",
                lookupWord = "problem",
                candidate = SuggestionResult("Problem", 0, 1.0, SuggestionSource.MAIN),
                settings = settings,
                isOrthographicVariant = false
            )
        )
    }

    @Test
    fun autoSubstitutionSkipsKnownWords() {
        AutoCorrector.loadCustomCorrections("de", """{"agree":"are"}""")

        val correction = AutoCorrector.processText(
            textBeforeCursor = "agree ",
            locale = "de",
            isKnownWord = { it.equals("agree", ignoreCase = true) }
        )

        assertNull(correction)
    }

    @Test
    fun customAutoSubstitutionOverridesKnownWordGuardAndPreservesCapitalization() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.saveCustomAutoCorrections(context, "fr", mapOf("ca" to "ça"))
        SettingsManager.setAutoCorrectEnabledLanguages(context, setOf("fr"))
        AutoCorrector.loadCorrections(context.assets, context)

        val correction = AutoCorrector.processText(
            textBeforeCursor = "Ca ",
            locale = "fr",
            context = context,
            isKnownWord = { it.equals("ca", ignoreCase = true) }
        )

        assertNotNull(correction)
        assertEquals("Ca", correction!!.first)
        assertEquals("Ça", correction.second)
    }

    @Test
    fun customStandardLanguageCorrectionDoesNotRemoveBundledDefaults() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.saveCustomAutoCorrections(context, "en", mapOf("teh" to "the"))
        SettingsManager.setAutoCorrectEnabledLanguages(context, setOf("en"))
        AutoCorrector.loadCorrections(context.assets, context)

        val correction = AutoCorrector.processText(
            textBeforeCursor = "Dont ",
            locale = "en",
            context = context,
            isKnownWord = { false }
        )

        assertNotNull(correction)
        assertEquals("Dont", correction!!.first)
        assertEquals("Don't", correction.second)
    }

    @Test
    fun bundledAutoSubstitutionOverridesKnownWordGuard() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setAutoCorrectEnabledLanguages(context, setOf("en", "fr"))
        AutoCorrector.loadCorrections(context.assets, context)

        val dontCorrection = AutoCorrector.processText(
            textBeforeCursor = "Dont ",
            locale = "de",
            context = context,
            isKnownWord = { true }
        )
        val caCorrection = AutoCorrector.processText(
            textBeforeCursor = "ca ",
            locale = "de",
            context = context,
            isKnownWord = { true }
        )

        assertNotNull(dontCorrection)
        assertEquals("Dont", dontCorrection!!.first)
        assertEquals("Don't", dontCorrection.second)
        assertNotNull(caCorrection)
        assertEquals("ca", caCorrection!!.first)
        assertEquals("ça", caCorrection.second)
    }

    @Test
    fun bundledFrenchAccentSubstitutionsCoverApostropheKnownWordsAndCommonAccents() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setAutoCorrectEnabledLanguages(context, setOf("fr"))
        AutoCorrector.loadCorrections(context.assets, context)

        val examples = mapOf(
            "jespere" to "j'espère",
            "paraitre" to "paraître",
            "epee" to "épée",
            "etre" to "être",
            "tres" to "très",
            "francais" to "français",
            "ecole" to "école",
            "probleme" to "problème",
            "Noel" to "Noël",
            "jai" to "j'ai",
            "daccord" to "d'accord",
            "quon" to "qu'on",
            "quelquun" to "quelqu'un",
            "aujourdhui" to "aujourd'hui",
            "quil" to "qu'il",
            "jusqua" to "jusqu'à",
            "lequipe" to "l'équipe",
            "premiere" to "première",
            "pres" to "près",
            "lhistoire" to "l'histoire",
            "detre" to "d'être",
            "sagit" to "s'agit",
            "cestadire" to "c'est-à-dire",
            "luimeme" to "lui-même",
            "recoit" to "reçoit",
            "theatre" to "théâtre",
            "leau" to "l'eau"
        )

        examples.forEach { (input, expected) ->
            val correction = AutoCorrector.processText(
                textBeforeCursor = "$input ",
                locale = "fr",
                context = context,
                isKnownWord = { it.equals(input, ignoreCase = true) }
            )

            assertNotNull("$input should be corrected", correction)
            assertEquals(input, correction!!.first)
            assertEquals(expected, correction.second)
        }
    }

    @Test
    fun exactReplacementRunsBeforeSuggestionSafetyChecks() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("da", 255)
            addTestEntry("ca", 50)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = true,
                    maxAutoReplaceDistance = 1
                )
            },
            exactReplacementProvider = { word, _ ->
                if (word == "Ca") "Ça" else null
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("Ca")
        val inputConnection = FakeInputConnection(context, "Ca")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertTrue(result.replaced)
        assertEquals("Ça ", inputConnection.text)
        assertEquals("Ça", result.replacement)
    }

    @Test
    fun exactReplacementRunsEvenWhenFuzzyAutoReplaceIsDisabled() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = false,
                    maxAutoReplaceDistance = 1
                )
            },
            exactReplacementProvider = { word, _ ->
                if (word == "tssst") "totallynewsubstitution" else null
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("tssst")
        val inputConnection = FakeInputConnection(context, "tssst")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertTrue(result.replaced)
        assertEquals("totallynewsubstitution ", inputConnection.text)
        assertEquals("totallynewsubstitution", result.replacement)
    }

    @Test
    fun exactReplacementSupportsApostropheWords() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = false,
                    maxAutoReplaceDistance = 1
                )
            },
            exactReplacementProvider = { word, _ ->
                if (word == "qu'on") "qu'on" else null
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("qu'on")
        val inputConnection = FakeInputConnection(context, "qu'on")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertTrue(result.replaced)
        assertEquals("qu'on ", inputConnection.text)
        assertEquals("qu'on", result.replacement)
    }

    @Test
    fun autoReplaceOnSpaceAppliesAccentOnlyDictionaryVariant() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("derrière", 200)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository, locale = Locale.FRENCH),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = true,
                    accentMatching = true,
                    maxAutoReplaceDistance = 1
                )
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("derriere")
        val inputConnection = FakeInputConnection(context, "derriere")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertTrue(result.replaced)
        assertEquals("derrière ", inputConnection.text)
        assertEquals("derrière", result.replacement)
    }

    @Test
    fun boundaryCharOverrideAutoReplacesBeforeEveryRequestedPunctuation() {
        val context = RuntimeEnvironment.getApplication()
        val punctuation = listOf('?', '!', ':', ',', '.', ';')

        punctuation.forEach { boundary ->
            val repository = FakeDictionaryRepository().apply {
                isReady = true
                addTestEntry("hello", 200)
            }
            val controller = AutoReplaceController(
                repository = repository,
                suggestionEngine = SuggestionEngine(repository),
                settingsProvider = {
                    SuggestionSettings(
                        autoReplaceOnSpaceEnter = true,
                        maxAutoReplaceDistance = 1
                    )
                }
            )
            val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
            tracker.setWord("hellp")
            val inputConnection = FakeInputConnection(context, "hellp")

            val result = controller.handleBoundary(
                keyCode = KeyEvent.KEYCODE_UNKNOWN,
                event = null,
                tracker = tracker,
                inputConnection = inputConnection,
                boundaryCharOverride = boundary
            )

            assertTrue("Expected replacement before '$boundary'", result.replaced)
            assertEquals("hello$boundary", inputConnection.text)
            assertEquals("hello", result.replacement)
        }
    }

    @Test
    fun boundaryCommitAddsFrenchSpacingWhenNoReplacementHappens() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("bonjour", 100)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = false,
                    frenchPunctuationSpacing = true
                )
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("bonjour")
        val inputConnection = FakeInputConnection(context, "bonjour")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SEMICOLON,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SEMICOLON),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertFalse(result.replaced)
        assertTrue(result.committed)
        assertEquals("bonjour ;", inputConnection.text)
    }

    @Test
    fun boundaryCommitAddsCommaSpaceWhenNoReplacementHappens() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("hello", 100)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = false,
                    commaSpace = true
                )
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("hello")
        val inputConnection = FakeInputConnection(context, "hello")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_COMMA,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_COMMA),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertFalse(result.replaced)
        assertTrue(result.committed)
        assertEquals("hello, ", inputConnection.text)
    }

    @Test
    fun softwareSpaceBoundaryReportsCommittedWhenNoReplacementHappens() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = false,
                    maxAutoReplaceDistance = 1
                )
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("hello")
        val inputConnection = FakeInputConnection(context, "hello")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = null,
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertEquals(false, result.replaced)
        assertTrue(result.committed)
        assertEquals("hello ", inputConnection.text)
    }

    @Test
    fun exactReplacementAllowsTwoLetterCustomSubstitutionWithApostropheBeforeDictionaryCaseCandidate() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.saveCustomAutoCorrections(context, "fr", mapOf("ct" to "c'était"))
        SettingsManager.setAutoCorrectEnabledLanguages(context, setOf("fr"))
        AutoCorrector.loadCorrections(context.assets, context)
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("CT", 255)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = true,
                    maxAutoReplaceDistance = 1
                )
            },
            knownWordProvider = { word -> word.equals("ct", ignoreCase = true) },
            exactReplacementProvider = { word, boundaryChar ->
                AutoCorrector.processText(
                    textBeforeCursor = word + (boundaryChar ?: ' '),
                    locale = "fr",
                    context = context,
                    isKnownWord = { candidate -> candidate.equals("ct", ignoreCase = true) }
                )?.takeIf { (original, replacement) ->
                    original == word && replacement != word
                }?.second
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("Ct")
        val inputConnection = FakeInputConnection(context, "Ct")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertTrue(result.replaced)
        assertEquals("C'était ", inputConnection.text)
        assertEquals("C'était", result.replacement)
    }

    @Test
    fun activeDictionaryKnownWordProviderPreventsFuzzyReplacement() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("trat", 255)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = true,
                    maxAutoReplaceDistance = 1
                )
            },
            knownWordProvider = { word -> word.equals("that", ignoreCase = true) }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("that")
        val inputConnection = FakeInputConnection(context, "that")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertEquals(false, result.replaced)
        assertEquals("that ", inputConnection.text)
    }

    @Test
    fun autoReplaceDoesNotCommitWhenFinalReplacementEqualsOriginalWord() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("und", 255)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = true,
                    maxAutoReplaceDistance = 1
                )
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("Und")
        val inputConnection = FakeInputConnection(context, "Und")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertEquals(false, result.replaced)
        assertEquals("Und ", inputConnection.text)
    }

    @Test
    fun primaryDictionaryCaseVariantRunsBeforeMultiDictionaryKnownWordGuard() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("Problem", 220)
            addTestEntry("problemlos", 255)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = true,
                    maxAutoReplaceDistance = 1
                )
            },
            knownWordProvider = { word -> word.equals("problem", ignoreCase = true) }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("problem")
        val inputConnection = FakeInputConnection(context, "problem")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertTrue(result.replaced)
        assertEquals("Problem ", inputConnection.text)
        assertEquals("Problem", result.replacement)
    }

    @Test
    fun primaryDictionaryCaseVariantDoesNotReplaceWhenExactLowercaseExists() {
        val context = RuntimeEnvironment.getApplication()
        val repository = FakeDictionaryRepository().apply {
            isReady = true
            addTestEntry("und", 255)
            addTestEntry("Und", 200)
        }
        val controller = AutoReplaceController(
            repository = repository,
            suggestionEngine = SuggestionEngine(repository),
            settingsProvider = {
                SuggestionSettings(
                    autoReplaceOnSpaceEnter = true,
                    maxAutoReplaceDistance = 1
                )
            }
        )
        val tracker = CurrentWordTracker(onWordChanged = {}, onWordReset = {})
        tracker.setWord("und")
        val inputConnection = FakeInputConnection(context, "und")

        val result = controller.handleBoundary(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            tracker = tracker,
            inputConnection = inputConnection
        )

        assertEquals(false, result.replaced)
        assertEquals("und ", inputConnection.text)
    }

    private class FakeInputConnection(
        context: Context,
        initialText: String
    ) : BaseInputConnection(View(context), true) {
        private val buffer = StringBuilder(initialText)

        val text: String
            get() = buffer.toString()

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
            return buffer.takeLast(n)
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val deleteStart = (buffer.length - beforeLength).coerceAtLeast(0)
            buffer.delete(deleteStart, buffer.length)
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            buffer.append(text ?: "")
            return true
        }

        override fun beginBatchEdit(): Boolean = true

        override fun endBatchEdit(): Boolean = true
    }
}
