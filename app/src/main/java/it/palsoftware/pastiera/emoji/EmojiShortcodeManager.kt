package it.palsoftware.pastiera.emoji

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class EmojiShortcodeManager(private val context: Context) {

    companion object {
        private const val TAG = "EmojiShortcode"
        private const val EMOJI_DATA_FILE = "emoji.json"
        private const val SYMBOL_SHORTCODES_FILE = "symbol_shortcodes.json"
        private val SHORTCODE_REGEX = Regex("[a-zA-Z0-9_]+")
        private val COMPLETED_SHORTCODE_REGEX = Regex("(^|[\\s\\(\\[\\{\"'])[:]([a-zA-Z0-9_]{1,30}):$")
    }

    private val shortcodeMap = mutableMapOf<String, String>()

    init {
        loadShortcodes()
    }

    private fun hexToEmoji(hex: String): String {
        val codePoints = hex.split("-").map { it.toInt(16) }
        val chars = codePoints.flatMap { Character.toChars(it).toList() }.toCharArray()
        return String(chars)
    }

    private fun loadShortcodes() {
        try {
            val json = context.assets.open(EMOJI_DATA_FILE).bufferedReader().use { it.readText() }
            val emojiArray = JSONArray(json)
            for (index in 0 until emojiArray.length()) {
                val emojiObject = emojiArray.getJSONObject(index)
                val emoji = hexToEmoji(emojiObject.getString("unified"))
                val shortNames = emojiObject.optJSONArray("short_names")
                if (shortNames != null) {
                    for (shortNameIndex in 0 until shortNames.length()) {
                        shortcodeMap[shortNames.getString(shortNameIndex).lowercase()] = emoji
                    }
                } else {
                    val shortName = emojiObject.optString("short_name", "")
                    if (shortName.isNotEmpty()) {
                        shortcodeMap[shortName.lowercase()] = emoji
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load emoji shortcodes", e)
        }

        try {
            val json = context.assets.open(SYMBOL_SHORTCODES_FILE).bufferedReader().use { it.readText() }
            val symbols = JSONObject(json)
            val keys = symbols.keys()
            while (keys.hasNext()) {
                val shortcode = keys.next()
                shortcodeMap[shortcode.lowercase()] = symbols.getString(shortcode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load symbol shortcodes", e)
        }
    }

    fun searchShortcodes(prefix: String, limit: Int = 10): List<Pair<String, String>> {
        if (prefix.isEmpty()) return emptyList()
        val normalizedPrefix = prefix.lowercase()
        return shortcodeMap
            .filter { it.key.startsWith(normalizedPrefix) }
            .toList()
            .sortedBy { it.first.length }
            .take(limit)
            .map { (shortcode, character) -> character to shortcode }
    }

    fun extractCurrentShortcode(textBeforeCursor: String): Pair<String, Int>? {
        if (textBeforeCursor.isEmpty()) return null
        val lastColonIndex = textBeforeCursor.lastIndexOf(':')
        if (lastColonIndex == -1) return null
        val afterColon = textBeforeCursor.substring(lastColonIndex + 1)
        if (!afterColon.matches(SHORTCODE_REGEX) || afterColon.length > 30) {
            return null
        }
        return afterColon to lastColonIndex
    }

    fun extractCompletedShortcode(textBeforeCursor: String): String? {
        if (textBeforeCursor.isEmpty() || !textBeforeCursor.endsWith(':')) return null
        val match = COMPLETED_SHORTCODE_REGEX.find(textBeforeCursor) ?: return null
        return match.groupValues.getOrNull(2)?.lowercase()
    }

    fun lookupExactShortcode(shortcode: String): String? {
        if (shortcode.isBlank()) return null
        return shortcodeMap[shortcode.lowercase()]
    }
}
