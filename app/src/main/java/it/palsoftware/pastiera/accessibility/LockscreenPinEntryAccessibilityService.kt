package it.palsoftware.pastiera.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Accessibility service that automatically focuses input fields when apps are opened.
 * Supports browser address bars, message input fields, and other text fields.
 */
class LockscreenPinEntry : AccessibilityService() {
    
    companion object {
        private const val TAG = "LockscreenPinEntry"
        val connected = MutableStateFlow(false)
    }
        
    override fun onServiceConnected() {
        super.onServiceConnected()
        
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        info.notificationTimeout = 100
        
        serviceInfo = info

        connected.tryEmit(true)

        Log.d(TAG, "LockscreenPinEntry Accessibility Service connected")
    }

    override fun onDestroy() {
        super.onDestroy()

        connected.tryEmit(true)
        Log.d(TAG, "LockscreenPinEntry Accessibility Service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Check if accessibility service has proper permissions
        if (!isAccessibilityServiceEnabled()) {
            Log.w(TAG, "Accessibility service not properly enabled")
            return
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "LockscreenPinEntry Accessibility Service interrupted")
    }

    /**
     * Check if the accessibility service is properly enabled with required permissions.
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val info = serviceInfo
            if (info == null) {
                Log.w(TAG, "Service info is null")
                return false
            }
            
            // Check if we have the required event types
            val hasWindowStateChanged = (info.eventTypes and AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) != 0
            if (!hasWindowStateChanged) {
                Log.w(TAG, "Missing TYPE_WINDOW_STATE_CHANGED event type")
                return false
            }
            
            // Check if we have the flag to retrieve interactive windows
            val canRetrieveWindows = (info.flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS) != 0
            if (!canRetrieveWindows) {
                Log.w(TAG, "Missing FLAG_RETRIEVE_INTERACTIVE_WINDOWS flag")
                return false
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service status", e)
            return false
        }
    }

    // --- Lockscreen PIN Entry ---

    private fun isDeviceLocked(): Boolean {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isKeyguardLocked == true
    }

    private fun clickPinButton(digit: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        try {
            // Try common SystemUI view IDs for PIN pad buttons
            val pinViewIds = listOf(
                "com.android.systemui:id/key$digit",
                "com.android.systemui:id/pin_key_$digit",
                "com.android.systemui:id/digit_$digit"
            )
            for (viewId in pinViewIds) {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
                if (nodes.isNotEmpty()) {
                    for (node in nodes) {
                        if (node.isClickable) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            node.recycle()
                            return true
                        }
                        node.recycle()
                    }
                }
            }

            // Fallback: find button by text content
            val nodes = rootNode.findAccessibilityNodeInfosByText(digit)
            for (node in nodes) {
                if (node.isClickable && node.className?.toString()?.contains("Button") != false) {
                    val nodeText = node.text?.toString()?.trim()
                    if (nodeText == digit) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        node.recycle()
                        return true
                    }
                }
                node.recycle()
            }
            return false
        } finally {
            rootNode.recycle()
        }
    }

    private fun clickPinEnter(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        try {
            val enterIds = listOf(
                "com.android.systemui:id/key_enter",
                "com.android.systemui:id/pin_key_enter",
                "com.android.systemui:id/check_button"
            )
            for (viewId in enterIds) {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
                if (nodes.isNotEmpty()) {
                    for (node in nodes) {
                        if (node.isClickable) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            node.recycle()
                            return true
                        }
                        node.recycle()
                    }
                }
            }

            return findAndClickNode(rootNode) { node ->
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                node.isClickable && (desc.contains("enter") || desc.contains("confirm") || desc.contains("ok"))
            }
        } finally {
            rootNode.recycle()
        }
    }

    private fun clickPinDelete(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        try {
            val deleteIds = listOf(
                "com.android.systemui:id/delete_button",
                "com.android.systemui:id/key_backspace",
                "com.android.systemui:id/pin_key_delete"
            )
            for (viewId in deleteIds) {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
                if (nodes.isNotEmpty()) {
                    for (node in nodes) {
                        if (node.isClickable) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            node.recycle()
                            return true
                        }
                        node.recycle()
                    }
                }
            }

            return findAndClickNode(rootNode) { node ->
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                node.isClickable && (desc.contains("delete") || desc.contains("backspace"))
            }
        } finally {
            rootNode.recycle()
        }
    }

    private fun findAndClickNode(root: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): Boolean {
        if (predicate(root)) {
            root.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            if (findAndClickNode(child, predicate)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null || event.action != KeyEvent.ACTION_DOWN) return false
        if (!SettingsManager.getLockscreenPinEntry(this)) return false
        if (!isDeviceLocked()) return false

        val keyCode = event.keyCode

        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            return clickPinEnter()
        }

        if (keyCode == KeyEvent.KEYCODE_DEL) {
            return clickPinDelete()
        }

        val digit = DeviceSpecific.getDigitFromKeyCode(keyCode) ?: return false
        return clickPinButton(digit)
    }
}
