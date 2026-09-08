package it.palsoftware.pastiera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomizationSettingLinkTest {
    private val entries = customizationSettingEntries().associateBy { it.id }

    @Test
    fun ledLinksPreserveTheEditedKeyboardTarget() {
        for (target in SettingsManager.KeyboardThemeTarget.values()) {
            val entry = entries.getValue("keyboard_theme.${target.name.lowercase()}.led_colors")
            assertEquals(target, entry.route.keyboardThemeTarget)
            assertEquals(KeyboardThemeEditorTab.Colors, entry.route.keyboardThemeTab)
            assertEquals("keyboard_theme", entry.route.customizationDestination)
        }
        assertNotNull(SettingLinkRegistry.byId(SettingLinkIds.KEYBOARD_THEME_LED_COLORS))
    }

    @Test
    fun nestedEditorsHaveDirectRoutes() {
        val expected = mapOf(
            "quick_launcher.auto_start_single" to "launcher_shortcut_behavior",
            "quick_launcher.width" to "launcher_shortcut_cosmetic",
            "quick_launcher.assignments" to "launcher_shortcut_assignments",
            "sounds.typing_mode" to "sounds",
            "variations.layout_override" to "variations",
            "keyboard_theme.hardware.assignment" to "keyboard_theme_assignment",
            "keyboard_theme.software.layout_overrides" to "keyboard_theme_assignment"
        )
        expected.forEach { (id, route) ->
            assertEquals(id, route, entries.getValue(id).route.customizationDestination)
        }
    }

    @Test
    fun conditionalControlsHaveAReachableVisibleParent() {
        entries.values.filter { it.availabilityCheck != null }.forEach { entry ->
            val visited = mutableSetOf<String>()
            var candidate = entry
            while (candidate.availabilityCheck != null) {
                assertTrue("Fallback cycle from ${entry.id}", visited.add(candidate.id))
                assertNotNull("Missing fallback for ${candidate.id}", candidate.unavailableFallbackId)
                candidate = entries.getValue(candidate.unavailableFallbackId!!)
            }
        }
    }
}
