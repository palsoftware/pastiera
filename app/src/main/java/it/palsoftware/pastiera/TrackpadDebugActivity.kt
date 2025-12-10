package it.palsoftware.pastiera

import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.ui.theme.PastieraTheme
import kotlinx.coroutines.launch

class TrackpadDebugActivity : ComponentActivity() {
    private val events = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make this a proper overlay window
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        )

        enableEdgeToEdge()
        setContent {
            TrackpadDebugScreen(
                events = events,
                onBackPressed = { finish() }
            )
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        logMotionEvent("onGenericMotionEvent", event)
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        logMotionEvent("onTouchEvent", event)
        return super.onTouchEvent(event)
    }

    private fun logMotionEvent(source: String, event: MotionEvent) {
        val actionStr = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> "ACTION_DOWN"
            MotionEvent.ACTION_UP -> "ACTION_UP"
            MotionEvent.ACTION_MOVE -> "ACTION_MOVE"
            MotionEvent.ACTION_CANCEL -> "ACTION_CANCEL"
            MotionEvent.ACTION_HOVER_ENTER -> "ACTION_HOVER_ENTER"
            MotionEvent.ACTION_HOVER_MOVE -> "ACTION_HOVER_MOVE"
            MotionEvent.ACTION_HOVER_EXIT -> "ACTION_HOVER_EXIT"
            MotionEvent.ACTION_SCROLL -> "ACTION_SCROLL"
            MotionEvent.ACTION_BUTTON_PRESS -> "ACTION_BUTTON_PRESS"
            MotionEvent.ACTION_BUTTON_RELEASE -> "ACTION_BUTTON_RELEASE"
            else -> "ACTION_${event.actionMasked}"
        }

        val sourceStr = when (event.source) {
            InputDevice.SOURCE_TOUCHPAD -> "TOUCHPAD"
            InputDevice.SOURCE_TOUCHSCREEN -> "TOUCHSCREEN"
            InputDevice.SOURCE_MOUSE -> "MOUSE"
            InputDevice.SOURCE_STYLUS -> "STYLUS"
            InputDevice.SOURCE_TRACKBALL -> "TRACKBALL"
            else -> "SOURCE_${event.source}"
        }

        val pointerCount = event.pointerCount
        val logLines = mutableListOf<String>()

        logLines.add("[$source] $actionStr from $sourceStr")
        logLines.add("  Time: ${event.eventTime}, Pointers: $pointerCount")

        for (i in 0 until pointerCount) {
            val pointerId = event.getPointerId(i)
            val x = event.getX(i)
            val y = event.getY(i)
            val pressure = event.getPressure(i)
            val size = event.getSize(i)
            val touchMajor = event.getTouchMajor(i)
            val touchMinor = event.getTouchMinor(i)

            logLines.add("  Pointer[$i] ID=$pointerId X=${"%.2f".format(x)} Y=${"%.2f".format(y)}")
            logLines.add("    Pressure=${"%.3f".format(pressure)} Size=${"%.3f".format(size)} Major=${"%.2f".format(touchMajor)} Minor=${"%.2f".format(touchMinor)}")
        }

        // Log axis values for scroll events
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
            val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            logLines.add("  Scroll: H=${"%.2f".format(hScroll)} V=${"%.2f".format(vScroll)}")
        }

        // Log button state
        val buttons = event.buttonState
        if (buttons != 0) {
            val buttonStr = mutableListOf<String>()
            if (buttons and MotionEvent.BUTTON_PRIMARY != 0) buttonStr.add("PRIMARY")
            if (buttons and MotionEvent.BUTTON_SECONDARY != 0) buttonStr.add("SECONDARY")
            if (buttons and MotionEvent.BUTTON_TERTIARY != 0) buttonStr.add("TERTIARY")
            if (buttonStr.isNotEmpty()) {
                logLines.add("  Buttons: ${buttonStr.joinToString(", ")}")
            }
        }

        logLines.add("") // Empty line separator

        events.addAll(logLines)

        // Keep only last 500 lines
        while (events.size > 500) {
            events.removeAt(0)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackpadDebugScreen(
    events: SnapshotStateList<String>,
    onBackPressed: () -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when new events are added
    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) {
            listState.animateScrollToItem(events.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f))
            .clickable { onBackPressed() }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointerEvent = event.changes.firstOrNull()

                        if (pointerEvent != null) {
                            val eventType = when (event.type) {
                                PointerEventType.Press -> "Press"
                                PointerEventType.Release -> "Release"
                                PointerEventType.Move -> "Move"
                                PointerEventType.Enter -> "Enter"
                                PointerEventType.Exit -> "Exit"
                                PointerEventType.Scroll -> "Scroll"
                                else -> "Unknown(${event.type})"
                            }

                            val logLines = mutableListOf<String>()
                            logLines.add("[$eventType]")
                            logLines.add("  Position: X=${"%.2f".format(pointerEvent.position.x)} Y=${"%.2f".format(pointerEvent.position.y)}")
                            logLines.add("  Pressed: ${pointerEvent.pressed}")
                            logLines.add("  Pressure: ${"%.3f".format(pointerEvent.pressure)}")
                            logLines.add("  Time: ${pointerEvent.uptimeMillis}")

                            if (event.type == PointerEventType.Scroll) {
                                logLines.add("  Scroll delta: ${event.changes.first().scrollDelta}")
                            }

                            logLines.add("") // Empty line

                            events.addAll(logLines)

                            // Keep only last 500 lines
                            while (events.size > 500) {
                                events.removeAt(0)
                            }
                        }
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = "Trackpad Debug - Tap to Close",
                    color = androidx.compose.ui.graphics.Color.Green,
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = { events.clear() }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Clear",
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Events captured: ${events.size}",
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Events list
            if (events.isEmpty()) {
                Text(
                    text = "Waiting for trackpad events...\nSwipe on the trackpad to see events here.",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    items(events) { event ->
                        Text(
                            text = event,
                            color = androidx.compose.ui.graphics.Color.Green,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
