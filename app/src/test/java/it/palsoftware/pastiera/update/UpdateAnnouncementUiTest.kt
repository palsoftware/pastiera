package it.palsoftware.pastiera.update

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class UpdateAnnouncementUiTest {

    @Test
    fun dialogNamesPlektraAndOnlyOpensTheReleasePage() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val releaseUrl = "https://github.com/pkb-rocks/plektra-updater-e2e-test/releases/tag/v0.87-e2e.1"

        showUpdateDialog(activity, "v0.87-e2e.1", "TEST ONLY - Plektra successor announcement", releaseUrl)

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(activity.getString(R.string.successor_dialog_title), shadowOf(dialog).title)
        assertTrue(shadowOf(dialog).message.toString().contains("Plektra"))
        assertTrue(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).text.isNullOrEmpty())

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(releaseUrl, shadowOf(activity).nextStartedActivity.data.toString())
    }

    @Test
    fun laterDismissesOnlyTheReleaseTag() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        showUpdateDialog(activity, "v0.87-e2e.1", "Named release", "https://example.com/release")
        ShadowAlertDialog.getLatestAlertDialog()
            .getButton(AlertDialog.BUTTON_NEUTRAL)
            .performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(SettingsManager.isReleaseDismissed(activity, "v0.87-e2e.1"))
        assertFalse(SettingsManager.isReleaseDismissed(activity, "Named release"))
    }

    @Test
    fun notificationNamesPlektraAndOpensTheReleasePage() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val releaseUrl = "https://example.com/plektra-release"

        NotificationHelper.showUpdateAvailableNotification(
            context = activity,
            displayName = "Plektra 0.87",
            releasePageUrl = releaseUrl
        )

        val manager = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = shadowOf(manager).allNotifications.single()
        assertEquals(activity.getString(R.string.notification_successor_release_title), notification.extras.getString("android.title"))
        assertTrue(notification.extras.getString("android.text").orEmpty().contains("Plektra 0.87"))

        notification.contentIntent.send()
        assertEquals(releaseUrl, shadowOf(activity).nextStartedActivity.data.toString())
    }
}
