package it.palsoftware.pastiera.clipboard

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager

internal fun interface ClipboardHistoryAccessPolicy {
    fun isHistoryAccessible(): Boolean
}

internal class SystemClipboardHistoryAccessPolicy(context: Context) : ClipboardHistoryAccessPolicy {
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    override fun isHistoryAccessible(): Boolean {
        val interactive = powerManager?.isInteractive ?: false
        val deviceLocked = keyguardManager?.isDeviceLocked ?: true
        return interactive && !deviceLocked
    }
}
