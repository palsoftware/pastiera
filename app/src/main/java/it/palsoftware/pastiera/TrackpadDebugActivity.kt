package it.palsoftware.pastiera

import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.inputmethod.trackpad.TrackpadAxisRange
import it.palsoftware.pastiera.inputmethod.trackpad.TrackpadCoordinateMapper
import it.palsoftware.pastiera.ui.theme.PastieraTheme

class TrackpadDebugActivity : LocalizedComponentActivity() {
    private val events = mutableStateListOf<String>()
    private var axisState by mutableStateOf(TrackpadDebugAxisState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PastieraTheme {
                TrackpadDebugScreen(
                    events = events,
                    axisState = axisState,
                    onBackPressed = { finish() }
                )
            }
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        android.util.Log.d("TrackpadDebug", "dispatchGenericMotionEvent: action=${event.actionMasked} source=${event.source}")
        updateAxisState(event)
        logMotionEvent("dispatchGenericMotionEvent", event)
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        android.util.Log.d("TrackpadDebug", "onTouchEvent: action=${event.actionMasked}")
        logMotionEvent("onTouchEvent", event)
        return super.onTouchEvent(event)
    }

    private fun updateAxisState(event: MotionEvent) {
        if (event.pointerCount == 0) return
        val device = InputDevice.getDevice(event.deviceId)
        val detectedXRange = motionRange(device, event, MotionEvent.AXIS_X)
        val detectedYRange = motionRange(device, event, MotionEvent.AXIS_Y)
        val xRange = detectedXRange
            ?: TrackpadAxisRange(0f, resources.displayMetrics.widthPixels.toFloat())
        val yRange = detectedYRange
            ?: TrackpadAxisRange(0f, resources.displayMetrics.heightPixels.toFloat())
        val action = event.actionMasked
        axisState = TrackpadDebugAxisState(
            x = event.x,
            y = event.y,
            xRange = xRange,
            yRange = yRange,
            active = action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL,
            source = device?.name.orEmpty().ifBlank { sourceName(event) },
            rangeSource = if (
                detectedXRange != null && detectedYRange != null
            ) {
                getString(R.string.trackpad_debug_range_motion)
            } else {
                getString(R.string.trackpad_debug_range_display)
            }
        )
    }

    private fun motionRange(
        device: InputDevice?,
        event: MotionEvent,
        axis: Int
    ): TrackpadAxisRange? {
        val motionRange = device?.getMotionRange(axis, event.source)
            ?: device?.getMotionRange(axis)
        return motionRange
            ?.let { TrackpadAxisRange(it.min, it.max) }
            ?.takeIf { it.isValid }
    }

