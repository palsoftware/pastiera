package it.palsoftware.pastiera.inputmethod.suggestions.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import it.palsoftware.pastiera.inputmethod.suggestions.SuggestionButtonHandler
import it.palsoftware.pastiera.inputmethod.VariationButtonHandler

/**
 * Renders the full-width suggestion bar with up to 3 items. Always occupies
 * a row (with placeholders) so the UI stays stable. Hidden when minimal UI
 * is forced or smart features are disabled by the caller.
 */
class FullSuggestionsBar(private val context: Context) {

    private var container: LinearLayout? = null
    private var lastSlots: List<String?> = emptyList()

    fun ensureView(): LinearLayout {
        if (container == null) {
            container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                visibility = View.GONE
            }
        }
        return container!!
    }

    fun update(
        suggestions: List<String>,
        shouldShow: Boolean,
        inputConnection: android.view.inputmethod.InputConnection?,
        listener: VariationButtonHandler.OnVariationSelectedListener?
    ) {
        val bar = container ?: return
        if (!shouldShow) {
            bar.visibility = View.GONE
            bar.removeAllViews()
            lastSlots = emptyList()
            return
        }

        val slots = buildSlots(suggestions)
        if (slots == lastSlots && bar.childCount > 0) {
            bar.visibility = View.VISIBLE
            return
        }

        renderSlots(bar, slots, inputConnection, listener)
        lastSlots = slots
    }

    private fun renderSlots(
        bar: LinearLayout,
        slots: List<String?>,
        inputConnection: android.view.inputmethod.InputConnection?,
        listener: VariationButtonHandler.OnVariationSelectedListener?
    ) {
        bar.removeAllViews()
        bar.visibility = View.VISIBLE

        val padV = dpToPx(10f)
        val padH = dpToPx(12f)
        val weightLayoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            marginEnd = dpToPx(3f)
        }

        val slotOrder = listOf(slots[0], slots[1], slots[2]) // left, center, right
        for (suggestion in slotOrder) {
            val button = TextView(context).apply {
                text = (suggestion ?: "")
                gravity = Gravity.CENTER
                textSize = 18f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(padH, padV, padH, padV)
                background = GradientDrawable().apply {
                    setColor(Color.rgb(17, 17, 17))
                    cornerRadius = dpToPx(6f).toFloat()
                    alpha = if (suggestion == null) 90 else 255
                }
                layoutParams = weightLayoutParams
                isClickable = suggestion != null
                isFocusable = suggestion != null
                if (suggestion != null) {
                    setOnClickListener(
                        SuggestionButtonHandler.createSuggestionClickListener(
                            suggestion,
                            inputConnection,
                            listener
                        )
                    )
                }
            }
            bar.addView(button)
        }
    }

    private fun buildSlots(suggestions: List<String>): List<String?> {
        val s0 = suggestions.getOrNull(0)
        val s1 = suggestions.getOrNull(1)
        val s2 = suggestions.getOrNull(2)
        return listOf(
            // left
            if (suggestions.size >= 3) s2 else null,
            // center
            s0,
            // right
            if (suggestions.size >= 2) s1 else null
        )
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
