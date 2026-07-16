package it.palsoftware.pastiera.inputmethod.suggestions

import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.suggestions.CasingHelper
import it.palsoftware.pastiera.inputmethod.AutoCapitalizeHelper
import it.palsoftware.pastiera.inputmethod.DebugCaptureStore
import it.palsoftware.pastiera.inputmethod.VariationButtonHandler
import it.palsoftware.pastiera.core.AutoSpaceTracker
import it.palsoftware.pastiera.core.Punctuation

/**
 * Handles clicks on suggestion buttons (full word replacements).
 */
object SuggestionButtonHandler {
    private const val TAG = "SuggestionButtonHandler"

    fun createSuggestionClickListener(
        suggestion: String,
        inputConnection: InputConnection?,
        listener: VariationButtonHandler.OnVariationSelectedListener? = null,
        shouldDisableAutoCapitalize: Boolean,
        onSuggestionCommitted: ((String) -> Unit)? = null
    ): View.OnClickListener {
        return View.OnClickListener {
            Log.d(TAG, "Click on suggestion button: $suggestion")

            if (inputConnection == null) {
                Log.w(TAG, "No inputConnection available to insert suggestion")
                return@OnClickListener
            }

            val context = it.context.applicationContext
            val forceLeadingCapital = AutoCapitalizeHelper.shouldAutoCapitalizeAtCursor(
                context = context,
                inputConnection = inputConnection,
                shouldDisableAutoCapitalize = shouldDisableAutoCapitalize
            ) && SettingsManager.getAutoCapitalizeFirstLetter(context)

            val committed = replaceCurrentWord(inputConnection, suggestion, forceLeadingCapital)
            if (committed) {
                onSuggestionCommitted?.invoke(suggestion)
            }
            listener?.onVariationSelected(suggestion)
        }
    }

    /**
     * Replace the word immediately before the cursor with the given suggestion.
     * Deletes up to the nearest whitespace/punctuation boundary and applies basic casing
     * (leading capital only). All-caps input (e.g., CapsLock) will not force the suggestion to uppercase.
     */
    private fun replaceCurrentWord(
        inputConnection: InputConnection,
        suggestion: String,
        forceLeadingCapital: Boolean
    ): Boolean {
        fun ensureTrailingSpaceAfterSuggestion(): Boolean {
            val beforeAfterCommit = inputConnection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (beforeAfterCommit.endsWith(" ")) {
                return true
            }
            inputConnection.commitText(" ", 1)
            val afterSecondTry = inputConnection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (afterSecondTry.endsWith(" ")) {
                return true
            }
            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE))
            inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE))
            val afterKeyEvent = inputConnection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            return afterKeyEvent.endsWith(" ")
        }

        val before = inputConnection.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        val after = inputConnection.getTextAfterCursor(64, 0)?.toString().orEmpty()
        fun isBoundaryChar(ch: Char, prev: Char?, next: Char?): Boolean {
            return Punctuation.isWordBoundary(ch, prev, next)
        }

        // Find start of word in 'before'
        var start = before.length
        while (start > 0) {
            val ch = before[start - 1]
            val prev = before.getOrNull(start - 2)
            val next = before.getOrNull(start)
            if (!isBoundaryChar(ch, prev, next)) {
                start--
                continue
            }
            break
        }
        // Find end of word in 'after'
        var end = 0
        while (end < after.length) {
            val ch = after[end]
            val prev = if (end == 0) before.lastOrNull() else after[end - 1]
            val next = after.getOrNull(end + 1)
            if (!isBoundaryChar(ch, prev, next)) {
                end++
                continue
            }
            break
        }

        val wordBeforeCursor = before.substring(start)
        val wordAfterCursor = after.substring(0, end)
        val currentWord = wordBeforeCursor + wordAfterCursor

        // Delete the full word around the cursor
        val deleteBefore = wordBeforeCursor.length
        val deleteAfter = wordAfterCursor.length
        val replacement = CasingHelper.applyCasing(suggestion, currentWord, forceLeadingCapital)
        val nextChar = after.getOrNull(end)
        val normalizedNextChar = nextChar?.let { Punctuation.normalizeApostrophe(it) }
        val nextIsWhitespace = normalizedNextChar?.isWhitespace() == true
        val shouldAppendSpace = !replacement.endsWith("'") &&
            !nextIsWhitespace

        val deleted = inputConnection.deleteSurroundingText(deleteBefore, deleteAfter)
        if (deleted) {
            Log.d(TAG, "Deleted ${deleteBefore + deleteAfter} chars ('$currentWord') before inserting suggestion")
        } else {
            Log.w(TAG, "Unable to delete surrounding word; inserting anyway")
        }

        val committed = inputConnection.commitText(replacement, 1)
        if (committed && shouldAppendSpace) {
            val committedWithSpace = inputConnection.commitText(" ", 1)
            val spaced = if (committedWithSpace) {
                val textBefore = inputConnection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
                if (textBefore.endsWith(" ")) true else ensureTrailingSpaceAfterSuggestion()
            } else {
                ensureTrailingSpaceAfterSuggestion()
            }
            if (spaced) {
                AutoSpaceTracker.markAutoSpace()
                Log.d(TAG, "Suggestion auto-space marked")
            } else {
                Log.w(TAG, "Suggestion space could not be enforced")
            }
        }
        val textToCommit = if (shouldAppendSpace) "$replacement " else replacement
        Log.d(TAG, "Suggestion inserted as '$textToCommit' (committed=$committed)")
        if (committed) {
            DebugCaptureStore.recordAutoCorrectionCommit(
                before = currentWord,
                after = replacement,
                trigger = DebugCaptureStore.AutoCorrectionTrigger.SUGGESTION_TAP,
                source = "UNKNOWN"
            )
        }
        return committed
    }
}
