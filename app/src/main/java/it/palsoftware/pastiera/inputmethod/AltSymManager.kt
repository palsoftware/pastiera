package it.palsoftware.pastiera.inputmethod

import android.content.SharedPreferences
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestisce le mappature Alt/SYM, il long press e l'inserimento dei caratteri speciali.
 */
class AltSymManager(
    assets: AssetManager,
    private val prefs: SharedPreferences
) {
    // Callback chiamato quando viene inserito un carattere Alt dopo long press
    var onAltCharInserted: ((Char) -> Unit)? = null

    companion object {
        private const val TAG = "AltSymManager"
    }

    private val handler = Handler(Looper.getMainLooper())

    private val altKeyMap = mutableMapOf<Int, String>()
    private val symKeyMap = mutableMapOf<Int, String>()

    private val pressedKeys = ConcurrentHashMap<Int, Long>()
    private val longPressRunnables = ConcurrentHashMap<Int, Runnable>()
    private val longPressActivated = ConcurrentHashMap<Int, Boolean>()
    private val insertedNormalChars = ConcurrentHashMap<Int, String>()

    private var longPressThreshold: Long = 500L

    init {
        altKeyMap.putAll(KeyMappingLoader.loadAltKeyMappings(assets))
        symKeyMap.putAll(KeyMappingLoader.loadSymKeyMappings(assets))
        reloadLongPressThreshold()
    }

    fun reloadLongPressThreshold() {
        longPressThreshold = prefs.getLong("long_press_threshold", 500L).coerceIn(50L, 1000L)
    }

    fun getAltMappings(): Map<Int, String> = altKeyMap

    fun getSymMappings(): Map<Int, String> = symKeyMap

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
        inputConnection: InputConnection
    ): Boolean {
        pressedKeys[keyCode] = System.currentTimeMillis()
        longPressActivated[keyCode] = false

        var normalChar = if (event != null && event.unicodeChar != 0) {
            event.unicodeChar.toChar().toString()
        } else {
            ""
        }

        if (normalChar.isNotEmpty()) {
            if (capsLockEnabled && event?.isShiftPressed != true) {
                normalChar = normalChar.uppercase()
            } else if (capsLockEnabled && event?.isShiftPressed == true) {
                normalChar = normalChar.lowercase()
            }
        }

        if (normalChar.isNotEmpty()) {
            inputConnection.commitText(normalChar, 1)
            insertedNormalChars[keyCode] = normalChar
        }

        scheduleLongPress(keyCode, inputConnection)
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
            inputConnection.commitText(altChar, 1)
            true
        } else {
            defaultHandler(keyCode, event)
        }
    }

    fun handleKeyUp(keyCode: Int, symKeyActive: Boolean): Boolean {
        val pressStartTime = pressedKeys.remove(keyCode)
        longPressActivated.remove(keyCode)
        insertedNormalChars.remove(keyCode)
        longPressRunnables.remove(keyCode)?.let { handler.removeCallbacks(it) }

        return pressStartTime != null && altKeyMap.containsKey(keyCode) && !symKeyActive
    }

    fun cancelPendingLongPress(keyCode: Int) {
        longPressRunnables.remove(keyCode)?.let { handler.removeCallbacks(it) }
    }

    private fun scheduleLongPress(
        keyCode: Int,
        inputConnection: InputConnection
    ) {
        reloadLongPressThreshold()

        val runnable = Runnable {
            if (pressedKeys.containsKey(keyCode)) {
                val altChar = altKeyMap[keyCode]
                val insertedChar = insertedNormalChars[keyCode]

                if (altChar != null) {
                    longPressActivated[keyCode] = true

                    if (insertedChar != null && insertedChar.isNotEmpty()) {
                        inputConnection.deleteSurroundingText(1, 0)
                    }

                    inputConnection.commitText(altChar, 1)
                    insertedNormalChars.remove(keyCode)
                    longPressRunnables.remove(keyCode)
                    Log.d(TAG, "Long press Alt per keyCode $keyCode -> $altChar")
                    // Notifica che un carattere Alt è stato inserito
                    if (altChar.isNotEmpty()) {
                        onAltCharInserted?.invoke(altChar[0])
                    }
                }
            }
        }

        longPressRunnables[keyCode] = runnable
        handler.postDelayed(runnable, longPressThreshold)
    }
}


