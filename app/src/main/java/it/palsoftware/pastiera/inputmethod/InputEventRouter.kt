package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.util.Log
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.NavModeController
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import android.os.Handler
import android.os.Looper
import it.palsoftware.pastiera.inputmethod.AltSymManager
import it.palsoftware.pastiera.core.SymLayoutController
import it.palsoftware.pastiera.core.SymLayoutController.SymKeyResult
import it.palsoftware.pastiera.core.TextInputController
import it.palsoftware.pastiera.core.AutoCorrectionManager
import it.palsoftware.pastiera.core.ModifierStateController
import it.palsoftware.pastiera.inputmethod.KeyboardEventTracker
import it.palsoftware.pastiera.inputmethod.TextSelectionHelper
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import it.palsoftware.pastiera.data.layout.LayoutMapping
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.data.layout.isRealMultiTap

/**
 * Routes IME key events to the appropriate handlers so that the service can
 * focus on lifecycle wiring.
 */
class InputEventRouter(
    private val context: Context,
    private val navModeController: NavModeController
) {

    var suggestionController: it.palsoftware.pastiera.core.suggestions.SuggestionController? = null

    private fun commitTextWithTracking(ic: InputConnection?, text: CharSequence, trackWord: Boolean = true) {
        ic?.commitText(text, 1)
        Log.d("PastieraIME", "commitTextWithTracking: '$text', trackWord=$trackWord")
        if (trackWord) {
            suggestionController?.onCharacterCommitted(text, ic)
        }
    }

    sealed class EditableFieldRoutingResult {
        object Continue : EditableFieldRoutingResult()
        object Consume : EditableFieldRoutingResult()
        object CallSuper : EditableFieldRoutingResult()
    }

    data class NoEditableFieldCallbacks(
        val isAlphabeticKey: (Int) -> Boolean,
        val isLauncherPackage: (String?) -> Boolean,
        val handleLauncherShortcut: (Int) -> Boolean,
        val handlePowerShortcut: (Int) -> Boolean,
        val togglePowerShortcutMode: (String, Boolean) -> Unit, // Callback per toast e stato nav mode
        val callSuper: () -> Boolean,
        val currentInputConnection: () -> InputConnection?
    )

    fun handleKeyDownWithNoEditableField(
        keyCode: Int,
        event: KeyEvent?,
        ctrlKeyMap: Map<Int, KeyMappingLoader.CtrlMapping>,
        callbacks: NoEditableFieldCallbacks,
        ctrlLatchActive: Boolean,
        editorInfo: EditorInfo?,
        currentPackageName: String?,
        powerShortcutsEnabled: Boolean
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Commented out: Nav mode is now persistent and won't close on back button press
            // if (navModeController.isNavModeActive()) {
            //     navModeController.exitNavMode()
            //     return false
            // }
            return callbacks.callSuper()
        }

        // Gestisci SYM per Power Shortcuts (toggle: attiva/disattiva)
        if (keyCode == KeyEvent.KEYCODE_SYM && powerShortcutsEnabled) {
            val message = context.getString(R.string.power_shortcuts_press_key)
            val isNavModeActive = navModeController.isNavModeActive()
            callbacks.togglePowerShortcutMode(message, isNavModeActive)
            return true // Consumiamo l'evento
        }

        if (navModeController.isNavModeKey(keyCode)) {
            return navModeController.handleNavModeKey(
                keyCode,
                event,
                isKeyDown = true,
                ctrlKeyMap = ctrlKeyMap,
                inputConnectionProvider = callbacks.currentInputConnection
            )
        }

        // Gestisci Power Shortcuts (SYM premuto + tasto alfabetico)
        if (!ctrlLatchActive && powerShortcutsEnabled) {
            if (callbacks.isAlphabeticKey(keyCode)) {
                if (callbacks.handlePowerShortcut(keyCode)) {
                    return true
                }
            }
        }

        // Launcher Shortcuts (logica esistente - mantieni per compatibilità)
        if (!ctrlLatchActive && SettingsManager.getLauncherShortcutsEnabled(context)) {
            val packageName = editorInfo?.packageName ?: currentPackageName
            if (callbacks.isLauncherPackage(packageName) && callbacks.isAlphabeticKey(keyCode)) {
                if (callbacks.handleLauncherShortcut(keyCode)) {
                    return true
                }
            }
        }

        return callbacks.callSuper()
    }

    fun handleKeyUpWithNoEditableField(
        keyCode: Int,
        event: KeyEvent?,
        ctrlKeyMap: Map<Int, KeyMappingLoader.CtrlMapping>,
        callbacks: NoEditableFieldCallbacks
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return callbacks.callSuper()
        }

        if (navModeController.isNavModeKey(keyCode)) {
            return navModeController.handleNavModeKey(
                keyCode,
                event,
                isKeyDown = false,
                ctrlKeyMap = ctrlKeyMap,
                inputConnectionProvider = callbacks.currentInputConnection
            )
        }
        return callbacks.callSuper()
    }

    data class EditableFieldKeyDownParams(
        val ctrlLatchFromNavMode: Boolean,
        val ctrlLatchActive: Boolean,
        val isInputViewActive: Boolean,
        val isInputViewShown: Boolean,
        val hasInputConnection: Boolean
    )

    data class EditableFieldKeyDownCallbacks(
        val exitNavMode: () -> Unit,
        val ensureInputViewCreated: () -> Unit,
        val callSuper: () -> Boolean
    )

    fun handleEditableFieldKeyDownPrelude(
        keyCode: Int,
        params: EditableFieldKeyDownParams,
        callbacks: EditableFieldKeyDownCallbacks
    ): EditableFieldRoutingResult {
        if (params.ctrlLatchFromNavMode && params.ctrlLatchActive) {
            callbacks.exitNavMode()
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return EditableFieldRoutingResult.CallSuper
        }

        if (params.hasInputConnection && params.isInputViewActive && !params.isInputViewShown) {
            callbacks.ensureInputViewCreated()
        }

        return EditableFieldRoutingResult.Continue
    }

    data class EditableFieldKeyDownHandlingParams(
        val inputConnection: InputConnection?,
        val isNumericField: Boolean,
        val isInputViewActive: Boolean,
        val shiftPressed: Boolean,
        val ctrlPressed: Boolean,
        val altPressed: Boolean,
        val ctrlLatchActive: Boolean,
        val altLatchActive: Boolean,
        val ctrlLatchFromNavMode: Boolean,
        val ctrlKeyMap: Map<Int, KeyMappingLoader.CtrlMapping>,
        val ctrlOneShot: Boolean,
        val altOneShot: Boolean,
        val clearAltOnSpaceEnabled: Boolean,
        val shiftOneShot: Boolean,
        val capsLockEnabled: Boolean,
        val cursorUpdateDelayMs: Long
    )

    data class EditableFieldKeyDownControllers(
        val modifierStateController: ModifierStateController,
        val symLayoutController: SymLayoutController,
        val altSymManager: AltSymManager,
        val variationStateController: VariationStateController
    )

    data class EditableFieldKeyDownHandlingCallbacks(
        val updateStatusBar: () -> Unit,
        val refreshStatusBar: () -> Unit,
        val disableShiftOneShot: () -> Unit,
        val clearAltOneShot: () -> Unit,
        val clearCtrlOneShot: () -> Unit,
        val getCharacterFromLayout: (Int, KeyEvent?, Boolean) -> Char?,
        val isAlphabeticKey: (Int) -> Boolean,
        val callSuper: () -> Boolean,
        val callSuperWithKey: (Int, KeyEvent?) -> Boolean,
        val startSpeechRecognition: () -> Unit,
        val getMapping: (Int) -> LayoutMapping?,
        val handleMultiTapCommit: (Int, LayoutMapping, Boolean, InputConnection?, Boolean) -> Boolean,
        val isLongPressSuppressed: (Int) -> Boolean
    )

    fun routeEditableFieldKeyDown(
        keyCode: Int,
        event: KeyEvent?,
        params: EditableFieldKeyDownHandlingParams,
        controllers: EditableFieldKeyDownControllers,
        callbacks: EditableFieldKeyDownHandlingCallbacks
    ): EditableFieldRoutingResult {
        var shiftOneShotActive = params.shiftOneShot
        var altLatchActive = params.altLatchActive
        var altOneShotActive = params.altOneShot
        val ic = params.inputConnection

        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (!params.shiftPressed) {
                val result = controllers.modifierStateController.handleShiftKeyDown(keyCode)
                if (result.shouldUpdateStatusBar) {
                    callbacks.updateStatusBar()
                } else if (result.shouldRefreshStatusBar) {
                    callbacks.refreshStatusBar()
                }
            }
            return EditableFieldRoutingResult.CallSuper
        }

        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            // Check if Alt is physically pressed (not latch) - if so, trigger speech recognition (if enabled)
            // Only trigger if both keys are physically pressed simultaneously, not if one is in latch
            if (event?.isAltPressed == true && 
                !params.ctrlPressed &&
                SettingsManager.getAltCtrlSpeechShortcutEnabled(context)) {
                callbacks.startSpeechRecognition()
                return EditableFieldRoutingResult.Consume
            }
            
            if (!params.ctrlPressed) {
                val result = controllers.modifierStateController.handleCtrlKeyDown(
                    keyCode,
                    params.isInputViewActive,
                    onNavModeDeactivated = {
                        navModeController.cancelNotification()
                    }
                )
                if (result.shouldConsume) {
                    if (result.shouldUpdateStatusBar) {
                        callbacks.updateStatusBar()
                    }
                    return EditableFieldRoutingResult.Consume
                } else if (result.shouldUpdateStatusBar) {
                    callbacks.updateStatusBar()
                }
            }
            return EditableFieldRoutingResult.CallSuper
        }

        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            // Check if Ctrl is physically pressed (not latch) - if so, trigger speech recognition (if enabled)
            // Only trigger if both keys are physically pressed simultaneously, not if one is in latch
            if (event?.isCtrlPressed == true && 
                !params.altPressed &&
                SettingsManager.getAltCtrlSpeechShortcutEnabled(context)) {
                callbacks.startSpeechRecognition()
                return EditableFieldRoutingResult.Consume
            }
            
            if (controllers.symLayoutController.isSymActive()) {
                if (controllers.symLayoutController.closeSymPage()) {
                    callbacks.updateStatusBar()
                }
            }
            if (!params.altPressed) {
                val result = controllers.modifierStateController.handleAltKeyDown(keyCode)
                if (result.shouldUpdateStatusBar) {
                    callbacks.updateStatusBar()
                }
            }
            return EditableFieldRoutingResult.Consume
        }

        if (keyCode == KeyEvent.KEYCODE_SYM) {
            controllers.symLayoutController.toggleSymPage()
            callbacks.updateStatusBar()
            return EditableFieldRoutingResult.Consume
        }

        if (keyCode == 322) {
            val swipeToDeleteEnabled = SettingsManager.getSwipeToDelete(context)
            if (swipeToDeleteEnabled) {
                if (ic != null && TextSelectionHelper.deleteLastWord(ic)) {
                    return EditableFieldRoutingResult.Consume
                }
            } else {
                return EditableFieldRoutingResult.Consume
            }
        }

        if (controllers.altSymManager.hasPendingPress(keyCode)) {
            return EditableFieldRoutingResult.Consume
        }

        if (
            params.clearAltOnSpaceEnabled &&
            (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER) &&
            (altLatchActive || altOneShotActive)
        ) {
            controllers.modifierStateController.clearAltState()
            altLatchActive = false
            altOneShotActive = false
            callbacks.updateStatusBar()
        }

        if (
            handleNumericAndSym(
                keyCode = keyCode,
                event = event,
                inputConnection = ic,
                isNumericField = params.isNumericField,
                altSymManager = controllers.altSymManager,
                symLayoutController = controllers.symLayoutController,
                ctrlLatchActive = params.ctrlLatchActive,
                ctrlOneShot = params.ctrlOneShot,
                altLatchActive = altLatchActive,
                cursorUpdateDelayMs = params.cursorUpdateDelayMs,
                updateStatusBar = callbacks.updateStatusBar,
                callSuper = callbacks.callSuper
            )
        ) {
            return EditableFieldRoutingResult.Consume
        }

        if (event?.isAltPressed == true || altLatchActive || altOneShotActive) {
            controllers.altSymManager.cancelPendingLongPress(keyCode)
            if (altOneShotActive) {
                callbacks.clearAltOneShot()
                callbacks.refreshStatusBar()
                altOneShotActive = false
            }

            if (keyCode == KeyEvent.KEYCODE_BACK) {
                return EditableFieldRoutingResult.CallSuper
            }

            if (
                handleAltModifiedKey(
                    keyCode = keyCode,
                    event = event,
                    inputConnection = ic,
                    altSymManager = controllers.altSymManager,
                    updateStatusBar = callbacks.updateStatusBar,
                    callSuperWithKey = callbacks.callSuperWithKey
                )
            ) {
                return EditableFieldRoutingResult.Consume
            }
        }

        if (event?.isCtrlPressed == true || params.ctrlLatchActive || params.ctrlOneShot) {
            if (
                handleCtrlModifiedKey(
                    keyCode = keyCode,
                    event = event,
                    inputConnection = ic,
                    ctrlKeyMap = params.ctrlKeyMap,
                    ctrlLatchFromNavMode = params.ctrlLatchFromNavMode,
                    ctrlOneShot = params.ctrlOneShot,
                    clearCtrlOneShot = {
                        callbacks.clearCtrlOneShot()
                    },
                    updateStatusBar = callbacks.updateStatusBar,
                    callSuper = callbacks.callSuper
                )
            ) {
                return EditableFieldRoutingResult.Consume
            }
        }

        val mapping = callbacks.getMapping(keyCode)
        val resolvedUppercase = mapping?.let {
            when {
                shiftOneShotActive -> true
                params.capsLockEnabled && event?.isShiftPressed != true -> true
                event?.isShiftPressed == true -> true
                else -> false
            }
        } ?: false

        // Compute long-press eligibility up front so multi-tap can still schedule it.
        val longPressSuppressed = callbacks.isLongPressSuppressed(keyCode)
        val useShiftForLongPress = SettingsManager.isLongPressShift(context)
        val hasLongPressSupport = if (useShiftForLongPress) {
            !longPressSuppressed && event != null && event.unicodeChar != 0 && event.unicodeChar.toChar().isLetter()
        } else {
            !longPressSuppressed && controllers.altSymManager.hasAltMapping(keyCode)
        }

        // Ignore system-generated repeats on multi-tap keys so holding the key
        // won't churn through tap levels. Legacy keys keep their normal repeat.
        if (mapping?.isRealMultiTap == true && (event?.repeatCount ?: 0) > 0) {
            return EditableFieldRoutingResult.Consume
        }

        // Multi-tap: commit immediately and replace within the timeout window.
        if (mapping?.isRealMultiTap == true && ic != null) {
            if (callbacks.handleMultiTapCommit(keyCode, mapping, resolvedUppercase, ic, hasLongPressSupport)) {
                if (shiftOneShotActive) {
                    callbacks.disableShiftOneShot()
                    shiftOneShotActive = false
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    callbacks.updateStatusBar()
                }, params.cursorUpdateDelayMs)
                return EditableFieldRoutingResult.Consume
            }
        }

        if (hasLongPressSupport) {
            val wasShiftOneShot = shiftOneShotActive
            val trackedChar = if (LayoutMappingRepository.isMapped(keyCode)) {
                LayoutMappingRepository.getCharacterStringWithModifiers(
                    keyCode,
                    event?.isShiftPressed == true,
                    params.capsLockEnabled,
                    shiftOneShotActive
                )
            } else {
                event?.unicodeChar?.takeIf { it != 0 }?.toChar()?.toString() ?: ""
            }
            val layoutChar = callbacks.getCharacterFromLayout(
                keyCode,
                event,
                event?.isShiftPressed == true
            )
            if (ic != null) {
                controllers.altSymManager.handleKeyWithAltMapping(
                    keyCode,
                    event,
                    params.capsLockEnabled,
                    ic,
                    shiftOneShotActive,
                    layoutChar
                )
                if (trackedChar.isNotEmpty() && trackedChar[0].isLetter()) {
                    suggestionController?.onCharacterCommitted(trackedChar, ic)
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    callbacks.updateStatusBar()
                }, params.cursorUpdateDelayMs)
            }
            if (wasShiftOneShot) {
                callbacks.disableShiftOneShot()
                callbacks.updateStatusBar()
                shiftOneShotActive = false
            }
            return EditableFieldRoutingResult.Consume
        }

        if (shiftOneShotActive) {
            val char = LayoutMappingRepository.getCharacterStringWithModifiers(
                keyCode,
                event?.isShiftPressed == true,
                params.capsLockEnabled,
                true
            )
            if (char.isNotEmpty() && char[0].isLetter()) {
                callbacks.disableShiftOneShot()
                commitTextWithTracking(ic, char)
                Handler(Looper.getMainLooper()).postDelayed({
                    callbacks.updateStatusBar()
                }, params.cursorUpdateDelayMs)
                return EditableFieldRoutingResult.Consume
            }
        }

        if (params.capsLockEnabled && LayoutMappingRepository.isMapped(keyCode)) {
            val char = LayoutMappingRepository.getCharacterStringWithModifiers(
                keyCode,
                event?.isShiftPressed == true,
                params.capsLockEnabled,
                false
            )
            if (char.isNotEmpty() && char[0].isLetter()) {
                commitTextWithTracking(ic, char)
                Handler(Looper.getMainLooper()).postDelayed({
                    callbacks.updateStatusBar()
                }, params.cursorUpdateDelayMs)
                return EditableFieldRoutingResult.Consume
            }
        }

        val charForVariations = if (LayoutMappingRepository.isMapped(keyCode)) {
            LayoutMappingRepository.getCharacterWithModifiers(
                keyCode,
                event?.isShiftPressed == true,
                params.capsLockEnabled,
                shiftOneShotActive
            )
        } else {
            callbacks.getCharacterFromLayout(keyCode, event, event?.isShiftPressed == true)
        }
        if (charForVariations != null) {
            if (controllers.variationStateController.hasVariationsFor(charForVariations)) {
                commitTextWithTracking(ic, charForVariations.toString())
                Handler(Looper.getMainLooper()).postDelayed({
                    callbacks.updateStatusBar()
                }, params.cursorUpdateDelayMs)
                return EditableFieldRoutingResult.Consume
            }
        }

        val isAlphabeticKey = callbacks.isAlphabeticKey(keyCode)
        if (isAlphabeticKey && LayoutMappingRepository.isMapped(keyCode)) {
            val char = LayoutMappingRepository.getCharacterStringWithModifiers(
                keyCode,
                event?.isShiftPressed == true,
                params.capsLockEnabled,
                shiftOneShotActive
            )
            if (char.isNotEmpty() && char[0].isLetter()) {
                Log.d("PastieraIME", "layout commit: '$char'")
                commitTextWithTracking(ic, char)
                Handler(Looper.getMainLooper()).postDelayed({
                    callbacks.updateStatusBar()
                }, params.cursorUpdateDelayMs)
                return EditableFieldRoutingResult.Consume
            }
        }

        // Fallback: if we reach this point and the key actually produced
        // a letter character, commit it with tracking so that the
        // suggestion pipeline can still work even when the key is not
        // covered by the current layout mappings.
        if (ic != null && event != null && event.unicodeChar != 0) {
            val ch = event.unicodeChar.toChar()
            if (ch.isLetter()) {
                Log.d("PastieraIME", "fallback commit: '$ch'")
                commitTextWithTracking(ic, ch.toString())
                Handler(Looper.getMainLooper()).postDelayed({
                    callbacks.updateStatusBar()
                }, params.cursorUpdateDelayMs)
                return EditableFieldRoutingResult.Consume
            }
        }

        return EditableFieldRoutingResult.CallSuper
    }

    fun handleTextInputPipeline(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        isAutoCorrectEnabled: Boolean,
        textInputController: TextInputController,
        autoCorrectionManager: AutoCorrectionManager,
        updateStatusBar: () -> Unit
    ): Boolean {
        val isBoundaryKey = keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER
        val isPunctuation = event?.unicodeChar != null &&
            event.unicodeChar != 0 &&
            event.unicodeChar.toChar() in ".,;:!?()[]{}\"'"

        if (
            autoCorrectionManager.handleBackspaceUndo(
                keyCode,
                inputConnection,
                isAutoCorrectEnabled,
                onStatusBarUpdate = updateStatusBar
            )
        ) {
            suggestionController?.onContextReset()
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_DEL && !shouldDisableSmartFeatures && inputConnection != null) {
            suggestionController?.refreshFromInputConnection(inputConnection)
        }

        if (
            textInputController.handleDoubleSpaceToPeriod(
                keyCode,
                inputConnection,
                shouldDisableSmartFeatures,
                onStatusBarUpdate = updateStatusBar
            )
        ) {
            return true
        }

        textInputController.handleAutoCapAfterPeriod(
            keyCode,
            inputConnection,
            shouldDisableSmartFeatures,
            onStatusBarUpdate = updateStatusBar
        )

        textInputController.handleAutoCapAfterEnter(
            keyCode,
            inputConnection,
            shouldDisableSmartFeatures,
            onStatusBarUpdate = updateStatusBar
        )

        if (
            autoCorrectionManager.handleSpaceOrPunctuation(
                keyCode,
                event,
                inputConnection,
                isAutoCorrectEnabled,
                onStatusBarUpdate = updateStatusBar
            )
        ) {
            suggestionController?.onContextReset()
            return true
        }

        if (!shouldDisableSmartFeatures && inputConnection != null && (isBoundaryKey || isPunctuation) && suggestionController != null) {
            suggestionController?.onBoundaryKey(keyCode, event, inputConnection)
            return true
        }

        autoCorrectionManager.handleAcceptOrResetOnOtherKeys(
            keyCode,
            event,
            isAutoCorrectEnabled
        )
        return false
    }

    fun handleNumericAndSym(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        isNumericField: Boolean,
        altSymManager: AltSymManager,
        symLayoutController: SymLayoutController,
        ctrlLatchActive: Boolean,
        ctrlOneShot: Boolean,
        altLatchActive: Boolean,
        cursorUpdateDelayMs: Long,
        updateStatusBar: () -> Unit,
        callSuper: () -> Boolean
    ): Boolean {
        val ic = inputConnection ?: return false

        // Numeric fields always use the Alt mapping for every key press (short press included).
        // However, if Ctrl is active, let Ctrl handling take precedence (e.g., for copy/paste).
        if (isNumericField) {
            val isCtrlActive = event?.isCtrlPressed == true || ctrlLatchActive || ctrlOneShot
            if (!isCtrlActive) {
                val altChar = altSymManager.getAltMappings()[keyCode]
                if (altChar != null) {
                    ic.commitText(altChar, 1)
                    Handler(Looper.getMainLooper()).postDelayed({
                        updateStatusBar()
                    }, cursorUpdateDelayMs)
                    return true
                }
            }
        }

        // If SYM is active, check SYM mappings first (they take precedence over Alt and Ctrl)
        // When SYM is active, all other modifiers are bypassed
        val shouldBypassSymForCtrl = event?.isCtrlPressed == true || ctrlLatchActive || ctrlOneShot
        if (!shouldBypassSymForCtrl && symLayoutController.isSymActive()) {
            return when (
                symLayoutController.handleKeyWhenActive(
                    keyCode,
                    event,
                    ic,
                    ctrlLatchActive = ctrlLatchActive,
                    altLatchActive = altLatchActive,
                    updateStatusBar = updateStatusBar
                )
            ) {
                SymKeyResult.CONSUME -> true
                SymKeyResult.CALL_SUPER -> callSuper()
                SymKeyResult.NOT_HANDLED -> false
            }
        }

        return false
    }

    /**
     * Handles Alt-modified key presses once Alt is considered active
     * (physical Alt, latch or one-shot). The caller is responsible for
     * managing Alt latch/one-shot state.
     */
    fun handleAltModifiedKey(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        altSymManager: AltSymManager,
        updateStatusBar: () -> Unit,
        callSuperWithKey: (Int, KeyEvent?) -> Boolean
    ): Boolean {
        val ic = inputConnection ?: return false

        // Consume Alt+Space to avoid Android's symbol picker and just insert a space.
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            ic.commitText(" ", 1)
            updateStatusBar()
            return true
        }

        val result = altSymManager.handleAltCombination(
            keyCode,
            ic,
            event
        ) { defaultKeyCode, defaultEvent ->
            // Fallback: delegate to caller (typically super.onKeyDown)
            callSuperWithKey(defaultKeyCode, defaultEvent)
        }

        if (result) {
            updateStatusBar()
        }
        return result
    }

    /**
     * Handles Ctrl-modified shortcuts in editable fields (copy/paste/cut/undo/select_all,
     * expand selection, DPAD/TAB/PAGE/ESC mappings and Ctrl+Backspace behaviour).
     * The caller is responsible for setting/clearing Ctrl latch and one-shot flags.
     */
    fun handleCtrlModifiedKey(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        ctrlKeyMap: Map<Int, KeyMappingLoader.CtrlMapping>,
        ctrlLatchFromNavMode: Boolean,
        ctrlOneShot: Boolean,
        clearCtrlOneShot: () -> Unit,
        updateStatusBar: () -> Unit,
        callSuper: () -> Boolean
    ): Boolean {
        val ic = inputConnection ?: return false

        if (ctrlOneShot && !ctrlLatchFromNavMode) {
            clearCtrlOneShot()
            updateStatusBar()
        }

        val ctrlMapping = ctrlKeyMap[keyCode]
        if (ctrlMapping != null) {
            when (ctrlMapping.type) {
                "action" -> {
                    when (ctrlMapping.value) {
                        "expand_selection_left" -> {
                            KeyboardEventTracker.notifyKeyEvent(
                                keyCode,
                                event,
                                "KEY_DOWN",
                                outputKeyCode = null,
                                outputKeyCodeName = "expand_selection_left"
                            )
                            TextSelectionHelper.expandSelectionLeft(ic)
                            return true
                        }
                        "expand_selection_right" -> {
                            KeyboardEventTracker.notifyKeyEvent(
                                keyCode,
                                event,
                                "KEY_DOWN",
                                outputKeyCode = null,
                                outputKeyCodeName = "expand_selection_right"
                            )
                            TextSelectionHelper.expandSelectionRight(ic)
                            return true
                        }
                        else -> {
                            val actionId = when (ctrlMapping.value) {
                                "copy" -> android.R.id.copy
                                "paste" -> android.R.id.paste
                                "cut" -> android.R.id.cut
                                "undo" -> android.R.id.undo
                                "select_all" -> android.R.id.selectAll
                                else -> null
                            }
                            if (actionId != null) {
                                KeyboardEventTracker.notifyKeyEvent(
                                    keyCode,
                                    event,
                                    "KEY_DOWN",
                                    outputKeyCode = null,
                                    outputKeyCodeName = ctrlMapping.value
                                )
                                ic.performContextMenuAction(actionId)
                                return true
                            }
                            return true
                        }
                    }
                }
                "keycode" -> {
                    val mappedKeyCode = when (ctrlMapping.value) {
                        "DPAD_UP" -> KeyEvent.KEYCODE_DPAD_UP
                        "DPAD_DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                        "DPAD_LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                        "DPAD_RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                        "DPAD_CENTER" -> KeyEvent.KEYCODE_DPAD_CENTER
                        "TAB" -> KeyEvent.KEYCODE_TAB
                        "PAGE_UP" -> KeyEvent.KEYCODE_PAGE_UP
                        "PAGE_DOWN" -> KeyEvent.KEYCODE_PAGE_DOWN
                        "ESCAPE" -> KeyEvent.KEYCODE_ESCAPE
                        "FORWARD_DEL" -> KeyEvent.KEYCODE_FORWARD_DEL
                        else -> null
                    }
                    if (mappedKeyCode != null) {
                        KeyboardEventTracker.notifyKeyEvent(
                            keyCode,
                            event,
                            "KEY_DOWN",
                            outputKeyCode = mappedKeyCode,
                            outputKeyCodeName = KeyboardEventTracker.getOutputKeyCodeName(mappedKeyCode)
                        )
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, mappedKeyCode))
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, mappedKeyCode))

                        if (mappedKeyCode in listOf(
                                KeyEvent.KEYCODE_DPAD_UP,
                                KeyEvent.KEYCODE_DPAD_DOWN,
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_DPAD_RIGHT,
                                KeyEvent.KEYCODE_PAGE_UP,
                                KeyEvent.KEYCODE_PAGE_DOWN
                            )
                        ) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                updateStatusBar()
                            }, 50)
                        }

                        return true
                    }
                    return true
                }
            }
        } else {
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                val extractedText: ExtractedText? = ic.getExtractedText(
                    ExtractedTextRequest().apply {
                        flags = ExtractedText.FLAG_SELECTING
                    },
                    0
                )

                val hasSelection = extractedText?.let {
                    it.selectionStart >= 0 && it.selectionEnd >= 0 && it.selectionStart != it.selectionEnd
                } ?: false

                if (hasSelection) {
                    KeyboardEventTracker.notifyKeyEvent(
                        keyCode,
                        event,
                        "KEY_DOWN",
                        outputKeyCode = null,
                        outputKeyCodeName = "delete_selection"
                    )
                    ic.commitText("", 0)
                    return true
                } else {
                    KeyboardEventTracker.notifyKeyEvent(
                        keyCode,
                        event,
                        "KEY_DOWN",
                        outputKeyCode = null,
                        outputKeyCodeName = "delete_last_word"
                    )
                    TextSelectionHelper.deleteLastWord(ic)
                    return true
                }
            }

            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_BACK) {
                return callSuper()
            }

            return true
        }

        return false
    }
}
