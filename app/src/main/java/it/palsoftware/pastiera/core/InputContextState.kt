package it.palsoftware.pastiera.core

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Immutable snapshot of the active input field. Extracted once per EditorInfo
 * to avoid duplicating the same detection logic across the IME.
 */
data class InputContextState(
    val isEditable: Boolean,
    val isReallyEditable: Boolean,
    val inputClass: Int,
    val inputVariation: Int,
    val restrictedReason: RestrictedReason?
) {

    val isPhoneField: Boolean
        get() = inputClass == InputType.TYPE_CLASS_PHONE

    val isNumericField: Boolean
        get() = inputClass == InputType.TYPE_CLASS_NUMBER || isPhoneField

    val shouldDisableSmartFeatures: Boolean
        get() = restrictedReason != null

    val isPasswordField: Boolean
        get() = restrictedReason == RestrictedReason.PASSWORD

    val isUriField: Boolean
        get() = restrictedReason == RestrictedReason.URI

    val isEmailField: Boolean
        get() = restrictedReason == RestrictedReason.EMAIL

    val isFilterField: Boolean
        get() = restrictedReason == RestrictedReason.FILTER

    enum class RestrictedReason {
        PASSWORD,
        URI,
        EMAIL,
        FILTER
    }

    companion object {
        val EMPTY = InputContextState(
            isEditable = false,
            isReallyEditable = false,
            inputClass = InputType.TYPE_NULL,
            inputVariation = 0,
            restrictedReason = null
        )

        fun fromEditorInfo(info: EditorInfo?): InputContextState {
            if (info == null) {
                return EMPTY
            }

            val inputType = info.inputType
            val inputClass = inputType and InputType.TYPE_MASK_CLASS
            val inputVariation = inputType and InputType.TYPE_MASK_VARIATION

            val isTextInput = inputClass != InputType.TYPE_NULL
            val isNotNoInput = inputClass != 0
            val isEditable = isTextInput && isNotNoInput

            val isReallyEditable = isEditable && (
                inputClass == InputType.TYPE_CLASS_TEXT ||
                inputClass == InputType.TYPE_CLASS_NUMBER ||
                inputClass == InputType.TYPE_CLASS_PHONE ||
                inputClass == InputType.TYPE_CLASS_DATETIME
            )

            val restrictedReason = when {
                inputVariation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                inputVariation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                inputVariation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                inputVariation == InputType.TYPE_NUMBER_VARIATION_PASSWORD ->
                    RestrictedReason.PASSWORD

                inputVariation == InputType.TYPE_TEXT_VARIATION_URI ->
                    RestrictedReason.URI

                inputVariation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ->
                    RestrictedReason.EMAIL

                inputVariation == InputType.TYPE_TEXT_VARIATION_FILTER ->
                    RestrictedReason.FILTER

                else -> null
            }

            return InputContextState(
                isEditable = isEditable,
                isReallyEditable = isReallyEditable,
                inputClass = inputClass,
                inputVariation = inputVariation,
                restrictedReason = restrictedReason
            )
        }
    }
}

