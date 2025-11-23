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
 */
class UnicodeCharacterRecyclerViewAdapter(
    private val characters: List<String>,
    private val onCharacterClick: (String) -> Unit
) : RecyclerView.Adapter<UnicodeCharacterRecyclerViewAdapter.CharacterViewHolder>() {

    class CharacterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val characterText: TextView = itemView.findViewById(android.R.id.text1)
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
        
        // Click listener setup
        view.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION && position < characters.size) {
                onCharacterClick(characters[position])
            }
        }
        
        return holder
    }

    override fun onBindViewHolder(holder: CharacterViewHolder, position: Int) {
        holder.characterText.text = characters[position]
        // Ensure theme-aware color is applied on each bind (in case theme changes)
        val nightModeFlags = holder.itemView.context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isDarkTheme = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
        holder.characterText.setTextColor(if (isDarkTheme) Color.WHITE else Color.BLACK)
    }

    override fun getItemCount(): Int = characters.size
}


