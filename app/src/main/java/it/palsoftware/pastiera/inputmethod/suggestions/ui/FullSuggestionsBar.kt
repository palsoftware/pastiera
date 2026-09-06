package it.palsoftware.pastiera.inputmethod.suggestions.ui

import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.graphics.drawable.StateListDrawable
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsActivity
import it.palsoftware.pastiera.inputmethod.NotificationHelper
import it.palsoftware.pastiera.inputmethod.suggestions.SuggestionButtonHandler
import it.palsoftware.pastiera.inputmethod.VariationButtonHandler
import it.palsoftware.pastiera.inputmethod.ui.HamburgerMenuView
import it.palsoftware.pastiera.inputmethod.ui.KeyboardThemeColors
import it.palsoftware.pastiera.inputmethod.ui.ModifierIndicatorView
import it.palsoftware.pastiera.inputmethod.StatusBarController
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonHost
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonId
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonRegistry
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonPosition
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonStyles
import android.view.inputmethod.InputMethodManager
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils.languageCode

/**
 * Renders the full-width suggestion bar with up to 3 items. Always occupies
 * a row (with placeholders) so the UI stays stable. Hidden when minimal UI
 * is forced or smart features are disabled by the caller.
 * Includes configurable Pastierina mode buttons on the left/right edges.
 */
