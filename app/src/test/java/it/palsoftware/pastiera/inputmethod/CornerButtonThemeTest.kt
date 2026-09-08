package it.palsoftware.pastiera.inputmethod

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.statusbar.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CornerButtonThemeTest {
    @Test
    fun roundedDisplayPreservesButtonThemeAndRefreshesBadgeColors() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setTitan2EliteRoundedCornerInsetsEnabled(context, true)
        val host = StatusBarButtonHost(context, StatusBarButtonRegistry())
        host.themeOverride = StatusBarButtonStyles.ThemeOverride(Color.YELLOW, Color.MAGENTA, Color.BLACK)
        val hosted = requireNotNull(host.getOrCreateButton(StatusBarButtonId.Clipboard, 40, StatusBarCallbacks(), 80, 40))
        val badge = hosted.button.getTag(R.id.tag_badge_view) as TextView
        assertEquals(Color.BLACK, badge.currentTextColor)
        val background = hosted.button.background.current as GradientDrawable
        assertEquals(Color.YELLOW, background.color!!.defaultColor)
        assertEquals(StatusBarButtonStyles.cornerRadiusForSize(40), background.cornerRadius)

        host.themeOverride = StatusBarButtonStyles.ThemeOverride(Color.BLUE, Color.CYAN, Color.WHITE)
        assertEquals(Color.WHITE, badge.currentTextColor)
        assertEquals(Color.BLUE, (hosted.button.background.current as GradientDrawable).color!!.defaultColor)
    }
    @Test
    fun outerIconMovesWithoutLedSurfaceAndRemainsStableAcrossRedrawAndDisable() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        SettingsManager.setTitan2EliteRoundedCornerInsetsEnabled(activity, true)
        val chrome = StatusBarController.ImeChromeLayout(activity).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            bottomCornerRadiiPx = 100 to 100
        }
        activity.setContentView(chrome)
        val host = StatusBarButtonHost(activity, StatusBarButtonRegistry())
        host.themeOverride = StatusBarButtonStyles.ThemeOverride(Color.GRAY, Color.BLUE, Color.WHITE)
        val hosted = requireNotNull(host.getOrCreateButton(StatusBarButtonId.Hamburger, 80, StatusBarCallbacks(), 120, 100))
        host.setOuterEdge(StatusBarButtonId.Hamburger, StatusBarButtonPosition.LEFT)
        chrome.addView(hosted.container, android.widget.LinearLayout.LayoutParams(120, 100))
        val button = hosted.button as android.widget.ImageView
        fun layoutAndDraw(): FloatArray {
            chrome.measure(android.view.View.MeasureSpec.makeMeasureSpec(1000, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY))
            chrome.layout(0, 0, 1000, 100)
            chrome.viewTreeObserver.dispatchOnPreDraw()
            return FloatArray(9).also { button.imageMatrix.getValues(it) }
        }
        val first = layoutAndDraw()
        org.junit.Assert.assertTrue(first[android.graphics.Matrix.MTRANS_X] > (button.width - button.drawable.intrinsicWidth) / 2f)
        repeat(3) { org.junit.Assert.assertArrayEquals(first, layoutAndDraw(), 0.001f) }
        host.setOuterEdge(StatusBarButtonId.Hamburger, null)
        layoutAndDraw()
        assertEquals(android.widget.ImageView.ScaleType.CENTER, button.scaleType)
        host.setOuterEdge(StatusBarButtonId.Hamburger, StatusBarButtonPosition.LEFT)
        org.junit.Assert.assertArrayEquals(first, layoutAndDraw(), 0.001f)
        SettingsManager.setTitan2EliteRoundedCornerInsetsEnabled(activity, false)
        layoutAndDraw()
        assertEquals(android.widget.ImageView.ScaleType.CENTER, button.scaleType)
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun microphoneRetainsThemedContourAcrossRecordingAndThemeChanges() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setTitan2EliteRoundedCornerInsetsEnabled(context, true)
        val host = StatusBarButtonHost(context, StatusBarButtonRegistry())
        host.themeOverride = StatusBarButtonStyles.ThemeOverride(Color.GRAY, Color.BLUE, Color.WHITE)
        val hosted = requireNotNull(host.getOrCreateButton(StatusBarButtonId.Microphone, 80, StatusBarCallbacks(), 120, 100))
        host.setOuterEdge(StatusBarButtonId.Microphone, StatusBarButtonPosition.RIGHT)
        fun color(): Int {
            org.junit.Assert.assertTrue(hosted.button.background is CurvedCornerButtonDrawable)
            val bitmap = android.graphics.Bitmap.createBitmap(120, 100, android.graphics.Bitmap.Config.ARGB_8888)
            hosted.button.background.setBounds(0, 0, 120, 100)
            hosted.button.background.draw(android.graphics.Canvas(bitmap))
            return bitmap.getPixel(60, 50)
        }
        assertEquals(Color.GRAY, color())
        repeat(2) {
            host.setMicrophoneActive(true)
            assertEquals(StatusBarButtonStyles.RECOGNITION_RED, color())
            host.updateMicrophoneAudioLevel(0f)
            assertEquals(Color.rgb(255, 50, 50), color())
            host.themeOverride = StatusBarButtonStyles.ThemeOverride(Color.GREEN, Color.BLUE, Color.WHITE)
            assertEquals(Color.rgb(255, 50, 50), color())
            host.setMicrophoneActive(false)
            assertEquals(Color.GREEN, color())
        }
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun lowRowFindsRoomAndContourIgnoresBottomPaddingAndUpdatesUpperCorners() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        SettingsManager.setTitan2EliteRoundedCornerInsetsEnabled(activity, true)
        SettingsManager.setTitan2EliteTopCornerMultiplier(activity, 2)
        val chrome = StatusBarController.ImeChromeLayout(activity).apply { bottomCornerRadiiPx = 100 to 100 }
        activity.setContentView(chrome)
        val button = android.widget.ImageView(activity)
        chrome.addView(button, android.widget.LinearLayout.LayoutParams(94, 80))
        chrome.layout(0, 0, 1000, 80)
        button.layout(0, 0, 94, 80)
        val background = CurvedCornerButtonDrawable(button, Color.GRAY, Color.BLUE, 10f, null, 0, leftEdge = true)
        button.background = background
        val center = background.contentCenter(54f, 54f, 57f, 40f, true)
        org.junit.Assert.assertTrue("Low rows need an upward correction: $center / ${chrome.width}x${chrome.height} / ${button.width}x${button.height}", center.y < 40f)
        org.junit.Assert.assertTrue("Low rows need an inward correction", center.x > 57f)
        org.junit.Assert.assertTrue(center.scale <= 1f)
        assertEquals(center, background.contentCenter(54f, 54f, 57f, 40f, true))
        fun pixels(): IntArray {
            val bitmap = android.graphics.Bitmap.createBitmap(94, 80, android.graphics.Bitmap.Config.ARGB_8888)
            background.setBounds(0, 0, 94, 80)
            background.draw(android.graphics.Canvas(bitmap))
            return IntArray(94 * 80).also { bitmap.getPixels(it, 0, 94, 0, 0, 94, 80) }
        }
        SettingsManager.setTitan2EliteMaxIconShrink(activity, 0)
        val unshrunk = background.contentCenter(90f, 75f, 57f, 40f, true)
        assertEquals(1f, unshrunk.scale)
        org.junit.Assert.assertTrue(unshrunk.x - 45f >= 0f && unshrunk.x + 45f <= 94f)
        org.junit.Assert.assertTrue(unshrunk.y - 37.5f >= 0f && unshrunk.y + 37.5f <= 80f)
        SettingsManager.setTitan2EliteMaxIconShrink(activity, 20)
        org.junit.Assert.assertTrue(background.contentCenter(90f, 75f, 57f, 40f, true).scale >= 0.8f)
        SettingsManager.setTitan2EliteMaxIconShrink(activity, 90)
        org.junit.Assert.assertTrue(background.contentCenter(90f, 75f, 57f, 40f, true).scale < 0.8f)
        SettingsManager.setTitan2EliteTopCornerMultiplier(activity, 1)
        assertEquals(1, SettingsManager.getTitan2EliteTopCornerMultiplier(activity))
        val singleCorners = pixels()
        SettingsManager.setTitan2EliteTopCornerMultiplier(activity, 2)
        org.junit.Assert.assertFalse(singleCorners.contentEquals(pixels()))
        val beforePadding = pixels()
        chrome.setPadding(0, 0, 0, 20)
        org.junit.Assert.assertArrayEquals(beforePadding, pixels())
        SettingsManager.setTitan2EliteTopCornerMultiplier(activity, 6)
        val strongerCorners = pixels()
        org.junit.Assert.assertFalse(beforePadding.contentEquals(strongerCorners))
        // The upper corner facing the middle must retain exactly its theme shape.
        for (y in 0 until 12) for (x in 82 until 94) {
            val before = beforePadding[y * 94 + x]
            val after = strongerCorners[y * 94 + x]
            // Compare coverage: unpremultiplying low-alpha edge pixels amplifies RGB rounding.
            org.junit.Assert.assertTrue("Inner corner changed at $x,$y",
                kotlin.math.abs(Color.alpha(before) - Color.alpha(after)) <= 1)
        }
        SettingsManager.setTitan2EliteTopCornerMultiplier(activity, 2)
        org.junit.Assert.assertArrayEquals(beforePadding, pixels())
    }

    @Test
    fun languagePaddingAndSizeStayStableAndRestoreWhenMovedInward() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        SettingsManager.setTitan2EliteRoundedCornerInsetsEnabled(activity, true)
        val chrome = StatusBarController.ImeChromeLayout(activity).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            bottomCornerRadiiPx = 100 to 100
        }
        activity.setContentView(chrome)
        val host = StatusBarButtonHost(activity, StatusBarButtonRegistry())
        host.themeOverride = StatusBarButtonStyles.ThemeOverride(Color.GRAY, Color.BLUE, Color.WHITE)
        val hosted = requireNotNull(host.getOrCreateButton(StatusBarButtonId.Language, 80, StatusBarCallbacks(), 94, 80))
        val button = hosted.button as android.widget.TextView
        button.text = "DE"
        val originalSize = button.textSize
        host.setOuterEdge(StatusBarButtonId.Language, StatusBarButtonPosition.LEFT)
        chrome.addView(hosted.container, android.widget.LinearLayout.LayoutParams(94, 80))
        fun draw(): List<Float> {
            chrome.measure(android.view.View.MeasureSpec.makeMeasureSpec(1000, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(80, android.view.View.MeasureSpec.EXACTLY))
            chrome.layout(0, 0, 1000, 80)
            chrome.viewTreeObserver.dispatchOnPreDraw()
            return listOf(button.paddingLeft.toFloat(), button.paddingBottom.toFloat(), button.textSize)
        }
        val initial = draw()
        org.junit.Assert.assertTrue(initial[0] > 0)
        repeat(4) { assertEquals(initial, draw()) }
        host.setOuterEdge(StatusBarButtonId.Language, null)
        draw()
        assertEquals(0, button.paddingLeft)
        assertEquals(0, button.paddingBottom)
        assertEquals(originalSize, button.textSize)
    }

}
