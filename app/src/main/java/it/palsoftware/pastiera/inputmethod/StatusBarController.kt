package it.palsoftware.pastiera.inputmethod

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Outline
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import androidx.core.content.ContextCompat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.RoundedCorner
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.TextView
import android.util.Log
import android.util.TypedValue
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.MainActivity
import it.palsoftware.pastiera.SymCustomizationActivity
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.SymPagesConfig
import it.palsoftware.pastiera.data.layout.LayoutFileStore
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import it.palsoftware.pastiera.data.mappings.AltModifierMappingResolver
import it.palsoftware.pastiera.data.variation.VariationRepository
import kotlin.math.max
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import it.palsoftware.pastiera.inputmethod.ui.ClipboardHistoryView
import it.palsoftware.pastiera.inputmethod.ui.EmojiPickerView
import it.palsoftware.pastiera.inputmethod.ui.HamburgerMenuView
import it.palsoftware.pastiera.inputmethod.ui.LedStatusView
import it.palsoftware.pastiera.inputmethod.ui.ModifierLedLayouts
import it.palsoftware.pastiera.inputmethod.ui.VariationBarView
import it.palsoftware.pastiera.inputmethod.ui.KeyboardThemeColors
import it.palsoftware.pastiera.inputmethod.suggestions.ui.FullSuggestionsBar
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonRegistry
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonPosition
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils.languageCode
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils.localeString
import it.palsoftware.pastiera.inputmethod.NotificationHelper
import it.palsoftware.pastiera.inputmethod.aospkeyboard.AospKeyboardView
import it.palsoftware.pastiera.inputmethod.aospkeyboard.SoftwareKeyboardLayoutTemplates
import it.palsoftware.pastiera.inputmethod.aospkeyboard.SoftwareKeyboardSymLabels
import android.content.res.AssetManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import it.palsoftware.pastiera.SettingsActivity

/**
 * Manages the status bar shown by the IME, handling view creation
 * and updating text/style based on modifier states.
 */
