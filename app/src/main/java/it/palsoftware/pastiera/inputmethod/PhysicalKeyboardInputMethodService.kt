package it.palsoftware.pastiera.inputmethod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import it.palsoftware.pastiera.AppBroadcastActions
import it.palsoftware.pastiera.ClicksPowerKeyboardController
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.SoftwareKeyboardModeActions
import android.inputmethodservice.InputMethodService
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.CursorAnchorInfo
import it.palsoftware.pastiera.clipboard.ClipboardDao
import it.palsoftware.pastiera.inputmethod.KeyboardEventTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.NotificationHelper
import it.palsoftware.pastiera.core.AutoCorrectionManager
import it.palsoftware.pastiera.core.DeferredPunctuationSpaceTracker
import it.palsoftware.pastiera.core.InputContextState
import it.palsoftware.pastiera.core.ModifierStateController
import it.palsoftware.pastiera.core.NavModeController
import it.palsoftware.pastiera.core.SymLayoutController
import it.palsoftware.pastiera.core.TextInputController
import it.palsoftware.pastiera.core.suggestions.SuggestionController
import it.palsoftware.pastiera.core.suggestions.SuggestionKind
import it.palsoftware.pastiera.core.suggestions.SuggestionResult
import it.palsoftware.pastiera.core.suggestions.SuggestionSettings
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.data.layout.LayoutFileStore
import it.palsoftware.pastiera.data.layout.LayoutMapping
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import it.palsoftware.pastiera.data.variation.VariationRepository
import it.palsoftware.pastiera.inputmethod.SpeechRecognitionActivity
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils
import it.palsoftware.pastiera.inputmethod.aospkeyboard.AospKeyboardView
import it.palsoftware.pastiera.inputmethod.aospkeyboard.SoftwareKeyboardLayoutTemplates
import it.palsoftware.pastiera.inputmethod.aospkeyboard.SoftwareKeyboardSymLabels
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils.localeString
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils.setAdditionalInputMethodSubtypesCompat
import it.palsoftware.pastiera.inputmethod.telex.VietnameseTelexProcessor
import it.palsoftware.pastiera.inputmethod.trackpad.TrackpadEventDeviceResolver
import it.palsoftware.pastiera.inputmethod.trackpad.TrackpadGestureDetector
import it.palsoftware.pastiera.inputmethod.expansion.ExpansionRuntimeConfig
import it.palsoftware.pastiera.inputmethod.expansion.ExpansionTriggerKind
import it.palsoftware.pastiera.inputmethod.expansion.SnippetExpansionSource
import it.palsoftware.pastiera.inputmethod.expansion.EmojiShortcodeSource
import it.palsoftware.pastiera.inputmethod.expansion.SymbolShortcodeSource
import it.palsoftware.pastiera.inputmethod.expansion.TextExpansionController
import java.util.Locale
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import it.palsoftware.pastiera.clipboard.ClipboardHistoryManager
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Input method service specialized for physical keyboards.
 * Handles advanced features such as long press that simulates Alt+key.
 */
class PhysicalKeyboardInputMethodService : InputMethodService(), ClicksAccessibilityKeyBridge.Target {

