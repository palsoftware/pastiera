package it.palsoftware.pastiera

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Dialog for selecting a Unicode character.
 * Uses a RecyclerView with common Unicode characters organized by category.
 */
@Composable
fun UnicodeCharacterPickerDialog(
    selectedLetter: String? = null,
    onCharacterSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // Header section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedLetter != null) {
                            stringResource(R.string.unicode_picker_title_for_letter, selectedLetter)
                        } else {
                            stringResource(R.string.unicode_picker_title)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.unicode_picker_close), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                // Common Unicode character categories
                val characterCategories = remember {
                    mapOf(
                        "punteggiatura" to listOf(
                            "¿", "¡", "…", "—", "–", "«", "»", "‹", "›", "„",
                            "‚", """, """, "'", "'", "•", "‥", "‰", "′", "″",
                            "‴", "‵", "‶", "‷", "‸", "※", "§", "¶", "†", "‡"
                        ),
                        "simboli_matematici" to listOf(
                            "±", "×", "÷", "≠", "≤", "≥", "≈", "∞", "∑", "∏",
                            "√", "∫", "∆", "∇", "∂", "α", "β", "γ", "δ", "ε",
                            "π", "Ω", "θ", "λ", "μ", "σ", "φ", "ω", "∑", "∏"
                        ),
                        "simboli_valuta" to listOf(
                            "€", "£", "¥", "$", "¢", "₹", "₽", "₩", "₪", "₫",
                            "₦", "₨", "₩", "₪", "₫", "₦", "₨", "₩", "₪", "₫"
                        ),
                        "simboli_tecnici" to listOf(
                            "~", "`", "{", "}", "[", "]", "<", ">", "^", "%",
                            "=", "\\", "|", "&", "@", "#", "*", "+", "-", "_",
                            "©", "®", "™", "°", "°", "°", "°", "°", "°", "°"
                        ),
                        "simboli_freccia" to listOf(
                            "←", "→", "↑", "↓", "↔", "↕", "↗", "↘", "↙", "↖",
                            "⇐", "⇒", "⇑", "⇓", "⇔", "⇕", "⇗", "⇘", "⇙", "⇖"
                        ),
                        "simboli_varie" to listOf(
                            "★", "☆", "♥", "♦", "♣", "♠", "♪", "♫", "☀", "☁",
                            "☂", "☃", "☎", "☏", "☐", "☑", "☒", "☓", "☔", "☕",
                            "☖", "☗", "☘", "☙", "☚", "☛", "☜", "☝", "☞", "☟"
                        )
                    )
                }
                
                // Helper function to translate category keys
                @Composable
                fun getCategoryName(categoryKey: String): String {
                    return when (categoryKey) {
                        "punteggiatura" -> stringResource(R.string.unicode_category_punctuation)
                        "simboli_matematici" -> stringResource(R.string.unicode_category_math)
                        "simboli_valuta" -> stringResource(R.string.unicode_category_currency)
                        "simboli_tecnici" -> stringResource(R.string.unicode_category_technical)
                        "simboli_freccia" -> stringResource(R.string.unicode_category_arrows)
                        "simboli_varie" -> stringResource(R.string.unicode_category_misc)
                        else -> categoryKey
                    }
                }
                
                // Category tabs
                var selectedCategory by remember { mutableStateOf(characterCategories.keys.first()) }
                
                // Tab selector (using scrollable Row instead of ScrollableTabRow for compatibility)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    characterCategories.keys.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(getCategoryName(category), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Character grid with RecyclerView for optimal performance
                val selectedCharacters = characterCategories[selectedCategory] ?: emptyList()
                
                key(selectedCategory) {
                    AndroidView(
                        factory = { context ->
                            val recyclerView = RecyclerView(context)
                            val screenWidth = context.resources.displayMetrics.widthPixels
                            val characterSize = (40 * context.resources.displayMetrics.density).toInt()
                            val spacing = (2 * context.resources.displayMetrics.density).toInt()
                            val padding = (4 * context.resources.displayMetrics.density).toInt()
                            
                            // Calculate number of columns based on screen width
                            val columns = (screenWidth / (characterSize + spacing)).coerceAtLeast(4)
                            
                            recyclerView.apply {
                                layoutManager = GridLayoutManager(context, columns)
                                adapter = UnicodeCharacterRecyclerViewAdapter(selectedCharacters) { character ->
                                    onCharacterSelected(character)
                                    onDismiss()
                                }
                                setPadding(padding, padding, padding, padding)
                                clipToPadding = false
                                // Performance optimizations
                                setHasFixedSize(true)
                                setItemViewCacheSize(20)
                            }
                            recyclerView
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}
