package it.palsoftware.pastiera.core.suggestions

import android.util.Log
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.core.AutoSpaceTracker

class CurrentWordTracker(
    private val onWordChanged: (String) -> Unit,
    private val onWordReset: () -> Unit,
    private val maxLength: Int = 48
) {

    private val current = StringBuilder()
    private val tag = "CurrentWordTracker"
    private val debugLogging = false

    val currentWord: String
        get() = current.toString()

    fun setWord(word: String) {
        current.clear()
        if (word.length <= maxLength) {
            current.append(word)
        } else {
            current.append(word.takeLast(maxLength))
        }
        if (debugLogging) Log.d(tag, "setWord currentWord='$current'")
        onWordChanged(current.toString())
    }

    fun onCharacterCommitted(text: CharSequence) {
        if (text.isEmpty()) return
        text.forEach { char ->
            val normalizedChar = normalizeApostrophe(char)
            val isWordChar = normalizedChar.isLetterOrDigit() ||
                (normalizedChar == '\'' && current.isNotEmpty() && current.last().isLetterOrDigit())
            if (isWordChar) {
                if (current.length < maxLength) {
                    current.append(normalizedChar)
                    if (debugLogging) Log.d(tag, "currentWord='$current'")
                    onWordChanged(current.toString())
                }
            } else {
                if (debugLogging) Log.d(tag, "reset on non-letter char='$char'")
                reset()
            }
        }
    }

    private fun normalizeApostrophe(c: Char): Char = when (c) {
        '’', '‘', 'ʼ' -> '\''
        else -> c
    }

    fun onBackspace() {
        if (current.isNotEmpty()) {
            current.deleteCharAt(current.length - 1)
            if (current.isNotEmpty()) {
                if (debugLogging) Log.d(tag, "currentWord after backspace='$current'")
                onWordChanged(current.toString())
            } else {
                reset()
            }
        }
    }

    fun onBoundaryReached(boundaryChar: Char? = null, inputConnection: InputConnection? = null) {
        if (boundaryChar != null) {
            // If an auto-space is pending, replace it with "<punctuation> " when punctuation is pressed.
            val punctuationSet = it.palsoftware.pastiera.core.Punctuation.AUTO_SPACE
            if (inputConnection != null && boundaryChar in punctuationSet) {
                val replaced = AutoSpaceTracker.replaceAutoSpaceWithPunctuation(
                    inputConnection,
                    boundaryChar.toString()
                )
                if (replaced) {
                    reset()
                    return
                }
            }
            inputConnection?.commitText(boundaryChar.toString(), 1)
        }
        reset()
    }

    fun reset() {
        if (current.isNotEmpty()) {
            if (debugLogging) Log.d(tag, "reset currentWord='$current'")
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
