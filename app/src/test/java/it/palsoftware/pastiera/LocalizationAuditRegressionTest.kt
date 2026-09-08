package it.palsoftware.pastiera

import android.content.Context
import android.content.res.Configuration
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocalizationAuditRegressionTest {
    private fun localizedContext(tag: String): Context {
        val app = RuntimeEnvironment.getApplication()
        val configuration = Configuration(app.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return app.createConfigurationContext(configuration)
    }
    private fun dictionaryLocales(context: Context): List<*> {
        val method = Class.forName("it.palsoftware.pastiera.CustomInputStylesScreenKt")
            .getDeclaredMethod("getLocalesWithDictionary", Context::class.java)
        method.isAccessible = true
        return method.invoke(null, context) as List<*>
    }

    @Test fun dictionaryCandidatesRequireInstalledDataButAcceptImportedKorean() {
        val context = localizedContext("ko")
        val file = File(context.filesDir,"dictionaries_serialized/custom/ko_base.dict")
        file.delete()
        assertFalse(dictionaryLocales(context).any { it.toString().startsWith("ko") })
        assertTrue(dictionaryLocales(context).contains("en"))
        try {
            file.parentFile!!.mkdirs()
            // Inventory checks presence only; dictionary decoding has its own tests.
            file.writeBytes(byteArrayOf(1))
            assertTrue(dictionaryLocales(context).contains("ko"))
        } finally { file.delete() }
    }

    @Test fun inputLanguagePickerUsesBaseKoreanCodeWithOrWithoutImportedDictionary() {
        val context = localizedContext("en")
        val dictionaries = dictionaryLocales(context).map { it.toString() }.filterNot { it == "ko" }
        for (installed in listOf(dictionaries, dictionaries + "ko")) {
            val locales = getAvailableInputLocales(installed)
            assertEquals(listOf("ko"), locales.filter { it.startsWith("ko") })
        }
    }

    @Test fun dictionaryWarningsRecognizeBaseAndBothRegionalLocaleFormats() {
        val context = localizedContext("ko")
        val method = Class.forName("it.palsoftware.pastiera.CustomInputStylesScreenKt")
            .getDeclaredMethod("hasDictionaryForLocale", Context::class.java, String::class.java)
            .apply { isAccessible = true }
        val file = File(context.filesDir, "dictionaries_serialized/custom/ko_base.dict")
        try {
            file.parentFile!!.mkdirs()
            file.writeBytes(byteArrayOf(1))
            for (tag in listOf("ko", "ko_KR", "ko-KR", "en", "en_US", "en-US")) {
                assertEquals(tag, true, method.invoke(null, context, tag))
            }
        } finally {
            file.delete()
        }
    }

    @Test fun editingActiveRegionalLayoutNotifiesImeAcrossLocaleSeparators() {
        val app = RuntimeEnvironment.getApplication()
        val imm = org.mockito.Mockito.mock(android.view.inputmethod.InputMethodManager::class.java)
        val subtype = android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder()
            .setLanguageTag("ko-KR").setSubtypeMode("keyboard").build()
        org.mockito.Mockito.`when`(imm.currentInputMethodSubtype).thenReturn(subtype)
        val context = object : android.content.ContextWrapper(app) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.INPUT_METHOD_SERVICE) imm else super.getSystemService(name)
        }
        val update = Class.forName("it.palsoftware.pastiera.CustomInputStylesScreenKt")
            .getDeclaredMethod("updateLocaleLayoutMapping", Context::class.java, String::class.java, String::class.java)
            .apply { isAccessible = true }
        val prefs = SettingsManager.getPreferences(context)
        val key = SettingsManager.KEY_KEYBOARD_LAYOUT_AUTO_MAPPING_UPDATED
        try {
            SettingsManager.setKeyboardLayoutAutoByLocale(context, true)
            prefs.edit().remove(key).commit()
            update.invoke(null, context, "ko_KR", "qwerty")
            assertTrue("Changing the active regional mapping must refresh the IME", prefs.contains(key))
            prefs.edit().remove(key).commit()
            update.invoke(null, context, "en_US", "azerty")
            assertFalse("An unrelated language must not refresh the active IME", prefs.contains(key))
            SettingsManager.setKeyboardLayoutAutoByLocale(context, false)
            update.invoke(null, context, "ko_KR", "korean_2set")
            assertFalse("Manual layout selection must not be overridden", prefs.contains(key))
        } finally {
            File(context.filesDir, "locale_layout_mapping.json").delete()
            prefs.edit().clear().commit()
        }
    }
}
