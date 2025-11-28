package it.palsoftware.pastiera.core

import android.text.InputType
import android.util.Log
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
    val inputType: Int,
    val restrictedReason: RestrictedReason?
) {

    val isPhoneField: Boolean
        get() = inputClass == InputType.TYPE_CLASS_PHONE

    val isNumericField: Boolean
        get() = inputClass == InputType.TYPE_CLASS_NUMBER || isPhoneField

    // Granular flags for different smart features
    // These allow fine-grained control over what to disable based on field type
    
    /**
     * Disable word suggestions (dictionary-based suggestions).
     * Disabled for: PASSWORD, URI, EMAIL, FILTER
     */
    val shouldDisableSuggestions: Boolean
        get() = restrictedReason != null
    
    /**
     * Disable auto-correction (automatic word replacement).
     * Disabled for: PASSWORD, URI, EMAIL, FILTER
     */
    val shouldDisableAutoCorrect: Boolean
        get() = restrictedReason != null
    
    /**
     * Disable auto-capitalization (automatic capitalization after period/enter).
     * Disabled for: PASSWORD, URI, EMAIL, FILTER
     */
    val shouldDisableAutoCapitalize: Boolean
        get() = restrictedReason != null
    
    /**
     * Disable double-space-to-period conversion.
     * Disabled for: PASSWORD, URI, EMAIL, FILTER
     */
    val shouldDisableDoubleSpaceToPeriod: Boolean
        get() = restrictedReason != null
    
    /**
     * Disable character variations (accented characters, special variants).
     * Disabled for: EMAIL (no accents in email addresses)
     * Enabled for: URI (browsers use URI fields as search bars), PASSWORD (accents may be needed), FILTER (accents in search queries)
     */
    val shouldDisableVariations: Boolean
        get() = restrictedReason == RestrictedReason.EMAIL
    
    /**
     * Field requires all characters to be capitalized (textCapCharacters flag).
     * When this is true, caps lock should be enabled automatically.
     */
    val requiresCapCharacters: Boolean
        get() = (inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0
    
    /**
     * Field requires first letter of each word to be capitalized (textCapWords flag).
     * When this is true, the first letter after space/punctuation should be capitalized.
     */
    val requiresCapWords: Boolean
        get() = (inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0
    
    /**
     * Field requires first letter of each sentence to be capitalized (textCapSentences flag).
     * When this is true, the first letter after sentence-ending punctuation should be capitalized.
     */
    val requiresCapSentences: Boolean
        get() = (inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0
    
    // Legacy flag for backward compatibility (maps to shouldDisableSuggestions)
    // TODO: Gradually replace all usages with specific flags
    val shouldDisableSmartFeatures: Boolean
        get() = shouldDisableSuggestions

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
        private const val TAG = "InputContextState"

        val EMPTY = InputContextState(
            isEditable = false,
            isReallyEditable = false,
            inputClass = InputType.TYPE_NULL,
            inputVariation = 0,
            inputType = InputType.TYPE_NULL,
            restrictedReason = null
        )

        private fun getInputClassName(inputClass: Int): String {
            return when (inputClass) {
                InputType.TYPE_CLASS_TEXT -> "TYPE_CLASS_TEXT"
                InputType.TYPE_CLASS_NUMBER -> "TYPE_CLASS_NUMBER"
                InputType.TYPE_CLASS_PHONE -> "TYPE_CLASS_PHONE"
                InputType.TYPE_CLASS_DATETIME -> "TYPE_CLASS_DATETIME"
                InputType.TYPE_NULL -> "TYPE_NULL"
                else -> "UNKNOWN(0x${Integer.toHexString(inputClass)})"
            }
        }

        private fun getInputVariationName(inputVariation: Int): String {
            return when (inputVariation) {
                InputType.TYPE_TEXT_VARIATION_NORMAL -> "TYPE_TEXT_VARIATION_NORMAL"
                InputType.TYPE_TEXT_VARIATION_PASSWORD -> "TYPE_TEXT_VARIATION_PASSWORD"
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> "TYPE_TEXT_VARIATION_VISIBLE_PASSWORD"
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> "TYPE_TEXT_VARIATION_WEB_PASSWORD"
                InputType.TYPE_TEXT_VARIATION_URI -> "TYPE_TEXT_VARIATION_URI"
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> "TYPE_TEXT_VARIATION_EMAIL_ADDRESS"
                InputType.TYPE_TEXT_VARIATION_FILTER -> "TYPE_TEXT_VARIATION_FILTER"
                InputType.TYPE_TEXT_VARIATION_PERSON_NAME -> "TYPE_TEXT_VARIATION_PERSON_NAME"
                InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS -> "TYPE_TEXT_VARIATION_POSTAL_ADDRESS"
                InputType.TYPE_NUMBER_VARIATION_PASSWORD -> "TYPE_NUMBER_VARIATION_PASSWORD"
                InputType.TYPE_NUMBER_VARIATION_NORMAL -> "TYPE_NUMBER_VARIATION_NORMAL"
                0 -> "NONE"
                else -> {
                    // Check for known variations that might not be in all API levels
                    when (inputVariation) {
                        0x00000010 -> "TYPE_TEXT_VARIATION_WEB_SEARCH" // API 11+
                        0x00000020 -> "TYPE_TEXT_VARIATION_WEB_EDIT_TEXT" // API 11+
                        0x00000030 -> "TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS" // API 11+
                        0x00000001 -> "TYPE_NUMBER_VARIATION_SIGNED" // API 11+
                        0x00000002 -> "TYPE_NUMBER_VARIATION_DECIMAL" // API 11+
                        else -> "UNKNOWN(0x${Integer.toHexString(inputVariation)})"
                    }
                }
            }
        }

        fun fromEditorInfo(info: EditorInfo?): InputContextState {
            if (info == null) {
                Log.d(TAG, "Input field: NULL (no EditorInfo)")
                return EMPTY
            }

            val inputType = info.inputType
            val inputClass = inputType and InputType.TYPE_MASK_CLASS
            val inputVariation = inputType and InputType.TYPE_MASK_VARIATION

            // Debug: log raw values to diagnose matching issues
            Log.d(TAG, "DEBUG matching: inputVariation=0x${Integer.toHexString(inputVariation)}, " +
                    "URI=0x${Integer.toHexString(InputType.TYPE_TEXT_VARIATION_URI)}, " +
                    "WEB_PASSWORD=0x${Integer.toHexString(InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)}, " +
                    "PASSWORD=0x${Integer.toHexString(InputType.TYPE_TEXT_VARIATION_PASSWORD)}")

            val isTextInput = inputClass != InputType.TYPE_NULL
            val isNotNoInput = inputClass != 0
            val isEditable = isTextInput && isNotNoInput

            val isReallyEditable = isEditable && (
                inputClass == InputType.TYPE_CLASS_TEXT ||
                inputClass == InputType.TYPE_CLASS_NUMBER ||
                inputClass == InputType.TYPE_CLASS_PHONE ||
                inputClass == InputType.TYPE_CLASS_DATETIME
            )

            // Check URI first (more specific) before password variations
            val restrictedReason = when {
                inputVariation == InputType.TYPE_TEXT_VARIATION_URI ->
                    RestrictedReason.URI

                inputVariation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                inputVariation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                inputVariation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                inputVariation == InputType.TYPE_NUMBER_VARIATION_PASSWORD ->
                    RestrictedReason.PASSWORD

                inputVariation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ->
                    RestrictedReason.EMAIL

                inputVariation == InputType.TYPE_TEXT_VARIATION_FILTER ->
                    RestrictedReason.FILTER

                else -> null
            }

            val state = InputContextState(
                isEditable = isEditable,
                isReallyEditable = isReallyEditable,
                inputClass = inputClass,
                inputVariation = inputVariation,
                inputType = inputType,
                restrictedReason = restrictedReason
            )

            // Debug logging
            val packageName = info.packageName ?: "unknown"
            val fieldName = info.fieldName ?: "unknown"
            val className = getInputClassName(inputClass)
            val variationName = getInputVariationName(inputVariation)
            val flags = mutableListOf<String>()
            
            if ((inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) flags.add("CAP_CHARACTERS")
            if ((inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0) flags.add("CAP_WORDS")
            if ((inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0) flags.add("CAP_SENTENCES")
            if ((inputType and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) flags.add("AUTO_COMPLETE")
            if ((inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT) != 0) flags.add("AUTO_CORRECT")
            if ((inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) flags.add("MULTI_LINE")
            if ((inputType and InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE) != 0) flags.add("IME_MULTI_LINE")
            if ((inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) flags.add("NO_SUGGESTIONS")

            Log.d(TAG, """
                |=== Input Field Detected ===
                |Package: $packageName
                |Field: $fieldName
                |InputClass: $className (0x${Integer.toHexString(inputClass)})
                |InputVariation: $variationName (0x${Integer.toHexString(inputVariation)})
                |Flags: ${if (flags.isEmpty()) "NONE" else flags.joinToString(", ")}
                |isEditable: $isEditable
                |isReallyEditable: $isReallyEditable
                |isNumericField: ${state.isNumericField}
                |isPhoneField: ${state.isPhoneField}
                |restrictedReason: ${restrictedReason ?: "NONE"}
                |--- Granular Smart Features Flags ---
                |shouldDisableSuggestions: ${state.shouldDisableSuggestions}
                |shouldDisableAutoCorrect: ${state.shouldDisableAutoCorrect}
                |shouldDisableAutoCapitalize: ${state.shouldDisableAutoCapitalize}
                |shouldDisableDoubleSpaceToPeriod: ${state.shouldDisableDoubleSpaceToPeriod}
                |shouldDisableVariations: ${state.shouldDisableVariations}
                |shouldDisableSmartFeatures (legacy): ${state.shouldDisableSmartFeatures}
                |============================
            """.trimMargin())

            return state
        }
    }
}

