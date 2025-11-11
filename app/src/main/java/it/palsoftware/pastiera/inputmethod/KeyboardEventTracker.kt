package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Tracker globale per gli eventi tastiera.
 * Permette al servizio di immissione di comunicare gli eventi alla MainActivity.
 */
object KeyboardEventTracker {
    private var _keyEventState: MutableState<KeyEventInfo?>? = null
    val keyEventState: MutableState<KeyEventInfo?>?
        get() = _keyEventState
    
    data class KeyEventInfo(
        val keyCode: Int,
        val keyCodeName: String,
        val action: String,
        val unicodeChar: Int,
        val isAltPressed: Boolean,
        val isShiftPressed: Boolean,
        val isCtrlPressed: Boolean
    )
    
    fun registerState(state: MutableState<KeyEventInfo?>) {
        _keyEventState = state
    }
    
    fun unregisterState() {
        _keyEventState = null
    }
    
    fun notifyKeyEvent(keyCode: Int, event: KeyEvent?, action: String) {
        if (event != null) {
            val keyEventInfo = KeyEventInfo(
                keyCode = keyCode,
                keyCodeName = getKeyCodeName(keyCode),
                action = action,
                unicodeChar = event.unicodeChar,
                isAltPressed = event.isAltPressed,
                isShiftPressed = event.isShiftPressed,
                isCtrlPressed = event.isCtrlPressed
            )
            _keyEventState?.value = keyEventInfo
        }
    }
    
    private fun getKeyCodeName(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_Q -> "KEYCODE_Q"
            KeyEvent.KEYCODE_W -> "KEYCODE_W"
            KeyEvent.KEYCODE_E -> "KEYCODE_E"
            KeyEvent.KEYCODE_R -> "KEYCODE_R"
            KeyEvent.KEYCODE_T -> "KEYCODE_T"
            KeyEvent.KEYCODE_Y -> "KEYCODE_Y"
            KeyEvent.KEYCODE_U -> "KEYCODE_U"
            KeyEvent.KEYCODE_I -> "KEYCODE_I"
            KeyEvent.KEYCODE_O -> "KEYCODE_O"
            KeyEvent.KEYCODE_P -> "KEYCODE_P"
            KeyEvent.KEYCODE_A -> "KEYCODE_A"
            KeyEvent.KEYCODE_S -> "KEYCODE_S"
            KeyEvent.KEYCODE_D -> "KEYCODE_D"
            KeyEvent.KEYCODE_F -> "KEYCODE_F"
            KeyEvent.KEYCODE_G -> "KEYCODE_G"
            KeyEvent.KEYCODE_H -> "KEYCODE_H"
            KeyEvent.KEYCODE_J -> "KEYCODE_J"
            KeyEvent.KEYCODE_K -> "KEYCODE_K"
            KeyEvent.KEYCODE_L -> "KEYCODE_L"
            KeyEvent.KEYCODE_Z -> "KEYCODE_Z"
            KeyEvent.KEYCODE_X -> "KEYCODE_X"
            KeyEvent.KEYCODE_C -> "KEYCODE_C"
            KeyEvent.KEYCODE_V -> "KEYCODE_V"
            KeyEvent.KEYCODE_B -> "KEYCODE_B"
            KeyEvent.KEYCODE_N -> "KEYCODE_N"
            KeyEvent.KEYCODE_M -> "KEYCODE_M"
            KeyEvent.KEYCODE_SPACE -> "KEYCODE_SPACE"
            KeyEvent.KEYCODE_ENTER -> "KEYCODE_ENTER"
            KeyEvent.KEYCODE_DEL -> "KEYCODE_DEL"
            KeyEvent.KEYCODE_BACK -> "KEYCODE_BACK"
            63 -> "KEYCODE_SYM"
            else -> "KEYCODE_$keyCode"
        }
    }
}



