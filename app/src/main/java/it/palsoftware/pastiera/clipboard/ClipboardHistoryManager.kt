package it.palsoftware.pastiera.clipboard

import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.TextUtils
import android.util.Log
import androidx.core.content.ContextCompat
import it.palsoftware.pastiera.SettingsManager

/**
 * Manages clipboard history tracking and provides popup display.
 * Listens to system clipboard changes and stores them in a database.
 */
class ClipboardHistoryManager internal constructor(
    private val context: Context,
    private val accessPolicy: ClipboardHistoryAccessPolicy,
    private val beforeTextCommit: (android.view.inputmethod.InputConnection?) -> Unit = {}
) : ClipboardManager.OnPrimaryClipChangedListener {

    constructor(
        context: Context,
        beforeTextCommit: (android.view.inputmethod.InputConnection?) -> Unit = {}
    ) : this(context, SystemClipboardHistoryAccessPolicy(context), beforeTextCommit)

    private lateinit var clipboardManager: ClipboardManager
    private var clipboardDao: ClipboardDao? = null
    private var isEnabled: Boolean = true // TODO: Add setting
    private var deviceStateReceiverRegistered = false
    private var screenOffReceived = false
    private var lastHistoryAccessible: Boolean? = null
    private val accessStateListeners = linkedSetOf<(Boolean) -> Unit>()
    private val deviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            screenOffReceived = intent?.action == Intent.ACTION_SCREEN_OFF
            refreshAccessState()
        }
    }

    fun onCreate() {
        clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(this)
        clipboardDao = ClipboardDao.getInstance(context)
        registerDeviceStateReceiver()
        lastHistoryAccessible = isHistoryAccessible()

        // Check if history is enabled
        isEnabled = getClipboardHistoryEnabled()

        if (isEnabled) {
            fetchPrimaryClip()
        }
    }

    fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(this)
        if (deviceStateReceiverRegistered) {
            runCatching { context.unregisterReceiver(deviceStateReceiver) }
            deviceStateReceiverRegistered = false
        }
        accessStateListeners.clear()
    }

    override fun onPrimaryClipChanged() {
        if (!isEnabled || !isHistoryAccessible()) return
        fetchPrimaryClip()
    }

    private fun fetchPrimaryClip() {
        if (!isHistoryAccessible()) return
        val clipData = clipboardManager.primaryClip ?: return
        if (clipData.itemCount == 0 || clipData.description?.hasMimeType("text/*") == false) {
            return
        }

        clipData.getItemAt(0)?.let { clipItem ->
            val timeStamp = System.currentTimeMillis() // TODO: Get actual clip timestamp if available
            val content = clipItem.coerceToText(context)
            if (TextUtils.isEmpty(content)) return
            if (!isHistoryAccessible()) return

            val retentionMinutes = getClipboardRetentionTime()
            clipboardDao?.addClip(timeStamp, false, content.toString(), retentionMinutes)
        }
    }

    fun toggleClipPinned(id: Long) {
        if (!isHistoryAccessible()) return
        clipboardDao?.togglePinned(id)
    }

    fun clearHistory() {
        if (!isHistoryAccessible()) return
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

    fun canRemove(index: Int) = isHistoryAccessible() && clipboardDao?.isPinned(index) == false

    fun removeEntry(index: Int, force: Boolean = false) {
        if (!isHistoryAccessible()) return
        val entry = getHistoryEntry(index) ?: return

        // For UX: allow deleting pinned entries when explicitly requested (force=true)
        if (entry.isPinned && !force) return

        if (entry.isPinned && force) {
            // Unpin first so DAO allows removal, then delete using the updated position
            toggleClipPinned(entry.id)
            val updatedIndex = (0 until getHistorySize()).firstOrNull { idx ->
                getHistoryEntry(idx)?.id == entry.id
            }
            updatedIndex?.let { clipboardDao?.deleteClipAt(it) }
        } else {
            clipboardDao?.deleteClipAt(index)
        }
    }

    fun sortHistoryEntries() {
        if (!isHistoryAccessible()) return
        clipboardDao?.sort()
    }

    fun prepareClipboardHistory() {
        if (!isHistoryAccessible()) return
        // Clear old clips before showing history
        val retentionMinutes = getClipboardRetentionTime()
        clipboardDao?.clearOldClips(true, retentionMinutes)
    }

    fun getHistorySize() = if (isHistoryAccessible()) clipboardDao?.count() ?: 0 else 0

    fun getHistoryEntry(position: Int) = if (isHistoryAccessible()) clipboardDao?.getAt(position) else null

    fun getHistoryEntryContent(id: Long) = if (isHistoryAccessible()) clipboardDao?.get(id) else null

    fun setHistoryChangeListener(listener: ClipboardDao.Listener?) {
        clipboardDao?.listener = listener
    }

    fun isHistoryAccessible(): Boolean = !screenOffReceived && accessPolicy.isHistoryAccessible()

    fun addAccessStateListener(listener: (Boolean) -> Unit) {
        accessStateListeners += listener
    }

    fun removeAccessStateListener(listener: (Boolean) -> Unit) {
        accessStateListeners -= listener
    }

    internal fun refreshAccessState() {
        val accessible = isHistoryAccessible()
        if (lastHistoryAccessible == accessible) return
        lastHistoryAccessible = accessible
        accessStateListeners.toList().forEach { listener -> listener(accessible) }
    }

    /**
     * Paste the given text into the input connection.
     */
    fun pasteText(text: String, inputConnection: android.view.inputmethod.InputConnection?) {
        if (!isHistoryAccessible()) return
        beforeTextCommit(inputConnection)
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

        return ClipboardHistoryPopupView(context, this).apply {
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
                index?.let { removeEntry(it, force = true) }
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

    private fun registerDeviceStateReceiver() {
        if (deviceStateReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_USER_UNLOCKED)
        }
        ContextCompat.registerReceiver(
            context,
            deviceStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        deviceStateReceiverRegistered = true
    }

    companion object {
        private const val TAG = "ClipboardHistoryManager"
    }
}
