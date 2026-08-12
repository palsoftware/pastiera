package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import android.view.KeyCharacterMap
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
import it.palsoftware.pastiera.inputmethod.AutoCorrector
import it.palsoftware.pastiera.inputmethod.NotificationHelper

class SuggestionController(
    context: Context,
    private val assets: AssetManager,
    private val settingsProvider: () -> SuggestionSettings,
    private val isEnabled: () -> Boolean = { true },
    debugLogging: Boolean = false,
    private val onSuggestionsUpdated: (List<SuggestionResult>) -> Unit,
    private var currentLocale: Locale = Locale.ITALIAN,
    private val keyboardLayoutProvider: () -> String = { "qwerty" },
    private val dictionaryRepositoryFactory: ((
        Context,
        AssetManager,
        UserDictionaryStore,
        Locale,
        Boolean
    ) -> DictionaryRepository)? = null,
    nextWordPredictorOverride: NextWordPredictor? = null,
    private val activeSuggestionLocalesProvider: (() -> List<Locale>)? = null
) {

    private val appContext = context.applicationContext
    private val debugLogging: Boolean = debugLogging
    private val userDictionaryStore = UserDictionaryStore()
    private val dictionaryRepositoryCache = mutableMapOf<String, DictionaryRepository>()
    private var dictionaryRepository: DictionaryRepository = createDictionaryRepository(currentLocale)
    private var suggestionEngine = SuggestionEngine(dictionaryRepository, locale = currentLocale, debugLogging = debugLogging).apply {
        setKeyboardLayout(keyboardLayoutProvider())
    }
    private var tracker = CurrentWordTracker(
        onWordChanged = { word ->
            updateSuggestionsForWord(word)
        },
        onWordReset = {
            cancelPendingWordSuggestions()
            latestSuggestions.set(emptyList())
            pendingAddUserWord = null
            suggestionsListener?.invoke(emptyList())
        },
        autoSpacePunctuationProvider = { settingsProvider().autoSpacePunctuation }
    )
    private var autoReplaceController = createAutoReplaceController()
    private val nextWordPredictor = nextWordPredictorOverride ?: NextWordPredictor(UserNGramStore(appContext))
    private val extraSuggestionEngines = mutableMapOf<String, SuggestionLanguageEngine>()

    private data class SuggestionLanguageEngine(
        val locale: Locale,
        val repository: DictionaryRepository,
        val engine: SuggestionEngine
    )

    private fun createDictionaryRepository(locale: Locale): DictionaryRepository {
        val cacheKey = dictionaryCacheKey(locale)
        return dictionaryRepositoryCache.getOrPut(cacheKey) {
            dictionaryRepositoryFactory?.invoke(appContext, assets, userDictionaryStore, locale, debugLogging)
                ?: AndroidDictionaryRepository(
                appContext,
                assets,
                userDictionaryStore,
                baseLocale = locale,
                debugLogging = debugLogging
            )
        }
    }

    private fun dictionaryCacheKey(locale: Locale): String {
        return locale.language
            .takeIf { it.isNotBlank() }
            ?.lowercase(Locale.ROOT)
            ?: locale.toLanguageTag().lowercase(Locale.ROOT)
    }

    private fun createAutoReplaceController(): AutoReplaceController {
        return AutoReplaceController(
            repository = dictionaryRepository,
            suggestionEngine = suggestionEngine,
            settingsProvider = settingsProvider,
            knownWordProvider = { word -> isKnownWordInActiveDictionaries(word) },
            exactReplacementProvider = { word, boundaryChar ->
                val boundary = boundaryChar ?: ' '
                AutoCorrector.processText(
                    textBeforeCursor = word + boundary,
                    locale = currentLocale.language,
                    context = appContext,
                    isKnownWord = { candidate -> isKnownWordInActiveDictionaries(candidate) }
                )?.takeIf { (original, replacement) ->
                    original == word && replacement != word
                }?.second
            }
        )
    }
    
    /**
     * Updates the locale and reloads the dictionary for the new language.
     */
    fun updateLocale(newLocale: Locale) {
        if (newLocale == currentLocale) return
        
        // Cancel previous load job if still running to prevent conflicts
        currentLoadJob?.cancel()
        currentLoadJob = null
        cancelPendingWordSuggestions()
        pendingInitialContextConnection = null
        pendingPrimaryRefreshAfterLoad = false
        pendingExtraRefreshAfterLoad = false
        
        currentLocale = newLocale
        dictionaryRepository = createDictionaryRepository(currentLocale)
        suggestionEngine = SuggestionEngine(dictionaryRepository, locale = currentLocale, debugLogging = debugLogging).apply {
            setKeyboardLayout(keyboardLayoutProvider())
        }
        autoReplaceController = createAutoReplaceController()
        extraSuggestionEngines.clear()
        
        // Recreate tracker to use new engine (tracker captures suggestionEngine in closure)
        tracker = CurrentWordTracker(
            onWordChanged = { word ->
                updateSuggestionsForWord(word)
            },
            onWordReset = {
                cancelPendingWordSuggestions()
                latestSuggestions.set(emptyList())
                pendingAddUserWord = null
                suggestionsListener?.invoke(emptyList())
            },
            autoSpacePunctuationProvider = { settingsProvider().autoSpacePunctuation }
        )
        
        // Reload dictionary in background and refresh the current word when ready.
        schedulePrimaryDictionaryLoad(refreshAfterLoad = true)
        
        // Reset tracker and clear suggestions
        previousCompletedWord = null
        sentenceStartPending = true
        tracker.reset()
        suggestionsListener?.invoke(emptyList())
    }

    /**
     * Updates the keyboard layout for proximity-based ranking.
     */
    fun updateKeyboardLayout(layout: String) {
        suggestionEngine.setKeyboardLayout(layout)
        extraSuggestionEngines.values.forEach { it.engine.setKeyboardLayout(layout) }
    }

    private val latestSuggestions: AtomicReference<List<SuggestionResult>> = AtomicReference(emptyList())
    // Dedicated IO scope so dictionary preload never blocks the main thread.
    private val loadScope = CoroutineScope(Dispatchers.IO)
    private val suggestionScope = CoroutineScope(Dispatchers.Default)
    private var currentLoadJob: Job? = null
    private var suggestionJob: Job? = null
    private val cursorHandler = Handler(Looper.getMainLooper())
    private var cursorRunnable: Runnable? = null
    private val cursorDebounceMs = 120L
    private var pendingAddUserWord: String? = null
    private var previousCompletedWord: String? = null
    private var pendingInitialContextConnection: InputConnection? = null
    @Volatile private var pendingPrimaryRefreshAfterLoad: Boolean = false
    @Volatile private var pendingExtraRefreshAfterLoad: Boolean = false
    @Volatile private var suggestionGeneration: Int = 0
    private var sentenceStartPending: Boolean = true

    private fun cancelPendingWordSuggestions() {
        suggestionGeneration += 1
        suggestionJob?.cancel()
        suggestionJob = null
    }

    private fun updateSuggestionsForWord(word: String) {
        val settings = settingsProvider()
        if (!settings.suggestionsEnabled) {
            cancelPendingWordSuggestions()
            pendingAddUserWord = null
            latestSuggestions.set(emptyList())
            suggestionsListener?.invoke(emptyList())
            return
        }
        if (debugLogging) {
            Log.d("PastieraIME", "trackerWordChanged='$word' len=${word.length}")
        }

        val generation = suggestionGeneration + 1
        suggestionGeneration = generation
        suggestionJob?.cancel()

        val wordSnapshot = word
        val localeSnapshot = currentLocale
        val layoutSnapshot = keyboardLayoutProvider()
        val primaryRepository = dictionaryRepository
        val extraRepositories = activeExtraSuggestionEngines().mapNotNull { extra ->
            if (!extra.repository.isReady) {
                scheduleRepositoryLoad(extra.repository, refreshAfterLoad = true)
                null
            } else {
                extra.locale to extra.repository
            }
        }

        suggestionJob = suggestionScope.launch {
            val primary = if (primaryRepository.isReady) {
                SuggestionEngine(primaryRepository, locale = localeSnapshot, debugLogging = debugLogging).apply {
                    setKeyboardLayout(layoutSnapshot)
                }.suggest(
                    wordSnapshot,
                    settings.maxSuggestions,
                    settings.accentMatching,
                    settings.useKeyboardProximity,
                    settings.useEditTypeRanking
                )
            } else {
                emptyList()
            }

            val extraSuggestions = extraRepositories.flatMap { (locale, repository) ->
                SuggestionEngine(repository, locale = locale, debugLogging = debugLogging).apply {
                    setKeyboardLayout(layoutSnapshot)
                }.suggest(
                    wordSnapshot,
                    settings.maxSuggestions,
                    settings.accentMatching,
                    settings.useKeyboardProximity,
                    settings.useEditTypeRanking
                )
            }

            val next = mergeSuggestionResults(primary, extraSuggestions, settings.maxSuggestions, localeSnapshot)
            val pendingCandidate = addWordCandidateFor(wordSnapshot, primaryRepository)

            cursorHandler.post {
                if (generation != suggestionGeneration || tracker.currentWord != wordSnapshot) {
                    return@post
                }
                pendingAddUserWord = pendingCandidate
                latestSuggestions.set(next)
                suggestionsListener?.invoke(next)
            }
        }
    }

    private fun addWordCandidateFor(word: String?, repository: DictionaryRepository = dictionaryRepository): String? {
        val candidate = word?.trim() ?: return null
        if (candidate.isEmpty() || candidate.none { it.isLetterOrDigit() }) return null
        if (!repository.isReady) return null
        return if (repository.isKnownWord(candidate)) null else candidate
    }
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
            .replace("'", "'")
            .replace("'", "'")
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
        inputConnection: InputConnection?,
        boundaryCharOverride: Char? = null
    ): AutoReplaceController.ReplaceResult {
        if (debugLogging) {
            Log.d(
                "PastieraIME",
                "SuggestionController.onBoundaryKey keyCode=$keyCode char=${event?.unicodeChar}"
            )
        }
        ensureDictionaryLoaded()

        // CRITICAL FIX: Sync tracker with actual text before processing boundary
        // The cursor debounce can cause tracker to be out of sync with the actual text field
        if (inputConnection != null && dictionaryRepository.isReady) {
            val word = extractWordAtCursor(inputConnection, includeAfterCursor = false)
            if (!word.isNullOrBlank()) {
                tracker.setWord(word, notify = false)
                Log.d("PastieraIME", "SYNC: Synced tracker to actual word='$word' before boundary")
            }
        }

        val boundaryChar = boundaryCharOverride ?: boundaryCharFor(keyCode, event)
        val wordBeforeBoundary = tracker.currentWord.takeIf { it.isNotBlank() }
        val result = autoReplaceController.handleBoundary(
            keyCode,
            event,
            tracker,
            inputConnection,
            boundaryCharOverride = boundaryChar
        )
        val completedWord = result.replacement ?: wordBeforeBoundary
        if (result.replaced) {
            pendingAddUserWord = addWordCandidateFor(result.replacement)
            NotificationHelper.triggerHapticFeedback(appContext)
        } else {
            pendingAddUserWord = null
        }
        handleCompletedWordBoundary(completedWord, boundaryChar)
        return result
    }

    /**
     * Reads the word at cursor immediately without debounce.
     * Use this when entering a text field to show suggestions right away.
     * If dictionary is not ready yet, does nothing - normal typing/cursor flow will handle it.
     */
    fun readInitialContext(inputConnection: InputConnection?) {
        if (!isEnabled()) return
        if (inputConnection == null) return
        if (!dictionaryRepository.isReady) {
            pendingInitialContextConnection = inputConnection
            ensureDictionaryLoaded(refreshAfterLoad = true)
            return
        }
        
        val word = extractWordAtCursor(inputConnection)
        if (!word.isNullOrBlank()) {
            tracker.setWord(word)
        } else if (previousCompletedWord == null) {
            publishSentenceStartPredictionsOrStarter()
        }
    }

    fun onCursorMoved(inputConnection: InputConnection?) {
        if (!isEnabled()) return
        ensureDictionaryLoaded()
        cursorRunnable?.let { cursorHandler.removeCallbacks(it) }
        if (inputConnection == null) {
            tracker.reset()
            previousCompletedWord = null
            sentenceStartPending = true
            suggestionsListener?.invoke(emptyList())
            return
        }
        cursorRunnable = Runnable {
            if (!dictionaryRepository.isReady) {
                tracker.reset()
                previousCompletedWord = null
                suggestionsListener?.invoke(emptyList())
                return@Runnable
            }
            val word = extractWordAtCursor(inputConnection)
            if (!word.isNullOrBlank()) {
                tracker.setWord(word)
            } else {
                tracker.reset()
                val previous = previousCompletedWord
                val lastChar = lastCharBeforeCursor(inputConnection)
                if (previous != null && isSoftPredictionBoundary(lastChar)) {
                    publishNextWordPredictions(previous)
                } else {
                    previousCompletedWord = null
                    sentenceStartPending = true
                    publishSentenceStartPredictionsOrStarter()
                }
            }
        }
        cursorHandler.postDelayed(cursorRunnable!!, cursorDebounceMs)
    }

    fun onContextReset() {
        if (!isEnabled()) return
        tracker.onContextChanged()
        pendingAddUserWord = null
        previousCompletedWord = null
        sentenceStartPending = true
        suggestionsListener?.invoke(emptyList())
    }

    fun onNavModeToggle() {
        if (!isEnabled()) return
        tracker.onContextChanged()
        previousCompletedWord = null
        sentenceStartPending = true
    }

    fun addUserWord(word: String) {
        if (!isEnabled()) return
        dictionaryRepositoryCache.values
            .ifEmpty { listOf(dictionaryRepository) }
            .forEach { repository -> repository.addUserEntryQuick(word) }
    }

    fun removeUserWord(word: String) {
        if (!isEnabled()) return
        dictionaryRepositoryCache.values
            .ifEmpty { listOf(dictionaryRepository) }
            .forEach { repository -> repository.removeUserEntry(word) }
        refreshUserDictionary()
    }

    fun markUsed(word: String) {
        if (!isEnabled()) return
        dictionaryRepositoryCache.values
            .ifEmpty { listOf(dictionaryRepository) }
            .forEach { repository -> repository.markUsed(word) }
    }

    fun isKnownWordInActiveDictionaries(word: String): Boolean {
        if (!isEnabled()) return false
        val candidate = word.trim()
        if (candidate.isEmpty()) return false

        ensureDictionaryLoaded()
        if (dictionaryRepository.isReady && dictionaryRepository.isKnownWord(candidate)) {
            return true
        }

        return activeExtraSuggestionEngines().any { extra ->
            if (!extra.repository.isReady) {
                scheduleRepositoryLoad(extra.repository, refreshAfterLoad = false)
                // Defer legacy auto-substitution while an explicitly active
                // extra dictionary is still loading; wrong replacements are
                // worse than skipping one boundary.
                true
            } else {
                extra.repository.isKnownWord(candidate)
            }
        }
    }

    fun currentSuggestions(): List<SuggestionResult> = latestSuggestions.get()

    fun userDictionarySnapshot(): List<UserDictionaryStore.UserEntry> = userDictionaryStore.getSnapshot()

    fun dismissSuggestion(candidate: String, hardDeleteUserWord: Boolean = false) {
        if (!isEnabled()) return
        val trimmed = candidate.trim()
        if (trimmed.isEmpty()) return

        val current = latestSuggestions.get()
        val suggestion = current.firstOrNull { it.candidate.equals(trimmed, ignoreCase = true) }
        if (suggestion?.kind == SuggestionKind.NEXT_WORD) {
            forgetNextWordSuggestion(trimmed, hardDeleteEverywhere = hardDeleteUserWord)
        }
        if (hardDeleteUserWord) {
            removeUserWord(trimmed)
            activeExtraSuggestionEngines().forEach { extra ->
                if (extra.repository.isReady) {
                    extra.repository.removeUserEntry(trimmed)
                }
            }
        }

        val next = fillWithStarterSuggestions(
            current.filterNot { it.candidate.equals(trimmed, ignoreCase = true) },
            settingsProvider(),
            excludedCandidates = setOf(trimmed)
        )
        latestSuggestions.set(next)
        suggestionsListener?.invoke(next)
    }

    private fun forgetNextWordSuggestion(candidate: String, hardDeleteEverywhere: Boolean) {
        val locales = listOf(currentLocale) + activeExtraLocales()
        locales.forEach { locale ->
            if (hardDeleteEverywhere) {
                nextWordPredictor.forgetNextWordEverywhere(locale, candidate)
            } else {
                val previous = previousCompletedWord
                if (previous != null) {
                    nextWordPredictor.forget(locale, previous, candidate)
                } else {
                    nextWordPredictor.forgetSentenceStart(locale, candidate)
                }
            }
        }
    }

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

    internal fun clearLearnedNextWordsForTests() {
        nextWordPredictor.clearAll()
        previousCompletedWord = null
    }

    internal fun flushNextWordLearningForTests() {
        nextWordPredictor.flushLearningForTests()
    }

    fun destroy() {
        currentLoadJob?.cancel()
        suggestionJob?.cancel()
        cursorRunnable?.let { cursorHandler.removeCallbacks(it) }
        nextWordPredictor.destroy()
    }

    private fun handleCompletedWordBoundary(completedWord: String?, boundaryChar: Char?) {
        val settings = settingsProvider()
        if (!settings.suggestionsEnabled) {
            previousCompletedWord = null
            latestSuggestions.set(emptyList())
            suggestionsListener?.invoke(emptyList())
            return
        }

        val cleanWord = completedWord?.trim()?.takeIf { it.any { ch -> ch.isLetterOrDigit() } }
        if (cleanWord != null) {
            if (sentenceStartPending) {
                nextWordPredictor.learnSentenceStart(currentLocale, cleanWord)
            }
            previousCompletedWord?.let { previous ->
                nextWordPredictor.learn(currentLocale, previous, cleanWord)
            }
        }

        when {
            cleanWord != null && isSoftPredictionBoundary(boundaryChar) -> {
                previousCompletedWord = cleanWord
                sentenceStartPending = false
                publishNextWordPredictions(cleanWord)
            }
            cleanWord == null && isSoftPredictionBoundary(boundaryChar) -> {
                val previous = previousCompletedWord
                if (previous != null) {
                    publishNextWordPredictions(previous)
                } else {
                    latestSuggestions.set(emptyList())
                    suggestionsListener?.invoke(emptyList())
                }
            }
            else -> {
                previousCompletedWord = null
                sentenceStartPending = true
                latestSuggestions.set(emptyList())
                suggestionsListener?.invoke(emptyList())
            }
        }
    }

    private fun publishNextWordPredictions(previousWord: String) {
        val settings = settingsProvider()
        val primary = nextWordPredictor.predict(
            currentLocale,
            previousWord,
            settings.maxSuggestions
        )
        val extras = activeExtraLocales().flatMap { locale ->
            nextWordPredictor.predict(locale, previousWord, settings.maxSuggestions)
        }
        val predictions = mergeSuggestionResults(primary, extras, settings.maxSuggestions)
        val suggestions = fillWithStarterSuggestions(predictions, settings)
        if (suggestions.isNotEmpty()) {
            latestSuggestions.set(suggestions)
            suggestionsListener?.invoke(suggestions)
        } else {
            publishStarterSuggestions()
        }
    }

    private fun publishSentenceStartPredictionsOrStarter() {
        val settings = settingsProvider()
        val primary = nextWordPredictor.predictSentenceStart(currentLocale, settings.maxSuggestions)
        val extras = activeExtraLocales().flatMap { locale ->
            nextWordPredictor.predictSentenceStart(locale, settings.maxSuggestions)
        }
        val predictions = mergeSuggestionResults(primary, extras, settings.maxSuggestions)
        val suggestions = fillWithStarterSuggestions(predictions, settings)
        if (suggestions.isNotEmpty()) {
            latestSuggestions.set(suggestions)
            suggestionsListener?.invoke(suggestions)
        } else {
            publishStarterSuggestions()
        }
    }

    private fun publishStarterSuggestions() {
        val settings = settingsProvider()
        if (!settings.suggestionsEnabled || !dictionaryRepository.isReady) {
            latestSuggestions.set(emptyList())
            suggestionsListener?.invoke(emptyList())
            return
        }

        val suggestions = starterSuggestions(settings)
        latestSuggestions.set(suggestions)
        suggestionsListener?.invoke(suggestions)
    }

    private fun starterSuggestions(settings: SuggestionSettings): List<SuggestionResult> {
        val primary = starterSuggestionsFor(dictionaryRepository, PRIMARY_SUGGESTION_BOOST, settings.maxSuggestions)
        val extras = activeExtraSuggestionEngines().flatMap { extra ->
            if (!extra.repository.isReady) {
                scheduleRepositoryLoad(extra.repository, refreshAfterLoad = true)
                emptyList()
            } else {
                starterSuggestionsFor(extra.repository, 0.0, settings.maxSuggestions)
            }
        }
        return mergeSuggestionResults(primary, extras, settings.maxSuggestions)
    }

    private fun fillWithStarterSuggestions(
        predictions: List<SuggestionResult>,
        settings: SuggestionSettings
    ): List<SuggestionResult> {
        if (predictions.size >= settings.maxSuggestions) return predictions.take(settings.maxSuggestions)

        val seen = predictions
            .mapTo(HashSet()) { it.candidate.lowercase(currentLocale) }
        val fillers = starterSuggestions(settings)
            .filter { seen.add(it.candidate.lowercase(currentLocale)) }
        return (predictions + fillers).take(settings.maxSuggestions)
    }

    private fun fillWithStarterSuggestions(
        predictions: List<SuggestionResult>,
        settings: SuggestionSettings,
        excludedCandidates: Set<String>
    ): List<SuggestionResult> {
        if (predictions.size >= settings.maxSuggestions) return predictions.take(settings.maxSuggestions)

        val excluded = excludedCandidates.mapTo(HashSet()) { it.lowercase(currentLocale) }
        val seen = predictions
            .mapTo(HashSet()) { it.candidate.lowercase(currentLocale) }
        val fillers = starterSuggestions(settings)
            .filter { result ->
                val key = result.candidate.lowercase(currentLocale)
                key !in excluded && seen.add(key)
            }
        return (predictions + fillers).take(settings.maxSuggestions)
    }

    private fun starterSuggestionsFor(
        repository: DictionaryRepository,
        scoreBoost: Double,
        limit: Int
    ): List<SuggestionResult> {
        return repository.topCommonEntries(limit * 3)
            .map { entry ->
                SuggestionResult(
                    candidate = entry.word,
                    distance = 0,
                    score = repository.effectiveFrequency(entry) / 1_600.0 + scoreBoost +
                        if (entry.source == SuggestionSource.USER) 5.0 else 0.0,
                    source = entry.source,
                    kind = SuggestionKind.STARTER_WORD
                )
            }
            .take(limit)
    }

    private fun mergeSuggestionResults(
        primary: List<SuggestionResult>,
        extras: List<SuggestionResult>,
        limit: Int,
        locale: Locale = currentLocale
    ): List<SuggestionResult> {
        val seen = HashSet<String>()
        return (primary.map { it to PRIMARY_SUGGESTION_BOOST } + extras.map { it to 0.0 })
            .sortedWith(
                compareByDescending<Pair<SuggestionResult, Double>> { (result, boost) ->
                    result.score + boost
                }.thenBy { (result, _) -> result.candidate.length }
            )
            .map { it.first }
            .filter { result -> seen.add(result.candidate.lowercase(locale)) }
            .take(limit)
    }

    private fun activeExtraLocales(): List<Locale> {
        val primaryLanguage = currentLocale.language.lowercase(Locale.ROOT)
        return activeSuggestionLocalesProvider?.invoke().orEmpty()
            .filter { it.language.isNotBlank() }
            .filter { it.language.lowercase(Locale.ROOT) != primaryLanguage }
            .distinctBy { it.toLanguageTag().lowercase(Locale.ROOT) }
    }

    private fun activeExtraSuggestionEngines(): List<SuggestionLanguageEngine> {
        val activeLocales = activeExtraLocales()
        val activeTags = activeLocales.map { it.toLanguageTag() }.toSet()
        extraSuggestionEngines.keys
            .filterNot { it in activeTags }
            .forEach { extraSuggestionEngines.remove(it) }
        return activeLocales.map { locale ->
            val tag = locale.toLanguageTag()
            extraSuggestionEngines.getOrPut(tag) {
                val repository = createDictionaryRepository(locale)
                val engine = SuggestionEngine(repository, locale = locale, debugLogging = debugLogging).apply {
                    setKeyboardLayout(keyboardLayoutProvider())
                }
                SuggestionLanguageEngine(locale, repository, engine)
            }
        }
    }

    private fun scheduleRepositoryLoad(repository: DictionaryRepository, refreshAfterLoad: Boolean) {
        if (refreshAfterLoad) {
            pendingExtraRefreshAfterLoad = true
        }
        if (!repository.isReady && !repository.isLoadStarted) {
            loadScope.launch {
                try {
                    repository.loadIfNeeded()
                    val shouldRefresh = refreshAfterLoad || pendingExtraRefreshAfterLoad
                    if (shouldRefresh && repository.isReady) {
                        pendingExtraRefreshAfterLoad = false
                        cursorHandler.post {
                            val word = tracker.currentWord
                            if (word.isNotBlank()) {
                                updateSuggestionsForWord(word)
                            } else if (previousCompletedWord == null) {
                                publishSentenceStartPredictionsOrStarter()
                            }
                        }
                    }
                } catch (_: CancellationException) {
                    // Cancelled due to rapid switches; safe to ignore.
                } catch (e: Exception) {
                    Log.e("PastieraIME", "Failed to load extra dictionary", e)
                }
            }
        }
    }

    private fun isSoftPredictionBoundary(boundaryChar: Char?): Boolean {
        return boundaryChar == ' ' || boundaryChar == ',' || boundaryChar == ';' || boundaryChar == ':'
    }

    private fun boundaryCharFor(keyCode: Int, event: KeyEvent?): Char? {
        val unicodeChar = event?.unicodeChar ?: 0
        return when {
            unicodeChar != 0 -> unicodeChar.toChar()
            keyCode == KeyEvent.KEYCODE_SPACE -> ' '
            keyCode == KeyEvent.KEYCODE_ENTER -> '\n'
            keyCode == KeyEvent.KEYCODE_COMMA -> ','
            keyCode == KeyEvent.KEYCODE_SEMICOLON -> ';'
            keyCode == KeyEvent.KEYCODE_PERIOD -> '.'
            keyCode == KeyEvent.KEYCODE_SLASH -> '/'
            keyCode == KeyEvent.KEYCODE_LEFT_BRACKET -> '['
            keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET -> ']'
            keyCode == KeyEvent.KEYCODE_BACKSLASH -> '\\'
            else -> KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                .get(keyCode, 0)
                .takeIf { it != 0 }
                ?.toChar()
        }
    }

    private fun lastCharBeforeCursor(inputConnection: InputConnection?): Char? {
        return try {
            inputConnection?.getTextBeforeCursor(1, 0)?.lastOrNull()
        } catch (_: Exception) {
            null
        }
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

    private fun extractWordAtCursor(
        inputConnection: InputConnection?,
        includeAfterCursor: Boolean = true
    ): String? {
        if (inputConnection == null) return null
        return try {
            val before = inputConnection.getTextBeforeCursor(CURSOR_WORD_CONTEXT_CHARS, 0)?.toString() ?: ""
            val after = if (includeAfterCursor) {
                inputConnection.getTextAfterCursor(CURSOR_WORD_CONTEXT_CHARS, 0)?.toString() ?: ""
            } else {
                ""
            }
            var start = before.length
            while (start > 0) {
                val ch = before[start - 1]
                val prev = before.getOrNull(start - 2)
                val next = before.getOrNull(start)
                if (!it.palsoftware.pastiera.core.Punctuation.isWordBoundary(ch, prev, next)) {
                    start--
                    continue
                }
                break
            }
            var end = 0
            if (includeAfterCursor) {
                while (end < after.length) {
                    val ch = after[end]
                    val prev = if (end == 0) before.lastOrNull() else after[end - 1]
                    val next = after.getOrNull(end + 1)
                    if (!it.palsoftware.pastiera.core.Punctuation.isWordBoundary(ch, prev, next)) {
                        end++
                        continue
                    }
                    break
                }
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
        schedulePrimaryDictionaryLoad(refreshAfterLoad = false)
        activeExtraSuggestionEngines().forEach { scheduleRepositoryLoad(it.repository, refreshAfterLoad = false) }
    }

    private fun ensureDictionaryLoaded(refreshAfterLoad: Boolean = false) {
        if (!dictionaryRepository.isReady) {
            schedulePrimaryDictionaryLoad(refreshAfterLoad)
        }
    }

    private fun schedulePrimaryDictionaryLoad(refreshAfterLoad: Boolean) {
        val repository = dictionaryRepository
        if (refreshAfterLoad) {
            pendingPrimaryRefreshAfterLoad = true
        }
        if (repository.isReady) {
            if (refreshAfterLoad) {
                pendingPrimaryRefreshAfterLoad = false
                cursorHandler.post { refreshSuggestionsAfterDictionaryReady(repository) }
            }
            return
        }
        if (repository.isLoadStarted) return

        currentLoadJob = loadScope.launch {
            try {
                repository.loadIfNeeded()
                val shouldRefresh = refreshAfterLoad || pendingPrimaryRefreshAfterLoad
                if (shouldRefresh && repository.isReady) {
                    pendingPrimaryRefreshAfterLoad = false
                    cursorHandler.post { refreshSuggestionsAfterDictionaryReady(repository) }
                }
            } catch (_: CancellationException) {
                // Cancelled due to rapid switches; safe to ignore.
            } catch (e: Exception) {
                Log.e("PastieraIME", "Failed to load dictionary", e)
            }
        }
    }

    private fun refreshSuggestionsAfterDictionaryReady(repository: DictionaryRepository) {
        if (!isEnabled() || repository !== dictionaryRepository || !repository.isReady) return

        pendingInitialContextConnection?.let { inputConnection ->
            pendingInitialContextConnection = null
            val word = extractWordAtCursor(inputConnection)
            if (!word.isNullOrBlank()) {
                tracker.setWord(word)
                return
            }
            if (previousCompletedWord == null) {
                publishSentenceStartPredictionsOrStarter()
                return
            }
        }

        val word = tracker.currentWord
        if (word.isNotBlank()) {
            updateSuggestionsForWord(word)
        } else if (previousCompletedWord == null) {
            publishSentenceStartPredictionsOrStarter()
        }
    }

    companion object {
        private const val CURSOR_WORD_CONTEXT_CHARS = 128
        private const val PRIMARY_SUGGESTION_BOOST = 0.35
    }
}
