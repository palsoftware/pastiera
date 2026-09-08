package it.palsoftware.pastiera.inputmethod

import android.graphics.Rect
import android.inputmethodservice.InputMethodService

internal object ImeInsetsPolicy {
    fun applyRenderedContentInsets(insets: InputMethodService.Insets, bounds: Rect?, windowHeight: Int) {
        val visible = bounds?.takeIf { it.width() > 0 && it.height() > 0 }
        insets.contentTopInsets = visible?.top ?: windowHeight
        insets.visibleTopInsets = insets.contentTopInsets
        insets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
        if (visible == null) insets.touchableRegion.setEmpty()
        else insets.touchableRegion.set(visible)
    }

}
