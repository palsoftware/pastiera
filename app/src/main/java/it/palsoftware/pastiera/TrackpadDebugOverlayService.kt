package it.palsoftware.pastiera

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class TrackpadDebugOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var debugTextView: TextView? = null
    private val events = mutableListOf<String>()

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(base))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // Check if we have overlay permission
        if (!android.provider.Settings.canDrawOverlays(this)) {
            android.util.Log.e("TrackpadDebug", "No overlay permission - stopping service")
            stopSelf()
            return
        }

        showOverlay()
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        debugTextView = TextView(this).apply {
            text = getString(R.string.trackpad_debug_title) + "\n" +
                getString(R.string.trackpad_debug_waiting) + "\n\n"
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(0xFF00FF00.toInt())
            setPadding(8, 8, 8, 8)
        }

        val scrollView = ScrollView(this).apply {
            addView(debugTextView)
        }

        overlayView = object : LinearLayout(this) {
            override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
                logEvent(ev)
                return super.dispatchGenericMotionEvent(ev)
            }

            override fun onTouchEvent(ev: MotionEvent): Boolean {
                logEvent(ev)
                return super.onTouchEvent(ev)
            }

            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                logEvent(ev)
                return super.dispatchTouchEvent(ev)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xDD000000.toInt())
            addView(scrollView)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager?.addView(overlayView, params)
    }

    private fun logEvent(event: MotionEvent) {
        val actionStr = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_HOVER_MOVE -> "HOVER"
            MotionEvent.ACTION_SCROLL -> "SCROLL"
            else -> "ACT_${event.actionMasked}"
        }

        val sourceStr = when (event.source) {
            android.view.InputDevice.SOURCE_TOUCHPAD -> "TOUCHPAD"
            android.view.InputDevice.SOURCE_TOUCHSCREEN -> "TOUCHSCREEN"
            android.view.InputDevice.SOURCE_MOUSE -> "MOUSE"
            else -> "SRC_${event.source}"
        }

        val line = "[$actionStr/$sourceStr] X=${"%.1f".format(event.x)} Y=${"%.1f".format(event.y)}\n"
        events.add(line)

        while (events.size > 50) {
            events.removeAt(0)
        }

        debugTextView?.post {
            debugTextView?.text = getString(R.string.trackpad_debug_overlay_events, events.size) +
                events.joinToString("")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager?.removeView(it) }
    }
}