    companion object {
        private const val TAG = "PastieraInputMethod"
        private const val TRACKPAD_DEBUG_TAG = "TrackpadDebug"
        private const val NATIVE_TRACKPAD_MIN_SWIPE_VELOCITY_PX_PER_MS = 2f
        private const val KEYBOARD_SURFACE_TRANSITION_DELAY_MS = 32L
        private const val KEYBOARD_DEVICE_SURFACE_TRANSITION_DELAY_MS = 250L
        private const val MODIFIER_ICON_OFF = 0
        private const val MODIFIER_ICON_ACTIVE = 1
        private const val MODIFIER_ICON_LOCKED = 2
        private const val DISCORD_PACKAGE_NAME = "com.discord"
        private val MESSENGER_ENTER_BEHAVIOR_PACKAGES = setOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "org.thoughtcrime.securesms",
            DISCORD_PACKAGE_NAME,
            "im.vector.app",
            "com.google.android.apps.messaging",
            "ch.threema.app",
            "ch.threema.app.libre",
            "com.instagram.android"
        )
        private val ENTER_BEHAVIOR_SEND_ACTION_PACKAGES = MESSENGER_ENTER_BEHAVIOR_PACKAGES -
            DISCORD_PACKAGE_NAME
        private val SOFTWARE_PREVIEW_KEY_CODES = listOf(
            KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I,
            KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S,
            KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_Z,
            KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B,
            KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_COMMA,
            KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_ENTER
        )
    }

    // SharedPreferences for settings
    private lateinit var prefs: SharedPreferences
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var lastSystemLocalesSignature: String = ""

    private lateinit var altSymManager: AltSymManager
    
    // Speech recognition using SpeechRecognizer (modern approach)
    private var speechRecognitionManager: SpeechRecognitionManager? = null
    private var isSpeechRecognitionActive: Boolean = false
    private var pendingSpeechRecognition: Boolean = false
    
    // Broadcast receiver for speech recognition (deprecated, kept for backwards compatibility)
    private var speechResultReceiver: BroadcastReceiver? = null
    // Broadcast receiver for permission request result
    private var permissionResultReceiver: BroadcastReceiver? = null
    // Broadcast receiver for user dictionary updates
    private var userDictionaryReceiver: BroadcastReceiver? = null
    // Broadcast receiver for additional IME subtypes updates
    private var additionalSubtypesReceiver: BroadcastReceiver? = null
    private lateinit var candidatesBarController: CandidatesBarController
    private lateinit var textExpansionController: TextExpansionController
    private lateinit var emojiShortcodeSource: EmojiShortcodeSource
    private lateinit var symbolShortcodeSource: SymbolShortcodeSource
    private val expansionAssetScope = CoroutineScope(Dispatchers.IO)

    // Keycode for the SYM key
    private val KEYCODE_SYM = 63

    // Minimal Phone (MP01) custom hardware keycodes
    private val KEYCODE_EM = 666  // Emoji key
    private val KEYCODE_MIC = 667 // Mic / speech-to-text key

    // Single instance to show toasts without overlapping
    private var lastLayoutToastText: String? = null
    private var lastLayoutToastTime: Long = 0
    private var suppressNextLayoutReload: Boolean = false
    private var activeKeyboardLayoutName: String = "qwerty"
    private var consumeAltEnterUntilKeyUp: Boolean = false
    private var dispatchingSoftwareKeyboardKey: Boolean = false
    
    // Aggiungi per Power Shortcuts
    private var powerShortcutToast: android.widget.Toast? = null
    
    // Mapping Ctrl+key -> action or keycode (loaded from JSON)
    private val ctrlKeyMap = mutableMapOf<Int, KeyMappingLoader.CtrlMapping>()
    
    // Accessor properties for backwards compatibility with existing code
    private var capsLockEnabled: Boolean
        get() = modifierStateController.capsLockEnabled
        set(value) { modifierStateController.capsLockEnabled = value }
    
    private var shiftPressed: Boolean
        get() = modifierStateController.shiftPressed
        set(value) { modifierStateController.shiftPressed = value }
    
    private var ctrlLatchActive: Boolean
        get() = modifierStateController.ctrlLatchActive
        set(value) { modifierStateController.ctrlLatchActive = value }
    
    private var altLatchActive: Boolean
        get() = modifierStateController.altLatchActive
        set(value) { modifierStateController.altLatchActive = value }
    
    private var ctrlPressed: Boolean
        get() = modifierStateController.ctrlPressed
        set(value) { modifierStateController.ctrlPressed = value }
    
    private var altPressed: Boolean
        get() = modifierStateController.altPressed
        set(value) { modifierStateController.altPressed = value }
    
    private var shiftPhysicallyPressed: Boolean
        get() = modifierStateController.shiftPhysicallyPressed
        set(value) { modifierStateController.shiftPhysicallyPressed = value }
    
    private var ctrlPhysicallyPressed: Boolean
        get() = modifierStateController.ctrlPhysicallyPressed
        set(value) { modifierStateController.ctrlPhysicallyPressed = value }
    
    private var altPhysicallyPressed: Boolean
        get() = modifierStateController.altPhysicallyPressed
        set(value) { modifierStateController.altPhysicallyPressed = value }
    
    private var shiftOneShot: Boolean
        get() = modifierStateController.shiftOneShot
        set(value) { modifierStateController.shiftOneShot = value }

    private var ctrlOneShot: Boolean
        get() = modifierStateController.ctrlOneShot
        set(value) { modifierStateController.ctrlOneShot = value }
    
    private var altOneShot: Boolean
        get() = modifierStateController.altOneShot
        set(value) { modifierStateController.altOneShot = value }
    
    private var ctrlLatchFromNavMode: Boolean
        get() = modifierStateController.ctrlLatchFromNavMode
        set(value) { modifierStateController.ctrlLatchFromNavMode = value }
    
    // Flag to track whether we are in a valid input context
    private var isInputViewActive = false
    private var emojiSearchExternalSelectionStart: Int? = null
    private var emojiSearchExternalSelectionEnd: Int? = null
    private var emojiSearchCursorAnchorMonitoringRequested: Boolean = false
    private var ignoreNextEmojiSearchCursorAnchorUpdate: Boolean = false
    
    // Snapshot of the current input context (numeric/password/restricted fields, etc.)
    private var inputContextState: InputContextState = InputContextState.EMPTY
    
    private val isNumericField: Boolean
        get() = inputContextState.isNumericField
    
    private val shouldDisableSmartFeatures: Boolean
        get() = inputContextState.shouldDisableSmartFeatures

    private val shouldDisableAutoCapitalize: Boolean
        get() {
            if (!inputContextState.shouldDisableAutoCapitalize) return false
            val includeRestrictedFields = SettingsManager.getAutoCapitalizeRestrictedFields(this)
            return !includeRestrictedFields || inputContextState.isPasswordField
        }
    
    // Current package name
    private var currentPackageName: String? = null
    
    // Constants
    private val DOUBLE_TAP_THRESHOLD = 500L
    private val CURSOR_UPDATE_DELAY = 50L
    private val MULTI_TAP_TIMEOUT_MS = 400L

    // Modifier/nav/SYM controllers
    private lateinit var modifierStateController: ModifierStateController
    private lateinit var navModeController: NavModeController
    private lateinit var symLayoutController: SymLayoutController
    private lateinit var textInputController: TextInputController
    private lateinit var autoCorrectionManager: AutoCorrectionManager
    private lateinit var suggestionController: SuggestionController
    private lateinit var variationStateController: VariationStateController
    private lateinit var inputEventRouter: InputEventRouter
    private lateinit var typingSoundPlayer: TypingSoundPlayer
    private var skipNextSelectionUpdateAfterCommit: Boolean = false
    private var editorHasActiveSelection: Boolean = false
    private lateinit var keyboardVisibilityController: KeyboardVisibilityController
    private lateinit var launcherShortcutController: LauncherShortcutController
    private lateinit var clipboardHistoryManager: ClipboardHistoryManager
    private var latestSuggestionResults: List<SuggestionResult> = emptyList()
    private var lastRenderedStatusSnapshot: StatusBarController.StatusSnapshot? = null
    private var lastRenderedEmojiMapText: String? = null
    private var lastRenderedSymMappings: Map<Int, String>? = null
    private var lastRenderedStatusInputConnection: android.view.inputmethod.InputConnection? = null
    private var lastRenderedPastierinaModeActive: Boolean? = null
    private var lastRenderedSoftwareKeyboardMode: SettingsManager.SoftwareKeyboardMode? = null
    private var lastRenderedModifierIndicators: Set<String>? = null
    private var requestedInputViewShown: Boolean = true
    private var suppressedAutoCapContextKey: String? = null
    private var clearAltOnSpaceEnabled: Boolean = false
    private var physicalKeyboardProfileOverride: String = "auto"
    private var isLanguageSwitchInProgress: Boolean = false
    // Stato per ricordare se il nav mode era attivo prima di entrare in un campo di testo
    private var navModeWasActiveBeforeEditableField: Boolean = false

    // Trackpad gesture detection
    private val trackpadScope = CoroutineScope(Dispatchers.IO)
    private lateinit var trackpadGestureDetector: TrackpadGestureDetector
    private var modifierStateBeforeHold: it.palsoftware.pastiera.core.ModifierStateController.LogicalState? = null
    private var variationInteractedDuringHold: Boolean = false
    private var modifierDownTimes = mutableMapOf<Int, Long>()
    private var otherKeyInteractedDuringHold: Boolean = false
    private var shiftLayerLatched: Boolean = false
    private var altLayerLatched: Boolean = false
    private var lastShiftTapUpTime: Long = 0L
    private var lastAltTapUpTime: Long = 0L
    private var symTogglePendingOnKeyUp: Boolean = false
    private var symChordUsedSinceKeyDown: Boolean = false
    private var nativeTrackpadGestureStart: NativeTrackpadGestureStart? = null
    private var nativeTrackpadLastX: Float = 0f
    private var nativeTrackpadLastY: Float = 0f
    private var nativeTrackpadLastEventTimeUptimeMs: Long = 0L
    private var nativeTrackpadGestureHandled: Boolean = false
    private var nativeTrackpadGestureAtMs: Long = 0L
    private var trackpadDecorMotionView: View? = null

    private val multiTapHandler = Handler(Looper.getMainLooper())
    private val multiTapController = MultiTapController(
        handler = multiTapHandler,
        timeoutMs = MULTI_TAP_TIMEOUT_MS
    )
    private val bounceKeyFilter = BounceKeyFilter()
    private val accidentalKeyPressFilter = AccidentalKeyPressFilter()
    private val physicalKeyResolver = PhysicalKeyResolver()
    private val clicksPowerButtonEventMapper = ClicksPowerButtonEventMapper()
    private var dispatchingClicksAccessibilityKeyEvent = false
    private var replayingProtectedNumberKey = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private var inputManager: InputManager? = null
    private var lastObservedAutoSoftwareKeyboardMode: SettingsManager.SoftwareKeyboardMode? = null
    private var pendingInputDeviceModeRefresh: Runnable? = null
    private var pendingKeyboardSurfaceTransition: Runnable? = null
    private var clicksConnectionChangePending: Boolean = false
    private var clicksDisconnectPending: Boolean = false
    private val connectedClicksInputDeviceIds = mutableSetOf<Int>()
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            SoftwareKeyboardAutoDetector.onInputDevicesChanged()
            val clicksConnected = InputDevice.getDevice(deviceId)
                ?.takeIf(DeviceSpecific::isClicksPowerKeyboard)
                ?.let { connectedClicksInputDeviceIds.add(deviceId) } == true
            scheduleInputDeviceModeRefresh(clicksConnectionChanged = clicksConnected)
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            accidentalKeyPressFilter.resetDevice(deviceId)
            clicksPowerButtonEventMapper.resetDevice(deviceId)
            val clicksDisconnected = connectedClicksInputDeviceIds.remove(deviceId)
            if (
                clicksDisconnected &&
                SettingsManager.getClicksCloseInputOnDisconnect(this@PhysicalKeyboardInputMethodService)
            ) {
                SoftwareKeyboardAutoDetector.beginClosingInputForClicksDisconnect()
                requestHideSelf(0)
            }
            SoftwareKeyboardAutoDetector.onInputDevicesChanged()
            scheduleInputDeviceModeRefresh(
                clicksConnectionChanged = clicksDisconnected,
                clicksDisconnected = clicksDisconnected
            )
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            accidentalKeyPressFilter.resetDevice(deviceId)
            clicksPowerButtonEventMapper.resetDevice(deviceId)
            SoftwareKeyboardAutoDetector.onInputDevicesChanged()
            val wasClicksKeyboard = deviceId in connectedClicksInputDeviceIds
            val device = InputDevice.getDevice(deviceId)
            val isClicksKeyboard = device != null && DeviceSpecific.isClicksPowerKeyboard(device)
            if (isClicksKeyboard) {
                connectedClicksInputDeviceIds += deviceId
            } else {
                connectedClicksInputDeviceIds -= deviceId
            }
            scheduleInputDeviceModeRefresh(
                clicksConnectionChanged = wasClicksKeyboard != isClicksKeyboard
            )
        }
    }
    private var pendingStatusBarUpdate: Runnable? = null
    private var lastSystemStatusIconResId: Int? = null
    private var pendingSelectionAutoCapCheck: Runnable? = null
    private val clipboardCleanupIntervalMs = 60_000L
    private val clipboardCleanupRunnable = object : Runnable {
        override fun run() {
            val retention = SettingsManager.getClipboardRetentionTime(this@PhysicalKeyboardInputMethodService)
            clipboardHistoryManager.prepareClipboardHistory()
            val count = clipboardHistoryManager.getHistorySize()
            uiHandler.post {
                if (::candidatesBarController.isInitialized) {
                    candidatesBarController.updateClipboardCount(count)
                }
            }
            uiHandler.postDelayed(this, clipboardCleanupIntervalMs)
        }
    }

    private fun startClipboardCleanupTimer() {
        uiHandler.removeCallbacks(clipboardCleanupRunnable)
        uiHandler.postDelayed(clipboardCleanupRunnable, clipboardCleanupIntervalMs)
    }

    private fun stopClipboardCleanupTimer() {
        uiHandler.removeCallbacks(clipboardCleanupRunnable)
    }

    private val symPage: Int
        get() = if (::symLayoutController.isInitialized) symLayoutController.currentSymPage() else 0

    private fun updateInputContextState(info: EditorInfo?) {
        inputContextState = InputContextState.fromEditorInfo(info)
    }

    private fun markSelectionUpdateSkipAfterCommit() {
        skipNextSelectionUpdateAfterCommit = true
        if (SettingsManager.isSuggestionDebugLoggingEnabled(this)) {
            Log.d(TAG, "markSelectionUpdateSkipAfterCommit() set skip flag")
        }
    }

    @Suppress("DEPRECATION")
    private fun updateNavModeStatusIcon(isActive: Boolean) {
        // Deprecated but still works on current Android versions; use for quick nav mode indicator.
        if (isActive) {
            showStatusIcon(R.drawable.ic_nav_mode_status)
            lastSystemStatusIconResId = R.drawable.ic_nav_mode_status
        } else {
            hideStatusIcon()
            lastSystemStatusIconResId = null
        }
    }

    @Suppress("DEPRECATION")
    private fun updateSystemStatusModifierIcon(
        snapshot: StatusBarController.StatusSnapshot,
        effectiveSoftwareKeyboardMode: SettingsManager.SoftwareKeyboardMode
    ) {
        if (snapshot.navModeActive) {
            if (lastSystemStatusIconResId != R.drawable.ic_nav_mode_status) {
                showStatusIcon(R.drawable.ic_nav_mode_status)
                lastSystemStatusIconResId = R.drawable.ic_nav_mode_status
            }
            return
        }

        val iconResId = if (
            SettingsManager.getModifierIndicatorShowsMenuBar(this) &&
            effectiveSoftwareKeyboardMode != SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        ) {
            systemStatusModifierIconResId(snapshot)
        } else {
            null
        }

        if (iconResId == lastSystemStatusIconResId) {
            return
        }

        if (iconResId != null) {
            showStatusIcon(iconResId)
        } else {
            hideStatusIcon()
        }
        lastSystemStatusIconResId = iconResId
    }

    private fun systemStatusModifierIconResId(snapshot: StatusBarController.StatusSnapshot): Int? {
        val shiftState = when {
            snapshot.capsLockEnabled -> MODIFIER_ICON_LOCKED
            snapshot.shiftPhysicallyPressed || snapshot.shiftOneShot -> MODIFIER_ICON_ACTIVE
            else -> MODIFIER_ICON_OFF
        }
        val ctrlState = when {
            snapshot.ctrlLatchActive -> MODIFIER_ICON_LOCKED
            snapshot.ctrlPhysicallyPressed || snapshot.ctrlOneShot -> MODIFIER_ICON_ACTIVE
            else -> MODIFIER_ICON_OFF
        }
        val altState = when {
            snapshot.altLatchActive -> MODIFIER_ICON_LOCKED
            snapshot.altPhysicallyPressed || snapshot.altOneShot -> MODIFIER_ICON_ACTIVE
            else -> MODIFIER_ICON_OFF
        }

        return modifierCombinationStatusIconResId(
            shiftState = shiftState,
            ctrlState = ctrlState,
            altState = altState
        ) ?: if (snapshot.symPage > 0) R.drawable.ic_status_modifier_sym else null
    }

    private fun modifierCombinationStatusIconResId(
        shiftState: Int,
        ctrlState: Int,
        altState: Int
    ): Int? = when ("$shiftState$ctrlState$altState") {
        "001" -> R.drawable.ic_status_modifiers_s0_c0_a1
        "002" -> R.drawable.ic_status_modifiers_s0_c0_a2
        "010" -> R.drawable.ic_status_modifiers_s0_c1_a0
        "011" -> R.drawable.ic_status_modifiers_s0_c1_a1
        "012" -> R.drawable.ic_status_modifiers_s0_c1_a2
        "020" -> R.drawable.ic_status_modifiers_s0_c2_a0
        "021" -> R.drawable.ic_status_modifiers_s0_c2_a1
        "022" -> R.drawable.ic_status_modifiers_s0_c2_a2
        "100" -> R.drawable.ic_status_modifiers_s1_c0_a0
        "101" -> R.drawable.ic_status_modifiers_s1_c0_a1
        "102" -> R.drawable.ic_status_modifiers_s1_c0_a2
        "110" -> R.drawable.ic_status_modifiers_s1_c1_a0
        "111" -> R.drawable.ic_status_modifiers_s1_c1_a1
        "112" -> R.drawable.ic_status_modifiers_s1_c1_a2
        "120" -> R.drawable.ic_status_modifiers_s1_c2_a0
        "121" -> R.drawable.ic_status_modifiers_s1_c2_a1
        "122" -> R.drawable.ic_status_modifiers_s1_c2_a2
        "200" -> R.drawable.ic_status_modifiers_s2_c0_a0
        "201" -> R.drawable.ic_status_modifiers_s2_c0_a1
        "202" -> R.drawable.ic_status_modifiers_s2_c0_a2
        "210" -> R.drawable.ic_status_modifiers_s2_c1_a0
        "211" -> R.drawable.ic_status_modifiers_s2_c1_a1
        "212" -> R.drawable.ic_status_modifiers_s2_c1_a2
        "220" -> R.drawable.ic_status_modifiers_s2_c2_a0
        "221" -> R.drawable.ic_status_modifiers_s2_c2_a1
        "222" -> R.drawable.ic_status_modifiers_s2_c2_a2
        else -> null
    }

    private fun refreshStatusBar() {
        updateStatusBarText()
    }

    private fun scheduleInputDeviceModeRefresh(
        clicksConnectionChanged: Boolean = false,
        clicksDisconnected: Boolean = false
    ) {
        clicksConnectionChangePending = clicksConnectionChangePending || clicksConnectionChanged
        clicksDisconnectPending = clicksDisconnectPending || clicksDisconnected
        pendingInputDeviceModeRefresh?.let { uiHandler.removeCallbacks(it) }
        val refresh = Runnable {
            pendingInputDeviceModeRefresh = null
            val didClicksConnectionChange = clicksConnectionChangePending
            val didClicksDisconnect = clicksDisconnectPending
            clicksConnectionChangePending = false
            clicksDisconnectPending = false
            refreshSoftwareKeyboardModeForConnectedDevices(
                clicksConnectionChanged = didClicksConnectionChange,
                clicksDisconnected = didClicksDisconnect
            )
        }
        pendingInputDeviceModeRefresh = refresh
        uiHandler.postDelayed(refresh, 120L)
    }

    private fun refreshSoftwareKeyboardModeForConnectedDevices(
        clicksConnectionChanged: Boolean,
        clicksDisconnected: Boolean
    ) {
        val autoMode = SoftwareKeyboardAutoDetector.resolve(this)
        val previousAutoMode = lastObservedAutoSoftwareKeyboardMode
        lastObservedAutoSoftwareKeyboardMode = autoMode
        val configuredMode = SettingsManager.getSoftwareKeyboardMode(this)
        val transition = SoftwareKeyboardDeviceTransitionPolicy.plan(
            configuredMode = configuredMode,
            previousAutoMode = previousAutoMode,
            autoMode = autoMode,
            clicksConnectionChanged = clicksConnectionChanged,
            clicksDisconnected = clicksDisconnected,
            closeInputOnClicksDisconnect = SettingsManager.getClicksCloseInputOnDisconnect(this)
        ) ?: return
        if (transition.clearTemporaryOverride) {
            SoftwareKeyboardModeActions.clearTemporaryMode(this)
        }
        scheduleKeyboardSurfaceTransition(
            mode = transition.mode,
            closeInput = transition.closeInput,
            requireActiveTextField = SettingsManager.getClicksShowKeyboardOnlyWithTextFocus(this),
            delayMs = KEYBOARD_DEVICE_SURFACE_TRANSITION_DELAY_MS
        )
    }

    private fun scheduleKeyboardSurfaceTransition(
        mode: SettingsManager.SoftwareKeyboardMode,
        closeInput: Boolean = false,
        requireActiveTextField: Boolean = false,
        delayMs: Long = KEYBOARD_SURFACE_TRANSITION_DELAY_MS
    ) {
        pendingKeyboardSurfaceTransition?.let(uiHandler::removeCallbacks)
        val transition = Runnable {
            pendingKeyboardSurfaceTransition = null
            if (::textExpansionController.isInitialized) textExpansionController.clear()
            invalidateRenderedStatusSnapshot()
            if (closeInput) {
                requestHideSelf(0)
                return@Runnable
            }
            keyboardVisibilityController.onKeyboardSurfaceChanged(
                ensureInputViewShown = mode == SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
                requireActiveTextField = requireActiveTextField
            )
        }
        pendingKeyboardSurfaceTransition = transition
        // A status-bar tap must finish dispatching before its own IME surface is replaced.
        // Two UI frames avoid InputDispatcher waiting for the disappearing touch target.
        uiHandler.postDelayed(transition, delayMs)
    }

    private fun toggleSoftwareKeyboardModeFromStatusBar() {
        val next = SoftwareKeyboardModeActions.toggleTemporaryMode(this)
        if (SettingsManager.getSoftwareKeyboardModeToggleToastsEnabled(this)) {
            val message = when (next) {
                SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL ->
                    getString(R.string.software_keyboard_mode_toggle_now_virtual)
                SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE ->
                    getString(R.string.software_keyboard_mode_toggle_now_hardware)
                SettingsManager.SoftwareKeyboardMode.AUTO ->
                    getString(R.string.software_keyboard_mode_auto_short)
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun scheduleStatusBarTextUpdate(delayMs: Long = CURSOR_UPDATE_DELAY) {
        pendingStatusBarUpdate?.let { uiHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingStatusBarUpdate = null
            updateStatusBarText()
        }
        pendingStatusBarUpdate = runnable
        uiHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelPendingSelectionDrivenUiWork() {
        pendingStatusBarUpdate?.let { uiHandler.removeCallbacks(it) }
        pendingStatusBarUpdate = null
        pendingSelectionAutoCapCheck?.let { uiHandler.removeCallbacks(it) }
        pendingSelectionAutoCapCheck = null
    }

    private fun invalidateRenderedStatusSnapshot() {
        lastRenderedStatusSnapshot = null
        lastRenderedEmojiMapText = null
        lastRenderedSymMappings = null
        lastRenderedStatusInputConnection = null
        lastRenderedPastierinaModeActive = null
        lastRenderedSoftwareKeyboardMode = null
        lastRenderedModifierIndicators = null
    }

    private fun checkAutoCapitalizeOnSelectionChange(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int
    ) {
        val perfStart = ImePerfLogger.mark()
        val state = inputContextState
        try {
            AutoCapitalizeHelper.checkAutoCapitalizeOnSelectionChange(
                this,
                currentInputConnection,
                shouldDisableAutoCapitalize,
                oldSelStart,
                oldSelEnd,
                newSelStart,
                newSelEnd,
                enableShift = { requestAutoCapShiftOneShot() },
                disableShift = { modifierStateController.consumeShiftOneShot() },
                onUpdateStatusBar = { updateStatusBarText() },
                inputContextState = state
            )
        } finally {
            ImePerfLogger.logDuration(
                label = "checkAutoCapitalizeOnSelectionChange",
                startNanos = perfStart,
                thresholdMs = 8L,
                details = "pkg=$currentPackageName"
            )
        }
    }

    private fun scheduleAutoCapitalizeOnSelectionChange(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int
    ) {
        pendingSelectionAutoCapCheck?.let { uiHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingSelectionAutoCapCheck = null
            checkAutoCapitalizeOnSelectionChange(oldSelStart, oldSelEnd, newSelStart, newSelEnd)
        }
        pendingSelectionAutoCapCheck = runnable
        uiHandler.postDelayed(runnable, CURSOR_UPDATE_DELAY * 2)
    }

    private fun isPureModifierKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
            keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT ||
            keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
            keyCode == KeyEvent.KEYCODE_CTRL_RIGHT ||
            keyCode == KeyEvent.KEYCODE_ALT_LEFT ||
            keyCode == KeyEvent.KEYCODE_ALT_RIGHT ||
            keyCode == KEYCODE_SYM
    }

    private fun isMinimalPhoneHardwareActive(): Boolean {
        return DeviceSpecific.isMinimalPhoneDevice(physicalKeyboardProfileOverride)
    }

    private fun openQuickLauncher(): Boolean = QuickLauncherOpener.open(this)
    
    /**
     * Starts voice input using SpeechRecognizer via SpeechRecognitionManager.
     */
    private fun startSpeechRecognition() {
        // If recognition is already active, toggle it off
        if (isSpeechRecognitionActive) {
            stopSpeechRecognition()
            return
        }
        
        // Check microphone permission first
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "RECORD_AUDIO permission not granted, requesting...")
            pendingSpeechRecognition = true
            val intent = Intent(this, PermissionRequestActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            return
        }
        
        // Initialize manager if not already created
        if (speechRecognitionManager == null) {
            speechRecognitionManager = SpeechRecognitionManager(
                context = this,
                inputConnectionProvider = { currentInputConnection },
                onError = { errorMessage ->
                    Log.e(TAG, "Speech recognition error: $errorMessage")
                },
                onRecognitionStateChanged = { isActive ->
                    // Update internal state
                    isSpeechRecognitionActive = isActive
                    
                    // Reset Alt and Ctrl modifiers when recognition starts
                    if (isActive) {
                        modifierStateController.clearAltState()
                        modifierStateController.clearCtrlState()
                    }
                    
                    // Update microphone button color and hint message based on recognition state
                    uiHandler.post {
                        candidatesBarController.setMicrophoneButtonActive(isActive)
                        candidatesBarController.showSpeechRecognitionHint(isActive)
                        // Reset audio level when recognition stops
                        if (!isActive) {
                            candidatesBarController.updateMicrophoneAudioLevel(-10f)
                        } else {
                            // Update status bar after resetting modifiers
                            updateStatusBarText()
                        }
                    }
                },
                shouldDisableAutoCapitalize = { shouldDisableAutoCapitalize },
                onAudioLevelChanged = { rmsdB ->
                    // Update microphone button based on audio level
                    uiHandler.post {
                        candidatesBarController.updateMicrophoneAudioLevel(rmsdB)
                    }
                }
            )
        }
        
        speechRecognitionManager?.startRecognition()
    }

    /**
     * Stops voice input if active.
     */
    private fun stopSpeechRecognition() {
        speechRecognitionManager?.stopRecognition()
    }

    private fun getSuggestionSettings(): SuggestionSettings {
        val suggestionsEnabled = SettingsManager.getSuggestionsEnabled(this)
        return SuggestionSettings(
            textReplacementsEnabled = SettingsManager.getAutoCorrectEnabled(this),
            suggestionsEnabled = suggestionsEnabled,
            accentMatching = SettingsManager.getAccentMatchingEnabled(this),
            autoReplaceOnSpaceEnter = SettingsManager.getAutoReplaceOnSpaceEnter(this),
            maxAutoReplaceDistance = SettingsManager.getMaxAutoReplaceDistance(this),
            maxSuggestions = 3,
            useKeyboardProximity = SettingsManager.getUseKeyboardProximity(this),
            useEditTypeRanking = SettingsManager.getUseEditTypeRanking(this),
            frenchPunctuationSpacing = SettingsManager.shouldApplyFrenchPunctuationSpacing(this),
            commaSpace = SettingsManager.getCommaSpace(this),
            autoSpacePunctuation = SettingsManager.getAutoSpacePunctuation(this)
        )
    }

    private fun clearAltOnBoundaryIfNeeded(keyCode: Int, updateStatusBar: () -> Unit) {
        if (!clearAltOnSpaceEnabled) return
        val isBoundary = keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER
        if (!isBoundary) return
        val hasAlt = altLatchActive || altOneShot
        if (!hasAlt) return
        if (altLatchActive && SettingsManager.getAltLatchStaysOnSpace(this)) {
            altOneShot = false
            updateStatusBar()
            return
        }
        modifierStateController.clearAltState()
        updateStatusBar()
    }

    private fun sendCtrlShortcut(keyCode: Int, shift: Boolean = false): Boolean {
        val ic = currentInputConnection ?: return false
        val now = System.currentTimeMillis()
        val metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or
            if (shift) {
                KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            } else {
                0
            }
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState))
        return true
    }

    /**
     * Resolves a meaningful editor action for Enter. Returns null for unspecified fields
     * or when actions are explicitly disabled. Works for both single-line and multiline fields.
     */
    private fun resolveEditorAction(info: EditorInfo?): Int? {
        if (info == null) return null
        val imeOptions = info.imeOptions
        if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) {
            return null
        }

        val action = when {
            info.actionId != 0 -> info.actionId
            else -> imeOptions and EditorInfo.IME_MASK_ACTION
        }

        return when (action) {
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_PREVIOUS -> action
            else -> null
        }
    }

    private fun resolveAppEnterBehavior(info: EditorInfo?): String? {
        val packageName = info?.packageName ?: return null
        if (packageName !in MESSENGER_ENTER_BEHAVIOR_PACKAGES) return null
        if (!SettingsManager.getAppEnterBehaviorEnabled(this)) return null

        val override = SettingsManager.getAppEnterBehaviorOverrides(this)
            .firstOrNull { it.packageName == packageName }
            ?.behavior
        if (override != null && override != SettingsManager.ENTER_BEHAVIOR_APP_DEFAULT) {
            return override
        }

        return when (SettingsManager.getAppEnterBehaviorPreset(this)) {
            SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE ->
                if (packageName == DISCORD_PACKAGE_NAME) {
                    null
                } else {
                    SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE
                }
            SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_CTRL_SEND ->
                SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND
            SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_ONLY ->
                SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE
            else -> null
        }
    }

    private fun resolveAppEnterAdditionalSendShortcut(info: EditorInfo?): String {
        val packageName = info?.packageName ?: return SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_NONE
        if (packageName !in MESSENGER_ENTER_BEHAVIOR_PACKAGES) return SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_NONE
        if (!SettingsManager.getAppEnterBehaviorEnabled(this)) return SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_NONE

        return SettingsManager.getAppEnterBehaviorOverrides(this)
            .firstOrNull { it.packageName == packageName }
            ?.additionalSendShortcut
            ?: SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_NONE
    }

    private fun resolveTestedAppSendAction(info: EditorInfo?): Int? {
        if (info?.packageName !in ENTER_BEHAVIOR_SEND_ACTION_PACKAGES) return null
        return resolveEditorAction(info) ?: EditorInfo.IME_ACTION_SEND
    }

    private fun consumeUnsupportedEnterSend(
        keyCode: Int,
        event: KeyEvent?,
        outputKeyCodeName: String
    ): Boolean {
        val hadCtrl = ctrlLatchFromNavMode ||
            ctrlLatchActive ||
            ctrlOneShot ||
            ctrlPressed ||
            ctrlPhysicallyPressed ||
            navModeController.isNavModeActive()
        if (hadCtrl) {
            val wasNavModeLatched = ctrlLatchFromNavMode || navModeController.isNavModeActive()
            modifierStateController.clearCtrlState(resetPressedState = false)
            if (wasNavModeLatched) {
                navModeController.cancelNotification()
                navModeController.refreshNavModeState()
            }
            updateStatusBarText()
        }
        notifyDebugKeyEvent(
            keyCode,
            event,
            "KEY_DOWN",
            origin = "ime_service",
            outputKeyCode = null,
            outputKeyCodeName = outputKeyCodeName
        )
        return true
    }

    private fun performPlainEnterSend(
        keyCode: Int,
        inputConnection: InputConnection,
        event: KeyEvent?,
        outputKeyCodeName: String
    ): Boolean {
        inputConnection.finishComposingText()
        val now = System.currentTimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0, 0)
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0, 0)
        val performed = inputConnection.sendKeyEvent(down) && inputConnection.sendKeyEvent(up)
        if (performed) {
            val wasNavModeLatched = ctrlLatchFromNavMode || navModeController.isNavModeActive()
            modifierStateController.clearCtrlState(resetPressedState = false)
            if (wasNavModeLatched) {
                navModeController.cancelNotification()
                navModeController.refreshNavModeState()
            }
            updateStatusBarText()
            suggestionController.onContextReset()
            notifyDebugKeyEvent(
                keyCode,
                event,
                "KEY_DOWN",
                origin = "ime_service",
                outputKeyCode = KeyEvent.KEYCODE_ENTER,
                outputKeyCodeName = outputKeyCodeName
            )
        }
        return performed
    }

    private fun isShiftModifierActive(event: KeyEvent?): Boolean {
        return event?.isShiftPressed == true || shiftPressed || shiftOneShot || shiftLayerLatched
    }

    private fun isCtrlModifierActive(event: KeyEvent?): Boolean {
        return event?.isCtrlPressed == true ||
            ctrlPressed ||
            ctrlPhysicallyPressed ||
            ctrlLatchActive ||
            ctrlOneShot ||
            ctrlLatchFromNavMode
    }

    private fun commitEnterNewline(
        keyCode: Int,
        inputConnection: InputConnection,
        event: KeyEvent?,
        outputKeyCodeName: String
    ): Boolean {
        inputConnection.finishComposingText()
        inputConnection.commitText("\n", 1)
        textInputController.handleAutoCapAfterEnter(
            keyCode,
            inputConnection,
            shouldDisableAutoCapitalize
        ) { updateStatusBarText() }
        suggestionController.onContextReset()
        notifyDebugKeyEvent(
            keyCode,
            event,
            "KEY_DOWN",
            origin = "ime_service",
            unicodeCharOverride = '\n'.code,
            outputKeyCode = null,
            outputKeyCodeName = outputKeyCodeName
        )
        return true
    }

    private fun performEnterEditorAction(
        keyCode: Int,
        actionId: Int,
        inputConnection: InputConnection,
        event: KeyEvent?,
        consumeCtrlState: Boolean = false
    ): Boolean {
        inputConnection.finishComposingText()
        // Skip autocorrection when Enter is mapped to an IME action.
        textInputController.handleAutoCapAfterEnter(
            keyCode,
            inputConnection,
            shouldDisableAutoCapitalize
        ) { updateStatusBarText() }
        val performed = inputConnection.performEditorAction(actionId)
        if (performed) {
            if (consumeCtrlState) {
                val wasNavModeLatched = ctrlLatchFromNavMode || navModeController.isNavModeActive()
                modifierStateController.clearCtrlState(resetPressedState = false)
                if (wasNavModeLatched) {
                    navModeController.cancelNotification()
                    navModeController.refreshNavModeState()
                }
                updateStatusBarText()
            }
            suggestionController.onContextReset()
            notifyDebugKeyEvent(
                keyCode,
                event,
                "KEY_DOWN",
                origin = "ime_service",
                outputKeyCode = null,
                outputKeyCodeName = "editor_action_$actionId"
            )
        }
        return performed
    }

    /**
     * Executes the field's editor action on Enter (e.g., Search/Go/Done) instead of inserting
     * a newline. Works for both single-line and multiline fields if they have an IME action configured.
     * Nav mode keeps its own Enter remapping, so we skip it here.
     */
    private fun handleEnterAsEditorAction(
        keyCode: Int,
        info: EditorInfo?,
        inputConnection: InputConnection?,
        event: KeyEvent?,
        isAutoCorrectEnabled: Boolean,
        ctrlActiveBeforePrelude: Boolean = false
    ): Boolean {
        if (keyCode != KeyEvent.KEYCODE_ENTER) {
            return false
        }

        val ic = inputConnection ?: return false
        val actionId = resolveEditorAction(info)
        val ctrlActiveForEnter = ctrlActiveBeforePrelude || isCtrlModifierActive(event)
        val symEnterSendActive =
            symTogglePendingOnKeyUp &&
                resolveAppEnterAdditionalSendShortcut(info) == SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_SYM_ENTER

        if (symEnterSendActive) {
            symChordUsedSinceKeyDown = true
            symTogglePendingOnKeyUp = false
            if (info?.packageName == DISCORD_PACKAGE_NAME) {
                return performPlainEnterSend(keyCode, ic, event, "app_sym_enter_plain_enter_send")
            }
            return resolveTestedAppSendAction(info)
                ?.let { performEnterEditorAction(keyCode, it, ic, event) }
                ?: consumeUnsupportedEnterSend(keyCode, event, "app_sym_enter_send_unsupported")
        }

        when (resolveAppEnterBehavior(info)) {
            SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE -> {
                if (navModeController.isNavModeActive() && ctrlActiveForEnter) {
                    return resolveTestedAppSendAction(info)
                        ?.let { performEnterEditorAction(keyCode, it, ic, event, consumeCtrlState = true) }
                        ?: false
                }
                return commitEnterNewline(keyCode, ic, event, "app_enter_newline")
            }
            SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND -> {
                if (!ctrlActiveForEnter) {
                    return commitEnterNewline(keyCode, ic, event, "app_enter_newline")
                }
                if (info?.packageName == DISCORD_PACKAGE_NAME) {
                    return performPlainEnterSend(keyCode, ic, event, "discord_plain_enter_send")
                }
                return resolveTestedAppSendAction(info)
                    ?.let { performEnterEditorAction(keyCode, it, ic, event, consumeCtrlState = true) }
                    ?: consumeUnsupportedEnterSend(keyCode, event, "app_enter_send_unsupported")
            }
            SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE -> {
                if (ctrlActiveForEnter) {
                    return resolveTestedAppSendAction(info)
                        ?.let { performEnterEditorAction(keyCode, it, ic, event, consumeCtrlState = true) }
                        ?: consumeUnsupportedEnterSend(keyCode, event, "app_enter_send_unsupported")
                }
                if (isShiftModifierActive(event)) {
                    return commitEnterNewline(keyCode, ic, event, "app_shift_enter_newline")
                }
                return resolveTestedAppSendAction(info)
                    ?.let { performEnterEditorAction(keyCode, it, ic, event) }
                    ?: consumeUnsupportedEnterSend(keyCode, event, "app_enter_send_unsupported")
            }
        }

        if (navModeController.isNavModeActive()) {
            return false
        }
        return actionId?.let { performEnterEditorAction(keyCode, it, ic, event) } ?: false
    }

    private fun notifyDebugKeyEvent(
        keyCode: Int,
        event: KeyEvent?,
        action: String,
        origin: String,
        unicodeCharOverride: Int? = null,
        outputKeyCode: Int? = null,
        outputKeyCodeName: String? = null
    ) {
        KeyboardEventTracker.notifyKeyEvent(
            keyCode = keyCode,
            event = event,
            action = action,
            origin = origin,
            altLatchActive = altLatchActive,
            altOneShot = altOneShot,
            shiftLatchActive = shiftLayerLatched,
            ctrlLatchActive = ctrlLatchActive,
            symPage = symPage,
            resolvedLayout = activeKeyboardLayoutName,
            unicodeCharOverride = unicodeCharOverride,
            outputKeyCode = outputKeyCode,
            outputKeyCodeName = outputKeyCodeName
        )
    }

    private fun resolveAltMappedUnicodeForDebug(
        keyCode: Int,
        altActive: Boolean
    ): Int? {
        if (!altActive) return null
        val mapped = altSymManager.getAltMappings()[keyCode] ?: return null
        if (mapped.isEmpty()) return null
        return mapped.codePointAt(0)
    }

    private fun handleSuggestionsUpdated(suggestions: List<SuggestionResult>) {
        latestSuggestionResults = suggestions
        DebugCaptureStore.recordSuggestionsUpdated(suggestions)
        scheduleStatusBarTextUpdate()
    }

    private fun visibleSuggestionStrings(): List<String> {
        if (latestSuggestionResults.isEmpty()) return emptyList()

        val hasWordStartSuggestion = latestSuggestionResults.any {
            it.kind == SuggestionKind.NEXT_WORD || it.kind == SuggestionKind.STARTER_WORD
        }
        val forceWordStartCapital = if (hasWordStartSuggestion) {
            val modifierSnapshot = modifierStateController.snapshot()
            modifierSnapshot.capsLockEnabled ||
                modifierSnapshot.shiftPhysicallyPressed ||
                modifierSnapshot.shiftOneShot ||
                shiftLayerLatched
        } else {
            false
        }
        val locale = getLocaleFromSubtype()

        return latestSuggestionResults.map { suggestion ->
            when (suggestion.kind) {
                SuggestionKind.NEXT_WORD,
                SuggestionKind.STARTER_WORD -> recaseWordStartSuggestion(
                    suggestion.candidate,
                    forceWordStartCapital,
                    locale
                )
                SuggestionKind.CURRENT_WORD -> suggestion.candidate
            }
        }
    }

    private fun recaseWordStartSuggestion(
        candidate: String,
        forceLeadingCapital: Boolean,
        locale: Locale
    ): String {
        val firstLetterIndex = candidate.indexOfFirst { it.isLetter() }
        if (firstLetterIndex < 0) return candidate

        val firstLetter = candidate[firstLetterIndex]
        val replacement = if (forceLeadingCapital) {
            firstLetter.titlecase(locale)
        } else {
            firstLetter.lowercase(locale)
        }
        return candidate.substring(0, firstLetterIndex) +
            replacement +
            candidate.substring(firstLetterIndex + 1)
    }

    private fun autoCapContextKey(): String? {
        val ic = currentInputConnection ?: return null
        return try {
            val before = ic.getTextBeforeCursor(200, 0)?.toString() ?: return null
            val after = ic.getTextAfterCursor(1, 0)?.toString().orEmpty()
            "$before|$after"
        } catch (_: Exception) {
            null
        }
    }

    private fun suppressAutoCapAtCurrentCursor() {
        suppressedAutoCapContextKey = autoCapContextKey()
    }

    private fun suppressAutoCapRenderingAtCursorIfNeeded() {
        if (!SettingsManager.getAutoCapitalizeRespectManualShiftOff(this)) {
            clearAutoCapSuppression()
            return
        }
        if (
            AutoCapitalizeHelper.shouldAutoCapitalizeAtCursor(
                context = this,
                inputConnection = currentInputConnection,
                shouldDisableAutoCapitalize = shouldDisableAutoCapitalize
            )
        ) {
            suppressAutoCapAtCurrentCursor()
        }
    }

    private fun clearAutoCapSuppression() {
        suppressedAutoCapContextKey = null
    }

    private fun isAutoCapSuppressedAtCursor(): Boolean {
        val suppressed = suppressedAutoCapContextKey ?: return false
        return autoCapContextKey() == suppressed
    }

    private fun requestAutoCapShiftOneShot(): Boolean {
        if (isAutoCapSuppressedAtCursor()) return false
        return modifierStateController.requestShiftOneShotFromAutoCap()
    }
    
    

    /**
     * Initializes the input context for a field.
     * This method contains all common initialization logic that must run
     * regardless of whether input view or candidates view is shown.
     */
    private fun initializeInputContext(restarting: Boolean) {
        if (restarting) {
            return
        }
        
        val state = inputContextState
        val isEditable = state.isEditable
        val isReallyEditable = state.isReallyEditable
        val canCheckAutoCapitalize = isEditable && !shouldDisableAutoCapitalize
        
        if (!isReallyEditable) {
            isInputViewActive = false
            
            if (canCheckAutoCapitalize) {
                AutoCapitalizeHelper.checkAndEnableAutoCapitalize(
                    this,
                    currentInputConnection,
                    shouldDisableAutoCapitalize,
                    enableShift = { requestAutoCapShiftOneShot() },
                    disableShift = { modifierStateController.consumeShiftOneShot() },
                    onUpdateStatusBar = { updateStatusBarText() }
                )
            }
            return
        }
        
        isInputViewActive = true
        
        enforceSmartFeatureDisabledState()
        
        if (ctrlLatchFromNavMode && ctrlLatchActive) {
            val inputConnection = currentInputConnection
            if (inputConnection != null) {
                navModeController.exitNavMode()
            }
        }
        
        AutoCapitalizeHelper.checkAndEnableAutoCapitalize(
            this,
            currentInputConnection,
            shouldDisableAutoCapitalize,
            enableShift = { requestAutoCapShiftOneShot() },
            disableShift = { modifierStateController.consumeShiftOneShot() },
            onUpdateStatusBar = { updateStatusBarText() }
        )
        
        symLayoutController.restoreSymPageIfNeeded { updateStatusBarText() }
        
        altSymManager.reloadLongPressThreshold()
        altSymManager.resetTransientState()
    }
    
    private fun enforceSmartFeatureDisabledState() {
        // The candidates surface also contains Pastiera's hardware-keyboard status bar.
        // Individual smart features hide their own content; the surface itself stays visible.
        deactivateVariations()
    }
    
    /**
     * Loads keyboard layout using the central resolver (auto-by-locale or manual override).
     */
    private fun loadKeyboardLayout() {
        val layoutName = try {
            val imm = getSystemService(InputMethodManager::class.java)
            val currentSubtype = imm.currentInputMethodSubtype
            AdditionalSubtypeUtils.resolveInputStyleLayout(assets, this, currentSubtype)
        } catch (e: Exception) {
            Log.w(TAG, "Error getting layout from subtype, using preferences", e)
            SettingsManager.getKeyboardLayout(this)
        }
        activeKeyboardLayoutName = layoutName
        val layout = LayoutMappingRepository.loadLayout(assets, layoutName, this)
        Log.d(TAG, "Keyboard layout loaded: $layoutName")
    }
    
    /**
     * Gets the character from the selected keyboard layout for a given keyCode and shift state.
     * If the keyCode is mapped in the layout, returns that character.
     * Otherwise, returns the character from the event (if available).
     * This ensures that keyboard layouts work correctly regardless of Android's system layout settings.
     */
    private fun getCharacterFromLayout(keyCode: Int, event: KeyEvent?, isShift: Boolean): Char? {
        // First, try to get the character from the selected layout
        val layoutChar = LayoutMappingRepository.getCharacter(keyCode, isShift)
        if (layoutChar != null) {
            return layoutChar
        }
        // If not mapped in layout, fall back to event's unicode character
        if (event != null && event.unicodeChar != 0) {
            return event.unicodeChar.toChar()
        }
        return null
    }
    
    /**
     * Gets the character string from the selected keyboard layout.
     * Returns the original event character if not mapped in layout.
     */
    private fun getCharacterStringFromLayout(keyCode: Int, event: KeyEvent?, isShift: Boolean): String {
        val char = getCharacterFromLayout(keyCode, event, isShift)
        return char?.toString() ?: ""
    }

    private fun switchToLayout(layoutName: String, showToast: Boolean) {
        activeKeyboardLayoutName = layoutName
        LayoutMappingRepository.loadLayout(assets, layoutName, this)
        variationStateController = VariationStateController(
            VariationRepository.loadVariations(assets, this, activeKeyboardLayoutName)
        )
        updateStatusBarText()

        // Update suggestion engine's keyboard layout for proximity-based ranking
        suggestionController?.updateKeyboardLayout(layoutName)
    }

    private fun cycleLayoutFromShortcut() {
        suppressNextLayoutReload = true
        val nextLayout = SettingsManager.cycleKeyboardLayout(this)
        if (nextLayout != null) {
            switchToLayout(nextLayout, showToast = false)
        }
    }

    private fun isVietnameseTelexActive(): Boolean {
        return VietnameseTelexProcessor.isActiveForLayout(activeKeyboardLayoutName)
    }

    private fun handleVietnameseTelexKey(keyCode: Int, event: KeyEvent?, inputConnection: InputConnection?): Boolean {
        if (!isVietnameseTelexActive()) return false
        val ic = inputConnection ?: return false
        if (event == null || event.repeatCount > 0) return false
        if (!LayoutMappingRepository.isMapped(keyCode)) return false

        val char = LayoutMappingRepository.getCharacterStringWithModifiers(
            keyCode = keyCode,
            isShiftPressed = event.isShiftPressed,
            capsLockEnabled = capsLockEnabled,
            shiftOneShot = shiftOneShot
        )
        if (char.length != 1) return false

        val rewrite = VietnameseTelexProcessor.rewrite(
            textBeforeCursor = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty(),
            keyChar = char[0]
        ) ?: return false

        ic.finishComposingText()
        ic.beginBatchEdit()
        ic.deleteSurroundingText(rewrite.replaceCount, 0)
        ic.commitText(rewrite.replacement, 1)
        ic.endBatchEdit()

        if (shiftOneShot) {
            modifierStateController.consumeShiftOneShot()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            updateStatusBarText()
        }, CURSOR_UPDATE_DELAY)
        return true
    }

    /**
     * Cycles to the next enabled input method subtype (language).
     * Prevents multiple simultaneous switches to avoid dictionary loading conflicts.
     */
    private fun cycleToNextLanguage() {
        if (isLanguageSwitchInProgress) {
            Log.d(TAG, "Language switch already in progress, ignoring request")
            return
        }

        isLanguageSwitchInProgress = true
        try {
            val switched = SubtypeCycler.cycleToNextSubtype(
                context = this,
                imeServiceClass = PhysicalKeyboardInputMethodService::class.java,
                assets = assets,
                showToast = SettingsManager.isToastOnLayoutSwitchEnabled(this)
            )

            // Reset flag; keep a short delay when a switch happened to avoid rapid repeats
            val delayMs = if (switched) 300L else 0L
            uiHandler.postDelayed({ isLanguageSwitchInProgress = false }, delayMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error cycling language", e)
            isLanguageSwitchInProgress = false
        }
    }
    
    private fun showPowerShortcutToast(message: String) {
        uiHandler.post {
            val now = System.currentTimeMillis()
            val sameText = lastLayoutToastText == message
            val sinceLast = now - lastLayoutToastTime
            
            if (!sameText || sinceLast > 1000) {
                lastLayoutToastText = message
                lastLayoutToastTime = now
                powerShortcutToast?.cancel()
                powerShortcutToast = android.widget.Toast.makeText(
                    applicationContext,
                    message,
                    android.widget.Toast.LENGTH_SHORT
                )
                powerShortcutToast?.show()
            }
        }
    }


    private fun handleMultiTapCommit(
        keyCode: Int,
        mapping: LayoutMapping,
        useUppercase: Boolean,
        inputConnection: InputConnection?,
        allowLongPress: Boolean
    ): Boolean {
        val ic = inputConnection ?: return false
        val tapResult = multiTapController.handleTap(keyCode, mapping, useUppercase, ic)
        if (tapResult.handled && allowLongPress) {
            tapResult.committedText?.let { committedText ->
                altSymManager.scheduleLongPressOnly(keyCode, ic, committedText)
            }
        }
        if (tapResult.handled) {
            if (SettingsManager.isSuggestionDebugLoggingEnabled(this)) {
                Log.d(TAG, "multiTap commit text='${tapResult.committedText}' replaced=${tapResult.replacedInWindow}")
            }
            // Prevent onUpdateSelection from re-triggering suggestion recalculation for the same commit.
            markSelectionUpdateSkipAfterCommit()
            tapResult.committedText?.let { committedText ->
                if (tapResult.replacedInWindow) {
                    // Replace the last character in the tracker to stay in sync with the text field.
                    suggestionController.currentSuggestions() // touch to keep listener consistent (noop)
                    suggestionController.onCharacterCommitted("\b$committedText", inputConnection)
                } else {
                    suggestionController.onCharacterCommitted(committedText, inputConnection)
                }
            }
        }
        return tapResult.handled
    }
    
    private fun reloadNavModeMappings() {
        try {
            ctrlKeyMap.clear()
            val assets = assets
            ctrlKeyMap.putAll(KeyMappingLoader.loadCtrlKeyMappings(assets, this))
            Log.d(TAG, "Nav mode mappings reloaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error reloading nav mode mappings", e)
        }
    }
    
    /**
     * Checks if a keycode corresponds to an alphabetic key (A-Z).
     * Returns true only for alphabetic keys, false for all others (modifiers, volume, etc.).
     */
    private fun isAlphabeticKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_B,
            KeyEvent.KEYCODE_C,
            KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_E,
            KeyEvent.KEYCODE_F,
            KeyEvent.KEYCODE_G,
            KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_I,
            KeyEvent.KEYCODE_J,
            KeyEvent.KEYCODE_K,
            KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_M,
            KeyEvent.KEYCODE_N,
            KeyEvent.KEYCODE_O,
            KeyEvent.KEYCODE_P,
            KeyEvent.KEYCODE_Q,
            KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_S,
            KeyEvent.KEYCODE_T,
            KeyEvent.KEYCODE_U,
            KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_W,
            KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_Y,
            KeyEvent.KEYCODE_Z -> true
            else -> false
        }
    }

    private fun isShortcutKey(keyCode: Int): Boolean =
        isAlphabeticKey(keyCode) ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_DEL ||
                keyCode == KeyEvent.KEYCODE_SPACE

    private fun updateModifierTapLatchSettings() {
        if (!::modifierStateController.isInitialized) {
            return
        }
        modifierStateController.shiftTapLatches = SettingsManager.getShiftTapLatches(this)
        modifierStateController.altTapLatches = SettingsManager.getAltTapLatches(this)
        modifierStateController.ctrlTapLatches = SettingsManager.getCtrlTapLatches(this)
    }

    override fun onCreate() {
        super.onCreate()
        ClicksAccessibilityKeyBridge.register(this)
        lastSystemLocalesSignature = resources.configuration.locales.toLanguageTags()
        prefs = getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
        clearAltOnSpaceEnabled = SettingsManager.getClearAltOnSpace(this)
        physicalKeyboardProfileOverride = SettingsManager.getPhysicalKeyboardProfileOverride(this)

        // Clear legacy nav mode notification since we now rely on the status icon only.
        NotificationHelper.cancelNavModeNotification(this)

        modifierStateController = ModifierStateController(DOUBLE_TAP_THRESHOLD)
        updateModifierTapLatchSettings()
        navModeController = NavModeController(this, modifierStateController)
        navModeController.setOnNavModeChangedListener { isActive ->
            updateNavModeStatusIcon(isActive)
        }
        inputEventRouter = InputEventRouter(this, navModeController).apply {
            onCommitText = { markSelectionUpdateSkipAfterCommit() }
        }
        typingSoundPlayer = TypingSoundPlayer(this).apply { reload() }
        textInputController = TextInputController(
            context = this,
            modifierStateController = modifierStateController,
            doubleTapThreshold = DOUBLE_TAP_THRESHOLD
        )
        autoCorrectionManager = AutoCorrectionManager(this)
        val suggestionDebugLogging = SettingsManager.isSuggestionDebugLoggingEnabled(this)
        
        // Get locale from current IME subtype
        val initialLocale = getLocaleFromSubtype()
        
        suggestionController = SuggestionController(
            context = this,
            assets = assets,
            settingsProvider = { getSuggestionSettings() },
            isEnabled = { SettingsManager.isExperimentalSuggestionsEnabled(this) },
            debugLogging = suggestionDebugLogging,
            onSuggestionsUpdated = { suggestions -> handleSuggestionsUpdated(suggestions) },
            currentLocale = initialLocale,
            keyboardLayoutProvider = { SettingsManager.getKeyboardLayout(this) },
            activeSuggestionLocalesProvider = { getAdditionalSuggestionLocalesForActiveInputStyle() }
        )
        inputEventRouter.suggestionController = suggestionController
        
        // Preload dictionary in background so it's ready when user focuses a field
        suggestionController.preloadDictionary()

        // Initialize clipboard history manager first (needed by candidatesBarController)
        clipboardHistoryManager = ClipboardHistoryManager(this)
        clipboardHistoryManager.onCreate()

        candidatesBarController = CandidatesBarController(this, clipboardHistoryManager, assets, PhysicalKeyboardInputMethodService::class.java)
        val snippetExpansionSource = SnippetExpansionSource {
            SettingsManager.getSnippets(this)
        }
        emojiShortcodeSource = EmojiShortcodeSource(assets)
        symbolShortcodeSource = SymbolShortcodeSource(assets)
        textExpansionController = TextExpansionController(
            context = this,
            handler = Handler(Looper.getMainLooper()),
            inputConnectionProvider = { currentInputConnection },
            inputContextProvider = { inputContextState },
            isSelectionCollapsedProvider = { !editorHasActiveSelection },
            anchorProvider = { window?.window?.decorView },
            configsProvider = {
                listOf(
                    ExpansionRuntimeConfig(
                        source = snippetExpansionSource,
                        triggerKind = ExpansionTriggerKind.PREFIX,
                        enabled = SettingsManager.getSnippetsEnabled(this),
                        prefix = SettingsManager.getSnippetsPrefix(this).first(),
                        presentation = SettingsManager.getSnippetsPresentation(this),
                        activationPolicy = SettingsManager.getSnippetsActivationPolicy(this)
                    ),
                    ExpansionRuntimeConfig(
                        source = emojiShortcodeSource,
                        triggerKind = ExpansionTriggerKind.COLON_SHORTCODE,
                        enabled = SettingsManager.getEmojiShortcodesEnabled(this),
                        presentation = SettingsManager.getEmojiSymbolsPresentation(this),
                        activationPolicy = SettingsManager.getEmojiSymbolsActivationPolicy(this),
                        exactOnClose = SettingsManager.getEmojiSymbolsExactOnClose(this)
                    ),
                    ExpansionRuntimeConfig(
                        source = symbolShortcodeSource,
                        triggerKind = ExpansionTriggerKind.COLON_SHORTCODE,
                        enabled = SettingsManager.getSymbolShortcodesEnabled(this),
                        presentation = SettingsManager.getEmojiSymbolsPresentation(this),
                        activationPolicy = SettingsManager.getEmojiSymbolsActivationPolicy(this),
                        exactOnClose = SettingsManager.getEmojiSymbolsExactOnClose(this)
                    )
                )
            },
            showSuggestionBar = { labels, onSelected ->
                candidatesBarController.showExpansionSuggestions(labels, onSelected)
            },
            clearSuggestionBar = { candidatesBarController.clearExpansionSuggestions() },
            requestSurfaceUpdate = { updateStatusBarText() },
            onCommitted = {
                markSelectionUpdateSkipAfterCommit()
                suggestionController.onContextReset()
                suggestionController.readInitialContext(currentInputConnection)
                updateStatusBarText()
            }
        )
        prepareEnabledExpansionAssets()
        candidatesBarController.onAddUserWord = { word ->
            if (shiftLayerLatched || altLayerLatched) {
                shiftLayerLatched = false
                altLayerLatched = false
                modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                modifierStateBeforeHold = null
            }
            variationInteractedDuringHold = true
            suggestionController.addUserWord(word)
            suggestionController.clearPendingAddWord()
            updateStatusBarText()
        }
        candidatesBarController.onAddUserWordSubstitutionRequested = { word ->
            showAddSubstitutionDialog(word)
        }
        candidatesBarController.onSuggestionCommitted = {
            if (shiftLayerLatched || altLayerLatched) {
                shiftLayerLatched = false
                altLayerLatched = false
                modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                modifierStateBeforeHold = null
            }
            if (shiftOneShot) {
                modifierStateController.consumeShiftOneShot()
            }
            variationInteractedDuringHold = true
            suggestionController.readInitialContext(currentInputConnection)
            updateStatusBarText()
        }
        candidatesBarController.onHideSuggestion = { suggestion ->
            suggestionController.dismissSuggestion(suggestion, hardDeleteUserWord = false)
            updateStatusBarText()
            NotificationHelper.triggerHapticFeedback(this)
        }
        candidatesBarController.onDeleteUserSuggestion = { suggestion ->
            suggestionController.dismissSuggestion(suggestion, hardDeleteUserWord = true)
            updateStatusBarText()
            NotificationHelper.triggerHapticFeedback(this)
        }
        candidatesBarController.canDeleteUserSuggestion = { suggestion ->
            suggestionController.userDictionarySnapshot().any { entry ->
                entry.word.equals(suggestion, ignoreCase = true)
            }
        }
        candidatesBarController.onLanguageSwitchRequested = {
            if (shiftLayerLatched || altLayerLatched) {
                shiftLayerLatched = false
                altLayerLatched = false
                modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                modifierStateBeforeHold = null
            }
            variationInteractedDuringHold = true
            cycleToNextLanguage()
        }

        // Register listener for variation selection (both controllers)
        val variationListener = object : VariationButtonHandler.OnVariationSelectedListener {
            override fun onBoundaryTextRequested(
                variation: String,
                inputConnection: InputConnection
            ): Boolean {
                return handleBoundaryTextBeforeCommit(variation, inputConnection)
            }

            override fun onVariationSelected(variation: String) {
                val keepLayerLatchedAfterVariation =
                    SettingsManager.isStaticVariationBarLayerStickyEnabled(this@PhysicalKeyboardInputMethodService)
                val hasLatchedLayer = shiftLayerLatched || altLayerLatched
                if (hasLatchedLayer && !keepLayerLatchedAfterVariation) {
                    shiftLayerLatched = false
                    altLayerLatched = false
                    modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                    modifierStateBeforeHold = null
                }
                variationInteractedDuringHold = true
                // Update variations after one has been selected (refresh view if needed)
                updateStatusBarText()
            }
        }
        candidatesBarController.onVariationSelectedListener = variationListener

        // Register listener for cursor movement (both controllers)
        val cursorListener = {
            if (shiftLayerLatched || altLayerLatched) {
                shiftLayerLatched = false
                altLayerLatched = false
                modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                modifierStateBeforeHold = null
            }
            variationInteractedDuringHold = true
            updateStatusBarText()
        }
        candidatesBarController.onCursorMovedListener = cursorListener

        // Register listener for speech recognition
        candidatesBarController.onSpeechRecognitionRequested = {
            if (shiftLayerLatched || altLayerLatched) {
                shiftLayerLatched = false
                altLayerLatched = false
                modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                modifierStateBeforeHold = null
            }
            variationInteractedDuringHold = true
            startSpeechRecognition()
        }
        // Register listener for clipboard page
        candidatesBarController.onClipboardRequested = {
            if (shiftLayerLatched || altLayerLatched) {
                shiftLayerLatched = false
                altLayerLatched = false
                modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                modifierStateBeforeHold = null
            }
            variationInteractedDuringHold = true
            ensureInputViewCreated()
            // Toggle clipboard as SYM page 3
            symLayoutController.openClipboardPage()
            updateStatusBarText()
        }
        // Register listener for emoji picker page
        candidatesBarController.onEmojiPickerRequested = {
            if (shiftLayerLatched || altLayerLatched) {
                shiftLayerLatched = false
                altLayerLatched = false
                modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                modifierStateBeforeHold = null
            }
            variationInteractedDuringHold = true
            ensureInputViewCreated()
            // Toggle emoji picker as SYM page 4
            symLayoutController.openEmojiPickerPage()
            updateStatusBarText()
        }
        candidatesBarController.onEmojiPageRequested = {
            ensureInputViewCreated()
            symLayoutController.openEmojiPage()
            updateStatusBarText()
        }
        // Register listener for symbols page
        candidatesBarController.onSymbolsPageRequested = {
            ensureInputViewCreated()
            // Toggle symbols as SYM page 2
            symLayoutController.openSymbolsPage()
            updateStatusBarText()
        }
        candidatesBarController.onSoftwareKeyboardSymToggleRequested = {
            ensureInputViewCreated()
            symLayoutController.toggleSymPage()
            updateStatusBarText()
        }
        candidatesBarController.onSymCloseRequested = {
            if (symLayoutController.closeSymPage()) {
                updateStatusBarText()
            }
        }
        candidatesBarController.onUndoRequested = {
            variationInteractedDuringHold = true
            sendCtrlShortcut(KeyEvent.KEYCODE_Z)
        }
        candidatesBarController.onRedoRequested = {
            variationInteractedDuringHold = true
            sendCtrlShortcut(KeyEvent.KEYCODE_Y)
        }
        candidatesBarController.onSoftwareKeyboardKeyPressed = { keyCode ->
            typingSoundPlayer.play(keyCode)
        }
        candidatesBarController.onSoftwareKeyboardModifierKeyDown = { keyCode ->
            handleSoftwareKeyboardModifierKeyDown(keyCode)
        }
        candidatesBarController.onSoftwareKeyboardModifierKeyUp = { keyCode ->
            handleSoftwareKeyboardModifierKeyUp(keyCode)
        }
        candidatesBarController.onSoftwareKeyboardKeyStroke = { keyCode, _ ->
            handleSoftwareKeyboardKeyStroke(keyCode)
        }
        candidatesBarController.onSoftwareKeyboardShiftTapped = {
            val wasShiftOneShot = modifierStateController.shiftOneShot
            val downResult = modifierStateController.handleShiftKeyDown(KeyEvent.KEYCODE_SHIFT_LEFT)
            if (wasShiftOneShot && !modifierStateController.shiftOneShot) {
                suppressAutoCapRenderingAtCursorIfNeeded()
            }
            val upResult = modifierStateController.handleShiftKeyUp(KeyEvent.KEYCODE_SHIFT_LEFT)
            if (
                downResult.shouldUpdateStatusBar ||
                downResult.shouldRefreshStatusBar ||
                upResult.shouldUpdateStatusBar ||
                upResult.shouldRefreshStatusBar
            ) {
                updateStatusBarText()
            }
        }
        candidatesBarController.onSoftwareKeyboardNonShiftInteraction = {
            modifierStateController.registerNonModifierKey()
        }
        candidatesBarController.onSoftwareKeyboardTextInput = { text, inputConnection, snapshot ->
            val ic = inputConnection ?: currentInputConnection
            val consumedShiftOneShot = text.length == 1 &&
                text[0].isLetter() &&
                modifierStateController.consumeShiftOneShot()
            val handled = handleSoftwareKeyboardTextInput(text, ic, snapshot)
            if (consumedShiftOneShot) {
                updateStatusBarText()
            }
            handled
        }
        candidatesBarController.onSoftwareKeyboardBoundaryTextInput = { text, inputConnection ->
            handleBoundaryTextBeforeCommit(text, inputConnection)
        }
        candidatesBarController.onMinimalUiToggleRequested = {
            keyboardVisibilityController.togglePastierinaMode()
        }
        candidatesBarController.onSoftwareKeyboardModeToggleRequested = {
            toggleSoftwareKeyboardModeFromStatusBar()
        }
        val postClipboardBadgeUpdate: () -> Unit = {
            val count = clipboardHistoryManager.getHistorySize()
            uiHandler.post {
                candidatesBarController.updateClipboardCount(count)
            }
        }
        clipboardHistoryManager.setHistoryChangeListener(object : ClipboardDao.Listener {
            override fun onClipInserted(position: Int) {
                postClipboardBadgeUpdate()
            }

            override fun onClipsRemoved(position: Int, count: Int) {
                postClipboardBadgeUpdate()
            }

            override fun onClipMoved(oldPosition: Int, newPosition: Int) {
                postClipboardBadgeUpdate()
            }
        })
        altSymManager = AltSymManager(
            assets = assets,
            prefs = prefs,
            context = this,
            activeLayoutNameProvider = { activeKeyboardLayoutName }
        )
        altSymManager.reloadSymMappings() // Load custom mappings for page 1 if present
        altSymManager.reloadSymMappings2() // Load custom mappings for page 2 if present
        altSymManager.onBoundaryTextRequested = { text, inputConnection ->
            handleBoundaryTextBeforeCommit(text, inputConnection)
        }
        // Register callback to be notified when an Alt character is inserted after long press.
        // Variations are updated automatically by updateStatusBarText().
        altSymManager.onAltCharInserted = { char ->
            DeferredPunctuationSpaceTracker.onTextCommitted(this, char.toString())
            updateStatusBarText()
            val ic = currentInputConnection
            // Apostrophe is never a boundary: use centralized punctuation set.
            val punctuationSet = it.palsoftware.pastiera.core.Punctuation.BOUNDARY
            val normalizedChar = it.palsoftware.pastiera.core.Punctuation.normalizeApostrophe(char)
            if (normalizedChar == '\'') {
                inputEventRouter.handleInWordApostrophe(ic, pendingApostrophe = false)
            } else if (normalizedChar.isLetter()) {
                // Variations-mode long-press replaces a letter: keep suggestion context in sync.
                markSelectionUpdateSkipAfterCommit()
                suggestionController.onCharacterCommitted(normalizedChar.toString(), ic)
            } else if (normalizedChar !in punctuationSet) {
                // Non-boundary Alt long-press (e.g., numbers/symbols) resets current word tracking
                suggestionController.onContextReset()
            }
        }
        // Track normal characters committed via Alt short press (no long press triggered)
        altSymManager.onNormalCharCommitted = { text ->
            if (::suggestionController.isInitialized) {
                // Avoid double-tracking plain letters already handled by the main pipeline.
                val ch = text.firstOrNull()
                val shouldTrack = ch == null || !ch.isLetter()
                if (shouldTrack) {
                    // Avoid double suggestion dispatch: skip the immediate selection update after commit.
                    markSelectionUpdateSkipAfterCommit()
                    suggestionController.onCharacterCommitted(text, currentInputConnection)
                }
            }
        }
        symLayoutController = SymLayoutController(this, prefs, altSymManager)
        keyboardVisibilityController = KeyboardVisibilityController(
            context = this,
            candidatesBarController = candidatesBarController,
            symLayoutController = symLayoutController,
            isInputViewActive = { isInputViewActive },
            hasActiveTextField = { inputContextState.isEditable },
            isNavModeLatched = { ctrlLatchFromNavMode },
            currentInputConnection = { currentInputConnection },
            isInputViewShown = { isInputViewShown },
            renderedSurface = {
                when {
                    candidatesBarController.isInputViewActuallyRendered() ->
                        KeyboardVisibilityController.RenderedSurface.FULL_INPUT_VIEW
                    candidatesBarController.isCandidatesViewActuallyRendered() ->
                        KeyboardVisibilityController.RenderedSurface.CANDIDATES_VIEW
                    else -> KeyboardVisibilityController.RenderedSurface.HIDDEN
                }
            },
            setRequestedInputViewShown = { shown -> requestedInputViewShown = shown },
            attachInputView = { view -> setInputView(view) },
            setCandidatesSurfaceActive = candidatesBarController::setCandidatesSurfaceActive,
            setCandidatesViewShown = { shown -> setCandidatesViewShown(shown) },
            synchronizeCandidatesContainerVisibility = ::synchronizeCandidatesContainerVisibility,
            postToUi = { action -> uiHandler.post(action) },
            postToUiDelayed = { delayMs, action -> uiHandler.postDelayed(action, delayMs) },
            showInputWindow = { showInput -> showWindow(showInput) },
            requestShowInputView = ::requestKeyboardInputView,
            refreshStatusBar = {
                invalidateRenderedStatusSnapshot()
                refreshStatusBar()
            }
        )
        inputManager = getSystemService(InputManager::class.java)
        InputDevice.getDeviceIds().forEach { deviceId ->
            InputDevice.getDevice(deviceId)
                ?.takeIf(DeviceSpecific::isClicksPowerKeyboard)
                ?.let { connectedClicksInputDeviceIds += deviceId }
        }
        lastObservedAutoSoftwareKeyboardMode = SoftwareKeyboardAutoDetector.resolve(this)
        inputManager?.registerInputDeviceListener(inputDeviceListener, uiHandler)
        launcherShortcutController = LauncherShortcutController(this)
        // Configura callbacks per gestire nav mode durante power shortcuts
        launcherShortcutController.setNavModeCallbacks(
            exitNavMode = { navModeController.exitNavMode() },
            enterNavMode = { navModeController.enterNavMode() }
        )

        // Initialize keyboard layout
        loadKeyboardLayout()
        
        // Initialize nav mode mappings file if needed
        it.palsoftware.pastiera.SettingsManager.initializeNavModeMappingsFile(this)
        ctrlKeyMap.putAll(KeyMappingLoader.loadCtrlKeyMappings(assets, this))
        variationStateController = VariationStateController(
            VariationRepository.loadVariations(assets, this, activeKeyboardLayoutName)
        )
        keyboardVisibilityController.syncStatusBarPresentationModeFromSettings()
        
        // Load auto-correction rules
        AutoCorrector.loadCorrections(assets, this)
        
        // Register additional subtypes (custom input styles)
        registerAdditionalSubtypes()
        
        // Trackpad gestures detector (instantiated early to avoid late-init issues in listener)
        Log.d(TRACKPAD_DEBUG_TAG, "onCreate: Building initial trackpad gesture detector...")
        trackpadGestureDetector = buildTrackpadGestureDetector()
        Log.d(TRACKPAD_DEBUG_TAG, "onCreate: Initial detector built")

        // Register listener for SharedPreferences changes
        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            Log.d(TRACKPAD_DEBUG_TAG, "SharedPrefs changed: key=$key")
            if (key == "sym_mappings_custom") {
                Log.d(TAG, "SYM mappings page 1 changed, reloading...")
                // Reload SYM mappings for page 1
                altSymManager.reloadSymMappings()
                altSymManager.reloadAltMappings()
                // Update status bar to reflect new mappings
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == "sym_mappings_page2_custom") {
                Log.d(TAG, "SYM mappings page 2 changed, reloading...")
                // Reload SYM mappings for page 2
                altSymManager.reloadSymMappings2()
                altSymManager.reloadAltMappings()
                // Update status bar to reflect new mappings
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == "sym_pages_config") {
                Log.d(TAG, "SYM pages configuration changed, refreshing status bar...")
                altSymManager.reloadAltMappings()
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == SettingsManager.KEY_ALT_CHARACTER_LAYER_BINDING) {
                Log.d(TAG, "Alt character layer binding changed, reloading mappings...")
                altSymManager.reloadAltMappings()
                Handler(Looper.getMainLooper()).post { updateStatusBarText() }
            } else if (key == "clear_alt_on_space") {
                clearAltOnSpaceEnabled = SettingsManager.getClearAltOnSpace(this)
            } else if (key == "emoji_shortcodes_enabled" || key == "symbol_shortcodes_enabled") {
                prepareEnabledExpansionAssets()
            } else if (key == "shift_tap_latches" || key == "alt_tap_latches" || key == "ctrl_tap_latches") {
                updateModifierTapLatchSettings()
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == "physical_keyboard_profile_override") {
                Log.d(TAG, "Physical keyboard profile override changed, reloading Alt mappings...")
                physicalKeyboardProfileOverride = SettingsManager.getPhysicalKeyboardProfileOverride(this)
                altSymManager.reloadAltMappings()
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == "physical_keyboard_currency_symbol") {
                Log.d(TAG, "Physical keyboard currency symbol changed, reloading Alt mappings...")
                altSymManager.reloadAltMappings()
            } else if (key != null && (key.startsWith("auto_correct_custom_") || key == "auto_correct_enabled_languages")) {
                Log.d(TAG, "Auto-correction rules changed, reloading...")
                // Reload auto-corrections (including new custom languages)
                AutoCorrector.loadCorrections(assets, this)
            } else if (key == "variations_updated") {
                Log.d(TAG, "Variations file changed, reloading...")
                // Reload variations from file
                variationStateController = VariationStateController(
                    VariationRepository.loadVariations(assets, this, activeKeyboardLayoutName)
                )
                candidatesBarController.invalidateStaticVariations()
                // Update status bar to reflect new variations
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == "nav_mode_mappings_updated") {
                Log.d(TAG, "Nav mode mappings changed, reloading...")
                // Reload nav mode key mappings
                reloadNavModeMappings()
            } else if (key == "keyboard_layout") {
                if (suppressNextLayoutReload) {
                    Log.d(TAG, "Keyboard layout change observed, reload suppressed")
                    suppressNextLayoutReload = false
                } else {
                    Log.d(TAG, "Keyboard layout changed, reloading...")
                    val layoutName = SettingsManager.getKeyboardLayout(this)
                    switchToLayout(layoutName, showToast = false)
                }
            } else if (key == "keyboard_layout_auto_by_locale" || key == SettingsManager.KEY_KEYBOARD_LAYOUT_AUTO_MAPPING_UPDATED) {
                Log.d(TAG, "Keyboard layout auto mode/mapping changed, resolving active layout...")
                loadKeyboardLayout()
                switchToLayout(activeKeyboardLayoutName, showToast = false)
            } else if (key == AdditionalSubtypeUtils.PREF_CUSTOM_INPUT_STYLES) {
                Log.d(TAG, "Custom input styles changed, re-registering subtypes...")
                registerAdditionalSubtypes()
            } else if (key == "trackpad_gestures_enabled") {
                val newValue = SettingsManager.getTrackpadGesturesEnabled(this)
                Log.d(TRACKPAD_DEBUG_TAG, "SharedPrefs listener: trackpad_gestures_enabled changed to $newValue")
                Log.d(TAG, "Trackpad gestures setting changed, restarting detection...")
                if (::trackpadGestureDetector.isInitialized) {
                    Log.d(TRACKPAD_DEBUG_TAG, "Detector initialized, stopping old detector...")
                    trackpadGestureDetector.stop()
                    Log.d(TRACKPAD_DEBUG_TAG, "Building new detector...")
                    trackpadGestureDetector = buildTrackpadGestureDetector()
                    if (shouldStartShizukuTrackpadDetector()) {
                        Log.d(TRACKPAD_DEBUG_TAG, "Starting new Shizuku detector...")
                        trackpadGestureDetector.start()
                    } else {
                        Log.d(TRACKPAD_DEBUG_TAG, "Detector start skipped after gestures change")
                    }
                    Log.d(TRACKPAD_DEBUG_TAG, "Detector restart complete for gestures_enabled change")
                } else {
                    Log.d(TRACKPAD_DEBUG_TAG, "Detector NOT initialized yet, skipping restart")
                }
            } else if (
                key == "trackpad_swipe_threshold" ||
                key == "trackpad_suggestion_swipe_threshold" ||
                key == "trackpad_delete_swipe_threshold"
            ) {
                val suggestionValue = SettingsManager.getTrackpadSuggestionSwipeThreshold(this)
                val deleteValue = SettingsManager.getTrackpadDeleteSwipeThreshold(this)
                Log.d(
                    TRACKPAD_DEBUG_TAG,
                    "SharedPrefs listener: trackpad thresholds changed: suggestion=$suggestionValue, delete=$deleteValue"
                )
                Log.d(TAG, "Trackpad swipe threshold changed, restarting detection...")
                if (::trackpadGestureDetector.isInitialized) {
                    Log.d(TRACKPAD_DEBUG_TAG, "Detector initialized, stopping old detector...")
                    trackpadGestureDetector.stop()
                    Log.d(TRACKPAD_DEBUG_TAG, "Building new detector...")
                    trackpadGestureDetector = buildTrackpadGestureDetector()
                    if (shouldStartShizukuTrackpadDetector()) {
                        Log.d(TRACKPAD_DEBUG_TAG, "Starting new Shizuku detector...")
                        trackpadGestureDetector.start()
                    } else {
                        Log.d(TRACKPAD_DEBUG_TAG, "Detector start skipped after swipe threshold change")
                    }
                    Log.d(TRACKPAD_DEBUG_TAG, "Detector restart complete for swipe_threshold change")
                } else {
                    Log.d(TRACKPAD_DEBUG_TAG, "Detector NOT initialized yet, skipping restart")
                }
            } else if (key == "trackpad_provider") {
                val newValue = SettingsManager.getTrackpadProvider(this)
                Log.d(TRACKPAD_DEBUG_TAG, "SharedPrefs listener: trackpad_provider changed to $newValue")
                if (::trackpadGestureDetector.isInitialized) {
                    trackpadGestureDetector.stop()
                    trackpadGestureDetector = buildTrackpadGestureDetector()
                    if (shouldStartShizukuTrackpadDetector()) {
                        Log.d(TRACKPAD_DEBUG_TAG, "Starting Shizuku detector for provider change")
                        trackpadGestureDetector.start()
                    }
                }
                attachTrackpadDecorViewMotionHook("provider_changed")
            } else if (key == "pastierina_mode_override") {
                keyboardVisibilityController.syncStatusBarPresentationModeFromSettings()
            } else if (key == SettingsManager.KEY_TITAN2_ELITE_ROUNDED_CORNER_INSETS) {
                if (::candidatesBarController.isInitialized) {
                    candidatesBarController.refreshWindowInsets()
                }
            } else if (
                key == "software_keyboard_mode" ||
                key == SettingsManager.KEY_SOFTWARE_KEYBOARD_MODE_RUNTIME_OVERRIDE
            ) {
                invalidateRenderedStatusSnapshot()
                val effectiveMode = SettingsManager.resolveEffectiveSoftwareKeyboardMode(this)
                scheduleKeyboardSurfaceTransition(
                    mode = effectiveMode
                )
            } else if (
                key == "software_keyboard_layout_style" ||
                key == "software_keyboard_number_row_enabled" ||
                key == "software_keyboard_left_modifier_key" ||
                key == "software_keyboard_right_modifier_key"
            ) {
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == "software_keyboard_nearest_key_touch_enabled") {
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (SettingsManager.isKeyboardThemePreferenceKey(key)) {
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (SettingsManager.isModifierIndicatorPreferenceKey(key)) {
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (
                key == SettingsManager.KEY_TYPING_SOUND_MODE ||
                key == SettingsManager.KEY_TYPING_SOUND_OUTPUT_MODE ||
                key == SettingsManager.KEY_TYPING_SOUND_CUSTOM_FILE_NAME ||
                key == SettingsManager.KEY_TYPING_SOUND_UPDATED_AT
            ) {
                typingSoundPlayer.reload()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        Log.d(TRACKPAD_DEBUG_TAG, "onCreate: SharedPreferences listener registered")
        
        // Register broadcast receiver for speech recognition
        speechResultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "Broadcast receiver called - action: ${intent?.action}")
                if (intent?.action == SpeechRecognitionActivity.ACTION_SPEECH_RESULT) {
                    val text = intent.getStringExtra(SpeechRecognitionActivity.EXTRA_TEXT)
                    Log.d(TAG, "Broadcast received with text: $text")
                    if (text != null && text.isNotEmpty()) {
                        Log.d(TAG, "Received speech recognition result: $text")
                        
                        // Delay text insertion to give the system time to restore InputConnection
                        // after the speech recognition activity has closed.
                        Handler(Looper.getMainLooper()).postDelayed({
                            // Try multiple times if InputConnection is not immediately available
                            var attempts = 0
                            val maxAttempts = 10
                            
                            fun tryInsertText() {
                                val inputConnection = currentInputConnection
                                if (inputConnection != null) {
                                    inputConnection.commitText(text, 1)
                                    Log.d(TAG, "Speech text inserted successfully: $text")
                                } else {
                                    attempts++
                                    if (attempts < maxAttempts) {
                                        Log.d(TAG, "InputConnection not available, attempt $attempts/$maxAttempts, retrying in 100ms...")
                                        Handler(Looper.getMainLooper()).postDelayed({ tryInsertText() }, 100)
                                    } else {
                                        Log.w(TAG, "InputConnection not available after $maxAttempts attempts, text not inserted: $text")
                                    }
                                }
                            }
                            
                            tryInsertText()
                        }, 300) // Wait 300ms before trying to insert text
                    }
                }
            }
        }
        
        val filter = IntentFilter(SpeechRecognitionActivity.ACTION_SPEECH_RESULT)
        
        // On Android 13+ (API 33+) we must specify whether the receiver is exported
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(speechResultReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(speechResultReceiver, filter)
        }
        
        Log.d(TAG, "Broadcast receiver registered for: ${SpeechRecognitionActivity.ACTION_SPEECH_RESULT}")
        
        // Register broadcast receiver for permission request result
        permissionResultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    PermissionRequestActivity.ACTION_PERMISSION_GRANTED -> {
                        Log.d(TAG, "RECORD_AUDIO permission granted, retrying speech recognition")
                        if (pendingSpeechRecognition) {
                            pendingSpeechRecognition = false
                            // Retry speech recognition now that permission is granted
                            startSpeechRecognition()
                        }
                    }
                    PermissionRequestActivity.ACTION_PERMISSION_DENIED -> {
                        Log.w(TAG, "RECORD_AUDIO permission denied by user")
                        pendingSpeechRecognition = false
                    }
                }
            }
        }
        
        val permissionFilter = IntentFilter().apply {
            addAction(PermissionRequestActivity.ACTION_PERMISSION_GRANTED)
            addAction(PermissionRequestActivity.ACTION_PERMISSION_DENIED)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(permissionResultReceiver, permissionFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(permissionResultReceiver, permissionFilter)
        }
        
        Log.d(TAG, "Broadcast receiver registered for permission request results")
        
        // Register broadcast receiver for user dictionary updates
        userDictionaryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AppBroadcastActions.USER_DICTIONARY_UPDATED) {
                    Log.d(TAG, "User dictionary updated, refreshing...")
                    if (::suggestionController.isInitialized) {
                        suggestionController.refreshUserDictionary()
                    }
                }
            }
        }
        
        val userDictFilter = IntentFilter(AppBroadcastActions.USER_DICTIONARY_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(userDictionaryReceiver, userDictFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(userDictionaryReceiver, userDictFilter)
        }
        
        Log.d(TAG, "Broadcast receiver registered for user dictionary updates")
        
        // Register broadcast receiver for additional IME subtypes updates
        additionalSubtypesReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "it.palsoftware.pastiera.ACTION_ADDITIONAL_SUBTYPES_UPDATED") {
                    Log.d(TAG, "Additional subtypes updated, refreshing...")
                    updateAdditionalSubtypes()
                }
            }
        }
        
        val subtypesFilter = IntentFilter("it.palsoftware.pastiera.ACTION_ADDITIONAL_SUBTYPES_UPDATED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(additionalSubtypesReceiver, subtypesFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(additionalSubtypesReceiver, subtypesFilter)
        }
        
        Log.d(TAG, "Broadcast receiver registered for additional subtypes updates")

        // Start trackpad gesture detection
        if (shouldStartShizukuTrackpadDetector()) {
            Log.d(TRACKPAD_DEBUG_TAG, "onCreate: Calling initial Shizuku trackpadGestureDetector.start()...")
            trackpadGestureDetector.start()
            Log.d(TRACKPAD_DEBUG_TAG, "onCreate: Initial Shizuku start() call completed")
        } else {
            Log.d(TRACKPAD_DEBUG_TAG, "onCreate: Initial Shizuku detector start skipped")
        }
    }

    private fun handleSoftwareKeyboardTextInput(
        text: String,
        inputConnection: InputConnection?,
        snapshot: StatusBarController.StatusSnapshot
    ): Boolean {
        val ic = inputConnection ?: return false

        if (text == " ") {
            if (::textExpansionController.isInitialized &&
                textExpansionController.handleKeyDown(KeyEvent.KEYCODE_SPACE)
            ) {
                return true
            }
            DeferredPunctuationSpaceTracker.prepareForTextCommit(this, ic, text)
            return SoftwareKeyboardTextInputHandler.handleSpaceInput(
                textInputController = textInputController,
                inputConnection = ic,
                shouldDisableDoubleSpaceToPeriod = snapshot.shouldDisableDoubleSpaceToPeriod,
                shouldDisableAutoCapitalize = snapshot.shouldDisableAutoCapitalize,
                shouldDisableSuggestions = snapshot.shouldDisableSuggestions,
                onDoubleSpaceHandled = { suggestionController.onContextReset() },
                onNormalBoundary = {
                    suggestionController.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, ic).committed
                },
                onCommitSpace = {
                    markSelectionUpdateSkipAfterCommit()
                    ic.commitText(" ", 1)
                },
                onStatusBarUpdate = { updateStatusBarText() }
            )
        }

        if (
            handleBoundaryTextBeforeCommit(
                text = text,
                inputConnection = ic,
                shouldDisableSuggestions = snapshot.shouldDisableSuggestions,
                shouldDisableAutoCorrect = snapshot.shouldDisableAutoCorrect
            )
        ) {
            if (::textExpansionController.isInitialized) textExpansionController.scheduleRefresh()
            return true
        }

        markSelectionUpdateSkipAfterCommit()
        if (DeferredPunctuationSpaceTracker.prepareForTextCommit(this, ic, text)) {
            suggestionController.onContextReset()
        }
        ic.commitText(text, 1)
        if (!snapshot.shouldDisableSuggestions) {
            suggestionController.onCharacterCommitted(text, ic)
        }
        updateStatusBarText()
        if (::textExpansionController.isInitialized) textExpansionController.scheduleRefresh()
        return true
    }

    private fun handleBoundaryTextBeforeCommit(
        text: String,
        inputConnection: InputConnection?,
        shouldDisableSuggestions: Boolean = inputContextState.shouldDisableSuggestions,
        shouldDisableAutoCorrect: Boolean = inputContextState.shouldDisableAutoCorrect
    ): Boolean {
        val ic = inputConnection ?: return false
        if (text.length != 1) return false
        val boundary = it.palsoftware.pastiera.core.Punctuation.normalizeApostrophe(text[0])
        if (boundary == '\'' || boundary !in it.palsoftware.pastiera.core.Punctuation.BOUNDARY) {
            return false
        }
        if (DeferredPunctuationSpaceTracker.prepareForTextCommit(this, ic, text)) {
            suggestionController.onContextReset()
        }
        markSelectionUpdateSkipAfterCommit()
        return inputEventRouter.handleBoundaryText(
            context = this,
            text = boundary.toString(),
            inputConnection = ic,
            shouldDisableSuggestions = shouldDisableSuggestions,
            isAutoCorrectEnabled = SettingsManager.getAutoCorrectEnabled(this) && !shouldDisableAutoCorrect,
            autoCorrectionManager = autoCorrectionManager,
            updateStatusBar = { updateStatusBarText() }
        )
    }

    private fun handleSoftwareKeyboardModifierKeyDown(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> {
                modifierStateBeforeHold = modifierStateController.captureLogicalState()
                variationInteractedDuringHold = false
                otherKeyInteractedDuringHold = false
                modifierDownTimes[keyCode] = SystemClock.uptimeMillis()
                val result = modifierStateController.handleCtrlKeyDown(
                    keyCode,
                    isInputViewActive,
                    onNavModeDeactivated = {
                        navModeController.cancelNotification()
                    }
                )
                if (result.shouldUpdateStatusBar || result.shouldRefreshStatusBar) {
                    updateStatusBarText()
                }
                true
            }
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                modifierStateBeforeHold = modifierStateController.captureLogicalState()
                variationInteractedDuringHold = false
                otherKeyInteractedDuringHold = false
                modifierDownTimes[keyCode] = SystemClock.uptimeMillis()
                if (symLayoutController.isSymActive()) {
                    symLayoutController.closeSymPage()
                }
                val result = modifierStateController.handleAltKeyDown(keyCode)
                if (result.shouldUpdateStatusBar || result.shouldRefreshStatusBar) {
                    updateStatusBarText()
                }
                true
            }
            KEYCODE_SYM -> {
                modifierDownTimes[keyCode] = SystemClock.uptimeMillis()
                dispatchSoftwareKeyboardSyntheticKey(keyCode, KeyEvent.ACTION_DOWN)
            }
            else -> false
        }
    }

    private fun handleSoftwareKeyboardModifierKeyUp(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> {
                val downTime = modifierDownTimes[keyCode] ?: 0L
                val now = SystemClock.uptimeMillis()
                val holdDuration = if (downTime > 0L) now - downTime else 0L
                val wasTap = holdDuration < 300L && !otherKeyInteractedDuringHold && !variationInteractedDuringHold
                val shortcutUsedDuringHold = otherKeyInteractedDuringHold

                val result = modifierStateController.handleCtrlKeyUp(keyCode)
                if (shortcutUsedDuringHold && ctrlOneShot && !ctrlLatchActive) {
                    modifierStateController.ctrlOneShot = false
                }
                if (result.shouldUpdateStatusBar || wasTap || shortcutUsedDuringHold) {
                    updateStatusBarText()
                }
                modifierDownTimes.remove(keyCode)
                variationInteractedDuringHold = false
                otherKeyInteractedDuringHold = false
                modifierStateBeforeHold = null
                true
            }
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                val result = modifierStateController.handleAltKeyUp(keyCode)
                if (result.shouldUpdateStatusBar || result.shouldRefreshStatusBar) {
                    updateStatusBarText()
                }
                modifierDownTimes.remove(keyCode)
                variationInteractedDuringHold = false
                otherKeyInteractedDuringHold = false
                modifierStateBeforeHold = null
                true
            }
            KEYCODE_SYM -> {
                val downTime = modifierDownTimes[keyCode] ?: 0L
                val now = SystemClock.uptimeMillis()
                val holdDuration = if (downTime > 0L) now - downTime else 0L
                if (holdDuration >= 300L && symLayoutController.currentSymPage() == 0) {
                    symChordUsedSinceKeyDown = true
                }
                modifierDownTimes.remove(keyCode)
                // Keep AOSP-rendered text SYM pages synchronous: the held-SYM preview and the
                // activated SYM page use the same view, so posting KEY_UP would draw one frame
                // of the base keyboard between them. Overlay pages replace the touched view and
                // must still be posted to avoid mutating the hierarchy during touch dispatch.
                if (symLayoutController.peekNextSymPage() in 3..4) {
                    uiHandler.post {
                        dispatchSoftwareKeyboardSyntheticKey(keyCode, KeyEvent.ACTION_UP)
                    }
                } else {
                    dispatchSoftwareKeyboardSyntheticKey(keyCode, KeyEvent.ACTION_UP)
                }
                true
            }
            else -> false
        }
    }

    private fun handleSoftwareKeyboardKeyStroke(keyCode: Int): Boolean {
        val consumeCtrlOneShotAfterStroke = ctrlOneShot && !ctrlLatchActive && !ctrlLatchFromNavMode
        val softwareModifierActive =
            symTogglePendingOnKeyUp ||
                symLayoutController.currentSymPage() in 1..4 ||
                altPressed ||
                altPhysicallyPressed ||
                altLatchActive ||
                altOneShot ||
                ctrlPressed ||
                ctrlPhysicallyPressed ||
                ctrlLatchActive ||
                ctrlOneShot ||
                ctrlLatchFromNavMode
        if (!softwareModifierActive) {
            return false
        }
        val (downHandled, upHandled) = try {
            dispatchingSoftwareKeyboardKey = true
            dispatchSoftwareKeyboardSyntheticKey(keyCode, KeyEvent.ACTION_DOWN) to
                dispatchSoftwareKeyboardSyntheticKey(keyCode, KeyEvent.ACTION_UP)
        } finally {
            dispatchingSoftwareKeyboardKey = false
        }
        if ((downHandled || upHandled) && SettingsManager.isQuickLauncherShortcut(this, keyCode)) {
            candidatesBarController.cancelSoftwareKeyboardTouchState()
        }
        if (consumeCtrlOneShotAfterStroke && (downHandled || upHandled)) {
            modifierStateController.ctrlOneShot = false
            updateStatusBarText()
        }
        return downHandled || upHandled
    }

    private fun dispatchSoftwareKeyboardSyntheticKey(keyCode: Int, action: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        val metaState = buildSoftwareKeyboardMetaState(keyCode)
        val event = KeyEvent(
            now,
            now,
            action,
            keyCode,
            0,
            metaState
        )
        return if (action == KeyEvent.ACTION_DOWN) {
            onKeyDown(keyCode, event)
        } else {
            onKeyUp(keyCode, event)
        }
    }

    private fun buildSoftwareKeyboardMetaState(keyCode: Int): Int {
        var metaState = 0
        val ctrlActive = keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
            keyCode == KeyEvent.KEYCODE_CTRL_RIGHT ||
            ctrlPressed ||
            ctrlPhysicallyPressed ||
            ctrlLatchActive ||
            ctrlOneShot ||
            ctrlLatchFromNavMode
        if (ctrlActive) {
            metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        }
        if (shiftPressed || shiftPhysicallyPressed || shiftOneShot || capsLockEnabled) {
            metaState = metaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        }
        if (altPressed || altPhysicallyPressed || altOneShot || altLatchActive) {
            metaState = metaState or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        }
        return metaState
    }

    private fun buildTrackpadGestureDetector(): TrackpadGestureDetector {
        val gesturesEnabled = SettingsManager.getTrackpadGesturesEnabled(this)
        val swipeThreshold = SettingsManager.getTrackpadSuggestionSwipeThreshold(this).toInt()
        val eventDevice = resolveTrackpadEventDevice()
        Log.d(
            TRACKPAD_DEBUG_TAG,
            "buildTrackpadGestureDetector() - gesturesEnabled=$gesturesEnabled, swipeThreshold=$swipeThreshold, eventDevice=$eventDevice"
        )
        return TrackpadGestureDetector(
            isEnabled = { shouldStartShizukuTrackpadDetector() },
            onSwipeUp = { third -> acceptSuggestionAtIndex(third) },
            scope = trackpadScope,
            swipeUpThreshold = swipeThreshold,
            eventDevice = eventDevice
        )
    }

    private fun resolveTrackpadEventDevice(): String {
        return TrackpadEventDeviceResolver.resolve(
            physicalKeyboardName = DeviceSpecific.physicalKeyboardName(),
            firmwareIncremental = Build.VERSION.INCREMENTAL.orEmpty()
        )
    }
    
    override fun onDestroy() {
        ClicksAccessibilityKeyBridge.unregister(this)
        accidentalKeyPressFilter.reset()
        clicksPowerButtonEventMapper.reset()
        expansionAssetScope.cancel()
        super.onDestroy()
        pendingInputDeviceModeRefresh?.let { uiHandler.removeCallbacks(it) }
        pendingInputDeviceModeRefresh = null
        pendingKeyboardSurfaceTransition?.let { uiHandler.removeCallbacks(it) }
        pendingKeyboardSurfaceTransition = null
        clicksConnectionChangePending = false
        clicksDisconnectPending = false
        connectedClicksInputDeviceIds.clear()
        inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        inputManager = null
        stopClipboardCleanupTimer()
        // Remove listener when service is destroyed
        prefsListener?.let {
            prefs.unregisterOnSharedPreferenceChangeListener(it)
        }
        
        // Cleanup SpeechRecognitionManager
        speechRecognitionManager?.destroy()
        speechRecognitionManager = null

        // Cleanup ClipboardHistoryManager
        if (::suggestionController.isInitialized) {
            suggestionController.destroy()
        }
        clipboardHistoryManager.setHistoryChangeListener(null)
        clipboardHistoryManager.onDestroy()
        typingSoundPlayer.release()

        // Unregister broadcast receiver (deprecated, but kept for backwards compatibility)
        speechResultReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error while unregistering broadcast receiver", e)
            }
        }
        
        // Unregister permission result receiver
        permissionResultReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error while unregistering permission result receiver", e)
            }
        }
        
        userDictionaryReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error while unregistering user dictionary receiver", e)
            }
        }
        
        additionalSubtypesReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error while unregistering additional subtypes receiver", e)
            }
        }
        speechResultReceiver = null
        multiTapController.cancelAll()
        updateNavModeStatusIcon(false)
        trackpadDecorMotionView?.setOnGenericMotionListener(null)
        trackpadDecorMotionView = null

        // Stop trackpad gesture detection
        trackpadGestureDetector.stop()
        trackpadScope.cancel()
    }

    private fun prepareEnabledExpansionAssets() {
        expansionAssetScope.launch {
            if (::emojiShortcodeSource.isInitialized && SettingsManager.getEmojiShortcodesEnabled(this@PhysicalKeyboardInputMethodService)) {
                emojiShortcodeSource.prepare()
            }
            if (::symbolShortcodeSource.isInitialized && SettingsManager.getSymbolShortcodesEnabled(this@PhysicalKeyboardInputMethodService)) {
                symbolShortcodeSource.prepare()
            }
        }
    }

    override fun onCreateInputView(): View? = keyboardVisibilityController.onCreateInputView()

    /**
     * Creates the candidates view shown when the soft keyboard is disabled.
     * Uses a separate StatusBarController instance to provide identical functionality.
     */
    override fun onCreateCandidatesView(): View? = keyboardVisibilityController.onCreateCandidatesView()

    /**
     * Determines whether the input view (soft keyboard) should be shown.
     * Respects the system flag (e.g. "Mostra tastiera virtuale" off for tastiere fisiche):
     * when the system asks for candidate-only mode we hide the main status UI and
     * expose the slim candidates view (LED strip + SYM layout on demand).
     */
    override fun onEvaluateInputViewShown(): Boolean {
        val systemShouldShowInputView = super.onEvaluateInputViewShown()
        val resolvedShowInputView =
            keyboardVisibilityController.onEvaluateInputViewShown(systemShouldShowInputView)
        requestedInputViewShown = resolvedShowInputView
        return resolvedShowInputView
    }

    override fun onComputeInsets(outInsets: InputMethodService.Insets?) {
        super.onComputeInsets(outInsets)
        outInsets?.let {
            val decorView = window?.window?.decorView
            ImeInsetsPolicy.applyCandidatesOnlyContentInsets(
                insets = it,
                candidatesOnly = !isFullscreenMode &&
                    if (::keyboardVisibilityController.isInitialized) {
                        keyboardVisibilityController.isCandidatesOnlySurface()
                    } else {
                        !requestedInputViewShown
                    },
                touchableWidth = decorView?.width ?: 0,
                touchableHeight = decorView?.height ?: 0
            )
        }
    }

    private fun synchronizeCandidatesContainerVisibility() {
        // InputMethodService can leave fullscreenArea INVISIBLE when an already-open input
        // window changes to candidates-only mode. Toggling the public extract-view state makes
        // the framework recompute that container; the second call restores the original state.
        setExtractViewShown(false)
        setExtractViewShown(true)
    }

    private fun requestKeyboardInputView() = requestShowSelf(0)

    /**
     * Evaluates whether the IME should run in fullscreen mode.
     */
    override fun onEvaluateFullscreenMode(): Boolean {
        // Keep the compact candidates surface available outside extract mode.
        return false
    }

    @Deprecated("Deprecated Android callback; kept to clear emoji search capture when the target view is clicked.")
    @Suppress("DEPRECATION")
    override fun onViewClicked(focusChanged: Boolean) {
        super.onViewClicked(focusChanged)
        if (symPage == 4 && ::candidatesBarController.isInitialized) {
            disableEmojiSearchInputCapture()
        }
    }

    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo?) {
        super.onUpdateCursorAnchorInfo(cursorAnchorInfo)
        if (
            symPage != 4 ||
            !::candidatesBarController.isInitialized ||
            !candidatesBarController.isEmojiPickerSearchInputActive()
        ) {
            return
        }
        if (ignoreNextEmojiSearchCursorAnchorUpdate) {
            ignoreNextEmojiSearchCursorAnchorUpdate = false
            return
        }
        disableEmojiSearchInputCapture()
    }

    /**
     * Resets all modifier key states.
     * Called when leaving a field or closing/reopening the keyboard.
     * @param preserveNavMode If true, keeps Ctrl latch active when nav mode is enabled.
     */
    private fun resetModifierStates(preserveNavMode: Boolean = false) {
        shiftLayerLatched = false
        altLayerLatched = false
        lastShiftTapUpTime = 0L
        lastAltTapUpTime = 0L
        modifierStateBeforeHold = null

        modifierStateController.resetModifiers(
            preserveNavMode = preserveNavMode,
            onNavModeCancelled = { navModeController.cancelNotification() }
        )
        
        symLayoutController.reset()
        altSymManager.resetTransientState()
        deactivateVariations()
        refreshStatusBar()
        navModeController.refreshNavModeState()
    }

    private fun disableEmojiSearchInputCapture() {
        if (::candidatesBarController.isInitialized) {
            candidatesBarController.disableEmojiPickerSearchInputCapture()
        }
        emojiSearchExternalSelectionStart = null
        emojiSearchExternalSelectionEnd = null
        emojiSearchCursorAnchorMonitoringRequested = false
        ignoreNextEmojiSearchCursorAnchorUpdate = false
        currentInputConnection?.requestCursorUpdates(0)
    }

    private fun updateEmojiSearchExternalSelectionSnapshot(inputConnection: InputConnection?) {
        val extracted = inputConnection?.getExtractedText(ExtractedTextRequest(), 0)
        emojiSearchExternalSelectionStart = extracted?.selectionStart
        emojiSearchExternalSelectionEnd = extracted?.selectionEnd
    }

    private fun shouldReturnEmojiSearchFocusToApp(inputConnection: InputConnection?): Boolean {
        val extracted = inputConnection?.getExtractedText(ExtractedTextRequest(), 0) ?: return false
        val previousStart = emojiSearchExternalSelectionStart
        val previousEnd = emojiSearchExternalSelectionEnd
        emojiSearchExternalSelectionStart = extracted.selectionStart
        emojiSearchExternalSelectionEnd = extracted.selectionEnd
        if (previousStart == null || previousEnd == null) {
            return false
        }
        return previousStart != extracted.selectionStart || previousEnd != extracted.selectionEnd
    }

    private fun ensureEmojiSearchCursorAnchorMonitoring(inputConnection: InputConnection?) {
        if (emojiSearchCursorAnchorMonitoringRequested) {
            return
        }
        val requested = inputConnection?.requestCursorUpdates(
            InputConnection.CURSOR_UPDATE_IMMEDIATE or InputConnection.CURSOR_UPDATE_MONITOR
        ) == true
        if (requested) {
            emojiSearchCursorAnchorMonitoringRequested = true
            ignoreNextEmojiSearchCursorAnchorUpdate = true
        }
    }
    
    /**
     * Forces creation and display of the input view.
     * Called when the first physical key is pressed.
     * Shows the keyboard if there is an active text field.
     * IMPORTANT: UI is never shown in nav mode.
     */
    private fun ensureInputViewCreated() {
        keyboardVisibilityController.ensureInputViewCreated()
    }
    /**
     * Aggiorna la status bar delegando al controller dedicato.
     */
    private fun updateStatusBarText() {
        val totalStart = ImePerfLogger.mark()
        var variationMs = 0L
        var suggestionsMs = 0L
        var updateBarsMs = 0L

        val pastierinaModeActive = candidatesBarController.isPastierinaModeActive()
        val effectiveSoftwareKeyboardMode = SettingsManager.resolveEffectiveSoftwareKeyboardMode(this)
        val variationStart = ImePerfLogger.mark()
        val variationSnapshot = if (pastierinaModeActive) {
            VariationStateController.Snapshot(isActive = false, lastInsertedChar = null, variations = emptyList())
        } else {
            variationStateController.refreshFromCursor(
                currentInputConnection,
                inputContextState.shouldDisableVariations,
                hasActiveSelection = editorHasActiveSelection
            )
        }
        variationMs = ImePerfLogger.elapsedMs(variationStart)
        val clipboardCount = clipboardHistoryManager?.getHistorySize() ?: 0
        
        val modifierSnapshot = modifierStateController.snapshot()
        val state = inputContextState
        val addWordCandidate = suggestionController.pendingAddWord()
        val suggestionsEnabled = SettingsManager.isExperimentalSuggestionsEnabled(this) && SettingsManager.getSuggestionsEnabled(this)
        val suggestionsStart = ImePerfLogger.mark()
        val baseSuggestions = if (suggestionsEnabled) visibleSuggestionStrings() else emptyList()
        suggestionsMs = ImePerfLogger.elapsedMs(suggestionsStart)
        val snapshot = StatusBarController.StatusSnapshot(
            capsLockEnabled = modifierSnapshot.capsLockEnabled,
            shiftPhysicallyPressed = modifierSnapshot.shiftPhysicallyPressed,
            shiftOneShot = modifierSnapshot.shiftOneShot,
            ctrlLatchActive = modifierSnapshot.ctrlLatchActive,
            ctrlPhysicallyPressed = modifierSnapshot.ctrlPhysicallyPressed,
            ctrlOneShot = modifierSnapshot.ctrlOneShot,
            ctrlLatchFromNavMode = modifierSnapshot.ctrlLatchFromNavMode,
            altLatchActive = modifierSnapshot.altLatchActive,
            altPhysicallyPressed = modifierSnapshot.altPhysicallyPressed,
            altOneShot = modifierSnapshot.altOneShot,
            symPage = symPage,
            clipboardCount = clipboardCount,
            variations = variationSnapshot.variations,
            suggestions = baseSuggestions,
            addWordCandidate = addWordCandidate,
            lastInsertedChar = variationSnapshot.lastInsertedChar,
            // Granular smart features flags
            shouldDisableSuggestions = state.shouldDisableSuggestions,
            shouldDisableAutoCorrect = state.shouldDisableAutoCorrect,
            shouldDisableAutoCapitalize = shouldDisableAutoCapitalize,
            shouldDisableDoubleSpaceToPeriod = state.shouldDisableDoubleSpaceToPeriod,
            shouldDisableVariations = state.shouldDisableVariations,
            isEmailField = state.isEmailField,
            shiftLayerLatched = shiftLayerLatched,
            altLayerLatched = altLayerLatched,
            activeKeyboardLayoutName = activeKeyboardLayoutName,
            softwareSymPreviewLabels = buildSoftwareSymPreviewLabels(modifierSnapshot),
            softwareSymPreviewTextLabels = buildSoftwareSymPreviewTextLabels(modifierSnapshot),
            softwareCtrlPreviewLabels = buildSoftwareCtrlPreviewLabels(modifierSnapshot),
            softwareCtrlPreviewIconRes = buildSoftwareCtrlPreviewIconRes(modifierSnapshot),
            softwareCtrlPreviewActive = shouldShowSoftwareCtrlPreview(modifierSnapshot),
            softwareAltPreviewLabels = buildSoftwareAltPreviewLabels(modifierSnapshot),
            softwareAltPreviewActive = shouldShowSoftwareAltPreview(modifierSnapshot),
            // Legacy flag for backward compatibility
            shouldDisableSmartFeatures = shouldDisableSmartFeatures
        )
        updateSystemStatusModifierIcon(snapshot, effectiveSoftwareKeyboardMode)
        val modifierIndicators = SettingsManager.getModifierIndicators(this)
        // Passa anche la mappa emoji quando SYM è attivo (solo pagina 1)
        val emojiMapText = symLayoutController.emojiMapText()
        // Passa le mappature SYM per la griglia emoji/caratteri
        val symMappings = symLayoutController.currentSymMappings()?.toMap()
        // Passa l'inputConnection per rendere i pulsanti clickabili
        val inputConnection = currentInputConnection
        val unchangedRenderedState =
            snapshot == lastRenderedStatusSnapshot &&
                emojiMapText == lastRenderedEmojiMapText &&
                symMappings == lastRenderedSymMappings &&
                inputConnection === lastRenderedStatusInputConnection &&
                pastierinaModeActive == lastRenderedPastierinaModeActive &&
                effectiveSoftwareKeyboardMode == lastRenderedSoftwareKeyboardMode &&
                modifierIndicators == lastRenderedModifierIndicators
        if (!unchangedRenderedState) {
            val updateBarsStart = ImePerfLogger.mark()
            candidatesBarController.updateStatusBars(snapshot, emojiMapText, inputConnection, symMappings)
            updateBarsMs = ImePerfLogger.elapsedMs(updateBarsStart)
            lastRenderedStatusSnapshot = snapshot
            lastRenderedEmojiMapText = emojiMapText
            lastRenderedSymMappings = symMappings
            lastRenderedStatusInputConnection = inputConnection
            lastRenderedPastierinaModeActive = pastierinaModeActive
            lastRenderedSoftwareKeyboardMode = effectiveSoftwareKeyboardMode
            lastRenderedModifierIndicators = modifierIndicators
        }
        ImePerfLogger.logDuration(
            label = "updateStatusBarText",
            startNanos = totalStart,
            thresholdMs = 16L,
            details = "variation=${variationMs}ms suggestions=${suggestionsMs}ms updateBars=${updateBarsMs}ms pkg=$currentPackageName"
        )
    }

    private fun buildSoftwareSymPreviewLabels(
        modifierSnapshot: it.palsoftware.pastiera.core.ModifierStateController.Snapshot
    ): Map<Int, String> {
        val shiftActive = modifierSnapshot.capsLockEnabled ||
            modifierSnapshot.shiftPhysicallyPressed ||
            modifierSnapshot.shiftOneShot
        return symLayoutController.previewNextSoftwareSymPageMappings(shiftActive)
    }

    private fun buildSoftwareSymPreviewTextLabels(
        modifierSnapshot: it.palsoftware.pastiera.core.ModifierStateController.Snapshot
    ): Map<String, String> {
        val shiftActive = modifierSnapshot.capsLockEnabled ||
            modifierSnapshot.shiftPhysicallyPressed ||
            modifierSnapshot.shiftOneShot
        val mappings = symLayoutController.previewNextSoftwareSymPageMappings(shiftActive)
        if (mappings.isEmpty()) {
            return emptyMap()
        }
        return SoftwareKeyboardSymLabels.buildContentByChar(
            page = symLayoutController.nextSoftwareTextSymPage(),
            rows = SoftwareKeyboardLayoutTemplates.rowTemplateFor(
                activeKeyboardLayoutName,
                softwareKeyboardLayoutStyle()
            ),
            symMappings = mappings,
            layoutName = activeKeyboardLayoutName
        ).mapKeys { (char, _) -> char.toString() }
    }

    private fun softwareKeyboardLayoutStyle(): AospKeyboardView.SoftwareLayoutStyle =
        when (SettingsManager.getSoftwareKeyboardLayoutStyle(this)) {
            SettingsManager.SoftwareKeyboardLayoutStyle.COMPACT -> AospKeyboardView.SoftwareLayoutStyle.COMPACT
            SettingsManager.SoftwareKeyboardLayoutStyle.EXTENDED_ISO -> AospKeyboardView.SoftwareLayoutStyle.EXTENDED_ISO
            SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ANSI -> AospKeyboardView.SoftwareLayoutStyle.FULL_ANSI
            SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ISO -> AospKeyboardView.SoftwareLayoutStyle.FULL_ISO
        }

    private fun shouldShowSoftwareCtrlPreview(
        modifierSnapshot: it.palsoftware.pastiera.core.ModifierStateController.Snapshot
    ): Boolean {
        return when {
            modifierSnapshot.ctrlLatchActive -> true
            modifierSnapshot.ctrlOneShot -> true
            modifierSnapshot.ctrlPhysicallyPressed -> SettingsManager.getNavModeCtrlHoldEnabled(this)
            else -> false
        }
    }

    private fun buildSoftwareCtrlPreviewLabels(
        modifierSnapshot: it.palsoftware.pastiera.core.ModifierStateController.Snapshot
    ): Map<Int, String> {
        if (!shouldShowSoftwareCtrlPreview(modifierSnapshot)) {
            return emptyMap()
        }
        return SOFTWARE_PREVIEW_KEY_CODES.mapNotNull { keyCode ->
            val shortcutKeyCode = resolveSoftwareCtrlPreviewShortcutKeyCode(keyCode, modifierSnapshot)
            val mapping = ctrlKeyMap[shortcutKeyCode] ?: return@mapNotNull null
            val label = softwareCtrlPreviewLabel(mapping) ?: return@mapNotNull null
            keyCode to label
        }.toMap()
    }

    private fun buildSoftwareCtrlPreviewIconRes(
        modifierSnapshot: it.palsoftware.pastiera.core.ModifierStateController.Snapshot
    ): Map<Int, Int> {
        if (!shouldShowSoftwareCtrlPreview(modifierSnapshot)) {
            return emptyMap()
        }
        return SOFTWARE_PREVIEW_KEY_CODES.mapNotNull { keyCode ->
            val shortcutKeyCode = resolveSoftwareCtrlPreviewShortcutKeyCode(keyCode, modifierSnapshot)
            val mapping = ctrlKeyMap[shortcutKeyCode] ?: return@mapNotNull null
            val iconRes = softwareCtrlPreviewIconRes(mapping) ?: return@mapNotNull null
            keyCode to iconRes
        }.toMap()
    }

    private fun shouldShowSoftwareAltPreview(
        modifierSnapshot: it.palsoftware.pastiera.core.ModifierStateController.Snapshot
    ): Boolean =
        modifierSnapshot.altLatchActive ||
            modifierSnapshot.altOneShot ||
            modifierSnapshot.altPhysicallyPressed

    private fun buildSoftwareAltPreviewLabels(
        modifierSnapshot: it.palsoftware.pastiera.core.ModifierStateController.Snapshot
    ): Map<Int, String> {
        if (!shouldShowSoftwareAltPreview(modifierSnapshot)) {
            return emptyMap()
        }
        val altMappings = KeyMappingLoader.loadVirtualAltKeyMappings(assets, this)
        return SOFTWARE_PREVIEW_KEY_CODES.mapNotNull { keyCode ->
            val label = altMappings[keyCode]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            keyCode to label
        }.toMap()
    }

    private fun resolveSoftwareCtrlPreviewShortcutKeyCode(
        keyCode: Int,
        modifierSnapshot: it.palsoftware.pastiera.core.ModifierStateController.Snapshot
    ): Int {
        val usesPhysicalNavGrid = modifierSnapshot.ctrlLatchFromNavMode ||
            (modifierSnapshot.ctrlPhysicallyPressed && SettingsManager.getNavModeCtrlHoldEnabled(this))
        if (usesPhysicalNavGrid) {
            return keyCode
        }
        if (!SettingsManager.getLayoutAwareCtrlShortcutsEnabled(this)) {
            return keyCode
        }
        val mappedChar = LayoutMappingRepository.getCharacter(keyCode, isShift = false)
            ?.lowercaseChar()
            ?: return keyCode
        return if (mappedChar in 'a'..'z') {
            KeyEvent.KEYCODE_A + (mappedChar - 'a')
        } else {
            keyCode
        }
    }

    private fun softwareCtrlPreviewLabel(mapping: KeyMappingLoader.CtrlMapping): String? {
        return when (mapping.type) {
            "keycode" -> when (mapping.value) {
                "DPAD_UP" -> "↑"
                "DPAD_DOWN" -> "↓"
                "DPAD_LEFT" -> "←"
                "DPAD_RIGHT" -> "→"
                "DPAD_CENTER" -> "OK"
                "MOVE_HOME" -> "Home"
                "MOVE_END" -> "End"
                "PAGE_UP" -> "PgUp"
                "PAGE_DOWN" -> "PgDn"
                "ESCAPE" -> "Esc"
                "TAB" -> "Tab"
                "FORWARD_DEL" -> "Del"
                else -> mapping.value.removePrefix("KEYCODE_")
            }
            "action" -> when (mapping.value) {
                "copy" -> "Copy"
                "paste" -> "Paste"
                "cut" -> "Cut"
                "undo" -> "Undo"
                "select_all" -> "All"
                "expand_selection_left" -> "Sel ←"
                "expand_selection_right" -> "Sel →"
                "move_word_left" -> "← word"
                "move_word_right" -> "word →"
                "expand_selection_word_left" -> "Sel word ←"
                "expand_selection_word_right" -> "Sel word →"
                "page_start" -> "Start"
                "page_end" -> "End"
                "toggle_minimal_ui" -> "Mini"
                "media_play_pause" -> "Play"
                "media_previous" -> "Prev"
                "media_next" -> "Next"
                else -> mapping.value
            }
            "command" -> mapping.value.substringAfterLast('.').replace('_', ' ').takeIf { it.isNotBlank() }
            "native_ctrl" -> "Ctrl"
            "none" -> null
            else -> null
        }
    }

    private fun softwareCtrlPreviewIconRes(mapping: KeyMappingLoader.CtrlMapping): Int? {
        return when (mapping.type) {
            "keycode" -> when (mapping.value) {
                "DPAD_UP" -> R.drawable.keyboard_arrow_up_24
                "DPAD_DOWN" -> R.drawable.keyboard_arrow_down_24
                "DPAD_LEFT" -> R.drawable.keyboard_arrow_left_24
                "DPAD_RIGHT" -> R.drawable.keyboard_arrow_right_24
                "TAB" -> R.drawable.keyboard_tab_24
                "MOVE_HOME" -> R.drawable.first_page_24
                "MOVE_END" -> R.drawable.last_page_24
                "PAGE_UP" -> R.drawable.keyboard_double_arrow_up_24
                "PAGE_DOWN" -> R.drawable.keyboard_double_arrow_down_24
                "ESCAPE" -> R.drawable.close_24
                "FORWARD_DEL" -> R.drawable.delete_24
                else -> null
            }
            "action" -> when (mapping.value) {
                "copy" -> R.drawable.content_copy_24
                "paste" -> R.drawable.content_paste_24
                "cut" -> R.drawable.content_cut_24
                "undo" -> R.drawable.undo_24
                "select_all" -> R.drawable.select_all_24
                "expand_selection_left" -> R.drawable.text_select_move_back_character_filled_24
                "expand_selection_right" -> R.drawable.text_select_move_forward_character_filled_24
                "move_word_left" -> R.drawable.text_select_move_back_word_24
                "move_word_right" -> R.drawable.text_select_move_forward_word_24
                "expand_selection_word_left" -> R.drawable.text_select_move_back_word_filled_24
                "expand_selection_word_right" -> R.drawable.text_select_move_forward_word_filled_24
                "page_start" -> R.drawable.first_page_24
                "page_end" -> R.drawable.last_page_24
                "toggle_minimal_ui" -> if (SettingsManager.getPastierinaModeActive(this)) {
                    R.drawable.expand_content_24
                } else {
                    R.drawable.collapse_content_24
                }
                "media_play_pause" -> R.drawable.play_pause_24
                "media_previous" -> R.drawable.skip_previous_24
                "media_next" -> R.drawable.skip_next_24
                else -> null
            }
            "command" -> when (mapping.value) {
                "pastiera.toggle_software_keyboard_mode" -> R.drawable.expansion_panels_24
                else -> null
            }
            else -> null
        }
    }
    
    /**
     * Disattiva le variazioni.
     */
    private fun deactivateVariations() {
        if (::variationStateController.isInitialized) {
            variationStateController.clear()
        }
    }
    

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        if (::textExpansionController.isInitialized) textExpansionController.clear()
        if (
            !restarting ||
            !SettingsManager.getAutoCapitalizeRespectManualShiftOff(this)
        ) {
            // A manual Shift-off suppresses auto-cap only for the current field session.
            // Text context alone cannot identify a field: every empty editor looks like "|".
            clearAutoCapSuppression()
        }
        DeferredPunctuationSpaceTracker.clear()
        bounceKeyFilter.reset()
        accidentalKeyPressFilter.reset()
        cancelPendingSelectionDrivenUiWork()
        invalidateRenderedStatusSnapshot()
        editorHasActiveSelection = false
        
        currentPackageName = info?.packageName
        updateDebugImeContextSnapshot(info)
        
        // Reset clipboard overlay when starting new input

        updateInputContextState(info)
        val state = inputContextState
        val isEditable = state.isEditable
        val isReallyEditable = state.isReallyEditable
        isInputViewActive = isEditable
        
        if (restarting) {
            enforceSmartFeatureDisabledState()
        }
        
        if (info != null && isEditable) {
            info.inputType = info.inputType or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        
        if (isEditable && !restarting) {
            val autoShowKeyboardEnabled = SettingsManager.getAutoShowKeyboard(this)
            if (autoShowKeyboardEnabled && isReallyEditable) {
                if (!isInputViewShown && isInputViewActive) {
                    ensureInputViewCreated()
                }
            }
        }
        
        if (!restarting) {
            if (ctrlLatchFromNavMode && ctrlLatchActive) {
                val inputConnection = currentInputConnection
                val hasValidInputConnection = inputConnection != null

                if (isReallyEditable && hasValidInputConnection) {
                    // Ricorda che nav mode era attivo prima di entrare nel campo di testo
                    navModeWasActiveBeforeEditableField = true
                    navModeController.exitNavMode()
                    resetModifierStates(preserveNavMode = false)
                }
            } else if (isEditable || !ctrlLatchFromNavMode) {
                resetModifierStates(preserveNavMode = false)
            }
        }

        initializeInputContext(restarting)
        suggestionController.onContextReset()

        // Always reset shift one-shot when entering a field (both restarting and new field)
        // Then let auto-cap logic decide if it should be enabled
        if (isEditable) {
            modifierStateController.consumeShiftOneShot()
            
            // Handle input field capitalization flags (CAP_CHARACTERS, CAP_WORDS, CAP_SENTENCES)
            AutoCapitalizeHelper.handleInputFieldCapitalizationFlags(
                context = this,
                state = state,
                inputConnection = currentInputConnection,
                enableCapsLock = { modifierStateController.capsLockEnabled = true },
                enableShiftOneShot = { requestAutoCapShiftOneShot() },
                onUpdateStatusBar = { updateStatusBarText() }
            )
            
            AutoCapitalizeHelper.checkAutoCapitalizeOnRestart(
                this,
                currentInputConnection,
                shouldDisableAutoCapitalize,
                enableShift = { requestAutoCapShiftOneShot() },
                disableShift = { modifierStateController.consumeShiftOneShot() },
                onUpdateStatusBar = { updateStatusBarText() },
                inputContextState = state
            )
        }

        startClipboardCleanupTimer()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (::textExpansionController.isInitialized) textExpansionController.clear()
        updateDebugImeContextSnapshot(info)
        attachTrackpadDecorViewMotionHook("onStartInputView")

        updateInputContextState(info)
        initializeInputContext(restarting)
        suggestionController.onContextReset()
        
        // Read word at cursor immediately when entering a populated text field
        if (!inputContextState.shouldDisableSuggestions) {
            suggestionController.readInitialContext(currentInputConnection)
        }

        val isEditable = inputContextState.isEditable
        val state = inputContextState
        
        // Always reset shift one-shot when entering a field (both restarting and new field)
        // Then let auto-cap logic decide if it should be enabled
        if (isEditable) {
            modifierStateController.consumeShiftOneShot()
            
            // Handle input field capitalization flags (CAP_CHARACTERS, CAP_WORDS, CAP_SENTENCES)
            AutoCapitalizeHelper.handleInputFieldCapitalizationFlags(
                context = this,
                state = state,
                inputConnection = currentInputConnection,
                enableCapsLock = { modifierStateController.capsLockEnabled = true },
                enableShiftOneShot = { requestAutoCapShiftOneShot() },
                onUpdateStatusBar = { updateStatusBarText() }
            )
            
            AutoCapitalizeHelper.checkAutoCapitalizeOnRestart(
                this,
                currentInputConnection,
                shouldDisableAutoCapitalize,
                enableShift = { requestAutoCapShiftOneShot() },
                disableShift = { modifierStateController.consumeShiftOneShot() },
                onUpdateStatusBar = { updateStatusBarText() },
                inputContextState = state
            )
        }

        // Check if trackpad gestures should be started
        if (::trackpadGestureDetector.isInitialized) {
            val gesturesEnabled = SettingsManager.getTrackpadGesturesEnabled(this)
            if (shouldStartShizukuTrackpadDetector() && !trackpadGestureDetector.isRunning()) {
                val shizukuRunning = try { Shizuku.pingBinder() } catch (e: Exception) { false }
                val shizukuAuthorized = try { 
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED 
                } catch (e: Exception) { false }
                
                if (shizukuRunning && shizukuAuthorized) {
                    Log.d(TRACKPAD_DEBUG_TAG, "onStartInputView: Gestures enabled and Shizuku ready, starting detector...")
                    trackpadGestureDetector.start()
                } else {
                    Log.d(TRACKPAD_DEBUG_TAG, "onStartInputView: Gestures enabled but Shizuku not ready (running=$shizukuRunning, authorized=$shizukuAuthorized)")
                }
            } else if (!shouldStartShizukuTrackpadDetector() && trackpadGestureDetector.isRunning()) {
                Log.d(TRACKPAD_DEBUG_TAG, "onStartInputView: stopping Shizuku detector for non-Shizuku provider")
                trackpadGestureDetector.stop()
            } else if (gesturesEnabled && trackpadGestureDetector.isRunning()) {
                Log.d(TRACKPAD_DEBUG_TAG, "onStartInputView: Gestures enabled and detector already running, skipping")
            }
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        if (::textExpansionController.isInitialized) textExpansionController.clear()
        keyboardVisibilityController.cancelPendingSurfaceTransition()
        accidentalKeyPressFilter.reset()
        isInputViewActive = false
        if (::candidatesBarController.isInitialized) {
            candidatesBarController.resetSuggestionActionMode()
        }
        inputContextState = InputContextState.EMPTY
        multiTapController.cancelAll()
        disableEmojiSearchInputCapture()
        resetModifierStates(preserveNavMode = true)
        // Se nav mode era attivo prima di entrare nel campo di testo, riattivalo ora
        if (navModeWasActiveBeforeEditableField) {
            navModeController.enterNavMode()
            navModeWasActiveBeforeEditableField = false
        } else if (!navModeController.isNavModeActive()) {
            hideStatusIcon()
            lastSystemStatusIconResId = null
        }
    }
    
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (::textExpansionController.isInitialized) textExpansionController.clear()
        isInputViewActive = false
        if (::candidatesBarController.isInitialized) {
            candidatesBarController.resetSuggestionActionMode()
        }
        stopClipboardCleanupTimer()
        if (finishingInput) {
            multiTapController.cancelAll()
            resetModifierStates(preserveNavMode = true)
            suggestionController.onContextReset()
            if (!navModeController.isNavModeActive()) {
                hideStatusIcon()
                lastSystemStatusIconResId = null
            }
        }
    }

    private fun updateDebugImeContextSnapshot(info: EditorInfo?) {
        val imm = getSystemService(InputMethodManager::class.java)
        val subtype = imm.currentInputMethodSubtype
        val resolvedLayout = runCatching {
            AdditionalSubtypeUtils.resolveActiveLayout(assets, this, subtype)
        }.getOrNull()
        DebugCaptureStore.updateImeContext(
            packageName = info?.packageName ?: currentPackageName,
            inputType = info?.inputType,
            subtypeLocale = subtype?.localeString(),
            resolvedLayout = resolvedLayout,
            physicalProfileOverride = physicalKeyboardProfileOverride
        )
    }
    
    override fun onWindowShown() {
        super.onWindowShown()
        updateStatusBarText()
        attachTrackpadDecorViewMotionHook("onWindowShown")
    }

    private fun shouldStartShizukuTrackpadDetector(): Boolean {
        return SettingsManager.getTrackpadGesturesEnabled(this) &&
            SettingsManager.getTrackpadProvider(this) == SettingsManager.TRACKPAD_PROVIDER_SHIZUKU
    }

    private fun isNativeImeTrackpadProviderActive(): Boolean {
        return SettingsManager.getTrackpadGesturesEnabled(this) &&
            SettingsManager.getTrackpadProvider(this) == SettingsManager.TRACKPAD_PROVIDER_NATIVE_IME
    }

    private fun attachTrackpadDecorViewMotionHook(reason: String) {
        val decorView = window?.window?.decorView
        if (decorView == null) {
            Log.d(TRACKPAD_DEBUG_TAG, "DecorView hook skipped[$reason]: no IME decorView")
            return
        }

        if (trackpadDecorMotionView === decorView) {
            decorView.isFocusableInTouchMode = true
            decorView.requestFocus()
            Log.d(TRACKPAD_DEBUG_TAG, "DecorView hook refreshed[$reason]: view=${decorView.javaClass.simpleName}")
            return
        }

        trackpadDecorMotionView?.setOnGenericMotionListener(null)
        trackpadDecorMotionView = decorView
        decorView.isFocusableInTouchMode = true
        decorView.requestFocus()
        decorView.setOnGenericMotionListener { _, event ->
            handleNativeImeTrackpadMotion(event, origin = "ime_decor")
        }
        Log.d(
            TRACKPAD_DEBUG_TAG,
            "DecorView hook attached[$reason]: view=${decorView.javaClass.simpleName}, focused=${decorView.isFocused}"
        )
    }
    
    /**
     * Registers additional subtypes (custom input styles) with the system.
     * Called on startup and when custom input styles are modified.
     */
    private fun registerAdditionalSubtypes() {
        try {
            val imm = getSystemService(InputMethodManager::class.java)
            
            // Get IME ID - try both formats
            val componentName = android.content.ComponentName(this, PhysicalKeyboardInputMethodService::class.java)
            val imeIdShort = componentName.flattenToShortString()
            val imeIdFull = componentName.flattenToString()
            
            // Find the actual IME in the system list to get the correct ID format
            val inputMethodInfo = imm.getInputMethodList().firstOrNull { info ->
                info.packageName == packageName && 
                info.serviceName == PhysicalKeyboardInputMethodService::class.java.name
            }
            
            val imeId = inputMethodInfo?.id ?: imeIdFull
            
            Log.d(TAG, "Registering additional subtypes")
            Log.d(TAG, "Component: $componentName")
            Log.d(TAG, "IME ID (short): $imeIdShort")
            Log.d(TAG, "IME ID (full): $imeIdFull")
            Log.d(TAG, "IME ID (from system): ${inputMethodInfo?.id}")
            Log.d(TAG, "Using IME ID: $imeId")
            Log.d(TAG, "IME found in system: ${inputMethodInfo != null}")
            
            val prefString = SettingsManager.getCustomInputStyles(this)
            Log.d(TAG, "Custom input styles pref string: $prefString")
            
            val subtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(
                prefString,
                assets,
                this
            )
            
            Log.d(TAG, "Created ${subtypes.size} additional subtypes")
            subtypes.forEachIndexed { index, subtype ->
                Log.d(TAG, "Subtype $index: locale=${subtype.localeString()}, nameResId=${subtype.nameResId}, extraValue=${subtype.extraValue}")
            }
            
            if (subtypes.isNotEmpty() && inputMethodInfo != null) {
                // Note: setAdditionalInputMethodSubtypes is deprecated but still works on most Android versions
                // The subtypes will appear in the IME picker but may need to be enabled manually by the user
                setAdditionalInputMethodSubtypesCompat(imm, imeId, subtypes)
                Log.d(TAG, "Successfully called setAdditionalInputMethodSubtypes with ${subtypes.size} subtypes")
                
                // Send broadcast to notify system of IME subtype changes (if supported)
                try {
                    val intent = Intent("android.view.InputMethod.SUBTYPE_CHANGED").apply {
                        setPackage("android")
                        putExtra("imeId", imeId)
                    }
                    sendBroadcast(intent)
                    Log.d(TAG, "Sent SUBTYPE_CHANGED broadcast")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not send SUBTYPE_CHANGED broadcast", e)
                }
                
                // Try to explicitly enable the additional subtypes after a delay
                // This ensures the system has processed the registration first
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        // Re-fetch InputMethodInfo to get updated subtype list
                        val updatedInfo = imm.getInputMethodList().firstOrNull { 
                            it.packageName == packageName && 
                            it.serviceName == PhysicalKeyboardInputMethodService::class.java.name
                        }
                        
                        if (updatedInfo != null) {
                            // Get all subtypes from InputMethodInfo (including base from method.xml and additional)
                            val allSubtypes = mutableListOf<android.view.inputmethod.InputMethodSubtype>()
                            for (i in 0 until updatedInfo.subtypeCount) {
                                allSubtypes.add(updatedInfo.getSubtypeAt(i))
                            }
                            
                            // Get current system locales to filter out removed ones
                            val currentSystemLocales = getSystemEnabledLocales()
                            val systemLanguageCodes = currentSystemLocales.map { locale ->
                                locale.split("_").first().lowercase()
                            }.toSet()
                            
                            // Filter ALL subtypes (base + additional) to keep only visible, valid input styles.
                            val validSubtypes = allSubtypes.filter { subtype ->
                                AdditionalSubtypeUtils.shouldKeepSubtype(
                                    this,
                                    assets,
                                    subtype,
                                    currentSystemLocales,
                                    systemLanguageCodes
                                )
                            }
                            
                            // Convert to hash codes for setExplicitlyEnabledInputMethodSubtypes
                            val validEnabledHashCodes = validSubtypes.map { it.hashCode() }.toIntArray()
                            
                            // Always update enabled subtypes, even if empty (to disable removed ones)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                imm.setExplicitlyEnabledInputMethodSubtypes(
                                    updatedInfo.id,
                                    validEnabledHashCodes
                                )
                                val removedBase = allSubtypes.count { !AdditionalSubtypeUtils.isAdditionalSubtype(it) } -
                                        validSubtypes.count { !AdditionalSubtypeUtils.isAdditionalSubtype(it) }
                                val removedAdditional = subtypes.size - validSubtypes.count { AdditionalSubtypeUtils.isAdditionalSubtype(it) }
                                Log.d(TAG, "Updated enabled subtypes: ${validEnabledHashCodes.size} valid (removed ${removedBase} base, ${removedAdditional} additional)")
                            } else {
                                Log.d(TAG, "Skipping explicit subtype enable: requires Android 14+")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not explicitly enable subtypes", e)
                        e.printStackTrace()
                    }
                }, 500) // Wait 500ms for system to process registration
            } else {
                // Even when there are no additional subtypes, we should still filter enabled subtypes
                // to remove base subtypes corresponding to removed system locales
                if (inputMethodInfo != null) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            val updatedInfo = imm.getInputMethodList().firstOrNull { 
                                it.packageName == packageName && 
                                it.serviceName == PhysicalKeyboardInputMethodService::class.java.name
                            }
                            
                            if (updatedInfo != null) {
                                // Get all subtypes from InputMethodInfo (base subtypes from method.xml)
                                val allSubtypes = mutableListOf<android.view.inputmethod.InputMethodSubtype>()
                                for (i in 0 until updatedInfo.subtypeCount) {
                                    allSubtypes.add(updatedInfo.getSubtypeAt(i))
                                }
                                
                                val currentSystemLocales = getSystemEnabledLocales()
                                val systemLanguageCodes = currentSystemLocales.map { locale ->
                                    locale.split("_").first().lowercase()
                                }.toSet()
                                
                                // Filter to keep only visible subtypes with valid system locales.
                                val validSubtypes = allSubtypes.filter { subtype ->
                                    AdditionalSubtypeUtils.shouldKeepSubtype(
                                        this,
                                        assets,
                                        subtype,
                                        currentSystemLocales,
                                        systemLanguageCodes
                                    )
                                }
                                
                                val validEnabledHashCodes = validSubtypes.map { it.hashCode() }.toIntArray()
                                
                                // Always update to disable removed subtypes
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    imm.setExplicitlyEnabledInputMethodSubtypes(
                                        updatedInfo.id,
                                        validEnabledHashCodes
                                    )
                                    Log.d(TAG, "Filtered base subtypes: kept ${validEnabledHashCodes.size}, removed ${allSubtypes.size - validSubtypes.size}")
                                } else {
                                    Log.d(TAG, "Skipping base subtype filter update: requires Android 14+")
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not filter enabled subtypes", e)
                        }
                    }, 500)
                }
                
                if (subtypes.isEmpty()) {
                    Log.d(TAG, "No subtypes to register")
                } else {
                    Log.w(TAG, "Cannot register subtypes: InputMethodInfo not found")
                }
            }
            
            // Refresh subtype caches if needed
            refreshSubtypeCaches()
            
            // Force a small delay to ensure system processes the registration
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val verifyInfo = imm.getInputMethodList().firstOrNull { 
                        it.packageName == packageName && 
                        it.serviceName == PhysicalKeyboardInputMethodService::class.java.name
                    }
                    if (verifyInfo != null) {
                        // Check all subtypes (enabled and disabled)
                        val allSubtypes = imm.getEnabledInputMethodSubtypeList(verifyInfo, true)
                        Log.d(TAG, "Verification: ${allSubtypes.size} total subtypes found after registration")
                        allSubtypes.forEachIndexed { index, subtype ->
                            val isAdditional = AdditionalSubtypeUtils.isAdditionalSubtype(subtype)
                            Log.d(TAG, "Subtype $index: locale=${subtype.localeString()}, isAdditional=$isAdditional, extraValue=${subtype.extraValue}")
                        }
                        
                        // Also try to get subtypes directly from InputMethodInfo
                        try {
                            val subtypeCount = verifyInfo.subtypeCount
                            Log.d(TAG, "InputMethodInfo reports $subtypeCount subtypes")
                            for (i in 0 until subtypeCount) {
                                val subtype = verifyInfo.getSubtypeAt(i)
                                val isAdditional = AdditionalSubtypeUtils.isAdditionalSubtype(subtype)
                                Log.d(TAG, "InputMethodInfo subtype $i: locale=${subtype.localeString()}, isAdditional=$isAdditional, extraValue=${subtype.extraValue}")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error getting subtypes from InputMethodInfo", e)
                        }
                    } else {
                        Log.w(TAG, "IME not found in system list for verification")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error verifying subtype registration", e)
                }
            }, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering additional subtypes", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Refreshes subtype caches after registration.
     * This ensures getEnabledInputMethodSubtypeList reflects the new subtypes.
     */
    private fun refreshSubtypeCaches() {
        try {
            val imm = getSystemService(InputMethodManager::class.java)
            // Force refresh by getting the enabled subtypes list
            val inputMethodInfo = imm.getInputMethodList().firstOrNull { 
                it.id == packageName + "/" + PhysicalKeyboardInputMethodService::class.java.name 
            }
            if (inputMethodInfo != null) {
                val enabledSubtypes = imm.getEnabledInputMethodSubtypeList(inputMethodInfo, true)
                Log.d(TAG, "Refreshed subtype caches, ${enabledSubtypes.size} enabled subtypes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing subtype caches", e)
        }
    }
    
    /**
     * Finds a subtype by locale.
     */
    private fun findSubtypeByLocale(locale: String): android.view.inputmethod.InputMethodSubtype? {
        return try {
            val imm = getSystemService(InputMethodManager::class.java)
            val inputMethodInfo = imm.getInputMethodList().firstOrNull { 
                it.id == packageName + "/" + PhysicalKeyboardInputMethodService::class.java.name 
            }
            if (inputMethodInfo != null) {
                val enabledSubtypes = imm.getEnabledInputMethodSubtypeList(inputMethodInfo, true)
                AdditionalSubtypeUtils.findSubtypeByLocale(enabledSubtypes.toTypedArray(), locale)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding subtype by locale: $locale", e)
            null
        }
    }
    
    /**
     * Finds a subtype by locale and keyboard layout set.
     */
    private fun findSubtypeByLocaleAndKeyboardLayoutSet(
        locale: String,
        layoutName: String
    ): android.view.inputmethod.InputMethodSubtype? {
        return try {
            val imm = getSystemService(InputMethodManager::class.java)
            val inputMethodInfo = imm.getInputMethodList().firstOrNull { 
                it.id == packageName + "/" + PhysicalKeyboardInputMethodService::class.java.name 
            }
            if (inputMethodInfo != null) {
                val enabledSubtypes = imm.getEnabledInputMethodSubtypeList(inputMethodInfo, true)
                AdditionalSubtypeUtils.findSubtypeByLocaleAndKeyboardLayoutSet(
                    enabledSubtypes.toTypedArray(),
                    locale,
                    layoutName
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding subtype by locale and layout: $locale:$layoutName", e)
            null
        }
    }
    
    /**
     * Gets the list of system-enabled locales.
     * Returns locales in format "en_US", "it_IT", etc.
     */
    private fun getSystemEnabledLocales(): Set<String> {
        val locales = mutableSetOf<String>()
        try {
            val config = resources.configuration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android N+ (API 24+)
                val localeList = config.locales
                for (i in 0 until localeList.size()) {
                    val locale = localeList[i]
                    val localeStr = formatLocaleStringForSystem(locale)
                    if (localeStr.isNotEmpty()) {
                        locales.add(localeStr)
                    }
                }
            } else {
                // Pre-Android N
                @Suppress("DEPRECATION")
                val locale = config.locale
                val localeStr = formatLocaleStringForSystem(locale)
                if (localeStr.isNotEmpty()) {
                    locales.add(localeStr)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting system locales", e)
        }
        return locales
    }
    
    /**
     * Formats a Locale object to "en_US" format.
     */
    private fun formatLocaleStringForSystem(locale: Locale): String {
        val language = locale.language
        val country = locale.country
        return if (country.isNotEmpty()) {
            "${language}_$country"
        } else {
            language
        }
    }
    
    /**
     * Gets the locale from an IME subtype.
     * Falls back to the current subtype, then Italian if no subtype is available.
     */
    private fun getLocaleFromSubtype(subtypeOverride: InputMethodSubtype? = null): Locale {
        val imm = getSystemService(InputMethodManager::class.java)
        val subtype = subtypeOverride ?: imm.currentInputMethodSubtype
        val localeString = subtype?.localeString() ?: "it-IT"
        return try {
            AdditionalSubtypeUtils.localeFromSubtypeString(localeString)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse locale from subtype: $localeString", e)
            Locale.ITALIAN
        }
    }

    private fun showAddSubstitutionDialog(word: String) {
        val replacement = word.trim()
        if (replacement.isBlank()) return
        val languageCode = getLocaleFromSubtype().language.ifBlank { "it" }
        try {
            val intent = Intent(this, AddSubstitutionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(AddSubstitutionActivity.EXTRA_REPLACEMENT, replacement)
                putExtra(AddSubstitutionActivity.EXTRA_LANGUAGE_CODE, languageCode)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open add-substitution dialog", e)
        }
    }

    private fun getAdditionalSuggestionLocalesForActiveInputStyle(): List<Locale> {
        val imm = getSystemService(InputMethodManager::class.java)
        val subtypeLocale = imm.currentInputMethodSubtype?.localeString()
            ?: getLocaleFromSubtype().toLanguageTag()
        val layout = SettingsManager.getKeyboardLayout(this)
        return SettingsManager.getAdditionalSuggestionLocalesForInputStyle(this, subtypeLocale, layout)
            .filterNot { tag -> tag.equals("x-pastiera", ignoreCase = true) }
            .mapNotNull { tag ->
                try {
                    AdditionalSubtypeUtils.localeFromSubtypeString(tag)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse additional suggestion locale: $tag", e)
                    null
                }
            }
    }
    
    /**
     * Called when the user switches IME subtypes (languages).
     * Reloads the dictionary for the new language and switches to the layout specified in the subtype or JSON mapping.
     */
    override fun onCurrentInputMethodSubtypeChanged(newSubtype: android.view.inputmethod.InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        
        if (::suggestionController.isInitialized) {
            val newLocale = getLocaleFromSubtype(newSubtype)
            suggestionController.updateLocale(newLocale)
            if (!inputContextState.shouldDisableSuggestions) {
                suggestionController.readInitialContext(currentInputConnection)
            }
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "IME subtype changed, updating locale to: ${newLocale.language}")
            }
        }
        
        val layoutToUse = AdditionalSubtypeUtils.resolveInputStyleLayout(assets, this, newSubtype)

        val currentLayout = SettingsManager.getKeyboardLayout(this)
        if (layoutToUse != currentLayout) {
            Log.d(TAG, "Switching layout for locale ${newSubtype.localeString()}: $layoutToUse (was: $currentLayout)")
            switchToLayout(layoutToUse, showToast = false)
        } else {
            switchToLayout(layoutToUse, showToast = false)
        }
    }
    
    override fun onWindowHidden() {
        super.onWindowHidden()
        SoftwareKeyboardAutoDetector.onInputWindowHidden()
        invalidateRenderedStatusSnapshot()
        if (::candidatesBarController.isInitialized) {
            candidatesBarController.resetSuggestionActionMode()
        }
        multiTapController.finalizeCycle()
        resetModifierStates(preserveNavMode = true)
        suggestionController.onContextReset()
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::textExpansionController.isInitialized) textExpansionController.clear()

        val systemLocalesSignature = newConfig.locales.toLanguageTags()
        if (systemLocalesSignature == lastSystemLocalesSignature) {
            Log.d(TAG, "Configuration changed without locale changes; keeping IME subtypes and editor session")
            return
        }
        lastSystemLocalesSignature = systemLocalesSignature

        // Only locale changes require subtype registration. Physical-keyboard connect/disconnect
        // also changes Configuration and must not restart the active editor session.
        Log.d(TAG, "System locales changed, re-registering IME subtypes")
        Handler(Looper.getMainLooper()).postDelayed({
            // First, remove system locales without dictionary that are no longer in system
            // (only when configuration changes, not when manually adding styles)
            AdditionalSubtypeUtils.removeSystemLocalesWithoutDictionary(this)
            // Then, auto-add new system locales without dictionary
            AdditionalSubtypeUtils.autoAddSystemLocalesWithoutDictionary(this)
            registerAdditionalSubtypes()
        }, 500) // Small delay to ensure system has processed locale changes
    }
    
    /**
     * Called when the cursor position or selection changes in the text field.
     */
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        val perfStart = ImePerfLogger.mark()
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        
        val state = inputContextState
        val cursorPositionChanged = (oldSelStart != newSelStart) || (oldSelEnd != newSelEnd)
        val collapsedSelection = newSelStart == newSelEnd
        editorHasActiveSelection = !collapsedSelection
        if (cursorPositionChanged && ::candidatesBarController.isInitialized) {
            candidatesBarController.resetSuggestionActionMode()
        }
        val forwardByOne = oldSelStart == oldSelEnd &&
            newSelEnd == newSelStart &&
            newSelStart == oldSelStart + 1
        val shouldSkipForCommit = skipNextSelectionUpdateAfterCommit && collapsedSelection && forwardByOne
        // Clear the flag so subsequent cursor moves are always processed.
        if (skipNextSelectionUpdateAfterCommit) {
            skipNextSelectionUpdateAfterCommit = false
        }

        if (
            symPage == 4 &&
            ::candidatesBarController.isInitialized &&
            candidatesBarController.isEmojiPickerSearchInputActive() &&
            !shouldSkipForCommit
        ) {
            // This callback comes from the app editor, not from the internal emoji search EditText.
            // Any external selection/cursor update means hardware typing should return to the app
            // until the user explicitly focuses the emoji search field again.
            disableEmojiSearchInputCapture()
        }
        
        if (cursorPositionChanged && collapsedSelection && !shouldSkipForCommit) {
            if (!forwardByOne) {
                DeferredPunctuationSpaceTracker.clear()
            }
            // Update suggestions on cursor movement (if suggestions enabled)
            if (!state.shouldDisableSuggestions) {
                suggestionController.onCursorMoved(currentInputConnection)
            }
            // Drop add-word candidate if cursor leaves its word
            suggestionController.clearPendingAddWordIfCursorOutside(currentInputConnection)
            
            // Always update status bar (it handles variations/suggestions internally based on flags).
            // Telegram can emit a selection update for every hardware key. Coalescing keeps
            // expensive InputConnection reads from stacking up behind fast typing.
            scheduleStatusBarTextUpdate()
        }
        if (cursorPositionChanged && ::textExpansionController.isInitialized) {
            if (collapsedSelection) textExpansionController.scheduleRefresh()
            else textExpansionController.clear()
        }
        if (SettingsManager.isSuggestionDebugLoggingEnabled(this)) {
            Log.d(
                TAG,
                "onUpdateSelection old=($oldSelStart,$oldSelEnd) new=($newSelStart,$newSelEnd) collapsed=$collapsedSelection forwardByOne=$forwardByOne skipForCommit=$shouldSkipForCommit"
            )
        }
        
        // Auto-cap reads editor context through InputConnection. For simple typing feedback,
        // debounce it so remote editors like Telegram don't pay that cost for every character.
        if (cursorPositionChanged && collapsedSelection && forwardByOne) {
            scheduleAutoCapitalizeOnSelectionChange(oldSelStart, oldSelEnd, newSelStart, newSelEnd)
        } else {
            pendingSelectionAutoCapCheck?.let { uiHandler.removeCallbacks(it) }
            pendingSelectionAutoCapCheck = null
            checkAutoCapitalizeOnSelectionChange(oldSelStart, oldSelEnd, newSelStart, newSelEnd)
        }
        ImePerfLogger.logDuration(
            label = "onUpdateSelection",
            startNanos = perfStart,
            thresholdMs = 16L,
            details = "old=($oldSelStart,$oldSelEnd) new=($newSelStart,$newSelEnd) forwardByOne=$forwardByOne skip=$shouldSkipForCommit pkg=$currentPackageName"
        )
    }

    private fun remapHardwareEvent(keyCode: Int, event: KeyEvent?): ClicksPowerButtonEventMapper.Result {
        val remapped = DeviceSpecific.remapHardwareKeyEvent(
            keyCode,
            event,
            physicalKeyboardProfileOverride
        )
        val isClicksPowerKeyboard = !dispatchingClicksAccessibilityKeyEvent &&
            (event
                ?.takeIf { it.deviceId >= 0 }
                ?.let { inputEvent ->
                    inputEvent.deviceId in connectedClicksInputDeviceIds ||
                        InputDevice.getDevice(inputEvent.deviceId)
                            ?.let(DeviceSpecific::isClicksPowerKeyboard) == true
                } == true)
        return clicksPowerButtonEventMapper.map(
            keyCode = remapped.keyCode,
            event = remapped.event,
            isClicksPowerKeyboard = isClicksPowerKeyboard,
            clicksButtonMode = SettingsManager.getClicksButtonMode(this),
            metaButtonMode = SettingsManager.getClicksMetaButtonMode(this),
            altButtonMode = SettingsManager.getClicksAltButtonMode(this),
            microphoneButtonMode = SettingsManager.getClicksMicrophoneButtonMode(this)
        )
    }

    override fun dispatchClicksAccessibilityKeyEvent(event: KeyEvent): Boolean {
        if (currentInputConnection == null || !inputContextState.isEditable) return false

        val dispatch = {
            dispatchingClicksAccessibilityKeyEvent = true
            try {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> onKeyDown(event.keyCode, event)
                    KeyEvent.ACTION_UP -> onKeyUp(event.keyCode, event)
                }
            } finally {
                dispatchingClicksAccessibilityKeyEvent = false
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dispatch()
        } else {
            uiHandler.post(dispatch)
        }
        return true
    }

    override fun dispatchClicksDirectAction(action: ClicksButtonDirectAction): Boolean {
        if (action != ClicksButtonDirectAction.TOGGLE_EMOJI_PICKER ||
            currentInputConnection == null || !inputContextState.isEditable
        ) {
            return false
        }
        val dispatch = { toggleEmojiPicker() }
        if (Looper.myLooper() == Looper.getMainLooper()) dispatch() else uiHandler.post(dispatch)
        return true
    }

    private fun toggleEmojiPicker() {
        ensureInputViewCreated()
        symLayoutController.openEmojiPickerPage()
        updateStatusBarText()
    }

    private data class AccidentalKeyInput(
        val resolution: PhysicalKeyResolver.Resolution,
        val configuration: AccidentalKeyPressFilter.Configuration
    )

    private fun accidentalKeyInput(keyCode: Int, event: KeyEvent?): AccidentalKeyInput {
        val device = event
            ?.takeIf { it.deviceId >= 0 }
            ?.let { InputDevice.getDevice(it.deviceId) }
        val isPhysicalKeyboard = device != null &&
            !device.isVirtual &&
            device.sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
        val isClicksPowerKeyboard = device?.let(DeviceSpecific::isClicksPowerKeyboard) == true
        val resolved = physicalKeyResolver.resolve(
                keyCode = keyCode,
                event = event,
                profile = ClicksPowerKeyboardLayout.takeIf { isClicksPowerKeyboard },
                clicksState = if (isClicksPowerKeyboard) {
                    ClicksPowerKeyboardController.currentState().keyboard
                } else {
                    null
                }
            )
        val configuredButtonMode = if (isClicksPowerKeyboard) {
            when (keyCode) {
                KeyEvent.KEYCODE_TAB -> SettingsManager.getClicksButtonMode(this)
                KeyEvent.KEYCODE_META_LEFT -> SettingsManager.getClicksMetaButtonMode(this)
                KeyEvent.KEYCODE_F12 -> SettingsManager.getClicksAltButtonMode(this)
                KeyEvent.KEYCODE_F11 -> SettingsManager.getClicksMicrophoneButtonMode(this)
                else -> null
            }
        } else {
            null
        }
        val isConfiguredModifier = when (configuredButtonMode) {
            SettingsManager.ClicksPowerButtonMode.ALT,
            SettingsManager.ClicksPowerButtonMode.SYM,
            SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER,
            SettingsManager.ClicksPowerButtonMode.OPEN_PASTIERA,
            SettingsManager.ClicksPowerButtonMode.TOGGLE_KEYBOARD_MODE,
            SettingsManager.ClicksPowerButtonMode.TOGGLE_EMOJI_PICKER -> true
            SettingsManager.ClicksPowerButtonMode.TAB -> false
            SettingsManager.ClicksPowerButtonMode.NATIVE,
            null -> resolved.isModifier
        }
        return AccidentalKeyInput(
            resolution = resolved.copy(isModifier = isConfiguredModifier),
            configuration = AccidentalKeyPressPolicy.configuration(
                isPhysicalKeyboard = isPhysicalKeyboard,
                isClicksPowerKeyboard = isClicksPowerKeyboard,
                globalOverlapEnabled = SettingsManager.getOverlappingKeysEnabled(this),
                clicksOverlapMode = SettingsManager.getClicksOverlappingKeysMode(this),
                clicksNumberRowMode = SettingsManager.getClicksNumberRowInputMode(this),
                clicksNumberRowRepeatEnabled = SettingsManager.isClicksNumberRowRepeatEnabled(this),
                longPressThresholdMs = SettingsManager.getLongPressThreshold(this)
            )
        )
    }

    private fun replayProtectedNumberKey(
        keyCode: Int,
        replay: AccidentalKeyPressFilter.KeyUpResult.ReplayTap
    ) {
        replayingProtectedNumberKey = true
        try {
            val downHandled = onKeyDown(keyCode, replay.downEvent)
            if (downHandled) {
                onKeyUp(keyCode, replay.upEvent)
            } else {
                currentInputConnection?.let { inputConnection ->
                    inputConnection.sendKeyEvent(replay.downEvent)
                    inputConnection.sendKeyEvent(replay.upEvent)
                }
            }
        } finally {
            replayingProtectedNumberKey = false
        }
    }

    override fun onKeyLongPress(keyCode_: Int, event_: KeyEvent?): Boolean {
        if (!replayingProtectedNumberKey) {
            val accidentalInput = accidentalKeyInput(keyCode_, event_)
            accidentalKeyPressFilter.shouldConsumeKeyDown(
                keyCode = keyCode_,
                event = event_,
                resolution = accidentalInput.resolution,
                configuration = accidentalInput.configuration
            )?.let { return true }
        }
        val remapped = remapHardwareEvent(keyCode_, event_)
        if (remapped.consume) return true
        val keyCode = remapped.keyCode
        val event = remapped.event
        // Handle long press even when the keyboard is hidden but we still have a valid InputConnection.
        val inputConnection = currentInputConnection
        if (inputConnection == null) {
            return super.onKeyLongPress(keyCode, event)
        }
        
        // If the keyboard is hidden but we have an InputConnection, reactivate it
        if (!isInputViewActive) {
            isInputViewActive = true
            if (!isInputViewShown) {
                ensureInputViewCreated()
            }
        }
        
        // Intercept long presses BEFORE Android handles them
        if (altSymManager.hasAltMapping(keyCode)) {
            // Consumiamo l'evento per evitare il popup di Android
            return true
        }
        
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyDown(keyCode_: Int, event_: KeyEvent?): Boolean {
        val perfStart = ImePerfLogger.mark()
        try {
        if (!replayingProtectedNumberKey) {
            val accidentalInput = accidentalKeyInput(keyCode_, event_)
            accidentalKeyPressFilter.shouldConsumeKeyDown(
                keyCode = keyCode_,
                event = event_,
                resolution = accidentalInput.resolution,
                configuration = accidentalInput.configuration
            )?.let { suppressed ->
                notifyDebugKeyEvent(
                    keyCode = keyCode_,
                    event = event_,
                    action = "KEY_DOWN_SUPPRESSED",
                    origin = "accidental_keys",
                    outputKeyCodeName = suppressed.debugOutput()
                )
                return true
            }
        }
        val remapped = remapHardwareEvent(keyCode_, event_)
        if (remapped.consume) {
            remapped.directAction?.let { ClicksButtonDirectActionExecutor.execute(this, it) }
            return true
        }
        val keyCode = remapped.keyCode
        val event = remapped.event
        bounceKeyFilter.shouldConsumeKeyDown(this, keyCode, event)?.let { suppressed ->
            notifyDebugKeyEvent(
                keyCode = keyCode,
                event = event,
                action = "KEY_DOWN_SUPPRESSED",
                origin = "bounce_keys",
                outputKeyCodeName = suppressed.debugOutput()
            )
            return true
        }

        // Check if we have an editable field at the very start
        val info = currentInputEditorInfo
        val initialInputConnection = currentInputConnection
        val inputType = info?.inputType ?: EditorInfo.TYPE_NULL
        val hasEditableField = initialInputConnection != null && inputType != EditorInfo.TYPE_NULL
        if (hasEditableField && !isInputViewActive) {
            isInputViewActive = true
        }
        if (hasEditableField && ::candidatesBarController.isInitialized) {
            candidatesBarController.resetSuggestionActionMode()
        }

        if (shouldPlayTypingSound(hasEditableField, keyCode, event)) {
            typingSoundPlayer.play(keyCode)
        }

        val emojiSearchCtrlActive = event?.isCtrlPressed == true ||
            ctrlPressed ||
            ctrlPhysicallyPressed ||
            ctrlLatchActive ||
            ctrlOneShot ||
            ctrlLatchFromNavMode
        val emojiSearchCandidateActive =
            hasEditableField &&
                symPage == 4 &&
                keyCode != KeyEvent.KEYCODE_BACK &&
                keyCode != KEYCODE_SYM &&
                !isPureModifierKey(keyCode) &&
                ::candidatesBarController.isInitialized &&
                candidatesBarController.isEmojiPickerSearchInputActive()
        if (emojiSearchCandidateActive) {
            ensureEmojiSearchCursorAnchorMonitoring(initialInputConnection)
            if (shouldReturnEmojiSearchFocusToApp(initialInputConnection)) {
                disableEmojiSearchInputCapture()
            }
        }
        // Let the picker handle text-editing shortcuts before the generic Ctrl router can
        // touch the app editor selection.
        if (
            emojiSearchCandidateActive &&
            candidatesBarController.isEmojiPickerSearchInputActive() &&
            candidatesBarController.handleEmojiPickerSearchKeyDown(
                event,
                emojiSearchCtrlActive,
                resolveTypedText = { typedEvent ->
                    getCharacterFromLayout(
                        typedEvent.keyCode,
                        typedEvent,
                        isShiftModifierActive(typedEvent)
                    )?.toString()
                }
            )
        ) {
            updateEmojiSearchExternalSelectionSnapshot(initialInputConnection)
            ensureEmojiSearchCursorAnchorMonitoring(initialInputConnection)
            return true
        }
        val emojiSearchInputConnection =
            if (emojiSearchCandidateActive && candidatesBarController.isEmojiPickerSearchInputActive()) {
                candidatesBarController.createEmojiPickerSearchInputConnection()
            } else {
                null
            }
        if (emojiSearchInputConnection != null && emojiSearchCtrlActive) {
            val handled = inputEventRouter.handleCtrlModifiedKey(
                keyCode = keyCode,
                event = event,
                inputConnection = emojiSearchInputConnection,
                ctrlKeyMap = ctrlKeyMap,
                ctrlLatchFromNavMode = ctrlLatchFromNavMode,
                ctrlOneShot = ctrlOneShot,
                ctrlPhysicallyPressed = ctrlPhysicallyPressed || ctrlPressed,
                clearCtrlOneShot = { ctrlOneShot = false },
                updateStatusBar = { updateStatusBarText() },
                callSuper = { false },
                toggleMinimalUi = { keyboardVisibilityController.togglePastierinaMode() }
            )
            if (handled) {
                updateEmojiSearchExternalSelectionSnapshot(initialInputConnection)
                ensureEmojiSearchCursorAnchorMonitoring(initialInputConnection)
                return true
            }
        }

        val expansionShortcutModifierActive = event?.isCtrlPressed == true ||
            event?.isAltPressed == true || event?.isMetaPressed == true || event?.isShiftPressed == true ||
            ctrlPressed || ctrlPhysicallyPressed || ctrlLatchActive || ctrlOneShot || ctrlLatchFromNavMode ||
            altPressed || altPhysicallyPressed || altLatchActive || altOneShot ||
            shiftPressed || modifierStateController.shiftPhysicallyPressed || shiftLayerLatched || shiftOneShot
        if (hasEditableField && !expansionShortcutModifierActive &&
            ::textExpansionController.isInitialized &&
            textExpansionController.handleKeyDown(keyCode)
        ) {
            return true
        }

        if (hasEditableField && keyCode == KEYCODE_SYM && event?.repeatCount == 0) {
            symTogglePendingOnKeyUp = true
            symChordUsedSinceKeyDown = false
        }

        // Minimal Phone dedicated keys (skip when Alt is active in any form so alt mappings can fire)
        // Gate this behavior to Minimal Phone devices only.
        val altActiveForDedicatedKeys = event?.isAltPressed == true || altLatchActive || altOneShot
        if (
            hasEditableField &&
            isMinimalPhoneHardwareActive() &&
            keyCode == KEYCODE_EM &&
            event?.repeatCount == 0 &&
            !altActiveForDedicatedKeys
        ) {
            if (symPage == 4) {
                symLayoutController.closeSymPage()
                updateStatusBarText()
            } else {
                ensureInputViewCreated()
                symLayoutController.openEmojiPickerPage()
                updateStatusBarText()
            }
            return true
        }
        if (
            hasEditableField &&
            isMinimalPhoneHardwareActive() &&
            keyCode == KEYCODE_MIC &&
            event?.repeatCount == 0 &&
            !altActiveForDedicatedKeys
        ) {
            startSpeechRecognition()
            return true
        }

        if (
            hasEditableField &&
            (symTogglePendingOnKeyUp || event?.isSymPressed == true) &&
            SettingsManager.getPowerShortcutsEnabled(this) &&
            SettingsManager.getQuickLauncherTextFieldShortcuts(this) &&
            SettingsManager.getLauncherShortcut(this, keyCode) != null &&
            event?.repeatCount == 0
        ) {
            symChordUsedSinceKeyDown = true
            if (launcherShortcutController.handleLauncherShortcut(keyCode)) {
                return true
            }
        }

        if (
            hasEditableField &&
            event?.repeatCount == 0 &&
            SettingsManager.getQuickLauncherAltShortcutsOutsideTextFields(this) &&
            SettingsManager.getQuickLauncherAltSpaceInTextFields(this) &&
            SettingsManager.getLauncherShortcut(this, keyCode) != null &&
            (event.isAltPressed || altPressed || altPhysicallyPressed || altLatchActive || altOneShot)
        ) {
            modifierStateController.clearAltState()
            updateStatusBarText()
            if (launcherShortcutController.handleLauncherShortcut(keyCode)) {
                return true
            }
        }

        if (
            hasEditableField &&
            symTogglePendingOnKeyUp &&
            keyCode != KEYCODE_SYM &&
            event?.repeatCount == 0 &&
            !isPureModifierKey(keyCode)
        ) {
            symChordUsedSinceKeyDown = true
            val symChar = symLayoutController.resolveChordSymbol(
                keyCode = keyCode,
                shiftPressed = event.isShiftPressed || shiftOneShot || capsLockEnabled
            )
            if (!symChar.isNullOrEmpty()) {
                val inputConnection = currentInputConnection
                if (!handleBoundaryTextBeforeCommit(symChar, inputConnection)) {
                    inputConnection?.commitText(symChar, 1)
                }
                updateStatusBarText()
                return true
            }
        }

        // If any SYM page or clipboard overlay is open, close on BACK and consume
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (candidatesBarController.handleBackPressed()) {
                return true
            }
            if (symLayoutController.isSymActive()) {
                if (symLayoutController.closeSymPage()) {
                    updateStatusBarText()
                    return true
                }
            }
        }

        val navModeBefore = navModeController.isNavModeActive()
        val ctrlActiveBeforePrelude = event?.isCtrlPressed == true ||
            ctrlPressed ||
            ctrlPhysicallyPressed ||
            ctrlLatchActive ||
            ctrlOneShot ||
            ctrlLatchFromNavMode

        val isModifierKey = keyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
            keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT ||
            keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
            keyCode == KeyEvent.KEYCODE_CTRL_RIGHT ||
            keyCode == KeyEvent.KEYCODE_ALT_LEFT ||
            keyCode == KeyEvent.KEYCODE_ALT_RIGHT

        if (event?.repeatCount == 0 && isModifierKey) {
            // Pressing a latched key again cancels the visual latch and restores logical state.
            if ((keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) && shiftLayerLatched) {
                shiftLayerLatched = false
                lastShiftTapUpTime = 0L
                // Tapping SHIFT while the visual Shift layer is latched should fully disable Shift.
                // Restoring the pre-hold snapshot here can resurrect stale one-shot/caps state.
                modifierStateController.clearShiftState(resetPressedState = true)
                modifierStateBeforeHold = null
                updateStatusBarText()
                return true
            }
            if ((keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) && altLayerLatched) {
                altLayerLatched = false
                lastAltTapUpTime = 0L
                // Tapping ALT while the visual Device SYM layer is latched should fully disable Alt.
                // Restoring the pre-hold snapshot here can resurrect stale one-shot/latch state.
                modifierStateController.clearAltState(resetPressedState = true)
                modifierStateBeforeHold = null
                updateStatusBarText()
                return true
            }

            modifierStateBeforeHold = modifierStateController.captureLogicalState()
            variationInteractedDuringHold = false
            otherKeyInteractedDuringHold = false
            modifierDownTimes[keyCode] = event.eventTime
        } else if (!isModifierKey && event?.repeatCount == 0) {
            otherKeyInteractedDuringHold = true
            lastShiftTapUpTime = 0L
            lastAltTapUpTime = 0L
        }

        multiTapController.resetForNewKey(keyCode)
        if (!isModifierKey) {
            modifierStateController.registerNonModifierKey()
        }
        
        // If NO editable field is active, handle ONLY nav mode
        if (!hasEditableField) {
            val powerShortcutsEnabled = SettingsManager.getPowerShortcutsEnabled(this)
            return inputEventRouter.handleKeyDownWithNoEditableField(
                keyCode = keyCode,
                event = event,
                ctrlKeyMap = ctrlKeyMap,
                callbacks = InputEventRouter.NoEditableFieldCallbacks(
                    isShortcutKey = { code -> isShortcutKey(code) },
                    isLauncherPackage = { pkg -> launcherShortcutController.isLauncher(pkg) },
                    handleLauncherShortcut = { key -> launcherShortcutController.handleLauncherShortcut(key) },
                    handlePowerShortcut = { key -> launcherShortcutController.handlePowerShortcut(key) },
                    togglePowerShortcutMode = { message, isNavModeActive -> 
                        launcherShortcutController.togglePowerShortcutMode(
                            showToast = { showPowerShortcutToast(it) },
                            isNavModeActive = isNavModeActive
                        )
                    },
                    callSuper = { super.onKeyDown(keyCode, event) },
                    currentInputConnection = { currentInputConnection }
                ),
                ctrlLatchActive = ctrlLatchActive,
                editorInfo = info,
                currentPackageName = currentPackageName,
                powerShortcutsEnabled = powerShortcutsEnabled
            )
        }
        
        val routingResult = inputEventRouter.handleEditableFieldKeyDownPrelude(
            keyCode = keyCode,
            params = InputEventRouter.EditableFieldKeyDownParams(
                ctrlLatchFromNavMode = ctrlLatchFromNavMode,
                ctrlLatchActive = ctrlLatchActive,
                isInputViewActive = isInputViewActive,
                isInputViewShown = isInputViewShown,
                hasInputConnection = hasEditableField
            ),
            callbacks = InputEventRouter.EditableFieldKeyDownCallbacks(
                exitNavMode = { navModeController.exitNavMode() },
                ensureInputViewCreated = { keyboardVisibilityController.ensureInputViewCreated() },
                callSuper = { super.onKeyDown(keyCode, event) }
            )
        )
        when (routingResult) {
            InputEventRouter.EditableFieldRoutingResult.Consume -> return true
            InputEventRouter.EditableFieldRoutingResult.CallSuper -> return super.onKeyDown(keyCode, event)
            InputEventRouter.EditableFieldRoutingResult.Continue -> {}
        }
        
        // Handle Alt+Shift for subtype cycling
        if (
            hasEditableField &&
            event != null &&
            event.repeatCount == 0 &&
            SettingsManager.isAltShiftLayoutSwitchEnabled(this) &&
            (((keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) &&
                (event.isAltPressed || altPhysicallyPressed)) ||
                ((keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) &&
                    (event.isShiftPressed || shiftPhysicallyPressed)))
        ) {
            modifierStateController.clearAltState(resetPressedState = true)
            modifierStateController.clearShiftState(resetPressedState = true)

            val showToast = SettingsManager.isToastOnLayoutSwitchEnabled(this)
            SubtypeCycler.cycleToNextSubtype(
                context = this,
                imeServiceClass = PhysicalKeyboardInputMethodService::class.java,
                assets = assets,
                showToast = showToast
            )

            updateStatusBarText()
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_ENTER && event?.repeatCount == 0) {
            // Recover if a previous chord lost its key-up while the input context changed.
            consumeAltEnterUntilKeyUp = false
        }

        // Keep repeats from a consumed Alt+Enter chord away from the editor.
        if (keyCode == KeyEvent.KEYCODE_ENTER && consumeAltEnterUntilKeyUp) {
            return true
        }

        // Handle Alt+Enter for subtype cycling
        if (
            hasEditableField &&
            event != null &&
            event.repeatCount == 0 &&
            SettingsManager.isAltEnterLayoutSwitchEnabled(this) &&
            (keyCode == KeyEvent.KEYCODE_ENTER &&
                    (event.isAltPressed || altPhysicallyPressed))
        ) {
            consumeAltEnterUntilKeyUp = true
            modifierStateController.clearAltState(resetPressedState = true)

            val showToast = SettingsManager.isToastOnLayoutSwitchEnabled(this)
            SubtypeCycler.cycleToNextSubtype(
                context = this,
                imeServiceClass = PhysicalKeyboardInputMethodService::class.java,
                assets = assets,
                showToast = showToast
            )

            updateStatusBarText()
            return true
        }

        // Handle Ctrl+Space for subtype cycling
        if (
            hasEditableField &&
            keyCode == KeyEvent.KEYCODE_SPACE &&
            SettingsManager.isCtrlSpaceLayoutSwitchEnabled(this) &&
            (event?.isCtrlPressed == true || ctrlPressed || ctrlLatchActive || ctrlOneShot)
        ) {
            var shouldUpdateStatusBar = false

            // Clear Alt state if active so we don't leave Alt latched.
            val hadAlt = altLatchActive || altOneShot || altPressed
            if (hadAlt) {
                modifierStateController.clearAltState(resetPressedState = true)
                shouldUpdateStatusBar = true
            }

            // Always reset Ctrl state after Ctrl+Space to avoid leaving it active.
            val hadCtrl = ctrlLatchActive ||
                ctrlOneShot ||
                ctrlPressed ||
                ctrlPhysicallyPressed ||
                ctrlLatchFromNavMode
            if (hadCtrl) {
                val navModeLatched = ctrlLatchFromNavMode
                val keepLockedCtrl = ctrlLatchActive &&
                    !ctrlLatchFromNavMode &&
                    SettingsManager.getCtrlTapLatches(this) &&
                    SettingsManager.getCtrlLatchStaysOnSpace(this)
                if (keepLockedCtrl) {
                    modifierStateController.ctrlOneShot = false
                    modifierStateController.ctrlPressed = false
                    modifierStateController.ctrlPhysicallyPressed = false
                    modifierStateController.ctrlLatchFromNavMode = false
                } else {
                    modifierStateController.clearCtrlState(resetPressedState = true)
                }
                if (navModeLatched && !keepLockedCtrl) {
                    navModeController.cancelNotification()
                    navModeController.refreshNavModeState()
                }
                shouldUpdateStatusBar = true
            }

            // Cycle to next subtype
            val showToast = SettingsManager.isToastOnLayoutSwitchEnabled(this)
            if (SubtypeCycler.cycleToNextSubtype(this, PhysicalKeyboardInputMethodService::class.java, assets, showToast = showToast)) {
                shouldUpdateStatusBar = true
            }

            if (shouldUpdateStatusBar) {
                updateStatusBarText()
            }
            return true
        }

        val ic = currentInputConnection
        val state = inputContextState
        val isAutoCorrectEnabled = SettingsManager.getAutoCorrectEnabled(this) && !state.shouldDisableAutoCorrect
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DEL) {
            DeferredPunctuationSpaceTracker.clear()
        }

        clearAltOnBoundaryIfNeeded(keyCode) { updateStatusBarText() }
        if (keyCode == KeyEvent.KEYCODE_ENTER && modifierStateController.consumeShiftOneShot()) {
            updateStatusBarText()
        }

        if (handleEnterAsEditorAction(
                keyCode,
                info,
                ic,
                event,
                isAutoCorrectEnabled,
                ctrlActiveBeforePrelude
            )
        ) {
            return true
        }
        
        val altActiveNow = event?.isAltPressed == true || altLatchActive || altOneShot
        val debugUnicodeOverride = resolveAltMappedUnicodeForDebug(
            keyCode = keyCode,
            altActive = altActiveNow
        )

        // Continue with normal IME logic
        notifyDebugKeyEvent(
            keyCode = keyCode,
            event = event,
            action = "KEY_DOWN",
            origin = "ime_service",
            unicodeCharOverride = debugUnicodeOverride
        )
        if (!isInputViewShown && isInputViewActive) {
            ensureInputViewCreated()
        }
        val ctrlActiveNow = event?.isCtrlPressed == true ||
            ctrlPressed ||
            ctrlPhysicallyPressed ||
            ctrlLatchActive ||
            ctrlOneShot ||
            ctrlLatchFromNavMode
        when {
            keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DEL -> {
                DeferredPunctuationSpaceTracker.clear()
            }
            !altActiveNow && !ctrlActiveNow && ic != null -> {
                val typedText = when {
                    keyCode == KeyEvent.KEYCODE_SPACE -> " "
                    event?.unicodeChar?.takeIf { it != 0 } != null -> event.unicodeChar.toChar().toString()
                    else -> ""
                }
                val insertedDeferredSpace =
                    DeferredPunctuationSpaceTracker.prepareForTextCommit(this, ic, typedText)
                if (insertedDeferredSpace) {
                    suggestionController.onContextReset()
                    if (typedText.firstOrNull()?.isLetter() == true) {
                        AutoCapitalizeHelper.enableAfterPunctuation(
                            context = this,
                            inputConnection = ic,
                            shouldDisableAutoCapitalize = shouldDisableAutoCapitalize,
                            onEnableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
                            disableShift = { modifierStateController.consumeShiftOneShot() },
                            onUpdateStatusBar = { updateStatusBarText() }
                        )
                    }
                }
            }
        }
        if (
            inputEventRouter.handleConfiguredForwardDeleteAlternatives(
                context = this,
                keyCode = keyCode,
                event = event,
                inputConnection = ic,
                altActive = altActiveNow
            )
        ) {
            return true
        }
        if (!altActiveNow) {
            if (
                inputEventRouter.handleTextInputPipeline(
                    context = this,
                    keyCode = keyCode,
                    event = event,
                    inputConnection = ic,
                    shouldDisableSuggestions = state.shouldDisableSuggestions,
                    shouldDisableAutoCorrect = state.shouldDisableAutoCorrect,
                    shouldDisableAutoCapitalize = shouldDisableAutoCapitalize,
                    shouldDisableDoubleSpaceToPeriod = state.shouldDisableDoubleSpaceToPeriod,
                    isAutoCorrectEnabled = isAutoCorrectEnabled,
                    textInputController = textInputController,
                    autoCorrectionManager = autoCorrectionManager,
                    inputContextState = state,
                    enableShiftOneShot = { requestAutoCapShiftOneShot() },
                    editorInfo = info
                ) { updateStatusBarText() }
            ) {
                return true
            }
        }

        if (!altActiveNow && !ctrlActiveNow && handleVietnameseTelexKey(keyCode, event, ic)) {
            return true
        }
        
        val routingDecision = inputEventRouter.routeEditableFieldKeyDown(
            keyCode = keyCode,
            event = event,
            params = InputEventRouter.EditableFieldKeyDownHandlingParams(
                inputConnection = ic,
                isNumericField = isNumericField,
                isInputViewActive = isInputViewActive,
                shiftPressed = shiftPressed,
                shiftLayerLatched = shiftLayerLatched,
                ctrlPressed = ctrlPressed,
                ctrlPhysicallyPressed = ctrlPhysicallyPressed,
                altPressed = altPressed,
                ctrlLatchActive = ctrlLatchActive,
                altLatchActive = altLatchActive,
                ctrlLatchFromNavMode = ctrlLatchFromNavMode,
                ctrlKeyMap = ctrlKeyMap,
                ctrlOneShot = ctrlOneShot,
                altOneShot = altOneShot,
                clearAltOnSpaceEnabled = clearAltOnSpaceEnabled,
                shiftOneShot = shiftOneShot,
                capsLockEnabled = capsLockEnabled,
                cursorUpdateDelayMs = CURSOR_UPDATE_DELAY,
                altMappingsOverride = if (dispatchingSoftwareKeyboardKey) {
                    KeyMappingLoader.loadVirtualAltKeyMappings(assets, this)
                } else {
                    null
                },
                shouldDisableSmartFeatures = shouldDisableSmartFeatures
            ),
            controllers = InputEventRouter.EditableFieldKeyDownControllers(
                modifierStateController = modifierStateController,
                symLayoutController = symLayoutController,
                altSymManager = altSymManager,
                variationStateController = variationStateController,
                textInputController = textInputController
            ),
            callbacks = InputEventRouter.EditableFieldKeyDownHandlingCallbacks(
                updateStatusBar = { updateStatusBarText() },
                refreshStatusBar = { refreshStatusBar() },
                disableShiftOneShot = {
                    modifierStateController.consumeShiftOneShot()
                },
                clearAltOneShot = { altOneShot = false },
                clearCtrlOneShot = { ctrlOneShot = false },
                getCharacterFromLayout = { code, keyEvent, isShiftPressed ->
                    getCharacterFromLayout(code, keyEvent, isShiftPressed)
                },
                isAlphabeticKey = { code -> isAlphabeticKey(code) },
                callSuper = { super.onKeyDown(keyCode, event) },
                callSuperWithKey = { defaultKeyCode, defaultEvent ->
                    super.onKeyDown(defaultKeyCode, defaultEvent)
                },
                startSpeechRecognition = { startSpeechRecognition() },
                getMapping = { code -> LayoutMappingRepository.getMapping(code) },
                handleMultiTapCommit = { code, mapping, uppercase, inputConnection, allowLongPress ->
                    handleMultiTapCommit(code, mapping, uppercase, inputConnection, allowLongPress)
                },
                isLongPressSuppressed = { code ->
                    multiTapController.isLongPressSuppressed(code)
                },
                toggleMinimalUi = { keyboardVisibilityController.togglePastierinaMode() },
                handleBoundaryText = { text, inputConnection ->
                    handleBoundaryTextBeforeCommit(text, inputConnection)
                },
                onShiftOneShotToggledOff = { suppressAutoCapRenderingAtCursorIfNeeded() }
            )
        )

        val navModeAfter = navModeController.isNavModeActive()
        if (navModeBefore != navModeAfter) {
            suggestionController.onNavModeToggle()
        }

        return when (routingDecision) {
            InputEventRouter.EditableFieldRoutingResult.Consume -> true
            InputEventRouter.EditableFieldRoutingResult.CallSuper -> super.onKeyDown(keyCode, event)
            InputEventRouter.EditableFieldRoutingResult.Continue -> super.onKeyDown(keyCode, event)
        }
        } finally {
            ImePerfLogger.logDuration(
                label = "onKeyDown",
                startNanos = perfStart,
                thresholdMs = 16L,
                details = "key=${KeyEvent.keyCodeToString(keyCode_)} repeat=${event_?.repeatCount ?: -1} pkg=$currentPackageName"
            )
        }
    }

    private fun shouldPlayTypingSound(hasEditableField: Boolean, keyCode: Int, event: KeyEvent?): Boolean {
        if (!hasEditableField || event?.repeatCount != 0) {
            return false
        }
        return keyCode != KeyEvent.KEYCODE_BACK
    }

    override fun onKeyUp(keyCode_: Int, event_: KeyEvent?): Boolean {
        if (!replayingProtectedNumberKey) {
            when (val result = accidentalKeyPressFilter.onKeyUp(keyCode_, event_)) {
                is AccidentalKeyPressFilter.KeyUpResult.Suppressed -> {
                    notifyDebugKeyEvent(
                        keyCode = keyCode_,
                        event = event_,
                        action = "KEY_UP_SUPPRESSED",
                        origin = "accidental_keys",
                        outputKeyCodeName = result.event.debugOutput()
                    )
                    return true
                }
                is AccidentalKeyPressFilter.KeyUpResult.ReplayTap -> {
                    replayProtectedNumberKey(keyCode_, result)
                    return true
                }
                null -> Unit
            }
        }
        val remapped = remapHardwareEvent(keyCode_, event_)
        if (remapped.consume) return true
        val keyCode = remapped.keyCode
        val event = remapped.event
        if (keyCode == KeyEvent.KEYCODE_ENTER && consumeAltEnterUntilKeyUp) {
            consumeAltEnterUntilKeyUp = false
            return true
        }
        bounceKeyFilter.shouldConsumeKeyUp(keyCode, event)?.let { suppressed ->
            notifyDebugKeyEvent(
                keyCode = keyCode,
                event = event,
                action = "KEY_UP_SUPPRESSED",
                origin = "bounce_keys",
                outputKeyCodeName = suppressed.debugOutput()
            )
            return true
        }

        // Check if we have an editable field at the start (same logic as onKeyDown)
        val info = currentInputEditorInfo
        val ic = currentInputConnection
        val inputType = info?.inputType ?: EditorInfo.TYPE_NULL
        val hasEditableField = ic != null && inputType != EditorInfo.TYPE_NULL

        if (
            hasEditableField &&
            symPage == 4 &&
            keyCode != KeyEvent.KEYCODE_BACK &&
            keyCode != KEYCODE_SYM &&
            !isPureModifierKey(keyCode) &&
            ::candidatesBarController.isInitialized &&
            candidatesBarController.isEmojiPickerSearchInputActive() &&
            candidatesBarController.shouldConsumeEmojiPickerSearchKeyUp(
                event,
                event?.isCtrlPressed == true ||
                    ctrlPressed ||
                    ctrlPhysicallyPressed ||
                    ctrlLatchActive ||
                    ctrlOneShot ||
                    ctrlLatchFromNavMode
            )
        ) {
            return true
        }
        
        // If NO editable field is active, handle ONLY nav mode Ctrl release
        if (!hasEditableField) {
            if (keyCode == KEYCODE_SYM) {
                symTogglePendingOnKeyUp = false
                symChordUsedSinceKeyDown = false
            }
            return inputEventRouter.handleKeyUpWithNoEditableField(
                keyCode = keyCode,
                event = event,
                ctrlKeyMap = ctrlKeyMap,
                callbacks = InputEventRouter.NoEditableFieldCallbacks(
                    isShortcutKey = { code -> isShortcutKey(code) },
                    isLauncherPackage = { pkg -> launcherShortcutController.isLauncher(pkg) },
                    handleLauncherShortcut = { key -> launcherShortcutController.handleLauncherShortcut(key) },
                    handlePowerShortcut = { key -> launcherShortcutController.handlePowerShortcut(key) },
                    togglePowerShortcutMode = { message, isNavModeActive -> 
                        launcherShortcutController.togglePowerShortcutMode(
                            showToast = { showPowerShortcutToast(it) },
                            isNavModeActive = isNavModeActive
                        )
                    },
                    callSuper = { super.onKeyUp(keyCode, event) },
                    currentInputConnection = { currentInputConnection }
                )
            )
        }
        
        // Continue with normal IME logic for text fields
        val inputConnection = currentInputConnection ?: return super.onKeyUp(keyCode, event)
        
        // Always notify the tracker (even when the event is consumed)
        notifyDebugKeyEvent(keyCode, event, "KEY_UP", origin = "ime_service")
        
        // Handle Shift release for double-tap
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (shiftPressed) {
                val downTime = modifierDownTimes[keyCode] ?: 0L
                val holdDuration = if (downTime > 0) event?.eventTime?.minus(downTime) ?: 0L else 0L
                val isLongHold = holdDuration > 300L
                val stickyEnabled = SettingsManager.isStaticVariationBarLayerStickyEnabled(this)
                val isIntentionalHold = variationInteractedDuringHold || (isLongHold && !otherKeyInteractedDuringHold)

                if (isIntentionalHold) {
                    modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                    // Sticky layer activation is handled via double-tap, not hold.
                    shiftLayerLatched = false
                    lastShiftTapUpTime = 0L
                    variationInteractedDuringHold = false
                    otherKeyInteractedDuringHold = false
                    modifierStateBeforeHold = null
                    modifierStateController.shiftPressed = false
                    modifierStateController.shiftPhysicallyPressed = false
                    updateStatusBarText()
                } else {
                    val result = modifierStateController.handleShiftKeyUp(keyCode)
                    if (result.shouldUpdateStatusBar) {
                        updateStatusBarText()
                    }
                    val isQuickTap = holdDuration < 300L && !variationInteractedDuringHold && !otherKeyInteractedDuringHold
                    if (stickyEnabled && isQuickTap) {
                        val now = event?.eventTime ?: System.currentTimeMillis()
                        if (lastShiftTapUpTime > 0L && now - lastShiftTapUpTime <= DOUBLE_TAP_THRESHOLD) {
                            shiftLayerLatched = true
                            lastShiftTapUpTime = 0L
                            updateStatusBarText()
                        } else {
                            lastShiftTapUpTime = now
                        }
                    } else {
                        lastShiftTapUpTime = 0L
                    }
                }
                variationInteractedDuringHold = false
                otherKeyInteractedDuringHold = false
                modifierDownTimes.remove(keyCode)
            }
            return super.onKeyUp(keyCode, event)
        }
        
        // Handle Ctrl release for double-tap
        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            if (ctrlPressed) {
                val downTime = modifierDownTimes[keyCode] ?: 0L
                val holdDuration = if (downTime > 0) event?.eventTime?.minus(downTime) ?: 0L else 0L
                val isLongHold = holdDuration > 300L
                val shortcutUsedDuringHold = otherKeyInteractedDuringHold
                val isIntentionalHold = variationInteractedDuringHold || (isLongHold && !otherKeyInteractedDuringHold)

                if (isIntentionalHold) {
                    modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                    variationInteractedDuringHold = false
                    otherKeyInteractedDuringHold = false
                    modifierStateBeforeHold = null
                    modifierStateController.ctrlPressed = false
                    modifierStateController.ctrlPhysicallyPressed = false
                    updateStatusBarText()
                } else {
                    val result = modifierStateController.handleCtrlKeyUp(keyCode)
                    if (result.shouldUpdateStatusBar) {
                        updateStatusBarText()
                    }
                }
                // Ctrl key-down enables one-shot; if Ctrl was used as a physically held shortcut,
                // clear that one-shot on release so Ctrl doesn't remain active.
                if (shortcutUsedDuringHold && ctrlOneShot && !ctrlLatchActive) {
                    ctrlOneShot = false
                    updateStatusBarText()
                }
                modifierDownTimes.remove(keyCode)
            }
            return super.onKeyUp(keyCode, event)
        }
        
        // Handle Alt release for double-tap
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            if (altPressed) {
                val downTime = modifierDownTimes[keyCode] ?: 0L
                val holdDuration = if (downTime > 0) event?.eventTime?.minus(downTime) ?: 0L else 0L
                val isLongHold = holdDuration > 300L
                val stickyEnabled = SettingsManager.isStaticVariationBarLayerStickyEnabled(this)
                val isIntentionalHold = variationInteractedDuringHold || (isLongHold && !otherKeyInteractedDuringHold)

                if (isIntentionalHold) {
                    modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
                    // Sticky layer activation is handled via double-tap, not hold.
                    altLayerLatched = false
                    lastAltTapUpTime = 0L
                    variationInteractedDuringHold = false
                    otherKeyInteractedDuringHold = false
                    modifierStateBeforeHold = null
                    modifierStateController.altPressed = false
                    modifierStateController.altPhysicallyPressed = false
                    updateStatusBarText()
                } else {
                    val result = modifierStateController.handleAltKeyUp(keyCode)
                    if (result.shouldUpdateStatusBar) {
                        updateStatusBarText()
                    }
                    val isQuickTap = holdDuration < 300L && !variationInteractedDuringHold && !otherKeyInteractedDuringHold
                    if (stickyEnabled && isQuickTap) {
                        val now = event?.eventTime ?: System.currentTimeMillis()
                        if (lastAltTapUpTime > 0L && now - lastAltTapUpTime <= DOUBLE_TAP_THRESHOLD) {
                            altLayerLatched = true
                            lastAltTapUpTime = 0L
                            updateStatusBarText()
                        } else {
                            lastAltTapUpTime = now
                        }
                    } else {
                        lastAltTapUpTime = 0L
                    }
                }
                variationInteractedDuringHold = false
                otherKeyInteractedDuringHold = false
                modifierDownTimes.remove(keyCode)
            }
            return super.onKeyUp(keyCode, event)
        }
        
        // Toggle SYM layout on key release only when SYM was tapped alone.
        if (keyCode == KEYCODE_SYM) {
            if (symTogglePendingOnKeyUp && !symChordUsedSinceKeyDown) {
                symLayoutController.toggleSymPage()
                updateStatusBarText()
            }
            symTogglePendingOnKeyUp = false
            symChordUsedSinceKeyDown = false
            return true
        }
        
        if (symLayoutController.handleKeyUp(keyCode, shiftPressed)) {
            return true
        }

        val handled = super.onKeyUp(keyCode, event)
        if (!isPureModifierKey(keyCode) && ::textExpansionController.isInitialized) {
            textExpansionController.scheduleRefresh()
        }
        return handled
    }

    /**
     * Aggiunge una nuova mappatura Alt+tasto -> carattere.
     */
    fun addAltKeyMapping(keyCode: Int, character: String) {
        altSymManager.addAltKeyMapping(keyCode, character)
    }

    /**
     * Rimuove una mappatura Alt+tasto esistente.
     */
    fun removeAltKeyMapping(keyCode: Int) {
        altSymManager.removeAltKeyMapping(keyCode)
    }
    
    /**
     * Updates additional IME subtypes from SharedPreferences.
     * This must be called from within the IME service process.
     */
    private fun updateAdditionalSubtypes() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val packageName = packageName
            val serviceName = PhysicalKeyboardInputMethodService::class.java.name
            
            val imeInfo = imm.enabledInputMethodList.find {
                it.packageName == packageName && 
                it.serviceName == serviceName
            } ?: run {
                Log.w(TAG, "IME not found, cannot update additional subtypes")
                return
            }
            
            val imeId = imeInfo.id
            val additionalSubtypes = SettingsManager.getAdditionalImeSubtypes(this)
            
            Log.d(TAG, "Updating additional subtypes from IME service: ${additionalSubtypes.joinToString(", ")}")
            
            if (additionalSubtypes.isEmpty()) {
                // Clear additional subtypes
                setAdditionalInputMethodSubtypesCompat(imm, imeId, emptyArray())
                Log.d(TAG, "Cleared additional subtypes")
                return
            }
            
            // Build subtypes
            val subtypes = additionalSubtypes.map { langCode ->
                val localeTag = getLocaleTagForLanguage(langCode)
                val nameResId = getSubtypeNameResourceId(langCode)
                InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeNameResId(nameResId)
                    .setSubtypeLocale(localeTag)
                    .setSubtypeMode("keyboard")
                    .setSubtypeExtraValue("noSuggestions=true")
                    .build()
            }
            
            setAdditionalInputMethodSubtypesCompat(imm, imeId, subtypes.toTypedArray())
            Log.d(TAG, "Updated ${subtypes.size} additional subtypes from IME service")
            
            // Verify
            val verifySubtypes = imm.getEnabledInputMethodSubtypeList(imeInfo, true)
            Log.d(TAG, "Verification: Android reports ${verifySubtypes.size} enabled subtypes after update")
            verifySubtypes.forEach { subtype ->
                val name = try {
                    if (subtype.nameResId != 0) {
                        getString(subtype.nameResId)
                    } else {
                        "N/A"
                    }
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
                Log.d(TAG, "  - locale: ${subtype.localeString()}, name: $name")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating additional subtypes from IME service", e)
        }
    }
    
    private fun getLocaleTagForLanguage(languageCode: String): String {
        val localeMap = mapOf(
            "ru" to "ru_RU",
            "pt" to "pt_PT",
            "de" to "de_DE",
            "da" to "da_DK",
            "no" to "no_NO",
            "nb" to "nb_NO",
            "nn" to "nn_NO",
            "fr" to "fr_FR",
            "es" to "es_ES",
            "pl" to "pl_PL",
            "it" to "it_IT",
            "en" to "en_US"
        )
        return localeMap[languageCode.lowercase()] ?: languageCode
    }
    
    private fun getSubtypeNameResourceId(languageCode: String): Int {
        val resourceName = "input_method_name_$languageCode"
        return resources.getIdentifier(resourceName, "string", packageName)
            .takeIf { it != 0 } ?: R.string.input_method_name
    }

    private fun handleNativeImeTrackpadMotion(event: MotionEvent, origin: String): Boolean {
        if (!isNativeImeTrackpadProviderActive()) {
            return false
        }
        if (!DeviceSpecific.isTitan2Device()) {
            return false
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            return false
        }

        val deviceName = InputDevice.getDevice(event.deviceId)?.name.orEmpty()
        val isTrackpadEvent = event.isFromSource(InputDevice.SOURCE_TOUCHPAD) ||
            deviceName.equals("touchPad", ignoreCase = true)
        if (!isTrackpadEvent) {
            return false
        }

        Log.d(
            TRACKPAD_DEBUG_TAG,
            "NativeMotion[$origin]: action=${motionActionName(event.actionMasked)} source=${event.source}(0x${event.source.toString(16)}) deviceId=${event.deviceId} device='$deviceName' x=${event.x} y=${event.y}"
        )

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                nativeTrackpadGestureStart = NativeTrackpadGestureStart(
                    x = event.x,
                    y = event.y,
                    origin = origin,
                    actionName = motionActionName(event.actionMasked),
                    deviceId = event.deviceId,
                    source = event.source,
                    eventTimeUptimeMs = event.eventTime
                )
                DebugCaptureStore.recordRawTrackpadEvent(
                    provider = SettingsManager.TRACKPAD_PROVIDER_NATIVE_IME,
                    origin = origin,
                    phase = "down",
                    action = motionActionName(event.actionMasked),
                    outcome = "start",
                    startX = event.x,
                    startY = event.y,
                    x = event.x,
                    y = event.y,
                    deltaX = 0f,
                    deltaY = 0f,
                    threshold = nativeImeTrackpadSuggestionSwipeThreshold(),
                    deviceId = event.deviceId,
                    source = event.source,
                    eventTimeUptimeMs = event.eventTime
                )
                nativeTrackpadLastX = event.x
                nativeTrackpadLastY = event.y
                nativeTrackpadLastEventTimeUptimeMs = event.eventTime
                nativeTrackpadGestureHandled = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                nativeTrackpadGestureStart ?: NativeTrackpadGestureStart(
                    x = event.x,
                    y = event.y,
                    origin = origin,
                    actionName = motionActionName(event.actionMasked),
                    deviceId = event.deviceId,
                    source = event.source,
                    eventTimeUptimeMs = event.eventTime
                ).also { nativeTrackpadGestureStart = it }
                for (index in 0 until event.historySize) {
                    val historicalX = event.getHistoricalX(0, index)
                    val historicalY = event.getHistoricalY(0, index)
                    nativeTrackpadLastX = historicalX
                    nativeTrackpadLastY = historicalY
                    nativeTrackpadLastEventTimeUptimeMs = event.getHistoricalEventTime(index)
                }
                nativeTrackpadLastX = event.x
                nativeTrackpadLastY = event.y
                nativeTrackpadLastEventTimeUptimeMs = event.eventTime
                return true
            }
            MotionEvent.ACTION_UP -> {
                val start = nativeTrackpadGestureStart
                if (start != null && !nativeTrackpadGestureHandled) {
                    handleNativeImeTrackpadSwipeCandidate(
                        start = start,
                        x = nativeTrackpadLastX,
                        y = nativeTrackpadLastY,
                        phase = "up",
                        eventTimeUptimeMs = nativeTrackpadLastEventTimeUptimeMs.takeIf { it > 0L } ?: event.eventTime
                    )
                }
                nativeTrackpadGestureStart = null
                nativeTrackpadGestureHandled = false
                nativeTrackpadLastEventTimeUptimeMs = 0L
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                nativeTrackpadGestureStart = null
                nativeTrackpadGestureHandled = false
                nativeTrackpadLastEventTimeUptimeMs = 0L
                return true
            }
            else -> return false
        }
    }

    private fun handleNativeImeTrackpadSwipeCandidate(
        start: NativeTrackpadGestureStart,
        x: Float,
        y: Float,
        phase: String,
        eventTimeUptimeMs: Long
    ): Boolean {
        val deltaX = x - start.x
        val deltaY = y - start.y
        val upwardDistance = -deltaY
        val leftwardDistance = -deltaX
        val suggestionThreshold = nativeImeTrackpadSuggestionSwipeThreshold()
        val deleteThreshold = nativeImeTrackpadDeleteSwipeThreshold()
        val durationMs = (eventTimeUptimeMs - start.eventTimeUptimeMs).coerceAtLeast(1L)
        val upVelocity = upwardDistance / durationMs
        val leftVelocity = leftwardDistance / durationMs
        val verticalEnough = upwardDistance >= suggestionThreshold
        val mostlyVertical = kotlin.math.abs(deltaX) < upwardDistance / 4f
        val verticalFastEnough = upVelocity >= NATIVE_TRACKPAD_MIN_SWIPE_VELOCITY_PX_PER_MS
        val leftEnough = leftwardDistance >= deleteThreshold
        val mostlyHorizontal = kotlin.math.abs(deltaY) < leftwardDistance / 4f
        val horizontalFastEnough = leftVelocity >= NATIVE_TRACKPAD_MIN_SWIPE_VELOCITY_PX_PER_MS
        Log.d(
            TRACKPAD_DEBUG_TAG,
            "Native candidate[$phase]: startX=${start.x}, startY=${start.y}, x=$x, y=$y, dx=$deltaX, dy=$deltaY, up=$upwardDistance, left=$leftwardDistance, suggestionThreshold=$suggestionThreshold, deleteThreshold=$deleteThreshold, duration=${durationMs}ms, upVelocity=$upVelocity, leftVelocity=$leftVelocity, verticalEnough=$verticalEnough, mostlyVertical=$mostlyVertical, verticalFastEnough=$verticalFastEnough, leftEnough=$leftEnough, mostlyHorizontal=$mostlyHorizontal, horizontalFastEnough=$horizontalFastEnough"
        )
        val candidateThreshold = if (leftwardDistance > upwardDistance) deleteThreshold else suggestionThreshold
        val direction = when {
            verticalEnough && mostlyVertical && verticalFastEnough -> NativeTrackpadSwipeDirection.UP
            leftEnough &&
                mostlyHorizontal &&
                horizontalFastEnough &&
                SettingsManager.getSwipeToDelete(this) &&
                SettingsManager.getSwipeToDeleteProvider(this) == SettingsManager.SWIPE_TO_DELETE_PROVIDER_NATIVE_IME -> NativeTrackpadSwipeDirection.LEFT
            else -> {
                DebugCaptureStore.recordRawTrackpadEvent(
                    provider = SettingsManager.TRACKPAD_PROVIDER_NATIVE_IME,
                    origin = start.origin,
                    phase = phase,
                    action = start.actionName,
                    outcome = "candidate",
                    startX = start.x,
                    startY = start.y,
                    x = x,
                    y = y,
                    deltaX = deltaX,
                    deltaY = deltaY,
                    threshold = candidateThreshold,
                    deviceId = start.deviceId,
                    source = start.source,
                    eventTimeUptimeMs = eventTimeUptimeMs
                )
                return false
            }
        }

        val now = System.currentTimeMillis()
        if (now - nativeTrackpadGestureAtMs < 250L) {
            DebugCaptureStore.recordRawTrackpadEvent(
                provider = SettingsManager.TRACKPAD_PROVIDER_NATIVE_IME,
                origin = start.origin,
                phase = phase,
                action = start.actionName,
                outcome = "debounced",
                startX = start.x,
                startY = start.y,
                x = x,
                y = y,
                deltaX = deltaX,
                deltaY = deltaY,
                threshold = when (direction) {
                    NativeTrackpadSwipeDirection.UP -> suggestionThreshold
                    NativeTrackpadSwipeDirection.LEFT -> deleteThreshold
                },
                deviceId = start.deviceId,
                source = start.source,
                eventTimeUptimeMs = eventTimeUptimeMs
            )
            nativeTrackpadGestureHandled = true
            return true
        }
        nativeTrackpadGestureAtMs = now
        nativeTrackpadGestureHandled = true

        when (direction) {
            NativeTrackpadSwipeDirection.UP -> {
                val third = nativeImeTrackpadThird(start.x)
                Log.d(
                    TRACKPAD_DEBUG_TAG,
                    "Native swipe accepted[$phase]: direction=UP startX=${start.x}, startY=${start.y}, x=$x, y=$y, dx=$deltaX, dy=$deltaY, duration=${durationMs}ms, velocity=$upVelocity, third=$third"
                )
                KeyboardEventTracker.notifySyntheticGestureKeyEvent(
                    provider = SettingsManager.TRACKPAD_PROVIDER_NATIVE_IME,
                    origin = start.origin,
                    phase = phase,
                    action = start.actionName,
                    direction = direction.name.lowercase(),
                    outcome = "accepted_suggestion_$third",
                    startX = start.x,
                    startY = start.y,
                    x = x,
                    y = y,
                    deltaX = deltaX,
                    deltaY = deltaY,
                    threshold = suggestionThreshold,
                    deviceId = start.deviceId,
                    source = start.source,
                    eventTimeUptimeMs = eventTimeUptimeMs
                )
                acceptSuggestionAtIndex(third)
            }
            NativeTrackpadSwipeDirection.LEFT -> {
                Log.d(
                    TRACKPAD_DEBUG_TAG,
                    "Native swipe accepted[$phase]: direction=LEFT startX=${start.x}, startY=${start.y}, x=$x, y=$y, dx=$deltaX, dy=$deltaY, duration=${durationMs}ms, velocity=$leftVelocity"
                )
                KeyboardEventTracker.notifySyntheticGestureKeyEvent(
                    provider = SettingsManager.TRACKPAD_PROVIDER_NATIVE_IME,
                    origin = start.origin,
                    phase = phase,
                    action = start.actionName,
                    direction = direction.name.lowercase(),
                    outcome = "accepted_delete",
                    startX = start.x,
                    startY = start.y,
                    x = x,
                    y = y,
                    deltaX = deltaX,
                    deltaY = deltaY,
                    threshold = deleteThreshold,
                    deviceId = start.deviceId,
                    source = start.source,
                    eventTimeUptimeMs = eventTimeUptimeMs
                )
                deleteWordFromNativeTrackpadSwipe()
            }
        }
        return true
    }

    private fun nativeImeTrackpadSuggestionSwipeThreshold(): Float {
        return SettingsManager.getTrackpadSuggestionSwipeThreshold(this)
    }

    private fun nativeImeTrackpadDeleteSwipeThreshold(): Float {
        return SettingsManager.getTrackpadDeleteSwipeThreshold(this)
    }

    private fun nativeImeTrackpadThird(x: Float): Int {
        val width = 1440f
        val clampedX = x.coerceIn(0f, width)
        return when {
            clampedX < width / 3f -> 0
            clampedX < (width * 2f) / 3f -> 1
            else -> 2
        }
    }

    private fun motionActionName(action: Int): String {
        return when (action) {
            MotionEvent.ACTION_DOWN -> "ACTION_DOWN"
            MotionEvent.ACTION_UP -> "ACTION_UP"
            MotionEvent.ACTION_MOVE -> "ACTION_MOVE"
            MotionEvent.ACTION_CANCEL -> "ACTION_CANCEL"
            MotionEvent.ACTION_SCROLL -> "ACTION_SCROLL"
            else -> "ACTION_$action"
        }
    }

    private fun deleteWordFromNativeTrackpadSwipe() {
        val ic = currentInputConnection
        if (ic == null) {
            Log.w(TRACKPAD_DEBUG_TAG, "Native swipe-to-delete ignored: no InputConnection")
            return
        }
        if (TextSelectionHelper.deleteLastWord(ic)) {
            Log.d(TRACKPAD_DEBUG_TAG, "Native swipe-to-delete deleted previous word")
        } else {
            Log.d(TRACKPAD_DEBUG_TAG, "Native swipe-to-delete found nothing to delete")
        }
    }

    private fun acceptSuggestionAtIndex(third: Int) {
        val visibleSuggestions = visibleSuggestionStrings()

        // Clear latched UI layers when selecting a suggestion via trackpad.
        if (shiftLayerLatched || altLayerLatched) {
            shiftLayerLatched = false
            altLayerLatched = false
            modifierStateBeforeHold?.let { modifierStateController.restoreLogicalState(it) }
            modifierStateBeforeHold = null
        }
        variationInteractedDuringHold = true

        // Allow gesture only when suggestions bar should be visible/usable
        val addWordCandidate = suggestionController.pendingAddWord()
        val addWordGestureEnabled = SettingsManager.getTrackpadGestureAddWordEnabled(this)
        val canAddWordByGesture = TrackpadAddWordGesturePolicy.canAddWordByGesture(
            third = third,
            addWordGestureEnabled = addWordGestureEnabled,
            fullWidthWhenAddOnlyEnabled = SettingsManager.getTrackpadGestureAddWordFullWidthEnabled(this),
            addWordCandidate = addWordCandidate,
            visibleSuggestions = visibleSuggestions
        )
        val allowGesture =
            symPage == 0 &&
            (visibleSuggestions.isNotEmpty() || canAddWordByGesture) &&
            SettingsManager.getSuggestionsEnabled(this) &&
            !shouldDisableSmartFeatures
        if (!allowGesture) {
            Log.d(
                TAG,
                "Trackpad gesture ignored: bar not visible/usable (sym=$symPage, suggestions=${visibleSuggestions.size})"
            )
            return
        }

        if (canAddWordByGesture) {
            val wordToAdd = addWordCandidate ?: return
            Log.d(TAG, "Adding user word '$wordToAdd' from trackpad gesture")
            uiHandler.post {
                val ic = currentInputConnection
                candidatesBarController.flashSuggestionSlot(2)
                suggestionController.addUserWord(wordToAdd)
                suggestionController.clearPendingAddWord()
                if (ic != null) {
                    AddWordCommitHelper.commitAutoSpaceAfterAddWord(ic)
                }
                updateStatusBarText()
                NotificationHelper.triggerHapticFeedback(this)
            }
            return
        }

        // Log current suggestions
        Log.d(TAG, "Current latestSuggestions: $visibleSuggestions")

        // Map third to suggestion index based on FullSuggestionsBar slot layout
        // slots[0] = left = suggestions[2]
        // slots[1] = center = suggestions[0]
        // slots[2] = right = suggestions[1]
        val suggestionIndex = when (third) {
            0 -> 2  // Left third → suggestions[2]
            1 -> 0  // Center third → suggestions[0]
            2 -> 1  // Right third → suggestions[1]
            else -> return
        }

        val suggestion = visibleSuggestions.getOrNull(suggestionIndex)
        if (suggestion == null) {
            Log.d(TAG, "No suggestion at index $suggestionIndex (third=$third), latestSuggestions=$visibleSuggestions")
            return
        }

        uiHandler.post {
            val ic = currentInputConnection
            if (ic == null) {
                Log.w(TAG, "No InputConnection available")
                return@post
            }

            // Provide visual feedback on the suggestions bar, matching variation press color
            candidatesBarController.flashSuggestionSlot(suggestionIndex)

            val forceLeadingCapital = AutoCapitalizeHelper.shouldAutoCapitalizeAtCursor(
                context = this,
                inputConnection = ic,
                shouldDisableAutoCapitalize = shouldDisableAutoCapitalize
            ) && SettingsManager.getAutoCapitalizeFirstLetter(this)

            Log.d(TAG, "Accepting suggestion '$suggestion' from third=$third (index=$suggestionIndex)")

            // Use the same logic as SuggestionButtonHandler
            val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
            val after = ic.getTextAfterCursor(64, 0)?.toString().orEmpty()
            fun isBoundaryChar(ch: Char, prev: Char?, next: Char?): Boolean {
                return it.palsoftware.pastiera.core.Punctuation.isWordBoundary(ch, prev, next)
            }

            // Find start of word in 'before'
            var start = before.length
            while (start > 0) {
                val ch = before[start - 1]
                val prev = before.getOrNull(start - 2)
                val next = before.getOrNull(start)
                if (!isBoundaryChar(ch, prev, next)) {
                    start--
                    continue
                }
                break
            }

            // Find end of word in 'after'
            var end = 0
            while (end < after.length) {
                val ch = after[end]
                val prev = if (end == 0) before.lastOrNull() else after[end - 1]
                val next = after.getOrNull(end + 1)
                if (!isBoundaryChar(ch, prev, next)) {
                    end++
                    continue
                }
                break
            }

            val wordBeforeCursor = before.substring(start)
            val wordAfterCursor = after.substring(0, end)
            val currentWord = wordBeforeCursor + wordAfterCursor

            val deleteBefore = wordBeforeCursor.length
            val deleteAfter = wordAfterCursor.length
            val replacement = it.palsoftware.pastiera.core.suggestions.CasingHelper.applyCasing(
                suggestion, currentWord, forceLeadingCapital
            )
            val shouldAppendSpace = !replacement.endsWith("'")

            ic.deleteSurroundingText(deleteBefore, deleteAfter)
            val textToCommit = if (shouldAppendSpace) "$replacement " else replacement
            ic.commitText(textToCommit, 1)
            if (shiftOneShot) {
                modifierStateController.consumeShiftOneShot()
            }
            DebugCaptureStore.recordAutoCorrectionCommit(
                before = currentWord,
                after = replacement,
                trigger = DebugCaptureStore.AutoCorrectionTrigger.SUGGESTION_TAP,
                source = "UNKNOWN"
            )

            if (shouldAppendSpace) {
                it.palsoftware.pastiera.core.AutoSpaceTracker.markAutoSpace()
            }

            // CRITICAL FIX: Reset tracker after accepting suggestion to prevent duplicate letters
            // The cursor debounce can cause tracker to be out of sync when user types quickly after accepting
            suggestionController.onContextReset()
            NotificationHelper.triggerHapticFeedback(this)
            Log.d(TAG, "Suggestion '$suggestion' inserted successfully")
        }
    }

    private data class NativeTrackpadGestureStart(
        val x: Float,
        val y: Float,
        val origin: String,
        val actionName: String,
        val deviceId: Int,
        val source: Int,
        val eventTimeUptimeMs: Long
    )

    private enum class NativeTrackpadSwipeDirection {
        UP,
        LEFT
    }
}
