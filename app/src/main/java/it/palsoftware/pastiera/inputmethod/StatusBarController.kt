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
import android.widget.PopupMenu
import android.widget.TextView
import android.util.Log
import android.util.TypedValue
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.MainActivity
import it.palsoftware.pastiera.SymCustomizationActivity
import it.palsoftware.pastiera.SettingsManager
import kotlin.math.max
import android.view.MotionEvent
import android.view.KeyEvent
import kotlin.math.abs
import it.palsoftware.pastiera.inputmethod.ui.LedStatusView
import it.palsoftware.pastiera.inputmethod.ui.VariationBarView
import it.palsoftware.pastiera.inputmethod.suggestions.ui.FullSuggestionsBar
import android.content.res.AssetManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Manages the status bar shown by the IME, handling view creation
 * and updating text/style based on modifier states.
 */
class StatusBarController(
    private val context: Context,
    private val mode: Mode = Mode.FULL,
    private val clipboardHistoryManager: it.palsoftware.pastiera.clipboard.ClipboardHistoryManager? = null,
    private val assets: AssetManager? = null,
    private val imeServiceClass: Class<*>? = null
) {
    enum class Mode {
        FULL,
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
    }
    
    /**
     * Updates the microphone button visual feedback based on audio level.
     * @param rmsdB The RMS audio level in decibels (typically -10 to 0)
     */
    fun updateMicrophoneAudioLevel(rmsdB: Float) {
        variationBarView?.updateMicrophoneAudioLevel(rmsdB)
    }
    
    /**
     * Shows or hides the speech recognition hint message.
     * When showing, replaces the swipe hint with speech recognition message.
     */
    fun showSpeechRecognitionHint(show: Boolean) {
        variationBarView?.showSpeechRecognitionHint(show)
    }

    companion object {
        private const val TAG = "StatusBarController"
        private val DEFAULT_BACKGROUND = Color.parseColor("#000000")
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
        // Legacy flag for backward compatibility
        val shouldDisableSmartFeatures: Boolean = false
    ) {
        val navModeActive: Boolean
            get() = ctrlLatchActive && ctrlLatchFromNavMode
    }

    private var statusBarLayout: LinearLayout? = null
    private var modifiersContainer: LinearLayout? = null
    private var emojiMapTextView: TextView? = null
    private var emojiKeyboardContainer: LinearLayout? = null
    private var emojiKeyButtons: MutableList<View> = mutableListOf()
    private var lastSymPageRendered: Int = 0
    private var lastSymMappingsRendered: Map<Int, String>? = null
    private var lastInputConnectionUsed: android.view.inputmethod.InputConnection? = null
    private var wasSymActive: Boolean = false
    private var symShown: Boolean = false
    private var lastSymHeight: Int = 0
    private val defaultSymHeightPx: Int
        get() = dpToPx(600f) // fallback when nothing measured yet
    private val ledStatusView = LedStatusView(context)
    private val variationBarView: VariationBarView? = if (mode == Mode.FULL) VariationBarView(context, assets, imeServiceClass) else null
    private var variationsWrapper: View? = null
    private var forceMinimalUi: Boolean = false
    private var fullSuggestionsBar: FullSuggestionsBar? = null
    private var baseBottomPadding: Int = 0

    fun setForceMinimalUi(force: Boolean) {
        if (mode != Mode.FULL) {
            return
        }
        if (forceMinimalUi == force) {
            return
        }
        forceMinimalUi = force
        if (force) {
            variationBarView?.hideImmediate()
        }
    }

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
            statusBarLayout?.let { layout ->
                baseBottomPadding = layout.paddingBottom
                ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
                    // Preserve space for the system IME switcher / nav bar while keeping zero extra gap otherwise
                    val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                    val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                    val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
                    val bottomInset = maxOf(navInsets.bottom, imeInsets.bottom, cutout.bottom)
                    view.updatePadding(bottom = baseBottomPadding + bottomInset)
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
            val ledStrip = ledStatusView.ensureView()
            
            statusBarLayout?.apply {
                // Full-width suggestions bar above the rest
                fullSuggestionsBar = FullSuggestionsBar(context)
                // Set subtype cycling parameters if available
                if (assets != null && imeServiceClass != null) {
                    fullSuggestionsBar?.setSubtypeCyclingParams(assets, imeServiceClass)
                }
                addView(fullSuggestionsBar?.ensureView())
                addView(modifiersContainer)
                variationsWrapper?.let { addView(it) }
                addView(emojiKeyboardContainer) // Griglia emoji prima dei LED
                addView(ledStrip) // LED sempre in fondo
            }
            statusBarLayout?.let { ViewCompat.requestApplyInsets(it) }
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
    
    /**
     * Updates the clipboard history view inline in the keyboard container.
     */
    private fun updateClipboardView(inputConnection: android.view.inputmethod.InputConnection? = null) {
        val container = emojiKeyboardContainer ?: return
        container.removeAllViews()
        emojiKeyButtons.clear()

        clipboardHistoryManager?.prepareClipboardHistory()

        val count = clipboardHistoryManager?.getHistorySize() ?: 0

        if (count == 0) {
            // Show empty state
            val padding = dpToPx(32f)
            val emptyText = TextView(context).apply {
                text = context.getString(R.string.clipboard_empty_state)
                textSize = 14f
                setTextColor(Color.argb(128, 255, 255, 255))
                gravity = Gravity.CENTER
                setPadding(padding, padding, padding, padding)
            }
            container.addView(emptyText)
            lastSymPageRendered = 3
            return
        }

        // Create header with "Clipboard History" title and Clear All button
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val padding = dpToPx(12f)
            setPadding(padding, dpToPx(8f), padding, dpToPx(4f))
        }

        val titleText = TextView(context).apply {
            text = context.getString(R.string.clipboard_history_title)
            textSize = 12f
            setTextColor(Color.argb(180, 255, 255, 255))
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val clearButton = TextView(context).apply {
            text = context.getString(R.string.clipboard_clear_all)
            textSize = 12f
            setTextColor(Color.parseColor("#FF6B6B"))
            isClickable = true
            isFocusable = true
            val padding = dpToPx(8f)
            setPadding(padding, padding / 2, padding, padding / 2)
            setOnClickListener {
                clipboardHistoryManager?.clearHistory()
                updateClipboardView(inputConnection)
            }
        }

        header.addView(titleText)
        header.addView(clearButton)
        container.addView(header)

        // Create scrollable grid container for clipboard entries
        val scrollView = android.widget.ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val gridContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Add clipboard entries to grid (2 columns)
        val columnCount = 2
        var currentRow: LinearLayout? = null

        for (i in 0 until count) {
            if (i % columnCount == 0) {
                // Create new row
                currentRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                gridContainer.addView(currentRow)
            }

            val entry = clipboardHistoryManager?.getHistoryEntry(i) ?: continue
            val entryView = createClipboardEntryView(entry, inputConnection, isInGrid = true)
            currentRow?.addView(entryView)
        }

        // Add empty cell if last row has only one item
        if (count % columnCount == 1 && currentRow != null) {
            val spacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            currentRow.addView(spacer)
        }

        scrollView.addView(gridContainer)
        container.addView(scrollView)

        lastSymPageRendered = 3
    }

    private fun createClipboardEntryView(
        entry: it.palsoftware.pastiera.clipboard.ClipboardHistoryEntry,
        inputConnection: android.view.inputmethod.InputConnection?,
        isInGrid: Boolean = false
    ): View {
        val marginHorizontal = dpToPx(4f)
        val marginVertical = dpToPx(4f)
        val paddingHorizontal = dpToPx(12f)
        val paddingVertical = dpToPx(12f)
        // Slightly shorter cards to fit more items vertically
        val fixedHeight = dpToPx(60f)

        // Use FrameLayout so pin can overlay the text without pushing it down
        val entryContainer = FrameLayout(context).apply {
            background = createRoundedBackground()
            setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
            layoutParams = LinearLayout.LayoutParams(
                if (isInGrid) 0 else ViewGroup.LayoutParams.MATCH_PARENT,
                fixedHeight,
                if (isInGrid) 1f else 0f
            ).apply {
                setMargins(marginHorizontal, marginVertical, marginHorizontal, marginVertical)
            }
        }

        val textView = TextView(context).apply {
            text = entry.text
            textSize = 14f
            setTextColor(Color.WHITE)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        entryContainer.addView(textView)

        // Pin indicator at top-right (overlays the text)
        if (entry.isPinned) {
            val pinIndicator = TextView(context).apply {
                text = "📌"
                textSize = 14f
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                )
            }
            entryContainer.addView(pinIndicator)
        }

        // Set click and long-click on the entire container
        entryContainer.apply {
            isClickable = true
            isFocusable = true
            isLongClickable = true

            // Click to paste
            setOnClickListener {
                inputConnection?.commitText(entry.text, 1)
            }

            // Long-press to show context menu
            setOnLongClickListener { view ->
                showClipboardContextMenu(view, entry, inputConnection)
                true
            }
        }

        return entryContainer
    }

    private fun showClipboardContextMenu(
        view: View,
        entry: it.palsoftware.pastiera.clipboard.ClipboardHistoryEntry,
        inputConnection: android.view.inputmethod.InputConnection?
    ) {
        val popup = android.widget.PopupMenu(context, view)

        // Add menu items
        val pinText = context.getString(R.string.clipboard_pin)
        val unpinText = context.getString(R.string.clipboard_unpin)
        val deleteText = context.getString(R.string.clipboard_delete)

        if (entry.isPinned) {
            popup.menu.add(unpinText)
        } else {
            popup.menu.add(pinText)
        }
        popup.menu.add(deleteText)

        popup.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                pinText, unpinText -> {
                    clipboardHistoryManager?.toggleClipPinned(entry.id)
                    updateClipboardView(inputConnection)
                    true
                }
                deleteText -> {
                    val index = (0 until (clipboardHistoryManager?.getHistorySize() ?: 0)).find { idx ->
                        clipboardHistoryManager?.getHistoryEntry(idx)?.id == entry.id
                    }
                    index?.let {
                        clipboardHistoryManager?.removeEntry(it, force = true)
                        updateClipboardView(inputConnection)
                    }
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    private fun createRoundedBackground(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(Color.argb(40, 255, 255, 255))
            cornerRadius = dpToPx(6f).toFloat()
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
        
        val keySpacing = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        
        // Calcola la larghezza fissa dei tasti basata sulla prima riga (10 caselle)
        val maxKeysInRow = 10 // Prima riga ha 10 caselle
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
            
            // Per la terza riga, aggiungi placeholder trasparente a sinistra
            if (rowIndex == 2) {
                val leftPlaceholder = createPlaceholderButton(keyHeight)
                rowLayout.addView(leftPlaceholder, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    marginEnd = keySpacing
                })
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
                    
                    // Usa solo OnTouchListener per feedback + click (più efficiente)
                    val originalBackground = keyButton.background as? GradientDrawable
                    if (originalBackground != null) {
                        val normalColor = Color.argb(40, 255, 255, 255)
                        val pressedColor = Color.argb(80, 255, 255, 255)
                        
                        keyButton.setOnTouchListener { view, motionEvent ->
                            when (motionEvent.action) {
                                android.view.MotionEvent.ACTION_DOWN -> {
                                    originalBackground.setColor(pressedColor)
                                    view.postInvalidate()
                                    true // Consuma per feedback immediato
                                }
                                android.view.MotionEvent.ACTION_UP -> {
                                    originalBackground.setColor(normalColor)
                                    view.postInvalidate()
                                    // Esegui commitText direttamente qui (più veloce)
                                    inputConnection.commitText(content, 1)
                                    true
                                }
                                android.view.MotionEvent.ACTION_CANCEL -> {
                                    originalBackground.setColor(normalColor)
                                    view.postInvalidate()
                                    true
                                }
                                else -> false
                            }
                        }
                    } else {
                        // Fallback: solo click listener se non c'è background
                        keyButton.setOnClickListener {
                            inputConnection.commitText(content, 1)
                        }
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
                val rightPlaceholder = createPlaceholderWithPencilButton(keyHeight)
                rowLayout.addView(rightPlaceholder, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
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
     * Crea un placeholder con icona matita per aprire la schermata di personalizzazione SYM.
     */
    private fun createPlaceholderWithPencilButton(height: Int): View {
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
            20f, // Aumentata ulteriormente per maggiore visibilità
            context.resources.displayMetrics
        ).toInt()
        
        val button = ImageView(context).apply {
            background = null
            setImageResource(R.drawable.ic_edit_24)
            setColorFilter(Color.WHITE) // Bianco
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
        
        placeholder.addView(button)
        return placeholder
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
        
        // Background del tasto con angoli leggermente arrotondati
        val cornerRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            6f, // Angoli leggermente arrotondati
            context.resources.displayMetrics
        )
        val drawable = GradientDrawable().apply {
            setColor(Color.argb(40, 255, 255, 255)) // Bianco semi-trasparente
            setCornerRadius(cornerRadius)
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
    
    /**
     * Anima l'apparizione della griglia emoji solo con slide up (nessun fade).
     * @param backgroundView Il view dello sfondo da impostare a opaco immediatamente
     */
    private fun animateEmojiKeyboardIn(view: View, backgroundView: View? = null) {
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
                bgView.background = ColorDrawable(DEFAULT_BACKGROUND)
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
        variationBarView?.onVariationSelectedListener = onVariationSelectedListener
        variationBarView?.onCursorMovedListener = onCursorMovedListener
        variationBarView?.updateInputConnection(inputConnection)
        variationBarView?.setSymModeActive(snapshot.symPage > 0 || snapshot.clipboardOverlay)
        variationBarView?.updateLanguageButtonText()
        
        val layout = ensureLayoutCreated(emojiMapText) ?: return
        val modifiersContainerView = modifiersContainer ?: return
        val emojiView = emojiMapTextView ?: return
        val emojiKeyboardView = emojiKeyboardContainer ?: return
        emojiView.visibility = View.GONE
        
        if (snapshot.navModeActive) {
            layout.visibility = View.GONE
            return
        }
        layout.visibility = View.VISIBLE
        
        if (layout.background !is ColorDrawable) {
            layout.background = ColorDrawable(DEFAULT_BACKGROUND)
        } else if (snapshot.symPage == 0) {
            (layout.background as ColorDrawable).alpha = 255
        }
        
        modifiersContainerView.visibility = View.GONE
        ledStatusView.update(snapshot)
        val variationsBar = if (!forceMinimalUi) variationBarView else null
        val variationsWrapperView = if (!forceMinimalUi) variationsWrapper else null
        val experimentalEnabled = SettingsManager.isExperimentalSuggestionsEnabled(context)
        val suggestionsEnabledSetting = SettingsManager.getSuggestionsEnabled(context)
        // Show full suggestions bar in CANDIDATES_ONLY mode or when not in minimal UI mode
        val showFullBar = (mode == Mode.CANDIDATES_ONLY || !forceMinimalUi) &&
            experimentalEnabled &&
            suggestionsEnabledSetting &&
            !snapshot.shouldDisableSuggestions &&
            snapshot.symPage == 0 &&
            !snapshot.clipboardOverlay
        fullSuggestionsBar?.update(
            snapshot.suggestions,
            showFullBar,
            inputConnection,
            onVariationSelectedListener,
            snapshot.shouldDisableSuggestions,
            snapshot.addWordCandidate,
            onAddUserWord
        )
        
        if (snapshot.clipboardOverlay) {
            // Show clipboard as dedicated overlay (not part of SYM pages)
            updateClipboardView(inputConnection)
            variationsBar?.resetVariationsState()

            // Pin background and hide variations while showing clipboard grid
            if (layout.background !is ColorDrawable) {
                layout.background = ColorDrawable(DEFAULT_BACKGROUND)
            }
            (layout.background as? ColorDrawable)?.alpha = 255
            variationsWrapperView?.apply {
                visibility = View.INVISIBLE
                isEnabled = false
                isClickable = false
            }
            variationsBar?.hideImmediate()

            val measured = ensureEmojiKeyboardMeasuredHeight(emojiKeyboardView, layout, forceReMeasure = true)
            // Prefer cached height; otherwise measured; fallback to default only if zero.
            val targetHeight = when {
                lastSymHeight > 0 -> lastSymHeight
                measured > 0 -> measured
                else -> defaultSymHeightPx
            }
            lastSymHeight = targetHeight
            emojiKeyboardView.setBackgroundColor(DEFAULT_BACKGROUND)
            emojiKeyboardView.visibility = View.VISIBLE
            emojiKeyboardView.layoutParams = (emojiKeyboardView.layoutParams ?: LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                targetHeight
            )).apply { height = targetHeight }
            if (!symShown && !wasSymActive) {
                emojiKeyboardView.alpha = 1f
                emojiKeyboardView.translationY = targetHeight.toFloat()
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

        if (snapshot.symPage > 0) {
            // Handle page 3 (clipboard) vs pages 1-2 (emoji/symbols)
            if (snapshot.symPage == 3) {
                // Show clipboard history inline (similar to emoji grid)
                updateClipboardView(inputConnection)
            } else if (symMappings != null) {
                updateEmojiKeyboard(symMappings, snapshot.symPage, inputConnection)
            }
            variationsBar?.resetVariationsState()

            // Pin background to opaque IME color and hide variations so SYM animates on a solid canvas.
            if (layout.background !is ColorDrawable) {
                layout.background = ColorDrawable(DEFAULT_BACKGROUND)
            }
            (layout.background as? ColorDrawable)?.alpha = 255
            variationsWrapperView?.apply {
                visibility = View.INVISIBLE // keep space to avoid shrink/flash
                isEnabled = false
                isClickable = false
            }
            variationsBar?.hideImmediate()

            val measured = ensureEmojiKeyboardMeasuredHeight(emojiKeyboardView, layout, forceReMeasure = true)
            val symHeight = if (measured > 0) measured else defaultSymHeightPx
            lastSymHeight = symHeight
            emojiKeyboardView.setBackgroundColor(DEFAULT_BACKGROUND)
            emojiKeyboardView.visibility = View.VISIBLE
            emojiKeyboardView.layoutParams = (emojiKeyboardView.layoutParams ?: LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                symHeight
            )).apply { height = symHeight }
            if (!symShown && !wasSymActive) {
                emojiKeyboardView.alpha = 1f // keep black visible immediately
                emojiKeyboardView.translationY = symHeight.toFloat()
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
            }
            symShown = false
            wasSymActive = false
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
            symShown = false
            wasSymActive = false
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

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
