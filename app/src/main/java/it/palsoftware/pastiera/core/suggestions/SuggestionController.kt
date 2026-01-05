package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.util.Log
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CancellationException
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import it.palsoftware.pastiera.inputmethod.NotificationHelper

class SuggestionController(
    context: Context,
    private val assets: AssetManager,
    private val settingsProvider: () -> SuggestionSettings,
    private val isEnabled: () -> Boolean = { true },
    debugLogging: Boolean = false,
    private val onSuggestionsUpdated: (List<SuggestionResult>) -> Unit,
    private var currentLocale: Locale = Locale.ITALIAN,
    private val keyboardLayoutProvider: () -> String = { "qwerty" }
) {

    private val appContext = context.applicationContext
    private val debugLogging: Boolean = debugLogging
    private val userDictionaryStore = UserDictionaryStore()
    private var dictionaryRepository = DictionaryRepository(appContext, assets, userDictionaryStore, baseLocale = currentLocale, debugLogging = debugLogging)
    private var suggestionEngine = SuggestionEngine(dictionaryRepository, locale = currentLocale, debugLogging = debugLogging).apply {
        setKeyboardLayout(keyboardLayoutProvider())
    }
    private var tracker = CurrentWordTracker(
        onWordChanged = { word ->
            val settings = settingsProvider()
            if (settings.suggestionsEnabled) {
                if (debugLogging) {
                    Log.d("PastieraIME", "trackerWordChanged='$word' len=${word.length}")
                }
                currentSuggestionJob?.cancel()
                currentSuggestionJob = suggestionScope.launch {
                    val next = suggestionEngine.suggest(word, settings.maxSuggestions, settings.accentMatching, settings.useKeyboardProximity, settings.useEditTypeRanking)
                    latestSuggestions.set(next)
                    withContext(Dispatchers.Main) {
                        suggestionsListener?.invoke(next)
                    }
                }
            }
        },
        onWordReset = {
            val settings = settingsProvider()
            if (settings.suggestionsEnabled) {
                currentSuggestionJob?.cancel()
                currentSuggestionJob = suggestionScope.launch {
                    val next = suggestionEngine.suggest("", settings.maxSuggestions, settings.accentMatching, settings.useKeyboardProximity, settings.useEditTypeRanking, contextHistory)
                    latestSuggestions.set(next)
                    withContext(Dispatchers.Main) {
                        suggestionsListener?.invoke(next)
                    }
                }
            } else {
                latestSuggestions.set(emptyList())
                suggestionsListener?.invoke(emptyList())
            }
        }
    )
    private var autoReplaceController = AutoReplaceController(dictionaryRepository, suggestionEngine, settingsProvider)
    
    private fun addToContextHistory(word: String) {
        if (word.isBlank()) return
        contextHistory.add(word)
        if (contextHistory.size > 3) {
            contextHistory.removeAt(0)
        }
    }

    private fun learnFromContext(committed: String) {
        if (committed.isBlank()) return
        
        // If it's a word, normalize it. If it's punctuation, keep it.
        val isWord = committed.any { it.isLetterOrDigit() }
        
        // Learn N-Grams (Bigrams, Trigrams, etc.)
        for (len in 1..contextHistory.size) {
            val context = contextHistory.takeLast(len)
            dictionaryRepository.addNGram(context, committed)
        }
        
        // Only add actual words to the unigram dictionary
        if (isWord) {
            dictionaryRepository.addUserEntryQuick(committed)
        }
        
        addToContextHistory(committed)
    }

    /**
     * Updates the locale and reloads the dictionary for the new language.
     */
    fun updateLocale(newLocale: Locale) {
        if (newLocale == currentLocale) return
        
        // Cancel previous load job if still running to prevent conflicts
        currentLoadJob?.cancel()
        currentLoadJob = null
        
        currentLocale = newLocale
        dictionaryRepository = DictionaryRepository(appContext, assets, userDictionaryStore, baseLocale = currentLocale, debugLogging = debugLogging)
        suggestionEngine = SuggestionEngine(dictionaryRepository, locale = currentLocale, debugLogging = debugLogging).apply {
            setKeyboardLayout(keyboardLayoutProvider())
        }
        autoReplaceController = AutoReplaceController(dictionaryRepository, suggestionEngine, settingsProvider)
        
        // Recreate tracker to use new engine (tracker captures suggestionEngine in closure)
        tracker = CurrentWordTracker(
            onWordChanged = { word ->
                val settings = settingsProvider()
                if (settings.suggestionsEnabled) {
                    currentSuggestionJob?.cancel()
                    currentSuggestionJob = suggestionScope.launch {
                        val next = suggestionEngine.suggest(word, settings.maxSuggestions, settings.accentMatching, settings.useKeyboardProximity, settings.useEditTypeRanking)
                        latestSuggestions.set(next)
                        withContext(Dispatchers.Main) {
                            suggestionsListener?.invoke(next)
                        }
                    }
                }
            },
            onWordReset = {
                val settings = settingsProvider()
                if (settings.suggestionsEnabled) {
                    currentSuggestionJob?.cancel()
                    currentSuggestionJob = suggestionScope.launch {
                        val next = suggestionEngine.suggest("", settings.maxSuggestions, settings.accentMatching, settings.useKeyboardProximity, settings.useEditTypeRanking, contextHistory)
                        latestSuggestions.set(next)
                        withContext(Dispatchers.Main) {
                            suggestionsListener?.invoke(next)
                        }
                    }
                } else {
                    latestSuggestions.set(emptyList())
                    suggestionsListener?.invoke(emptyList())
                }
            }
        )
        
        // Reload dictionary in background
        currentLoadJob = loadScope.launch {
            dictionaryRepository.loadIfNeeded()
        }
        
        // Reset tracker and clear suggestions
        tracker.reset()
        suggestionsListener?.invoke(emptyList())
    }

    /**
     * Updates the keyboard layout for proximity-based ranking.
     */
    fun updateKeyboardLayout(layout: String) {
        suggestionEngine.setKeyboardLayout(layout)
    }

    private val latestSuggestions: AtomicReference<List<SuggestionResult>> = AtomicReference(emptyList())
    // Dedicated IO scope so dictionary preload never blocks the main thread.
    private val loadScope = CoroutineScope(Dispatchers.IO)
    private val suggestionScope = CoroutineScope(Dispatchers.Default)
    private var currentLoadJob: Job? = null
    private var currentSuggestionJob: Job? = null
    private val cursorHandler = Handler(Looper.getMainLooper())
    private var cursorRunnable: Runnable? = null
    private val cursorDebounceMs = 120L
    private var pendingAddUserWord: String? = null
    private val contextHistory = mutableListOf<String>()
    
    var suggestionsListener: ((List<SuggestionResult>) -> Unit)? = onSuggestionsUpdated

    fun onCharacterCommitted(text: CharSequence, inputConnection: InputConnection?) {
        if (!isEnabled()) return
        if (debugLogging) {
            val caller = Throwable().stackTrace.getOrNull(1)?.let { "${it.className}#${it.methodName}:${it.lineNumber}" }
            Log.d("PastieraIME", "SuggestionController.onCharacterCommitted('$text') caller=$caller")
        }
        ensureDictionaryLoaded()

        // Normalize curly/variant apostrophes to straight for tracking and suggestions.
        val normalizedText = text
            .toString()
            .replace("’", "'")
            .replace("‘", "'")
            .replace("ʼ", "'")
        
        // Clear last replacement if user types new characters
        autoReplaceController.clearLastReplacement()
        
        // Clear rejected words when user types a new letter (allows re-correction)
        if (normalizedText.isNotEmpty() && normalizedText.any { it.isLetterOrDigit() }) {
            autoReplaceController.clearRejectedWords()
            pendingAddUserWord = null
        }
        
        tracker.onCharacterCommitted(normalizedText)
    }

    fun refreshFromInputConnection(inputConnection: InputConnection?) {
        if (!isEnabled()) return
        tracker.onBackspace()
    }

    fun onBoundaryKey(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?
    ): AutoReplaceController.ReplaceResult {
        if (debugLogging) {
            Log.d(
                "PastieraIME",
                "SuggestionController.onBoundaryKey keyCode=$keyCode char=${event?.unicodeChar}"
            )
        }
        ensureDictionaryLoaded()

        // Removed the synchronous sync with InputConnection here to avoid cursor lag.
        // The tracker is already updated via onCharacterCommitted and onCursorMoved (debounced).

        val result = autoReplaceController.handleBoundary(
            keyCode,
            event,
            tracker,
            inputConnection,
            contextHistory,
            latestSuggestions.get()
        )
        
        // Move all learning and next-word prediction to background to keep cursor movement instant
        suggestionScope.launch {
            // Learn NGrams and update contextHistory
            result.committedWord?.let { committed ->
                learnFromContext(committed)
            }
            
            // Also learn from the boundary character itself if it's relevant punctuation (e.g. comma)
            val boundaryChar = event?.unicodeChar?.toChar() ?: when(keyCode) {
                KeyEvent.KEYCODE_COMMA -> ','
                KeyEvent.KEYCODE_PERIOD -> '.'
                else -> null
            }
            
            if (boundaryChar != null && (boundaryChar == ',' || boundaryChar == '.' || boundaryChar == '!' || boundaryChar == '?')) {
                learnFromContext(boundaryChar.toString())
            }

            // Predict next words after a boundary key (e.g. space)
            val settings = settingsProvider()
            if (settings.suggestionsEnabled) {
                val next = suggestionEngine.suggest("", settings.maxSuggestions, settings.accentMatching, settings.useKeyboardProximity, settings.useEditTypeRanking, contextHistory)
                latestSuggestions.set(next)
                withContext(Dispatchers.Main) {
                    suggestionsListener?.invoke(next)
                }
            } else {
                withContext(Dispatchers.Main) {
                    latestSuggestions.set(emptyList())
                    suggestionsListener?.invoke(emptyList())
                }
            }
        }
        
        return result
    }

    /**
     * Reads the word at cursor immediately without debounce.
     * Use this when entering a text field to show suggestions right away.
     * If dictionary is not ready yet, does nothing - normal typing/cursor flow will handle it.
     */
    fun readInitialContext(inputConnection: InputConnection?) {
        if (!isEnabled()) return
        if (inputConnection == null || !dictionaryRepository.isReady) return
        
        // Try to recover context history from the text before the cursor
        rebuildContextHistory(inputConnection)
        
        val word = extractWordAtCursor(inputConnection)
        if (!word.isNullOrBlank()) {
            tracker.setWord(word)
        } else {
            // Even if word is blank, trigger suggestions (next-word prediction)
            val settings = settingsProvider()
            if (settings.suggestionsEnabled) {
                currentSuggestionJob?.cancel()
                currentSuggestionJob = suggestionScope.launch {
                    val next = suggestionEngine.suggest("", settings.maxSuggestions, settings.accentMatching, settings.useKeyboardProximity, settings.useEditTypeRanking, contextHistory)
                    latestSuggestions.set(next)
                    withContext(Dispatchers.Main) {
                        suggestionsListener?.invoke(next)
                    }
                }
            }
        }
    }

    private fun rebuildContextHistory(inputConnection: InputConnection) {
        try {
            // Keep it small and fast (32 chars is enough for ~3-4 words)
            val textBefore = inputConnection.getTextBeforeCursor(32, 0)?.toString() ?: ""
            if (textBefore.isEmpty()) {
                contextHistory.clear()
                return
            }
            
            // Simple tokenization: split by whitespace and keep relevant punctuation
            val tokens = mutableListOf<String>()
            val parts = textBefore.trim().split("\\s+".toRegex())
            
            parts.forEach { part ->
                if (part.isEmpty()) return@forEach
                
                val currentWord = StringBuilder()
                part.forEach { char ->
                    if (char.isLetterOrDigit() || char == '\'') {
                        currentWord.append(char)
                    } else {
                        if (currentWord.isNotEmpty()) {
                            tokens.add(currentWord.toString())
                            currentWord.setLength(0)
                        }
                        if (char == ',' || char == '.' || char == '!' || char == '?') {
                            tokens.add(char.toString())
                        }
                    }
                }
                if (currentWord.isNotEmpty()) {
                    tokens.add(currentWord.toString())
                }
            }
            
            contextHistory.clear()
            contextHistory.addAll(tokens.takeLast(3))
        } catch (e: Exception) {
            contextHistory.clear()
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
            // Try to recover context history from the text before the cursor
            rebuildContextHistory(inputConnection)
            
            if (!dictionaryRepository.isReady) {
                tracker.reset()
                suggestionsListener?.invoke(emptyList())
                return@Runnable
            }
            val word = extractWordAtCursor(inputConnection)
            if (!word.isNullOrBlank()) {
                tracker.setWord(word)
            } else {
                tracker.reset()
                // Trigger next-word prediction even on empty word (e.g. after space or manual move to whitespace)
                val settings = settingsProvider()
                if (settings.suggestionsEnabled) {
                    currentSuggestionJob?.cancel()
                    currentSuggestionJob = suggestionScope.launch {
                        val next = suggestionEngine.suggest("", settings.maxSuggestions, settings.accentMatching, settings.useKeyboardProximity, settings.useEditTypeRanking, contextHistory)
                        latestSuggestions.set(next)
                        withContext(Dispatchers.Main) {
                            suggestionsListener?.invoke(next)
                        }
                    }
                } else {
                    suggestionsListener?.invoke(emptyList())
                }
            }
        }
        cursorHandler.postDelayed(cursorRunnable!!, cursorDebounceMs)
    }

    fun onContextReset() {
        if (!isEnabled()) return
        tracker.onContextChanged()
        contextHistory.clear()
        pendingAddUserWord = null
        suggestionsListener?.invoke(emptyList())
    }

    fun onNavModeToggle() {
        if (!isEnabled()) return
        tracker.onContextChanged()
        contextHistory.clear()
    }

    fun addUserWord(word: String) {
        if (!isEnabled()) return
        dictionaryRepository.addUserEntryQuick(word)
    }

    fun onSuggestionSelected(suggestion: String) {
        if (!isEnabled()) return
        suggestionScope.launch {
            learnFromContext(suggestion)
            withContext(Dispatchers.Main) {
                tracker.reset()
            }
        }
    }

    fun removeUserWord(word: String) {
        if (!isEnabled()) return
        dictionaryRepository.removeUserEntry(word)
        refreshUserDictionary()
    }

    fun markUsed(word: String) {
        if (!isEnabled()) return
        dictionaryRepository.markUsed(word)
    }

    fun currentSuggestions(): List<SuggestionResult> = latestSuggestions.get()

    fun userDictionarySnapshot(): List<UserDictionaryStore.UserEntry> = userDictionaryStore.getSnapshot()

    /**
     * Forces a refresh of user dictionary entries.
     * Should be called when words are added/removed from settings.
     */
    fun refreshUserDictionary() {
        if (!isEnabled()) return
        loadScope.launch {
            try {
                dictionaryRepository.refreshUserEntries()
            } catch (_: CancellationException) {
                // Cancelled due to rapid switches; safe to ignore.
            } catch (e: Exception) {
                Log.e("PastieraIME", "Failed to refresh user dictionary", e)
            }
        }
    }

    fun handleBackspaceUndo(keyCode: Int, inputConnection: InputConnection?): Boolean {
        if (!isEnabled()) return false
        val undone = autoReplaceController.handleBackspaceUndo(keyCode, inputConnection)
        if (undone) {
            pendingAddUserWord = autoReplaceController.consumeLastUndoOriginalWord()
        }
        return undone
    }

    fun pendingAddWord(): String? = pendingAddUserWord
    fun clearPendingAddWord() {
        pendingAddUserWord = null
    }

    /**
     * Clears the pending add-word candidate if the cursor is no longer on that word.
     * Keeps the candidate only while the cursor remains on the originating token.
     */
    fun clearPendingAddWordIfCursorOutside(inputConnection: InputConnection?) {
        val pending = pendingAddUserWord ?: return
        val currentWord = extractWordAtCursor(inputConnection)
        if (currentWord == null || !currentWord.equals(pending, ignoreCase = true)) {
            pendingAddUserWord = null
        }
    }

    private fun extractWordAtCursor(inputConnection: InputConnection?): String? {
        if (inputConnection == null) return null
        return try {
            val before = inputConnection.getTextBeforeCursor(12, 0)?.toString() ?: ""
            val after = inputConnection.getTextAfterCursor(12, 0)?.toString() ?: ""
            val boundary = " \t\n\r" + it.palsoftware.pastiera.core.Punctuation.BOUNDARY
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
        if (!dictionaryRepository.isReady && !dictionaryRepository.isLoadStarted) {
            loadScope.launch {
                dictionaryRepository.loadIfNeeded()
            }
        }
    }

    private fun ensureDictionaryLoaded() {
        if (!dictionaryRepository.isReady) {
            dictionaryRepository.ensureLoadScheduled {
                loadScope.launch {
                    dictionaryRepository.loadIfNeeded()
                }
            }
        }
    }
}
