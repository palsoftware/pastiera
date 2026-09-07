package it.palsoftware.pastiera

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsManagerStatusBarPresentationTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        SettingsManager.getPreferences(context).edit().clear().commit()
    }

    @Test
    fun unifiedPresentationModeRoundTrips() {
        SettingsManager.setStatusBarPresentationMode(
            context,
            SettingsManager.StatusBarPresentationMode.UNIFIED
        )

        assertEquals(
            SettingsManager.StatusBarPresentationMode.UNIFIED,
            SettingsManager.getStatusBarPresentationMode(context)
        )
    }

    @Test
    fun unknownPresentationModeFallsBackToExtended() {
        SettingsManager.getPreferences(context).edit()
            .putString("pastierina_mode_override", "unknown")
            .commit()

        assertEquals(
            SettingsManager.StatusBarPresentationMode.FULL_STATUS_BAR,
            SettingsManager.getStatusBarPresentationMode(context)
        )
    }
}
