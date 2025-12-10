package it.palsoftware.pastiera.inputmethod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import it.palsoftware.pastiera.SettingsManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.inputmethod.KeyboardEventTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.NotificationHelper
import it.palsoftware.pastiera.core.AutoCorrectionManager
import it.palsoftware.pastiera.core.InputContextState
import it.palsoftware.pastiera.core.ModifierStateController
import it.palsoftware.pastiera.core.NavModeController
import it.palsoftware.pastiera.core.SymLayoutController
import it.palsoftware.pastiera.core.TextInputController
import it.palsoftware.pastiera.core.suggestions.SuggestionController
import it.palsoftware.pastiera.core.suggestions.SuggestionResult
import it.palsoftware.pastiera.core.suggestions.SuggestionSettings
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.data.layout.LayoutFileStore
import it.palsoftware.pastiera.data.layout.LayoutMapping
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import it.palsoftware.pastiera.data.variation.VariationRepository
import it.palsoftware.pastiera.inputmethod.SpeechRecognitionActivity
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils
import java.util.Locale
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import it.palsoftware.pastiera.clipboard.ClipboardHistoryManager

/**
 * Input method service specialized for physical keyboards.
 * Handles advanced features such as long press that simulates Alt+key.
 */
class PhysicalKeyboardInputMethodService : InputMethodService() {

    companion object {
        private const val TAG = "PastieraInputMethod"
    }

    // SharedPreferences for settings
    private lateinit var prefs: SharedPreferences
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

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

    // Keycode for the SYM key
    private val KEYCODE_SYM = 63

    // Single instance to show toasts without overlapping
    private var lastLayoutToastText: String? = null
    private var lastLayoutToastTime: Long = 0
    private var suppressNextLayoutReload: Boolean = false
    
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
    
    // Snapshot of the current input context (numeric/password/restricted fields, etc.)
    private var inputContextState: InputContextState = InputContextState.EMPTY
    
    private val isNumericField: Boolean
        get() = inputContextState.isNumericField
    
    private val shouldDisableSmartFeatures: Boolean
        get() = inputContextState.shouldDisableSmartFeatures
    
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
    private lateinit var keyboardVisibilityController: KeyboardVisibilityController
    private lateinit var launcherShortcutController: LauncherShortcutController
    private lateinit var clipboardHistoryManager: ClipboardHistoryManager
    private var latestSuggestions: List<String> = emptyList()
    private var clearAltOnSpaceEnabled: Boolean = false
    private var isLanguageSwitchInProgress: Boolean = false
    private var clipboardOverlayActive: Boolean = false
    // Stato per ricordare se il nav mode era attivo prima di entrare in un campo di testo
    private var navModeWasActiveBeforeEditableField: Boolean = false

    private val multiTapHandler = Handler(Looper.getMainLooper())
    private val multiTapController = MultiTapController(
        handler = multiTapHandler,
        timeoutMs = MULTI_TAP_TIMEOUT_MS
    )
    private val uiHandler = Handler(Looper.getMainLooper())

    private val symPage: Int
        get() = if (::symLayoutController.isInitialized) symLayoutController.currentSymPage() else 0

    private fun updateInputContextState(info: EditorInfo?) {
        inputContextState = InputContextState.fromEditorInfo(info)
    }

    @Suppress("DEPRECATION")
    private fun updateNavModeStatusIcon(isActive: Boolean) {
        // Deprecated but still works on current Android versions; use for quick nav mode indicator.
        if (isActive) {
            showStatusIcon(R.drawable.ic_nav_mode_status)
        } else {
            hideStatusIcon()
        }
    }

