package it.palsoftware.pastiera.inputmethod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import it.palsoftware.pastiera.SettingsManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.inputmethod.KeyboardEventTracker
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.view.MotionEvent
import android.view.InputDevice
import it.palsoftware.pastiera.inputmethod.MotionEventTracker
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import it.palsoftware.pastiera.data.variation.VariationRepository

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
    
    // Broadcast receiver for speech recognition
    private var speechResultReceiver: BroadcastReceiver? = null
    private lateinit var statusBarController: StatusBarController
    private lateinit var candidatesViewController: StatusBarController

    // Keycode for the SYM key
    private val KEYCODE_SYM = 63
    
    // State to track the active SYM page (0=disabled, 1=page1 emoji, 2=page2 characters)
    private var symPage = 0
    
    // Mapping Ctrl+key -> action or keycode (loaded from JSON)
    private val ctrlKeyMap = mutableMapOf<Int, KeyMappingLoader.CtrlMapping>()
    
    // Mapping of character variations (loaded from JSON)
    private val variationsMap = mutableMapOf<Char, List<String>>()
    
    // Last inserted character and its available variations
    private var lastInsertedChar: Char? = null
    private var availableVariations: List<String> = emptyList()
    private var variationsActive = false
    
    // Accessor properties for backwards compatibility with existing code
    private var capsLockEnabled: Boolean
        get() = shiftState.latchActive
        set(value) { shiftState.latchActive = value }
    
    private var shiftPressed: Boolean
        get() = shiftState.pressed
        set(value) { shiftState.pressed = value }
    
    private var ctrlLatchActive: Boolean
        get() = ctrlState.latchActive
        set(value) { ctrlState.latchActive = value }
    
    private var altLatchActive: Boolean
        get() = altState.latchActive
        set(value) { altState.latchActive = value }
    
    private var ctrlPressed: Boolean
        get() = ctrlState.pressed
        set(value) { ctrlState.pressed = value }
    
    private var altPressed: Boolean
        get() = altState.pressed
        set(value) { altState.pressed = value }
    
    private var shiftPhysicallyPressed: Boolean
        get() = shiftState.physicallyPressed
        set(value) { shiftState.physicallyPressed = value }
    
    private var ctrlPhysicallyPressed: Boolean
        get() = ctrlState.physicallyPressed
        set(value) { ctrlState.physicallyPressed = value }
    
    private var altPhysicallyPressed: Boolean
        get() = altState.physicallyPressed
        set(value) { altState.physicallyPressed = value }
    
    private var shiftOneShot: Boolean
        get() = autoCapitalizeState.shiftOneShot
        set(value) { autoCapitalizeState.shiftOneShot = value }
    
    private var ctrlOneShot: Boolean
        get() = ctrlState.oneShot
        set(value) { ctrlState.oneShot = value }
    
    private var altOneShot: Boolean
        get() = altState.oneShot
        set(value) { altState.oneShot = value }
    
    private var shiftOneShotEnabledTime: Long
        get() = autoCapitalizeState.shiftOneShotEnabledTime
        set(value) { autoCapitalizeState.shiftOneShotEnabledTime = value }
    
    private var ctrlLatchFromNavMode: Boolean
        get() = ctrlState.latchFromNavMode
        set(value) { ctrlState.latchFromNavMode = value }
    
    // Double-tap tracking on space to insert period and space
    private var lastSpacePressTime: Long = 0
    
    // Flag to track whether we are in a valid input context
    private var isInputViewActive = false
    
    // Flag to track whether we are in a numeric field
    private var isNumericField = false
    
    // Flag to track whether we are in a field that should have smart features disabled
    // (password, URL, email, filter, codes, OTP, tokens, etc.)
    private var shouldDisableSmartFeatures = false
    
    
    // Cache for launcher packages
    private var cachedLauncherPackages: Set<String>? = null
    
    // Current package name
    private var currentPackageName: String? = null
    
    // Modifier key handler
    private lateinit var modifierKeyHandler: ModifierKeyHandler
    private val shiftState = ModifierKeyHandler.ShiftState()
    private val ctrlState = ModifierKeyHandler.CtrlState()
    private val altState = ModifierKeyHandler.AltState()
    
    // Auto-capitalize helper state
    private val autoCapitalizeState = AutoCapitalizeHelper.AutoCapitalizeState()
    
    // Constants
    private val DOUBLE_TAP_THRESHOLD = 500L
    private val CURSOR_UPDATE_DELAY = 50L

    private fun refreshStatusBar() {
        updateStatusBarText()
    }
    
    /**
     * Syncs shiftState.oneShot with autoCapitalizeState.shiftOneShot.
     * This ensures both states stay synchronized when Shift is manually pressed.
     * When shiftOneShot is enabled/disabled by user action (Shift key press),
     * both states are updated.
     */
    private fun syncShiftOneShotFromShiftState() {
        autoCapitalizeState.shiftOneShot = shiftState.oneShot
        if (shiftState.oneShot && shiftState.oneShotEnabledTime > 0) {
            autoCapitalizeState.shiftOneShotEnabledTime = shiftState.oneShotEnabledTime
        } else if (!shiftState.oneShot) {
            autoCapitalizeState.shiftOneShotEnabledTime = 0
        }
    }
    
    /**
     * Syncs autoCapitalizeState.shiftOneShot with shiftState.oneShot.
     * This ensures both states stay synchronized when shiftOneShot is used (letter typed)
     * or disabled by other logic.
     */
    private fun syncShiftOneShotToShiftState() {
        shiftState.oneShot = autoCapitalizeState.shiftOneShot
        if (autoCapitalizeState.shiftOneShot && autoCapitalizeState.shiftOneShotEnabledTime > 0) {
            shiftState.oneShotEnabledTime = autoCapitalizeState.shiftOneShotEnabledTime
        } else if (!autoCapitalizeState.shiftOneShot) {
            shiftState.oneShotEnabledTime = 0
        }
    }
    
    /**
     * Checks if the given EditorInfo represents a field that should have smart features disabled.
     * This includes:
     * - Password fields (TYPE_TEXT_VARIATION_PASSWORD, TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, 
     *   TYPE_TEXT_VARIATION_WEB_PASSWORD, TYPE_NUMBER_VARIATION_PASSWORD)
     * - URL fields (TYPE_TEXT_VARIATION_URI)
     * - Email fields (TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
     * - Filter/search fields (TYPE_TEXT_VARIATION_FILTER)
     * 
     * Note: We do NOT check for TYPE_TEXT_FLAG_NO_SUGGESTIONS because we set it on all editable
     * fields to prevent Android's suggestion popup, so it's not a reliable indicator.
     * 
     * For these fields, all smart features are disabled:
     * - Autocorrection
     * - Suggestions
     * - Predictive text
     * - Auto-capitalize
     * - Double-space to period
     * - Variations (long-press variants)
     * 
     * @param info The EditorInfo to check
     * @return true if smart features should be disabled, false otherwise
     */
    private fun shouldDisableSmartFeatures(info: EditorInfo?): Boolean {
        if (info == null) return false
        val inputType = info.inputType
        val inputVariation = inputType and android.text.InputType.TYPE_MASK_VARIATION
        
        // Check for password field types
        val isPasswordType = inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                            inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                            inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                            inputVariation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        
        // Check for URL fields
        val isUriType = inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_URI
        
        // Check for email fields
        val isEmailType = inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        
        // Check for filter/search fields
        val isFilterType = inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_FILTER
        
        // Only check specific field types, not flags (since we set NO_SUGGESTIONS on all fields)
        return isPasswordType || isUriType || isEmailType || isFilterType
    }
    
    private fun checkFieldEditability(info: EditorInfo?): Pair<Boolean, Boolean> {
        val isEditable = info?.let { editorInfo ->
            val inputType = editorInfo.inputType
            val isTextInput = inputType and android.text.InputType.TYPE_MASK_CLASS != android.text.InputType.TYPE_NULL
            val isNotNoInput = inputType and android.text.InputType.TYPE_MASK_CLASS != 0
            isTextInput && isNotNoInput
        } ?: false
        
        val isReallyEditable = isEditable && info?.let { editorInfo ->
            val inputType = editorInfo.inputType
            val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
            inputClass == android.text.InputType.TYPE_CLASS_TEXT ||
            inputClass == android.text.InputType.TYPE_CLASS_NUMBER ||
            inputClass == android.text.InputType.TYPE_CLASS_PHONE ||
            inputClass == android.text.InputType.TYPE_CLASS_DATETIME
        } ?: false
        
        return Pair(isEditable, isReallyEditable)
    }

    /**
     * Initializes the input context for a field.
     * This method contains all common initialization logic that must run
     * regardless of whether input view or candidates view is shown.
     */
    private fun initializeInputContext(info: EditorInfo?, restarting: Boolean) {
        if (restarting) {
            return
        }
        
        val (isEditable, isReallyEditable) = checkFieldEditability(info)
        val canCheckAutoCapitalize = isEditable && !shouldDisableSmartFeatures
        
        if (!isReallyEditable) {
            if (ctrlLatchFromNavMode && ctrlLatchActive) {
                isInputViewActive = false
            } else {
                isInputViewActive = false
            }
            
            if (canCheckAutoCapitalize) {
                AutoCapitalizeHelper.checkAndEnableAutoCapitalize(
                    this,
                    currentInputConnection,
                    shouldDisableSmartFeatures,
                    autoCapitalizeState,
                    delayMs = 100L,
                    onUpdateStatusBar = { updateStatusBarText() }
                )
            }
            return
        }
        
        isInputViewActive = true
        
        isNumericField = info?.let { editorInfo ->
            val inputType = editorInfo.inputType
            val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
            inputClass == android.text.InputType.TYPE_CLASS_NUMBER
        } ?: false
        
        shouldDisableSmartFeatures = shouldDisableSmartFeatures(info)
        
        if (shouldDisableSmartFeatures) {
            setCandidatesViewShown(false)
            deactivateVariations()
        }
        
        if (ctrlLatchFromNavMode && ctrlLatchActive) {
            val inputConnection = currentInputConnection
            if (inputConnection != null) {
                ctrlLatchFromNavMode = false
                ctrlLatchActive = false
                NotificationHelper.cancelNavModeNotification(this)
            }
        }
        
        AutoCapitalizeHelper.checkAndEnableAutoCapitalize(
            this,
            currentInputConnection,
            shouldDisableSmartFeatures,
            autoCapitalizeState,
            delayMs = 100L,
            onUpdateStatusBar = { updateStatusBarText() }
        )
        
        // Restore SYM layout if it was active when SymCustomizationActivity was opened
        val restoreSymPage = SettingsManager.getRestoreSymPage(this)
        if (restoreSymPage > 0) {
            symPage = restoreSymPage
            // Save current symPage to SharedPreferences
            prefs.edit().putInt("current_sym_page", symPage).apply()
            // Clear restore state after restoring
            SettingsManager.clearRestoreSymPage(this)
            // Update status bar to show SYM layout
            Handler(Looper.getMainLooper()).post {
                updateStatusBarText()
            }
        }
        
        altSymManager.reloadLongPressThreshold()
        altSymManager.resetTransientState()
    }
    
    /**
     * Reloads nav mode key mappings from the file.
     */
    private fun loadKeyboardLayout() {
        val layoutName = SettingsManager.getKeyboardLayout(this)
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
     * Verifica se il package corrente è un launcher.
     */
    private fun isLauncher(packageName: String?): Boolean {
        if (packageName == null) return false
        
        // Cache la lista dei launcher per evitare query ripetute
        if (cachedLauncherPackages == null) {
            try {
                val pm = packageManager
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                }
                
                val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
                cachedLauncherPackages = resolveInfos.map { it.activityInfo.packageName }.toSet()
                Log.d(TAG, "Launcher packages trovati: $cachedLauncherPackages")
            } catch (e: Exception) {
                Log.e(TAG, "Errore nel rilevamento dei launcher", e)
                cachedLauncherPackages = emptySet()
            }
        }
        
        val isLauncher = cachedLauncherPackages?.contains(packageName) ?: false
        Log.d(TAG, "isLauncher($packageName) = $isLauncher")
        return isLauncher
    }
    
    /**
     * Apre un'app tramite package name.
     */
    private fun launchApp(packageName: String): Boolean {
        try {
            val pm = packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Log.d(TAG, "App aperta: $packageName")
                return true
            } else {
                Log.w(TAG, "Nessun launch intent trovato per: $packageName")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'apertura dell'app $packageName", e)
            return false
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
    
    /**
     * Handles launcher shortcuts when not in a text field.
     */
    private fun handleLauncherShortcut(keyCode: Int): Boolean {
        val shortcut = SettingsManager.getLauncherShortcut(this, keyCode)
        if (shortcut != null) {
            // Gestisci diversi tipi di azioni
            when (shortcut.type) {
                SettingsManager.LauncherShortcut.TYPE_APP -> {
                    if (shortcut.packageName != null) {
                        val success = launchApp(shortcut.packageName)
                        if (success) {
                            Log.d(TAG, "Scorciatoia launcher eseguita: tasto $keyCode -> ${shortcut.packageName}")
                            return true // Consumiamo l'evento
                        }
                    }
                }
                SettingsManager.LauncherShortcut.TYPE_SHORTCUT -> {
                    // TODO: Gestire scorciatoie in futuro
                    Log.d(TAG, "Tipo scorciatoia non ancora implementato: ${shortcut.type}")
                }
                else -> {
                    Log.d(TAG, "Tipo azione sconosciuto: ${shortcut.type}")
                }
            }
        } else {
            // Tasto non assegnato: mostra dialog per assegnare un'app
            showLauncherShortcutAssignmentDialog(keyCode)
            return true // Consumiamo l'evento per evitare che venga gestito altrove
        }
        return false // Non consumiamo l'evento
    }
    
    /**
     * Mostra il dialog per assegnare un'app a un tasto.
     */
    private fun showLauncherShortcutAssignmentDialog(keyCode: Int) {
        try {
            val intent = Intent(this, LauncherShortcutAssignmentActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(LauncherShortcutAssignmentActivity.EXTRA_KEY_CODE, keyCode)
            }
            startActivity(intent)
            Log.d(TAG, "Dialog assegnazione mostrato per tasto $keyCode")
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel mostrare il dialog di assegnazione", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
        
        NotificationHelper.createNotificationChannel(this)
        
        modifierKeyHandler = ModifierKeyHandler(DOUBLE_TAP_THRESHOLD)
        
        statusBarController = StatusBarController(this)
        candidatesViewController = StatusBarController(this)

        // Register listener for variation selection (both controllers)
        val variationListener = object : VariationButtonHandler.OnVariationSelectedListener {
            override fun onVariationSelected(variation: String) {
                // Update variations after one has been selected (refresh view if needed)
                updateStatusBarText()
            }
        }
        statusBarController.onVariationSelectedListener = variationListener
        candidatesViewController.onVariationSelectedListener = variationListener

        // Register listener for cursor movement (both controllers)
        val cursorListener = {
            updateStatusBarText()
        }
        statusBarController.onCursorMovedListener = cursorListener
        candidatesViewController.onCursorMovedListener = cursorListener
        altSymManager = AltSymManager(assets, prefs, this)
        altSymManager.reloadSymMappings() // Load custom mappings for page 1 if present
        altSymManager.reloadSymMappings2() // Load custom mappings for page 2 if present
        // Register callback to be notified when an Alt character is inserted after long press.
        // Variations are updated automatically by updateStatusBarText().
        altSymManager.onAltCharInserted = { char ->
            updateStatusBarText()
        }
        
        // Initialize keyboard layout
        loadKeyboardLayout()
        
        // Initialize nav mode mappings file if needed
        it.palsoftware.pastiera.SettingsManager.initializeNavModeMappingsFile(this)
        ctrlKeyMap.putAll(KeyMappingLoader.loadCtrlKeyMappings(assets, this))
        variationsMap.putAll(VariationRepository.loadVariations(assets))
        
        // Load auto-correction rules
        AutoCorrector.loadCorrections(assets, this)
        
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
            } else if (key != null && (key.startsWith("auto_correct_custom_") || key == "auto_correct_enabled_languages")) {
                Log.d(TAG, "Auto-correction rules changed, reloading...")
                // Reload auto-corrections (including new custom languages)
                AutoCorrector.loadCorrections(assets, this)
            } else if (key == "nav_mode_mappings_updated") {
                Log.d(TAG, "Nav mode mappings changed, reloading...")
                // Reload nav mode key mappings
                reloadNavModeMappings()
            } else if (key == "keyboard_layout") {
                Log.d(TAG, "Keyboard layout changed, reloading...")
                // Reload keyboard layout
                loadKeyboardLayout()
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
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Remove listener when service is destroyed
        prefsListener?.let {
            prefs.unregisterOnSharedPreferenceChangeListener(it)
        }
        
        // Unregister broadcast receiver
        speechResultReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error while unregistering broadcast receiver", e)
            }
        }
        speechResultReceiver = null
        
    }

    override fun onCreateInputView(): View? {
        val layout = statusBarController.getOrCreateLayout(altSymManager.buildEmojiMapText())
        
        if (layout.parent != null) {
            (layout.parent as? android.view.ViewGroup)?.removeView(layout)
        }
        
        refreshStatusBar()
        return layout
    }

    /**
     * Creates the candidates view shown when the soft keyboard is disabled.
     * Uses a separate StatusBarController instance to provide identical functionality.
     */
    override fun onCreateCandidatesView(): View? {
        val layout = candidatesViewController.getOrCreateLayout(altSymManager.buildEmojiMapText())

        if (layout.parent != null) {
            (layout.parent as? android.view.ViewGroup)?.removeView(layout)
        }

        refreshStatusBar()
        return layout
    }

    /**
     * Determines whether the input view (soft keyboard) should be shown.
     * Always returns true to ensure the entire IME area is touchable, even in candidates mode.
     * This allows the sym board (emoji grid) to be clickable when using touch screen.
     */
    override fun onEvaluateInputViewShown(): Boolean {
        // Always return true to force Android to treat the input view as shown.
        // This makes the entire IME area touchable, not just the bottom bar.
        // Hide candidates view to avoid conflicts.
        setCandidatesViewShown(false)
        return true
    }

    /**
     * Computes the insets for the IME window.
     * This increases the "content" area to include the candidate view area,
     * allowing the application to shift upwards properly without the candidates view
     * covering system UI.
     */
    override fun onComputeInsets(outInsets: InputMethodService.Insets?) {
        super.onComputeInsets(outInsets)
        
        if (outInsets != null && !isFullscreenMode()) {
            outInsets.contentTopInsets = outInsets.visibleTopInsets
        }
    }

    /**
     * Resets all modifier key states.
     * Called when leaving a field or closing/reopening the keyboard.
     * @param preserveNavMode If true, keeps Ctrl latch active when nav mode is enabled.
     */
    private fun resetModifierStates(preserveNavMode: Boolean = false) {
        val savedCtrlLatch = if (preserveNavMode && (ctrlLatchActive || ctrlLatchFromNavMode)) {
            if (ctrlLatchActive) {
                ctrlLatchFromNavMode = true
                true
            } else if (ctrlLatchFromNavMode) {
                true
            } else {
                false
            }
        } else {
            false
        }
        
        modifierKeyHandler.resetShiftState(shiftState)
        capsLockEnabled = false
        AutoCapitalizeHelper.reset(autoCapitalizeState)
        
        if (preserveNavMode && savedCtrlLatch) {
            ctrlLatchActive = true
        } else {
            if (ctrlLatchFromNavMode || ctrlLatchActive) {
                NotificationHelper.cancelNavModeNotification(this)
            }
            modifierKeyHandler.resetCtrlState(ctrlState, preserveNavMode = false)
        }
        modifierKeyHandler.resetAltState(altState)
        
        symPage = 0
        altSymManager.resetTransientState()
        deactivateVariations()
        refreshStatusBar()
    }
    
    /**
     * Forces creation and display of the input view.
     * Called when the first physical key is pressed.
     * Shows the keyboard if there is an active text field.
     * IMPORTANT: UI is never shown in nav mode.
     */
    private fun ensureInputViewCreated() {
        if (ctrlLatchFromNavMode && !isInputViewActive) {
            return
        }
        
        if (!isInputViewActive) {
            return
        }
        
        val inputConnection = currentInputConnection
        if (inputConnection == null) {
            return
        }
        
        val layout = statusBarController.getOrCreateLayout(altSymManager.buildEmojiMapText())
        refreshStatusBar()

        if (layout.parent == null) {
            setInputView(layout)
        }

        // Only call requestShowSelf if the input view is not already shown
        // and we are in a valid state (not in nav mode, input view is active)
        if (!isInputViewShown && isInputViewActive && !ctrlLatchFromNavMode) {
            try {
                requestShowSelf(0)
            } catch (_: Exception) {
                // Silently catch exceptions to avoid crashes
            }
        }
    }
    
    
    private fun isInputConnectionAvailable(): Boolean {
        return currentInputConnection != null
    }

    private fun isNavModeActive(): Boolean {
        return ctrlLatchActive && ctrlLatchFromNavMode
    }

    private fun isNavModeKey(keyCode: Int): Boolean {
        val navModeEnabled = SettingsManager.getNavModeEnabled(this)
        val navModeActive = isNavModeActive()
        if (!navModeEnabled && !navModeActive) {
            return false
        }
        val isCtrlKey = keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT
        return isCtrlKey || navModeActive
    }

    private fun sendMappedDpadKey(mappedKeyCode: Int, event: KeyEvent?): Boolean {
        val inputConnection = currentInputConnection ?: return false
        val downTime = event?.downTime ?: SystemClock.uptimeMillis()
        val eventTime = event?.eventTime ?: downTime
        val metaState = event?.metaState ?: 0

        val downEvent = KeyEvent(
            downTime,
            eventTime,
            KeyEvent.ACTION_DOWN,
            mappedKeyCode,
            0,
            metaState
        )
        val upEvent = KeyEvent(
            downTime,
            eventTime,
            KeyEvent.ACTION_UP,
            mappedKeyCode,
            0,
            metaState
        )
        inputConnection.sendKeyEvent(downEvent)
        inputConnection.sendKeyEvent(upEvent)
        Log.d(TAG, "Nav mode: dispatched DPAD keycode $mappedKeyCode")
        return true
    }

    private fun applyNavModeResult(result: NavModeHandler.NavModeResult) {
        result.ctrlLatchActive?.let { latchActive ->
            ctrlLatchActive = latchActive
            if (latchActive) {
                ctrlLatchFromNavMode = true
                NotificationHelper.showNavModeActivatedNotification(this)
            } else if (ctrlLatchFromNavMode) {
                ctrlLatchFromNavMode = false
                NotificationHelper.cancelNavModeNotification(this)
            }
        }
        result.ctrlPhysicallyPressed?.let { ctrlPhysicallyPressed = it }
        result.ctrlPressed?.let { ctrlPressed = it }
        result.lastCtrlReleaseTime?.let { ctrlState.lastReleaseTime = it }
    }

    private fun handleNavModeKey(keyCode: Int, event: KeyEvent?, isKeyDown: Boolean): Boolean {
        val navModeEnabled = SettingsManager.getNavModeEnabled(this)
        val navModeActive = isNavModeActive()
        if (!navModeEnabled && !navModeActive) {
            return false
        }

        val isCtrlKey = keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT
        if (isCtrlKey) {
            if (isKeyDown) {
                val (shouldConsume, result) = NavModeHandler.handleCtrlKeyDown(
                    keyCode,
                    ctrlPressed,
                    ctrlLatchActive,
                    ctrlState.lastReleaseTime
                )
                applyNavModeResult(result)
                if (shouldConsume) {
                    ctrlPressed = true
                }
                return shouldConsume
            } else {
                val result = NavModeHandler.handleCtrlKeyUp()
                applyNavModeResult(result)
                ctrlPressed = false
                return true
            }
        }

        if (!isKeyDown) {
            return isNavModeActive()
        }

        if (!isNavModeActive()) {
            return false
        }

        if (!isInputConnectionAvailable()) {
            return false
        }

        val ctrlMapping = ctrlKeyMap[keyCode] ?: return false
        if (ctrlMapping.type != "keycode") {
            return false
        }

        val mappedKeyCode = when (ctrlMapping.value) {
            "DPAD_UP" -> KeyEvent.KEYCODE_DPAD_UP
            "DPAD_DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
            "DPAD_LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
            "DPAD_RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
            else -> null
        } ?: return false

        return sendMappedDpadKey(mappedKeyCode, event)
    }
    
    /**
     * Updates variations by checking the character immediately to the left of the cursor.
     * 
     * This function:
     * 1. Checks if there is an active text selection (if so, disables variations)
     * 2. Gets the character immediately before the cursor using getTextBeforeCursor(1, 0)
     *    - getTextBeforeCursor(1, 0) returns exactly 1 character to the left of the cursor
     *    - The last character of the returned string is the character immediately before the cursor
     * 3. Looks up variations for that character and updates the UI accordingly
     * 
     * Note: This always checks the character to the LEFT of the cursor (in LTR languages),
     * regardless of how the cursor was moved (keyboard, swipe pad, mouse, etc.).
     */
    private fun updateVariationsFromCursor() {
        // Disable variations for restricted fields
        if (shouldDisableSmartFeatures) {
            deactivateVariations()
            return
        }
        
        val inputConnection = currentInputConnection
        if (inputConnection == null) {
            deactivateVariations()
            return
        }
        
        // Check if there is an active selection (more than one character selected).
        // If there is a selection, completely disable variations.
        try {
            val extractedText = inputConnection.getExtractedText(
                android.view.inputmethod.ExtractedTextRequest().apply {
                    flags = android.view.inputmethod.ExtractedText.FLAG_SELECTING
                },
                0
            )
            
            if (extractedText != null) {
                val selectionStart = extractedText.selectionStart
                val selectionEnd = extractedText.selectionEnd
                
                // If there is an active selection (selectionStart != selectionEnd), disable variations
                if (selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd) {
                    // Active selection detected, disable variations
                    variationsActive = false
                    lastInsertedChar = null
                    availableVariations = emptyList()
                    return
                }
            }
        } catch (e: Exception) {
            // If there is any error while checking the selection, keep normal logic.
            Log.d(TAG, "Error while checking selection state: ${e.message}")
        }
        
        // Get the character immediately before the cursor (to the left in LTR languages)
        // getTextBeforeCursor(1, 0) returns exactly 1 character before the cursor position
        val textBeforeCursor = inputConnection.getTextBeforeCursor(1, 0)
        if (textBeforeCursor != null && textBeforeCursor.isNotEmpty()) {
            // The last character of the returned string is the character immediately before the cursor
            val charBeforeCursor = textBeforeCursor[textBeforeCursor.length - 1]
            // Check whether the character has variations
            val variations = variationsMap[charBeforeCursor]
            if (variations != null && variations.isNotEmpty()) {
                lastInsertedChar = charBeforeCursor
                availableVariations = variations
                variationsActive = true
            } else {
                // No variations available for this character
                variationsActive = false
                lastInsertedChar = null
                availableVariations = emptyList()
            }
        } else {
            // No character before the cursor (cursor is at the start of text)
            variationsActive = false
            lastInsertedChar = null
            availableVariations = emptyList()
        }
    }
    
    /**
     * Aggiorna la status bar delegando al controller dedicato.
     */
    private fun updateStatusBarText() {
        // Aggiorna le variazioni controllando il carattere prima del cursore
        updateVariationsFromCursor()
        
        val snapshot = StatusBarController.StatusSnapshot(
            capsLockEnabled = capsLockEnabled,
            shiftPhysicallyPressed = shiftPhysicallyPressed,
            shiftOneShot = shiftOneShot,
            ctrlLatchActive = ctrlLatchActive,
            ctrlPhysicallyPressed = ctrlPhysicallyPressed,
            ctrlOneShot = ctrlOneShot,
            ctrlLatchFromNavMode = ctrlLatchFromNavMode,
            altLatchActive = altLatchActive,
            altPhysicallyPressed = altPhysicallyPressed,
            altOneShot = altOneShot,
            symPage = symPage,
            variations = if (variationsActive) availableVariations else emptyList(),
            lastInsertedChar = lastInsertedChar,
            shouldDisableSmartFeatures = shouldDisableSmartFeatures
        )
        // Passa anche la mappa emoji quando SYM è attivo (solo pagina 1)
        val emojiMapText = if (symPage == 1) altSymManager.buildEmojiMapText() else ""
        // Passa le mappature SYM per la griglia emoji/caratteri
        val symMappings = when (symPage) {
            1 -> altSymManager.getSymMappings()
            2 -> altSymManager.getSymMappings2()
            else -> null
        }
        // Passa l'inputConnection per rendere i pulsanti clickabili
        val inputConnection = currentInputConnection
        statusBarController.update(snapshot, emojiMapText, inputConnection, symMappings)
        candidatesViewController.update(snapshot, emojiMapText, inputConnection, symMappings)
    }
    
    /**
     * Disattiva le variazioni.
     */
    private fun deactivateVariations() {
        variationsActive = false
        lastInsertedChar = null
        availableVariations = emptyList()
    }
    

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        
        currentPackageName = info?.packageName
        
        val (isEditable, isReallyEditable) = checkFieldEditability(info)
        isInputViewActive = isEditable
        
        isNumericField = info?.let { editorInfo ->
            val inputType = editorInfo.inputType
            val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
            inputClass == android.text.InputType.TYPE_CLASS_NUMBER
        } ?: false
        
        shouldDisableSmartFeatures = shouldDisableSmartFeatures(info)
        
        if (shouldDisableSmartFeatures) {
            setCandidatesViewShown(false)
            deactivateVariations()
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
                    NotificationHelper.cancelNavModeNotification(this)
                    ctrlLatchFromNavMode = false
                    ctrlLatchActive = false
                    resetModifierStates(preserveNavMode = false)
                }
            } else if (isEditable || !ctrlLatchFromNavMode) {
                resetModifierStates(preserveNavMode = false)
            }
        }
        
        initializeInputContext(info, restarting)
        
        if (restarting && isEditable && !shouldDisableSmartFeatures) {
            AutoCapitalizeHelper.checkAutoCapitalizeOnRestart(
                this,
                currentInputConnection,
                shouldDisableSmartFeatures,
                autoCapitalizeState,
                onUpdateStatusBar = { updateStatusBarText() }
            )
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        initializeInputContext(info, restarting)
        
        val (isEditable, _) = checkFieldEditability(info)
        
        if (restarting && isEditable && !shouldDisableSmartFeatures) {
            AutoCapitalizeHelper.checkAutoCapitalizeOnRestart(
                this,
                currentInputConnection,
                shouldDisableSmartFeatures,
                autoCapitalizeState,
                onUpdateStatusBar = { updateStatusBarText() }
            )
        }
    }
    
    override fun onFinishInput() {
        super.onFinishInput()
        isInputViewActive = false
        isNumericField = false
        resetModifierStates(preserveNavMode = true)
    }
    
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        isInputViewActive = false
        if (finishingInput) {
            resetModifierStates(preserveNavMode = true)
        }
    }
    
    override fun onWindowShown() {
        super.onWindowShown()
        updateStatusBarText()
    }
    
    override fun onWindowHidden() {
        super.onWindowHidden()
        resetModifierStates(preserveNavMode = true)
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
        
        if (!shouldDisableSmartFeatures) {
            val cursorPositionChanged = (oldSelStart != newSelStart) || (oldSelEnd != newSelEnd)
            if (cursorPositionChanged && newSelStart == newSelEnd) {
                Handler(Looper.getMainLooper()).postDelayed({
                    updateStatusBarText()
                }, CURSOR_UPDATE_DELAY)
            }
        }
        
        AutoCapitalizeHelper.checkAutoCapitalizeOnSelectionChange(
            this,
            currentInputConnection,
            shouldDisableSmartFeatures,
            autoCapitalizeState,
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            onUpdateStatusBar = { updateStatusBarText() }
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
        val ic = currentInputConnection
        val inputType = info?.inputType ?: EditorInfo.TYPE_NULL
        val hasEditableField = ic != null && inputType != EditorInfo.TYPE_NULL
        
        // If NO editable field is active, handle ONLY nav mode
        if (!hasEditableField) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (isNavModeActive()) {
                    ctrlLatchActive = false
                    ctrlLatchFromNavMode = false
                    NotificationHelper.cancelNavModeNotification(this)
                    return false
                }
                return super.onKeyDown(keyCode, event)
            }

            if (isNavModeKey(keyCode)) {
                return handleNavModeKey(keyCode, event, isKeyDown = true)
            }
            
            // Handle launcher shortcuts (only when nav mode is not active)
            if (!ctrlLatchActive && SettingsManager.getLauncherShortcutsEnabled(this)) {
                val packageName = info?.packageName ?: currentPackageName
                if (isLauncher(packageName) && isAlphabeticKey(keyCode)) {
                    if (handleLauncherShortcut(keyCode)) {
                        return true // Shortcut executed, consume the event
                    }
                }
            }
            
            // Not handled by nav mode, pass to Android and STOP
            // Do NOT execute any IME logic (no ensureInputViewCreated, no status bar, etc.)
            return super.onKeyDown(keyCode, event)
        }
        
        // If we have an editable field, nav mode must NOT be active
        // Deactivate nav mode if it was active
        if (ctrlLatchFromNavMode && ctrlLatchActive) {
            ctrlLatchActive = false
            ctrlLatchFromNavMode = false
            NotificationHelper.cancelNavModeNotification(this)
        }
        
        // Continue with normal IME logic for text fields
        val isBackKey = keyCode == KeyEvent.KEYCODE_BACK
        
        // Handle Back to hide candidates view or close keyboard
        if (isBackKey) {
            if (isInputViewActive && !onEvaluateInputViewShown()) {
                setCandidatesViewShown(false)
                return true
            }
            // Always let Back key pass through to close keyboard, even with modifiers active
            return super.onKeyDown(keyCode, event)
        }
        
        // If we have a valid InputConnection but the keyboard is hidden,
        // ensure it's shown and continue with normal processing
        if (ic != null && !isInputViewShown && isInputViewActive) {
            ensureInputViewCreated()
        }
        
        // Continue with normal IME logic
        KeyboardEventTracker.notifyKeyEvent(keyCode, event, "KEY_DOWN")
        if (!isInputViewShown && isInputViewActive) {
            ensureInputViewCreated()
        }
        
        // ========== AUTO-CORRECTION HANDLING ==========
        // Skip all auto-correction, suggestions, and text modifications for restricted fields
        val isAutoCorrectEnabled = SettingsManager.getAutoCorrectEnabled(this) && !shouldDisableSmartFeatures
        
        // HANDLE BACKSPACE TO UNDO AUTO-CORRECTION (BEFORE normal handling)
        if (isAutoCorrectEnabled && keyCode == KeyEvent.KEYCODE_DEL) {
            // Check whether there is a correction to undo
            val correction = AutoCorrector.getLastCorrection()
            
            if (correction != null) {
                // Get the text before the cursor to verify that it matches the correction
                val textBeforeCursor = ic.getTextBeforeCursor(
                    correction.correctedWord.length + 2, // +2 per sicurezza (spazio/punteggiatura)
                    0
                )
                
                if (textBeforeCursor != null && 
                    textBeforeCursor.length >= correction.correctedWord.length) {
                    
                    // Extract the last characters (they may include space/punctuation)
                    val lastChars = textBeforeCursor.substring(
                        maxOf(0, textBeforeCursor.length - correction.correctedWord.length - 1)
                    )
                    
                    // Verify that the text matches the correction.
                    // The corrected word may be followed by space or punctuation.
                    if (lastChars.endsWith(correction.correctedWord) ||
                        lastChars.trimEnd().endsWith(correction.correctedWord)) {
                        
                        // Delete the corrected word (and any trailing space/punctuation).
                        // First count how many characters must be deleted.
                        val charsToDelete = if (lastChars.endsWith(correction.correctedWord)) {
                            correction.correctedWord.length
                        } else {
                            // C'è spazio/punteggiatura dopo, cancelliamo anche quello
                            var deleteCount = correction.correctedWord.length
                            var i = textBeforeCursor.length - 1
                            while (i >= 0 && i >= textBeforeCursor.length - deleteCount - 1 &&
                                   (textBeforeCursor[i].isWhitespace() || 
                                    textBeforeCursor[i] in ".,;:!?()[]{}\"'")) {
                                deleteCount++
                                i--
                            }
                            deleteCount
                        }
                        
                        // Delete characters
                        ic.deleteSurroundingText(charsToDelete, 0)
                        
                        // Reinsert the original word
                        ic.commitText(correction.originalWord, 1)
                        
                        // Undo the correction
                        AutoCorrector.undoLastCorrection()
                        
                        Log.d(TAG, "Auto-correction undone: '${correction.correctedWord}' → '${correction.originalWord}'")
                        updateStatusBarText()
                        return true // Consume the event, do not handle backspace further
                    }
                }
            }
        }
        
        // HANDLE DOUBLE-TAP SPACE TO INSERT PERIOD AND SPACE (BEFORE auto-correction)
        // Disabled for restricted fields
        val isSpace = keyCode == KeyEvent.KEYCODE_SPACE
        if (isSpace && !shouldDisableSmartFeatures) {
            val doubleSpaceToPeriodEnabled = SettingsManager.getDoubleSpaceToPeriod(this)
            if (doubleSpaceToPeriodEnabled) {
                val currentTime = System.currentTimeMillis()
                // Check whether this is a double-tap on space (second tap within DOUBLE_TAP_THRESHOLD)
                val isDoubleTap = lastSpacePressTime > 0 && 
                                 (currentTime - lastSpacePressTime) < DOUBLE_TAP_THRESHOLD
                
                if (isDoubleTap) {
                    // When space is pressed the second time, in onKeyDown the second space has NOT yet been inserted.
                    // So we check whether there is already a space before the cursor (from the first tap).
                    val textBeforeCursor = ic.getTextBeforeCursor(100, 0)
                    if (textBeforeCursor != null && textBeforeCursor.endsWith(" ")) {
                        // Exception: if there are already multiple consecutive spaces,
                        // do not trigger replacement. Check if there is another space before.
                        if (textBeforeCursor.length >= 2 && textBeforeCursor[textBeforeCursor.length - 2] == ' ') {
                            // Multiple spaces already present, do not trigger replacement.
                            Log.d(TAG, "Double-tap space ignored: multiple spaces already present")
                            // Do not reset lastSpacePressTime here so that the next space is still a potential double-tap.
                        } else {
                            // Find the last non-space character before the space
                            var lastCharIndex = textBeforeCursor.length - 2 // Index of last character before space
                            while (lastCharIndex >= 0 && textBeforeCursor[lastCharIndex].isWhitespace()) {
                                lastCharIndex--
                            }
                            
                            // Check whether the last non-space character is alphabetical
                            if (lastCharIndex >= 0) {
                                val lastChar = textBeforeCursor[lastCharIndex]
                                if (lastChar.isLetter()) {
                                    // Last character is a letter, proceed with replacement.
                                    // There is a space from the first tap; delete it and insert ". "
                                    ic.deleteSurroundingText(1, 0)
                                    ic.commitText(". ", 1)
                                    
                                    // Enable shiftOneShot to capitalize the next letter
                                    shiftOneShot = true
                                    shiftOneShotEnabledTime = System.currentTimeMillis()
                                    updateStatusBarText()
                                    
                                    Log.d(TAG, "Double-tap space: inserted '. ' and enabled shiftOneShot (last character: '$lastChar')")
                                    
                                    // Reset to avoid triple-tap
                                    lastSpacePressTime = 0
                                    return true // Consume the event to prevent space insertion
                                } else {
                                    // Last character is not alphabetical (punctuation, number, etc.).
                                    // Do not trigger replacement, let the space be inserted normally.
                                    Log.d(TAG, "Double-tap space ignored: last character is not alphabetical ('$lastChar')")
                                    // Do not reset lastSpacePressTime here so that the next space is still a potential double-tap.
                                }
                            } else {
                                // No character found before space (empty field).
                                // Insert ". " anyway.
                                ic.deleteSurroundingText(1, 0)
                                ic.commitText(". ", 1)
                                
                                // Enable shiftOneShot to capitalize the next letter
                                shiftOneShot = true
                                shiftOneShotEnabledTime = System.currentTimeMillis()
                                updateStatusBarText()
                                
                                Log.d(TAG, "Double-tap space: inserted '. ' and enabled shiftOneShot (empty field)")
                                
                                // Reset to avoid triple-tap
                                lastSpacePressTime = 0
                                return true // Consume the event to prevent space insertion
                            }
                        }
                    } else {
                        // No space found before the cursor (edge case).
                        // This should not happen in a normal double-tap, but handle it anyway:
                        // do nothing special and let the space be inserted normally.
                        Log.d(TAG, "Double-tap space: no space found before cursor")
                    }
                }
                
                // Update timestamp of last space press
                lastSpacePressTime = currentTime
            } else {
                // If the feature is disabled, reset timestamp
                lastSpacePressTime = 0
            }
        } else {
            // If this is not space, reset timestamp after a certain time
            // (to avoid late double-tap detection)
            if (lastSpacePressTime > 0) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSpacePressTime >= DOUBLE_TAP_THRESHOLD) {
                    lastSpacePressTime = 0
                }
            }
        }

        // ========== AUTO-CAPITALIZE AFTER PERIOD ==========
        val autoCapitalizeAfterPeriodEnabled = SettingsManager.getAutoCapitalizeAfterPeriod(this) && !shouldDisableSmartFeatures
        if (autoCapitalizeAfterPeriodEnabled &&
            keyCode == KeyEvent.KEYCODE_SPACE && !shiftOneShot) {
            AutoCapitalizeHelper.enableAfterPunctuation(
                ic,
                autoCapitalizeState,
                onUpdateStatusBar = { updateStatusBarText() }
            )
        }

        // HANDLE AUTO-CORRECTION FOR SPACE AND PUNCTUATION
        if (isAutoCorrectEnabled) {
            // Controlla se è spazio o punteggiatura
            val isPunctuation = event?.unicodeChar != null &&
                               event.unicodeChar != 0 &&
                               event.unicodeChar.toChar() in ".,;:!?()[]{}\"'"
            
            if (isSpace || isPunctuation) {
                // Get text before cursor
                val textBeforeCursor = ic.getTextBeforeCursor(100, 0)
                
                // Process and obtain correction if available
                val correction = AutoCorrector.processText(textBeforeCursor, context = this)
                
                if (correction != null) {
                    val (wordToReplace, correctedWord) = correction
                    
                    // Delete the word to be corrected
                    ic.deleteSurroundingText(wordToReplace.length, 0)
                    
                    // Insert the corrected word
                    ic.commitText(correctedWord, 1)
                    
                    // Record the correction so it can be undone
                    AutoCorrector.recordCorrection(
                        originalWord = wordToReplace,
                        correctedWord = correctedWord
                    )
                    
                    // If it was space, insert it after the correction
                    if (isSpace) {
                        ic.commitText(" ", 1)
                    } else if (isPunctuation && event?.unicodeChar != null && event.unicodeChar != 0) {
                        // If it was punctuation, insert it after the correction
                        val punctChar = event.unicodeChar.toChar().toString()
                        ic.commitText(punctChar, 1)
                    }
                    
                    updateStatusBarText()
                    return true // Consume the event
                }
            }
        }
        
        // ACCEPT CORRECTION WHEN ANY OTHER KEY IS PRESSED
        // (except backspace, which is already handled above)
        if (isAutoCorrectEnabled && keyCode != KeyEvent.KEYCODE_DEL) {
            // Any key other than backspace accepts the correction.
            // This includes characters, modifiers, arrows, etc.
            AutoCorrector.acceptLastCorrection()
            
            // If a normal character (not a modifier) is typed, reset rejected words
            // because the user may have changed the text.
            if (event != null && event.unicodeChar != 0) {
                val char = event.unicodeChar.toChar()
                // Solo per caratteri alfabetici o numerici (non modificatori, frecce, ecc.)
                if (char.isLetterOrDigit()) {
                    AutoCorrector.clearRejectedWords()
                }
            }
        }
        // ========== END AUTO-CORRECTION HANDLING ==========
        
        // ========== AUTO-CAPITALIZE AFTER ENTER ==========
        if (keyCode == KeyEvent.KEYCODE_ENTER && !shouldDisableSmartFeatures) {
            AutoCapitalizeHelper.enableAfterEnter(
                this,
                ic,
                shouldDisableSmartFeatures,
                autoCapitalizeState,
                onUpdateStatusBar = { updateStatusBarText() }
            )
        }
        
        // Handle double-tap Shift to toggle Caps Lock
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (!shiftPressed) {
                val result = modifierKeyHandler.handleShiftKeyDown(keyCode, shiftState)
                // Sync shiftState.oneShot with autoCapitalizeState.shiftOneShot
                // User manually pressed Shift - sync from shiftState to autoCapitalizeState
                syncShiftOneShotFromShiftState()
                shiftPressed = true
                if (result.shouldUpdateStatusBar) {
                    updateStatusBarText()
                } else if (result.shouldRefreshStatusBar) {
                    refreshStatusBar()
                }
            }
            return super.onKeyDown(keyCode, event)
        }
        
        // Handle double-tap Ctrl to toggle Ctrl latch
        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            if (!ctrlPressed) {
                val result = modifierKeyHandler.handleCtrlKeyDown(
                    keyCode,
                    ctrlState,
                    isInputViewActive,
                    onNavModeDeactivated = {
                        NotificationHelper.cancelNavModeNotification(this)
                    }
                )
                ctrlPressed = true
                if (result.shouldConsume) {
                    if (result.shouldUpdateStatusBar) {
                        updateStatusBarText()
                    }
                    return true
                } else if (result.shouldUpdateStatusBar) {
                    updateStatusBarText()
                }
            }
            return super.onKeyDown(keyCode, event)
        }
        
        // Handle double-tap Alt to toggle Alt latch
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            if (!altPressed) {
                val result = modifierKeyHandler.handleAltKeyDown(keyCode, altState)
                altPressed = true
                if (result.shouldUpdateStatusBar) {
                    updateStatusBarText()
                }
            }
            return true  // Consume Alt event to prevent Android symbol picker popup
        }
        
        // Handle SYM key (toggle/latch with 3 states: disabled -> page1 -> page2 -> disabled)
        if (keyCode == KEYCODE_SYM) {
            // Cycle between the 3 states: 0 -> 1 -> 2 -> 0
            symPage = (symPage + 1) % 3
            // Save current symPage to SharedPreferences for potential restore
            prefs.edit().putInt("current_sym_page", symPage).apply()
            updateStatusBarText()
            // Consume the event to prevent Android from handling it
            return true
        }
        
        // Handle keycode 322 to delete the last word (swipe to delete)
        if (keyCode == 322) {
            val swipeToDeleteEnabled = SettingsManager.getSwipeToDelete(this)
                if (swipeToDeleteEnabled) {
                if (TextSelectionHelper.deleteLastWord(ic)) {
                    // Consumiamo l'evento
                    return true
                }
                    } else {
                // Feature disabled, still consume the event to avoid unwanted behavior
                return true
            }
        }
        
        // If the key is already pressed, consume the event to avoid repeats and popups
        if (altSymManager.hasPendingPress(keyCode)) {
            return true
        }
        
        // If SYM is active, check SYM mappings first (they take precedence over Alt and Ctrl)
        // When SYM is active, all other modifiers are bypassed
        if (symPage > 0) {
            // Check if auto-close is enabled
            val autoCloseEnabled = SettingsManager.getSymAutoClose(this)
            
            // Handle Back key: always close keyboard, even when SYM is active
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                // Close SYM layout first
                symPage = 0
                prefs.edit().putInt("current_sym_page", 0).apply()
                updateStatusBarText()
                // Pass Back event to close keyboard
                return super.onKeyDown(keyCode, event)
            }
            
            // Handle Enter key: close SYM and pass Enter event normally if auto-close is enabled
            if (keyCode == KeyEvent.KEYCODE_ENTER && autoCloseEnabled) {
                // Close SYM layout first
                symPage = 0
                prefs.edit().putInt("current_sym_page", 0).apply()
                updateStatusBarText()
                // Pass Enter event normally (as if SYM was never active)
                return super.onKeyDown(keyCode, event)
            }
            
            // Handle Alt key: close SYM if auto-close is enabled (but not if Ctrl or Shift are also pressed)
            // Only close if Alt is pressed alone (not with other keys)
            if ((keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) && 
                autoCloseEnabled && 
                !(event?.isCtrlPressed == true || event?.isShiftPressed == true)) {
                symPage = 0
                prefs.edit().putInt("current_sym_page", 0).apply()
                updateStatusBarText()
                // Consume Alt to prevent it from being handled as Alt+key combination
                return true
            }
            
            val symChar = when (symPage) {
                1 -> altSymManager.getSymMappings()[keyCode]
                2 -> altSymManager.getSymMappings2()[keyCode]
                else -> null
            }
            if (symChar != null) {
                // Insert emoji or character from SYM map
                ic.commitText(symChar, 1)
                
                // Auto-close SYM after inserting character if enabled and no modifiers are pressed
                if (autoCloseEnabled && 
                    !(event?.isCtrlPressed == true || event?.isShiftPressed == true || 
                      event?.isAltPressed == true || ctrlLatchActive || altLatchActive)) {
                    symPage = 0
                    prefs.edit().putInt("current_sym_page", 0).apply()
                    updateStatusBarText()
                }
                
                // Consume the event
                return true
            }
        }
        
        // If Alt is pressed or Alt latch / Alt one-shot are active, handle Alt+key combination
        // Alt has priority over Ctrl
        if (event?.isAltPressed == true || altLatchActive || altOneShot) {
            altSymManager.cancelPendingLongPress(keyCode)
            if (altOneShot) {
                altOneShot = false
                refreshStatusBar()
            }
            
            // Eccezione per Back: chiude sempre la tastiera anche con Alt premuto
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                // Lascia passare Back anche con Alt premuto per chiudere la tastiera
                return super.onKeyDown(keyCode, event)
            }
            
            // FIX: Consumiamo Alt+Spazio per evitare il popup di selezione simboli di Android
            // Questo gestisce i casi: Spazio->Alt, Alt->Spazio, Alt->tasto alfabetico->Spazio
            // Inseriamo uno spazio nel campo di testo
            if (keyCode == KeyEvent.KEYCODE_SPACE) {
                ic.commitText(" ", 1)
                updateStatusBarText()
                return true  // Consumiamo l'evento senza passarlo ad Android
            }
            
            val result = altSymManager.handleAltCombination(
                keyCode,
                ic,
                event
            ) { defaultKeyCode, defaultEvent ->
                // FIX: Anche se non c'è una mappatura Alt per questo tasto,
                // consumiamo l'evento quando Alt è attivo e il tasto è Spazio
                // per evitare comportamenti indesiderati di Android
                // Inseriamo uno spazio nel campo di testo
                if (keyCode == KeyEvent.KEYCODE_SPACE) {
                    ic.commitText(" ", 1)
                    updateStatusBarText()
                    return@handleAltCombination true
                }
                super.onKeyDown(defaultKeyCode, defaultEvent)
            }
            // If an Alt character has been inserted, update variations
            if (result) {
                updateStatusBarText()
            }
            return result
        }
        
        // Handle Ctrl+key shortcuts (checks both physical Ctrl, Ctrl latch and one-shot).
        // IMPORTANT: If we are in nav mode (ctrlLatchFromNavMode), Ctrl latch MUST NOT be disabled here.
        // BUT: in a text field, nav mode is already disabled, so treat Ctrl latch as normal.
        if (event?.isCtrlPressed == true || ctrlLatchActive || ctrlOneShot) {
            // If it was one-shot, disable it after use (but NOT when in nav mode)
            val wasOneShot = ctrlOneShot
            if (wasOneShot && !ctrlLatchFromNavMode) {
                ctrlOneShot = false
                updateStatusBarText()
            }
            // IMPORTANT: In nav mode never disable Ctrl latch after using a key;
            // Ctrl latch stays active until nav mode is exited.
            
            // Check whether a Ctrl mapping exists for this key
            val ctrlMapping = ctrlKeyMap[keyCode]
            if (ctrlMapping != null) {
                when (ctrlMapping.type) {
                    "action" -> {
                        // Handle special custom actions
                        when (ctrlMapping.value) {
                            "expand_selection_left" -> {
                                // Try to expand selection to the left.
                                // Always consume the event to avoid inserting 'W'.
                                KeyboardEventTracker.notifyKeyEvent(
                                    keyCode,
                                    event,
                                    "KEY_DOWN",
                                    outputKeyCode = null,
                                    outputKeyCodeName = "expand_selection_left"
                                )
                                TextSelectionHelper.expandSelectionLeft(ic)
                                return true
                            }
                            "expand_selection_right" -> {
                                // Try to expand selection to the right.
                                // Always consume the event to avoid inserting 'R'.
                                KeyboardEventTracker.notifyKeyEvent(
                                    keyCode,
                                    event,
                                    "KEY_DOWN",
                                    outputKeyCode = null,
                                    outputKeyCodeName = "expand_selection_right"
                                )
                                TextSelectionHelper.expandSelectionRight(ic)
                                return true
                            }
                            else -> {
                                // Execute standard context menu action
                                val actionId = when (ctrlMapping.value) {
                                    "copy" -> android.R.id.copy
                                    "paste" -> android.R.id.paste
                                    "cut" -> android.R.id.cut
                                    "undo" -> android.R.id.undo
                                    "select_all" -> android.R.id.selectAll
                                    else -> null
                                }
                                if (actionId != null) {
                                    // Notify the event with the action name
                                    KeyboardEventTracker.notifyKeyEvent(
                                        keyCode,
                                        event,
                                        "KEY_DOWN",
                                        outputKeyCode = null,
                                        outputKeyCodeName = ctrlMapping.value
                                    )
                                    ic.performContextMenuAction(actionId)
                                    return true
                                } else {
                                    // Unknown action, consume the event to avoid inserting characters
                                    return true
                                }
                            }
                        }
                    }
                    "keycode" -> {
                        // Send the mapped keycode
                        val mappedKeyCode = when (ctrlMapping.value) {
                            "DPAD_UP" -> KeyEvent.KEYCODE_DPAD_UP
                            "DPAD_DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                            "DPAD_LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                            "DPAD_RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                            "TAB" -> KeyEvent.KEYCODE_TAB
                            "PAGE_UP" -> KeyEvent.KEYCODE_PAGE_UP
                            "PAGE_DOWN" -> KeyEvent.KEYCODE_PAGE_DOWN
                            "ESCAPE" -> KeyEvent.KEYCODE_ESCAPE
                            else -> null
                        }
                        if (mappedKeyCode != null) {
                            // Notify the event with the output keycode
                            KeyboardEventTracker.notifyKeyEvent(
                                keyCode,
                                event,
                                "KEY_DOWN",
                                outputKeyCode = mappedKeyCode,
                                outputKeyCodeName = KeyboardEventTracker.getOutputKeyCodeName(mappedKeyCode)
                            )
                            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, mappedKeyCode))
                            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, mappedKeyCode))
                            
                            // Update variations after cursor movement or other operations.
                            // Use a delayed post to ensure Android has completed the operation.
                            if (mappedKeyCode in listOf(
                                KeyEvent.KEYCODE_DPAD_UP,
                                KeyEvent.KEYCODE_DPAD_DOWN,
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_DPAD_RIGHT,
                                KeyEvent.KEYCODE_PAGE_UP,
                                KeyEvent.KEYCODE_PAGE_DOWN
                            )) {
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    updateStatusBarText()
                                }, 50) // 50ms per dare tempo ad Android di aggiornare la posizione del cursore
                            }
                            
                            return true
                        } else {
                            // Unknown keycode, consume event to avoid inserting characters
                            return true
                        }
                    }
                }
            } else {
                // Ctrl is pressed but this key has no valid mapping.
                // Special handling for Backspace: delete last word or selected text.
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    // Verifica se c'è del testo selezionato
                    val extractedText = ic.getExtractedText(
                        android.view.inputmethod.ExtractedTextRequest().apply {
                            flags = android.view.inputmethod.ExtractedText.FLAG_SELECTING
                        },
                        0
                    )
                    
                    val hasSelection = extractedText?.let {
                        it.selectionStart >= 0 && it.selectionEnd >= 0 && it.selectionStart != it.selectionEnd
                    } ?: false
                    
                    if (hasSelection) {
                        // If some text is selected, delete it
                        KeyboardEventTracker.notifyKeyEvent(
                            keyCode,
                            event,
                            "KEY_DOWN",
                            outputKeyCode = null,
                            outputKeyCodeName = "delete_selection"
                        )
                        // Delete selected text using commitText with an empty string
                        ic.commitText("", 0)
                        return true
                    } else {
                        // Ctrl+Backspace deletes last word
                        KeyboardEventTracker.notifyKeyEvent(
                            keyCode,
                            event,
                            "KEY_DOWN",
                            outputKeyCode = null,
                            outputKeyCodeName = "delete_last_word"
                        )
                        TextSelectionHelper.deleteLastWord(ic)
                        return true
                    }
                }
                // Eccezione per Enter: continua a funzionare normalmente
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    // Lascia passare Enter anche con Ctrl premuto
                    return super.onKeyDown(keyCode, event)
                }
                // Eccezione per Back: chiude sempre la tastiera anche con Ctrl premuto
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    // Lascia passare Back anche con Ctrl premuto per chiudere la tastiera
                    return super.onKeyDown(keyCode, event)
                }
                // For all other keys without mappings, consume the event
                return true
            }
        }
        
        // Check whether this key has an Alt mapping or should support long press with Shift.
        // If it does, handle it with long press (even when shiftOneShot is active).
        // This avoids inserting the normal character when the user intends a long press.
        val useShift = SettingsManager.isLongPressShift(this)
        val hasLongPressSupport = if (useShift) {
            // With Shift, support long press for any letter key
            event != null && event.unicodeChar != 0 && event.unicodeChar.toChar().isLetter()
        } else {
            // With Alt, only keys with Alt mapping
            altSymManager.hasAltMapping(keyCode)
        }
        
        if (hasLongPressSupport) {
            // In numeric fields, only insert Alt character directly if using Alt modifier
            // (Shift modifier doesn't make sense for numeric fields)
            if (isNumericField && !useShift) {
                val altChar = altSymManager.getAltMappings()[keyCode]
                if (altChar != null) {
                    ic.commitText(altChar, 1)
                    // Update variations after insertion (with delay to ensure commitText is completed)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        updateStatusBarText()
                    }, 50) // 50ms per dare tempo ad Android di completare commitText e aggiornare il cursore
                    return true
                }
            }
            // Otherwise use the standard long-press handling
            val wasShiftOneShot = shiftOneShot
            // Get character from layout for conversion
            val layoutChar = getCharacterFromLayout(keyCode, event, event?.isShiftPressed == true)
            altSymManager.handleKeyWithAltMapping(
                keyCode,
                event,
                capsLockEnabled,
                ic,
                shiftOneShot,
                layoutChar
            )
            // Disable shiftOneShot after handling a key with Alt mapping,
            // so that it does not stay active for the next key.
            if (wasShiftOneShot) {
                shiftOneShot = false
                shiftOneShotEnabledTime = 0
                // Sync to shiftState
                syncShiftOneShotToShiftState()
                updateStatusBarText()
            }
            return true
        }
        
        // Handle Shift one-shot for keys without Alt mapping
        if (shiftOneShot) {
            val char = LayoutMappingRepository.getCharacterStringWithModifiers(
                keyCode,
                isShiftPressed = event?.isShiftPressed == true,
                capsLockEnabled = capsLockEnabled,
                shiftOneShot = true
            )
            if (char.isNotEmpty() && char[0].isLetter()) {
                // Disable shift one-shot (used when letter is typed)
                shiftOneShot = false
                shiftOneShotEnabledTime = 0
                // Sync to shiftState
                syncShiftOneShotToShiftState()
                ic.commitText(char, 1)
                Handler(Looper.getMainLooper()).postDelayed({
                    updateStatusBarText()
                }, CURSOR_UPDATE_DELAY)
                return true
            }
        }
        
        // When there is no mapping, handle Caps Lock for regular characters.
        // Apply Caps Lock to alphabetical characters.
        if (capsLockEnabled && LayoutMappingRepository.isMapped(keyCode)) {
            val char = LayoutMappingRepository.getCharacterStringWithModifiers(
                keyCode,
                isShiftPressed = event?.isShiftPressed == true,
                capsLockEnabled = capsLockEnabled,
                shiftOneShot = false
            )
            if (char.isNotEmpty() && char[0].isLetter()) {
                ic.commitText(char, 1)
                Handler(Looper.getMainLooper()).postDelayed({
                    updateStatusBarText()
                }, CURSOR_UPDATE_DELAY)
                return true
            }
        }
        
        // When there is no mapping, check whether the character has variations.
        // If it does, handle it ourselves so we can show variation suggestions.
        val charForVariations = if (LayoutMappingRepository.isMapped(keyCode)) {
            LayoutMappingRepository.getCharacterWithModifiers(
                keyCode,
                isShiftPressed = event?.isShiftPressed == true,
                capsLockEnabled = capsLockEnabled,
                shiftOneShot = shiftOneShot
            )
        } else {
            getCharacterFromLayout(keyCode, event, event?.isShiftPressed == true)
        }
        if (charForVariations != null) {
            // Check whether the character has variations
            if (variationsMap.containsKey(charForVariations)) {
                // Insert the character ourselves so we can show variations
                ic.commitText(charForVariations.toString(), 1)
                Handler(Looper.getMainLooper()).postDelayed({
                    updateStatusBarText()
                }, CURSOR_UPDATE_DELAY)
                return true
            }
            // If the character has no variations, previous variations remain visible
            // (display only, no action)
        }
        
        // Convert all alphabetic characters using the selected keyboard layout
        // This ensures that layout conversion works even when no special modifiers are active
        // Check if this is an alphabetic key that should be converted
        val isAlphabeticKey = isAlphabeticKey(keyCode)
        if (isAlphabeticKey && LayoutMappingRepository.isMapped(keyCode)) {
            val char = LayoutMappingRepository.getCharacterStringWithModifiers(
                keyCode,
                isShiftPressed = event?.isShiftPressed == true,
                capsLockEnabled = capsLockEnabled,
                shiftOneShot = shiftOneShot
            )
            if (char.isNotEmpty() && char[0].isLetter()) {
                // Insert the converted character
                ic.commitText(char, 1)
                
                Handler(Looper.getMainLooper()).postDelayed({
                    updateStatusBarText()
                }, CURSOR_UPDATE_DELAY)
                
                // Consume the event to prevent Android from handling it
                return true
            }
        }
        
        // When there is no mapping, let Android handle the event normally.
        // Variations will be updated automatically by onUpdateSelection() after Android completes the insertion.
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // Check if we have an editable field at the start (same logic as onKeyDown)
        val info = currentInputEditorInfo
        val ic = currentInputConnection
        val inputType = info?.inputType ?: EditorInfo.TYPE_NULL
        val hasEditableField = ic != null && inputType != EditorInfo.TYPE_NULL
        
        // If NO editable field is active, handle ONLY nav mode Ctrl release
        if (!hasEditableField) {
            if (isNavModeKey(keyCode)) {
                return handleNavModeKey(keyCode, event, isKeyDown = false)
            }
            // Not handled by nav mode, pass to Android
            return super.onKeyUp(keyCode, event)
        }
        
        // Continue with normal IME logic for text fields
        val inputConnection = currentInputConnection ?: return super.onKeyUp(keyCode, event)
        
        // Always notify the tracker (even when the event is consumed)
        KeyboardEventTracker.notifyKeyEvent(keyCode, event, "KEY_UP")
        
        // Handle Shift release for double-tap
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (shiftPressed) {
                val result = modifierKeyHandler.handleShiftKeyUp(keyCode, shiftState)
                // Sync shiftState.oneShot with autoCapitalizeState.shiftOneShot
                // Shift one-shot remains active until used (when letter is typed)
                syncShiftOneShotFromShiftState()
                shiftPressed = false
                if (result.shouldUpdateStatusBar) {
                    updateStatusBarText()
                }
            }
            return super.onKeyUp(keyCode, event)
        }
        
        // Handle Ctrl release for double-tap
        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            if (ctrlPressed) {
                val result = modifierKeyHandler.handleCtrlKeyUp(keyCode, ctrlState)
                ctrlPressed = false
                if (result.shouldUpdateStatusBar) {
                    updateStatusBarText()
                }
            }
            return super.onKeyUp(keyCode, event)
        }
        
        // Handle Alt release for double-tap
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            if (altPressed) {
                val result = modifierKeyHandler.handleAltKeyUp(keyCode, altState)
                altPressed = false
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
        
        if (altSymManager.handleKeyUp(keyCode, symPage > 0, shiftPressed)) {
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
     * Intercepts trackpad/touch-sensitive keyboard motion events.
     * The Unihertz Titan 2 keyboard can act as a trackpad, sending MotionEvents
     * for scrolling, cursor movement, and gestures.
     */
    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event == null) {
            return super.onGenericMotionEvent(event)
        }
        
        // Check if this is a trackpad/touch event from the keyboard
        val source = event.source
        val isFromTrackpad = (source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                            (source and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD
        
        // Also check if it's from a keyboard device (touch-sensitive keyboard)
        val device = event.device
        val isFromKeyboard = device != null && 
                            ((source and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD ||
                             device.name?.contains("keyboard", ignoreCase = true) == true ||
                             device.name?.contains("titan", ignoreCase = true) == true)
        
        if (isFromTrackpad || isFromKeyboard) {
            Log.d(TAG, "Motion event intercepted - Action: ${MotionEventTracker.getActionName(event.action)}, " +
                    "Source: ${MotionEventTracker.getSourceName(source)}, " +
                    "Device: ${device?.name}, " +
                    "X: ${event.x}, Y: ${event.y}, " +
                    "ScrollX: ${event.getAxisValue(MotionEvent.AXIS_HSCROLL)}, " +
                    "ScrollY: ${event.getAxisValue(MotionEvent.AXIS_VSCROLL)}")
            
            // Notify the tracker for debug display
            MotionEventTracker.notifyMotionEvent(event)
            
            // Handle different motion event types
            when (event.action) {
                MotionEvent.ACTION_SCROLL -> {
                    val scrollX = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
                    val scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                    Log.d(TAG, "Trackpad scroll detected - X: $scrollX, Y: $scrollY")
                    
                    // You can handle scroll events here
                    // For example, convert to cursor movement or scroll actions
                    // return true to consume the event, false to pass it to Android
                }
                MotionEvent.ACTION_MOVE -> {
                    Log.d(TAG, "Trackpad move detected - X: ${event.x}, Y: ${event.y}")
                    // Handle cursor movement
                }
                MotionEvent.ACTION_DOWN -> {
                    Log.d(TAG, "Trackpad touch down detected - X: ${event.x}, Y: ${event.y}")
                    // Handle touch down (click)
                }
                MotionEvent.ACTION_UP -> {
                    Log.d(TAG, "Trackpad touch up detected")
                    // Handle touch up (release)
                }
            }
            
            // Return true to consume the event, false to let Android handle it
            // For now, we'll let Android handle it but log everything for debugging
            return false
        }
        
        return super.onGenericMotionEvent(event)
    }
}

