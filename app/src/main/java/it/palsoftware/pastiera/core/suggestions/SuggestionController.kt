package com.pastiera.ime.suggestion

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class SuggestionController(
    private val baseDir: File,
    private var currentLocale: Locale,
    private val debugLogging: Boolean = false,
    private val keyboardLayoutProvider: () -> List<String> = { emptyList() },
    private val activeSuggestionLocalesProvider: (() -> List<Locale>)? = null
) {
    private var dictionaryRepository: DictionaryRepository = createDictionaryRepository(currentLocale)
    private var primaryEngine: SuggestionEngine = SuggestionEngine(
        dictionaryRepository,
        locale = currentLocale,
        debugLogging = debugLogging
    ).apply {
        setKeyboardLayout(keyboardLayoutProvider())
    }

    private val extraSuggestionEngines = mutableMapOf<String, SuggestionLanguageEngine>()
    private val tracker = WordTracker()
    private val loadScope = CoroutineScope(Dispatchers.IO)
    private val cursorHandler = Handler(Looper.getMainLooper())

    private var currentLoadJob: Job? = null
    private var pendingInitialContextConnection: InputConnection? = null
    private var pendingPrimaryRefreshAfterLoad = false
    private var pendingExtraRefreshAfterLoad = false

    private var previousCompletedWord: String? = null
    private var listener: SuggestionListener? = null

    interface SuggestionListener {
        fun onSuggestionsUpdated(suggestions: List<Suggestion>)
    }

    private data class SuggestionLanguageEngine(
        val locale: Locale,
        val repository: DictionaryRepository,
        val engine: SuggestionEngine
    )

    fun setListener(listener: SuggestionListener?) {
        this.listener = listener
    }

    fun setLocale(locale: Locale) {
        if (currentLocale == locale) return
        currentLocale = locale
        currentLoadJob?.cancel()
        dictionaryRepository = createDictionaryRepository(locale)
        primaryEngine = SuggestionEngine(
            dictionaryRepository,
            locale = locale,
            debugLogging = debugLogging
        ).apply {
            setKeyboardLayout(keyboardLayoutProvider())
        }
        extraSuggestionEngines.clear()
        ensureDictionaryLoaded(refreshAfterLoad = true)
    }

    fun onStartInput(inputConnection: InputConnection?) {
        tracker.reset()
        previousCompletedWord = null
        if (inputConnection != null) {
            if (dictionaryRepository.isReady) {
                readInitialContext(inputConnection)
            } else {
                pendingInitialContextConnection = inputConnection
                ensureDictionaryLoaded(refreshAfterLoad = true)
            }
        }
    }

    fun onKey(keyCode: Int, event: KeyEvent?, inputConnection: InputConnection?) {
        val boundaryChar = boundaryCharFor(keyCode, event)
        if (boundaryChar != null) {
            val completed = tracker.currentWord
            if (completed.isNotBlank()) {
                previousCompletedWord = completed
            }
            tracker.reset()
            if (inputConnection != null) {
                publishNextWordPredictions(previousCompletedWord)
            }
            return
        }

        if (keyCode == KeyEvent.KEYCODE_DEL) {
            if (tracker.currentWord.isNotEmpty()) {
                tracker.deleteLastChar()
                updateSuggestionsForWord(tracker.currentWord)
            } else if (inputConnection != null) {
                val extracted = extractWordAtCursor(inputConnection, includeAfterCursor = false)
                if (!extracted.isNullOrEmpty()) {
                    tracker.setWord(extracted)
                    updateSuggestionsForWord(tracker.currentWord)
                } else {
                    publishSentenceStartPredictionsOrStarter()
                }
            }
            return
        }

        if (event != null && event.unicodeChar > 0) {
            val ch = event.unicodeChar.toChar()
            if (isWordChar(ch)) {
                tracker.appendChar(ch)
                updateSuggestionsForWord(tracker.currentWord)
            }
        }
    }

    fun onCursorMoved(inputConnection: InputConnection) {
        ensureDictionaryLoaded()
        val wordAtCursor = extractWordAtCursor(inputConnection, includeAfterCursor = true)
        if (!wordAtCursor.isNullOrEmpty()) {
            tracker.setWord(wordAtCursor)
            updateSuggestionsForWord(wordAtCursor)
        } else {
            tracker.reset()
            val charBefore = lastCharBeforeCursor(inputConnection)
            if (isSoftPredictionBoundary(charBefore)) {
                val previous = extractWordAtCursor(inputConnection, includeAfterCursor = false)
                previousCompletedWord = previous
                publishNextWordPredictions(previous)
            } else {
                previousCompletedWord = null
                publishSentenceStartPredictionsOrStarter()
            }
        }
    }

    fun acceptSuggestion(suggestion: Suggestion, inputConnection: InputConnection) {
        val current = tracker.currentWord
        val textToInsert = suggestion.word + " "
        
        inputConnection.beginBatchEdit()
        if (current.isNotEmpty()) {
            inputConnection.deleteSurroundingText(current.length, 0)
        }
        inputConnection.commitText(textToInsert, 1)
        inputConnection.endBatchEdit()

        previousCompletedWord = suggestion.word
        tracker.reset()
        publishNextWordPredictions(suggestion.word)
    }

    private fun readInitialContext(inputConnection: InputConnection) {
        val extracted = extractWordAtCursor(inputConnection, includeAfterCursor = true)
        if (!extracted.isNullOrEmpty()) {
            tracker.setWord(extracted)
            updateSuggestionsForWord(extracted)
        } else {
            val charBefore = lastCharBeforeCursor(inputConnection)
            if (isSoftPredictionBoundary(charBefore)) {
                val previous = extractWordAtCursor(inputConnection, includeAfterCursor = false)
                previousCompletedWord = previous
                publishNextWordPredictions(previous)
            } else {
                publishSentenceStartPredictionsOrStarter()
            }
        }
    }

    private fun updateSuggestionsForWord(word: String) {
        if (word.isBlank()) {
            publishSentenceStartPredictionsOrStarter()
            return
        }

        val primaryResults = primaryEngine.getSuggestions(word).map { 
            it.copy(score = it.score + PRIMARY_SUGGESTION_BOOST) 
        }

        val extraEngines = activeExtraSuggestionEngines()
        val extraResults = extraEngines.flatMap { engineWrapper ->
            if (!engineWrapper.repository.isReady) {
                scheduleRepositoryLoad(engineWrapper.repository, refreshAfterLoad = true)
                emptyList()
            } else {
                engineWrapper.engine.getSuggestions(word)
            }
        }

        val merged = (primaryResults + extraResults)
            .groupBy { it.word.lowercase(currentLocale) }
            .map { (_, group) -> group.maxByOrNull { it.score }!! }
            .sortedByDescending { it.score }
            .take(3)

        listener?.onSuggestionsUpdated(merged)
    }

    private fun publishNextWordPredictions(previousWord: String?) {
        if (previousWord.isNullOrBlank()) {
            publishSentenceStartPredictionsOrStarter()
            return
        }

        val primaryPredictions = primaryEngine.getNextWordPredictions(previousWord, CONTEXT_FOLLOWER_LOOKUP_LIMIT)
            .map { it.copy(score = it.score + CONTEXT_BOOST_MAX) }

        val extraEngines = activeExtraSuggestionEngines()
        val extraPredictions = extraEngines.flatMap { engineWrapper ->
            if (!engineWrapper.repository.isReady) {
                scheduleRepositoryLoad(engineWrapper.repository, refreshAfterLoad = true)
                emptyList()
            } else {
                engineWrapper.engine.getNextWordPredictions(previousWord, CONTEXT_FOLLOWER_LOOKUP_LIMIT)
            }
        }

        val merged = (primaryPredictions + extraPredictions)
            .groupBy { it.word.lowercase(currentLocale) }
            .map { (_, group) -> group.maxByOrNull { it.score }!! }
            .sortedByDescending { it.score }
            .take(3)

        if (merged.isNotEmpty()) {
            listener?.onSuggestionsUpdated(merged)
        } else {
            publishSentenceStartPredictionsOrStarter()
        }
    }

    private fun publishSentenceStartPredictionsOrStarter() {
        val predictions = primaryEngine.getSentenceStartPredictions(3)
        listener?.onSuggestionsUpdated(predictions)
    }

    private fun createDictionaryRepository(locale: Locale): DictionaryRepository {
        return DictionaryRepository(baseDir, locale)
    }

    private fun dictionaryCacheKey(locale: Locale): String = locale.toLanguageTag()

    private fun activeExtraLocales(): List<Locale> {
        val primaryLanguage = currentLocale.language.lowercase(Locale.ROOT)
        return activeSuggestionLocalesProvider?.invoke().orEmpty()
            .filter { it.language.isNotBlank() }
            .filter { it.language.lowercase(Locale.ROOT) != primaryLanguage }
    }

    private fun activeExtraSuggestionEngines(): List<SuggestionLanguageEngine> {
        val extraLocales = activeExtraLocales()
        if (extraLocales.isEmpty()) return emptyList()

        return extraLocales.map { locale ->
            val cacheKey = dictionaryCacheKey(locale)
            extraSuggestionEngines.getOrPut(cacheKey) {
                val repository = createDictionaryRepository(locale)
                val engine = SuggestionEngine(repository, locale = locale, debugLogging = debugLogging).apply {
                    setKeyboardLayout(keyboardLayoutProvider())
                }
                SuggestionLanguageEngine(locale, repository, engine)
            }
        }
    }

    private fun ensureDictionaryLoaded(refreshAfterLoad: Boolean = false) {
        if (dictionaryRepository.isReady) {
            if (pendingInitialContextConnection != null) {
                val connection = pendingInitialContextConnection
                pendingInitialContextConnection = null
                readInitialContext(connection)
            }
            return
        }
        if (refreshAfterLoad) {
            pendingPrimaryRefreshAfterLoad = true
        }
        schedulePrimaryDictionaryLoad(refreshAfterLoad = refreshAfterLoad)
    }

    private fun schedulePrimaryDictionaryLoad(refreshAfterLoad: Boolean) {
        if (currentLoadJob?.isActive == true) return
        currentLoadJob = loadScope.launch {
            try {
                dictionaryRepository.ensureLoaded()
                cursorHandler.post {
                    if (pendingInitialContextConnection != null) {
                        val connection = pendingInitialContextConnection
                        pendingInitialContextConnection = null
                        readInitialContext(connection)
                    } else if (pendingPrimaryRefreshAfterLoad) {
                        pendingPrimaryRefreshAfterLoad = false
                        refreshCurrentSuggestions()
                    }
                }
            } catch (_: CancellationException) {
                // Load job cancelled due to locale change
            } catch (e: Exception) {
                Log.e("PastieraIME", "Failed to load primary dictionary", e)
            }
        }
    }

    private fun scheduleRepositoryLoad(repository: DictionaryRepository, refreshAfterLoad: Boolean) {
        if (repository.isReady) return
        if (refreshAfterLoad) {
            pendingExtraRefreshAfterLoad = true
        }
        loadScope.launch {
            try {
                repository.ensureLoaded()
                if (pendingExtraRefreshAfterLoad) {
                    pendingExtraRefreshAfterLoad = false
                    cursorHandler.post { refreshCurrentSuggestions() }
                }
            } catch (_: CancellationException) {
                // Load job cancelled
            } catch (e: Exception) {
                Log.e("PastieraIME", "Failed to load extra dictionary", e)
            }
        }
    }

    private fun refreshCurrentSuggestions() {
        val word = tracker.currentWord
        if (word.isNotBlank()) {
            updateSuggestionsForWord(word)
        } else if (previousCompletedWord != null) {
            publishNextWordPredictions(previousCompletedWord)
        } else {
            publishSentenceStartPredictionsOrStarter()
        }
    }

    private fun extractWordAtCursor(
        inputConnection: InputConnection,
        includeAfterCursor: Boolean = true
    ): String? {
        val before = inputConnection.getTextBeforeCursor(MAX_CURSOR_WORD_LOOKBACK, 0)?.toString() ?: ""
        val after = if (includeAfterCursor) {
            inputConnection.getTextAfterCursor(MAX_CURSOR_WORD_LOOKAHEAD, 0)?.toString() ?: ""
        } else ""

        val wordBefore = before.takeLastWhile { isWordChar(it) }
        val wordAfter = after.takeWhile { isWordChar(it) }
        val fullWord = wordBefore + wordAfter

        return fullWord.takeIf { it.isNotBlank() }
    }

    private fun lastCharBeforeCursor(inputConnection: InputConnection): Char? {
        val text = inputConnection.getTextBeforeCursor(1, 0)
        return text?.firstOrNull()
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '\''

    private fun isSoftPredictionBoundary(ch: Char?): Boolean {
        if (ch == null) return false
        return ch == ' ' || ch == '\n' || ch == '\t' || ch == ','
    }

    private fun boundaryCharFor(keyCode: Int, event: KeyEvent?): Char? {
        if (event != null && event.unicodeChar > 0) {
            val char = event.unicodeChar.toChar()
            if (!char.isWhitespace() && KeyCharacterMap.deviceHasKey(keyCode)) {
                return char
            }
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> ' '
            KeyEvent.KEYCODE_ENTER -> '\n'
            KeyEvent.KEYCODE_COMMA -> ','
            KeyEvent.KEYCODE_PERIOD -> '.'
            else -> null
        }
    }

    companion object {
        private const val MAX_CURSOR_WORD_LOOKBACK = 64
        private const val MAX_CURSOR_WORD_LOOKAHEAD = 32
        private const val PRIMARY_SUGGESTION_BOOST = 12.0
        private const val CONTEXT_FOLLOWER_LOOKUP_LIMIT = 20
        private const val CONTEXT_BOOST_MAX = 8.0
    }
}
