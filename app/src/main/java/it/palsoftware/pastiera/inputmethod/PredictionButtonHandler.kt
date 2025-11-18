package it.palsoftware.pastiera.inputmethod

import android.util.Log
import android.view.View
import android.view.inputmethod.InputConnection

/**
 * Handles clicks on word prediction buttons.
 */
object PredictionButtonHandler {
    private const val TAG = "PredictionButtonHandler"

    /**
     * Callback called when a prediction is selected.
     */
    interface OnPredictionSelectedListener {
        /**
         * Called when a prediction is selected.
         * @param prediction The selected word prediction
         */
        fun onPredictionSelected(prediction: String)
    }

    /**
     * Creates a listener for a prediction button.
     * When clicked, deletes the partial word before cursor and inserts the full prediction.
     */
    fun createPredictionClickListener(
        prediction: String,
        inputConnection: InputConnection?,
        listener: OnPredictionSelectedListener? = null
    ): View.OnClickListener {
        return View.OnClickListener {
            Log.d(TAG, "Click on prediction button: $prediction")

            if (inputConnection == null) {
                Log.w(TAG, "No inputConnection available to insert prediction")
                return@OnClickListener
            }

            try {
                // Get text before cursor to find the partial word
                val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0)?.toString() ?: ""

                // Extract the last word (partial word being typed)
                val partialWord = extractLastWord(textBeforeCursor)

                if (partialWord.isNotEmpty()) {
                    // Delete the partial word
                    val deleted = inputConnection.deleteSurroundingText(partialWord.length, 0)
                    if (deleted) {
                        Log.d(TAG, "Deleted partial word: '$partialWord' (${partialWord.length} chars)")
                    } else {
                        Log.w(TAG, "Unable to delete partial word")
                    }
                } else {
                    Log.d(TAG, "No partial word to delete, inserting prediction directly")
                }

                // Insert the full prediction with a trailing space
                inputConnection.commitText("$prediction ", 1)
                Log.d(TAG, "Prediction '$prediction' inserted with trailing space")

                // Notify listener if present
                listener?.onPredictionSelected(prediction)

            } catch (e: Exception) {
                Log.e(TAG, "Error inserting prediction: ${e.message}", e)
            }
        }
    }

    /**
     * Extract the last word from text (word being currently typed).
     * Handles word boundaries like spaces, punctuation, etc.
     */
    private fun extractLastWord(text: String): String {
        if (text.isBlank()) return ""

        // Find the last word boundary (space, punctuation, etc.)
        // Use same logic as WordPredictionEngine for consistency
        val wordBoundaryChars = charArrayOf(' ', '\n', '\t', '.', ',', '!', '?', ';', ':', '-', '(', ')', '[', ']', '{', '}', '"', '\'', '/', '\\')
        val lastBoundaryIndex = text.indexOfLast { it in wordBoundaryChars }

        return if (lastBoundaryIndex >= 0) {
            text.substring(lastBoundaryIndex + 1).trim()
        } else {
            text.trim()
        }
    }
}
