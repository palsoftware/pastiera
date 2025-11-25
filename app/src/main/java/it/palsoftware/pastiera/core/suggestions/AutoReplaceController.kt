package it.palsoftware.pastiera.core.suggestions

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import java.util.Locale

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
        val unicodeChar = event?.unicodeChar ?: 0
        val boundaryChar = when {
            unicodeChar != 0 -> unicodeChar.toChar()
            keyCode == KeyEvent.KEYCODE_SPACE -> ' '
            keyCode == KeyEvent.KEYCODE_ENTER -> '\n'
            else -> null
        }

        val settings = settingsProvider()
        if (!settings.autoReplaceOnSpaceEnter || inputConnection == null) {
            tracker.onBoundaryReached(boundaryChar, inputConnection)
            return ReplaceResult(false, unicodeChar != 0)
        }

        val word = tracker.currentWord
        if (word.isBlank()) {
            tracker.onBoundaryReached(boundaryChar, inputConnection)
            return ReplaceResult(false, unicodeChar != 0)
        }

        val suggestions = suggestionEngine.suggest(word, limit = 1, includeAccentMatching = settings.accentMatching)
        val top = suggestions.firstOrNull()
        val shouldReplace = top != null && !repository.isKnownWord(word) && top.distance <= settings.maxAutoReplaceDistance

        if (shouldReplace && top != null) {
            val replacement = applyCasing(top.candidate, word)
            inputConnection.beginBatchEdit()
            inputConnection.deleteSurroundingText(word.length, 0)
            inputConnection.commitText(replacement, 1)
            repository.markUsed(replacement)
            tracker.reset()
            inputConnection.endBatchEdit()
            if (boundaryChar != null) {
                inputConnection.commitText(boundaryChar.toString(), 1)
            }
            return ReplaceResult(true, true)
        }

        tracker.onBoundaryReached(boundaryChar, inputConnection)
        return ReplaceResult(false, unicodeChar != 0)
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
