package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.SharedPreferences
import it.palsoftware.pastiera.SettingsManager
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.inputmethod.KeyboardEventTracker

/**
 * Servizio di immissione specializzato per tastiere fisiche.
 * Gestisce funzionalità avanzate come il long press che simula Alt+tasto.
 */
class PhysicalKeyboardInputMethodService : InputMethodService() {

    companion object {
        private const val TAG = "PastieraInputMethod"
    }

    // SharedPreferences per le impostazioni
    private lateinit var prefs: SharedPreferences

    private lateinit var altSymManager: AltSymManager
    private lateinit var statusBarController: StatusBarController

    // Keycode per il tasto SYM
    private val KEYCODE_SYM = 63
    
    // Stato per tracciare se SYM è attualmente attivo (latch/toggle)
    private var symKeyActive = false
    
    // Mappatura Ctrl+tasto -> azione o keycode (caricata da JSON)
    private val ctrlKeyMap = mutableMapOf<Int, KeyMappingLoader.CtrlMapping>()
    
    // Mappatura variazioni caratteri (caricata da JSON)
    private val variationsMap = mutableMapOf<Char, List<String>>()
    
    // Ultimo carattere inserito e relative variazioni disponibili
    private var lastInsertedChar: Char? = null
    private var availableVariations: List<String> = emptyList()
    private var variationsActive = false
    
    // Stato Caps Lock
    private var capsLockEnabled = false
    
    // Tracciamento doppio tap su Shift per attivare Caps Lock
    private var lastShiftReleaseTime: Long = 0
    private var shiftPressed = false
    
    // Stati latch per Ctrl e Alt
    private var ctrlLatchActive = false
    private var altLatchActive = false
    
    // Tracciamento doppio tap su Ctrl e Alt
    private var lastCtrlReleaseTime: Long = 0
    private var ctrlPressed = false
    private var lastAltReleaseTime: Long = 0
    private var altPressed = false
    
    // Tracciamento tasti modificatori premuti fisicamente (per la status bar)
    private var shiftPhysicallyPressed = false
    private var ctrlPhysicallyPressed = false
    private var altPhysicallyPressed = false
    
    // Stati one-shot per i tasti modificatori (attivi fino al prossimo tasto)
    private var shiftOneShot = false
    private var ctrlOneShot = false
    private var altOneShot = false
    
    private val DOUBLE_TAP_THRESHOLD = 500L // millisecondi
    
    // Flag per tracciare se siamo in un contesto di input valido
    private var isInputViewActive = false
    
    // Flag per tracciare se siamo in un campo numerico
    private var isNumericField = false

    private fun refreshStatusBar() {
        updateStatusBarText()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate() chiamato")
        prefs = getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
        statusBarController = StatusBarController(this)
        // Registra listener per la selezione delle variazioni
        statusBarController.onVariationSelectedListener = object : VariationButtonHandler.OnVariationSelectedListener {
            override fun onVariationSelected(variation: String) {
                // Aggiorna le variazioni dopo che una variazione è stata selezionata
                // (per aggiornare la visualizzazione se necessario)
                updateStatusBarText()
            }
        }
        altSymManager = AltSymManager(assets, prefs)
        // Registra callback per notificare quando viene inserito un carattere Alt dopo long press
        // Le variazioni vengono aggiornate automaticamente da updateStatusBarText()
        altSymManager.onAltCharInserted = { char ->
            updateStatusBarText()
        }
        ctrlKeyMap.putAll(KeyMappingLoader.loadCtrlKeyMappings(assets))
        variationsMap.putAll(KeyMappingLoader.loadVariations(assets))
        Log.d(TAG, "onCreate() completato")
    }

    override fun onCreateInputView(): View? {
        Log.d(TAG, "onCreateInputView() chiamato")
        val layout = statusBarController.getOrCreateLayout(altSymManager.buildEmojiMapText())
        refreshStatusBar()
        Log.d(TAG, "onCreateInputView() completato - view creata: ${layout != null}")
        return layout
    }

    // Flag per tracciare se Ctrl latch è stato attivato nel nav mode (anche quando si entra in un campo di testo)
    private var ctrlLatchFromNavMode = false
    
    /**
     * Resetta tutti gli stati dei tasti modificatori.
     * Viene chiamato quando si esce da un campo o si chiude/riapre la tastiera.
     * @param preserveNavMode Se true, preserva Ctrl latch se attivo nel nav mode
     */
    private fun resetModifierStates(preserveNavMode: Boolean = false) {
        Log.d(TAG, "resetModifierStates() chiamato - reset di tutti gli stati modificatori, preserveNavMode: $preserveNavMode, ctrlLatchActive: $ctrlLatchActive, ctrlLatchFromNavMode: $ctrlLatchFromNavMode")
        
        // Salva lo stato del nav mode se necessario
        // Se Ctrl latch è attivo e viene dal nav mode, preservalo
        val savedCtrlLatch = if (preserveNavMode && (ctrlLatchActive || ctrlLatchFromNavMode)) {
            if (ctrlLatchActive) {
                ctrlLatchFromNavMode = true // Marca che il Ctrl latch viene dal nav mode
                true
            } else if (ctrlLatchFromNavMode) {
                true // Mantieni attivo se era già marcato come nav mode
            } else {
                false
            }
        } else {
            false
        }
        
        // Reset Caps Lock
        capsLockEnabled = false
        
        // Reset stati one-shot
        shiftOneShot = false
        ctrlOneShot = false
        altOneShot = false
        
        // Reset stati latch (ma preserva Ctrl latch nel nav mode se richiesto)
        if (preserveNavMode && savedCtrlLatch) {
            // Mantieni Ctrl latch attivo nel nav mode
            ctrlLatchActive = true
            Log.d(TAG, "resetModifierStates() - preservato Ctrl latch nel nav mode")
        } else {
            ctrlLatchActive = false
            ctrlLatchFromNavMode = false // Reset anche il flag del nav mode
        }
        altLatchActive = false
        
        // Reset SYM
        symKeyActive = false
        
        // Reset stati fisici
        shiftPressed = false
        ctrlPressed = false
        altPressed = false
        
        // Reset stati fisicamente premuti (per status bar)
        shiftPhysicallyPressed = false
        ctrlPhysicallyPressed = false
        altPhysicallyPressed = false
        
        // Reset tempi di rilascio
        lastShiftReleaseTime = 0
        lastCtrlReleaseTime = 0
        lastAltReleaseTime = 0
        
        // Reset stato Alt/SYM
        altSymManager.resetTransientState()
        
        // Reset variazioni
        deactivateVariations()
        
        // Aggiorna la status bar
        refreshStatusBar()
    }
    
