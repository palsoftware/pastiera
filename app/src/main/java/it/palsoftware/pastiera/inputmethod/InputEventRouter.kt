package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.text.InputType
import android.util.Log
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.NavModeController
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import android.os.Handler
import android.os.Looper
import it.palsoftware.pastiera.core.SymLayoutController
import it.palsoftware.pastiera.core.SymLayoutController.SymKeyResult
import it.palsoftware.pastiera.core.TextInputController
import it.palsoftware.pastiera.core.AutoCorrectionManager
import it.palsoftware.pastiera.core.ModifierStateController
import it.palsoftware.pastiera.core.AutoSpaceTracker
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import it.palsoftware.pastiera.commands.CommandExecutor
import it.palsoftware.pastiera.commands.CommandRegistry
import it.palsoftware.pastiera.commands.CommandSurface
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
    private val swipeToDeleteKeyCodes = setOf(322, 404)
    private val restrictedFieldBasicCtrlActions = setOf("select_all", "copy", "cut", "paste")

    var suggestionController: it.palsoftware.pastiera.core.suggestions.SuggestionController? = null
    var onCommitText: (() -> Unit)? = null

    private fun isSuggestionDebugLoggingEnabled(): Boolean =
        SettingsManager.isSuggestionDebugLoggingEnabled(context)

    /**
     * Track in-word apostrophes so suggestions don't reset (e.g., "we'" -> "we'll").
     */
    fun handleInWordApostrophe(inputConnection: InputConnection?, pendingApostrophe: Boolean = false) {
        val ic = inputConnection ?: return
        val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        val last = before.lastOrNull()
        val prev = before.dropLast(1).lastOrNull()
        val normalizeApostrophe: (Char?) -> Char? = { c ->
            when (c) {
                '’', '‘', 'ʼ' -> '\''
                else -> c
            }
        }
        val lastNorm = normalizeApostrophe(last)
        val prevNorm = normalizeApostrophe(prev)

        // Two scenarios:
        // 1) pendingApostrophe=true (key event about to commit apostrophe): look at previous char.
        // 2) apostrophe already committed (long-press/Alt): last is apostrophe, prev must be word char.
        val isWordApostrophe = when {
            pendingApostrophe -> lastNorm?.isLetterOrDigit() == true
            lastNorm == '\'' -> prevNorm?.isLetterOrDigit() == true
            else -> false
        }

        if (isWordApostrophe) {
            suggestionController?.onCharacterCommitted("'", ic)
        }
    }

    private fun commitTextWithTracking(ic: InputConnection?, text: CharSequence, trackWord: Boolean = true) {
        if (isSuggestionDebugLoggingEnabled()) {
            Log.d("PastieraIME", "commitTextWithTracking enter: '$text', trackWord=$trackWord")
        }
        onCommitText?.invoke()
        ic?.commitText(text, 1)
        if (trackWord) {
            if (isSuggestionDebugLoggingEnabled()) {
                Log.d("PastieraIME", "commitTextWithTracking notify SC: '$text'")
            }
            suggestionController?.onCharacterCommitted(text, ic)
        }
    }

    sealed class EditableFieldRoutingResult {
        object Continue : EditableFieldRoutingResult()
        object Consume : EditableFieldRoutingResult()
        object CallSuper : EditableFieldRoutingResult()
    }

    data class NoEditableFieldCallbacks(
        val isShortcutKey: (Int) -> Boolean,
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

        if (
            !ctrlLatchActive &&
            event?.isSymPressed == true &&
            SettingsManager.getQuickLauncherTextFieldShortcuts(context) &&
            SettingsManager.isQuickLauncherShortcut(context, keyCode)
        ) {
            if (powerShortcutsEnabled && callbacks.handlePowerShortcut(keyCode)) {
                return true
            }
            if (callbacks.handleLauncherShortcut(keyCode)) {
                return true
            }
        }

        if (
            !ctrlLatchActive &&
            event?.isAltPressed == true &&
            SettingsManager.getQuickLauncherAltShortcutsOutsideTextFields(context) &&
            callbacks.isShortcutKey(keyCode) &&
            callbacks.handleLauncherShortcut(keyCode)
        ) {
            return true
        }

        // Gestisci Power Shortcuts (SYM premuto + tasto alfabetico)
        if (!ctrlLatchActive && powerShortcutsEnabled) {
            if (callbacks.isShortcutKey(keyCode)) {
                if (callbacks.handlePowerShortcut(keyCode)) {
                    return true
                }
            }
        }

        // Launcher Shortcuts (logica esistente - mantieni per compatibilità)
        if (!ctrlLatchActive && SettingsManager.getLauncherShortcutsEnabled(context)) {
            val packageName = editorInfo?.packageName ?: currentPackageName
            if (callbacks.isLauncherPackage(packageName) && callbacks.isShortcutKey(keyCode)) {
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
        val shiftLayerLatched: Boolean,
        val ctrlPressed: Boolean,
        val ctrlPhysicallyPressed: Boolean,
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
        val cursorUpdateDelayMs: Long,
        val altMappingsOverride: Map<Int, String>? = null,
        val shouldDisableSmartFeatures: Boolean = false
    )

    data class EditableFieldKeyDownControllers(
        val modifierStateController: ModifierStateController,
        val symLayoutController: SymLayoutController,
        val altSymManager: AltSymManager,
        val variationStateController: VariationStateController,
        val textInputController: TextInputController
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
        val isLongPressSuppressed: (Int) -> Boolean,
        val toggleMinimalUi: () -> Unit,
        val handleBoundaryText: (String, InputConnection?) -> Boolean = { _, _ -> false },
        val onShiftOneShotToggledOff: () -> Unit = {}
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
        val effectiveCtrlActive = event?.isCtrlPressed == true ||
            params.ctrlPressed ||
            params.ctrlPhysicallyPressed ||
            params.ctrlLatchActive ||
            params.ctrlOneShot ||
            params.ctrlLatchFromNavMode

        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (!params.shiftPressed) {
                val wasShiftOneShot = controllers.modifierStateController.shiftOneShot
                val result = controllers.modifierStateController.handleShiftKeyDown(keyCode)
                if (wasShiftOneShot && !controllers.modifierStateController.shiftOneShot) {
                    callbacks.onShiftOneShotToggledOff()
                }
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
            // Reset Alt state if physically pressed when Sym is pressed.
            // Alt+Sym is Android's language switch shortcut, so we reset Alt to prevent
            // the one-shot state from being applied after the user switches languages.
            if (event?.isAltPressed == true) {
                controllers.modifierStateController.clearAltState(resetPressedState = true)
                callbacks.updateStatusBar()
            }

            // Do not toggle SYM here: the service now toggles on KEY_UP
            // so SYM+key chords can be used without opening the SYM layout.
            return EditableFieldRoutingResult.Consume
        }

        if (keyCode in swipeToDeleteKeyCodes) {
            val swipeToDeleteEnabled = SettingsManager.getSwipeToDelete(context)
            val swipeToDeleteProvider = SettingsManager.getSwipeToDeleteProvider(context)
            if (
                swipeToDeleteEnabled &&
                swipeToDeleteProvider == SettingsManager.SWIPE_TO_DELETE_PROVIDER_TITAN2_KEYCODE
            ) {
                if (ic != null && TextSelectionHelper.deleteLastWord(ic)) {
                    return EditableFieldRoutingResult.Consume
                }
            } else {
                KeyboardEventTracker.notifyKeyEvent(
                    keyCode = keyCode,
                    event = event,
                    action = "KEY_DOWN",
                    origin = "ime_service",
                    outputKeyCode = null,
                    outputKeyCodeName = "swipe_to_delete_ignored_${swipeToDeleteProvider}"
                )
                return EditableFieldRoutingResult.Consume
            }
        }

        if (controllers.altSymManager.hasPendingPress(keyCode)) {
            return EditableFieldRoutingResult.Consume
        }

        var passThroughAltBoundary = false
        if (
            params.clearAltOnSpaceEnabled &&
            (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER) &&
            (altLatchActive || altOneShotActive)
        ) {
            val keepLatchedAlt = altLatchActive && SettingsManager.getAltLatchStaysOnSpace(context)
            if (keepLatchedAlt) {
                if (altOneShotActive) {
                    controllers.modifierStateController.altOneShot = false
                    altOneShotActive = false
                    callbacks.updateStatusBar()
                }
                passThroughAltBoundary = true
            } else {
                controllers.modifierStateController.clearAltState()
                altLatchActive = false
                altOneShotActive = false
                callbacks.updateStatusBar()
            }
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
                ctrlPressed = params.ctrlPressed,
                ctrlPhysicallyPressed = params.ctrlPhysicallyPressed,
                ctrlLatchFromNavMode = params.ctrlLatchFromNavMode,
                ctrlOneShot = params.ctrlOneShot,
                altLatchActive = altLatchActive,
                altMappingsOverride = params.altMappingsOverride,
                cursorUpdateDelayMs = params.cursorUpdateDelayMs,
                updateStatusBar = callbacks.updateStatusBar,
                handleBoundaryText = callbacks.handleBoundaryText,
                callSuper = callbacks.callSuper
            )
        ) {
            return EditableFieldRoutingResult.Consume
        }

        if (!passThroughAltBoundary && (event?.isAltPressed == true || altLatchActive || altOneShotActive)) {
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
                    altMappingsOverride = params.altMappingsOverride,
                    updateStatusBar = callbacks.updateStatusBar,
                    callSuperWithKey = callbacks.callSuperWithKey
                )
            ) {
                return EditableFieldRoutingResult.Consume
            }
        }

        if (event?.isCtrlPressed == true || params.ctrlLatchActive || params.ctrlOneShot || (params.isNumericField && effectiveCtrlActive)) {
            if (
                handleCtrlModifiedKey(
                    keyCode = keyCode,
                    event = event,
                    inputConnection = ic,
                    ctrlKeyMap = params.ctrlKeyMap,
                    ctrlLatchFromNavMode = params.ctrlLatchFromNavMode,
                    ctrlOneShot = params.ctrlOneShot,
                    ctrlPhysicallyPressed = params.ctrlPressed || params.ctrlPhysicallyPressed,
                    selectionShiftActive = params.shiftPressed || event?.isShiftPressed == true,
                    forceBasicContextMenuActions = params.isNumericField,
                    clearCtrlOneShot = {
                        callbacks.clearCtrlOneShot()
                    },
                    updateStatusBar = callbacks.updateStatusBar,
                    callSuper = callbacks.callSuper,
                    toggleMinimalUi = callbacks.toggleMinimalUi
                )
            ) {
                return EditableFieldRoutingResult.Consume
            }
        }

        val mapping = callbacks.getMapping(keyCode)
        val resolvedUppercase = mapping?.let {
            when {
                shiftOneShotActive -> true
                params.shiftLayerLatched -> true
                params.capsLockEnabled && event?.isShiftPressed != true -> true
                event?.isShiftPressed == true -> true
                else -> false
            }
        } ?: false

        // Compute long-press eligibility up front so multi-tap can still schedule it.
        val longPressSuppressed = callbacks.isLongPressSuppressed(keyCode)
        val longPressMode = SettingsManager.getLongPressModifier(context)
        val effectiveShiftForLongPress =
            event?.isShiftPressed == true || shiftOneShotActive || params.shiftLayerLatched
        val charForLongPress = if (LayoutMappingRepository.isMapped(keyCode)) {
            LayoutMappingRepository.getCharacterWithModifiers(
                keyCode,
                effectiveShiftForLongPress,
                params.capsLockEnabled,
                shiftOneShotActive
            )
        } else {
            callbacks.getCharacterFromLayout(keyCode, event, effectiveShiftForLongPress)
        }
        val hasLongPressSupport = when (longPressMode) {
            "shift" -> !longPressSuppressed && event != null && event.unicodeChar != 0 && event.unicodeChar.toChar().isLetter()
            "variations" -> !longPressSuppressed && charForLongPress != null && controllers.variationStateController.hasVariationsFor(charForLongPress)
            "sym", "sym_symbols", "sym_emoji" -> !longPressSuppressed && controllers.altSymManager.hasSymLongPressMapping(
                keyCode = keyCode,
                shiftPressed = effectiveShiftForLongPress
            )
            else -> !longPressSuppressed && controllers.altSymManager.hasAltMapping(keyCode)
        }

        if (
            controllers.textInputController.handleSpacedHyphenToEnDash(
                keyCode = keyCode,
                inputConnection = ic,
                shouldDisableSmartPunctuation = params.shouldDisableSmartFeatures
            )
        ) {
            Handler(Looper.getMainLooper()).postDelayed({
                callbacks.updateStatusBar()
            }, params.cursorUpdateDelayMs)
            return EditableFieldRoutingResult.Consume
        }

        val smartReplacementText = when {
            keyCode == KeyEvent.KEYCODE_SPACE -> " "
            LayoutMappingRepository.isMapped(keyCode) -> LayoutMappingRepository.getCharacterStringWithModifiers(
                keyCode,
                effectiveShiftForLongPress,
                params.capsLockEnabled,
                shiftOneShotActive
            ).takeIf { it.length == 1 }.orEmpty()
            event?.unicodeChar?.takeIf { it != 0 } != null -> event.unicodeChar.toChar().toString()
            else -> ""
        }
        if (
            controllers.textInputController.handlePendingMidWordQuoteToApostrophe(
                typedText = smartReplacementText,
                inputConnection = ic,
                shouldDisableSmartPunctuation = params.shouldDisableSmartFeatures
            )
        ) {
            suggestionController?.onCharacterCommitted("'", ic)
            if (smartReplacementText.isNotEmpty()) {
                suggestionController?.onCharacterCommitted(smartReplacementText, ic)
            }
            Handler(Looper.getMainLooper()).postDelayed({
                callbacks.updateStatusBar()
            }, params.cursorUpdateDelayMs)
            return EditableFieldRoutingResult.Consume
        }
        if (
            controllers.textInputController.handleSmartQuoteReplacement(
                typedText = smartReplacementText,
                inputConnection = ic,
                shouldDisableSmartPunctuation = params.shouldDisableSmartFeatures
            )
        ) {
            Handler(Looper.getMainLooper()).postDelayed({
                callbacks.updateStatusBar()
            }, params.cursorUpdateDelayMs)
            return EditableFieldRoutingResult.Consume
        }

        val shouldUseMultiTap = mapping?.isRealMultiTap == true &&
            !shouldSuppressUppercaseEszettMultiTap(mapping, resolvedUppercase)

        // Ignore system-generated repeats on multi-tap keys so holding the key
        // won't churn through tap levels. Legacy keys keep their normal repeat.
        if (shouldUseMultiTap && (event?.repeatCount ?: 0) > 0) {
            return EditableFieldRoutingResult.Consume
        }

        // Multi-tap: commit immediately and replace within the timeout window.
        // Uppercase multi-tap is intentional for layouts that expose accented
        // uppercase letters such as E -> È -> É.
        if (shouldUseMultiTap && ic != null) {
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
                    effectiveShiftForLongPress,
                    params.capsLockEnabled,
                    shiftOneShotActive
                )
            } else {
                event?.unicodeChar?.takeIf { it != 0 }?.toChar()?.toString() ?: ""
            }
            val layoutChar = callbacks.getCharacterFromLayout(
                keyCode,
                event,
                effectiveShiftForLongPress
            )
            if (ic != null) {
                controllers.altSymManager.handleKeyWithAltMapping(
                    keyCode,
                    event,
                    params.capsLockEnabled,
                    ic,
                    shiftOneShotActive || params.shiftLayerLatched,
                    layoutChar
                )
                // Track immediately only when using Shift long press (result still a letter).
                val shouldTrackImmediately = longPressMode == "shift"
                if (shouldTrackImmediately && trackedChar.isNotEmpty() && trackedChar[0].isLetter()) {
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
                effectiveShiftForLongPress,
                params.capsLockEnabled,
                shiftOneShotActive
            )
        } else {
            callbacks.getCharacterFromLayout(keyCode, event, effectiveShiftForLongPress)
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
                effectiveShiftForLongPress,
                params.capsLockEnabled,
                shiftOneShotActive
            )
            if (char.isNotEmpty() && char[0].isLetter()) {
                if (isSuggestionDebugLoggingEnabled()) {
                    Log.d("PastieraIME", "layout commit: '$char'")
                }
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
                if (isSuggestionDebugLoggingEnabled()) {
                    Log.d("PastieraIME", "fallback commit: '$ch'")
                }
                commitTextWithTracking(ic, ch.toString())
                Handler(Looper.getMainLooper()).postDelayed({
                    callbacks.updateStatusBar()
                }, params.cursorUpdateDelayMs)
                return EditableFieldRoutingResult.Consume
            }
        }

        return EditableFieldRoutingResult.CallSuper
    }

    fun handleConfiguredForwardDeleteAlternatives(
        context: android.content.Context,
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        altActive: Boolean
    ): Boolean {
        if (keyCode != KeyEvent.KEYCODE_DEL || inputConnection == null) {
            return false
        }

        val selectedText = inputConnection.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            return false
        }

        val shiftTrigger =
            SettingsManager.getShiftBackspaceDelete(context) &&
            event?.isShiftPressed == true
        val altTrigger =
            SettingsManager.getAltBackspaceDelete(context) &&
            altActive

        if (shiftTrigger || altTrigger) {
            inputConnection.deleteSurroundingText(0, 1)
            return true
        }

        if (
            SettingsManager.getBackspaceAtStartDelete(context) &&
            event?.isShiftPressed != true &&
            !altActive
        ) {
            val textBefore = inputConnection.getTextBeforeCursor(1, 0)
            if (textBefore?.isEmpty() == true) {
                inputConnection.deleteSurroundingText(0, 1)
                return true
            }
        }

        return false
    }

    private fun shouldSuppressUppercaseEszettMultiTap(
        mapping: LayoutMapping,
        resolvedUppercase: Boolean
    ): Boolean {
        return resolvedUppercase && mapping.taps.drop(1).any { it.uppercase == "ẞ" }
    }

    fun handleTextInputPipeline(
        context: android.content.Context,
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        shouldDisableSuggestions: Boolean,
        shouldDisableAutoCorrect: Boolean,
        shouldDisableAutoCapitalize: Boolean,
        shouldDisableDoubleSpaceToPeriod: Boolean,
        isAutoCorrectEnabled: Boolean,
        textInputController: TextInputController,
        autoCorrectionManager: AutoCorrectionManager,
        inputContextState: it.palsoftware.pastiera.core.InputContextState?,
        enableShiftOneShot: (() -> Boolean)?,
        editorInfo: EditorInfo? = null,
        updateStatusBar: () -> Unit
    ): Boolean {
        val isEnterKey = keyCode == KeyEvent.KEYCODE_ENTER
        val isSpaceKey = keyCode == KeyEvent.KEYCODE_SPACE
        val typedCharRaw = event?.unicodeChar?.takeIf { it != 0 }?.toChar()
        val typedChar = when (typedCharRaw) {
            '’', '‘', 'ʼ' -> '\''
            else -> typedCharRaw
        }
        val prevCharRaw = inputConnection?.getTextBeforeCursor(1, 0)?.lastOrNull()
        val prevChar = when (prevCharRaw) {
            '’', '‘', 'ʼ' -> '\''
            else -> prevCharRaw
        }
        val isWordApostrophe = typedChar == '\'' && prevChar?.isLetterOrDigit() == true
        val isBoundaryKey = isSpaceKey || isEnterKey
        // Apostrophe is never a boundary/punctuation for suggestions.
        val boundarySet = it.palsoftware.pastiera.core.Punctuation.BOUNDARY
        val isPunctuation = typedChar != null &&
            typedChar in boundarySet &&
            typedChar != '\''

        // Keep suggestions alive for in-word apostrophes (e.g., "we'" → "we'll").
        if (isWordApostrophe && !shouldDisableSuggestions) {
            handleInWordApostrophe(inputConnection, pendingApostrophe = true)
        }

        // Clear pending auto-space flag on backspace (avoid stale state); keep it for letters to handle long-press punctuation.
        if (typedChar == null && keyCode == KeyEvent.KEYCODE_DEL) {
            AutoSpaceTracker.clear()
        }

        // Try new dictionary-based auto-replace undo first (if experimental suggestions enabled)
        if (keyCode == KeyEvent.KEYCODE_DEL && 
            SettingsManager.isExperimentalSuggestionsEnabled(context) &&
            SettingsManager.getAutoReplaceOnSpaceEnter(context)) {
            
            val sc = suggestionController
            if (sc != null) {
                val undoHandled = sc.handleBackspaceUndo(keyCode, inputConnection)
                if (undoHandled) {
                    updateStatusBar()
                    return true
                }
            }
        }

        // Then try legacy auto-correction undo
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

        // Refresh suggestions on DEL (if suggestions enabled)
        if (keyCode == KeyEvent.KEYCODE_DEL && !shouldDisableSuggestions && inputConnection != null) {
            suggestionController?.refreshFromInputConnection(inputConnection)
        }

        // Handle double-space-to-period (if enabled)
        if (
            textInputController.handleDoubleSpaceToPeriod(
                keyCode,
                inputConnection,
                shouldDisableDoubleSpaceToPeriod,
                shouldDisableAutoCapitalize,
                onStatusBarUpdate = updateStatusBar
            )
        ) {
            return true
        }

        if (
            textInputController.handleSpacedHyphenToEnDash(
                keyCode = keyCode,
                inputConnection = inputConnection,
                shouldDisableSmartPunctuation = inputContextState?.shouldDisableSmartFeatures == true
            )
        ) {
            return true
        }

        val smartReplacementTextForPipeline =
            if (isSpaceKey) " " else event?.unicodeChar?.takeIf { it != 0 }?.toChar()?.toString().orEmpty()
        if (
            textInputController.handlePendingMidWordQuoteToApostrophe(
                typedText = smartReplacementTextForPipeline,
                inputConnection = inputConnection,
                shouldDisableSmartPunctuation = inputContextState?.shouldDisableSmartFeatures == true
            )
        ) {
            suggestionController?.onCharacterCommitted("'", inputConnection)
            if (smartReplacementTextForPipeline.isNotEmpty()) {
                suggestionController?.onCharacterCommitted(smartReplacementTextForPipeline, inputConnection)
            }
            return true
        }

        if (
            textInputController.handleSmartQuoteReplacement(
                typedText = smartReplacementTextForPipeline,
                inputConnection = inputConnection,
                shouldDisableSmartPunctuation = inputContextState?.shouldDisableSmartFeatures == true
            )
        ) {
            return true
        }

        // Handle auto-capitalization after period (if enabled)
        textInputController.handleAutoCapAfterPeriod(
            keyCode,
            inputConnection,
            shouldDisableAutoCapitalize,
            onStatusBarUpdate = updateStatusBar
        )

        // Handle auto-capitalization after Enter (if enabled)
        textInputController.handleAutoCapAfterEnter(
            keyCode,
            inputConnection,
            shouldDisableAutoCapitalize,
            onStatusBarUpdate = updateStatusBar
        )

        // Handle field-specific capitalization flags (CAP_WORDS, CAP_SENTENCES) after boundary keys
        if (inputContextState != null && enableShiftOneShot != null) {
            val shouldCap = it.palsoftware.pastiera.inputmethod.AutoCapitalizeHelper.shouldCapitalizeAfterBoundary(
                context = context,
                state = inputContextState,
                inputConnection = inputConnection,
                keyCode = keyCode
            )
            if (shouldCap) {
                if (enableShiftOneShot()) {
                    updateStatusBar()
                }
            }
        }

        // For Enter, apply autocorrect only when there is NO IME action.
        // This avoids altering text for fields that map Enter to actions (e.g., Send).
        if (isEnterKey) {
            val imeOptions = editorInfo?.imeOptions ?: 0
            val actionId = editorInfo?.actionId ?: 0
            val actionLabel = editorInfo?.actionLabel
            val maskedAction = imeOptions and EditorInfo.IME_MASK_ACTION
            val resolvedAction = if (actionId != 0) actionId else maskedAction
            // Treat masked action or custom actionLabel as an IME action, even if NO_ENTER_ACTION is set.
            val hasImeAction = actionLabel != null || resolvedAction in listOf(
                EditorInfo.IME_ACTION_GO,
                EditorInfo.IME_ACTION_SEARCH,
                EditorInfo.IME_ACTION_SEND,
                EditorInfo.IME_ACTION_NEXT,
                EditorInfo.IME_ACTION_DONE,
                EditorInfo.IME_ACTION_PREVIOUS
            )

            Log.d(
                "EnterPipeline",
                "imeOptions=0x${Integer.toHexString(imeOptions)}, " +
                        "maskedAction=0x${Integer.toHexString(maskedAction)}, " +
                        "actionId=$actionId, actionLabel=${actionLabel ?: "null"}, " +
                        "hasImeAction=$hasImeAction"
            )

            if (hasImeAction) {
                // Skip autocorrection/haptics when Enter is used as an IME action.
                Log.d("EnterPipeline", "Skipping autocorrection and suggestions: hasImeAction=true")
                return false
            }

            val inputType = editorInfo?.inputType ?: 0
            val isMultiline = (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0

            val handled = if (shouldDisableSuggestions || suggestionController == null) {
                autoCorrectionManager.handleBoundaryKey(
                    keyCode,
                    event,
                    inputConnection,
                    isAutoCorrectEnabled,
                    commitBoundary = isMultiline, // commit newline inside autocorrect only for multiline
                    onStatusBarUpdate = updateStatusBar,
                    boundaryCharOverride = '\n',
                    isKnownWord = { word -> suggestionController?.isKnownWordInActiveDictionaries(word) == true }
                )
            } else {
                false
            }
            if (handled) {
                suggestionController?.onContextReset()
                // For multiline with commitBoundary=true, newline was already committed; consume Enter.
                if (isMultiline) {
                    Log.d("EnterPipeline", "Autocorrected; boundary committed (multiline, no IME action)")
                    return true
                }
                // Single-line: let app handle Enter/newline.
                return false
            }

            // No autocorrection: run suggestions/auto-replace on Enter with the real keycode/event.
            var replaceResult: it.palsoftware.pastiera.core.suggestions.AutoReplaceController.ReplaceResult? = null
            if (!shouldDisableSuggestions && inputConnection != null) {
                replaceResult = suggestionController?.onBoundaryKey(keyCode, event, inputConnection)
            }
            // If auto-replace committed the boundary, consume Enter to avoid double newline.
            if (replaceResult?.committed == true) {
                Log.d("EnterPipeline", "Auto-replace committed boundary on Enter; consuming")
                return true
            }
            Log.d("EnterPipeline", "No autocorrection/auto-replace commit; letting app handle Enter/newline")
            return false
        }

        if (
            (isBoundaryKey || isPunctuation) &&
            (shouldDisableSuggestions || suggestionController == null) &&
            autoCorrectionManager.handleBoundaryKey(
                keyCode,
                event,
                inputConnection,
                isAutoCorrectEnabled,
                commitBoundary = true,
                onStatusBarUpdate = updateStatusBar,
                isKnownWord = { word -> suggestionController?.isKnownWordInActiveDictionaries(word) == true }
            )
        ) {
            suggestionController?.onContextReset()
            return true
        }
        if (
            isPunctuation &&
            SettingsManager.shouldApplyFrenchPunctuationSpacing(context) &&
            inputConnection != null &&
            it.palsoftware.pastiera.core.Punctuation.commitFrenchSpacedPunctuation(inputConnection, typedChar)
        ) {
            suggestionController?.onContextReset()
            return true
        }

        // Handle suggestions on boundary keys/punctuation (if suggestions enabled)
        if (!shouldDisableSuggestions && inputConnection != null && (isBoundaryKey || isPunctuation) && suggestionController != null) {
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

    fun handleBoundaryText(
        context: android.content.Context,
        text: String,
        inputConnection: InputConnection?,
        shouldDisableSuggestions: Boolean,
        isAutoCorrectEnabled: Boolean,
        autoCorrectionManager: AutoCorrectionManager,
        updateStatusBar: () -> Unit
    ): Boolean {
        val input = inputConnection ?: return false
        if (text.length != 1) return false
        val boundaryChar = it.palsoftware.pastiera.core.Punctuation.normalizeApostrophe(text[0])
        if (boundaryChar == '\'' || boundaryChar !in it.palsoftware.pastiera.core.Punctuation.BOUNDARY) {
            return false
        }

        if (
            (shouldDisableSuggestions || suggestionController == null) &&
            autoCorrectionManager.handleBoundaryKey(
                keyCode = KeyEvent.KEYCODE_UNKNOWN,
                event = null,
                inputConnection = input,
                isAutoCorrectEnabled = isAutoCorrectEnabled,
                commitBoundary = true,
                onStatusBarUpdate = updateStatusBar,
                boundaryCharOverride = boundaryChar,
                isKnownWord = { word ->
                    suggestionController?.isKnownWordInActiveDictionaries(word) == true
                }
            )
        ) {
            suggestionController?.onContextReset()
            return true
        }

        if (
            SettingsManager.shouldApplyFrenchPunctuationSpacing(context) &&
            it.palsoftware.pastiera.core.Punctuation.commitFrenchSpacedPunctuation(input, boundaryChar)
        ) {
            suggestionController?.onContextReset()
            updateStatusBar()
            return true
        }

        val controller = suggestionController
        if (!shouldDisableSuggestions && controller != null) {
            controller.onBoundaryKey(
                keyCode = KeyEvent.KEYCODE_UNKNOWN,
                event = null,
                inputConnection = input,
                boundaryCharOverride = boundaryChar
            )
            updateStatusBar()
            return true
        }

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
        ctrlPressed: Boolean,
        ctrlPhysicallyPressed: Boolean,
        ctrlLatchFromNavMode: Boolean,
        ctrlOneShot: Boolean,
        altLatchActive: Boolean,
        altMappingsOverride: Map<Int, String>? = null,
        cursorUpdateDelayMs: Long,
        updateStatusBar: () -> Unit,
        handleBoundaryText: (String, InputConnection?) -> Boolean = { _, _ -> false },
        callSuper: () -> Boolean
    ): Boolean {
        val ic = inputConnection ?: return false

        // Numeric fields always use the Alt mapping for every key press (short press included).
        // However, if Ctrl is active, let Ctrl handling take precedence (e.g., for copy/paste).
        if (isNumericField) {
            val isCtrlActive = event?.isCtrlPressed == true ||
                ctrlLatchActive ||
                ctrlOneShot ||
                ctrlPressed ||
                ctrlPhysicallyPressed ||
                ctrlLatchFromNavMode
            if (!isCtrlActive) {
                val altChar = (altMappingsOverride ?: altSymManager.getAltMappings())[keyCode]
                if (altChar != null) {
                    val dpadKeyCode = deviceLayerDpadKeyCode(altChar)
                    if (dpadKeyCode != null) {
                        sendModifiedKeyEvent(ic, dpadKeyCode, ctrl = false, shift = false)
                    } else {
                        ic.commitText(altChar, 1)
                    }
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
                    updateStatusBar = updateStatusBar,
                    handleBoundaryText = handleBoundaryText
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
        altMappingsOverride: Map<Int, String>? = null,
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

        val deviceLayerValue = (altMappingsOverride ?: altSymManager.getAltMappings())[keyCode]
        val deviceLayerDpad = deviceLayerDpadKeyCode(deviceLayerValue)
        if (deviceLayerDpad != null) {
            sendModifiedKeyEvent(ic, deviceLayerDpad, ctrl = false, shift = false)
            updateStatusBar()
            return true
        }

        val result = altSymManager.handleAltCombination(
            keyCode,
            ic,
            event,
            mappingsOverride = altMappingsOverride
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
        ctrlPhysicallyPressed: Boolean,
        selectionShiftActive: Boolean = false,
        forceBasicContextMenuActions: Boolean = false,
        clearCtrlOneShot: () -> Unit,
        updateStatusBar: () -> Unit,
        callSuper: () -> Boolean,
        toggleMinimalUi: () -> Unit
    ): Boolean {
        val isPhysicalCtrlCombo = event?.isCtrlPressed == true || ctrlPhysicallyPressed
        val useNavModeForHeldCtrl = SettingsManager.getNavModeCtrlHoldEnabled(context)
        val useLayoutAwareCtrlShortcuts = SettingsManager.getLayoutAwareCtrlShortcutsEnabled(context)
        val shortcutKeyCode = if (useLayoutAwareCtrlShortcuts) {
            resolveLayoutShortcutKeyCode(keyCode)
        } else {
            keyCode
        }
        val usesPhysicalNavGrid = ctrlLatchFromNavMode ||
            (isPhysicalCtrlCombo && useNavModeForHeldCtrl)
        val mappingKeyCode = if (usesPhysicalNavGrid) keyCode else shortcutKeyCode
        val ctrlMapping = ctrlKeyMap[mappingKeyCode]
        val shouldForceContextMenuAction =
            forceBasicContextMenuActions &&
            ctrlMapping?.type == "action" &&
                ctrlMapping.value in restrictedFieldBasicCtrlActions

        fun passThroughCtrlCombo(): Boolean {
            val ic = inputConnection ?: return false
            if (event != null) {
                ic.sendKeyEvent(event.withKeyCodeAndCtrl(shortcutKeyCode))
            } else {
                val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_CTRL_RIGHT_ON
                val down = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, shortcutKeyCode, 0, meta)
                ic.sendKeyEvent(down)
            }
            return true
        }

        // When Ctrl is physically held, prefer native app shortcuts (rich-text editors, IDEs, etc.).
        // This must take precedence over one-shot, because a physical press sets one-shot internally.
        if (isPhysicalCtrlCombo && !ctrlLatchFromNavMode && !useNavModeForHeldCtrl && !shouldForceContextMenuAction) {
            return passThroughCtrlCombo()
        }

        if (ctrlOneShot && !ctrlLatchFromNavMode) {
            clearCtrlOneShot()
            updateStatusBar()
        }

        if (ctrlMapping?.type == "command") {
            val command = CommandRegistry(context).resolve(ctrlMapping.value)
                ?: return callSuper()
            if (!command.defaultSurfaces.contains(CommandSurface.NavMode)) {
                return callSuper()
            }
            KeyboardEventTracker.notifyKeyEvent(
                keyCode,
                event,
                "KEY_DOWN",
                origin = "ime_router",
                outputKeyCode = null,
                outputKeyCodeName = ctrlMapping.value
            )
            return CommandExecutor(
                context = context,
                navModeController = navModeController,
                inputConnectionProvider = { inputConnection }
            ).execute(command).isSuccess
        }

        val ic = inputConnection ?: return false

        if (ctrlMapping != null) {
            when (ctrlMapping.type) {
                "action" -> {
                    when (ctrlMapping.value) {
                        "expand_selection_left" -> {
                            KeyboardEventTracker.notifyKeyEvent(
                                keyCode,
                                event,
                                "KEY_DOWN",
                                origin = "ime_router",
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
                                origin = "ime_router",
                                outputKeyCode = null,
                                outputKeyCodeName = "expand_selection_right"
                            )
                            TextSelectionHelper.expandSelectionRight(ic)
                            return true
                        }
                        "move_word_left" -> {
                            KeyboardEventTracker.notifyKeyEvent(
                                keyCode,
                                event,
                                "KEY_DOWN",
                                origin = "ime_router",
                                outputKeyCode = null,
                                outputKeyCodeName = if (selectionShiftActive) {
                                    "expand_selection_word_left"
                                } else {
                                    "move_word_left"
                                }
                            )
                            if (selectionShiftActive) {
                                TextSelectionHelper.expandSelectionWordLeft(ic)
                            } else {
                                TextSelectionHelper.moveCursorWordLeft(ic)
                            }
                            return true
                        }
                        "move_word_right" -> {
                            KeyboardEventTracker.notifyKeyEvent(
                                keyCode,
                                event,
                                "KEY_DOWN",
                                origin = "ime_router",
                                outputKeyCode = null,
                                outputKeyCodeName = if (selectionShiftActive) {
                                    "expand_selection_word_right"
                                } else {
                                    "move_word_right"
                                }
                            )
                            if (selectionShiftActive) {
                                TextSelectionHelper.expandSelectionWordRight(ic)
                            } else {
                                TextSelectionHelper.moveCursorWordRight(ic)
                            }
                            return true
                        }
                        "expand_selection_word_left" -> {
                            KeyboardEventTracker.notifyKeyEvent(
                                keyCode,
                                event,
                                "KEY_DOWN",
                                origin = "ime_router",
                                outputKeyCode = null,
                                outputKeyCodeName = "expand_selection_word_left"
                            )
                            TextSelectionHelper.expandSelectionWordLeft(ic)
                            return true
                        }
                        "expand_selection_word_right" -> {
                            KeyboardEventTracker.notifyKeyEvent(
                                keyCode,
                                event,
                                "KEY_DOWN",
                                origin = "ime_router",
                                outputKeyCode = null,
                                outputKeyCodeName = "expand_selection_word_right"
                            )
                            TextSelectionHelper.expandSelectionWordRight(ic)
                            return true
                        }
                        "page_start", "page_end" -> {
                            val targetKeyCode = when (ctrlMapping.value) {
                                "page_start" -> KeyEvent.KEYCODE_MOVE_HOME
                                "page_end" -> KeyEvent.KEYCODE_MOVE_END
                                else -> null
                            } ?: return callSuper()
                            KeyboardEventTracker.notifyKeyEvent(
                                keyCode,
                                event,
                                "KEY_DOWN",
                                origin = "ime_router",
                                outputKeyCode = targetKeyCode,
                                outputKeyCodeName = if (selectionShiftActive) {
                                    "ctrl_shift_${KeyboardEventTracker.getOutputKeyCodeName(targetKeyCode)}"
                                } else {
                                    "ctrl_${KeyboardEventTracker.getOutputKeyCodeName(targetKeyCode)}"
                                }
                            )
                            sendModifiedKeyEvent(ic, targetKeyCode, ctrl = true, shift = selectionShiftActive)
                            Handler(Looper.getMainLooper()).postDelayed({
                                updateStatusBar()
                            }, 50)
                            return true
                        }
                        "toggle_minimal_ui" -> {
                            KeyboardEventTracker.notifyKeyEvent(
                                keyCode,
                                event,
                                "KEY_DOWN",
                                origin = "ime_router",
                                outputKeyCode = null,
                                outputKeyCodeName = "toggle_minimal_ui"
                            )
                            toggleMinimalUi()
                            return true
                        }
                        "media_play_pause", "media_previous", "media_next" -> {
                            val mediaKeyCode = when (ctrlMapping.value) {
                                "media_play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                                "media_previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                                "media_next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                                else -> null
                            } ?: return callSuper()
                            KeyboardEventTracker.notifyKeyEvent(
                                keyCode,
                                event,
                                "KEY_DOWN",
                                origin = "ime_router",
                                outputKeyCode = mediaKeyCode,
                                outputKeyCodeName = KeyboardEventTracker.getOutputKeyCodeName(mediaKeyCode)
                            )
                            dispatchMediaKey(mediaKeyCode)
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
                                    origin = "ime_router",
                                    outputKeyCode = null,
                                    outputKeyCodeName = ctrlMapping.value
                                )
                                ic.performContextMenuAction(actionId)
                                return true
                            }
                            // Unknown action: let the target app handle the original Ctrl combo.
                            return callSuper()
                        }
                    }
                }
                "native_ctrl" -> return passThroughCtrlCombo()
                "keycode" -> {
                    val mappedKeyCode = when (ctrlMapping.value) {
                        "DPAD_UP" -> KeyEvent.KEYCODE_DPAD_UP
                        "DPAD_DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                        "DPAD_LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                        "DPAD_RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                        "DPAD_CENTER" -> KeyEvent.KEYCODE_DPAD_CENTER
                        "TAB" -> KeyEvent.KEYCODE_TAB
                        "MOVE_HOME" -> KeyEvent.KEYCODE_MOVE_HOME
                        "MOVE_END" -> KeyEvent.KEYCODE_MOVE_END
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
                            origin = "ime_router",
                            outputKeyCode = mappedKeyCode,
                            outputKeyCodeName = if (selectionShiftActive && mappedKeyCode.isSelectionAwareNavKey()) {
                                "shift_${KeyboardEventTracker.getOutputKeyCodeName(mappedKeyCode)}"
                            } else {
                                KeyboardEventTracker.getOutputKeyCodeName(mappedKeyCode)
                            }
                        )
                        sendModifiedKeyEvent(
                            inputConnection = ic,
                            keyCode = mappedKeyCode,
                            ctrl = false,
                            shift = selectionShiftActive && mappedKeyCode.isSelectionAwareNavKey()
                        )

                        if (mappedKeyCode in listOf(
                                KeyEvent.KEYCODE_DPAD_UP,
                                KeyEvent.KEYCODE_DPAD_DOWN,
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_DPAD_RIGHT,
                                KeyEvent.KEYCODE_MOVE_HOME,
                                KeyEvent.KEYCODE_MOVE_END,
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
                    // Unknown keycode mapping: fallback to app-native Ctrl handling.
                    return callSuper()
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
                        origin = "ime_router",
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
                        origin = "ime_router",
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

            // No explicit Pastiera mapping: preserve app-native Ctrl shortcuts (e.g. Ctrl+B/Ctrl+I).
            if (isPhysicalCtrlCombo && !ctrlLatchFromNavMode) {
                return passThroughCtrlCombo()
            }
            return callSuper()
        }

        return false
    }

    private fun dispatchMediaKey(keyCode: Int): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val eventTime = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
        return true
    }

    private fun deviceLayerDpadKeyCode(value: String?): Int? = when (value) {
        "__DPAD_UP__" -> KeyEvent.KEYCODE_DPAD_UP
        "__DPAD_DOWN__" -> KeyEvent.KEYCODE_DPAD_DOWN
        "__DPAD_LEFT__" -> KeyEvent.KEYCODE_DPAD_LEFT
        "__DPAD_RIGHT__" -> KeyEvent.KEYCODE_DPAD_RIGHT
        else -> null
    }

    private fun sendModifiedKeyEvent(
        inputConnection: InputConnection,
        keyCode: Int,
        ctrl: Boolean,
        shift: Boolean
    ): Boolean {
        val eventTime = SystemClock.uptimeMillis()
        val metaState =
            (if (ctrl) KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON else 0) or
                (if (shift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0)
        inputConnection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
        inputConnection.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0, metaState))
        return true
    }

    private fun Int.isSelectionAwareNavKey(): Boolean {
        return this == KeyEvent.KEYCODE_DPAD_UP ||
            this == KeyEvent.KEYCODE_DPAD_DOWN ||
            this == KeyEvent.KEYCODE_DPAD_LEFT ||
            this == KeyEvent.KEYCODE_DPAD_RIGHT ||
            this == KeyEvent.KEYCODE_MOVE_HOME ||
            this == KeyEvent.KEYCODE_MOVE_END ||
            this == KeyEvent.KEYCODE_PAGE_UP ||
            this == KeyEvent.KEYCODE_PAGE_DOWN
    }

    private fun resolveLayoutShortcutKeyCode(keyCode: Int): Int {
        val mappedChar = LayoutMappingRepository.getCharacter(keyCode, isShift = false)
            ?.lowercaseChar()
            ?: return keyCode

        return if (mappedChar in 'a'..'z') {
            KeyEvent.KEYCODE_A + (mappedChar - 'a')
        } else {
            keyCode
        }
    }

    private fun KeyEvent.withKeyCode(newKeyCode: Int): KeyEvent {
        if (keyCode == newKeyCode) return this
        return KeyEvent(
            downTime,
            eventTime,
            action,
            newKeyCode,
            repeatCount,
            metaState,
            deviceId,
            scanCode,
            flags,
            source
        )
    }

    private fun KeyEvent.withKeyCodeAndCtrl(newKeyCode: Int): KeyEvent {
        val ctrlMetaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (keyCode == newKeyCode && metaState == ctrlMetaState) return this
        return KeyEvent(
            downTime,
            eventTime,
            action,
            newKeyCode,
            repeatCount,
            ctrlMetaState,
            deviceId,
            scanCode,
            flags,
            source
        )
    }
}
