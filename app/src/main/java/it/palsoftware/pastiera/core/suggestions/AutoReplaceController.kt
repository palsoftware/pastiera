package it.palsoftware.pastiera.core.suggestions

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.core.AutoSpaceTracker
import it.palsoftware.pastiera.core.Punctuation
import android.util.Log
import java.text.Normalizer
import java.util.Locale
import it.palsoftware.pastiera.inputmethod.DebugCaptureStore

class AutoReplaceController(
    private val repository: DictionaryRepository,
    private val suggestionEngine: SuggestionEngine,
    private val settingsProvider: () -> SuggestionSettings,
    private val knownWordProvider: ((String) -> Boolean)? = null,
    private val exactReplacementProvider: ((String, Char?) -> String?)? = null
) {
    private fun triggerFromBoundaryChar(boundaryChar: Char?): DebugCaptureStore.AutoCorrectionTrigger {
        return when (boundaryChar) {
            ' ' -> DebugCaptureStore.AutoCorrectionTrigger.SPACE
            '\n' -> DebugCaptureStore.AutoCorrectionTrigger.ENTER
            else -> DebugCaptureStore.AutoCorrectionTrigger.OTHER
        }
    }

    private fun punctuationBoundaryForKeyCode(keyCode: Int): Char? {
        return when (keyCode) {
            KeyEvent.KEYCODE_COMMA -> ','
            KeyEvent.KEYCODE_PERIOD -> '.'
            KeyEvent.KEYCODE_SEMICOLON -> ';'
            KeyEvent.KEYCODE_SLASH -> '/'
            else -> null
        }
    }
    public data class ReplaceResult(
        val replaced: Boolean,
        val committed: Boolean,
        val replacement: String? = null
    )

    companion object {
        internal data class ApostropheSplit(val prefix: String, val root: String)

        internal fun normalizeApostrophes(input: String): String {
            return WordNormalization.normalizeApostrophes(input)
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
                    isSupportedApostrophePrefix(prefixRaw) &&
                    prefixRaw.all { it.isLetter() }
            val isRootOk = root.length >= 3 && root.all { it.isLetter() }
            return if (isPrefixOk && isRootOk) ApostropheSplit(prefix, root) else null
        }

        private fun isSupportedApostrophePrefix(prefix: String): Boolean {
            if (prefix.length <= 3) return true
            return prefix.lowercase(Locale.ROOT) in setOf(
                "dall",
                "dell",
                "nell",
                "sull",
                "coll",
                "quell",
                "quest"
            )
        }

        internal fun isAccentOnlyVariant(input: String, candidate: String): Boolean {
            if (input.equals(candidate, ignoreCase = true)) return false
            val normalizedInput = WordNormalization.normalizeForDictionary(input, Locale.ROOT)
            val normalizedCandidate = WordNormalization.normalizeForDictionary(candidate, Locale.ROOT)
            return normalizedInput == normalizedCandidate
        }

        internal fun isCaseOnlyVariant(input: String, candidate: String): Boolean {
            if (input.isEmpty() || candidate.isEmpty()) return false
            return input != candidate && input.equals(candidate, ignoreCase = true)
        }

        internal fun isAcronymLike(candidate: String): Boolean {
            val letters = candidate.filter { it.isLetter() }
            return letters.length >= 2 && letters.all { it.isUpperCase() }
        }

        internal fun hasSingleRepeatedCharInsertion(input: String, candidate: String): Boolean {
            if (candidate.length != input.length + 1) return false
            var i = 0
            var j = 0
            var insertedIndex = -1
            while (i < input.length && j < candidate.length) {
                if (input[i].equals(candidate[j], ignoreCase = true)) {
                    i++
                    j++
                } else {
                    if (insertedIndex != -1) return false
                    insertedIndex = j
                    j++
                }
            }
            if (insertedIndex == -1) insertedIndex = candidate.lastIndex
            val inserted = candidate.getOrNull(insertedIndex) ?: return false
            val prev = candidate.getOrNull(insertedIndex - 1)
            val next = candidate.getOrNull(insertedIndex + 1)
            return prev?.equals(inserted, ignoreCase = true) == true ||
                next?.equals(inserted, ignoreCase = true) == true
        }

        private fun changesFirstLetter(input: String, candidate: String): Boolean {
            val inputIndex = input.indexOfFirst { it.isLetter() }
            val candidateIndex = candidate.indexOfFirst { it.isLetter() }
            if (inputIndex < 0 || candidateIndex < 0) return false
            return !input[inputIndex].equals(candidate[candidateIndex], ignoreCase = true)
        }

        internal fun isSafeAutoReplaceCandidate(
            input: String,
            lookupWord: String,
            candidate: SuggestionResult?,
            settings: SuggestionSettings,
            isOrthographicVariant: Boolean
        ): Boolean {
            if (candidate == null) return false
            val isCaseVariant = isCaseOnlyVariant(input, candidate.candidate)
            if (candidate.kind != SuggestionKind.CURRENT_WORD) return false
            if (!isOrthographicVariant && !isCaseVariant && candidate.distance <= 0) return false
            if (candidate.distance > settings.maxAutoReplaceDistance) return false
            if (input.all { it.isLowerCase() } && isAcronymLike(candidate.candidate)) return false
            if (!isCaseVariant &&
                input.firstOrNull()?.isLowerCase() == true &&
                candidate.candidate.firstOrNull()?.isUpperCase() == true
            ) {
                return false
            }
            val lengthDelta = candidate.candidate.length - input.length
            if (lengthDelta == 0) {
                return isOrthographicVariant || isCaseVariant || !changesFirstLetter(input, candidate.candidate)
            }
            if (lengthDelta == 1 && hasSingleRepeatedCharInsertion(input, candidate.candidate)) return true

            // Pure suffix/prefix growth or shortening is usually morphology/completion, not a typo.
            return isOrthographicVariant && lookupWord.length == candidate.candidate.length
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
        inputConnection: InputConnection?,
        boundaryCharOverride: Char? = null
    ): ReplaceResult {
        fun ensureTrailingSpace(connection: InputConnection): Boolean {
            val before = connection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (before.endsWith(" ")) {
                return true
            }
            connection.commitText(" ", 1)
            val afterCommit = connection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (afterCommit.endsWith(" ")) {
                return true
            }
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE))
            val afterKey = connection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            return afterKey.endsWith(" ")
        }

        fun commitBoundary(connection: InputConnection, boundary: Char, settings: SuggestionSettings): Boolean {
            if (
                settings.frenchPunctuationSpacing &&
                Punctuation.commitFrenchSpacedPunctuation(connection, boundary)
            ) {
                return true
            }
            if (
                settings.commaSpace &&
                Punctuation.commitCommaSpace(connection, boundary)
            ) {
                return true
            }
            return when (boundary) {
                ' ' -> ensureTrailingSpace(connection).also { committed ->
                    if (committed) AutoSpaceTracker.markAutoSpace()
                }
                else -> {
                    connection.commitText(boundary.toString(), 1)
                    true
                }
            }
        }

        fun commitBoundaryAndReset(
            tracker: CurrentWordTracker,
            connection: InputConnection,
            boundary: Char?,
            settings: SuggestionSettings
        ): Boolean {
            if (boundary != null) {
                val committed = commitBoundary(connection, boundary, settings)
                tracker.reset()
                return committed
            }
            tracker.onBoundaryReached(null, connection)
            return false
        }

        val unicodeChar = event?.unicodeChar ?: 0
        val boundaryChar = boundaryCharOverride ?: when {
            unicodeChar != 0 -> unicodeChar.toChar()
            keyCode == KeyEvent.KEYCODE_SPACE -> ' '
            keyCode == KeyEvent.KEYCODE_ENTER -> '\n'
            else -> punctuationBoundaryForKeyCode(keyCode)
        }
        val boundaryCommittedByTracker = boundaryChar != null && inputConnection != null

        val settings = settingsProvider()
        val trigger = triggerFromBoundaryChar(boundaryChar)
        if (inputConnection == null) {
            tracker.onBoundaryReached(boundaryChar, inputConnection)
            DebugCaptureStore.recordAutoCorrectionAttempt(
                before = tracker.currentWord,
                trigger = trigger,
                outcome = DebugCaptureStore.AutoCorrectionOutcome.NOT_APPLICABLE,
                reason = "no_input_connection"
            )
            return ReplaceResult(false, boundaryCommittedByTracker)
        }

        // If there's a non-word symbol between the last word and cursor (e.g., emoji), skip.
        val textBefore = inputConnection.getTextBeforeCursor(32, 0)?.toString().orEmpty()
        if (hasTrailingHardBoundary(textBefore)) {
            val boundaryCommitted = commitBoundaryAndReset(tracker, inputConnection, boundaryChar, settings)
            DebugCaptureStore.recordAutoCorrectionAttempt(
                before = tracker.currentWord,
                trigger = trigger,
                outcome = DebugCaptureStore.AutoCorrectionOutcome.SKIPPED,
                reason = "hard_boundary_before_cursor"
            )
            return ReplaceResult(false, boundaryCommitted)
        }

        val word = tracker.currentWord
        if (word.isBlank()) {
            val boundaryCommitted = commitBoundaryAndReset(tracker, inputConnection, boundaryChar, settings)
            DebugCaptureStore.recordAutoCorrectionAttempt(
                before = word,
                trigger = trigger,
                outcome = DebugCaptureStore.AutoCorrectionOutcome.NOT_APPLICABLE,
                reason = "empty_word"
            )
            return ReplaceResult(false, boundaryCommitted)
        }

        val apostropheSplit = splitApostropheWord(word)
        val lookupWord = apostropheSplit?.root ?: word
        val wordLower = word.lowercase()

        val exactReplacement = if (settings.textReplacementsEnabled) {
            exactReplacementProvider?.invoke(word, boundaryChar)
        } else {
            null
        }
        exactReplacement?.let { replacement ->
            if (!rejectedWords.contains(wordLower)) {
                inputConnection.beginBatchEdit()
                inputConnection.deleteSurroundingText(word.length, 0)
                val shouldAppendBoundary = boundaryChar != null &&
                    !(boundaryChar == ' ' && replacement.endsWith("'"))
                inputConnection.commitText(replacement, 1)
                repository.markUsed(replacement)
                lastReplacement = LastReplacement(
                    originalWord = word,
                    replacedWord = replacement
                )
                tracker.reset()
                inputConnection.endBatchEdit()
                var boundaryCommitted = false
                if (shouldAppendBoundary) {
                    boundaryCommitted = commitBoundary(inputConnection, boundaryChar, settings)
                }
                DebugCaptureStore.recordAutoCorrectionCommit(
                    before = word,
                    after = replacement,
                    trigger = trigger,
                    source = "TEXT_REPLACEMENT",
                    distance = 0,
                    kind = SuggestionKind.CURRENT_WORD.name
                )
                Log.d("AutoReplaceController", "Committed exact replacement '$word' -> '$replacement'")
                return ReplaceResult(true, true, replacement)
            }
        }

        if (!settings.autoReplaceOnSpaceEnter) {
            val boundaryCommitted = commitBoundaryAndReset(tracker, inputConnection, boundaryChar, settings)
            DebugCaptureStore.recordAutoCorrectionAttempt(
                before = word,
                trigger = trigger,
                outcome = DebugCaptureStore.AutoCorrectionOutcome.NOT_APPLICABLE,
                reason = "auto_replace_disabled"
            )
            return ReplaceResult(false, boundaryCommitted)
        }

        primaryDictionaryCaseVariant(lookupWord, word)?.let { caseReplacement ->
            if (!rejectedWords.contains(wordLower)) {
                inputConnection.beginBatchEdit()
                inputConnection.deleteSurroundingText(word.length, 0)
                val shouldAppendBoundary = boundaryChar != null &&
                    !(boundaryChar == ' ' && caseReplacement.endsWith("'"))
                inputConnection.commitText(caseReplacement, 1)
                repository.markUsed(caseReplacement)
                lastReplacement = LastReplacement(
                    originalWord = word,
                    replacedWord = caseReplacement
                )
                tracker.reset()
                inputConnection.endBatchEdit()
                var boundaryCommitted = false
                if (shouldAppendBoundary) {
                    boundaryCommitted = commitBoundary(inputConnection, boundaryChar, settings)
                }
                DebugCaptureStore.recordAutoCorrectionCommit(
                    before = word,
                    after = caseReplacement,
                    trigger = trigger,
                    source = "PRIMARY_CASE",
                    distance = 0,
                    kind = SuggestionKind.CURRENT_WORD.name
                )
                Log.d("AutoReplaceController", "Committed primary dictionary case replacement '$word' -> '$caseReplacement'")
                return ReplaceResult(true, true, caseReplacement)
            }
        }

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
        val isOrthographicVariant = top != null && isAccentOnlyVariant(word, top.candidate)
        val isCaseVariant = top != null && isCaseOnlyVariant(word, top.candidate)
        val minWordLength = if (isOrthographicVariant) 2 else 3 // Allow short orthographic fixes (e.g., "ja" -> "já")
        val maxLengthRatio = 1.25 // Keep as a fallback guard for longer typo candidates.
        
        // Check if word has been rejected by user
        val isRejected = rejectedWords.contains(wordLower)
        
        // Check if word exists in dictionary
        val isKnownWord = knownWordProvider?.invoke(lookupWord) ?: repository.isKnownWord(lookupWord)
        val isExactKnownWord = repository.getExactWordFrequency(lookupWord) > 0
        val hasExactPrimaryCase = primaryDictionaryHasExactCase(lookupWord)

        // Only auto-replace if word is NOT known (i.e., it's a typo/unknown word)
        // Don't replace valid words with other valid words, even if they have higher frequency
        val isSafeCandidate = isSafeAutoReplaceCandidate(
            input = word,
            lookupWord = lookupWord,
            candidate = top,
            settings = settings,
            isOrthographicVariant = isOrthographicVariant
        )

        val shouldReplace = top != null
            && (!isKnownWord || (isCaseVariant && !hasExactPrimaryCase) || (isOrthographicVariant && !isExactKnownWord)) // Allow case/orthographic fixes
            && !isRejected // Don't auto-correct if user has rejected this word
            && isSafeCandidate
            && lookupWord.length >= minWordLength // Minimum word length check on root
            && (top.candidate.length <= (word.length * maxLengthRatio).toInt() ||
                hasSingleRepeatedCharInsertion(word, top.candidate)) // Max length ratio check on full text

	        if (shouldReplace) {
	            val replacement = applyCasing(top!!.candidate, word)
	            if (replacement == word) {
	                DebugCaptureStore.recordAutoCorrectionAttempt(
	                    before = word,
	                    trigger = trigger,
	                    source = top.source.name,
	                    after = top.candidate,
	                    outcome = DebugCaptureStore.AutoCorrectionOutcome.SKIPPED,
	                    reason = "same_replacement",
	                    distance = top.distance,
	                    kind = top.kind.name
	                )
	                val boundaryCommitted = commitBoundaryAndReset(tracker, inputConnection, boundaryChar, settings)
	                return ReplaceResult(false, boundaryCommitted)
	            }
	            val source = top.source.name
	            inputConnection.beginBatchEdit()
            inputConnection.deleteSurroundingText(word.length, 0)
            val shouldAppendBoundary = boundaryChar != null &&
                !(boundaryChar == ' ' && replacement.endsWith("'"))
            inputConnection.commitText(replacement, 1)
            repository.markUsed(replacement)
            
            // Store last replacement for undo
            lastReplacement = LastReplacement(
                originalWord = word,
                replacedWord = replacement
            )
            
            tracker.reset()
            inputConnection.endBatchEdit()
            var boundaryCommitted = false
            if (shouldAppendBoundary) {
                boundaryCommitted = commitBoundary(inputConnection, boundaryChar, settings)
            }
            val committedSuffix = if (boundaryCommitted && shouldAppendBoundary) boundaryChar.toString() else ""
            DebugCaptureStore.recordAutoCorrectionCommit(
                before = word,
                after = replacement,
                trigger = trigger,
                source = source,
                distance = top.distance,
                kind = top.kind.name
            )
            Log.d("AutoReplaceController", "Committed text '${replacement + committedSuffix}', markAutoSpace=${boundaryCommitted && boundaryChar == ' '}")
            return ReplaceResult(true, true, replacement)
        }

        val skipReason = when {
            top == null -> "no_suggestion"
            isRejected -> "rejected_by_user"
            isKnownWord && !isCaseVariant && !(isOrthographicVariant && !isExactKnownWord) -> "known_word"
            top.distance > settings.maxAutoReplaceDistance -> "distance_too_high"
            top.kind != SuggestionKind.CURRENT_WORD -> "not_current_word"
            !isOrthographicVariant && !isCaseVariant && top.distance <= 0 -> "not_edit_distance"
            word.all { it.isLowerCase() } && isAcronymLike(top.candidate) -> "acronym_candidate"
            !isSafeCandidate -> "unsafe_shape"
            lookupWord.length < minWordLength -> "word_too_short"
            top.candidate.length > (word.length * maxLengthRatio).toInt() &&
                !hasSingleRepeatedCharInsertion(word, top.candidate) -> "candidate_too_long"
            else -> "constraints_not_met"
        }
        DebugCaptureStore.recordAutoCorrectionAttempt(
            before = word,
            trigger = trigger,
            source = top?.source?.name ?: "UNKNOWN",
            after = top?.candidate,
            outcome = DebugCaptureStore.AutoCorrectionOutcome.SKIPPED,
            reason = skipReason,
            distance = top?.distance,
            kind = top?.kind?.name
        )

        // Clear last replacement if no replacement happened
        lastReplacement = null
        val boundaryCommitted = commitBoundaryAndReset(tracker, inputConnection, boundaryChar, settings)
        return ReplaceResult(false, boundaryCommitted)
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

    private fun primaryDictionaryCaseVariant(lookupWord: String, originalWord: String): String? {
        if (!repository.isReady || lookupWord != originalWord) return null
        if (originalWord.none { it.isLetter() } || originalWord.any { it.isUpperCase() }) return null

        val normalized = WordNormalization.normalizeForDictionary(originalWord, Locale.ROOT)
        val entries = repository.topByNormalized(normalized, limit = 8)
        if (entries.any { it.word == originalWord }) return null

        return entries
            .firstOrNull { entry ->
                entry.word != originalWord &&
                    entry.word.equals(originalWord, ignoreCase = true) &&
                    entry.word.any { it.isUpperCase() }
            }
            ?.word
    }

    private fun primaryDictionaryHasExactCase(word: String): Boolean {
        if (!repository.isReady || word.isBlank()) return false
        val normalized = WordNormalization.normalizeForDictionary(word, Locale.ROOT)
        return repository.topByNormalized(normalized, limit = 8).any { it.word == word }
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
