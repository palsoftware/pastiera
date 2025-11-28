package it.palsoftware.pastiera.inputmethod

import android.text.TextUtils
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.InputContextState

/**
 * Central helper for smart auto-capitalization rules.
 * Provides a single decision path and a single entry point to enable/clear smart Shift.
 */
object AutoCapitalizeHelper {
    private var smartShiftRequested = false

    private data class AutoCapSettings(
        val autoCapFirstLetter: Boolean,
        val autoCapAfterPeriod: Boolean
    )

    data class CursorContext(
        val before: CharSequence,
        val after: CharSequence
    )

    private fun resolveAutoCapSettings(
        context: android.content.Context,
        inputConnection: InputConnection
    ): AutoCapSettings {
        val userAutoCapFirstLetter = SettingsManager.getAutoCapitalizeFirstLetter(context)
        val userAutoCapAfterPeriod = SettingsManager.getAutoCapitalizeAfterPeriod(context)

        // Respect user preferences: if explicitly disabled, don't apply auto-cap
        // even if the input field requests it. If enabled, always apply.
        // This gives users full control over the feature.
        val autoCapFirstLetter = userAutoCapFirstLetter
        val autoCapAfterPeriod = userAutoCapAfterPeriod

        return AutoCapSettings(autoCapFirstLetter, autoCapAfterPeriod)
    }

    /**
     * Reads the cursor context, ignoring any selected text (treated as removed/replaced).
     * Prefers ExtractedText; falls back to surrounding text APIs.
     */
    private fun readContext(inputConnection: InputConnection): CursorContext? {
        val extracted = inputConnection.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted != null && extracted.text != null) {
            val text = extracted.text
            val selStart = extracted.selectionStart
            val selEnd = extracted.selectionEnd
            if (selStart >= 0 && selEnd >= selStart && selEnd <= text.length) {
                val before = text.subSequence(0, selStart)
                val after = text.subSequence(selEnd, text.length)
                return CursorContext(before, after)
            }
        }

        val before = inputConnection.getTextBeforeCursor(200, 0) ?: return null
        val after = inputConnection.getTextAfterCursor(200, 0) ?: ""
        val selected = inputConnection.getSelectedText(0) ?: ""

        val beforeEffective = when {
            selected.isEmpty() -> before
            before.length >= selected.length && before.endsWith(selected) ->
                before.dropLast(selected.length)
            else -> before
        }
        val afterEffective = when {
            selected.isEmpty() -> after
            after.length >= selected.length && after.startsWith(selected) ->
                after.drop(selected.length)
            else -> after
        }

