package it.palsoftware.pastiera.inputmethod

import android.os.Handler
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.data.layout.LayoutMapping
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.data.layout.isRealMultiTap

data class MultiTapState(
    var lastKeyCode: Int = -1,
    var tapIndex: Int = 0,
    var expiry: Long = 0L,
    var active: Boolean = false,
    var suppressLongPress: Boolean = false,
    var lastCommitLength: Int = 0,
    var useUppercase: Boolean = false
)

/**
 * Coordinates multi-tap cycles, including timeout scheduling and long-press suppression.
 */
class MultiTapController(
    private val handler: Handler,
    private val timeoutMs: Long
) {

    val state = MultiTapState()

    private val timeoutRunnable = Runnable {
        finalizeCycle()
    }

    fun resetForNewKey(keyCode: Int) {
        if (state.active && state.lastKeyCode != keyCode) {
            finalizeCycle()
        }
    }

    fun isLongPressSuppressed(keyCode: Int): Boolean {
        return state.active && state.suppressLongPress && state.lastKeyCode == keyCode
    }

    fun handleTap(
        keyCode: Int,
        mapping: LayoutMapping,
        useUppercase: Boolean,
        inputConnection: InputConnection
    ): Boolean {
        if (!mapping.isRealMultiTap) {
            finalizeCycle()
            return false
        }

        val now = System.currentTimeMillis()
        val isSameKeyWithinWindow = state.active &&
            state.lastKeyCode == keyCode &&
            now < state.expiry

        val nextTapIndex = if (isSameKeyWithinWindow) {
            (state.tapIndex + 1) % mapping.taps.size
        } else {
            0
        }

        val activeUppercase = if (isSameKeyWithinWindow && state.active) state.useUppercase else useUppercase
        val text = LayoutMappingRepository.resolveText(mapping, activeUppercase, nextTapIndex) ?: return false

        if (isSameKeyWithinWindow) {
            // Replace previous character atomically to avoid flicker in apps like Messages.
            inputConnection.finishComposingText()
            inputConnection.beginBatchEdit()
            val deleteLength = if (state.lastCommitLength > 0) state.lastCommitLength else 1
            inputConnection.deleteSurroundingText(deleteLength, 0)
            inputConnection.commitText(text, 1)
            inputConnection.endBatchEdit()
        } else {
            // Ensure previous cycle (if any) is cleaned up before starting anew.
            if (state.active && state.lastKeyCode != keyCode) {
                finalizeCycle()
            }
            inputConnection.commitText(text, 1)
        }

        state.lastKeyCode = keyCode
        state.tapIndex = nextTapIndex
        state.expiry = now + timeoutMs
        state.active = true
        state.suppressLongPress = true
        state.lastCommitLength = text.length
        state.useUppercase = activeUppercase

        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, timeoutMs)
        return true
    }

    fun finalizeCycle() {
        state.active = false
        state.suppressLongPress = false
        state.lastCommitLength = 0
        state.useUppercase = false
        handler.removeCallbacks(timeoutRunnable)
    }

    fun cancelAll() {
        state.active = false
        state.suppressLongPress = false
        state.lastCommitLength = 0
        state.useUppercase = false
        handler.removeCallbacksAndMessages(null)
    }
}
