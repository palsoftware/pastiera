package it.palsoftware.pastiera.inputmethod

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.content.ContextCompat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.util.Log
import android.util.TypedValue
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.MainActivity
import it.palsoftware.pastiera.SymCustomizationActivity
import it.palsoftware.pastiera.SettingsActivity
import it.palsoftware.pastiera.SettingsManager
import kotlin.math.max
import android.view.MotionEvent
import android.view.KeyEvent
import kotlin.math.abs

/**
 * Manages the status bar shown by the IME, handling view creation
 * and updating text/style based on modifier states.
 */
class StatusBarController(
    private val context: Context
) {
    // Listener for variation selection
    var onVariationSelectedListener: VariationButtonHandler.OnVariationSelectedListener? = null

    // Listener for prediction selection
    var onPredictionSelectedListener: PredictionButtonHandler.OnPredictionSelectedListener? = null

    // Listener for speech recognition results
    var onSpeechResultListener: ((String) -> Unit)? = null
    
    // Listener for cursor movement (to update variations)
    var onCursorMovedListener: (() -> Unit)? = null

    companion object {
        private const val TAG = "StatusBarController"
        private const val NAV_MODE_LABEL = "NAV MODE"
        private val DEFAULT_BACKGROUND = Color.parseColor("#000000")
        private val NAV_MODE_BACKGROUND = Color.argb(100, 0, 0, 0)
        
        // LED colors
        private val LED_COLOR_GRAY_OFF = Color.argb(26, 255, 255, 255) // Gray when LED is off
        private val LED_COLOR_RED_LOCKED = Color.rgb(247, 99, 0) // Orange/red when locked
        private val LED_COLOR_BLUE_ACTIVE = Color.rgb(100, 150, 255) // Blue when active
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
        val variations: List<String> = emptyList(),
        val lastInsertedChar: Char? = null,
        val shouldDisableSmartFeatures: Boolean = false,
        val predictions: List<String> = emptyList() // Word predictions (max 3)
    ) {
        val navModeActive: Boolean
            get() = ctrlLatchActive && ctrlLatchFromNavMode
    }

    private var statusBarLayout: LinearLayout? = null
    private var modifiersContainer: LinearLayout? = null
    private var emojiMapTextView: TextView? = null
    private var emojiKeyboardContainer: LinearLayout? = null
    private var variationsContainer: LinearLayout? = null
    private var variationsOverlay: View? = null
    private var variationsWrapper: FrameLayout? = null
    private var variationButtons: MutableList<TextView> = mutableListOf()
    private var predictionsContainer: LinearLayout? = null // Word predictions display
    private var predictionButtons: MutableList<TextView> = mutableListOf()
    private var ledContainer: LinearLayout? = null
    private var shiftLed: View? = null
    private var ctrlLed: View? = null
    private var altLed: View? = null
    private var symLed: View? = null
    private var emojiKeyButtons: MutableList<View> = mutableListOf()
    private var currentVariationsRow: LinearLayout? = null
    private var microphoneButtonView: ImageView? = null
    private var settingsButtonView: ImageView? = null
    private var lastDisplayedVariations: List<String> = emptyList()
    private var currentPredictionsRow: LinearLayout? = null // Prediction row tracking
    private var lastDisplayedPredictions: List<String> = emptyList() // Prediction tracking
    private var currentInputConnection: android.view.inputmethod.InputConnection? = null
    private var isSwipeInProgress = false
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var lastCursorMoveX = 0f // Last X position where cursor was moved
    private var swipeDirection: Int? = null // 1 for right, -1 for left
    private var isFlickInProgress = false // Track if upward flick detected
    private var isSymModeActive = false

    fun getLayout(): LinearLayout? = statusBarLayout

    fun getOrCreateLayout(emojiMapText: String = ""): LinearLayout {
        if (statusBarLayout == null) {
            statusBarLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(DEFAULT_BACKGROUND)
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

            // Container for variation buttons (horizontal, left-aligned).
            // Fixed height to keep the bar height consistent (increased by 10%).
            val variationsContainerHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                55f, // 50f * 1.1 (aumentata del 10%)
                context.resources.displayMetrics
            ).toInt()
            val variationsVerticalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                8.8f, // 8f * 1.1 (aumentato del 10%)
                context.resources.displayMetrics
            ).toInt()
            
            variationsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                // Padding: sinistro invariato (64dp), destro ridotto del 67% (64dp * 0.31 = 19.84dp)
                val rightPadding = (leftPadding * 0.31f).toInt()
                setPadding(leftPadding, variationsVerticalPadding, rightPadding, variationsVerticalPadding)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    variationsContainerHeight // Altezza fissa invece di WRAP_CONTENT
                )
                visibility = View.GONE
            }
            
            // Create FrameLayout wrapper for variationsContainer with overlay
            variationsWrapper = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    variationsContainerHeight
                )
                visibility = View.GONE
            }
            
            // Add variationsContainer to wrapper
            variationsWrapper?.addView(variationsContainer)
            
            // Create transparent overlay for swipe gestures
            variationsOverlay = View(context).apply {
                background = ColorDrawable(Color.TRANSPARENT)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                visibility = View.GONE
            }
            
            // Add overlay to wrapper (on top of variationsContainer)
            variationsWrapper?.addView(variationsOverlay)
            
            // Add OnTouchListener to overlay for swipe gestures
            val SWIPE_THRESHOLD = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                6f, // dp - reduced threshold to start swipe (was 10f)
                context.resources.displayMetrics
            )
            val INCREMENTAL_THRESHOLD = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                9.6f, // dp - threshold for each incremental cursor movement (8f * 1.2 = 9.6f, 20% slower)
                context.resources.displayMetrics
            )
            
            variationsOverlay?.setOnTouchListener { view, motionEvent ->
                // Don't handle swipe if SYM mode is active
                if (isSymModeActive) {
                    return@setOnTouchListener false
                }
                
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isSwipeInProgress = false
                        isFlickInProgress = false
                        swipeDirection = null
                        touchStartX = motionEvent.x
                        touchStartY = motionEvent.y
                        lastCursorMoveX = motionEvent.x
                        Log.d(TAG, "Touch down on overlay at ($touchStartX, $touchStartY)")
                        // Intercept all events to handle both swipe and tap
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = motionEvent.x - touchStartX
                        val deltaY = motionEvent.y - touchStartY // Keep sign to detect upward (negative)
                        val absDeltaY = abs(deltaY)
                        val incrementalDeltaX = motionEvent.x - lastCursorMoveX

                        // UPWARD FLICK threshold (smaller than swipe for quicker detection)
                        val FLICK_THRESHOLD = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            20f, // 20dp upward movement to trigger flick
                            context.resources.displayMetrics
                        )

                        // Check for UPWARD FLICK (for prediction selection) - highest priority
                        if (!isSwipeInProgress && !isFlickInProgress && deltaY < -FLICK_THRESHOLD && absDeltaY > abs(deltaX)) {
                            // Upward flick detected!
                            isFlickInProgress = true
                            Log.d(TAG, "Upward flick detected at x=$touchStartX")
                            // Will handle prediction selection in ACTION_UP
                            true
                        }
                        // If scroll is mainly horizontal and exceeds initial threshold, or swipe is already in progress
                        else if (isSwipeInProgress || (abs(deltaX) > SWIPE_THRESHOLD && abs(deltaX) > absDeltaY)) {
                            // Determine or update swipe direction
                            if (!isSwipeInProgress) {
                                isSwipeInProgress = true
                                swipeDirection = if (deltaX > 0) 1 else -1
                                Log.d(TAG, "Swipe started: ${if (swipeDirection == 1) "RIGHT" else "LEFT"}")
                            } else {
                                // Update direction if user changes direction during swipe
                                // Only change if movement is clearly in opposite direction
                                val currentDirection = if (incrementalDeltaX > 0) 1 else -1
                                if (currentDirection != swipeDirection && abs(incrementalDeltaX) > SWIPE_THRESHOLD) {
                                    swipeDirection = currentDirection
                                    Log.d(TAG, "Swipe direction changed: ${if (swipeDirection == 1) "RIGHT" else "LEFT"}")
                                }
                            }
                            
                            // Continue moving cursor as finger moves (in any direction)
                            if (isSwipeInProgress && swipeDirection != null) {
                                val inputConnection = currentInputConnection
                                
                                if (inputConnection != null) {
                                    // Check movement in current swipe direction
                                    val movementInDirection = if (swipeDirection == 1) incrementalDeltaX else -incrementalDeltaX
                                    
                                    // Move cursor if movement exceeds threshold in current direction
                                    if (movementInDirection > INCREMENTAL_THRESHOLD) {
                                        // Use TextSelectionHelper to move cursor directly (safer than DPAD keys)
                                        // This only affects the text field, not UI navigation
                                        val moved = if (swipeDirection == 1) {
                                            TextSelectionHelper.moveCursorRight(inputConnection)
                                        } else {
                                            TextSelectionHelper.moveCursorLeft(inputConnection)
                                        }
                                        
                                        if (moved) {
                                            // Update last cursor move position
                                            lastCursorMoveX = motionEvent.x
                                            
                                            Log.d(TAG, "Cursor moved: ${if (swipeDirection == 1) "RIGHT" else "LEFT"}")
                                            
                                            // Notify listener to update variations after cursor movement
                                            // Use a delayed post to ensure Android has completed the cursor movement
                                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                                onCursorMovedListener?.invoke()
                                            }, 50) // 50ms delay to allow Android to update cursor position
                                        } else {
                                            Log.d(TAG, "Cursor movement failed: ${if (swipeDirection == 1) "RIGHT" else "LEFT"} (probably at text boundary)")
                                        }
                                    }
                                }
                            }
                            true // Consume the event - swipe continues as long as finger is down
                        } else {
                            true // Still intercept to prevent button handling
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isFlickInProgress) {
                            // Handle upward flick gesture for prediction selection
                            handleFlickGesture(touchStartX)
                            isFlickInProgress = false
                            Log.d(TAG, "Flick gesture ended")
                            true // Consume the event
                        } else if (isSwipeInProgress) {
                            isSwipeInProgress = false
                            swipeDirection = null
                            Log.d(TAG, "Swipe ended on overlay")
                            true // Consume to prevent button click
                        } else {
                            // Not a swipe, find and click button under touch position in variationsContainer
                            val x = motionEvent.x
                            val y = motionEvent.y
                            Log.d(TAG, "Tap detected on overlay at ($x, $y), looking for button")

                            // Find clickable view at touch position in variationsContainer
                            val clickedView = variationsContainer?.let { findClickableViewAt(it, x, y) }
                            if (clickedView != null) {
                                Log.d(TAG, "Button clicked via overlay: ${clickedView.javaClass.simpleName}")
                                clickedView.performClick()
                            } else {
                                Log.d(TAG, "No clickable button found at touch position")
                            }
                            true // Consume the event
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        isSwipeInProgress = false
                        isFlickInProgress = false
                        swipeDirection = null
                        true
                    }
                    else -> true
                }
            }
            
            // Container for LEDs along the bottom edge
            val ledHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                5.5f, // Increased from 4f for better visibility
                context.resources.displayMetrics
            ).toInt()
            val ledBottomPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                0f,
                context.resources.displayMetrics
            ).toInt()
            val ledGap = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                1.5f, // Small gap between LED strips
                context.resources.displayMetrics
            ).toInt()
            
            ledContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                setPadding(0, 0, 0, ledBottomPadding)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            
            // Create LEDs for each modifier in order: SHIFT - SYM - unused - unused - CONTROL - ALT.
            // We split the width into 6 equal parts.
            shiftLed = createFlatLed(0, ledHeight, false) // Parte 1
            symLed = createFlatLed(0, ledHeight, false)   // Parte 2
            val unused1 = createFlatLed(0, ledHeight, false) // Parte 3 (unused)
            val unused2 = createFlatLed(0, ledHeight, false) // Parte 4 (unused)
            ctrlLed = createFlatLed(0, ledHeight, false)  // Parte 5
            altLed = createFlatLed(0, ledHeight, false)   // Parte 6
            
            // Hide unused parts (make them fully transparent)
            unused1.visibility = View.INVISIBLE
            unused2.visibility = View.INVISIBLE
            
            ledContainer?.apply {
                // Add LEDs in the correct order, each occupying 1/6 of the width with gaps
                addView(shiftLed, LinearLayout.LayoutParams(0, ledHeight, 1f).apply {
                    marginEnd = ledGap
                })
                addView(symLed, LinearLayout.LayoutParams(0, ledHeight, 1f).apply {
                    marginEnd = ledGap
                })
                addView(unused1, LinearLayout.LayoutParams(0, ledHeight, 1f).apply {
                    marginEnd = ledGap
                })
                addView(unused2, LinearLayout.LayoutParams(0, ledHeight, 1f).apply {
                    marginEnd = ledGap
                })
                addView(ctrlLed, LinearLayout.LayoutParams(0, ledHeight, 1f).apply {
                    marginEnd = ledGap
                })
                addView(altLed, LinearLayout.LayoutParams(0, ledHeight, 1f))
            }
            
            statusBarLayout?.apply {
                addView(modifiersContainer)
                addView(variationsWrapper) // Use wrapper instead of variationsContainer
                addView(emojiKeyboardContainer) // Griglia emoji prima dei LED
                addView(ledContainer) // LED sempre in fondo
            }
        } else if (emojiMapText.isNotEmpty()) {
            emojiMapTextView?.text = emojiMapText
        }
        return statusBarLayout!!
    }
    
    /**
     * Ensures the layout is created before updating.
     * This is important for candidates view which may not have been created yet.
     */
    private fun ensureLayoutCreated(emojiMapText: String = ""): LinearLayout? {
        return statusBarLayout ?: getOrCreateLayout(emojiMapText)
    }
    
    /**
     * Handles upward flick gesture for prediction selection.
     * Determines which zone (left/center/right) the flick was in and selects the corresponding prediction.
     */
    private fun handleFlickGesture(touchX: Float) {
        // Only handle flicks when predictions are displayed
        if (lastDisplayedPredictions.isEmpty()) {
            Log.d(TAG, "Flick detected but no predictions displayed")
            return
        }

        val screenWidth = context.resources.displayMetrics.widthPixels.toFloat()

        // Determine zone: divide screen into 3 equal parts
        val zone = when {
            touchX < screenWidth / 3 -> 0 // Left zone
            touchX < (screenWidth * 2 / 3) -> 1 // Center zone
            else -> 2 // Right zone
        }

        val zoneNames = listOf("LEFT", "CENTER", "RIGHT")
        Log.d(TAG, "Flick in ${zoneNames[zone]} zone (x=$touchX, screenWidth=$screenWidth)")

        // Get the prediction for this zone
        val prediction = lastDisplayedPredictions.getOrNull(zone)
        if (prediction != null) {
            Log.d(TAG, "Selecting prediction: $prediction")

            // Trigger haptic feedback
            performHapticFeedback()

            // Simulate prediction button click
            val inputConnection = currentInputConnection
            if (inputConnection != null) {
                // Use the PredictionButtonHandler to insert the word
                PredictionButtonHandler.createPredictionClickListener(
                    prediction,
                    inputConnection,
                    onPredictionSelectedListener
                ).onClick(null)

                Log.d(TAG, "Prediction '$prediction' selected via flick gesture")
            } else {
                Log.w(TAG, "No inputConnection available for flick gesture")
            }
        } else {
            Log.w(TAG, "No prediction available for zone $zone (only ${lastDisplayedPredictions.size} predictions)")
            // Still provide feedback to acknowledge the gesture
            performHapticFeedback()
        }
    }

    /**
     * Triggers haptic feedback for gesture recognition.
     * Uses View.performHapticFeedback() which doesn't require VIBRATE permission.
     */
    private fun performHapticFeedback() {
        try {
            // Use the overlay view or status bar layout for haptic feedback
            val feedbackView = variationsOverlay ?: statusBarLayout
            feedbackView?.performHapticFeedback(
                android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
            Log.d(TAG, "Haptic feedback triggered")
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering haptic feedback: ${e.message}", e)
        }
    }

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
     * Crea un LED rettangolare e piatto per un modificatore.
     */
    private fun createFlatLed(width: Int, height: Int, isActive: Boolean): View {
        val cornerRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            2f, // Rounded top corners for modern look
            context.resources.displayMetrics
        )
        
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(if (isActive) Color.WHITE else Color.argb(80, 255, 255, 255))
            // Rounded corners only on top
            cornerRadii = floatArrayOf(
                cornerRadius, cornerRadius, // Top left
                cornerRadius, cornerRadius, // Top right
                0f, 0f, // Bottom left
                0f, 0f  // Bottom right
            )
        }
        
        return View(context).apply {
            background = drawable
            layoutParams = LinearLayout.LayoutParams(width, height)
        }
    }
    
    /**
     * Aggiorna lo stato del LED SYM con colori specifici per pagina.
     * @param led Il view del LED SYM da aggiornare
     * @param symPage Il numero di pagina SYM (0=spento, 1=blu, 2=arancione)
     */
    private fun updateSymLed(led: View?, symPage: Int) {
        led?.let {
            val cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                2f, // Rounded top corners
                context.resources.displayMetrics
            )
            
            // Colors: blue for page 1, red/orange for page 2, gray when off (same as other LEDs)
            val color = when (symPage) {
                1 -> LED_COLOR_BLUE_ACTIVE // Blue for page 1
                2 -> LED_COLOR_RED_LOCKED // Red/orange for page 2 (same as locked state)
                else -> LED_COLOR_GRAY_OFF // Gray when off (same as Shift LED)
            }
            
            // Get previous color from tag (if exists)
            val previousColorTag = it.getTag(R.id.led_previous_color) as? Int
            val previousColor = previousColorTag ?: LED_COLOR_GRAY_OFF
            
            // Save new color to tag
            it.setTag(R.id.led_previous_color, color)
            
            // Animate color change for smooth transitions
            if (previousColor != color) {
                val animator = ValueAnimator.ofArgb(previousColor, color).apply {
                    duration = 200
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { animation ->
                        val animatedColor = animation.animatedValue as Int
                        val animatedDrawable = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            setColor(animatedColor)
                            // Rounded corners only on top
                            cornerRadii = floatArrayOf(
                                cornerRadius, cornerRadius, // Top left
                                cornerRadius, cornerRadius, // Top right
                                0f, 0f, // Bottom left
                                0f, 0f  // Bottom right
                            )
                        }
                        it.background = animatedDrawable
                    }
                }
                animator.start()
            } else {
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(color)
                    // Rounded corners only on top
                    cornerRadii = floatArrayOf(
                        cornerRadius, cornerRadius, // Top left
                        cornerRadius, cornerRadius, // Top right
                        0f, 0f, // Bottom left
                        0f, 0f  // Bottom right
                    )
                }
                it.background = drawable
            }
        }
    }
    
    /**
     * Aggiorna lo stato di un LED.
     * @param led Il view del LED da aggiornare
     * @param isLocked Se true, il LED è rosso (lockato), se false e isActive è true è blu (attivo), altrimenti grigio (spento)
     * @param isActive Se true e isLocked è false, il LED è blu (attivo)
     */
    private fun updateLed(led: View?, isLocked: Boolean, isActive: Boolean = false) {
        led?.let {
            val cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                2f, // Rounded top corners
                context.resources.displayMetrics
            )
            
            // More vibrant colors with better contrast
            val color = when {
                isLocked -> LED_COLOR_RED_LOCKED // Orange/red when locked
                isActive -> LED_COLOR_BLUE_ACTIVE // Blue when active
                else -> LED_COLOR_GRAY_OFF // Gray when off
            }
            
            // Get previous color from tag (if exists)
            val previousColorTag = it.getTag(R.id.led_previous_color) as? Int
            val previousColor = previousColorTag ?: LED_COLOR_GRAY_OFF
            
            // Save new color to tag
            it.setTag(R.id.led_previous_color, color)
            
            // Animate color change for smooth transitions
            if (previousColor != color) {
                val animator = ValueAnimator.ofArgb(previousColor, color).apply {
                    duration = 200
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { animation ->
                        val animatedColor = animation.animatedValue as Int
                        val animatedDrawable = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            setColor(animatedColor)
                            // Rounded corners only on top
                            cornerRadii = floatArrayOf(
                                cornerRadius, cornerRadius, // Top left
                                cornerRadius, cornerRadius, // Top right
                                0f, 0f, // Bottom left
                                0f, 0f  // Bottom right
                            )
                        }
                        it.background = animatedDrawable
                    }
                }
                animator.start()
            } else {
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(color)
                    // Rounded corners only on top
                    cornerRadii = floatArrayOf(
                        cornerRadius, cornerRadius, // Top left
                        cornerRadius, cornerRadius, // Top right
                        0f, 0f, // Bottom left
                        0f, 0f  // Bottom right
                    )
                }
                it.background = drawable
            }
        }
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
    
    /**
     * Aggiorna la griglia emoji/caratteri con le mappature SYM.
     * @param symMappings Le mappature da visualizzare
     * @param page La pagina attiva (1=emoji, 2=caratteri)
     * @param inputConnection L'input connection per inserire caratteri quando si clicca sui pulsanti
     */
    private fun updateEmojiKeyboard(symMappings: Map<Int, String>, page: Int, inputConnection: android.view.inputmethod.InputConnection? = null) {
        val container = emojiKeyboardContainer ?: return
        
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
        
        val keySpacing = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        
        // Calcola la larghezza fissa dei tasti basata sulla riga più lunga
        val maxKeysInRow = keyboardRows.maxOfOrNull { it.size } ?: 10
        val screenWidth = context.resources.displayMetrics.widthPixels
        val horizontalPadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            8f * 2, // padding sinistro + destro
            context.resources.displayMetrics
        ).toInt()
        val availableWidth = screenWidth - horizontalPadding
        val totalSpacing = keySpacing * (maxKeysInRow - 1)
        val fixedKeyWidth = (availableWidth - totalSpacing) / maxKeysInRow
        
        val keyHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            56f,
            context.resources.displayMetrics
        ).toInt()
        
        // Crea ogni riga della tastiera
        for ((rowIndex, row) in keyboardRows.withIndex()) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL // Centra le righe più corte
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    // Aggiungi margine solo tra le righe, non dopo l'ultima
                    if (rowIndex < keyboardRows.size - 1) {
                        bottomMargin = keySpacing
                    }
                }
            }
            
            for ((index, keyCode) in row.withIndex()) {
                val label = keyLabels[keyCode] ?: ""
                val content = symMappings[keyCode] ?: ""
                
                val keyButton = createEmojiKeyButton(label, content, keyHeight, page)
                emojiKeyButtons.add(keyButton)
                
                // Aggiungi click listener per rendere il pulsante touchabile
                if (content.isNotEmpty() && inputConnection != null) {
                    keyButton.isClickable = true
                    keyButton.isFocusable = true
                    keyButton.setOnClickListener {
                        // Inserisci il carattere/emoji quando si clicca
                        inputConnection.commitText(content, 1)
                        Log.d(TAG, "Clicked SYM button for keyCode $keyCode: $content")
                    }
                    
                    // Aggiungi feedback visivo quando il pulsante viene premuto
                    val originalBackground = keyButton.background
                    keyButton.setOnTouchListener { view, motionEvent ->
                        when (motionEvent.action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                // Dimmer lo sfondo quando premuto
                                if (originalBackground is GradientDrawable) {
                                    val pressedColor = Color.argb(80, 255, 255, 255) // Più opaco
                                    originalBackground.setColor(pressedColor)
                                }
                                view.invalidate()
                            }
                            android.view.MotionEvent.ACTION_UP,
                            android.view.MotionEvent.ACTION_CANCEL -> {
                                // Ripristina lo sfondo originale
                                if (originalBackground is GradientDrawable) {
                                    val normalColor = Color.argb(40, 255, 255, 255) // Sfondo normale
                                    originalBackground.setColor(normalColor)
                                }
                                view.invalidate()
                            }
                        }
                        false // Non consumare l'evento, lascia che il click listener funzioni
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
            
            // Aggiungi il pulsante ingranaggio alla fine della terza riga (a destra della M)
            if (rowIndex == 2) {
                val settingsButton = createSettingsButton(keyHeight)
                val iconSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    24f, // Aumentato del 20% (20f * 1.2 = 24f)
                    context.resources.displayMetrics
                ).toInt()
                rowLayout.addView(settingsButton, LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    marginStart = keySpacing
                    gravity = Gravity.CENTER_VERTICAL
                })
            }
            
            container.addView(rowLayout)
        }
    }
    
    /**
     * Crea il pulsante ingranaggio per aprire la schermata di personalizzazione SYM.
     */
    private fun createSettingsButton(height: Int): View {
        // Dimensione aumentata del 20% per l'icona
        val iconSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            14.4f, // Aumentato del 20% (12f * 1.2 = 14.4f)
            context.resources.displayMetrics
        ).toInt()
        
        val button = ImageView(context).apply {
            background = null // Nessuno sfondo
            setImageResource(R.drawable.ic_edit_24)
            // Grigio quasi nero invece di bianco
            setColorFilter(Color.rgb(40, 40, 40)) // Grigio molto scuro, quasi nero
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true // Permette il ridimensionamento
            maxWidth = iconSize // Limita la larghezza massima
            maxHeight = iconSize // Limita l'altezza massima
            setPadding(0, 0, 0, 0) // Nessun padding
            layoutParams = LinearLayout.LayoutParams(
                iconSize, // Larghezza fissa basata sulla dimensione dell'icona
                iconSize  // Altezza fissa basata sulla dimensione dell'icona
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            isClickable = true
            isFocusable = true
        }
        
        button.setOnClickListener {
            // Save current SYM page state temporarily (will be confirmed only if user presses back)
            val prefs = context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
            val currentSymPage = prefs.getInt("current_sym_page", 0)
            if (currentSymPage > 0) {
                // Save as pending - will be converted to restore only if user presses back
                SettingsManager.setPendingRestoreSymPage(context, currentSymPage)
            }
            
            // Apri SymCustomizationActivity direttamente
            val intent = Intent(context, SymCustomizationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Errore nell'apertura della schermata di personalizzazione SYM", e)
            }
        }
        
        return button
    }
    
    /**
     * Crea un tasto della griglia emoji/caratteri.
     * @param label La lettera del tasto
     * @param content L'emoji o carattere da mostrare
     * @param height L'altezza del tasto
     * @param page La pagina attiva (1=emoji, 2=caratteri)
     */
    private fun createEmojiKeyButton(label: String, content: String, height: Int, page: Int): View {
        val keyLayout = FrameLayout(context).apply {
            setPadding(0, 0, 0, 0) // Nessun padding per permettere all'emoji di occupare tutto lo spazio
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
        }
        
        // Background del tasto senza angoli arrotondati e senza bordi
        val drawable = GradientDrawable().apply {
            setColor(Color.argb(40, 255, 255, 255)) // Bianco semi-trasparente
            setCornerRadius(0f) // Nessun angolo arrotondato
            // Nessun bordo
        }
        keyLayout.background = drawable
        
        // Emoji/carattere deve occupare tutto il tasto, centrata
        // Calcola textSize in base all'altezza disponibile (convertendo da pixel a sp)
        val heightInDp = height / context.resources.displayMetrics.density
        val contentTextSize = if (page == 2) {
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
            // Per pagina 2 (caratteri), rendi bianco e in grassetto
            if (page == 2) {
                setTextColor(Color.WHITE)
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
            setTextColor(Color.WHITE) // Bianco 100% opaco
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
        
        // Calcola la larghezza fissa dei tasti basata sulla riga più lunga
        // Usa ViewTreeObserver per ottenere la larghezza effettiva del container dopo il layout
        val maxKeysInRow = keyboardRows.maxOfOrNull { it.size } ?: 10
        
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
                                val keyButton = row.getChildAt(j)
                                val layoutParams = keyButton.layoutParams as? LinearLayout.LayoutParams
                                layoutParams?.let {
                                    it.width = fixedKeyWidth
                                    keyButton.layoutParams = it
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
                gravity = Gravity.CENTER_HORIZONTAL // Centra le righe più corte
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (rowIndex < keyboardRows.size - 1) {
                        bottomMargin = keySpacing
                    }
                }
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
            
            container.addView(rowLayout)
        }
        
        return container
    }
    
    /**
     * Anima l'apparizione della griglia emoji (slide up + fade in).
     * @param backgroundView Il view dello sfondo da animare insieme
     */
    private fun animateEmojiKeyboardIn(view: View, backgroundView: View? = null) {
        val height = view.height
        if (height == 0) {
            // Se l'altezza non è ancora disponibile, usa una stima
            view.measure(
                View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
        }
        val measuredHeight = view.measuredHeight
        val startHeight = 0
        val endHeight = measuredHeight
        
        view.alpha = 0f
        view.translationY = measuredHeight.toFloat()
        view.visibility = View.VISIBLE
        
        // Anima anche lo sfondo se fornito
        backgroundView?.let { animateBackgroundIn(it) }
        
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 125
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                view.alpha = progress
                view.translationY = measuredHeight * (1f - progress)
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
     * @param backgroundView Il view dello sfondo da animare insieme
     * @param onAnimationEnd Callback chiamato quando l'animazione è completata
     */
    private fun animateEmojiKeyboardOut(view: View, backgroundView: View? = null, onAnimationEnd: (() -> Unit)? = null) {
        val height = view.height
        if (height == 0) {
            view.visibility = View.GONE
            onAnimationEnd?.invoke()
            return
        }

        // Anima anche lo sfondo se fornito
        backgroundView?.let { bgView ->
            animateBackgroundOut(bgView, null)
        }

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

    /**
     * Anima l'apparizione dei suggerimenti (fade in).
     */
    private fun animateVariationsIn(view: View) {
        view.alpha = 0f
        view.visibility = View.VISIBLE

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 75
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                view.alpha = progress
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.alpha = 1f
                }
            })
        }
        animator.start()
    }

    /**
     * Anima la scomparsa dei suggerimenti (fade out).
     * @param onAnimationEnd Callback chiamato quando l'animazione è completata
     */
    private fun animateVariationsOut(view: View, onAnimationEnd: (() -> Unit)? = null) {
        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 50
            interpolator = AccelerateDecelerateInterpolator()
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
        }
        animator.start()
    }

    private fun animateMicrophoneOut(view: View, onAnimationEnd: (() -> Unit)? = null) {
        if (view.visibility != View.VISIBLE) {
            view.visibility = View.GONE
            view.alpha = 1f
            onAnimationEnd?.invoke()
            return
        }

        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 75
            interpolator = AccelerateDecelerateInterpolator()
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
        }
        animator.start()
    }

    /**
     * Anima l'apparizione dello sfondo nero (fade in).
     */
    private fun animateBackgroundIn(view: View) {
        val colorDrawable = ColorDrawable(DEFAULT_BACKGROUND)
        view.background = colorDrawable
        colorDrawable.alpha = 0

        val animator = ValueAnimator.ofInt(0, 255).apply {
            duration = 125
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val alpha = animation.animatedValue as Int
                (view.background as? ColorDrawable)?.alpha = alpha
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    (view.background as? ColorDrawable)?.alpha = 255
                }
            })
        }
        animator.start()
    }

    /**
     * Anima la scomparsa dello sfondo nero (fade out).
     * @param onAnimationEnd Callback chiamato quando l'animazione è completata
     */
    private fun animateBackgroundOut(view: View, onAnimationEnd: (() -> Unit)? = null) {
        val background = view.background
        if (background !is ColorDrawable) {
            // Se non è un ColorDrawable, creane uno nuovo
            val colorDrawable = ColorDrawable(DEFAULT_BACKGROUND)
            colorDrawable.alpha = 255
            view.background = colorDrawable
        }

        val animator = ValueAnimator.ofInt(255, 0).apply {
            duration = 100
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val alpha = animation.animatedValue as Int
                (view.background as? ColorDrawable)?.alpha = alpha
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    (view.background as? ColorDrawable)?.alpha = 255
                    onAnimationEnd?.invoke()
                }
            })
        }
        animator.start()
    }
    
    /**
     * Crea un pulsante per una variazione.
     */
    private fun createVariationButton(
        variation: String,
        inputConnection: android.view.inputmethod.InputConnection?,
        buttonWidth: Int
    ): TextView {
        // Converti dp in pixel
        val dp4 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            4f,
            context.resources.displayMetrics
        ).toInt()
        val dp6 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            6f,
            context.resources.displayMetrics
        ).toInt()
        val dp3 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            3f, // Spazio ridotto tra i pulsanti
            context.resources.displayMetrics
        ).toInt()
        
        // Altezza fissa per tutti i pulsanti (quadrati, stessa della larghezza)
        val buttonHeight = buttonWidth
        
        // Crea il background del pulsante (rettangolo senza angoli arrotondati, scuro)
        val drawable = GradientDrawable().apply {
            setColor(Color.rgb(17, 17, 17)) // Grigio quasi nero
            setCornerRadius(0f) // Nessun angolo arrotondato
            // Nessun bordo
        }
        
        // Crea un drawable per lo stato pressed (più chiaro)
        val pressedDrawable = GradientDrawable().apply {
            setColor(Color.rgb(38, 0, 255)) // azzurro quando pressed
            setCornerRadius(0f) // Nessun angolo arrotondato
            // Nessun bordo
        }
        
        // Crea uno StateListDrawable per gestire gli stati (normale e pressed)
        val stateListDrawable = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), drawable) // Stato normale
        }
        
        val button = TextView(context).apply {
            text = variation
            textSize = 17.6f // 16f * 1.1 (aumentato del 10%)
            setTextColor(Color.WHITE) // Testo bianco
            setTypeface(null, android.graphics.Typeface.BOLD) // Testo in grassetto
            gravity = Gravity.CENTER
            // Padding
            setPadding(dp6, dp4, dp6, dp4)
            background = stateListDrawable
            layoutParams = LinearLayout.LayoutParams(
                buttonWidth, // Larghezza calcolata dinamicamente
                buttonHeight  // Altezza fissa (quadrato)
            ).apply {
                marginEnd = dp3 // Margine ridotto tra i pulsanti
            }
            // Rendi il pulsante clickabile
            isClickable = true
            isFocusable = true
        }
        
        // Aggiungi il listener per il click
        button.setOnClickListener(
            VariationButtonHandler.createVariationClickListener(
                variation,
                inputConnection,
                onVariationSelectedListener
            )
        )
        
        return button
    }

    /**
     * Creates a prediction button (word suggestion).
     */
    private fun createPredictionButton(
        prediction: String,
        inputConnection: android.view.inputmethod.InputConnection?,
        buttonWidth: Int
    ): TextView {
        // Convert dp to pixels
        val dp4 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        val dp6 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            6f,
            context.resources.displayMetrics
        ).toInt()
        val dp3 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            context.resources.displayMetrics
        ).toInt()

        val buttonHeight = buttonWidth

        // Create background (slightly lighter than variation buttons to differentiate)
        val drawable = GradientDrawable().apply {
            setColor(Color.rgb(25, 25, 25)) // Slightly lighter gray
            setCornerRadius(0f)
        }

        // Pressed state (blue highlight)
        val pressedDrawable = GradientDrawable().apply {
            setColor(Color.rgb(38, 0, 255)) // Blue when pressed
            setCornerRadius(0f)
        }

        // State list drawable
        val stateListDrawable = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), drawable)
        }

        val button = TextView(context).apply {
            text = prediction
            textSize = 16f // Slightly smaller than variations
            setTextColor(Color.rgb(200, 200, 200)) // Slightly dimmer white to differentiate
            setTypeface(null, android.graphics.Typeface.NORMAL) // Not bold (unlike variations)
            gravity = Gravity.CENTER
            setPadding(dp6, dp4, dp6, dp4)
            background = stateListDrawable
            layoutParams = LinearLayout.LayoutParams(
                buttonWidth,
                buttonHeight
            ).apply {
                marginEnd = dp3
            }
            isClickable = true
            isFocusable = true
            // Single line, ellipsize if too long
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        // Add click listener
        button.setOnClickListener(
            PredictionButtonHandler.createPredictionClickListener(
                prediction,
                inputConnection,
                onPredictionSelectedListener
            )
        )

        return button
    }

    /**
     * Crea il pulsante microfono (usato nei suggerimenti).
     */
    private fun createMicrophoneButton(buttonSize: Int): ImageView {
        val drawable = GradientDrawable().apply {
            setColor(Color.rgb(17, 17, 17))
            setCornerRadius(0f)
        }

        return ImageView(context).apply {
            setImageResource(R.drawable.ic_baseline_mic_24)
            setColorFilter(Color.WHITE)
            background = drawable
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                buttonSize,
                buttonSize
            )
        }
    }
    
    /**
     * Creates a small settings button for the status bar (placed to the right of the microphone).
     */
    private fun createStatusBarSettingsButton(buttonSize: Int): ImageView {
        // Smaller icon size - 60% of button size, then 10% smaller = 54% of button size
        val iconSize = (buttonSize * 0.54f).toInt()
        val dp3 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            context.resources.displayMetrics
        ).toInt()
        
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_settings_24)
            // Dark gray color (not invasive)
            setColorFilter(Color.rgb(100, 100, 100))
            background = null // No background
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = true
            setPadding(dp3, dp3, dp3, dp3)
            layoutParams = LinearLayout.LayoutParams(
                buttonSize,
                buttonSize
            )
        }
    }
    
    /**
     * Avvia il riconoscimento vocale di Google Voice Typing.
     */
    private fun startSpeechRecognition(inputConnection: android.view.inputmethod.InputConnection?) {
        try {
            val intent = Intent(context, SpeechRecognitionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                       Intent.FLAG_ACTIVITY_NO_HISTORY or
                       Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }

            context.startActivity(intent)
            Log.d(TAG, "Riconoscimento vocale avviato")
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel lancio del riconoscimento vocale", e)
        }
    }
    
    /**
     * Gestisce il risultato del riconoscimento vocale.
     */
    fun handleSpeechResult(text: String, inputConnection: android.view.inputmethod.InputConnection?) {
        if (text.isNotEmpty() && inputConnection != null) {
            Log.d(TAG, "Inserimento testo riconosciuto: $text")
            inputConnection.commitText(text, 1)
            // Notifica il listener se presente
            onSpeechResultListener?.invoke(text)
        }
    }
    
    /**
     * Crea un pulsante placeholder trasparente per riempire gli slot vuoti.
     */
    private fun createPlaceholderButton(buttonWidth: Int): View {
        // Larghezza e altezza fissa per tutti i pulsanti (quadrati, circa 48dp)
        val buttonSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            48f, 
            context.resources.displayMetrics
        ).toInt()
        val dp3 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            3f, // Spazio ridotto tra i pulsanti
            context.resources.displayMetrics
        ).toInt()
        
        // Altezza fissa per tutti i pulsanti (quadrati, stessa della larghezza)
        val buttonHeight = buttonWidth
        
        // Crea un background trasparente
        val drawable = GradientDrawable().apply {
            setColor(Color.TRANSPARENT) // Completamente trasparente
            setCornerRadius(0f) // Nessun angolo arrotondato
        }
        
        val button = View(context).apply {
            background = drawable
            layoutParams = LinearLayout.LayoutParams(
                buttonWidth, // Larghezza calcolata dinamicamente
                buttonHeight  // Altezza fissa (quadrato)
            ).apply {
                marginEnd = dp3 // Margine ridotto tra i pulsanti
            }
            // Non clickabile
            isClickable = false
            isFocusable = false
        }
        
        return button
    }

    /**
     * Mostra i suggerimenti (variazioni) e il microfono.
     * Questa funzione viene chiamata dopo che la griglia SYM è completamente collassata.
     *
     * PRIORITIZATION:
     * 1. Character variations (if available)
     * 2. Word predictions (if no variations)
     */
    private fun showVariationsAndMicrophone(snapshot: StatusSnapshot, inputConnection: android.view.inputmethod.InputConnection?) {
        val variationsContainerView = variationsContainer ?: return
        variationsContainerView.visibility = View.VISIBLE
        variationsWrapper?.visibility = View.VISIBLE
        variationsOverlay?.visibility = View.VISIBLE

        // Check variations first (highest priority)
        val limitedVariations = if (snapshot.variations.isNotEmpty() && snapshot.lastInsertedChar != null) {
            snapshot.variations.take(7)
        } else {
            emptyList()
        }

        // Decide whether to show variations or predictions
        val shouldShowVariations = limitedVariations.isNotEmpty()
        val shouldShowPredictions = !shouldShowVariations && snapshot.predictions.isNotEmpty()

        if (shouldShowVariations) {
            // Show character variations (existing logic)
            showCharacterVariations(limitedVariations, inputConnection, variationsContainerView)
        } else if (shouldShowPredictions) {
            // Show word predictions (NEW!)
            showWordPredictions(snapshot.predictions, inputConnection, variationsContainerView)
        } else {
            // Show empty state with microphone
            showEmptyState(variationsContainerView)
        }
    }

    /**
     * Shows character variations in the status bar.
     */
    private fun showCharacterVariations(
        limitedVariations: List<String>,
        inputConnection: android.view.inputmethod.InputConnection?,
        variationsContainerView: LinearLayout
    ) {

        // Verifica se le variazioni sono le stesse di quelle già visualizzate
        val variationsChanged = limitedVariations != lastDisplayedVariations
        val hasExistingRow = currentVariationsRow != null && 
                            currentVariationsRow?.parent == variationsContainerView &&
                            currentVariationsRow?.visibility == View.VISIBLE

        // Se le variazioni non sono cambiate e c'è già una riga visualizzata, non fare nulla
        if (!variationsChanged && hasExistingRow) {
            return
        }

        // Le variazioni sono cambiate o non c'è una riga esistente, ricrea tutto
        variationButtons.clear()
        currentVariationsRow?.let {
            variationsContainerView.removeView(it)
            currentVariationsRow = null
        }

        val screenWidth = context.resources.displayMetrics.widthPixels
        val leftPadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            64f,
            context.resources.displayMetrics
        ).toInt()
        // Right padding reduced by 67% (from 64dp to 19.84dp)
        val rightPadding = (leftPadding * 0.31f).toInt()
        val availableWidth = screenWidth - leftPadding - rightPadding

        val spacingBetweenButtons = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            context.resources.displayMetrics
        ).toInt()
        // Total elements: 7 variations + 1 microphone + 1 settings = 9 elements
        // Total spacing: 8 spaces between 9 elements
        val totalSpacing = spacingBetweenButtons * 8
        val buttonWidth = max(1, (availableWidth - totalSpacing) / 9)

        val variationsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        currentVariationsRow = variationsRow

        val rowLayoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )
        variationsContainerView.addView(variationsRow, 0, rowLayoutParams)

        lastDisplayedVariations = limitedVariations

        for (variation in limitedVariations) {
            val button = createVariationButton(variation, inputConnection, buttonWidth)
            variationButtons.add(button)
            variationsRow.addView(button)
        }

        val placeholderCount = 7 - limitedVariations.size
        for (i in 0 until placeholderCount) {
            val placeholderButton = createPlaceholderButton(buttonWidth)
            variationsRow.addView(placeholderButton)
        }

        val microphoneButton = microphoneButtonView ?: createMicrophoneButton(buttonWidth)
        microphoneButtonView = microphoneButton
        (microphoneButton.parent as? ViewGroup)?.removeView(microphoneButton)
        val micParams = LinearLayout.LayoutParams(buttonWidth, buttonWidth)
        variationsContainerView.addView(microphoneButton, micParams)
        microphoneButton.setOnClickListener {
            startSpeechRecognition(inputConnection)
        }
        microphoneButton.alpha = 1f
        microphoneButton.visibility = View.VISIBLE
        
        // Add settings button to the right of microphone
        val settingsButton = settingsButtonView ?: createStatusBarSettingsButton(buttonWidth)
        settingsButtonView = settingsButton
        (settingsButton.parent as? ViewGroup)?.removeView(settingsButton)
        val settingsParams = LinearLayout.LayoutParams(buttonWidth, buttonWidth).apply {
            // Move icon 10% higher (negative top margin = 10% of button height)
            topMargin = (-buttonWidth * 0.1f).toInt()
        }
        variationsContainerView.addView(settingsButton, settingsParams)
        settingsButton.setOnClickListener {
            // Open Settings screen
            // FLAG_ACTIVITY_NEW_TASK is required when starting activity from a service
            val intent = Intent(context, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening Settings screen", e)
            }
        }
        settingsButton.alpha = 1f
        settingsButton.visibility = View.VISIBLE

        // Fai il fade in solo se le variazioni sono cambiate
        if (variationsChanged) {
            animateVariationsIn(variationsRow)
        } else {
            // Se le variazioni sono le stesse ma non c'era una riga, imposta direttamente la visibilità senza animazione
            variationsRow.alpha = 1f
            variationsRow.visibility = View.VISIBLE
        }
    }

    /**
     * Shows word predictions in the status bar (triple-split layout).
     */
    private fun showWordPredictions(
        predictions: List<String>,
        inputConnection: android.view.inputmethod.InputConnection?,
        variationsContainerView: LinearLayout
    ) {
        // Take max 3 predictions
        val limitedPredictions = predictions.take(3)

        // Check if predictions changed
        val predictionsChanged = limitedPredictions != lastDisplayedPredictions
        val hasExistingRow = currentPredictionsRow != null &&
                            currentPredictionsRow?.parent == variationsContainerView &&
                            currentPredictionsRow?.visibility == View.VISIBLE

        // If predictions haven't changed and row exists, do nothing
        if (!predictionsChanged && hasExistingRow) {
            return
        }

        // Clear previous predictions
        predictionButtons.clear()
        currentPredictionsRow?.let {
            variationsContainerView.removeView(it)
            currentPredictionsRow = null
        }

        // Also clear any variation rows
        currentVariationsRow?.let {
            variationsContainerView.removeView(it)
            currentVariationsRow = null
        }

        val screenWidth = context.resources.displayMetrics.widthPixels
        val leftPadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            16f, // Less padding for predictions (wider buttons)
            context.resources.displayMetrics
        ).toInt()
        val rightPadding = leftPadding
        val availableWidth = screenWidth - leftPadding - rightPadding

        val spacingBetweenButtons = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            8f, // More spacing between predictions
            context.resources.displayMetrics
        ).toInt()

        // Calculate button width for triple-split layout
        // 3 predictions + 1 microphone + 1 settings = 5 elements
        // Total spacing: 4 spaces between 5 elements
        val totalSpacing = spacingBetweenButtons * 4
        val predictionButtonWidth = ((availableWidth - totalSpacing) * 0.6f / 3).toInt() // 60% for predictions
        val iconButtonWidth = ((availableWidth - totalSpacing) * 0.2f).toInt() // 20% each for mic & settings

        val predictionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        currentPredictionsRow = predictionsRow

        val rowLayoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )
        variationsContainerView.addView(predictionsRow, 0, rowLayoutParams)

        lastDisplayedPredictions = limitedPredictions

        // Add prediction buttons
        for (prediction in limitedPredictions) {
            val button = createPredictionButton(prediction, inputConnection, predictionButtonWidth)
            predictionButtons.add(button)
            predictionsRow.addView(button)
        }

        // Add placeholder if less than 3 predictions
        val placeholderCount = 3 - limitedPredictions.size
        for (i in 0 until placeholderCount) {
            val placeholderButton = createPlaceholderButton(predictionButtonWidth)
            predictionsRow.addView(placeholderButton)
        }

        // Add microphone button
        val microphoneButton = microphoneButtonView ?: createMicrophoneButton(iconButtonWidth)
        microphoneButtonView = microphoneButton
        (microphoneButton.parent as? ViewGroup)?.removeView(microphoneButton)
        val micParams = LinearLayout.LayoutParams(iconButtonWidth, iconButtonWidth)
        variationsContainerView.addView(microphoneButton, micParams)
        microphoneButton.setOnClickListener {
            startSpeechRecognition(inputConnection)
        }
        microphoneButton.alpha = 1f
        microphoneButton.visibility = View.VISIBLE

        // Add settings button
        val settingsButton = settingsButtonView ?: createStatusBarSettingsButton(iconButtonWidth)
        settingsButtonView = settingsButton
        (settingsButton.parent as? ViewGroup)?.removeView(settingsButton)
        val settingsParams = LinearLayout.LayoutParams(iconButtonWidth, iconButtonWidth).apply {
            topMargin = (-iconButtonWidth * 0.1f).toInt()
        }
        variationsContainerView.addView(settingsButton, settingsParams)
        settingsButton.setOnClickListener {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_settings", true)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening Settings screen", e)
            }
        }
        settingsButton.alpha = 1f
        settingsButton.visibility = View.VISIBLE

        // Animate in
        if (predictionsChanged) {
            animateVariationsIn(predictionsRow) // Reuse variation animation
        } else {
            predictionsRow.alpha = 1f
            predictionsRow.visibility = View.VISIBLE
        }
    }

    /**
     * Shows empty state (just microphone and settings buttons).
     */
    private fun showEmptyState(variationsContainerView: LinearLayout) {
        // Clear any existing rows
        currentVariationsRow?.let {
            variationsContainerView.removeView(it)
            currentVariationsRow = null
        }
        currentPredictionsRow?.let {
            variationsContainerView.removeView(it)
            currentPredictionsRow = null
        }

        lastDisplayedVariations = emptyList()
        lastDisplayedPredictions = emptyList()

        // Calculate button size
        val buttonWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            40f,
            context.resources.displayMetrics
        ).toInt()

        // Add microphone button
        val microphoneButton = microphoneButtonView ?: createMicrophoneButton(buttonWidth)
        microphoneButtonView = microphoneButton
        (microphoneButton.parent as? ViewGroup)?.removeView(microphoneButton)
        val micParams = LinearLayout.LayoutParams(buttonWidth, buttonWidth)
        variationsContainerView.addView(microphoneButton, micParams)
        microphoneButton.alpha = 1f
        microphoneButton.visibility = View.VISIBLE

        // Add settings button
        val settingsButton = settingsButtonView ?: createStatusBarSettingsButton(buttonWidth)
        settingsButtonView = settingsButton
        (settingsButton.parent as? ViewGroup)?.removeView(settingsButton)
        val settingsParams = LinearLayout.LayoutParams(buttonWidth, buttonWidth).apply {
            topMargin = (-buttonWidth * 0.1f).toInt()
        }
        variationsContainerView.addView(settingsButton, settingsParams)
        settingsButton.alpha = 1f
        settingsButton.visibility = View.VISIBLE
    }

    fun update(snapshot: StatusSnapshot, emojiMapText: String = "", inputConnection: android.view.inputmethod.InputConnection? = null, symMappings: Map<Int, String>? = null) {
        // Save current inputConnection for swipe gestures
        currentInputConnection = inputConnection

        // Update SYM mode state (swipe pad disabled when SYM mode is active)
        isSymModeActive = snapshot.symPage > 0
        
        // Ensure layout is created (important for candidates view which may not have been created yet)
        val layout = ensureLayoutCreated(emojiMapText) ?: return
        val modifiersContainerView = modifiersContainer ?: return
        val emojiView = emojiMapTextView ?: return
        val variationsContainerView = variationsContainer ?: return
        val emojiKeyboardView = emojiKeyboardContainer ?: return
        emojiView.visibility = View.GONE // Sempre nascosto, usiamo la griglia

        if (snapshot.navModeActive) {
            // Nascondi completamente la barra di stato nel nav mode
            // La notifica è sufficiente per indicare che il nav mode è attivo
            layout.visibility = View.GONE
            return
        }
        
        // Mostra la barra quando non siamo in nav mode
        layout.visibility = View.VISIBLE

        // Assicurati che lo sfondo sia un ColorDrawable per poter animare l'alpha
        if (layout.background !is ColorDrawable) {
            layout.background = ColorDrawable(DEFAULT_BACKGROUND)
        } else {
            // Se è già un ColorDrawable, assicurati che sia completamente opaco quando non siamo in SYM
            if (snapshot.symPage == 0) {
                (layout.background as ColorDrawable).alpha = 255
            }
        }
        
        // Hide modifiers container (no longer needed since red dot indicator is removed)
        modifiersContainerView.visibility = View.GONE
        
        // Aggiorna i LED nel bordo inferiore
        // Shift: rosso se lockato (Caps Lock), blu se attivo (premuto/one-shot), grigio se spento
        val shiftLocked = snapshot.capsLockEnabled
        val shiftActive = (snapshot.shiftPhysicallyPressed || snapshot.shiftOneShot) && !shiftLocked
        updateLed(shiftLed, shiftLocked, shiftActive)
        
        // Ctrl: rosso se lockato, blu se attivo (premuto/one-shot), grigio se spento
        val ctrlLocked = snapshot.ctrlLatchActive
        val ctrlActive = (snapshot.ctrlPhysicallyPressed || snapshot.ctrlOneShot) && !ctrlLocked
        updateLed(ctrlLed, ctrlLocked, ctrlActive)
        
        // Alt: rosso se lockato, blu se attivo (premuto/one-shot), grigio se spento
        val altLocked = snapshot.altLatchActive
        val altActive = (snapshot.altPhysicallyPressed || snapshot.altOneShot) && !altLocked
        updateLed(altLed, altLocked, altActive)
        
        // SYM: blu per pagina 1, arancione per pagina 2, grigio se spento
        updateSymLed(symLed, snapshot.symPage)
        
        // Gestisci le animazioni tra SYM e suggerimenti
        if (snapshot.symPage > 0 && symMappings != null) {
            updateEmojiKeyboard(symMappings, snapshot.symPage, inputConnection)
            // Resetta le variazioni visualizzate quando SYM viene attivato
            lastDisplayedVariations = emptyList()
            val showSymKeyboard = {
                if (emojiKeyboardView.visibility != View.VISIBLE) {
                    animateEmojiKeyboardIn(emojiKeyboardView, layout)
                }
                variationsContainerView.visibility = View.GONE
                variationsWrapper?.visibility = View.GONE
                variationsOverlay?.visibility = View.GONE
            }

            if (emojiKeyboardView.visibility != View.VISIBLE) {
                var pendingAnimations = 0
                fun animationCompleted() {
                    pendingAnimations--
                    if (pendingAnimations == 0) {
                        showSymKeyboard()
                    }
                }

                currentVariationsRow?.let { row ->
                    if (row.parent == variationsContainerView && row.visibility == View.VISIBLE) {
                        pendingAnimations++
                        animateVariationsOut(row) {
                            (row.parent as? ViewGroup)?.removeView(row)
                            if (currentVariationsRow == row) {
                                currentVariationsRow = null
                            }
                            animationCompleted()
                        }
                    }
                }

                microphoneButtonView?.let { microphone ->
                    if (microphone.visibility == View.VISIBLE) {
                        // Rimuovi immediatamente il microfono dal container per evitare che si muova durante l'animazione
                        (microphone.parent as? ViewGroup)?.removeView(microphone)
                        // Nascondi immediatamente senza animazione per evitare movimenti visibili
                        microphone.visibility = View.GONE
                        microphone.alpha = 1f
                        animationCompleted()
                    }
                }
                
                settingsButtonView?.let { settings ->
                    if (settings.visibility == View.VISIBLE) {
                        // Rimuovi immediatamente il pulsante settings dal container
                        (settings.parent as? ViewGroup)?.removeView(settings)
                        settings.visibility = View.GONE
                        settings.alpha = 1f
                        animationCompleted()
                    }
                }

                if (pendingAnimations == 0) {
                    showSymKeyboard()
                }
            } else {
                microphoneButtonView?.visibility = View.GONE
                settingsButtonView?.visibility = View.GONE
            }
            return
        }

        // SYM si sta disattivando
        if (emojiKeyboardView.visibility == View.VISIBLE) {
            animateEmojiKeyboardOut(emojiKeyboardView, layout) {
                showVariationsAndMicrophone(snapshot, inputConnection)
            }
        } else {
            showVariationsAndMicrophone(snapshot, inputConnection)
        }
    }
}


