package it.palsoftware.pastiera.inputmethod.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.StatusBarController
import kotlin.math.roundToInt

/**
 * Compact controller around the LED strip at the bottom of the IME status bar.
 */
class LedStatusView(
    private val context: Context
) {
    companion object {
        private val LED_COLOR_GRAY_OFF = Color.argb(100, 17, 17, 17)
        private val LED_COLOR_RED_LOCKED = Color.rgb(247, 99, 0)
        private val LED_COLOR_BLUE_ACTIVE = Color.rgb(100, 150, 255)
    }

    private val ledHeight: Int by lazy {
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            5.5f,
            context.resources.displayMetrics
        ).toInt()
    }
    private val topPadding: Int by lazy {
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            1f,
            context.resources.displayMetrics
        ).toInt()
    }
    private val cornerRadius: Float by lazy {
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            context.resources.displayMetrics
        )
    }

    private var container: ModifierLedCanvas? = null
    private val ledsByState = mutableMapOf<ModifierLedState, MutableList<View>>()
    private val segmentsByView = mutableMapOf<View, ModifierLedSegment>()
    private val statePriority = mutableMapOf<ModifierLedState, Int>()

    var bottomCornerRadiiPx: Pair<Int, Int>? = null
        set(value) {
            if (field == value) return
            field = value
            container?.cornerRadiiPx = value
            container?.let { canvas ->
                for (index in 0 until canvas.childCount) canvas.getChildAt(index).invalidate()
            }
        }

    internal var layout: ModifierLedLayout = ModifierLedLayouts.DEFAULT
        set(value) {
            if (field == value) return
            field = value
            rebuildSegments()
        }

    var onLongPressListener: (() -> Unit)? = null
    var themeOverride: KeyboardThemeColors? = null

    fun ensureView(): ViewGroup {
        container?.let { return it }

        container = ModifierLedCanvas(context, ledHeight).apply {
            cornerRadiiPx = bottomCornerRadiiPx
            setPadding(0, topPadding, 0, 0)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnLongClickListener {
                onLongPressListener?.invoke()
                true
            }
        }

        rebuildSegments()

        return container!!
    }

    fun getView(): ViewGroup? = container

    fun update(snapshot: StatusBarController.StatusSnapshot) {
        val shiftLocked = snapshot.capsLockEnabled
        val shiftActive = (snapshot.shiftPhysicallyPressed || snapshot.shiftOneShot) && !shiftLocked
        updateLeds(ModifierLedState.SHIFT, shiftLocked, shiftActive)

        val ctrlLocked = snapshot.ctrlLatchActive
        val ctrlActive = (snapshot.ctrlPhysicallyPressed || snapshot.ctrlOneShot) && !ctrlLocked
        updateLeds(ModifierLedState.CTRL, ctrlLocked, ctrlActive)

        val altLocked = snapshot.altLatchActive
        val altActive = (snapshot.altPhysicallyPressed || snapshot.altOneShot) && !altLocked
        updateLeds(ModifierLedState.ALT, altLocked, altActive)

        updateSymLeds(snapshot.symPage)
    }

    private fun rebuildSegments() {
        val canvas = container ?: return
        ledsByState.clear()
        segmentsByView.clear()
        canvas.replaceSegments(layout.segments) { segment ->
            createLedView(themeOverride?.ledInactive ?: LED_COLOR_GRAY_OFF, segment).also { led ->
                ledsByState.getOrPut(segment.state) { mutableListOf() }.add(led)
            }
        }
    }

    private fun createLedView(initialColor: Int, segment: ModifierLedSegment): View {
        return View(context).apply {
            segmentsByView[this] = segment
            background = createDrawable(initialColor, segment)
            setTag(R.id.led_previous_color, initialColor)
        }
    }

    private fun createDrawable(color: Int, segment: ModifierLedSegment): GradientDrawable {
        return object : GradientDrawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }

            override fun draw(canvas: Canvas) {
                val radii = bottomCornerRadiiPx
                if (radii == null) {
                    super.draw(canvas)
                    return
                }
                // One physical contour per side in rounded mode. Alt/Sym and
                // Shift share it; a locked modifier wins over an active one.
                if (layout == ModifierLedLayouts.TITAN_2_ELITE && segment.y == 0f) return
                if (layout == ModifierLedLayouts.TITAN_2_ELITE && segment.state == ModifierLedState.SHIFT) {
                    val otherState = if (segment.x < 0.5f) ModifierLedState.ALT else ModifierLedState.SYM
                    val priority = maxOf(statePriority[ModifierLedState.SHIFT] ?: 0, statePriority[otherState] ?: 0)
                    paint.color = when (priority) {
                        2 -> themeOverride?.ledLocked ?: LED_COLOR_RED_LOCKED
                        1 -> themeOverride?.ledActive ?: LED_COLOR_BLUE_ACTIVE
                        else -> themeOverride?.ledInactive ?: LED_COLOR_GRAY_OFF
                    }
                }
                val width = bounds.width().toFloat()
                val height = bounds.height().toFloat()
                // Each row follows a concentric contour, so the indicator retains
                // its thickness through the bend instead of being cut off by it.
                val inset = (1f - segment.y - segment.height / 2f) * ledHeight + topPadding
                val leftRadius = radii.first.toFloat().coerceIn(inset, maxOf(inset, width / 2f))
                val rightRadius = radii.second.toFloat().coerceIn(inset, maxOf(inset, width / 2f))
                val leftArc = leftRadius - inset
                val rightArc = rightRadius - inset
                val contour = Path().apply {
                    moveTo(inset, 0f)
                    lineTo(inset, height - leftRadius)
                    if (leftArc > 0f) {
                        arcTo(inset, height - leftRadius - leftArc,
                            leftRadius + leftArc, height - inset, 180f, -90f, false)
                    }
                    lineTo(width - rightRadius, height - inset)
                    if (rightArc > 0f) {
                        arcTo(width - rightRadius - rightArc, height - rightRadius - rightArc,
                            width - inset, height - inset, 90f, -90f, false)
                    }
                    lineTo(width - inset, 0f)
                }
                val measure = PathMeasure(contour, false)
                paint.strokeWidth = segment.height * ledHeight
                val joinRightIndicators = layout == ModifierLedLayouts.TITAN_2_ELITE &&
                    (segment.state == ModifierLedState.CTRL ||
                        (segment.state == ModifierLedState.SHIFT && segment.x > 0.5f))
                paint.strokeCap = if (joinRightIndicators) Paint.Cap.BUTT else Paint.Cap.ROUND
                // Leave room for the round caps at both ends of each segment.
                val cap = if (joinRightIndicators) 0f else paint.strokeWidth / 2f
                val start = measure.length * segment.x + cap
                val endFraction = if (joinRightIndicators && segment.state == ModifierLedState.CTRL) {
                    layout.segments.first { it.state == ModifierLedState.SHIFT && it.x > segment.x }.x
                } else segment.x + segment.width
                val end = measure.length * endFraction - cap
                if (end > start) {
                    val stroke = Path()
                    measure.getSegment(start, end, stroke, true)
                    canvas.drawPath(stroke, paint)
                }
            }
        }.apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = this@LedStatusView.cornerRadius
        }
    }

    private fun updateLeds(state: ModifierLedState, isLocked: Boolean, isActive: Boolean = false) {
        statePriority[state] = if (isLocked) 2 else if (isActive) 1 else 0
        val theme = themeOverride
        val targetColor = when {
            isLocked -> theme?.ledLocked ?: LED_COLOR_RED_LOCKED
            isActive -> theme?.ledActive ?: LED_COLOR_BLUE_ACTIVE
            else -> theme?.ledInactive ?: LED_COLOR_GRAY_OFF
        }
        ledsByState[state].orEmpty().forEach { led -> animateLedColor(led, targetColor) }
    }

    private fun updateSymLeds(symPage: Int) {
        statePriority[ModifierLedState.SYM] = if (symPage == 2) 2 else if (symPage > 0) 1 else 0
        val theme = themeOverride
        val targetColor = when (symPage) {
            1 -> theme?.ledActive ?: LED_COLOR_BLUE_ACTIVE
            2 -> theme?.ledLocked ?: LED_COLOR_RED_LOCKED
            3 -> theme?.ledActive ?: LED_COLOR_BLUE_ACTIVE
            4 -> theme?.ledActive ?: LED_COLOR_BLUE_ACTIVE
            else -> theme?.ledInactive ?: LED_COLOR_GRAY_OFF
        }
        ledsByState[ModifierLedState.SYM].orEmpty().forEach { led -> animateLedColor(led, targetColor) }
        // The visible idle contour depends on both the upper and lower states.
        ledsByState[ModifierLedState.SHIFT].orEmpty().forEach { it.invalidate() }
    }

    private fun animateLedColor(led: View?, targetColor: Int) {
        led ?: return
        val previousColor = (led.getTag(R.id.led_previous_color) as? Int) ?: LED_COLOR_GRAY_OFF
        led.setTag(R.id.led_previous_color, targetColor)

        if (previousColor == targetColor) {
            led.background = createDrawable(targetColor, segmentsByView.getValue(led))
            return
        }

        ValueAnimator.ofArgb(previousColor, targetColor).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                segmentsByView[led]?.let { led.background = createDrawable(color, it) }
            }
        }.start()
    }

    private class ModifierLedCanvas(
        context: Context,
        private val contentHeightPx: Int
    ) : ViewGroup(context) {
        private val segments = mutableListOf<ModifierLedSegment>()
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            // Rounded indicators overlap the controls. Their transparent center
            // must not become a full-row long-press target above those controls.
            if (cornerRadiiPx != null && event.actionMasked == MotionEvent.ACTION_DOWN) {
                val edge = 3.1f * resources.displayMetrics.density
                if (event.x > edge && event.x < width - edge && event.y < height - edge) {
                    return false
                }
            }
            return super.dispatchTouchEvent(event)
        }

        var cornerRadiiPx: Pair<Int, Int>? = null
            set(value) {
                field = value
                requestLayout()
            }

        fun replaceSegments(
            newSegments: List<ModifierLedSegment>,
            createView: (ModifierLedSegment) -> View
        ) {
            removeAllViews()
            segments.clear()
            newSegments.forEach { segment ->
                segments.add(segment)
                addView(createView(segment))
            }
            requestLayout()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val measuredWidth = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
            val curveHeight = cornerRadiiPx?.let { maxOf(it.first, it.second) } ?: 0
            val desiredHeight = paddingTop + maxOf(contentHeightPx, curveHeight) + paddingBottom
            val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
            val contentWidth = (measuredWidth - paddingLeft - paddingRight).coerceAtLeast(0)
            val availableHeight = (measuredHeight - paddingTop - paddingBottom).coerceAtLeast(0)

            for (index in 0 until childCount) {
                val child = getChildAt(index)
                val segment = segments[index]
                if (cornerRadiiPx != null) {
                    child.measure(
                        MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
                    )
                    continue
                }
                child.measure(
                    MeasureSpec.makeMeasureSpec(
                        (contentWidth * segment.width).roundToInt().coerceAtLeast(1),
                        MeasureSpec.EXACTLY
                    ),
                    MeasureSpec.makeMeasureSpec(
                        (availableHeight * segment.height).roundToInt().coerceAtLeast(1),
                        MeasureSpec.EXACTLY
                    )
                )
            }
            setMeasuredDimension(measuredWidth, measuredHeight)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val contentWidth = (right - left - paddingLeft - paddingRight).coerceAtLeast(0)
            val availableHeight = (bottom - top - paddingTop - paddingBottom).coerceAtLeast(0)
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                val segment = segments[index]
                if (cornerRadiiPx != null) {
                    child.layout(0, 0, right - left, bottom - top)
                    continue
                }
                val childLeft = paddingLeft + (contentWidth * segment.x).roundToInt()
                val childTop = paddingTop + (availableHeight * segment.y).roundToInt()
                child.layout(
                    childLeft,
                    childTop,
                    childLeft + child.measuredWidth,
                    childTop + child.measuredHeight
                )
            }
        }

    }
}
