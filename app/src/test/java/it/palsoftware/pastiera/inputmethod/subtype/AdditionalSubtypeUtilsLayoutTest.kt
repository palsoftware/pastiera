package it.palsoftware.pastiera.inputmethod.subtype

import android.os.LocaleList
import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.data.layout.JsonLayoutLoader
import it.palsoftware.pastiera.data.layout.LayoutFileStore
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils.localeString
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdditionalSubtypeUtilsLayoutTest {

    @After
    fun tearDown() {
        val context = RuntimeEnvironment.getApplication()
        LayoutFileStore.getLayoutsDirectory(context).deleteRecursively()
        SettingsManager.getPreferences(context).edit().clear().commit()
        val configuration = context.resources.configuration
        configuration.setLocales(LocaleList(Locale.US))
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
        ShadowLog.clear()
    }

    @Test
    fun germanLocales_resolveToQwertz() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals("qwertz", AdditionalSubtypeUtils.getLayoutForLocale(context.assets, "de", context))
        assertEquals("qwertz", AdditionalSubtypeUtils.getLayoutForLocale(context.assets, "de_DE", context))
        assertEquals("qwertz", AdditionalSubtypeUtils.getLayoutForLocale(context.assets, "de-AT", context))
    }

    @Test
    fun additionalSubtypes_skipBaseLocaleDuplicateLayout() {
        val context = RuntimeEnvironment.getApplication()

        val subtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(
            "en_US:qwerty",
            context.assets,
            context
        )

        assertEquals(0, subtypes.size)
    }

    @Test
    fun additionalSubtypes_keepSameLocaleDifferentLayout() {
        val context = RuntimeEnvironment.getApplication()

        val subtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(
            "en_US:vietnamese_telex_qwerty",
            context.assets,
            context
        )

        assertEquals(1, subtypes.size)
        assertEquals("vietnamese_telex_qwerty", AdditionalSubtypeUtils.getKeyboardLayoutFromSubtype(subtypes[0]))
    }

    @Test
    fun regionalMapping_acceptsBothSeparatorsBeforeLanguageFallback() {
        val context = RuntimeEnvironment.getApplication()
        val mapping = java.io.File(context.filesDir, "locale_layout_mapping.json")
        try {
            for (key in listOf("ko_KR", "ko-KR")) {
                mapping.writeText("""{"$key":"qwerty","ko":"korean_2set"}""")
                for (locale in listOf("ko_KR", "ko-KR")) {
                    assertEquals("qwerty", AdditionalSubtypeUtils.getLayoutForLocale(context.assets, locale, context))
                }
                assertEquals("korean_2set", AdditionalSubtypeUtils.getLayoutForLocale(context.assets, "ko", context))
            }
        } finally {
            mapping.delete()
        }
        assertEquals("azerty", AdditionalSubtypeUtils.getLayoutForLocale(context.assets, "fr-FR", context))
    }

    @Test
    fun koreanPickerAndSavedRegionalLocales_resolveAndRegisterWithoutDictionary() {
        val context = RuntimeEnvironment.getApplication()
        for ((locale, languageTag) in listOf("ko" to "ko", "ko_KR" to "ko-KR", "ko-KR" to "ko-KR")) {
            assertEquals("korean_2set", AdditionalSubtypeUtils.getLayoutForLocale(context.assets, locale, context))
            val subtype = AdditionalSubtypeUtils.createAdditionalSubtypesArray(
                "$locale:korean_2set", context.assets, context
            ).single()
            assertEquals(languageTag, subtype.localeString())
            assertEquals("korean_2set", AdditionalSubtypeUtils.getKeyboardLayoutFromSubtype(subtype))
            assertTrue(AdditionalSubtypeUtils.matchesLocaleAndKeyboardLayoutSet(subtype, locale, "korean_2set"))
        }
    }

    @Test
    fun koreanSystemLocale_withoutDictionary_isAutoAddedAsDynamicSubtype() {
        val context = RuntimeEnvironment.getApplication()
        val configuration = context.resources.configuration
        configuration.setLocales(LocaleList(Locale.KOREA))
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
        SettingsManager.setCustomInputStyles(context, "en:qwerty")

        AdditionalSubtypeUtils.autoAddSystemLocalesWithoutDictionary(context)

        assertEquals("en:qwerty;ko_KR:korean_2set", SettingsManager.getCustomInputStyles(context))
        val subtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(
            SettingsManager.getCustomInputStyles(context),
            context.assets,
            context
        )
        assertTrue(subtypes.any { subtype ->
            subtype.localeString() == "ko-KR" &&
                AdditionalSubtypeUtils.getKeyboardLayoutFromSubtype(subtype) == "korean_2set" &&
                subtype.isAsciiCapable
        })
    }

    @Test
    fun turkishSystemLocale_withoutDictionary_usesSameDynamicSubtypeConvention() {
        val context = RuntimeEnvironment.getApplication()
        val configuration = context.resources.configuration
        configuration.setLocales(LocaleList(Locale.forLanguageTag("tr-TR")))
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
        SettingsManager.setCustomInputStyles(context, "")

        AdditionalSubtypeUtils.autoAddSystemLocalesWithoutDictionary(context)

        assertEquals("tr_TR:turkish_multitap", SettingsManager.getCustomInputStyles(context))
    }

    @Test
    fun removingAutoAddedSystemLocale_preservesUserStyleForSameLocale() {
        val context = RuntimeEnvironment.getApplication()
        val configuration = context.resources.configuration
        configuration.setLocales(LocaleList(Locale.KOREA))
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
        SettingsManager.setCustomInputStyles(context, "")

        AdditionalSubtypeUtils.autoAddSystemLocalesWithoutDictionary(context)
        SettingsManager.setCustomInputStyles(
            context,
            "${SettingsManager.getCustomInputStyles(context)};ko_KR:qwerty"
        )

        configuration.setLocales(LocaleList(Locale.US))
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
        AdditionalSubtypeUtils.removeSystemLocalesWithoutDictionary(context)

        assertEquals("ko_KR:qwerty", SettingsManager.getCustomInputStyles(context))
    }

    @Test
    fun subtypeMatching_usesLocaleAndLayout() {
        val context = RuntimeEnvironment.getApplication()
        val subtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(
            "en_US:vietnamese_telex_qwerty",
            context.assets,
            context
        )

        assertEquals(
            true,
            AdditionalSubtypeUtils.matchesLocaleAndKeyboardLayoutSet(
                subtypes[0],
                "en_US",
                "vietnamese_telex_qwerty"
            )
        )
        assertEquals(
            false,
            AdditionalSubtypeUtils.matchesLocaleAndKeyboardLayoutSet(
                subtypes[0],
                "en_US",
                "qwerty"
            )
        )
    }

    @Test
    fun traversalLikeCustomDisplayNameNeverBecomesAssetPath() {
        val context = RuntimeEnvironment.getApplication()
        val layoutId = "../../Русский путь 273"
        val json = """{"name":"Русский путь 273","mappings":{"KEYCODE_Q":{"lowercase":"й","uppercase":"Й"}}}"""
        assertTrue(
            LayoutFileStore.saveLayoutFromJson(context, layoutId, json)
                is LayoutFileStore.LayoutImportResult.Success
        )
        ShadowLog.clear()

        val displayName = AdditionalSubtypeUtils.buildSubtypeDisplayName(
            context,
            context.assets,
            Locale.forLanguageTag("ru"),
            "ru",
            layoutId
        )
        val subtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(
            "ru:$layoutId",
            context.assets,
            context
        )
        val mapping = JsonLayoutLoader.loadLayout(context.assets, layoutId, context)

        assertTrue(displayName.endsWith(" · Русский путь 273"))
        assertEquals(1, subtypes.size)
        assertEquals(layoutId, AdditionalSubtypeUtils.getKeyboardLayoutFromSubtype(subtypes[0]))
        assertEquals("й", mapping?.get(KeyEvent.KEYCODE_Q)?.lowercase)
        assertFalse(
            ShadowLog.getLogsForTag("LayoutFileStore").any { log ->
                log.msg.contains("Error getting layout metadata from assets") ||
                    log.throwable is java.io.FileNotFoundException
            }
        )
    }
}
