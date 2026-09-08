package it.palsoftware.pastiera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppEnterBehaviorPresetTest {

    @Test
    fun matchingKnownAppBehaviorsRestoreRegularPreset() {
        val overrides = listOf(
            override("com.whatsapp", SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND),
            override("com.facebook.orca", SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND)
        )

        assertEquals(
            SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_CTRL_SEND,
            inferKnownAppEnterBehaviorPreset(overrides)
        )
    }

    @Test
    fun differingKnownAppBehaviorsAreCustom() {
        val overrides = listOf(
            override("com.whatsapp", SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND),
            override("com.facebook.orca", SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE)
        )

        assertEquals(
            SettingsManager.ENTER_BEHAVIOR_PRESET_CUSTOM,
            inferKnownAppEnterBehaviorPreset(overrides)
        )
    }

    @Test
    fun manuallyAddedAppsDoNotChangeKnownAppPreset() {
        val overrides = listOf(
            override("com.whatsapp", SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND),
            override("com.example.chat", SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE)
        )

        assertEquals(
            SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_CTRL_SEND,
            inferKnownAppEnterBehaviorPreset(overrides)
        )
    }

    @Test
    fun noKnownAppsHasNoDerivedPreset() {
        assertNull(
            inferKnownAppEnterBehaviorPreset(
                listOf(override("com.example.chat", SettingsManager.ENTER_BEHAVIOR_APP_DEFAULT))
            )
        )
    }

    @Test
    fun mixedAdditionalSendShortcutsAreCustom() {
        val overrides = listOf(
            SettingsManager.AppEnterBehaviorOverride(
                packageName = "com.whatsapp",
                behavior = SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND,
                additionalSendShortcut = SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_NONE
            ),
            SettingsManager.AppEnterBehaviorOverride(
                packageName = "com.facebook.orca",
                behavior = SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND,
                additionalSendShortcut = SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_SYM_ENTER
            )
        )

        assertEquals(
            ENTER_ADDITIONAL_SEND_SHORTCUT_CUSTOM,
            commonAdditionalSendShortcut(overrides)
        )
    }

    @Test
    fun newAppUsesNoneWhenAdditionalSendShortcutsAreCustom() {
        assertEquals(
            SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_NONE,
            additionalSendShortcutForNewApp(ENTER_ADDITIONAL_SEND_SHORTCUT_CUSTOM)
        )
    }

    private fun override(packageName: String, behavior: String) =
        SettingsManager.AppEnterBehaviorOverride(packageName, behavior)
}
