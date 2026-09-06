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
    fun gaplessClipboardUsesAndRefreshesThemeSurfaceAndBadgeColors() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setTitan2EliteRoundedCornerInsetsEnabled(context, true)
        val host = StatusBarButtonHost(context, StatusBarButtonRegistry())
        host.themeOverride = StatusBarButtonStyles.ThemeOverride(Color.YELLOW, Color.MAGENTA, Color.BLACK)
        val hosted = requireNotNull(host.getOrCreateButton(StatusBarButtonId.Clipboard, 40, StatusBarCallbacks(), 80, 40))
        val badge = hosted.button.getTag(R.id.tag_badge_view) as TextView
        assertEquals(Color.BLACK, badge.currentTextColor)
        val background = hosted.button.background.current as GradientDrawable
        assertEquals(Color.YELLOW, background.color!!.defaultColor)
        assertEquals(0f, background.cornerRadius)

        host.themeOverride = StatusBarButtonStyles.ThemeOverride(Color.BLUE, Color.CYAN, Color.WHITE)
        assertEquals(Color.WHITE, badge.currentTextColor)
        assertEquals(Color.BLUE, (hosted.button.background.current as GradientDrawable).color!!.defaultColor)
    }
}
