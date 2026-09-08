package it.palsoftware.pastiera.inputmethod.subtype

import android.content.Context
import android.content.ContextWrapper
import android.os.LocaleList
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodService
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubtypeRegistrationAuditRegressionTest {
    private val app = RuntimeEnvironment.getApplication()
    private val imm = mock(InputMethodManager::class.java)
    private val info = mock(InputMethodInfo::class.java)
    private val imeId = "${app.packageName}/${PhysicalKeyboardInputMethodService::class.java.name}"
    private val en = subtype("en_US")
    private val it = subtype("it_IT")
    private val ru = subtype("ru_RU")
    private val staticSubtypes = listOf(en, it, ru)
    private var enabled: List<InputMethodSubtype> = listOf(en, it)
    private var lastExplicit = intArrayOf()
    private val context = object : ContextWrapper(app) {
        override fun getSystemService(name: String): Any? = if (name == Context.INPUT_METHOD_SERVICE) imm else super.getSystemService(name)
    }

    init {
        SettingsManager.getPreferences(app).edit().clear().commit()
        val configuration = app.resources.configuration
        configuration.setLocales(LocaleList(Locale.US, Locale.ITALY))
        app.resources.updateConfiguration(configuration, app.resources.displayMetrics)
        `when`(info.id).thenReturn(imeId)
        `when`(info.packageName).thenReturn(app.packageName)
        `when`(info.serviceName).thenReturn(PhysicalKeyboardInputMethodService::class.java.name)
        `when`(info.subtypeCount).thenReturn(staticSubtypes.size)
        staticSubtypes.forEachIndexed { index, subtype -> `when`(info.getSubtypeAt(index)).thenReturn(subtype) }
        `when`(imm.inputMethodList).thenReturn(listOf(info))
        `when`(imm.getEnabledInputMethodSubtypeList(info, true)).thenAnswer { enabled }
        doAnswer { call ->
            lastExplicit = call.getArgument<IntArray>(1)
            enabled = staticSubtypes.filter { it.hashCode() in lastExplicit }
            null
        }.`when`(imm).setExplicitlyEnabledInputMethodSubtypes(eq(imeId), any(IntArray::class.java))
    }

    @After fun cleanup() {
        SettingsManager.getPreferences(app).edit().clear().commit()
    }

    @Test fun systemStyleHiddenThenShown_isEnabledAgain() {
        SettingsManager.hideSystemInputStyle(app, "it_IT", "qwerty")
        sync()
        assertTrue("Hide should remove Italian", it.hashCode() !in lastExplicit)
        SettingsManager.showSystemInputStyle(app, "it_IT", "qwerty")
        sync()
        assertTrue("Visible Italian system style must be restored to the enabled list", it.hashCode() in lastExplicit)
    }

    @Test fun addedSystemLocale_isEnabledWithExistingExplicitEnglish() {
        enabled = listOf(en)
        sync()
        assertTrue("New Italian system locale must be added even when English was explicitly enabled", it.hashCode() in lastExplicit)
    }

    @Test fun legacyRussianSelection_isNotDiscardedAsRedundantStaticSubtype() {
        val configuration = app.resources.configuration
        configuration.setLocales(LocaleList(Locale.US))
        app.resources.updateConfiguration(configuration, app.resources.displayMetrics)
        enabled = listOf(en)
        SettingsManager.setCustomInputStyles(app, "")
        SettingsManager.setAdditionalImeSubtypes(app, setOf("ru"))
        AdditionalSubtypeUtils.registerAdditionalSubtypes(context)
        assertTrue("Russian selected in Languages must be enabled", ru.hashCode() in lastExplicit)
    }

    @Test fun explicitlySelectedStaticStyleIsRestoredWithoutBecomingSystemLocale() {
        enabled = listOf(en)
        SettingsManager.setCustomInputStyles(app, "ru_RU:russian_translit")
        AdditionalSubtypeUtils.registerAdditionalSubtypes(context)
        assertTrue(ru.hashCode() in lastExplicit)
    }

    @Test fun dynamicKoreanStyleIsRegisteredAndEnabledAlongsideLatin() {
        SettingsManager.setCustomInputStyles(app,"ko_KR:korean_2set")
        AdditionalSubtypeUtils.registerAdditionalSubtypes(context)
        val korean = AdditionalSubtypeUtils.createAdditionalSubtypesArray("ko_KR:korean_2set",app.assets,app).single()
        assertTrue(korean.hashCode() in lastExplicit)
        assertTrue(en.hashCode() in lastExplicit)
    }

    private fun sync() {
        val method = AdditionalSubtypeUtils::class.java.declaredMethods.single { it.name == "syncExplicitlyEnabledSubtypes" }
        method.isAccessible = true
        method.invoke(AdditionalSubtypeUtils, app, imm, info.id, info, emptyArray<InputMethodSubtype>(), "")
    }

    private fun subtype(locale: String): InputMethodSubtype = InputMethodSubtype.InputMethodSubtypeBuilder()
        .setSubtypeLocale(locale)
        .setSubtypeMode("keyboard")
        .setSubtypeExtraValue("noSuggestions=true")
        .build()
}
