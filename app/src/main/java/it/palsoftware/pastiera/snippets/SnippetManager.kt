package it.palsoftware.pastiera.snippets

import android.content.Context
import it.palsoftware.pastiera.SettingsManager
import java.util.Locale

class SnippetManager(private val context: Context) {

    companion object {
        private val SHORTCUT_REGEX = Regex("[a-zA-Z0-9_]+")
    }

    data class CompletedSnippet(
        val shortcut: String,
        val boundary: String
    )

    fun searchSnippets(prefix: String, limit: Int = 10): List<Pair<String, String>> {
        if (prefix.isBlank()) return emptyList()
        val normalizedPrefix = prefix.lowercase(Locale.ROOT)
        return SettingsManager.getSnippets(context)
            .asSequence()
            .filter { (shortcut, _) -> shortcut.startsWith(normalizedPrefix) }
            .sortedBy { (shortcut, _) -> shortcut.length }
            .take(limit)
            .map { (shortcut, value) -> value to shortcut }
            .toList()
    }

    fun extractCurrentSnippet(textBeforeCursor: String, trigger: Char): Pair<String, Int>? {
        if (textBeforeCursor.isEmpty()) return null
        val triggerIndex = textBeforeCursor.lastIndexOf(trigger)
        if (triggerIndex == -1) return null
        if (triggerIndex > 0) {
            val previousChar = textBeforeCursor[triggerIndex - 1]
            if (!previousChar.isWhitespace() && previousChar !in "([{\"'") {
                return null
            }
        }
        val afterTrigger = textBeforeCursor.substring(triggerIndex + 1)
        if (!afterTrigger.matches(SHORTCUT_REGEX) || afterTrigger.length > 40) {
            return null
        }
        return afterTrigger.lowercase(Locale.ROOT) to triggerIndex
    }

    fun extractCompletedSnippet(textBeforeCursor: String, trigger: Char): CompletedSnippet? {
        if (textBeforeCursor.length < 2) return null
        val boundaryChar = textBeforeCursor.last()
        if (boundaryChar != ' ' && boundaryChar != '\n' && boundaryChar != '\t') {
            return null
        }
        val content = textBeforeCursor.dropLast(1)
        val (shortcut, _) = extractCurrentSnippet(content, trigger) ?: return null
        return CompletedSnippet(shortcut = shortcut, boundary = boundaryChar.toString())
    }

    fun lookupExactSnippet(shortcut: String): String? {
        if (shortcut.isBlank()) return null
        return SettingsManager.getSnippets(context)[shortcut.lowercase(Locale.ROOT)]
    }
}
