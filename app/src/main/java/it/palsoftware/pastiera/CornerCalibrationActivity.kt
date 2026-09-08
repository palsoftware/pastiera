package it.palsoftware.pastiera

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.view.RoundedCorner
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Locale
import kotlin.math.roundToInt

/** Full-display optical calibration; no IME or button-top rounding in the preview. */
class CornerCalibrationActivity : LocalizedComponentActivity() {
    private var draft = T2eCornerCalibration()
    private lateinit var preview: Preview
    private lateinit var keyboardField: EditText
    private var keyboardVisible = false
    private var resumed = false
    private val refreshSliders = mutableListOf<() -> Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        draft = if (savedInstanceState != null) T2eCornerCalibration(
            savedInstanceState.getFloat("size", 1f), savedInstanceState.getFloat("offset"),
            savedInstanceState.getFloat("squircle"), savedInstanceState.getFloat("shift_x"),
            savedInstanceState.getFloat("shift_y")) else T2eCornerCalibration.readSaved(this)
        keyboardVisible = savedInstanceState?.getBoolean("keyboard_visible") ?: false
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        preview = Preview()
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(8))
        }
        fun text(value: String, size: Float = 16f) = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.BLACK)
        }
        controls.addView(text("Displaykurve kalibrieren", 22f))
        controls.addView(text("Kontur direkt am Displayrand vergleichen. Änderungen bleiben bis „Übernehmen“ eine Vorschau."))
        val toggle = Switch(this).apply {
            text = "Echte Tastatur statt grauer Fläche"
            setTextColor(Color.BLACK)
            isChecked = keyboardVisible
        }
        keyboardField = EditText(this).apply {
            hint = "Testfeld für die Tastatur"
            setTextColor(Color.BLACK)
            setHintTextColor(Color.DKGRAY)
            setBackgroundColor(Color.WHITE)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
            maxLines = 1
        }
        controls.addView(toggle)
        controls.addView(keyboardField, LinearLayout.LayoutParams(-1, dp(44)))
        toggle.setOnCheckedChangeListener { _, enabled ->
            keyboardVisible = enabled
            updateKeyboardMode()
        }
        fun slider(label: String, max: Int, progress: () -> Int, update: (Int) -> Unit, value: () -> String) {
            val title = text("")
            val seek = SeekBar(this).apply {
                this.max = max
                thumbTintList = android.content.res.ColorStateList.valueOf(Color.DKGRAY)
                progressTintList = android.content.res.ColorStateList.valueOf(Color.DKGRAY)
            }
            val refresh = { title.text = "$label: ${value()}"; seek.progress = progress() }
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        update(p)
                        title.text = "$label: ${value()}"
                        refreshPreview()
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
            refreshSliders.add(refresh)
            controls.addView(title)
            val row = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            fun stepButton(symbol: String, step: Int) = Button(this).apply {
                text = symbol
                contentDescription = "$label $symbol"
                minWidth = 0
                minimumWidth = 0
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    update((seek.progress + step).coerceIn(0, max))
                    refresh()
                    refreshPreview()
                }
            }
            row.addView(stepButton("−", -1), LinearLayout.LayoutParams(dp(44), dp(44)))
            row.addView(seek, LinearLayout.LayoutParams(0, dp(44), 1f))
            row.addView(stepButton("+", 1), LinearLayout.LayoutParams(dp(44), dp(44)))
            controls.addView(row)
            refresh()
        }
        fun decimal(v: Float) = String.format(Locale.GERMAN, "%.2f", v)
        slider("Eckgröße", 140, { (draft.size * 100).roundToInt() - 40 },
            { draft = draft.copy(size = (it + 40) / 100f) }, { "${(draft.size * 100).roundToInt()} %" })
        slider("Versatz (− außen / + innen)", 128, { ((draft.offsetPx + 16) * 4).toInt() },
            { draft = draft.copy(offsetPx = it / 4f - 16) }, { "${decimal(draft.offsetPx)} px" })
        slider("Squircle (0 = Kreis)", 80, { (draft.squircle * 20).roundToInt() },
            { draft = draft.copy(squircle = it / 20f) }, { decimal(draft.squircle) })
        slider("X-Versatz (− links / + rechts)", 128, { ((draft.shiftXPx + 16) * 4).toInt() },
            { draft = draft.copy(shiftXPx = it / 4f - 16) }, { "${decimal(draft.shiftXPx)} px" })
        slider("Y-Versatz (− oben / + unten)", 128, { ((draft.shiftYPx + 16) * 4).toInt() },
            { draft = draft.copy(shiftYPx = it / 4f - 16) }, { "${decimal(draft.shiftYPx)} px" })
        val actions = LinearLayout(this)
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label; isAllCaps = false; setOnClickListener { action() }
        }
        actions.addView(button("Zurücksetzen") {
            draft = T2eCornerCalibration()
            refreshSliders.forEach { it() }
            refreshPreview()
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        actions.addView(button("Übernehmen") {
            draft.save(this)
            Toast.makeText(this, "Für den T2E-Modus gespeichert", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        controls.addView(actions)
        controls.addView(button("Schließen") { finish() })
        val scroll = ScrollView(this).apply { addView(controls) }
        root.addView(scroll, FrameLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(120)
        })
        setContentView(root)
        updateKeyboardMode()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("keyboard_visible", keyboardVisible)
        outState.putFloat("size", draft.size)
        outState.putFloat("offset", draft.offsetPx)
        outState.putFloat("squircle", draft.squircle)
        outState.putFloat("shift_x", draft.shiftXPx)
        outState.putFloat("shift_y", draft.shiftYPx)
        super.onSaveInstanceState(outState)
    }

    private fun refreshPreview() {
        preview.invalidate()
        T2eCornerCalibration.setPreview(this, draft.takeIf { resumed && keyboardVisible })
    }

    private fun updateKeyboardMode() {
        preview.visibility = if (keyboardVisible) View.INVISIBLE else View.VISIBLE
        keyboardField.visibility = if (keyboardVisible) View.VISIBLE else View.GONE
        refreshPreview()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        if (keyboardVisible) {
            keyboardField.requestFocus()
            keyboardField.post { if (resumed && keyboardVisible) imm.showSoftInput(keyboardField, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }
        } else {
            imm.hideSoftInputFromWindow(keyboardField.windowToken, 0)
            keyboardField.clearFocus()
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (::keyboardField.isInitialized) updateKeyboardMode()
    }

    override fun onPause() {
        resumed = false
        T2eCornerCalibration.setPreview(this, null)
        super.onPause()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private inner class Preview : View(this@CornerCalibrationActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val insets = rootWindowInsets
            fun radius(position: Int): Float = if (Build.VERSION.SDK_INT >= 31)
                (insets?.getRoundedCorner(position)?.radius?.takeIf { it > 0 } ?: dp(50)).toFloat()
                else dp(50).toFloat()
            val left = radius(RoundedCorner.POSITION_BOTTOM_LEFT)
            val right = radius(RoundedCorner.POSITION_BOTTOM_RIGHT)
            val path = T2eCornerGeometry.path(width.toFloat(), height.toFloat(), left, right, draft)
            canvas.save()
            canvas.clipRect(0f, height - maxOf(left, right) * 1.8f - dp(12), width.toFloat(), height.toFloat())
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(42, 42, 42)
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = Color.BLACK
            canvas.drawPath(path, paint)
            canvas.restore()
        }
    }
}
