package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.TextView
import it.palsoftware.pastiera.inputmethod.KeyboardEventTracker
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Servizio di immissione specializzato per tastiere fisiche.
 * Gestisce funzionalità avanzate come il long press che simula Alt+tasto.
 */
class PhysicalKeyboardInputMethodService : InputMethodService() {

    companion object {
        private const val TAG = "PastieraInputMethod"
    }

    // Mappa per tracciare i tasti premuti e il tempo di pressione
    private val pressedKeys = ConcurrentHashMap<Int, Long>()
    
    // Mappa per tracciare i Runnable dei long press in attesa
    private val longPressRunnables = ConcurrentHashMap<Int, Runnable>()
    
    // Mappa per tracciare se un long press è stato attivato
    private val longPressActivated = ConcurrentHashMap<Int, Boolean>()
    
    // Mappa per tracciare i caratteri normali inseriti (per poterli cancellare in caso di long press)
    private val insertedNormalChars = ConcurrentHashMap<Int, String>()
    
    // Handler per gestire i long press
    private val handler = Handler(Looper.getMainLooper())
    
    // Soglia per considerare un long press (in millisecondi) - caricata dalle preferenze
    private var longPressThreshold: Long = 500L
    
    // SharedPreferences per le impostazioni
    private lateinit var prefs: SharedPreferences
    
    // Keycode per il tasto SYM
    private val KEYCODE_SYM = 63
    
    // Stato per tracciare se SYM è attualmente attivo (latch/toggle)
    private var symKeyActive = false
    
    // Mappatura Alt+tasto -> carattere speciale (caricata da JSON)
    private val altKeyMap = mutableMapOf<Int, String>()
    
    // Mappatura SYM+tasto -> emoji (caricata da JSON)
    private val symKeyMap = mutableMapOf<Int, String>()
    
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
    
