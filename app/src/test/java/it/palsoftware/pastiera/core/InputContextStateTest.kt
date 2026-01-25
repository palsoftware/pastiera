package it.palsoftware.pastiera.core

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InputContextStateTest {

    @Test
    fun testFromEditorInfoNormalText() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        }
        val state = InputContextState.fromEditorInfo(info)
        
        assertTrue(state.isEditable)
        assertTrue(state.isReallyEditable)
        assertFalse(state.isPasswordField)
        assertNull(state.restrictedReason)
        assertFalse(state.shouldDisableSuggestions)
    }

    @Test
    fun testFromEditorInfoPassword() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val state = InputContextState.fromEditorInfo(info)
        
        assertTrue(state.isEditable)
        assertTrue(state.isPasswordField)
        assertEquals(InputContextState.RestrictedReason.PASSWORD, state.restrictedReason)
        assertTrue(state.shouldDisableSuggestions)
        assertTrue(state.shouldDisableAutoCorrect)
        assertTrue(state.shouldDisableAutoCapitalize)
    }

    @Test
    fun testFromEditorInfoEmail() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val state = InputContextState.fromEditorInfo(info)
        
        assertTrue(state.isEmailField)
        assertEquals(InputContextState.RestrictedReason.EMAIL, state.restrictedReason)
        assertTrue(state.shouldDisableVariations)
    }

    @Test
    fun testCapFlags() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val state = InputContextState.fromEditorInfo(info)
        assertTrue(state.requiresCapSentences)
        assertFalse(state.requiresCapWords)
        assertFalse(state.requiresCapCharacters)

        val infoWords = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val stateWords = InputContextState.fromEditorInfo(infoWords)
        assertTrue(stateWords.requiresCapWords)

        val infoCaps = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        val stateCaps = InputContextState.fromEditorInfo(infoCaps)
        assertTrue(stateCaps.requiresCapCharacters)
    }

    @Test
    fun testNumericFields() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val state = InputContextState.fromEditorInfo(info)
        assertTrue(state.isNumericField)
        assertFalse(state.isPhoneField)

        val infoPhone = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_PHONE
        }
        val statePhone = InputContextState.fromEditorInfo(infoPhone)
        assertTrue(statePhone.isNumericField)
        assertTrue(statePhone.isPhoneField)
    }

    @Test
    fun testEmptyEditorInfo() {
        val state = InputContextState.fromEditorInfo(null)
        assertEquals(InputContextState.EMPTY, state)
        assertFalse(state.isEditable)
    }
}

