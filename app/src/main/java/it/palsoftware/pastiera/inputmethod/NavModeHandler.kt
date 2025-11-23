package it.palsoftware.pastiera.inputmethod

import android.util.Log

/**
 * Handles nav mode: double-tap Ctrl to enable/disable Ctrl latch
 * even when no text field is focused.
 */
object NavModeHandler {
    private const val TAG = "NavModeHandler"
    private const val DOUBLE_TAP_THRESHOLD = 500L // milliseconds
    
    /**
     * Handles Ctrl key down while in nav mode.
     * @param keyCode The keycode of the pressed Ctrl key
     * @param ctrlPressed Whether Ctrl is already pressed
     * @param ctrlLatchActive Whether Ctrl latch is currently active
     * @param lastCtrlReleaseTime Timestamp of the last Ctrl release
     * @param isConsecutiveTap True when the previous key event was the same Ctrl key
     * @return Pair<Boolean, NavModeResult> where the Boolean indicates if the event is consumed
     *         and NavModeResult contains state changes to apply
     */
    fun handleCtrlKeyDown(
        keyCode: Int,
        ctrlPressed: Boolean,
        ctrlLatchActive: Boolean,
        lastCtrlReleaseTime: Long,
        isConsecutiveTap: Boolean
    ): Pair<Boolean, NavModeResult> {
        if (ctrlPressed) {
            // Ctrl already pressed, ignore
            return Pair(false, NavModeResult())
        }
        
        val currentTime = System.currentTimeMillis()
        val allowDoubleTap = isConsecutiveTap
        
        if (ctrlLatchActive) {
            // If Ctrl latch is active, a single tap deactivates it
            Log.d(TAG, "Nav mode: Ctrl latch deactivated")
            return Pair(true, NavModeResult(
                ctrlLatchActive = false,
                ctrlPhysicallyPressed = true,
                shouldHideKeyboard = true,
                lastCtrlReleaseTime = 0
            ))
        } else {
            // Check for double tap
            if (allowDoubleTap && currentTime - lastCtrlReleaseTime < DOUBLE_TAP_THRESHOLD && lastCtrlReleaseTime > 0) {
                // Double tap detected - activate Ctrl latch and show the keyboard
                Log.d(TAG, "Nav mode: Ctrl latch activated with double tap")
                return Pair(true, NavModeResult(
                    ctrlLatchActive = true,
                    ctrlPhysicallyPressed = true,
                    shouldShowKeyboard = true,
                    lastCtrlReleaseTime = 0
                ))
            } else {
                // Single tap - do nothing, wait for the second tap
                Log.d(TAG, "Nav mode: first tap on Ctrl, waiting for second tap")
                return Pair(true, NavModeResult(
                    ctrlPhysicallyPressed = true,
                    lastCtrlReleaseTime = lastCtrlReleaseTime
                ))
            }
        }
    }
    
    /**
     * Handles Ctrl key up in nav mode.
     */
    fun handleCtrlKeyUp(): NavModeResult {
        return NavModeResult(
            ctrlPressed = false,
            ctrlPhysicallyPressed = false,
            lastCtrlReleaseTime = System.currentTimeMillis()
        )
    }
    
    /**
     * Result of nav mode operations.
     * Contains the state updates that should be applied to the service.
     */
    data class NavModeResult(
        val ctrlLatchActive: Boolean? = null,
        val ctrlPhysicallyPressed: Boolean? = null,
        val ctrlPressed: Boolean? = null,
        val lastCtrlReleaseTime: Long? = null,
        val shouldShowKeyboard: Boolean = false,
        val shouldHideKeyboard: Boolean = false
    )
}
