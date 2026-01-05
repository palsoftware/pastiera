package it.palsoftware.pastiera.core

import android.view.KeyEvent
import it.palsoftware.pastiera.inputmethod.ModifierKeyHandler

enum class ShiftState { OFF, ONE_SHOT, CAPS }

/**
 * Centralizes modifier key state (Shift/Ctrl/Alt) and keeps one-shot / latch
 * bookkeeping in sync with the UI and auto-capitalization helpers.
 */
class ModifierStateController(
    private val doubleTapThreshold: Long
) {
    private val modifierKeyHandler = ModifierKeyHandler(doubleTapThreshold)
    private var lastKeyWasModifier = false
    private var lastModifierKeyCode: Int = 0

    private class ShiftStateMachine(
        private val doubleTapThreshold: Long
    ) {
        var state: ShiftState = ShiftState.OFF
            private set

        private var lastTapTime: Long = 0

        fun tap(
            now: Long = System.currentTimeMillis(),
            isConsecutiveTap: Boolean
        ): ShiftState {
            val doubleTap = isConsecutiveTap && now - lastTapTime < doubleTapThreshold
            lastTapTime = now
            state = when {
                doubleTap -> if (state == ShiftState.CAPS) ShiftState.OFF else ShiftState.CAPS
                state == ShiftState.OFF -> ShiftState.ONE_SHOT
                else -> ShiftState.OFF
            }
            return state
        }

        fun requestOneShot(): Boolean {
            if (state == ShiftState.CAPS) {
                return false
            }
            if (state != ShiftState.ONE_SHOT) {
                state = ShiftState.ONE_SHOT
                return true
            }
            return false
        }

        fun consumeOneShot(): Boolean {
            return if (state == ShiftState.ONE_SHOT) {
                state = ShiftState.OFF
                true
            } else {
                false
            }
        }

        fun setCapsLock(enabled: Boolean) {
            state = if (enabled) ShiftState.CAPS else ShiftState.OFF
        }

        fun restore(newState: ShiftState) {
            state = newState
        }

        fun reset() {
            state = ShiftState.OFF
            lastTapTime = 0
        }
    }

    private val shiftStateMachine = ShiftStateMachine(doubleTapThreshold)
    private var shiftPressedFlag = false
    private var shiftPhysicallyPressedFlag = false

    private val ctrlState = ModifierKeyHandler.CtrlState()
    private val altState = ModifierKeyHandler.AltState()

    fun registerModifierTap(keyCode: Int): Boolean {
        val isConsecutive = lastKeyWasModifier && lastModifierKeyCode == keyCode
        lastKeyWasModifier = true
        lastModifierKeyCode = keyCode
        return isConsecutive
    }

    fun registerNonModifierKey() {
        lastKeyWasModifier = false
        lastModifierKeyCode = 0
    }

    data class Snapshot(
        val capsLockEnabled: Boolean,
        val shiftPhysicallyPressed: Boolean,
        val shiftOneShot: Boolean,
        val ctrlLatchActive: Boolean,
        val ctrlPhysicallyPressed: Boolean,
        val ctrlOneShot: Boolean,
        val ctrlLatchFromNavMode: Boolean,
        val altLatchActive: Boolean,
        val altPhysicallyPressed: Boolean,
        val altOneShot: Boolean
    )

    data class LogicalState(
        val shiftState: ShiftState,
        val ctrlOneShot: Boolean,
        val ctrlLatchActive: Boolean,
        val ctrlLatchFromNavMode: Boolean,
        val altOneShot: Boolean,
        val altLatchActive: Boolean
    )

    fun captureLogicalState(): LogicalState {
        return LogicalState(
            shiftState = shiftStateMachine.state,
            ctrlOneShot = ctrlState.oneShot,
            ctrlLatchActive = ctrlState.latchActive,
            ctrlLatchFromNavMode = ctrlState.latchFromNavMode,
            altOneShot = altState.oneShot,
            altLatchActive = altState.latchActive
        )
    }

    fun restoreLogicalState(captured: LogicalState) {
        shiftStateMachine.restore(captured.shiftState)
        ctrlState.oneShot = captured.ctrlOneShot
        ctrlState.latchActive = captured.ctrlLatchActive
        ctrlState.latchFromNavMode = captured.ctrlLatchFromNavMode
        altState.oneShot = captured.altOneShot
        altState.latchActive = captured.altLatchActive
    }

    val shiftState: ShiftState
        get() = shiftStateMachine.state

    var capsLockEnabled: Boolean
        get() = shiftStateMachine.state == ShiftState.CAPS
        set(value) { shiftStateMachine.setCapsLock(value) }

    var shiftPressed: Boolean
        get() = shiftPressedFlag
        set(value) { shiftPressedFlag = value }

    var shiftPhysicallyPressed: Boolean
        get() = shiftPhysicallyPressedFlag
        set(value) { shiftPhysicallyPressedFlag = value }

    var shiftOneShot: Boolean
        get() = shiftStateMachine.state == ShiftState.ONE_SHOT
        set(value) {
            if (value) {
                shiftStateMachine.requestOneShot()
            } else {
                shiftStateMachine.consumeOneShot()
            }
        }

    var ctrlLatchActive: Boolean
        get() = ctrlState.latchActive
        set(value) { ctrlState.latchActive = value }

    var ctrlPressed: Boolean
        get() = ctrlState.pressed
        set(value) { ctrlState.pressed = value }

    var ctrlPhysicallyPressed: Boolean
        get() = ctrlState.physicallyPressed
        set(value) { ctrlState.physicallyPressed = value }

    var ctrlOneShot: Boolean
        get() = ctrlState.oneShot
        set(value) { ctrlState.oneShot = value }

    var ctrlLatchFromNavMode: Boolean
        get() = ctrlState.latchFromNavMode
        set(value) { ctrlState.latchFromNavMode = value }

    var ctrlLastReleaseTime: Long
        get() = ctrlState.lastReleaseTime
        set(value) { ctrlState.lastReleaseTime = value }

    var altLatchActive: Boolean
        get() = altState.latchActive
        set(value) { altState.latchActive = value }

    var altPressed: Boolean
        get() = altState.pressed
        set(value) { altState.pressed = value }

    var altPhysicallyPressed: Boolean
        get() = altState.physicallyPressed
        set(value) { altState.physicallyPressed = value }

    var altOneShot: Boolean
        get() = altState.oneShot
        set(value) { altState.oneShot = value }

    fun snapshot(): Snapshot {
        return Snapshot(
            capsLockEnabled = capsLockEnabled,
            shiftPhysicallyPressed = shiftPhysicallyPressed,
            shiftOneShot = shiftOneShot,
            ctrlLatchActive = ctrlLatchActive,
            ctrlPhysicallyPressed = ctrlPhysicallyPressed,
            ctrlOneShot = ctrlOneShot,
            ctrlLatchFromNavMode = ctrlLatchFromNavMode,
            altLatchActive = altLatchActive,
            altPhysicallyPressed = altPhysicallyPressed,
            altOneShot = altOneShot
        )
    }

    fun handleShiftKeyDown(keyCode: Int): ModifierKeyHandler.ModifierKeyResult {
        if (keyCode != KeyEvent.KEYCODE_SHIFT_LEFT &&
            keyCode != KeyEvent.KEYCODE_SHIFT_RIGHT
        ) {
            return ModifierKeyHandler.ModifierKeyResult()
        }

        if (shiftPressedFlag) {
            return ModifierKeyHandler.ModifierKeyResult()
        }

        shiftPhysicallyPressedFlag = true
        shiftPressedFlag = true
        val previous = shiftStateMachine.state
        val isConsecutiveTap = registerModifierTap(keyCode)
        val current = shiftStateMachine.tap(isConsecutiveTap = isConsecutiveTap)
        val changed = previous != current
        return ModifierKeyHandler.ModifierKeyResult(
            shouldUpdateStatusBar = changed,
            shouldRefreshStatusBar = changed
        )
    }

    fun handleShiftKeyUp(keyCode: Int): ModifierKeyHandler.ModifierKeyResult {
        if (keyCode != KeyEvent.KEYCODE_SHIFT_LEFT &&
            keyCode != KeyEvent.KEYCODE_SHIFT_RIGHT
        ) {
            return ModifierKeyHandler.ModifierKeyResult()
        }

        shiftPressedFlag = false
        shiftPhysicallyPressedFlag = false
        return ModifierKeyHandler.ModifierKeyResult(shouldUpdateStatusBar = true)
    }

    fun handleCtrlKeyDown(
        keyCode: Int,
        isInputViewActive: Boolean,
        onNavModeDeactivated: (() -> Unit)? = null
    ): ModifierKeyHandler.ModifierKeyResult {
        if (ctrlPressed) {
            return ModifierKeyHandler.ModifierKeyResult()
        }
        val isConsecutiveTap = registerModifierTap(keyCode)
        val result = modifierKeyHandler.handleCtrlKeyDown(
            keyCode,
            ctrlState,
            isInputViewActive,
            isConsecutiveTap = isConsecutiveTap,
            onNavModeDeactivated
        )
        ctrlPressed = true
        return result
    }

    fun handleCtrlKeyUp(keyCode: Int): ModifierKeyHandler.ModifierKeyResult {
        val result = modifierKeyHandler.handleCtrlKeyUp(keyCode, ctrlState)
        ctrlPressed = false
        return result
    }

    fun handleAltKeyDown(keyCode: Int): ModifierKeyHandler.ModifierKeyResult {
        if (altPressed) {
            return ModifierKeyHandler.ModifierKeyResult()
        }
        val isConsecutiveTap = registerModifierTap(keyCode)
        val result = modifierKeyHandler.handleAltKeyDown(
            keyCode,
            altState,
            isConsecutiveTap = isConsecutiveTap
        )
        altPressed = true
        return result
    }

    fun handleAltKeyUp(keyCode: Int): ModifierKeyHandler.ModifierKeyResult {
        val result = modifierKeyHandler.handleAltKeyUp(keyCode, altState)
        altPressed = false
        return result
    }

    /**
     * Clears Alt latch/one-shot state (used when Space should auto-disable Alt).
     * Optionally resets pressed flags if they are not reliable anymore.
     */
    fun clearAltState(resetPressedState: Boolean = false) {
        altState.latchActive = false
        altState.oneShot = false
        altState.lastReleaseTime = 0
        if (resetPressedState) {
            altState.pressed = false
            altState.physicallyPressed = false
        }
    }

    /**
     * Clears Ctrl state (latch/one-shot/nav mode flags) and, when requested,
     * resets pressed tracking to avoid leaving Ctrl active after shortcuts.
     */
    fun clearCtrlState(resetPressedState: Boolean = false) {
        ctrlState.latchActive = false
        ctrlState.oneShot = false
        ctrlState.latchFromNavMode = false
        ctrlState.lastReleaseTime = 0
        if (resetPressedState) {
            ctrlState.pressed = false
            ctrlState.physicallyPressed = false
        }
    }

    fun requestShiftOneShotFromAutoCap(): Boolean {
        return shiftStateMachine.requestOneShot()
    }

    fun consumeShiftOneShot(): Boolean {
        return shiftStateMachine.consumeOneShot()
    }

    fun resetModifiers(
        preserveNavMode: Boolean,
        onNavModeCancelled: () -> Unit
    ) {
        val savedCtrlLatch = if (preserveNavMode && (ctrlLatchActive || ctrlLatchFromNavMode)) {
            if (ctrlLatchActive) {
                ctrlLatchFromNavMode = true
                true
            } else {
                ctrlLatchFromNavMode
            }
        } else {
            false
        }

        shiftStateMachine.reset()
        shiftPressedFlag = false
        shiftPhysicallyPressedFlag = false
        lastKeyWasModifier = false
        lastModifierKeyCode = 0

        if (preserveNavMode && savedCtrlLatch) {
            ctrlLatchActive = true
        } else {
            if (ctrlLatchFromNavMode || ctrlLatchActive) {
                onNavModeCancelled()
            }
            modifierKeyHandler.resetCtrlState(ctrlState, preserveNavMode = false)
        }
        modifierKeyHandler.resetAltState(altState)
    }
}
