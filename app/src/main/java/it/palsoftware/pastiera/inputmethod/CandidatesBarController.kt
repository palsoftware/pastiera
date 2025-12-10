package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.res.AssetManager
import android.widget.LinearLayout
import android.view.inputmethod.InputConnection

/**
 * Coordinates the two StatusBarController instances (full input view vs
 * candidates-only view) so the IME service can treat them as a single surface.
 */
class CandidatesBarController(
    context: Context,
    clipboardHistoryManager: it.palsoftware.pastiera.clipboard.ClipboardHistoryManager? = null,
    assets: AssetManager? = null,
    imeServiceClass: Class<*>? = null
) {

    private val inputStatusBar = StatusBarController(context, StatusBarController.Mode.FULL, clipboardHistoryManager, assets, imeServiceClass)
    private val candidatesStatusBar = StatusBarController(context, StatusBarController.Mode.CANDIDATES_ONLY, clipboardHistoryManager, assets, imeServiceClass)

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

    fun getInputView(emojiMapText: String = ""): LinearLayout {
        return inputStatusBar.getOrCreateLayout(emojiMapText)
    }

    fun getCandidatesView(emojiMapText: String = ""): LinearLayout {
        return candidatesStatusBar.getOrCreateLayout(emojiMapText)
    }

    fun setForceMinimalUi(force: Boolean) {
        inputStatusBar.setForceMinimalUi(force)
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
        candidatesStatusBar.update(snapshot, emojiMapText, inputConnection, symMappings)
    }
}
