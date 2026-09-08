package it.palsoftware.pastiera

import android.content.Context
import android.graphics.Path
import kotlin.math.*

internal data class T2eCornerCalibration(
    val size: Float = 1.17f,
    val offsetPx: Float = 2.25f,
    val squircle: Float = 0.85f,
    val shiftXPx: Float = -0.50f,
    val shiftYPx: Float = -1.25f
) {
    companion object {
        const val KEY = "titan2_elite_corner_calibration"
        private var previewOwner: Any? = null
        private var preview: T2eCornerCalibration? = null
        private val previewListeners = mutableSetOf<() -> Unit>()

        fun setPreview(owner: Any, value: T2eCornerCalibration?) {
            if (value == null && previewOwner !== owner) return
            if (previewOwner === owner && preview == value) return
            previewOwner = if (value != null) owner else null
            preview = value
            previewListeners.toList().forEach { it() }
        }

        fun addPreviewListener(listener: () -> Unit) { previewListeners.add(listener) }
        fun removePreviewListener(listener: () -> Unit) { previewListeners.remove(listener) }

        fun read(context: Context): T2eCornerCalibration = preview ?: readSaved(context)

        fun readSaved(context: Context): T2eCornerCalibration = runCatching {
            val defaults = T2eCornerCalibration()
            val json = org.json.JSONObject(SettingsManager.getPreferences(context).getString(KEY, "{}") ?: "{}")
            fun number(key: String, fallback: Float, range: ClosedFloatingPointRange<Float>): Float =
                json.optDouble(key, fallback.toDouble()).toFloat().let {
                    if (it.isFinite()) it.coerceIn(range) else fallback
                }
            T2eCornerCalibration(number("size", defaults.size, 0.4f..1.8f),
                number("offset_px", defaults.offsetPx, -16f..16f), number("squircle", defaults.squircle, 0f..4f),
                number("shift_x_px", defaults.shiftXPx, -16f..16f), number("shift_y_px", defaults.shiftYPx, -16f..16f))
        }.getOrDefault(T2eCornerCalibration())
    }

    fun save(context: Context) {
        val json = org.json.JSONObject().put("size", size).put("offset_px", offsetPx).put("squircle", squircle)
            .put("shift_x_px", shiftXPx).put("shift_y_px", shiftYPx)
        SettingsManager.getPreferences(context).edit().putString(KEY, json.toString()).apply()
    }
}

/** One contour for the calibration preview, IME outline and outer buttons.
 * Offset is measured along the inward unit normal, not by scaling the curve.
 */
internal object T2eCornerGeometry {
    data class Point(val x: Float, val y: Float)

    fun point(radius: Float, bottom: Float, angle: Double, calibration: T2eCornerCalibration,
              extraInset: Float = 0f): Point {
        val r = (radius * calibration.size).coerceAtLeast(1f).toDouble()
        val n = 2.0 + calibration.squircle
        val c = cos(angle).coerceIn(0.0, 1.0)
        val s = sin(angle).coerceIn(0.0, 1.0)
        val nx = c.pow(2.0 - 2.0 / n)
        val ny = s.pow(2.0 - 2.0 / n)
        val length = hypot(nx, ny)
        val inset = calibration.offsetPx + extraInset
        return Point((r - r * c.pow(2.0 / n) + inset * nx / length).toFloat(),
            (bottom - r + r * s.pow(2.0 / n) - inset * ny / length).toFloat())
    }

    fun atY(radius: Float, bottom: Float, y: Float, calibration: T2eCornerCalibration,
            extraInset: Float = 0f): Point {
        val start = point(radius, bottom, 0.0, calibration, extraInset)
        if (y <= start.y) return Point(start.x, y)
        var low = 0.0
        var high = PI / 2
        repeat(28) {
            val mid = (low + high) / 2
            if (point(radius, bottom, mid, calibration, extraInset).y < y) low = mid else high = mid
        }
        return point(radius, bottom, (low + high) / 2, calibration, extraInset)
    }

    fun path(width: Float, height: Float, leftRadius: Float, rightRadius: Float,
             calibration: T2eCornerCalibration, extraInset: Float = 0f): Path {
        val inset = calibration.offsetPx + extraInset
        val top = -2f * max(leftRadius, rightRadius) * calibration.size - abs(inset)
        return Path().apply {
            moveTo(inset, top)
            lineTo(width - inset, top)
            for (i in 0..256) {
                val p = point(rightRadius, height, PI / 2 * i / 256, calibration, extraInset)
                lineTo(width - p.x, p.y)
            }
            for (i in 256 downTo 0) {
                val p = point(leftRadius, height, PI / 2 * i / 256, calibration, extraInset)
                lineTo(p.x, p.y)
            }
            close()
            offset(calibration.shiftXPx, calibration.shiftYPx)
        }
    }
}
