package it.palsoftware.pastiera

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import rikka.shizuku.ShizukuRemoteProcess
import java.io.BufferedReader
import java.io.InputStreamReader

class TrackpadDebugAccessibilityService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var debugTextView: TextView? = null
    private val events = mutableListOf<String>()
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var geteventJob: Job? = null
    private val handler = Handler(Looper.getMainLooper())

    // Trackpad gesture detection
    private var touchDown = false
    private var startX = 0
    private var startY = 0
    private var currentX = 0
    private var currentY = 0
    private var startPosSet = false
    private val SWIPE_UP_THRESHOLD = 100 // Minimum Y distance for swipe up
    private val trackpadMaxX = 1400 // Approximate max X from getevent observations

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startGetevent()
        } else {
            updateDebugText("Shizuku permission denied")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        showOverlay()
        checkShizukuAndStart()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need these events
    }

    override fun onInterrupt() {
        // Required override
    }

    private fun checkShizukuAndStart() {
        try {
            if (!Shizuku.pingBinder()) {
                updateDebugText("Shizuku not running!\nPlease start Shizuku app")
                return
            }

            if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startGetevent()
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                updateDebugText("Need Shizuku permission")
                Shizuku.requestPermission(1001)
            } else {
                Shizuku.requestPermission(1001)
            }
        } catch (e: Exception) {
            updateDebugText("Shizuku error: ${e.message}")
            android.util.Log.e("TrackpadDebug", "Shizuku check failed", e)
        }
    }

    private fun startGetevent() {
        geteventJob?.cancel()
        geteventJob = serviceScope.launch {
            try {
                updateDebugText("Starting getevent...")

                // Use reflection to access Shizuku.newProcess
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                newProcessMethod.isAccessible = true

                val process = newProcessMethod.invoke(
                    null,
                    arrayOf("getevent", "-l", "/dev/input/event7"),
                    null,
                    null
                ) as Process

                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        parseTrackpadEvent(line)
                        logShizukuEvent(line)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TrackpadDebug", "getevent failed", e)
                updateDebugText("getevent error: ${e.message}\nMake sure Shizuku is running")
            }
        }
    }

    private fun parseTrackpadEvent(line: String) {
        try {
            when {
                line.contains("BTN_TOUCH") && line.contains("DOWN") -> {
                    touchDown = true
                    startPosSet = false
                    android.util.Log.d("TrackpadDebug", "Touch DOWN")
                }
                line.contains("BTN_TOUCH") && line.contains("UP") -> {
                    if (touchDown) {
                        detectGesture()
                    }
                    touchDown = false
                    startPosSet = false
                    android.util.Log.d("TrackpadDebug", "Touch UP at ($currentX, $currentY)")
                }
                line.contains("ABS_MT_POSITION_X") -> {
                    // Parse hex value from: "EV_ABS  ABS_MT_POSITION_X  00000abc"
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        val hexValue = parts.last()
                        val newX = hexValue.toIntOrNull(16)
                        if (newX != null) {
                            currentX = newX
                            if (touchDown && !startPosSet) {
                                startX = newX
                            }
                        }
                    }
                }
                line.contains("ABS_MT_POSITION_Y") -> {
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        val hexValue = parts.last()
                        val newY = hexValue.toIntOrNull(16)
                        if (newY != null) {
                            currentY = newY
                            if (touchDown && !startPosSet) {
                                startY = newY
                                startPosSet = true
                                android.util.Log.d("TrackpadDebug", "Start position set: ($startX, $startY)")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TrackpadDebug", "Error parsing event: $line", e)
        }
    }

    private fun detectGesture() {
        val deltaY = startY - currentY  // Positive = swipe up
        val deltaX = currentX - startX

        android.util.Log.d("TrackpadDebug", "Gesture: deltaX=$deltaX, deltaY=$deltaY")

        // Detect swipe up
        if (deltaY > SWIPE_UP_THRESHOLD && Math.abs(deltaX) < Math.abs(deltaY)) {
            // Determine which third of trackpad (0=left, 1=center, 2=right)
            val third = when {
                currentX < trackpadMaxX / 3 -> 0
                currentX < (trackpadMaxX * 2) / 3 -> 1
                else -> 2
            }

            android.util.Log.d("TrackpadDebug", "SWIPE UP detected in third: $third")
            handler.post {
                acceptSuggestion(third)
            }
        }
    }

    private fun acceptSuggestion(index: Int) {
        // TODO: Trigger suggestion acceptance
        android.util.Log.d("TrackpadDebug", "Accepting suggestion at index: $index")

        // For now, just show in debug overlay
        handler.post {
            events.add(">>> ACCEPT SUGGESTION #$index <<<\n")
            while (events.size > 10) {
                events.removeAt(0)
            }
            debugTextView?.text = "Trackpad (Shizuku)\n\n" + events.joinToString("")
        }
    }

    private fun logShizukuEvent(line: String) {
        if (line.contains("ABS_MT") || line.contains("BTN_TOUCH") || line.contains("SYN_REPORT")) {
            handler.post {
                val shortLine = line.take(50)
                if (events.isEmpty() || events.last() != shortLine + "\n") {
                    events.add(shortLine + "\n")
                    while (events.size > 8) {
                        events.removeAt(0)
                    }
                    debugTextView?.text = "Trackpad (Shizuku)\n\n" + events.joinToString("")
                }
            }
        }
    }

    private fun updateDebugText(text: String) {
        handler.post {
            debugTextView?.text = text
        }
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        debugTextView = TextView(this).apply {
            text = "Trackpad Debug (Shizuku)\nChecking Shizuku...\n\n"
            textSize = 8f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(0xFF00FF00.toInt())
            setPadding(8, 8, 8, 8)
        }

        val scrollView = ScrollView(this).apply {
            addView(debugTextView)
        }

        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xDD000000.toInt())
            addView(scrollView)
        }

        val params = WindowManager.LayoutParams(
            350,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 100
        }

        windowManager?.addView(overlayView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        geteventJob?.cancel()
        serviceScope.cancel()
        overlayView?.let { windowManager?.removeView(it) }
    }
}
