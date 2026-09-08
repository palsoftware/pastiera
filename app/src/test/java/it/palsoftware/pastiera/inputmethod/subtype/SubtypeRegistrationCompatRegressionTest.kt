package it.palsoftware.pastiera.inputmethod.subtype

import android.content.Context
import android.content.ContextWrapper
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29,33])
class SubtypeRegistrationCompatRegressionTest {
    @Suppress("DEPRECATION")
    @Test fun olderAndroidRegistersKoreanWithoutCallingApi34() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = SettingsManager.getPreferences(app)
        prefs.edit().clear().commit()
        val imm = mock(InputMethodManager::class.java)
        val info = mock(InputMethodInfo::class.java)
        val imeId = "${app.packageName}/${PhysicalKeyboardInputMethodService::class.java.name}"
        `when`(info.id).thenReturn(imeId)
        `when`(info.packageName).thenReturn(app.packageName)
        `when`(info.serviceName).thenReturn(PhysicalKeyboardInputMethodService::class.java.name)
        `when`(imm.inputMethodList).thenReturn(listOf(info))
        val context = object : ContextWrapper(app) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.INPUT_METHOD_SERVICE) imm else super.getSystemService(name)
        }
        try {
            SettingsManager.setCustomInputStyles(app,"ko_KR:korean_2set")
            AdditionalSubtypeUtils.registerAdditionalSubtypes(context)
            val captor = ArgumentCaptor.forClass(Array<InputMethodSubtype>::class.java)
            verify(imm).setAdditionalInputMethodSubtypes(eq(imeId),captor.capture())
            assertTrue(captor.value.any { AdditionalSubtypeUtils.getKeyboardLayoutFromSubtype(it) == "korean_2set" })
            assertFalse(mockingDetails(imm).invocations.any { it.method.name == "setExplicitlyEnabledInputMethodSubtypes" })
        } finally { prefs.edit().clear().commit() }
    }
}
