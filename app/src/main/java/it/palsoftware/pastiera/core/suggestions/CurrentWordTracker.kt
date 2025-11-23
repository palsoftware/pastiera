package it.palsoftware.pastiera.core.suggestions

import android.view.inputmethod.InputConnection

class CurrentWordTracker(
    private val onWordChanged: (String) -> Unit,
    private val onWordReset: () -> Unit,
    private val maxLength: Int = 48
) {

    private val current = StringBuilder()

    val currentWord: String
        get() = current.toString()

    fun onCharacterCommitted(text: CharSequence) {
        if (text.isEmpty()) return
        text.forEach { char ->
            if (char.isLetterOrDigit()) {
                if (current.length < maxLength) {
                    current.append(char)
                    onWordChanged(current.toString())
                }
            } else {
                reset()
            }
        }
    }

    fun onBoundaryReached(boundaryChar: Char? = null, inputConnection: InputConnection? = null) {
        if (boundaryChar != null) {
            inputConnection?.commitText(boundaryChar.toString(), 1)
        }
        reset()
    }

    fun reset() {
        if (current.isNotEmpty()) {
            current.clear()
            onWordReset()
        }
    }

    fun onCursorMoved() {
        reset()
    }

    fun onContextChanged() {
        reset()
    }
}
