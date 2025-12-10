package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import it.palsoftware.pastiera.core.AutoSpaceTracker
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Alt/SYM mappings, long press handling and special character insertion.
 */
class AltSymManager(
    private val assets: AssetManager,
    private val prefs: SharedPreferences,
    private val context: Context? = null
) {
    // Callback invoked when an Alt character is inserted after a long press
    var onAltCharInserted: ((Char) -> Unit)? = null

    companion object {
        private const val TAG = "AltSymManager"
    }

    private val handler = Handler(Looper.getMainLooper())

    private val altKeyMap = mutableMapOf<Int, String>()
    private val symKeyMap = mutableMapOf<Int, String>()
    private val symKeyMap2 = mutableMapOf<Int, String>()

    private val pressedKeys = ConcurrentHashMap<Int, Long>()
    private val longPressRunnables = ConcurrentHashMap<Int, Runnable>()
    private val longPressActivated = ConcurrentHashMap<Int, Boolean>()
    private val insertedNormalChars = ConcurrentHashMap<Int, String>()

    private var longPressThreshold: Long = 500L

    init {
        altKeyMap.putAll(KeyMappingLoader.loadAltKeyMappings(assets, context))
        symKeyMap.putAll(KeyMappingLoader.loadSymKeyMappings(assets))
        symKeyMap2.putAll(KeyMappingLoader.loadSymKeyMappingsPage2(assets))
        reloadLongPressThreshold()
    }

    fun reloadLongPressThreshold() {
        longPressThreshold = prefs.getLong("long_press_threshold", 500L).coerceIn(50L, 1000L)
    }

    fun getAltMappings(): Map<Int, String> = altKeyMap

    fun getSymMappings(): Map<Int, String> = symKeyMap
    
    fun getSymMappings2(): Map<Int, String> = symKeyMap2
    
    /**
     * Ricarica le mappature SYM, controllando prima le personalizzazioni.
     */
    fun reloadSymMappings() {
        if (context != null) {
            val customMappings = it.palsoftware.pastiera.SettingsManager.getSymMappings(context)
            if (customMappings.isNotEmpty()) {
                symKeyMap.clear()
                symKeyMap.putAll(customMappings)
                Log.d(TAG, "Loaded custom SYM mappings: ${customMappings.size} entries")
            } else {
                // Use default mappings from JSON
                symKeyMap.clear()
                symKeyMap.putAll(KeyMappingLoader.loadSymKeyMappings(assets))
                Log.d(TAG, "Loaded default SYM mappings")
            }
        }
    }
    
    /**
     * Reloads SYM mappings for page 2, checking for custom mappings first.
     */
    fun reloadSymMappings2() {
        if (context != null) {
            val customMappings = it.palsoftware.pastiera.SettingsManager.getSymMappingsPage2(context)
            if (customMappings.isNotEmpty()) {
                symKeyMap2.clear()
                symKeyMap2.putAll(customMappings)
                Log.d(TAG, "Loaded custom SYM page 2 mappings: ${customMappings.size} entries")
            } else {
                // Use default mappings from JSON
                symKeyMap2.clear()
                symKeyMap2.putAll(KeyMappingLoader.loadSymKeyMappingsPage2(assets))
                Log.d(TAG, "Loaded default SYM page 2 mappings")
            }
        }
    }

    fun hasAltMapping(keyCode: Int): Boolean = altKeyMap.containsKey(keyCode)

    fun hasPendingPress(keyCode: Int): Boolean = pressedKeys.containsKey(keyCode)

    fun addAltKeyMapping(keyCode: Int, character: String) {
        altKeyMap[keyCode] = character
    }

    fun removeAltKeyMapping(keyCode: Int) {
        altKeyMap.remove(keyCode)
    }

    fun resetTransientState() {
        longPressRunnables.values.forEach { handler.removeCallbacks(it) }
        longPressRunnables.clear()
        pressedKeys.clear()
        longPressActivated.clear()
        insertedNormalChars.clear()
    }

    fun buildEmojiMapText(): String {
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

    fun handleKeyWithAltMapping(
        keyCode: Int,
        event: KeyEvent?,
        capsLockEnabled: Boolean,
        inputConnection: InputConnection,
        shiftOneShot: Boolean = false,
        layoutChar: Char? = null // Optional character from keyboard layout
    ): Boolean {
        pressedKeys[keyCode] = System.currentTimeMillis()
        longPressActivated[keyCode] = false

        // Use centralized character retrieval from layout manager when key is mapped
        var normalChar = if (LayoutMappingRepository.isMapped(keyCode)) {
            LayoutMappingRepository.getCharacterStringWithModifiers(
                keyCode,
                isShiftPressed = event?.isShiftPressed == true,
                capsLockEnabled = capsLockEnabled,
                shiftOneShot = shiftOneShot
            )
        } else {
            // Fallback: use layout character if provided, otherwise fall back to event's unicode character
            if (layoutChar != null) {
                layoutChar.toString()
            } else if (event != null && event.unicodeChar != 0) {
                event.unicodeChar.toChar().toString()
            } else {
                ""
            }
        }

        // For unmapped keys, apply case conversion if needed (fallback only)
        if (normalChar.isNotEmpty() && !LayoutMappingRepository.isMapped(keyCode)) {
            // Gestisci shiftOneShot: se è attivo e il carattere è una lettera, rendilo maiuscolo
            if (shiftOneShot && normalChar.isNotEmpty() && normalChar[0].isLetter()) {
                normalChar = normalChar.uppercase()
            } else if (capsLockEnabled && event?.isShiftPressed != true) {
                normalChar = normalChar.uppercase()
            } else if (capsLockEnabled && event?.isShiftPressed == true) {
                normalChar = normalChar.lowercase()
            }
        }

        if (normalChar.isNotEmpty()) {
            inputConnection.commitText(normalChar, 1)
            insertedNormalChars[keyCode] = normalChar
        }

        // Check if this key should support long press
        val useShift = context?.let { 
            SettingsManager.isLongPressShift(it) 
        } ?: false
        
        // Only schedule long press if:
        // - Using Alt and key has Alt mapping, OR
        // - Using Shift and key is mapped in layout (works for any character, not just letters)
        val shouldScheduleLongPress = if (useShift) {
            LayoutMappingRepository.isMapped(keyCode) && normalChar.isNotEmpty()
        } else {
            altKeyMap.containsKey(keyCode)
        }
        
        if (shouldScheduleLongPress) {
            scheduleLongPress(keyCode, inputConnection)
        }
        
        return true
    }

    fun handleAltCombination(
        keyCode: Int,
        inputConnection: InputConnection,
        event: KeyEvent?,
        defaultHandler: (Int, KeyEvent?) -> Boolean
    ): Boolean {
        val altChar = altKeyMap[keyCode]
        return if (altChar != null) {
            val punctuationSet = it.palsoftware.pastiera.core.Punctuation.AUTO_SPACE
            if (altChar.isNotEmpty() && altChar[0] in punctuationSet) {
                val applied = AutoSpaceTracker.replaceAutoSpaceWithPunctuation(inputConnection, altChar)
                if (applied) {
                    Log.d(TAG, "Alt mapping applied with auto-space replacement for '$altChar'")
                    onAltCharInserted?.invoke(altChar[0])
                    return true
                }
            }
            AutoSpaceTracker.clear()
            inputConnection.commitText(altChar, 1)
            if (altChar.isNotEmpty()) {
                onAltCharInserted?.invoke(altChar[0])
            }
            true
        } else {
            defaultHandler(keyCode, event)
        }
    }

    fun handleKeyUp(keyCode: Int, symKeyActive: Boolean, shiftPressed: Boolean = false): Boolean {
        val pressStartTime = pressedKeys.remove(keyCode)
        longPressActivated.remove(keyCode)
        insertedNormalChars.remove(keyCode)
        
        // Non cancellare il long press se shift è ancora premuto
        // Questo permette al long press di completarsi anche se il tasto viene rilasciato mentre shift è premuto
        if (!shiftPressed) {
            longPressRunnables.remove(keyCode)?.let { handler.removeCallbacks(it) }
        }

        return pressStartTime != null && altKeyMap.containsKey(keyCode) && !symKeyActive
    }

    fun cancelPendingLongPress(keyCode: Int) {
        longPressRunnables.remove(keyCode)?.let { handler.removeCallbacks(it) }
    }

    /**
     * Schedules a long-press without committing a new character, reusing the
     * same runnable logic used by handleKeyWithAltMapping. This is used so
     * multi-tap commits can still trigger Alt/Shift long-press behaviour.
     */
    fun scheduleLongPressOnly(
        keyCode: Int,
        inputConnection: InputConnection,
        insertedChar: String
    ) {
        pressedKeys[keyCode] = System.currentTimeMillis()
        longPressActivated[keyCode] = false
        insertedNormalChars[keyCode] = insertedChar
        scheduleLongPress(keyCode, inputConnection)
    }

    private fun scheduleLongPress(
        keyCode: Int,
        inputConnection: InputConnection
    ) {
        reloadLongPressThreshold()
        
        // Check if we should use Shift or Alt
        val useShift = context?.let { 
            SettingsManager.isLongPressShift(it) 
        } ?: false

        val runnable = Runnable {
            if (pressedKeys.containsKey(keyCode)) {
                val insertedChar = insertedNormalChars[keyCode]
                
                if (useShift) {
                    // Long press with Shift: get uppercase from layout (always use JSON for mapped keys)
                    if (LayoutMappingRepository.isMapped(keyCode)) {
                        // Always use JSON to get uppercase character (works correctly for complex layouts like Arabic)
                        val upperChar = LayoutMappingRepository.getUppercase(keyCode)
                        if (upperChar != null) {
                            longPressActivated[keyCode] = true
                            val upperCharString = upperChar
                            
                            // Delete the previously inserted character and insert uppercase from JSON
                            inputConnection.deleteSurroundingText(1, 0)
                            inputConnection.commitText(upperCharString, 1)
                            
                            insertedNormalChars.remove(keyCode)
                            longPressRunnables.remove(keyCode)
                            Log.d(TAG, "Long press Shift per keyCode $keyCode -> $upperCharString")
                            // Notify that a character was inserted
                            upperChar.firstOrNull()?.let { onAltCharInserted?.invoke(it) }
                        }
                    } else if (insertedChar != null && insertedChar.isNotEmpty() && insertedChar[0].isLetter()) {
                        // Fallback for unmapped keys only: use Kotlin uppercase (not ideal but necessary)
                        longPressActivated[keyCode] = true
                        val upperChar = insertedChar.uppercase()
                        
                        // Delete the previously inserted character and insert uppercase
                        inputConnection.deleteSurroundingText(1, 0)
                        inputConnection.commitText(upperChar, 1)
                        
                        insertedNormalChars.remove(keyCode)
                        longPressRunnables.remove(keyCode)
                        Log.d(TAG, "Long press Shift per keyCode $keyCode -> $upperChar (fallback)")
                        // Notify that a character was inserted
                        if (upperChar.isNotEmpty()) {
                            onAltCharInserted?.invoke(upperChar[0])
                        }
                    }
                } else {
                    // Long press with Alt: use existing Alt mapping
                    val altChar = altKeyMap[keyCode]
                    
                    if (altChar != null) {
                        longPressActivated[keyCode] = true
                        
                        if (insertedChar != null && insertedChar.isNotEmpty()) {
                            inputConnection.deleteSurroundingText(1, 0)
                        }

                        val punctuationSet = it.palsoftware.pastiera.core.Punctuation.AUTO_SPACE
                        if (altChar.isNotEmpty() && altChar[0] in punctuationSet) {
                            val applied = AutoSpaceTracker.replaceAutoSpaceWithPunctuation(inputConnection, altChar)
                            if (applied) {
                                Log.d(TAG, "Long press Alt mapping applied with auto-space replacement for '$altChar'")
                                onAltCharInserted?.invoke(altChar[0])
                                insertedNormalChars.remove(keyCode)
                                longPressRunnables.remove(keyCode)
                                return@Runnable
                            }
                        }

                        AutoSpaceTracker.clear()
                        inputConnection.commitText(altChar, 1)
                        insertedNormalChars.remove(keyCode)
                        longPressRunnables.remove(keyCode)
                        Log.d(TAG, "Long press Alt per keyCode $keyCode -> $altChar")
                        // Notify that an Alt character was inserted
                        if (altChar.isNotEmpty()) {
                            onAltCharInserted?.invoke(altChar[0])
                        }
                    }
                }
            }
        }

        longPressRunnables[keyCode] = runnable
        handler.postDelayed(runnable, longPressThreshold)
    }
}
