package it.palsoftware.pastiera.clipboard

import android.content.ContentValues
import android.content.Context
import android.os.SystemClock
import android.util.Log

/**
 * Data Access Object for clipboard history.
 * Provides cached access to clipboard entries stored in SQLite.
 */
class ClipboardDao private constructor(private val db: ClipboardDatabase) {

    interface Listener {
        fun onClipInserted(position: Int)
        fun onClipsRemoved(position: Int, count: Int)
        fun onClipMoved(oldPosition: Int, newPosition: Int)
    }

    var listener: Listener? = null

    // Track when we last cleaned old clips
    private var lastClearOldClips = 0L

    // In-memory cache loaded at start
    private val cache = mutableListOf<ClipboardHistoryEntry>().apply {
        db.readableDatabase.query(
            TABLE,
            arrayOf(COLUMN_ID, COLUMN_TIMESTAMP, COLUMN_PINNED, COLUMN_TEXT),
            null,
            null,
            null,
            null,
            "$COLUMN_PINNED DESC, $COLUMN_TIMESTAMP DESC"
        ).use {
            while (it.moveToNext()) {
                add(ClipboardHistoryEntry(
                    it.getLong(0),
                    it.getLong(1),
                    it.getInt(2) != 0,
                    it.getString(3)
                ))
            }
        }
        sort()
    }

    /**
     * Add a new clipboard entry or update existing one with same text.
     */
    fun addClip(timestamp: Long, pinned: Boolean, text: String) {
        clearOldClips()

        // Check if this exact text already exists
        val existingIndex = cache.indexOfFirst { it.text == text }
        if (existingIndex >= 0 && cache[existingIndex].timeStamp == timestamp) {
            return // Nothing to do - exact same entry
        }

        if (existingIndex >= 0) {
            // Update timestamp of existing entry
            updateTimestampAt(existingIndex, timestamp)
            return
        }

        // Insert new entry
        insertNewEntry(timestamp, pinned, text)
    }

    private fun insertNewEntry(timestamp: Long, pinned: Boolean, text: String) {
        val cv = ContentValues(3).apply {
            put(COLUMN_TIMESTAMP, timestamp)
            put(COLUMN_PINNED, pinned)
            put(COLUMN_TEXT, text)
        }
        val rowId = db.writableDatabase.insert(TABLE, null, cv)

        val entry = ClipboardHistoryEntry(rowId, timestamp, pinned, text)
        cache.add(entry)
        cache.sort()
        listener?.onClipInserted(cache.indexOf(entry))
    }

    private fun updateTimestampAt(index: Int, timestamp: Long) {
        val entry = cache[index]
        entry.timeStamp = timestamp
        cache.sort()
        listener?.onClipMoved(index, cache.indexOf(entry))

        val cv = ContentValues(1).apply {
            put(COLUMN_TIMESTAMP, timestamp)
        }
        db.writableDatabase.update(TABLE, cv, "$COLUMN_ID = ${entry.id}", null)
    }

    fun isPinned(index: Int) = cache.getOrNull(index)?.isPinned ?: false

    fun getAt(index: Int) = cache.getOrNull(index)

    fun get(id: Long) = cache.firstOrNull { it.id == id }

    fun count() = cache.size

    fun sort() = cache.sort()

    fun togglePinned(id: Long) {
        val entry = cache.firstOrNull { it.id == id } ?: return
        entry.isPinned = !entry.isPinned
        entry.timeStamp = System.currentTimeMillis()

        if (listener != null) {
            val oldPos = cache.indexOf(entry)
            cache.sort()
            val newPos = cache.indexOf(entry)
            listener?.onClipMoved(oldPos, newPos)
        } else {
            cache.sort()
        }

        val cv = ContentValues(2).apply {
            put(COLUMN_PINNED, entry.isPinned)
            put(COLUMN_TIMESTAMP, entry.timeStamp)
        }
        db.writableDatabase.update(TABLE, cv, "$COLUMN_ID = ${entry.id}", null)
    }

    fun deleteClipAt(index: Int) {
        val entry = cache.getOrNull(index) ?: return
        cache.removeAt(index)
        db.writableDatabase.delete(TABLE, "$COLUMN_ID = ${entry.id}", null)
    }

    /**
     * Remove old clipboard entries based on retention time setting.
     * @param now If true, force clear immediately; otherwise respect debounce
     */
    fun clearOldClips(now: Boolean = false, retentionMinutes: Long = 120) {
        if (listener != null) return // Never clear while clipboard UI is visible

        if (!now && lastClearOldClips > SystemClock.elapsedRealtime() - 5 * 1000) {
            return // Debounce: only clear every 5 seconds
        }

        lastClearOldClips = SystemClock.elapsedRealtime()

        if (retentionMinutes > 120) return // No retention limit

        val minTime = System.currentTimeMillis() - retentionMinutes * 60 * 1000L
        if (!cache.removeAll { it.timeStamp < minTime && !it.isPinned }) {
            return // Nothing was removed
        }

        db.writableDatabase.delete(TABLE, "$COLUMN_TIMESTAMP < $minTime AND $COLUMN_PINNED = 0", null)
    }

    fun clearNonPinned() {
        if (listener != null) {
            val indicesToRemove = mutableListOf<Int>()
            cache.forEachIndexed { idx, clip ->
                if (!clip.isPinned) indicesToRemove.add(idx)
            }
            if (indicesToRemove.isEmpty()) return

            cache.removeAll { !it.isPinned }
            listener?.onClipsRemoved(indicesToRemove[0], indicesToRemove.size)
        } else if (!cache.removeAll { !it.isPinned }) {
            return // Nothing to remove
        }

        db.writableDatabase.delete(TABLE, "$COLUMN_PINNED = 0", null)
    }

    fun clear() {
        if (count() == 0) return
        val count = count()
        cache.clear()
        listener?.onClipsRemoved(0, count)
        db.writableDatabase.delete(TABLE, null, null)
    }

    companion object {
        private const val TAG = "ClipboardDao"

        const val TABLE = "CLIPBOARD"
        private const val COLUMN_ID = "ID"
        private const val COLUMN_TIMESTAMP = "TIMESTAMP"
        private const val COLUMN_PINNED = "PINNED"
        private const val COLUMN_TEXT = "TEXT"

        const val CREATE_TABLE = """
            CREATE TABLE $TABLE (
                $COLUMN_ID INTEGER PRIMARY KEY,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_PINNED TINYINT NOT NULL,
                $COLUMN_TEXT TEXT
            )
        """

        @Volatile
        private var instance: ClipboardDao? = null

        /**
         * Get the singleton instance, or create it if needed.
         * Returns null if instance can't be created (e.g. device locked).
         */
        fun getInstance(context: Context): ClipboardDao? {
            return instance ?: synchronized(this) {
                instance ?: try {
                    ClipboardDao(ClipboardDatabase.getInstance(context)).also {
                        instance = it
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to create ClipboardDao", e)
                    null
                }
            }
        }
    }
}
