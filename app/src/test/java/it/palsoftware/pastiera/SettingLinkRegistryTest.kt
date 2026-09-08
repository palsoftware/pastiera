package it.palsoftware.pastiera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingLinkRegistryTest {

    @Test
    fun idsAreUnique() {
        val ids = SettingLinkRegistry.entries.map { it.id }
        assertEquals("setting link ids must be unique", ids.size, ids.toSet().size)
    }

    @Test
    fun idsFollowStableNamingScheme() {
        val pattern = Regex("^[a-z0-9_]+(\\.[a-z0-9_]+)+$")
        SettingLinkRegistry.entries.forEach { entry ->
            assertTrue("id '${entry.id}' violates the naming scheme", pattern.matches(entry.id))
        }
    }

    @Test
    fun everyRouteDestinationHasBreadcrumbTitle() {
        SettingLinkRegistry.entries.forEach { entry ->
            assertTrue(
                "destination ${entry.route.destination} of '${entry.id}' lacks a breadcrumb title",
                SettingLinkRegistry.destinationTitles.containsKey(entry.route.destination)
            )
        }
    }

    @Test
    fun everyCustomizationSubRouteHasBreadcrumbSubtitle() {
        SettingLinkRegistry.entries
            .mapNotNull { it.route.customizationDestination }
            .forEach { sub ->
                assertTrue(
                    "customization destination '$sub' lacks a breadcrumb subtitle",
                    SettingLinkRegistry.customizationSubtitles.containsKey(sub)
                )
            }
    }

    @Test
    fun buildLinkAndParseLinkRoundTrip() {
        SettingLinkRegistry.entries.forEach { entry ->
            assertEquals("https://pastiera.eu/settings/${entry.id}", SettingLinkRegistry.buildLink(entry.id))
            for (host in listOf("pkb.rocks", "pastiera.eu")) {
                assertEquals(
                    entry.id,
                    SettingLinkRegistry.parseSettingLink("https", host, "/settings/${entry.id}")
                )
            }
            assertEquals(
                entry.id,
                SettingLinkRegistry.parseSettingLink("pastiera", "setting", "/${entry.id}")
            )
        }
    }

    @Test
    fun parseLinkRejectsForeignSchemeHostAndEmptyPath() {
        assertNull(SettingLinkRegistry.parseSettingLink("https", "setting", "/text_input.auto_capitalize"))
        assertNull(SettingLinkRegistry.parseSettingLink("pastiera", "settings", "/text_input.auto_capitalize"))
        assertNull(SettingLinkRegistry.parseSettingLink("pastiera", "setting", "/"))
        assertNull(SettingLinkRegistry.parseSettingLink("pastiera", "setting", null))
    }

    @Test
    fun parseLinkAcceptsPathWithoutLeadingSlashAndTrimsWhitespace() {
        assertEquals(
            "text_input.auto_capitalize",
            SettingLinkRegistry.parseSettingLink("pastiera", "setting", " /text_input.auto_capitalize ")
        )
    }

    @Test
    fun webLinksRejectOtherOriginsAndMalformedPaths() {
        val id = "text_input.auto_capitalize"
        for (host in listOf("www.pkb.rocks", "pkb.rocks.example.org", "example.org")) {
            assertNull(SettingLinkRegistry.parseSettingLink("https", host, "/settings/$id"))
        }
        for (host in listOf("pkb.rocks", "pastiera.eu")) {
            assertNull(SettingLinkRegistry.parseSettingLink("http", host, "/settings/$id"))
            for (path in listOf("/", "/settings/", "/setting/$id", "/settings/$id/extra",
                "/settings/$id/", "/settings/../$id", "/settings/ $id", "/settingsx/$id")) {
                assertNull(SettingLinkRegistry.parseSettingLink("https", host, path))
            }
        }
    }

    @Test
    fun unknownIdResolvesToNull() {
        assertNull(SettingLinkRegistry.byId("no.such_entry"))
    }

    @Test
    fun buildSettingMarkdownUsesLabelWithDescriptionAndLinkWithout() {
        val link = "pastiera://setting/text_input.auto_capitalize"
        assertEquals(
            "[Auto capitalize]($link)",
            SettingLinkRegistry.buildSettingMarkdown("Auto capitalize", link, withDescription = true)
        )
        assertEquals(
            "[$link]($link)",
            SettingLinkRegistry.buildSettingMarkdown("Auto capitalize", link, withDescription = false)
        )
    }

    @Test
    fun buildSettingMarkdownEscapesBracketsInLabel() {
        val link = "pastiera://setting/main.about"
        assertEquals(
            "[\\[Advanced\\] stuff]($link)",
            SettingLinkRegistry.buildSettingMarkdown("[Advanced] stuff", link, withDescription = true)
        )
    }

    @Test
    fun scoreMatchPrefersTitlePrefixOverWordPrefixOverSubstringOverSummary() {
        val title = "Auto capitalize"
        val summary = "Capitalize the first letter of a sentence"

        assertEquals(6, SettingLinkRegistry.scoreMatch(title, summary, "auto") ?: -1)
        assertEquals(4, SettingLinkRegistry.scoreMatch(title, summary, "cap") ?: -1)
        assertEquals(2, SettingLinkRegistry.scoreMatch(title, summary, "pitaliz") ?: -1)
        assertEquals(1, SettingLinkRegistry.scoreMatch(title, summary, "sentence") ?: -1)
        assertEquals(0, SettingLinkRegistry.scoreMatch(title, summary, "entenc") ?: -1)
        assertNull(SettingLinkRegistry.scoreMatch(title, summary, "zzz"))
        assertNull(SettingLinkRegistry.scoreMatch(title, summary, ""))
    }

    @Test
    fun scoreMatchSumsTokensAndRequiresEveryTokenToMatch() {
        val title = "Auto capitalize"
        val summary = "Capitalize the first letter of a sentence"

        assertEquals(10, SettingLinkRegistry.scoreMatch(title, summary, "auto capitalize") ?: -1)
        assertEquals(7, SettingLinkRegistry.scoreMatch(title, summary, "auto sentence") ?: -1)
        assertNull(SettingLinkRegistry.scoreMatch(title, summary, "auto zzz"))
    }

    @Test
    fun scoreMatchFoldsDiacriticsCaseAndSharpS() {
        assertEquals(
            SettingLinkRegistry.scoreMatch("Grosse", null, "grosse"),
            SettingLinkRegistry.scoreMatch("Größe", null, "grosse")
        )
        assertEquals(6, SettingLinkRegistry.scoreMatch("Éclairage", null, "eclairage") ?: -1)
    }

    @Test
    fun scoreMatchConsidersKeywordsLikeSummary() {
        val title = "Menubar"
        val keywords = "LED, lights, icon"

        assertEquals(1, SettingLinkRegistry.scoreMatch(title, null, "led", keywords = keywords) ?: -1)
        assertEquals(0, SettingLinkRegistry.scoreMatch(title, null, "igh", keywords = keywords) ?: -1)
        assertNull(SettingLinkRegistry.scoreMatch(title, null, "led"))
    }

    @Test
    fun everyKeywordAssignmentTargetsARegisteredEntry() {
        SettingLinkRegistry.keywordsById.keys.forEach { id ->
            assertTrue(
                "keyword assignment targets unknown entry id '$id'",
                SettingLinkRegistry.byId(id) != null
            )
        }
    }

    @Test
    fun conditionalEntriesHaveRegisteredVisibleFallbacks() {
        SettingLinkRegistry.entries
            .filter { it.availability != SettingAvailability.Always }
            .forEach { entry ->
                val fallbackId = entry.unavailableFallbackId
                assertTrue("conditional entry '${entry.id}' needs a fallback", fallbackId != null)
                assertTrue(
                    "fallback '$fallbackId' of '${entry.id}' is not registered",
                    fallbackId?.let(SettingLinkRegistry::byId) != null
                )
            }
    }

    @Test
    fun trackpadDetailLinksRouteThroughAdvancedSettings() {
        val trackpadIds = setOf(
            SettingLinkIds.TRACKPAD_GESTURES_ENABLED,
            SettingLinkIds.TRACKPAD_PROVIDER,
            SettingLinkIds.TRACKPAD_SHIZUKU_DEVICE,
            SettingLinkIds.TRACKPAD_SENSITIVITY,
            SettingLinkIds.TRACKPAD_SUGGESTION_SWIPE_THRESHOLD,
            SettingLinkIds.TRACKPAD_DELETE_SWIPE_THRESHOLD,
            SettingLinkIds.TRACKPAD_DEBUG
        )

        trackpadIds.forEach { id ->
            assertEquals(
                SettingsDestination.Advanced,
                requireNotNull(SettingLinkRegistry.byId(id)).route.destination
            )
        }
    }

    @Test
    fun softwareThemeTogglesRouteToKeysTab() {
        val toggleIds = setOf(
            SettingLinkIds.KEYBOARD_THEME_TOGGLE_SHOW_LEDS,
            SettingLinkIds.KEYBOARD_THEME_TOGGLE_DISTRIBUTE_SPACING,
            SettingLinkIds.KEYBOARD_THEME_TOGGLE_ORTHOLINEAR,
            SettingLinkIds.KEYBOARD_THEME_TOGGLE_ATTACH_POPUP,
            SettingLinkIds.KEYBOARD_THEME_TOGGLE_POPUP_TAIL,
            SettingLinkIds.KEYBOARD_THEME_TOGGLE_PREVIEW_ON_HOLD,
            SettingLinkIds.KEYBOARD_THEME_TOGGLE_CHARACTER_PICKER
        )

        toggleIds.forEach { id ->
            val route = requireNotNull(SettingLinkRegistry.byId(id)).route
            assertEquals(SettingsManager.KeyboardThemeTarget.SOFTWARE, route.keyboardThemeTarget)
            assertEquals(KeyboardThemeEditorTab.Keys, route.keyboardThemeTab)
        }
    }

    @Test
    fun ledColorsRouteToColorsTab() {
        val route = requireNotNull(
            SettingLinkRegistry.byId(SettingLinkIds.KEYBOARD_THEME_LED_COLORS)
        ).route

        assertEquals(KeyboardThemeEditorTab.Colors, route.keyboardThemeTab)
    }
}
