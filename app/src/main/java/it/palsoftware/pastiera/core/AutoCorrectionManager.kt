package it.palsoftware.pastiera.core

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.inputmethod.AutoCorrector
import it.palsoftware.pastiera.inputmethod.NotificationHelper

class AutoCorrectionManager(
    private val context: Context
) {

    companion object {
        private const val TAG = "AutoCorrectionManager"
    }

    fun handleBackspaceUndo(
        keyCode: Int,
        inputConnection: InputConnection?,
        isAutoCorrectEnabled: Boolean,
        onStatusBarUpdate: () -> Unit
    ): Boolean {
        if (!isAutoCorrectEnabled || keyCode != KeyEvent.KEYCODE_DEL || inputConnection == null) {
            return false
        }

        val correction = AutoCorrector.getLastCorrection() ?: return false
        val textBeforeCursor = inputConnection.getTextBeforeCursor(
            correction.correctedWord.length + 2,
            0
        ) ?: return false

        if (textBeforeCursor.length < correction.correctedWord.length) {
            return false
        }

        val lastChars = textBeforeCursor.substring(
            maxOf(0, textBeforeCursor.length - correction.correctedWord.length - 1)
        )

        val matchesCorrection = lastChars.endsWith(correction.correctedWord) ||
            lastChars.trimEnd().endsWith(correction.correctedWord)

        if (!matchesCorrection) {
            return false
        }

        val charsToDelete = if (lastChars.endsWith(correction.correctedWord)) {
            correction.correctedWord.length
        } else {
            var deleteCount = correction.correctedWord.length
            var i = textBeforeCursor.length - 1
            while (i >= 0 &&
                i >= textBeforeCursor.length - deleteCount - 1 &&
                (textBeforeCursor[i].isWhitespace() ||
                        textBeforeCursor[i] in ".,;:!?()[]{}\"'")
            ) {
                deleteCount++
                i--
            }
            deleteCount
        }

        inputConnection.deleteSurroundingText(charsToDelete, 0)
        inputConnection.commitText(correction.originalWord, 1)
        AutoCorrector.undoLastCorrection()
        onStatusBarUpdate()
        Log.d(TAG, "Auto-correction undone: '${correction.correctedWord}' → '${correction.originalWord}'")
        return true
    }

    fun handleSpaceOrPunctuation(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        isAutoCorrectEnabled: Boolean,
        onStatusBarUpdate: () -> Unit
    ): Boolean {
        if (!isAutoCorrectEnabled || inputConnection == null) {
            return false
        }

        val isSpace = keyCode == KeyEvent.KEYCODE_SPACE
        val isPunctuation = event?.unicodeChar != null &&
            event.unicodeChar != 0 &&
            event.unicodeChar.toChar() in ".,;:!?()[]{}\"'"

        if (!isSpace && !isPunctuation) {
            return false
        }

        val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0)
        val correction = AutoCorrector.processText(textBeforeCursor, context = context) ?: return false

        val (wordToReplace, correctedWord) = correction
        inputConnection.deleteSurroundingText(wordToReplace.length, 0)
        inputConnection.commitText(correctedWord, 1)
        AutoCorrector.recordCorrection(wordToReplace, correctedWord)
        
        // Trigger haptic feedback when autocorrection occurs
        NotificationHelper.triggerHapticFeedback(context)

        when {
            isSpace -> inputConnection.commitText(" ", 1)
            isPunctuation && event?.unicodeChar != null && event.unicodeChar != 0 -> {
                inputConnection.commitText(event.unicodeChar.toChar().toString(), 1)
            }
        }

        onStatusBarUpdate()
        return true
    }

    fun handleAcceptOrResetOnOtherKeys(
        keyCode: Int,
        event: KeyEvent?,
        isAutoCorrectEnabled: Boolean
    ) {
        if (!isAutoCorrectEnabled || keyCode == KeyEvent.KEYCODE_DEL) {
            return
        }

        AutoCorrector.acceptLastCorrection()
        if (event != null && event.unicodeChar != 0) {
            val char = event.unicodeChar.toChar()
            if (char.isLetterOrDigit()) {
                AutoCorrector.clearRejectedWords()
            }
        }
    }
}

