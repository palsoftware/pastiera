package it.palsoftware.pastiera

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.app.AppCompatDelegate

/**
 * Adapter for Unicode character RecyclerView.
 * Optimized for performance using classic RecyclerView.
 * Supports separators using empty string markers that span full width.
 */
class UnicodeCharacterRecyclerViewAdapter(
    private val characters: List<String>,
    private val onCharacterClick: (String) -> Unit
) : RecyclerView.Adapter<UnicodeCharacterRecyclerViewAdapter.CharacterViewHolder>() {

    companion object {
        // Marker for separator items (empty string)
        private const val SEPARATOR_MARKER = ""
    }

    class CharacterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val characterText: TextView = itemView.findViewById(android.R.id.text1)
    }

    private fun isSeparator(position: Int): Boolean {
        return position < characters.size && characters[position] == SEPARATOR_MARKER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CharacterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        val holder = CharacterViewHolder(view)
        
        // Configure TextView to center character, theme-aware color and bold
        holder.characterText.apply {
            textSize = 28.8f // 24 * 1.2 (20% larger)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 0)
            minHeight = (48 * parent.context.resources.displayMetrics.density).toInt() // 40 * 1.2
            minWidth = (48 * parent.context.resources.displayMetrics.density).toInt() // 40 * 1.2
            // Use theme-aware text color: white for dark theme, black for light theme
            // Check UI mode directly from configuration (most reliable method)
            val nightModeFlags = parent.context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val isDarkTheme = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
            setTextColor(if (isDarkTheme) Color.WHITE else Color.BLACK)
            setTypeface(null, Typeface.BOLD)
        }
        
        // Click listener setup - only for non-separator items
        view.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION && position < characters.size) {
                if (!isSeparator(position)) {
                    onCharacterClick(characters[position])
                }
            }
        }
        
        return holder
    }

    override fun onBindViewHolder(holder: CharacterViewHolder, position: Int) {
        if (isSeparator(position)) {
            // Render separator as invisible, non-clickable space with zero height
            holder.characterText.text = ""
            holder.characterText.isClickable = false
            holder.characterText.isEnabled = false
            holder.itemView.isClickable = false
            holder.itemView.isEnabled = false
            // Make separator invisible and with zero height
            holder.itemView.alpha = 0f
            holder.itemView.minimumHeight = 0
            holder.characterText.minHeight = 0
            holder.characterText.height = 0
            // Set layout params to zero height
            val layoutParams = holder.itemView.layoutParams
            layoutParams.height = 0
            holder.itemView.layoutParams = layoutParams
        } else {
            holder.characterText.text = characters[position]
            holder.characterText.isClickable = true
            holder.characterText.isEnabled = true
            holder.itemView.isClickable = true
            holder.itemView.isEnabled = true
            holder.itemView.alpha = 1f
            // Reset height to default for non-separators
            val density = holder.itemView.context.resources.displayMetrics.density
            holder.characterText.minHeight = (48 * density).toInt()
            val layoutParams = holder.itemView.layoutParams
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            holder.itemView.layoutParams = layoutParams
            // Ensure theme-aware color is applied on each bind (in case theme changes)
            val nightModeFlags = holder.itemView.context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val isDarkTheme = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
            holder.characterText.setTextColor(if (isDarkTheme) Color.WHITE else Color.BLACK)
        }
    }

    override fun getItemCount(): Int = characters.size
}


