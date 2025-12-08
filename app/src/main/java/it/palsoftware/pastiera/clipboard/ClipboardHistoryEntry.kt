package it.palsoftware.pastiera.clipboard

/**
 * Represents a single clipboard history entry.
 * @param id Unique identifier for the entry
 * @param timeStamp When the entry was created/last updated (System.currentTimeMillis())
 * @param isPinned Whether the entry is pinned (won't be auto-deleted)
 * @param text The clipboard text content
 */
data class ClipboardHistoryEntry(
    val id: Long,
    var timeStamp: Long,
    var isPinned: Boolean,
    val text: String
) : Comparable<ClipboardHistoryEntry> {

    /**
     * Comparator for sorting clipboard entries:
     * - Pinned items come first (or last depending on settings)
     * - Within same pinned state, sort by timestamp (most recent first)
     */
    override fun compareTo(other: ClipboardHistoryEntry): Int {
        val result = other.isPinned.compareTo(isPinned)
        if (result == 0) return other.timeStamp.compareTo(timeStamp)
        // TODO: Add setting for pinnedFirst vs pinnedLast
        // For now, pinned items always come first
        return result
    }
}
