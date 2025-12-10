package it.palsoftware.pastiera.inputmethod.suggestions.ui

import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import it.palsoftware.pastiera.SettingsActivity
import it.palsoftware.pastiera.inputmethod.suggestions.SuggestionButtonHandler
import it.palsoftware.pastiera.inputmethod.VariationButtonHandler
import it.palsoftware.pastiera.inputmethod.SubtypeCycler
import android.view.inputmethod.InputMethodManager
import android.inputmethodservice.InputMethodService

/**
 * Renders the full-width suggestion bar with up to 3 items. Always occupies
 * a row (with placeholders) so the UI stays stable. Hidden when minimal UI
 * is forced or smart features are disabled by the caller.
 * Includes a language button on the right that cycles through IME subtypes.
 */
class FullSuggestionsBar(private val context: Context) {

    private var container: LinearLayout? = null
    private var frameContainer: FrameLayout? = null
    private var languageButton: TextView? = null
    private var lastSlots: List<String?> = emptyList()
    private var assets: AssetManager? = null
    private var imeServiceClass: Class<*>? = null
    private var showLanguageButton: Boolean = false // Control visibility of language button
    private val targetHeightPx: Int by lazy {
        // Compact row sized around three suggestion pills
        dpToPx(36f)
    }

    /**
     * Sets the assets and IME service class needed for subtype cycling.
     */
    fun setSubtypeCyclingParams(assets: AssetManager, imeServiceClass: Class<*>) {
        this.assets = assets
        this.imeServiceClass = imeServiceClass
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
            }
            
            // Create language button positioned absolutely on the right
            languageButton = TextView(context).apply {
                text = getCurrentLanguageCode()
                gravity = Gravity.CENTER
                textSize = 12f
                includeFontPadding = false
                minHeight = 0
                maxLines = 1
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dpToPx(8f), dpToPx(2f), dpToPx(8f), dpToPx(2f))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(50, 50, 50))
                    cornerRadius = dpToPx(4f).toFloat()
                }
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    marginEnd = dpToPx(4f)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    cycleToNextSubtype()
                }
                setOnLongClickListener {
                    openSettings()
                    true
                }
            }
            
            frameContainer?.addView(container)
            frameContainer?.addView(languageButton)
            // Ensure the outer layout (when attached to parent LinearLayout) keeps the target height
            frameContainer?.layoutParams = (frameContainer?.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, targetHeightPx)
        }
        return frameContainer!!
    }
    
    private fun getCurrentLanguageCode(): String {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val currentSubtype = imm?.currentInputMethodSubtype
            val locale = currentSubtype?.locale ?: "en_US"
            // Extract country code from locale (e.g., "it_IT" -> "IT", "en_US" -> "US")
            val parts = locale.split("_")
            if (parts.size >= 2) {
                parts[1].uppercase()
            } else {
                // Fallback: use first two letters of language code
                parts[0].uppercase().take(2)
            }
        } catch (e: Exception) {
            "EN"
        }
    }
    
    private fun cycleToNextSubtype() {
        val assets = this.assets
        val imeServiceClass = this.imeServiceClass
        if (assets != null && imeServiceClass != null) {
            SubtypeCycler.cycleToNextSubtype(context, imeServiceClass, assets, showToast = true)
            // Update button text after cycling
            languageButton?.text = getCurrentLanguageCode()
        }
    }

    fun update(
        suggestions: List<String>,
        shouldShow: Boolean,
        inputConnection: android.view.inputmethod.InputConnection?,
        listener: VariationButtonHandler.OnVariationSelectedListener?,
        shouldDisableSuggestions: Boolean,
        addWordCandidate: String?,
        onAddUserWord: ((String) -> Unit)?
    ) {
        val bar = container ?: return
        val frame = frameContainer ?: return
        
        if (!shouldShow) {
            frame.visibility = View.GONE
            bar.visibility = View.GONE
            bar.removeAllViews()
            languageButton?.visibility = View.GONE
            lastSlots = emptyList()
            return
        }

        frame.visibility = View.VISIBLE
        // Show or hide language button based on showLanguageButton flag
        languageButton?.visibility = if (showLanguageButton) View.VISIBLE else View.GONE
        // Update language button text in case subtype changed externally
        languageButton?.text = getCurrentLanguageCode()

        val slots = buildSlots(suggestions)
        if (slots == lastSlots && bar.childCount > 0) {
            bar.visibility = View.VISIBLE
            return
        }

        renderSlots(bar, slots, inputConnection, listener, shouldDisableSuggestions, addWordCandidate, onAddUserWord)
        lastSlots = slots
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

    private fun renderSlots(
        bar: LinearLayout,
        slots: List<String?>,
        inputConnection: android.view.inputmethod.InputConnection?,
        listener: VariationButtonHandler.OnVariationSelectedListener?,
        shouldDisableSuggestions: Boolean,
        addWordCandidate: String?,
        onAddUserWord: ((String) -> Unit)?
    ) {
        bar.removeAllViews()
        bar.visibility = View.VISIBLE

        // Force bar and frame to the target height to avoid fallback to wrap_content.
        (bar.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            lp.height = targetHeightPx
            bar.layoutParams = lp
        } ?: run {
            bar.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                targetHeightPx
            )
        }
        (frameContainer?.layoutParams as? ViewGroup.LayoutParams)?.let { lp ->
            lp.height = targetHeightPx
            frameContainer?.layoutParams = lp
        }
        bar.minimumHeight = targetHeightPx
        frameContainer?.minimumHeight = targetHeightPx

        val padV = dpToPx(3f) // tighter vertical padding to further reduce height
        val padH = dpToPx(12f)
        val weightLayoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT,
            1f
        ).apply {
            marginEnd = dpToPx(3f)
        }

        val slotOrder = listOf(slots[0], slots[1], slots[2]) // left, center, right
        for (suggestion in slotOrder) {
            val button = TextView(context).apply {
                text = (suggestion ?: "")
                gravity = Gravity.CENTER
                textSize = 14f // keep readable while shrinking the bar
                includeFontPadding = false
                minHeight = 0
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.NORMAL)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
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
                    if (addWordCandidate != null && suggestion.equals(addWordCandidate, ignoreCase = true)) {
                        val addDrawable = androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.ic_input_add)?.mutate()
                        addDrawable?.setTint(Color.YELLOW)
                        addDrawable?.setBounds(0, 0, dpToPx(18f), dpToPx(18f))
                        setCompoundDrawables(null, null, addDrawable, null)
                        compoundDrawablePadding = dpToPx(6f)
                        setOnClickListener {
                            onAddUserWord?.invoke(suggestion)
                        }
                    } else {
                        setOnClickListener(
                            SuggestionButtonHandler.createSuggestionClickListener(
                                suggestion,
                                inputConnection,
                                listener,
                                shouldDisableSuggestions
                            )
                        )
                    }
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
