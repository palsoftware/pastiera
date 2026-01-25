package it.palsoftware.pastiera.core.suggestions

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.core.AutoSpaceTracker
import android.util.Log
import java.io.File
import java.text.Normalizer
import org.json.JSONObject

class AutoReplaceController(
    private val repository: DictionaryRepository,
    private val suggestionEngine: SuggestionEngine,
    private val settingsProvider: () -> SuggestionSettings
) {
    // #region agent log
    private fun debugLog(hypothesisId: String, location: String, message: String, data: Map<String, Any?> = emptyMap()) {
        try {
            val logFile = File("/Users/andrea/Desktop/DEV/Pastiera/pastiera/.cursor/debug.log")
            val logEntry = JSONObject().apply {
                put("sessionId", "debug-session")
                put("runId", "run1")
                put("hypothesisId", hypothesisId)
                put("location", location)
                put("message", message)
                put("timestamp", System.currentTimeMillis())
                put("data", JSONObject(data))
            }
            logFile.appendText(logEntry.toString() + "\n")
        } catch (e: Exception) {
            // Ignore log errors
        }
    }
    // #endregion

    public data class ReplaceResult(val replaced: Boolean, val committed: Boolean)

    companion object {
        internal data class ApostropheSplit(val prefix: String, val root: String)

        internal fun normalizeApostrophes(input: String): String {
            return input
                .replace("’", "'")
                .replace("‘", "'")
                .replace("ʼ", "'")
        }

        internal fun stripAccents(input: String): String {
            return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replace("\\p{Mn}".toRegex(), "")
        }

        /**
         * Split a word with a single apostrophe into prefix (with apostrophe) and root.
         * Language-agnostic: only checks structure/length, not locale.
         */
        internal fun splitApostropheWord(word: String): ApostropheSplit? {
            val normalized = normalizeApostrophes(word)
            val apostropheCount = normalized.count { it == '\'' }
            if (apostropheCount != 1) return null
            val idx = normalized.indexOf('\'')
            if (idx <= 0 || idx >= normalized.lastIndex) return null

            val prefix = normalized.substring(0, idx + 1)
            val root = normalized.substring(idx + 1)
            val prefixRaw = prefix.dropLast(1)

            val isPrefixOk = prefixRaw.isNotEmpty() &&
                    prefixRaw.length <= 3 &&
                    prefixRaw.all { it.isLetter() }
            val isRootOk = root.length >= 3 && root.all { it.isLetter() }
            return if (isPrefixOk && isRootOk) ApostropheSplit(prefix, root) else null
        }

        internal fun isAccentOnlyVariant(input: String, candidate: String): Boolean {
            if (input.equals(candidate, ignoreCase = true)) return false
            val normalizedInput = stripAccents(input.lowercase())
            val normalizedCandidate = stripAccents(candidate.lowercase())
            return normalizedInput == normalizedCandidate
        }

        internal fun recomposeApostropheCandidate(
            split: ApostropheSplit,
            candidate: String
        ): String? {
            val prefix = split.prefix
            val normalizedCandidate = normalizeApostrophes(candidate)
            val hasApostrophe = normalizedCandidate.contains('\'')
            val matchesPrefix = normalizedCandidate.length >= prefix.length &&
                    normalizedCandidate.substring(0, prefix.length).equals(prefix, ignoreCase = true)

            val rootPart = when {
                matchesPrefix -> candidate.substring(prefix.length)
                hasApostrophe -> return null // don't mix different apostrophe prefixes
                else -> candidate
            }
            val recasedRoot = CasingHelper.applyCasing(rootPart, split.root, forceLeadingCapital = false)
            return prefix + recasedRoot
        }
    }
    
    // Track last replacement for undo
    private data class LastReplacement(
        val originalWord: String,
        val replacedWord: String
    )
    private var lastReplacement: LastReplacement? = null
    private var lastUndoOriginalWord: String? = null
    
    // Track rejected words to avoid auto-correcting them again
    private val rejectedWords = mutableSetOf<String>()

    private fun hasTrailingHardBoundary(textBeforeCursor: String): Boolean {
        var i = textBeforeCursor.length - 1
        while (i >= 0) {
            val normalized = it.palsoftware.pastiera.core.Punctuation.normalizeApostrophe(textBeforeCursor[i])
            if (normalized.isWhitespace() || normalized in it.palsoftware.pastiera.core.Punctuation.BOUNDARY) {
                i--
                continue
            }
            if (normalized.isLetterOrDigit() || normalized == '\'') {
                return false
            }
            return true
        }
        return false
    }

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

        // If there's a non-word symbol between the last word and cursor (e.g., emoji), skip.
        val textBefore = inputConnection.getTextBeforeCursor(32, 0)?.toString().orEmpty()
        if (hasTrailingHardBoundary(textBefore)) {
            tracker.onBoundaryReached(boundaryChar, inputConnection)
            return ReplaceResult(false, unicodeChar != 0)
        }

        val word = tracker.currentWord
        // #region agent log
        val textBeforeReal = inputConnection?.getTextBeforeCursor(16, 0)?.toString().orEmpty()
        debugLog("C", "AutoReplaceController.handleBoundary:beforeReplace", "handleBoundary called", mapOf(
            "trackerWord" to word,
            "trackerWordLength" to word.length,
            "textBeforeReal" to textBeforeReal,
            "textBeforeRealLength" to textBeforeReal.length,
            "keyCode" to keyCode,
            "boundaryChar" to (boundaryChar?.toString() ?: "null")
        ))
        // #endregion
        if (word.isBlank()) {
            tracker.onBoundaryReached(boundaryChar, inputConnection)
            return ReplaceResult(false, unicodeChar != 0)
        }

        val apostropheSplit = splitApostropheWord(word)
        val lookupWord = apostropheSplit?.root ?: word

        val suggestions = suggestionEngine.suggest(
            lookupWord,
            limit = 1,
            includeAccentMatching = settings.accentMatching,
            useKeyboardProximity = settings.useKeyboardProximity,
            useEditTypeRanking = settings.useEditTypeRanking
        )
        val topRaw = suggestions.firstOrNull()
        val top = topRaw?.let {
            if (apostropheSplit != null) {
                val recomposed = recomposeApostropheCandidate(apostropheSplit, it.candidate) ?: return@let null
                it.copy(candidate = recomposed)
            } else {
                it
            }
        }
        
        // Safety checks for auto-replace
        val isAccentVariant = top != null && isAccentOnlyVariant(word, top.candidate)
        val minWordLength = if (isAccentVariant) 2 else 3 // Allow short accent-only fixes (e.g., "ja" -> "já")
        val maxLengthRatio = 1.25 // Don't auto-correct if replacement is >25% longer
        
        // Check if word has been rejected by user
        val wordLower = word.lowercase()
        val isRejected = rejectedWords.contains(wordLower)
        
        // Check if word exists in dictionary
        val isKnownWord = repository.isKnownWord(lookupWord)
        val isExactKnownWord = repository.getExactWordFrequency(lookupWord) > 0

        // Only auto-replace if word is NOT known (i.e., it's a typo/unknown word)
        // Don't replace valid words with other valid words, even if they have higher frequency
        val shouldReplace = top != null
            && (!isKnownWord || (isAccentVariant && !isExactKnownWord)) // Allow accent-only fix when exact word isn't known
            && !isRejected // Don't auto-correct if user has rejected this word
            && top.distance <= settings.maxAutoReplaceDistance
            && lookupWord.length >= minWordLength // Minimum word length check on root
            && top.candidate.length <= (word.length * maxLengthRatio).toInt() // Max length ratio check on full text

        if (shouldReplace) {
            val replacement = applyCasing(top!!.candidate, word)
            // #region agent log
            val textBeforeDelete = inputConnection.getTextBeforeCursor(16, 0)?.toString().orEmpty()
            debugLog("C", "AutoReplaceController.handleBoundary:beforeDelete", "about to deleteSurroundingText", mapOf(
                "trackerWord" to word,
                "trackerWordLength" to word.length,
                "deleteCount" to word.length,
                "textBeforeDelete" to textBeforeDelete,
                "textBeforeDeleteLength" to textBeforeDelete.length,
                "replacement" to replacement
            ))
            // #endregion
            inputConnection.beginBatchEdit()
            inputConnection.deleteSurroundingText(word.length, 0)
            // #region agent log
            val textAfterDelete = inputConnection.getTextBeforeCursor(16, 0)?.toString().orEmpty()
            debugLog("C", "AutoReplaceController.handleBoundary:afterDelete", "deleteSurroundingText completed", mapOf(
                "textAfterDelete" to textAfterDelete,
                "textAfterDeleteLength" to textAfterDelete.length,
                "deletedCount" to word.length
            ))
            // #endregion
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
                        textBeforeCursor[i] in it.palsoftware.pastiera.core.Punctuation.BOUNDARY)
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
        splitApostropheWord(replacement.originalWord)?.root?.lowercase()?.let { rejectedWords.add(it) }
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
