package it.palsoftware.pastiera.inputmethod.suggestions

import android.util.Log
import android.view.View
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.inputmethod.VariationButtonHandler
import java.util.Locale

/**
 * Handles clicks on suggestion buttons (full word replacements).
 */
object SuggestionButtonHandler {
    private const val TAG = "SuggestionButtonHandler"

    fun createSuggestionClickListener(
        suggestion: String,
        inputConnection: InputConnection?,
        listener: VariationButtonHandler.OnVariationSelectedListener? = null
    ): View.OnClickListener {
        return View.OnClickListener {
            Log.d(TAG, "Click on suggestion button: $suggestion")

            if (inputConnection == null) {
                Log.w(TAG, "No inputConnection available to insert suggestion")
                return@OnClickListener
            }

            replaceCurrentWord(inputConnection, suggestion)
            listener?.onVariationSelected(suggestion)
        }
    }

    /**
     * Replace the word immediately before the cursor with the given suggestion.
     * Deletes up to the nearest whitespace/punctuation boundary and applies basic casing
     * (leading capital only). All-caps input (e.g., CapsLock) will not force the suggestion to uppercase.
     */
    private fun replaceCurrentWord(inputConnection: InputConnection, suggestion: String) {
        val before = inputConnection.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        val after = inputConnection.getTextAfterCursor(64, 0)?.toString().orEmpty()
        val boundaryChars = " \t\n\r.,;:!?()[]{}\"'"

        // Find start of word in 'before'
        var start = before.length
        while (start > 0 && !boundaryChars.contains(before[start - 1])) {
            start--
        }
        // Find end of word in 'after'
        var end = 0
        while (end < after.length && !boundaryChars.contains(after[end])) {
            end++
        }

        val wordBeforeCursor = before.substring(start)
        val wordAfterCursor = after.substring(0, end)
        val currentWord = wordBeforeCursor + wordAfterCursor

        // Delete the full word around the cursor
        val deleteBefore = wordBeforeCursor.length
        val deleteAfter = wordAfterCursor.length
        val replacement = applyCasing(suggestion, currentWord)

        val deleted = inputConnection.deleteSurroundingText(deleteBefore, deleteAfter)
        if (deleted) {
            Log.d(TAG, "Deleted ${deleteBefore + deleteAfter} chars ('$currentWord') before inserting suggestion")
        } else {
            Log.w(TAG, "Unable to delete surrounding word; inserting anyway")
        }

        inputConnection.commitText("$replacement ", 1)
        Log.d(TAG, "Suggestion inserted as '$replacement '")
    }

    private fun applyCasing(candidate: String, original: String): String {
        if (original.isEmpty()) return candidate
        return when {
            original.first().isUpperCase() && original.drop(1).all { it.isLowerCase() } ->
                candidate.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            else -> candidate
        }
    }
}
