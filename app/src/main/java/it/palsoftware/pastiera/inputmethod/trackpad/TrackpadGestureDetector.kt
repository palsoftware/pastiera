package it.palsoftware.pastiera.inputmethod.trackpad

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Listens to trackpad events via Shizuku and triggers callbacks on swipe.
 * Keeps gesture logic isolated so the IME can stay lean and only react to events.
 */
class TrackpadGestureDetector(
    private val isEnabled: () -> Boolean,
    private val onSwipeUp: (third: Int) -> Unit,
    private val scope: CoroutineScope,
    private val eventDevice: String = DEFAULT_EVENT_DEVICE,
    private val trackpadMaxX: Int = DEFAULT_TRACKPAD_MAX_X,
    private val swipeUpThreshold: Int = DEFAULT_SWIPE_UP_THRESHOLD,
    private val logTag: String = DEFAULT_LOG_TAG,
    private val shizukuPing: () -> Boolean = { Shizuku.pingBinder() }
) {

    private var geteventJob: Job? = null
    private var touchDown = false
    private var startX = 0
    private var startY = 0
    private var currentX = 0
    private var currentY = 0
    private var startPosSet = false

    fun start() {
        if (!isEnabled()) {
            Log.d(logTag, "Trackpad gestures disabled in settings")
            return
        }

        if (!shizukuPing()) {
            Log.w(logTag, "Shizuku not available, trackpad gesture detection disabled")
            return
        }

        geteventJob?.cancel()
        geteventJob = scope.launch(Dispatchers.IO) {
            try {
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                newProcessMethod.isAccessible = true

                val process = newProcessMethod.invoke(
                    null,
                    arrayOf("getevent", "-l", eventDevice),
                    null,
                    null
                ) as Process

                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        parseTrackpadEvent(line)
                    }
                }
            } catch (e: Exception) {
                Log.e(logTag, "Trackpad getevent failed", e)
            }
        }
        Log.d(logTag, "Trackpad gesture detection started")
    }

    fun stop() {
        geteventJob?.cancel()
        geteventJob = null
        Log.d(logTag, "Trackpad gesture detection stopped")
    }

    private fun parseTrackpadEvent(line: String) {
        when {
            line.contains("BTN_TOUCH") && line.contains("DOWN") -> {
                touchDown = true
                startPosSet = false
            }

            line.contains("BTN_TOUCH") && line.contains("UP") -> {
                if (touchDown) {
                    detectGesture()
                }
                touchDown = false
                startPosSet = false
            }

            line.contains("ABS_MT_POSITION_X") -> {
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
                        }
                    }
                }
            }
        }
    }

    private fun detectGesture() {
        val deltaY = startY - currentY  // Positive = swipe up
        val deltaX = currentX - startX
        val absDeltaX = kotlin.math.abs(deltaX)

        // Require primarily vertical swipe: deltaY must be at least 3x larger than horizontal drift
        if (deltaY > swipeUpThreshold && absDeltaX < deltaY / 3) {
            val third = when {
                startX < trackpadMaxX / 3 -> 0  // Left
                startX < (trackpadMaxX * 2) / 3 -> 1  // Center
                else -> 2  // Right
            }

            Log.d(
                logTag,
                ">>> SWIPE UP DETECTED in third $third (deltaY=$deltaY, absDeltaX=$absDeltaX, startX=$startX) <<<"
            )
            onSwipeUp(third)
        }
    }

    companion object {
        const val DEFAULT_TRACKPAD_MAX_X = 1440
        const val DEFAULT_SWIPE_UP_THRESHOLD = 300
        const val DEFAULT_EVENT_DEVICE = "/dev/input/event7"
        const val DEFAULT_LOG_TAG = "PastieraIME"
    }
}


