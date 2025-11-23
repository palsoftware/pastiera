package it.palsoftware.pastiera.inputmethod.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.StatusBarController

/**
 * Compact controller around the LED strip at the bottom of the IME status bar.
 */
class LedStatusView(
    private val context: Context
) {
    companion object {
        private val LED_COLOR_GRAY_OFF = Color.argb(26, 255, 255, 255)
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
    private val ledGap: Int by lazy {
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            1.5f,
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

    private var container: LinearLayout? = null
    private var shiftLed: View? = null
    private var symLed: View? = null
    private var ctrlLed: View? = null
    private var altLed: View? = null

    fun ensureView(): LinearLayout {
        container?.let { return it }

        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.BOTTOM
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        shiftLed = createLedView(LED_COLOR_GRAY_OFF)
        symLed = createLedView(LED_COLOR_GRAY_OFF)
        val unused1 = createLedView(LED_COLOR_GRAY_OFF).apply { visibility = View.INVISIBLE }
        val unused2 = createLedView(LED_COLOR_GRAY_OFF).apply { visibility = View.INVISIBLE }
        ctrlLed = createLedView(LED_COLOR_GRAY_OFF)
        altLed = createLedView(LED_COLOR_GRAY_OFF)

        container?.apply {
            addView(shiftLed, LinearLayout.LayoutParams(0, ledHeight, 1f).apply { marginEnd = ledGap })
            addView(symLed, LinearLayout.LayoutParams(0, ledHeight, 1f).apply { marginEnd = ledGap })
            addView(unused1, LinearLayout.LayoutParams(0, ledHeight, 1f).apply { marginEnd = ledGap })
            addView(unused2, LinearLayout.LayoutParams(0, ledHeight, 1f).apply { marginEnd = ledGap })
            addView(ctrlLed, LinearLayout.LayoutParams(0, ledHeight, 1f).apply { marginEnd = ledGap })
            addView(altLed, LinearLayout.LayoutParams(0, ledHeight, 1f))
        }

        return container!!
    }

    fun getView(): LinearLayout? = container

    fun update(snapshot: StatusBarController.StatusSnapshot) {
        val shiftLocked = snapshot.capsLockEnabled
        val shiftActive = (snapshot.shiftPhysicallyPressed || snapshot.shiftOneShot) && !shiftLocked
        updateLed(shiftLed, shiftLocked, shiftActive)

        val ctrlLocked = snapshot.ctrlLatchActive
        val ctrlActive = (snapshot.ctrlPhysicallyPressed || snapshot.ctrlOneShot) && !ctrlLocked
        updateLed(ctrlLed, ctrlLocked, ctrlActive)

        val altLocked = snapshot.altLatchActive
        val altActive = (snapshot.altPhysicallyPressed || snapshot.altOneShot) && !altLocked
        updateLed(altLed, altLocked, altActive)

        updateSymLed(symLed, snapshot.symPage)
    }

    private fun createLedView(initialColor: Int): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ledHeight, 1f)
            background = createDrawable(initialColor)
            setTag(R.id.led_previous_color, initialColor)
        }
    }

    private fun createDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = this@LedStatusView.cornerRadius
        }
    }

    private fun updateLed(led: View?, isLocked: Boolean, isActive: Boolean = false) {
        val targetColor = when {
            isLocked -> LED_COLOR_RED_LOCKED
            isActive -> LED_COLOR_BLUE_ACTIVE
            else -> LED_COLOR_GRAY_OFF
        }
        animateLedColor(led, targetColor)
    }

    private fun updateSymLed(led: View?, symPage: Int) {
        val targetColor = when (symPage) {
            1 -> LED_COLOR_BLUE_ACTIVE
            2 -> LED_COLOR_RED_LOCKED
            else -> LED_COLOR_GRAY_OFF
        }
        animateLedColor(led, targetColor)
    }

    private fun animateLedColor(led: View?, targetColor: Int) {
        led ?: return
        val previousColor = (led.getTag(R.id.led_previous_color) as? Int) ?: LED_COLOR_GRAY_OFF
        led.setTag(R.id.led_previous_color, targetColor)

        if (previousColor == targetColor) {
            led.background = createDrawable(targetColor)
            return
        }

        ValueAnimator.ofArgb(previousColor, targetColor).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val color = animator.animatedValue as Int
                led.background = createDrawable(color)
            }
        }.start()
    }
}

