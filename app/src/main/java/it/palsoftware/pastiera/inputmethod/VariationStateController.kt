package it.palsoftware.pastiera.inputmethod

import android.util.Log
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

/**
 * Tracks character variation availability for the current cursor position
 * and exposes snapshots for the status / variation bars.
 */
class VariationStateController(
    private val variationsMap: Map<Char, List<String>>
) {

    data class Snapshot(
        val isActive: Boolean,
        val lastInsertedChar: Char?,
        val variations: List<String>
    )

    companion object {
        private const val TAG = "VariationStateCtrl"
    }

    private var lastInsertedChar: Char? = null
    private var availableVariations: List<String> = emptyList()
    private var variationsActive: Boolean = false

    fun refreshFromCursor(
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean
    ): Snapshot {
        if (shouldDisableSmartFeatures || inputConnection == null) {
            clear()
            return snapshot()
        }

        if (hasActiveSelection(inputConnection)) {
            clear()
            return snapshot()
        }

        val textBeforeCursor = inputConnection.getTextBeforeCursor(1, 0)
        if (!textBeforeCursor.isNullOrEmpty()) {
            val charBeforeCursor = textBeforeCursor.last()
            val variations = variationsMap[charBeforeCursor]
            if (!variations.isNullOrEmpty()) {
                lastInsertedChar = charBeforeCursor
                availableVariations = variations
                variationsActive = true
            } else {
                clear()
            }
        } else {
            clear()
        }

        return snapshot()
    }

    private fun hasActiveSelection(inputConnection: InputConnection): Boolean {
        return try {
            val extractedText = inputConnection.getExtractedText(
                ExtractedTextRequest().apply {
                    flags = ExtractedText.FLAG_SELECTING
                },
                0
            )
            extractedText != null &&
                extractedText.selectionStart >= 0 &&
                extractedText.selectionEnd >= 0 &&
                extractedText.selectionStart != extractedText.selectionEnd
        } catch (e: Exception) {
            Log.d(TAG, "Error while checking selection state: ${e.message}")
            false
        }
    }

    fun hasVariationsFor(char: Char): Boolean = variationsMap.containsKey(char)

    fun clear() {
        variationsActive = false
        lastInsertedChar = null
        availableVariations = emptyList()
    }

    fun snapshot(): Snapshot {
        return Snapshot(
            isActive = variationsActive,
            lastInsertedChar = if (variationsActive) lastInsertedChar else null,
            variations = if (variationsActive) availableVariations else emptyList()
        )
    }
}


