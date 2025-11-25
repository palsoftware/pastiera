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
                            "“", "”", "‘", "’", "\"", // Virgolette doppie e singole standard aperte e chiuse
                            "¿", "¡", "…", "—", "–", "«", "»", "‹", "›", "„",
                            "‚",  "'", "'", "•", "‥", "‰", "′", "″",
                            "‴", "‵", "‶", "‷", "‸", "※", "§", "¶", "†", "‡",
                            ";", ":", "!", "?", ".", ",", "‽", "⁇", "⁈", "⁉",
                            "(", ")", "[", "]", "{", "}", "<", ">",
                        ),
                        "simboli_matematici" to listOf(
                            "±", "×", "÷", "≠", "≤", "≥", "≈", "∞", "∑", "∏",
                            "√", "∫", "∆", "∇", "∂", "α", "β", "γ", "δ", "ε",
                            "π", "Ω", "θ", "λ", "μ", "σ", "φ", "ω",
                            "½", "¼", "¾", "⅓", "⅔", "⅕", "⅖", "⅗", "⅘", "⅙",
                            "⅚", "⅛", "⅜", "⅝", "⅞", "∝", "∠", "∡", "∢", "∟",
                            "∴", "∵", "∶", "∷", "∼", "∽", "≀", "≁", "≂", "≃",
                            "≄", "≅", "≆", "≇", "≉", "≊", "≋", "≌", "≍"
                        ),
                        "simboli_valuta" to listOf(
                            "€", "£", "¥", "$", "¢", "₹", "₽", "₩", "₪", "₫",
                            "₦", "₨", "₩", "₪", "₫", "₦", "₨", "₩", "₪", "₫",
                            "₭", "₮", "₯", "₰", "₱", "₲", "₳", "₴", "₵", "₶", "֏"
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
                            // A variations - Order: grave, acute, circumflex, tilde, diaeresis, ring, macron, breve, ogonek, hook below
                            "À", "à", "Á", "á", "Â", "â", "Ã", "ã", "Ä", "ä", "Å", "å", "Ā", "ā", "Ă", "ă", "Ą", "ą",
                            "Ạ", "ạ", "Ả", "ả", "Ấ", "ấ", "Ầ", "ầ", "Ẩ", "ẩ", "Ẫ", "ẫ", "Ậ", "ậ", "Ắ", "ắ", "Ằ", "ằ", "Ẳ", "ẳ", "Ẵ", "ẵ", "Ặ", "ặ",
                            "Æ", "æ", "Ǣ", "ǣ", "Ǽ", "ǽ", "",
                            // B variations
                            "Ɓ", "Ƃ", "ƃ", "Ƅ", "ƅ", "",
                            // C variations - Order: cedilla, acute, circumflex, dot above, caron
                            "Ç", "ç", "Ć", "ć", "Ĉ", "ĉ", "Ċ", "ċ", "Č", "č", "Ƈ", "ƈ", "Ȼ", "ȼ", "",
                            // D variations - Order: eth, caron, d with stroke, dot below, tilde below
                            "Ð", "ð", "Ď", "ď", "Đ", "đ", "Ɖ", "Ɗ", "Ƌ", "ƌ", "ƍ", "Ḍ", "ḍ", "Ḑ", "ḑ", "Ǳ", "ǲ", "ǳ", "Ǆ", "ǅ", "ǆ", "",
                            // E variations - Order: grave, acute, circumflex, diaeresis, macron, breve, dot above, ogonek, caron, hook below
                            "È", "è", "Ȅ", "ȅ", "É", "é", "Ȇ", "ȇ", "Ê", "ê", "Ë", "ë", "Ē", "ē", "Ĕ", "ĕ", "Ė", "ė", "Ę", "ę", "Ě", "ě",
                            "Ǝ", "Ə", "Ɛ", "ǝ", "Ȩ", "ȩ", "Ɇ", "ɇ",
                            "Ẹ", "ẹ", "Ẻ", "ẻ", "Ẽ", "ẽ", "Ế", "ế", "Ề", "ề", "Ể", "ể", "Ễ", "ễ", "Ệ", "ệ", "",
                            // F variations
                            "Ƒ", "ƒ", "",
                            // G variations - Order: circumflex, breve, dot above, cedilla
                            "Ĝ", "ĝ", "Ğ", "ğ", "Ġ", "ġ", "Ģ", "ģ", "Ɠ", "Ǥ", "ǥ", "Ǧ", "ǧ", "Ǵ", "ǵ", "ɢ", "",
                            // H variations - Order: circumflex, stroke, breve below, line below, tilde below
                            "Ĥ", "ĥ", "Ħ", "ħ", "ƕ", "Ƕ", "Ȟ", "ȟ", "Ḥ", "ḥ", "Ḫ", "ḫ", "Ḩ", "ḩ", "ɦ", "ɧ", "",
                            // I variations - Order: grave, acute, circumflex, diaeresis, tilde, macron, breve, ogonek, dot above, hook below
                            "Ì", "ì", "Ȉ", "ȉ", "Í", "í", "Ȋ", "ȋ", "Î", "î", "Ï", "ï", "Ĩ", "ĩ", "Ī", "ī", "Ĭ", "ĭ", "Į", "į", "İ", "ı",
                            "Ɨ", "ɨ", "ɩ", "Ị", "ị", "",
                            // J variations - Order: circumflex
                            "Ĵ", "ĵ", "ȷ", "Ɉ", "ɉ", "",
                            // K variations - Order: cedilla, caron, acute, dot below, line below
                            "Ķ", "ķ", "ĸ", "Ƙ", "ƙ", "Ǩ", "ǩ", "Ḱ", "ḱ", "Ḳ", "ḳ", "Ḵ", "ḵ", "",
                            // L variations - Order: acute, cedilla, caron, dot above, stroke, ring below
                            "Ĺ", "ĺ", "Ļ", "ļ", "Ľ", "ľ", "Ŀ", "ŀ", "Ł", "ł", "Ḷ", "ḷ", "Ḹ", "ḹ", "ƚ", "Ǉ", "ǈ", "ǉ", "",
                            // M variations - Order: grave, acute, circumflex, macron, caron, dot above, dot below
                            "M̀", "m̀", "Ḿ", "ḿ", "M̂", "m̂", "M̄", "m̄", "M̌", "m̌", "Ṁ", "ṁ", "Ṃ", "ṃ", "Ɱ", "ɱ", "",
                            // N variations - Order: tilde, acute, cedilla, caron, eng, dot below
                            "Ñ", "ñ", "Ń", "ń", "Ņ", "ņ", "Ň", "ň", "ŉ", "Ŋ", "ŋ", "Ṇ", "ṇ", "Ɲ", "ƞ", "Ǌ", "ǋ", "ǌ", "Ƞ", "ȵ", "ɲ", "ɳ", "ɴ", "",
                            // O variations - Order: grave, acute, circumflex, tilde, diaeresis, stroke, macron, breve, double acute, ogonek, hook below, tilde below
                            "Ò", "ò", "Ȍ", "ȍ", "Ó", "ó", "Ȏ", "ȏ", "Ô", "ô", "Õ", "õ", "Ö", "ö", "Ø", "ø", "Ō", "ō", "Ŏ", "ŏ", "Ő", "ő",
                            "Ɵ", "Ơ", "ơ", "Ǒ", "ǒ", "Ǫ", "ǫ", "Ǭ", "ǭ", "Ǿ", "ǿ", "Ȯ", "ȯ", "Ȱ", "ȱ", "ɵ",
                            "Ọ", "ọ", "Ỏ", "ỏ", "Ố", "ố", "Ồ", "ồ", "Ổ", "ổ", "Ỗ", "ỗ", "Ộ", "ộ", "Ớ", "ớ", "Ờ", "ờ", "Ở", "ở", "Ỡ", "ỡ", "Ợ", "ợ", "Ṏ", "ṏ", "",
                            // P variations
                            "Ƥ", "ƥ", "ᵽ", "",
                            // Q variations
                            "Ɋ", "ɋ", "",
                            // R variations - Order: acute, cedilla, caron
                            "Ŕ", "ŕ", "Ȑ", "ȑ", "Ȓ", "ȓ", "Ŗ", "ŗ", "Ř", "ř", "Ʀ", "ɍ", "ɹ", "ɺ", "ɻ", "ɼ", "ɽ", "ɾ", "ɿ", "",
                            // S variations - Order: acute, circumflex, cedilla, caron, dot below
                            "Ś", "ś", "Ŝ", "ŝ", "Ş", "ş", "Š", "š", "Ṣ", "ṣ", "Ʃ", "ƪ", "Ș", "ș", "ʃ", "ʅ", "ʆ", "",
                            // T variations - Order: cedilla, caron, stroke, dot below
                            "Ţ", "ţ", "Ť", "ť", "Ŧ", "ŧ", "Ṭ", "ṭ", "ƫ", "Ƭ", "ƭ", "Ʈ", "Ț", "ț", "ȶ", "Ⱦ", "ʇ", "ʈ", "",
                            // U variations - Order: grave, acute, circumflex, diaeresis, tilde, macron, breve, ring above, double acute, ogonek, hook below
                            "Ù", "ù", "Ȕ", "ȕ", "Ú", "ú", "Ȗ", "ȗ", "Û", "û", "Ü", "ü", "Ũ", "ũ", "Ū", "ū", "Ŭ", "ŭ", "Ů", "ů", "Ű", "ű", "Ų", "ų",
                            "Ư", "ư", "Ǔ", "ǔ", "Ǖ", "ǖ", "Ǘ", "ǘ", "Ǚ", "ǚ", "Ǜ", "ǜ", "Ʉ", "ʉ",
                            "Ụ", "ụ", "Ủ", "ủ", "Ứ", "ứ", "Ừ", "ừ", "Ử", "ử", "Ữ", "ữ", "Ự", "ự", "",
                            // V variations - Order: tilde below
                            "Ʋ", "Ƴ", "ƴ", "Ṽ", "ṽ", "Ʌ", "ʌ", "ʋ", "",
                            // W variations - Order: circumflex
                            "Ŵ", "ŵ", "Ɯ", "Ƿ", "Ǹ", "ǹ", "ɯ", "ɰ", "ʍ", "",
                            // X variations
                            "Ẋ", "ẋ", "Ẍ", "ẍ", "Ƣ", "ƣ", "",
                            // Y variations - Order: acute, circumflex, diaeresis, macron, hook below
                            "Ý", "ý", "Ŷ", "ŷ", "Ÿ", "ÿ", "Ȳ", "ȳ", "Ỳ", "ỳ", "Ỵ", "ỵ", "Ỷ", "ỷ", "Ỹ", "ỹ", "Ɏ", "ɏ", "ʎ", "ʏ", "",
                            // Z variations - Order: acute, dot above, caron
                            "Ź", "ź", "Ż", "ż", "Ž", "ž", "Ƶ", "ƶ", "Ʒ", "Ƹ", "ƹ", "ƺ", "Ǯ", "ǯ", "Ȥ", "ȥ", "ɀ", "ʐ", "ʑ", "ʓ"
                        ),
                        "simboli_varie" to listOf(
                            // Mathematical operators and symbols
                            "∅", "∈", "∉", "∋", "∌", "∐", "−", "∓",
                            "∔", "∕", "∖", "∗", "∘", "∙", "∛", "∜",
                            "∣", "∤", "∥", "∦", "∧",
                            "∨", "∩", "∪", "∬", "∭", "∮", "∯", "∰", "∱",
                            "∲", "∳", "∸", "∹", "∺", "∻",
                            "∾", "∿", "≀", "≁", "≂", "≃", "≄", "≅",
                            "≆", "≇", "≉", "≊", "≋", "≌", "≍", "≎", "≏",
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
                                
                                val layoutManager = GridLayoutManager(context, columns)
                                val adapter = UnicodeCharacterRecyclerViewAdapter(selectedCharacters) { character ->
                                    onCharacterSelected(character)
                                    onDismiss()
                                }
                                
                                // Configure span size lookup to make separators (empty strings) span full width
                                layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                                    override fun getSpanSize(position: Int): Int {
                                        // If it's a separator (empty string), make it span all columns
                                        return if (position < selectedCharacters.size && selectedCharacters[position].isEmpty()) {
                                            columns
                                        } else {
                                            1
                                        }
                                    }
                                }
                                
                                recyclerView.apply {
                                    this.layoutManager = layoutManager
                                    this.adapter = adapter
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
