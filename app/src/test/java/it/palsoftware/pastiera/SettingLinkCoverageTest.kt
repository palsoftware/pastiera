package it.palsoftware.pastiera

import android.content.Context
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingLinkCoverageTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun resetSettings() {
        SettingsManager.getPreferences(context).edit().clear().commit()
    }

    @Test
    fun auditedSystemControlsAreDiscoverableUsingTheirDisplayedTitle() {
        SettingsManager.setNavModeEnabled(context, true)
        val controls = mapOf(
            "nav_mode.enabled" to R.string.nav_mode_enable_title,
            "nav_mode.ctrl_hold" to R.string.nav_mode_ctrl_hold_title,
            "nav_mode.layout_aware_ctrl_shortcuts" to R.string.layout_aware_ctrl_shortcuts_title,
            "accessibility.bounce_keys_delay" to R.string.settings_accessibility_bounce_keys_delay_link_title,
            "trackpad.add_word" to R.string.trackpad_gesture_add_word_title,
            "trackpad.add_word_full_width" to R.string.trackpad_gesture_add_word_full_width_title,
            "trackpad.swipe_to_delete" to R.string.swipe_to_delete_title,
            "trackpad.swipe_to_delete_provider" to R.string.swipe_to_delete_provider_title
        )
        controls.forEach { (id, titleRes) ->
            val results = SettingLinkRegistry.search(context, context.getString(titleRes))
            assertTrue("Search does not expose $id", results.any { it.id == id })
        }
    }

    @Test
    fun hiddenNavControlsResolveToVisibleEnableSwitchAndReturnWhenEnabled() {
        val ids = listOf("nav_mode.ctrl_hold", "nav_mode.layout_aware_ctrl_shortcuts")
        SettingsManager.setNavModeEnabled(context, false)
        ids.forEach { id ->
            val entry = requireNotNull(SettingLinkRegistry.byId(id))
            assertFalse(entry.isAvailable(context))
            assertEquals("nav_mode.enabled", SettingLinkRegistry.visibleTarget(context, entry).id)
            assertFalse(SettingLinkRegistry.search(context, context.getString(entry.titleRes)).any { it.id == id })
        }
        SettingsManager.setNavModeEnabled(context, true)
        ids.forEach { id ->
            val entry = requireNotNull(SettingLinkRegistry.byId(id))
            assertTrue(entry.isAvailable(context))
            assertEquals(id, SettingLinkRegistry.visibleTarget(context, entry).id)
        }
    }

    @Test
    fun disabledButVisibleLayoutShortcutStillResolvesToItsOwnRow() {
        SettingsManager.setNavModeEnabled(context, true)
        SettingsManager.setNavModeCtrlHoldEnabled(context, true)
        val entry = requireNotNull(SettingLinkRegistry.byId("nav_mode.layout_aware_ctrl_shortcuts"))
        assertTrue(entry.isAvailable(context))
        assertEquals(entry.id, SettingLinkRegistry.visibleTarget(context, entry).id)
    }

    @Test
    fun registeredDeviceControlsRetainTheirActualSubscreenInNavigationStack() {
        val expectedTargets = mapOf(
            "on_screen.number_row" to KeyboardsDevicesDestination.OnScreen,
            "hardware.currency" to KeyboardsDevicesDestination.BuiltIn,
            "clicks.backlight" to KeyboardsDevicesDestination.PowerKeyboard
        )
        expectedTargets.forEach { (id, expected) ->
            val route = requireNotNull(SettingLinkRegistry.byId(id)).route
            val stackEntry = route.toSettingsPage()
            assertEquals(SettingsDestination.KeyboardsDevices, stackEntry.destination)
            assertEquals("Incorrect subpage for $id", expected, stackEntry.keyboardsDevicesDestination)
        }
        assertEquals(
            KeyboardsDevicesDestination.Main,
            requireNotNull(SettingLinkRegistry.byId(SettingLinkIds.MAIN_KEYBOARDS_DEVICES))
                .route.toSettingsPage().keyboardsDevicesDestination
        )
    }

    @Test
    fun appLanguageLinkResolvesToLanguageScreen() {
        val entry = requireNotNull(SettingLinkRegistry.byId(SettingLinkIds.MAIN_APP_LANGUAGE))
        assertEquals(SettingsDestination.AppLanguage, entry.route.toSettingsPage().destination)
    }

    @Test
    fun supplementalLiteralEntriesHaveUiReferences() {
        val sources = mainSources()
        val registrySources = sources.filter { it.name.endsWith("SettingEntries.kt") }
        assertTrue("Supplemental registries were not found", registrySources.isNotEmpty())
        val entryPattern = Regex("""SettingEntry\(\s*(?:id\s*=\s*)?"([a-z0-9_]+(?:\.[a-z0-9_]+)+)"""")
        val ids = registrySources.flatMap { source ->
            entryPattern.findAll(source.readText()).map { it.groupValues[1] }.toList()
        }
        assertTrue("No literal supplemental entries were found", ids.isNotEmpty())
        val uiText = sources.filterNot { it in registrySources || it.name == "SettingLinks.kt" }
            .joinToString("\n") { it.readText() }
        ids.forEach { id ->
            assertTrue("Supplemental entry $id has no literal UI reference", uiText.contains("\"$id\""))
        }
    }

    @Test
    fun literalUiHooksResolveInRegistry() {
        // Only complete static literals match; interpolated per-item IDs are deliberately excluded.
        val hookPattern = Regex("""(?:\.settingRow\(\s*|\blinkId\s*=\s*)"([a-z0-9_]+(?:\.[a-z0-9_]+)+)"""")
        val hooks = mainSources().flatMap { source ->
            hookPattern.findAll(source.readText()).map { it.groupValues[1] }.toList()
        }.toSet()
        assertTrue("No literal UI hooks were found", hooks.isNotEmpty())
        hooks.forEach { id ->
            assertTrue("UI hook $id has no registered entry", SettingLinkRegistry.byId(id) != null)
        }
    }

    private fun mainSources(): List<File> {
        val workingDirectory = File(System.getProperty("user.dir"))
        val sourceDirectory = listOf(
            File(workingDirectory, "src/main/java/it/palsoftware/pastiera"),
            File(workingDirectory, "app/src/main/java/it/palsoftware/pastiera")
        ).firstOrNull { it.isDirectory }
        requireNotNull(sourceDirectory) { "Main sources not found relative to $workingDirectory" }
        return sourceDirectory.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

}
