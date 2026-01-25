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
import android.graphics.drawable.StateListDrawable
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsActivity
import it.palsoftware.pastiera.inputmethod.suggestions.SuggestionButtonHandler
import it.palsoftware.pastiera.inputmethod.VariationButtonHandler
import it.palsoftware.pastiera.inputmethod.SubtypeCycler
import it.palsoftware.pastiera.inputmethod.ui.HamburgerMenuView
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonRegistry
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks
import android.view.inputmethod.InputMethodManager
import android.inputmethodservice.InputMethodService
import it.palsoftware.pastiera.core.suggestions.DictionaryRepository

/**
 * Renders the full-width suggestion bar with up to 3 items. Always occupies
 * a row (with placeholders) so the UI stays stable. Hidden when minimal UI
 * is forced or smart features are disabled by the caller.
 * Includes a hamburger menu button on the right that opens the hamburger menu.
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
    }

    private var container: LinearLayout? = null
    private var frameContainer: FrameLayout? = null
    private var hamburgerButton: ImageView? = null
    private var hamburgerMenuView: HamburgerMenuView? = null
    private var lastMinimalUiActive: Boolean? = null
    private var lastSlots: List<String?> = emptyList()
    private var assets: AssetManager? = null
    private var imeServiceClass: Class<*>? = null
    private var showHamburgerButton: Boolean = false // Control visibility of hamburger button
    private val suggestionButtons: MutableList<TextView> = mutableListOf()
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
            
            // Create hamburger menu button positioned absolutely on the right
            hamburgerButton = ImageView(context).apply {
                setImageResource(R.drawable.ic_menu_24)
                setColorFilter(Color.WHITE)
                scaleType = ImageView.ScaleType.CENTER
                background = null
                val buttonSize = dpToPx(32f)
                layoutParams = FrameLayout.LayoutParams(
                    buttonSize,
                    buttonSize
                ).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    marginEnd = 0
                }
                setPadding(0, 0, 0, 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    toggleHamburgerMenu()
                }
            }
            
            frameContainer?.addView(container)
            hamburgerButton?.let { frameContainer?.addView(it) }
            
            // Create hamburger menu view if buttonRegistry and callbacks are available
            frameContainer?.let { frame ->
                if (buttonRegistry != null && callbacksProvider != null) {
                    if (hamburgerMenuView == null) {
                        hamburgerMenuView = HamburgerMenuView(context, buttonRegistry)
                    }
                    hamburgerMenuView?.attachTo(frame)
                    lastMinimalUiActive?.let { hamburgerMenuView?.setMinimalUiActive(it) }
                }
            }
            // Ensure the outer layout (when attached to parent LinearLayout) keeps the target height
            frameContainer?.layoutParams = (frameContainer?.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, targetHeightPx)
        }
        return frameContainer!!
    }
    
    private fun toggleHamburgerMenu() {
        val menu = hamburgerMenuView ?: return
        val callbacks = callbacksProvider?.invoke() ?: return

        if (menu.isVisible()) {
            menu.hide()
        } else {
            callbacks.onHapticFeedback?.invoke()
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
    }
    
    /**
     * Updates the microphone button visual feedback based on audio level.
     */
    fun updateMicrophoneAudioLevel(rmsdB: Float) {
        hamburgerMenuView?.updateMicrophoneAudioLevel(rmsdB)
    }
    
    /**
     * Updates the clipboard badge count.
     */
    fun updateClipboardCount(count: Int) {
        hamburgerMenuView?.updateClipboardCount(count)
    }
    
    /**
     * Refreshes the language button text.
     */
    fun refreshLanguageText() {
        hamburgerMenuView?.refreshLanguageText()
    }

    fun setMinimalUiActive(isActive: Boolean) {
        lastMinimalUiActive = isActive
        showHamburgerButton = isActive
        hamburgerButton?.visibility = if (isActive) View.VISIBLE else View.GONE
        hamburgerMenuView?.setMinimalUiActive(isActive)
    }

    fun isHamburgerMenuVisible(): Boolean = hamburgerMenuView?.isVisible() == true

    fun hideHamburgerMenu() {
        hamburgerMenuView?.hide()
    }

    /**
     * Checks if a dictionary file exists for the current IME subtype.
     * Returns true if a dictionary is found (serialized format).
     */
    private fun hasDictionaryForCurrentSubtype(): Boolean {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val currentSubtype = imm?.currentInputMethodSubtype
            val locale = currentSubtype?.locale ?: return false
            
            // Extract language code from locale (e.g., "it_IT" -> "it", "en_US" -> "en")
            val langCode = locale.split("_")[0]
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
        onAddUserWord: ((String) -> Unit)?
    ) {
        val bar = container ?: return
        val frame = frameContainer ?: return
        
        // Hide bar if shouldShow is false or if no dictionary exists for current subtype
        val hasDictionary = hasDictionaryForCurrentSubtype()
        if (!shouldShow || !hasDictionary) {
            suggestionButtons.clear()
            frame.visibility = View.GONE
            bar.visibility = View.GONE
            bar.removeAllViews()
            hamburgerButton?.visibility = View.GONE
            lastSlots = emptyList()
            return
        }

        frame.visibility = View.VISIBLE
        // Show or hide hamburger button based on showHamburgerButton flag
        hamburgerButton?.visibility = if (showHamburgerButton) View.VISIBLE else View.GONE

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
        suggestionButtons.clear()
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

        val slotOrder = listOf(slots[0], slots[1], slots[2]) // left, center, right
        for ((index, suggestion) in slotOrder.withIndex()) {
            val slotIndex = suggestionButtons.size
            val weightLayoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            ).apply {
                // Apply margin only if not the last suggestion box
                if (index < slotOrder.size - 1) {
                    marginEnd = dpToPx(3f)
                }
            }
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
                background = buildSuggestionBackground()
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
                            flashSlot(slotIndex)
                            onAddUserWord?.invoke(suggestion)
                        }
                    } else {
                        val clickListener = SuggestionButtonHandler.createSuggestionClickListener(
                            suggestion,
                            inputConnection,
                            listener,
                            shouldDisableSuggestions
                        )
                        setOnClickListener { view ->
                            flashSlot(slotIndex)
                            clickListener.onClick(view)
                        }
                    }
                }
            }
            bar.addView(button)
            suggestionButtons.add(button)
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
        val normalDrawable = GradientDrawable().apply {
            setColor(DEFAULT_SUGGESTION_COLOR)
            cornerRadius = 0f
            alpha = 255 // placeholders look identical; they stay non-clickable
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(PRESSED_BLUE)
            cornerRadius = 0f
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
