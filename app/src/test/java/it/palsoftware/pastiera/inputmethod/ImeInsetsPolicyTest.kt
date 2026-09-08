package it.palsoftware.pastiera.inputmethod

import android.inputmethodservice.InputMethodService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImeInsetsPolicyTest {
    @Test
    fun renderedBarReservesItsHeightAndRestrictsTouchToActualBounds() {
        val insets = InputMethodService.Insets().apply {
            contentTopInsets = 600
            visibleTopInsets = 0
            touchableRegion = android.graphics.Region()
        }
        val bounds = android.graphics.Rect(12, 492, 708, 600)

        ImeInsetsPolicy.applyRenderedContentInsets(insets, bounds, 600)

        assertEquals(108, 600 - insets.contentTopInsets)
        assertEquals(492, insets.visibleTopInsets)
        assertEquals(InputMethodService.Insets.TOUCHABLE_INSETS_REGION, insets.touchableInsets)
        assertEquals(bounds, insets.touchableRegion.bounds)
    }

    @Test
    fun missingOrEmptyContentClearsStaleTouchAreaAndReservedHeight() {
        for (bounds in listOf(null, android.graphics.Rect(0, 0, 720, 0), android.graphics.Rect(0, 0, 0, 108))) {
            val insets = InputMethodService.Insets().apply {
                contentTopInsets = 0
                visibleTopInsets = 0
                touchableRegion = android.graphics.Region(0, 0, 720, 108)
            }

            ImeInsetsPolicy.applyRenderedContentInsets(insets, bounds, 108)

            assertEquals(108, insets.contentTopInsets)
            assertEquals(108, insets.visibleTopInsets)
            assertEquals(InputMethodService.Insets.TOUCHABLE_INSETS_REGION, insets.touchableInsets)
            assertTrue(insets.touchableRegion.isEmpty)
        }
    }

}
