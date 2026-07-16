package it.palsoftware.pastiera.inputmethod.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView

class EmojiShortcodePopup(
    private val context: Context,
    private val onEmojiSelected: (String, String) -> Unit,
    private val onDismiss: () -> Unit
) {
    private val contentView = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor("#1E1E1E"))
        val padding = dpToPx(12f)
        setPadding(padding, padding, padding, padding)
    }
    private val entriesContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    private val popupWindow = PopupWindow(
        contentView,
        dpToPx(360f),
        ViewGroup.LayoutParams.WRAP_CONTENT,
        false
    ).apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        isOutsideTouchable = false
        isTouchable = true
        isFocusable = false
        elevation = 8f
        setOnDismissListener { onDismiss() }
    }

    private var selectedIndex = 0
    private var suggestions: List<Pair<String, String>> = emptyList()
    private val itemViews = mutableListOf<View>()

    init {
        val title = TextView(context).apply {
            text = "Shortcodes"
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        val scrollView = ScrollView(context).apply {
            addView(entriesContainer)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(260f)
            )
        }
        contentView.addView(title)
        contentView.addView(scrollView)
    }

    fun show(anchorView: View, suggestions: List<Pair<String, String>>) {
        if (suggestions.isEmpty()) {
            dismiss()
            return
        }
        this.suggestions = suggestions
        selectedIndex = 0
        refreshEntries()
        if (!popupWindow.isShowing) {
            popupWindow.showAtLocation(anchorView, Gravity.BOTTOM or Gravity.START, dpToPx(8f), dpToPx(96f))
        }
    }

    fun updateSuggestions(newSuggestions: List<Pair<String, String>>) {
        if (newSuggestions.isEmpty()) {
            dismiss()
            return
        }
        suggestions = newSuggestions
        selectedIndex = selectedIndex.coerceAtMost(newSuggestions.lastIndex)
        refreshEntries()
    }

    fun dismiss() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    fun isShowing(): Boolean = popupWindow.isShowing

    fun handlePhysicalKey(keyCode: Int, event: KeyEvent?): Boolean {
        if (suggestions.isEmpty()) return false
        when (keyCode) {
            KeyEvent.KEYCODE_ESCAPE -> {
                dismiss()
                return true
            }
            KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                val (emoji, shortcode) = suggestions[selectedIndex]
                onEmojiSelected(emoji, shortcode)
                dismiss()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (selectedIndex > 0) {
                    selectedIndex--
                    updateSelection()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (selectedIndex < suggestions.lastIndex) {
                    selectedIndex++
                    updateSelection()
                }
                return true
            }
        }

        if (event != null && event.isAltPressed) {
            val unicodeChar = event.getUnicodeChar(KeyEvent.META_ALT_ON)
            if (unicodeChar > 0) {
                val produced = unicodeChar.toChar()
                if (produced in '0'..'9') {
                    val index = if (produced == '0') 9 else produced - '1'
                    if (index <= suggestions.lastIndex) {
                        val (emoji, shortcode) = suggestions[index]
                        onEmojiSelected(emoji, shortcode)
                        dismiss()
                    }
                    return true
                }
            }
        }

        return false
    }

    private fun refreshEntries() {
        entriesContainer.removeAllViews()
        itemViews.clear()
        suggestions.forEachIndexed { index, (emoji, shortcode) ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padding = dpToPx(8f)
                setPadding(padding, padding, padding, padding)
                setOnClickListener {
                    onEmojiSelected(emoji, shortcode)
                    dismiss()
                }
            }
            val indexView = TextView(context).apply {
                text = if (index == 9) "0" else "${index + 1}"
                setTextColor(Color.LTGRAY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                layoutParams = LinearLayout.LayoutParams(dpToPx(20f), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val emojiView = TextView(context).apply {
                text = emoji
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                layoutParams = LinearLayout.LayoutParams(dpToPx(40f), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val shortcodeView = TextView(context).apply {
                text = ":$shortcode:"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                ellipsize = TextUtils.TruncateAt.END
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(indexView)
            row.addView(emojiView)
            row.addView(shortcodeView)
            itemViews.add(row)
            entriesContainer.addView(row)
        }
        updateSelection()
    }

    private fun updateSelection() {
        itemViews.forEachIndexed { index, view ->
            view.setBackgroundColor(if (index == selectedIndex) Color.parseColor("#0D47A1") else Color.TRANSPARENT)
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
