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
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import android.content.res.AssetManager
import it.palsoftware.pastiera.inputmethod.SubtypeCycler

/**
 * Handles the variations row (suggestions + microphone/language) rendered above the LED strip.
 */
class VariationBarView(
    private val context: Context,
    private val assets: AssetManager? = null,
    private val imeServiceClass: Class<*>? = null
) {
    companion object {
        private const val TAG = "VariationBarView"
        private const val SWIPE_HINT_SHOW_DELAY_MS = 1000L
        private val PRESSED_BLUE = Color.rgb(100, 150, 255) // Same as LED active blue
        private val RECOGNITION_RED = Color.rgb(255, 80, 80) // Red color for active recognition
    }

    var onVariationSelectedListener: VariationButtonHandler.OnVariationSelectedListener? = null
    var onCursorMovedListener: (() -> Unit)? = null
    var onSpeechRecognitionRequested: (() -> Unit)? = null
    var onAddUserWord: ((String) -> Unit)? = null
    var onLanguageSwitchRequested: (() -> Unit)? = null
    var onClipboardRequested: (() -> Unit)? = null
    
    /**
     * Sets the microphone button active state (red pulsing background) during speech recognition.
     */
    fun setMicrophoneButtonActive(isActive: Boolean) {
        microphoneButtonView?.let { button ->
            isMicrophoneActive = isActive
            if (isActive) {
                // Initialize red background (will be updated by audio level)
                startMicrophoneAudioFeedback(button)
            } else {
                // Stop animation and restore normal state
                stopMicrophoneAudioFeedback(button)
            }
        }
    }
    
    /**
     * Updates the microphone button visual feedback based on audio level.
     * Changes the red color intensity based on audio volume.
     * @param rmsdB The RMS audio level in decibels (typically -10 to 0, lower is quieter)
     */
    fun updateMicrophoneAudioLevel(rmsdB: Float) {
        microphoneButtonView?.let { button ->
            if (!isMicrophoneActive) return@let
            
            // Map RMS value (-10 to 0) to a normalized value (0.0 to 1.0)
            // Clamp the value to reasonable bounds
            val minRms = -10f
            val maxRms = 0f
            val normalizedLevel = ((rmsdB - minRms) / (maxRms - minRms)).coerceIn(0f, 1f)
            
            // Map to color intensity: darker red at 0.0, brighter red at 1.0
            // Use a curve to make the effect more visible (power of 2)
            val intensity = normalizedLevel * normalizedLevel // Quadratic curve for more noticeable effect
            
            // Calculate red color values: from dark red (128, 0, 0) to bright red (255, 50, 50)
            // Keep it red by maintaining lower G and B values relative to R
            val r = (128 + (255 - 128) * intensity).toInt()
            val g = (0 + (50 - 0) * intensity).toInt()
            val b = (0 + (50 - 0) * intensity).toInt()
            val color = Color.rgb(r, g, b)
            
            // Update the drawable color
            currentMicrophoneDrawable?.setColor(color)
            button.background?.invalidateSelf()
        }
    }
    
    /**
     * Initializes the microphone button for audio feedback (red background).
     */
    private fun startMicrophoneAudioFeedback(button: ImageView) {
        // Stop any existing animation
        stopMicrophoneAudioFeedback(button)
        
        // Create base drawable with initial red color (medium intensity)
        currentMicrophoneDrawable = GradientDrawable().apply {
            setColor(RECOGNITION_RED)
            cornerRadius = 0f
        }
        
        // Store original background for pressed state
        val pressedDrawable = GradientDrawable().apply {
            setColor(PRESSED_BLUE)
            cornerRadius = 0f
        }
        
        // Create state list with red as normal state
        val stateList = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), currentMicrophoneDrawable)
        }
        button.background = stateList
        
        // Set initial alpha
        button.alpha = 1f
    }
    
    /**
     * Stops the audio feedback and restores normal button state.
     */
    private fun stopMicrophoneAudioFeedback(button: ImageView) {
        // Cancel any pulse animation if still running
        microphonePulseAnimator?.cancel()
        microphonePulseAnimator = null
        
        // Reset alpha
        button.alpha = 1f
        
        // Clear reference to drawable
        currentMicrophoneDrawable = null
        
        // Restore normal state
        val normalDrawable = GradientDrawable().apply {
            setColor(Color.rgb(17, 17, 17))
            cornerRadius = 0f
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(PRESSED_BLUE)
            cornerRadius = 0f
        }
        val stateList = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
        button.background = stateList
    }


    private var wrapper: FrameLayout? = null
    private var container: LinearLayout? = null
    private var buttonsContainer: LinearLayout? = null
    private var overlay: FrameLayout? = null
    private var swipeIndicator: View? = null
    private var emptyHintView: TextView? = null
    private var microphonePulseAnimator: ValueAnimator? = null
    private var shouldShowSwipeHint: Boolean = false
    private var currentVariationsRow: LinearLayout? = null
    private var variationButtons: MutableList<TextView> = mutableListOf()
    private var microphoneButtonView: ImageView? = null
    private var settingsButtonView: ImageView? = null
    private var languageButtonView: TextView? = null
    private var isMicrophoneActive: Boolean = false
    private var lastLanguageSwitchTime: Long = 0
    private val LANGUAGE_SWITCH_DEBOUNCE_MS = 500L // Minimum time between language switches
    private var currentMicrophoneDrawable: GradientDrawable? = null
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
    private var emailVariations: List<String> = emptyList()
    private var lastInputConnectionUsed: android.view.inputmethod.InputConnection? = null
    private var lastIsStaticContent: Boolean? = null
    private var pressedView: View? = null
    private var longPressHandler: Handler? = null
    private var longPressRunnable: Runnable? = null
    private var longPressExecuted: Boolean = false
    private var clipboardButtonView: ImageView? = null
    private var clipboardContainer: FrameLayout? = null
    private var clipboardBadgeView: TextView? = null

    fun ensureView(): FrameLayout {
        if (wrapper != null) {
            return wrapper!!
        }

        val basePadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            64f,
            context.resources.displayMetrics
        ).toInt()
        val leftPadding = 0 // clipboard flush to the left edge
        val rightPadding = 0 // remove trailing gap so language button sits flush to the right
        val variationsVerticalPadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            8f,
            context.resources.displayMetrics
        ).toInt()
        val variationsContainerHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            55f,
            context.resources.displayMetrics
        ).toInt()

        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(leftPadding, variationsVerticalPadding, rightPadding, variationsVerticalPadding)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                variationsContainerHeight
            )
            visibility = View.GONE
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
        container?.addView(buttonsContainer)

        wrapper = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                variationsContainerHeight
            )
            visibility = View.GONE
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

    fun getWrapper(): FrameLayout? = wrapper

    fun setSymModeActive(active: Boolean) {
        isSymModeActive = active
        if (active) {
            hideSwipeIndicator(immediate = true)
            hideSwipeHintImmediate()
            overlay?.visibility = View.GONE
        }
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
        removeMicrophoneImmediate()
        removeSettingsImmediate()
        removeLanguageButtonImmediate()
        removeClipboardButtonImmediate()
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

        removeMicrophoneImmediate()
        removeSettingsImmediate()
        removeLanguageButtonImmediate()
        removeClipboardButtonImmediate()
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
        val containerView = container ?: return
        val wrapperView = wrapper ?: return
        val overlayView = overlay ?: return

        currentInputConnection = inputConnection

        // Decide whether to use suggestions, dynamic variations (from cursor) or static utility keys.
        val staticModeEnabled = SettingsManager.isStaticVariationBarModeEnabled(context)
        // Variations are controlled separately from suggestions
        val canShowVariations = !snapshot.shouldDisableVariations
        val canShowSuggestions = !snapshot.shouldDisableSuggestions
        // Legacy variations: always honor them when present, independent of suggestions.
        val hasDynamicVariations = canShowVariations && snapshot.variations.isNotEmpty()
        val hasSuggestions = canShowSuggestions && snapshot.suggestions.isNotEmpty()
        val useDynamicVariations = !staticModeEnabled && hasDynamicVariations
        val allowStaticFallback = staticModeEnabled || snapshot.shouldDisableVariations

        val effectiveVariations: List<String>
        val isStaticContent: Boolean
        // Legacy behavior: give priority to letter variations when available, otherwise suggestions.
        when {
            useDynamicVariations -> {
                effectiveVariations = snapshot.variations
                isStaticContent = false
            }
            hasSuggestions -> {
                effectiveVariations = snapshot.suggestions
                isStaticContent = false
            }
            allowStaticFallback -> {
                val variations = if (snapshot.isEmailField) {
                    if (emailVariations.isEmpty()) {
                        emailVariations = VariationRepository.loadEmailVariations(context.assets, context)
                    }
                    emailVariations
                } else {
                    if (staticVariations.isEmpty()) {
                        staticVariations = VariationRepository.loadStaticVariations(context.assets, context)
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

        val limitedVariations = effectiveVariations.take(7)
        val showSwipeHint = effectiveVariations.isEmpty() && !allowStaticFallback
        shouldShowSwipeHint = showSwipeHint

        containerView.visibility = View.VISIBLE
        wrapperView.visibility = View.VISIBLE
        overlayView.visibility = if (isSymModeActive) View.GONE else View.VISIBLE
        updateSwipeHintVisibility(animate = true)

        val variationsChanged = limitedVariations != lastDisplayedVariations
        val inputConnectionChanged = lastInputConnectionUsed !== inputConnection
        val contentModeChanged = lastIsStaticContent != isStaticContent
        val hasExistingRow = currentVariationsRow != null &&
            currentVariationsRow?.parent == containerView &&
            currentVariationsRow?.visibility == View.VISIBLE

        if (!variationsChanged && !inputConnectionChanged && !contentModeChanged && hasExistingRow) {
            return
        }

        variationButtons.clear()
        currentVariationsRow?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        currentVariationsRow = null

        val screenWidth = context.resources.displayMetrics.widthPixels
        val leftPadding = containerView.paddingLeft
        val rightPadding = containerView.paddingRight
        val availableWidth = screenWidth - leftPadding - rightPadding

        val spacingBetweenButtons = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            context.resources.displayMetrics
        ).toInt()

        // Fixed-size square buttons (clipboard, mic, language)
        val fixedButtonSize = max(1, (availableWidth - spacingBetweenButtons * 10) / 10)
        val fixedButtonsTotalWidth = fixedButtonSize * 3
        // Spacing: clipboard->variations, variations->mic, mic->language
        val fixedButtonsSpacing = spacingBetweenButtons * 3

        val variationCount = limitedVariations.size
        val variationsAvailableWidth = availableWidth - fixedButtonsTotalWidth - fixedButtonsSpacing

        val baseButtonWidth = if (variationCount > 0) {
            max(1, (variationsAvailableWidth - spacingBetweenButtons * (variationCount - 1)) / variationCount)
        } else {
            // If no variations, fall back to fixed button size to avoid division by zero
            fixedButtonSize
        }
        val buttonWidth: Int
        val maxButtonWidth: Int
        if (variationCount < 7 && variationCount > 0) {
            buttonWidth = baseButtonWidth
            maxButtonWidth = baseButtonWidth
        } else {
            buttonWidth = baseButtonWidth
            maxButtonWidth = baseButtonWidth * 3 // Cap at 3x when we have 7 variations
        }

        val variationsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        currentVariationsRow = variationsRow

        // Variations row takes available space (weight=1)
        val rowLayoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )
        containerView.addView(variationsRow, 0, rowLayoutParams)

        lastDisplayedVariations = limitedVariations
        lastInputConnectionUsed = inputConnection
        lastIsStaticContent = isStaticContent

        // Add clipboard button with badge container at the start of variations row
        val clipboardButton = clipboardButtonView ?: createClipboardButton(fixedButtonSize)
        clipboardButtonView = clipboardButton
        val badge = clipboardBadgeView ?: createClipboardBadge()
        clipboardBadgeView = badge

        val container = clipboardContainer ?: FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(fixedButtonSize, fixedButtonSize).apply {
                marginEnd = spacingBetweenButtons
            }
        }.also { clipboardContainer = it }
        (container.parent as? ViewGroup)?.removeView(container)
        container.removeAllViews()
        container.addView(clipboardButton, FrameLayout.LayoutParams(fixedButtonSize, fixedButtonSize))
        container.addView(
            badge,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.TOP
            ).apply {
                val m = dpToPx(2f)
                val offset = dpToPx(2f) // push badge slightly downward
                setMargins(m, m + offset, m, m)
            }
        )

        variationsRow.addView(container, 0)
        clipboardButton.setOnClickListener {
            NotificationHelper.triggerHapticFeedback(context)
            onClipboardRequested?.invoke()
        }
        clipboardButton.alpha = 1f
        clipboardButton.visibility = View.VISIBLE
        // Update badge with current clipboard count
        updateClipboardBadge(snapshot.clipboardCount)

        val addCandidate = snapshot.addWordCandidate
        for (variation in limitedVariations) {
            val isAddCandidate = addCandidate != null && variation.equals(addCandidate, ignoreCase = true)
            val button = createVariationButton(variation, inputConnection, buttonWidth, maxButtonWidth, isStaticContent, isAddCandidate)
            variationButtons.add(button)
            variationsRow.addView(button)
        }

        // Add buttons to the fixed-position container on the right
        val buttonsContainerView = buttonsContainer ?: return
        buttonsContainerView.removeAllViews()
        
        val microphoneButton = microphoneButtonView ?: createMicrophoneButton(fixedButtonSize)
        microphoneButtonView = microphoneButton
        (microphoneButton.parent as? ViewGroup)?.removeView(microphoneButton)
        val micParams = LinearLayout.LayoutParams(fixedButtonSize, fixedButtonSize).apply {
            marginStart = spacingBetweenButtons
        }
        buttonsContainerView.addView(microphoneButton, micParams)
        microphoneButton.setOnClickListener {
            NotificationHelper.triggerHapticFeedback(context)
            // Use callback if available (modern SpeechRecognizer approach), otherwise fallback to Activity
            if (onSpeechRecognitionRequested != null) {
                onSpeechRecognitionRequested?.invoke()
            } else {
                startSpeechRecognition(inputConnection)
            }
        }
        microphoneButton.alpha = 1f
        microphoneButton.visibility = View.VISIBLE

        // Language switch button (language code)
        val languageButton = languageButtonView ?: createLanguageButton(fixedButtonSize)
        languageButtonView = languageButton
        (languageButton.parent as? ViewGroup)?.removeView(languageButton)
        val languageParams = LinearLayout.LayoutParams(fixedButtonSize, fixedButtonSize).apply {
            topMargin = 0
            marginStart = spacingBetweenButtons
        }
        buttonsContainerView.addView(languageButton, languageParams)
        // Update language code text
        updateLanguageButtonText(languageButton)
        languageButton.setOnClickListener {
            val now = System.currentTimeMillis()
            // Debounce: prevent rapid consecutive clicks
            if (now - lastLanguageSwitchTime < LANGUAGE_SWITCH_DEBOUNCE_MS) {
                return@setOnClickListener
            }
            lastLanguageSwitchTime = now
            
            // Disable button during switch to prevent multiple simultaneous switches
            languageButton.isEnabled = false
            languageButton.alpha = 0.5f
            
            onLanguageSwitchRequested?.invoke()
            
            // Re-enable button and update text after language switch (with a delay to ensure the change is applied)
            Handler(Looper.getMainLooper()).postDelayed({
                languageButton.isEnabled = true
                languageButton.alpha = 1f
                updateLanguageButtonText(languageButton)
            }, 300)
        }
        languageButton.setOnLongClickListener {
            openSettings()
            true
        }
        languageButton.alpha = 1f
        languageButton.visibility = View.VISIBLE

        if (variationsChanged) {
            animateVariationsIn(variationsRow)
        } else {
            variationsRow.alpha = 1f
            variationsRow.visibility = View.VISIBLE
            // Update language button text even when variations haven't changed
            languageButtonView?.let { updateLanguageButtonText(it) }
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
            setTextColor(Color.argb(120, 255, 255, 255))
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

    private fun startSpeechRecognition(inputConnection: android.view.inputmethod.InputConnection?) {
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

    private fun removeMicrophoneImmediate() {
        microphoneButtonView?.let { microphone ->
            (microphone.parent as? ViewGroup)?.removeView(microphone)
            microphone.visibility = View.GONE
            microphone.alpha = 1f
        }
    }

    private fun removeLanguageButtonImmediate() {
        languageButtonView?.let { language ->
            (language.parent as? ViewGroup)?.removeView(language)
            language.visibility = View.GONE
            language.alpha = 1f
        }
    }
    
    private fun removeClipboardButtonImmediate() {
        clipboardContainer?.let { container ->
            (container.parent as? ViewGroup)?.removeView(container)
        }
        clipboardButtonView?.apply {
            visibility = View.GONE
            alpha = 1f
        }
    }

    private fun removeSettingsImmediate() {
        settingsButtonView?.let { settings ->
            (settings.parent as? ViewGroup)?.removeView(settings)
            settings.visibility = View.GONE
            settings.alpha = 1f
        }
    }

    private fun createVariationButton(
        variation: String,
        inputConnection: android.view.inputmethod.InputConnection?,
        buttonWidth: Int,
        maxButtonWidth: Int,
        isStatic: Boolean,
        isAddCandidate: Boolean
    ): TextView {
        val dp2 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            2f,
            context.resources.displayMetrics
        ).toInt()
        val dp4 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        val dp3 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
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
        
        // Use max of minimum width (buttonWidth) and required width, but cap at maxButtonWidth
        val calculatedWidth = max(buttonWidth, min(requiredWidth, maxButtonWidth))
        
        // Keep height fixed (square based on minimum width)
        val buttonHeight = buttonWidth

        val drawable = GradientDrawable().apply {
            setColor(Color.rgb(17, 17, 17))
            cornerRadius = 0f
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(PRESSED_BLUE)
            cornerRadius = 0f
        }
        val stateListDrawable = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), drawable)
        }

        return TextView(context).apply {
            text = variation
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(0, 0, 0, 0) // Testing with 0 padding
            if (isAddCandidate) {
                val addDrawable = ContextCompat.getDrawable(context, android.R.drawable.ic_input_add)?.mutate()
                addDrawable?.setTint(Color.YELLOW)
                setCompoundDrawablesWithIntrinsicBounds(null, null, addDrawable, null)
                compoundDrawablePadding = dp4
            }
            background = stateListDrawable
            layoutParams = LinearLayout.LayoutParams(calculatedWidth, buttonHeight).apply {
                marginEnd = dp3
            }
            isClickable = true
            isFocusable = true
            setOnClickListener(
                if (isAddCandidate) {
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
            )
        }
    }

    private fun createPlaceholderButton(buttonWidth: Int): View {
        val dp3 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            context.resources.displayMetrics
        ).toInt()
        val drawable = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = 0f
        }
        return View(context).apply {
            background = drawable
            layoutParams = LinearLayout.LayoutParams(buttonWidth, buttonWidth).apply {
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
        val drawable = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                Color.argb(50, 255, 204, 0),
                Color.argb(170, 255, 221, 0),
                Color.argb(50, 255, 204, 0)
            )
        )
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

    private fun createMicrophoneButton(buttonSize: Int): ImageView {
        val normalDrawable = GradientDrawable().apply {
            setColor(Color.rgb(17, 17, 17))
            cornerRadius = 0f
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(PRESSED_BLUE)
            cornerRadius = 0f
        }
        val stateList = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_baseline_mic_24)
            setColorFilter(Color.WHITE)
            background = stateList
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize)
        }
    }

    private fun createClipboardButton(buttonSize: Int): ImageView {
        val normalDrawable = GradientDrawable().apply {
            setColor(Color.rgb(17, 17, 17))
            cornerRadius = 0f
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(PRESSED_BLUE)
            cornerRadius = 0f
        }
        val stateList = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_content_paste_24)
            setColorFilter(Color.WHITE)
            background = stateList
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize)
        }
    }

    private fun createClipboardBadge(): TextView {
        val padding = dpToPx(2f)
        return TextView(context).apply {
            background = null
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            minWidth = 0
            minHeight = 0
            visibility = View.GONE
        }
    }

    private fun updateClipboardBadge(count: Int) {
        val badge = clipboardBadgeView ?: return
        if (count <= 0) {
            badge.visibility = View.GONE
            return
        }
        badge.visibility = View.VISIBLE
        badge.text = count.toString()
    }

    private fun createStatusBarSettingsButton(buttonSize: Int): ImageView {
        val dp3 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            context.resources.displayMetrics
        ).toInt()
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_settings_24)
            setColorFilter(Color.rgb(100, 100, 100))
            background = null
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = true
            setPadding(dp3, dp3, dp3, dp3)
            layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize)
        }
    }

    private fun createLanguageButton(buttonSize: Int): TextView {
        val normalDrawable = GradientDrawable().apply {
            setColor(Color.rgb(17, 17, 17))
            cornerRadius = 0f
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(PRESSED_BLUE)
            cornerRadius = 0f
        }
        val stateList = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }

        return TextView(context).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            background = stateList
            isClickable = true
            isFocusable = true
            includeFontPadding = false
            minHeight = buttonSize
            maxHeight = buttonSize
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize)
        }
    }

    /**
     * Updates the language button text with the current language code.
     */
    private fun updateLanguageButtonText(button: TextView) {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            val currentSubtype = imm.currentInputMethodSubtype
            val languageCode = if (currentSubtype != null) {
                // Extract language code from locale (e.g., "en_US" -> "EN", "it_IT" -> "IT")
                val locale = currentSubtype.locale
                locale.split("_").firstOrNull()?.uppercase() ?: "??"
            } else {
                "??"
            }
            button.text = languageCode
            applyLanguageLongPressHint(button, languageCode)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating language button text", e)
            applyLanguageLongPressHint(button, "??")
        }
    }

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
        emailVariations = emptyList()
    }

    /**
     * Updates the language button text with the current language code.
     */
    fun updateLanguageButtonText() {
        languageButtonView?.let { button ->
            updateLanguageButtonText(button)
        }
    }

    private fun applyLanguageLongPressHint(button: TextView, languageCode: String) {
        // Clear any icons so the label stays perfectly centered.
        button.setCompoundDrawables(null, null, null, null)
        button.compoundDrawablePadding = 0
        button.gravity = Gravity.CENTER
        button.textAlignment = View.TEXT_ALIGNMENT_CENTER
        button.setPadding(0, 0, 0, 0)

        val paintCopy = TextPaint(button.paint).apply {
            textSize = button.textSize
        }
        val textWidth = paintCopy.measureText(languageCode).coerceAtLeast(1f)
        // Target 3 dashes -> 3 dash segments + 2 gaps = 5 units.
        val dashLength = max(dpToPx(2f).toFloat(), textWidth / 5f)
        val gapLength = dashLength
        val dashEffect = DashPathEffect(floatArrayOf(dashLength, gapLength), 0f)

        val dottedText = SpannableString(languageCode).apply {
            setSpan(
                object : UnderlineSpan() {
                    override fun updateDrawState(tp: TextPaint) {
                        super.updateDrawState(tp)
                        tp.isUnderlineText = true
                        // Use a dashed underline to hint the long-press action.
                        tp.pathEffect = dashEffect
                    }
                },
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        button.text = dottedText
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
