package it.palsoftware.pastiera.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.text.TextUtils
import android.util.Log
import it.palsoftware.pastiera.SettingsManager

/**
 * Manages clipboard history tracking and provides popup display.
 * Listens to system clipboard changes and stores them in a database.
 */
class ClipboardHistoryManager(
    private val context: Context
) : ClipboardManager.OnPrimaryClipChangedListener {

    private lateinit var clipboardManager: ClipboardManager
    private var clipboardDao: ClipboardDao? = null
    private var isEnabled: Boolean = true // TODO: Add setting

    fun onCreate() {
        clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(this)
        clipboardDao = ClipboardDao.getInstance(context)

        // Check if history is enabled
        isEnabled = getClipboardHistoryEnabled()

        if (isEnabled) {
            fetchPrimaryClip()
        }
    }

    fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(this)
    }

    override fun onPrimaryClipChanged() {
        if (!isEnabled) return
        fetchPrimaryClip()
    }

    private fun fetchPrimaryClip() {
        val clipData = clipboardManager.primaryClip ?: return
        if (clipData.itemCount == 0 || clipData.description?.hasMimeType("text/*") == false) {
            return
        }

        clipData.getItemAt(0)?.let { clipItem ->
            val timeStamp = System.currentTimeMillis() // TODO: Get actual clip timestamp if available
            val content = clipItem.coerceToText(context)
            if (TextUtils.isEmpty(content)) return

            clipboardDao?.addClip(timeStamp, false, content.toString())
        }
    }

    fun toggleClipPinned(id: Long) {
        clipboardDao?.togglePinned(id)
    }

    fun clearHistory() {
        clipboardDao?.clearNonPinned()
        try {
            // Clear system clipboard (API 28+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                clipboardManager.clearPrimaryClip()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear system clipboard", e)
        }
    }

    fun canRemove(index: Int) = clipboardDao?.isPinned(index) == false

    fun removeEntry(index: Int) {
        if (canRemove(index)) {
            clipboardDao?.deleteClipAt(index)
        }
    }

    fun sortHistoryEntries() {
        clipboardDao?.sort()
    }

    fun prepareClipboardHistory() {
        // Clear old clips before showing history
        val retentionMinutes = getClipboardRetentionTime()
        clipboardDao?.clearOldClips(true, retentionMinutes)
    }

    fun getHistorySize() = clipboardDao?.count() ?: 0

    fun getHistoryEntry(position: Int) = clipboardDao?.getAt(position)

    fun getHistoryEntryContent(id: Long) = clipboardDao?.get(id)

    fun setHistoryChangeListener(listener: ClipboardDao.Listener?) {
        clipboardDao?.listener = listener
    }

    /**
     * Paste the given text into the input connection.
     */
    fun pasteText(text: String, inputConnection: android.view.inputmethod.InputConnection?) {
        inputConnection?.commitText(text, 1)
    }

    /**
     * Shows the clipboard history popup above the keyboard.
     * Returns the popup view that was created.
     */
    fun showClipboardHistoryPopup(
        inputConnection: android.view.inputmethod.InputConnection?,
        onDismiss: () -> Unit
    ): ClipboardHistoryPopupView? {
        if (!isEnabled) return null

        prepareClipboardHistory()

        return ClipboardHistoryPopupView(context).apply {
            setOnItemClickListener { entry ->
                pasteText(entry.text, inputConnection)
                dismiss()
                onDismiss()
            }
            setOnPinClickListener { entry ->
                toggleClipPinned(entry.id)
            }
            setOnDeleteClickListener { entry ->
                val index = getHistoryEntry(0)?.let {
                    (0 until getHistorySize()).find { idx ->
                        getHistoryEntry(idx)?.id == entry.id
                    }
                }
                index?.let { removeEntry(it) }
            }
            setOnClearAllClickListener {
                clearHistory()
            }
            show()
        }
    }

    private fun getClipboardHistoryEnabled(): Boolean {
        return SettingsManager.getClipboardHistoryEnabled(context)
    }

    private fun getClipboardRetentionTime(): Long {
        return SettingsManager.getClipboardRetentionTime(context)
    }

    companion object {
        private const val TAG = "ClipboardHistoryManager"
    }
}
