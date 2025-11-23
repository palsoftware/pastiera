package it.palsoftware.pastiera.core

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.AutoCapitalizeHelper

/**
 * Orchestrates text-level helpers such as double-space-to-period and
 * auto-capitalization triggers. Keeps state like double-space timing isolated
 * from the IME service.
 *
 * Important: this controller never decides long-term Shift state on its own.
 * For smart auto-cap (Shift one-shot for the next character), it always
 * delegates to [AutoCapitalizeHelper] and [ModifierStateController] so that
 * there is a single source of truth for modifier state.
 */
class TextInputController(
    private val context: Context,
    private val modifierStateController: ModifierStateController,
    private val doubleTapThreshold: Long
) {

    private var lastSpacePressTime: Long = 0L

    fun handleDoubleSpaceToPeriod(
        keyCode: Int,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        onStatusBarUpdate: () -> Unit
    ): Boolean {
        // Detects a "double space" pattern and replaces the trailing space
        // with ". ". The decision to enable Shift one-shot after that is
        // delegated to AutoCapitalizeHelper so it can be tracked as a
        // smart auto-capitalization (and cleared when context changes).
        if (keyCode != KeyEvent.KEYCODE_SPACE || shouldDisableSmartFeatures) {
            if (lastSpacePressTime > 0) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSpacePressTime >= doubleTapThreshold) {
                    lastSpacePressTime = 0
                }
            }
            return false
        }

        if (!SettingsManager.getDoubleSpaceToPeriod(context)) {
            lastSpacePressTime = 0
            return false
        }

        val currentTime = System.currentTimeMillis()
        val isDoubleTap = lastSpacePressTime > 0 &&
            (currentTime - lastSpacePressTime) < doubleTapThreshold

        if (!isDoubleTap || inputConnection == null) {
            lastSpacePressTime = currentTime
            return false
        }

        val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0) ?: return false
        if (!textBeforeCursor.endsWith(" ") || 
            (textBeforeCursor.length >= 2 && textBeforeCursor[textBeforeCursor.length - 2] == ' ')) {
            lastSpacePressTime = currentTime
            return false
        }

        var lastCharIndex = textBeforeCursor.length - 2
        while (lastCharIndex >= 0 && textBeforeCursor[lastCharIndex].isWhitespace()) {
            lastCharIndex--
        }

        if (lastCharIndex < 0) {
            lastSpacePressTime = currentTime
            return false
        }

        val lastChar = textBeforeCursor[lastCharIndex]
        val isEndPunctuation = lastChar in ".!?"
        if (isEndPunctuation) {
            lastSpacePressTime = currentTime
            return false
        }

        inputConnection.deleteSurroundingText(1, 0)
        inputConnection.commitText(". ", 1)
        AutoCapitalizeHelper.enableAfterPunctuation(
            context = context,
            inputConnection = inputConnection,
            shouldDisableSmartFeatures = shouldDisableSmartFeatures,
            onEnableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
            disableShift = { modifierStateController.consumeShiftOneShot() },
            onUpdateStatusBar = onStatusBarUpdate
        )
        lastSpacePressTime = 0
        return true
    }

    fun handleAutoCapAfterPeriod(
        keyCode: Int,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        onStatusBarUpdate: () -> Unit
    ) {
        // If user presses Space after punctuation and Shift is not already
        // one-shot (e.g. pressed manually), delegate to AutoCapitalizeHelper.
        // The helper inspects the surrounding text and user settings to decide
        // whether to enable smart Shift for the next character.
        if (keyCode == KeyEvent.KEYCODE_SPACE &&
            !modifierStateController.shiftOneShot
        ) {
            AutoCapitalizeHelper.enableAfterPunctuation(
                context = context,
                inputConnection = inputConnection,
                shouldDisableSmartFeatures = shouldDisableSmartFeatures,
                onEnableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
                disableShift = { modifierStateController.consumeShiftOneShot() },
                onUpdateStatusBar = onStatusBarUpdate
            )
        }
    }

    fun handleAutoCapAfterEnter(
        keyCode: Int,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        onStatusBarUpdate: () -> Unit
    ) {
        // After Enter, we reuse the same smart auto-cap logic used for
        // "first letter in empty field" by delegating to AutoCapitalizeHelper.
        // This keeps all "start of sentence" detection in a single place.
        if (keyCode == KeyEvent.KEYCODE_ENTER && !shouldDisableSmartFeatures) {
            AutoCapitalizeHelper.enableAfterEnter(
                context,
                inputConnection,
                shouldDisableSmartFeatures,
                onEnableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
                disableShift = { modifierStateController.consumeShiftOneShot() },
                onUpdateStatusBar = onStatusBarUpdate
            )
        }
    }
}
