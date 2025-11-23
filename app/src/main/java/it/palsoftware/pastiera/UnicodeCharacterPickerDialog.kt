package it.palsoftware.pastiera

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Centered title
                    Text(
                        text = if (selectedLetter != null) {
                            stringResource(R.string.unicode_picker_title_for_letter, selectedLetter)
                        } else {
                            stringResource(R.string.unicode_picker_title)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    // Close button on the right
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
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
                            "‴", "‵", "‶", "‷", "‸", "※", "§", "¶", "†", "‡",
                            ";", ":", "!", "?", ".", ",", "‽", "⁇", "⁈", "⁉"
                        ),
                        "simboli_matematici" to listOf(
                            "±", "×", "÷", "≠", "≤", "≥", "≈", "∞", "∑", "∏",
                            "√", "∫", "∆", "∇", "∂", "α", "β", "γ", "δ", "ε",
                            "π", "Ω", "θ", "λ", "μ", "σ", "φ", "ω", "∑", "∏",
                            "½", "¼", "¾", "⅓", "⅔", "⅕", "⅖", "⅗", "⅘", "⅙",
                            "⅚", "⅛", "⅜", "⅝", "⅞", "∝", "∠", "∡", "∢", "∟",
                            "∴", "∵", "∶", "∷", "∼", "∽", "≀", "≁", "≂", "≃",
                            "≄", "≅", "≆", "≇", "≈", "≉", "≊", "≋", "≌", "≍"
                        ),
                        "simboli_valuta" to listOf(
                            "€", "£", "¥", "$", "¢", "₹", "₽", "₩", "₪", "₫",
                            "₦", "₨", "₩", "₪", "₫", "₦", "₨", "₩", "₪", "₫",
                            "₭", "₮", "₯", "₰", "₱", "₲", "₳", "₴", "₵", "₶"
                        ),
                        "simboli_tecnici" to listOf(
                            "~", "`", "{", "}", "[", "]", "<", ">", "^", "%",
                            "=", "\\", "|", "&", "@", "#", "*", "+", "-", "_",
                            "©", "®", "™", "°", "℠", "℡", "℣", "ℤ", "℥", "Ω",
                            "℧", "ℨ", "℩", "K", "Å", "ℬ", "ℭ", "℮", "ℯ", "ℰ"
                        ),
                        "simboli_freccia" to listOf(
                            "←", "→", "↑", "↓", "↔", "↕", "↗", "↘", "↙", "↖",
                            "⇐", "⇒", "⇑", "⇓", "⇔", "⇕", "⇗", "⇘", "⇙", "⇖",
                            "⇠", "⇡", "⇢", "⇣", "⇤", "⇥", "⇦", "⇧", "⇨", "⇩",
                            "⇪", "⇫", "⇬", "⇭", "⇮", "⇯", "⇰", "⇱", "⇲", "⇳"
                        ),
                        "variazioni" to listOf(
                            // A variations
                            "À", "Á", "Â", "Ã", "Ä", "Å", "Ā", "Ă", "Ą", "à", "á", "â", "ã", "ä", "å", "ā", "ă", "ą",
                            "Æ", "æ", "Ǣ", "ǣ", "Ǽ", "ǽ",
                            // B variations
                            "Ɓ", "Ƃ", "ƃ", "Ƅ", "ƅ",
                            // C variations
                            "Ç", "Ć", "Ĉ", "ĉ", "Ċ", "ċ", "Č", "č", "Ƈ", "ƈ", "Ȼ", "ȼ",
                            // D variations
                            "Ð", "Ď", "ď", "Đ", "đ", "Ɖ", "Ɗ", "Ƌ", "ƌ", "ƍ", "Ǳ", "ǲ", "ǳ", "Ǆ", "ǅ", "ǆ",
                            // E variations
                            "È", "É", "Ê", "Ë", "Ē", "Ĕ", "Ė", "ė", "Ę", "ę", "Ě", "ě", "Ǝ", "Ə", "Ɛ", "ǝ", "Ȅ", "ȅ", "Ȇ", "ȇ", "Ȩ", "ȩ", "Ɇ", "ɇ",
                            // F variations
                            "Ƒ", "ƒ",
                            // G variations
                            "Ĝ", "ĝ", "Ğ", "ğ", "Ġ", "ġ", "Ģ", "ģ", "Ɠ", "Ǥ", "ǥ", "Ǧ", "ǧ", "Ǵ", "ǵ", "ɢ",
                            // H variations
                            "Ĥ", "ĥ", "Ħ", "ħ", "ƕ", "Ƕ", "Ȟ", "ȟ", "ɦ", "ɧ",
                            // I variations
                            "Ì", "Í", "Î", "Ï", "Ĩ", "ĩ", "Ī", "ī", "Ĭ", "ĭ", "Į", "į", "İ", "ı", "Ɨ", "Ɩ", "Ȉ", "ȉ", "Ȋ", "ȋ", "ɨ", "ɩ",
                            // J variations
                            "Ĵ", "ĵ", "Ɩ", "ȷ", "Ɉ", "ɉ",
                            // K variations
                            "Ķ", "ķ", "ĸ", "Ƙ", "ƙ", "Ǩ", "ǩ", "Ḱ", "ḱ", "Ḳ", "ḳ", "Ḵ", "ḵ",
                            // L variations
                            "Ĺ", "ĺ", "Ļ", "ļ", "Ľ", "ľ", "Ŀ", "ŀ", "Ł", "ł", "ƚ", "Ǉ", "ǈ", "ǉ", "Ǌ", "ǋ", "ǌ",
                            // M variations
                            "Ɯ", "ɯ", "ɰ",
                            // N variations
                            "Ñ", "Ń", "ń", "Ņ", "ņ", "Ň", "ň", "ŉ", "Ŋ", "ŋ", "Ɲ", "ƞ", "Ǌ", "ǋ", "ǌ", "Ƞ", "ȵ", "ɲ", "ɳ", "ɴ",
                            // O variations
                            "Ò", "Ó", "Ô", "Õ", "Ö", "Ø", "Ō", "ō", "Ŏ", "ŏ", "Ő", "ő", "Ɵ", "Ơ", "ơ", "Ǒ", "ǒ", "Ǫ", "ǫ", "Ǭ", "ǭ", "Ǿ", "ǿ", "Ȍ", "ȍ", "Ȏ", "ȏ", "Ȯ", "ȯ", "Ȱ", "ȱ", "ɵ",
                            // P variations
                            "Ƥ", "ƥ", "ᵽ",
                            // Q variations
                            "Ɋ", "ɋ",
                            // R variations
                            "Ŕ", "ŕ", "Ŗ", "ŗ", "Ř", "ř", "Ʀ", "Ȑ", "ȑ", "Ȓ", "ȓ", "ɍ", "ɹ", "ɺ", "ɻ", "ɼ", "ɽ", "ɾ", "ɿ",
                            // S variations
                            "Ś", "ś", "Ŝ", "ŝ", "Ş", "ş", "Š", "š", "Ţ", "ţ", "Ť", "ť", "Ŧ", "ŧ", "Ʃ", "ƪ", "ƫ", "Ƭ", "ƭ", "Ʈ", "Ș", "ș", "ȿ", "ɀ", "ʃ", "ʅ", "ʆ",
                            // T variations
                            "Ţ", "ţ", "Ť", "ť", "Ŧ", "ŧ", "ƫ", "Ƭ", "ƭ", "Ʈ", "Ț", "ț", "ȶ", "Ⱦ", "ȿ", "ɀ", "ʇ", "ʈ",
                            // U variations
                            "Ù", "Ú", "Û", "Ü", "Ũ", "ũ", "Ū", "ū", "Ŭ", "ŭ", "Ů", "ů", "Ű", "ű", "Ų", "ų", "Ư", "ư", "Ʋ", "Ƴ", "ƴ", "Ǔ", "ǔ", "Ǖ", "ǖ", "Ǘ", "ǘ", "Ǚ", "ǚ", "Ǜ", "ǜ", "Ȕ", "ȕ", "Ȗ", "ȗ", "Ʉ", "ʉ",
                            // V variations
                            "Ʋ", "Ƴ", "ƴ", "Ʌ", "ʌ", "ʋ",
                            // W variations
                            "Ŵ", "ŵ", "Ɯ", "Ƿ", "Ǹ", "ǹ", "ɯ", "ɰ", "ʍ",
                            // X variations
                            "Ẋ", "ẋ", "Ẍ", "ẍ", "Ƣ", "ƣ", "Ǯ", "ǯ",
                            // Y variations
                            "Ý", "ý", "Ŷ", "ŷ", "Ÿ", "ÿ", "Ƴ", "ƴ", "Ȳ", "ȳ", "Ɏ", "ɏ", "ʎ", "ʏ",
                            // Z variations
                            "Ź", "ź", "Ż", "ż", "Ž", "ž", "Ƶ", "ƶ", "Ʒ", "Ƹ", "ƹ", "ƺ", "Ǯ", "ǯ", "Ȥ", "ȥ", "ɀ", "ʐ", "ʑ", "ʓ"
                        ),
                        "simboli_varie" to listOf(
                            // Mathematical operators and symbols
                            "∅", "∈", "∉", "∋", "∌", "∏", "∑", "∐", "−", "∓",
                            "∔", "∕", "∖", "∗", "∘", "∙", "√", "∛", "∜", "∝",
                            "∞", "∟", "∠", "∡", "∢", "∣", "∤", "∥", "∦", "∧",
                            "∨", "∩", "∪", "∫", "∬", "∭", "∮", "∯", "∰", "∱",
                            "∲", "∳", "∴", "∵", "∶", "∷", "∸", "∹", "∺", "∻",
                            "∼", "∽", "∾", "∿", "≀", "≁", "≂", "≃", "≄", "≅",
                            "≆", "≇", "≈", "≉", "≊", "≋", "≌", "≍", "≎", "≏",
                            // Additional symbols
                            "⊂", "⊃", "⊄", "⊅", "⊆", "⊇", "⊈", "⊉", "⊊", "⊋",
                            "⊌", "⊍", "⊎", "⊏", "⊐", "⊑", "⊒", "⊓", "⊔", "⊕",
                            "⊖", "⊗", "⊘", "⊙", "⊚", "⊛", "⊜", "⊝", "⊞", "⊟",
                            "⊠", "⊡", "⊢", "⊣", "⊤", "⊥", "⊦", "⊧", "⊨", "⊩",
                            "⊪", "⊫", "⊬", "⊭", "⊮", "⊯", "⊰", "⊱", "⊲", "⊳",
                            "⊴", "⊵", "⊶", "⊷", "⊸", "⊹", "⊺", "⊻", "⊼", "⊽",
                            "⊾", "⊿", "⋀", "⋁", "⋂", "⋃", "⋄", "⋅", "⋆", "⋇",
                            "⋈", "⋉", "⋊", "⋋", "⋌", "⋍", "⋎", "⋏", "⋐", "⋑",
                            "⋒", "⋓", "⋔", "⋕", "⋖", "⋗", "⋘", "⋙", "⋚", "⋛",
                            "⋜", "⋝", "⋞", "⋟", "⋠", "⋡", "⋢", "⋣", "⋤", "⋥",
                            "⋦", "⋧", "⋨", "⋩", "⋪", "⋫", "⋬", "⋭", "⋮", "⋯",
                            "⋰", "⋱", "⋲", "⋳", "⋴", "⋵", "⋶", "⋷", "⋸", "⋹",
                            "⋺", "⋻", "⋼", "⋽", "⋾", "⋿",
                            // Unit symbols and other technical symbols
                            "℀", "℁", "℃", "℄", "℅", "℆", "ℇ", "℈", "℉", "ℊ",
                            "ℋ", "ℌ", "ℍ", "ℎ", "ℏ", "ℐ", "ℑ", "ℒ", "ℓ", "℔",
                            "ℕ", "№", "℗", "℘", "ℙ", "ℚ", "ℛ", "ℜ", "ℝ", "℞",
                            "℟", "℠", "℡", "™", "℣", "ℤ", "℥", "Ω", "℧", "ℨ",
                            "℩", "K", "Å", "ℬ", "ℭ", "℮", "ℯ", "ℰ", "ℱ", "Ⅎ",
                            "ℳ", "ℴ", "ℵ", "ℶ", "ℷ", "ℸ", "ℹ", "℺", "℻", "ℼ",
                            "ℽ", "ℾ", "ℿ", "⅀", "⅁", "⅂", "⅃", "⅄", "ⅅ", "ⅆ",
                            "ⅇ", "ⅈ", "ⅉ", "⅊", "⅋", "⅌", "⅍", "ⅎ", "⅏"
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
                        "variazioni" -> stringResource(R.string.unicode_category_variations)
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
                            label = { 
                                Text(
                                    getCategoryName(category), 
                                    style = MaterialTheme.typography.labelMedium // 20% larger than labelSmall
                                ) 
                            },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Character grid with RecyclerView for optimal performance
                val selectedCharacters = characterCategories[selectedCategory] ?: emptyList()
                
                key(selectedCategory) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clipToBounds()
                    ) {
                        AndroidView(
                            factory = { context ->
                                val recyclerView = RecyclerView(context)
                                val screenWidth = context.resources.displayMetrics.widthPixels
                                val characterSize = (48 * context.resources.displayMetrics.density).toInt() // 40 * 1.2 (20% larger)
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
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