class StatusBarController(
    private val context: Context,
    private val mode: Mode = Mode.INPUT_VIEW,
    private val clipboardHistoryManager: it.palsoftware.pastiera.clipboard.ClipboardHistoryManager? = null,
    private val assets: AssetManager? = null,
    private val imeServiceClass: Class<*>? = null
) {
    enum class Mode {
        INPUT_VIEW,
        CANDIDATES_ONLY
    }

    // Listener for variation selection
    var onVariationSelectedListener: VariationButtonHandler.OnVariationSelectedListener? = null
        set(value) {
            field = value
            variationBarView?.onVariationSelectedListener = value
        }
    
    // Listener for cursor movement (to update variations)
    var onCursorMovedListener: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onCursorMovedListener = value
        }
    
    // Listener for speech recognition request
    var onSpeechRecognitionRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onSpeechRecognitionRequested = value
        }

    var onAddUserWord: ((String) -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onAddUserWord = value
        }

    var onAddUserWordSubstitutionRequested: ((String) -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onAddUserWordSubstitutionRequested = value
        }

    var onSuggestionCommitted: (() -> Unit)? = null

    var onHideSuggestion: ((String) -> Unit)? = null

    var onDeleteUserSuggestion: ((String) -> Unit)? = null

    var canDeleteUserSuggestion: ((String) -> Boolean)? = null
    
    var onLanguageSwitchRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onLanguageSwitchRequested = value
        }
    
    var onClipboardRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onClipboardRequested = value
        }
    
    var onEmojiPickerRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onEmojiPickerRequested = value
        }

    var onEmojiPageRequested: (() -> Unit)? = null
    
    var onSymbolsPageRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onSymbolsPageRequested = value
        }

    var onSoftwareKeyboardSymToggleRequested: (() -> Unit)? = null

    var onSymCloseRequested: (() -> Unit)? = null

    /**
     * Fired when the inline emoji picker toggles its search panel visibility. The host must
     * force a re-render because the panel state is internal to the picker and not part of the
     * status snapshot; with an active search the picker moves to a popup above the keyboard.
     */
    var onEmojiPickerSearchPanelToggled: ((Boolean) -> Unit)? = null

    var onUndoRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onUndoRequested = value
        }

    var onRedoRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onRedoRequested = value
        }

    var onSoftwareKeyboardKeyPressed: ((Int) -> Unit)? = null

    var onSoftwareKeyboardModifierKeyDown: ((Int) -> Boolean)? = null

    var onSoftwareKeyboardModifierKeyUp: ((Int) -> Boolean)? = null

    var onSoftwareKeyboardKeyStroke: ((Int, String) -> Boolean)? = null

    var onSoftwareKeyboardShiftTapped: (() -> Unit)? = null

    var onSoftwareKeyboardNonShiftInteraction: (() -> Unit)? = null

    var onSoftwareKeyboardTextInput: ((String, android.view.inputmethod.InputConnection?, StatusSnapshot) -> Boolean)? = null

    var onSoftwareKeyboardBoundaryTextInput: ((String, android.view.inputmethod.InputConnection?) -> Boolean)? = null

    var onHamburgerMenuRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onHamburgerMenuRequested = value
        }

    var onMinimalUiToggleRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onMinimalUiToggleRequested = value
        }

    var onSoftwareKeyboardModeToggleRequested: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView?.onSoftwareKeyboardModeToggleRequested = value
        }
    
    // Callback for speech recognition state changes (active/inactive)
    var onSpeechRecognitionStateChanged: ((Boolean) -> Unit)? = null
        set(value) {
            field = value
            // Note: VariationBarView doesn't need this directly, but we can add it if needed
        }
    
    fun invalidateStaticVariations() {
        variationBarView?.invalidateStaticVariations()
    }
    
    /**
     * Sets the microphone button active state.
     */
    fun setMicrophoneButtonActive(isActive: Boolean) {
        variationBarView?.setMicrophoneButtonActive(isActive)
        hamburgerMenuView?.setMicrophoneActive(isActive)
        fullSuggestionsBar?.setMicrophoneButtonActive(isActive)
    }
    
    /**
     * Updates the microphone button visual feedback based on audio level.
     * @param rmsdB The RMS audio level in decibels (typically -10 to 0)
     */
    fun updateMicrophoneAudioLevel(rmsdB: Float) {
        variationBarView?.updateMicrophoneAudioLevel(rmsdB)
        hamburgerMenuView?.updateMicrophoneAudioLevel(rmsdB)
        fullSuggestionsBar?.updateMicrophoneAudioLevel(rmsdB)
    }
    
    /**
     * Shows or hides the speech recognition hint message.
     * When showing, replaces the swipe hint with speech recognition message.
     */
    fun showSpeechRecognitionHint(show: Boolean) {
        variationBarView?.showSpeechRecognitionHint(show)
    }

    /**
     * Updates only the clipboard badge count without re-rendering variations.
     */
    fun updateClipboardCount(count: Int) {
        variationBarView?.updateClipboardCount(count)
        hamburgerMenuView?.updateClipboardCount(count)
        fullSuggestionsBar?.updateClipboardCount(count)
    }

    /**
     * Briefly highlights a suggestion slot using the original suggestion index
     * ordering (0=center, 1=right, 2=left). Used for trackpad/swipe commits.
     */
    fun flashSuggestionSlot(suggestionIndex: Int) {
        fullSuggestionsBar?.flashSuggestionAtIndex(suggestionIndex)
    }

    companion object {
        private const val TAG = "StatusBarController"
        private val DEFAULT_BACKGROUND = Color.parseColor("#000000")
        private const val TITAN_2_ELITE_CORNER_FALLBACK_RADIUS_DP = 50f
        private const val HARDWARE_SYM_KEY_HEIGHT_DP = 56f
    }

    data class StatusSnapshot(
        val capsLockEnabled: Boolean,
        val shiftPhysicallyPressed: Boolean,
        val shiftOneShot: Boolean,
        val ctrlLatchActive: Boolean,
        val ctrlPhysicallyPressed: Boolean,
        val ctrlOneShot: Boolean,
        val ctrlLatchFromNavMode: Boolean,
        val altLatchActive: Boolean,
        val altPhysicallyPressed: Boolean,
        val altOneShot: Boolean,
        val symPage: Int, // 0=disattivato, 1=pagina1 emoji, 2=pagina2 caratteri
        val clipboardOverlay: Boolean = false, // mostra la clipboard come view dedicata
        val clipboardCount: Int = 0, // numero di elementi in clipboard
        val variations: List<String> = emptyList(),
        val suggestions: List<String> = emptyList(),
        val addWordCandidate: String? = null,
        val lastInsertedChar: Char? = null,
        // Granular smart features flags
        val shouldDisableSuggestions: Boolean = false,
        val shouldDisableAutoCorrect: Boolean = false,
        val shouldDisableAutoCapitalize: Boolean = false,
        val shouldDisableDoubleSpaceToPeriod: Boolean = false,
        val shouldDisableVariations: Boolean = false,
        val isEmailField: Boolean = false,
        // UI latch flags for static variation bar layers.
        val shiftLayerLatched: Boolean = false,
        val altModifierLayerLatched: Boolean = false,
        val activeKeyboardLayoutName: String = "qwerty",
        val softwareSymPreviewLabels: Map<Int, String> = emptyMap(),
        val softwareSymPreviewTextLabels: Map<String, String> = emptyMap(),
        val softwareCtrlPreviewLabels: Map<Int, String> = emptyMap(),
        val softwareCtrlPreviewIconRes: Map<Int, Int> = emptyMap(),
        val softwareCtrlPreviewActive: Boolean = false,
        val softwareAltPreviewLabels: Map<Int, String> = emptyMap(),
        val softwareAltPreviewActive: Boolean = false,
        // Legacy flag for backward compatibility
        val shouldDisableSmartFeatures: Boolean = false
    ) {
        val navModeActive: Boolean
            get() = ctrlLatchActive && ctrlLatchFromNavMode
    }

    private var statusBarLayout: LinearLayout? = null
    private var modifiersContainer: LinearLayout? = null
    private var emojiMapTextView: TextView? = null
    private var symSurfaceContainer: FrameLayout? = null
    private var symSurfaceStack: LinearLayout? = null
    private var symSurfaceCloseButton: View? = null
    private var emojiKeyboardContainer: LinearLayout? = null
    private var emojiKeyboardHorizontalPaddingPx: Int = 0
    private var emojiKeyboardBottomPaddingPx: Int = 0
    private var clipboardHistoryView: ClipboardHistoryView? = null
    private var lastClipboardCountRendered: Int = -1
    private var emojiPickerView: EmojiPickerView? = null
    private var emojiPickerSearchPopup: PopupWindow? = null
    private var emojiPickerSearchPopupShowPending: Boolean = false
    private var softwareKeyboardView: AospKeyboardView? = null

    // removeAllViews() on the keyboard container dispatches a touch CANCEL to the
    // removed AospKeyboardView, whose modifier release synchronously re-runs update()
    // and would addView() a still-attached child. Swaps run under this guard and
    // re-entrant callers skip the reparenting entirely.
    private var swappingKeyboardContainerChildren: Boolean = false
    private var emojiKeyButtons: MutableList<View> = mutableListOf()
    private var lastSymPageRendered: Int = 0
    private var lastSymMappingsRendered: Map<Int, String>? = null
    private var lastInputConnectionUsed: android.view.inputmethod.InputConnection? = null
    private var wasSymActive: Boolean = false
    private var isTitan2Layout: Boolean = false

    // Trackpad debug
    private var trackpadDebugLaunched = false
    private var symShown: Boolean = false
    private var lastSymHeight: Int = 0
    private val defaultSymHeightPx: Int
        get() = dpToPx(600f) // fallback when nothing measured yet
    private val ledStatusView = LedStatusView(context)
    private val buttonRegistry = StatusBarButtonRegistry()
    private val variationBarView: VariationBarView? =
        VariationBarView(context, assets, imeServiceClass, buttonRegistry)
    private var variationsWrapper: View? = null
    private var hamburgerMenuView: HamburgerMenuView? = null
    private var pastierinaModeActive: Boolean = false
    private var fullSuggestionsBar: FullSuggestionsBar? = null
    private var expansionSuggestions: List<String> = emptyList()
    private var onExpansionSuggestionSelected: ((String) -> Unit)? = null
    private var baseLeftPadding: Int = 0
    private var baseRightPadding: Int = 0
    private var baseBottomPadding: Int = 0
    private var lastHamburgerInputConnection: android.view.inputmethod.InputConnection? = null
    private var lastInsetsLogSignature: String? = null
    private var softwareKeyboardShown: Boolean = false
    private var lastSoftwareKeyboardHeight: Int = 0
    private var lastSoftwareKeyboardSymPageRendered: Int = 0
    private var lastSoftwareKeyboardSymLayoutRendered: String? = null
    private var lastSoftwareKeyboardSymStyleRendered: AospKeyboardView.SoftwareLayoutStyle? = null
    
    init {
        onHamburgerMenuRequested = { toggleHamburgerMenu() }
    }

    private fun logImeOverlayInsetsIfEnabled(
        navBottom: Int,
        imeBottom: Int,
        cutoutBottom: Int,
        bottomInset: Int,
        appliedBottomPadding: Int
    ) {
        if (!SettingsManager.isImeOverlayDebugLoggingEnabled(context)) {
            return
        }

        val signature = "$navBottom|$imeBottom|$cutoutBottom|$bottomInset|$appliedBottomPadding"
        if (signature == lastInsetsLogSignature) {
            return
        }
        lastInsetsLogSignature = signature

        Log.d(
            TAG,
            "IME overlay insets: nav=$navBottom ime=$imeBottom cutout=$cutoutBottom " +
                "bottomInset=$bottomInset baseBottomPadding=$baseBottomPadding " +
                "appliedBottomPadding=$appliedBottomPadding"
        )
    }

    private fun activeInputStyle(): Pair<String?, String?> {
        val subtype = (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.currentInputMethodSubtype
        val locale = subtype?.localeString()
        val layout = if (assets != null) {
            AdditionalSubtypeUtils.resolveInputStyleLayout(assets, context, subtype)
        } else {
            SettingsManager.getKeyboardLayout(context)
        }
        return locale to layout
    }

    private fun hardwareTheme(): SettingsManager.KeyboardThemeSettings {
        val (locale, layout) = activeInputStyle()
        return SettingsManager.getEffectiveKeyboardTheme(
            context,
            SettingsManager.KeyboardThemeTarget.HARDWARE,
            locale,
            layout
        )
    }

    private fun softwareTheme(): SettingsManager.KeyboardThemeSettings {
        val (locale, layout) = activeInputStyle()
        return SettingsManager.getEffectiveKeyboardTheme(
            context,
            SettingsManager.KeyboardThemeTarget.SOFTWARE,
            locale,
            layout
        )
    }

    private fun activeThemeSettings(
        isFullSoftwareKeyboardMode: Boolean =
            mode == Mode.INPUT_VIEW &&
                SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) == SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
    ): SettingsManager.KeyboardThemeSettings =
        if (isFullSoftwareKeyboardMode) softwareTheme() else hardwareTheme()

    private fun activeThemeColors(
        isFullSoftwareKeyboardMode: Boolean =
            mode == Mode.INPUT_VIEW &&
                SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) == SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
    ): KeyboardThemeColors =
        activeThemeSettings(isFullSoftwareKeyboardMode).toKeyboardThemeColors()

    private fun applyKeyboardThemeOverrides(activeColors: KeyboardThemeColors) {
        statusBarLayout?.setBackgroundColor(activeColors.background)
        val roundedCorners = SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)
        val surfaceBackground = if (roundedCorners) Color.TRANSPARENT else activeColors.background
        symSurfaceStack?.setBackgroundColor(surfaceBackground)
        symSurfaceContainer?.setBackgroundColor(surfaceBackground)
        (statusBarLayout as? ImeChromeLayout)?.let { chrome ->
            val regularButtons = buttonRegistry.getEnabledButtons(context)
            val compactButtons = buttonRegistry.getEnabledPastierinaButtons(context)
            chrome.regularCornerColors = Pair(
                if (regularButtons.any { it.position == StatusBarButtonPosition.LEFT }) activeColors.statusBarButton else activeColors.normalKey,
                if (regularButtons.any { it.position == StatusBarButtonPosition.RIGHT }) activeColors.statusBarButton else activeColors.normalKey
            )
            chrome.compactCornerColors = Pair(
                if (compactButtons.any { it.position == StatusBarButtonPosition.LEFT }) activeColors.statusBarButton else activeColors.suggestion,
                if (compactButtons.any { it.position == StatusBarButtonPosition.RIGHT }) activeColors.statusBarButton else activeColors.suggestion
            )
            chrome.bottomFillColors = activeColors.normalKey to activeColors.suggestion
            chrome.expandedCloseColor = activeColors.statusBarButton
            chrome.expandedKeyHeightPx = hardwareSymKeyHeightPx(activeColors)
            chrome.invalidate()
        }
        emojiKeyboardContainer?.setBackgroundColor(activeColors.background)
        variationBarView?.themeOverride = activeColors
        ledStatusView.themeOverride = activeColors
        fullSuggestionsBar?.themeOverride = activeColors
        hamburgerMenuView?.themeOverride = activeColors
        clipboardHistoryView?.themeOverride = activeColors
        emojiPickerView?.themeOverride = activeColors
        applySurfaceCloseButtonTheme(activeColors)
    }

    private fun modifierLedLayout() = ModifierLedLayouts.resolve(
        physicalProfileOverride = SettingsManager.getPhysicalKeyboardProfileOverride(context),
        titan2EliteAutoDetected = DeviceSpecific.isTitan2EliteDevice()
    )

    private fun statusBarCallbacks(): StatusBarCallbacks =
        StatusBarCallbacks(
            onClipboardRequested = onClipboardRequested,
            onSpeechRecognitionRequested = onSpeechRecognitionRequested,
            onEmojiPickerRequested = onEmojiPickerRequested,
            onLanguageSwitchRequested = onLanguageSwitchRequested,
            onHamburgerMenuRequested = onHamburgerMenuRequested,
            onMinimalUiToggleRequested = { handleMinimalUiToggleFromMenu() },
            onSoftwareKeyboardModeToggleRequested = onSoftwareKeyboardModeToggleRequested,
            onOpenSettings = { openSettings() },
            onSymbolsPageRequested = onSymbolsPageRequested,
            onUndoRequested = onUndoRequested,
            onRedoRequested = onRedoRequested,
            onHapticFeedback = { NotificationHelper.triggerHapticFeedback(context) }
        )

    private fun applyChromeZOrder() {
        // Keep rows in normal child order. A positive translationZ casts a
        // full-width shadow at the chrome/keyboard boundary in software mode.
        fullSuggestionsBar?.ensureView()?.apply {
            elevation = 0f
            translationZ = 0f
        }
        variationsWrapper?.apply {
            elevation = 0f
            translationZ = 0f
        }
        symSurfaceContainer?.translationZ = 0f
        symSurfaceStack?.translationZ = 0f
        emojiKeyboardContainer?.translationZ = 0f
    }

    private fun ensureMainChildOrder() {
        val layout = statusBarLayout ?: return
        val suggestions = fullSuggestionsBar?.ensureView()
        val modifiers = modifiersContainer
        val variations = variationsWrapper
        val surface = symSurfaceContainer
        val children = listOf(suggestions, modifiers, variations, surface).filterNotNull()
        val alreadyOrdered = children.withIndex().all { (index, child) ->
            child.parent === layout && layout.indexOfChild(child) == index
        }
        if (alreadyOrdered) {
            return
        }
        children.forEach { child ->
            val parent = child.parent
            if (parent === layout) {
                layout.removeView(child)
            } else if (parent is ViewGroup) {
                parent.removeView(child)
            }
        }
        children.forEach { layout.addView(it) }
    }

    private fun SettingsManager.KeyboardThemeSettings.toAospThemeOverride(): AospKeyboardView.ThemeOverride =
        AospKeyboardView.ThemeOverride(
            background = background,
            divider = divider,
            normalKey = normalKey,
            specialKey = specialKey,
            textAndIcons = textAndIcons,
            ledInactive = ledInactive,
            ledActive = ledActive,
            ledLocked = ledLocked,
            accent = accent,
            keyPopup = keyPopup,
            keyPopupSelected = keyPopupSelected,
            keyPopupStyle = keyPopupStyle,
            keyPopupAttached = keyPopupAttached,
            keyPopupTailEnabled = keyPopupTailEnabled,
            keyPreviewAfterLongPress = keyPreviewAfterLongPress,
            keyAlternatesPopupEnabled = keyAlternatesPopupEnabled,
            keyCornerRadiusRatio = keyCornerRadiusRatio,
            keyHeightScale = keyHeightScale,
            numberRowHeightScale = numberRowHeightScale,
            keyWidthScale = keyWidthScale,
            rowGapScale = rowGapScale,
            distributeHorizontalSpacing = distributeHorizontalSpacing,
            ortholinear = ortholinear
        )

    fun setPastierinaModeActive(active: Boolean) {
        if (pastierinaModeActive == active) {
            return
        }
        pastierinaModeActive = active
        updatePastierinaModeState()
        if (active) {
            variationBarView?.hideImmediate()
            hideHamburgerMenu()
        }
    }

    fun isPastierinaModeActive(): Boolean = pastierinaModeActive

    fun dismissEmojiPickerPopup() {
        emojiPickerSearchPopupShowPending = false
        emojiPickerSearchPopup?.let { popup ->
            if (popup.isShowing) popup.dismiss()
            popup.contentView = null
        }
    }

    fun getLayout(): LinearLayout? = statusBarLayout

    fun refreshWindowInsets() {
        statusBarLayout?.let { ViewCompat.requestApplyInsets(it) }
    }

    fun collapseLayout() {
        val layout = statusBarLayout ?: return
        hideHamburgerMenu()
        layout.visibility = View.GONE
        layout.layoutParams = (layout.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0
        )).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = 0
        }
        layout.requestLayout()
        (layout.parent as? View)?.requestLayout()
    }

    fun expandLayout() {
        val layout = statusBarLayout ?: return
        restoreLayoutHeight(layout)
        layout.requestLayout()
        (layout.parent as? View)?.requestLayout()
    }

    fun getOrCreateLayout(emojiMapText: String = ""): LinearLayout {
        if (statusBarLayout == null) {
            statusBarLayout = ImeChromeLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = true
                clipToPadding = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(DEFAULT_BACKGROUND)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
            }
            statusBarLayout?.let { layout ->
                baseLeftPadding = layout.paddingLeft
                baseRightPadding = layout.paddingRight
                baseBottomPadding = layout.paddingBottom
                ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
                    // Use getInsetsIgnoringVisibility to get stable insets for navigation and gesture areas
                    // We should NOT include IME insets as that would add padding when the keyboard itself is shown
                    val navAndGestures = insets.getInsetsIgnoringVisibility(
                        WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.systemGestures()
                    )
                    val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
                    val useTitan2EliteRoundedCornerInsets =
                        SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)
                    val platformInsets = insets.toWindowInsets()
                    val bottomLeftRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        platformInsets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0
                    } else {
                        0
                    }
                    val bottomRightRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        platformInsets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0
                    } else {
                        0
                    }
                    val fallbackCornerRadius = if (useTitan2EliteRoundedCornerInsets) {
                        dpToPx(TITAN_2_ELITE_CORNER_FALLBACK_RADIUS_DP)
                    } else {
                        0
                    }
                    val bottomInset = max(navAndGestures.bottom, cutout.bottom)
                    val appliedBottomPadding = baseBottomPadding + bottomInset
                    (view as? ImeChromeLayout)?.bottomCornerRadiiPx =
                        if (useTitan2EliteRoundedCornerInsets) {
                            Pair(
                                bottomLeftRadius.takeIf { it > 0 } ?: fallbackCornerRadius,
                                bottomRightRadius.takeIf { it > 0 } ?: fallbackCornerRadius
                            )
                        } else {
                            null
                        }
                    ledStatusView.bottomCornerRadiiPx = (view as? ImeChromeLayout)?.bottomCornerRadiiPx
                    view.updatePadding(
                        left = baseLeftPadding,
                        right = baseRightPadding,
                        bottom = appliedBottomPadding
                    )
                    logImeOverlayInsetsIfEnabled(
                        navBottom = navAndGestures.bottom,
                        imeBottom = 0,
                        cutoutBottom = cutout.bottom,
                        bottomInset = bottomInset,
                        appliedBottomPadding = appliedBottomPadding
                    )
                    insets
                }
            }

            // Container for modifier indicators (horizontal, left-aligned).
            // Add left padding to avoid the IME collapse button.
            val leftPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                64f, 
                context.resources.displayMetrics
            ).toInt()
            val horizontalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                16f, 
                context.resources.displayMetrics
            ).toInt()
            val verticalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                8f, 
                context.resources.displayMetrics
            ).toInt()
            
            modifiersContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(leftPadding, verticalPadding, horizontalPadding, verticalPadding)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                visibility = View.GONE
            }

            // Container for emoji grid (when SYM is active) - placed at the bottom
            val emojiKeyboardHorizontalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                8f,
                context.resources.displayMetrics
            ).toInt()
            val emojiKeyboardBottomPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12f, // Padding in basso per evitare i controlli IME
                context.resources.displayMetrics
            ).toInt()
            emojiKeyboardHorizontalPaddingPx = emojiKeyboardHorizontalPadding
            emojiKeyboardBottomPaddingPx = emojiKeyboardBottomPadding
            
            emojiKeyboardContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                // No top padding, only horizontal and bottom
                setPadding(emojiKeyboardHorizontalPadding, 0, emojiKeyboardHorizontalPadding, emojiKeyboardBottomPadding)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                visibility = View.GONE
            }
            
            // Keep the TextView for backward compatibility (hidden)
            emojiMapTextView = TextView(context).apply {
                visibility = View.GONE
            }

            variationsWrapper = variationBarView?.ensureView()
            attachHamburgerMenu(variationsWrapper)
            ledStatusView.layout = modifierLedLayout()
            val ledStrip = ledStatusView.ensureView()
            ledStatusView.onLongPressListener = { handleMinimalUiToggleFromMenu() }

            fullSuggestionsBar = FullSuggestionsBar(
                context,
                buttonRegistry,
                callbacksProvider = { statusBarCallbacks() }
            )
            if (assets != null && imeServiceClass != null) {
                fullSuggestionsBar?.setSubtypeCyclingParams(assets, imeServiceClass)
            }

            symSurfaceStack = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = true
                clipToPadding = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(DEFAULT_BACKGROUND)
                addView(emojiKeyboardContainer)
                addView(ledStrip)
            }
            symSurfaceCloseButton = createSurfaceCloseButton()
            symSurfaceContainer = FrameLayout(context).apply {
                clipChildren = true
                clipToPadding = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(DEFAULT_BACKGROUND)
                addView(symSurfaceStack)
                addView(symSurfaceCloseButton)
            }

            statusBarLayout?.apply {
                addView(fullSuggestionsBar?.ensureView())
                addView(modifiersContainer)
                variationsWrapper?.let { addView(it) }
                addView(symSurfaceContainer)
            }
            (statusBarLayout as? ImeChromeLayout)?.apply {
                surfaceView = symSurfaceContainer
                indicatorView = ledStrip
                expandedSurfaceView = emojiKeyboardContainer
                compactStatusRow = fullSuggestionsBar?.ensureView()
                expandedCloseButton = symSurfaceCloseButton
            }
            applyChromeZOrder()
            applyAccessibilitySecondRowReadPreference()
            statusBarLayout?.let { ViewCompat.requestApplyInsets(it) }
        } else if (emojiMapText.isNotEmpty()) {
            emojiMapTextView?.text = emojiMapText
        }
        return statusBarLayout!!
    }

    private fun restoreLayoutHeight(layout: View) {
        val params = layout.layoutParams ?: return
        if (params.height != 0) {
            return
        }
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        layout.layoutParams = params
    }

    private fun attachHamburgerMenu(wrapper: View?) {
        val frame = wrapper as? FrameLayout ?: return
        val menu = hamburgerMenuView ?: HamburgerMenuView(context, buttonRegistry).also { hamburgerMenuView = it }
        menu.attachTo(frame)
    }

    private fun activeHamburgerWrapper(): View? {
        return variationsWrapper
    }

    private fun showHamburgerMenu() {
        if (hamburgerMenuView == null) {
            attachHamburgerMenu(activeHamburgerWrapper())
        } else {
            attachHamburgerMenu(activeHamburgerWrapper())
        }
        val menu = hamburgerMenuView ?: return
        val callbacks = statusBarCallbacks().copy(onHamburgerMenuRequested = null)
        menu.show(callbacks) { hideHamburgerMenu() }
    }

    private fun hideHamburgerMenu() {
        hamburgerMenuView?.hide()
        fullSuggestionsBar?.hideHamburgerMenu()
    }

    fun resetSuggestionActionMode() {
        fullSuggestionsBar?.resetActionMode()
    }

    fun showExpansionSuggestions(suggestions: List<String>, onSelected: (String) -> Unit) {
        expansionSuggestions = suggestions.take(3)
        onExpansionSuggestionSelected = onSelected
    }

    fun clearExpansionSuggestions() {
        expansionSuggestions = emptyList()
        onExpansionSuggestionSelected = null
    }

    fun cancelSoftwareKeyboardTouchState() {
        // The software keyboard can also sit below the emoji picker while its search is
        // active, so prefer the cached instance over the container's first child.
        (softwareKeyboardView ?: emojiKeyboardContainer?.getChildAt(0) as? AospKeyboardView)
            ?.cancelActiveTouchState()
    }

    private fun toggleHamburgerMenu() {
        if (hamburgerMenuView?.isVisible() == true) {
            hideHamburgerMenu()
        } else {
            showHamburgerMenu()
        }
    }

    private fun updatePastierinaModeState() {
        hamburgerMenuView?.setMinimalUiActive(pastierinaModeActive)
        fullSuggestionsBar?.setMinimalUiActive(pastierinaModeActive)
    }

    private fun handleMinimalUiToggleFromMenu() {
        onMinimalUiToggleRequested?.invoke()
        if (!pastierinaModeActive) {
            hideHamburgerMenu()
        }
    }

    fun handleBackPressed(): Boolean {
        if (fullSuggestionsBar?.isHamburgerMenuVisible() == true || hamburgerMenuView?.isVisible() == true) {
            hideHamburgerMenu()
            return true
        }
        return false
    }

    fun handleEmojiPickerSearchKeyDown(
        event: KeyEvent?,
        ctrlActive: Boolean,
        resolveTypedText: ((KeyEvent) -> String?)? = null
    ): Boolean {
        if (event == null) return false
        return emojiPickerView?.handleSearchKeyDown(event, ctrlActive, resolveTypedText) == true
    }

    fun shouldConsumeEmojiPickerSearchKeyUp(event: KeyEvent?, ctrlActive: Boolean): Boolean {
        if (event == null) return false
        return emojiPickerView?.shouldConsumeSearchKeyUp(event, ctrlActive) == true
    }

    fun disableEmojiPickerSearchInputCapture() {
        emojiPickerView?.disableSearchInputCapture()
    }

    fun isEmojiPickerSearchInputActive(): Boolean {
        return emojiPickerView?.isSearchInputActive() == true
    }

    fun createEmojiPickerSearchInputConnection(): android.view.inputmethod.InputConnection? {
        return emojiPickerView?.createSearchInputConnection()
    }

    private fun openSettings() {
        try {
            val intent = Intent(context, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Settings", e)
        }
    }

    private fun launchTrackpadDebug() {
        if (!trackpadDebugLaunched) {
            trackpadDebugLaunched = true
            val intent = Intent(context, it.palsoftware.pastiera.TrackpadDebugActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * Ensures the layout is created before updating.
     * This is important for candidates view which may not have been created yet.
     */
    private fun ensureLayoutCreated(emojiMapText: String = ""): LinearLayout? {
        return statusBarLayout ?: getOrCreateLayout(emojiMapText)
    }
    
    /**
     * Recursively finds a clickable view at the given coordinates in the view hierarchy.
     * Coordinates are relative to the parent view.
     */
    private fun findClickableViewAt(parent: View, x: Float, y: Float): View? {
        if (parent !is ViewGroup) {
            // Single view: check if it's clickable and contains the point
            if (x >= 0 && x < parent.width &&
                y >= 0 && y < parent.height &&
                parent.isClickable) {
                return parent
            }
            return null
        }
        
        // For ViewGroup, check children first (they are on top)
        // Iterate in reverse to check topmost views first
        for (i in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(i)
            if (child.visibility == View.VISIBLE) {
                val childLeft = child.left.toFloat()
                val childTop = child.top.toFloat()
                val childRight = child.right.toFloat()
                val childBottom = child.bottom.toFloat()
                
                if (x >= childLeft && x < childRight &&
                    y >= childTop && y < childBottom) {
                    // Point is inside this child, recurse with relative coordinates
                    val childX = x - childLeft
                    val childY = y - childTop
                    val found = findClickableViewAt(child, childX, childY)
                    if (found != null) {
                        return found
                    }
                    
                    // If child itself is clickable, return it
                    if (child.isClickable) {
                        return child
                    }
                }
            }
        }
        
        // If no child was found and parent is clickable, return parent
        if (parent.isClickable) {
            return parent
        }
        
        return null
    }
    
    /**
     * Crea un indicatore per un modificatore (deprecato, mantenuto per compatibilità).
     */
    private fun createModifierIndicator(text: String, isActive: Boolean): TextView {
        val dp8 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            8f, 
            context.resources.displayMetrics
        ).toInt()
        val dp6 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            6f, 
            context.resources.displayMetrics
        ).toInt()
        
        return TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(if (isActive) Color.WHITE else Color.argb(180, 255, 255, 255))
            gravity = Gravity.CENTER
            setPadding(dp6, dp8, dp6, dp8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp8 // Margine a destra tra gli indicatori
            }
        }
    }

    private fun updateMenuBarModifierIndicators(
        container: LinearLayout,
        snapshot: StatusSnapshot,
        show: Boolean,
        theme: KeyboardThemeColors
    ) {
        container.removeAllViews()
        if (!show) {
            container.visibility = View.GONE
            return
        }

        val indicators = buildMenuBarModifierIndicators(snapshot)
        if (indicators.isEmpty()) {
            container.visibility = View.GONE
            return
        }

        indicators.forEachIndexed { index, indicator ->
            val view = when (indicator) {
                is MenuBarModifierIndicator.Icon -> ImageView(context).apply {
                    setImageResource(indicator.resId)
                    setColorFilter(if (indicator.locked) theme.ledLocked else theme.ledActive)
                    scaleType = ImageView.ScaleType.CENTER
                    contentDescription = indicator.description
                }
                is MenuBarModifierIndicator.Text -> TextView(context).apply {
                    text = indicator.label
                    textSize = 12f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(if (indicator.locked) theme.ledLocked else theme.ledActive)
                    contentDescription = indicator.description
                }
            }
            container.addView(
                view,
                LinearLayout.LayoutParams(dpToPx(26f), dpToPx(26f)).apply {
                    if (index != indicators.lastIndex) marginEnd = dpToPx(2f)
                }
            )
        }
        container.visibility = View.VISIBLE
    }

    private fun buildMenuBarModifierIndicators(snapshot: StatusSnapshot): List<MenuBarModifierIndicator> {
        val indicators = mutableListOf<MenuBarModifierIndicator>()
        val shiftLocked = snapshot.capsLockEnabled
        val shiftActive = (snapshot.shiftPhysicallyPressed || snapshot.shiftOneShot) && !shiftLocked
        if (shiftLocked || shiftActive) {
            indicators.add(
                MenuBarModifierIndicator.Icon(
                    resId = if (shiftLocked) R.drawable.shift_filled_24 else R.drawable.shift_24,
                    locked = shiftLocked,
                    description = "Shift"
                )
            )
        }

        val ctrlLocked = snapshot.ctrlLatchActive
        val ctrlActive = (snapshot.ctrlPhysicallyPressed || snapshot.ctrlOneShot) && !ctrlLocked
        if (ctrlLocked || ctrlActive) {
            indicators.add(
                MenuBarModifierIndicator.Icon(
                    resId = R.drawable.keyboard_control_key_24,
                    locked = ctrlLocked,
                    description = "Ctrl"
                )
            )
        }

        val altLocked = snapshot.altLatchActive
        val altActive = (snapshot.altPhysicallyPressed || snapshot.altOneShot) && !altLocked
        if (altLocked || altActive) {
            indicators.add(
                MenuBarModifierIndicator.Icon(
                    resId = R.drawable.keyboard_option_key_24,
                    locked = altLocked,
                    description = "Alt"
                )
            )
        }

        if (snapshot.symPage > 0) {
            indicators.add(
                MenuBarModifierIndicator.Text(
                    label = "SYM",
                    locked = snapshot.symPage == 2,
                    description = "SYM"
                )
            )
        }

        return indicators
    }

    private sealed class MenuBarModifierIndicator(open val locked: Boolean, open val description: String) {
        data class Icon(
            val resId: Int,
            override val locked: Boolean,
            override val description: String
        ) : MenuBarModifierIndicator(locked, description)

        data class Text(
            val label: String,
            override val locked: Boolean,
            override val description: String
        ) : MenuBarModifierIndicator(locked, description)
    }
    
    /**
     * Updates the clipboard history view inline in the keyboard container.
     */
    private fun updateClipboardView(
        inputConnection: android.view.inputmethod.InputConnection? = null,
        softwareKeyboardHeight: Int? = null
    ) {
        val manager = clipboardHistoryManager ?: return
        val container = emojiKeyboardContainer ?: return
        // Clipboard page should be edge-to-edge; remove the SYM container side padding.
        container.setPadding(0, 0, 0, 0)

        // Reuse the same view to avoid flicker caused by removeAllViews()/recreate on each status update.
        val view = clipboardHistoryView ?: ClipboardHistoryView(context, manager) {
            onSymCloseRequested?.invoke()
        }.also { clipboardHistoryView = it }
        view.themeOverride = (if (
            mode == Mode.INPUT_VIEW &&
                SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) == SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        ) softwareTheme() else hardwareTheme()).toKeyboardThemeColors()
        if (view.parent !== container && !swappingKeyboardContainerChildren) {
            withKeyboardContainerSwap {
                container.removeAllViews()
                emojiKeyButtons.clear()
                container.addView(view)
            }
        }
        view.configureSoftwareKeyboardMode(softwareKeyboardHeight)
        view.configureRoundedLayout(
            SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)
        )
        view.setInputConnection(inputConnection)

        // Refresh only when needed (data changed), otherwise keep the list stable.
        val count = manager.getHistorySize()
        if (count != lastClipboardCountRendered) {
            manager.prepareClipboardHistory()
            view.refresh()
            lastClipboardCountRendered = count
        }
        lastSymPageRendered = 3
    }

    /**
     * Updates the emoji picker view inline in the keyboard container.
     */
    private fun updateEmojiPickerView(
        snapshot: StatusSnapshot,
        inputConnection: android.view.inputmethod.InputConnection? = null,
        softwareKeyboardHeight: Int? = null
    ) {
        val container = emojiKeyboardContainer ?: return
        // Emoji picker page should be edge-to-edge; remove the SYM container side padding.
        container.setPadding(0, 0, 0, 0)

        // Reuse the same view to avoid flicker caused by removeAllViews()/recreate on each status update.
        val view = emojiPickerView ?: EmojiPickerView(context) {
            onSymCloseRequested?.invoke()
        }.also { emojiPickerView = it }
        view.onSearchPanelVisibilityChanged = { visible ->
            onEmojiPickerSearchPanelToggled?.invoke(visible)
        }
        view.themeOverride = (if (
            mode == Mode.INPUT_VIEW &&
                SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) == SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        ) softwareTheme() else hardwareTheme()).toKeyboardThemeColors()
        val wasDetachedFromHost = view.parent == null
        val needsContainerAttachment = view.parent !== container
        val pickerShownAboveSoftwareKeyboard =
            softwareKeyboardHeight != null &&
                view.isSearchPanelShowing() &&
                showEmojiPickerSearchPopup(
                    container,
                    view,
                    snapshot,
                    inputConnection,
                    softwareKeyboardHeight
                )
        if (!pickerShownAboveSoftwareKeyboard) {
            dismissEmojiPickerSearchPopup(view)
            if (!swappingKeyboardContainerChildren && (needsContainerAttachment || container.childCount != 1)) {
                withKeyboardContainerSwap {
                    view.reorderingWithinContainer {
                        if (container.getChildAt(0) === view) {
                            // Unstack without detaching the picker: only remove the
                            // views stacked below it (e.g. the software keyboard).
                            while (container.childCount > 1) {
                                container.removeViewAt(container.childCount - 1)
                            }
                        } else {
                            container.removeAllViews()
                            emojiKeyButtons.clear()
                            container.addView(view)
                        }
                    }
                }
            }
            view.configureSoftwareKeyboardMode(
                heightPx = softwareKeyboardHeight,
                onKeyboardLayoutRequested = if (softwareKeyboardHeight != null) onEmojiPickerRequested else null
            )
        } else {
            // Search active: the main IME window keeps its normal keyboard height. The compact
            // picker is rendered in a separate window above it, avoiding a Surface resize.
            view.configureSoftwareKeyboardMode(
                heightPx = null,
                onKeyboardLayoutRequested = null
            )
        }
        val roundedControls = SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context) && !pickerShownAboveSoftwareKeyboard
        val colors = activeThemeColors()
        val iconSize = if (pastierinaModeActive) {
            (dpToPx(36f * colors.suggestionsHeightScale.coerceIn(0.65f, 1.6f)) - dpToPx(4f)) * 0.64f
        } else minOf(dpToPx(24f).toFloat(), hardwareSymKeyHeightPx(colors) * 0.48f)
        view.configureRoundedControls(roundedControls, hardwareSymKeyHeightPx(colors), iconSize)
        (statusBarLayout as? ImeChromeLayout)?.expandedPickerButtons = if (roundedControls) view.edgeControls else null
        view.setInputConnection(inputConnection)

        // Only scroll to top when view is just added (first open or switching pages)
        // Don't scroll if view is already in container (user is browsing)
        if (lastSymPageRendered != 4) {
            view.refresh() // First time or switching from another page
        } else if (wasDetachedFromHost) {
            view.scrollToTop() // View was just added (happens when reopening after being removed)
        }
        lastSymPageRendered = 4
    }

    private fun showEmojiPickerSearchPopup(
        container: ViewGroup,
        picker: EmojiPickerView,
        snapshot: StatusSnapshot,
        inputConnection: android.view.inputmethod.InputConnection?,
        softwareKeyboardHeight: Int
    ): Boolean {
        val layout = statusBarLayout ?: return false
        val keyboardView = ensureSoftwareKeyboardViewInstance(container)
        val keyboardHeight = softwareKeyboardHeight.takeIf { it > 0 }
            ?: measureSoftwareKeyboardDesiredHeight(keyboardView, layout).takeIf { it > 0 }
            ?: return false
        configureSoftwareKeyboard(keyboardView, snapshot, inputConnection, null)
        if (swappingKeyboardContainerChildren) {
            keyboardView.visibility = View.VISIBLE
            return true
        }

        if (container.childCount != 1 || container.getChildAt(0) !== keyboardView) {
            withKeyboardContainerSwap {
                picker.reorderingWithinContainer {
                    if (picker.parent === container) {
                        container.removeView(picker)
                    }
                    container.removeAllViews()
                    emojiKeyButtons.clear()
                    container.addView(
                        keyboardView,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            keyboardHeight
                        )
                    )
                }
            }
        }
        keyboardView.visibility = View.VISIBLE

        val popup = emojiPickerSearchPopup ?: PopupWindow(context).apply {
            isFocusable = false
            isTouchable = true
            isOutsideTouchable = false
            isClippingEnabled = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 0f
            animationStyle = 0
        }.also { emojiPickerSearchPopup = it }
        if (popup.contentView !== picker) {
            popup.contentView = picker
        }
        popup.width = context.resources.displayMetrics.widthPixels
        popup.height = EmojiPickerView.configuredHeightPx(context)
        if (!popup.isShowing && !emojiPickerSearchPopupShowPending) {
            emojiPickerSearchPopupShowPending = true
            layout.post {
                emojiPickerSearchPopupShowPending = false
                if (picker.isSearchPanelShowing() && !popup.isShowing && layout.isAttachedToWindow) {
                    popup.showAtLocation(layout, Gravity.BOTTOM, 0, keyboardHeight)
                }
            }
        }
        return true
    }

    private fun dismissEmojiPickerSearchPopup(picker: EmojiPickerView) {
        emojiPickerSearchPopupShowPending = false
        val popup = emojiPickerSearchPopup ?: return
        if (popup.isShowing) {
            picker.reorderingWithinContainer {
                popup.dismiss()
            }
        }
        if (popup.contentView === picker) {
            popup.contentView = null
        }
    }

    /**
     * Aggiorna la griglia emoji/caratteri con le mappature SYM.
     * @param symMappings Le mappature da visualizzare
     * @param page La pagina attiva (1=emoji, 2=caratteri)
     * @param inputConnection L'input connection per inserire caratteri quando si clicca sui pulsanti
     */
    private fun updateEmojiKeyboard(symMappings: Map<Int, String>, page: Int, inputConnection: android.view.inputmethod.InputConnection? = null) {
        val container = emojiKeyboardContainer ?: return
        // Restore default padding for emoji/symbols pages.
        val roundedCorners = SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)
        val sidePadding = if (roundedCorners) 0 else emojiKeyboardHorizontalPaddingPx
        container.setPadding(sidePadding, 0, sidePadding, 0)
        val inputConnectionChanged = lastInputConnectionUsed != inputConnection
        val inputConnectionBecameAvailable = lastInputConnectionUsed == null && inputConnection != null
        if (lastSymPageRendered == page && lastSymMappingsRendered == symMappings && !inputConnectionChanged && !inputConnectionBecameAvailable) {
            return
        }
        
        // Rimuovi tutti i tasti esistenti
        container.removeAllViews()
        emojiKeyButtons.clear()
        
        // Definizione delle righe della tastiera
        val keyboardRows = listOf(
            listOf(android.view.KeyEvent.KEYCODE_Q, android.view.KeyEvent.KEYCODE_W, android.view.KeyEvent.KEYCODE_E, 
                   android.view.KeyEvent.KEYCODE_R, android.view.KeyEvent.KEYCODE_T, android.view.KeyEvent.KEYCODE_Y, 
                   android.view.KeyEvent.KEYCODE_U, android.view.KeyEvent.KEYCODE_I, android.view.KeyEvent.KEYCODE_O, 
                   android.view.KeyEvent.KEYCODE_P),
            listOf(android.view.KeyEvent.KEYCODE_A, android.view.KeyEvent.KEYCODE_S, android.view.KeyEvent.KEYCODE_D, 
                   android.view.KeyEvent.KEYCODE_F, android.view.KeyEvent.KEYCODE_G, android.view.KeyEvent.KEYCODE_H, 
                   android.view.KeyEvent.KEYCODE_J, android.view.KeyEvent.KEYCODE_K, android.view.KeyEvent.KEYCODE_L),
            listOf(android.view.KeyEvent.KEYCODE_Z, android.view.KeyEvent.KEYCODE_X, android.view.KeyEvent.KEYCODE_C, 
                   android.view.KeyEvent.KEYCODE_V, android.view.KeyEvent.KEYCODE_B, android.view.KeyEvent.KEYCODE_N, 
                   android.view.KeyEvent.KEYCODE_M)
        )
        
        val keyLabels = mapOf(
            android.view.KeyEvent.KEYCODE_Q to "Q", android.view.KeyEvent.KEYCODE_W to "W", android.view.KeyEvent.KEYCODE_E to "E",
            android.view.KeyEvent.KEYCODE_R to "R", android.view.KeyEvent.KEYCODE_T to "T", android.view.KeyEvent.KEYCODE_Y to "Y",
            android.view.KeyEvent.KEYCODE_U to "U", android.view.KeyEvent.KEYCODE_I to "I", android.view.KeyEvent.KEYCODE_O to "O",
            android.view.KeyEvent.KEYCODE_P to "P", android.view.KeyEvent.KEYCODE_A to "A", android.view.KeyEvent.KEYCODE_S to "S",
            android.view.KeyEvent.KEYCODE_D to "D", android.view.KeyEvent.KEYCODE_F to "F", android.view.KeyEvent.KEYCODE_G to "G",
            android.view.KeyEvent.KEYCODE_H to "H", android.view.KeyEvent.KEYCODE_J to "J", android.view.KeyEvent.KEYCODE_K to "K",
            android.view.KeyEvent.KEYCODE_L to "L", android.view.KeyEvent.KEYCODE_Z to "Z", android.view.KeyEvent.KEYCODE_X to "X",
            android.view.KeyEvent.KEYCODE_C to "C", android.view.KeyEvent.KEYCODE_V to "V", android.view.KeyEvent.KEYCODE_B to "B",
            android.view.KeyEvent.KEYCODE_N to "N", android.view.KeyEvent.KEYCODE_M to "M"
        )
        
        val keySpacing = if (roundedCorners) 0 else TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        
        // Calcola la larghezza fissa dei tasti basata sulla prima riga (10 caselle)
        val maxKeysInRow = 10 // Prima riga ha 10 caselle
        val screenWidth = context.resources.displayMetrics.widthPixels
        val horizontalPadding = if (roundedCorners) 0 else TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            8f * 2, // padding sinistro + destro
            context.resources.displayMetrics
        ).toInt()
        val availableWidth = screenWidth - horizontalPadding
        val totalSpacing = keySpacing * (maxKeysInRow - 1)
        val fixedKeyWidth = (availableWidth - totalSpacing) / maxKeysInRow
        
        val keyHeight = hardwareSymKeyHeightPx()
        
        // Crea ogni riga della tastiera
        for ((rowIndex, row) in keyboardRows.withIndex()) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = if (isTitan2Layout) Gravity.START else Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (rowIndex < keyboardRows.size - 1) {
                        bottomMargin = keySpacing
                    }
                }
            }
            
            if (isTitan2Layout) {
                // Ortholinear layout for Titan 2
                when (rowIndex) {
                    0 -> { // Row 1: Q W E R T Y U I O P (10 keys)
                        for ((index, keyCode) in row.withIndex()) {
                            addKeyToRow(rowLayout, keyCode, symMappings, fixedKeyWidth, keyHeight, keySpacing, page, inputConnection, index == row.size - 1)
                        }
                    }
                    1 -> { // Row 2: A S D F G H J K L (9 keys) -> Add placeholder at the end to make it 10
                        for ((index, keyCode) in row.withIndex()) {
                            addKeyToRow(rowLayout, keyCode, symMappings, fixedKeyWidth, keyHeight, keySpacing, page, inputConnection, false)
                        }
                        rowLayout.addView(View(context), LinearLayout.LayoutParams(fixedKeyWidth, keyHeight))
                    }
                    2 -> { // Row 3: Z X C V [Editor] [Globe] B N M [Close]
                        // Z X C V (4 keys)
                        for (i in 0..3) {
                            addKeyToRow(rowLayout, row[i], symMappings, fixedKeyWidth, keyHeight, keySpacing, page, inputConnection, false)
                        }
                        
                        // Editor button (left part of spacebar area)
                        val editorButton = createSymEditorButton(keyHeight, fixedKeyWidth, page)
                        rowLayout.addView(editorButton)
                        rowLayout.addView(View(context), LinearLayout.LayoutParams(keySpacing, keyHeight))
                        
                        // Globe Button (right part of spacebar area)
                        val selectionButton = createKeyboardSelectionButton(keyHeight, fixedKeyWidth)
                        rowLayout.addView(selectionButton)
                        rowLayout.addView(View(context), LinearLayout.LayoutParams(keySpacing, keyHeight))
                        
                        // B N M (3 keys)
                        for (i in 4..6) {
                            addKeyToRow(rowLayout, row[i], symMappings, fixedKeyWidth, keyHeight, keySpacing, page, inputConnection, false)
                        }

                        rowLayout.addView(View(context), LinearLayout.LayoutParams(fixedKeyWidth, keyHeight))
                    }
                }
                container.addView(rowLayout)
                continue
            }
            
            // Default non-Titan 2 layout logic...
            // (The rest of the loop for non-Titan 2 remains the same)
            
            // Per la terza riga, aggiungi placeholder con emoji picker button a sinistra
            if (rowIndex == 2) {
                val leftPlaceholder = createPlaceholderWithEmojiPickerButton(keyHeight, page)
                rowLayout.addView(leftPlaceholder, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    marginEnd = keySpacing
                })
            }
            
            for ((index, keyCode) in row.withIndex()) {
                val label = keyLabels[keyCode] ?: ""
                val content = symMappings[keyCode] ?: ""
                
                val keyButton = createEmojiKeyButton(label, content, keyHeight, page)
                emojiKeyButtons.add(keyButton)
                keyButton.isLongClickable = true
                keyButton.setOnLongClickListener {
                    openSymCustomization(page = page, keyCode = keyCode, openPicker = true)
                    true
                }
                
                // Aggiungi click listener per rendere il pulsante touchabile
                if (content.isNotEmpty() && inputConnection != null) {
                    keyButton.isClickable = true
                    keyButton.isFocusable = true
                    keyButton.setOnClickListener {
                        commitTouchSymbolAfterCloseIfNeeded(keyButton, inputConnection, content)
                    }
                }
                
                // Usa larghezza fissa invece di weight
                rowLayout.addView(keyButton, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    // Aggiungi margine solo se non è l'ultimo tasto della riga
                    if (index < row.size - 1) {
                        marginEnd = keySpacing
                    }
                })
            }
            
            // Per la terza riga, aggiungi placeholder con icona matita a destra
            if (rowIndex == 2) {
                val rightPlaceholder = createPlaceholderWithPencilButton(keyHeight, page)
                rowLayout.addView(rightPlaceholder, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    marginStart = keySpacing
                })
                rowLayout.addView(View(context), LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    marginStart = keySpacing
                })
            }
            
            container.addView(rowLayout)
        }

        // Cache what was rendered to avoid rebuilding on each status refresh
        lastSymPageRendered = page
        lastSymMappingsRendered = HashMap(symMappings)
        lastInputConnectionUsed = inputConnection
    }

    private inline fun <T> withKeyboardContainerSwap(block: () -> T): T {
        swappingKeyboardContainerChildren = true
        try {
            return block()
        } finally {
            swappingKeyboardContainerChildren = false
        }
    }

    private fun updateSoftwareKeyboard(
        snapshot: StatusSnapshot,
        inputConnection: android.view.inputmethod.InputConnection? = null,
        symMappings: Map<Int, String>? = null
    ) {
        val container = emojiKeyboardContainer ?: return
        container.setPadding(0, 0, 0, emojiKeyboardBottomPaddingPx)
        val keyboardView = obtainSoftwareKeyboardView(container)
        configureSoftwareKeyboard(keyboardView, snapshot, inputConnection, symMappings)
        (keyboardView.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            if (
                params.width != ViewGroup.LayoutParams.MATCH_PARENT ||
                params.height != 0 ||
                params.weight != 1f
            ) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = 0
                params.weight = 1f
                keyboardView.layoutParams = params
            }
        }
        softwareKeyboardShown = true
        lastSymPageRendered = 0
        lastInputConnectionUsed = inputConnection
    }

    /**
     * Returns the cached software keyboard view, placing it as the container's only child
     * (weight-based) so it fills the whole surface. Used by the plain software keyboard page.
     */
    private fun obtainSoftwareKeyboardView(container: ViewGroup): AospKeyboardView {
        val view = ensureSoftwareKeyboardViewInstance(container)
        if (swappingKeyboardContainerChildren || (container.childCount == 1 && container.getChildAt(0) === view)) {
            view.visibility = View.VISIBLE
            return view
        }
        withKeyboardContainerSwap {
            container.removeAllViews()
            emojiKeyButtons.clear()
            container.addView(
                view,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        }
        view.visibility = View.VISIBLE
        return view
    }

    private fun ensureSoftwareKeyboardViewInstance(container: ViewGroup): AospKeyboardView {
        return softwareKeyboardView ?: AospKeyboardView(context).also { view ->
            var parent: ViewGroup? = container
            while (parent != null) {
                parent.clipChildren = false
                parent.clipToPadding = false
                parent = parent.parent as? ViewGroup
            }
            softwareKeyboardView = view
        }
    }

    private fun softwareKeyboardSearchTarget(snapshot: StatusSnapshot): EmojiPickerView? {
        if (snapshot.symPage != 4) return null
        val picker = emojiPickerView ?: return null
        if (!picker.isSearchInputActive()) return null
        return picker
    }

    private fun configureSoftwareKeyboard(
        keyboardView: AospKeyboardView,
        snapshot: StatusSnapshot,
        inputConnection: android.view.inputmethod.InputConnection?,
        symMappings: Map<Int, String>?
    ) {
        val uppercase = snapshot.capsLockEnabled || snapshot.shiftPhysicallyPressed || snapshot.shiftOneShot
        val layoutName = resolveSoftwareKeyboardLayoutName(snapshot)
        keyboardView.visibility = View.VISIBLE
        keyboardView.listener = object : AospKeyboardView.Listener {
            override fun onText(text: String) {
                onSoftwareKeyboardNonShiftInteraction?.invoke()
                val searchTarget = softwareKeyboardSearchTarget(snapshot)
                if (searchTarget != null && searchTarget.handleSearchTextInput(text)) {
                    return
                }
                val handled = onSoftwareKeyboardTextInput?.invoke(text, inputConnection, snapshot) == true
                if (!handled) {
                    inputConnection?.commitText(text, 1)
                }
            }

            override fun onBackspace() {
                onSoftwareKeyboardNonShiftInteraction?.invoke()
                val searchTarget = softwareKeyboardSearchTarget(snapshot)
                if (searchTarget != null && searchTarget.handleSearchBackspace()) {
                    return
                }
                inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }

            override fun onEnter() {
                onSoftwareKeyboardNonShiftInteraction?.invoke()
                val searchTarget = softwareKeyboardSearchTarget(snapshot)
                if (searchTarget != null) {
                    searchTarget.commitTopSearchResultAndClose()
                    return
                }
                inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }

            override fun onShift() {
                onSoftwareKeyboardShiftTapped?.invoke()
            }

            override fun onSymbols() {
                onSoftwareKeyboardNonShiftInteraction?.invoke()
                prepareSoftwareKeyboardForSymbolTransition()
                onSoftwareKeyboardSymToggleRequested?.invoke()
            }

            override fun onCtrl() {
                onSoftwareKeyboardNonShiftInteraction?.invoke()
                inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT))
                inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT))
            }

            override fun onLanguageSwitch() {
                onSoftwareKeyboardNonShiftInteraction?.invoke()
                onLanguageSwitchRequested?.invoke()
            }

            override fun onCursorMove(delta: Int) {
                onSoftwareKeyboardNonShiftInteraction?.invoke()
                val connection = inputConnection ?: return
                val moved = if (delta < 0) {
                    TextSelectionHelper.moveCursorLeft(connection)
                } else {
                    TextSelectionHelper.moveCursorRight(connection)
                }
                if (moved) {
                    onCursorMovedListener?.invoke()
                }
            }

            override fun onKeyPressSound(keyCode: Int) {
                onSoftwareKeyboardKeyPressed?.invoke(keyCode)
            }

            override fun onModifierKeyDown(keyCode: Int): Boolean {
                return onSoftwareKeyboardModifierKeyDown?.invoke(keyCode) == true
            }

            override fun onModifierKeyUp(keyCode: Int): Boolean {
                return onSoftwareKeyboardModifierKeyUp?.invoke(keyCode) == true
            }

            override fun onKeyStroke(keyCode: Int, text: String): Boolean {
                return onSoftwareKeyboardKeyStroke?.invoke(keyCode, text) == true
            }

            override fun onSymbolText(text: String): Boolean {
                val connection = inputConnection ?: return false
                commitTouchSymbolAfterCloseIfNeeded(keyboardView, connection, text)
                return true
            }

            override fun onSymbolLongPress(keyCode: Int): Boolean {
                val page = snapshot.symPage
                if (page !in 1..2) {
                    return false
                }
                openSymCustomization(page = page, keyCode = keyCode, openPicker = true)
                return true
            }
        }
        keyboardView.layoutName = layoutName
        keyboardView.layoutStyle = softwareKeyboardLayoutStyle()
        keyboardView.includeNumberRow = SettingsManager.getSoftwareKeyboardNumberRowEnabled(context)
        keyboardView.nearestKeyTouchEnabled =
            SettingsManager.getSoftwareKeyboardNearestKeyTouchEnabled(context)
        keyboardView.shifted = uppercase
        keyboardView.shiftLocked = snapshot.capsLockEnabled
        keyboardView.ctrlOneShot = snapshot.ctrlOneShot
        keyboardView.ctrlLocked = snapshot.ctrlLatchActive || snapshot.ctrlLatchFromNavMode
        keyboardView.ctrlPressed = snapshot.ctrlPhysicallyPressed
        keyboardView.ctrlPreviewActive = snapshot.softwareCtrlPreviewActive
        keyboardView.altOneShot = snapshot.altOneShot
        keyboardView.altLocked = snapshot.altLatchActive
        keyboardView.altPressed = snapshot.altPhysicallyPressed
        keyboardView.altPreviewActive = snapshot.softwareAltPreviewActive
        keyboardView.symPageActive = snapshot.symPage in listOf(1, 2, 5)
        val activeSymProjection = if (snapshot.symPage in listOf(1, 2, 5) && symMappings != null) {
            SoftwareKeyboardSymLabels.project(
                page = snapshot.symPage,
                rows = SoftwareKeyboardLayoutTemplates.rowTemplateFor(layoutName, softwareKeyboardLayoutStyle()),
                symMappings = symMappings,
                layoutName = layoutName
            )
        } else {
            SoftwareKeyboardSymLabels.Projection(emptyMap(), emptyMap())
        }
        keyboardView.symPageLabels = activeSymProjection.contentByKeyCode
        keyboardView.symPageTextLabels = activeSymProjection.contentByBaseText
        keyboardView.symPreviewLabels = snapshot.softwareSymPreviewLabels
        keyboardView.symPreviewTextLabels = snapshot.softwareSymPreviewTextLabels
        keyboardView.ctrlPreviewLabels = snapshot.softwareCtrlPreviewLabels
        keyboardView.ctrlPreviewIconRes = snapshot.softwareCtrlPreviewIconRes
        keyboardView.altPreviewLabels = snapshot.softwareAltPreviewLabels
        val symKeySpec = nextSoftwareSymKeySpec(snapshot.symPage)
        keyboardView.symbolsLabel = symKeySpec.label
        keyboardView.symbolsIconRes = symKeySpec.iconRes
        keyboardView.spacebarLabel = buildSoftwareKeyboardSpacebarLabel(snapshot)
        keyboardView.longPressTimeoutMs = SettingsManager.getLongPressThreshold(context)
        keyboardView.longPressAlternatesProvider = { output ->
            resolveSoftwareKeyboardLongPressAlternates(output, snapshot)
        }
        keyboardView.longPressHintProvider = { output ->
            resolveSoftwareKeyboardAltLongPressHint(output, snapshot)
        }
        keyboardView.longPressLayerAlternatesProvider = { output ->
            resolveSoftwareKeyboardLongPressLayerAlternates(output, snapshot)
        }
        keyboardView.longPressLayerPopupBelowKey =
            SettingsManager.getSoftwareKeyboardLongPressLayerPopupBelowKey(context)
        keyboardView.themeOverride = softwareTheme().toAospThemeOverride()
    }

    private fun softwareKeyboardLayoutStyle(): AospKeyboardView.SoftwareLayoutStyle =
        when (SettingsManager.getSoftwareKeyboardLayoutStyle(context)) {
            SettingsManager.SoftwareKeyboardLayoutStyle.COMPACT -> AospKeyboardView.SoftwareLayoutStyle.COMPACT
            SettingsManager.SoftwareKeyboardLayoutStyle.EXTENDED_ISO -> AospKeyboardView.SoftwareLayoutStyle.EXTENDED_ISO
            SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ANSI -> AospKeyboardView.SoftwareLayoutStyle.FULL_ANSI
            SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ISO -> AospKeyboardView.SoftwareLayoutStyle.FULL_ISO
        }

    private fun prepareSoftwareKeyboardForSymbolTransition() {
        val activeColors = softwareTheme()
        emojiKeyboardContainer?.apply {
            setBackgroundColor(activeColors.background)
        }
        // Also covers the stacked layout where the keyboard is not the first container child.
        (softwareKeyboardView ?: emojiKeyboardContainer?.getChildAt(0) as? AospKeyboardView)
            ?.visibility = View.INVISIBLE
    }

    private data class SoftwareSymKeySpec(
        val label: String,
        val iconRes: Int? = null
    )

    private fun nextSoftwareSymKeySpec(currentPage: Int): SoftwareSymKeySpec {
        val pageValues = SettingsManager.getSymPagesConfig(context).enabledOrderedPages().mapNotNull { page ->
            when (page) {
                it.palsoftware.pastiera.SymPagesConfig.PAGE_EMOJI -> 1
                it.palsoftware.pastiera.SymPagesConfig.PAGE_SYMBOLS -> 2
                it.palsoftware.pastiera.SymPagesConfig.PAGE_CLIPBOARD -> 3
                it.palsoftware.pastiera.SymPagesConfig.PAGE_EMOJI_PICKER -> 4
                it.palsoftware.pastiera.SymPagesConfig.PAGE_DEVICE -> 5
                else -> null
            }
        }
        if (pageValues.isEmpty()) {
            return SoftwareSymKeySpec("SYM")
        }
        val nextPage = if (currentPage == 0) {
            pageValues.firstOrNull()
        } else {
            val currentIndex = pageValues.indexOf(currentPage)
            when {
                currentIndex < 0 -> pageValues.firstOrNull()
                currentIndex == pageValues.lastIndex -> 0
                else -> pageValues[currentIndex + 1]
            }
        }
        return when (nextPage) {
            1 -> SoftwareSymKeySpec("", R.drawable.ic_emoji_emotions_24)
            2 -> SoftwareSymKeySpec("", R.drawable.ic_emoji_symbols_24)
            3 -> SoftwareSymKeySpec("", R.drawable.ic_content_paste_24)
            4 -> SoftwareSymKeySpec("", R.drawable.ic_emoji_emotions_24)
            else -> SoftwareSymKeySpec("ABC")
        }
    }

    private fun resolveSoftwareKeyboardLongPressAlternates(output: String, snapshot: StatusSnapshot): List<String> {
        if (output.isEmpty()) return emptyList()
        val baseChar = output.first()
        val keyCode = SoftwareKeyboardSymLabels.keyCodeForChar(
            baseChar,
            resolveSoftwareKeyboardLayoutName(snapshot)
        ) ?: return emptyList()
        return when (SettingsManager.getLongPressModifier(context)) {
            "alt" -> AltModifierMappingResolver.resolve(context.assets, context)[keyCode]?.let(::listOf).orEmpty()
            "shift" -> listOf(output.uppercase()).filter { it != output }
            "sym", "sym_symbols", "sym_emoji" -> {
                val map = softwareKeyboardLongPressSymMappings(SettingsManager.resolveLongPressSymPage(context))
                map[keyCode]?.let(::listOf).orEmpty()
            }
            "variations" -> {
                val variations = VariationRepository.loadVariations(
                    assets = context.assets,
                    context = context,
                    activeLayoutName = resolveSoftwareKeyboardLayoutName(snapshot)
                )
                variations[baseChar] ?: variations[baseChar.lowercaseChar()] ?: emptyList()
            }
            else -> emptyList()
        }
    }

    private fun resolveSoftwareKeyboardAltLongPressHint(output: String, snapshot: StatusSnapshot): String? {
        if (output.isEmpty()) return null
        val keyCode = SoftwareKeyboardSymLabels.keyCodeForChar(
            output.first(),
            resolveSoftwareKeyboardLayoutName(snapshot)
        ) ?: return null
        return AltModifierMappingResolver.resolve(context.assets, context)[keyCode]
    }

    private fun resolveSoftwareKeyboardLongPressLayerAlternates(
        output: String,
        snapshot: StatusSnapshot
    ): List<AospKeyboardView.LongPressLayerAlternative> {
        if (!SettingsManager.getSoftwareKeyboardLongPressLayerPopupEnabled(context) || output.isEmpty()) {
            return emptyList()
        }
        val keyCode = SoftwareKeyboardSymLabels.keyCodeForChar(
            output.first(),
            resolveSoftwareKeyboardLayoutName(snapshot)
        ) ?: return emptyList()
        val alternatives = mutableListOf<AospKeyboardView.LongPressLayerAlternative>()
        AltModifierMappingResolver.resolve(context.assets, context)[keyCode]?.takeIf { it.isNotBlank() }?.let { alt ->
            alternatives += AospKeyboardView.LongPressLayerAlternative(label = alt, output = alt)
        }
        SettingsManager.getSymPagesConfig(context)
            .normalizedOrder()
            .filter { it == SymPagesConfig.PAGE_EMOJI || it == SymPagesConfig.PAGE_SYMBOLS }
            .forEach { page ->
                val mapping = when (page) {
                    SymPagesConfig.PAGE_EMOJI -> softwareKeyboardLongPressSymMappings(1)
                    SymPagesConfig.PAGE_SYMBOLS -> softwareKeyboardLongPressSymMappings(2)
                    else -> emptyMap()
                }
                mapping[keyCode]?.takeIf { it.isNotBlank() }?.let { value ->
                    alternatives += AospKeyboardView.LongPressLayerAlternative(label = value, output = value)
                }
            }
        return alternatives.distinctBy { it.output }
    }

    private fun softwareKeyboardLongPressSymMappings(page: Int): Map<Int, String> {
        return when (page) {
            1 -> SettingsManager.getSymMappings(context).takeIf { it.isNotEmpty() }
                ?: KeyMappingLoader.loadSymKeyMappings(context.assets)
            2 -> SettingsManager.getSymMappingsPage2(context).takeIf { it.isNotEmpty() }
                ?: KeyMappingLoader.loadSymKeyMappingsPage2(context.assets)
            else -> emptyMap()
        }
    }

    private fun updateSoftwareSymbolKeyboard(
        symMappings: Map<Int, String>,
        snapshot: StatusSnapshot,
        inputConnection: android.view.inputmethod.InputConnection? = null
    ) {
        val page = snapshot.symPage
        val container = emojiKeyboardContainer ?: return
        container.setPadding(0, 0, 0, emojiKeyboardBottomPaddingPx)
        val inputConnectionChanged = lastInputConnectionUsed != inputConnection
        val layoutName = resolveSoftwareKeyboardLayoutName(snapshot)
        val layoutStyle = softwareKeyboardLayoutStyle()
        if (
            lastSymPageRendered == page &&
            lastSoftwareKeyboardSymPageRendered == page &&
            lastSoftwareKeyboardSymLayoutRendered == layoutName &&
            lastSoftwareKeyboardSymStyleRendered == layoutStyle &&
            lastSymMappingsRendered == symMappings &&
            !inputConnectionChanged
        ) {
            return
        }

        container.removeAllViews()
        emojiKeyButtons.clear()

        val rows = SoftwareKeyboardLayoutTemplates.rowTemplateFor(layoutName, layoutStyle)
        val softwareSymContentByChar = SoftwareKeyboardSymLabels.buildContentByChar(
            page = page,
            rows = rows,
            symMappings = symMappings,
            layoutName = layoutName
        )
        val keySpacing = dpToPx(2f)
        val keyHeight = ((lastSoftwareKeyboardHeight.takeIf { it > 0 } ?: dpToPx(200f)) - emojiKeyboardBottomPaddingPx) / 4
        val screenWidth = context.resources.displayMetrics.widthPixels
        val columns = maxOf(10, rows.maxOf { it.length }, rows.getOrNull(2)?.length?.plus(2) ?: 0)
        val fixedKeyWidth = ((screenWidth - keySpacing * (columns - 1)) / columns).coerceAtLeast(1)

        rows.forEachIndexed { rowIndex, row ->
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    keyHeight
                )
            }
            if (rowIndex == 2) {
                rowLayout.addView(createSoftwareSymbolControl("", keyHeight, fixedKeyWidth, iconRes = R.drawable.shift_24) {
                    // Keep page stable; shifted symbol layers can be added later without touching PKB SYM.
                }, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply { marginEnd = keySpacing })
            }
            row.forEachIndexed { index, labelChar ->
                val label = labelChar.toString().uppercase()
                val content = softwareSymContentByChar[labelChar] ?: softwareSymbolFallback(labelChar)
                val keyButton = createEmojiKeyButton(label, content, keyHeight, page)
                if (content.isNotEmpty() && inputConnection != null) {
                    keyButton.isClickable = true
                    keyButton.isFocusable = true
                    keyButton.setOnClickListener {
                        commitTouchSymbolAfterCloseIfNeeded(keyButton, inputConnection, content)
                    }
                }
                rowLayout.addView(keyButton, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    if (index < row.length - 1) marginEnd = keySpacing
                })
            }
            if (rowIndex == 2) {
                rowLayout.addView(createSoftwareSymbolControl("", keyHeight, fixedKeyWidth, iconRes = R.drawable.backspace_24) {
                    inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                    inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                }, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply { marginStart = keySpacing })
            }
            container.addView(rowLayout)
        }

        val row4 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyHeight)
        }
        val symKeySpec = nextSoftwareSymKeySpec(page)
        row4.addView(createSoftwareSymbolControl(symKeySpec.label, keyHeight, fixedKeyWidth, iconRes = symKeySpec.iconRes) {
            onSoftwareKeyboardSymToggleRequested?.invoke()
        }, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply { marginEnd = keySpacing })
        row4.addView(createSoftwareSymbolControl("", keyHeight, fixedKeyWidth, iconRes = R.drawable.keyboard_control_key_24) {
            sendSoftwareCtrlTap(inputConnection)
        }, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply { marginEnd = keySpacing })
        row4.addView(createSoftwareSymbolControl(",", keyHeight, fixedKeyWidth) {
            inputConnection?.commitText(",", 1)
        }, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply { marginEnd = keySpacing })
        row4.addView(createSoftwareSymbolSpaceControl(buildSoftwareKeyboardSpacebarLabel(snapshot), keyHeight, inputConnection), LinearLayout.LayoutParams(fixedKeyWidth * 4, keyHeight).apply { marginEnd = keySpacing })
        row4.addView(createSoftwareSymbolControl(".", keyHeight, fixedKeyWidth) {
            inputConnection?.commitText(".", 1)
        }, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply { marginEnd = keySpacing })
        row4.addView(createSoftwareSymbolControl("", keyHeight, fixedKeyWidth, iconRes = R.drawable.keyboard_control_key_24) {
            sendSoftwareCtrlTap(inputConnection)
        }, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply { marginEnd = keySpacing })
        row4.addView(createSoftwareSymbolControl("", keyHeight, fixedKeyWidth, iconRes = R.drawable.keyboard_return_24) {
            inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight))
        container.addView(row4)

        lastSymPageRendered = page
        lastSoftwareKeyboardSymPageRendered = page
        lastSoftwareKeyboardSymLayoutRendered = layoutName
        lastSoftwareKeyboardSymStyleRendered = layoutStyle
        lastSymMappingsRendered = HashMap(symMappings)
        lastInputConnectionUsed = inputConnection
    }

    private fun softwareSymbolFallback(char: Char): String =
        when {
            char.isLetterOrDigit() -> ""
            else -> char.toString()
        }

    private fun createSoftwareSymbolControl(
        label: String,
        height: Int,
        width: Int,
        iconRes: Int? = null,
        onClick: () -> Unit
    ): TextView {
        val theme = activeThemeColors(isFullSoftwareKeyboardMode = true)
        return TextView(context).apply {
            text = label
            setTextColor(theme.textAndIcons)
            textSize = if (label.length <= 1) 22f else 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            iconRes?.let { resId ->
                val icon = ContextCompat.getDrawable(context, resId)?.mutate()
                icon?.setTint(theme.textAndIcons)
                val iconSize = (height * 0.46f).toInt().coerceAtLeast(dpToPx(18f))
                icon?.setBounds(0, 0, iconSize, iconSize)
                setCompoundDrawables(null, icon, null, null)
            }
            background = GradientDrawable().apply {
                setColor(theme.statusBarButton)
                setStroke(dpToPx(1f), theme.divider)
                cornerRadius = dpToPx(6f).toFloat()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(width, height)
        }
    }

    private fun createSoftwareSymbolSpaceControl(
        label: String,
        height: Int,
        inputConnection: android.view.inputmethod.InputConnection?
    ): TextView {
        val view = createSoftwareSymbolControl(label, height, 0, onClick = {
            inputConnection?.commitText(" ", 1)
        })
        var downX = 0f
        var lastX = 0f
        var moved = false
        val step = dpToPx(18f).toFloat()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var longPressTriggered = false
        val longPressRunnable = Runnable {
            longPressTriggered = true
            onLanguageSwitchRequested?.invoke()
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    lastX = event.x
                    moved = false
                    longPressTriggered = false
                    handler.postDelayed(longPressRunnable, SettingsManager.getLongPressThreshold(context))
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val delta = event.x - lastX
                    if (kotlin.math.abs(delta) >= step) {
                        handler.removeCallbacks(longPressRunnable)
                        moved = true
                        val steps = (delta / step).toInt()
                        repeat(kotlin.math.abs(steps).coerceAtMost(4)) {
                            val connection = inputConnection ?: return@repeat
                            val didMove = if (steps > 0) {
                                TextSelectionHelper.moveCursorRight(connection)
                            } else {
                                TextSelectionHelper.moveCursorLeft(connection)
                            }
                            if (didMove) {
                                onCursorMovedListener?.invoke()
                            }
                        }
                        lastX += steps * step
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (!moved && !longPressTriggered && kotlin.math.abs(event.x - downX) < step) {
                        inputConnection?.commitText(" ", 1)
                    }
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    true
                }
                else -> false
            }
        }
        return view
    }

    private fun sendSoftwareCtrlTap(inputConnection: android.view.inputmethod.InputConnection?) {
        inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT))
        inputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT))
    }

    private fun buildSoftwareKeyboardSpacebarLabel(snapshot: StatusSnapshot): String {
        val language = try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.currentInputMethodSubtype?.languageCode()
                ?.uppercase()
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        } ?: "??"
        val layout = when (resolveSoftwareKeyboardLayoutName(snapshot)) {
            "german_multitap_qwertz" -> "QWERTZ DE"
            "qwertz" -> "QWERTZ"
            "azerty" -> "AZERTY"
            else -> "QWERTY"
        }
        return "$language · $layout"
    }

    private fun resolveSoftwareKeyboardLayoutName(snapshot: StatusSnapshot): String {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val subtype = imm?.currentInputMethodSubtype
            AdditionalSubtypeUtils.resolveActiveLayout(context.assets, context, subtype)
        } catch (e: Exception) {
            snapshot.activeKeyboardLayoutName
        }
    }

    /**
     * Crea un placeholder trasparente per allineare le righe.
     */
    private fun createPlaceholderButton(height: Int): View {
        return FrameLayout(context).apply {
            background = null // Trasparente
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
            isClickable = false
            isFocusable = false
        }
    }
    
    /**
     * Crea un placeholder con icona emoji per aprire l'emoji picker (symPage 4).
     */
    private fun createPlaceholderWithEmojiPickerButton(height: Int, page: Int): View {
        val theme = activeThemeColors()
        val placeholder = FrameLayout(context).apply {
            setPadding(0, 0, 0, 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
        }
        
        placeholder.background = null
        
        val iconSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            28f,
            context.resources.displayMetrics
        ).toInt()
        
        val button = ImageView(context).apply {
            background = null
            setImageResource(if (page == 1) R.drawable.ic_emoji_symbols_24 else R.drawable.ic_sentiment_satisfied_24)
            setColorFilter(theme.textAndIcons)
            contentDescription = context.getString(R.string.status_bar_button_emoji_description)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            maxWidth = iconSize
            maxHeight = iconSize
            layoutParams = FrameLayout.LayoutParams(
                iconSize,
                iconSize
            ).apply {
                gravity = Gravity.CENTER
            }
            isClickable = true
            isFocusable = true
        }
        
        button.setOnClickListener {
            if (page == 1) {
                onSymbolsPageRequested?.invoke()
            } else {
                onEmojiPageRequested?.invoke()
            }
        }
        
        placeholder.addView(button)
        return placeholder
    }
    
    /**
     * Crea un placeholder con icona matita per aprire la schermata di personalizzazione SYM.
     */
    private fun createPlaceholderWithPencilButton(height: Int, page: Int): View {
        val theme = activeThemeColors()
        val placeholder = FrameLayout(context).apply {
            setPadding(0, 0, 0, 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
        }
        
        // Background trasparente
        placeholder.background = null
        
        // Dimensione icona più grande
        val iconSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            28f, // Aumentata per maggiore visibilità
            context.resources.displayMetrics
        ).toInt()
        
        val button = ImageView(context).apply {
            background = null
            setImageResource(R.drawable.ic_edit_24)
            setColorFilter(theme.textAndIcons)
            contentDescription = context.getString(R.string.sym_customization_button)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            maxWidth = iconSize
            maxHeight = iconSize
            layoutParams = FrameLayout.LayoutParams(
                iconSize,
                iconSize
            ).apply {
                gravity = Gravity.CENTER
            }
            isClickable = true
            isFocusable = true
        }
        
        button.setOnClickListener {
            openSymCustomization(page = page, keyCode = null, openPicker = false)
        }
        
        placeholder.addView(button)
        return placeholder
    }

    private fun createSymEditorButton(height: Int, width: Int, page: Int): View {
        val theme = activeThemeColors()
        val button = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(width, height)
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.sym_customization_button)
        }
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_edit_24)
            setColorFilter(theme.textAndIcons)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val padding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12f,
                context.resources.displayMetrics
            ).toInt()
            setPadding(padding, padding, padding, padding)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        button.addView(icon)
        button.setOnClickListener {
            openSymCustomization(page = page, keyCode = null, openPicker = false)
        }
        return button
    }

    private fun openSymCustomization(page: Int, keyCode: Int?, openPicker: Boolean) {
        val prefs = context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
        val currentSymPage = prefs.getInt("current_sym_page", 0)
        if (currentSymPage > 0) {
            SettingsManager.setPendingRestoreSymPage(context, currentSymPage)
        }

        val intent = if (page == 5) {
            Intent(context, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(
                    SettingsActivity.EXTRA_DESTINATION,
                    SettingsActivity.DESTINATION_DEVICE_SYM_LAYER_EDITOR
                )
            }
        } else Intent(context, SymCustomizationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(SymCustomizationActivity.EXTRA_INITIAL_PAGE, page)
            keyCode?.let { putExtra(SymCustomizationActivity.EXTRA_INITIAL_KEY_CODE, it) }
            putExtra(SymCustomizationActivity.EXTRA_OPEN_PICKER, openPicker)
            putExtra(SymCustomizationActivity.EXTRA_RETURN_AFTER_PICKER, openPicker && keyCode != null)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'apertura della schermata di personalizzazione SYM", e)
        }
    }
    
    /**
     * Crea un tasto della griglia emoji/caratteri.
     * @param label La lettera del tasto
     * @param content L'emoji o carattere da mostrare
     * @param height L'altezza del tasto
     * @param page La pagina attiva (1=emoji, 2=caratteri)
     */
    private fun createEmojiKeyButton(label: String, content: String, height: Int, page: Int): View {
        val theme = activeThemeColors()
        val keyLayout = FrameLayout(context).apply {
            setPadding(0, 0, 0, 0) // Nessun padding per permettere all'emoji di occupare tutto lo spazio
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
            contentDescription = buildSymKeyContentDescription(label, content)
        }
        
        // Background del tasto con angoli leggermente arrotondati
        val cornerRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            6f, // Angoli leggermente arrotondati
            context.resources.displayMetrics
        )
        val drawable = GradientDrawable().apply {
            setColor(theme.normalKey)
            val roundedCorners = SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)
            setCornerRadius(if (roundedCorners) 0f else cornerRadius)
            if (!roundedCorners) setStroke(dpToPx(1f), theme.divider)
        }
        keyLayout.background = drawable
        
        // Emoji/carattere deve occupare tutto il tasto, centrata
        // Calcola textSize in base all'altezza disponibile (convertendo da pixel a sp)
        val heightInDp = height / context.resources.displayMetrics.density
        val roundedCorners = SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)
        val contentTextSize = if (page == 2 || roundedCorners) {
            // Per caratteri unicode, usa una dimensione più piccola
            (heightInDp * 0.5f)
        } else {
            // Per emoji, usa la dimensione normale
            (heightInDp * 0.75f)
        }
        
        val contentText = TextView(context).apply {
            text = content
            textSize = contentTextSize // textSize è in sp
            gravity = Gravity.CENTER
            if (roundedCorners) setTextColor(theme.textAndIcons)
            // Per pagina 2 (caratteri), rendi bianco e in grassetto
            if (page == 2) {
                setTextColor(theme.textAndIcons)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            // Larghezza e altezza per occupare tutto lo spazio disponibile
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }
        
        // Label (lettera) - posizionato in basso a destra, davanti all'emoji
        val labelPadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            2f, // Pochissimo margine
            context.resources.displayMetrics
        ).toInt()
        
        val labelText = TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(theme.textAndIcons)
            gravity = Gravity.END or Gravity.BOTTOM
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                rightMargin = labelPadding
                bottomMargin = labelPadding
            }
        }
        
        // Aggiungi prima il contenuto (dietro) poi il testo (davanti)
        keyLayout.addView(contentText)
        keyLayout.addView(labelText)
        
        return keyLayout
    }

    private fun buildSymKeyContentDescription(label: String, content: String): String {
        if (content.isBlank()) {
            return label
        }
        return context.getString(R.string.sym_key_content_description, label, content)
    }

    private fun createHideKeyboardButton(height: Int, width: Int): View {
        val button = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(width, height)
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.close)
        }
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_close_24)
            setColorFilter(hardwareTheme().textAndIcons)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = createCloseButtonBackground(hardwareTheme().toKeyboardThemeColors())
            val padding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                4f,
                context.resources.displayMetrics
            ).toInt()
            setPadding(padding, padding, padding, padding)
            layoutParams = FrameLayout.LayoutParams(
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    36f,
                    context.resources.displayMetrics
                ).toInt(),
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    32f,
                    context.resources.displayMetrics
                ).toInt(),
                Gravity.BOTTOM or Gravity.END
            )
        }
        button.addView(icon)
        button.setOnClickListener {
            if (onSymCloseRequested != null) {
                onSymCloseRequested?.invoke()
            } else {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(button.windowToken, 0)
            }
        }
        return button
    }

    private fun createSurfaceCloseButton(): View {
        val buttonSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            36f,
            context.resources.displayMetrics
        ).toInt()
        val buttonHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            32f,
            context.resources.displayMetrics
        ).toInt()
        val padding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_close_24)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = createCloseButtonBackground()
            contentDescription = context.getString(R.string.close)
            setPadding(padding, padding, padding, padding)
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                buttonSize,
                buttonHeight,
                Gravity.BOTTOM or Gravity.END
            )
            setOnClickListener {
                onSymCloseRequested?.invoke()
            }
        }
    }

    private fun createKeyboardSelectionButton(height: Int, width: Int): View {
        val theme = activeThemeColors()
        val button = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(width, height)
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.change_keyboard_button)
        }
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_globe_24)
            setColorFilter(theme.textAndIcons)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        button.addView(icon)
        button.setOnClickListener {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        }
        return button
    }

    private fun createCloseButtonBackground(theme: KeyboardThemeColors? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(theme?.statusBarButton ?: Color.argb(95, 220, 38, 38))
            if (theme != null) {
                setStroke(dpToPx(1f), theme.divider)
            }
            cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                6f,
                context.resources.displayMetrics
            )
        }
    }

    private fun applySurfaceCloseButtonTheme(theme: KeyboardThemeColors) {
        (symSurfaceCloseButton as? ImageView)?.apply {
            setColorFilter(theme.textAndIcons)
            background = createCloseButtonBackground(theme)
            val rounded = SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)
            (layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                val screenWidth = statusBarLayout?.width?.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                val rightInset = if (rounded) dpToPx(3.1f) else 0
                val targetWidth = if (rounded) screenWidth - (screenWidth / 10) * 9 - rightInset else dpToPx(36f)
                val targetHeight = if (rounded) hardwareSymKeyHeightPx(theme) else dpToPx(32f)
                if (params.width != targetWidth || params.height != targetHeight || params.rightMargin != rightInset) {
                    params.width = targetWidth
                    params.height = targetHeight
                    params.rightMargin = rightInset
                    layoutParams = params
                }
            }
            setPadding(dpToPx(4f), dpToPx(4f), dpToPx(if (rounded) 12f else 4f), dpToPx(if (rounded) 12f else 4f))
            if (rounded) {
                background = ColorDrawable(theme.statusBarButton)
                drawable?.let { icon ->
                    val clipboardSize = if (pastierinaModeActive) {
                        (dpToPx(36f * theme.suggestionsHeightScale.coerceIn(0.65f, 1.6f)) - dpToPx(4f)) * 0.64f
                    } else {
                        minOf(dpToPx(24f).toFloat(), dpToPx(55f * theme.variationsHeightScale.coerceIn(0.65f, 1.6f)) * 0.48f)
                    }
                    val scale = clipboardSize / icon.intrinsicHeight.coerceAtLeast(1)
                    scaleType = ImageView.ScaleType.MATRIX
                    imageMatrix = Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(
                            (layoutParams.width - icon.intrinsicWidth * scale) / 2f - paddingLeft - dpToPx(4f),
                            (layoutParams.height - icon.intrinsicHeight * scale) / 2f - paddingTop - dpToPx(2f)
                        )
                    }
                }
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        val displayRadius = (statusBarLayout as? ImeChromeLayout)?.bottomCornerRadiiPx?.second
                            ?: dpToPx(TITAN_2_ELITE_CORNER_FALLBACK_RADIUS_DP)
                        val radius = (displayRadius - reservedExpandedLedHeight()).coerceAtLeast(0).toFloat()
                        val horizontalRadius = (displayRadius - dpToPx(3.1f)).coerceAtLeast(0).toFloat()
                        val path = Path().apply {
                            addRoundRect(
                                RectF(0f, -2f * radius, view.width.toFloat(), view.height.toFloat()),
                                floatArrayOf(0f, 0f, 0f, 0f, horizontalRadius, radius, 0f, 0f),
                                Path.Direction.CW
                            )
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) outline.setPath(path)
                        else {
                            @Suppress("DEPRECATION")
                            outline.setConvexPath(path)
                        }
                    }
                }
                clipToOutline = true
                invalidateOutline()
            } else {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                clipToOutline = false
                outlineProvider = ViewOutlineProvider.BACKGROUND
            }
        }
    }

    private fun closeSymAfterTouchKeyIfNeeded(): Boolean {
        val shouldClose =
            SettingsManager.getSymAutoClose(context) &&
            SettingsManager.getSymAutoCloseOnTouch(context)
        if (shouldClose) {
            onSymCloseRequested?.invoke()
        }
        return shouldClose
    }

    private fun commitTouchSymbolAfterCloseIfNeeded(
        anchor: View,
        inputConnection: android.view.inputmethod.InputConnection,
        content: String
    ) {
        val commit = {
            if (onSoftwareKeyboardBoundaryTextInput?.invoke(content, inputConnection) != true) {
                inputConnection.commitText(content, 1)
            }
        }
        if (closeSymAfterTouchKeyIfNeeded()) {
            anchor.post(commit)
        } else {
            commit()
        }
    }

    private fun addKeyToRow(
        rowLayout: LinearLayout,
        keyCode: Int,
        symMappings: Map<Int, String>,
        width: Int,
        height: Int,
        spacing: Int,
        page: Int,
        inputConnection: android.view.inputmethod.InputConnection?,
        isLast: Boolean
    ) {
        val keyLabels = mapOf(
            android.view.KeyEvent.KEYCODE_Q to "Q", android.view.KeyEvent.KEYCODE_W to "W", android.view.KeyEvent.KEYCODE_E to "E",
            android.view.KeyEvent.KEYCODE_R to "R", android.view.KeyEvent.KEYCODE_T to "T", android.view.KeyEvent.KEYCODE_Y to "Y",
            android.view.KeyEvent.KEYCODE_U to "U", android.view.KeyEvent.KEYCODE_I to "I", android.view.KeyEvent.KEYCODE_O to "O",
            android.view.KeyEvent.KEYCODE_P to "P", android.view.KeyEvent.KEYCODE_A to "A", android.view.KeyEvent.KEYCODE_S to "S",
            android.view.KeyEvent.KEYCODE_D to "D", android.view.KeyEvent.KEYCODE_F to "F", android.view.KeyEvent.KEYCODE_G to "G",
            android.view.KeyEvent.KEYCODE_H to "H", android.view.KeyEvent.KEYCODE_J to "J", android.view.KeyEvent.KEYCODE_K to "K",
            android.view.KeyEvent.KEYCODE_L to "L", android.view.KeyEvent.KEYCODE_Z to "Z", android.view.KeyEvent.KEYCODE_X to "X",
            android.view.KeyEvent.KEYCODE_C to "C", android.view.KeyEvent.KEYCODE_V to "V", android.view.KeyEvent.KEYCODE_B to "B",
            android.view.KeyEvent.KEYCODE_N to "N", android.view.KeyEvent.KEYCODE_M to "M"
        )
        val label = keyLabels[keyCode] ?: ""
        val content = symMappings[keyCode] ?: ""
        val keyButton = createEmojiKeyButton(label, content, height, page)
        keyButton.isLongClickable = true
        keyButton.setOnLongClickListener {
            openSymCustomization(page = page, keyCode = keyCode, openPicker = true)
            true
        }
        
        if (content.isNotEmpty() && inputConnection != null) {
            keyButton.isClickable = true
            keyButton.isFocusable = true
            keyButton.setOnClickListener {
                commitTouchSymbolAfterCloseIfNeeded(keyButton, inputConnection, content)
            }
        }
        
        rowLayout.addView(keyButton, LinearLayout.LayoutParams(width, height))
        if (!isLast) {
            rowLayout.addView(View(context), LinearLayout.LayoutParams(spacing, height))
        }
    }
    
    /**
     * Crea una griglia emoji personalizzabile (per la schermata di personalizzazione).
     * Restituisce una View che può essere incorporata in Compose tramite AndroidView.
     * 
     * @param symMappings Le mappature emoji da visualizzare
     * @param onKeyClick Callback chiamato quando un tasto viene cliccato (keyCode, emoji)
     */
    fun createCustomizableEmojiKeyboard(
        symMappings: Map<Int, String>,
        onKeyClick: (Int, String) -> Unit,
        page: Int = 1 // Default a pagina 1 (emoji)
    ): View {
        isTitan2Layout = SettingsManager.isTitan2LayoutEnabled(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bottomPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12f,
                context.resources.displayMetrics
            ).toInt()
            setPadding(0, 0, 0, bottomPadding) // Nessun padding orizzontale, solo in basso
            // Aggiungi sfondo nero per migliorare la visibilità dei caratteri con tema chiaro
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Definizione delle righe della tastiera (stessa struttura della tastiera reale)
        val keyboardRows = listOf(
            listOf(android.view.KeyEvent.KEYCODE_Q, android.view.KeyEvent.KEYCODE_W, android.view.KeyEvent.KEYCODE_E, 
                   android.view.KeyEvent.KEYCODE_R, android.view.KeyEvent.KEYCODE_T, android.view.KeyEvent.KEYCODE_Y, 
                   android.view.KeyEvent.KEYCODE_U, android.view.KeyEvent.KEYCODE_I, android.view.KeyEvent.KEYCODE_O, 
                   android.view.KeyEvent.KEYCODE_P),
            listOf(android.view.KeyEvent.KEYCODE_A, android.view.KeyEvent.KEYCODE_S, android.view.KeyEvent.KEYCODE_D, 
                   android.view.KeyEvent.KEYCODE_F, android.view.KeyEvent.KEYCODE_G, android.view.KeyEvent.KEYCODE_H, 
                   android.view.KeyEvent.KEYCODE_J, android.view.KeyEvent.KEYCODE_K, android.view.KeyEvent.KEYCODE_L),
            listOf(android.view.KeyEvent.KEYCODE_Z, android.view.KeyEvent.KEYCODE_X, android.view.KeyEvent.KEYCODE_C, 
                   android.view.KeyEvent.KEYCODE_V, android.view.KeyEvent.KEYCODE_B, android.view.KeyEvent.KEYCODE_N, 
                   android.view.KeyEvent.KEYCODE_M)
        )
        
        val keyLabels = mapOf(
            android.view.KeyEvent.KEYCODE_Q to "Q", android.view.KeyEvent.KEYCODE_W to "W", android.view.KeyEvent.KEYCODE_E to "E",
            android.view.KeyEvent.KEYCODE_R to "R", android.view.KeyEvent.KEYCODE_T to "T", android.view.KeyEvent.KEYCODE_Y to "Y",
            android.view.KeyEvent.KEYCODE_U to "U", android.view.KeyEvent.KEYCODE_I to "I", android.view.KeyEvent.KEYCODE_O to "O",
            android.view.KeyEvent.KEYCODE_P to "P", android.view.KeyEvent.KEYCODE_A to "A", android.view.KeyEvent.KEYCODE_S to "S",
            android.view.KeyEvent.KEYCODE_D to "D", android.view.KeyEvent.KEYCODE_F to "F", android.view.KeyEvent.KEYCODE_G to "G",
            android.view.KeyEvent.KEYCODE_H to "H", android.view.KeyEvent.KEYCODE_J to "J", android.view.KeyEvent.KEYCODE_K to "K",
            android.view.KeyEvent.KEYCODE_L to "L", android.view.KeyEvent.KEYCODE_Z to "Z", android.view.KeyEvent.KEYCODE_X to "X",
            android.view.KeyEvent.KEYCODE_C to "C", android.view.KeyEvent.KEYCODE_V to "V", android.view.KeyEvent.KEYCODE_B to "B",
            android.view.KeyEvent.KEYCODE_N to "N", android.view.KeyEvent.KEYCODE_M to "M"
        )
        
        val keySpacing = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        
        // Calcola la larghezza fissa dei tasti basata sulla prima riga (10 caselle)
        // Usa ViewTreeObserver per ottenere la larghezza effettiva del container dopo il layout
        val maxKeysInRow = 10 // Prima riga ha 10 caselle
        
        // Inizializza con una larghezza temporanea, verrà aggiornata dopo il layout
        var fixedKeyWidth = 0
        
        container.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val containerWidth = container.width
                if (containerWidth > 0) {
                    val totalSpacing = keySpacing * (maxKeysInRow - 1)
                    fixedKeyWidth = (containerWidth - totalSpacing) / maxKeysInRow
                    
                    // Aggiorna tutti i tasti con la larghezza corretta
                    for (i in 0 until container.childCount) {
                        val rowLayout = container.getChildAt(i) as? LinearLayout
                        rowLayout?.let { row ->
                            for (j in 0 until row.childCount) {
                                val child = row.getChildAt(j)
                                val layoutParams = child.layoutParams as? LinearLayout.LayoutParams
                                layoutParams?.let {
                                    if (it.width != keySpacing) {
                                        // Update width for keys and placeholders, but NOT for spacing views
                                        it.width = fixedKeyWidth
                                        child.layoutParams = it
                                    }
                                }
                            }
                        }
                    }
                    
                    // Rimuovi il listener dopo il primo layout
                    container.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        })
        
        // Valore iniziale basato sulla larghezza dello schermo (verrà aggiornato dal listener)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val totalSpacing = keySpacing * (maxKeysInRow - 1)
        fixedKeyWidth = (screenWidth - totalSpacing) / maxKeysInRow
        
        val keyHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            56f,
            context.resources.displayMetrics
        ).toInt()
        
        // Crea ogni riga della tastiera (stessa struttura della tastiera reale)
        for ((rowIndex, row) in keyboardRows.withIndex()) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = if (isTitan2Layout) Gravity.START else Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (rowIndex < keyboardRows.size - 1) {
                        bottomMargin = keySpacing
                    }
                }
            }
            
            if (isTitan2Layout) {
                // Ortholinear layout for Titan 2 (Customization Preview)
                when (rowIndex) {
                    0 -> { // Row 1: Q W E R T Y U I O P (10 keys)
                        for ((index, keyCode) in row.withIndex()) {
                            addKeyToPreviewRow(rowLayout, keyCode, symMappings, fixedKeyWidth, keyHeight, keySpacing, page, onKeyClick, index == row.size - 1)
                        }
                    }
                    1 -> { // Row 2: A S D F G H J K L (9 keys) -> Add placeholder at the end to make it 10
                        for ((index, keyCode) in row.withIndex()) {
                            addKeyToPreviewRow(rowLayout, keyCode, symMappings, fixedKeyWidth, keyHeight, keySpacing, page, onKeyClick, false)
                        }
                        rowLayout.addView(View(context), LinearLayout.LayoutParams(fixedKeyWidth, keyHeight))
                    }
                    2 -> { // Row 3: Z X C V [Close Placeholder] [Globe Placeholder] B N M [Gap]
                        // Z X C V (4 keys)
                        for (i in 0..3) {
                            addKeyToPreviewRow(rowLayout, row[i], symMappings, fixedKeyWidth, keyHeight, keySpacing, page, onKeyClick, false)
                        }
                        
                        // Close Button Placeholder (no icon in customization preview)
                        rowLayout.addView(View(context), LinearLayout.LayoutParams(fixedKeyWidth, keyHeight))
                        rowLayout.addView(View(context), LinearLayout.LayoutParams(keySpacing, keyHeight))
                        
                        // Globe Button Placeholder (no icon in customization preview)
                        rowLayout.addView(View(context), LinearLayout.LayoutParams(fixedKeyWidth, keyHeight))
                        rowLayout.addView(View(context), LinearLayout.LayoutParams(keySpacing, keyHeight))
                        
                        // B N M (3 keys)
                        for (i in 4..6) {
                            addKeyToPreviewRow(rowLayout, row[i], symMappings, fixedKeyWidth, keyHeight, keySpacing, page, onKeyClick, false)
                        }

                        // Right Gap (placeholder for the physical cutout/space at the end of row 3)
                        rowLayout.addView(View(context), LinearLayout.LayoutParams(fixedKeyWidth, keyHeight))
                    }
                }
                container.addView(rowLayout)
                continue
            }
            
            // Per la terza riga, aggiungi placeholder trasparente a sinistra
            if (rowIndex == 2) {
                val leftPlaceholder = createPlaceholderButton(keyHeight)
                rowLayout.addView(leftPlaceholder, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    marginEnd = keySpacing
                })
            }
            
            for ((index, keyCode) in row.withIndex()) {
                val label = keyLabels[keyCode] ?: ""
                val emoji = symMappings[keyCode] ?: ""
                
                // Usa la stessa funzione createEmojiKeyButton della tastiera reale
                val keyButton = createEmojiKeyButton(label, emoji, keyHeight, page)
                
                // Aggiungi click listener
                keyButton.setOnClickListener {
                    onKeyClick(keyCode, emoji)
                }
                
                // Usa larghezza fissa invece di weight (stesso layout della tastiera reale)
                rowLayout.addView(keyButton, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    if (index < row.size - 1) {
                        marginEnd = keySpacing
                    }
                })
            }
            
            // Per la terza riga nella schermata di personalizzazione, aggiungi placeholder trasparente a destra
            // per mantenere l'allineamento (senza matita e senza click listener)
            if (rowIndex == 2) {
                val rightPlaceholder = createPlaceholderButton(keyHeight)
                rowLayout.addView(rightPlaceholder, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    marginStart = keySpacing
                })
            }
            
            container.addView(rowLayout)
        }
        
        return container
    }

    private fun addKeyToPreviewRow(
        rowLayout: LinearLayout,
        keyCode: Int,
        symMappings: Map<Int, String>,
        width: Int,
        height: Int,
        spacing: Int,
        page: Int,
        onKeyClick: (Int, String) -> Unit,
        isLast: Boolean
    ) {
        val keyLabels = mapOf(
            android.view.KeyEvent.KEYCODE_Q to "Q", android.view.KeyEvent.KEYCODE_W to "W", android.view.KeyEvent.KEYCODE_E to "E",
            android.view.KeyEvent.KEYCODE_R to "R", android.view.KeyEvent.KEYCODE_T to "T", android.view.KeyEvent.KEYCODE_Y to "Y",
            android.view.KeyEvent.KEYCODE_U to "U", android.view.KeyEvent.KEYCODE_I to "I", android.view.KeyEvent.KEYCODE_O to "O",
            android.view.KeyEvent.KEYCODE_P to "P", android.view.KeyEvent.KEYCODE_A to "A", android.view.KeyEvent.KEYCODE_S to "S",
            android.view.KeyEvent.KEYCODE_D to "D", android.view.KeyEvent.KEYCODE_F to "F", android.view.KeyEvent.KEYCODE_G to "G",
            android.view.KeyEvent.KEYCODE_H to "H", android.view.KeyEvent.KEYCODE_J to "J", android.view.KeyEvent.KEYCODE_K to "K",
            android.view.KeyEvent.KEYCODE_L to "L", android.view.KeyEvent.KEYCODE_Z to "Z", android.view.KeyEvent.KEYCODE_X to "X",
            android.view.KeyEvent.KEYCODE_C to "C", android.view.KeyEvent.KEYCODE_V to "V", android.view.KeyEvent.KEYCODE_B to "B",
            android.view.KeyEvent.KEYCODE_N to "N", android.view.KeyEvent.KEYCODE_M to "M"
        )
        val label = keyLabels[keyCode] ?: ""
        val emoji = symMappings[keyCode] ?: ""
        val keyButton = createEmojiKeyButton(label, emoji, height, page)
        keyButton.setOnClickListener {
            onKeyClick(keyCode, emoji)
        }
        rowLayout.addView(keyButton, LinearLayout.LayoutParams(width, height))
        if (!isLast) {
            rowLayout.addView(View(context), LinearLayout.LayoutParams(spacing, height))
        }
    }
    
    /**
     * Anima l'apparizione della griglia emoji solo con slide up (nessun fade).
     * @param backgroundView Il view dello sfondo da impostare a opaco immediatamente
     */
    private fun animateEmojiKeyboardIn(view: View, backgroundView: View? = null) {
        if (SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) {
            view.alpha = 1f
            view.translationY = 0f
            view.visibility = View.VISIBLE
            backgroundView?.setBackgroundColor(activeThemeColors().background)
            return
        }
        val height = view.height
        if (height == 0) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
        }
        val measuredHeight = view.measuredHeight

        view.alpha = 1f
        view.translationY = measuredHeight.toFloat()
        view.visibility = View.VISIBLE

        // Set background to opaque immediately without animation
        backgroundView?.let { bgView ->
            if (bgView.background !is ColorDrawable) {
                bgView.background = ColorDrawable(activeThemeColors().background)
            }
            (bgView.background as? ColorDrawable)?.alpha = 255
        }

        val animator = ValueAnimator.ofFloat(measuredHeight.toFloat(), 0f).apply {
            duration = 125
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                view.translationY = value
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.translationY = 0f
                    view.alpha = 1f
                }
            })
        }
        animator.start()
    }
    
    /**
     * Anima la scomparsa della griglia emoji (slide down + fade out).
     * @param backgroundView Il view dello sfondo (non animato, rimane opaco)
     * @param onAnimationEnd Callback chiamato quando l'animazione è completata
     */
    private fun animateEmojiKeyboardOut(view: View, backgroundView: View? = null, onAnimationEnd: (() -> Unit)? = null) {
        if (SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) {
            view.visibility = View.GONE
            view.translationY = 0f
            view.alpha = 1f
            onAnimationEnd?.invoke()
            return
        }
        val height = view.height
        if (height == 0) {
            view.visibility = View.GONE
            onAnimationEnd?.invoke()
            return
        }

        // Background remains opaque, no animation

        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 100
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                view.alpha = progress
                view.translationY = height * (1f - progress)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                    view.translationY = 0f
                    view.alpha = 1f
                    onAnimationEnd?.invoke()
                }
            })
        }
        animator.start()
    }

    
    

    fun update(snapshot: StatusSnapshot, emojiMapText: String = "", inputConnection: android.view.inputmethod.InputConnection? = null, symMappings: Map<Int, String>? = null) {
        isTitan2Layout = SettingsManager.isTitan2LayoutEnabled(context)
        val isFullSoftwareKeyboardMode =
            mode == Mode.INPUT_VIEW &&
                SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) == SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        val isSoftwareKeyboardClipboardPage = isFullSoftwareKeyboardMode && snapshot.symPage == 3
        val isSoftwareKeyboardEmojiPage = isFullSoftwareKeyboardMode && snapshot.symPage == 4
        val isSoftwareKeyboardSymbolPage = isFullSoftwareKeyboardMode && snapshot.symPage in listOf(1, 2, 5)
        val isSoftwareKeyboardOverlayPage =
            isSoftwareKeyboardSymbolPage || isSoftwareKeyboardClipboardPage || isSoftwareKeyboardEmojiPage
        if (!isSoftwareKeyboardEmojiPage) {
            dismissEmojiPickerPopup()
        }
        val activeTheme = activeThemeSettings(isFullSoftwareKeyboardMode)
        val activeColors = activeTheme.toKeyboardThemeColors()
        val softwareThemeSettings = if (isFullSoftwareKeyboardMode) activeTheme else softwareTheme()
        variationBarView?.onVariationSelectedListener = onVariationSelectedListener
        variationBarView?.onCursorMovedListener = onCursorMovedListener
        variationBarView?.updateInputConnection(inputConnection)
        variationBarView?.forceVariationAreaVisible = isFullSoftwareKeyboardMode
        variationBarView?.setSymModeActive((snapshot.symPage > 0 && !isSoftwareKeyboardOverlayPage) || snapshot.clipboardOverlay)
        variationBarView?.updateLanguageButtonText()
        updateClipboardCount(snapshot.clipboardCount)
        hamburgerMenuView?.refreshLanguageText()
        fullSuggestionsBar?.refreshLanguageText()
        updatePastierinaModeState()
        if (inputConnection !== lastHamburgerInputConnection) {
            hideHamburgerMenu()
            lastHamburgerInputConnection = inputConnection
        }
        if ((snapshot.symPage > 0 && !isFullSoftwareKeyboardMode) || snapshot.clipboardOverlay || (pastierinaModeActive && !isFullSoftwareKeyboardMode)) {
            hideHamburgerMenu()
        }
        
        val layout = ensureLayoutCreated(emojiMapText) ?: return
        restoreLayoutHeight(layout)
        ensureMainChildOrder()
        applyChromeZOrder()
        applyKeyboardThemeOverrides(activeColors)
        applyAccessibilitySecondRowReadPreference()
        val modifiersContainerView = modifiersContainer ?: return
        val emojiView = emojiMapTextView ?: return
        val emojiKeyboardView = emojiKeyboardContainer ?: return
        val symSurfaceView = symSurfaceContainer ?: return
        val symSurfaceStackView = symSurfaceStack ?: return
        emojiView.visibility = View.GONE
        
        if (snapshot.navModeActive) {
            layout.visibility = View.GONE
            return
        }
        layout.visibility = View.VISIBLE
        applyAccessibilityLiveRegionPreference(layout)
        
        if (layout.background !is ColorDrawable) {
            layout.background = ColorDrawable(activeTheme.background)
        } else if (snapshot.symPage == 0) {
            (layout.background as ColorDrawable).alpha = 255
        }
        
        modifiersContainerView.visibility = View.GONE
        val showHardwareBottomIndicators = SettingsManager.getModifierIndicatorShowsBottomStrip(context)
        val showHardwareStatusBarIndicators = SettingsManager.getModifierIndicatorShowsStatusBar(context)
        fullSuggestionsBar?.setModifierMenuIndicatorsEnabled(
            !isFullSoftwareKeyboardMode && showHardwareStatusBarIndicators
        )
        fullSuggestionsBar?.updateModifierIndicators(snapshot)
        updateMenuBarModifierIndicators(
            container = modifiersContainerView,
            snapshot = snapshot,
            show = false,
            theme = activeColors
        )

        ledStatusView.layout = modifierLedLayout()
        val showLedStrip = if (isFullSoftwareKeyboardMode) {
            softwareThemeSettings.showLeds
        } else {
            showHardwareBottomIndicators
        }
        ledStatusView.getView()?.visibility = if (showLedStrip) View.VISIBLE else View.GONE
        if (showLedStrip) {
            ledStatusView.update(snapshot)
        }
        val showSecondRow = !pastierinaModeActive
        val variationsBar = if (showSecondRow) variationBarView else null
        val variationsWrapperView = if (showSecondRow) variationsWrapper else null
        if (!showSecondRow) {
            variationBarView?.hideImmediate()
        }
        val experimentalEnabled = SettingsManager.isExperimentalSuggestionsEnabled(context)
        val suggestionsEnabledSetting = SettingsManager.getSuggestionsEnabled(context)
        // Keep the suggestion/status row stable in both full-status-bar and Pastierina mode.
        val expansionActive = expansionSuggestions.isNotEmpty()
        val showFullBar = expansionActive || (
            suggestionsEnabledSetting &&
                (experimentalEnabled || isFullSoftwareKeyboardMode) &&
                (isFullSoftwareKeyboardMode || !snapshot.shouldDisableSuggestions) &&
                (snapshot.symPage == 0 || isSoftwareKeyboardOverlayPage) &&
                !snapshot.clipboardOverlay
            )
        val suggestionsAnnouncementDelayMs = SettingsManager.getAccessibilitySuggestionsAnnouncementDelayMs(context)
        fullSuggestionsBar?.setAccessibilityAnnouncementConfig(
            liveAnnouncementsEnabled = isAccessibilityLiveAnnouncementsEnabled(),
            suggestionsAnnouncementDelayMs = suggestionsAnnouncementDelayMs
        )
        fullSuggestionsBar?.requireDictionaryForSuggestions = !expansionActive && !isFullSoftwareKeyboardMode
        fullSuggestionsBar?.update(
            if (expansionActive) expansionSuggestions else snapshot.suggestions,
            showFullBar,
            inputConnection,
            onVariationSelectedListener,
            if (expansionActive || isFullSoftwareKeyboardMode) false else snapshot.shouldDisableSuggestions,
            if (expansionActive) null else snapshot.addWordCandidate,
            onAddUserWord,
            onAddUserWordSubstitutionRequested,
            onSuggestionCommitted,
            onHideSuggestion,
            onDeleteUserSuggestion,
            canDeleteUserSuggestion,
            if (expansionActive) { _, suggestion -> onExpansionSuggestionSelected?.invoke(suggestion) } else null
        )
        val shouldShowSoftwareKeyboard =
            isFullSoftwareKeyboardMode &&
                !snapshot.clipboardOverlay
        (layout as? ImeChromeLayout)?.expandedPickerButtons = null
        (layout as? ImeChromeLayout)?.softwareKeyboardModeActive = shouldShowSoftwareKeyboard
        if (snapshot.clipboardOverlay) {
            // Show clipboard as dedicated overlay (not part of SYM pages)
            updateClipboardView(
                inputConnection,
                softwareKeyboardHeight = lastSoftwareKeyboardHeight.takeIf { isFullSoftwareKeyboardMode && it > 0 }
            )
            variationsBar?.resetVariationsState()

            // Pin background and hide variations while showing clipboard grid
            if (layout.background !is ColorDrawable) {
                layout.background = ColorDrawable(activeColors.background)
            }
            (layout.background as? ColorDrawable)?.alpha = 255
            variationsWrapperView?.apply {
                visibility = View.INVISIBLE
                isEnabled = false
                isClickable = false
            }
            variationsBar?.hideImmediate()

            val measured = ensureEmojiKeyboardMeasuredHeight(emojiKeyboardView, layout, forceReMeasure = true)
            val animationHeight = if (measured > 0) measured else defaultSymHeightPx
            emojiKeyboardView.setBackgroundColor(activeColors.background)
            emojiKeyboardView.visibility = View.VISIBLE
            val surfaceHeight = resolveSurfaceHeightWithOptionalLed(animationHeight, showLedStrip)
            setSurfaceCloseVisible(SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context))
            applySymSurfaceLayout(symSurfaceView, symSurfaceStackView, emojiKeyboardView, surfaceHeight, reserveLedSpace = showLedStrip)
            if (!symShown && !wasSymActive) {
                emojiKeyboardView.alpha = 1f
                emojiKeyboardView.translationY = surfaceHeight.toFloat()
                animateEmojiKeyboardIn(emojiKeyboardView, layout)
                symShown = true
                wasSymActive = true
            } else {
                emojiKeyboardView.alpha = 1f
                emojiKeyboardView.translationY = 0f
                wasSymActive = true
            }
            return
        }

        if (shouldShowSoftwareKeyboard && (snapshot.symPage == 0 || isSoftwareKeyboardSymbolPage)) {
            updateSoftwareKeyboard(snapshot, inputConnection, symMappings)
            if (showSecondRow) {
                variationsWrapperView?.apply {
                    visibility = View.VISIBLE
                    isEnabled = true
                    isClickable = true
                }
                val snapshotForVariations = snapshot.copy(
                    suggestions = emptyList(),
                    addWordCandidate = null,
                    variations = snapshot.variations.ifEmpty {
                        SettingsManager.getStaticVariationBasePreset(context)
                    },
                    shouldDisableVariations = false
                )
                variationsBar?.showVariations(snapshotForVariations, inputConnection)
            } else {
                variationBarView?.hideImmediate()
            }
            val measured = measureSoftwareKeyboardDesiredHeight(emojiKeyboardView, layout)
            val keyboardHeight = if (measured > 0) measured else defaultSymHeightPx
            lastSoftwareKeyboardHeight = keyboardHeight
            emojiKeyboardView.setBackgroundColor(softwareThemeSettings.background)
            emojiKeyboardView.visibility = View.VISIBLE
            applySymSurfaceLayout(
                symSurfaceView,
                symSurfaceStackView,
                emojiKeyboardView,
                resolveSurfaceHeightWithOptionalLed(keyboardHeight, showLedStrip),
                reserveLedSpace = showLedStrip
            )
            setSurfaceCloseVisible(false)
            symShown = snapshot.symPage in listOf(1, 2, 5)
            wasSymActive = snapshot.symPage in listOf(1, 2, 5)
            return
        } else {
            softwareKeyboardShown = false
        }

        if (snapshot.symPage > 0) {
            // Handle page 3 (clipboard), page 4 (emoji picker) vs pages 1-2 (emoji/symbols)
            if (snapshot.symPage == 3) {
                // Show clipboard history inline (similar to emoji grid)
                updateClipboardView(
                    inputConnection,
                    softwareKeyboardHeight = lastSoftwareKeyboardHeight.takeIf { isFullSoftwareKeyboardMode && it > 0 }
                )
            } else if (snapshot.symPage == 4) {
                // Show emoji picker view
                updateEmojiPickerView(
                    snapshot,
                    inputConnection,
                    softwareKeyboardHeight = lastSoftwareKeyboardHeight.takeIf { isFullSoftwareKeyboardMode && it > 0 }
                )
            } else if (isSoftwareKeyboardSymbolPage && symMappings != null) {
                updateSoftwareSymbolKeyboard(symMappings, snapshot, inputConnection)
            } else if (symMappings != null) {
                updateEmojiKeyboard(symMappings, snapshot.symPage, inputConnection)
            }
            variationsBar?.resetVariationsState()

            // Pin background to opaque IME color and hide variations so SYM animates on a solid canvas.
            if (layout.background !is ColorDrawable) {
                layout.background = ColorDrawable(activeColors.background)
            }
            (layout.background as? ColorDrawable)?.alpha = 255
            if (isSoftwareKeyboardOverlayPage) {
                variationsWrapperView?.apply {
                    visibility = View.VISIBLE
                    isEnabled = true
                    isClickable = true
                }
                val snapshotForVariations = if (snapshot.suggestions.isNotEmpty()) {
                    snapshot.copy(suggestions = emptyList(), addWordCandidate = null)
                } else snapshot
                variationsBar?.showVariations(snapshotForVariations, inputConnection)
            } else {
                variationsWrapperView?.apply {
                    visibility = View.INVISIBLE // keep space to avoid shrink/flash
                    isEnabled = false
                    isClickable = false
                }
                variationsBar?.hideImmediate()
            }

            val measured = ensureEmojiKeyboardMeasuredHeight(emojiKeyboardView, layout, forceReMeasure = true)
            val symHeight = resolveSymSurfaceHeight(
                snapshot = snapshot,
                measuredHeight = measured,
                isFullSoftwareKeyboardMode = isFullSoftwareKeyboardMode
            )
            val surfaceHeight = resolveSurfaceHeightWithOptionalLed(symHeight, showLedStrip)
            lastSymHeight = surfaceHeight
            emojiKeyboardView.setBackgroundColor(activeColors.background)
            emojiKeyboardView.visibility = View.VISIBLE
            applySymSurfaceLayout(symSurfaceView, symSurfaceStackView, emojiKeyboardView, surfaceHeight, reserveLedSpace = showLedStrip)
            setSurfaceCloseVisible(snapshot.symPage in listOf(1, 2, 5) ||
                (snapshot.symPage == 3 && SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) ||
                (snapshot.symPage == 4 && (layout as? ImeChromeLayout)?.expandedPickerButtons != null))
            if (!symShown && !wasSymActive) {
                emojiKeyboardView.alpha = 1f // keep black visible immediately
                emojiKeyboardView.translationY = surfaceHeight.toFloat()
                animateEmojiKeyboardIn(emojiKeyboardView, layout)
                symShown = true
                wasSymActive = true
            } else {
                emojiKeyboardView.alpha = 1f
                emojiKeyboardView.translationY = 0f
                wasSymActive = true
            }
            return
        }
        
        if (emojiKeyboardView.visibility == View.VISIBLE) {
            animateEmojiKeyboardOut(emojiKeyboardView, layout) {
                variationsWrapperView?.apply {
                    visibility = View.VISIBLE
                    isEnabled = true
                    isClickable = true
                }
                val snapshotForVariations = if (snapshot.suggestions.isNotEmpty()) {
                    snapshot.copy(suggestions = emptyList(), addWordCandidate = null)
                } else snapshot
                variationsBar?.showVariations(snapshotForVariations, inputConnection)
                setSurfaceCloseVisible(false)
                resetSymSurfaceToLedOnly(symSurfaceView)
            }
            symShown = false
            wasSymActive = false
            lastSymPageRendered = 0 // Reset when closing SYM page
        } else {
            emojiKeyboardView.visibility = View.GONE
            variationsWrapperView?.apply {
                visibility = View.VISIBLE
                isEnabled = true
                isClickable = true
            }
            val snapshotForVariations = if (snapshot.suggestions.isNotEmpty()) {
                snapshot.copy(suggestions = emptyList(), addWordCandidate = null)
            } else snapshot
            variationsBar?.showVariations(snapshotForVariations, inputConnection)
            setSurfaceCloseVisible(false)
            resetSymSurfaceToLedOnly(symSurfaceView)
            symShown = false
            wasSymActive = false
            lastSymPageRendered = 0 // Reset when closing SYM page
        }
    }

    private fun updateAccessibilityStateDescription(view: View) {
        val newStateDescription = buildLayoutAccessibilityStateDescription()
        if (ViewCompat.getStateDescription(view)?.toString() == newStateDescription) {
            return
        }
        ViewCompat.setStateDescription(view, newStateDescription)
    }

    private fun isAccessibilityLiveAnnouncementsEnabled(): Boolean {
        return SettingsManager.getAccessibilityLiveAnnouncementsEnabled(context)
    }

    private fun isAccessibilityReadSecondRowEnabled(): Boolean {
        return SettingsManager.getAccessibilityReadSecondRowEnabled(context)
    }

    private fun applyAccessibilityLiveRegionPreference(view: View) {
        if (view.accessibilityLiveRegion != View.ACCESSIBILITY_LIVE_REGION_NONE) {
            view.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
        }
    }

    private fun applyAccessibilitySecondRowReadPreference() {
        val importance = if (isAccessibilityReadSecondRowEnabled()) {
            View.IMPORTANT_FOR_ACCESSIBILITY_YES
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        variationsWrapper?.let { wrapper ->
            if (wrapper.importantForAccessibility != importance) {
                wrapper.importantForAccessibility = importance
            }
        }
        modifiersContainer?.let { container ->
            if (container.importantForAccessibility != importance) {
                container.importantForAccessibility = importance
            }
        }
    }

    private fun buildLayoutAccessibilityStateDescription(): String {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val subtype = imm?.currentInputMethodSubtype
            val languageLabel = if (subtype != null) {
                val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
                subtype.getDisplayName(context, context.packageName, appInfo)?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?: subtype.localeString().ifBlank { "Unknown" }
            } else {
                "Unknown"
            }

            val layoutName = subtype
                ?.let { AdditionalSubtypeUtils.getKeyboardLayoutFromSubtype(it) }
                ?.takeIf { it.isNotBlank() }
                ?: "qwerty"
            val layoutLabel = LayoutFileStore.getLayoutMetadataFromAssets(context.assets, layoutName)?.name
                ?.takeIf { it.isNotBlank() }
                ?: LayoutFileStore.getLayoutMetadata(context, layoutName)?.name
                ?.takeIf { it.isNotBlank() }
                ?: layoutName

            context.getString(
                R.string.status_bar_button_language_state_description,
                languageLabel,
                layoutLabel
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update accessibility state description", e)
            context.getString(
                R.string.status_bar_button_language_state_description,
                "Unknown",
                "qwerty"
            )
        }
    }

    private fun ensureEmojiKeyboardMeasuredHeight(view: View, parent: View, forceReMeasure: Boolean = false): Int {
        if (view.height > 0 && !forceReMeasure) {
            return view.height
        }
        val width = if (parent.width > 0) parent.width else context.resources.displayMetrics.widthPixels
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        return view.measuredHeight
    }

    private fun measureSoftwareKeyboardDesiredHeight(view: View, parent: View): Int {
        val width = if (parent.width > 0) parent.width else context.resources.displayMetrics.widthPixels
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        return view.measuredHeight
    }

    private fun applySymSurfaceLayout(
        surface: FrameLayout,
        stack: LinearLayout,
        content: View,
        surfaceHeight: Int,
        reserveLedSpace: Boolean
    ) {
        surface.visibility = View.VISIBLE
        val surfaceParams = surface.layoutParams as? LinearLayout.LayoutParams
        if (surfaceParams == null) {
            surface.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                surfaceHeight
            )
        } else if (
            surfaceParams.width != ViewGroup.LayoutParams.MATCH_PARENT ||
            surfaceParams.height != surfaceHeight ||
            surfaceParams.weight != 0f
        ) {
            surfaceParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            surfaceParams.height = surfaceHeight
            surfaceParams.weight = 0f
            surface.layoutParams = surfaceParams
        }
        val stackParams = stack.layoutParams as? FrameLayout.LayoutParams
        if (stackParams == null) {
            stack.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        } else if (
            stackParams.width != ViewGroup.LayoutParams.MATCH_PARENT ||
            stackParams.height != ViewGroup.LayoutParams.MATCH_PARENT
        ) {
            stackParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            stackParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            stack.layoutParams = stackParams
        }
        updateSurfaceCloseBottomMargin(if (reserveLedSpace) reservedExpandedLedHeight() else 0)

        val contentParams = content.layoutParams as? LinearLayout.LayoutParams
        val targetContentHeight = 0
        val targetContentWeight = 1f
        if (contentParams == null) {
            content.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                targetContentHeight,
                targetContentWeight
            )
        } else if (
            contentParams.width != ViewGroup.LayoutParams.MATCH_PARENT ||
            contentParams.height != targetContentHeight ||
            contentParams.weight != targetContentWeight
        ) {
            contentParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            contentParams.height = targetContentHeight
            contentParams.weight = targetContentWeight
            content.layoutParams = contentParams
        }
    }

    private fun setSurfaceCloseVisible(visible: Boolean) {
        symSurfaceCloseButton?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updateSurfaceCloseBottomMargin(bottomMargin: Int) {
        val button = symSurfaceCloseButton ?: return
        val params = button.layoutParams as? FrameLayout.LayoutParams ?: return
        if (params.bottomMargin == bottomMargin) {
            return
        }
        params.bottomMargin = bottomMargin
        button.layoutParams = params
    }

    private fun resolveSurfaceHeightWithOptionalLed(contentHeight: Int, reserveLedSpace: Boolean): Int {
        if (!reserveLedSpace) {
            return contentHeight
        }
        val ledHeight = reservedExpandedLedHeight()
        return contentHeight + ledHeight
    }

    private fun reservedExpandedLedHeight(): Int =
        if (SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) dpToPx(3.1f)
        else measureLedStripHeight()

    private fun measureLedStripHeight(): Int {
        val ledStrip = ledStatusView.getView() ?: return 0
        if (ledStrip.measuredHeight > 0) {
            return ledStrip.measuredHeight
        }
        val width = statusBarLayout?.width?.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        ledStrip.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return ledStrip.measuredHeight
    }

    private fun resetSymSurfaceToLedOnly(surface: FrameLayout) {
        surface.layoutParams = (surface.layoutParams as? LinearLayout.LayoutParams
            ?: LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            weight = 0f
        }
    }

    private fun resolveSymSurfaceHeight(
        snapshot: StatusSnapshot,
        measuredHeight: Int,
        isFullSoftwareKeyboardMode: Boolean
    ): Int {
        if (!isFullSoftwareKeyboardMode && snapshot.symPage in listOf(1, 2, 5)) {
            // All hardware SYM pages use the same three key rows. Do not let
            // measurement under the previous page's weighted layout resize them.
            val gap = if (SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) 0 else dpToPx(4f)
            return 3 * hardwareSymKeyHeightPx() + 2 * gap
        }
        if (snapshot.symPage == 4 && measuredHeight > 0) {
            return measuredHeight
        }
        if (isFullSoftwareKeyboardMode && lastSoftwareKeyboardHeight > 0) {
            return lastSoftwareKeyboardHeight
        }
        return if (measuredHeight > 0) measuredHeight else defaultSymHeightPx
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    private fun hardwareSymKeyHeightPx(theme: KeyboardThemeColors = activeThemeColors()): Int {
        if (!SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) return dpToPx(HARDWARE_SYM_KEY_HEIGHT_DP)
        return if (pastierinaModeActive) dpToPx(36f * theme.suggestionsHeightScale.coerceIn(0.65f, 1.6f))
        else dpToPx(55f * theme.variationsHeightScale.coerceIn(0.65f, 1.6f))
    }

    internal class ImeChromeLayout(context: Context) : LinearLayout(context) {
        private val screenAwakeController = ImeTouchScreenAwakeController(context)
        var regularCornerColors: Pair<Int, Int> = Color.BLACK to Color.BLACK
        var compactCornerColors: Pair<Int, Int> = Color.BLACK to Color.BLACK
        var bottomFillColors: Pair<Int, Int> = Color.BLACK to Color.BLACK
        var expandedCloseColor: Int = Color.BLACK
        var expandedCloseButton: View? = null
        var expandedPickerButtons: Pair<View, View>? = null
        var expandedKeyHeightPx: Int = (HARDWARE_SYM_KEY_HEIGHT_DP * resources.displayMetrics.density).toInt()
        private val cornerFillPaint = Paint()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (bottomCornerRadiiPx != null && expandedSurfaceView?.visibility == View.VISIBLE) {
                val surface = surfaceView
                val content = expandedSurfaceView
                if (surface != null && content != null) {
                    cornerFillPaint.color = bottomFillColors.first
                    canvas.drawRect(0f, (surface.top + content.bottom).toFloat(), width.toFloat(), height.toFloat(), cornerFillPaint)
                    expandedCloseButton?.takeIf { it.visibility == View.VISIBLE }?.let { button ->
                        cornerFillPaint.color = expandedCloseColor
                        canvas.drawRect(
                            (surface.left + button.left).toFloat(), (surface.top + button.top).toFloat(),
                            width.toFloat(), height.toFloat(), cornerFillPaint
                        )
                    }
                    expandedPickerButtons?.let { (leftButton, rightButton) ->
                        listOf(leftButton, rightButton).forEach { button ->
                            if (button.visibility != View.VISIBLE) return@forEach
                            val bounds = android.graphics.Rect(0, 0, button.width, button.height)
                            offsetDescendantRectToMyCoords(button, bounds)
                            cornerFillPaint.color = expandedCloseColor
                            canvas.drawRect(
                                if (button === leftButton) 0f else bounds.left.toFloat(), bounds.top.toFloat(),
                                if (button === rightButton) width.toFloat() else bounds.right.toFloat(),
                                height.toFloat(), cornerFillPaint
                            )
                        }
                    }
                }
            }
            val row = nestedRow ?: return
            val radii = bottomCornerRadiiPx ?: return
            val colors = if (row === compactStatusRow) compactCornerColors else regularCornerColors
            // Fill only the corner cutouts beside this row. Never paint across
            // the suggestion/variation boundary or extend a button below its row.
            cornerFillPaint.color = colors.first
            canvas.drawRect(0f, row.top.toFloat(), radii.first.toFloat(), row.bottom.toFloat(), cornerFillPaint)
            cornerFillPaint.color = colors.second
            canvas.drawRect((width - radii.second).toFloat(), row.top.toFloat(), width.toFloat(), row.bottom.toFloat(), cornerFillPaint)
            // Continue the row's themed surface to the bottom of the display.
            // Children draw afterward, keeping the modifier lights above the fill.
            cornerFillPaint.color = if (row === compactStatusRow) bottomFillColors.second else bottomFillColors.first
            canvas.drawRect(0f, row.bottom.toFloat(), width.toFloat(), height.toFloat(), cornerFillPaint)
            cornerFillPaint.color = colors.second
            canvas.drawRect((width - radii.second).toFloat(), row.bottom.toFloat(), width.toFloat(), height.toFloat(), cornerFillPaint)
        }
        var indicatorView: View? = null
        var expandedSurfaceView: View? = null
        var compactStatusRow: View? = null
        private var nestedRow: View? = null
        private var originalRowMargins = intArrayOf(0, 0, 0)
        private var originalRowOutline: ViewOutlineProvider? = null
        private var originalRowClip = false
        private var originalRowMinHeight = 0
        private val originalIconTransforms = mutableMapOf<ImageView, Pair<ImageView.ScaleType, Matrix>>()

        private fun updateNestedStatusRow() {
            indicatorView?.layoutParams?.height = ViewGroup.LayoutParams.WRAP_CONTENT
            (indicatorView?.layoutParams as? LayoutParams)?.topMargin = 0
            expandedSurfaceView?.clipToOutline = false
            nestedRow?.let { row ->
                (row.layoutParams as LayoutParams).apply {
                    leftMargin = originalRowMargins[0]
                    rightMargin = originalRowMargins[1]
                    bottomMargin = originalRowMargins[2]
                }
                row.outlineProvider = originalRowOutline
                row.clipToOutline = originalRowClip
                row.minimumHeight = originalRowMinHeight
            }
            nestedRow = null
            val radii = bottomCornerRadiiPx ?: return
            if (expandedSurfaceView?.visibility == View.VISIBLE && indicatorView?.visibility == View.VISIBLE) {
                val density = resources.displayMetrics.density
                val radius = maxOf(radii.first, radii.second)
                val stripHeight = (3.1f * density).toInt()
                val ledHeight = maxOf(radius + density.toInt(), expandedKeyHeightPx + stripHeight)
                (indicatorView?.layoutParams as? LayoutParams)?.apply {
                    height = ledHeight
                    topMargin = -(ledHeight - stripHeight).coerceAtLeast(0)
                }
                expandedSurfaceView?.apply {
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            val left = (radii.first - stripHeight).coerceAtLeast(0).toFloat()
                            val right = (radii.second - stripHeight).coerceAtLeast(0).toFloat()
                            val path = Path().apply {
                                addRoundRect(
                                    RectF(stripHeight.toFloat(), -2f * radius, view.width.toFloat() - stripHeight, view.height.toFloat()),
                                    floatArrayOf(0f, 0f, 0f, 0f, right, right, left, left),
                                    Path.Direction.CW
                                )
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) outline.setPath(path)
                            else {
                                @Suppress("DEPRECATION")
                                outline.setConvexPath(path)
                            }
                        }
                    }
                    clipToOutline = true
                    invalidateOutline()
                }
                return
            }
            if (softwareKeyboardModeActive || expandedSurfaceView?.visibility != View.GONE ||
                indicatorView?.visibility != View.VISIBLE || surfaceView?.visibility != View.VISIBLE
            ) return
            val surfaceIndex = indexOfChild(surfaceView)
            val row = (surfaceIndex - 1 downTo 0)
                .map(::getChildAt).firstOrNull { it.visibility == View.VISIBLE } ?: return
            val params = row.layoutParams as LayoutParams
            nestedRow = row
            originalRowMargins = intArrayOf(params.leftMargin, params.rightMargin, params.bottomMargin)
            originalRowOutline = row.outlineProvider
            originalRowClip = row.clipToOutline
            originalRowMinHeight = row.minimumHeight
            // Both LED rows occupy 5.5 dp, with 1 dp of edge spacing.
            val inset = (6.5f * resources.displayMetrics.density).toInt()
            val radius = maxOf(radii.first, radii.second)
            val stripTop = (resources.displayMetrics.density).toInt()
            params.leftMargin += inset
            params.rightMargin += inset
            // The LED surface draws first; overlap its empty center with the row.
            val requestedRowHeight = params.height.coerceAtLeast(0)
            val bottomInset = (3.1f * resources.displayMetrics.density).toInt()
            indicatorView?.layoutParams?.height = maxOf(radius + stripTop, requestedRowHeight + bottomInset)
            // A fixed-height row does not honor minimumHeight during measurement.
            // Overlapping more than that height puts the LED surface above the
            // row's top, while LinearLayout still reserves its full height below.
            // onLayout expands the row to meet the LEDs after measurement.
            val overlap = if (params.height >= 0) requestedRowHeight
                else (radius + stripTop - inset).coerceAtLeast(0)
            params.bottomMargin -= overlap
            row.minimumHeight = maxOf(originalRowMinHeight, overlap)
            row.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val left = (radii.first - inset).coerceAtLeast(0).toFloat()
                    val right = (radii.second - inset).coerceAtLeast(0).toFloat()
                    val bottomExtension = inset - (3.1f * resources.displayMetrics.density).toInt()
                    val path = Path().apply {
                        addRoundRect(
                            RectF(0f, -2f * radius, view.width.toFloat(), view.height.toFloat()),
                            floatArrayOf(0f, 0f, 0f, 0f, right, right + bottomExtension, left, left + bottomExtension),
                            Path.Direction.CW
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) outline.setPath(path)
                    else {
                        @Suppress("DEPRECATION")
                        outline.setConvexPath(path)
                    }
                }
            }
            row.clipToOutline = true
            row.invalidateOutline()
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            originalIconTransforms.forEach { (icon, original) ->
                icon.scaleType = original.first
                icon.imageMatrix = original.second
            }
            originalIconTransforms.clear()
            val row = nestedRow as? ViewGroup ?: return
            val originalContentHeight = row.height
            // A fixed-height row can be shorter than the requested overlap.
            // Anchor its actual bottom to the inner LED contour after layout.
            // The straight lower indicators occupy only the lower LED row;
            // their top edge is closer to the bottom than the two-row side arcs.
            val bottomInset = (3.1f * resources.displayMetrics.density).toInt()
            surfaceView?.let { surface ->
                val targetBottom = surface.bottom - bottomInset
                val extraHeight = (targetBottom - row.bottom).coerceAtLeast(0)
                // Fill the space up to the row's original top instead of translating
                // a short row downward and exposing an empty band above it.
                fun extendContent(view: View) {
                    val oldTop = if (view === row) view.top else 0
                    val oldLeft = view.left
                    val oldWidth = view.width
                    val oldHeight = view.height
                    val targetHeight = if (view === row) oldHeight + extraHeight
                        else (view.parent as View).height
                    view.measure(
                        MeasureSpec.makeMeasureSpec(oldWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY)
                    )
                    view.layout(oldLeft, oldTop, oldLeft + oldWidth, oldTop + targetHeight)
                    if (view is ViewGroup) {
                        for (index in 0 until view.childCount) {
                            val child = view.getChildAt(index)
                            if (child.visibility == View.VISIBLE &&
                                child.height >= originalContentHeight - 4f * resources.displayMetrics.density &&
                                child.height <= view.height
                            ) extendContent(child)
                        }
                    }
                }
                if (extraHeight > 0) extendContent(row)
            }
            // Fixed-height button containers otherwise leave unused space beneath
            // their contents when the corner geometry makes the row taller.
            for (index in 0 until row.childCount) {
                val child = row.getChildAt(index)
                if (child.visibility == View.VISIBLE && child.height < row.height) {
                    child.offsetTopAndBottom(row.height - row.paddingBottom - child.bottom)
                }
            }
            val radii = bottomCornerRadiiPx ?: return
            fun fitIcons(view: View, offsetX: Int) {
                if (view is ImageView && view.visibility == View.VISIBLE) {
                    val onLeft = offsetX < radii.first
                    val onRight = offsetX + view.width > row.width - radii.second
                    val drawable = view.drawable
                    if ((onLeft || onRight) && drawable != null &&
                        drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0
                    ) {
                        originalIconTransforms[view] = view.scaleType to Matrix(view.imageMatrix)
                        val iconFraction = if (row === compactStatusRow) 0.64f else 0.48f
                        val iconHeight = if (row === compactStatusRow) {
                            (minOf(view.height, originalContentHeight) - 4f * resources.displayMetrics.density).coerceAtLeast(1f)
                        } else view.height.toFloat()
                        val size = minOf(view.width.toFloat(), iconHeight) * iconFraction
                        val requestedScale = size / maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight)
                        val scale = if (row === compactStatusRow) requestedScale else minOf(1f, requestedScale)
                        val inward = 4f * resources.displayMetrics.density * if (onLeft) 1f else -1f
                        view.scaleType = ImageView.ScaleType.MATRIX
                        view.imageMatrix = Matrix().apply {
                            setScale(scale, scale)
                            postTranslate(
                                (view.width - drawable.intrinsicWidth * scale) / 2f - view.paddingLeft + inward,
                                (view.height - drawable.intrinsicHeight * scale) / 2f - view.paddingTop -
                                    2f * resources.displayMetrics.density
                            )
                        }
                    }
                }
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) {
                        val child = view.getChildAt(index)
                        fitIcons(child, offsetX + child.left)
                    }
                }
            }
            fitIcons(row, 0)
        }

        // (left, right) display corner radii in px; null disables the outline clip.
        var bottomCornerRadiiPx: Pair<Int, Int>? = null
            set(value) {
                if (field == value) return
                field = value
                applyBottomCornerClip()
                requestLayout()
            }

        var surfaceView: View? = null
            set(value) {
                field = value
                requestLayout()
            }
        var softwareKeyboardModeActive: Boolean = false
            set(value) {
                if (field == value) return
                field = value
                requestLayout()
            }

        init {
            setChildrenDrawingOrderEnabled(true)
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            screenAwakeController.onTouchAction(event.actionMasked)
            return super.dispatchTouchEvent(event)
        }

        // Extend the shape above the view so even a bar shorter than the display
        // radius follows the original arc, rather than shrinking it to fit the bar.
        private fun applyBottomCornerClip() {
            val radii = bottomCornerRadiiPx
            val radius = radii?.let { maxOf(it.first, it.second) } ?: 0
            if (radius <= 0) {
                clipToOutline = false
                outlineProvider = ViewOutlineProvider.BACKGROUND
                return
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val left = radii!!.first.coerceIn(0, view.width / 2).toFloat()
                    val right = radii.second.coerceIn(0, view.width / 2).toFloat()
                    val path = Path().apply {
                        addRoundRect(
                            RectF(0f, -2f * radius, view.width.toFloat(), view.height.toFloat()),
                            floatArrayOf(0f, 0f, 0f, 0f, right, right, left, left),
                            Path.Direction.CW
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        outline.setPath(path)
                    } else {
                        @Suppress("DEPRECATION")
                        outline.setConvexPath(path)
                    }
                }
            }
            clipToOutline = true
            invalidateOutline()
        }

        override fun onDetachedFromWindow() {
            screenAwakeController.release()
            super.onDetachedFromWindow()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            updateNestedStatusRow()
            val surface = surfaceView
            if (!softwareKeyboardModeActive || surface == null || surface.visibility == View.GONE) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }

            var totalChildHeight = 0
            var maxWidth = 0

            for (index in 0 until childCount) {
                val child = getChildAt(index)
                if (child.visibility == View.GONE) continue
                measureChildWithMargins(
                    child,
                    widthMeasureSpec,
                    0,
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    0
                )
                val params = child.layoutParams as MarginLayoutParams
                totalChildHeight += child.measuredHeight + params.topMargin + params.bottomMargin
                maxWidth = maxOf(maxWidth, child.measuredWidth + params.leftMargin + params.rightMargin)
            }

            val measuredWidth = resolveSize(maxWidth + paddingLeft + paddingRight, widthMeasureSpec)
            val desiredHeight = paddingTop + paddingBottom + totalChildHeight
            val measuredHeight = desiredHeight
            setMeasuredDimension(measuredWidth, measuredHeight)
        }

        override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int {
            val surfaceIndex = surfaceView
                ?.let(::indexOfChild)
                ?.takeIf { it in 0 until childCount }
                ?: return super.getChildDrawingOrder(childCount, drawingPosition)

            return if (drawingPosition == 0) {
                surfaceIndex
            } else {
                val shiftedPosition = drawingPosition - 1
                if (shiftedPosition < surfaceIndex) shiftedPosition else shiftedPosition + 1
            }
        }
    }

}
