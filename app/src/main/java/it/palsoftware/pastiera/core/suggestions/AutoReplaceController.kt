package it.palsoftware.pastiera.core.suggestions

import android.view.KeyEvent
import android.view.inputmethod.InputConnection

class AutoReplaceController(
    private val repository: DictionaryRepository,
    private val suggestionEngine: SuggestionEngine,
    private val settingsProvider: () -> SuggestionSettings
) {

    data class ReplaceResult(val replaced: Boolean, val committed: Boolean)

    fun handleBoundary(
        keyCode: Int,
        event: KeyEvent?,
        tracker: CurrentWordTracker,
        inputConnection: InputConnection?
    ): ReplaceResult {
        val boundaryChar = when {
            event?.unicodeChar != null && event.unicodeChar != 0 -> event.unicodeChar.toChar()
            keyCode == KeyEvent.KEYCODE_SPACE -> ' '
            keyCode == KeyEvent.KEYCODE_ENTER -> '\n'
            else -> null
        }

        val settings = settingsProvider()
        if (!settings.autoReplaceOnSpaceEnter || inputConnection == null) {
            tracker.onBoundaryReached(boundaryChar, inputConnection)
            return ReplaceResult(false, event?.unicodeChar != null)
        }

        val word = tracker.currentWord
        if (word.isBlank()) {
            tracker.onBoundaryReached(boundaryChar, inputConnection)
            return ReplaceResult(false, event?.unicodeChar != null)
        }

        val suggestions = suggestionEngine.suggest(word, limit = 1, includeAccentMatching = settings.accentMatching)
        val top = suggestions.firstOrNull()
        val shouldReplace = top != null && !repository.isKnownWord(word) && top.distance <= settings.maxAutoReplaceDistance

        if (shouldReplace && top != null) {
            inputConnection.beginBatchEdit()
            inputConnection.deleteSurroundingText(word.length, 0)
            inputConnection.commitText(top.candidate, 1)
            repository.markUsed(top.candidate)
            tracker.reset()
            inputConnection.endBatchEdit()
            if (boundaryChar != null) {
                inputConnection.commitText(boundaryChar.toString(), 1)
            }
            return ReplaceResult(true, true)
        }

        tracker.onBoundaryReached(boundaryChar, inputConnection)
        return ReplaceResult(false, event?.unicodeChar != null)
    }
}
