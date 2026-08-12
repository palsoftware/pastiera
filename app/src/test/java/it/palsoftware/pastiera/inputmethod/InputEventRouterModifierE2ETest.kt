package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.core.ModifierStateController
import it.palsoftware.pastiera.core.NavModeController
import it.palsoftware.pastiera.core.SymLayoutController
import it.palsoftware.pastiera.core.TextInputController
import it.palsoftware.pastiera.core.AutoSpaceTracker
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.SymPagesConfig
import it.palsoftware.pastiera.data.layout.LayoutMapping
import it.palsoftware.pastiera.data.layout.TapMapping
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InputEventRouterModifierE2ETest {

    private lateinit var context: Context
    private lateinit var modifierStateController: ModifierStateController
    private lateinit var router: InputEventRouter
    private lateinit var altSymManager: AltSymManager
    private lateinit var symLayoutController: SymLayoutController
    private lateinit var variationStateController: VariationStateController
    private lateinit var ctrlKeyMap: Map<Int, KeyMappingLoader.CtrlMapping>
    private lateinit var inputConnectionRecorder: RecordingInputConnection
    private lateinit var inputConnection: InputConnection
    private lateinit var prefs: android.content.SharedPreferences
    private var shiftLayerLatchedForTest: Boolean = false

    private val doubleTapThreshold = 300L

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SettingsManager.setSymPagesConfig(context, SymPagesConfig())
        SettingsManager.resetSymMappings(context)
        SettingsManager.resetSymMappingsPage2(context)
        SettingsManager.resetVariationsToDefault(context)
        SettingsManager.setLongPressModifier(context, "alt")
        SettingsManager.setPhysicalKeyboardCurrencySymbol(context, "€")
        SettingsManager.setAltLatchStaysOnSpace(context, false)
        SettingsManager.setFrenchPunctuationSpacing(context, false)
        AutoSpaceTracker.clear()
        prefs = context.getSharedPreferences("router_e2e_modifier_tests", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        modifierStateController = ModifierStateController(doubleTapThreshold)
        val navModeController = NavModeController(context, modifierStateController)
        router = InputEventRouter(context, navModeController)

        altSymManager = AltSymManager(context.assets, prefs, context)
        altSymManager.reloadSymMappings()
        altSymManager.reloadSymMappings2()
        symLayoutController = SymLayoutController(context, prefs, altSymManager)
        variationStateController = VariationStateController(emptyMap())
        ctrlKeyMap = KeyMappingLoader.loadCtrlKeyMappings(context.assets, context)

        inputConnectionRecorder = RecordingInputConnection()
        inputConnection = inputConnectionRecorder.asProxy()
    }

    @After
    fun tearDown() {
        DeviceSpecific.clearTestOverrides()
        SettingsManager.resetVariationsToDefault(context)
        SettingsManager.setLongPressModifier(context, "alt")
        SettingsManager.setPhysicalKeyboardCurrencySymbol(context, "€")
        SettingsManager.setAltLatchStaysOnSpace(context, false)
        SettingsManager.setMidWordQuoteToApostrophe(context, false)
        SettingsManager.setAutoSpacePunctuation(context, "")
        SettingsManager.setFrenchPunctuationSpacing(context, false)
        AutoSpaceTracker.clear()
    }

    @Test
    fun variationsLongPress_path_respectsShiftLayerLatch_forInitialCommit() {
        SettingsManager.setLongPressModifier(context, "variations")
        SettingsManager.saveVariations(
            context,
            variations = mapOf(
                "a" to listOf("á"),
                "A" to listOf("Á")
            )
        )
        variationStateController = VariationStateController(
            mapOf(
                'a' to listOf("á"),
                'A' to listOf("Á")
            )
        )
        shiftLayerLatchedForTest = true

        val callbacks = TestCallbacks(modifierStateController)
        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = keyDown(KeyEvent.KEYCODE_A),
            callbacks = callbacks
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        // Regression guard: with old code this was "a" because shift latch
        // was ignored by the long-press behavior path.
        assertEquals("A", inputConnectionRecorder.committedTexts.first())
    }

    @Test
    fun stickyAltThenA_consumesOneShot_andCleansStateOnRelease() {
        val callbacks = TestCallbacks(modifierStateController)

        val altDownResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_ALT_LEFT,
            event = keyDown(KeyEvent.KEYCODE_ALT_LEFT),
            callbacks = callbacks
        )
        assertTrue(altDownResult is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertTrue(modifierStateController.altOneShot)
        assertTrue(modifierStateController.altPressed)

        modifierStateController.handleAltKeyUp(KeyEvent.KEYCODE_ALT_LEFT)
        assertTrue(modifierStateController.altOneShot)
        assertFalse(modifierStateController.altPressed)
        assertFalse(modifierStateController.altPhysicallyPressed)

        val aResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = keyDown(KeyEvent.KEYCODE_A),
            callbacks = callbacks
        )
        assertTrue(aResult is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals(1, callbacks.clearAltOneShotCalls)
        assertFalse(modifierStateController.altOneShot)
        assertTrue(inputConnectionRecorder.commitTextCalls > 0)
    }

    @Test
    fun holdAltThenA_consumesDuringHold_andCleansPressedFlagsOnAltRelease() {
        val callbacks = TestCallbacks(modifierStateController)

        val altDownResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_ALT_LEFT,
            event = keyDown(KeyEvent.KEYCODE_ALT_LEFT),
            callbacks = callbacks
        )
        assertTrue(altDownResult is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertTrue(modifierStateController.altPressed)
        assertTrue(modifierStateController.altOneShot)

        val aWhileHeldResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = keyDown(KeyEvent.KEYCODE_A, metaState = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON),
            callbacks = callbacks
        )
        assertTrue(aWhileHeldResult is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals(1, callbacks.clearAltOneShotCalls)

        modifierStateController.handleAltKeyUp(KeyEvent.KEYCODE_ALT_LEFT)
        assertFalse(modifierStateController.altPressed)
        assertFalse(modifierStateController.altPhysicallyPressed)
        assertFalse(modifierStateController.altOneShot)
    }

    @Test
    fun latchedAltThenSpace_clearsLatchByDefaultAndLetsSpacePassThrough() {
        modifierStateController.altLatchActive = true
        val callbacks = TestCallbacks(modifierStateController)

        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = keyDown(KeyEvent.KEYCODE_SPACE),
            callbacks = callbacks,
            clearAltOnSpaceEnabled = true
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.CallSuper)
        assertFalse(modifierStateController.altLatchActive)
        assertTrue(inputConnectionRecorder.committedTexts.isEmpty())
    }

    @Test
    fun latchedAltThenSpace_canKeepLatchWhenConfigured() {
        SettingsManager.setAltLatchStaysOnSpace(context, true)
        modifierStateController.altLatchActive = true
        val callbacks = TestCallbacks(modifierStateController)

        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = keyDown(KeyEvent.KEYCODE_SPACE),
            callbacks = callbacks,
            clearAltOnSpaceEnabled = true
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.CallSuper)
        assertTrue(modifierStateController.altLatchActive)
        assertTrue(inputConnectionRecorder.committedTexts.isEmpty())
    }

    @Test
    fun stickyCtrlThenA_consumesOneShot_andPerformsMappedAction() {
        val callbacks = TestCallbacks(modifierStateController)

        val ctrlDownResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_CTRL_LEFT,
            event = keyDown(KeyEvent.KEYCODE_CTRL_LEFT),
            callbacks = callbacks
        )
        assertTrue(ctrlDownResult is InputEventRouter.EditableFieldRoutingResult.CallSuper)
        assertTrue(modifierStateController.ctrlOneShot)
        assertTrue(modifierStateController.ctrlPressed)

        modifierStateController.handleCtrlKeyUp(KeyEvent.KEYCODE_CTRL_LEFT)
        assertTrue(modifierStateController.ctrlOneShot)
        assertFalse(modifierStateController.ctrlPressed)
        assertFalse(modifierStateController.ctrlPhysicallyPressed)

        val aResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = keyDown(KeyEvent.KEYCODE_A),
            callbacks = callbacks
        )
        assertTrue(aResult is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals(1, callbacks.clearCtrlOneShotCalls)
        assertFalse(modifierStateController.ctrlOneShot)
    }

    @Test
    fun holdCtrlThenAThenRelease_routerPathBypassesOneShotConsumeButKeepsPressedCleanup() {
        val callbacks = TestCallbacks(modifierStateController)

        val ctrlDownResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_CTRL_LEFT,
            event = keyDown(KeyEvent.KEYCODE_CTRL_LEFT),
            callbacks = callbacks
        )
        assertTrue(ctrlDownResult is InputEventRouter.EditableFieldRoutingResult.CallSuper)
        assertTrue(modifierStateController.ctrlPressed)
        assertTrue(modifierStateController.ctrlOneShot)

        val aWhileHeldResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = keyDown(KeyEvent.KEYCODE_A, metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON),
            callbacks = callbacks
        )
        assertTrue(aWhileHeldResult is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals(0, callbacks.clearCtrlOneShotCalls)
        assertTrue(inputConnectionRecorder.sentKeyEvents.isNotEmpty())

        modifierStateController.handleCtrlKeyUp(KeyEvent.KEYCODE_CTRL_LEFT)
        assertFalse(modifierStateController.ctrlPressed)
        assertFalse(modifierStateController.ctrlPhysicallyPressed)

        // Router-level path does not perform the service's release cleanup for stale one-shot state.
        // The service-level E2E test covers the actual runtime regression/fix.
        assertFalse(modifierStateController.ctrlLatchActive)
    }

    @Test
    fun heldCtrlVInNumericField_performsPasteInsteadOfCommittingAltDigitOrNativeKey() {
        val callbacks = TestCallbacks(modifierStateController)
        modifierStateController.ctrlPressed = true
        modifierStateController.ctrlPhysicallyPressed = true

        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_V,
            event = keyDown(KeyEvent.KEYCODE_V, metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON),
            callbacks = callbacks,
            isNumericField = true
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals(listOf(android.R.id.paste), inputConnectionRecorder.contextMenuActions)
        assertTrue(inputConnectionRecorder.committedTexts.isEmpty())
        assertTrue(inputConnectionRecorder.sentKeyEvents.isEmpty())
    }

    @Test
    fun nativeCtrlMappingInNumericField_stillPassesNativeCtrlInsteadOfForcingPaste() {
        ctrlKeyMap = mapOf(
            KeyEvent.KEYCODE_V to KeyMappingLoader.CtrlMapping("native_ctrl", "")
        )
        val callbacks = TestCallbacks(modifierStateController)
        modifierStateController.ctrlPressed = true
        modifierStateController.ctrlPhysicallyPressed = true

        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_V,
            event = keyDown(KeyEvent.KEYCODE_V, metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON),
            callbacks = callbacks,
            isNumericField = true
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertTrue(inputConnectionRecorder.contextMenuActions.isEmpty())
        assertTrue(inputConnectionRecorder.committedTexts.isEmpty())
        val sentEvent = inputConnectionRecorder.sentKeyEvents.single()
        assertEquals(KeyEvent.KEYCODE_V, sentEvent.keyCode)
        assertTrue(sentEvent.isCtrlPressed)
    }

    @Test
    fun titan2AltQuote_midWordQuoteToApostrophe_replacesAfterFollowingLetter() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan 2",
            device = "titan2",
            product = "titan2"
        )
        SettingsManager.setMidWordQuoteToApostrophe(context, true)
        SettingsManager.setAutoSpacePunctuation(context, "\"")
        altSymManager.reloadAltMappings()
        inputConnectionRecorder.textBeforeCursor = "qu"
        val callbacks = TestCallbacks(modifierStateController)
        modifierStateController.altOneShot = true

        val quoteResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_K,
            event = keyDown(KeyEvent.KEYCODE_K),
            callbacks = callbacks
        )
        val letterResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_O,
            event = keyDown(KeyEvent.KEYCODE_O),
            callbacks = callbacks
        )

        assertTrue(quoteResult is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertTrue(letterResult is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals(listOf("\"", "'o"), inputConnectionRecorder.committedTexts)
        assertEquals("qu'o", inputConnectionRecorder.textBeforeCursor)
        assertEquals(1, callbacks.clearAltOneShotCalls)
    }

    @Test
    fun titan2AltQuote_midWordQuoteToApostrophe_keepsOpeningQuoteBeforeFollowingLetter() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan 2",
            device = "titan2",
            product = "titan2"
        )
        SettingsManager.setMidWordQuoteToApostrophe(context, true)
        altSymManager.reloadAltMappings()
        inputConnectionRecorder.textBeforeCursor = ""
        val callbacks = TestCallbacks(modifierStateController)
        modifierStateController.altOneShot = true

        val quoteResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_K,
            event = keyDown(KeyEvent.KEYCODE_K),
            callbacks = callbacks
        )
        val letterResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_W,
            event = keyDown(KeyEvent.KEYCODE_W),
            callbacks = callbacks
        )

        assertTrue(quoteResult is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertTrue(letterResult is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals(listOf("\"", "w"), inputConnectionRecorder.committedTexts)
        assertEquals("\"w", inputConnectionRecorder.textBeforeCursor)
        assertEquals(1, callbacks.clearAltOneShotCalls)
    }

    @Test
    fun titan2AltQuestion_addsFrenchPunctuationSpacing() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan 2",
            device = "titan2",
            product = "titan2"
        )
        SettingsManager.setFrenchPunctuationSpacing(context, true)
        altSymManager.reloadAltMappings()
        inputConnectionRecorder.textBeforeCursor = "bonjour"
        val callbacks = TestCallbacks(modifierStateController)
        modifierStateController.altOneShot = true

        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_M,
            event = keyDown(KeyEvent.KEYCODE_M),
            callbacks = callbacks
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals(listOf(" ?"), inputConnectionRecorder.committedTexts)
        assertEquals("bonjour ?", inputConnectionRecorder.textBeforeCursor)
        assertEquals(1, callbacks.clearAltOneShotCalls)
    }

    @Test
    fun symEmojiPage_defaultLayout_mapsA_andConsumes() {
        val callbacks = TestCallbacks(modifierStateController)
        if (!altSymManager.getSymMappings().containsKey(KeyEvent.KEYCODE_A)) {
            SettingsManager.saveSymMappings(context, mapOf(KeyEvent.KEYCODE_A to "😢"))
            altSymManager.reloadSymMappings()
        }
        symLayoutController.toggleSymPage() // opens first text page (emoji in default config)
        assertTrue(symLayoutController.isSymActive())

        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = keyDown(KeyEvent.KEYCODE_A),
            callbacks = callbacks
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertTrue(inputConnectionRecorder.committedTexts.isNotEmpty())
        assertTrue(
            "commits=${inputConnectionRecorder.committedTexts}",
            inputConnectionRecorder.committedTexts.contains("😢")
        )
    }

    @Test
    fun symSymbolsPage_defaultLayout_mapsA_andConsumes() {
        val callbacks = TestCallbacks(modifierStateController)
        if (!altSymManager.getSymMappings2().containsKey(KeyEvent.KEYCODE_A)) {
            SettingsManager.saveSymMappingsPage2(context, mapOf(KeyEvent.KEYCODE_A to "="))
            altSymManager.reloadSymMappings2()
        }
        symLayoutController.openSymbolsPage()
        assertTrue(symLayoutController.isSymActive())

        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = keyDown(KeyEvent.KEYCODE_A),
            callbacks = callbacks
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertTrue(inputConnectionRecorder.committedTexts.isNotEmpty())
        assertTrue(
            "commits=${inputConnectionRecorder.committedTexts}",
            inputConnectionRecorder.committedTexts.contains("=")
        )
    }

    @Test
    fun symEmojiPage_customMapping_isUsed() {
        val callbacks = TestCallbacks(modifierStateController)
        SettingsManager.saveSymMappings(context, mapOf(KeyEvent.KEYCODE_A to "🧪"))
        altSymManager.reloadSymMappings()
        symLayoutController.toggleSymPage()

        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = keyDown(KeyEvent.KEYCODE_A),
            callbacks = callbacks
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals("🧪", inputConnectionRecorder.committedTexts.last())
    }

    @Test
    fun symSymbolsPage_customMapping_isUsed() {
        val callbacks = TestCallbacks(modifierStateController)
        SettingsManager.saveSymMappingsPage2(context, mapOf(KeyEvent.KEYCODE_A to "#"))
        altSymManager.reloadSymMappings2()
        symLayoutController.openSymbolsPage()

        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = keyDown(KeyEvent.KEYCODE_A),
            callbacks = callbacks
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals("#", inputConnectionRecorder.committedTexts.last())
    }

    @Test
    fun symSymbolsPage_boundaryMapping_usesBoundaryHandlerBeforeCommit() {
        SettingsManager.saveSymMappingsPage2(context, mapOf(KeyEvent.KEYCODE_S to ";"))
        altSymManager.reloadSymMappings2()
        symLayoutController.openSymbolsPage()
        val boundaryTexts = mutableListOf<String>()
        val callbacks = TestCallbacks(
            modifierStateController = modifierStateController,
            boundaryTextHandler = { text, _ ->
                boundaryTexts += text
                true
            }
        )

        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_S,
            event = keyDown(KeyEvent.KEYCODE_S),
            callbacks = callbacks
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals(listOf(";"), boundaryTexts)
        assertTrue(inputConnectionRecorder.committedTexts.isEmpty())
    }

    @Test
    fun altMapping_q25Profile_usesQ25AssetAndCommitsMappedChar() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "zinwa",
            manufacturer = "zinwa",
            model = "Q25",
            device = "Q25",
            product = "q25"
        )
        rebuildAltSymControllers()
        assertEquals("Q25", KeyMappingLoader.getDeviceName(context))

        val callbacks = TestCallbacks(modifierStateController)
        primeAltOneShot(callbacks)

        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_K,
            event = keyDown(KeyEvent.KEYCODE_K),
            callbacks = callbacks
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals("'", inputConnectionRecorder.committedTexts.last())
    }

    @Test
    fun altMapping_q25Profile_mapsReportedZeroAndCurrencyKeys() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "zinwa",
            manufacturer = "zinwa",
            model = "Q25",
            device = "Q25",
            product = "q25"
        )
        rebuildAltSymControllers()
        assertEquals("Q25", KeyMappingLoader.getDeviceName(context))

        val callbacks = TestCallbacks(modifierStateController)
        primeAltOneShot(callbacks)

        val zeroResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_0,
            event = keyDown(KeyEvent.KEYCODE_0),
            callbacks = callbacks
        )

        assertTrue(zeroResult is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals("0", inputConnectionRecorder.committedTexts.last())

        primeAltOneShot(callbacks)

        val currencyResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_GRAVE,
            event = keyDown(KeyEvent.KEYCODE_GRAVE),
            callbacks = callbacks
        )

        assertTrue(currencyResult is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals("€", inputConnectionRecorder.committedTexts.last())
    }

    @Test
    fun altMapping_q25Profile_usesConfiguredCurrencySymbol() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "zinwa",
            manufacturer = "zinwa",
            model = "Q25",
            device = "Q25",
            product = "q25"
        )
        SettingsManager.setPhysicalKeyboardCurrencySymbol(context, "$")
        rebuildAltSymControllers()

        val callbacks = TestCallbacks(modifierStateController)
        primeAltOneShot(callbacks)

        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_GRAVE,
            event = keyDown(KeyEvent.KEYCODE_GRAVE),
            callbacks = callbacks
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals("$", inputConnectionRecorder.committedTexts.last())
    }

    @Test
    fun altMapping_key2Profile_usesKey2AssetAndCommitsMappedChar() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "blackberry",
            manufacturer = "blackberry",
            model = "bbf100-1",
            device = "athena",
            product = "lineage_athena"
        )
        rebuildAltSymControllers()
        assertEquals("key2", KeyMappingLoader.getDeviceName(context))

        val callbacks = TestCallbacks(modifierStateController)
        primeAltOneShot(callbacks)

        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_K,
            event = keyDown(KeyEvent.KEYCODE_K),
            callbacks = callbacks
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals("'", inputConnectionRecorder.committedTexts.last())
    }

    @Test
    fun altMapping_titan2EliteQwertyOverride_usesEliteAssetAndCommitsMappedChar() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan 2",
            device = "titan2",
            product = "titan2"
        )
        SettingsManager.setPhysicalKeyboardProfileOverride(context, "titan2elite_qwerty")
        rebuildAltSymControllers()
        assertEquals("titan2elite_qwerty", KeyMappingLoader.getDeviceName(context))

        val callbacks = TestCallbacks(modifierStateController)
        primeAltOneShot(callbacks)

        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_J,
            event = keyDown(KeyEvent.KEYCODE_J),
            callbacks = callbacks
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals("#", inputConnectionRecorder.committedTexts.last())
    }

    @Test
    fun altMapping_unknownProfile_fallsBackToDefaultMappings() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unknown",
            manufacturer = "unknown",
            model = "unknown",
            device = "unknown",
            product = "unknown"
        )
        rebuildAltSymControllers()
        assertEquals("unknown", KeyMappingLoader.getDeviceName(context))

        val callbacks = TestCallbacks(modifierStateController)
        primeAltOneShot(callbacks)

        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_T,
            event = keyDown(KeyEvent.KEYCODE_T),
            callbacks = callbacks
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals("(", inputConnectionRecorder.committedTexts.last())
    }

    @Test
    fun swipeToDelete_handlesBothKnownSwipeKeyCodes() {
        SettingsManager.setSwipeToDelete(context, true)
        SettingsManager.setSwipeToDeleteProvider(
            context,
            SettingsManager.SWIPE_TO_DELETE_PROVIDER_TITAN2_KEYCODE
        )
        inputConnectionRecorder.textBeforeCursor = "hello world"
        val callbacks = TestCallbacks(modifierStateController)

        listOf(322, 404).forEach { keyCode ->
            inputConnectionRecorder.deleteSurroundingTextCalls = 0
            val result = routeKeyDown(
                keyCode = keyCode,
                event = keyDown(keyCode),
                callbacks = callbacks
            )

            assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
            assertTrue(inputConnectionRecorder.deleteSurroundingTextCalls > 0)
        }
    }

    @Test
    fun swipeToDelete_disabledStillConsumesBothKnownSwipeKeyCodes() {
        SettingsManager.setSwipeToDelete(context, false)
        inputConnectionRecorder.textBeforeCursor = "hello world"
        val callbacks = TestCallbacks(modifierStateController)

        listOf(322, 404).forEach { keyCode ->
            inputConnectionRecorder.deleteSurroundingTextCalls = 0
            val result = routeKeyDown(
                keyCode = keyCode,
                event = keyDown(keyCode),
                callbacks = callbacks
            )

            assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
            assertEquals(0, inputConnectionRecorder.deleteSurroundingTextCalls)
        }
    }

    @Test
    fun shiftOnGermanMultiTapQwertz_commitsUppercaseWithoutCyclingVariants() {
        it.palsoftware.pastiera.data.layout.LayoutMappingRepository.loadLayout(
            context.assets,
            "german_multitap_qwertz",
            context
        )
        SettingsManager.setLongPressModifier(context, "variations")
        val multiTapController = MultiTapController(
            handler = Handler(Looper.getMainLooper()),
            timeoutMs = 400L
        )
        val callbacks = callbacksWithCurrentLayout(
            multiTapCommitHandler = { code, mapping, uppercase, inputConnection, _ ->
                inputConnection != null &&
                    multiTapController.handleTap(code, mapping, uppercase, inputConnection).handled
            }
        )

        repeat(2) {
            val result = routeKeyDown(
                keyCode = KeyEvent.KEYCODE_S,
                event = keyDown(
                    KeyEvent.KEYCODE_S,
                    metaState = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
                ),
                callbacks = callbacks
            )

            assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
            symLayoutController.handleKeyUp(KeyEvent.KEYCODE_S, shiftPressed = true)
        }

        assertEquals(listOf("S", "S"), inputConnectionRecorder.committedTexts)
        assertEquals(0, inputConnectionRecorder.deleteSurroundingTextCalls)
    }

    @Test
    fun shiftedRealMultiTapLayout_cyclesUppercaseAccentVariants() {
        val italianStyleMapping = LayoutMapping(
            lowercase = "e",
            uppercase = "E",
            multiTapEnabled = true,
            taps = listOf(
                TapMapping(lowercase = "e", uppercase = "E"),
                TapMapping(lowercase = "è", uppercase = "È"),
                TapMapping(lowercase = "é", uppercase = "É")
            )
        )
        val multiTapController = MultiTapController(
            handler = Handler(Looper.getMainLooper()),
            timeoutMs = 400L
        )
        val callbacks = TestCallbacks(
            modifierStateController = modifierStateController,
            mappingProvider = { key ->
                if (key == KeyEvent.KEYCODE_E) italianStyleMapping else null
            },
            multiTapCommitHandler = { code, mapping, uppercase, inputConnection, _ ->
                inputConnection != null &&
                    multiTapController.handleTap(code, mapping, uppercase, inputConnection).handled
            }
        )

        repeat(3) {
            val result = routeKeyDown(
                keyCode = KeyEvent.KEYCODE_E,
                event = keyDown(
                    KeyEvent.KEYCODE_E,
                    metaState = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
                ),
                callbacks = callbacks
            )

            assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        }

        assertEquals(listOf("E", "È", "É"), inputConnectionRecorder.committedTexts)
        assertEquals(2, inputConnectionRecorder.deleteSurroundingTextCalls)
    }

    @Test
    fun latchedShiftOnGermanMultiTapQwertz_keepsDoubleSInWords() {
        it.palsoftware.pastiera.data.layout.LayoutMappingRepository.loadLayout(
            context.assets,
            "german_multitap_qwertz",
            context
        )
        inputConnectionRecorder.textBeforeCursor = "foo"
        shiftLayerLatchedForTest = true
        val multiTapController = MultiTapController(
            handler = Handler(Looper.getMainLooper()),
            timeoutMs = 400L
        )
        val callbacks = callbacksWithCurrentLayout(
            multiTapCommitHandler = { code, mapping, uppercase, inputConnection, _ ->
                inputConnection != null &&
                    multiTapController.handleTap(code, mapping, uppercase, inputConnection).handled
            }
        )

        repeat(2) {
            val result = routeKeyDown(
                keyCode = KeyEvent.KEYCODE_S,
                event = keyDown(KeyEvent.KEYCODE_S),
                callbacks = callbacks
            )

            assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
            symLayoutController.handleKeyUp(KeyEvent.KEYCODE_S, shiftPressed = false)
        }

        assertEquals(listOf("S", "S"), inputConnectionRecorder.committedTexts)
        assertEquals(0, inputConnectionRecorder.deleteSurroundingTextCalls)
    }

    @Test
    fun shiftOnPlainQwertz_commitsUppercaseWithoutMultitapSideEffects() {
        it.palsoftware.pastiera.data.layout.LayoutMappingRepository.loadLayout(
            context.assets,
            "qwertz",
            context
        )
        SettingsManager.setLongPressModifier(context, "variations")
        val callbacks = callbacksWithCurrentLayout()

        repeat(2) {
            val result = routeKeyDown(
                keyCode = KeyEvent.KEYCODE_S,
                event = keyDown(
                    KeyEvent.KEYCODE_S,
                    metaState = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
                ),
                callbacks = callbacks
            )

            assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        }

        assertEquals(listOf("S", "S"), inputConnectionRecorder.committedTexts)
        assertEquals(0, inputConnectionRecorder.deleteSurroundingTextCalls)
    }

    private fun routeKeyDown(
        keyCode: Int,
        event: KeyEvent,
        callbacks: TestCallbacks,
        clearAltOnSpaceEnabled: Boolean = false,
        isNumericField: Boolean = false
    ): InputEventRouter.EditableFieldRoutingResult {
        return router.routeEditableFieldKeyDown(
            keyCode = keyCode,
            event = event,
            params = buildParams(clearAltOnSpaceEnabled, isNumericField),
            controllers = InputEventRouter.EditableFieldKeyDownControllers(
                modifierStateController = modifierStateController,
                symLayoutController = symLayoutController,
                altSymManager = altSymManager,
                variationStateController = variationStateController,
                textInputController = TextInputController(context, modifierStateController, 500L)
            ),
            callbacks = callbacks.asRouterCallbacks()
        )
    }

    private fun buildParams(
        clearAltOnSpaceEnabled: Boolean = false,
        isNumericField: Boolean = false
    ): InputEventRouter.EditableFieldKeyDownHandlingParams {
        return InputEventRouter.EditableFieldKeyDownHandlingParams(
            inputConnection = inputConnection,
            isNumericField = isNumericField,
            isInputViewActive = true,
            shiftPressed = modifierStateController.shiftPressed,
            shiftLayerLatched = shiftLayerLatchedForTest,
            ctrlPressed = modifierStateController.ctrlPressed,
            ctrlPhysicallyPressed = modifierStateController.ctrlPhysicallyPressed,
            altPressed = modifierStateController.altPressed,
            ctrlLatchActive = modifierStateController.ctrlLatchActive,
            altLatchActive = modifierStateController.altLatchActive,
            ctrlLatchFromNavMode = modifierStateController.ctrlLatchFromNavMode,
            ctrlKeyMap = ctrlKeyMap,
            ctrlOneShot = modifierStateController.ctrlOneShot,
            altOneShot = modifierStateController.altOneShot,
            clearAltOnSpaceEnabled = clearAltOnSpaceEnabled,
            shiftOneShot = modifierStateController.shiftOneShot,
            capsLockEnabled = modifierStateController.capsLockEnabled,
            cursorUpdateDelayMs = 0L
        )
    }

    private fun keyDown(keyCode: Int, metaState: Int = 0): KeyEvent {
        return KeyEvent(
            0L,
            System.currentTimeMillis(),
            KeyEvent.ACTION_DOWN,
            keyCode,
            0,
            metaState
        )
    }

    private fun callbacksWithCurrentLayout(
        multiTapCommitHandler: (Int, it.palsoftware.pastiera.data.layout.LayoutMapping, Boolean, InputConnection?, Boolean) -> Boolean = { _, _, _, _, _ -> false }
    ): TestCallbacks {
        return TestCallbacks(
            modifierStateController,
            mappingProvider = { key ->
                it.palsoftware.pastiera.data.layout.LayoutMappingRepository.getMapping(key)
            },
            multiTapCommitHandler = multiTapCommitHandler
        )
    }

    private fun rebuildAltSymControllers() {
        altSymManager = AltSymManager(context.assets, prefs, context)
        altSymManager.reloadSymMappings()
        altSymManager.reloadSymMappings2()
        symLayoutController = SymLayoutController(context, prefs, altSymManager)
        inputConnectionRecorder.committedTexts.clear()
    }

    private fun primeAltOneShot(callbacks: TestCallbacks) {
        val altDownResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_ALT_LEFT,
            event = keyDown(KeyEvent.KEYCODE_ALT_LEFT),
            callbacks = callbacks
        )
        assertTrue(altDownResult is InputEventRouter.EditableFieldRoutingResult.Consume)
        modifierStateController.handleAltKeyUp(KeyEvent.KEYCODE_ALT_LEFT)
    }

    private class TestCallbacks(
        private val modifierStateController: ModifierStateController,
        private val mappingProvider: (Int) -> it.palsoftware.pastiera.data.layout.LayoutMapping? = { null },
        private val multiTapCommitHandler: (Int, it.palsoftware.pastiera.data.layout.LayoutMapping, Boolean, InputConnection?, Boolean) -> Boolean = { _, _, _, _, _ -> false },
        private val boundaryTextHandler: (String, InputConnection?) -> Boolean = { _, _ -> false }
    ) {
        var updateStatusBarCalls = 0
        var refreshStatusBarCalls = 0
        var clearAltOneShotCalls = 0
        var clearCtrlOneShotCalls = 0

        fun asRouterCallbacks(): InputEventRouter.EditableFieldKeyDownHandlingCallbacks {
            return InputEventRouter.EditableFieldKeyDownHandlingCallbacks(
                updateStatusBar = { updateStatusBarCalls++ },
                refreshStatusBar = { refreshStatusBarCalls++ },
                disableShiftOneShot = { modifierStateController.shiftOneShot = false },
                clearAltOneShot = {
                    clearAltOneShotCalls++
                    modifierStateController.altOneShot = false
                },
                clearCtrlOneShot = {
                    clearCtrlOneShotCalls++
                    modifierStateController.ctrlOneShot = false
                },
                getCharacterFromLayout = { _, _, _ -> null },
                isAlphabeticKey = { key -> key in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z },
                callSuper = { false },
                callSuperWithKey = { _, _ -> false },
                startSpeechRecognition = { },
                getMapping = mappingProvider,
                handleMultiTapCommit = multiTapCommitHandler,
                isLongPressSuppressed = { false },
                toggleMinimalUi = { },
                handleBoundaryText = boundaryTextHandler
            )
        }
    }

    private class RecordingInputConnection {
        var commitTextCalls = 0
        var deleteSurroundingTextCalls = 0
        var textBeforeCursor: String = ""
        val committedTexts = mutableListOf<String>()
        val sentKeyEvents = mutableListOf<KeyEvent>()
        val contextMenuActions = mutableListOf<Int>()

        fun asProxy(): InputConnection {
            return Proxy.newProxyInstance(
                InputConnection::class.java.classLoader,
                arrayOf(InputConnection::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "commitText" -> {
                        commitTextCalls++
                        val text = args?.getOrNull(0)?.toString()
                        if (text != null) {
                            committedTexts += text
                            textBeforeCursor += text
                        }
                        true
                    }
                    "deleteSurroundingText" -> {
                        deleteSurroundingTextCalls++
                        val before = (args?.getOrNull(0) as? Int) ?: 0
                        if (before > 0 && textBeforeCursor.isNotEmpty()) {
                            val keep = (textBeforeCursor.length - before).coerceAtLeast(0)
                            textBeforeCursor = textBeforeCursor.take(keep)
                        }
                        true
                    }
                    "sendKeyEvent" -> {
                        val event = args?.getOrNull(0) as? KeyEvent
                        if (event != null) {
                            sentKeyEvents += event
                        }
                        true
                    }
                    "performContextMenuAction" -> {
                        val id = args?.getOrNull(0) as? Int
                        if (id != null) {
                            contextMenuActions += id
                        }
                        true
                    }
                    "getTextBeforeCursor" -> textBeforeCursor
                    "getTextAfterCursor" -> ""
                    "getExtractedText" -> ExtractedText().apply {
                        selectionStart = 0
                        selectionEnd = 0
                    }
                    else -> defaultValue(method.returnType)
                }
            } as InputConnection
        }

        private fun defaultValue(type: Class<*>): Any? {
            return when {
                type == Boolean::class.javaPrimitiveType -> false
                type == Int::class.javaPrimitiveType -> 0
                type == Long::class.javaPrimitiveType -> 0L
                type == Float::class.javaPrimitiveType -> 0f
                type == Double::class.javaPrimitiveType -> 0.0
                type == Short::class.javaPrimitiveType -> 0.toShort()
                type == Byte::class.javaPrimitiveType -> 0.toByte()
                type == Char::class.javaPrimitiveType -> 0.toChar()
                else -> null
            }
        }
    }
}
