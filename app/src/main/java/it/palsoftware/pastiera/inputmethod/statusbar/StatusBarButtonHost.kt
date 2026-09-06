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
        applyTheme(button, height.takeIf { it > 0 } ?: size)
        val container = if (result.badgeView != null || result.flashOverlayView != null) {
            createWrappedView(button, result.badgeView, result.flashOverlayView, width, height)
        } else {
            button
        }

        val hosted = HostedButton(id, button, container)
        hostedButtons[id] = hosted
        prepareForAttach(hosted, width, height)
        return hosted
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
            val gapless = it.palsoftware.pastiera.SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)
            view.background = StatusBarButtonStyles.createButtonDrawable(
                heightPx = height,
                normalColor = if (active) theme.pressedColor else theme.normalColor,
                pressedColor = theme.pressedColor,
                cornerRadiusRatio = if (gapless) 0f else theme.cornerRadiusRatio,
                borderColor = theme.borderColor,
                borderWidthPx = if (gapless) 0 else theme.borderWidthPx
            )
        }
        when (view) {
            is ImageView -> view.setColorFilter(theme.iconColor)
            is TextView -> view.setTextColor(theme.iconColor)
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
