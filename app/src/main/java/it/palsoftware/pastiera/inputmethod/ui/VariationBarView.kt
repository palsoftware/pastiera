package it.palsoftware.pastiera.inputmethod.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.UnderlineSpan
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsActivity
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.StatusBarController
import it.palsoftware.pastiera.inputmethod.TextSelectionHelper
import it.palsoftware.pastiera.inputmethod.NotificationHelper
import it.palsoftware.pastiera.inputmethod.VariationButtonHandler
import it.palsoftware.pastiera.inputmethod.SpeechRecognitionActivity
import it.palsoftware.pastiera.data.variation.VariationRepository
import android.graphics.Paint
import android.text.TextUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import android.content.res.AssetManager
import it.palsoftware.pastiera.inputmethod.SubtypeCycler
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonRegistry
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonHost
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarAnimator
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonId
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonPosition
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonStyles

/**
 * Handles the variations row (suggestions + microphone/language) rendered above the LED strip.
 */
class VariationBarView(
    private val context: Context,
    private val assets: AssetManager? = null,
    private val imeServiceClass: Class<*>? = null,
    private val buttonRegistry: StatusBarButtonRegistry? = null
) {
    companion object {
        private const val TAG = "VariationBarView"
        private const val SWIPE_HINT_SHOW_DELAY_MS = 1000L
        private const val BASE_HEIGHT_DP = 55f
        private const val BASE_VERTICAL_PADDING_DP = 4f
    }

    var onVariationSelectedListener: VariationButtonHandler.OnVariationSelectedListener? = null
    var onCursorMovedListener: (() -> Unit)? = null
    var onSpeechRecognitionRequested: (() -> Unit)? = null
    var onAddUserWord: ((String) -> Unit)? = null
    var onAddUserWordSubstitutionRequested: ((String) -> Unit)? = null
    var onLanguageSwitchRequested: (() -> Unit)? = null
    var onClipboardRequested: (() -> Unit)? = null
    var onEmojiPickerRequested: (() -> Unit)? = null
    var onSymbolsPageRequested: (() -> Unit)? = null
    var onUndoRequested: (() -> Unit)? = null
    var onRedoRequested: (() -> Unit)? = null
    var onHamburgerMenuRequested: (() -> Unit)? = null
    var onMinimalUiToggleRequested: (() -> Unit)? = null
    var onSoftwareKeyboardModeToggleRequested: (() -> Unit)? = null
    
    /**
     * Sets the microphone button active state (red pulsing background) during speech recognition.
     */
    fun setMicrophoneButtonActive(isActive: Boolean) {
        isMicrophoneActive = isActive
        buttonHost?.setMicrophoneActive(isActive)
    }
    
    /**
     * Updates the microphone button visual feedback based on audio level.
     * Changes the red color intensity based on audio volume.
     * @param rmsdB The RMS audio level in decibels (typically -10 to 0, lower is quieter)
     */
    fun updateMicrophoneAudioLevel(rmsdB: Float) {
        if (!isMicrophoneActive) return
        buttonHost?.updateMicrophoneAudioLevel(rmsdB)
    }


    private var wrapper: FrameLayout? = null
    private var container: LinearLayout? = null
    private var buttonsContainer: LinearLayout? = null
    private var leftButtonsContainer: LinearLayout? = null
    private var overlay: FrameLayout? = null
    private var swipeIndicator: View? = null
    private var emptyHintView: TextView? = null
    private var shouldShowSwipeHint: Boolean = false
    private var currentVariationsRow: LinearLayout? = null
    private var variationButtons: MutableList<TextView> = mutableListOf()
    private var isMicrophoneActive: Boolean = false
    private var lastDisplayedVariations: List<String> = emptyList()
    private var isSymModeActive = false
    private var isShowingSpeechRecognitionHint: Boolean = false
    private var originalHintText: CharSequence? = null
    private var isSwipeInProgress = false
    private var swipeDirection: Int? = null
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var lastCursorMoveX = 0f
    private var currentInputConnection: android.view.inputmethod.InputConnection? = null
    private var staticVariations: List<String> = emptyList()
    private var staticVariationsShift: List<String> = emptyList()
    private var staticVariationsAlt: List<String> = emptyList()
    private var emailVariations: List<String> = emptyList()
    private var lastInputConnectionUsed: android.view.inputmethod.InputConnection? = null
    private var lastIsStaticContent: Boolean? = null
    private var lastVariationAreaVisible: Boolean? = null
    private var lastThemeSignature: Int? = null
    private var lastGeometrySignature: List<Any>? = null
    private var lastSnapshot: StatusBarController.StatusSnapshot? = null
    private var pressedView: View? = null
    private var longPressHandler: Handler? = null
    private var longPressRunnable: Runnable? = null
    private var longPressExecuted: Boolean = false
    private var lastClipboardCount: Int? = null
    private var isTitan2Layout: Boolean = false
    
    // New modular components
    private val statusBarAnimator = StatusBarAnimator()
    private val buttonHost = buttonRegistry?.let { StatusBarButtonHost(context, it) }
    var forceVariationAreaVisible: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            lastVariationAreaVisible = null
        }
    var themeOverride: KeyboardThemeColors? = null
        set(value) {
            if (field == value) {
                return
            }
            field = value
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
            wrapper?.setBackgroundColor(value?.background ?: Color.TRANSPARENT)
            container?.setBackgroundColor(value?.background ?: Color.TRANSPARENT)
            emptyHintView?.setTextColor(colorWithAlpha(value?.textAndIcons ?: Color.WHITE, 120))
            swipeIndicator?.background = createSwipeIndicatorDrawable()
            applyHeight()
            lastThemeSignature = null
        }

    fun ensureView(): FrameLayout {
        if (wrapper != null) {
            return wrapper!!
        }

        val leftPadding = 0 // clipboard flush to the left edge
        val rightPadding = 0 // remove trailing gap so language button sits flush to the right
        val variationsVerticalPadding = verticalPaddingPx()
        val variationsContainerHeight = targetHeightPx()

        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(leftPadding, variationsVerticalPadding, rightPadding, variationsVerticalPadding)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                variationsContainerHeight
            )
            visibility = View.GONE
            setBackgroundColor(themeOverride?.background ?: Color.TRANSPARENT)
            addOnLayoutChangeListener { view, left, _, right, _, oldLeft, _, oldRight, _ ->
                if (right - left != oldRight - oldLeft && view.visibility == View.VISIBLE) {
                    view.post {
                        lastSnapshot?.let { snapshot ->
                            if (view.visibility == View.VISIBLE) showVariations(snapshot, currentInputConnection)
                        }
                    }
                }
            }
        }
        
        // Container for left fixed buttons (clipboard)
        leftButtonsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Container for mic and settings buttons (fixed position on the right)
        buttonsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        leftButtonsContainer?.let { container?.addView(it) }
        container?.addView(buttonsContainer)

        wrapper = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                variationsContainerHeight
            )
            visibility = View.GONE
            setBackgroundColor(themeOverride?.background ?: Color.TRANSPARENT)
            addView(container)
        }

        overlay = FrameLayout(context).apply {
            background = ColorDrawable(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }.also { overlayView ->
            val indicator = createSwipeIndicator()
            swipeIndicator = indicator
            overlayView.addView(indicator)
            val hint = createSwipeHintView()
            emptyHintView = hint
            overlayView.addView(hint)
            wrapper?.addView(overlayView)
            installOverlayTouchListener(overlayView)
        }

        return wrapper!!
    }

    private fun targetHeightPx(): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            BASE_HEIGHT_DP * (themeOverride?.variationsHeightScale ?: 1f).coerceIn(0.65f, 1.6f),
            context.resources.displayMetrics
        ).toInt()

    private fun verticalPaddingPx(): Int =
        if (SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) 0 else TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            BASE_VERTICAL_PADDING_DP * min(
                (themeOverride?.variationsHeightScale ?: 1f).coerceIn(0.65f, 1.6f),
                1f
            ),
            context.resources.displayMetrics
        ).toInt()

    private fun applyHeight() {
        val height = targetHeightPx()
        val verticalPadding = verticalPaddingPx()
        var geometryChanged = false
        container?.let { view ->
            val params = (view.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            if (params.height != height || params.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = height
                view.layoutParams = params
                geometryChanged = true
            }
            if (view.paddingTop != verticalPadding || view.paddingBottom != verticalPadding) {
                view.setPadding(view.paddingLeft, verticalPadding, view.paddingRight, verticalPadding)
                geometryChanged = true
            }
        }
        wrapper?.let { view ->
            val params = (view.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            if (params.height != height || params.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = height
                view.layoutParams = params
                geometryChanged = true
            }
        }
        if (geometryChanged) {
            lastDisplayedVariations = emptyList()
            lastIsStaticContent = null
            lastVariationAreaVisible = null
        }
    }

    fun getWrapper(): FrameLayout? = wrapper

    fun setSymModeActive(active: Boolean) {
        isSymModeActive = active
        if (active) {
            hideSwipeIndicator(immediate = true)
            hideSwipeHintImmediate()
            overlay?.visibility = View.GONE
        }
    }

    /**
     * Updates only the clipboard badge count without rebuilding the row.
     */
    fun updateClipboardCount(count: Int) {
        buttonHost?.updateClipboardCount(count)
        lastClipboardCount = count
    }

    fun updateInputConnection(inputConnection: android.view.inputmethod.InputConnection?) {
        currentInputConnection = inputConnection
    }

    fun resetVariationsState() {
        lastDisplayedVariations = emptyList()
        lastInputConnectionUsed = null
        lastIsStaticContent = null
    }

    fun hideImmediate() {
        currentVariationsRow?.let { row ->
            (row.parent as? ViewGroup)?.removeView(row)
        }
        currentVariationsRow = null
        variationButtons.clear()
        buttonHost?.detachAll()
        hideSwipeIndicator(immediate = true)
        hideSwipeHintImmediate()
        shouldShowSwipeHint = false
        container?.visibility = View.GONE
        wrapper?.visibility = View.GONE
        overlay?.visibility = View.GONE
    }

    fun hideForSym(onHidden: () -> Unit) {
        val containerView = container ?: run {
            onHidden()
            return
        }
        val row = currentVariationsRow
        val overlayView = overlay

        buttonHost?.detachAll()
        hideSwipeIndicator(immediate = true)
        hideSwipeHintImmediate()
        shouldShowSwipeHint = false

        if (row != null && row.parent == containerView && row.visibility == View.VISIBLE) {
            animateVariationsOut(row) {
                (row.parent as? ViewGroup)?.removeView(row)
                if (currentVariationsRow == row) {
                    currentVariationsRow = null
                }
                containerView.visibility = View.GONE
                wrapper?.visibility = View.GONE
                overlayView?.visibility = View.GONE
                onHidden()
            }
        } else {
            currentVariationsRow = null
            containerView.visibility = View.GONE
            wrapper?.visibility = View.GONE
            overlayView?.visibility = View.GONE
            onHidden()
        }
    }

    fun showVariations(snapshot: StatusBarController.StatusSnapshot, inputConnection: android.view.inputmethod.InputConnection?) {
        lastSnapshot = snapshot
        applyHeight()
        isTitan2Layout = SettingsManager.isTitan2LayoutEnabled(context)
        val containerView = container ?: return
        val wrapperView = wrapper ?: return
        val overlayView = overlay ?: return

        currentInputConnection = inputConnection

        // Decide whether to use suggestions, dynamic variations (from cursor) or static utility keys.
        val staticModeEnabled = SettingsManager.isStaticVariationBarModeEnabled(context)
        val statusBarVariationsEnabled = forceVariationAreaVisible || SettingsManager.areStatusBarVariationsEnabled(context)
        // Variations are controlled separately from suggestions
        val canShowVariations = !snapshot.shouldDisableVariations
        val canShowSuggestions = !snapshot.shouldDisableSuggestions
        // Legacy variations: always honor them when present, independent of suggestions.
        val hasDynamicVariations = canShowVariations && snapshot.variations.isNotEmpty()
        val hasSuggestions = canShowSuggestions && snapshot.suggestions.isNotEmpty()
        val useDynamicVariations = statusBarVariationsEnabled && !staticModeEnabled && hasDynamicVariations
        val allowStaticFallback = statusBarVariationsEnabled &&
            (forceVariationAreaVisible || staticModeEnabled || snapshot.shouldDisableVariations)

        val effectiveVariations: List<String>
        val isStaticContent: Boolean
        // Legacy behavior: give priority to letter variations when available, otherwise suggestions.
        when {
            useDynamicVariations -> {
                effectiveVariations = snapshot.variations
                isStaticContent = false
            }
            statusBarVariationsEnabled && hasSuggestions -> {
                effectiveVariations = snapshot.suggestions
                isStaticContent = false
            }
            allowStaticFallback -> {
                val variations = if (snapshot.isEmailField) {
                    if (emailVariations.isEmpty()) {
                        emailVariations = VariationRepository.loadEmailVariations(context.assets, context)
                    }
                    emailVariations
                } else if (snapshot.shiftPhysicallyPressed || snapshot.shiftOneShot || snapshot.capsLockEnabled || snapshot.shiftLayerLatched) {
                    if (staticVariationsShift.isEmpty()) {
                        val loaded = VariationRepository.loadStaticVariationsShift(context.assets, context)
                        staticVariationsShift = if (loaded.isNotEmpty()) {
                            loaded
                        } else {
                            SettingsManager.getDefaultStaticVariationShiftPreset()
                        }
                    }
                    staticVariationsShift
                } else if (snapshot.altPhysicallyPressed || snapshot.altOneShot || snapshot.altLatchActive || snapshot.altModifierLayerLatched) {
                    if (staticVariationsAlt.isEmpty()) {
                        val loaded = VariationRepository.loadStaticVariationsAlt(context.assets, context)
                        staticVariationsAlt = if (loaded.isNotEmpty()) {
                            loaded
                        } else {
                            SettingsManager.getDefaultStaticVariationAltPreset()
                        }
                    }
                    staticVariationsAlt
                } else {
                    val staticPreset = SettingsManager.getStaticVariationBarPreset(context)
                    if (staticPreset != SettingsManager.STATIC_VARIATION_PRESET_SYMBOLS) {
                        staticVariations = SettingsManager.getStaticVariationBasePreset(context)
                    } else if (staticVariations.isEmpty()) {
                        val loaded = VariationRepository.loadStaticVariations(context.assets, context)
                        staticVariations = loaded.ifEmpty {
                            SettingsManager.getStaticVariationBasePreset(context)
                        }
                    }
                    staticVariations
                }
                effectiveVariations = variations
                isStaticContent = true
            }
            else -> {
                // Keep the bar visible (mic/settings) but show empty placeholders in the variation row.
                effectiveVariations = emptyList()
                isStaticContent = false
            }
        }

        containerView.visibility = View.VISIBLE
        containerView.alpha = 1f
        wrapperView.visibility = View.VISIBLE
        wrapperView.alpha = 1f
        overlayView.visibility = if (isSymModeActive) View.GONE else View.VISIBLE
        overlayView.alpha = 1f

        val variationAreaVisible = statusBarVariationsEnabled
        val rawDisplayedVariations = if (variationAreaVisible) effectiveVariations else emptyList()
        shouldShowSwipeHint = variationAreaVisible && !staticModeEnabled && rawDisplayedVariations.isEmpty()
        updateSwipeHintVisibility(animate = true)
        val variationsChanged = rawDisplayedVariations != lastDisplayedVariations
        val inputConnectionChanged = lastInputConnectionUsed !== inputConnection
        val contentModeChanged = lastIsStaticContent != isStaticContent
        val variationAreaVisibilityChanged = lastVariationAreaVisible != variationAreaVisible
        val themeSignature = themeOverride.signature()
        val themeChanged = lastThemeSignature != themeSignature
        val roundedCorners = SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)
        val fallbackWidth = context.resources.displayMetrics.widthPixels -
            if (roundedCorners) 2 * dpToPx(6.5f) else 0
        val availableWidth = ((containerView.width.takeIf { it > 0 } ?: fallbackWidth) -
            containerView.paddingLeft - containerView.paddingRight).coerceAtLeast(1)
        val geometrySignature = listOf(
            availableWidth, verticalPaddingPx(), roundedCorners,
            SettingsManager.getDynamicVariationBarSlotCount(context),
            SettingsManager.getDynamicVariationBarResizeToContent(context)
        )
        val hasExistingRow = currentVariationsRow != null &&
            currentVariationsRow?.parent == containerView &&
            currentVariationsRow?.visibility == View.VISIBLE

        if (!variationsChanged &&
            !inputConnectionChanged &&
            !contentModeChanged &&
            !variationAreaVisibilityChanged &&
            !themeChanged &&
            lastGeometrySignature == geometrySignature &&
            (hasExistingRow || !variationAreaVisible)
        ) {
            currentVariationsRow?.let { row ->
                row.visibility = View.VISIBLE
                row.alpha = 1f
                for (index in 0 until row.childCount) {
                    row.getChildAt(index).visibility = View.VISIBLE
                    row.getChildAt(index).alpha = 1f
                }
            }
            leftButtonsContainer?.alpha = 1f
            buttonsContainer?.alpha = 1f
            buttonHost?.refreshLanguageText()
            return
        }

        variationButtons.clear()
        currentVariationsRow?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        currentVariationsRow = null

        val spacingBetweenButtons = if (roundedCorners) 0 else TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            context.resources.displayMetrics
        ).toInt()

        // Get enabled buttons from registry to calculate space dynamically
        val registry = buttonRegistry ?: return
        val enabledButtons = registry.getEnabledButtons(context)
        val leftButtonCount = enabledButtons.count { it.position == StatusBarButtonPosition.LEFT }
        val rightButtonCount = enabledButtons.count { it.position == StatusBarButtonPosition.RIGHT }
        val totalButtonCount = leftButtonCount + rightButtonCount
        val hasLeftButtons = leftButtonCount > 0
        val hasRightButtons = rightButtonCount > 0
        
        // Calculate fixed button size from the actual amount of visible content.
        // If variations are hidden, buttons divide the whole bar width.
        val reservesVariationArea = variationAreaVisible
        val dynamicSlotCount = SettingsManager.getDynamicVariationBarSlotCount(context)
        val resizeDynamicVariationsToContent = SettingsManager.getDynamicVariationBarResizeToContent(context)
        val maxButtonHeight = (targetHeightPx() - 2 * verticalPaddingPx()).coerceAtLeast(1)

        val variationSlotsForSizing = when {
            !reservesVariationArea -> 0
            isStaticContent -> rawDisplayedVariations.size
            resizeDynamicVariationsToContent -> rawDisplayedVariations.size.coerceAtMost(dynamicSlotCount)
            else -> dynamicSlotCount
        }
        val variationSlots = if (reservesVariationArea) variationSlotsForSizing else 0
        val totalElements = (totalButtonCount + variationSlots).coerceAtLeast(1)
        val rawFixedButtonSize = max(1, (availableWidth - spacingBetweenButtons * (totalElements - 1)) / totalElements)
        val fixedButtonWidth = if (reservesVariationArea) {
            min(rawFixedButtonSize, maxButtonHeight)
        } else {
            rawFixedButtonSize
        }
        val fixedButtonHeight = maxButtonHeight
        val fixedButtonsTotalWidth = fixedButtonWidth * totalButtonCount
        // Space only between buttons within each group (no trailing margin).
        val fixedButtonsSpacing = spacingBetweenButtons * (
            (leftButtonCount - 1).coerceAtLeast(0) + (rightButtonCount - 1).coerceAtLeast(0)
        )

        val variationCount = rawDisplayedVariations.size
        val variationToLeftGap = if (variationAreaVisible && hasLeftButtons && variationCount > 0) spacingBetweenButtons else 0
        val variationToRightGap = if (variationAreaVisible && hasRightButtons && variationCount > 0) spacingBetweenButtons else 0
        val variationsAvailableWidth = availableWidth -
            fixedButtonsTotalWidth -
            fixedButtonsSpacing -
            variationToLeftGap -
            variationToRightGap

        val baseButtonWidth = if (reservesVariationArea && variationSlotsForSizing > 0) {
            max(1, (variationsAvailableWidth - spacingBetweenButtons * (variationSlotsForSizing - 1)) / variationSlotsForSizing)
        } else if (variationCount > 0) {
            max(1, (variationsAvailableWidth - spacingBetweenButtons * (variationCount - 1)) / variationCount)
        } else {
            // If no variations, fall back to fixed button size to avoid division by zero
            fixedButtonWidth
        }
        val buttonWidth = baseButtonWidth
        val maxButtonWidth = baseButtonWidth
        val variationButtonHeight = maxButtonHeight
        val maxVisibleVariationCount = if (reservesVariationArea && variationsAvailableWidth > 0) {
            ((variationsAvailableWidth + spacingBetweenButtons) / (buttonWidth + spacingBetweenButtons)).coerceAtLeast(0)
        } else {
            rawDisplayedVariations.size
        }
        val displayedVariations = rawDisplayedVariations.take(maxVisibleVariationCount)

        val variationsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            alpha = 1f
            visibility = View.VISIBLE
        }
        currentVariationsRow = if (variationAreaVisible) variationsRow else null

        // Variations row takes available space (weight=1)
        val rowLayoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT,
            1f
        ).apply {
            marginStart = variationToLeftGap
            marginEnd = variationToRightGap
        }
        val insertionIndex = leftButtonsContainer?.let { leftContainer ->
            if (containerView.indexOfChild(leftContainer) == -1) {
                containerView.addView(leftContainer, 0)
            }
            1
        } ?: 0
        if (variationAreaVisible) {
            containerView.addView(variationsRow, insertionIndex, rowLayoutParams)
        }

        lastDisplayedVariations = rawDisplayedVariations
        lastInputConnectionUsed = inputConnection
        lastIsStaticContent = isStaticContent
        lastVariationAreaVisible = variationAreaVisible
        lastThemeSignature = themeSignature
        lastGeometrySignature = geometrySignature
        
        // Create a single callbacks object with all available callbacks.
        // Each button factory will extract only the callbacks it needs.
        val statusBarCallbacks = StatusBarCallbacks(
            onClipboardRequested = onClipboardRequested,
            onSpeechRecognitionRequested = onSpeechRecognitionRequested ?: { startSpeechRecognition() },
            onEmojiPickerRequested = onEmojiPickerRequested,
            onLanguageSwitchRequested = onLanguageSwitchRequested,
            onHamburgerMenuRequested = onHamburgerMenuRequested,
            onMinimalUiToggleRequested = onMinimalUiToggleRequested,
            onSoftwareKeyboardModeToggleRequested = onSoftwareKeyboardModeToggleRequested,
            onOpenSettings = { openSettings() },
            onSymbolsPageRequested = onSymbolsPageRequested,
            onUndoRequested = onUndoRequested,
            onRedoRequested = onRedoRequested,
            onHapticFeedback = { NotificationHelper.triggerHapticFeedback(context) }
        )
        
        // Generic function to create and add any button type.
        // The factory handles all button-specific logic (callbacks, state, etc.)
        fun createAndAddButton(
            buttonId: StatusBarButtonId,
            container: LinearLayout,
            isLastInGroup: Boolean
        ) {
            val host = buttonHost ?: return
            val hosted = host.getOrCreateButton(
                buttonId,
                fixedButtonHeight,
                statusBarCallbacks,
                fixedButtonWidth,
                fixedButtonHeight
            ) ?: return
            val params = LinearLayout.LayoutParams(fixedButtonWidth, fixedButtonHeight).apply {
                marginEnd = if (isLastInGroup) 0 else spacingBetweenButtons
            }
            hosted.container.visibility = View.VISIBLE
            hosted.container.alpha = 1f
            hosted.button.visibility = View.VISIBLE
            hosted.button.alpha = 1f
            hosted.container.layoutParams = params
            container.addView(hosted.container)

            if (buttonId == StatusBarButtonId.Clipboard) {
                host.updateClipboardCount(snapshot.clipboardCount)
            }
        }
        
        // Build left side buttons
        leftButtonsContainer?.removeAllViews()
        val leftButtons = enabledButtons.filter { it.position == StatusBarButtonPosition.LEFT }
        leftButtons.forEachIndexed { index, config ->
            val isLast = index == leftButtons.lastIndex
            leftButtonsContainer?.let { createAndAddButton(config.id, it, isLast) }
        }

        val addCandidate = snapshot.addWordCandidate
        val addOnly = addCandidate != null &&
            displayedVariations.size == 1 &&
            displayedVariations.firstOrNull()?.equals(addCandidate, ignoreCase = true) == true
        for ((index, variation) in displayedVariations.withIndex()) {
            val isAddCandidate = addCandidate != null && variation.equals(addCandidate, ignoreCase = true)
            val isLast = index == displayedVariations.lastIndex
            val variationWidth = if (addOnly) {
                variationsAvailableWidth.coerceAtLeast(buttonWidth)
            } else {
                buttonWidth
            }
            val button = createVariationButton(
                variation,
                inputConnection,
                variationWidth,
                variationButtonHeight,
                maxButtonWidth,
                isStaticContent,
                isAddCandidate,
                isLast,
                spacingBetweenButtons
            )
            button.alpha = 1f
            button.visibility = View.VISIBLE
            variationButtons.add(button)
            variationsRow.addView(button)
        }

        // Build right side buttons
        val buttonsContainerView = buttonsContainer ?: return
        buttonsContainerView.removeAllViews()
        val rightButtons = enabledButtons.filter { it.position == StatusBarButtonPosition.RIGHT }
            .sortedBy { it.order }
        rightButtons.forEachIndexed { index, config ->
            val isLast = index == rightButtons.lastIndex
            createAndAddButton(config.id, buttonsContainerView, isLast)
        }

        if (variationAreaVisible) {
            variationsRow.alpha = 1f
            variationsRow.visibility = View.VISIBLE
            buttonHost?.refreshLanguageText()
        } else {
            buttonHost?.refreshLanguageText()
        }
    }

    private fun installOverlayTouchListener(overlayView: FrameLayout) {
        val swipeThreshold = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            6f,
            context.resources.displayMetrics
        )

            overlayView.setOnTouchListener { _, motionEvent ->
                if (isSymModeActive) {
                    return@setOnTouchListener false
                }

            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Track the view under the finger so we can show pressed state despite the overlay intercepting the touch.
                    pressedView?.isPressed = false
                    pressedView = container?.let { findClickableViewAt(it, motionEvent.x, motionEvent.y) }
                    pressedView?.isPressed = true
                    isSwipeInProgress = false
                    swipeDirection = null
                    touchStartX = motionEvent.x
                    touchStartY = motionEvent.y
                    lastCursorMoveX = motionEvent.x
                    hideSwipeHintImmediate()
                    
                    // Setup long press detection
                    cancelLongPress()
                    longPressExecuted = false
                    if (pressedView != null && pressedView?.isLongClickable == true) {
                        longPressHandler = Handler(Looper.getMainLooper())
                        longPressRunnable = Runnable {
                            longPressExecuted = true
                            pressedView?.performLongClick()
                        }
                        longPressHandler?.postDelayed(longPressRunnable!!, 500) // 500ms for long press
                    }
                    
                    Log.d(TAG, "Touch down on overlay at ($touchStartX, $touchStartY)")
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = motionEvent.x - touchStartX
                    val deltaY = abs(motionEvent.y - touchStartY)
                    val incrementalDeltaX = motionEvent.x - lastCursorMoveX
                    updateSwipeIndicatorPosition(overlayView, motionEvent.x)

                    // Cancel long press if user moves too much
                    if (abs(deltaX) > swipeThreshold || deltaY > swipeThreshold) {
                        cancelLongPress()
                    }

                    if (isSwipeInProgress || (abs(deltaX) > swipeThreshold && abs(deltaX) > deltaY)) {
                        if (!isSwipeInProgress) {
                            isSwipeInProgress = true
                            swipeDirection = if (deltaX > 0) 1 else -1
                            // Clear pressed state when a swipe starts to avoid stuck highlights.
                            pressedView?.isPressed = false
                            pressedView = null
                            revealSwipeIndicator(overlayView, motionEvent.x)
                            Log.d(TAG, "Swipe started: ${if (swipeDirection == 1) "RIGHT" else "LEFT"}")
                        } else {
                            val currentDirection = if (incrementalDeltaX > 0) 1 else -1
                            if (currentDirection != swipeDirection && abs(incrementalDeltaX) > swipeThreshold) {
                                swipeDirection = currentDirection
                                Log.d(TAG, "Swipe direction changed: ${if (swipeDirection == 1) "RIGHT" else "LEFT"}")
                            }
                        }

                        if (isSwipeInProgress && swipeDirection != null) {
                            val inputConnection = currentInputConnection
                            if (inputConnection != null) {
                                // Read the threshold value dynamically to support real-time changes
                                val incrementalThresholdDp = SettingsManager.getSwipeIncrementalThreshold(context)
                                val incrementalThreshold = TypedValue.applyDimension(
                                    TypedValue.COMPLEX_UNIT_DIP,
                                    incrementalThresholdDp,
                                    context.resources.displayMetrics
                                )
                                val movementInDirection = if (swipeDirection == 1) incrementalDeltaX else -incrementalDeltaX
                                if (movementInDirection > incrementalThreshold) {
                                    val moved = if (swipeDirection == 1) {
                                        TextSelectionHelper.moveCursorRight(inputConnection)
                                    } else {
                                        TextSelectionHelper.moveCursorLeft(inputConnection)
                                    }

                                    if (moved) {
                                        lastCursorMoveX = motionEvent.x
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            onCursorMovedListener?.invoke()
                                        }, 50)
                                    }
                                }
                            }
                        }
                        true
                    } else {
                        // No swipe detected yet: update pressed highlight if we moved onto another button.
                        val currentTarget = container?.let { findClickableViewAt(it, motionEvent.x, motionEvent.y) }
                        if (pressedView != currentTarget) {
                            pressedView?.isPressed = false
                            pressedView = currentTarget
                            pressedView?.isPressed = true
                        }
                        true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val wasLongPress = longPressExecuted
                    cancelLongPress()
                    pressedView?.isPressed = false
                    val pressedTarget = pressedView
                    pressedView = null
                    hideSwipeIndicator()
                    updateSwipeHintVisibility(animate = true)
                    if (isSwipeInProgress) {
                        isSwipeInProgress = false
                        swipeDirection = null
                        Log.d(TAG, "Swipe ended on overlay")
                        true
                    } else {
                        // Don't execute click if long press was executed
                        if (!wasLongPress) {
                            val x = motionEvent.x
                            val y = motionEvent.y
                            val clickedView = container?.let { findClickableViewAt(it, x, y) }
                            if (clickedView != null && clickedView == pressedTarget) {
                                clickedView.performClick()
                            }
                        }
                        true
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    cancelLongPress()
                    pressedView?.isPressed = false
                    pressedView = null
                    hideSwipeIndicator()
                    updateSwipeHintVisibility(animate = true)
                    isSwipeInProgress = false
                    swipeDirection = null
                    true
                }
                else -> true
            }
        }
    }

    private fun revealSwipeIndicator(overlayView: FrameLayout, x: Float) {
        val indicator = swipeIndicator ?: return
        updateSwipeIndicatorPosition(overlayView, x)
        indicator.animate().cancel()
        indicator.alpha = 0f
        indicator.visibility = View.VISIBLE
        indicator.animate()
            .alpha(1f)
            .setDuration(60)
            .setListener(null)
            .start()
    }

    private fun hideSwipeIndicator(immediate: Boolean = false) {
        val indicator = swipeIndicator ?: return
        indicator.animate().cancel()
        if (immediate) {
            indicator.alpha = 0f
            indicator.visibility = View.GONE
            return
        }
        indicator.animate()
            .alpha(0f)
            .setDuration(140)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    indicator.visibility = View.GONE
                    indicator.alpha = 0f
                }
            })
            .start()
    }

    private fun updateSwipeHintVisibility(animate: Boolean) {
        val hint = emptyHintView ?: return
        val overlayView = overlay ?: return
        // Don't show swipe hint if we're showing speech recognition hint
        val shouldShow = shouldShowSwipeHint && overlayView.visibility == View.VISIBLE && !isShowingSpeechRecognitionHint
        hint.animate().cancel()
        if (shouldShow) {
            if (hint.visibility != View.VISIBLE) {
                hint.visibility = View.VISIBLE
                hint.alpha = 0f
            }
            if (animate) {
                hint.animate()
                    .alpha(0.7f)
                    .setDuration(420)
                    .setStartDelay(SWIPE_HINT_SHOW_DELAY_MS)
                    .setListener(null)
                    .start()
            } else {
                hint.alpha = 0.7f
            }
        } else {
            // Don't hide if we're showing speech recognition hint
            if (!isShowingSpeechRecognitionHint) {
                if (animate) {
                    hint.animate()
                        .setStartDelay(0)
                        .alpha(0f)
                        .setDuration(120)
                        .setListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                hint.visibility = View.GONE
                                hint.alpha = 0f
                            }
                        })
                        .start()
                } else {
                    hint.alpha = 0f
                    hint.visibility = View.GONE
                }
            }
        }
    }

    private fun hideSwipeHintImmediate() {
        val hint = emptyHintView ?: return
        hint.animate().cancel()
        hint.alpha = 0f
        hint.visibility = View.GONE
    }
    
    /**
     * Shows or hides the speech recognition hint message in the hint view.
     * When showing, replaces the swipe hint text with speech recognition message.
     * When hiding, restores the original swipe hint behavior.
     */
    fun showSpeechRecognitionHint(show: Boolean) {
        val hint = emptyHintView ?: return
        val overlayView = overlay ?: return
        
        isShowingSpeechRecognitionHint = show
        
        if (show) {
            // Ensure overlay is visible
            overlayView.visibility = View.VISIBLE
            
            // Save original hint text if not already saved
            if (originalHintText == null) {
                originalHintText = hint.text
            }
            
            // Set speech recognition message
            hint.text = context.getString(R.string.speech_recognition_prompt)
            
            // Show hint immediately (no delay) with animation
            hint.animate().cancel()
            hint.visibility = View.VISIBLE
            hint.alpha = 0f
            hint.animate()
                .alpha(0.7f)
                .setDuration(300)
                .setStartDelay(0)
                .start()
        } else {
            // Restore original hint text
            if (originalHintText != null) {
                hint.text = originalHintText
                originalHintText = null
            }
            
            // Hide hint with animation
            hint.animate().cancel()
            hint.animate()
                .alpha(0f)
                .setDuration(200)
                .setStartDelay(0)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        hint.visibility = View.GONE
                        // Restore swipe hint visibility logic
                        updateSwipeHintVisibility(animate = false)
                    }
                })
                .start()
        }
    }

    private fun createSwipeHintView(): TextView {
        return TextView(context).apply {
            text = context.getString(R.string.swipe_to_move_cursor)
            setTextColor(colorWithAlpha(themeOverride?.textAndIcons ?: Color.WHITE, 120))
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            alpha = 0f
            background = null
            isClickable = false
            isFocusable = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
            visibility = View.GONE
        }
    }

    private fun updateSwipeIndicatorPosition(overlayView: FrameLayout, x: Float) {
        val indicator = swipeIndicator ?: return
        val indicatorWidth = if (indicator.width > 0) indicator.width else (indicator.layoutParams?.width ?: 0)
        if (indicatorWidth <= 0 || overlayView.width <= 0) {
            return
        }
        val clampedX = x.coerceIn(0f, overlayView.width.toFloat())
        indicator.translationX = clampedX - (indicatorWidth / 2f)
        indicator.translationY = 0f
    }

    private fun startSpeechRecognition() {
        try {
            val intent = Intent(context, SpeechRecognitionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
            context.startActivity(intent)
            Log.d(TAG, "Speech recognition started")
        } catch (e: Exception) {
            Log.e(TAG, "Unable to launch speech recognition", e)
        }
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
    
    private fun cancelLongPress() {
        longPressRunnable?.let { runnable ->
            longPressHandler?.removeCallbacks(runnable)
        }
        longPressHandler = null
        longPressRunnable = null
        // Don't reset longPressExecuted here, it needs to persist until ACTION_UP
    }

    private fun createVariationButton(
        variation: String,
        inputConnection: android.view.inputmethod.InputConnection?,
        buttonWidth: Int,
        buttonHeight: Int,
        maxButtonWidth: Int,
        isStatic: Boolean,
        isAddCandidate: Boolean,
        isLast: Boolean,
        spacingBetweenButtons: Int
    ): TextView {
        val dp4 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()

        // Calculate text width needed
        val paint = Paint().apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                16f,
                context.resources.displayMetrics
            )
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val textWidth = paint.measureText(variation).toInt()
        val horizontalPadding = 0 // Testing with 0 padding
        val requiredWidth = textWidth + horizontalPadding
        
        // Keep status bar layout stable; long labels are clipped instead of resizing the row.
        val calculatedWidth = buttonWidth
        
        val stateListDrawable = themeOverride?.let {
            VariationButtonStyles.createButtonDrawable(
                heightPx = buttonHeight,
                normalColor = it.normalKey,
                pressedColor = it.accent,
                cornerRadiusRatio = if (SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) 0f else it.chromeCornerRadiusRatio,
                borderColor = it.divider,
                borderWidthPx = if (SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) 0 else dpToPx(1f)
            )
        } ?: VariationButtonStyles.createButtonDrawable(
            buttonHeight,
            cornerRadiusRatio = if (SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) 0f else VariationButtonStyles.BUTTON_CORNER_RADIUS_RATIO
        )

        return TextView(context).apply {
            text = variation
            textSize = 16f
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this,
                6,
                16,
                1,
                TypedValue.COMPLEX_UNIT_SP
            )
            setTextColor(themeOverride?.textAndIcons ?: Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, 0) // Testing with 0 padding
            if (isAddCandidate) {
                val addDrawable = ContextCompat.getDrawable(context, android.R.drawable.ic_input_add)?.mutate()
                addDrawable?.setTint(themeOverride?.accent ?: Color.YELLOW)
                setCompoundDrawablesWithIntrinsicBounds(null, null, addDrawable, null)
                compoundDrawablePadding = dp4
            }
            background = stateListDrawable
            layoutParams = LinearLayout.LayoutParams(calculatedWidth, buttonHeight).apply {
                marginEnd = if (isLast) 0 else spacingBetweenButtons
            }
            isClickable = true
            isFocusable = true
            val clickListener = if (isAddCandidate) {
                View.OnClickListener {
                    onAddUserWord?.invoke(variation)
                }
            } else if (isStatic) {
                VariationButtonHandler.createStaticVariationClickListener(
                    variation,
                    inputConnection,
                    context,
                    onVariationSelectedListener
                )
            } else {
                VariationButtonHandler.createVariationClickListener(
                    variation,
                    inputConnection,
                    context,
                    onVariationSelectedListener
                )
            }
            setOnClickListener { view ->
                NotificationHelper.triggerTapHapticFeedback(view)
                clickListener.onClick(view)
            }
            if (isAddCandidate) {
                setOnLongClickListener {
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    onAddUserWordSubstitutionRequested?.invoke(variation)
                    true
                }
            }
        }
    }

    private fun createPlaceholderButton(buttonWidth: Int, buttonHeight: Int): View {
        val dp3 = if (SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) 0 else TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            context.resources.displayMetrics
        ).toInt()
        val drawable = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = if (SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) 0f else VariationButtonStyles.cornerRadiusForSize(
                buttonHeight,
                themeOverride?.chromeCornerRadiusRatio
            )
        }
        return View(context).apply {
            background = drawable
            layoutParams = LinearLayout.LayoutParams(buttonWidth, buttonHeight).apply {
                marginEnd = dp3
            }
            isClickable = false
            isFocusable = false
        }
    }

    private fun createSwipeIndicator(): View {
        val barWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            32f,
            context.resources.displayMetrics
        ).toInt().coerceAtLeast(12)
        val drawable = createSwipeIndicatorDrawable()
        return View(context).apply {
            background = drawable
            alpha = 0f
            visibility = View.GONE
            isClickable = false
            isFocusable = false
            layoutParams = FrameLayout.LayoutParams(barWidth, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.TOP or Gravity.START
            }
        }
    }

    private fun createSwipeIndicatorDrawable(): GradientDrawable {
        val color = themeOverride?.cursorSwipe ?: Color.rgb(255, 221, 0)
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                colorWithAlpha(color, 50),
                colorWithAlpha(color, 170),
                colorWithAlpha(color, 50)
            )
        )
    }

    private fun colorWithAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun animateVariationsIn(view: View) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 75
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                view.alpha = progress
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.alpha = 1f
                }
            })
        }.start()
    }

    private fun animateVariationsOut(view: View, onAnimationEnd: (() -> Unit)? = null) {
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 50
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                view.alpha = progress
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                    view.alpha = 1f
                    onAnimationEnd?.invoke()
                }
            })
        }.start()
    }

    private fun findClickableViewAt(parent: View, x: Float, y: Float): View? {
        if (parent !is ViewGroup) {
            return if (x >= 0 && x < parent.width &&
                y >= 0 && y < parent.height &&
                parent.isClickable) {
                parent
            } else {
                null
            }
        }

        for (i in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(i)
            if (child.visibility == View.VISIBLE) {
                val childLeft = child.left.toFloat()
                val childTop = child.top.toFloat()
                val childRight = child.right.toFloat()
                val childBottom = child.bottom.toFloat()

                if (x >= childLeft && x < childRight &&
                    y >= childTop && y < childBottom) {
                    val childX = x - childLeft
                    val childY = y - childTop
                    val result = findClickableViewAt(child, childX, childY)
                    if (result != null) {
                        return result
                    }
                }
            }
        }

        return if (parent.isClickable) parent else null
    }

    fun invalidateStaticVariations() {
        staticVariations = emptyList()
        staticVariationsShift = emptyList()
        staticVariationsAlt = emptyList()
        emailVariations = emptyList()
    }

    /**
     * Updates the language button text with the current language code.
     */
    fun updateLanguageButtonText() {
        buttonHost?.refreshLanguageText()
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    private fun KeyboardThemeColors?.signature(): Int =
        if (this == null) {
            0
        } else {
            listOf(
                background,
                divider,
                normalKey,
                specialKey,
                textAndIcons,
                accent,
                cursorSwipe,
                suggestion,
                statusBarButton,
                chromeCornerRadiusRatio,
                variationsHeightScale
            ).hashCode()
        }
}