        return CursorContext(beforeEffective, afterEffective)
    }

    /**
     * Checks if the given text ends with sentence-ending punctuation (.!?)
     * followed by whitespace. Used for auto-capitalization and double-space-to-period.
     * 
     * @param textBeforeCursor The text before the cursor position
     * @param requireWhitespaceAfter If true, requires whitespace after punctuation (for auto-cap).
     *                               If false, only checks if punctuation exists (for double-space prevention).
     * @return true if text ends with sentence-ending punctuation (with optional whitespace requirement)
     */
    fun hasSentenceEndingPunctuation(
        textBeforeCursor: CharSequence,
        requireWhitespaceAfter: Boolean = true
    ): Boolean {
        if (textBeforeCursor.isEmpty()) return false
        
        val lastNonWhitespaceIndex = textBeforeCursor.indexOfLast { !it.isWhitespace() }
        if (lastNonWhitespaceIndex < 0) return false
        
        val lastNonWhitespaceChar = textBeforeCursor[lastNonWhitespaceIndex]
        val isSentencePunctuation = when (lastNonWhitespaceChar) {
            '.' -> {
                val prevIndex = lastNonWhitespaceIndex - 1
                val isNotDoublePeriod = !(prevIndex >= 0 && textBeforeCursor[prevIndex] == '.')
                if (!isNotDoublePeriod) return false
                
                if (requireWhitespaceAfter) {
                    // All characters after period must be whitespace (end of sentence)
                    lastNonWhitespaceIndex < textBeforeCursor.length - 1 &&
                        (lastNonWhitespaceIndex + 1 until textBeforeCursor.length)
                            .all { textBeforeCursor[it].isWhitespace() }
                } else {
                    true // Just check if it's a period (not double)
                }
            }
            '!', '?' -> {
                if (requireWhitespaceAfter) {
                    // All characters after punctuation must be whitespace (end of sentence)
                    lastNonWhitespaceIndex < textBeforeCursor.length - 1 &&
                        (lastNonWhitespaceIndex + 1 until textBeforeCursor.length)
                            .all { textBeforeCursor[it].isWhitespace() }
                } else {
                    true // Just check if it's sentence-ending punctuation
                }
            }
            else -> false
        }
        
        return isSentencePunctuation
    }

    /**
     * Pure decision: should smart auto-cap request Shift given before/after?
     */
    private fun shouldAutoCap(
        settings: AutoCapSettings,
        before: CharSequence,
        after: CharSequence
    ): Boolean {
        val isCursorAtStart = before.isEmpty()
        val isAfterNewline = before.lastOrNull() == '\n'

        if (settings.autoCapFirstLetter && (isCursorAtStart || isAfterNewline)) {
            return true
        }

        if (settings.autoCapAfterPeriod && before.isNotEmpty()) {
            if (hasSentenceEndingPunctuation(before, requireWhitespaceAfter = true)) {
                return true
            }
        }

        return false
    }

    private fun clearSmartShift(
        disableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        if (smartShiftRequested) {
            val changed = disableShift()
            smartShiftRequested = false
            if (changed) onUpdateStatusBar()
        }
    }

    /**
     * Single entry point to evaluate and set/clear smart Shift.
     * Also considers field-specific capitalization flags (CAP_WORDS, CAP_SENTENCES).
     * Field-specific flags take precedence over user settings.
     */
    fun maybeEnableSmartShift(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableAutoCapitalize: Boolean,
        enableShift: () -> Boolean,
        disableShift: () -> Boolean = { false },
        onUpdateStatusBar: () -> Unit,
        inputContextState: InputContextState? = null
    ) {
        val ic = inputConnection ?: run {
            clearSmartShift(disableShift, onUpdateStatusBar)
            return
        }

        // Check field-specific capitalization flags FIRST
        // These take precedence over user settings and shouldDisableAutoCapitalize
        if (inputContextState != null) {
            // CAP_CHARACTERS is handled separately (caps lock)
            if (inputContextState.requiresCapCharacters) {
                clearSmartShift(disableShift, onUpdateStatusBar)
                return
            }
            
            // CAP_WORDS: capitalize at start of word (ignore user settings)
            if (inputContextState.requiresCapWords) {
                if (isAtStartOfWord(ic)) {
                    if (enableShift()) {
                        smartShiftRequested = true
                        onUpdateStatusBar()
                    }
                    return
                } else {
                    clearSmartShift(disableShift, onUpdateStatusBar)
                    return
                }
            }
            
            // CAP_SENTENCES: capitalize at start of sentence (ignore user settings)
            if (inputContextState.requiresCapSentences) {
                if (isAtStartOfSentence(ic)) {
                    if (enableShift()) {
                        smartShiftRequested = true
                        onUpdateStatusBar()
                    }
                    return
                } else {
                    clearSmartShift(disableShift, onUpdateStatusBar)
                    return
                }
            }
        }

        // Only check shouldDisableAutoCapitalize for user settings-based auto-cap
        if (shouldDisableAutoCapitalize) {
            clearSmartShift(disableShift, onUpdateStatusBar)
            return
        }

        // Fall back to user settings-based auto-capitalization
        val settings = resolveAutoCapSettings(context, ic)
        if (!settings.autoCapFirstLetter && !settings.autoCapAfterPeriod) {
            clearSmartShift(disableShift, onUpdateStatusBar)
            return
        }

        val cursorContext = readContext(ic) ?: run {
            clearSmartShift(disableShift, onUpdateStatusBar)
            return
        }

        val shouldCapitalize = shouldAutoCap(settings, cursorContext.before, cursorContext.after)
        if (shouldCapitalize) {
            if (enableShift()) {
                smartShiftRequested = true
                onUpdateStatusBar()
            }
        } else {
            clearSmartShift(disableShift, onUpdateStatusBar)
        }
    }

    fun shouldAutoCapitalizeAtCursor(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableAutoCapitalize: Boolean
    ): Boolean {
        if (inputConnection == null || shouldDisableAutoCapitalize) return false
        val settings = resolveAutoCapSettings(context, inputConnection)
        if (!settings.autoCapFirstLetter && !settings.autoCapAfterPeriod) {
            return false
        }
        val cursorContext = readContext(inputConnection) ?: return false
        return shouldAutoCap(settings, cursorContext.before, cursorContext.after)
    }

    // Thin wrappers kept for call sites; all delegate to maybeEnableSmartShift.
    fun checkAndEnableAutoCapitalize(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableAutoCapitalize: Boolean,
        enableShift: () -> Boolean,
        disableShift: () -> Boolean = { false },
        onUpdateStatusBar: () -> Unit
    ) {
        maybeEnableSmartShift(
            context = context,
            inputConnection = inputConnection,
            shouldDisableAutoCapitalize = shouldDisableAutoCapitalize,
            enableShift = enableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar
        )
    }

    fun checkAutoCapitalizeOnSelectionChange(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableAutoCapitalize: Boolean,
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        enableShift: () -> Boolean,
        disableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit,
        inputContextState: InputContextState? = null
    ) {
        maybeEnableSmartShift(
            context = context,
            inputConnection = inputConnection,
            shouldDisableAutoCapitalize = shouldDisableAutoCapitalize,
            enableShift = enableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar,
            inputContextState = inputContextState
        )
    }

    fun checkAutoCapitalizeOnRestart(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableAutoCapitalize: Boolean,
        enableShift: () -> Boolean,
        disableShift: () -> Boolean = { false },
        onUpdateStatusBar: () -> Unit,
        inputContextState: InputContextState? = null
    ) {
        maybeEnableSmartShift(
            context = context,
            inputConnection = inputConnection,
            shouldDisableAutoCapitalize = shouldDisableAutoCapitalize,
            enableShift = enableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar,
            inputContextState = inputContextState
        )
    }

    fun enableAfterPunctuation(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableAutoCapitalize: Boolean,
        onEnableShift: () -> Boolean,
        disableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        maybeEnableSmartShift(
            context = context,
            inputConnection = inputConnection,
            shouldDisableAutoCapitalize = shouldDisableAutoCapitalize,
            enableShift = onEnableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar
        )
    }

    fun enableAfterEnter(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableAutoCapitalize: Boolean,
        onEnableShift: () -> Boolean,
        disableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        maybeEnableSmartShift(
            context = context,
            inputConnection = inputConnection,
            shouldDisableAutoCapitalize = shouldDisableAutoCapitalize,
            enableShift = onEnableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar
        )
    }

    /**
     * Checks if the cursor is at the start of a word (after space or word-separating punctuation).
     * Used for textCapWords to determine if the next letter should be capitalized.
     */
    fun isAtStartOfWord(inputConnection: InputConnection?): Boolean {
        if (inputConnection == null) return false
        
        val cursorContext = readContext(inputConnection) ?: return false
        val before = cursorContext.before
        
        // At start of field
        if (before.isEmpty()) return true
        
        // Check if last character is whitespace or word-separating punctuation
        val lastChar = before.lastOrNull() ?: return false
        return lastChar.isWhitespace() || lastChar in ".,;:!?()[]{}\"'"
    }
    
    /**
     * Checks if the cursor is at the start of a sentence (after sentence-ending punctuation).
     * Used for textCapSentences to determine if the next letter should be capitalized.
     */
    fun isAtStartOfSentence(inputConnection: InputConnection?): Boolean {
        if (inputConnection == null) return false
        
        val cursorContext = readContext(inputConnection) ?: return false
        val before = cursorContext.before
        
        // At start of field
        if (before.isEmpty()) return true
        
        // Check if text ends with sentence-ending punctuation followed by whitespace
        return hasSentenceEndingPunctuation(before, requireWhitespaceAfter = true)
    }
    
    /**
     * Handles input field capitalization flags (CAP_CHARACTERS, CAP_WORDS, CAP_SENTENCES).
     * This is called when entering a new input field to apply field-specific
     * capitalization rules.
     */
    fun handleInputFieldCapitalizationFlags(
        state: InputContextState,
        inputConnection: InputConnection?,
        enableCapsLock: () -> Unit,
        enableShiftOneShot: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        if (!state.isEditable) return
        
        // Handle textCapCharacters: enable caps lock automatically
        if (state.requiresCapCharacters) {
            enableCapsLock()
            onUpdateStatusBar()
            return // CAP_CHARACTERS takes precedence
        }
        
        // Handle textCapWords: enable shift one-shot if at start of word
        if (state.requiresCapWords) {
            if (isAtStartOfWord(inputConnection)) {
                if (enableShiftOneShot()) {
                    smartShiftRequested = true
                    onUpdateStatusBar()
                }
            }
        }
        
        // Handle textCapSentences: enable shift one-shot if at start of sentence
        // Note: This works together with the existing auto-cap logic
        if (state.requiresCapSentences) {
            if (isAtStartOfSentence(inputConnection)) {
                if (enableShiftOneShot()) {
                    smartShiftRequested = true
                    onUpdateStatusBar()
                }
            }
        }
    }
    
    /**
     * Checks if capitalization should be applied after a boundary key (space, enter, punctuation)
     * based on field capitalization flags. This is called when a boundary key is pressed.
     */
    fun shouldCapitalizeAfterBoundary(
        state: InputContextState,
        inputConnection: InputConnection?,
        keyCode: Int
    ): Boolean {
        if (!state.isEditable || inputConnection == null) return false
        
        // CAP_CHARACTERS doesn't need per-character checks (caps lock handles it)
        if (state.requiresCapCharacters) return false
        
        val isSpace = keyCode == KeyEvent.KEYCODE_SPACE
        val isEnter = keyCode == KeyEvent.KEYCODE_ENTER
        
        // For CAP_WORDS: capitalize after space or enter
        if (state.requiresCapWords && (isSpace || isEnter)) {
            return true
        }
        
        // For CAP_SENTENCES: capitalize after space/enter if text ends with sentence-ending punctuation
        if (state.requiresCapSentences && (isSpace || isEnter)) {
            val cursorContext = readContext(inputConnection) ?: return false
            val before = cursorContext.before
            // Use hasSentenceEndingPunctuation to properly check for ". " or "! " or "? "
            return hasSentenceEndingPunctuation(before, requireWhitespaceAfter = true)
        }
        
        return false
    }
}
