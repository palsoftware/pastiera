package it.palsoftware.pastiera.inputmethod.statusbar

import android.content.Context
import android.widget.ImageView
import android.widget.TextView
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Shared host for status bar buttons across different containers.
 * Handles wrapping (badge/flash) and state updates consistently.
 */
class StatusBarButtonHost(
    private val context: Context,
    private val registry: StatusBarButtonRegistry
) {

    data class HostedButton(
        val id: StatusBarButtonId,
        val button: View,
        val container: View
    )

    private val recordingColors = mutableMapOf<View, Int>()
    private val outerEdges = mutableMapOf<View, StatusBarButtonPosition>()

    private val hostedButtons = mutableMapOf<StatusBarButtonId, HostedButton>()

    var themeOverride: StatusBarButtonStyles.ThemeOverride? = null
        set(value) {
            if (field == value) {
                return
            }
            field = value
            hostedButtons.values.forEach { hosted ->
                applyTheme(hosted.button, resolveThemeHeight(hosted))
            }
        }

    fun getOrCreateButton(
        id: StatusBarButtonId,
        size: Int,
        callbacks: StatusBarCallbacks,
        width: Int,
        height: Int
    ): HostedButton? {
        val existing = hostedButtons[id]
        if (existing != null) {
            if (id == StatusBarButtonId.Language) {
                registry.getLanguageFactory().refreshLanguageText(context, existing.button)
            }
            prepareForAttach(existing, width, height)
            return existing
        }

        val result = registry.createButton(context, id, size, callbacks) ?: return null
        val button = result.view
        if (button is ImageView || button is TextView) {
            val originalScaleType = (button as? ImageView)?.scaleType
            val originalTextSize = (button as? TextView)?.textSize
            val originalPadding = intArrayOf(button.paddingLeft, button.paddingTop, button.paddingRight, button.paddingBottom)
            var corrected = false
            val listener = android.view.ViewTreeObserver.OnPreDrawListener {
                if (alignOuterIcon(button, originalTextSize)) {
                    corrected = true
                } else if (corrected) {
                    if (button is ImageView) button.scaleType = originalScaleType
                    if (button is TextView && originalTextSize != null) button.setTextSize(TypedValue.COMPLEX_UNIT_PX, originalTextSize)
                    button.setPadding(originalPadding[0], originalPadding[1], originalPadding[2], originalPadding[3])
                    corrected = false
                }
                true
            }
            button.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.viewTreeObserver.addOnPreDrawListener(listener)
                }
                override fun onViewDetachedFromWindow(v: View) {
                    v.viewTreeObserver.removeOnPreDrawListener(listener)
                }
            })
        }
        applyTheme(button, height.takeIf { it > 0 } ?: size)
        val container = if (result.badgeView != null || result.flashOverlayView != null) {
            createWrappedView(button, result.badgeView, result.flashOverlayView, width, height)
        } else {
            button
        }

        val hosted = HostedButton(id, button, container)
        hostedButtons[id] = hosted
        if (id == StatusBarButtonId.Microphone) {
            registry.getMicrophoneFactory().setBackgroundRenderer(button) { color ->
                if (color == null) recordingColors.remove(button) else recordingColors[button] = color
                applyTheme(button, resolveThemeHeight(hosted))
            }
        }
        prepareForAttach(hosted, width, height)
        return hosted
    }

    fun setOuterEdge(id: StatusBarButtonId, edge: StatusBarButtonPosition?) {
        val hosted = hostedButtons[id] ?: return
        if (edge == null) outerEdges.remove(hosted.button) else outerEdges[hosted.button] = edge
        applyTheme(hosted.button, resolveThemeHeight(hosted))
    }

    fun updateButtonLayout(id: StatusBarButtonId, width: Int, height: Int) {
        val hosted = hostedButtons[id] ?: return
        if (hosted.container is FrameLayout) {
            val params = (hosted.button.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(width, height)
            params.width = width
            params.height = height
            hosted.button.layoutParams = params
        }
    }

    fun updateClipboardCount(count: Int) {
        updateButton(StatusBarButtonId.Clipboard, ButtonState.ClipboardState(count))
    }

    fun setMicrophoneActive(isActive: Boolean) {
        val hosted = hostedButtons[StatusBarButtonId.Microphone] ?: return
        registry.getMicrophoneFactory().setActive(hosted.button, isActive)
    }

    fun updateMicrophoneAudioLevel(rmsdB: Float) {
        val hosted = hostedButtons[StatusBarButtonId.Microphone] ?: return
        registry.getMicrophoneFactory().updateAudioLevel(hosted.button, rmsdB)
    }

    fun refreshLanguageText() {
        val hosted = hostedButtons[StatusBarButtonId.Language] ?: return
        registry.getLanguageFactory().refreshLanguageText(context, hosted.button)
    }

    fun setMinimalUiActive(isActive: Boolean) {
        updateButton(StatusBarButtonId.MinimalUi, ButtonState.MinimalUiState(isActive))
    }

    fun detachAll() {
        hostedButtons.keys.forEach { detachButton(it) }
    }

    fun detachButton(id: StatusBarButtonId) {
        val hosted = hostedButtons[id] ?: return
        (hosted.container.parent as? ViewGroup)?.removeView(hosted.container)
        hosted.button.visibility = View.GONE
        hosted.button.alpha = 1f
    }

    fun cleanup() {
        hostedButtons.forEach { (id, hosted) ->
            registry.cleanupButton(id, hosted.button)
        }
        hostedButtons.clear()
        outerEdges.clear()
        recordingColors.clear()
    }

    private fun updateButton(id: StatusBarButtonId, state: ButtonState) {
        val hosted = hostedButtons[id] ?: return
        registry.updateButton(id, hosted.button, state)
        applyTheme(hosted.button, resolveThemeHeight(hosted), state)
    }

    private fun prepareForAttach(hosted: HostedButton, width: Int, height: Int) {
        (hosted.container.parent as? ViewGroup)?.removeView(hosted.container)
        hosted.container.visibility = View.VISIBLE
        hosted.container.alpha = 1f
        hosted.button.visibility = View.VISIBLE
        hosted.button.alpha = 1f
        if (hosted.container is FrameLayout) {
            val params = (hosted.button.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(width, height)
            params.width = width
            params.height = height
            hosted.button.layoutParams = params
        } else {
            hosted.button.layoutParams = (hosted.button.layoutParams ?: ViewGroup.LayoutParams(width, height)).apply {
                this.width = width
                this.height = height
            }
        }
        applyTheme(hosted.button, height)
    }

    private fun createWrappedView(
        button: View,
        badgeView: View?,
        flashOverlayView: View?,
        width: Int,
        height: Int
    ): View {
        val frame = FrameLayout(context)
        frame.addView(button, FrameLayout.LayoutParams(width, height))

        badgeView?.let { badge ->
            (badge.parent as? ViewGroup)?.removeView(badge)
            frame.addView(
                badge,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.TOP
                ).apply {
                    val margin = dpToPx(2f)
                    val offset = dpToPx(2f)
                    setMargins(margin, margin + offset, margin, margin)
                }
            )
        }

        flashOverlayView?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
            frame.addView(overlay)
        }

        return frame
    }

    private fun resolveThemeHeight(hosted: HostedButton): Int? {
        return hosted.button.layoutParams?.height?.takeIf { it > 0 }
            ?: hosted.button.height.takeIf { it > 0 }
            ?: hosted.container.layoutParams?.height?.takeIf { it > 0 }
            ?: hosted.container.height.takeIf { it > 0 }
    }

    private fun applyTheme(view: View, fallbackHeight: Int? = null, state: ButtonState? = null) {
        (view.getTag(it.palsoftware.pastiera.R.id.tag_badge_view) as? TextView)?.let { badge ->
            val params = view.layoutParams
            val enlargedPastierinaButton = params != null && params.height > 0 &&
                params.width > params.height * 1.2f &&
                it.palsoftware.pastiera.SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (enlargedPastierinaButton) 14f else 10f)
            (badge.layoutParams as? FrameLayout.LayoutParams)?.let { badgeParams ->
                val rightMargin = dpToPx(if (enlargedPastierinaButton) 12f else 2f)
                if (badgeParams.rightMargin != rightMargin) {
                    badgeParams.rightMargin = rightMargin
                    badge.layoutParams = badgeParams
                }
            }
        }
        val theme = themeOverride ?: return
        (view.getTag(it.palsoftware.pastiera.R.id.tag_badge_view) as? TextView)?.setTextColor(theme.iconColor)
        val height = view.layoutParams?.height?.takeIf { it > 0 }
            ?: view.height.takeIf { it > 0 }
            ?: fallbackHeight?.takeIf { it > 0 }
        if (height != null) {
            val active = state is ButtonState.MinimalUiState && state.isActive
            val normalColor = recordingColors[view] ?: if (active) theme.pressedColor else theme.normalColor
            val background = StatusBarButtonStyles.createButtonDrawable(
                heightPx = height,
                normalColor = normalColor,
                pressedColor = theme.pressedColor,
                cornerRadiusRatio = theme.cornerRadiusRatio,
                borderColor = theme.borderColor,
                borderWidthPx = theme.borderWidthPx
            )
            view.background = if (
                outerEdges[view] != null &&
                it.palsoftware.pastiera.SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)
            ) {
                CurvedCornerButtonDrawable(
                    view, normalColor,
                    theme.pressedColor, height * theme.cornerRadiusRatio,
                    theme.borderColor, theme.borderWidthPx,
                    leftEdge = outerEdges[view] == StatusBarButtonPosition.LEFT
                )
            } else background
        }
        when (view) {
            is ImageView -> view.setColorFilter(theme.iconColor)
            is TextView -> view.setTextColor(theme.iconColor)
        }
    }

    // Runs after layout, including configurations without the bottom LED surface.
    private fun alignOuterIcon(view: View, originalTextSize: Float?): Boolean {
        val edge = outerEdges[view] ?: return false
        if (!it.palsoftware.pastiera.SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) return false
        if (view.width <= 0 || view.height <= 0) return false
        val icon = (view as? ImageView)?.drawable
        if (view is ImageView && (icon == null || icon.intrinsicWidth <= 0 || icon.intrinsicHeight <= 0)) return false
        val contour = view.background as? CurvedCornerButtonDrawable ?: return false
        var ancestor = view.parent
        while (ancestor != null && ancestor !is it.palsoftware.pastiera.inputmethod.StatusBarController.ImeChromeLayout) {
            ancestor = ancestor.parent
        }
        val chrome = ancestor as? it.palsoftware.pastiera.inputmethod.StatusBarController.ImeChromeLayout ?: return false
        val radii = chrome.bottomCornerRadiiPx ?: return false
        val location = IntArray(2)
        val chromeLocation = IntArray(2)
        view.getLocationInWindow(location)
        chrome.getLocationInWindow(chromeLocation)
        val left = location[0] - chromeLocation[0]
        val contentWidth: Float
        val contentHeight: Float
        val scale: Float
        if (icon != null) {
            contentWidth = icon.intrinsicWidth.toFloat().coerceAtMost(view.width.toFloat())
            scale = contentWidth / icon.intrinsicWidth
            contentHeight = icon.intrinsicHeight * scale
        } else {
            val text = view as? TextView ?: return false
            val textPaint = android.text.TextPaint(text.paint).apply { textSize = originalTextSize ?: text.textSize }
            contentWidth = textPaint.measureText(text.text.toString())
            contentHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
            scale = 1f
        }
        val availableExclusion = (view.width - contentWidth).coerceAtLeast(0f)
        val excluded = if (edge == StatusBarButtonPosition.LEFT) {
            (radii.first - left).toFloat()
        } else {
            (left + view.width - (chrome.width - radii.second)).toFloat()
        }.coerceIn(0f, availableExclusion)
        val preferredX = view.width / 2f + if (edge == StatusBarButtonPosition.LEFT) excluded / 4f else -excluded / 4f
        val center = contour.contentCenter(contentWidth, contentHeight, preferredX, view.height / 2f,
            edge == StatusBarButtonPosition.LEFT)
        if (view is ImageView) {
            view.scaleType = ImageView.ScaleType.MATRIX
            view.imageMatrix = android.graphics.Matrix().apply {
                setScale(scale * center.scale, scale * center.scale)
                postTranslate(center.x - contentWidth * center.scale / 2f - view.paddingLeft,
                    center.y - contentHeight * center.scale / 2f - view.paddingTop)
            }
        } else if (view is TextView) {
            val targetTextSize = (originalTextSize ?: view.textSize) * center.scale
            if (view.textSize != targetTextSize) view.setTextSize(TypedValue.COMPLEX_UNIT_PX, targetTextSize)
            val dx = center.x - view.width / 2f
            val dy = center.y - view.height / 2f
            val leftPadding = kotlin.math.round(2f * dx.coerceAtLeast(0f)).toInt()
            val rightPadding = kotlin.math.round(-2f * dx.coerceAtMost(0f)).toInt()
            val topPadding = kotlin.math.round(2f * dy.coerceAtLeast(0f)).toInt()
            val bottomPadding = kotlin.math.round(-2f * dy.coerceAtMost(0f)).toInt()
            if (view.paddingLeft != leftPadding || view.paddingRight != rightPadding ||
                view.paddingTop != topPadding || view.paddingBottom != bottomPadding) {
                view.setPadding(leftPadding, topPadding, rightPadding, bottomPadding)
            }
        }
        return true
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
