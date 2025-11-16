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
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.inputmethod.KeyboardEventTracker
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.view.MotionEvent
import android.view.InputDevice
import it.palsoftware.pastiera.inputmethod.MotionEventTracker

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
    
    // Caps Lock state
    private var capsLockEnabled = false
    
    // Double-tap tracking on Shift to enable Caps Lock
    private var lastShiftReleaseTime: Long = 0
    private var shiftPressed = false
    
    // Latch states for Ctrl and Alt
    private var ctrlLatchActive = false
    private var altLatchActive = false
    
    // Double-tap tracking on Ctrl and Alt
    private var lastCtrlReleaseTime: Long = 0
    private var ctrlPressed = false
    private var lastAltReleaseTime: Long = 0
    private var altPressed = false
    
    // Tracking of physically pressed modifier keys (for the status bar)
    private var shiftPhysicallyPressed = false
    private var ctrlPhysicallyPressed = false
    private var altPhysicallyPressed = false
    
    // One-shot states for modifier keys (active until the next key)
    private var shiftOneShot = false
    private var ctrlOneShot = false
    private var altOneShot = false
    
    private val DOUBLE_TAP_THRESHOLD = 500L // milliseconds
    
    // Double-tap tracking on space to insert period and space
    private var lastSpacePressTime: Long = 0
    
    // Flag to track whether we are in a valid input context
    private var isInputViewActive = false
    
    // Flag to track whether we are in a numeric field
    private var isNumericField = false
    
    // Flag to avoid infinite loops when reactivating the keyboard recursively
    private var isRehandlingKeyAfterReactivation = false
    
    // Cache for launcher packages
    private var cachedLauncherPackages: Set<String>? = null
    
    // Current package name
    private var currentPackageName: String? = null

    private fun refreshStatusBar() {
        updateStatusBarText()
    }
    
    /**
     * Reloads nav mode key mappings from the file.
     */
    private fun loadKeyboardLayout() {
        val layoutName = SettingsManager.getKeyboardLayout(this)
        val layout = KeyboardLayoutManager.loadLayout(assets, layoutName)
        KeyboardLayoutManager.setLayout(layout)
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
        val layoutChar = KeyboardLayoutManager.getCharacter(keyCode, isShift)
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
        Log.d(TAG, "onCreate() called")
        prefs = getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
        
        // Create notification channel for Android 8.0+
        NotificationHelper.createNotificationChannel(this)
        
        statusBarController = StatusBarController(this)
        // Register listener for variation selection
        statusBarController.onVariationSelectedListener = object : VariationButtonHandler.OnVariationSelectedListener {
            override fun onVariationSelected(variation: String) {
                // Update variations after one has been selected (refresh view if needed)
                updateStatusBarText()
            }
        }
        // Register listener for cursor movement (to update variations when cursor moves via swipe pad)
        statusBarController.onCursorMovedListener = {
            updateStatusBarText()
        }
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
        variationsMap.putAll(KeyMappingLoader.loadVariations(assets))
        
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
        Log.d(TAG, "onCreate() completed")
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
        
        Log.d(TAG, "onDestroy() called")
    }

    override fun onCreateInputView(): View? {
        Log.d(TAG, "onCreateInputView() called")
        val layout = statusBarController.getOrCreateLayout(altSymManager.buildEmojiMapText())
        
        // If the view already has a parent, remove it before returning.
        // This avoids "The specified child already has a parent" crash.
        if (layout.parent != null) {
            Log.d(TAG, "onCreateInputView() - removing view from existing parent")
            (layout.parent as? android.view.ViewGroup)?.removeView(layout)
        }
        
        refreshStatusBar()
        Log.d(TAG, "onCreateInputView() completed - view created: ${layout != null}, parent: ${layout.parent}")
        return layout
    }

    // Flag per tracciare se Ctrl latch è stato attivato nel nav mode (anche quando si entra in un campo di testo)
    private var ctrlLatchFromNavMode = false
    
    /**
     * Resets all modifier key states.
     * Called when leaving a field or closing/reopening the keyboard.
     * @param preserveNavMode If true, keeps Ctrl latch active when nav mode is enabled.
     */
    private fun resetModifierStates(preserveNavMode: Boolean = false) {
        Log.d(TAG, "resetModifierStates() called - resetting all modifier states, preserveNavMode: $preserveNavMode, ctrlLatchActive: $ctrlLatchActive, ctrlLatchFromNavMode: $ctrlLatchFromNavMode")
        
        // Save nav mode state if needed.
        // If Ctrl latch is active and comes from nav mode, preserve it.
        val savedCtrlLatch = if (preserveNavMode && (ctrlLatchActive || ctrlLatchFromNavMode)) {
            if (ctrlLatchActive) {
                ctrlLatchFromNavMode = true // Mark that Ctrl latch comes from nav mode
                true
            } else if (ctrlLatchFromNavMode) {
                true // Keep active if it was already marked as nav mode
            } else {
                false
            }
        } else {
            false
        }
        
        // Reset Caps Lock
        capsLockEnabled = false
        
        // Reset one-shot states
        shiftOneShot = false
        ctrlOneShot = false
        altOneShot = false
        
        // Reset latch states (but keep Ctrl latch in nav mode if requested)
        if (preserveNavMode && savedCtrlLatch) {
            // Keep Ctrl latch active in nav mode
            ctrlLatchActive = true
            Log.d(TAG, "resetModifierStates() - preserved Ctrl latch in nav mode")
        } else {
            // If nav mode was active, cancel it and remove the notification
            if (ctrlLatchFromNavMode || ctrlLatchActive) {
                Log.d(TAG, "resetModifierStates() - nav mode deactivated, cancelling notification")
                NotificationHelper.cancelNavModeNotification(this)
            }
            ctrlLatchActive = false
            ctrlLatchFromNavMode = false // Also reset nav mode flag
        }
        altLatchActive = false
        
        // Reset SYM
        symPage = 0
        
        // Reset physical states
        shiftPressed = false
        ctrlPressed = false
        altPressed = false
        
        // Reset physically pressed states (for status bar)
        shiftPhysicallyPressed = false
        ctrlPhysicallyPressed = false
        altPhysicallyPressed = false
        
        // Reset release times
        lastShiftReleaseTime = 0
        lastCtrlReleaseTime = 0
        lastAltReleaseTime = 0
        
        // Reset Alt/SYM transient state
        altSymManager.resetTransientState()
        
        // Reset variations
        deactivateVariations()
        
        // Update status bar
        refreshStatusBar()
    }
    
    /**
     * Forces creation and display of the input view.
     * Called when the first physical key is pressed.
     * Shows the keyboard if there is an active text field.
     * IMPORTANT: UI is never shown in nav mode.
     */
    private fun ensureInputViewCreated() {
        Log.d(TAG, "ensureInputViewCreated() called - isInputViewActive: $isInputViewActive, ctrlLatchActive: $ctrlLatchActive, ctrlLatchFromNavMode: $ctrlLatchFromNavMode")
        
        // In nav mode we never show the UI
        if (ctrlLatchFromNavMode && !isInputViewActive) {
            Log.d(TAG, "ensureInputViewCreated() - nav mode active without text field, not showing keyboard")
            return
        }
        
        // Show keyboard only if there is an active text field
        if (!isInputViewActive) {
            Log.d(TAG, "ensureInputViewCreated() - not in a valid input context, not showing keyboard")
            return
        }
        
        // Verify that we have a valid input connection (active text field)
        val inputConnection = currentInputConnection
        if (inputConnection == null) {
            Log.d(TAG, "ensureInputViewCreated() - no inputConnection, not showing keyboard")
            return
        }
        
        val layout = statusBarController.getOrCreateLayout(altSymManager.buildEmojiMapText())
        refreshStatusBar()

        if (layout.parent == null) {
            Log.d(TAG, "ensureInputViewCreated() - setInputView() on new layout")
            setInputView(layout)
        }

        Log.d(TAG, "ensureInputViewCreated() - requestShowSelf()")
        requestShowSelf(0)
    }
    
    
    /**
     * Gestisce i tasti quando Ctrl latch è attivo nel nav mode (senza campo di testo).
     * Permette di usare le combinazioni Ctrl+tasto anche quando non c'è un campo di testo attivo.
     * IMPORTANTE: In nav mode l'UI non viene mai mostrata.
     */
    private fun handleCtrlKeyInNavMode(keyCode: Int, event: KeyEvent?): Boolean {
        // Notifica l'evento al tracker
        KeyboardEventTracker.notifyKeyEvent(keyCode, event, "KEY_DOWN")
        
        // NON mostriamo l'UI in nav mode
        
        // Gestisci gli shortcut Ctrl+tasto (solo keycode direzionali e azioni, non inserimento testo)
        val ctrlMapping = ctrlKeyMap[keyCode]
        if (ctrlMapping != null) {
            when (ctrlMapping.type) {
                "action" -> {
                    // Nel nav mode, le azioni come copy/paste non hanno senso senza campo di testo
                    // Ma possiamo gestire expand_selection se necessario
                    when (ctrlMapping.value) {
                        "expand_selection_left", "expand_selection_right" -> {
                            // Queste azioni richiedono un input connection, quindi non funzionano nel nav mode
                            Log.d(TAG, "Nav mode: azione $ctrlMapping.value richiede input connection")
                        }
                        else -> {
                            Log.d(TAG, "Nav mode: azione $ctrlMapping.value non supportata senza campo di testo")
                        }
                    }
                    // Consumiamo l'evento per evitare che Android lo gestisca
                    return true
                }
                "keycode" -> {
                    // Invia il keycode direzionale usando lo stesso metodo che usiamo quando siamo in un campo di testo
                    val dpadKeyCode = when (ctrlMapping.value) {
                        "DPAD_UP" -> KeyEvent.KEYCODE_DPAD_UP
                        "DPAD_DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                        "DPAD_LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                        "DPAD_RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                        else -> null
                    }
                    if (dpadKeyCode != null) {
                        // Usa lo stesso metodo che funziona quando siamo in un campo di testo
                        val inputConnection = currentInputConnection
                        if (inputConnection != null) {
                            // Usa esattamente lo stesso metodo che funziona in campo di testo
                            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, dpadKeyCode))
                            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, dpadKeyCode))
                            Log.d(TAG, "Nav mode: inviato keycode $dpadKeyCode tramite inputConnection.sendKeyEvent (stesso metodo usato in campo di testo)")
                            
                            // Aggiorna le variazioni dopo il movimento del cursore
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                updateStatusBarText()
                            }, 50) // 50ms per dare tempo ad Android di aggiornare la posizione del cursore
                            
                            return true
                        } else {
                            Log.w(TAG, "Nav mode: nessun inputConnection disponibile per inviare keycode $dpadKeyCode")
                            // Consumiamo comunque l'evento per evitare che venga processato
                            return true
                        }
                    }
                }
            }
        }
        
        // Se non c'è mappatura, passa l'evento ad Android
        return super.onKeyDown(keyCode, event)
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
                    Log.d(TAG, "Active selection detected (start: $selectionStart, end: $selectionEnd), variations disabled")
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
                Log.d(TAG, "Variations updated for character before cursor '$charBeforeCursor': $variations")
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
            lastInsertedChar = lastInsertedChar
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
        
        // Traccia il package corrente
        currentPackageName = info?.packageName
        Log.d(TAG, "onStartInput() called - restarting: $restarting, info: ${info?.packageName}, inputType: ${info?.inputType}, ctrlLatchActive: $ctrlLatchActive")
        
        // Check whether the field is actually editable
        val isEditable = info?.let { editorInfo ->
            val inputType = editorInfo.inputType
            // Check whether this is an editable text field (not TYPE_NULL)
            val isTextInput = inputType and android.text.InputType.TYPE_MASK_CLASS != android.text.InputType.TYPE_NULL
            // Exclude non-editable fields such as lists
            val isNotNoInput = inputType and android.text.InputType.TYPE_MASK_CLASS != 0
            isTextInput && isNotNoInput
        } ?: false
        
        Log.d(TAG, "onStartInput() - isEditable: $isEditable")
        
        // Mark that we are in a valid input context only if the field is editable
        isInputViewActive = isEditable
        
        // Check whether the field is numeric
        isNumericField = info?.let { editorInfo ->
            val inputType = editorInfo.inputType
            val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
            inputClass == android.text.InputType.TYPE_CLASS_NUMBER
        } ?: false
        Log.d(TAG, "onStartInput() - isNumericField: $isNumericField")
        
        // Disable suggestions to avoid popup
        if (info != null && isEditable) {
            info.inputType = info.inputType or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        
        // Automatically show keyboard when an editable field gains focus (if enabled in settings)
        if (isEditable && !restarting) {
            val autoShowKeyboardEnabled = SettingsManager.getAutoShowKeyboard(this)
            if (autoShowKeyboardEnabled) {
                // Check whether the field is REALLY editable (not just apparently editable)
                val isReallyEditable = info?.let { editorInfo ->
                    val inputType = editorInfo.inputType
                    val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
                    // Check that this is a truly editable input type
                    inputClass == android.text.InputType.TYPE_CLASS_TEXT ||
                    inputClass == android.text.InputType.TYPE_CLASS_NUMBER ||
                    inputClass == android.text.InputType.TYPE_CLASS_PHONE ||
                    inputClass == android.text.InputType.TYPE_CLASS_DATETIME
                } ?: false
                
                if (isReallyEditable) {
                    Log.d(TAG, "onStartInput() - editable field detected, auto-show keyboard (auto_show_keyboard enabled)")
                    ensureInputViewCreated()
                }
            }
        }
        
        // Reset modifier states when entering a new field (only if this is not a restart).
        // IMPORTANT: disable nav mode ONLY when entering an editable text field.
        // Do not disable it when moving to other UI elements (icons, lists, etc.).
        if (!restarting) {
            // If we are in nav mode, ALWAYS preserve it unless we enter a truly editable field.
            if (ctrlLatchFromNavMode && ctrlLatchActive) {
                // Check whether the field is REALLY editable (not just apparently editable)
                val isReallyEditable = isEditable && info?.let { editorInfo ->
                    val inputType = editorInfo.inputType
                    val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
                    // Check that this is a truly editable input type
                    inputClass == android.text.InputType.TYPE_CLASS_TEXT ||
                    inputClass == android.text.InputType.TYPE_CLASS_NUMBER ||
                    inputClass == android.text.InputType.TYPE_CLASS_PHONE ||
                    inputClass == android.text.InputType.TYPE_CLASS_DATETIME
                } ?: false
                
                // Also check if we have a valid input connection
                val inputConnection = currentInputConnection
                val hasValidInputConnection = inputConnection != null
                
                if (isReallyEditable && hasValidInputConnection) {
                    // Disable nav mode ONLY when entering a truly editable text field
                    if (ctrlLatchFromNavMode || ctrlLatchActive) {
                        Log.d(TAG, "onStartInput() - nav mode disabled because we entered a truly editable text field")
                        NotificationHelper.cancelNavModeNotification(this)
                    }
                    ctrlLatchFromNavMode = false
                    ctrlLatchActive = false
                    resetModifierStates(preserveNavMode = false)
                } else {
                    // Non è un campo realmente editabile o non c'è input connection - mantieni il nav mode
                    Log.d(TAG, "onStartInput() - nav mode attivo, campo non realmente editabile (isReallyEditable: $isReallyEditable, hasInputConnection: $hasValidInputConnection), mantengo nav mode")
                    // Non resettare gli stati modificatori, preserva il nav mode
                }
            } else if (isEditable) {
                // Non siamo in nav mode ma siamo in un campo editabile - reset normale
                resetModifierStates(preserveNavMode = false)
            } else if (!ctrlLatchFromNavMode) {
                // Non siamo in nav mode e non siamo in un campo editabile - reset normale
                resetModifierStates(preserveNavMode = false)
            }
            // Se siamo in nav mode e non siamo in un campo editabile, non fare nulla (mantieni nav mode)
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "onStartInputView() called - restarting: $restarting, ctrlLatchFromNavMode: $ctrlLatchFromNavMode")
        
        // Check whether the field is actually editable
        val isEditable = info?.let { editorInfo ->
            val inputType = editorInfo.inputType
            val isTextInput = inputType and android.text.InputType.TYPE_MASK_CLASS != android.text.InputType.TYPE_NULL
            val isNotNoInput = inputType and android.text.InputType.TYPE_MASK_CLASS != 0
            isTextInput && isNotNoInput
        } ?: false
        
        // Mark that we are in a valid input context only if the field is editable.
        // BUT: if we are in nav mode, do not set isInputViewActive to true when there is no editable field.
        if (isEditable) {
            // Check whether the field is REALLY editable (not just apparently editable)
            val isReallyEditable = info?.let { editorInfo ->
                val inputType = editorInfo.inputType
                val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
                // Check that this is a truly editable input type
                inputClass == android.text.InputType.TYPE_CLASS_TEXT ||
                inputClass == android.text.InputType.TYPE_CLASS_NUMBER ||
                inputClass == android.text.InputType.TYPE_CLASS_PHONE ||
                inputClass == android.text.InputType.TYPE_CLASS_DATETIME
            } ?: false
            
            if (isReallyEditable) {
                isInputViewActive = true
                // Check whether the field is numeric
                isNumericField = info?.let { editorInfo ->
                    val inputType = editorInfo.inputType
                    val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
                    inputClass == android.text.InputType.TYPE_CLASS_NUMBER
                } ?: false
                Log.d(TAG, "onStartInputView() - isNumericField: $isNumericField")
                // If we are in nav mode and enter a truly editable field, disable nav mode
                if (ctrlLatchFromNavMode && ctrlLatchActive) {
                    val inputConnection = currentInputConnection
                    if (inputConnection != null) {
                        ctrlLatchFromNavMode = false
                        ctrlLatchActive = false
                        Log.d(TAG, "onStartInputView() - nav mode disabled because we entered a truly editable text field")
                        // Cancel notification when nav mode is disabled
                        NotificationHelper.cancelNavModeNotification(this)
                    }
                }
                
                // Handle auto-capitalize: enable shiftOneShot when the field is empty.
                // BUT: disable it for password fields even if auto-capitalize is enabled.
                val autoCapitalizeEnabled = SettingsManager.getAutoCapitalizeFirstLetter(this)
                if (autoCapitalizeEnabled) {
                    // Check whether the field is a password field
                    val isPasswordField = info?.let { editorInfo ->
                        val inputType = editorInfo.inputType
                        val inputVariation = inputType and android.text.InputType.TYPE_MASK_VARIATION
                        inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                        inputVariation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                    } ?: false
                    
                    // Do not apply auto-capitalize to password fields
                    if (!isPasswordField) {
                        val inputConnection = currentInputConnection
                        if (inputConnection != null) {
                            // Check whether the field is empty
                            val textBeforeCursor = inputConnection.getTextBeforeCursor(1000, 0)
                            val textAfterCursor = inputConnection.getTextAfterCursor(1000, 0)
                            val isFieldEmpty = (textBeforeCursor == null || textBeforeCursor.trim().isEmpty()) &&
                                              (textAfterCursor == null || textAfterCursor.trim().isEmpty())
                            
                            if (isFieldEmpty) {
                                // Enable shiftOneShot for the first letter
                                shiftOneShot = true
                                Log.d(TAG, "Auto-capitalize: empty field detected, shiftOneShot enabled")
                                updateStatusBarText() // Update to show "shift"
                            }
                        }
                    } else {
                        Log.d(TAG, "Auto-capitalize: password field detected, auto-capitalize disabled")
                    }
                }
            } else {
                // Not a truly editable field - if we are in nav mode, keep it
                if (ctrlLatchFromNavMode && ctrlLatchActive) {
                    Log.d(TAG, "onStartInputView() - nav mode active, field not truly editable, keeping nav mode")
                    isInputViewActive = false
                } else {
                    isInputViewActive = false
                }
            }
        } else if (!ctrlLatchFromNavMode) {
            // Not in nav mode and there is no editable field
            isInputViewActive = false
        } else {
            // We are in nav mode and there is no editable field - keep isInputViewActive = false
            isInputViewActive = false
        }
        // Reload Alt manager related settings
        altSymManager.reloadLongPressThreshold()
        altSymManager.resetTransientState()
    }
    
    override fun onFinishInput() {
        super.onFinishInput()
        Log.d(TAG, "onFinishInput() called - resetting modifier states")
        // Mark that we are no longer in a valid input context
        isInputViewActive = false
        isNumericField = false
        // Reset modifier states when leaving a field.
        // Preserve Ctrl latch when nav mode is active.
        resetModifierStates(preserveNavMode = true)
    }
    
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        Log.d(TAG, "onFinishInputView() called - finishingInput: $finishingInput, ctrlLatchFromNavMode: $ctrlLatchFromNavMode, ctrlLatchActive: $ctrlLatchActive")
        // Mark that we are no longer in a valid input context
        isInputViewActive = false
        // Reset modifier states when the view is hidden.
        // IMPORTANT: also preserve nav mode here, otherwise it is reset while navigating.
        if (finishingInput) {
            resetModifierStates(preserveNavMode = true)
        }
    }
    
    override fun onWindowShown() {
        super.onWindowShown()
        Log.d(TAG, "onWindowShown() called - window is visible")
        // Update status text when the window is shown
        updateStatusBarText()
    }
    
    override fun onWindowHidden() {
        super.onWindowHidden()
        Log.d(TAG, "onWindowHidden() called - window is hidden, resetting modifier states")
        // Reset modifier states when the keyboard is hidden.
        // Preserve Ctrl latch when nav mode is active.
        resetModifierStates(preserveNavMode = true)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        // Handle long press even when the keyboard is hidden but we still have a valid InputConnection.
        val inputConnection = currentInputConnection
        if (inputConnection == null) {
            return super.onKeyLongPress(keyCode, event)
        }
        
        // If the keyboard is hidden but we have an InputConnection, reactivate it
        if (!isInputViewActive) {
            Log.d(TAG, "Long press detected with keyboard hidden - reactivating immediately")
            ensureInputViewCreated()
            isInputViewActive = true
        }
        
        // Intercept long presses BEFORE Android handles them
        if (altSymManager.hasAltMapping(keyCode)) {
            // Consumiamo l'evento per evitare il popup di Android
            return true
        }
        
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Exception for nav mode: handle Ctrl even when we are not in a text field
        val isCtrlKey = keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT
        val isBackKey = keyCode == KeyEvent.KEYCODE_BACK
        
        // Handle Back to exit nav mode
        if (isBackKey && ctrlLatchFromNavMode && ctrlLatchActive && !isInputViewActive) {
            // We are in nav mode and Back is pressed - disable nav mode
            ctrlLatchActive = false
            ctrlLatchFromNavMode = false
            Log.d(TAG, "Nav mode disabled via Back")
            // Cancel notification when nav mode is disabled
            NotificationHelper.cancelNavModeNotification(this)
            // No need to hide the keyboard because UI is never shown in nav mode.
            // Do not consume Back; let Android handle it.
            return super.onKeyDown(keyCode, event)
        }
        
        // If we are not in a valid input context, handle Ctrl for nav mode
        // and also other keys when Ctrl latch is active (nav mode active).
        // When re-handling events after reactivation, skip this check.
        if (!isInputViewActive && !isRehandlingKeyAfterReactivation) {
            // Gestisci le scorciatoie del launcher quando non siamo in un campo di testo
            // MA: bypassa questa logica se siamo in nav mode (ctrlLatchActive) o se le scorciatoie sono disabilitate
            // ATTENZIONE: abilita le scorciatoie SOLO per i tasti alfabetici (A-Z), non per modificatori, volume, ecc.
            if (!ctrlLatchActive && SettingsManager.getLauncherShortcutsEnabled(this)) {
                val packageName = currentInputEditorInfo?.packageName ?: currentPackageName
                if (isLauncher(packageName) && isAlphabeticKey(keyCode)) {
                    // Siamo nel launcher, non in un campo di testo, e il tasto è alfabetico - controlla se c'è una scorciatoia
                    if (handleLauncherShortcut(keyCode)) {
                        return true // Scorciatoia eseguita, consumiamo l'evento
                    }
                }
            }
            
            // Check if we have a valid InputConnection (we are on a text field)
            // but the keyboard is hidden (for example after pressing Back)
            val inputConnection = currentInputConnection
            
            // Handle Ctrl for nav mode FIRST, even if there is no InputConnection
            if (isCtrlKey) {
                // Check if nav mode is enabled
                if (!it.palsoftware.pastiera.SettingsManager.getNavModeEnabled(this)) {
                    // Nav mode is disabled, do nothing
                    return super.onKeyDown(keyCode, event)
                }
                
                // Handle nav mode: double-tap on Ctrl to toggle Ctrl latch
                val (shouldConsume, result) = NavModeHandler.handleCtrlKeyDown(
                    keyCode,
                    ctrlPressed,
                    ctrlLatchActive,
                    lastCtrlReleaseTime
                )
                
                // IMPORTANT: apply ctrlLatchActive and ctrlLatchFromNavMode
                // BEFORE calling ensureInputViewCreated(), so that when
                // onStartInput() or onStartInputView() are executed, flags are already set.
                result.ctrlLatchActive?.let { 
                    ctrlLatchActive = it
                    // If it is activated in nav mode, mark the flag first
                    if (it) {
                        ctrlLatchFromNavMode = true
                        Log.d(TAG, "Nav mode: Ctrl latch activated, ctrlLatchFromNavMode = true (set BEFORE ensureInputViewCreated)")
                        // Show notification when nav mode is activated
                        NotificationHelper.showNavModeActivatedNotification(this)
                    } else {
                        ctrlLatchFromNavMode = false
                        Log.d(TAG, "Nav mode: Ctrl latch deactivated, ctrlLatchFromNavMode = false")
                        // Cancel notification when nav mode is deactivated
                        NotificationHelper.cancelNavModeNotification(this)
                    }
                }
                result.ctrlPhysicallyPressed?.let { ctrlPhysicallyPressed = it }
                result.lastCtrlReleaseTime?.let { lastCtrlReleaseTime = it }
                
                // In nav mode UI is never shown, so ignore shouldShowKeyboard.
                // If we are not in nav mode (isInputViewActive is true), show the keyboard normally.
                if (result.shouldShowKeyboard && isInputViewActive) {
                    // Update status bar BEFORE showing the keyboard
                    updateStatusBarText()
                    // Now that flags are set, show the keyboard
                    ensureInputViewCreated()
                }
                // No need to handle shouldHideKeyboard because UI is never shown in nav mode
                
                if (shouldConsume) {
                    ctrlPressed = true
                    return true
                }
                // If we don't consume, pass the event to Android
                return super.onKeyDown(keyCode, event)
            }
            
            // If we have a valid InputConnection but the keyboard is hidden
            // and we are pressing a regular key (not a modifier, not Back),
            // automatically reactivate the keyboard.
            if (inputConnection != null && !isCtrlKey && !isBackKey && !ctrlLatchFromNavMode) {
                // Ensure this is a key that should be handled by the IME
                // (exclude system keys such as HOME, MENU, etc.)
                val isRegularKey = (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) ||
                                  (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) ||
                                  keyCode == KeyEvent.KEYCODE_SPACE ||
                                  keyCode == KeyEvent.KEYCODE_ENTER ||
                                  keyCode == KeyEvent.KEYCODE_DEL ||
                                  keyCode == KeyEvent.KEYCODE_COMMA ||
                                  keyCode == KeyEvent.KEYCODE_PERIOD ||
                                  keyCode == KeyEvent.KEYCODE_SLASH ||
                                  keyCode == KeyEvent.KEYCODE_SEMICOLON ||
                                  keyCode == KeyEvent.KEYCODE_APOSTROPHE ||
                                  keyCode == KeyEvent.KEYCODE_MINUS ||
                                  keyCode == KeyEvent.KEYCODE_EQUALS ||
                                  keyCode == KeyEvent.KEYCODE_LEFT_BRACKET ||
                                  keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET
                
                if (isRegularKey) {
                    Log.d(TAG, "Regular key ($keyCode) detected with keyboard hidden but valid InputConnection - reactivating immediately")
                    // Force keyboard recreation immediately
                    ensureInputViewCreated()
                    // Set isInputViewActive to true to allow normal key handling
                    isInputViewActive = true
                    
                    // Consume the event to prevent Android from handling it,
                    // then call onKeyDown recursively after a short delay to let reactivation complete.
                    val savedEvent = event
                    val savedKeyCode = keyCode
                    // Use post instead of postDelayed to execute as soon as possible
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        // After the keyboard is reactivated, call onKeyDown recursively.
                        // This allows Pastiera's full logic to handle the key.
                        if (savedEvent != null) {
                            // Small delay to ensure the keyboard is fully initialized
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                // Set flag to avoid infinite loops
                                isRehandlingKeyAfterReactivation = true
                                // Call onKeyDown recursively with the saved event
                                onKeyDown(savedKeyCode, savedEvent)
                                // Reset flag after handling
                                isRehandlingKeyAfterReactivation = false
                                Log.d(TAG, "KeyEvent re-handled after keyboard reactivation (keyCode: $savedKeyCode)")
                            }, 50) // Delay ridotto a 50ms per una risposta più rapida
                        }
                    }
                    
                    // Consume the original event to prevent Android from handling it
                    return true
                } else {
                    // Not a regular key; continue with the normal flow
                    if (ctrlLatchActive) {
                        // If Ctrl latch is active in nav mode, handle keys even without a text field.
                        // This allows Ctrl+key combinations in nav mode.
                        return handleCtrlKeyInNavMode(keyCode, event)
                    }
                    Log.d(TAG, "onKeyDown() - not in a valid input context, passing event to Android")
                    return super.onKeyDown(keyCode, event)
                }
            } else if (ctrlLatchActive) {
                // If Ctrl latch is active in nav mode, handle keys even without a text field.
                // This allows Ctrl+key combinations in nav mode.
                return handleCtrlKeyInNavMode(keyCode, event)
            }
            Log.d(TAG, "onKeyDown() - not in a valid input context, passing event to Android")
            return super.onKeyDown(keyCode, event)
        }
        
        val inputConnection = currentInputConnection ?: return super.onKeyDown(keyCode, event)
        
        Log.d(TAG, "onKeyDown() - keyCode: $keyCode, inputConnection: ${inputConnection != null}")
        
        // Always notify the tracker (even when the event is consumed)
        KeyboardEventTracker.notifyKeyEvent(keyCode, event, "KEY_DOWN")
        
        // Force view creation on the first key press (including modifiers).
        // This ensures the status bar is visible even if the virtual keyboard is disabled.
        Log.d(TAG, "onKeyDown() - calling ensureInputViewCreated() to activate keyboard")
        ensureInputViewCreated()
        
        // ========== AUTO-CORRECTION HANDLING ==========
        val isAutoCorrectEnabled = SettingsManager.getAutoCorrectEnabled(this)
        
        // HANDLE BACKSPACE TO UNDO AUTO-CORRECTION (BEFORE normal handling)
        if (isAutoCorrectEnabled && keyCode == KeyEvent.KEYCODE_DEL) {
            // Check whether there is a correction to undo
            val correction = AutoCorrector.getLastCorrection()
            
            if (correction != null) {
                // Get the text before the cursor to verify that it matches the correction
                val textBeforeCursor = inputConnection.getTextBeforeCursor(
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
                        inputConnection.deleteSurroundingText(charsToDelete, 0)
                        
                        // Reinsert the original word
                        inputConnection.commitText(correction.originalWord, 1)
                        
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
        val isSpace = keyCode == KeyEvent.KEYCODE_SPACE
        if (isSpace) {
            val doubleSpaceToPeriodEnabled = SettingsManager.getDoubleSpaceToPeriod(this)
            if (doubleSpaceToPeriodEnabled) {
                val currentTime = System.currentTimeMillis()
                // Check whether this is a double-tap on space (second tap within DOUBLE_TAP_THRESHOLD)
                val isDoubleTap = lastSpacePressTime > 0 && 
                                 (currentTime - lastSpacePressTime) < DOUBLE_TAP_THRESHOLD
                
                if (isDoubleTap) {
                    // When space is pressed the second time, in onKeyDown the second space has NOT yet been inserted.
                    // So we check whether there is already a space before the cursor (from the first tap).
                    val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0)
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
                                    inputConnection.deleteSurroundingText(1, 0)
                                    inputConnection.commitText(". ", 1)
                                    
                                    // Enable shiftOneShot to capitalize the next letter
                                    shiftOneShot = true
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
                                inputConnection.deleteSurroundingText(1, 0)
                                inputConnection.commitText(". ", 1)
                                
                                // Enable shiftOneShot to capitalize the next letter
                                shiftOneShot = true
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
        val autoCapitalizeAfterPeriodEnabled = SettingsManager.getAutoCapitalizeAfterPeriod(this)

        if (autoCapitalizeAfterPeriodEnabled && inputConnection != null &&
            keyCode == KeyEvent.KEYCODE_SPACE && !shiftOneShot) {

            val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0)

            // Check if text ends with punctuation that requires capitalization: ".", "!", "?"
            // For period, avoid multiple consecutive periods (ellipsis "...")
            // For "!" and "?", consecutive characters are acceptable (e.g., "!!!" or "???")
            if (textBeforeCursor != null && textBeforeCursor.isNotEmpty()) {
                val lastChar = textBeforeCursor[textBeforeCursor.length - 1]
                val shouldCapitalize = when (lastChar) {
                    '.' -> {
                        // For period, check that it's not part of an ellipsis
                        textBeforeCursor.length >= 2 && textBeforeCursor[textBeforeCursor.length - 2] != '.'
                    }
                    '!', '?' -> {
                        // For "!" and "?", just check that the character exists
                        true
                    }
                    else -> false
                }
                
                if (shouldCapitalize) {
                    shiftOneShot = true
                    updateStatusBarText()
                }
            }
        }

        // HANDLE AUTO-CORRECTION FOR SPACE AND PUNCTUATION
        if (isAutoCorrectEnabled) {
            // Controlla se è spazio o punteggiatura
            val isPunctuation = event?.unicodeChar != null &&
                               event.unicodeChar != 0 &&
                               event.unicodeChar.toChar() in ".,;:!?()[]{}\"'"
            
            if (isSpace || isPunctuation) {
                // Get text before cursor
                val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0)
                
                // Process and obtain correction if available
                val correction = AutoCorrector.processText(textBeforeCursor, context = this)
                
                if (correction != null) {
                    val (wordToReplace, correctedWord) = correction
                    
                    // Delete the word to be corrected
                    inputConnection.deleteSurroundingText(wordToReplace.length, 0)
                    
                    // Insert the corrected word
                    inputConnection.commitText(correctedWord, 1)
                    
                    // Record the correction so it can be undone
                    AutoCorrector.recordCorrection(
                        originalWord = wordToReplace,
                        correctedWord = correctedWord
                    )
                    
                    // If it was space, insert it after the correction
                    if (isSpace) {
                        inputConnection.commitText(" ", 1)
                    } else if (isPunctuation && event?.unicodeChar != null && event.unicodeChar != 0) {
                        // If it was punctuation, insert it after the correction
                        val punctChar = event.unicodeChar.toChar().toString()
                        inputConnection.commitText(punctChar, 1)
                    }
                    
                    Log.d(TAG, "Auto-correction applied: '$wordToReplace' → '$correctedWord'")
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
        
        // Handle double-tap Shift to toggle Caps Lock
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (!shiftPressed) {
                // Shift just pressed
                shiftPhysicallyPressed = true
                val currentTime = System.currentTimeMillis()
                
                if (capsLockEnabled) {
                    // If Caps Lock is active, a single tap disables it
                    capsLockEnabled = false
                    updateStatusBarText()
                    lastShiftReleaseTime = 0 // Reset per evitare attivazioni indesiderate
                } else if (shiftOneShot) {
                    // If Shift one-shot is active, check for a quick double-tap
                    if (currentTime - lastShiftReleaseTime < DOUBLE_TAP_THRESHOLD && lastShiftReleaseTime > 0) {
                        // Double-tap detected while one-shot is active - enable Caps Lock
                        shiftOneShot = false
                        capsLockEnabled = true
                        Log.d(TAG, "Shift double-tap: one-shot -> Caps Lock")
                        refreshStatusBar()
                        lastShiftReleaseTime = 0 // Reset per evitare triple tap
                    } else {
                        // Single tap while one-shot is active - disable one-shot
                        shiftOneShot = false
                        Log.d(TAG, "Shift one-shot disabled")
                        refreshStatusBar()
                        lastShiftReleaseTime = 0 // Reset per evitare attivazioni indesiderate
                    }
                } else {
                    // If Caps Lock is not active and one-shot is not active, check for double-tap
                    if (currentTime - lastShiftReleaseTime < DOUBLE_TAP_THRESHOLD && lastShiftReleaseTime > 0) {
                        // Double-tap detected - enable Caps Lock
                        capsLockEnabled = true
                        updateStatusBarText()
                        lastShiftReleaseTime = 0 // Reset per evitare triple tap
                    } else {
                        // Shift pressed once - enable one-shot
                        shiftOneShot = true
                        Log.d(TAG, "Shift one-shot enabled")
                        updateStatusBarText() // Update to show "shift"
                    }
                }
                shiftPressed = true
            }
            // Non consumiamo l'evento, lasciamo che Android gestisca Shift normalmente
            return super.onKeyDown(keyCode, event)
        }
        
        // Handle double-tap Ctrl to toggle Ctrl latch
        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            if (!ctrlPressed) {
                // Ctrl just pressed
                ctrlPhysicallyPressed = true
                val currentTime = System.currentTimeMillis()
                
                if (ctrlLatchActive) {
                    // If Ctrl latch is active, a single tap disables it.
                    // BUT: if it is active from nav mode and we are NOT in a text field, disable nav mode.
                    // When in a text field, nav mode is already disabled in onStartInput.
                    if (ctrlLatchFromNavMode && !isInputViewActive) {
                        // We are in nav mode and Ctrl is pressed - disable nav mode
                        ctrlLatchActive = false
                        ctrlLatchFromNavMode = false
                        Log.d(TAG, "Ctrl latch disabled from nav mode (Ctrl pressed)")
                        // Cancel notification when nav mode is disabled
                        NotificationHelper.cancelNavModeNotification(this)
                        // Non serve nascondere la tastiera perché in nav mode non viene mai mostrata
                    } else if (!ctrlLatchFromNavMode) {
                        // Regular Ctrl latch (not nav mode), disable it normally
                        ctrlLatchActive = false
                        updateStatusBarText()
                    } else {
                        // Ctrl latch from nav mode but we are in a text field - this should not happen,
                        // but if it does, disable it anyway.
                        if (ctrlLatchFromNavMode) {
                            NotificationHelper.cancelNavModeNotification(this)
                        }
                        ctrlLatchActive = false
                        ctrlLatchFromNavMode = false
                        updateStatusBarText()
                    }
                    lastCtrlReleaseTime = 0 // Reset per evitare attivazioni indesiderate
                } else if (ctrlOneShot) {
                    // If Ctrl one-shot is active, check for a quick double-tap
                    if (currentTime - lastCtrlReleaseTime < DOUBLE_TAP_THRESHOLD && lastCtrlReleaseTime > 0) {
                        // Double-tap detected while one-shot is active - enable Ctrl latch
                        ctrlOneShot = false
                        ctrlLatchActive = true
                        Log.d(TAG, "Ctrl double-tap: one-shot -> latch")
                        updateStatusBarText()
                        lastCtrlReleaseTime = 0 // Reset per evitare triple tap
                    } else {
                        // Single tap while one-shot is active - disable one-shot
                        ctrlOneShot = false
                        Log.d(TAG, "Ctrl one-shot disabled")
                        updateStatusBarText()
                        lastCtrlReleaseTime = 0 // Reset per evitare attivazioni indesiderate
                    }
                } else {
                    // If Ctrl latch is not active and one-shot is not active, check for double-tap
                    if (currentTime - lastCtrlReleaseTime < DOUBLE_TAP_THRESHOLD && lastCtrlReleaseTime > 0) {
                        // Double-tap detected - enable Ctrl latch
                        ctrlLatchActive = true
                        updateStatusBarText()
                        lastCtrlReleaseTime = 0 // Reset per evitare triple tap
                    } else {
                        // Ctrl pressed once - enable one-shot
                        ctrlOneShot = true
                        updateStatusBarText() // Update to show "ctrl"
                    }
                }
                ctrlPressed = true
            }
            // Non consumiamo l'evento, lasciamo che Android gestisca Ctrl normalmente
            return super.onKeyDown(keyCode, event)
        }
        
        // Handle double-tap Alt to toggle Alt latch
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            if (!altPressed) {
                // Alt just pressed
                altPhysicallyPressed = true
                val currentTime = System.currentTimeMillis()
                
                if (altLatchActive) {
                    // If Alt latch is active, a single tap disables it
                    altLatchActive = false
                    updateStatusBarText()
                    lastAltReleaseTime = 0 // Reset per evitare attivazioni indesiderate
                } else if (altOneShot) {
                    // If Alt one-shot is active, check for a quick double-tap
                    if (currentTime - lastAltReleaseTime < DOUBLE_TAP_THRESHOLD && lastAltReleaseTime > 0) {
                        // Double-tap detected while one-shot is active - enable Alt latch
                        altOneShot = false
                        altLatchActive = true
                        Log.d(TAG, "Alt double-tap: one-shot -> latch")
                        updateStatusBarText()
                        lastAltReleaseTime = 0 // Reset per evitare triple tap
                    } else {
                        // Single tap while one-shot is active - disable one-shot
                        altOneShot = false
                        Log.d(TAG, "Alt one-shot disabled")
                        updateStatusBarText()
                        lastAltReleaseTime = 0 // Reset per evitare attivazioni indesiderate
                    }
                } else {
                    // If Alt latch is not active and one-shot is not active, check for double-tap
                    if (currentTime - lastAltReleaseTime < DOUBLE_TAP_THRESHOLD && lastAltReleaseTime > 0) {
                        // Double-tap detected - enable Alt latch
                        altLatchActive = true
                        updateStatusBarText()
                        lastAltReleaseTime = 0 // Reset per evitare triple tap
                    } else {
                        // Alt pressed once - enable one-shot
                        altOneShot = true
                        updateStatusBarText() // Update to show "alt"
                    }
                }
                altPressed = true
            }
            // Consumiamo l'evento Alt per evitare che Android gestisca Alt+Spazio come scorciatoia
            // Questo previene il popup di selezione simboli di Android
            Log.d(TAG, "Alt key consumed to prevent Android symbol picker popup")
            return true  // Consumiamo l'evento invece di passarlo ad Android
        }
        
        // Handle SYM key (toggle/latch with 3 states: disabled -> page1 -> page2 -> disabled)
        if (keyCode == KEYCODE_SYM) {
            // Cycle between the 3 states: 0 -> 1 -> 2 -> 0
            symPage = (symPage + 1) % 3
            updateStatusBarText()
            // Consume the event to prevent Android from handling it
            return true
        }
        
        // Handle keycode 322 to delete the last word (swipe to delete)
        if (keyCode == 322) {
            val swipeToDeleteEnabled = SettingsManager.getSwipeToDelete(this)
                if (swipeToDeleteEnabled) {
                if (TextSelectionHelper.deleteLastWord(inputConnection)) {
                    // Consumiamo l'evento
                    return true
                }
                    } else {
                // Feature disabled, still consume the event to avoid unwanted behavior
                Log.d(TAG, "Swipe to delete disabled, keycode 322 event ignored")
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
            val symChar = when (symPage) {
                1 -> altSymManager.getSymMappings()[keyCode]
                2 -> altSymManager.getSymMappings2()[keyCode]
                else -> null
            }
            if (symChar != null) {
                // Insert emoji or character from SYM map
                inputConnection.commitText(symChar, 1)
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
            
            // FIX: Consumiamo Alt+Spazio per evitare il popup di selezione simboli di Android
            // Questo gestisce i casi: Spazio->Alt, Alt->Spazio, Alt->tasto alfabetico->Spazio
            // Inseriamo uno spazio nel campo di testo
            if (keyCode == KeyEvent.KEYCODE_SPACE) {
                inputConnection.commitText(" ", 1)
                Log.d(TAG, "Alt+Space combination: inserted space to prevent Android symbol picker popup")
                updateStatusBarText()
                return true  // Consumiamo l'evento senza passarlo ad Android
            }
            
            val result = altSymManager.handleAltCombination(
                keyCode,
                inputConnection,
                event
            ) { defaultKeyCode, defaultEvent ->
                // FIX: Anche se non c'è una mappatura Alt per questo tasto,
                // consumiamo l'evento quando Alt è attivo e il tasto è Spazio
                // per evitare comportamenti indesiderati di Android
                // Inseriamo uno spazio nel campo di testo
                if (keyCode == KeyEvent.KEYCODE_SPACE) {
                    inputConnection.commitText(" ", 1)
                    Log.d(TAG, "Alt+Space (no mapping): inserted space to prevent Android symbol picker")
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
                                TextSelectionHelper.expandSelectionLeft(inputConnection)
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
                                TextSelectionHelper.expandSelectionRight(inputConnection)
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
                                    inputConnection.performContextMenuAction(actionId)
                                    return true
                                } else {
                                    // Unknown action, consume the event to avoid inserting characters
                                    Log.d(TAG, "Ctrl+action not recognized: ${ctrlMapping.value}, event consumed")
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
                            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, mappedKeyCode))
                            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, mappedKeyCode))
                            
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
                            Log.d(TAG, "Ctrl+keycode not recognized: ${ctrlMapping.value}, event consumed")
                            return true
                        }
                    }
                }
            } else {
                // Ctrl is pressed but this key has no valid mapping.
                // Special handling for Backspace: delete last word or selected text.
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    // Verifica se c'è del testo selezionato
                    val extractedText = inputConnection.getExtractedText(
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
                        inputConnection.commitText("", 0)
                        Log.d(TAG, "Ctrl+Backspace: selected text deleted")
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
                        TextSelectionHelper.deleteLastWord(inputConnection)
                        return true
                    }
                }
                // Eccezione per Enter: continua a funzionare normalmente
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    // Lascia passare Enter anche con Ctrl premuto
                        Log.d(TAG, "Ctrl+Enter without mapping, passing event to Android")
                    return super.onKeyDown(keyCode, event)
                }
                // For all other keys without mappings, consume the event
                Log.d(TAG, "Ctrl+key without mapping (keyCode: $keyCode), event consumed")
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
                    Log.d(TAG, "Numeric field: direct insertion of Alt character '$altChar' for keyCode $keyCode")
                    inputConnection.commitText(altChar, 1)
                    // Update variations after insertion (with delay to ensure commitText is completed)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        updateStatusBarText()
                    }, 30) // 30ms per dare tempo ad Android di completare commitText
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
                inputConnection,
                shiftOneShot,
                layoutChar
            )
            // Disable shiftOneShot after handling a key with Alt mapping,
            // so that it does not stay active for the next key.
            if (wasShiftOneShot) {
                shiftOneShot = false
                updateStatusBarText()
            }
            return true
        }
        
        // Handle Shift one-shot for keys without Alt mapping
        if (shiftOneShot) {
            val charFromLayout = getCharacterStringFromLayout(keyCode, event, isShift = true)
            if (charFromLayout.isNotEmpty() && charFromLayout[0].isLetter()) {
                Log.d(TAG, "Shift one-shot active, character from layout: $charFromLayout")
                // Always force uppercase when shiftOneShot is active
                val char = charFromLayout.uppercase()
                Log.d(TAG, "Shift one-shot, modified character: $char")
                shiftOneShot = false
                inputConnection.commitText(char, 1)
                // Update variations after insertion (with delay to ensure commitText is completed)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    updateStatusBarText()
                }, 30) // 30ms per dare tempo ad Android di completare commitText
                return true
            }
        }
        
        // When there is no mapping, handle Caps Lock for regular characters.
        // Apply Caps Lock to alphabetical characters.
        val charFromLayout = getCharacterStringFromLayout(keyCode, event, event?.isShiftPressed == true)
        if (charFromLayout.isNotEmpty() && charFromLayout[0].isLetter()) {
            var char = charFromLayout
            var shouldConsume = false
            
            // Apply Caps Lock when active (but only if Shift is not pressed)
            if (capsLockEnabled && event?.isShiftPressed != true) {
                char = char.uppercase()
                shouldConsume = true
            } else if (capsLockEnabled && event?.isShiftPressed == true) {
                // When Caps Lock is active and Shift is pressed, force lowercase
                char = char.lowercase()
                shouldConsume = true
            }
            
            if (shouldConsume) {
                inputConnection.commitText(char, 1)
                // Update variations after insertion (with delay to ensure commitText is completed)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    updateStatusBarText()
                }, 30) // 30ms per dare tempo ad Android di completare commitText
                return true
            }
        }
        
        // When there is no mapping, check whether the character has variations.
        // If it does, handle it ourselves so we can show variation suggestions.
        val charForVariations = getCharacterFromLayout(keyCode, event, event?.isShiftPressed == true)
        if (charForVariations != null) {
            // Check whether the character has variations
            if (variationsMap.containsKey(charForVariations)) {
                // Insert the character ourselves so we can show variations
                inputConnection.commitText(charForVariations.toString(), 1)
                // Update variations after insertion (with delay to ensure commitText is completed)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    updateStatusBarText()
                }, 30) // 30ms per dare tempo ad Android di completare commitText
                return true
            }
            // If the character has no variations, previous variations remain visible
            // (display only, no action)
        }
        
        // Convert all alphabetic characters using the selected keyboard layout
        // This ensures that layout conversion works even when no special modifiers are active
        // Check if this is an alphabetic key that should be converted
        val isAlphabeticKey = isAlphabeticKey(keyCode)
        if (isAlphabeticKey && KeyboardLayoutManager.isMapped(keyCode)) {
            val convertedChar = getCharacterFromLayout(keyCode, event, event?.isShiftPressed == true)
            if (convertedChar != null && convertedChar.isLetter()) {
                // Get the character from the layout and insert it ourselves
                // This overrides Android's system layout handling
                var char = convertedChar.toString()
                
                // Apply Caps Lock if enabled (but only if Shift is not pressed)
                if (capsLockEnabled && event?.isShiftPressed != true) {
                    char = char.uppercase()
                } else if (capsLockEnabled && event?.isShiftPressed == true) {
                    // When Caps Lock is active and Shift is pressed, force lowercase
                    char = char.lowercase()
                }
                
                Log.d(TAG, "Layout conversion: keyCode=$keyCode, original=${event?.unicodeChar?.toChar()}, converted=$char")
                
                // Insert the converted character
                inputConnection.commitText(char, 1)
                
                // Update variations after insertion (with delay to ensure commitText is completed)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    updateStatusBarText()
                }, 30) // 30ms per dare tempo ad Android di completare commitText
                
                // Consume the event to prevent Android from handling it
                return true
            }
        }
        
        // When there is no mapping, let Android handle the event normally.
        // Update variations after Android has processed the event.
        val result = super.onKeyDown(keyCode, event)
        // Update variations after any operation that could change text or cursor position
        // (inserted characters, backspace, arrows, etc.).
        if (result) {
            // Use a delayed post to ensure Android has completed all changes,
            // including character insertions, cursor moves, backspace, etc.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                updateStatusBarText()
            }, 30) // 30ms per dare tempo ad Android di completare le modifiche
        }
        return result
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // Exception for nav mode: handle Ctrl even when we are not in a text field
        val isCtrlKey = keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT
        
        if (!isInputViewActive && isCtrlKey) {
            // Handle Ctrl release in nav mode
            if (ctrlPressed) {
                val result = NavModeHandler.handleCtrlKeyUp()
                result.ctrlPressed?.let { ctrlPressed = it }
                result.ctrlPhysicallyPressed?.let { ctrlPhysicallyPressed = it }
                result.lastCtrlReleaseTime?.let { lastCtrlReleaseTime = it }
                updateStatusBarText()
                Log.d(TAG, "Nav mode: Ctrl released")
            }
            // Consumiamo l'evento per evitare che Android lo gestisca
            return true
        }
        
        val inputConnection = currentInputConnection ?: return super.onKeyUp(keyCode, event)
        
        // Always notify the tracker (even when the event is consumed)
        KeyboardEventTracker.notifyKeyEvent(keyCode, event, "KEY_UP")
        
        // Handle Shift release for double-tap
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (shiftPressed) {
                lastShiftReleaseTime = System.currentTimeMillis()
                shiftPressed = false
                shiftPhysicallyPressed = false
                // Do not disable shiftOneShot here - it is disabled when used.
                // If released without being used, it remains active for the next key.
                updateStatusBarText() // Update to remove physical "shift"
            }
            // Non consumiamo l'evento, lasciamo che Android gestisca Shift normalmente
            return super.onKeyUp(keyCode, event)
        }
        
        // Handle Ctrl release for double-tap
        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            if (ctrlPressed) {
                lastCtrlReleaseTime = System.currentTimeMillis()
                ctrlPressed = false
                ctrlPhysicallyPressed = false
                // Do not disable ctrlOneShot here - it is disabled when used.
                // If released without being used, it remains active for the next key.
                updateStatusBarText() // Update to remove physical "ctrl"
            }
            // Non consumiamo l'evento, lasciamo che Android gestisca Ctrl normalmente
            return super.onKeyUp(keyCode, event)
        }
        
        // Handle Alt release for double-tap
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            if (altPressed) {
                lastAltReleaseTime = System.currentTimeMillis()
                altPressed = false
                altPhysicallyPressed = false
                // Do not disable altOneShot here - it is disabled when used.
                // If released without being used, it remains active for the next key.
                updateStatusBarText() // Update to remove physical "alt"
            }
            // Non consumiamo l'evento, lasciamo che Android gestisca Alt normalmente
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

