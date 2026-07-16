package it.palsoftware.pastiera

/**
 * Configuration for SYM pages order and visibility.
 */
data class SymPagesConfig(
    val emojiEnabled: Boolean = true,
    val symbolsEnabled: Boolean = true,
    val clipboardEnabled: Boolean = false,
    val emojiPickerEnabled: Boolean = false,
    val gifPickerEnabled: Boolean = false,
    val symPageOrder: List<String> = DEFAULT_ORDER
) {
    companion object {
        const val PAGE_EMOJI = "emoji"
        const val PAGE_SYMBOLS = "symbols"
        const val PAGE_CLIPBOARD = "clipboard"
        const val PAGE_EMOJI_PICKER = "emoji_picker"
        const val PAGE_GIF_PICKER = "gif_picker"
        val DEFAULT_ORDER = listOf(PAGE_EMOJI, PAGE_SYMBOLS, PAGE_CLIPBOARD, PAGE_EMOJI_PICKER, PAGE_GIF_PICKER)
    }

    fun normalizedOrder(): List<String> {
        val knownPages = DEFAULT_ORDER
        val ordered = symPageOrder
            .map { it.trim() }
            .filter { it in knownPages }
            .distinct()
            .toMutableList()
        knownPages.forEach { pageId ->
            if (pageId !in ordered) {
                ordered.add(pageId)
            }
        }
        return ordered
    }

    fun isPageEnabled(pageId: String): Boolean = when (pageId) {
        PAGE_EMOJI -> emojiEnabled
        PAGE_SYMBOLS -> symbolsEnabled
        PAGE_CLIPBOARD -> clipboardEnabled
        PAGE_EMOJI_PICKER -> emojiPickerEnabled
        PAGE_GIF_PICKER -> gifPickerEnabled
        else -> false
    }

    fun enabledOrderedPages(): List<String> {
        return normalizedOrder().filter { isPageEnabled(it) }
    }

    fun prefersEmojiLongPressLayer(): Boolean {
        val order = normalizedOrder()
        val emojiIndex = order.indexOf(PAGE_EMOJI)
        val symbolsIndex = order.indexOf(PAGE_SYMBOLS)
        return when {
            emojiIndex < 0 && symbolsIndex < 0 -> true
            emojiIndex < 0 -> false
            symbolsIndex < 0 -> true
            else -> emojiIndex <= symbolsIndex
        }
    }
}