    private fun refreshStatusBar() {
        updateStatusBarText()
    }
    
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
                shouldDisableAutoCapitalize = { inputContextState.shouldDisableAutoCapitalize },
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
            suggestionsEnabled = suggestionsEnabled,
            accentMatching = SettingsManager.getAccentMatchingEnabled(this),
            autoReplaceOnSpaceEnter = SettingsManager.getAutoReplaceOnSpaceEnter(this),
            maxAutoReplaceDistance = SettingsManager.getMaxAutoReplaceDistance(this),
            maxSuggestions = 3,
            useKeyboardProximity = SettingsManager.getUseKeyboardProximity(this),
            useEditTypeRanking = SettingsManager.getUseEditTypeRanking(this)
        )
    }

    private fun clearAltOnBoundaryIfNeeded(keyCode: Int, updateStatusBar: () -> Unit) {
        if (!clearAltOnSpaceEnabled) return
        val isBoundary = keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER
        if (!isBoundary) return
        val hasAlt = altLatchActive || altOneShot
        if (!hasAlt) return
        modifierStateController.clearAltState()
        updateStatusBar()
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
        isAutoCorrectEnabled: Boolean
    ): Boolean {
        if (keyCode != KeyEvent.KEYCODE_ENTER || navModeController.isNavModeActive()) {
            return false
        }

        val actionId = resolveEditorAction(info) ?: return false
        val ic = inputConnection ?: return false

        ic.finishComposingText()
        autoCorrectionManager.handleBoundaryKey(
            keyCode = keyCode,
            event = event,
            inputConnection = ic,
            isAutoCorrectEnabled = isAutoCorrectEnabled,
            commitBoundary = false,
            onStatusBarUpdate = { updateStatusBarText() }
        )
        textInputController.handleAutoCapAfterEnter(
            keyCode,
            ic,
            inputContextState.shouldDisableAutoCapitalize
        ) { updateStatusBarText() }
        val performed = ic.performEditorAction(actionId)
        if (performed) {
            suggestionController.onContextReset()
            KeyboardEventTracker.notifyKeyEvent(
                keyCode,
                event,
                "KEY_DOWN",
                outputKeyCode = null,
                outputKeyCodeName = "editor_action_$actionId"
            )
        }
        return performed
    }

    private fun handleSuggestionsUpdated(suggestions: List<SuggestionResult>) {
        latestSuggestions = suggestions.map { it.candidate }
        uiHandler.post { updateStatusBarText() }
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
        val canCheckAutoCapitalize = isEditable && !state.shouldDisableAutoCapitalize
        
        if (!isReallyEditable) {
            isInputViewActive = false
            
            if (canCheckAutoCapitalize) {
                AutoCapitalizeHelper.checkAndEnableAutoCapitalize(
                    this,
                    currentInputConnection,
                    state.shouldDisableAutoCapitalize,
                    enableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
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
            state.shouldDisableAutoCapitalize,
            enableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
            disableShift = { modifierStateController.consumeShiftOneShot() },
            onUpdateStatusBar = { updateStatusBarText() }
        )
        
        symLayoutController.restoreSymPageIfNeeded { updateStatusBarText() }
        
        altSymManager.reloadLongPressThreshold()
        altSymManager.resetTransientState()
    }
    
    private fun enforceSmartFeatureDisabledState() {
        val state = inputContextState
        // Hide candidates view if suggestions are disabled
        if (state.shouldDisableSuggestions) {
            setCandidatesViewShown(false)
        }
        deactivateVariations()
    }
    
    /**
     * Loads keyboard layout from the current subtype if available, otherwise from JSON mapping or preferences.
     */
    private fun loadKeyboardLayout() {
        val layoutName = try {
            // First, try to get layout from current subtype
            val imm = getSystemService(InputMethodManager::class.java)
            val currentSubtype = imm.currentInputMethodSubtype
            if (currentSubtype != null) {
                val layoutFromSubtype = AdditionalSubtypeUtils.getKeyboardLayoutFromSubtype(currentSubtype)
                if (layoutFromSubtype != null) {
                    Log.d(TAG, "Loading layout from subtype: $layoutFromSubtype")
                    layoutFromSubtype
                } else {
                    // If not in subtype, get from JSON mapping based on locale
                    val locale = currentSubtype.locale ?: "en_US"
                            val layoutFromMapping = AdditionalSubtypeUtils.getLayoutForLocale(assets, locale, this)
                    Log.d(TAG, "Loading layout from JSON mapping for locale $locale: $layoutFromMapping")
                    layoutFromMapping
                }
            } else {
                // No subtype available, use preferences
                SettingsManager.getKeyboardLayout(this)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting layout from subtype, using preferences", e)
            SettingsManager.getKeyboardLayout(this)
        }
        
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
        LayoutMappingRepository.loadLayout(assets, layoutName, this)
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
                showToast = true // show toast "LANGUAGE - LAYOUT"
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
        val handled = multiTapController.handleTap(keyCode, mapping, useUppercase, ic)
        if (handled && allowLongPress) {
            val committedText = LayoutMappingRepository.resolveText(
                mapping,
                multiTapController.state.useUppercase,
                multiTapController.state.tapIndex
            )
            if (!committedText.isNullOrEmpty()) {
                altSymManager.scheduleLongPressOnly(keyCode, ic, committedText)
            }
        }
        if (handled) {
            val committedText = LayoutMappingRepository.resolveText(
                mapping,
                multiTapController.state.useUppercase,
                multiTapController.state.tapIndex
            )
            if (!committedText.isNullOrEmpty()) {
                suggestionController.onCharacterCommitted(committedText, inputConnection)
            }
        }
        return handled
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
    
    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
        clearAltOnSpaceEnabled = SettingsManager.getClearAltOnSpace(this)

        // Clear legacy nav mode notification since we now rely on the status icon only.
        NotificationHelper.cancelNavModeNotification(this)

        modifierStateController = ModifierStateController(DOUBLE_TAP_THRESHOLD)
        navModeController = NavModeController(this, modifierStateController)
        navModeController.setOnNavModeChangedListener { isActive ->
            updateNavModeStatusIcon(isActive)
        }
        inputEventRouter = InputEventRouter(this, navModeController)
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
            keyboardLayoutProvider = { SettingsManager.getKeyboardLayout(this) }
        )
        inputEventRouter.suggestionController = suggestionController
        
        // Preload dictionary in background so it's ready when user focuses a field
        suggestionController.preloadDictionary()

        // Initialize clipboard history manager first (needed by candidatesBarController)
        clipboardHistoryManager = ClipboardHistoryManager(this)
        clipboardHistoryManager.onCreate()

        candidatesBarController = CandidatesBarController(this, clipboardHistoryManager, assets, PhysicalKeyboardInputMethodService::class.java)
        candidatesBarController.onAddUserWord = { word ->
            suggestionController.addUserWord(word)
            suggestionController.clearPendingAddWord()
            updateStatusBarText()
        }
        candidatesBarController.onLanguageSwitchRequested = {
            cycleToNextLanguage()
        }

        // Register listener for variation selection (both controllers)
        val variationListener = object : VariationButtonHandler.OnVariationSelectedListener {
            override fun onVariationSelected(variation: String) {
                // Update variations after one has been selected (refresh view if needed)
                updateStatusBarText()
            }
        }
        candidatesBarController.onVariationSelectedListener = variationListener

        // Register listener for cursor movement (both controllers)
        val cursorListener = {
            updateStatusBarText()
        }
        candidatesBarController.onCursorMovedListener = cursorListener

        // Register listener for speech recognition
        candidatesBarController.onSpeechRecognitionRequested = {
            startSpeechRecognition()
        }
        // Register listener for clipboard page
        candidatesBarController.onClipboardRequested = {
            ensureInputViewCreated()
            // Toggle dedicated clipboard overlay (not part of SYM pages)
            clipboardOverlayActive = !clipboardOverlayActive
            // Ensure SYM state is closed when opening overlay
            if (clipboardOverlayActive && symLayoutController.isSymActive()) {
                symLayoutController.closeSymPage()
            }
            updateStatusBarText()
        }
        altSymManager = AltSymManager(assets, prefs, this)
        altSymManager.reloadSymMappings() // Load custom mappings for page 1 if present
        altSymManager.reloadSymMappings2() // Load custom mappings for page 2 if present
        // Register callback to be notified when an Alt character is inserted after long press.
        // Variations are updated automatically by updateStatusBarText().
        altSymManager.onAltCharInserted = { char ->
            updateStatusBarText()
            val ic = currentInputConnection
            // Apostrophe is never a boundary: use centralized punctuation set.
            val punctuationSet = it.palsoftware.pastiera.core.Punctuation.BOUNDARY
            val normalizedChar = it.palsoftware.pastiera.core.Punctuation.normalizeApostrophe(char)
            if (normalizedChar == '\'') {
                inputEventRouter.handleInWordApostrophe(ic, pendingApostrophe = false)
            } else if (normalizedChar in punctuationSet && ic != null) {
                val isAutoCorrectEnabled = SettingsManager.getAutoCorrectEnabled(this) && !inputContextState.shouldDisableAutoCorrect
                autoCorrectionManager.handleBoundaryKey(
                    keyCode = KeyEvent.KEYCODE_UNKNOWN,
                    event = null,
                    inputConnection = ic,
                    isAutoCorrectEnabled = isAutoCorrectEnabled,
                    commitBoundary = true,
                    onStatusBarUpdate = { updateStatusBarText() },
                    boundaryCharOverride = normalizedChar
                )
            }
        }
        symLayoutController = SymLayoutController(this, prefs, altSymManager)
        keyboardVisibilityController = KeyboardVisibilityController(
            candidatesBarController = candidatesBarController,
            symLayoutController = symLayoutController,
            isInputViewActive = { isInputViewActive },
            isNavModeLatched = { ctrlLatchFromNavMode },
            currentInputConnection = { currentInputConnection },
            isInputViewShown = { isInputViewShown },
            attachInputView = { view -> setInputView(view) },
            setCandidatesViewShown = { shown -> setCandidatesViewShown(shown) },
            requestShowInputView = { requestShowSelf(0) },
            refreshStatusBar = { refreshStatusBar() }
        )
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
        variationStateController = VariationStateController(VariationRepository.loadVariations(assets, this))
        
        // Load auto-correction rules
        AutoCorrector.loadCorrections(assets, this)
        
        // Register additional subtypes (custom input styles)
        registerAdditionalSubtypes()
        
        // Register listener for SharedPreferences changes
        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            if (key == "sym_mappings_custom") {
                Log.d(TAG, "SYM mappings page 1 changed, reloading...")
                // Reload SYM mappings for page 1
                altSymManager.reloadSymMappings()
                // Update status bar to reflect new mappings
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == "sym_mappings_page2_custom") {
                Log.d(TAG, "SYM mappings page 2 changed, reloading...")
                // Reload SYM mappings for page 2
                altSymManager.reloadSymMappings2()
                // Update status bar to reflect new mappings
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == "sym_pages_config") {
                Log.d(TAG, "SYM pages configuration changed, refreshing status bar...")
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == "clear_alt_on_space") {
                clearAltOnSpaceEnabled = SettingsManager.getClearAltOnSpace(this)
            } else if (key != null && (key.startsWith("auto_correct_custom_") || key == "auto_correct_enabled_languages")) {
                Log.d(TAG, "Auto-correction rules changed, reloading...")
                // Reload auto-corrections (including new custom languages)
                AutoCorrector.loadCorrections(assets, this)
            } else if (key == "variations_updated") {
                Log.d(TAG, "Variations file changed, reloading...")
                // Reload variations from file
                variationStateController = VariationStateController(VariationRepository.loadVariations(assets, this))
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
            } else if (key == AdditionalSubtypeUtils.PREF_CUSTOM_INPUT_STYLES) {
                Log.d(TAG, "Custom input styles changed, re-registering subtypes...")
                registerAdditionalSubtypes()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        
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
                if (intent?.action == "it.palsoftware.pastiera.ACTION_USER_DICTIONARY_UPDATED") {
                    Log.d(TAG, "User dictionary updated, refreshing...")
                    if (::suggestionController.isInitialized) {
                        suggestionController.refreshUserDictionary()
                    }
                }
            }
        }
        
        val userDictFilter = IntentFilter("it.palsoftware.pastiera.ACTION_USER_DICTIONARY_UPDATED")
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
        
        // Update additional subtypes on startup
        updateAdditionalSubtypes()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Remove listener when service is destroyed
        prefsListener?.let {
            prefs.unregisterOnSharedPreferenceChangeListener(it)
        }
        
        // Cleanup SpeechRecognitionManager
        speechRecognitionManager?.destroy()
        speechRecognitionManager = null

        // Cleanup ClipboardHistoryManager
        clipboardHistoryManager.onDestroy()

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
        val shouldShowInputView = super.onEvaluateInputViewShown()
        return keyboardVisibilityController.onEvaluateInputViewShown(shouldShowInputView)
    }

    /**
     * Computes the insets for the IME window.
     * This is critical for candidates view to receive touch events properly.
     * Setting contentTopInsets = visibleTopInsets ensures touch events reach the candidates view.
     */
    override fun onComputeInsets(outInsets: InputMethodService.Insets?) {
        super.onComputeInsets(outInsets)
        
        if (outInsets != null && !isFullscreenMode()) {
            outInsets.contentTopInsets = outInsets.visibleTopInsets
        }
    }

    /**
     * Evaluates whether the IME should run in fullscreen mode.
     * This is important for candidates view to receive touch events properly.
     */
    override fun onEvaluateFullscreenMode(): Boolean {
        // Return false to allow candidates view to receive touch events
        // Fullscreen mode can sometimes limit touch event handling
        return false
    }

    /**
     * Resets all modifier key states.
     * Called when leaving a field or closing/reopening the keyboard.
     * @param preserveNavMode If true, keeps Ctrl latch active when nav mode is enabled.
     */
    private fun resetModifierStates(preserveNavMode: Boolean = false) {
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
        // If SYM is active, ensure clipboard overlay is dismissed so SYM renders correctly.
        if (symLayoutController.isSymActive() && clipboardOverlayActive) {
            clipboardOverlayActive = false
        }
        val variationSnapshot = variationStateController.refreshFromCursor(
            currentInputConnection,
            inputContextState.shouldDisableVariations
        )
        val clipboardCount = clipboardHistoryManager?.getHistorySize() ?: 0
        
        val modifierSnapshot = modifierStateController.snapshot()
        val state = inputContextState
        val addWordCandidate = suggestionController.pendingAddWord()
        val suggestionsEnabled = SettingsManager.isExperimentalSuggestionsEnabled(this) && SettingsManager.getSuggestionsEnabled(this)
        val baseSuggestions = if (suggestionsEnabled) latestSuggestions else emptyList()
        val suggestionsWithAdd = if (addWordCandidate != null) {
            listOf(addWordCandidate)
        } else baseSuggestions
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
            clipboardOverlay = clipboardOverlayActive,
            clipboardCount = clipboardCount,
            variations = variationSnapshot.variations,
            suggestions = suggestionsWithAdd,
            addWordCandidate = addWordCandidate,
            lastInsertedChar = variationSnapshot.lastInsertedChar,
            // Granular smart features flags
            shouldDisableSuggestions = state.shouldDisableSuggestions,
            shouldDisableAutoCorrect = state.shouldDisableAutoCorrect,
            shouldDisableAutoCapitalize = state.shouldDisableAutoCapitalize,
            shouldDisableDoubleSpaceToPeriod = state.shouldDisableDoubleSpaceToPeriod,
            shouldDisableVariations = state.shouldDisableVariations,
            isEmailField = state.isEmailField,
            // Legacy flag for backward compatibility
            shouldDisableSmartFeatures = shouldDisableSmartFeatures
        )
        // Passa anche la mappa emoji quando SYM è attivo (solo pagina 1)
        val emojiMapText = symLayoutController.emojiMapText()
        // Passa le mappature SYM per la griglia emoji/caratteri
        val symMappings = symLayoutController.currentSymMappings()
        // Passa l'inputConnection per rendere i pulsanti clickabili
        val inputConnection = currentInputConnection
        candidatesBarController.updateStatusBars(snapshot, emojiMapText, inputConnection, symMappings)
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
        
        currentPackageName = info?.packageName
        
        // Reset clipboard overlay when starting new input
        clipboardOverlayActive = false

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
                enableShiftOneShot = { modifierStateController.requestShiftOneShotFromAutoCap() },
                onUpdateStatusBar = { updateStatusBarText() }
            )
            
            AutoCapitalizeHelper.checkAutoCapitalizeOnRestart(
                this,
                currentInputConnection,
                state.shouldDisableAutoCapitalize,
                enableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
                disableShift = { modifierStateController.consumeShiftOneShot() },
                onUpdateStatusBar = { updateStatusBarText() },
                inputContextState = state
            )
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        // Register additional subtypes when IME becomes active
        // This ensures dynamic languages are loaded even if service was already created
        if (!restarting) {
            registerAdditionalSubtypes()
        }

        updateInputContextState(info)
        initializeInputContext(restarting)
        suggestionController.onContextReset()

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
                enableShiftOneShot = { modifierStateController.requestShiftOneShotFromAutoCap() },
                onUpdateStatusBar = { updateStatusBarText() }
            )
            
            AutoCapitalizeHelper.checkAutoCapitalizeOnRestart(
                this,
                currentInputConnection,
                state.shouldDisableAutoCapitalize,
                enableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
                disableShift = { modifierStateController.consumeShiftOneShot() },
                onUpdateStatusBar = { updateStatusBarText() },
                inputContextState = state
            )
        }
    }
    
    override fun onFinishInput() {
        super.onFinishInput()
        isInputViewActive = false
        inputContextState = InputContextState.EMPTY
        multiTapController.cancelAll()
        resetModifierStates(preserveNavMode = true)
        // Se nav mode era attivo prima di entrare nel campo di testo, riattivalo ora
        if (navModeWasActiveBeforeEditableField) {
            navModeController.enterNavMode()
            navModeWasActiveBeforeEditableField = false
        }
    }
    
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        isInputViewActive = false
        if (finishingInput) {
            multiTapController.cancelAll()
            resetModifierStates(preserveNavMode = true)
            suggestionController.onContextReset()
        }
    }
    
    override fun onWindowShown() {
        super.onWindowShown()
        updateStatusBarText()
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
                Log.d(TAG, "Subtype $index: locale=${subtype.locale}, nameResId=${subtype.nameResId}, extraValue=${subtype.extraValue}")
            }
            
            if (subtypes.isNotEmpty() && inputMethodInfo != null) {
                // Note: setAdditionalInputMethodSubtypes is deprecated but still works on most Android versions
                // The subtypes will appear in the IME picker but may need to be enabled manually by the user
                imm.setAdditionalInputMethodSubtypes(imeId, subtypes)
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
                            
                            // Filter ALL subtypes (base + additional) to keep only those with valid system locales
                            val validSubtypes = allSubtypes.filter { subtype ->
                                AdditionalSubtypeUtils.shouldKeepSubtype(subtype, currentSystemLocales, systemLanguageCodes)
                            }
                            
                            // Convert to hash codes for setExplicitlyEnabledInputMethodSubtypes
                            val validEnabledHashCodes = validSubtypes.map { it.hashCode() }.toIntArray()
                            
                            // Always update enabled subtypes, even if empty (to disable removed ones)
                            imm.setExplicitlyEnabledInputMethodSubtypes(
                                updatedInfo.id,
                                validEnabledHashCodes
                            )
                            val removedBase = allSubtypes.count { !AdditionalSubtypeUtils.isAdditionalSubtype(it) } - 
                                             validSubtypes.count { !AdditionalSubtypeUtils.isAdditionalSubtype(it) }
                            val removedAdditional = subtypes.size - validSubtypes.count { AdditionalSubtypeUtils.isAdditionalSubtype(it) }
                            Log.d(TAG, "Updated enabled subtypes: ${validEnabledHashCodes.size} valid (removed ${removedBase} base, ${removedAdditional} additional)")
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
                                
                                // Filter to keep only subtypes with valid system locales
                                val validSubtypes = allSubtypes.filter { subtype ->
                                    AdditionalSubtypeUtils.shouldKeepSubtype(subtype, currentSystemLocales, systemLanguageCodes)
                                }
                                
                                val validEnabledHashCodes = validSubtypes.map { it.hashCode() }.toIntArray()
                                
                                // Always update to disable removed subtypes
                                imm.setExplicitlyEnabledInputMethodSubtypes(
                                    updatedInfo.id,
                                    validEnabledHashCodes
                                )
                                Log.d(TAG, "Filtered base subtypes: kept ${validEnabledHashCodes.size}, removed ${allSubtypes.size - validSubtypes.size}")
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
                            Log.d(TAG, "Subtype $index: locale=${subtype.locale}, isAdditional=$isAdditional, extraValue=${subtype.extraValue}")
                        }
                        
                        // Also try to get subtypes directly from InputMethodInfo
                        try {
                            val subtypeCount = verifyInfo.subtypeCount
                            Log.d(TAG, "InputMethodInfo reports $subtypeCount subtypes")
                            for (i in 0 until subtypeCount) {
                                val subtype = verifyInfo.getSubtypeAt(i)
                                val isAdditional = AdditionalSubtypeUtils.isAdditionalSubtype(subtype)
                                Log.d(TAG, "InputMethodInfo subtype $i: locale=${subtype.locale}, isAdditional=$isAdditional, extraValue=${subtype.extraValue}")
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
     * Gets the locale from the current IME subtype.
     * Falls back to Italian if no subtype is available.
     */
    private fun getLocaleFromSubtype(): Locale {
        val imm = getSystemService(InputMethodManager::class.java)
        val subtype = imm.currentInputMethodSubtype
        val localeString = subtype?.locale ?: "it_IT"
        return try {
            // Convert "en_US" format to Locale
            val parts = localeString.split("_")
            when (parts.size) {
                2 -> Locale(parts[0], parts[1])
                1 -> Locale(parts[0])
                else -> Locale.ITALIAN
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse locale from subtype: $localeString", e)
            Locale.ITALIAN
        }
    }
    
    /**
     * Called when the user switches IME subtypes (languages).
     * Reloads the dictionary for the new language and switches to the layout specified in the subtype or JSON mapping.
     */
    override fun onCurrentInputMethodSubtypeChanged(newSubtype: android.view.inputmethod.InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        
        if (::suggestionController.isInitialized) {
            val newLocale = getLocaleFromSubtype()
            suggestionController.updateLocale(newLocale)
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "IME subtype changed, updating locale to: ${newLocale.language}")
            }
        }
        
        // Get layout from subtype if specified, otherwise from JSON mapping
        val layoutToUse = AdditionalSubtypeUtils.getKeyboardLayoutFromSubtype(newSubtype)
            ?: run {
                val locale = newSubtype.locale ?: "en_US"
                AdditionalSubtypeUtils.getLayoutForLocale(assets, locale, this)
            }
        
        // Always switch to the layout for the current locale (they are strictly linked)
        // Update preferences to keep them in sync
        val currentLayout = SettingsManager.getKeyboardLayout(this)
        if (layoutToUse != currentLayout) {
            Log.d(TAG, "Switching layout for locale ${newSubtype.locale}: $layoutToUse (was: $currentLayout)")
            // Update preferences first to keep them in sync
            SettingsManager.setKeyboardLayout(this, layoutToUse)
            // Then load the layout
            switchToLayout(layoutToUse, showToast = false)
        } else {
            // Even if layout matches, ensure it's loaded (in case it was changed manually)
            switchToLayout(layoutToUse, showToast = false)
        }
    }
    
    override fun onWindowHidden() {
        super.onWindowHidden()
        multiTapController.finalizeCycle()
        resetModifierStates(preserveNavMode = true)
        suggestionController.onContextReset()
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        
        // Re-register subtypes when configuration changes (including when new system locales are added)
        // This ensures new system locales are available in IME picker without restarting IME
        Log.d(TAG, "Configuration changed, re-registering subtypes to pick up new system locales")
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
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        
        val state = inputContextState
        val cursorPositionChanged = (oldSelStart != newSelStart) || (oldSelEnd != newSelEnd)
        // Skip the reset when the selection moved forward by 1 as a direct result of our own commit.
        val movedByCommit = oldSelStart == oldSelEnd &&
            newSelEnd == newSelStart &&
            newSelStart == oldSelStart + 1
        
        if (cursorPositionChanged && newSelStart == newSelEnd && !movedByCommit) {
            // Update suggestions on cursor movement (if suggestions enabled)
            if (!state.shouldDisableSuggestions) {
                suggestionController.onCursorMoved(currentInputConnection)
            }
            
            // Always update status bar (it handles variations/suggestions internally based on flags)
            Handler(Looper.getMainLooper()).postDelayed({
                updateStatusBarText()
            }, CURSOR_UPDATE_DELAY)
        }
        
        // Check auto-capitalization on selection change (if auto-cap enabled)
        AutoCapitalizeHelper.checkAutoCapitalizeOnSelectionChange(
            this,
            currentInputConnection,
            state.shouldDisableAutoCapitalize,
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            enableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
            disableShift = { modifierStateController.consumeShiftOneShot() },
            onUpdateStatusBar = { updateStatusBarText() },
            inputContextState = state
        )
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Check if we have an editable field at the very start
        val info = currentInputEditorInfo
        val initialInputConnection = currentInputConnection
        val inputType = info?.inputType ?: EditorInfo.TYPE_NULL
        val hasEditableField = initialInputConnection != null && inputType != EditorInfo.TYPE_NULL
        if (hasEditableField && !isInputViewActive) {
            isInputViewActive = true
        }

        // If any SYM page or clipboard overlay is open, close on BACK and consume
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            var consumed = false
            if (symLayoutController.isSymActive()) {
                if (symLayoutController.closeSymPage()) {
                    consumed = true
                }
            }
            if (clipboardOverlayActive) {
                clipboardOverlayActive = false
                consumed = true
            }
            if (consumed) {
                updateStatusBarText()
                return true
            }
        }

        val navModeBefore = navModeController.isNavModeActive()

        val isModifierKey = keyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
            keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT ||
            keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
            keyCode == KeyEvent.KEYCODE_CTRL_RIGHT ||
            keyCode == KeyEvent.KEYCODE_ALT_LEFT ||
            keyCode == KeyEvent.KEYCODE_ALT_RIGHT

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
                    isAlphabeticKey = { code -> isAlphabeticKey(code) },
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
        
        // Handle Ctrl+Space for subtype cycling
        if (
            hasEditableField &&
            keyCode == KeyEvent.KEYCODE_SPACE &&
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
                modifierStateController.clearCtrlState(resetPressedState = true)
                if (navModeLatched) {
                    navModeController.cancelNotification()
                    navModeController.refreshNavModeState()
                }
                shouldUpdateStatusBar = true
            }

            // Cycle to next subtype
            if (SubtypeCycler.cycleToNextSubtype(this, PhysicalKeyboardInputMethodService::class.java, assets, showToast = true)) {
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

        clearAltOnBoundaryIfNeeded(keyCode) { updateStatusBarText() }

        if (handleEnterAsEditorAction(keyCode, info, ic, event, isAutoCorrectEnabled)) {
            return true
        }
        
        // Continue with normal IME logic
        KeyboardEventTracker.notifyKeyEvent(keyCode, event, "KEY_DOWN")
        if (!isInputViewShown && isInputViewActive) {
            ensureInputViewCreated()
        }
        
        if (
            inputEventRouter.handleTextInputPipeline(
                context = this,
                keyCode = keyCode,
                event = event,
                inputConnection = ic,
                shouldDisableSuggestions = state.shouldDisableSuggestions,
                shouldDisableAutoCorrect = state.shouldDisableAutoCorrect,
                shouldDisableAutoCapitalize = state.shouldDisableAutoCapitalize,
                shouldDisableDoubleSpaceToPeriod = state.shouldDisableDoubleSpaceToPeriod,
                isAutoCorrectEnabled = isAutoCorrectEnabled,
                textInputController = textInputController,
                autoCorrectionManager = autoCorrectionManager,
                inputContextState = state,
                enableShiftOneShot = { modifierStateController.requestShiftOneShotFromAutoCap() }
            ) { updateStatusBarText() }
        ) {
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
                ctrlPressed = ctrlPressed,
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
                cursorUpdateDelayMs = CURSOR_UPDATE_DELAY
            ),
            controllers = InputEventRouter.EditableFieldKeyDownControllers(
                modifierStateController = modifierStateController,
                symLayoutController = symLayoutController,
                altSymManager = altSymManager,
                variationStateController = variationStateController
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
                }
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
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // Check if we have an editable field at the start (same logic as onKeyDown)
        val info = currentInputEditorInfo
        val ic = currentInputConnection
        val inputType = info?.inputType ?: EditorInfo.TYPE_NULL
        val hasEditableField = ic != null && inputType != EditorInfo.TYPE_NULL
        
        // If NO editable field is active, handle ONLY nav mode Ctrl release
        if (!hasEditableField) {
            return inputEventRouter.handleKeyUpWithNoEditableField(
                keyCode = keyCode,
                event = event,
                ctrlKeyMap = ctrlKeyMap,
                callbacks = InputEventRouter.NoEditableFieldCallbacks(
                    isAlphabeticKey = { code -> isAlphabeticKey(code) },
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
        KeyboardEventTracker.notifyKeyEvent(keyCode, event, "KEY_UP")
        
        // Handle Shift release for double-tap
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (shiftPressed) {
                val result = modifierStateController.handleShiftKeyUp(keyCode)
                if (result.shouldUpdateStatusBar) {
                    updateStatusBarText()
                }
            }
            return super.onKeyUp(keyCode, event)
        }
        
        // Handle Ctrl release for double-tap
        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            if (ctrlPressed) {
                val result = modifierStateController.handleCtrlKeyUp(keyCode)
                if (result.shouldUpdateStatusBar) {
                    updateStatusBarText()
                }
            }
            return super.onKeyUp(keyCode, event)
        }
        
        // Handle Alt release for double-tap
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            if (altPressed) {
                val result = modifierStateController.handleAltKeyUp(keyCode)
                if (result.shouldUpdateStatusBar) {
                    updateStatusBarText()
                }
            }
            return super.onKeyUp(keyCode, event)
        }
        
        // Handle SYM key release (nothing to do; it is a toggle)
        if (keyCode == KEYCODE_SYM) {
            // Consumiamo l'evento
            return true
        }
        
        if (symLayoutController.handleKeyUp(keyCode, shiftPressed)) {
            return true
        }
        
        return super.onKeyUp(keyCode, event)
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
            val serviceName = "${packageName}.inputmethod.PhysicalKeyboardInputMethodService"
            
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
                imm.setAdditionalInputMethodSubtypes(imeId, emptyArray())
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
            
            imm.setAdditionalInputMethodSubtypes(imeId, subtypes.toTypedArray())
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
                Log.d(TAG, "  - locale: ${subtype.locale}, name: $name")
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
}
