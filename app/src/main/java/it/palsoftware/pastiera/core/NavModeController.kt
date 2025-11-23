package it.palsoftware.pastiera.core

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import it.palsoftware.pastiera.inputmethod.NavModeHandler
import it.palsoftware.pastiera.inputmethod.NotificationHelper

/**
 * Encapsulates nav-mode specific state and routing logic (Ctrl latch double-tap,
 * keycode remapping, notification lifecycle).
 */
class NavModeController(
    private val context: Context,
    private val modifierStateController: ModifierStateController
) {

    companion object {
        private const val TAG = "NavModeController"
    }

    fun isNavModeActive(): Boolean {
        return modifierStateController.ctrlLatchActive && modifierStateController.ctrlLatchFromNavMode
    }

    fun hasCtrlLatchFromNavMode(): Boolean = modifierStateController.ctrlLatchFromNavMode

    fun isNavModeKey(keyCode: Int): Boolean {
        val navModeEnabled = SettingsManager.getNavModeEnabled(context)
        val navModeActive = isNavModeActive()
        if (!navModeEnabled && !navModeActive) {
            return false
        }
        val isCtrlKey = keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT
        return isCtrlKey || navModeActive
    }

    fun handleNavModeKey(
        keyCode: Int,
        event: KeyEvent?,
        isKeyDown: Boolean,
        ctrlKeyMap: Map<Int, KeyMappingLoader.CtrlMapping>,
        inputConnectionProvider: () -> InputConnection?
    ): Boolean {
        val navModeEnabled = SettingsManager.getNavModeEnabled(context)
        val navModeActive = isNavModeActive()
        if (!navModeEnabled && !navModeActive) {
            return false
        }

        val isCtrlKey = keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT
        if (isCtrlKey) {
            return if (isKeyDown) {
                val isConsecutiveTap = modifierStateController.registerModifierTap(keyCode)
                val (shouldConsume, result) = NavModeHandler.handleCtrlKeyDown(
                    keyCode,
                    modifierStateController.ctrlPressed,
                    modifierStateController.ctrlLatchActive,
                    modifierStateController.ctrlLastReleaseTime,
                    isConsecutiveTap
                )
                applyNavModeResult(result)
                if (shouldConsume) {
                    modifierStateController.ctrlPressed = true
                }
                shouldConsume
            } else {
                val result = NavModeHandler.handleCtrlKeyUp()
                applyNavModeResult(result)
                modifierStateController.ctrlPressed = false
                true
            }
        }

        if (!isKeyDown) {
            return isNavModeActive()
        }

        if (!isNavModeActive()) {
            return false
        }

        val inputConnection = inputConnectionProvider() ?: return false

        val mappedKeyCode = when {
            keyCode == KeyEvent.KEYCODE_ENTER -> KeyEvent.KEYCODE_DPAD_CENTER
            else -> {
                val ctrlMapping = ctrlKeyMap[keyCode] ?: return false
                if (ctrlMapping.type != "keycode") {
                    return false
                }
                when (ctrlMapping.value) {
                    "DPAD_UP" -> KeyEvent.KEYCODE_DPAD_UP
                    "DPAD_DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                    "DPAD_LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                    "DPAD_RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                    "DPAD_CENTER" -> KeyEvent.KEYCODE_DPAD_CENTER
                    "TAB" -> KeyEvent.KEYCODE_TAB
                    "PAGE_UP" -> KeyEvent.KEYCODE_PAGE_UP
                    "PAGE_DOWN" -> KeyEvent.KEYCODE_PAGE_DOWN
                    "ESCAPE" -> KeyEvent.KEYCODE_ESCAPE
                    else -> null
                }
            }
        } ?: return false

        return sendMappedKey(mappedKeyCode, event, inputConnection)
    }

    fun cancelNotification() {
        NotificationHelper.cancelNavModeNotification(context)
    }

    fun exitNavMode() {
        if (modifierStateController.ctrlLatchFromNavMode || modifierStateController.ctrlLatchActive) {
            modifierStateController.ctrlLatchFromNavMode = false
            modifierStateController.ctrlLatchActive = false
            NotificationHelper.cancelNavModeNotification(context)
        }
    }
    
    /**
     * Riattiva nav mode (usato quando power shortcut termina e nav mode era attivo prima).
     * Riutilizza applyNavModeResult per mantenere la logica consistente.
     */
    fun enterNavMode() {
        val navModeEnabled = SettingsManager.getNavModeEnabled(context)
        if (navModeEnabled && !isNavModeActive()) {
            // Riutilizza la logica esistente per attivare nav mode
            applyNavModeResult(
                NavModeHandler.NavModeResult(
                    ctrlLatchActive = true
                )
            )
            Log.d(TAG, "Nav mode riattivato")
        }
    }

    private fun applyNavModeResult(result: NavModeHandler.NavModeResult) {
        result.ctrlLatchActive?.let { latchActive ->
            modifierStateController.ctrlLatchActive = latchActive
            if (latchActive) {
                modifierStateController.ctrlLatchFromNavMode = true
                NotificationHelper.showNavModeActivatedNotification(context)
            } else if (modifierStateController.ctrlLatchFromNavMode) {
                modifierStateController.ctrlLatchFromNavMode = false
                NotificationHelper.cancelNavModeNotification(context)
            }
        }
        result.ctrlPhysicallyPressed?.let { modifierStateController.ctrlPhysicallyPressed = it }
        result.ctrlPressed?.let { modifierStateController.ctrlPressed = it }
        result.lastCtrlReleaseTime?.let { modifierStateController.ctrlLastReleaseTime = it }
    }

    private fun sendMappedKey(
        mappedKeyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection
    ): Boolean {
        val downTime = event?.downTime ?: SystemClock.uptimeMillis()
        val eventTime = event?.eventTime ?: downTime
        val metaState = event?.metaState ?: 0

        val downEvent = KeyEvent(
            downTime,
            eventTime,
            KeyEvent.ACTION_DOWN,
            mappedKeyCode,
            0,
            metaState
        )
        val upEvent = KeyEvent(
            downTime,
            eventTime,
            KeyEvent.ACTION_UP,
            mappedKeyCode,
            0,
            metaState
        )
        inputConnection.sendKeyEvent(downEvent)
        inputConnection.sendKeyEvent(upEvent)
        Log.d(TAG, "Nav mode: dispatched keycode $mappedKeyCode")
        return true
    }
}

