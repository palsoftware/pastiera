package it.palsoftware.pastiera.inputmethod

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager

/**
 * Helper for handling auto-capitalization logic.
 * Manages shiftOneShot state based on cursor position and text context.
 */
object AutoCapitalizeHelper {
    private const val TAG = "AutoCapitalizeHelper"
    private const val PROTECTION_WINDOW_MS = 300L // Protection window for recently enabled shiftOneShot
    private const val DEFAULT_DELAY_MS = 50L
    private const val LONG_DELAY_MS = 150L
    private const val VERY_LONG_DELAY_MS = 200L

    data class AutoCapitalizeState(
        var shiftOneShot: Boolean = false,
        var shiftOneShotEnabledTime: Long = 0
    )

    data class AutoCapitalizeCheckResult(
        val shouldEnable: Boolean,
        val reason: String? = null
    )

    /**
     * Checks if cursor is at start or after newline and enables shiftOneShot if needed.
     * Used in initializeInputContext and onUpdateSelection.
     */
    fun checkAndEnableAutoCapitalize(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        currentState: AutoCapitalizeState,
        delayMs: Long = DEFAULT_DELAY_MS,
        onUpdateStatusBar: () -> Unit
    ) {
        if (!SettingsManager.getAutoCapitalizeFirstLetter(context)) {
            return
        }

        if (shouldDisableSmartFeatures) {
            return
        }

        if (inputConnection == null) {
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            val textBeforeCursor = inputConnection.getTextBeforeCursor(1, 0)
            val isCursorAtStart = textBeforeCursor == null || textBeforeCursor.isEmpty()
            val isAfterNewline = textBeforeCursor != null &&
                    textBeforeCursor.isNotEmpty() &&
                    textBeforeCursor[textBeforeCursor.length - 1] == '\n'

            if (isCursorAtStart || isAfterNewline) {
                if (!currentState.shiftOneShot) {
                    currentState.shiftOneShot = true
                    currentState.shiftOneShotEnabledTime = System.currentTimeMillis()
                    onUpdateStatusBar()
                }
            }
        }, delayMs)
    }

    /**
     * Checks auto-capitalize condition after field is cleared (e.g., after sending message).
     * Used in onUpdateSelection when cursor moves to start.
     */
    fun checkAutoCapitalizeOnSelectionChange(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        currentState: AutoCapitalizeState,
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        onUpdateStatusBar: () -> Unit
    ) {
        if (!SettingsManager.getAutoCapitalizeFirstLetter(context)) {
            disableIfActive(currentState, onUpdateStatusBar)
            return
        }

        // Disable if there's a selection
        if (newSelStart != newSelEnd) {
            disableIfActive(currentState, onUpdateStatusBar)
            return
        }

        // Disable for restricted fields
        if (shouldDisableSmartFeatures) {
            disableIfActive(currentState, onUpdateStatusBar)
            return
        }

        if (inputConnection == null) {
            disableIfActive(currentState, onUpdateStatusBar)
            return
        }

        val cursorMovedToStart = newSelStart == 0 && oldSelStart != 0
        val cursorAtStart = newSelStart == 0

        // Use appropriate delay based on context
        val delay = when {
            cursorMovedToStart -> VERY_LONG_DELAY_MS
            cursorAtStart && oldSelStart == 0 && newSelEnd == 0 && oldSelEnd == 0 -> LONG_DELAY_MS
            else -> DEFAULT_DELAY_MS
        }

        Handler(Looper.getMainLooper()).postDelayed({
            val textBeforeCursor = inputConnection.getTextBeforeCursor(1000, 0)
            val textAfterCursor = inputConnection.getTextAfterCursor(1000, 0)

            val isCursorAtStart = textBeforeCursor == null || textBeforeCursor.isEmpty()
            val isFieldEmpty = isCursorAtStart && (textAfterCursor == null || textAfterCursor.isEmpty())
            val isAfterNewline = textBeforeCursor != null &&
                    textBeforeCursor.isNotEmpty() &&
                    textBeforeCursor[textBeforeCursor.length - 1] == '\n'

            if (isCursorAtStart || isAfterNewline) {
                if (!currentState.shiftOneShot) {
                    currentState.shiftOneShot = true
                    currentState.shiftOneShotEnabledTime = System.currentTimeMillis()
                    onUpdateStatusBar()
                }
            } else {
                // Cursor not at start - disable shiftOneShot if protection window expired
                disableIfActiveAndExpired(currentState, onUpdateStatusBar)
            }
        }, delay)
    }

    /**
     * Checks if cursor is at start in an empty field (used when restarting input).
     */
    fun checkAutoCapitalizeOnRestart(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        currentState: AutoCapitalizeState,
        onUpdateStatusBar: () -> Unit
    ) {
        if (!SettingsManager.getAutoCapitalizeFirstLetter(context)) {
            return
        }

        if (shouldDisableSmartFeatures) {
            return
        }

        if (inputConnection == null) {
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            val textBeforeCursor = inputConnection.getTextBeforeCursor(1000, 0)
            val textAfterCursor = inputConnection.getTextAfterCursor(1000, 0)
            val isCursorAtStart = textBeforeCursor == null || textBeforeCursor.isEmpty()
            val isFieldEmpty = isCursorAtStart && (textAfterCursor == null || textAfterCursor.isEmpty())

            if (isCursorAtStart && isFieldEmpty) {
                if (!currentState.shiftOneShot) {
                    currentState.shiftOneShot = true
                    currentState.shiftOneShotEnabledTime = System.currentTimeMillis()
                    onUpdateStatusBar()
                }
            }
        }, LONG_DELAY_MS)
    }

    /**
     * Enables shiftOneShot after period/exclamation/question mark.
     * Used when space is pressed after punctuation.
     */
    fun enableAfterPunctuation(
        inputConnection: InputConnection?,
        currentState: AutoCapitalizeState,
        onUpdateStatusBar: () -> Unit
    ): Boolean {
        if (inputConnection == null) {
            return false
        }

        if (currentState.shiftOneShot) {
            return false // Already enabled
        }

        val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0)

        if (textBeforeCursor == null || textBeforeCursor.isEmpty()) {
            return false
        }

        val lastChar = textBeforeCursor[textBeforeCursor.length - 1]
        val shouldCapitalize = when (lastChar) {
            '.' -> {
                // Check it's not part of ellipsis
                textBeforeCursor.length >= 2 && textBeforeCursor[textBeforeCursor.length - 2] != '.'
            }
            '!', '?' -> true
            else -> false
        }

        if (shouldCapitalize) {
            currentState.shiftOneShot = true
            currentState.shiftOneShotEnabledTime = System.currentTimeMillis()
            onUpdateStatusBar()
            return true
        }

        return false
    }

    /**
     * Enables shiftOneShot after ENTER key is pressed.
     */
    fun enableAfterEnter(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        currentState: AutoCapitalizeState,
        onUpdateStatusBar: () -> Unit
    ) {
        if (!SettingsManager.getAutoCapitalizeFirstLetter(context)) {
            return
        }

        if (shouldDisableSmartFeatures) {
            return
        }

        if (inputConnection == null) {
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            val textBeforeCursor = inputConnection.getTextBeforeCursor(1, 0)
            val isAfterNewline = textBeforeCursor != null &&
                    textBeforeCursor.isNotEmpty() &&
                    textBeforeCursor[textBeforeCursor.length - 1] == '\n'

            if (isAfterNewline) {
                currentState.shiftOneShot = true
                currentState.shiftOneShotEnabledTime = System.currentTimeMillis()
                onUpdateStatusBar()
            }
        }, DEFAULT_DELAY_MS)
    }

    /**
     * Disables shiftOneShot if it's active and protection window has expired.
     */
    private fun disableIfActiveAndExpired(
        currentState: AutoCapitalizeState,
        onUpdateStatusBar: () -> Unit
    ) {
        if (!currentState.shiftOneShot) {
            return
        }

        val timeSinceEnabled = System.currentTimeMillis() - currentState.shiftOneShotEnabledTime
        if (timeSinceEnabled > PROTECTION_WINDOW_MS) {
            currentState.shiftOneShot = false
            currentState.shiftOneShotEnabledTime = 0
            onUpdateStatusBar()
        }
    }

    /**
     * Disables shiftOneShot if it's active (regardless of protection window).
     */
    private fun disableIfActive(
        currentState: AutoCapitalizeState,
        onUpdateStatusBar: () -> Unit
    ) {
        if (currentState.shiftOneShot) {
            currentState.shiftOneShot = false
            currentState.shiftOneShotEnabledTime = 0
            onUpdateStatusBar()
        }
    }

    /**
     * Resets auto-capitalize state.
     */
    fun reset(state: AutoCapitalizeState) {
        state.shiftOneShot = false
        state.shiftOneShotEnabledTime = 0
    }
}



