package it.palsoftware.pastiera

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import it.palsoftware.pastiera.data.emoji.EmojiRepository

/**
 * Adapter for emoji entries with optional variants shown on long-press popup.
 */
class EmojiEntryRecyclerViewAdapter(
    private val entries: List<EmojiRepository.EmojiEntry>,
    private val onEmojiSelected: (String) -> Unit
) : RecyclerView.Adapter<EmojiEntryRecyclerViewAdapter.EmojiEntryViewHolder>() {

    class EmojiEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val emojiText: TextView = itemView.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiEntryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        val holder = EmojiEntryViewHolder(view)

        holder.emojiText.apply {
            textSize = 28.8f // 24 * 1.2 (20% larger)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            val size = (48 * parent.context.resources.displayMetrics.density).toInt()
            minHeight = size
            minWidth = size
            setTypeface(null, Typeface.BOLD)
            val nightModeFlags = parent.context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val isDarkTheme = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
            setTextColor(if (isDarkTheme) Color.WHITE else Color.BLACK)
        }

        view.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION && position < entries.size) {
                onEmojiSelected(entries[position].base)
            }
        }

        view.setOnLongClickListener {
            val position = holder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION || position >= entries.size) return@setOnLongClickListener true
            val entry = entries[position]
            if (entry.variants.isEmpty()) return@setOnLongClickListener false
            showVariantsPopup(view, entry)
            true
        }

        return holder
    }

    override fun onBindViewHolder(holder: EmojiEntryViewHolder, position: Int) {
        val entry = entries[position]
        holder.emojiText.text = entry.base
    }

    override fun getItemCount(): Int = entries.size

    private fun showVariantsPopup(anchor: View, entry: EmojiRepository.EmojiEntry) {
        val context = anchor.context
        val density = context.resources.displayMetrics.density
        val horizontalPadding = (16 * density).toInt()
        val verticalPadding = (12 * density).toInt()
        val itemHorizontalPadding = (12 * density).toInt()
        val itemVerticalPadding = (8 * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            gravity = Gravity.CENTER
        }

        var popup: PopupWindow? = null
        val options = listOf(entry.base) + entry.variants
        options.forEach { emoji ->
            val textView = TextView(context).apply {
                text = emoji
                textSize = 24f
                gravity = Gravity.CENTER
                setPadding(itemHorizontalPadding, itemVerticalPadding, itemHorizontalPadding, itemVerticalPadding)
                val nightModeFlags = context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                val isDarkTheme = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
                setTextColor(if (isDarkTheme) Color.WHITE else Color.BLACK)
            }
            textView.setOnClickListener {
                onEmojiSelected(emoji)
                popup?.dismiss()
            }
            container.addView(textView)
        }

        popup = PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#DDFFFFFF")))
            isOutsideTouchable = true
            elevation = 8f
        }

        popup.showAsDropDown(anchor, 0, -anchor.height / 2)
    }
}

