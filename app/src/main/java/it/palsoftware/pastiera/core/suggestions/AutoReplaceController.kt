package it.palsoftware.pastiera.core.suggestions

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.core.AutoSpaceTracker
import android.util.Log

class AutoReplaceController(
    private val repository: DictionaryRepository,
    private val suggestionEngine: SuggestionEngine,
    private val settingsProvider: () -> SuggestionSettings
) {

    data class ReplaceResult(val replaced: Boolean, val committed: Boolean)
    
    // Track last replacement for undo
    private data class LastReplacement(
        val originalWord: String,
        val replacedWord: String
    )
    private var lastReplacement: LastReplacement? = null
    private var lastUndoOriginalWord: String? = null
    
    // Track rejected words to avoid auto-correcting them again
    private val rejectedWords = mutableSetOf<String>()

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

        // If cursor is after non-letter/digit and not standard punctuation (e.g., emoji),
        // skip auto-replace to avoid dropping trailing symbols.
        val textBefore = inputConnection.getTextBeforeCursor(16, 0)?.toString().orEmpty()
        val lastCharBeforeCursor = textBefore.lastOrNull()
        val allowedPunctuation = ".,;:!?()[]{}\"'-"
        if (lastCharBeforeCursor != null &&
            !lastCharBeforeCursor.isLetterOrDigit() &&
            lastCharBeforeCursor !in allowedPunctuation &&
            !lastCharBeforeCursor.isWhitespace()
        ) {
            tracker.onBoundaryReached(boundaryChar, inputConnection)
            return ReplaceResult(false, unicodeChar != 0)
        }

        val word = tracker.currentWord
        if (word.isBlank()) {
            tracker.onBoundaryReached(boundaryChar, inputConnection)
            return ReplaceResult(false, unicodeChar != 0)
        }

        val suggestions = suggestionEngine.suggest(
            word,
            limit = 1,
            includeAccentMatching = settings.accentMatching,
            useKeyboardProximity = settings.useKeyboardProximity,
            useEditTypeRanking = settings.useEditTypeRanking
        )
        val top = suggestions.firstOrNull()
        
        // Safety checks for auto-replace
        val minWordLength = 3 // Don't auto-correct words shorter than 3 characters
        val maxLengthRatio = 1.25 // Don't auto-correct if replacement is >25% longer
        
        // Check if word has been rejected by user
        val wordLower = word.lowercase()
        val isRejected = rejectedWords.contains(wordLower)
        
        // Check if word exists in dictionary
        val isKnownWord = repository.isKnownWord(word)
        val currentWordFrequency = if (isKnownWord) repository.getExactWordFrequency(word) else 0
        
        // Get top suggestion frequency (need to look it up)
        val topSuggestionFrequency = top?.let { suggestion ->
            repository.getExactWordFrequency(suggestion.candidate)
        } ?: 0
        
        // Allow auto-replace if:
        // 1. Word is not known, OR
        // 2. Word is known BUT top suggestion has higher frequency AND same exact length
        val canReplaceKnownWord = isKnownWord 
            && top != null
            && topSuggestionFrequency > currentWordFrequency
            && top.candidate.length == word.length
        
        val shouldReplace = top != null 
            && (!isKnownWord || canReplaceKnownWord) // Allow replacement if unknown OR if known but better version exists
            && !isRejected // Don't auto-correct if user has rejected this word
            && top.distance <= settings.maxAutoReplaceDistance
            && word.length >= minWordLength // Minimum word length check
            && top.candidate.length <= (word.length * maxLengthRatio).toInt() // Max length ratio check

        if (shouldReplace) {
            val replacement = applyCasing(top!!.candidate, word)
            inputConnection.beginBatchEdit()
            inputConnection.deleteSurroundingText(word.length, 0)
            inputConnection.commitText(replacement, 1)
            repository.markUsed(replacement)
            
            // Store last replacement for undo
            lastReplacement = LastReplacement(
                originalWord = word,
                replacedWord = replacement
            )
            
            tracker.reset()
            inputConnection.endBatchEdit()
            if (boundaryChar != null) {
                // Skip auto-space if replacement ends with apostrophe (elision, e.g., "dell'")
                val shouldAppendBoundary = !(boundaryChar == ' ' && replacement.endsWith("'"))
                if (shouldAppendBoundary) {
                    inputConnection.commitText(boundaryChar.toString(), 1)
                    if (boundaryChar == ' ') {
                        AutoSpaceTracker.markAutoSpace()
                    }
                }
                Log.d("AutoReplaceController", "Committed boundary '$boundaryChar', markAutoSpace=${shouldAppendBoundary && boundaryChar == ' '}")
            }
            return ReplaceResult(true, true)
        }

        // Clear last replacement if no replacement happened
        lastReplacement = null
        tracker.onBoundaryReached(boundaryChar, inputConnection)
        return ReplaceResult(false, unicodeChar != 0)
    }

    fun handleBackspaceUndo(
        keyCode: Int,
        inputConnection: InputConnection?
    ): Boolean {
        val settings = settingsProvider()
        if (!settings.autoReplaceOnSpaceEnter || keyCode != KeyEvent.KEYCODE_DEL || inputConnection == null) {
            return false
        }

        val replacement = lastReplacement ?: return false
        
        // Get text before cursor (need extra chars to check for boundary char)
        val textBeforeCursor = inputConnection.getTextBeforeCursor(
            replacement.replacedWord.length + 2, // +2 for boundary char and safety
            0
        ) ?: return false

        if (textBeforeCursor.length < replacement.replacedWord.length) {
            return false
        }

        // Check if text ends with replaced word (with or without boundary char)
        val lastChars = textBeforeCursor.substring(
            maxOf(0, textBeforeCursor.length - replacement.replacedWord.length - 1)
        )

        val matchesReplacement = lastChars.endsWith(replacement.replacedWord) ||
            lastChars.trimEnd().endsWith(replacement.replacedWord)

        if (!matchesReplacement) {
            return false
        }

        // Calculate chars to delete: replaced word + potential boundary char
        val charsToDelete = if (lastChars.endsWith(replacement.replacedWord)) {
            // No boundary char after, just delete the word
            replacement.replacedWord.length
        } else {
            // There's whitespace/punctuation after, include it in deletion
            var deleteCount = replacement.replacedWord.length
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

        inputConnection.beginBatchEdit()
        inputConnection.deleteSurroundingText(charsToDelete, 0)
        inputConnection.commitText(replacement.originalWord, 1)
        inputConnection.endBatchEdit()
        
        // Mark word as rejected so it won't be auto-corrected again
        rejectedWords.add(replacement.originalWord.lowercase())
        lastUndoOriginalWord = replacement.originalWord
        
        // Clear last replacement after undo
        lastReplacement = null
        return true
    }

    fun clearLastReplacement() {
        lastReplacement = null
    }
    
    fun clearRejectedWords() {
        rejectedWords.clear()
    }

    private fun applyCasing(candidate: String, original: String): String {
        return CasingHelper.applyCasing(candidate, original, forceLeadingCapital = false)
    }

    fun consumeLastUndoOriginalWord(): String? {
        val word = lastUndoOriginalWord
        lastUndoOriginalWord = null
        return word
    }
}