    /**
     * Forza la creazione e visualizzazione della view.
     * Viene chiamata quando viene premuto il primo tasto fisico.
     * Mostra la tastiera se c'è un campo di testo attivo o se Ctrl latch è attivo (nav mode).
     */
    private fun ensureInputViewCreated() {
        Log.d(TAG, "ensureInputViewCreated() chiamato - isInputViewActive: $isInputViewActive, ctrlLatchActive: $ctrlLatchActive")
        
        // Eccezione per nav mode: mostra la tastiera se Ctrl latch è attivo
        val shouldShow = isInputViewActive || ctrlLatchActive
        
        if (!shouldShow) {
            Log.d(TAG, "ensureInputViewCreated() - non siamo in un contesto di input valido e Ctrl latch non è attivo, non mostro la tastiera")
            return
        }
        
        // Verifica se c'è un input connection valido (campo di testo attivo)
        // Nel nav mode, potrebbe non esserci un input connection, ma mostriamo comunque la tastiera
        val inputConnection = currentInputConnection
        if (inputConnection == null && !ctrlLatchActive) {
            Log.d(TAG, "ensureInputViewCreated() - nessun inputConnection e Ctrl latch non è attivo, non mostro la tastiera")
            return
        }
        
        val layout = statusBarController.getOrCreateLayout(altSymManager.buildEmojiMapText())
        refreshStatusBar()

        if (layout.parent == null) {
            Log.d(TAG, "ensureInputViewCreated() - setInputView() su nuova layout")
            setInputView(layout)
        }

        Log.d(TAG, "ensureInputViewCreated() - requestShowSelf()")
        requestShowSelf(0)
    }
    
    
    /**
     * Gestisce i tasti quando Ctrl latch è attivo nel nav mode (senza campo di testo).
     * Permette di usare le combinazioni Ctrl+tasto anche quando non c'è un campo di testo attivo.
     */
    private fun handleCtrlKeyInNavMode(keyCode: Int, event: KeyEvent?): Boolean {
        // Notifica l'evento al tracker
        KeyboardEventTracker.notifyKeyEvent(keyCode, event, "KEY_DOWN")
        
        // Forza la creazione della view se necessario
        ensureInputViewCreated()
        
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
     * Aggiorna le variazioni controllando il carattere prima del cursore.
     */
    private fun updateVariationsFromCursor() {
        val inputConnection = currentInputConnection
        if (inputConnection == null) {
            deactivateVariations()
            return
        }
        
        // Ottieni il carattere prima del cursore
        val textBeforeCursor = inputConnection.getTextBeforeCursor(1, 0)
        if (textBeforeCursor != null && textBeforeCursor.isNotEmpty()) {
            val charBeforeCursor = textBeforeCursor[textBeforeCursor.length - 1]
            // Controlla se il carattere ha variazioni disponibili
            val variations = variationsMap[charBeforeCursor]
            if (variations != null && variations.isNotEmpty()) {
                lastInsertedChar = charBeforeCursor
                availableVariations = variations
                variationsActive = true
                Log.d(TAG, "Variazioni aggiornate per carattere prima del cursore '$charBeforeCursor': $variations")
            } else {
                // Nessuna variazione disponibile per questo carattere
                variationsActive = false
                lastInsertedChar = null
                availableVariations = emptyList()
            }
        } else {
            // Nessun carattere prima del cursore
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
            symKeyActive = symKeyActive,
            variations = if (variationsActive) availableVariations else emptyList(),
            lastInsertedChar = lastInsertedChar
        )
        // Passa anche la mappa emoji quando SYM è attivo
        val emojiMapText = if (symKeyActive) altSymManager.buildEmojiMapText() else ""
        // Passa l'inputConnection per rendere i pulsanti clickabili
        val inputConnection = currentInputConnection
        statusBarController.update(snapshot, emojiMapText, inputConnection)
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
        Log.d(TAG, "onStartInput() chiamato - restarting: $restarting, info: ${info?.packageName}, inputType: ${info?.inputType}, ctrlLatchActive: $ctrlLatchActive")
        
        // Verifica se il campo è effettivamente editabile
        val isEditable = info?.let { editorInfo ->
            val inputType = editorInfo.inputType
            // Controlla se è un campo di testo editabile (non TYPE_NULL)
            val isTextInput = inputType and android.text.InputType.TYPE_MASK_CLASS != android.text.InputType.TYPE_NULL
            // Escludi i campi non editabili come le liste
            val isNotNoInput = inputType and android.text.InputType.TYPE_MASK_CLASS != 0
            isTextInput && isNotNoInput
        } ?: false
        
        Log.d(TAG, "onStartInput() - isEditable: $isEditable")
        
        // Segna che siamo in un contesto di input valido solo se il campo è editabile
        isInputViewActive = isEditable
        
        // Verifica se il campo è numerico
        isNumericField = info?.let { editorInfo ->
            val inputType = editorInfo.inputType
            val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
            inputClass == android.text.InputType.TYPE_CLASS_NUMBER
        } ?: false
        Log.d(TAG, "onStartInput() - isNumericField: $isNumericField")
        
        // Disabilita i suggerimenti per evitare popup
        if (info != null && isEditable) {
            info.inputType = info.inputType or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        
        // Reset degli stati modificatori quando si entra in un nuovo campo (solo se non è un restart)
        // IMPORTANTE: Disattiva il nav mode SOLO quando si entra in un campo di testo editabile
        // Non disattivarlo quando si passa a un altro elemento UI (come icone, liste, ecc.)
        if (!restarting) {
            // Se siamo in nav mode, preservalo SEMPRE a meno che non entriamo in un campo realmente editabile
            if (ctrlLatchFromNavMode && ctrlLatchActive) {
                // Verifica se il campo è REALMENTE editabile (non solo sembra editabile)
                val isReallyEditable = isEditable && info?.let { editorInfo ->
                    val inputType = editorInfo.inputType
                    val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
                    // Verifica che sia un tipo di input realmente editabile
                    inputClass == android.text.InputType.TYPE_CLASS_TEXT ||
                    inputClass == android.text.InputType.TYPE_CLASS_NUMBER ||
                    inputClass == android.text.InputType.TYPE_CLASS_PHONE ||
                    inputClass == android.text.InputType.TYPE_CLASS_DATETIME
                } ?: false
                
                // Verifica anche se abbiamo un input connection valido
                val inputConnection = currentInputConnection
                val hasValidInputConnection = inputConnection != null
                
                if (isReallyEditable && hasValidInputConnection) {
                    // Disattiva il nav mode SOLO quando si entra in un campo di testo realmente editabile
                    ctrlLatchFromNavMode = false
                    ctrlLatchActive = false
                    Log.d(TAG, "onStartInput() - disattivato nav mode perché entrato in campo di testo realmente editabile")
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
        Log.d(TAG, "onStartInputView() chiamato - restarting: $restarting, ctrlLatchFromNavMode: $ctrlLatchFromNavMode")
        
        // Verifica se il campo è effettivamente editabile
        val isEditable = info?.let { editorInfo ->
            val inputType = editorInfo.inputType
            val isTextInput = inputType and android.text.InputType.TYPE_MASK_CLASS != android.text.InputType.TYPE_NULL
            val isNotNoInput = inputType and android.text.InputType.TYPE_MASK_CLASS != 0
            isTextInput && isNotNoInput
        } ?: false
        
        // Segna che siamo in un contesto di input valido solo se il campo è editabile
        // MA: se siamo in nav mode, non impostare isInputViewActive a true se non c'è un campo editabile
        if (isEditable) {
            // Verifica se il campo è REALMENTE editabile (non solo sembra editabile)
            val isReallyEditable = info?.let { editorInfo ->
                val inputType = editorInfo.inputType
                val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
                // Verifica che sia un tipo di input realmente editabile
                inputClass == android.text.InputType.TYPE_CLASS_TEXT ||
                inputClass == android.text.InputType.TYPE_CLASS_NUMBER ||
                inputClass == android.text.InputType.TYPE_CLASS_PHONE ||
                inputClass == android.text.InputType.TYPE_CLASS_DATETIME
            } ?: false
            
            if (isReallyEditable) {
                isInputViewActive = true
                // Verifica se il campo è numerico
                isNumericField = info?.let { editorInfo ->
                    val inputType = editorInfo.inputType
                    val inputClass = inputType and android.text.InputType.TYPE_MASK_CLASS
                    inputClass == android.text.InputType.TYPE_CLASS_NUMBER
                } ?: false
                Log.d(TAG, "onStartInputView() - isNumericField: $isNumericField")
                // Se siamo in nav mode e entriamo in un campo realmente editabile, disattiva il nav mode
                if (ctrlLatchFromNavMode && ctrlLatchActive) {
                    val inputConnection = currentInputConnection
                    if (inputConnection != null) {
                        ctrlLatchFromNavMode = false
                        ctrlLatchActive = false
                        Log.d(TAG, "onStartInputView() - disattivato nav mode perché entrato in campo di testo realmente editabile")
                    }
                }
            } else {
                // Non è un campo realmente editabile - se siamo in nav mode, mantienilo
                if (ctrlLatchFromNavMode && ctrlLatchActive) {
                    Log.d(TAG, "onStartInputView() - nav mode attivo, campo non realmente editabile, mantengo nav mode")
                    isInputViewActive = false
                } else {
                    isInputViewActive = false
                }
            }
        } else if (!ctrlLatchFromNavMode) {
            // Non siamo in nav mode e non c'è un campo editabile
            isInputViewActive = false
        } else {
            // Siamo in nav mode e non c'è un campo editabile - mantieni isInputViewActive = false
            isInputViewActive = false
        }
        // Ricarica eventuali impostazioni relative all'Alt manager
        altSymManager.reloadLongPressThreshold()
        altSymManager.resetTransientState()
    }
    
    override fun onFinishInput() {
        super.onFinishInput()
        Log.d(TAG, "onFinishInput() chiamato - reset degli stati modificatori")
        // Segna che non siamo più in un contesto di input valido
        isInputViewActive = false
        isNumericField = false
        // Reset degli stati modificatori quando si esce da un campo
        // Preserva Ctrl latch se attivo nel nav mode
        resetModifierStates(preserveNavMode = true)
    }
    
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        Log.d(TAG, "onFinishInputView() chiamato - finishingInput: $finishingInput, ctrlLatchFromNavMode: $ctrlLatchFromNavMode, ctrlLatchActive: $ctrlLatchActive")
        // Segna che non siamo più in un contesto di input valido
        isInputViewActive = false
        // Reset degli stati modificatori quando la view viene nascosta
        // IMPORTANTE: Preserva il nav mode anche qui, altrimenti viene resettato quando si naviga
        if (finishingInput) {
            resetModifierStates(preserveNavMode = true)
        }
    }
    
    override fun onWindowShown() {
        super.onWindowShown()
        Log.d(TAG, "onWindowShown() chiamato - window è visibile")
        // Aggiorna il testo quando la window viene mostrata
        updateStatusBarText()
    }
    
    override fun onWindowHidden() {
        super.onWindowHidden()
        Log.d(TAG, "onWindowHidden() chiamato - window è nascosta, reset degli stati modificatori")
        // Reset degli stati modificatori quando la tastiera viene nascosta
        // Preserva Ctrl latch se attivo nel nav mode
        resetModifierStates(preserveNavMode = true)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        val inputConnection = currentInputConnection ?: return super.onKeyLongPress(keyCode, event)
        
        // Intercetta i long press PRIMA che Android li gestisca
        if (altSymManager.hasAltMapping(keyCode)) {
            // Consumiamo l'evento per evitare il popup di Android
            return true
        }
        
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Eccezione per il nav mode: gestisci Ctrl anche quando non siamo in un campo di testo
        val isCtrlKey = keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT
        val isBackKey = keyCode == KeyEvent.KEYCODE_BACK
        
        // Gestisci il Back per uscire dal nav mode
        if (isBackKey && ctrlLatchFromNavMode && ctrlLatchActive && !isInputViewActive) {
            // Siamo in nav mode e premiamo Back - disattiva il nav mode
            ctrlLatchActive = false
            ctrlLatchFromNavMode = false
            Log.d(TAG, "Nav mode disattivato da Back")
            updateStatusBarText()
            requestHideSelf(0) // Nascondi la tastiera quando si esce dal nav mode
            // Non consumiamo l'evento Back, lasciamo che Android lo gestisca
            return super.onKeyDown(keyCode, event)
        }
        
        // Se non siamo in un contesto di input valido, gestisci Ctrl per il nav mode
        // e anche altri tasti se Ctrl latch è attivo (nav mode attivo)
        if (!isInputViewActive) {
            if (isCtrlKey) {
                // Gestisci il nav mode: doppio tap su Ctrl per attivare/disattivare Ctrl latch
                val (shouldConsume, result) = NavModeHandler.handleCtrlKeyDown(
                    keyCode,
                    ctrlPressed,
                    ctrlLatchActive,
                    lastCtrlReleaseTime
                )
                
                // IMPORTANTE: Applica PRIMA ctrlLatchActive e ctrlLatchFromNavMode
                // PRIMA di chiamare ensureInputViewCreated(), così quando viene chiamato
                // onStartInput() o onStartInputView(), il flag è già impostato
                result.ctrlLatchActive?.let { 
                    ctrlLatchActive = it
                    // Se viene attivato nel nav mode, marca il flag PRIMA
                    if (it) {
                        ctrlLatchFromNavMode = true
                        Log.d(TAG, "Nav mode: Ctrl latch attivato, ctrlLatchFromNavMode = true (impostato PRIMA di ensureInputViewCreated)")
                    } else {
                        ctrlLatchFromNavMode = false
                        Log.d(TAG, "Nav mode: Ctrl latch disattivato, ctrlLatchFromNavMode = false")
                    }
                }
                result.ctrlPhysicallyPressed?.let { ctrlPhysicallyPressed = it }
                result.lastCtrlReleaseTime?.let { lastCtrlReleaseTime = it }
                
                // Aggiorna la status bar PRIMA di mostrare la tastiera
                updateStatusBarText()
                
                if (result.shouldShowKeyboard) {
                    // Ora che i flag sono impostati, mostra la tastiera
                    ensureInputViewCreated()
                }
                if (result.shouldHideKeyboard) {
                    requestHideSelf(0)
                }
                
                if (shouldConsume) {
                    ctrlPressed = true
                    return true
                }
            } else if (ctrlLatchActive) {
                // Se Ctrl latch è attivo nel nav mode, gestisci i tasti anche senza campo di testo
                // Questo permette di usare le combinazioni Ctrl+tasto nel nav mode
                return handleCtrlKeyInNavMode(keyCode, event)
            }
            Log.d(TAG, "onKeyDown() - non siamo in un contesto di input valido, passo l'evento ad Android")
            return super.onKeyDown(keyCode, event)
        }
        
        val inputConnection = currentInputConnection ?: return super.onKeyDown(keyCode, event)
        
        Log.d(TAG, "onKeyDown() - keyCode: $keyCode, inputConnection: ${inputConnection != null}")
        
        // Notifica sempre l'evento al tracker (anche se viene consumato)
        KeyboardEventTracker.notifyKeyEvent(keyCode, event, "KEY_DOWN")
        
        // Forza la creazione della view al primo tasto premuto (inclusi i tasti modificatori)
        // Questo assicura che la status bar sia visibile anche se la tastiera virtuale non è abilitata
        Log.d(TAG, "onKeyDown() - chiamata ensureInputViewCreated() per attivare la tastiera")
        ensureInputViewCreated()
        
        // Gestisci il doppio tap su Shift per attivare/disattivare Caps Lock
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (!shiftPressed) {
                // Shift appena premuto
                shiftPhysicallyPressed = true
                val currentTime = System.currentTimeMillis()
                
                if (capsLockEnabled) {
                    // Se Caps Lock è attivo, un singolo tap lo disattiva
                    capsLockEnabled = false
                    updateStatusBarText()
                    lastShiftReleaseTime = 0 // Reset per evitare attivazioni indesiderate
                } else if (shiftOneShot) {
                    // Se Shift one-shot è attivo, controlla se è un doppio tap veloce
                    if (currentTime - lastShiftReleaseTime < DOUBLE_TAP_THRESHOLD && lastShiftReleaseTime > 0) {
                        // Doppio tap rilevato mentre one-shot è attivo - attiva Caps Lock
                        shiftOneShot = false
                        capsLockEnabled = true
                        Log.d(TAG, "Shift doppio tap: one-shot -> Caps Lock")
                        refreshStatusBar()
                        lastShiftReleaseTime = 0 // Reset per evitare triple tap
                    } else {
                        // Singolo tap mentre one-shot è attivo - disattiva one-shot
                        shiftOneShot = false
                        Log.d(TAG, "Shift one-shot disattivato")
                        refreshStatusBar()
                        lastShiftReleaseTime = 0 // Reset per evitare attivazioni indesiderate
                    }
                } else {
                    // Se Caps Lock non è attivo e one-shot non è attivo, controlla il doppio tap
                    if (currentTime - lastShiftReleaseTime < DOUBLE_TAP_THRESHOLD && lastShiftReleaseTime > 0) {
                        // Doppio tap rilevato - attiva Caps Lock
                        capsLockEnabled = true
                        updateStatusBarText()
                        lastShiftReleaseTime = 0 // Reset per evitare triple tap
                    } else {
                        // Shift premuto da solo - attiva one-shot
                        shiftOneShot = true
                        Log.d(TAG, "Shift one-shot attivato")
                        updateStatusBarText() // Aggiorna per mostrare "shift"
                    }
                }
                shiftPressed = true
            }
            // Non consumiamo l'evento, lasciamo che Android gestisca Shift normalmente
            return super.onKeyDown(keyCode, event)
        }
        
        // Gestisci il doppio tap su Ctrl per attivare/disattivare Ctrl latch
        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            if (!ctrlPressed) {
                // Ctrl appena premuto
                ctrlPhysicallyPressed = true
                val currentTime = System.currentTimeMillis()
                
                if (ctrlLatchActive) {
                    // Se Ctrl latch è attivo, un singolo tap lo disattiva
                    // MA: se è attivo dal nav mode e NON siamo in un campo di testo, disattiva il nav mode
                    // Se siamo in un campo di testo, il nav mode è già stato disattivato in onStartInput
                    if (ctrlLatchFromNavMode && !isInputViewActive) {
                        // Siamo in nav mode e premiamo Ctrl - disattiva il nav mode
                        ctrlLatchActive = false
                        ctrlLatchFromNavMode = false
                        Log.d(TAG, "Ctrl latch disattivato dal nav mode (premuto Ctrl)")
                        updateStatusBarText()
                        requestHideSelf(0) // Nascondi la tastiera quando si esce dal nav mode
                    } else if (!ctrlLatchFromNavMode) {
                        // Ctrl latch normale (non nav mode), disattivalo normalmente
                        ctrlLatchActive = false
                        updateStatusBarText()
                    } else {
                        // Ctrl latch dal nav mode ma siamo in un campo di testo - non dovrebbe succedere
                        // ma se succede, disattiva comunque
                        ctrlLatchActive = false
                        ctrlLatchFromNavMode = false
                        updateStatusBarText()
                    }
                    lastCtrlReleaseTime = 0 // Reset per evitare attivazioni indesiderate
                } else if (ctrlOneShot) {
                    // Se Ctrl one-shot è attivo, controlla se è un doppio tap veloce
                    if (currentTime - lastCtrlReleaseTime < DOUBLE_TAP_THRESHOLD && lastCtrlReleaseTime > 0) {
                        // Doppio tap rilevato mentre one-shot è attivo - attiva Ctrl latch
                        ctrlOneShot = false
                        ctrlLatchActive = true
                        Log.d(TAG, "Ctrl doppio tap: one-shot -> latch")
                        updateStatusBarText()
                        lastCtrlReleaseTime = 0 // Reset per evitare triple tap
                    } else {
                        // Singolo tap mentre one-shot è attivo - disattiva one-shot
                        ctrlOneShot = false
                        Log.d(TAG, "Ctrl one-shot disattivato")
                        updateStatusBarText()
                        lastCtrlReleaseTime = 0 // Reset per evitare attivazioni indesiderate
                    }
                } else {
                    // Se Ctrl latch non è attivo e one-shot non è attivo, controlla il doppio tap
                    if (currentTime - lastCtrlReleaseTime < DOUBLE_TAP_THRESHOLD && lastCtrlReleaseTime > 0) {
                        // Doppio tap rilevato - attiva Ctrl latch
                        ctrlLatchActive = true
                        updateStatusBarText()
                        lastCtrlReleaseTime = 0 // Reset per evitare triple tap
                    } else {
                        // Ctrl premuto da solo - attiva one-shot
                        ctrlOneShot = true
                        updateStatusBarText() // Aggiorna per mostrare "ctrl"
                    }
                }
                ctrlPressed = true
            }
            // Non consumiamo l'evento, lasciamo che Android gestisca Ctrl normalmente
            return super.onKeyDown(keyCode, event)
        }
        
        // Gestisci il doppio tap su Alt per attivare/disattivare Alt latch
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            if (!altPressed) {
                // Alt appena premuto
                altPhysicallyPressed = true
                val currentTime = System.currentTimeMillis()
                
                if (altLatchActive) {
                    // Se Alt latch è attivo, un singolo tap lo disattiva
                    altLatchActive = false
                    updateStatusBarText()
                    lastAltReleaseTime = 0 // Reset per evitare attivazioni indesiderate
                } else if (altOneShot) {
                    // Se Alt one-shot è attivo, controlla se è un doppio tap veloce
                    if (currentTime - lastAltReleaseTime < DOUBLE_TAP_THRESHOLD && lastAltReleaseTime > 0) {
                        // Doppio tap rilevato mentre one-shot è attivo - attiva Alt latch
                        altOneShot = false
                        altLatchActive = true
                        Log.d(TAG, "Alt doppio tap: one-shot -> latch")
                        updateStatusBarText()
                        lastAltReleaseTime = 0 // Reset per evitare triple tap
                    } else {
                        // Singolo tap mentre one-shot è attivo - disattiva one-shot
                        altOneShot = false
                        Log.d(TAG, "Alt one-shot disattivato")
                        updateStatusBarText()
                        lastAltReleaseTime = 0 // Reset per evitare attivazioni indesiderate
                    }
                } else {
                    // Se Alt latch non è attivo e one-shot non è attivo, controlla il doppio tap
                    if (currentTime - lastAltReleaseTime < DOUBLE_TAP_THRESHOLD && lastAltReleaseTime > 0) {
                        // Doppio tap rilevato - attiva Alt latch
                        altLatchActive = true
                        updateStatusBarText()
                        lastAltReleaseTime = 0 // Reset per evitare triple tap
                    } else {
                        // Alt premuto da solo - attiva one-shot
                        altOneShot = true
                        updateStatusBarText() // Aggiorna per mostrare "alt"
                    }
                }
                altPressed = true
            }
            // Non consumiamo l'evento, lasciamo che Android gestisca Alt normalmente
            return super.onKeyDown(keyCode, event)
        }
        
        // Gestisci il tasto SYM (toggle/latch)
        if (keyCode == KEYCODE_SYM) {
            // Toggle dello stato SYM
            symKeyActive = !symKeyActive
            updateStatusBarText()
            // Consumiamo l'evento per evitare che Android lo gestisca
            return true
        }
        
        // Gestisci il keycode 322 per cancellare l'ultima parola
        if (keyCode == 322) {
            if (TextSelectionHelper.deleteLastWord(inputConnection)) {
                // Consumiamo l'evento
                return true
            }
        }
        
        // Se il tasto è già premuto, consumiamo l'evento per evitare ripetizioni e popup
        if (altSymManager.hasPendingPress(keyCode)) {
            return true
        }
        
        // Gestisci gli shortcut Ctrl+tasto (controlla sia Ctrl premuto che Ctrl latch attivo o one-shot)
        // IMPORTANTE: Se siamo in nav mode (ctrlLatchFromNavMode), il Ctrl latch NON deve essere disattivato
        // MA: se siamo in un campo di testo, il nav mode è già stato disattivato, quindi usiamo il Ctrl latch normale
        if (event?.isCtrlPressed == true || ctrlLatchActive || ctrlOneShot) {
            // Se era one-shot, disattivalo dopo l'uso (ma NON se siamo in nav mode)
            val wasOneShot = ctrlOneShot
            if (wasOneShot && !ctrlLatchFromNavMode) {
                ctrlOneShot = false
                updateStatusBarText()
            }
            // IMPORTANTE: Se siamo in nav mode, NON disattivare mai il Ctrl latch dopo l'uso di un tasto
            // Il Ctrl latch rimane attivo finché non si esce dal nav mode
            
            // Controlla se esiste una mappatura Ctrl per questo tasto
            val ctrlMapping = ctrlKeyMap[keyCode]
            if (ctrlMapping != null) {
                when (ctrlMapping.type) {
                    "action" -> {
                        // Gestisci azioni speciali personalizzate
                        when (ctrlMapping.value) {
                            "expand_selection_left" -> {
                                // Tenta di espandere la selezione a sinistra
                                // Consumiamo sempre l'evento per evitare che il carattere 'W' venga inserito
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
                                // Tenta di espandere la selezione a destra
                                // Consumiamo sempre l'evento per evitare che il carattere 'R' venga inserito
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
                                // Esegui l'azione del context menu standard
                                val actionId = when (ctrlMapping.value) {
                                    "copy" -> android.R.id.copy
                                    "paste" -> android.R.id.paste
                                    "cut" -> android.R.id.cut
                                    "undo" -> android.R.id.undo
                                    "select_all" -> android.R.id.selectAll
                                    else -> null
                                }
                                if (actionId != null) {
                                    // Notifica l'evento con il nome dell'azione
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
                                    // Azione non riconosciuta, consuma l'evento per evitare inserimento carattere
                                    Log.d(TAG, "Ctrl+azione non riconosciuta: ${ctrlMapping.value}, evento consumato")
                                    return true
                                }
                            }
                        }
                    }
                    "keycode" -> {
                        // Invia il keycode mappato
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
                            // Notifica l'evento con il keycode di output
                            KeyboardEventTracker.notifyKeyEvent(
                                keyCode,
                                event,
                                "KEY_DOWN",
                                outputKeyCode = mappedKeyCode,
                                outputKeyCodeName = KeyboardEventTracker.getOutputKeyCodeName(mappedKeyCode)
                            )
                            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, mappedKeyCode))
                            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, mappedKeyCode))
                            return true
                        } else {
                            // Keycode non riconosciuto, consuma l'evento per evitare inserimento carattere
                            Log.d(TAG, "Ctrl+keycode non riconosciuto: ${ctrlMapping.value}, evento consumato")
                            return true
                        }
                    }
                }
            } else {
                // Ctrl è premuto ma il tasto non ha una mappatura valida
                // Gestione speciale per Backspace: cancella l'ultima parola
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    // Ctrl+Backspace cancella l'ultima parola
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
                // Eccezione per Enter: continua a funzionare normalmente
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    // Lascia passare Enter anche con Ctrl premuto
                    Log.d(TAG, "Ctrl+Enter senza mappatura, passo l'evento ad Android")
                    return super.onKeyDown(keyCode, event)
                }
                // Per tutti gli altri tasti senza mappatura, consumiamo l'evento
                Log.d(TAG, "Ctrl+tasto senza mappatura (keyCode: $keyCode), evento consumato")
                return true
            }
        }
        
        // Se Alt è premuto o Alt latch è attivo o Alt one-shot, gestisci la combinazione Alt+tasto
        if (event?.isAltPressed == true || altLatchActive || altOneShot) {
            altSymManager.cancelPendingLongPress(keyCode)
            if (altOneShot) {
                altOneShot = false
                refreshStatusBar()
            }
            val result = altSymManager.handleAltCombination(
                keyCode,
                inputConnection,
                event
            ) { defaultKeyCode, defaultEvent ->
                super.onKeyDown(defaultKeyCode, defaultEvent)
            }
            // Se un carattere Alt è stato inserito, aggiorna le variazioni
            if (result) {
                updateStatusBarText()
            }
            return result
        }
        
        // Se SYM è attivo, controlla prima la mappa SYM
        if (symKeyActive) {
            val symChar = altSymManager.getSymMappings()[keyCode]
            if (symChar != null) {
                // Inserisci l'emoji dalla mappa SYM
                inputConnection.commitText(symChar, 1)
                // Consumiamo l'evento
                return true
            }
        }
        
        // Gestisci Shift one-shot PRIMA di tutto (deve avere priorità)
        if (shiftOneShot && event != null && event.unicodeChar != 0) {
            var char = event.unicodeChar.toChar().toString()
            if (char.isNotEmpty() && char[0].isLetter()) {
                Log.d(TAG, "Shift one-shot attivo, carattere originale: $char")
                // Forza sempre il maiuscolo quando shiftOneShot è attivo
                char = char.uppercase()
                Log.d(TAG, "Shift one-shot, carattere modificato: $char")
                shiftOneShot = false
                inputConnection.commitText(char, 1)
                // Aggiorna le variazioni dopo l'inserimento
                updateStatusBarText()
                return true
            }
        }
        
        // Gestisci auto-maiuscola per la prima lettera (dopo Shift one-shot, prima delle mappature Alt)
        if (event != null && event.unicodeChar != 0 && !event.isShiftPressed && !capsLockEnabled) {
            val char = event.unicodeChar.toChar().toString()
            if (char.isNotEmpty() && char[0].isLetter() && char[0].isLowerCase()) {
                // Controlla se l'auto-maiuscola è abilitata
                val autoCapitalizeEnabled = SettingsManager.getAutoCapitalizeFirstLetter(this)
                if (autoCapitalizeEnabled) {
                    // Controlla se il testo prima del cursore è vuoto o contiene solo spazi
                    val textBeforeCursor = inputConnection.getTextBeforeCursor(1000, 0)
                    if (textBeforeCursor != null) {
                        val trimmedText = textBeforeCursor.trim()
                        // Se il testo prima del cursore è vuoto o contiene solo spazi, è la prima lettera
                        if (trimmedText.isEmpty()) {
                            Log.d(TAG, "Auto-maiuscola: prima lettera rilevata, carattere: $char")
                            val capitalizedChar = char.uppercase()
                            inputConnection.commitText(capitalizedChar, 1)
                            // Aggiorna le variazioni dopo l'inserimento
                            updateStatusBarText()
                            return true
                        }
                    }
                }
            }
        }
        
        // Controlla se questo tasto ha una mappatura Alt
        if (altSymManager.hasAltMapping(keyCode)) {
            // Se siamo in un campo numerico, inserisci direttamente il carattere Alt
            if (isNumericField) {
                val altChar = altSymManager.getAltMappings()[keyCode]
                if (altChar != null) {
                    Log.d(TAG, "Campo numerico: inserimento diretto carattere Alt '$altChar' per keyCode $keyCode")
                    inputConnection.commitText(altChar, 1)
                    // Aggiorna le variazioni dopo l'inserimento
                    updateStatusBarText()
                    return true
                }
            }
            // Altrimenti, usa la gestione normale con long press
            altSymManager.handleKeyWithAltMapping(
                keyCode,
                event,
                capsLockEnabled,
                inputConnection
            )
            return true
        }
        
        // Se non ha mappatura, gestisci Caps Lock per i caratteri normali
        // Applica Caps Lock ai caratteri alfabetici
        if (event != null && event.unicodeChar != 0) {
            var char = event.unicodeChar.toChar().toString()
            var shouldConsume = false
            
            // Applica Caps Lock se attivo (ma solo se Shift non è premuto)
            if (capsLockEnabled && event.isShiftPressed != true && char.isNotEmpty() && char[0].isLetter()) {
                char = char.uppercase()
                shouldConsume = true
            } else if (capsLockEnabled && event.isShiftPressed == true && char.isNotEmpty() && char[0].isLetter()) {
                // Se Caps Lock è attivo e Shift è premuto, rendi minuscolo
                char = char.lowercase()
                shouldConsume = true
            }
            
            if (shouldConsume) {
                inputConnection.commitText(char, 1)
                // Aggiorna le variazioni dopo l'inserimento
                updateStatusBarText()
                return true
            }
        }
        
        // Se non ha mappatura, controlla se il carattere ha variazioni disponibili
        // Se sì, gestiscilo noi stessi per poter visualizzare le variazioni
        if (event != null && event.unicodeChar != 0) {
            val char = event.unicodeChar.toChar()
            // Controlla se il carattere ha variazioni disponibili
            if (variationsMap.containsKey(char)) {
                // Inserisci il carattere noi stessi per poter visualizzare le variazioni
                inputConnection.commitText(char.toString(), 1)
                // Aggiorna le variazioni dopo l'inserimento
                updateStatusBarText()
                return true
            }
            // Se il carattere non ha variazioni, le variazioni precedenti rimangono visibili
            // (solo visualizzazione, nessuna azione)
        }
        
        // Se non ha mappatura, lascia che Android gestisca normalmente
        // Aggiorna le variazioni dopo che Android ha gestito l'evento
        val result = super.onKeyDown(keyCode, event)
        // Aggiorna le variazioni dopo qualsiasi operazione che potrebbe cambiare il testo o la posizione del cursore
        // (caratteri inseriti, Backspace, frecce, ecc.)
        if (result) {
            // Usa un post per aggiornare le variazioni dopo che Android ha gestito l'evento
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                updateStatusBarText()
            }
        }
        return result
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // Eccezione per il nav mode: gestisci Ctrl anche quando non siamo in un campo di testo
        val isCtrlKey = keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT
        
        if (!isInputViewActive && isCtrlKey) {
            // Gestisci il rilascio di Ctrl nel nav mode
            if (ctrlPressed) {
                val result = NavModeHandler.handleCtrlKeyUp()
                result.ctrlPressed?.let { ctrlPressed = it }
                result.ctrlPhysicallyPressed?.let { ctrlPhysicallyPressed = it }
                result.lastCtrlReleaseTime?.let { lastCtrlReleaseTime = it }
                updateStatusBarText()
                Log.d(TAG, "Nav mode: Ctrl rilasciato")
            }
            // Consumiamo l'evento per evitare che Android lo gestisca
            return true
        }
        
        val inputConnection = currentInputConnection ?: return super.onKeyUp(keyCode, event)
        
        // Notifica sempre l'evento al tracker (anche se viene consumato)
        KeyboardEventTracker.notifyKeyEvent(keyCode, event, "KEY_UP")
        
        // Gestisci il rilascio di Shift per il doppio tap
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (shiftPressed) {
                lastShiftReleaseTime = System.currentTimeMillis()
                shiftPressed = false
                shiftPhysicallyPressed = false
                // Non disattivare shiftOneShot qui - viene disattivato quando viene usato
                // Se viene rilasciato senza essere usato, rimane attivo per il prossimo tasto
                updateStatusBarText() // Aggiorna per rimuovere "shift" fisico
            }
            // Non consumiamo l'evento, lasciamo che Android gestisca Shift normalmente
            return super.onKeyUp(keyCode, event)
        }
        
        // Gestisci il rilascio di Ctrl per il doppio tap
        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            if (ctrlPressed) {
                lastCtrlReleaseTime = System.currentTimeMillis()
                ctrlPressed = false
                ctrlPhysicallyPressed = false
                // Non disattivare ctrlOneShot qui - viene disattivato quando viene usato
                // Se viene rilasciato senza essere usato, rimane attivo per il prossimo tasto
                updateStatusBarText() // Aggiorna per rimuovere "ctrl" fisico
            }
            // Non consumiamo l'evento, lasciamo che Android gestisca Ctrl normalmente
            return super.onKeyUp(keyCode, event)
        }
        
        // Gestisci il rilascio di Alt per il doppio tap
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            if (altPressed) {
                lastAltReleaseTime = System.currentTimeMillis()
                altPressed = false
                altPhysicallyPressed = false
                // Non disattivare altOneShot qui - viene disattivato quando viene usato
                // Se viene rilasciato senza essere usato, rimane attivo per il prossimo tasto
                updateStatusBarText() // Aggiorna per rimuovere "alt" fisico
            }
            // Non consumiamo l'evento, lasciamo che Android gestisca Alt normalmente
            return super.onKeyUp(keyCode, event)
        }
        
        // Gestisci il rilascio del tasto SYM (non serve fare nulla, è un toggle)
        if (keyCode == KEYCODE_SYM) {
            // Consumiamo l'evento
            return true
        }
        
        if (altSymManager.handleKeyUp(keyCode, symKeyActive)) {
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
}

