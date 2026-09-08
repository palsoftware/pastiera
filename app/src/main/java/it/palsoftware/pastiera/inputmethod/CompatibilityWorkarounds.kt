package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent

/**
 * Central registry for narrow app- and device-specific compatibility workarounds.
 *
 * Keep targeted exceptions here so their scope and removal conditions remain discoverable.
 */
internal object CompatibilityWorkarounds {
    const val TELEGRAM_PACKAGE_NAME = "org.telegram.messenger"
    const val CLICKS_POWER_PROFILE_ID = "clicks_power"

    /**
     * Clicks Power emits some SYM chords as a synthetic Shift+key macro in one input frame.
     * That synthetic Shift must modify the character without acting as a logical Shift tap.
     */
    fun isClicksPowerSyntheticShiftChord(
        profileId: String,
        event: KeyEvent?,
        activeShiftDownTimes: Sequence<Long>
    ): Boolean {
        if (
            profileId != CLICKS_POWER_PROFILE_ID ||
            event == null ||
            event.repeatCount > 0 ||
            !event.isShiftPressed ||
            event.eventTime <= 0L
        ) {
            return false
        }
        return activeShiftDownTimes.any { it == event.eventTime }
    }
}

/** Drops implausibly fast repeated Shift taps reported by the Clicks Power keyboard. */
internal class ClicksPowerShiftTapFilter {
    data class SuppressedEvent(
        val deltaMs: Long,
        val identity: String
    ) {
        fun debugOutput(): String =
            "clicks_shift_bounce:ignored:delta=${deltaMs}ms:threshold=${MINIMUM_TAP_INTERVAL_MS}ms:id=$identity"
    }

    private data class KeyIdentity(
        val deviceId: Int,
        val keyCode: Int
    ) {
        override fun toString(): String = "$deviceId:$keyCode"
    }

    private val lastAcceptedDownTimes = mutableMapOf<KeyIdentity, Long>()
    private val activeAcceptedDowns = mutableSetOf<KeyIdentity>()
    private val suppressedKeyUps = mutableMapOf<KeyIdentity, SuppressedEvent>()

    fun shouldConsumeKeyDown(
        isClicksPowerKeyboard: Boolean,
        keyCode: Int,
        event: KeyEvent?
    ): SuppressedEvent? {
        if (!isClicksPowerKeyboard || event == null || event.repeatCount > 0) return null

        if (!isShiftKey(keyCode)) {
            resetTapHistory(event.deviceId)
            return null
        }

        val identity = KeyIdentity(event.deviceId, keyCode)
        val now = event.eventTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        val previous = lastAcceptedDownTimes[identity]
        if (previous != null) {
            val delta = now - previous
            if (delta in 0 until MINIMUM_TAP_INTERVAL_MS) {
                return SuppressedEvent(delta, identity.toString()).also { suppressed ->
                    if (identity !in activeAcceptedDowns) {
                        suppressedKeyUps[identity] = suppressed
                    }
                }
            }
        }

        lastAcceptedDownTimes[identity] = now
        activeAcceptedDowns += identity
        return null
    }

    fun shouldConsumeKeyUp(keyCode: Int, event: KeyEvent?): SuppressedEvent? {
        if (event == null || !isShiftKey(keyCode)) return null

        val identity = KeyIdentity(event.deviceId, keyCode)
        suppressedKeyUps.remove(identity)?.let { return it }
        activeAcceptedDowns -= identity
        return null
    }

    fun resetDevice(deviceId: Int) {
        lastAcceptedDownTimes.keys.removeAll { it.deviceId == deviceId }
        activeAcceptedDowns.removeAll { it.deviceId == deviceId }
        suppressedKeyUps.keys.removeAll { it.deviceId == deviceId }
    }

    fun reset() {
        lastAcceptedDownTimes.clear()
        activeAcceptedDowns.clear()
        suppressedKeyUps.clear()
    }

    private fun resetTapHistory(deviceId: Int) {
        lastAcceptedDownTimes.keys.removeAll { it.deviceId == deviceId }
    }

    private fun isShiftKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT

    private companion object {
        const val MINIMUM_TAP_INTERVAL_MS = 80L
    }
}
