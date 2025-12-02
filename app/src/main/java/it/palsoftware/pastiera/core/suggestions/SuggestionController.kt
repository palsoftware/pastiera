package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.util.Log
import java.util.concurrent.atomic.AtomicReference
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import it.palsoftware.pastiera.inputmethod.NotificationHelper

class SuggestionController(
    context: Context,
    private val assets: AssetManager,
    private val settingsProvider: () -> SuggestionSettings,
    private val isEnabled: () -> Boolean = { true },
    debugLogging: Boolean = false,
    private val onSuggestionsUpdated: (List<SuggestionResult>) -> Unit,
    private var currentLocale: Locale = Locale.ITALIAN
) {

    private val appContext = context.applicationContext
    private val debugLogging: Boolean = debugLogging
    private val symSpellEngine = SymSpellEngine(appContext, assets, debugLogging = debugLogging)
    private val personalDictionary = PersonalDictionary.getInstance(context)
    private val tracker = CurrentWordTracker(
        onWordChanged = { word ->
            val settings = settingsProvider()
            if (settings.suggestionsEnabled) {
                onSuggestionsUpdated(symSpellEngine.suggest(word, settings.maxSuggestions))
            }
        },
        onWordReset = { onSuggestionsUpdated(emptyList()) }
    )
    private val latestSuggestions: AtomicReference<List<SuggestionResult>> = AtomicReference(emptyList())
    private val loadScope = CoroutineScope(Dispatchers.Default)
    private val cursorHandler = Handler(Looper.getMainLooper())
    private var cursorRunnable: Runnable? = null
    private val cursorDebounceMs = 120L

    // Track last autocorrection for undo on backspace
    private data class LastCorrection(
        val original: String,
        val replacement: String,
        val boundaryChar: Char?
    )
    private var lastCorrection: LastCorrection? = null

    // Words the user has rejected via backspace - don't autocorrect these
    private val ignoredWords = mutableSetOf<String>()

    var suggestionsListener: ((List<SuggestionResult>) -> Unit)? = onSuggestionsUpdated

    init {
        // Eagerly start loading the dictionary in the background
        loadScope.launch {
            symSpellEngine.loadDictionary()
        }
    }

    fun onCharacterCommitted(text: CharSequence, inputConnection: InputConnection?) {
        if (!isEnabled()) return
        if (debugLogging) Log.d("PastieraIME", "SuggestionController.onCharacterCommitted('$text')")
        // User is typing new characters, clear any pending correction undo
        lastCorrection = null
        ensureDictionaryLoaded()

        tracker.onCharacterCommitted(text)
        updateSuggestions()
    }

    fun refreshFromInputConnection(inputConnection: InputConnection?) {
        if (!isEnabled()) return
        tracker.onBackspace()
        updateSuggestions()
    }

    /**
     * Handle backspace key. Returns true if an autocorrection was undone.
     */
    fun onBackspace(inputConnection: InputConnection?): Boolean {
        if (!isEnabled() || inputConnection == null) return false

        val correction = lastCorrection
        if (correction != null) {
            // User is undoing the autocorrection
            if (debugLogging) {
                Log.d("PastieraIME", "Undoing autocorrection: '${correction.replacement}' -> '${correction.original}'")
            }

            inputConnection.beginBatchEdit()

            // Delete the replacement + boundary char
            val deleteLen = correction.replacement.length + (if (correction.boundaryChar != null) 1 else 0)
            inputConnection.deleteSurroundingText(deleteLen, 0)

            // Restore original word + boundary char
            val restoreText = correction.original + (correction.boundaryChar?.toString() ?: "")
            inputConnection.commitText(restoreText, 1)

            inputConnection.endBatchEdit()

            // Add to ignored words so we don't correct it again
            ignoredWords.add(correction.original.lowercase())
            if (debugLogging) {
                Log.d("PastieraIME", "Added '${correction.original}' to ignored words")
            }

            lastCorrection = null
            tracker.reset()
            suggestionsListener?.invoke(emptyList())

            return true
        }

        // No correction to undo, clear any pending correction state
        lastCorrection = null
        return false
    }

    private fun updateSuggestions() {
        val settings = settingsProvider()
        if (settings.suggestionsEnabled) {
            val next = symSpellEngine.suggest(tracker.currentWord, settings.maxSuggestions)
            val summary = next.take(3).joinToString { "${it.candidate}:${it.distance}" }
            if (debugLogging) Log.d("PastieraIME", "suggestions (${next.size}): $summary")
            latestSuggestions.set(next)
            suggestionsListener?.invoke(next)
        } else {
            suggestionsListener?.invoke(emptyList())
        }
    }

    data class ReplaceResult(val replaced: Boolean, val committed: Boolean)

    fun onBoundaryKey(
        keyCode: Int,
        event: KeyEvent?,
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
        if (debugLogging) {
            Log.d("PastieraIME", "onBoundaryKey: autoReplace=${settings.autoReplaceOnSpaceEnter} word='${tracker.currentWord}'")
        }

        if (!settings.autoReplaceOnSpaceEnter || inputConnection == null) {
            tracker.onBoundaryReached(boundaryChar, inputConnection)
            suggestionsListener?.invoke(emptyList())
            return ReplaceResult(false, unicodeChar != 0)
        }

        ensureDictionaryLoaded()

        val word = tracker.currentWord
        if (word.isBlank()) {
            tracker.onBoundaryReached(boundaryChar, inputConnection)
            suggestionsListener?.invoke(emptyList())
            return ReplaceResult(false, unicodeChar != 0)
        }

        // Check if this word was rejected via backspace undo
        val isIgnored = ignoredWords.contains(word.lowercase())
        if (isIgnored) {
            if (debugLogging) {
                Log.d("PastieraIME", "onBoundaryKey: '$word' is in ignored list, skipping autocorrection")
            }
            tracker.onBoundaryReached(boundaryChar, inputConnection)
            suggestionsListener?.invoke(emptyList())
            return ReplaceResult(false, unicodeChar != 0)
        }

        // Check personal dictionary first - it has priority over SymSpell
        val personalEntry = personalDictionary.getEntry(word)
        if (personalEntry != null) {
            if (personalEntry.replacement != null) {
                // User defined a custom replacement - apply it (case-sensitive)
                val replacement = personalEntry.replacement
                if (debugLogging) {
                    Log.d("PastieraIME", "onBoundaryKey: personal dict replacing '$word' -> '$replacement'")
                }
                inputConnection.beginBatchEdit()
                inputConnection.deleteSurroundingText(word.length, 0)
                inputConnection.commitText(replacement, 1)
                tracker.reset()
                inputConnection.endBatchEdit()
                if (boundaryChar != null) {
                    inputConnection.commitText(boundaryChar.toString(), 1)
                }
                lastCorrection = LastCorrection(word, replacement, boundaryChar)
                NotificationHelper.triggerHapticFeedback(appContext)
                suggestionsListener?.invoke(emptyList())
                return ReplaceResult(true, true)
            } else {
                // Word is in personal dict with no replacement - don't autocorrect
                if (debugLogging) {
                    Log.d("PastieraIME", "onBoundaryKey: '$word' is in personal dictionary (no replacement), skipping autocorrection")
                }
                tracker.onBoundaryReached(boundaryChar, inputConnection)
                suggestionsListener?.invoke(emptyList())
                return ReplaceResult(false, unicodeChar != 0)
            }
        }

        // Special case: "i" -> "I"
        if (word == "i") {
            if (debugLogging) {
                Log.d("PastieraIME", "onBoundaryKey: replacing 'i' -> 'I'")
            }
            inputConnection.beginBatchEdit()
            inputConnection.deleteSurroundingText(1, 0)
            inputConnection.commitText("I", 1)
            tracker.reset()
            inputConnection.endBatchEdit()
            if (boundaryChar != null) {
                inputConnection.commitText(boundaryChar.toString(), 1)
            }
            lastCorrection = LastCorrection("i", "I", boundaryChar)
            NotificationHelper.triggerHapticFeedback(appContext)
            suggestionsListener?.invoke(emptyList())
            return ReplaceResult(true, true)
        }

        val suggestions = symSpellEngine.suggest(word, maxSuggestions = 1)
        val top = suggestions.firstOrNull()
        val isKnown = symSpellEngine.isKnownWord(word)

        if (debugLogging) {
            Log.d("PastieraIME", "onBoundaryKey: top=${top?.candidate}:${top?.distance} isKnown=$isKnown maxDist=${settings.maxAutoReplaceDistance}")
        }

        val shouldReplace = top != null && !isKnown && top.distance <= settings.maxAutoReplaceDistance

        if (shouldReplace && top != null) {
            val replacement = applyCasing(top.candidate, word)
            if (debugLogging) {
                Log.d("PastieraIME", "onBoundaryKey: replacing '$word' -> '$replacement'")
            }
            inputConnection.beginBatchEdit()
            inputConnection.deleteSurroundingText(word.length, 0)
            inputConnection.commitText(replacement, 1)
            tracker.reset()
            inputConnection.endBatchEdit()
            if (boundaryChar != null) {
                inputConnection.commitText(boundaryChar.toString(), 1)
            }

            // Save this correction so it can be undone with backspace
            lastCorrection = LastCorrection(word, replacement, boundaryChar)

            NotificationHelper.triggerHapticFeedback(appContext)
            suggestionsListener?.invoke(emptyList())
            return ReplaceResult(true, true)
        }

        // No replacement made, clear any pending correction
        lastCorrection = null

        tracker.onBoundaryReached(boundaryChar, inputConnection)
        suggestionsListener?.invoke(emptyList())
        return ReplaceResult(false, unicodeChar != 0)
    }

    private fun applyCasing(candidate: String, original: String): String {
        if (original.isEmpty()) return candidate
        return when {
            original.all { it.isUpperCase() } -> candidate.uppercase()
            original.first().isUpperCase() -> candidate.replaceFirstChar { it.uppercaseChar() }
            else -> candidate
        }
    }

    fun onCursorMoved(inputConnection: InputConnection?) {
        if (!isEnabled()) return
        ensureDictionaryLoaded()
        cursorRunnable?.let { cursorHandler.removeCallbacks(it) }
        if (inputConnection == null) {
            tracker.reset()
            suggestionsListener?.invoke(emptyList())
            return
        }
        cursorRunnable = Runnable {
            if (!symSpellEngine.isReady) {
                tracker.reset()
                suggestionsListener?.invoke(emptyList())
                return@Runnable
            }
            val word = extractWordAtCursor(inputConnection)
            if (!word.isNullOrBlank()) {
                tracker.setWord(word)
                updateSuggestions()
            } else {
                tracker.reset()
                suggestionsListener?.invoke(emptyList())
            }
        }
        cursorHandler.postDelayed(cursorRunnable!!, cursorDebounceMs)
    }

    fun onContextReset() {
        if (!isEnabled()) return
        tracker.onContextChanged()
        lastCorrection = null
        // Clear ignored words when switching contexts (new text field, etc.)
        if (debugLogging && ignoredWords.isNotEmpty()) {
            Log.d("PastieraIME", "onContextReset: clearing ignored words: $ignoredWords")
        }
        ignoredWords.clear()
        suggestionsListener?.invoke(emptyList())
    }

    fun onNavModeToggle() {
        if (!isEnabled()) return
        tracker.onContextChanged()
    }

    fun currentSuggestions(): List<SuggestionResult> = latestSuggestions.get()

    private fun extractWordAtCursor(inputConnection: InputConnection?): String? {
        if (inputConnection == null) return null
        return try {
            val before = inputConnection.getTextBeforeCursor(12, 0)?.toString() ?: ""
            val after = inputConnection.getTextAfterCursor(12, 0)?.toString() ?: ""
            val boundary = " \t\n\r.,;:!?()[]{}\"'"
            var start = before.length
            while (start > 0 && !boundary.contains(before[start - 1])) {
                start--
            }
            var end = 0
            while (end < after.length && !boundary.contains(after[end])) {
                end++
            }
            val word = before.substring(start) + after.substring(0, end)
            if (word.isBlank()) null else word
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Preloads the dictionary in background.
     * Should be called during initialization to have dictionary ready when user focuses a field.
     */
    fun preloadDictionary() {
        if (!symSpellEngine.isReady) {
            loadScope.launch {
                symSpellEngine.loadDictionary()
            }
        }
    }

    private fun ensureDictionaryLoaded() {
        if (!symSpellEngine.isReady) {
            loadScope.launch {
                symSpellEngine.loadDictionary()
            }
        }
    }
}
