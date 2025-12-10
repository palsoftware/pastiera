package it.palsoftware.pastiera.inputmethod.suggestions

import android.util.Log
import android.view.View
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.suggestions.CasingHelper
import it.palsoftware.pastiera.inputmethod.AutoCapitalizeHelper
import it.palsoftware.pastiera.inputmethod.NotificationHelper
import it.palsoftware.pastiera.inputmethod.VariationButtonHandler
import it.palsoftware.pastiera.core.AutoSpaceTracker

/**
 * Handles clicks on suggestion buttons (full word replacements).
 */
object SuggestionButtonHandler {
    private const val TAG = "SuggestionButtonHandler"

    fun createSuggestionClickListener(
        suggestion: String,
        inputConnection: InputConnection?,
        listener: VariationButtonHandler.OnVariationSelectedListener? = null,
        shouldDisableAutoCapitalize: Boolean
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
                NotificationHelper.triggerHapticFeedback(context)
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
        val before = inputConnection.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        val after = inputConnection.getTextAfterCursor(64, 0)?.toString().orEmpty()
        val boundaryChars = " \t\n\r" + it.palsoftware.pastiera.core.Punctuation.BOUNDARY

        fun isApostropheWithinWord(prev: Char?, next: Char?): Boolean {
            if (prev?.isLetterOrDigit() != true) return false
            return next == null || next.isLetterOrDigit()
        }

        // Find start of word in 'before'
        var start = before.length
        while (start > 0) {
            val ch = before[start - 1]
            if (!boundaryChars.contains(ch)) {
                start--
                continue
            }
            val prev = before.getOrNull(start - 2)
            val next = before.getOrNull(start)
            if (ch == '\'' && isApostropheWithinWord(prev, next)) {
                start--
                continue
            }
            break
        }
        // Find end of word in 'after'
        var end = 0
        while (end < after.length) {
            val ch = after[end]
            if (!boundaryChars.contains(ch)) {
                end++
                continue
            }
            val prev = if (end == 0) before.lastOrNull() else after[end - 1]
            val next = after.getOrNull(end + 1)
            if (ch == '\'' && isApostropheWithinWord(prev, next)) {
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
        val shouldAppendSpace = !replacement.endsWith("'")

        val deleted = inputConnection.deleteSurroundingText(deleteBefore, deleteAfter)
        if (deleted) {
            Log.d(TAG, "Deleted ${deleteBefore + deleteAfter} chars ('$currentWord') before inserting suggestion")
        } else {
            Log.w(TAG, "Unable to delete surrounding word; inserting anyway")
        }

        val textToCommit = if (shouldAppendSpace) "$replacement " else replacement
        val committed = inputConnection.commitText(textToCommit, 1)
        if (committed && shouldAppendSpace) {
            AutoSpaceTracker.markAutoSpace()
            Log.d(TAG, "Suggestion auto-space marked")
        }
        Log.d(TAG, "Suggestion inserted as '$textToCommit' (committed=$committed)")
        return committed
    }
}
