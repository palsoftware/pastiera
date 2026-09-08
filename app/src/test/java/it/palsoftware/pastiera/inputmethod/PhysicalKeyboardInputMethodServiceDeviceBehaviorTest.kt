package it.palsoftware.pastiera.inputmethod

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.AltModifierBinding
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.SymPagesConfig
import it.palsoftware.pastiera.core.InputContextState
import it.palsoftware.pastiera.core.ModifierStateController
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import java.lang.reflect.Field
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PhysicalKeyboardInputMethodServiceDeviceBehaviorTest {

    private lateinit var service: PhysicalKeyboardInputMethodService
    private lateinit var recorder: RecordingInputConnection
    private lateinit var inputConnection: InputConnection
    private lateinit var editorInfo: EditorInfo

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        DeviceSpecific.clearTestOverrides()
        SettingsManager.setSymPagesConfig(context, SymPagesConfig())
        SettingsManager.resetSymMappings(context)
        SettingsManager.resetSymMappingsPage2(context)
        SettingsManager.setStaticVariationBarLayerStickyEnabled(context, true)
        SettingsManager.setAutoCapitalizeFirstLetter(context, true)
        SettingsManager.setAutoCapitalizeAfterPeriod(context, true)
        SettingsManager.setAutoCapitalizeRespectManualShiftOff(context, true)
        SettingsManager.setAutoCapitalizeRestrictedFields(context, false)
        SettingsManager.setPhysicalKeyboardProfileOverride(context, "auto")
        SettingsManager.setAltModifierBinding(context, AltModifierBinding.DeviceSym)

        service = Robolectric.buildService(PhysicalKeyboardInputMethodService::class.java)
            .create()
            .get()

        recorder = RecordingInputConnection()
        inputConnection = recorder.asProxy()
        editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            packageName = "it.palsoftware.pastiera.test"
        }

        // Inject active editable-field context so onKeyDown/onKeyUp run the same path as on device.
        setField(service, "mInputConnection", inputConnection)
        setField(service, "mStartedInputConnection", inputConnection)
        setField(service, "mInputEditorInfo", editorInfo)
        setField(service, "isInputViewActive", true)
        setField(service, "inputContextState", InputContextState.fromEditorInfo(editorInfo))
    }

    @Test
    fun mp01DedicatedEmojiKey_isIgnoredWhenMp01IsNotActive() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan 2",
            device = "titan2",
            product = "titan2"
        )
        setField(service, "physicalKeyboardProfileOverride", "auto")

        val handled = service.onKeyDown(
            666,
            keyEvent(KeyEvent.ACTION_DOWN, 666, 6_000L, 6_000L)
        )

        assertFalse(handled)
    }

    @Test
    fun mp01DedicatedEmojiKey_isHandledWhenManualMp01OverrideIsActive() {
        DeviceSpecific.setBuildFingerprintForTests(
            brand = "unihertz",
            manufacturer = "unihertz",
            model = "Titan 2",
            device = "titan2",
            product = "titan2"
        )
        setField(service, "physicalKeyboardProfileOverride", "mp01")

        val handled = service.onKeyDown(
            666,
            keyEvent(KeyEvent.ACTION_DOWN, 666, 6_100L, 6_100L)
        )

        assertTrue(handled)
    }

    @Test
    fun ctrlHoldShortcutRelease_doesNotLeaveCtrlActive() {
        val t0 = 1_000L

        service.onKeyDown(
            KeyEvent.KEYCODE_CTRL_LEFT,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, t0, t0)
        )
        service.onKeyDown(
            KeyEvent.KEYCODE_A,
            keyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_A,
                downTime = t0,
                eventTime = t0 + 120L,
                metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
            )
        )
        service.onKeyUp(
            KeyEvent.KEYCODE_A,
            keyEvent(
                action = KeyEvent.ACTION_UP,
                keyCode = KeyEvent.KEYCODE_A,
                downTime = t0,
                eventTime = t0 + 150L,
                metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
            )
        )
        service.onKeyUp(
            KeyEvent.KEYCODE_CTRL_LEFT,
            keyEvent(
                action = KeyEvent.ACTION_UP,
                keyCode = KeyEvent.KEYCODE_CTRL_LEFT,
                downTime = t0,
                eventTime = t0 + 420L
            )
        )

        val modifier = modifierController()
        assertFalse(modifier.ctrlPressed)
        assertFalse(modifier.ctrlPhysicallyPressed)
        assertFalse(modifier.ctrlOneShot)
        assertFalse(modifier.ctrlLatchActive)
    }

    @Test
    fun altDoubleTapLatch_thenSingleTapFullyDeactivates() {
        val t0 = 2_000L

        tapAlt(t0)          // one-shot
        tapAlt(t0 + 120L)   // latch (+ visual altModifierLayerLatched on quick double tap)
        tapAlt(t0 + 240L)   // user expects off; device reports still active

        val modifier = modifierController()
        val altModifierLayerLatched = getField<Boolean>(service, "altModifierLayerLatched")

        assertFalse(modifier.altOneShot)
        assertFalse(modifier.altLatchActive)
        assertFalse(altModifierLayerLatched)
    }

    @Test
    fun shiftDoubleTapLatch_thenSingleTapFullyDeactivates() {
        val t0 = 2_500L

        tapShift(t0)          // one-shot
        tapShift(t0 + 120L)   // latch (+ visual shiftLayerLatched on quick double tap)
        tapShift(t0 + 240L)   // user expects off

        val modifier = modifierController()
        val shiftLayerLatched = getField<Boolean>(service, "shiftLayerLatched")

        assertFalse(modifier.shiftOneShot)
        assertFalse(modifier.capsLockEnabled)
        assertFalse(shiftLayerLatched)
    }

    @Test
    fun clicksPower_bouncedShiftTapDoesNotEnableCapsLock() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setPhysicalKeyboardProfileOverride(context, "clicks_power")
        setField(service, "physicalKeyboardProfileOverride", "clicks_power")
        val t0 = 2_600L

        tapShift(t0)
        tapShift(t0 + 60L)

        val modifier = modifierController()
        assertTrue(modifier.shiftOneShot)
        assertFalse(modifier.capsLockEnabled)
        assertFalse(getField<Boolean>(service, "shiftLayerLatched"))
    }

    @Test
    fun clicksPower_intentionalShiftDoubleTapStillEnablesCapsLock() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setPhysicalKeyboardProfileOverride(context, "clicks_power")
        setField(service, "physicalKeyboardProfileOverride", "clicks_power")
        val t0 = 2_700L

        tapShift(t0)
        tapShift(t0 + 120L)

        val modifier = modifierController()
        assertFalse(modifier.shiftOneShot)
        assertTrue(modifier.capsLockEnabled)
        assertTrue(getField<Boolean>(service, "shiftLayerLatched"))
    }

    @Test
    fun clicksPower_sameFrameShiftMacroDoesNotToggleLogicalShift() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setPhysicalKeyboardProfileOverride(context, "clicks_power")
        setField(service, "physicalKeyboardProfileOverride", "clicks_power")

        repeat(4) { index ->
            typeClicksSyntheticAtMacro(3_000L + index * 400L)
            val modifier = modifierController()
            assertFalse(modifier.shiftOneShot)
            assertFalse(modifier.capsLockEnabled)
            assertFalse(modifier.shiftPressed)
            assertFalse(modifier.shiftPhysicallyPressed)
        }
    }

    @Test
    fun shiftEnter_consumesManualShift_whenAutoCapitalizeAtLineStartIsDisabled() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setAutoCapitalizeFirstLetter(context, false)
        SettingsManager.setAutoCapitalizeAfterPeriod(context, false)
        editorInfo.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        setField(service, "mInputEditorInfo", editorInfo)
        setField(service, "inputContextState", InputContextState.fromEditorInfo(editorInfo))
        val t0 = 2_700L

        service.onKeyDown(
            KeyEvent.KEYCODE_SHIFT_LEFT,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, t0, t0)
        )
        service.onKeyDown(
            KeyEvent.KEYCODE_ENTER,
            keyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_ENTER,
                downTime = t0,
                eventTime = t0 + 80L,
                metaState = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            )
        )
        service.onKeyUp(
            KeyEvent.KEYCODE_SHIFT_LEFT,
            keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, t0, t0 + 120L)
        )

        val modifier = modifierController()
        assertFalse(modifier.shiftPressed)
        assertFalse(modifier.shiftPhysicallyPressed)
        assertFalse(modifier.shiftOneShot)
        assertFalse(modifier.capsLockEnabled)

        service.onKeyDown(
            KeyEvent.KEYCODE_A,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, t0 + 200L, t0 + 200L)
        )
        assertTrue("commits=${recorder.committedTexts}", recorder.committedTexts.contains("a"))
        assertFalse("commits=${recorder.committedTexts}", recorder.committedTexts.contains("A"))
    }

    @Test
    fun capWords_allAutoCapDisabled_clicksPowerAltPThenAStaysLowercase() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setAutoCapitalizeFirstLetter(context, false)
        SettingsManager.setAutoCapitalizeAfterPeriod(context, false)
        SettingsManager.setPhysicalKeyboardProfileOverride(context, "clicks_power")
        setField(service, "physicalKeyboardProfileOverride", "clicks_power")
        getField<AlternateCharacterManager>(service, "alternateCharacterManager")
            .reloadModifierAndDeviceSymMappings()
        focusNewField(
            newRecorder = RecordingInputConnection(),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        )

        assertFalse(modifierController().shiftOneShot)

        val t0 = 2_800L
        tapAlt(t0)
        service.onKeyDown(
            KeyEvent.KEYCODE_P,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_P, t0 + 40L, t0 + 40L)
        )
        service.onKeyUp(
            KeyEvent.KEYCODE_P,
            keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_P, t0 + 40L, t0 + 60L)
        )
        service.onKeyDown(
            KeyEvent.KEYCODE_A,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, t0 + 80L, t0 + 80L)
        )

        assertEquals("@a", recorder.textBeforeCursor)
        assertFalse(
            AutoCapitalizeHelper.shouldCapitalizeAfterBoundary(
                context = context,
                state = InputContextState.fromEditorInfo(editorInfo),
                inputConnection = inputConnection,
                keyCode = KeyEvent.KEYCODE_SPACE
            )
        )
    }

    @Test
    fun autoCap_manualShiftOff_doesNotLeakIntoAnotherEmptyField() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setAutoCapitalizeFirstLetter(context, true)
        SettingsManager.setAutoCapitalizeAfterPeriod(context, true)

        service.onStartInput(editorInfo, false)
        assertTrue(modifierController().shiftOneShot)

        tapShift(2_900L)
        assertFalse(modifierController().shiftOneShot)

        focusNewField(RecordingInputConnection())

        assertTrue(modifierController().shiftOneShot)
    }

    @Test
    fun switchingFromAutoCapitalizedLatinFieldToKorean_clearsStaleShift() {
        service.onStartInput(editorInfo, false)
        assertTrue(modifierController().shiftOneShot)

        invokePrivate(service, "switchToLayout", "korean_2set", false)

        assertFalse(modifierController().shiftOneShot)
        service.onKeyDown(
            KeyEvent.KEYCODE_R,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_R, 2_950L, 2_950L)
        )
        service.onKeyDown(
            KeyEvent.KEYCODE_K,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_K, 3_000L, 3_000L)
        )
        assertEquals("가", recorder.composingTexts.last())
    }

    @Test
    fun manualShiftPressedAfterSwitchingToKorean_producesTenseConsonant() {
        invokePrivate(service, "switchToLayout", "korean_2set", false)
        tapShift(3_050L)

        service.onKeyDown(
            KeyEvent.KEYCODE_R,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_R, 3_100L, 3_100L)
        )
        service.onKeyDown(
            KeyEvent.KEYCODE_K,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_K, 3_150L, 3_150L)
        )

        assertEquals("까", recorder.composingTexts.last())
    }

    @Test
    fun koreanComposition_doesNotSurviveRestartOfCurrentField() {
        invokePrivate(service, "switchToLayout", "korean_2set", false)
        service.onKeyDown(
            KeyEvent.KEYCODE_R,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_R, 3_200L, 3_200L)
        )
        service.onKeyDown(
            KeyEvent.KEYCODE_K,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_K, 3_250L, 3_250L)
        )
        assertEquals("가", recorder.composingTexts.last())

        // Messaging apps can restart the same editor after sending and clearing its text.
        recorder.composingTexts.clear()
        service.onStartInput(editorInfo, true)

        service.onKeyDown(
            KeyEvent.KEYCODE_S,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_S, 3_300L, 3_300L)
        )
        service.onKeyDown(
            KeyEvent.KEYCODE_K,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_K, 3_350L, 3_350L)
        )

        assertEquals("나", recorder.composingTexts.last())
    }

    @Test
    fun autoCap_manualShiftOff_survivesRestartOfCurrentField() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setAutoCapitalizeFirstLetter(context, true)
        SettingsManager.setAutoCapitalizeAfterPeriod(context, true)

        service.onStartInput(editorInfo, false)
        assertTrue(modifierController().shiftOneShot)

        tapShift(3_000L)
        assertFalse(modifierController().shiftOneShot)

        service.onStartInput(editorInfo, true)

        assertFalse(modifierController().shiftOneShot)
    }

    @Test
    fun autoCap_manualShiftOff_doesNotSurviveRestart_whenRespectIsDisabled() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setAutoCapitalizeFirstLetter(context, true)
        SettingsManager.setAutoCapitalizeAfterPeriod(context, true)
        SettingsManager.setAutoCapitalizeRespectManualShiftOff(context, false)

        service.onStartInput(editorInfo, false)
        assertTrue(modifierController().shiftOneShot)

        tapShift(3_100L)
        assertFalse(modifierController().shiftOneShot)

        service.onStartInput(editorInfo, true)

        assertTrue(modifierController().shiftOneShot)
    }

    @Test
    fun autoCap_restrictedFieldsSetting_startsUriFieldWithShift() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setAutoCapitalizeFirstLetter(context, true)
        SettingsManager.setAutoCapitalizeRestrictedFields(context, true)

        focusNewField(
            newRecorder = RecordingInputConnection(),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        )

        assertTrue(modifierController().shiftOneShot)
    }

    @Test
    fun autoCap_restrictedFieldsSetting_neverStartsPasswordWithShift() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setAutoCapitalizeFirstLetter(context, true)
        SettingsManager.setAutoCapitalizeRestrictedFields(context, true)

        focusNewField(
            newRecorder = RecordingInputConnection(),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )

        assertFalse(modifierController().shiftOneShot)
    }

    @Test
    fun autoCap_newFieldAfterNewline_startsWithShift() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setAutoCapitalizeFirstLetter(context, true)
        SettingsManager.setAutoCapitalizeAfterPeriod(context, true)
        val nextField = RecordingInputConnection().apply {
            textBeforeCursor = "Previous line\n"
        }

        focusNewField(nextField)

        assertTrue(modifierController().shiftOneShot)
    }

    @Test
    fun deviceSanity_ctrlLatch_canBeDisabledByAnotherCtrlTap() {
        val t0 = 3_000L
        tapCtrl(t0)
        tapCtrl(t0 + 120L) // latch
        tapCtrl(t0 + 260L) // toggle off

        val modifier = modifierController()
        assertFalse(modifier.ctrlOneShot)
        assertFalse(modifier.ctrlLatchActive)
    }

    @Test
    fun deviceSanity_ctrlStickyThenA_triggersShortcutPath() {
        val t0 = 3_600L
        tapCtrl(t0) // one-shot

        service.onKeyDown(
            KeyEvent.KEYCODE_A,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, t0 + 60L, t0 + 60L)
        )

        assertTrue(
            recorder.contextMenuActions.contains(android.R.id.selectAll) ||
                recorder.sentKeyEvents.isNotEmpty()
        )
    }

    @Test
    fun deviceSanity_altHoldThenA_doesNotStickAlt() {
        val t0 = 3_800L
        service.onKeyDown(
            KeyEvent.KEYCODE_ALT_LEFT,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT, t0, t0)
        )
        service.onKeyDown(
            KeyEvent.KEYCODE_A,
            keyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_A,
                downTime = t0,
                eventTime = t0 + 80L,
                metaState = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            )
        )
        service.onKeyUp(
            KeyEvent.KEYCODE_A,
            keyEvent(
                action = KeyEvent.ACTION_UP,
                keyCode = KeyEvent.KEYCODE_A,
                downTime = t0,
                eventTime = t0 + 110L,
                metaState = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            )
        )
        service.onKeyUp(
            KeyEvent.KEYCODE_ALT_LEFT,
            keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ALT_LEFT, t0, t0 + 300L)
        )

        val modifier = modifierController()
        assertFalse(modifier.altPressed)
        assertFalse(modifier.altPhysicallyPressed)
        assertFalse(modifier.altLatchActive)
    }

    @Test
    fun altShiftLayoutSwitch_consumesShortcutAndClearsModifiers_whenEnabled() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setAltShiftLayoutSwitchEnabled(context, true)
        val t0 = 3_900L

        service.onKeyDown(
            KeyEvent.KEYCODE_ALT_LEFT,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT, t0, t0)
        )
        assertTrue(modifierController().altPressed)

        val handled = service.onKeyDown(
            KeyEvent.KEYCODE_SHIFT_LEFT,
            keyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_SHIFT_LEFT,
                downTime = t0,
                eventTime = t0 + 80L,
                metaState = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            )
        )

        val modifier = modifierController()
        assertTrue(handled)
        assertFalse(modifier.altPressed)
        assertFalse(modifier.altPhysicallyPressed)
        assertFalse(modifier.altOneShot)
        assertFalse(modifier.altLatchActive)
        assertFalse(modifier.shiftPressed)
        assertFalse(modifier.shiftOneShot)
        assertFalse(modifier.capsLockEnabled)
    }

    @Test
    fun altShiftLayoutSwitch_ignoresAltOneShot_whenShiftPressedLater() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setAltShiftLayoutSwitchEnabled(context, true)
        val t0 = 3_950L

        service.onKeyDown(
            KeyEvent.KEYCODE_ALT_LEFT,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT, t0, t0)
        )
        service.onKeyUp(
            KeyEvent.KEYCODE_ALT_LEFT,
            keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ALT_LEFT, t0, t0 + 40L)
        )
        assertTrue(modifierController().altOneShot)

        val handled = service.onKeyDown(
            KeyEvent.KEYCODE_SHIFT_LEFT,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, t0 + 120L, t0 + 120L)
        )

        val modifier = modifierController()
        assertFalse(handled)
        assertTrue(modifier.altOneShot)
        assertTrue(modifier.shiftOneShot)
        assertFalse(modifier.altPhysicallyPressed)
    }

    @Test
    fun altEnterLayoutSwitch_consumesShortcutAndClearsModifiers_whenEnabled() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setAltEnterLayoutSwitchEnabled(context, true)
        val t0 = 3_900L

        service.onKeyDown(
            KeyEvent.KEYCODE_ALT_LEFT,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT, t0, t0)
        )
        assertTrue(modifierController().altPressed)

        val handled = service.onKeyDown(
            KeyEvent.KEYCODE_ENTER,
            keyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_ENTER,
                downTime = t0,
                eventTime = t0 + 80L,
                metaState = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            )
        )

        val modifier = modifierController()
        assertTrue(handled)
        assertFalse(modifier.altPressed)
        assertFalse(modifier.altPhysicallyPressed)
        assertFalse(modifier.altOneShot)
        assertFalse(modifier.altLatchActive)

        val keyUpHandled = service.onKeyUp(
            KeyEvent.KEYCODE_ENTER,
            keyEvent(
                action = KeyEvent.ACTION_UP,
                keyCode = KeyEvent.KEYCODE_ENTER,
                downTime = t0,
                eventTime = t0 + 100L,
                metaState = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            )
        )
        assertTrue(keyUpHandled)
    }

    @Test
    fun altEnterLayoutSwitch_doesNotConsumeWhenDisabled() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setAltEnterLayoutSwitchEnabled(context, false)
        setField(service, "clearAltOnSpaceEnabled", false)
        val t0 = 3_920L

        service.onKeyDown(
            KeyEvent.KEYCODE_ALT_LEFT,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT, t0, t0)
        )
        service.onKeyDown(
            KeyEvent.KEYCODE_ENTER,
            keyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_ENTER,
                downTime = t0,
                eventTime = t0 + 80L,
                metaState = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            )
        )

        assertTrue(modifierController().altPhysicallyPressed)
    }

    @Test
    fun altEnterLayoutSwitch_consumesKeyRepeatsUntilKeyUp() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setAltEnterLayoutSwitchEnabled(context, true)
        val t0 = 3_940L

        service.onKeyDown(
            KeyEvent.KEYCODE_ALT_LEFT,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT, t0, t0)
        )
        service.onKeyDown(
            KeyEvent.KEYCODE_ENTER,
            keyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_ENTER,
                downTime = t0,
                eventTime = t0 + 80L,
                metaState = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            )
        )

        val repeatedHandled = service.onKeyDown(
            KeyEvent.KEYCODE_ENTER,
            keyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_ENTER,
                downTime = t0,
                eventTime = t0 + 160L,
                metaState = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON,
                repeatCount = 1
            )
        )

        assertTrue(repeatedHandled)
        assertFalse(recorder.committedTexts.contains("\n"))
        assertTrue(recorder.sentKeyEvents.isEmpty())
    }

    @Test
    fun altEnterLayoutSwitch_doesNotTriggerWhenEnterIsPressedFirst() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setAltEnterLayoutSwitchEnabled(context, true)
        val t0 = 3_960L

        service.onKeyDown(
            KeyEvent.KEYCODE_ENTER,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, t0, t0)
        )
        service.onKeyDown(
            KeyEvent.KEYCODE_ALT_LEFT,
            keyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_ALT_LEFT,
                downTime = t0 + 80L,
                eventTime = t0 + 80L
            )
        )

        assertTrue(modifierController().altPressed)
        assertTrue(modifierController().altPhysicallyPressed)
    }

    @Test
    fun deviceSanity_symACyclesDeviceThenEmojiThenSymbols_exactMappings() {
        val t0 = 4_000L

        tapSym(t0)
        service.onKeyDown(
            KeyEvent.KEYCODE_A,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, t0 + 20L, t0 + 20L)
        )
        assertTrue("commits=${recorder.committedTexts}", recorder.committedTexts.contains("@"))

        tapSym(t0 + 200L) // re-open SYM (Device SYM)
        tapSym(t0 + 260L) // next page -> emoji
        service.onKeyDown(
            KeyEvent.KEYCODE_A,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, t0 + 300L, t0 + 300L)
        )
        assertTrue("commits=${recorder.committedTexts}", recorder.committedTexts.contains("😢"))

        tapSym(t0 + 400L) // re-open SYM (Device SYM)
        tapSym(t0 + 460L) // next page -> emoji
        tapSym(t0 + 520L) // next page -> symbols
        service.onKeyDown(
            KeyEvent.KEYCODE_A,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, t0 + 560L, t0 + 560L)
        )
        assertTrue("commits=${recorder.committedTexts}", recorder.committedTexts.contains("="))
    }

    @Test
    fun symPlusQuickLauncherShortcut_opensQuickLauncherFromEditableField() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setQuickLauncherTextFieldShortcuts(context, true)
        SettingsManager.setQuickLauncherShortcut(context, KeyEvent.KEYCODE_SPACE)
        val t0 = 4_200L

        val symHandled = service.onKeyDown(
            KeyEvent.KEYCODE_SYM,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SYM, t0, t0)
        )
        val spaceHandled = service.onKeyDown(
            KeyEvent.KEYCODE_SPACE,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE, t0, t0 + 40L)
        )

        val startedIntent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertTrue(symHandled)
        assertTrue(spaceHandled)
        assertEquals(QuickLauncherActivity::class.java.name, startedIntent.component?.className)
        assertTrue(startedIntent.getBooleanExtra(QuickLauncherActivity.EXTRA_TOGGLE_REQUEST, false))
        assertFalse("Space should not be committed when opening QuickLauncher", recorder.committedTexts.contains(" "))
    }

    @Test
    fun heldSymPlusQuickLauncherShortcut_reopensFromEditableFieldAfterLauncherCloses() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setQuickLauncherTextFieldShortcuts(context, true)
        SettingsManager.setQuickLauncherShortcut(context, KeyEvent.KEYCODE_SPACE)
        val t0 = 4_600L

        val handled = service.onKeyDown(
            KeyEvent.KEYCODE_SPACE,
            keyEvent(
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_SPACE,
                t0,
                t0 + 40L,
                KeyEvent.META_SYM_ON
            )
        )

        val startedIntent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
        assertTrue(handled)
        assertEquals(QuickLauncherActivity::class.java.name, startedIntent.component?.className)
        assertTrue(startedIntent.getBooleanExtra(QuickLauncherActivity.EXTRA_TOGGLE_REQUEST, false))
        assertFalse("Space should not be committed when reopening QuickLauncher", recorder.committedTexts.contains(" "))
    }

    @Test
    fun vietnameseTelexLayout_rewritesThroughImePath() {
        LayoutMappingRepository.loadLayout(service.assets, "vietnamese_telex_qwerty", service)
        setField(service, "activeKeyboardLayoutName", "vietnamese_telex_qwerty")
        recorder.textBeforeCursor = "ca"

        val handled = service.onKeyDown(
            KeyEvent.KEYCODE_A,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 5_000L, 5_000L)
        )

        assertTrue(handled)
        assertTrue(recorder.deleteSurroundingTextCalls.contains(2 to 0))
        assertTrue("commits=${recorder.committedTexts}", recorder.committedTexts.contains("câ"))
    }

    @Test
    fun vietnameseTelexLayout_ctrlA_shortcutWinsOverTelexRewrite_midWord() {
        LayoutMappingRepository.loadLayout(service.assets, "vietnamese_telex_qwerty", service)
        setField(service, "activeKeyboardLayoutName", "vietnamese_telex_qwerty")
        recorder.textBeforeCursor = "ca"

        tapCtrl(5_200L) // ctrl one-shot
        service.onKeyDown(
            KeyEvent.KEYCODE_A,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 5_260L, 5_260L)
        )

        assertTrue(
            "Expected shortcut path, commits=${recorder.committedTexts}, deleteCalls=${recorder.deleteSurroundingTextCalls}",
            recorder.contextMenuActions.contains(android.R.id.selectAll) || recorder.sentKeyEvents.isNotEmpty()
        )
        assertTrue("Telex rewrite should not delete text", recorder.deleteSurroundingTextCalls.isEmpty())
        assertFalse("Telex rewrite should not commit transformed text", recorder.committedTexts.contains("câ"))
    }

    @Test
    fun vietnameseTelexLayout_physicalCtrlHold_shortcutWinsWhenEventCtrlMetaMissing() {
        LayoutMappingRepository.loadLayout(service.assets, "vietnamese_telex_qwerty", service)
        setField(service, "activeKeyboardLayoutName", "vietnamese_telex_qwerty")
        recorder.textBeforeCursor = "ca"

        val t0 = 5_400L
        service.onKeyDown(
            KeyEvent.KEYCODE_CTRL_LEFT,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, t0, t0)
        )
        service.onKeyDown(
            KeyEvent.KEYCODE_A,
            keyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_A,
                downTime = t0,
                eventTime = t0 + 80L,
                metaState = 0
            )
        )

        assertTrue(
            "Expected shortcut path with physical Ctrl fallback, commits=${recorder.committedTexts}, deleteCalls=${recorder.deleteSurroundingTextCalls}",
            recorder.contextMenuActions.contains(android.R.id.selectAll) || recorder.sentKeyEvents.isNotEmpty()
        )
        assertTrue("Telex rewrite should not delete text", recorder.deleteSurroundingTextCalls.isEmpty())
        assertFalse("Telex rewrite should not commit transformed text", recorder.committedTexts.contains("câ"))
    }

    private fun tapAlt(start: Long) {
        service.onKeyDown(
            KeyEvent.KEYCODE_ALT_LEFT,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT, start, start)
        )
        service.onKeyUp(
            KeyEvent.KEYCODE_ALT_LEFT,
            keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ALT_LEFT, start, start + 30L)
        )
    }

    private fun tapShift(start: Long) {
        service.onKeyDown(
            KeyEvent.KEYCODE_SHIFT_LEFT,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, start, start)
        )
        service.onKeyUp(
            KeyEvent.KEYCODE_SHIFT_LEFT,
            keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, start, start + 30L)
        )
    }

    private fun tapCtrl(start: Long) {
        service.onKeyDown(
            KeyEvent.KEYCODE_CTRL_LEFT,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, start, start)
        )
        service.onKeyUp(
            KeyEvent.KEYCODE_CTRL_LEFT,
            keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, start, start + 30L)
        )
    }

    private fun typeClicksSyntheticAtMacro(start: Long) {
        val shiftMeta = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        service.onKeyDown(
            KeyEvent.KEYCODE_SHIFT_LEFT,
            keyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_SHIFT_LEFT,
                downTime = start,
                eventTime = start,
                metaState = shiftMeta
            )
        )
        service.onKeyDown(
            KeyEvent.KEYCODE_2,
            keyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_2,
                downTime = start,
                eventTime = start,
                metaState = shiftMeta
            )
        )
        service.onKeyUp(
            KeyEvent.KEYCODE_2,
            keyEvent(
                action = KeyEvent.ACTION_UP,
                keyCode = KeyEvent.KEYCODE_2,
                downTime = start,
                eventTime = start + 90L,
                metaState = shiftMeta
            )
        )
        service.onKeyUp(
            KeyEvent.KEYCODE_SHIFT_LEFT,
            keyEvent(
                action = KeyEvent.ACTION_UP,
                keyCode = KeyEvent.KEYCODE_SHIFT_LEFT,
                downTime = start,
                eventTime = start + 120L
            )
        )
    }

    private fun tapSym(start: Long) {
        service.onKeyDown(
            63,
            keyEvent(KeyEvent.ACTION_DOWN, 63, start, start)
        )
        service.onKeyUp(
            63,
            keyEvent(KeyEvent.ACTION_UP, 63, start, start + 20L)
        )
    }

    private fun focusNewField(
        newRecorder: RecordingInputConnection,
        inputType: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    ) {
        recorder = newRecorder
        inputConnection = recorder.asProxy()
        editorInfo = EditorInfo().apply {
            this.inputType = inputType
            packageName = "it.palsoftware.pastiera.test"
        }
        setField(service, "mInputConnection", inputConnection)
        setField(service, "mStartedInputConnection", inputConnection)
        setField(service, "mInputEditorInfo", editorInfo)
        service.onStartInput(editorInfo, false)
    }

    private fun keyEvent(
        action: Int,
        keyCode: Int,
        downTime: Long,
        eventTime: Long,
        metaState: Int = 0,
        repeatCount: Int = 0
    ): KeyEvent {
        return KeyEvent(downTime, eventTime, action, keyCode, repeatCount, metaState)
    }

    private fun modifierController(): ModifierStateController {
        return getField(service, "modifierStateController")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getField(target: Any, fieldName: String): T {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            try {
                val field = cls.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(target) as T
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        error("Field not found: $fieldName")
    }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            try {
                val field: Field = cls.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        error("Field not found: $fieldName")
    }

    private fun invokePrivate(target: Any, methodName: String, vararg args: Any?) {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            val method = cls.declaredMethods.firstOrNull { candidate ->
                candidate.name == methodName && candidate.parameterCount == args.size
            }
            if (method != null) {
                method.isAccessible = true
                method.invoke(target, *args)
                return
            }
            cls = cls.superclass
        }
        error("Method not found: $methodName")
    }

    private class RecordingInputConnection {
        var textBeforeCursor: String = ""
        val committedTexts = mutableListOf<String>()
        val composingTexts = mutableListOf<String>()
        val sentKeyEvents = mutableListOf<KeyEvent>()
        val contextMenuActions = mutableListOf<Int>()
        val deleteSurroundingTextCalls = mutableListOf<Pair<Int, Int>>()

        fun asProxy(): InputConnection {
            return Proxy.newProxyInstance(
                InputConnection::class.java.classLoader,
                arrayOf(InputConnection::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "commitText" -> {
                        val text = args?.getOrNull(0)?.toString()
                        if (text != null) {
                            committedTexts += text
                            textBeforeCursor += text
                        }
                        true
                    }
                    "setComposingText" -> {
                        val text = args?.getOrNull(0)?.toString()
                        if (text != null) composingTexts += text
                        true
                    }
                    "deleteSurroundingText" -> {
                        val before = (args?.getOrNull(0) as? Int) ?: 0
                        val after = (args?.getOrNull(1) as? Int) ?: 0
                        deleteSurroundingTextCalls += before to after
                        if (before > 0 && textBeforeCursor.isNotEmpty()) {
                            val keep = (textBeforeCursor.length - before).coerceAtLeast(0)
                            textBeforeCursor = textBeforeCursor.take(keep)
                        }
                        true
                    }
                    "sendKeyEvent" -> {
                        val event = args?.getOrNull(0) as? KeyEvent
                        if (event != null) sentKeyEvents += event
                        true
                    }
                    "performContextMenuAction" -> {
                        val id = args?.getOrNull(0) as? Int
                        if (id != null) contextMenuActions += id
                        true
                    }
                    "getTextBeforeCursor" -> {
                        val count = (args?.getOrNull(0) as? Int) ?: textBeforeCursor.length
                        textBeforeCursor.takeLast(count)
                    }
                    "getExtractedText" -> ExtractedText().apply {
                        text = textBeforeCursor
                        selectionStart = textBeforeCursor.length
                        selectionEnd = textBeforeCursor.length
                    }
                    "beginBatchEdit", "endBatchEdit", "finishComposingText" -> true
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