class FullSuggestionsBar(
    private val context: Context,
    private val buttonRegistry: StatusBarButtonRegistry? = null,
    private val callbacksProvider: (() -> StatusBarCallbacks)? = null
) {

    companion object {
        private val PRESSED_BLUE = Color.rgb(100, 150, 255) // Align with variation bar press state
        private val DEFAULT_SUGGESTION_COLOR = Color.argb(100, 17, 17, 17)
        private const val FLASH_DURATION_MS = 160L
        private const val BASE_HEIGHT_DP = 36f
    }

    private var container: LinearLayout? = null
    private var frameContainer: FrameLayout? = null
    private var minimalLeftButtonsContainer: LinearLayout? = null
    private var minimalRightButtonsContainer: LinearLayout? = null
    private var modifierIndicatorsContainer: LinearLayout? = null
    private var hamburgerMenuView: HamburgerMenuView? = null
    private var modifierIndicatorView: ModifierIndicatorView? = null
    private var lastMinimalUiActive: Boolean? = null
    private var lastSlots: List<String?> = emptyList()
    private var assets: AssetManager? = null
    private var imeServiceClass: Class<*>? = null
    private var showMinimalUiButtons: Boolean = false
    private var showModifierMenuIndicators: Boolean = false
    private val buttonHost = buttonRegistry?.let { StatusBarButtonHost(context, it) }
    private val suggestionButtons: MutableList<TextView> = mutableListOf()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var reenableSuggestionsAccessibilityRunnable: Runnable? = null
    private var liveAnnouncementsEnabled: Boolean = false
    private var suggestionsAnnouncementDelayMs: Long = 600L
    private var lastAnnouncedSlots: List<String?> = emptyList()
    private var actionCandidate: String? = null
    private var actionSlots: List<String?> = emptyList()
    var requireDictionaryForSuggestions: Boolean = true
    var themeOverride: KeyboardThemeColors? = null
        set(value) {
            if (field == value) {
                return
            }
            field = value
            hamburgerMenuView?.themeOverride = value
            modifierIndicatorView?.themeOverride = value
            buttonHost?.themeOverride = value?.let {
                StatusBarButtonStyles.ThemeOverride(
                    normalColor = it.statusBarButton,
                    pressedColor = it.accent,
                    iconColor = it.textAndIcons,
                    cornerRadiusRatio = it.keyCornerRadiusRatio,
                    borderColor = it.divider,
                    borderWidthPx = dpToPx(1f)
                )
            }
            if (lastSlots.isNotEmpty()) lastSlots = emptyList()
            applyHeight()
        }

    @Suppress("DEPRECATION")
    private fun View.announceForAccessibilityCompat(text: CharSequence) {
        announceForAccessibility(text)
    }

    private val targetHeightPx: Int
        get() = dpToPx(BASE_HEIGHT_DP * (themeOverride?.suggestionsHeightScale ?: 1f).coerceIn(0.65f, 1.6f))

    /**
     * Sets the assets and IME service class needed for subtype cycling.
     */
    fun setSubtypeCyclingParams(assets: AssetManager, imeServiceClass: Class<*>) {
        this.assets = assets
        this.imeServiceClass = imeServiceClass
    }

    fun setAccessibilityAnnouncementConfig(
        liveAnnouncementsEnabled: Boolean,
        suggestionsAnnouncementDelayMs: Long
    ) {
        this.liveAnnouncementsEnabled = liveAnnouncementsEnabled
        this.suggestionsAnnouncementDelayMs = suggestionsAnnouncementDelayMs
            .coerceAtLeast(0L)
    }

    fun ensureView(): FrameLayout {
        if (frameContainer == null) {
            // Create frame container to allow overlaying the language button
            frameContainer = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    targetHeightPx
                )
                visibility = View.GONE
                minimumHeight = targetHeightPx
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            }
            
            // Create the suggestions container
            container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    targetHeightPx
                )
                visibility = View.GONE
                minimumHeight = targetHeightPx
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
            }
            
            minimalLeftButtonsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                visibility = View.GONE
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    targetHeightPx
                ).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
            }

            minimalRightButtonsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                visibility = View.GONE
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    targetHeightPx
                ).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                }
            }

            modifierIndicatorView = ModifierIndicatorView(context).apply {
                themeOverride = this@FullSuggestionsBar.themeOverride
            }
            modifierIndicatorsContainer = modifierIndicatorView?.ensureView()?.apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    targetHeightPx
                ).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
            }
            
            frameContainer?.addView(container)
            modifierIndicatorsContainer?.let { frameContainer?.addView(it) }
            minimalLeftButtonsContainer?.let { frameContainer?.addView(it) }
            minimalRightButtonsContainer?.let { frameContainer?.addView(it) }
            
            // Create hamburger menu view if buttonRegistry and callbacks are available
            frameContainer?.let { frame ->
                if (buttonRegistry != null && callbacksProvider != null) {
                    if (hamburgerMenuView == null) {
                        hamburgerMenuView = HamburgerMenuView(context, buttonRegistry).apply {
                            themeOverride = this@FullSuggestionsBar.themeOverride
                        }
                    }
                }
                hamburgerMenuView?.attachTo(frame)
                lastMinimalUiActive?.let { hamburgerMenuView?.setMinimalUiActive(it) }
            }
            // Ensure the outer layout (when attached to parent LinearLayout) keeps the target height
            frameContainer?.layoutParams = (frameContainer?.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, targetHeightPx)
        }
        return frameContainer!!
    }

    private fun applyHeight() {
        val height = targetHeightPx
        frameContainer?.let { frame ->
            val params = (frame.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            if (params.height != height || params.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = height
                frame.layoutParams = params
            }
            frame.minimumHeight = height
        }
        container?.let { bar ->
            val params = (bar.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            if (params.height != height || params.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = height
                bar.layoutParams = params
            }
            bar.minimumHeight = height
        }
        listOfNotNull(minimalLeftButtonsContainer, minimalRightButtonsContainer).forEach { buttons ->
            val params = (buttons.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height)
            if (params.height != height) {
                params.height = height
                buttons.layoutParams = params
            }
        }
        modifierIndicatorsContainer?.let { indicators ->
            val params = (indicators.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height)
            if (params.height != height) {
                params.height = height
                indicators.layoutParams = params
            }
        }
    }
    
    private fun toggleHamburgerMenu() {
        val menu = hamburgerMenuView ?: return
        val callbacks = callbacksProvider?.invoke() ?: return

        if (menu.isVisible()) {
            menu.hide()
        } else {
            menu.show(callbacks) {
                menu.hide()
            }
        }
    }
    
    /**
     * Sets the microphone button active state.
     */
    fun setMicrophoneButtonActive(isActive: Boolean) {
        hamburgerMenuView?.setMicrophoneActive(isActive)
        buttonHost?.setMicrophoneActive(isActive)
    }
    
    /**
     * Updates the microphone button visual feedback based on audio level.
     */
    fun updateMicrophoneAudioLevel(rmsdB: Float) {
        hamburgerMenuView?.updateMicrophoneAudioLevel(rmsdB)
        buttonHost?.updateMicrophoneAudioLevel(rmsdB)
    }
    
    /**
     * Updates the clipboard badge count.
     */
    fun updateClipboardCount(count: Int) {
        hamburgerMenuView?.updateClipboardCount(count)
        buttonHost?.updateClipboardCount(count)
    }
    
    /**
     * Refreshes the language button text.
     */
    fun refreshLanguageText() {
        hamburgerMenuView?.refreshLanguageText()
        buttonHost?.refreshLanguageText()
    }

    fun setMinimalUiActive(isActive: Boolean) {
        lastMinimalUiActive = isActive
        showMinimalUiButtons = isActive
        renderMinimalUiButtons()
        hamburgerMenuView?.setMinimalUiActive(isActive)
    }

    fun setModifierMenuIndicatorsEnabled(enabled: Boolean) {
        showModifierMenuIndicators = enabled
        if (!enabled) {
            modifierIndicatorsContainer?.visibility = View.GONE
        }
        applyContainerInsetsForMinimalButtons()
        frameContainer?.requestLayout()
    }

    fun updateModifierIndicators(snapshot: StatusBarController.StatusSnapshot) {
        val visible = showModifierMenuIndicators && modifierIndicatorView?.update(snapshot) == true
        modifierIndicatorsContainer?.visibility = if (visible) View.VISIBLE else View.GONE
        applyContainerInsetsForMinimalButtons()
        frameContainer?.requestLayout()
    }

    fun isHamburgerMenuVisible(): Boolean = hamburgerMenuView?.isVisible() == true

    fun hideHamburgerMenu() {
        hamburgerMenuView?.hide()
    }

    fun resetActionMode() {
        actionCandidate = null
        actionSlots = emptyList()
        lastSlots = emptyList()
    }

    /**
     * Checks if a dictionary file exists for the current IME subtype.
     * Returns true if a dictionary is found (serialized format).
     */
    private fun hasDictionaryForCurrentSubtype(): Boolean {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val currentSubtype = imm?.currentInputMethodSubtype
            val langCode = currentSubtype?.languageCode() ?: return false
            it.palsoftware.pastiera.core.suggestions.AndroidDictionaryRepository.hasDictionaryForLocale(context, langCode)
        } catch (e: Exception) {
            false
        }
    }

    fun update(
        suggestions: List<String>,
        shouldShow: Boolean,
        inputConnection: android.view.inputmethod.InputConnection?,
        listener: VariationButtonHandler.OnVariationSelectedListener?,
        shouldDisableSuggestions: Boolean,
        addWordCandidate: String?,
        onAddUserWord: ((String) -> Unit)?,
        onAddUserWordSubstitutionRequested: ((String) -> Unit)?,
        onSuggestionCommitted: (() -> Unit)?,
        onHideSuggestion: ((String) -> Unit)?,
        onDeleteUserSuggestion: ((String) -> Unit)?,
        canDeleteUserSuggestion: ((String) -> Boolean)?,
        onSuggestionSelectedOverride: ((Int, String) -> Unit)? = null
    ) {
        val bar = container ?: return
        val frame = frameContainer ?: return
        
        // Hide suggestions when unavailable, but keep Pastierina buttons visible.
        val hasDictionary = !requireDictionaryForSuggestions || hasDictionaryForCurrentSubtype()
        val canShowSuggestions = shouldShow && hasDictionary
        if (!canShowSuggestions && !showMinimalUiButtons) {
            cancelPendingSuggestionsAccessibilityEnable()
            suggestionButtons.clear()
            frame.visibility = View.GONE
            bar.visibility = View.GONE
            bar.removeAllViews()
            renderMinimalUiButtons()
            lastSlots = emptyList()
            lastAnnouncedSlots = emptyList()
            actionCandidate = null
            actionSlots = emptyList()
            return
        }

        frame.visibility = View.VISIBLE
        frame.alpha = 1f
        bar.alpha = 1f
        renderMinimalUiButtons()
        applyContainerInsetsForMinimalButtons()

        if (!canShowSuggestions) {
            cancelPendingSuggestionsAccessibilityEnable()
            suggestionButtons.clear()
            bar.visibility = View.GONE
            bar.removeAllViews()
            lastSlots = emptyList()
            lastAnnouncedSlots = emptyList()
            actionCandidate = null
            actionSlots = emptyList()
            return
        }

        val slots = buildSlots(suggestions, addWordCandidate)
        applySuggestionsAccessibilityThrottle(slots)
        if (actionCandidate != null && slots != actionSlots) {
            actionCandidate = null
            actionSlots = emptyList()
        }
        if (actionCandidate == null && slots == lastSlots && bar.childCount > 0) {
            bar.visibility = View.VISIBLE
            bar.alpha = 1f
            frame.alpha = 1f
            for (index in 0 until bar.childCount) {
                bar.getChildAt(index).alpha = 1f
                bar.getChildAt(index).visibility = View.VISIBLE
            }
            return
        }

        renderSlots(
            bar,
            slots,
            inputConnection,
            listener,
            shouldDisableSuggestions,
            addWordCandidate,
            onAddUserWord,
            onAddUserWordSubstitutionRequested,
            onSuggestionCommitted,
            onHideSuggestion,
            onDeleteUserSuggestion,
            canDeleteUserSuggestion,
            onSuggestionSelectedOverride
        )
        lastSlots = slots
    }

    fun showPreview(suggestions: List<String>, showStatusButton: Boolean = false) {
        val frame = ensureView()
        val bar = container ?: return
        frame.visibility = View.VISIBLE
        bar.visibility = View.VISIBLE
        showMinimalUiButtons = showStatusButton
        renderMinimalUiButtons()
        applyContainerInsetsForMinimalButtons()
        renderSlots(
            bar = bar,
            slots = listOf(suggestions.getOrNull(2), suggestions.getOrNull(0), suggestions.getOrNull(1)),
            inputConnection = null,
            listener = null,
            shouldDisableSuggestions = false,
            addWordCandidate = null,
            onAddUserWord = null,
            onAddUserWordSubstitutionRequested = null,
            onSuggestionCommitted = null,
            onHideSuggestion = null,
            onDeleteUserSuggestion = null,
            canDeleteUserSuggestion = null,
            onSuggestionSelectedOverride = null
        )
        lastSlots = emptyList()
    }

    private fun openSettings() {
        try {
            val intent = Intent(context, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Ignore failures to avoid crashing the suggestions bar
        }
    }

    private fun gaplessChrome(): Boolean =
        it.palsoftware.pastiera.SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)

    private fun chromeSpacingPx(): Int = if (gaplessChrome()) 0 else dpToPx(3f)

    private fun minimalButtonWidthPx(): Int {
        val size = (targetHeightPx - dpToPx(4f)).coerceAtLeast(dpToPx(24f))
        return if (it.palsoftware.pastiera.SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) {
            maxOf(dpToPx(56f), (size * 1.6f).toInt())
        } else size
    }

    private fun renderMinimalUiButtons() {
        val leftContainer = minimalLeftButtonsContainer ?: return
        val rightContainer = minimalRightButtonsContainer ?: return
        val registry = buttonRegistry ?: return
        val host = buttonHost ?: return

        leftContainer.removeAllViews()
        rightContainer.removeAllViews()
        if (!showMinimalUiButtons) {
            leftContainer.visibility = View.GONE
            rightContainer.visibility = View.GONE
            applyContainerInsetsForMinimalButtons()
            return
        }

        val buttonSize = (targetHeightPx - dpToPx(4f)).coerceAtLeast(dpToPx(24f))
        val buttonWidth = minimalButtonWidthPx()
        val buttonHeight = if (it.palsoftware.pastiera.SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) {
            targetHeightPx
        } else buttonSize
        val spacing = chromeSpacingPx()
        val callbacks = (callbacksProvider?.invoke() ?: StatusBarCallbacks())
            .copy(onHamburgerMenuRequested = { toggleHamburgerMenu() })

        fun addButton(buttonId: StatusBarButtonId, target: LinearLayout, isLast: Boolean) {
            val hosted = host.getOrCreateButton(
                id = buttonId,
                size = buttonSize,
                callbacks = callbacks,
                width = buttonWidth,
                height = buttonHeight
            ) ?: return
            hosted.container.layoutParams = LinearLayout.LayoutParams(buttonWidth, buttonHeight).apply {
                marginEnd = if (isLast) 0 else spacing
            }
            target.addView(hosted.container)
        }

        val enabledButtons = registry.getEnabledPastierinaButtons(context)
        val leftButtons = enabledButtons
            .filter { it.position == StatusBarButtonPosition.LEFT }
            .sortedBy { it.order }
        val rightButtons = enabledButtons
            .filter { it.position == StatusBarButtonPosition.RIGHT }
            .sortedBy { it.order }

        leftButtons.forEachIndexed { index, config ->
            addButton(config.id, leftContainer, index == leftButtons.lastIndex)
        }
        rightButtons.forEachIndexed { index, config ->
            addButton(config.id, rightContainer, index == rightButtons.lastIndex)
        }

        leftContainer.visibility = if (leftButtons.isEmpty()) View.GONE else View.VISIBLE
        rightContainer.visibility = if (rightButtons.isEmpty()) View.GONE else View.VISIBLE
        applyContainerInsetsForMinimalButtons()
    }

    private fun applyContainerInsetsForMinimalButtons() {
        val bar = container ?: return
        val spacing = chromeSpacingPx()
        val indicatorInset = modifierIndicatorsContainer?.takeIf {
            showModifierMenuIndicators && it.visibility == View.VISIBLE
        }?.let {
            modifierIndicatorsWidthPx(it) + spacing
        } ?: 0
        val minimalLeftInset = if (showMinimalUiButtons) {
            minimalLeftButtonsContainer?.takeIf { it.visibility == View.VISIBLE }?.let {
                it.childCount * minimalButtonWidthPx() +
                    (it.childCount - 1).coerceAtLeast(0) * spacing +
                    spacing
            } ?: 0
        } else {
            0
        }
        minimalLeftButtonsContainer?.let { buttons ->
            val params = (buttons.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, targetHeightPx).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
            if (params.marginStart != indicatorInset) {
                params.marginStart = indicatorInset
                buttons.layoutParams = params
            }
        }
        val leftInset = indicatorInset + minimalLeftInset
        val rightInset = if (showMinimalUiButtons) {
            minimalRightButtonsContainer?.takeIf { it.visibility == View.VISIBLE }?.let {
                it.childCount * minimalButtonWidthPx() +
                    (it.childCount - 1).coerceAtLeast(0) * spacing +
                    spacing
            } ?: 0
        } else {
            0
        }
        val params = (bar.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                targetHeightPx
            )
        var changed = false
        if (params.height != targetHeightPx) {
            params.height = targetHeightPx
            changed = true
        }
        if (params.marginStart != leftInset) {
            params.marginStart = leftInset
            changed = true
            lastSlots = emptyList()
        }
        if (params.marginEnd != rightInset) {
            params.marginEnd = rightInset
            changed = true
            lastSlots = emptyList()
        }
        if (changed || bar.layoutParams !is FrameLayout.LayoutParams) {
            bar.layoutParams = params
        }
    }

    private fun modifierIndicatorsWidthPx(indicators: LinearLayout): Int {
        val childCount = indicators.childCount
        if (childCount <= 0) {
            return 0
        }
        val chipWidth = dpToPx(26f)
        val chipGap = dpToPx(2f)
        return indicators.paddingLeft +
            indicators.paddingRight +
            childCount * chipWidth +
            (childCount - 1).coerceAtLeast(0) * chipGap
    }

    private fun renderSlots(
        bar: LinearLayout,
        slots: List<String?>,
        inputConnection: android.view.inputmethod.InputConnection?,
        listener: VariationButtonHandler.OnVariationSelectedListener?,
        shouldDisableSuggestions: Boolean,
        addWordCandidate: String?,
        onAddUserWord: ((String) -> Unit)?,
        onAddUserWordSubstitutionRequested: ((String) -> Unit)?,
        onSuggestionCommitted: (() -> Unit)?,
        onHideSuggestion: ((String) -> Unit)?,
        onDeleteUserSuggestion: ((String) -> Unit)?,
        canDeleteUserSuggestion: ((String) -> Boolean)?,
        onSuggestionSelectedOverride: ((Int, String) -> Unit)?
    ) {
        bar.removeAllViews()
        suggestionButtons.clear()
        bar.visibility = View.VISIBLE
        bar.alpha = 1f
        frameContainer?.alpha = 1f

        // Force bar and frame to the target height to avoid fallback to wrap_content.
        applyContainerInsetsForMinimalButtons()
        (frameContainer?.layoutParams as? ViewGroup.LayoutParams)?.let { lp ->
            lp.height = targetHeightPx
            frameContainer?.layoutParams = lp
        }
        bar.minimumHeight = targetHeightPx
        frameContainer?.minimumHeight = targetHeightPx

        val heightScale = (themeOverride?.suggestionsHeightScale ?: 1f).coerceIn(0.65f, 1.6f)
        val maxTextSp = (14f * heightScale).coerceIn(12f, 20f).toInt()
        val minTextSp = (7f * heightScale).coerceIn(7f, 12f).toInt()
        val padV = dpToPx(3f * heightScale)
        val padH = dpToPx(12f)

        val addOnly = addWordCandidate != null &&
            slots[0]?.equals(addWordCandidate, ignoreCase = true) == true &&
            slots[1] == null &&
            slots[2] == null
        val slotOrder = if (addOnly) {
            listOf(slots[0])
        } else {
            listOf(slots[0], slots[1], slots[2]) // left, center, right
        }
        for ((index, suggestion) in slotOrder.withIndex()) {
            val slotIndex = suggestionButtons.size
            val weightLayoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (addOnly) 3f else 1f
            ).apply {
                // Apply margin only if not the last suggestion box
                if (index < slotOrder.size - 1) {
                    marginEnd = chromeSpacingPx()
                }
            }
            if (suggestion != null && actionCandidate?.equals(suggestion, ignoreCase = true) == true) {
                val actionSlot = buildSuggestionActionSlot(
                    candidate = suggestion,
                    weightLayoutParams = weightLayoutParams,
                    onHideSuggestion = onHideSuggestion,
                    onDeleteUserSuggestion = onDeleteUserSuggestion,
                    canDeleteUserSuggestion = canDeleteUserSuggestion
                )
                bar.addView(actionSlot)
                continue
            }
            val button = TextView(context).apply {
                alpha = 1f
                text = (suggestion ?: "")
                gravity = Gravity.CENTER
                textSize = maxTextSp.toFloat()
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this,
                    minTextSp,
                    maxTextSp,
                    1,
                    TypedValue.COMPLEX_UNIT_SP
                )
                includeFontPadding = false
                minHeight = 0
                setTextColor(themeOverride?.textAndIcons ?: Color.WHITE)
                setTypeface(null, android.graphics.Typeface.NORMAL)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(padH, padV, padH, padV)
                background = buildSuggestionBackground()
                layoutParams = weightLayoutParams
                isClickable = suggestion != null
                isFocusable = suggestion != null
                importantForAccessibility = if (suggestion.isNullOrBlank()) {
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO
                } else {
                    View.IMPORTANT_FOR_ACCESSIBILITY_YES
                }
                if (suggestion != null) {
                    if (onSuggestionSelectedOverride != null) {
                        setOnClickListener { view ->
                            resetActionMode()
                            flashSlot(slotIndex)
                            NotificationHelper.triggerTapHapticFeedback(view)
                            onSuggestionSelectedOverride(slotIndex, suggestion)
                        }
                    } else if (addWordCandidate != null && suggestion.equals(addWordCandidate, ignoreCase = true)) {
                        val addDrawable = androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.ic_input_add)?.mutate()
                        addDrawable?.setTint(Color.YELLOW)
                        addDrawable?.setBounds(0, 0, dpToPx(18f), dpToPx(18f))
                        setCompoundDrawables(null, null, addDrawable, null)
                        compoundDrawablePadding = dpToPx(6f)
                        setOnClickListener { view ->
                            resetActionMode()
                            flashSlot(slotIndex)
                            NotificationHelper.triggerTapHapticFeedback(view)
                            onAddUserWord?.invoke(suggestion)
                        }
                        setOnLongClickListener {
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            resetActionMode()
                            onAddUserWordSubstitutionRequested?.invoke(suggestion)
                            true
                        }
                    } else {
                        val clickListener = SuggestionButtonHandler.createSuggestionClickListener(
                            suggestion,
                            inputConnection,
                            listener,
                            shouldDisableSuggestions,
                            onSuggestionCommitted
                        )
                        setOnClickListener { view ->
                            resetActionMode()
                            flashSlot(slotIndex)
                            NotificationHelper.triggerTapHapticFeedback(view)
                            clickListener.onClick(view)
                        }
                        setOnLongClickListener {
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            actionCandidate = suggestion
                            actionSlots = slots
                            lastSlots = emptyList()
                            renderSlots(
                                bar = bar,
                                slots = slots,
                                inputConnection = inputConnection,
                                listener = listener,
                                shouldDisableSuggestions = shouldDisableSuggestions,
                                addWordCandidate = addWordCandidate,
                                onAddUserWord = onAddUserWord,
                                onAddUserWordSubstitutionRequested = onAddUserWordSubstitutionRequested,
                                onSuggestionCommitted = onSuggestionCommitted,
                                onHideSuggestion = onHideSuggestion,
                                onDeleteUserSuggestion = onDeleteUserSuggestion,
                                canDeleteUserSuggestion = canDeleteUserSuggestion,
                                onSuggestionSelectedOverride = onSuggestionSelectedOverride
                            )
                            true
                        }
                    }
                }
            }
            bar.addView(button)
            suggestionButtons.add(button)
        }
    }

    private fun buildSuggestionActionSlot(
        candidate: String,
        weightLayoutParams: LinearLayout.LayoutParams,
        onHideSuggestion: ((String) -> Unit)?,
        onDeleteUserSuggestion: ((String) -> Unit)?,
        canDeleteUserSuggestion: ((String) -> Boolean)?
    ): LinearLayout {
        val canDelete = canDeleteUserSuggestion?.invoke(candidate) == true
        val actions = buildList {
            add(ActionButtonSpec(android.R.drawable.ic_menu_view, themeOverride?.statusBarButton ?: Color.rgb(68, 92, 140), themeOverride?.textAndIcons ?: Color.WHITE) {
                onHideSuggestion?.invoke(candidate)
            })
            if (canDelete) {
                add(ActionButtonSpec(android.R.drawable.ic_menu_delete, themeOverride?.statusBarButton ?: Color.rgb(120, 52, 58), themeOverride?.textAndIcons ?: Color.WHITE) {
                    onDeleteUserSuggestion?.invoke(candidate)
                })
            }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = weightLayoutParams
            background = buildSuggestionBackground()
            val padding = if (gaplessChrome()) 0 else dpToPx(4f)
            setPadding(padding, padding, padding, padding)

        actions.forEachIndexed { index, action ->
            val button = ImageView(context).apply {
                ContextCompat.getDrawable(context, action.iconRes)?.mutate()?.let { icon ->
                    icon.setTint(action.iconColor)
                    setImageDrawable(icon)
                }
                scaleType = ImageView.ScaleType.CENTER
                background = buildActionBackground(action.backgroundColor)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                ).apply {
                    if (index < actions.lastIndex) marginEnd = chromeSpacingPx()
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    resetActionMode()
                    action.onClick()
                }
                setOnLongClickListener {
                    resetActionMode()
                    true
                }
            }
            addView(button)
        }
        }
    }

    private fun buildActionBackground(color: Int): StateListDrawable {
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = if (gaplessChrome()) 0f else dpToPx(7f).toFloat()
            setColor(color)
        }
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = if (gaplessChrome()) 0f else dpToPx(7f).toFloat()
            setColor(themeOverride?.accent ?: PRESSED_BLUE)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    private data class ActionButtonSpec(
        val iconRes: Int,
        val backgroundColor: Int,
        val iconColor: Int,
        val onClick: () -> Unit
    )

    private fun buildSlots(suggestions: List<String>, addWordCandidate: String?): List<String?> {
        val s0 = suggestions.getOrNull(0)
        val s1 = suggestions.getOrNull(1)
        val addCandidate = addWordCandidate?.takeUnless { candidate ->
            suggestions.any { it.equals(candidate, ignoreCase = true) }
        }
        val s2 = addCandidate ?: suggestions.getOrNull(2)
        return listOf(
            // left
            s2,
            // center
            s0,
            // right
            if (suggestions.size >= 2) s1 else null
        )
    }

    private fun applySuggestionsAccessibilityThrottle(slots: List<String?>) {
        val bar = container ?: return
        val hasVisibleSuggestions = slots.any { !it.isNullOrBlank() }
        if (!hasVisibleSuggestions || !liveAnnouncementsEnabled) {
            cancelPendingSuggestionsAccessibilityEnable()
            if (bar.importantForAccessibility != View.IMPORTANT_FOR_ACCESSIBILITY_YES) {
                bar.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
            if (bar.accessibilityLiveRegion != View.ACCESSIBILITY_LIVE_REGION_NONE) {
                bar.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
            }
            return
        }

        if (bar.importantForAccessibility != View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) {
            bar.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        if (bar.accessibilityLiveRegion != View.ACCESSIBILITY_LIVE_REGION_NONE) {
            bar.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
        }
        cancelPendingSuggestionsAccessibilityEnable()
        val slotsSnapshot = slots.toList()
        val enableRunnable = Runnable {
            if (bar.importantForAccessibility != View.IMPORTANT_FOR_ACCESSIBILITY_YES) {
                bar.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
            if (slotsSnapshot != lastAnnouncedSlots) {
                val announcement = slotsSnapshot
                    .mapNotNull { it?.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(", ")
                if (announcement.isNotBlank()) {
                    bar.announceForAccessibilityCompat(announcement)
                    lastAnnouncedSlots = slotsSnapshot
                }
            }
        }
        reenableSuggestionsAccessibilityRunnable = enableRunnable
        mainHandler.postDelayed(enableRunnable, suggestionsAnnouncementDelayMs)
    }

    private fun cancelPendingSuggestionsAccessibilityEnable() {
        reenableSuggestionsAccessibilityRunnable?.let { mainHandler.removeCallbacks(it) }
        reenableSuggestionsAccessibilityRunnable = null
    }

    /**
     * Briefly highlights the slot that corresponds to the given suggestion index.
     * suggestionIndex uses the original ordering (0=center, 1=right, 2=left).
     */
    fun flashSuggestionAtIndex(suggestionIndex: Int) {
        val slotIndex = when (suggestionIndex) {
            0 -> 1 // center
            1 -> 2 // right
            2 -> 0 // left
            else -> return
        }
        flashSlot(slotIndex)
    }

    private fun flashSlot(slotIndex: Int) {
        val button = suggestionButtons.getOrNull(slotIndex) ?: return
        button.isPressed = true
        button.refreshDrawableState()
        button.postDelayed({
            button.isPressed = false
            button.refreshDrawableState()
        }, FLASH_DURATION_MS)
    }

    private fun buildSuggestionBackground(): StateListDrawable {
        val radiusRatio = if (gaplessChrome()) 0f else themeOverride?.chromeCornerRadiusRatio ?: 0f
        val radius = (targetHeightPx * radiusRatio).coerceAtLeast(0f)
        val normalDrawable = GradientDrawable().apply {
            setColor(themeOverride?.suggestion ?: DEFAULT_SUGGESTION_COLOR)
            cornerRadius = radius
            alpha = 255 // placeholders look identical; they stay non-clickable
            if (!gaplessChrome()) themeOverride?.let { setStroke(dpToPx(1f), it.divider) }
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(themeOverride?.accent ?: PRESSED_BLUE)
            cornerRadius = radius
            if (!gaplessChrome()) themeOverride?.let { setStroke(dpToPx(1f), it.divider) }
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