    private fun sourceName(event: MotionEvent): String = when {
        event.isFromSource(InputDevice.SOURCE_TOUCHPAD) -> "TOUCHPAD"
        event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) -> "TOUCHSCREEN"
        event.isFromSource(InputDevice.SOURCE_MOUSE) -> "MOUSE"
        else -> "SOURCE_${event.source}"
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
    axisState: TrackpadDebugAxisState,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showYAxis by rememberSaveable { mutableStateOf(false) }

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
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.trackpad_debug_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = androidx.compose.ui.graphics.Color.Green
                )
                Row {
                    IconButton(onClick = { events.clear() }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.clear),
                            tint = androidx.compose.ui.graphics.Color.Green
                        )
                    }
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                            tint = androidx.compose.ui.graphics.Color.Green
                        )
                    }
                }
            }

            // Event counter
            Text(
                text = stringResource(R.string.trackpad_debug_events_captured, events.size),
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color.Green
            )

            TrackpadCoordinatePanel(
                state = axisState,
                showYAxis = showYAxis,
                onShowYAxisChanged = { showYAxis = it },
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Events list
            if (events.isEmpty()) {
                Text(
                    text = stringResource(R.string.trackpad_debug_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color.Green
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(events) { event ->
                        Text(
                            text = event,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = androidx.compose.ui.graphics.Color.Green,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Transparent overlay to capture all pointer/touch events
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointerEvent = event.changes.firstOrNull()

                            if (pointerEvent != null) {
                                val eventType = when (event.type) {
                                    PointerEventType.Press -> context.getString(R.string.trackpad_debug_event_press)
                                    PointerEventType.Release -> context.getString(R.string.trackpad_debug_event_release)
                                    PointerEventType.Move -> context.getString(R.string.trackpad_debug_event_move)
                                    PointerEventType.Enter -> context.getString(R.string.trackpad_debug_event_enter)
                                    PointerEventType.Exit -> context.getString(R.string.trackpad_debug_event_exit)
                                    PointerEventType.Scroll -> context.getString(R.string.trackpad_debug_event_scroll)
                                    else -> context.getString(R.string.trackpad_debug_event_unknown, event.type)
                                }

                                val logLines = mutableListOf<String>()
                                logLines.add("[$eventType]")
                                logLines.add(
                                    context.getString(
                                        R.string.trackpad_debug_position,
                                        pointerEvent.position.x,
                                        pointerEvent.position.y
                                    )
                                )
                                logLines.add(context.getString(R.string.trackpad_debug_pressed, pointerEvent.pressed))
                                logLines.add(context.getString(R.string.trackpad_debug_pressure, pointerEvent.pressure))
                                logLines.add(context.getString(R.string.trackpad_debug_time, pointerEvent.uptimeMillis))

                                if (event.type == PointerEventType.Scroll) {
                                    logLines.add(
                                        context.getString(
                                            R.string.trackpad_debug_scroll_delta,
                                            event.changes.first().scrollDelta
                                        )
                                    )
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
        )
    }
}

data class TrackpadDebugAxisState(
    val x: Float = 0f,
    val y: Float = 0f,
    val xRange: TrackpadAxisRange = TrackpadAxisRange(0f, 1f),
    val yRange: TrackpadAxisRange = TrackpadAxisRange(0f, 1f),
    val active: Boolean = false,
    val source: String = "—",
    val rangeSource: String = "—"
)

@Composable
private fun TrackpadCoordinatePanel(
    state: TrackpadDebugAxisState,
    showYAxis: Boolean,
    onShowYAxisChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f),
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            androidx.compose.ui.graphics.Color.Green.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.trackpad_debug_coordinates_title),
                        color = androidx.compose.ui.graphics.Color.Green,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${state.source} · ${state.rangeSource}",
                        color = androidx.compose.ui.graphics.Color.Green.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.trackpad_debug_show_y_axis),
                        color = androidx.compose.ui.graphics.Color.Green,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Switch(
                        checked = showYAxis,
                        onCheckedChange = onShowYAxisChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = androidx.compose.ui.graphics.Color.Black,
                            checkedTrackColor = androidx.compose.ui.graphics.Color.Green
                        )
                    )
                }
            }

            TrackpadAxisBar(
                label = "X",
                value = state.x,
                range = state.xRange,
                active = state.active,
                showThirds = true
            )
            if (showYAxis) {
                TrackpadAxisBar(
                    label = "Y",
                    value = state.y,
                    range = state.yRange,
                    active = state.active,
                    showThirds = false
                )
            }
        }
    }
}

@Composable
private fun TrackpadAxisBar(
    label: String,
    value: Float,
    range: TrackpadAxisRange,
    active: Boolean,
    showThirds: Boolean
) {
    val green = androidx.compose.ui.graphics.Color.Green
    val normalized = TrackpadCoordinateMapper.normalized(value, range)
    val third = TrackpadCoordinateMapper.third(value, range)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (showThirds) {
                    stringResource(R.string.trackpad_debug_axis_value_third, label, value, third + 1)
                } else {
                    stringResource(R.string.trackpad_debug_axis_value, label, value)
                },
                color = green,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "${"%.2f".format(range.min)} … ${"%.2f".format(range.max)}",
                color = green.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
        ) {
            val startX = 8.dp.toPx()
            val endX = size.width - 8.dp.toPx()
            val centerY = size.height / 2f
            drawLine(
                color = green.copy(alpha = 0.35f),
                start = Offset(startX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
            if (showThirds) {
                listOf(1f / 3f, 2f / 3f).forEach { fraction ->
                    val x = startX + (endX - startX) * fraction
                    drawLine(
                        color = green.copy(alpha = 0.65f),
                        start = Offset(x, 3.dp.toPx()),
                        end = Offset(x, size.height - 3.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            val markerX = startX + (endX - startX) * normalized
            drawCircle(
                color = green.copy(alpha = if (active) 1f else 0.55f),
                radius = if (active) 7.dp.toPx() else 5.dp.toPx(),
                center = Offset(markerX, centerY)
            )
        }
    }
}