    // Riferimento alla status bar per aggiornare l'icona Caps Lock
    private var statusBarTextView: TextView? = null
    private var statusBarLayout: LinearLayout? = null
    private var emojiMapTextView: TextView? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate() chiamato")
        prefs = getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
        loadLongPressThreshold()
        loadAltKeyMappings()
        loadSymKeyMappings()
        Log.d(TAG, "onCreate() completato")
    }
    
    /**
     * Carica la soglia del long press dalle preferenze.
     */
    private fun loadLongPressThreshold() {
        longPressThreshold = prefs.getLong("long_press_threshold", 500L).coerceIn(50L, 1000L)
    }

    override fun onCreateInputView(): View? {
        Log.d(TAG, "onCreateInputView() chiamato - statusBarLayout è null: ${statusBarLayout == null}")
        // Crea una barra di stato che mostra "Pastiera attiva"
        statusBarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.parseColor("#2196F3")) // Blu Material
        }
        
        statusBarTextView = TextView(this).apply {
            updateStatusBarText()
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(16, 12, 16, 12)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        statusBarLayout?.addView(statusBarTextView)
        
        // TextView per la mappa emoji (inizialmente nascosto)
        emojiMapTextView = TextView(this).apply {
            text = buildEmojiMapText()
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(16, 8, 16, 12)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }
        
        statusBarLayout?.addView(emojiMapTextView)
        
        updateStatusBarLayout()
        
        Log.d(TAG, "onCreateInputView() completato - view creata: ${statusBarLayout != null}")
        return statusBarLayout
    }
    
    /**
     * Costruisce il testo della mappa emoji da mostrare nella status bar.
     */
    private fun buildEmojiMapText(): String {
        val keyLabels = mapOf(
            KeyEvent.KEYCODE_Q to "Q", KeyEvent.KEYCODE_W to "W", KeyEvent.KEYCODE_E to "E",
            KeyEvent.KEYCODE_R to "R", KeyEvent.KEYCODE_T to "T", KeyEvent.KEYCODE_Y to "Y",
            KeyEvent.KEYCODE_U to "U", KeyEvent.KEYCODE_I to "I", KeyEvent.KEYCODE_O to "O",
            KeyEvent.KEYCODE_P to "P", KeyEvent.KEYCODE_A to "A", KeyEvent.KEYCODE_S to "S",
            KeyEvent.KEYCODE_D to "D", KeyEvent.KEYCODE_F to "F", KeyEvent.KEYCODE_G to "G",
            KeyEvent.KEYCODE_H to "H", KeyEvent.KEYCODE_J to "J", KeyEvent.KEYCODE_K to "K",
            KeyEvent.KEYCODE_L to "L", KeyEvent.KEYCODE_Z to "Z", KeyEvent.KEYCODE_X to "X",
            KeyEvent.KEYCODE_C to "C", KeyEvent.KEYCODE_V to "V", KeyEvent.KEYCODE_B to "B",
            KeyEvent.KEYCODE_N to "N", KeyEvent.KEYCODE_M to "M"
        )
        
        val rows = mutableListOf<String>()
        val keys = listOf(
            listOf(KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P),
            listOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L),
            listOf(KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M)
        )
        
        for (row in keys) {
            val rowText = row.joinToString("  ") { keyCode ->
                val label = keyLabels[keyCode] ?: ""
                val emoji = symKeyMap[keyCode] ?: ""
                "$label:$emoji"
            }
            rows.add(rowText)
        }
        
        return rows.joinToString("\n")
    }
    
    /**
     * Aggiorna il layout della status bar per mostrare/nascondere la mappa emoji.
     */
    private fun updateStatusBarLayout() {
        if (symKeyActive) {
            emojiMapTextView?.visibility = View.VISIBLE
        } else {
            emojiMapTextView?.visibility = View.GONE
        }
    }
    
    /**
     * Forza la creazione e visualizzazione della view.
     * Viene chiamata quando viene premuto il primo tasto fisico.
     */
    private fun ensureInputViewCreated() {
        Log.d(TAG, "ensureInputViewCreated() chiamato - statusBarLayout è null: ${statusBarLayout == null}")
        
        // Se la view non esiste, creala
        if (statusBarLayout == null) {
            Log.d(TAG, "Creazione della view...")
            val newView = onCreateInputView()
            Log.d(TAG, "View creata: ${newView != null}")
            if (newView != null) {
                Log.d(TAG, "Chiamata setInputView()...")
                setInputView(newView)
                Log.d(TAG, "Chiamata requestShowSelf() per forzare la visualizzazione...")
                requestShowSelf(0)
                Log.d(TAG, "ensureInputViewCreated() completato")
            } else {
                Log.w(TAG, "View è null, non posso chiamare setInputView()")
            }
        } else {
            // View già esistente: controlla se ha già un parent
            val hasParent = statusBarLayout?.parent != null
            Log.d(TAG, "View già esistente, hasParent: $hasParent")
            
            // Aggiorna sempre il testo della status bar quando viene mostrata
            updateStatusBarText()
            
            if (!hasParent) {
                // La view esiste ma non è ancora impostata, impostala
                Log.d(TAG, "Chiamata setInputView() per view esistente senza parent...")
                setInputView(statusBarLayout)
            } else {
                Log.d(TAG, "View ha già un parent, non chiamo setInputView()")
            }
            
            // Forza sempre la visualizzazione con requestShowSelf()
            Log.d(TAG, "Chiamata requestShowSelf() per forzare la visualizzazione...")
            requestShowSelf(0)
            Log.d(TAG, "ensureInputViewCreated() completato")
        }
    }
    
    /**
     * Aggiorna il testo della status bar per mostrare lo stato dei tasti modificatori.
     * Mostra minuscolo quando premuti fisicamente, maiuscolo quando in latch.
     */
    private fun updateStatusBarText() {
        val statusParts = mutableListOf<String>()
        
        // Caps Lock (Shift latch) - mostra sempre 🔒 quando attivo
        if (capsLockEnabled) {
            statusParts.add("🔒")
        }
        
        // SYM key
        if (symKeyActive) {
            statusParts.add("🔣")
        }
        
        // Shift: minuscolo se premuto fisicamente, maiuscolo se in latch (Caps Lock), minuscolo se one-shot
        if (capsLockEnabled) {
            statusParts.add("SHIFT")
        } else if (shiftPhysicallyPressed) {
            statusParts.add("shift")
        } else if (shiftOneShot) {
            statusParts.add("shift")
        }
        
        // Ctrl: minuscolo se premuto fisicamente, maiuscolo se in latch, minuscolo se one-shot
        if (ctrlLatchActive) {
            statusParts.add("CTRL")
        } else if (ctrlPhysicallyPressed) {
            statusParts.add("ctrl")
        } else if (ctrlOneShot) {
            statusParts.add("ctrl")
        }
        
        // Alt: minuscolo se premuto fisicamente, maiuscolo se in latch, minuscolo se one-shot
        if (altLatchActive) {
            statusParts.add("ALT")
        } else if (altPhysicallyPressed) {
            statusParts.add("alt")
        } else if (altOneShot) {
            statusParts.add("alt")
        }
        
        val status = if (statusParts.isNotEmpty()) {
            "${statusParts.joinToString(" ")} Pastiera attiva"
        } else {
            "Pastiera attiva"
        }
        statusBarTextView?.text = status
        updateStatusBarLayout()
    }
    
    /**
     * Carica le mappature Alt+tasto dal file JSON.
     */
    private fun loadAltKeyMappings() {
        try {
            val inputStream: InputStream = assets.open("alt_key_mappings.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")
            
            // Mappa i nomi dei keycode alle costanti KeyEvent
            val keyCodeMap = mapOf(
                "KEYCODE_Q" to KeyEvent.KEYCODE_Q,
                "KEYCODE_W" to KeyEvent.KEYCODE_W,
                "KEYCODE_E" to KeyEvent.KEYCODE_E,
                "KEYCODE_R" to KeyEvent.KEYCODE_R,
                "KEYCODE_T" to KeyEvent.KEYCODE_T,
                "KEYCODE_Y" to KeyEvent.KEYCODE_Y,
                "KEYCODE_U" to KeyEvent.KEYCODE_U,
                "KEYCODE_I" to KeyEvent.KEYCODE_I,
                "KEYCODE_O" to KeyEvent.KEYCODE_O,
                "KEYCODE_P" to KeyEvent.KEYCODE_P,
                "KEYCODE_A" to KeyEvent.KEYCODE_A,
                "KEYCODE_S" to KeyEvent.KEYCODE_S,
                "KEYCODE_D" to KeyEvent.KEYCODE_D,
                "KEYCODE_F" to KeyEvent.KEYCODE_F,
                "KEYCODE_G" to KeyEvent.KEYCODE_G,
                "KEYCODE_H" to KeyEvent.KEYCODE_H,
                "KEYCODE_J" to KeyEvent.KEYCODE_J,
                "KEYCODE_K" to KeyEvent.KEYCODE_K,
                "KEYCODE_L" to KeyEvent.KEYCODE_L,
                "KEYCODE_Z" to KeyEvent.KEYCODE_Z,
                "KEYCODE_X" to KeyEvent.KEYCODE_X,
                "KEYCODE_C" to KeyEvent.KEYCODE_C,
                "KEYCODE_V" to KeyEvent.KEYCODE_V,
                "KEYCODE_B" to KeyEvent.KEYCODE_B,
                "KEYCODE_N" to KeyEvent.KEYCODE_N,
                "KEYCODE_M" to KeyEvent.KEYCODE_M,
                "KEYCODE_1" to KeyEvent.KEYCODE_1,
                "KEYCODE_2" to KeyEvent.KEYCODE_2,
                "KEYCODE_3" to KeyEvent.KEYCODE_3,
                "KEYCODE_4" to KeyEvent.KEYCODE_4,
                "KEYCODE_5" to KeyEvent.KEYCODE_5,
                "KEYCODE_6" to KeyEvent.KEYCODE_6,
                "KEYCODE_7" to KeyEvent.KEYCODE_7,
                "KEYCODE_8" to KeyEvent.KEYCODE_8,
                "KEYCODE_9" to KeyEvent.KEYCODE_9,
                "KEYCODE_0" to KeyEvent.KEYCODE_0,
                "KEYCODE_MINUS" to KeyEvent.KEYCODE_MINUS,
                "KEYCODE_EQUALS" to KeyEvent.KEYCODE_EQUALS,
                "KEYCODE_LEFT_BRACKET" to KeyEvent.KEYCODE_LEFT_BRACKET,
                "KEYCODE_RIGHT_BRACKET" to KeyEvent.KEYCODE_RIGHT_BRACKET,
                "KEYCODE_SEMICOLON" to KeyEvent.KEYCODE_SEMICOLON,
                "KEYCODE_APOSTROPHE" to KeyEvent.KEYCODE_APOSTROPHE,
                "KEYCODE_COMMA" to KeyEvent.KEYCODE_COMMA,
                "KEYCODE_PERIOD" to KeyEvent.KEYCODE_PERIOD,
                "KEYCODE_SLASH" to KeyEvent.KEYCODE_SLASH
            )
            
            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyCodeMap[keyName]
                val character = mappingsObject.getString(keyName)
                
                if (keyCode != null) {
                    altKeyMap[keyCode] = character
                }
            }
        } catch (e: Exception) {
            // In caso di errore, usa mappature di default
            e.printStackTrace()
            // Fallback a mappature di base
            altKeyMap[KeyEvent.KEYCODE_T] = "("
            altKeyMap[KeyEvent.KEYCODE_Y] = ")"
        }
    }
    
    /**
     * Carica le mappature SYM+tasto dal file JSON.
     */
    private fun loadSymKeyMappings() {
        try {
            val inputStream: InputStream = assets.open("sym_key_mappings.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")
            
            // Mappa i nomi dei keycode alle costanti KeyEvent (stessa mappa di loadAltKeyMappings)
            val keyCodeMap = mapOf(
                "KEYCODE_Q" to KeyEvent.KEYCODE_Q,
                "KEYCODE_W" to KeyEvent.KEYCODE_W,
                "KEYCODE_E" to KeyEvent.KEYCODE_E,
                "KEYCODE_R" to KeyEvent.KEYCODE_R,
                "KEYCODE_T" to KeyEvent.KEYCODE_T,
                "KEYCODE_Y" to KeyEvent.KEYCODE_Y,
                "KEYCODE_U" to KeyEvent.KEYCODE_U,
                "KEYCODE_I" to KeyEvent.KEYCODE_I,
                "KEYCODE_O" to KeyEvent.KEYCODE_O,
                "KEYCODE_P" to KeyEvent.KEYCODE_P,
                "KEYCODE_A" to KeyEvent.KEYCODE_A,
                "KEYCODE_S" to KeyEvent.KEYCODE_S,
                "KEYCODE_D" to KeyEvent.KEYCODE_D,
                "KEYCODE_F" to KeyEvent.KEYCODE_F,
                "KEYCODE_G" to KeyEvent.KEYCODE_G,
                "KEYCODE_H" to KeyEvent.KEYCODE_H,
                "KEYCODE_J" to KeyEvent.KEYCODE_J,
                "KEYCODE_K" to KeyEvent.KEYCODE_K,
                "KEYCODE_L" to KeyEvent.KEYCODE_L,
                "KEYCODE_Z" to KeyEvent.KEYCODE_Z,
                "KEYCODE_X" to KeyEvent.KEYCODE_X,
                "KEYCODE_C" to KeyEvent.KEYCODE_C,
                "KEYCODE_V" to KeyEvent.KEYCODE_V,
                "KEYCODE_B" to KeyEvent.KEYCODE_B,
                "KEYCODE_N" to KeyEvent.KEYCODE_N,
                "KEYCODE_M" to KeyEvent.KEYCODE_M
            )
            
            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyCodeMap[keyName]
                val emoji = mappingsObject.getString(keyName)
                
                if (keyCode != null) {
                    symKeyMap[keyCode] = emoji
                }
            }
        } catch (e: Exception) {
            // In caso di errore, usa mappature di default
            e.printStackTrace()
            // Fallback a emoji di base
            symKeyMap[KeyEvent.KEYCODE_Q] = "😀"
            symKeyMap[KeyEvent.KEYCODE_W] = "😂"
        }
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        Log.d(TAG, "onStartInput() chiamato - restarting: $restarting, info: ${info?.packageName}")
        // Disabilita i suggerimenti per evitare popup
        if (info != null) {
            info.inputType = info.inputType or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "onStartInputView() chiamato - restarting: $restarting, statusBarLayout: ${statusBarLayout != null}")
        // Ricarica la soglia del long press (potrebbe essere cambiata nelle impostazioni)
        loadLongPressThreshold()
        // Reset dello stato quando si inizia a inserire testo
        pressedKeys.clear()
        longPressActivated.clear()
        insertedNormalChars.clear()
        // Non resettare SYM e Caps Lock - rimangono attivi finché non vengono disattivati manualmente
        // Cancella tutti i long press in attesa
        longPressRunnables.values.forEach { handler.removeCallbacks(it) }
        longPressRunnables.clear()
    }
    
    override fun onWindowShown() {
        super.onWindowShown()
        Log.d(TAG, "onWindowShown() chiamato - window è visibile")
        // Aggiorna il testo quando la window viene mostrata
        updateStatusBarText()
    }
    
    override fun onWindowHidden() {
        super.onWindowHidden()
        Log.d(TAG, "onWindowHidden() chiamato - window è nascosta")
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        val inputConnection = currentInputConnection ?: return super.onKeyLongPress(keyCode, event)
        
        // Intercetta i long press PRIMA che Android li gestisca
        if (altKeyMap.containsKey(keyCode)) {
            // Consumiamo l'evento per evitare il popup di Android
            return true
        }
        
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val inputConnection = currentInputConnection ?: return super.onKeyDown(keyCode, event)
        
        Log.d(TAG, "onKeyDown() - keyCode: $keyCode, statusBarLayout: ${statusBarLayout != null}, inputConnection: ${inputConnection != null}")
        
        // Notifica sempre l'evento al tracker (anche se viene consumato)
        KeyboardEventTracker.notifyKeyEvent(keyCode, event, "KEY_DOWN")
        
        // Forza la creazione della view al primo tasto premuto (escludendo solo i tasti modificatori)
        // Questo assicura che la status bar sia visibile anche se la tastiera virtuale non è abilitata
        val isModifierKey = keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || 
                           keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT ||
                           keyCode == KeyEvent.KEYCODE_CTRL_LEFT || 
                           keyCode == KeyEvent.KEYCODE_CTRL_RIGHT ||
                           keyCode == KeyEvent.KEYCODE_ALT_LEFT || 
                           keyCode == KeyEvent.KEYCODE_ALT_RIGHT
        
        Log.d(TAG, "onKeyDown() - isModifierKey: $isModifierKey")
        
        if (!isModifierKey) {
            Log.d(TAG, "onKeyDown() - chiamata ensureInputViewCreated() per tasto non modificatore")
            ensureInputViewCreated()
        } else {
            Log.d(TAG, "onKeyDown() - tasto modificatore, salto ensureInputViewCreated()")
        }
        
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
                        updateStatusBarText()
                        lastShiftReleaseTime = 0 // Reset per evitare triple tap
                    } else {
                        // Singolo tap mentre one-shot è attivo - disattiva one-shot
                        shiftOneShot = false
                        Log.d(TAG, "Shift one-shot disattivato")
                        updateStatusBarText()
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
                    ctrlLatchActive = false
                    updateStatusBarText()
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
            deleteLastWord(inputConnection)
            // Consumiamo l'evento
            return true
        }
        
        // Se il tasto è già premuto, consumiamo l'evento per evitare ripetizioni e popup
        if (pressedKeys.containsKey(keyCode)) {
            return true
        }
        
        // Gestisci gli shortcut Ctrl+tasto (controlla sia Ctrl premuto che Ctrl latch attivo o one-shot)
        if (event?.isCtrlPressed == true || ctrlLatchActive || ctrlOneShot) {
            // Se era one-shot, disattivalo dopo l'uso
            val wasOneShot = ctrlOneShot
            if (wasOneShot) {
                ctrlOneShot = false
                updateStatusBarText()
            }
            
            when (keyCode) {
                KeyEvent.KEYCODE_W -> {
                    // Ctrl+W: Freccia su
                    inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
                    inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP))
                    return true
                }
                KeyEvent.KEYCODE_S -> {
                    // Ctrl+S: Freccia giù
                    inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
                    inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN))
                    return true
                }
                KeyEvent.KEYCODE_A -> {
                    // Ctrl+A: Freccia sinistra
                    inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                    inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
                    return true
                }
                KeyEvent.KEYCODE_D -> {
                    // Ctrl+D: Freccia destra
                    inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                    inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
                    return true
                }
                KeyEvent.KEYCODE_C -> {
                    // Ctrl+C: Copia
                    inputConnection.performContextMenuAction(android.R.id.copy)
                    return true
                }
                KeyEvent.KEYCODE_V -> {
                    // Ctrl+V: Incolla
                    inputConnection.performContextMenuAction(android.R.id.paste)
                    return true
                }
                KeyEvent.KEYCODE_X -> {
                    // Ctrl+X: Taglia
                    inputConnection.performContextMenuAction(android.R.id.cut)
                    return true
                }
                KeyEvent.KEYCODE_Z -> {
                    // Ctrl+Z: Annulla (se supportato)
                    inputConnection.performContextMenuAction(android.R.id.undo)
                    return true
                }
            }
        }
        
        // Se Alt è premuto o Alt latch è attivo o Alt one-shot, gestisci la combinazione Alt+tasto
        if (event?.isAltPressed == true || altLatchActive || altOneShot) {
            // Cancella eventuali long press in attesa per questo tasto
            longPressRunnables[keyCode]?.let { handler.removeCallbacks(it) }
            longPressRunnables.remove(keyCode)
            // Se era one-shot, disattivalo dopo l'uso
            if (altOneShot) {
                altOneShot = false
                updateStatusBarText()
            }
            return handleAltKey(keyCode, inputConnection, event)
        }
        
        // Se SYM è attivo, controlla prima la mappa SYM
        if (symKeyActive) {
            val symChar = symKeyMap[keyCode]
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
                updateStatusBarText()
                inputConnection.commitText(char, 1)
                return true
            }
        }
        
        // Controlla se questo tasto ha una mappatura Alt
        val hasAltMapping = altKeyMap.containsKey(keyCode)
        
        if (hasAltMapping) {
            
            // Consumiamo l'evento per evitare che Android mostri il popup
            // Registra il tempo di pressione del tasto
            pressedKeys[keyCode] = System.currentTimeMillis()
            longPressActivated[keyCode] = false
            
            // Inserisci SUBITO il carattere normale a schermo
            var normalChar = if (event != null && event.unicodeChar != 0) {
                event.unicodeChar.toChar().toString()
            } else {
                ""
            }
            
            // Applica Caps Lock se attivo (ma solo se Shift non è premuto)
            if (normalChar.isNotEmpty() && capsLockEnabled && event?.isShiftPressed != true) {
                // Se Caps Lock è attivo e Shift non è premuto, rendi maiuscolo
                normalChar = normalChar.uppercase()
            } else if (normalChar.isNotEmpty() && capsLockEnabled && event?.isShiftPressed == true) {
                // Se Caps Lock è attivo e Shift è premuto, rendi minuscolo (comportamento standard)
                normalChar = normalChar.lowercase()
            }
            
            if (normalChar.isNotEmpty()) {
                inputConnection.commitText(normalChar, 1)
                insertedNormalChars[keyCode] = normalChar
            }
            
            // Gestisci il long press per simulare Alt+tasto
            scheduleLongPress(keyCode, inputConnection)
            
            // Consumiamo l'evento per evitare il popup standard di Android
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
                return true
            }
        }
        
        // Se non ha mappatura, lascia che Android gestisca normalmente
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
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
        
        val pressStartTime = pressedKeys.remove(keyCode)
        
        // Controlla PRIMA se è stato un long press (prima di rimuovere dalla mappa)
        val wasLongPress = longPressActivated.containsKey(keyCode)
        if (wasLongPress) {
            // Rimuovi dalla mappa dopo aver controllato
            longPressActivated.remove(keyCode)
        }
        
        val insertedChar = insertedNormalChars.remove(keyCode)
        
        // Cancella il long press in attesa per questo tasto
        val longPressRunnable = longPressRunnables.remove(keyCode)
        longPressRunnable?.let { handler.removeCallbacks(it) }
        
        // Se il tasto ha una mappatura Alt e abbiamo gestito l'evento in onKeyDown
        // Ma solo se SYM non era attivo (perché se SYM era attivo, abbiamo già inserito l'emoji)
        if (pressStartTime != null && altKeyMap.containsKey(keyCode) && !symKeyActive) {
            // Consumiamo sempre l'evento per evitare il popup
            if (wasLongPress) {
                // È stato un long press, il carattere normale è già stato cancellato
                // e il carattere Alt è già stato inserito nel Runnable
                // Non fare nulla
                return true
            } else {
                // Pressione normale, il carattere normale è già stato inserito in onKeyDown
                // Non fare nulla
                return true
            }
        }
        
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Gestisce la combinazione Alt+tasto premuta fisicamente.
     */
    private fun handleAltKey(
        keyCode: Int,
        inputConnection: InputConnection,
        event: KeyEvent?
    ): Boolean {
        val altChar = altKeyMap[keyCode]
        
        if (altChar != null) {
            // Invia il carattere speciale corrispondente ad Alt+tasto
            inputConnection.commitText(altChar, 1)
            return true
        }
        
        // Se non c'è una mappatura, lascia che il sistema gestisca il tasto
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Programma il controllo del long press per simulare Alt+tasto.
     */
    private fun scheduleLongPress(
        keyCode: Int,
        inputConnection: InputConnection
    ) {
        // Ricarica sempre il valore dalle preferenze per applicare le modifiche immediatamente
        loadLongPressThreshold()
        
        // Crea un Runnable per gestire il long press
        val longPressRunnable = Runnable {
            // Se il tasto è ancora premuto dopo il threshold, è un long press
            if (pressedKeys.containsKey(keyCode)) {
                val altChar = altKeyMap[keyCode]
                val insertedChar = insertedNormalChars[keyCode]
                
                if (altChar != null) {
                    // Segna che il long press è stato attivato
                    longPressActivated[keyCode] = true
                    
                    // Cancella il carattere normale che è stato inserito in onKeyDown
                    if (insertedChar != null && insertedChar.isNotEmpty()) {
                        // Cancella il carattere normale (backspace)
                        inputConnection.deleteSurroundingText(1, 0)
                    }
                    
                    // Inserisci il carattere Alt+tasto
                    inputConnection.commitText(altChar, 1)
                    
                    // Rimuovi il carattere normale dalla mappa
                    insertedNormalChars.remove(keyCode)
                    
                    // Rimuovi anche il Runnable dalla mappa
                    longPressRunnables.remove(keyCode)
                }
            }
        }
        
        // Salva il Runnable per poterlo cancellare se necessario
        longPressRunnables[keyCode] = longPressRunnable
        
        // Programma l'esecuzione dopo il threshold (usa il valore dalle preferenze)
        handler.postDelayed(longPressRunnable, longPressThreshold)
    }

    /**
     * Cancella l'ultima parola prima del cursore.
     */
    private fun deleteLastWord(inputConnection: InputConnection) {
        // Ottieni il testo prima del cursore (fino a 100 caratteri)
        val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0)
        
        if (textBeforeCursor != null && textBeforeCursor.isNotEmpty()) {
            // Trova l'ultima parola (separata da spazi o all'inizio del testo)
            var endIndex = textBeforeCursor.length
            var startIndex = endIndex
            
            // Trova la fine dell'ultima parola (ignora spazi alla fine)
            while (startIndex > 0 && textBeforeCursor[startIndex - 1].isWhitespace()) {
                startIndex--
            }
            
            // Trova l'inizio dell'ultima parola (primo spazio o inizio del testo)
            while (startIndex > 0 && !textBeforeCursor[startIndex - 1].isWhitespace()) {
                startIndex--
            }
            
            // Calcola quanti caratteri cancellare
            val charsToDelete = endIndex - startIndex
            
            if (charsToDelete > 0) {
                // Cancella l'ultima parola (inclusi eventuali spazi dopo)
                inputConnection.deleteSurroundingText(charsToDelete, 0)
            }
        }
    }
    
    /**
     * Aggiunge una nuova mappatura Alt+tasto -> carattere.
     */
    fun addAltKeyMapping(keyCode: Int, character: String) {
        altKeyMap[keyCode] = character
    }

    /**
     * Rimuove una mappatura Alt+tasto esistente.
     */
    fun removeAltKeyMapping(keyCode: Int) {
        altKeyMap.remove(keyCode)
    }
}

