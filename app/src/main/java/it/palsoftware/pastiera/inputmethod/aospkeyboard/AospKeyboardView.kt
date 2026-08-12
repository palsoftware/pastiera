/*
 * Pastiera modifications for full virtual keyboard mode.
 *
 * This file derives keyboard geometry behavior from Android Open Source Project LatinIME
 * (`platform/packages/inputmethods/LatinIME`) at commit
 * 127336e9f29d69607eab55982324b210279ae8c5.
 *
 * AOSP LatinIME is licensed under the Apache License, Version 2.0. See
 * THIRD_PARTY_NOTICES.md and third_party/licenses/Apache-2.0.txt in this repository.
 */
package it.palsoftware.pastiera.inputmethod.aospkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.util.AttributeSet
import android.util.TypedValue
import android.view.accessibility.AccessibilityEvent
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import java.util.Locale
import kotlin.math.abs

/**
 * AOSP LatinIME alphabet key plane embedded in Pastiera.
 *
 * This intentionally keeps Pastiera's IME lifecycle/suggestions/status bars, but mirrors the
 * AOSP qwerty/qwertz/azerty key geometry from rows_*.xml and row_qwerty4.xml.
 */
class AospKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onText(text: String)
        fun onBackspace()
        fun onEnter()
        fun onShift()
        fun onSymbols()
        fun onCtrl()
        fun onLanguageSwitch()
        fun onCursorMove(delta: Int)
        fun onKeyPressSound(keyCode: Int)
        fun onModifierKeyDown(keyCode: Int): Boolean = false
        fun onModifierKeyUp(keyCode: Int): Boolean = false
        fun onKeyStroke(keyCode: Int, text: String): Boolean = false
        fun onSymbolText(text: String): Boolean = false
        fun onSymbolLongPress(keyCode: Int): Boolean = false
    }

    data class ThemeOverride(
        val background: Int,
        val divider: Int,
        val normalKey: Int,
        val specialKey: Int,
        val textAndIcons: Int,
        val ledInactive: Int,
        val ledActive: Int,
        val ledLocked: Int,
        val accent: Int,
        val keyPopup: Int = specialKey,
        val keyPopupSelected: Int = accent,
        val keyPopupStyle: String = "floating",
        val keyPopupAttached: Boolean = true,
        val keyPopupTailEnabled: Boolean = true,
        val keyPreviewAfterLongPress: Boolean = false,
        val keyAlternatesPopupEnabled: Boolean = true,
        val keyCornerRadiusRatio: Float = 0.08f,
        val keyHeightScale: Float = 1f,
        val numberRowHeightScale: Float = 0.8f,
        val keyWidthScale: Float = 1f,
        val rowGapScale: Float = 1f,
        val distributeHorizontalSpacing: Boolean = true,
        val ortholinear: Boolean = false
    )

    enum class SoftwareLayoutStyle {
        COMPACT,
        EXTENDED_ISO,
        FULL_ANSI,
        FULL_ISO
    }

    private enum class KeyType { CHAR, SHIFT, BACKSPACE, SYMBOLS, CTRL, ALT, COMMA, PERIOD, SPACE, ENTER, LANGUAGE }

    private data class KeySpec(
        val type: KeyType,
        val label: String,
        val output: String = label,
        val hint: String = "",
        val moreKeys: List<String> = emptyList(),
        val xPercent: Float,
        val widthPercent: Float,
        val visualInsetLeftPercent: Float = 0f,
        val visualInsetRightPercent: Float = 0f
    )

    private data class Key(
        val spec: KeySpec,
        val hitRect: RectF,
        val visualRect: RectF
    )

    data class LongPressLayerAlternative(
        val label: String,
        val output: String
    )

    internal data class AccessibilityKeySnapshot(
        val virtualId: Int,
        val resourceName: String,
        val label: String,
        val bounds: Rect,
        val clickable: Boolean,
        val enabled: Boolean,
        val selected: Boolean,
        val stateDescription: String?
    )

    private data class MoreKeyChoice(
        val label: String,
        val output: String
    )

    private data class MoreKeysPanelState(
        val baseKey: Key,
        val keys: List<MoreKeyChoice>,
        val layerKeys: List<MoreKeyChoice>,
        val popupRectInView: RectF?,
        val layerPopupRectInView: RectF?,
        val layerPopupBelowKey: Boolean,
        val keyWidth: Float,
        val keyHeight: Float,
        val layerKeyWidth: Float,
        val layerKeyHeight: Float,
        val padding: Float,
        var selectedIndex: Int = -1,
        var selectedLayerIndex: Int = -1
    )

    private data class PreviewPopupState(
        val label: String,
        val rect: RectF,
        val hasMoreKeys: Boolean
    )

    var listener: Listener? = null
    var layoutName: String = "qwerty"
        set(value) {
            val normalized = value.trim().lowercase(Locale.ROOT).ifBlank { "qwerty" }
            if (field == normalized) {
                return
            }
            field = normalized
            rebuildKeys(width, height)
            invalidateKeyboard()
        }
    var layoutStyle: SoftwareLayoutStyle = SoftwareLayoutStyle.COMPACT
        set(value) {
            if (field == value) {
                return
            }
            field = value
            rebuildKeys(width, height)
            invalidateKeyboard()
        }
    var includeNumberRow: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            rebuildKeys(width, height)
            requestLayout()
            invalidateKeyboard()
        }
    var nearestKeyTouchEnabled: Boolean = true
    var shifted: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            rebuildKeys(width, height)
            invalidateKeyboard()
        }
    var shiftLocked: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateKeyboard()
        }
    var ctrlOneShot: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateKeyboard()
        }
    var ctrlLocked: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateKeyboard()
        }
    var ctrlPressed: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateKeyboard()
        }
    var ctrlPreviewActive: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateKeyboard()
        }
    var altOneShot: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateKeyboard()
        }
    var altLocked: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateKeyboard()
        }
    var altPressed: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateKeyboard()
        }
    var altPreviewActive: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateKeyboard()
        }
    var symPageActive: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateKeyboard()
        }
    var symPreviewLabels: Map<Int, String> = emptyMap()
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateKeyboard()
        }
    var symPreviewTextLabels: Map<String, String> = emptyMap()
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateKeyboard()
        }
    var symPageLabels: Map<Int, String> = emptyMap()
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateKeyboard()
        }
    var symPageTextLabels: Map<String, String> = emptyMap()
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateKeyboard()
        }
    var ctrlPreviewLabels: Map<Int, String> = emptyMap()
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateKeyboard()
        }
    var ctrlPreviewIconRes: Map<Int, Int> = emptyMap()
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateKeyboard()
        }
    var altPreviewLabels: Map<Int, String> = emptyMap()
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateKeyboard()
        }
    var spacebarLabel: String = "space"
        set(value) {
            val normalized = value.ifBlank { "space" }
            if (field == normalized) {
                return
            }
            field = normalized
            invalidateKeyboard()
        }
    var symbolsLabel: String = "SYM"
        set(value) {
            val normalized = value.ifBlank { "SYM" }
            if (field == normalized) {
                return
            }
            field = normalized
            invalidateKeyboard()
        }
    var symbolsIconRes: Int? = null
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidateKeyboard()
        }
    var longPressTimeoutMs: Long = 500L
        set(value) {
            field = value.coerceIn(50L, 1000L)
        }
    var longPressAlternatesProvider: ((String) -> List<String>)? = null
    var longPressHintProvider: ((String) -> String?)? = null
    var longPressLayerAlternatesProvider: ((String) -> List<LongPressLayerAlternative>)? = null
    var longPressLayerPopupBelowKey: Boolean = true
    var themeOverride: ThemeOverride? = null
        set(value) {
            if (field == value) {
                return
            }
            field = value
            rebuildKeys(width, height)
            requestLayout()
            invalidateKeyboard()
        }

    private val keys = mutableListOf<Key>()
    private val accessibilityVirtualIds = linkedMapOf<String, Int>()
    private var nextAccessibilityVirtualId = 1
    private var pressedKey: Key? = null
    private var activePointerId: Int = -1
    private var heldModifierKey: Key? = null
    private var heldModifierPointerId: Int = -1
    private var chordKey: Key? = null
    private var chordPointerId: Int = -1
    private var previewPopupState: PreviewPopupState? = null
    private var moreKeysPanelState: MoreKeysPanelState? = null
    private var popupOverlayDrawable: Drawable? = null
    private val handler = Handler(Looper.getMainLooper())
    private var longPressTriggered = false
    private val longPressRunnable = Runnable { showMoreKeysOrRepeat() }
    private var spaceSwipeActive = false
    private var spaceSwipeLastX = 0f
    private var spaceLongPressArmed = false

    private val accessibilityHelper = object : ExploreByTouchHelper(this) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val key = keys.firstOrNull { it.hitRect.contains(x, y) } ?: return INVALID_ID
            return accessibilityVirtualId(key)
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            keys.forEach { key -> virtualViewIds += accessibilityVirtualId(key) }
        }

        override fun onPopulateNodeForVirtualView(
            virtualViewId: Int,
            node: AccessibilityNodeInfoCompat
        ) {
            val key = keyForAccessibilityVirtualId(virtualViewId)
            if (key == null) {
                node.contentDescription = context.getString(R.string.software_keyboard_accessibility_unknown_key)
                node.setBoundsInParent(Rect(0, 0, 1, 1))
                node.isEnabled = false
                return
            }

            val resourceName = accessibilityResourceName(key)
            node.className = Button::class.java.name
            node.packageName = context.packageName
            node.viewIdResourceName = "${context.packageName}:id/$resourceName"
            node.contentDescription = accessibilityLabel(key)
            node.setBoundsInParent(Rect().also { key.hitRect.roundOut(it) })
            node.isClickable = isEnabled
            node.isEnabled = isEnabled
            node.isFocusable = true
            node.isSelected = isAccessibilityKeySelected(key)
            accessibilityStateDescription(key)?.let { node.stateDescription = it }
            if (isEnabled) {
                node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK)
            }
        }

        override fun onPerformActionForVirtualView(
            virtualViewId: Int,
            action: Int,
            arguments: Bundle?
        ): Boolean {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK || !isEnabled) {
                return false
            }
            val key = keyForAccessibilityVirtualId(virtualViewId) ?: return false
            performAccessibilityKeyClick(key)
            sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
            return true
        }
    }

    private val keyboardBackground = drawable(R.drawable.keyboard_background_lxx_dark)
    private val normalKeyBackground = drawable(R.drawable.btn_keyboard_key_normal_off_lxx_dark)
    private val normalKeyPressedBackground = drawable(R.drawable.btn_keyboard_key_pressed_off_lxx_dark)
    private val shiftedKeyBackground = drawable(R.drawable.btn_keyboard_key_normal_on_lxx_dark)
    private val shiftedKeyPressedBackground = drawable(R.drawable.btn_keyboard_key_pressed_on_lxx_dark)
    private val spacebarBackground = drawable(R.drawable.btn_keyboard_spacebar_normal_lxx_dark)
    private val spacebarPressedBackground = drawable(R.drawable.btn_keyboard_spacebar_pressed_lxx_dark)
    private val previewBackground = drawable(R.drawable.keyboard_key_feedback_background_lxx_dark)
    private val previewMoreBackground = drawable(R.drawable.keyboard_key_feedback_more_background_lxx_dark)
    private val moreKeysBackground = drawable(R.drawable.keyboard_popup_panel_background_lxx_dark)
    private val shiftIcon = drawable(R.drawable.shift_24)
    private val shiftFilledIcon = drawable(R.drawable.shift_filled_24)
    private val shiftLockIcon = drawable(R.drawable.shift_lock_24)
    private val backspaceIcon = drawable(R.drawable.backspace_24)
    private val returnIcon = drawable(R.drawable.keyboard_return_24)
    private val ctrlIcon = drawable(R.drawable.keyboard_control_key_24)
    private val altIcon = drawable(R.drawable.keyboard_option_key_24)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(238, 238, 238)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(156, 164, 172)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }

    private val gapPx = dp(2f)
    private val horizontalPaddingPx = 0
    private val verticalPaddingPx: Int
        get() = gapPx
    private val preferredKeyHeightPx: Int
        get() = (dp(50f) * (themeOverride?.keyHeightScale ?: 1f).coerceIn(0.72f, 1.9f)).toInt()
    private val preferredNumberRowHeightPx: Int
        get() = (dp(50f) * (themeOverride?.numberRowHeightScale ?: 0.8f).coerceIn(0.45f, 1.4f)).toInt()
    private val rowGapPx: Int
        get() = (dp(6f) * ((themeOverride?.rowGapScale ?: 0f).coerceIn(0f, 2f))).toInt()

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
        setBackgroundColor(Color.BLACK)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        return accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
    }

    private fun invalidateKeyboard() {
        invalidate()
        accessibilityHelper.invalidateRoot()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = preferredKeyboardHeightPx()
        val resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, resolvedHeight)
    }

    fun preferredKeyboardHeightPx(): Int {
        val rowCount = keyboardRowCount()
        return verticalPaddingPx * 2 + rowHeights(rowCount).sum() + rowGapPx * (rowCount - 1)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rebuildKeys(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        themeOverride?.let {
            canvas.drawColor(it.background)
        } ?: drawDrawable(canvas, keyboardBackground, RectF(0f, 0f, width.toFloat(), height.toFloat()))
        keys.forEach { key ->
            drawDrawable(canvas, backgroundFor(key), key.visualRect)
            val previewIcon = previewIconFor(key)
            if (previewIcon != null) {
                drawCenteredIcon(canvas, previewIcon, key.visualRect)
                return@forEach
            }
            val previewLabel = previewLabelFor(key)
            val label = previewLabel ?: symPageLabelFor(key) ?: displayLabel(key.spec)
            textPaint.textSize = when (key.spec.type) {
                KeyType.SPACE -> sp(12f)
                KeyType.SYMBOLS -> sp(16f)
                KeyType.ENTER, KeyType.SHIFT, KeyType.BACKSPACE, KeyType.CTRL, KeyType.ALT, KeyType.LANGUAGE -> sp(23f)
                else -> sp(24f)
            }
            if (previewLabel != null && heldModifierKey?.spec?.type != KeyType.SYMBOLS) {
                textPaint.textSize = previewTextSize(previewLabel, key.visualRect)
            }
            textPaint.typeface = if (key.spec.type == KeyType.SPACE) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textPaint.color = themeOverride?.textAndIcons
                ?: if (isFunctional(key.spec.type)) Color.rgb(202, 209, 216) else Color.rgb(238, 238, 238)
            if (previewLabel == null && drawFunctionalIcon(canvas, key)) {
                return@forEach
            }
            val baselineOffset = -(textPaint.ascent() + textPaint.descent()) / 2f
            val y = if (key.spec.type == KeyType.SPACE) key.visualRect.centerY() + dp(7f) else key.visualRect.centerY() + baselineOffset
            canvas.drawText(label, key.visualRect.centerX(), y, textPaint)
            val altHint = displayAltHint(key)
            val longPressHint = displayHint(key)
            if (previewLabel == null && altHint.isNotBlank()) {
                hintPaint.textSize = sp(10f)
                hintPaint.color = themeOverride?.textAndIcons?.let { colorWithAlpha(it, 150) }
                    ?: Color.rgb(156, 164, 172)
                hintPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(altHint, key.visualRect.left + dp(9f), key.visualRect.bottom - dp(7f), hintPaint)
            }
            if (previewLabel == null && longPressHint.isNotBlank()) {
                hintPaint.textSize = sp(10f)
                hintPaint.color = themeOverride?.textAndIcons?.let { colorWithAlpha(it, 150) }
                    ?: Color.rgb(156, 164, 172)
                hintPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(longPressHint, key.visualRect.right - dp(9f), key.visualRect.top + dp(12f), hintPaint)
                hintPaint.textAlign = Paint.Align.CENTER
            }
        }
    }

    private fun drawFunctionalIcon(canvas: Canvas, key: Key): Boolean {
        val icon = when (key.spec.type) {
            KeyType.SHIFT -> when {
                shiftLocked -> shiftLockIcon
                shifted -> shiftFilledIcon
                else -> shiftIcon
            }
            KeyType.BACKSPACE -> backspaceIcon
            KeyType.ENTER -> returnIcon
            KeyType.CTRL -> ctrlIcon
            KeyType.ALT -> altIcon
            KeyType.SYMBOLS -> symbolsIconRes?.let(::drawable)
            else -> null
        } ?: return false
        drawCenteredIcon(canvas, icon, key.visualRect)
        return true
    }

    private fun drawCenteredIcon(canvas: Canvas, source: Drawable, rect: RectF) {
        val icon = source.constantState?.newDrawable()?.mutate() ?: source.mutate()
        val color = themeOverride?.textAndIcons ?: Color.rgb(202, 209, 216)
        icon.setTint(color)
        val maxSize = minOf(sp(26f), minOf(rect.width(), rect.height()) * 0.62f)
        val intrinsicWidth = icon.intrinsicWidth.takeIf { it > 0 } ?: 24
        val intrinsicHeight = icon.intrinsicHeight.takeIf { it > 0 } ?: 24
        val scale = minOf(maxSize / intrinsicWidth, maxSize / intrinsicHeight)
        val iconWidth = intrinsicWidth * scale
        val iconHeight = intrinsicHeight * scale
        val left = (rect.centerX() - iconWidth / 2f).toInt()
        val top = (rect.centerY() - iconHeight / 2f).toInt()
        icon.setBounds(left, top, (left + iconWidth).toInt(), (top + iconHeight).toInt())
        icon.draw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val pointerIndex = event.actionIndex
                val key = findKey(event.getX(pointerIndex), event.getY(pointerIndex))
                pressedKey = key
                activePointerId = event.getPointerId(pointerIndex)
                spaceSwipeActive = false
                spaceSwipeLastX = event.x
                spaceLongPressArmed = false
                longPressTriggered = false
                invalidateKeyboard()
                key?.let {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    listener?.onKeyPressSound(soundKeyCodeFor(it))
                    if (it.spec.type.isHoldModifier()) {
                        heldModifierKey = it
                        heldModifierPointerId = event.getPointerId(pointerIndex)
                        listener?.onModifierKeyDown(soundKeyCodeFor(it))
                        invalidateKeyboard()
                    } else {
                        if (!isModifierPreviewLayerActive()) {
                            showPreviewIfImmediate(it)
                            handler.postDelayed(longPressRunnable, longPressTimeoutMs)
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                val key = findKey(event.getX(pointerIndex), event.getY(pointerIndex)) ?: return true
                if (key.spec.type.isHoldModifier()) return true
                if (heldModifierKey == null) {
                    val previousKey = pressedKey
                    if (
                        previousKey != null &&
                        moreKeysPanelState == null &&
                        !longPressTriggered &&
                        !spaceSwipeActive
                    ) {
                        handler.removeCallbacks(longPressRunnable)
                        dismissPopup()
                        dispatchKey(previousKey)
                    }
                    activePointerId = event.getPointerId(pointerIndex)
                    pressedKey = key
                    spaceSwipeActive = false
                    spaceSwipeLastX = event.getX(pointerIndex)
                    spaceLongPressArmed = false
                    longPressTriggered = false
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    listener?.onKeyPressSound(soundKeyCodeFor(key))
                    if (!isModifierPreviewLayerActive()) {
                        showPreviewIfImmediate(key)
                        handler.postDelayed(longPressRunnable, longPressTimeoutMs)
                    }
                    invalidateKeyboard()
                    return true
                }
                handler.removeCallbacks(longPressRunnable)
                chordKey = key
                chordPointerId = event.getPointerId(pointerIndex)
                pressedKey = key
                longPressTriggered = false
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                listener?.onKeyPressSound(soundKeyCodeFor(key))
                if (!isModifierPreviewLayerActive()) {
                    showPreviewIfImmediate(key)
                }
                invalidateKeyboard()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = activePointerIndex(event).takeIf { it >= 0 } ?: 0
                val pointerX = event.getX(pointerIndex)
                val pointerY = event.getY(pointerIndex)
                if (moreKeysPanelState != null) {
                    updateMoreKeysSelection(pointerX, pointerY)
                    return true
                }
                val pressed = pressedKey
                if (pressed?.spec?.type == KeyType.SPACE) {
                    val step = dp(18f).toFloat()
                    val delta = pointerX - spaceSwipeLastX
                    if (kotlin.math.abs(delta) >= step) {
                        handler.removeCallbacks(longPressRunnable)
                        dismissPopup()
                        spaceSwipeActive = true
                        spaceLongPressArmed = false
                        longPressTriggered = true
                        val steps = (delta / step).toInt()
                        repeat(kotlin.math.abs(steps).coerceAtMost(4)) {
                            listener?.onCursorMove(if (steps > 0) 1 else -1)
                        }
                        spaceSwipeLastX += steps * step
                    }
                    return true
                }
                val key = findKey(pointerX, pointerY)
                if (key != pressedKey) {
                    handler.removeCallbacks(longPressRunnable)
                    dismissPopup()
                    pressedKey = key
                    invalidateKeyboard()
                    key?.let {
                        listener?.onKeyPressSound(soundKeyCodeFor(it))
                        if (!isModifierPreviewLayerActive()) {
                            showPreviewIfImmediate(it)
                            handler.postDelayed(longPressRunnable, longPressTimeoutMs)
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == chordPointerId) {
                    handler.removeCallbacks(longPressRunnable)
                    val key = chordKey
                    dismissPopup()
                    chordKey = null
                    chordPointerId = -1
                    pressedKey = heldModifierKey
                    invalidateKeyboard()
                    if (key != null && !longPressTriggered && !spaceSwipeActive) {
                        dispatchKey(key)
                    }
                    return true
                }
                if (pointerId == heldModifierPointerId) {
                    releaseHeldModifier()
                    return true
                }
                if (pointerId == activePointerId) {
                    handler.removeCallbacks(longPressRunnable)
                    moreKeysPanelState?.let { panel ->
                        val selected = selectedMoreKey(event.getX(pointerIndex), event.getY(pointerIndex), panel)
                        dismissPopup()
                        pressedKey = null
                        activePointerId = -1
                        invalidateKeyboard()
                        if (selected != null) {
                            listener?.onText(selected)
                        }
                        return true
                    }
                    val key = pressedKey
                    val wasSpaceSwipe = spaceSwipeActive
                    val wasSpaceLongPress = spaceLongPressArmed
                    dismissPopup()
                    spaceSwipeActive = false
                    spaceLongPressArmed = false
                    pressedKey = null
                    activePointerId = -1
                    invalidateKeyboard()
                    if (key?.spec?.type == KeyType.SPACE && wasSpaceLongPress && !wasSpaceSwipe) {
                        listener?.onLanguageSwitch()
                        return true
                    }
                    if (key != null && !longPressTriggered && !wasSpaceSwipe) {
                        dispatchKey(key)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                moreKeysPanelState?.let { panel ->
                    val selected = selectedMoreKey(event.x, event.y, panel)
                    dismissPopup()
                    pressedKey = null
                    invalidateKeyboard()
                    if (selected != null) {
                        listener?.onText(selected)
                    }
                    return true
                }
                val key = pressedKey
                val releasedHeldModifier = key != null && key == heldModifierKey
                dismissPopup()
                val wasSpaceSwipe = spaceSwipeActive
                val wasSpaceLongPress = spaceLongPressArmed
                spaceSwipeActive = false
                spaceLongPressArmed = false
                pressedKey = null
                activePointerId = -1
                chordKey = null
                chordPointerId = -1
                if (releasedHeldModifier) {
                    releaseHeldModifier()
                    return true
                }
                invalidateKeyboard()
                if (key?.spec?.type == KeyType.SPACE && wasSpaceLongPress && !wasSpaceSwipe) {
                    listener?.onLanguageSwitch()
                    return true
                }
                if (key != null && !longPressTriggered && !wasSpaceSwipe) {
                    dispatchKey(key)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                dismissPopup()
                spaceSwipeActive = false
                spaceLongPressArmed = false
                releaseHeldModifier()
                chordKey = null
                chordPointerId = -1
                pressedKey = null
                activePointerId = -1
                invalidateKeyboard()
                return true
            }
        }
        return true
    }

    private fun rebuildKeys(viewWidth: Int, viewHeight: Int) {
        keys.clear()
        if (viewWidth <= 0 || viewHeight <= 0) {
            return
        }
        val theme = themeOverride
        val keyWidthScale = (theme?.keyWidthScale ?: 1f).coerceIn(0.72f, 1.12f)
        val totalWidthScale = if (theme?.distributeHorizontalSpacing == false) keyWidthScale else 1f
        val usableWidth = ((viewWidth - horizontalPaddingPx * 2) * totalWidthScale).toInt()
        val horizontalOffset = horizontalPaddingPx + ((viewWidth - horizontalPaddingPx * 2) - usableWidth) / 2f
        val visualWidthScale = if (theme?.distributeHorizontalSpacing == false) 1f else keyWidthScale
        val rows = rowsFor(layoutName, layoutStyle)
        val rowCount = rows.size.coerceAtLeast(1)
        val desiredRowHeights = rowHeights(rowCount).map { it.toFloat() }
        val availableHeight = viewHeight - verticalPaddingPx * 2 - rowGapPx * (rowCount - 1)
        val heightScale = minOf(1f, availableHeight / desiredRowHeights.sum().coerceAtLeast(1f))
        var y = verticalPaddingPx.toFloat()
        rows.forEachIndexed { rowIndex, row ->
            val rowHeight = desiredRowHeights.getOrElse(rowIndex) { preferredKeyHeightPx.toFloat() } * heightScale
            val visualVerticalInset = minOf(gapPx / 2f, rowHeight / 4f)
            row.forEach { spec ->
                val rawLeft = horizontalOffset + usableWidth * (spec.xPercent / 100f)
                val rawRight = horizontalOffset + usableWidth * ((spec.xPercent + spec.widthPercent) / 100f)
                val hit = RectF(rawLeft, y, rawRight, y + rowHeight)
                val visualInset = hit.width() * (1f - visualWidthScale) / 2f
                val visual = RectF(
                    hit.left + gapPx / 2f + visualInset + usableWidth * (spec.visualInsetLeftPercent / 100f),
                    hit.top + visualVerticalInset,
                    hit.right - gapPx / 2f - visualInset - usableWidth * (spec.visualInsetRightPercent / 100f),
                    hit.bottom - visualVerticalInset
                )
                keys.add(Key(spec, hit, visual))
            }
            y += rowHeight + rowGapPx
        }
    }

    private fun keyboardRowCount(): Int = if (includeNumberRow) 5 else 4

    private fun rowHeights(rowCount: Int): List<Int> =
        List(rowCount) { rowIndex ->
            if (includeNumberRow && rowIndex == 0) preferredNumberRowHeightPx else preferredKeyHeightPx
        }

    private fun rowsFor(layout: String, style: SoftwareLayoutStyle): List<List<KeySpec>> {
        val family = SoftwareKeyboardLayoutTemplates.familyFor(layout)
        val rowStrings = SoftwareKeyboardLayoutTemplates.rowTemplateFor(family, style)
        if (style == SoftwareLayoutStyle.FULL_ANSI || style == SoftwareLayoutStyle.FULL_ISO) {
            return maybeWithNumberRow(fullRowsFor(rowStrings, style))
        }
        val row1Width = 100f / rowStrings[0].length
        val row1 = rowStrings[0].mapIndexed { index, ch ->
            charSpec(ch, index * row1Width, widthPercent = row1Width)
        }
        val row2Start = if (
            themeOverride?.ortholinear == true ||
            family == SoftwareKeyboardLayoutTemplates.Family.AZERTY ||
            style == SoftwareLayoutStyle.EXTENDED_ISO
        ) 0f else 5f
        val row2Width = if (style == SoftwareLayoutStyle.EXTENDED_ISO) {
            100f / rowStrings[1].length
        } else {
            10f
        }
        val row2 = rowStrings[1].mapIndexed { index, ch -> charSpec(ch, row2Start + index * row2Width, widthPercent = row2Width) }
        val row3CharWidth = if (style == SoftwareLayoutStyle.EXTENDED_ISO) row1Width else 10f
        val row3Start = if (style == SoftwareLayoutStyle.EXTENDED_ISO) row3CharWidth * 2f else 15f
        val row3SideKeyWidth = if (style == SoftwareLayoutStyle.EXTENDED_ISO) row3CharWidth * 2f else 15f
        val row3Chars = rowStrings[2].mapIndexed { index, ch ->
            charSpec(ch, row3Start + index * row3CharWidth, widthPercent = row3CharWidth)
        }
        val row3 = listOf(
            KeySpec(KeyType.SHIFT, "⇧", xPercent = 0f, widthPercent = row3SideKeyWidth, visualInsetRightPercent = 1f)
        ) + row3Chars + listOf(
            KeySpec(
                KeyType.BACKSPACE,
                "⌫",
                xPercent = 100f - row3SideKeyWidth,
                widthPercent = row3SideKeyWidth,
                visualInsetLeftPercent = 1f
            )
        )
        return maybeWithNumberRow(listOf(row1, row2, row3, bottomRow(includeEnter = true)))
    }

    private fun maybeWithNumberRow(rows: List<List<KeySpec>>): List<List<KeySpec>> =
        if (includeNumberRow) listOf(numberRowFor(rows.firstOrNull().orEmpty())) + rows else rows

    private fun numberRowFor(referenceRow: List<KeySpec>): List<KeySpec> {
        val referenceChars = referenceRow.filter { it.type == KeyType.CHAR }
        val labels = numberRowLabels(referenceChars.size)
        return referenceChars.mapIndexedNotNull { index, spec ->
            labels.getOrNull(index)?.firstOrNull()?.let { ch ->
                charSpec(ch, xPercent = spec.xPercent, widthPercent = spec.widthPercent)
            }
        }
    }

    private fun numberRowLabels(count: Int): List<String> {
        val labels = mutableListOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        labels += listOf("+", "-", "=").take((count - labels.size).coerceAtLeast(0))
        return labels.take(count)
    }

    private fun fullRowsFor(rowStrings: List<String>, style: SoftwareLayoutStyle): List<List<KeySpec>> {
        if (style == SoftwareLayoutStyle.FULL_ANSI) {
            return fullAnsiRowsFor(rowStrings)
        }
        val columns = maxOf(rowStrings[0].length + 1, rowStrings[1].length + 1, rowStrings[2].length + 2)
        val cellWidth = 100f / columns
        fun row(chars: String, reservedRightCells: Int): List<KeySpec> {
            val freeColumns = columns - reservedRightCells
            val start = if (themeOverride?.ortholinear == true || chars.length >= freeColumns) {
                0f
            } else {
                (freeColumns - chars.length) * cellWidth / 2f
            }
            return chars.mapIndexed { index, ch ->
                charSpec(ch, start + index * cellWidth, widthPercent = cellWidth)
            }
        }

        val row1 = row(rowStrings[0], reservedRightCells = 1) + KeySpec(
            KeyType.BACKSPACE,
            "⌫",
            xPercent = 100f - cellWidth,
            widthPercent = cellWidth
        )
        val row2 = row(rowStrings[1], reservedRightCells = 1) + KeySpec(
            KeyType.ENTER,
            "↵",
            output = "\n",
            xPercent = 100f - cellWidth,
            widthPercent = cellWidth
        )
        val row3Start = cellWidth
        val row3Chars = rowStrings[2].mapIndexed { index, ch ->
            charSpec(ch, row3Start + index * cellWidth, widthPercent = cellWidth)
        }
        val row3 = listOf(
            KeySpec(KeyType.SHIFT, "⇧", xPercent = 0f, widthPercent = cellWidth)
        ) + row3Chars + listOf(
            KeySpec(
                KeyType.SHIFT,
                "⇧",
                xPercent = 100f - cellWidth,
                widthPercent = cellWidth
            )
        )
        return listOf(row1, row2, row3, bottomRow(includeEnter = false))
    }

    private fun fullAnsiRowsFor(rowStrings: List<String>): List<List<KeySpec>> {
        val columns = maxOf(rowStrings[0].length + 1, rowStrings[1].length + 2, rowStrings[2].length + 3)
        val cellWidth = 100f / columns
        fun row(chars: String, reservedRightCells: Int): List<KeySpec> {
            val freeColumns = columns - reservedRightCells
            val start = if (themeOverride?.ortholinear == true || chars.length >= freeColumns) {
                0f
            } else {
                (freeColumns - chars.length) * cellWidth / 2f
            }
            return chars.mapIndexed { index, ch ->
                charSpec(ch, start + index * cellWidth, widthPercent = cellWidth)
            }
        }

        val row1 = row(rowStrings[0], reservedRightCells = 1) + KeySpec(
            KeyType.BACKSPACE,
            "⌫",
            xPercent = 100f - cellWidth,
            widthPercent = cellWidth
        )
        val row2 = row(rowStrings[1], reservedRightCells = 2) + KeySpec(
            KeyType.ENTER,
            "↵",
            output = "\n",
            xPercent = 100f - cellWidth * 2f,
            widthPercent = cellWidth * 2f
        )
        val row3Chars = rowStrings[2].mapIndexed { index, ch ->
            charSpec(ch, cellWidth + index * cellWidth, widthPercent = cellWidth)
        }
        val row3 = listOf(
            KeySpec(KeyType.SHIFT, "⇧", xPercent = 0f, widthPercent = cellWidth)
        ) + row3Chars + listOf(
            KeySpec(
                KeyType.SHIFT,
                "⇧",
                xPercent = 100f - cellWidth * 2f,
                widthPercent = cellWidth * 2f
            )
        )
        return listOf(row1, row2, row3, bottomRow(includeEnter = false))
    }

    private fun bottomRow(includeEnter: Boolean): List<KeySpec> {
        val row = listOf(
            KeySpec(KeyType.SYMBOLS, "SYM", xPercent = 0f, widthPercent = 12f),
            bottomModifierSpec(SettingsManager.getSoftwareKeyboardLeftModifierKey(context), xPercent = 12f),
            KeySpec(KeyType.COMMA, ",", xPercent = 22f, widthPercent = 8f, moreKeys = listOf("'", "\"", ";", ":")),
            KeySpec(KeyType.SPACE, "space", output = " ", xPercent = 30f, widthPercent = 40f),
            KeySpec(KeyType.PERIOD, ".", xPercent = 70f, widthPercent = 8f, moreKeys = listOf("!", "?", ";", ":", "…")),
            bottomModifierSpec(SettingsManager.getSoftwareKeyboardRightModifierKey(context), xPercent = 78f)
        )
        return if (!includeEnter) {
            row
        } else {
            row + listOf(
            KeySpec(KeyType.ENTER, "↵", output = "\n", xPercent = 88f, widthPercent = 12f)
            )
        }
    }

    private fun bottomModifierSpec(
        modifierKey: SettingsManager.SoftwareKeyboardModifierKey,
        xPercent: Float
    ): KeySpec {
        return when (modifierKey) {
            SettingsManager.SoftwareKeyboardModifierKey.ALT ->
                KeySpec(KeyType.ALT, "ALT", xPercent = xPercent, widthPercent = 10f)
            SettingsManager.SoftwareKeyboardModifierKey.CTRL ->
                KeySpec(KeyType.CTRL, "CTRL", xPercent = xPercent, widthPercent = 10f)
        }
    }

    private fun charSpec(ch: Char, xPercent: Float, hint: String = "", widthPercent: Float = 10f): KeySpec {
        val label = ch.toString()
        return KeySpec(
            type = KeyType.CHAR,
            label = label,
            output = label,
            hint = hint,
            moreKeys = moreKeysFor(ch),
            xPercent = xPercent,
            widthPercent = widthPercent
        )
    }

    private fun moreKeysFor(ch: Char): List<String> = when (ch.lowercaseChar()) {
        'a' -> listOf("à", "á", "â", "ä", "æ", "ã", "å", "ā")
        'c' -> listOf("ç", "ć", "č")
        'e' -> listOf("è", "é", "ê", "ë", "ē", "ė", "ę")
        'i' -> listOf("î", "ï", "í", "ī", "į", "ì")
        'n' -> listOf("ñ", "ń")
        'o' -> listOf("ô", "ö", "ò", "ó", "œ", "ø", "ō", "õ")
        's' -> listOf("ß", "ś", "š")
        'u' -> listOf("û", "ü", "ù", "ú", "ū")
        'y' -> listOf("ÿ")
        'z' -> listOf("ž", "ź", "ż")
        else -> emptyList()
    }

    private fun displayLabel(spec: KeySpec): String = when (spec.type) {
        KeyType.CHAR -> if (shifted && spec.label == "'") "?" else if (shifted) spec.label.uppercase(Locale.ROOT) else spec.label
        KeyType.SPACE -> spacebarLabel
        KeyType.SYMBOLS -> symbolsLabel
        else -> spec.label
    }

    private fun accessibilityVirtualId(key: Key): Int {
        val resourceName = accessibilityResourceName(key)
        return accessibilityVirtualIds.getOrPut(resourceName) { nextAccessibilityVirtualId++ }
    }

    private fun keyForAccessibilityVirtualId(virtualViewId: Int): Key? {
        return keys.firstOrNull { accessibilityVirtualId(it) == virtualViewId }
    }

    internal fun accessibilityVirtualIdForResourceName(resourceName: String): Int? {
        val key = keys.firstOrNull { accessibilityResourceName(it) == resourceName } ?: return null
        return accessibilityVirtualId(key)
    }

    internal fun accessibilitySnapshotForResourceName(resourceName: String): AccessibilityKeySnapshot? {
        val key = keys.firstOrNull { accessibilityResourceName(it) == resourceName } ?: return null
        return accessibilitySnapshot(key)
    }

    internal fun visibleAccessibilitySnapshots(): List<AccessibilityKeySnapshot> {
        return keys.map(::accessibilitySnapshot)
    }

    private fun accessibilitySnapshot(key: Key): AccessibilityKeySnapshot {
        return AccessibilityKeySnapshot(
            virtualId = accessibilityVirtualId(key),
            resourceName = accessibilityResourceName(key),
            label = accessibilityLabel(key),
            bounds = Rect().also { key.hitRect.roundOut(it) },
            clickable = isEnabled,
            enabled = isEnabled,
            selected = isAccessibilityKeySelected(key),
            stateDescription = accessibilityStateDescription(key)
        )
    }

    internal fun performAccessibilityClickForResourceName(resourceName: String): Boolean {
        if (!isEnabled) return false
        val key = keys.firstOrNull { accessibilityResourceName(it) == resourceName } ?: return false
        performAccessibilityKeyClick(key)
        return true
    }

    private fun accessibilityResourceName(key: Key): String {
        val baseName = "pastiera_key_${accessibilityIdentityToken(key)}"
        val matchingKeys = keys.filter { candidate ->
            "pastiera_key_${accessibilityIdentityToken(candidate)}" == baseName
        }
        val duplicateIndex = matchingKeys.indexOf(key)
        return when {
            duplicateIndex <= 0 -> baseName
            key.hitRect.centerX() > width / 2f -> "${baseName}_right"
            else -> "${baseName}_${duplicateIndex + 1}"
        }
    }

    private fun accessibilityIdentityToken(key: Key): String {
        return when (key.spec.type) {
            KeyType.CHAR -> accessibilityTextToken(key.spec.output)
            KeyType.SHIFT -> "shift"
            KeyType.BACKSPACE -> "backspace"
            KeyType.SYMBOLS -> "symbols"
            KeyType.CTRL -> "ctrl"
            KeyType.ALT -> "alt"
            KeyType.COMMA -> "comma"
            KeyType.PERIOD -> "period"
            KeyType.SPACE -> "space"
            KeyType.ENTER -> "enter"
            KeyType.LANGUAGE -> "language"
        }
    }

    private fun accessibilityTextToken(text: String): String {
        val normalized = text.lowercase(Locale.ROOT)
        if (normalized.length == 1 && normalized[0].let { it in 'a'..'z' || it in '0'..'9' }) {
            return normalized
        }
        return when (normalized) {
            "," -> "comma"
            "." -> "period"
            ";" -> "semicolon"
            ":" -> "colon"
            "!" -> "exclamation"
            "?" -> "question"
            "'" -> "apostrophe"
            "\"" -> "quote"
            "/" -> "slash"
            "\\" -> "backslash"
            "+" -> "plus"
            "-" -> "minus"
            "=" -> "equals"
            "[" -> "left_bracket"
            "]" -> "right_bracket"
            "(" -> "left_parenthesis"
            ")" -> "right_parenthesis"
            else -> normalized.codePoints().toArray().joinToString("_") { codePoint ->
                "u${codePoint.toString(16).padStart(4, '0')}"
            }
        }
    }

    private fun accessibilityLabel(key: Key): String {
        previewLabelFor(key)?.let { return it }
        symPageLabelFor(key)?.let { return it }
        return when (key.spec.type) {
            KeyType.CHAR -> displayLabel(key.spec)
            KeyType.SHIFT -> context.getString(R.string.software_keyboard_accessibility_shift)
            KeyType.BACKSPACE -> context.getString(R.string.software_keyboard_accessibility_backspace)
            KeyType.SYMBOLS -> context.getString(R.string.software_keyboard_accessibility_symbols)
            KeyType.CTRL -> context.getString(R.string.software_keyboard_accessibility_control)
            KeyType.ALT -> context.getString(R.string.software_keyboard_accessibility_alt)
            KeyType.COMMA -> context.getString(R.string.software_keyboard_accessibility_comma)
            KeyType.PERIOD -> context.getString(R.string.software_keyboard_accessibility_period)
            KeyType.SPACE -> context.getString(R.string.software_keyboard_accessibility_space)
            KeyType.ENTER -> context.getString(R.string.software_keyboard_accessibility_enter)
            KeyType.LANGUAGE -> context.getString(R.string.software_keyboard_accessibility_language_switch)
        }
    }

    private fun isAccessibilityKeySelected(key: Key): Boolean {
        return when (key.spec.type) {
            KeyType.SHIFT -> shifted || shiftLocked
            KeyType.CTRL -> ctrlOneShot || ctrlLocked || ctrlPressed || ctrlPreviewActive
            KeyType.ALT -> altOneShot || altLocked || altPressed || altPreviewActive
            KeyType.SYMBOLS -> symPageActive
            else -> false
        }
    }

    private fun accessibilityStateDescription(key: Key): String? {
        val states = buildList {
            if (!isEnabled) {
                add(context.getString(R.string.software_keyboard_accessibility_disabled))
            }
            if (isAccessibilityKeySelected(key)) {
                add(context.getString(R.string.software_keyboard_accessibility_selected))
            }
            if (key == pressedKey || key == heldModifierKey) {
                add(context.getString(R.string.software_keyboard_accessibility_pressed))
            }
        }
        return states.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun performAccessibilityKeyClick(key: Key) {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        val keyCode = soundKeyCodeFor(key)
        listener?.onKeyPressSound(keyCode)
        if (key.spec.type.isHoldModifier()) {
            listener?.onModifierKeyDown(keyCode)
            listener?.onModifierKeyUp(keyCode)
        } else {
            dispatchKey(key)
        }
        invalidateKeyboard()
    }

    private fun displayHint(key: Key): String {
        if (symPageActive) return ""
        if (key.spec.type !in listOf(KeyType.CHAR, KeyType.COMMA, KeyType.PERIOD)) return ""
        return longPressAlternatesFor(key).firstOrNull().orEmpty()
    }

    private fun displayAltHint(key: Key): String {
        if (symPageActive) return ""
        if (key.spec.type !in listOf(KeyType.CHAR, KeyType.COMMA, KeyType.PERIOD)) return ""
        return longPressHintProvider
            ?.invoke(key.spec.output)
            ?.takeIf { it.isNotBlank() }
            .orEmpty()
    }

    private fun previewLabelFor(key: Key): String? {
        val keyCode = modifierPreviewKeyCodeFor(key)
        val heldType = heldModifierKey?.spec?.type
        return when {
            heldType == KeyType.CTRL || ctrlPreviewActive -> ctrlPreviewLabels[keyCode]
            heldType == KeyType.ALT || altPreviewActive -> altPreviewLabels[keyCode]
            heldType == KeyType.SYMBOLS && !symPageActive ->
                symPreviewTextLabels[key.spec.output] ?: symPreviewLabels[keyCode]
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    private fun previewIconFor(key: Key): Drawable? {
        val keyCode = modifierPreviewKeyCodeFor(key)
        val heldType = heldModifierKey?.spec?.type
        if (heldType != KeyType.CTRL && !ctrlPreviewActive) {
            return null
        }
        return ctrlPreviewIconRes[keyCode]?.let(::drawable)
    }

    private fun isModifierPreviewLayerActive(): Boolean {
        val heldType = heldModifierKey?.spec?.type
        return heldType == KeyType.SYMBOLS ||
            heldType == KeyType.CTRL ||
            heldType == KeyType.ALT ||
            ctrlPreviewActive ||
            altPreviewActive
    }

    private fun symPageLabelFor(key: Key): String? {
        if (!symPageActive) return null
        if (heldModifierKey?.spec?.type == KeyType.CTRL || ctrlPreviewActive) return null
        if (key.spec.type !in listOf(KeyType.CHAR, KeyType.COMMA, KeyType.PERIOD)) return null
        symPageTextLabels[key.spec.output]?.takeIf { it.isNotBlank() }?.let { return it }
        return symPageLabels[soundKeyCodeFor(key)]?.takeIf { it.isNotBlank() }
    }

    private fun previewTextSize(label: String, rect: RectF): Float {
        var textSize = when {
            label.length <= 2 -> sp(20f)
            label.length <= 5 -> sp(14f)
            else -> sp(11f)
        }
        textPaint.textSize = textSize
        val maxWidth = rect.width() - dp(8f)
        while (textSize > sp(8f) && textPaint.measureText(label) > maxWidth) {
            textSize -= sp(1f)
            textPaint.textSize = textSize
        }
        return textSize
    }

    private fun dispatchKey(key: Key) {
        when (key.spec.type) {
            KeyType.CHAR -> {
                if (dispatchSymPageTextIfNeeded(key)) {
                    return
                }
                val text = if (shifted && key.spec.output == "'") "?" else if (shifted) key.spec.output.uppercase(Locale.ROOT) else key.spec.output
                if (listener?.onKeyStroke(soundKeyCodeFor(key), text) != true) {
                    listener?.onText(text)
                }
            }
            KeyType.COMMA, KeyType.PERIOD, KeyType.SPACE -> {
                if (dispatchSymPageTextIfNeeded(key)) {
                    return
                }
                if (listener?.onKeyStroke(soundKeyCodeFor(key), key.spec.output) != true) {
                    listener?.onText(key.spec.output)
                }
            }
            KeyType.BACKSPACE -> listener?.onBackspace()
            KeyType.ENTER -> listener?.onEnter()
            KeyType.SHIFT -> listener?.onShift()
            KeyType.SYMBOLS -> listener?.onSymbols()
            KeyType.CTRL -> listener?.onCtrl()
            KeyType.ALT -> listener?.onModifierKeyDown(KeyEvent.KEYCODE_ALT_LEFT).also {
                listener?.onModifierKeyUp(KeyEvent.KEYCODE_ALT_LEFT)
            }
            KeyType.LANGUAGE -> listener?.onLanguageSwitch()
        }
    }

    private fun dispatchSymPageTextIfNeeded(key: Key): Boolean {
        if (!symPageActive) {
            return false
        }
        val text = symPageLabelFor(key) ?: return false
        return listener?.onSymbolText(text) == true
    }

    private fun releaseHeldModifier() {
        val key = heldModifierKey ?: return
        listener?.onModifierKeyUp(soundKeyCodeFor(key))
        heldModifierKey = null
        heldModifierPointerId = -1
        if (pressedKey == key) {
            pressedKey = null
        }
        invalidateKeyboard()
    }

    fun cancelActiveTouchState() {
        handler.removeCallbacks(longPressRunnable)
        dismissPopup()
        spaceSwipeActive = false
        spaceLongPressArmed = false
        releaseHeldModifier()
        chordKey = null
        chordPointerId = -1
        pressedKey = null
        activePointerId = -1
        invalidateKeyboard()
    }

    private fun showMoreKeysOrRepeat() {
        val key = pressedKey ?: return
        if (isModifierPreviewLayerActive()) {
            return
        }
        if (key.spec.type == KeyType.BACKSPACE) {
            longPressTriggered = true
            listener?.onKeyPressSound(KeyEvent.KEYCODE_DEL)
            listener?.onBackspace()
            handler.postDelayed(longPressRunnable, 55L)
            return
        }
        if (key.spec.type == KeyType.SPACE) {
            longPressTriggered = true
            spaceLongPressArmed = true
            dismissPopup()
            invalidateKeyboard()
            return
        }
        if (symPageActive) {
            if (!key.spec.type.canOpenSymbolPicker()) {
                return
            }
            val keyCode = soundKeyCodeFor(key)
            if (listener?.onSymbolLongPress(keyCode) == true) {
                longPressTriggered = true
                dismissPopup()
                invalidateKeyboard()
            }
            return
        }
        val resolvedMoreKeys = if (themeOverride?.keyAlternatesPopupEnabled == false) {
            emptyList()
        } else {
            longPressAlternatesFor(key)
        }
        val layerKeys = longPressLayerAlternatesFor(key).map { alternative ->
            MoreKeyChoice(label = alternative.label, output = alternative.output)
        }
        if (resolvedMoreKeys.isEmpty() && layerKeys.isEmpty()) {
            if (themeOverride?.keyPreviewAfterLongPress == true) {
                showPreview(key)
            }
            return
        }
        longPressTriggered = true
        dismissPopup()
        val moreKeys = resolvedMoreKeys
            .map { if (shifted && it.length == 1 && it[0].isLetter()) it.uppercase(Locale.ROOT) else it }
            .map { MoreKeyChoice(label = it, output = it) }
        val itemWidth = dp(42f)
        val itemHeight = dp(52f)
        val layerItemWidth = dp(48f)
        val layerItemHeight = dp(42f)
        val padding = dp(6f)
        val layerPopupWidth = padding * 2 + layerKeys.size * layerItemWidth
        val layerPopupHeight = padding * 2 + layerItemHeight
        val popupWidth = if (moreKeys.isNotEmpty()) padding * 2 + moreKeys.size * itemWidth else 0
        val popupHeight = if (moreKeys.isNotEmpty()) padding * 2 + itemHeight else 0
        val placeLayerBelowKey = longPressLayerPopupBelowKey && layerKeys.isNotEmpty()
        val popupStackGap = if (layerKeys.isNotEmpty() && !placeLayerBelowKey) dp(4f) else 0
        val popupLeft = if (moreKeys.isNotEmpty()) {
            (key.visualRect.centerX() - popupWidth / 2f).coerceIn(0f, width - popupWidth.toFloat())
        } else {
            0f
        }
        val popupTop = if (moreKeys.isNotEmpty()) {
            key.visualRect.top -
                popupHeight -
                popupStackGap -
                (if (layerKeys.isNotEmpty() && !placeLayerBelowKey) layerPopupHeight else 0) -
                popupVerticalOffset(themeOverride)
        } else {
            0f
        }
        val layerPopupLeft = (key.visualRect.centerX() - layerPopupWidth / 2f)
            .coerceIn(0f, width - layerPopupWidth.toFloat())
        val layerPopupTop = if (placeLayerBelowKey) {
            key.visualRect.bottom + popupVerticalOffset(themeOverride)
        } else {
            if (moreKeys.isNotEmpty()) {
                popupTop + popupHeight + popupStackGap
            } else {
                key.visualRect.top - layerPopupHeight - popupVerticalOffset(themeOverride)
            }
        }
        val layerPopupRect = if (layerKeys.isNotEmpty()) {
            RectF(
                layerPopupLeft,
                layerPopupTop,
                layerPopupLeft + layerPopupWidth,
                layerPopupTop + layerPopupHeight
            )
        } else {
            null
        }
        moreKeysPanelState = MoreKeysPanelState(
            baseKey = key,
            keys = moreKeys,
            layerKeys = layerKeys,
            popupRectInView = if (moreKeys.isNotEmpty()) {
                RectF(popupLeft, popupTop, popupLeft + popupWidth, popupTop + popupHeight)
            } else {
                null
            },
            layerPopupRectInView = layerPopupRect,
            layerPopupBelowKey = placeLayerBelowKey,
            keyWidth = itemWidth.toFloat(),
            keyHeight = itemHeight.toFloat(),
            layerKeyWidth = layerItemWidth.toFloat(),
            layerKeyHeight = layerItemHeight.toFloat(),
            padding = padding.toFloat(),
            selectedIndex = if (moreKeys.isNotEmpty()) 0 else -1,
            selectedLayerIndex = -1
        )
        previewPopupState = null
        updatePopupOverlay()
        invalidateKeyboard()
    }

    private fun showPreview(key: Key) {
        if (isModifierPreviewLayerActive()) return
        if (key.spec.type != KeyType.CHAR && key.spec.type != KeyType.COMMA && key.spec.type != KeyType.PERIOD) return
        val previewWidth = maxOf(key.visualRect.width() + dp(18f), dp(52f).toFloat())
        val previewHeight = dp(72f).toFloat()
        val popupLeft = (key.visualRect.centerX() - previewWidth / 2f).coerceIn(0f, width - previewWidth)
        val popupTop = key.visualRect.top - previewHeight - popupVerticalOffset(themeOverride)
        previewPopupState = PreviewPopupState(
            label = symPageLabelFor(key) ?: displayLabel(key.spec),
            rect = RectF(popupLeft, popupTop, popupLeft + previewWidth, popupTop + previewHeight),
            hasMoreKeys = longPressAlternatesFor(key).isNotEmpty()
        )
        moreKeysPanelState = null
        updatePopupOverlay()
        invalidateKeyboard()
    }

    private fun showPreviewIfImmediate(key: Key) {
        if (themeOverride?.keyPreviewAfterLongPress == true) return
        showPreview(key)
    }

    private fun dismissPopup() {
        previewPopupState = null
        moreKeysPanelState = null
        updatePopupOverlay()
    }

    private fun updateMoreKeysSelection(x: Float, y: Float) {
        val panel = moreKeysPanelState ?: return
        val selectedLayer = selectedLayerKeyIndex(x, y, panel)
        if (selectedLayer >= 0) {
            if (panel.selectedLayerIndex == selectedLayer && panel.selectedIndex < 0) return
            panel.selectedLayerIndex = selectedLayer
            panel.selectedIndex = -1
            updatePopupOverlay()
            invalidateKeyboard()
            return
        }
        val selected = selectedMoreKeyIndex(x, y, panel)
        if (selected < 0) {
            return
        }
        if (selected == panel.selectedIndex) return
        panel.selectedIndex = selected
        panel.selectedLayerIndex = -1
        updatePopupOverlay()
        invalidateKeyboard()
    }

    private fun selectedMoreKey(x: Float, y: Float, panel: MoreKeysPanelState): String? {
        val layerIndex = selectedLayerKeyIndex(x, y, panel)
        if (layerIndex >= 0) {
            return panel.layerKeys.getOrNull(layerIndex)?.output
        }
        panel.selectedLayerIndex.takeIf { it >= 0 }?.let { selectedLayerIndex ->
            return panel.layerKeys.getOrNull(selectedLayerIndex)?.output
        }
        val index = selectedMoreKeyIndex(x, y, panel).takeIf { it >= 0 } ?: panel.selectedIndex
        return panel.keys.getOrNull(index)?.output
    }

    private fun selectedMoreKeyIndex(x: Float, y: Float, panel: MoreKeysPanelState): Int {
        val rect = panel.popupRectInView ?: return -1
        if (panel.keys.isEmpty()) return -1
        val verticalSlop = dp(24f)
        if (y < rect.top - verticalSlop || y > rect.bottom + verticalSlop) return -1
        val relativeX = (x - rect.left - panel.padding).coerceIn(0f, panel.keys.size * panel.keyWidth - 1f)
        return (relativeX / panel.keyWidth).toInt().coerceIn(0, panel.keys.lastIndex)
    }

    private fun selectedLayerKeyIndex(x: Float, y: Float, panel: MoreKeysPanelState): Int {
        val rect = panel.layerPopupRectInView ?: return -1
        val verticalSlop = dp(20f)
        if (y < rect.top - verticalSlop || y > rect.bottom + verticalSlop) return -1
        val relativeX = (x - rect.left - panel.padding).coerceIn(0f, panel.layerKeys.size * panel.layerKeyWidth - 1f)
        return (relativeX / panel.layerKeyWidth).toInt().coerceIn(0, panel.layerKeys.lastIndex)
    }

    private fun findKey(x: Float, y: Float): Key? {
        keys.firstOrNull { it.hitRect.contains(x, y) }?.let { return it }
        if (!nearestKeyTouchEnabled) return null

        val verticalSlop = maxOf(rowGapPx.toFloat(), dp(8f).toFloat())
        val candidateRows = keys
            .filter { y >= it.hitRect.top - verticalSlop && y <= it.hitRect.bottom + verticalSlop }
            .ifEmpty { return null }
        return candidateRows.minByOrNull { key ->
            val dx = when {
                x < key.hitRect.left -> key.hitRect.left - x
                x > key.hitRect.right -> x - key.hitRect.right
                else -> 0f
            }
            val dy = abs(y - key.hitRect.centerY())
            dx * dx + dy * dy
        }
    }

    private fun activePointerIndex(event: MotionEvent): Int =
        activePointerId.takeIf { it >= 0 }?.let { event.findPointerIndex(it) } ?: -1

    private fun soundKeyCodeFor(key: Key): Int {
        return when (key.spec.type) {
            KeyType.SPACE -> KeyEvent.KEYCODE_SPACE
            KeyType.BACKSPACE -> KeyEvent.KEYCODE_DEL
            KeyType.ENTER -> KeyEvent.KEYCODE_ENTER
            KeyType.SHIFT -> KeyEvent.KEYCODE_SHIFT_LEFT
            KeyType.CTRL -> KeyEvent.KEYCODE_CTRL_LEFT
            KeyType.ALT -> KeyEvent.KEYCODE_ALT_LEFT
            KeyType.SYMBOLS, KeyType.LANGUAGE -> KeyEvent.KEYCODE_SYM
            KeyType.COMMA -> KeyEvent.KEYCODE_COMMA
            KeyType.PERIOD -> KeyEvent.KEYCODE_PERIOD
            KeyType.CHAR -> physicalKeyCodeForText(key.spec.output)
        }
    }

    private fun modifierPreviewKeyCodeFor(key: Key): Int {
        if (key.spec.type == KeyType.CHAR) {
            val character = key.spec.output.firstOrNull()?.lowercaseChar()
            if (character !in 'a'..'z') return KeyEvent.KEYCODE_UNKNOWN
        }
        return soundKeyCodeFor(key)
    }

    private fun physicalKeyCodeForText(text: String): Int {
        val char = text.firstOrNull() ?: return KeyEvent.KEYCODE_UNKNOWN
        return SoftwareKeyboardSymLabels.keyCodeForChar(char, layoutName)
            ?: keyCodeForText(text)
    }

    private fun keyCodeForText(text: String): Int {
        val char = text.firstOrNull()?.lowercaseChar() ?: return KeyEvent.KEYCODE_UNKNOWN
        return when (char) {
            in 'a'..'z' -> KeyEvent.KEYCODE_A + (char - 'a')
            in '0'..'9' -> KeyEvent.KEYCODE_0 + (char - '0')
            else -> KeyEvent.KEYCODE_UNKNOWN
        }
    }

    private fun longPressAlternatesFor(key: Key): List<String> {
        if (!key.spec.type.canShowLongPressAlternates()) {
            return emptyList()
        }
        val providerAlternates = longPressAlternatesProvider
            ?.invoke(key.spec.output)
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        return providerAlternates.ifEmpty { key.spec.moreKeys }
    }

    private fun longPressLayerAlternatesFor(key: Key): List<LongPressLayerAlternative> {
        if (!key.spec.type.canShowLongPressAlternates()) {
            return emptyList()
        }
        return longPressLayerAlternatesProvider
            ?.invoke(key.spec.output)
            ?.filter { it.output.isNotBlank() && it.label.isNotBlank() }
            .orEmpty()
    }


    private fun drawPreviewPopup(canvas: Canvas, offsetX: Float = 0f, offsetY: Float = 0f) {
        val popup = previewPopupState ?: return
        val rect = popup.rect.offsetBy(offsetX, offsetY)
        val theme = themeOverride
        if (theme != null) {
            drawThemedPopupBackground(canvas, theme, rect, rect.centerX(), hasTail = shouldDrawPopupTail(theme))
        } else {
            drawDrawable(canvas, if (popup.hasMoreKeys) previewMoreBackground else previewBackground, rect)
        }
        textPaint.textSize = sp(30f)
        textPaint.typeface = Typeface.DEFAULT
        textPaint.color = themeOverride?.textAndIcons ?: Color.rgb(238, 238, 238)
        val baselineOffset = -(textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(popup.label, rect.centerX(), rect.centerY() + baselineOffset, textPaint)
    }

    private fun drawMoreKeysPanel(canvas: Canvas, offsetX: Float = 0f, offsetY: Float = 0f) {
        val panel = moreKeysPanelState ?: return
        val theme = themeOverride
        panel.popupRectInView?.offsetBy(offsetX, offsetY)?.let { panelRect ->
            if (theme != null) {
                drawThemedPopupBackground(
                    canvas,
                    theme,
                    panelRect,
                    panel.baseKey.visualRect.centerX() + offsetX,
                    hasTail = shouldDrawPopupTail(theme)
                )
            } else {
                drawDrawable(canvas, moreKeysBackground, panelRect)
            }
            panel.keys.forEachIndexed { index, choice ->
                val left = panelRect.left + panel.padding + index * panel.keyWidth
                val top = panelRect.top + panel.padding
                val rect = RectF(left, top, left + panel.keyWidth, top + panel.keyHeight)
                if (index == panel.selectedIndex) {
                    if (theme != null) {
                        drawThemedPopupBackground(canvas, theme, rect, rect.centerX(), hasTail = false, selected = true)
                    } else {
                        drawDrawable(canvas, normalKeyBackground, rect)
                    }
                }
                textPaint.textSize = sp(24f)
                textPaint.typeface = Typeface.DEFAULT
                textPaint.color = theme?.let {
                    if (index == panel.selectedIndex) readableTextColor(it.keyPopupSelected) else it.textAndIcons
                } ?: if (index == panel.selectedIndex) Color.BLACK else Color.rgb(238, 238, 238)
                val baselineOffset = -(textPaint.ascent() + textPaint.descent()) / 2f
                canvas.drawText(choice.label, rect.centerX(), rect.centerY() + baselineOffset, textPaint)
            }
        }
        val layerRect = panel.layerPopupRectInView?.offsetBy(offsetX, offsetY) ?: return
        if (theme != null) {
            drawThemedPopupBackground(
                canvas,
                theme,
                layerRect,
                panel.baseKey.visualRect.centerX() + offsetX,
                hasTail = shouldDrawPopupTail(theme),
                tailOnTop = panel.layerPopupBelowKey
            )
        } else {
            drawDrawable(canvas, moreKeysBackground, layerRect)
        }
        panel.layerKeys.forEachIndexed { index, choice ->
            val left = layerRect.left + panel.padding + index * panel.layerKeyWidth
            val top = layerRect.top + panel.padding
            val rect = RectF(left, top, left + panel.layerKeyWidth, top + panel.layerKeyHeight)
            if (index == panel.selectedLayerIndex) {
                if (theme != null) {
                    drawThemedPopupBackground(canvas, theme, rect, rect.centerX(), hasTail = false, selected = true)
                } else {
                    drawDrawable(canvas, normalKeyBackground, rect)
                }
            }
            textPaint.textSize = previewTextSize(choice.label, rect)
            textPaint.typeface = Typeface.DEFAULT
            textPaint.color = theme?.let {
                if (index == panel.selectedLayerIndex) readableTextColor(it.keyPopupSelected) else it.textAndIcons
            } ?: if (index == panel.selectedLayerIndex) Color.BLACK else Color.rgb(238, 238, 238)
            val baselineOffset = -(textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(choice.label, rect.centerX(), rect.centerY() + baselineOffset, textPaint)
        }
    }

    private fun updatePopupOverlay() {
        val root = rootView ?: return
        popupOverlayDrawable?.let { root.overlay.remove(it) }
        popupOverlayDrawable = null
        if (previewPopupState == null && moreKeysPanelState == null) {
            root.invalidate()
            return
        }
        val location = IntArray(2)
        val rootLocation = IntArray(2)
        getLocationOnScreen(location)
        root.getLocationOnScreen(rootLocation)
        val offsetX = (location[0] - rootLocation[0]).toFloat()
        val offsetY = (location[1] - rootLocation[1]).toFloat()
        popupOverlayDrawable = object : Drawable() {
            override fun draw(canvas: Canvas) {
                drawPreviewPopup(canvas, offsetX, offsetY)
                drawMoreKeysPanel(canvas, offsetX, offsetY)
            }

            override fun setAlpha(alpha: Int) = Unit
            override fun setColorFilter(colorFilter: ColorFilter?) = Unit
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }.also { drawable ->
            drawable.setBounds(0, 0, root.width, root.height)
            root.overlay.add(drawable)
        }
        root.invalidate()
    }

    override fun onDetachedFromWindow() {
        popupOverlayDrawable?.let { rootView?.overlay?.remove(it) }
        popupOverlayDrawable = null
        super.onDetachedFromWindow()
    }

    private fun backgroundFor(key: Key): Drawable? {
        themeOverride?.let { theme ->
            val pressed = key == pressedKey
            val baseColor = themedKeyColor(theme, key.spec.type)
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = keyCornerRadius(theme)
                setColor(if (pressed) blendColors(baseColor, theme.accent, 0.28f) else baseColor)
                setStroke(dp(1f), theme.divider)
            }
        }
        val pressed = key == pressedKey
        val drawable = when {
            key.spec.type == KeyType.SPACE && pressed -> spacebarPressedBackground
            key.spec.type == KeyType.SPACE -> spacebarBackground
            key.spec.type == KeyType.SHIFT && shifted && pressed -> shiftedKeyPressedBackground
            key.spec.type == KeyType.SHIFT && shifted -> shiftedKeyBackground
            pressed -> normalKeyPressedBackground
            else -> normalKeyBackground
        }
        return drawable?.constantState?.newDrawable()?.mutate() ?: drawable
    }

    private fun themedKeyColor(theme: ThemeOverride, type: KeyType): Int {
        return when (type) {
            KeyType.SHIFT -> when {
                shiftLocked -> theme.ledLocked
                shifted -> theme.ledActive
                else -> theme.specialKey
            }
            KeyType.CTRL -> when {
                ctrlLocked -> theme.ledLocked
                ctrlPressed || ctrlOneShot -> theme.ledActive
                else -> theme.specialKey
            }
            KeyType.ALT -> when {
                altLocked -> theme.ledLocked
                altPressed || altOneShot -> theme.ledActive
                else -> theme.specialKey
            }
            else -> if (isFunctional(type)) theme.specialKey else theme.normalKey
        }
    }

    private fun drawThemedPopupBackground(
        canvas: Canvas,
        theme: ThemeOverride,
        rect: RectF,
        anchorX: Float,
        hasTail: Boolean,
        selected: Boolean = false,
        tailOnTop: Boolean = false
    ) {
        val classic = theme.keyPopupStyle == SettingsManager.KEYBOARD_THEME_POPUP_STYLE_CLASSIC
        val radius = if (selected) {
            if (classic) dp(8f).toFloat() else keyCornerRadius(theme)
        } else if (classic) {
            dp(13f).toFloat()
        } else {
            maxOf(dp(16f).toFloat(), keyCornerRadius(theme) * 0.72f)
        }
        val fillColor = if (selected) theme.keyPopupSelected else theme.keyPopup
        val path = roundedPopupPath(rect, radius, anchorX, hasTail, classic, tailOnTop)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorWithAlpha(Color.BLACK, if (classic) 52 else if (isDarkColor(fillColor)) 80 else 38)
            style = Paint.Style.FILL
        }
        canvas.save()
        canvas.translate(0f, dp(if (classic) 2.5f else 1.5f).toFloat())
        canvas.drawPath(path, shadowPaint)
        canvas.restore()
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fillColor
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, fillPaint)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.divider
            style = Paint.Style.STROKE
            strokeWidth = dp(1f).toFloat()
        }
        canvas.drawPath(path, strokePaint)
    }

    private fun popupVerticalOffset(theme: ThemeOverride?): Float {
        if (theme == null) return dp(8f).toFloat()
        return when {
            shouldDrawPopupTail(theme) -> popupTailHeight(theme)
            theme.keyPopupAttached -> dp(2f).toFloat()
            else -> dp(8f).toFloat()
        }
    }

    private fun shouldDrawPopupTail(theme: ThemeOverride): Boolean =
        theme.keyPopupAttached && theme.keyPopupTailEnabled

    private fun popupTailHeight(theme: ThemeOverride): Float =
        dp(if (theme.keyPopupStyle == SettingsManager.KEYBOARD_THEME_POPUP_STYLE_CLASSIC) 34f else 18f).toFloat()

    private fun roundedPopupPath(
        rect: RectF,
        radius: Float,
        anchorX: Float,
        hasTail: Boolean,
        classic: Boolean,
        tailOnTop: Boolean = false
    ): Path {
        val body = Path().apply {
            addRoundRect(rect, radius, radius, Path.Direction.CW)
        }
        if (hasTail) {
            val tailWidth = minOf(
                dp(if (classic) 34f else 30f).toFloat(),
                rect.width() * if (classic) 0.34f else 0.38f
            )
            val tailHeight = dp(if (classic) 34f else 18f).toFloat()
            val centerX = anchorX.coerceIn(rect.left + radius, rect.right - radius)
            val sideCurveDivisor = if (classic) 2.0f else 2.4f
            val tail = Path().apply {
                if (tailOnTop) {
                    moveTo(centerX - tailWidth / 2f, rect.top + radius * if (classic) 0.12f else 0.25f)
                    cubicTo(
                        centerX - tailWidth / sideCurveDivisor,
                        rect.top - tailHeight * 0.34f,
                        centerX - tailWidth / 3.2f,
                        rect.top - tailHeight,
                        centerX,
                        rect.top - tailHeight
                    )
                    cubicTo(
                        centerX + tailWidth / 3.2f,
                        rect.top - tailHeight,
                        centerX + tailWidth / sideCurveDivisor,
                        rect.top - tailHeight * 0.34f,
                        centerX + tailWidth / 2f,
                        rect.top + radius * if (classic) 0.12f else 0.25f
                    )
                } else {
                    moveTo(centerX - tailWidth / 2f, rect.bottom - radius * if (classic) 0.12f else 0.25f)
                    cubicTo(
                        centerX - tailWidth / sideCurveDivisor,
                        rect.bottom + tailHeight * 0.34f,
                        centerX - tailWidth / 3.2f,
                        rect.bottom + tailHeight,
                        centerX,
                        rect.bottom + tailHeight
                    )
                    cubicTo(
                        centerX + tailWidth / 3.2f,
                        rect.bottom + tailHeight,
                        centerX + tailWidth / sideCurveDivisor,
                        rect.bottom + tailHeight * 0.34f,
                        centerX + tailWidth / 2f,
                        rect.bottom - radius * if (classic) 0.12f else 0.25f
                    )
                }
                close()
            }
            body.op(tail, Path.Op.UNION)
        }
        return body
    }

    private fun readableTextColor(backgroundColor: Int): Int {
        return if (isDarkColor(backgroundColor)) Color.WHITE else Color.BLACK
    }

    private fun isDarkColor(color: Int): Boolean {
        val luminance = 0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)
        return luminance < 150f
    }

    private fun keyCornerRadius(theme: ThemeOverride): Float {
        return preferredKeyHeightPx * theme.keyCornerRadiusRatio.coerceIn(0f, 0.35f)
    }

    private fun drawDrawable(canvas: Canvas, drawable: Drawable?, rect: RectF) {
        if (drawable == null) return
        drawable.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        drawable.draw(canvas)
    }

    private fun RectF.offsetBy(dx: Float, dy: Float): RectF =
        RectF(left + dx, top + dy, right + dx, bottom + dy)

    private fun isFunctional(type: KeyType): Boolean =
        type == KeyType.SHIFT ||
            type == KeyType.BACKSPACE ||
            type == KeyType.SYMBOLS ||
            type == KeyType.CTRL ||
            type == KeyType.ALT ||
            type == KeyType.ENTER ||
            type == KeyType.LANGUAGE

    private fun KeyType.canShowLongPressAlternates(): Boolean =
        this == KeyType.CHAR || this == KeyType.COMMA || this == KeyType.PERIOD

    private fun KeyType.canOpenSymbolPicker(): Boolean =
        this == KeyType.CHAR || this == KeyType.COMMA || this == KeyType.PERIOD

    private fun KeyType.isHoldModifier(): Boolean =
        this == KeyType.CTRL || this == KeyType.ALT || this == KeyType.SYMBOLS

    private fun drawable(resId: Int): Drawable? = ContextCompat.getDrawable(context, resId)

    private fun colorWithAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun blendColors(first: Int, second: Int, ratio: Float): Int {
        val clamped = ratio.coerceIn(0f, 1f)
        val inverse = 1f - clamped
        return Color.rgb(
            (Color.red(first) * inverse + Color.red(second) * clamped).toInt().coerceIn(0, 255),
            (Color.green(first) * inverse + Color.green(second) * clamped).toInt().coerceIn(0, 255),
            (Color.blue(first) * inverse + Color.blue(second) * clamped).toInt().coerceIn(0, 255)
        )
    }

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics
    ).toInt()

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics
    )

    fun showInputMethodPicker() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
    }
}
