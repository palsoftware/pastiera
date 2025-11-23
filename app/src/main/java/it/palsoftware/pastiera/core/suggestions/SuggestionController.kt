package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.content.res.AssetManager
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import java.util.concurrent.atomic.AtomicReference

class SuggestionController(
    context: Context,
    assets: AssetManager,
    private val settingsProvider: () -> SuggestionSettings,
    onSuggestionsUpdated: (List<SuggestionResult>) -> Unit
) {

    private val appContext = context.applicationContext
    private val userDictionaryStore = UserDictionaryStore()
    private val dictionaryRepository = DictionaryRepository(appContext, assets, userDictionaryStore)
    private val suggestionEngine = SuggestionEngine(dictionaryRepository)
    private val tracker = CurrentWordTracker(
        onWordChanged = { word ->
            val settings = settingsProvider()
            if (settings.suggestionsEnabled) {
                onSuggestionsUpdated(suggestionEngine.suggest(word, settings.maxSuggestions, settings.accentMatching))
            }
        },
        onWordReset = { onSuggestionsUpdated(emptyList()) }
    )
    private val autoReplaceController = AutoReplaceController(dictionaryRepository, suggestionEngine, settingsProvider)
    private val latestSuggestions: AtomicReference<List<SuggestionResult>> = AtomicReference(emptyList())

    var suggestionsListener: ((List<SuggestionResult>) -> Unit)? = onSuggestionsUpdated

    fun onCharacterCommitted(text: CharSequence) {
        tracker.onCharacterCommitted(text)
        val settings = settingsProvider()
        if (settings.suggestionsEnabled) {
            val next = suggestionEngine.suggest(tracker.currentWord, settings.maxSuggestions, settings.accentMatching)
            latestSuggestions.set(next)
            suggestionsListener?.invoke(next)
        }
    }

    fun onBoundaryKey(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?
    ): AutoReplaceController.ReplaceResult {
        val result = autoReplaceController.handleBoundary(keyCode, event, tracker, inputConnection)
        if (result.replaced) {
            dictionaryRepository.refreshUserEntries()
        }
        suggestionsListener?.invoke(emptyList())
        return result
    }

    fun onCursorMoved() {
        tracker.onCursorMoved()
        suggestionsListener?.invoke(emptyList())
    }

    fun onContextReset() {
        tracker.onContextChanged()
        suggestionsListener?.invoke(emptyList())
    }

    fun onNavModeToggle() {
        tracker.onContextChanged()
    }

    fun addUserWord(word: String) {
        dictionaryRepository.addUserEntry(word)
    }

    fun removeUserWord(word: String) {
        dictionaryRepository.removeUserEntry(word)
    }

    fun markUsed(word: String) {
        dictionaryRepository.markUsed(word)
    }

    fun currentSuggestions(): List<SuggestionResult> = latestSuggestions.get()

    fun userDictionarySnapshot(): List<UserDictionaryStore.UserEntry> = userDictionaryStore.getSnapshot()
}
