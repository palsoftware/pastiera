package it.palsoftware.pastiera.inputmethod

import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NestedStatusBarGeometryTest {
    @Test
    fun statusRowNestsInsideIndicatorsAndRestoresWhenDisabledOrExpanded() {
        val context = RuntimeEnvironment.getApplication()
        val chrome = StatusBarController.ImeChromeLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val row = FrameLayout(context)
        val side = LinearLayout(context).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val buttonWrapper = FrameLayout(context)
        val button = android.widget.ImageView(context)
        buttonWrapper.addView(button, FrameLayout.LayoutParams(112, 74))
        side.addView(buttonWrapper, LinearLayout.LayoutParams(112, 74))
        row.addView(side, FrameLayout.LayoutParams(112, -1, android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL))
        val surface = View(context)
        val expanded = View(context).apply { visibility = View.GONE }
        chrome.addView(row, LinearLayout.LayoutParams(-1, 74))
        row.minimumHeight = 74
        chrome.addView(surface, LinearLayout.LayoutParams(-1, 101))
        chrome.surfaceView = surface
        chrome.indicatorView = surface
        chrome.expandedSurfaceView = expanded
        chrome.bottomCornerRadiiPx = 100 to 100
        fun measure() {
            chrome.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            chrome.layout(0, 0, chrome.measuredWidth, chrome.measuredHeight)
        }
        measure()
        assertTrue(row.clipToOutline)
        assertTrue(row.left > 0)
        assertEquals("The status content must start at the top of the bar", 0, row.top)
        assertEquals(0, side.top)
        assertEquals(row.height, side.height)
        assertEquals(0, buttonWrapper.top)
        assertEquals(row.height, button.height)
        assertTrue(row.bottom > surface.top)
        assertEquals("No unused band may remain below the indicators", chrome.height, surface.bottom)
        assertEquals((3.1f * context.resources.displayMetrics.density).toInt(), surface.bottom - row.bottom)
        val nestedHeight = chrome.measuredHeight
        measure()
        assertEquals(nestedHeight, chrome.measuredHeight)

        row.layoutParams = (row.layoutParams as LinearLayout.LayoutParams).apply { height = 140 }
        measure()
        assertEquals("Side lights must begin at the top of a tall row", row.top, surface.top)

        expanded.visibility = View.VISIBLE
        chrome.indicatorView = null
        chrome.requestLayout()
        measure()
        assertEquals(0, row.left)
        assertEquals(row.bottom, surface.top)
        assertTrue(!row.clipToOutline)
        assertEquals(74, row.minimumHeight)

        expanded.visibility = View.GONE
        chrome.bottomCornerRadiiPx = null
        measure()
        assertEquals(0, row.left)
        assertEquals(row.bottom, surface.top)
    }

    @Test
    fun expandedKeyboardOverlaysCornerLightsInsteadOfReservingCornerHeight() {
        val context = RuntimeEnvironment.getApplication()
        val chrome = StatusBarController.ImeChromeLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val surface = FrameLayout(context)
        val stack = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val content = FrameLayout(context)
        val lights = View(context)
        stack.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        stack.addView(lights, LinearLayout.LayoutParams(-1, -2))
        surface.addView(stack, FrameLayout.LayoutParams(-1, -1))
        chrome.addView(surface, LinearLayout.LayoutParams(-1, 400))
        chrome.surfaceView = surface
        chrome.expandedSurfaceView = content
        chrome.indicatorView = lights
        chrome.bottomCornerRadiiPx = 100 to 100
        chrome.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        chrome.layout(0, 0, chrome.measuredWidth, chrome.measuredHeight)
        assertTrue(content.clipToOutline)
        assertEquals((3.1f * context.resources.displayMetrics.density).toInt(), surface.height - content.height)
        assertTrue(lights.top < content.bottom)
        assertEquals(surface.height, lights.bottom)
    }
}
