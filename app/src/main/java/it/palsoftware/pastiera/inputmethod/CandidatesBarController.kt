package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Rect
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.view.inputmethod.InputConnection

/**
 * Coordinates the two StatusBarController instances (full input view vs
 * candidates-only view) so the IME service can treat them as a single surface.
 */
class CandidatesBarController(
    private val context: Context,
    clipboardHistoryManager: it.palsoftware.pastiera.clipboard.ClipboardHistoryManager? = null,
    assets: AssetManager? = null,
    imeServiceClass: Class<*>? = null
) {

    private val inputStatusBar = StatusBarController(context, StatusBarController.Mode.INPUT_VIEW, clipboardHistoryManager, assets, imeServiceClass)
    private val candidatesStatusBar = StatusBarController(context, StatusBarController.Mode.CANDIDATES_ONLY, clipboardHistoryManager, assets, imeServiceClass)
    private var candidatesSurfaceActive = true

    var onVariationSelectedListener: VariationButtonHandler.OnVariationSelectedListener? = null
        set(value) {
            field = value
            inputStatusBar.onVariationSelectedListener = value
            candidatesStatusBar.onVariationSelectedListener = value
        }

    var onCursorMovedListener: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onCursorMovedListener = value
            candidatesStatusBar.onCursorMovedListener = value
        }

    var onSpeechRecognitionRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSpeechRecognitionRequested = value
            candidatesStatusBar.onSpeechRecognitionRequested = value
        }

    var onAddUserWord: ((String) -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onAddUserWord = value
            candidatesStatusBar.onAddUserWord = value
        }

    var onAddUserWordSubstitutionRequested: ((String) -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onAddUserWordSubstitutionRequested = value
            candidatesStatusBar.onAddUserWordSubstitutionRequested = value
        }

    var onSuggestionCommitted: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSuggestionCommitted = value
            candidatesStatusBar.onSuggestionCommitted = value
        }

    var onHideSuggestion: ((String) -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onHideSuggestion = value
            candidatesStatusBar.onHideSuggestion = value
        }

    var onDeleteUserSuggestion: ((String) -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onDeleteUserSuggestion = value
            candidatesStatusBar.onDeleteUserSuggestion = value
        }

    var canDeleteUserSuggestion: ((String) -> Boolean)? = null
        set(value) {
            field = value
            inputStatusBar.canDeleteUserSuggestion = value
            candidatesStatusBar.canDeleteUserSuggestion = value
        }

    var onLanguageSwitchRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onLanguageSwitchRequested = value
            candidatesStatusBar.onLanguageSwitchRequested = value
        }
    
    var onClipboardRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onClipboardRequested = value
            candidatesStatusBar.onClipboardRequested = value
        }
    
    var onEmojiPickerRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onEmojiPickerRequested = value
            candidatesStatusBar.onEmojiPickerRequested = value
        }

    var onEmojiPageRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onEmojiPageRequested = value
            candidatesStatusBar.onEmojiPageRequested = value
        }
    
    var onSymbolsPageRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSymbolsPageRequested = value
            candidatesStatusBar.onSymbolsPageRequested = value
        }

    var onSoftwareKeyboardSymToggleRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardSymToggleRequested = value
            candidatesStatusBar.onSoftwareKeyboardSymToggleRequested = value
        }

    var onSymCloseRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSymCloseRequested = value
            candidatesStatusBar.onSymCloseRequested = value
        }

    var onUndoRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onUndoRequested = value
            candidatesStatusBar.onUndoRequested = value
        }

    var onRedoRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onRedoRequested = value
            candidatesStatusBar.onRedoRequested = value
        }

    var onSoftwareKeyboardKeyPressed: ((Int) -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardKeyPressed = value
            candidatesStatusBar.onSoftwareKeyboardKeyPressed = value
        }

    var onSoftwareKeyboardModifierKeyDown: ((Int) -> Boolean)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardModifierKeyDown = value
            candidatesStatusBar.onSoftwareKeyboardModifierKeyDown = value
        }

    var onSoftwareKeyboardModifierKeyUp: ((Int) -> Boolean)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardModifierKeyUp = value
            candidatesStatusBar.onSoftwareKeyboardModifierKeyUp = value
        }

    var onSoftwareKeyboardKeyStroke: ((Int, String) -> Boolean)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardKeyStroke = value
            candidatesStatusBar.onSoftwareKeyboardKeyStroke = value
        }

    var onSoftwareKeyboardShiftTapped: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardShiftTapped = value
            candidatesStatusBar.onSoftwareKeyboardShiftTapped = value
        }

    var onSoftwareKeyboardNonShiftInteraction: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardNonShiftInteraction = value
            candidatesStatusBar.onSoftwareKeyboardNonShiftInteraction = value
        }

    var onSoftwareKeyboardTextInput: ((String, InputConnection?, StatusBarController.StatusSnapshot) -> Boolean)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardTextInput = value
            candidatesStatusBar.onSoftwareKeyboardTextInput = value
        }

    var onSoftwareKeyboardBoundaryTextInput: ((String, InputConnection?) -> Boolean)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardBoundaryTextInput = value
            candidatesStatusBar.onSoftwareKeyboardBoundaryTextInput = value
        }

    var onMinimalUiToggleRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onMinimalUiToggleRequested = value
            candidatesStatusBar.onMinimalUiToggleRequested = value
        }

    var onSoftwareKeyboardModeToggleRequested: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onSoftwareKeyboardModeToggleRequested = value
            candidatesStatusBar.onSoftwareKeyboardModeToggleRequested = value
        }

    fun getInputView(emojiMapText: String = ""): LinearLayout {
        return inputStatusBar.getOrCreateLayout(emojiMapText)
    }

    fun getCandidatesView(emojiMapText: String = ""): LinearLayout {
        return candidatesStatusBar.getOrCreateLayout(emojiMapText).also {
            if (!candidatesSurfaceActive) candidatesStatusBar.collapseLayout()
        }
    }

    /**
     * The framework hides its candidates frame with INVISIBLE, which still reserves the child's
     * measured height. Collapse the child while the full input view is active and explicitly
     * restore it before returning to hardware/candidates-only mode.
     */
    fun setCandidatesSurfaceActive(active: Boolean) {
        candidatesSurfaceActive = active
        if (active) candidatesStatusBar.expandLayout() else candidatesStatusBar.collapseLayout()
    }

    fun isInputViewActuallyRendered(): Boolean =
        inputStatusBar.getLayout().isActuallyRendered()

    fun isCandidatesViewActuallyRendered(): Boolean =
        candidatesStatusBar.getLayout().isActuallyRendered()

    private fun View?.isActuallyRendered(): Boolean {
        if (this == null || !isAttachedToWindow || !isShown || width <= 0 || height <= 0) {
            return false
        }
        val visibleBounds = Rect()
        return getGlobalVisibleRect(visibleBounds) &&
            visibleBounds.width() > 0 &&
            visibleBounds.height() > 0
    }

    fun setPastierinaModeActive(active: Boolean) {
        inputStatusBar.setPastierinaModeActive(active)
        candidatesStatusBar.setPastierinaModeActive(active)
    }

    fun handleBackPressed(): Boolean {
        return inputStatusBar.handleBackPressed() || candidatesStatusBar.handleBackPressed()
    }

    fun handleEmojiPickerSearchKeyDown(
        event: KeyEvent?,
        ctrlActive: Boolean,
        resolveTypedText: ((KeyEvent) -> String?)? = null
    ): Boolean {
        return inputStatusBar.handleEmojiPickerSearchKeyDown(event, ctrlActive, resolveTypedText) ||
            candidatesStatusBar.handleEmojiPickerSearchKeyDown(event, ctrlActive, resolveTypedText)
    }

    fun shouldConsumeEmojiPickerSearchKeyUp(event: KeyEvent?, ctrlActive: Boolean): Boolean {
        return inputStatusBar.shouldConsumeEmojiPickerSearchKeyUp(event, ctrlActive) ||
            candidatesStatusBar.shouldConsumeEmojiPickerSearchKeyUp(event, ctrlActive)
    }

    fun disableEmojiPickerSearchInputCapture() {
        inputStatusBar.disableEmojiPickerSearchInputCapture()
        candidatesStatusBar.disableEmojiPickerSearchInputCapture()
    }

    fun isEmojiPickerSearchInputActive(): Boolean {
        return inputStatusBar.isEmojiPickerSearchInputActive() ||
            candidatesStatusBar.isEmojiPickerSearchInputActive()
    }

    fun createEmojiPickerSearchInputConnection(): InputConnection? {
        return inputStatusBar.createEmojiPickerSearchInputConnection()
            ?: candidatesStatusBar.createEmojiPickerSearchInputConnection()
    }

    fun isPastierinaModeActive(): Boolean = inputStatusBar.isPastierinaModeActive()

    fun refreshWindowInsets() {
        inputStatusBar.refreshWindowInsets()
        candidatesStatusBar.refreshWindowInsets()
    }
    
    fun invalidateStaticVariations() {
        inputStatusBar.invalidateStaticVariations()
        candidatesStatusBar.invalidateStaticVariations()
    }

    fun setMicrophoneButtonActive(isActive: Boolean) {
        inputStatusBar.setMicrophoneButtonActive(isActive)
        candidatesStatusBar.setMicrophoneButtonActive(isActive)
    }
    
    fun updateMicrophoneAudioLevel(rmsdB: Float) {
        inputStatusBar.updateMicrophoneAudioLevel(rmsdB)
        candidatesStatusBar.updateMicrophoneAudioLevel(rmsdB)
    }
    
    fun showSpeechRecognitionHint(show: Boolean) {
        inputStatusBar.showSpeechRecognitionHint(show)
        candidatesStatusBar.showSpeechRecognitionHint(show)
    }

    fun updateStatusBars(
        snapshot: StatusBarController.StatusSnapshot,
        emojiMapText: String,
        inputConnection: InputConnection?,
        symMappings: Map<Int, String>?
    ) {
        inputStatusBar.update(snapshot, emojiMapText, inputConnection, symMappings)
        if (candidatesSurfaceActive) {
            candidatesStatusBar.update(snapshot, emojiMapText, inputConnection, symMappings)
        } else {
            candidatesStatusBar.collapseLayout()
        }
    }

    fun updateClipboardCount(count: Int) {
        inputStatusBar.updateClipboardCount(count)
        candidatesStatusBar.updateClipboardCount(count)
    }

    fun flashSuggestionSlot(suggestionIndex: Int) {
        inputStatusBar.flashSuggestionSlot(suggestionIndex)
        candidatesStatusBar.flashSuggestionSlot(suggestionIndex)
    }

    fun resetSuggestionActionMode() {
        inputStatusBar.resetSuggestionActionMode()
        candidatesStatusBar.resetSuggestionActionMode()
    }

    fun showExpansionSuggestions(suggestions: List<String>, onSelected: (String) -> Unit) {
        inputStatusBar.showExpansionSuggestions(suggestions, onSelected)
        candidatesStatusBar.showExpansionSuggestions(suggestions, onSelected)
    }

    fun clearExpansionSuggestions() {
        inputStatusBar.clearExpansionSuggestions()
        candidatesStatusBar.clearExpansionSuggestions()
    }

    fun cancelSoftwareKeyboardTouchState() {
        inputStatusBar.cancelSoftwareKeyboardTouchState()
        candidatesStatusBar.cancelSoftwareKeyboardTouchState()
    }

}
