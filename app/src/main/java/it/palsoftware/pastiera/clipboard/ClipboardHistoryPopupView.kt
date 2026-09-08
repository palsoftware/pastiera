package it.palsoftware.pastiera.clipboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import it.palsoftware.pastiera.R

/**
 * Popup window that displays clipboard history.
 * Triggered by SHIFT+CTRL+V keyboard shortcut.
 */
class ClipboardHistoryPopupView(
    private val context: Context,
    private val clipboardHistoryManager: ClipboardHistoryManager
) {

    private val popupWindow: PopupWindow
    private val contentView: LinearLayout
    private val entriesContainer: LinearLayout
    private val clearButton: Button
    private val accessStateListener: (Boolean) -> Unit = {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            refreshEntries()
        } else {
            contentView.post { refreshEntries() }
        }
    }

    private var onItemClickListener: ((ClipboardHistoryEntry) -> Unit)? = null
    private var onPinClickListener: ((ClipboardHistoryEntry) -> Unit)? = null
    private var onDeleteClickListener: ((ClipboardHistoryEntry) -> Unit)? = null
    private var onClearAllClickListener: (() -> Unit)? = null

    init {
        // Create the main content container
        contentView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E")) // Dark background
            val padding = dpToPx(16f)
            setPadding(padding, padding, padding, padding)
        }

        // Header with title and clear button
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleText = TextView(context).apply {
            text = context.getString(R.string.clipboard_history_title)
            textSize = 18f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        clearButton = Button(context).apply {
            text = context.getString(R.string.clipboard_clear_all)
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#333333"))
            val buttonPadding = dpToPx(8f)
            setPadding(buttonPadding, buttonPadding / 2, buttonPadding, buttonPadding / 2)
            setOnClickListener {
                if (clipboardHistoryManager.isHistoryAccessible()) {
                    onClearAllClickListener?.invoke()
                }
                refreshEntries()
            }
        }

        header.addView(titleText)
        header.addView(clearButton)
        contentView.addView(header)

        // Entries container (scrollable)
        entriesContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollView = ScrollView(context).apply {
            addView(entriesContainer)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(300f) // Max height
            )
        }

        contentView.addView(scrollView)

        // Create popup window
        popupWindow = PopupWindow(
            contentView,
            dpToPx(400f), // Width
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true // Focusable
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = 8f
            setOnDismissListener {
                clipboardHistoryManager.removeAccessStateListener(accessStateListener)
            }
        }

        refreshEntries()
    }

    fun setOnItemClickListener(listener: (ClipboardHistoryEntry) -> Unit) {
        onItemClickListener = listener
    }

    fun setOnPinClickListener(listener: (ClipboardHistoryEntry) -> Unit) {
        onPinClickListener = listener
    }

    fun setOnDeleteClickListener(listener: (ClipboardHistoryEntry) -> Unit) {
        onDeleteClickListener = listener
    }

    fun setOnClearAllClickListener(listener: () -> Unit) {
        onClearAllClickListener = listener
    }

    private fun refreshEntries() {
        entriesContainer.removeAllViews()

        if (!clipboardHistoryManager.isHistoryAccessible()) {
            clearButton.visibility = View.GONE
            addStateText(R.string.clipboard_locked_state)
            return
        }

        clearButton.visibility = View.VISIBLE

        val count = clipboardHistoryManager.getHistorySize()
        if (count == 0) {
            addStateText(R.string.clipboard_empty_state)
            return
        }

        for (i in 0 until count) {
            val entry = clipboardHistoryManager.getHistoryEntry(i) ?: continue
            val entryView = createEntryView(entry)
            entriesContainer.addView(entryView)
        }
    }

    private fun addStateText(stringRes: Int) {
        val stateText = TextView(context).apply {
            text = context.getString(stringRes)
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            val padding = dpToPx(16f)
            setPadding(padding, padding, padding, padding)
        }
        entriesContainer.addView(stateText)
    }

    private fun createEntryView(entry: ClipboardHistoryEntry): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#2A2A2A"))
            val margin = dpToPx(4f)
            val padding = dpToPx(12f)
            setPadding(padding, padding, padding, padding)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = margin
            }
            isClickable = true
            isFocusable = true
            contentDescription = entry.text
            setOnClickListener {
                onItemClickListener?.invoke(entry)
            }
        }

        // Text content (truncated)
        val textView = TextView(context).apply {
            text = entry.text.take(100) + if (entry.text.length > 100) "..." else ""
            textSize = 14f
            setTextColor(Color.WHITE)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        // Pin button
        val pinButton = ImageButton(context).apply {
            setImageResource(android.R.drawable.btn_star_big_on)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(if (entry.isPinned) Color.YELLOW else Color.GRAY)
            contentDescription = context.getString(if (entry.isPinned) R.string.clipboard_unpin else R.string.clipboard_pin)
            val size = dpToPx(32f)
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener {
                onPinClickListener?.invoke(entry)
                refreshEntries()
            }
        }

        // Delete button
        val deleteButton = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.RED)
            contentDescription = context.getString(R.string.clipboard_delete)
            val size = dpToPx(32f)
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener {
                onDeleteClickListener?.invoke(entry)
                refreshEntries()
            }
        }

        container.addView(textView)
        container.addView(pinButton)
        if (!entry.isPinned) {
            container.addView(deleteButton)
        }

        return container
    }

    fun show() {
        clipboardHistoryManager.addAccessStateListener(accessStateListener)
        refreshEntries()
        // Show at top of screen or above keyboard
        popupWindow.showAtLocation(
            contentView,
            Gravity.CENTER,
            0,
            0
        )
    }

    fun dismiss() {
        clipboardHistoryManager.removeAccessStateListener(accessStateListener)
        popupWindow.dismiss()
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
