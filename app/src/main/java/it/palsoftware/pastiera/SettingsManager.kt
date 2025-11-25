package it.palsoftware.pastiera

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.KeyEvent
import org.json.JSONObject
import java.io.InputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Manages the app settings.
 * Centralizes access to SharedPreferences for Pastiera settings.
 */
object SettingsManager {
    private const val TAG = "SettingsManager"
    private const val PREFS_NAME = "pastiera_prefs"
    
    // Settings keys
    private const val KEY_LONG_PRESS_THRESHOLD = "long_press_threshold"
    private const val KEY_AUTO_CAPITALIZE_FIRST_LETTER = "auto_capitalize_first_letter"
    private const val KEY_DOUBLE_SPACE_TO_PERIOD = "double_space_to_period"
    private const val KEY_SWIPE_TO_DELETE = "swipe_to_delete"
    private const val KEY_AUTO_SHOW_KEYBOARD = "auto_show_keyboard"
    private const val KEY_CLEAR_ALT_ON_SPACE = "clear_alt_on_space"
    private const val KEY_ALT_CTRL_SPEECH_SHORTCUT = "alt_ctrl_speech_shortcut"
    private const val KEY_SYM_MAPPINGS_CUSTOM = "sym_mappings_custom"
    private const val KEY_SYM_MAPPINGS_PAGE2_CUSTOM = "sym_mappings_page2_custom"
    private const val KEY_AUTO_CORRECT_ENABLED = "auto_correct_enabled"
    private const val KEY_AUTO_CORRECT_ENABLED_LANGUAGES = "auto_correct_enabled_languages"
    private const val KEY_SUGGESTIONS_ENABLED = "suggestions_enabled"
    private const val KEY_ACCENT_MATCHING_ENABLED = "accent_matching_enabled"
    private const val KEY_AUTO_REPLACE_ON_SPACE_ENTER = "auto_replace_on_space_enter"
    private const val KEY_AUTO_CAPITALIZE_AFTER_PERIOD = "auto_capitalize_after_period"
    private const val KEY_LONG_PRESS_MODIFIER = "long_press_modifier" // "alt" or "shift"
    private const val KEY_KEYBOARD_LAYOUT = "keyboard_layout" // "qwerty", "azerty", etc.
    private const val KEY_KEYBOARD_LAYOUT_LIST = "keyboard_layout_list" // JSON array of layout ids for cycling
    private const val KEY_RESTORE_SYM_PAGE = "restore_sym_page" // SYM page to restore when returning from settings
    private const val KEY_PENDING_RESTORE_SYM_PAGE = "pending_restore_sym_page" // Temporary SYM page state saved when opening settings
    private const val KEY_SYM_PAGES_CONFIG = "sym_pages_config" // Order/enabled pages for SYM
    private const val KEY_SYM_AUTO_CLOSE = "sym_auto_close" // Auto-close SYM layout after key press
    private const val KEY_DISMISSED_RELEASES = "dismissed_releases" // Set of release tag_names that were dismissed
    private const val KEY_TUTORIAL_COMPLETED = "tutorial_completed" // Whether the first-run tutorial has been completed
    private const val KEY_SWIPE_INCREMENTAL_THRESHOLD = "swipe_incremental_threshold" // Distance in DIP for cursor movement
    private const val KEY_STATIC_VARIATION_BAR_MODE = "static_variation_bar_mode" // Use static variation bar instead of dynamic cursor-based variations
    private const val KEY_VARIATIONS_UPDATED = "variations_updated" // Trigger for reloading variations in input method service
    
    private const val VARIATIONS_FILE_NAME = "variations.json"
    
    // Default values
    private const val DEFAULT_LONG_PRESS_THRESHOLD = 300L
    private const val MIN_LONG_PRESS_THRESHOLD = 50L
    private const val MAX_LONG_PRESS_THRESHOLD = 1000L
    private const val DEFAULT_SWIPE_INCREMENTAL_THRESHOLD = 9.6f
    private const val MIN_SWIPE_INCREMENTAL_THRESHOLD = 3f
    private const val MAX_SWIPE_INCREMENTAL_THRESHOLD = 25f
    private const val DEFAULT_AUTO_CAPITALIZE_FIRST_LETTER = true
    private const val DEFAULT_DOUBLE_SPACE_TO_PERIOD = true
    private const val DEFAULT_SWIPE_TO_DELETE = false
    private const val DEFAULT_AUTO_SHOW_KEYBOARD = true
    private const val DEFAULT_CLEAR_ALT_ON_SPACE = false
    private const val DEFAULT_ALT_CTRL_SPEECH_SHORTCUT = true
    private const val DEFAULT_AUTO_CORRECT_ENABLED = true
    private const val DEFAULT_SUGGESTIONS_ENABLED = true
    private const val DEFAULT_ACCENT_MATCHING_ENABLED = true
    private const val DEFAULT_AUTO_REPLACE_ON_SPACE_ENTER = false
    private const val DEFAULT_AUTO_CAPITALIZE_AFTER_PERIOD = true
    private const val DEFAULT_LONG_PRESS_MODIFIER = "alt"
    private const val DEFAULT_KEYBOARD_LAYOUT = "qwerty"
    private const val DEFAULT_SYM_AUTO_CLOSE = true
    private val DEFAULT_SYM_PAGES_CONFIG = SymPagesConfig()
    private const val DEFAULT_STATIC_VARIATION_BAR_MODE = false
    private const val DEFAULT_EXPERIMENTAL_SUGGESTIONS_ENABLED = false
    private const val DEFAULT_SUGGESTION_DEBUG_LOGGING = false
    private const val KEY_EXPERIMENTAL_SUGGESTIONS_ENABLED = "experimental_suggestions_enabled"
    private const val KEY_SUGGESTION_DEBUG_LOGGING = "suggestion_debug_logging"
    
    /**
     * Returns the SharedPreferences instance for Pastiera.
     */
    fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Returns the long-press threshold in milliseconds.
     */
    fun getLongPressThreshold(context: Context): Long {
        return getPreferences(context).getLong(KEY_LONG_PRESS_THRESHOLD, DEFAULT_LONG_PRESS_THRESHOLD)
    }
    
    /**
     * Sets the long-press threshold in milliseconds.
     * The value is automatically clamped between MIN and MAX.
     */
    fun setLongPressThreshold(context: Context, threshold: Long) {
        val clampedValue = threshold.coerceIn(MIN_LONG_PRESS_THRESHOLD, MAX_LONG_PRESS_THRESHOLD)
        getPreferences(context).edit()
            .putLong(KEY_LONG_PRESS_THRESHOLD, clampedValue)
            .apply()
    }
    
    /**
     * Returns the minimum allowed value for the long-press threshold.
     */
    fun getMinLongPressThreshold(): Long = MIN_LONG_PRESS_THRESHOLD
    
    /**
     * Returns the maximum allowed value for the long-press threshold.
     */
    fun getMaxLongPressThreshold(): Long = MAX_LONG_PRESS_THRESHOLD
    
    /**
     * Returns the default value for the long-press threshold.
     */
    fun getDefaultLongPressThreshold(): Long = DEFAULT_LONG_PRESS_THRESHOLD
    
    /**
     * Returns the swipe incremental threshold in DIP.
     * This is the distance that must be traveled to move the cursor one position.
     */
    fun getSwipeIncrementalThreshold(context: Context): Float {
        return getPreferences(context).getFloat(KEY_SWIPE_INCREMENTAL_THRESHOLD, DEFAULT_SWIPE_INCREMENTAL_THRESHOLD)
    }
    
    /**
     * Sets the swipe incremental threshold in DIP.
     * The value is automatically clamped between MIN and MAX.
     */
    fun setSwipeIncrementalThreshold(context: Context, threshold: Float) {
        val clampedValue = threshold.coerceIn(MIN_SWIPE_INCREMENTAL_THRESHOLD, MAX_SWIPE_INCREMENTAL_THRESHOLD)
        getPreferences(context).edit()
            .putFloat(KEY_SWIPE_INCREMENTAL_THRESHOLD, clampedValue)
            .apply()
    }
    
    /**
     * Returns the minimum allowed value for the swipe incremental threshold.
     */
    fun getMinSwipeIncrementalThreshold(): Float = MIN_SWIPE_INCREMENTAL_THRESHOLD
    
    /**
     * Returns the maximum allowed value for the swipe incremental threshold.
     */
    fun getMaxSwipeIncrementalThreshold(): Float = MAX_SWIPE_INCREMENTAL_THRESHOLD
    
    /**
     * Returns the default value for the swipe incremental threshold.
     */
    fun getDefaultSwipeIncrementalThreshold(): Float = DEFAULT_SWIPE_INCREMENTAL_THRESHOLD
    
    /**
     * Returns the state of auto-capitalization for the first letter.
     */
    fun getAutoCapitalizeFirstLetter(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_AUTO_CAPITALIZE_FIRST_LETTER, DEFAULT_AUTO_CAPITALIZE_FIRST_LETTER)
    }
    
    /**
     * Sets the state of auto-capitalization for the first letter.
     */
    fun setAutoCapitalizeFirstLetter(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_AUTO_CAPITALIZE_FIRST_LETTER, enabled)
            .apply()
    }

    /**
     * Returns the state of auto-capitalization after period.
     */
    fun getAutoCapitalizeAfterPeriod(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_AUTO_CAPITALIZE_AFTER_PERIOD, DEFAULT_AUTO_CAPITALIZE_AFTER_PERIOD)
    }

    /**
     * Sets the state of auto-capitalization after period.
     */
    fun setAutoCapitalizeAfterPeriod(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_AUTO_CAPITALIZE_AFTER_PERIOD, enabled)
            .apply()
    }

    /**
     * Returns the state of the double-space-to-period feature.
     */
    fun getDoubleSpaceToPeriod(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_DOUBLE_SPACE_TO_PERIOD, DEFAULT_DOUBLE_SPACE_TO_PERIOD)
    }
    
    /**
     * Sets the state of the double-space-to-period feature.
     */
    fun setDoubleSpaceToPeriod(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_DOUBLE_SPACE_TO_PERIOD, enabled)
            .apply()
    }
    
    /**
     * Returns the state of swipe-to-delete (keycode 322).
     */
    fun getSwipeToDelete(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SWIPE_TO_DELETE, DEFAULT_SWIPE_TO_DELETE)
    }
    
    /**
     * Sets the state of swipe-to-delete (keycode 322).
     */
    fun setSwipeToDelete(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SWIPE_TO_DELETE, enabled)
            .apply()
    }
    
    /**
     * Returns the state of automatically showing the keyboard when a field gains focus.
     */
    fun getAutoShowKeyboard(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_AUTO_SHOW_KEYBOARD, DEFAULT_AUTO_SHOW_KEYBOARD)
    }
    
    /**
     * Sets the state of automatically showing the keyboard when a field gains focus.
     */
    fun setAutoShowKeyboard(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_AUTO_SHOW_KEYBOARD, enabled)
            .apply()
    }

    /**
     * Returns whether Alt+Ctrl shortcut for speech recognition is enabled.
     */
    fun getAltCtrlSpeechShortcutEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_ALT_CTRL_SPEECH_SHORTCUT, DEFAULT_ALT_CTRL_SPEECH_SHORTCUT)
    }

    /**
     * Sets whether Alt+Ctrl shortcut for speech recognition is enabled.
     */
    fun setAltCtrlSpeechShortcutEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_ALT_CTRL_SPEECH_SHORTCUT, enabled)
            .apply()
    }

    /**
     * Returns whether the static variation bar mode is enabled.
     * When enabled, the variation row shows a fixed set of utility keys
     * instead of dynamic cursor-based character variations.
     */
    fun isStaticVariationBarModeEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_STATIC_VARIATION_BAR_MODE, DEFAULT_STATIC_VARIATION_BAR_MODE)
    }

    /**
     * Sets whether the static variation bar mode is enabled.
     */
    fun setStaticVariationBarModeEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_STATIC_VARIATION_BAR_MODE, enabled)
            .apply()
    }

    /**
     * Returns whether Alt/Alt-Lock should be cleared when pressing Space.
     */
    fun getClearAltOnSpace(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_CLEAR_ALT_ON_SPACE, DEFAULT_CLEAR_ALT_ON_SPACE)
    }

    /**
     * Sets whether Alt/Alt-Lock should be cleared when pressing Space.
     */
    fun setClearAltOnSpace(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_CLEAR_ALT_ON_SPACE, enabled)
            .apply()
    }
    
    /**
     * Returns custom SYM mappings.
     * Returns an empty map if there are no custom mappings.
     */
    fun getSymMappings(context: Context): Map<Int, String> {
        val prefs = getPreferences(context)
        val jsonString = prefs.getString(KEY_SYM_MAPPINGS_CUSTOM, null) ?: return emptyMap()
        
        return try {
            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")
            val keyCodeMap = mapOf(
                "KEYCODE_Q" to KeyEvent.KEYCODE_Q, "KEYCODE_W" to KeyEvent.KEYCODE_W,
                "KEYCODE_E" to KeyEvent.KEYCODE_E, "KEYCODE_R" to KeyEvent.KEYCODE_R,
                "KEYCODE_T" to KeyEvent.KEYCODE_T, "KEYCODE_Y" to KeyEvent.KEYCODE_Y,
                "KEYCODE_U" to KeyEvent.KEYCODE_U, "KEYCODE_I" to KeyEvent.KEYCODE_I,
                "KEYCODE_O" to KeyEvent.KEYCODE_O, "KEYCODE_P" to KeyEvent.KEYCODE_P,
                "KEYCODE_A" to KeyEvent.KEYCODE_A, "KEYCODE_S" to KeyEvent.KEYCODE_S,
                "KEYCODE_D" to KeyEvent.KEYCODE_D, "KEYCODE_F" to KeyEvent.KEYCODE_F,
                "KEYCODE_G" to KeyEvent.KEYCODE_G, "KEYCODE_H" to KeyEvent.KEYCODE_H,
                "KEYCODE_J" to KeyEvent.KEYCODE_J, "KEYCODE_K" to KeyEvent.KEYCODE_K,
                "KEYCODE_L" to KeyEvent.KEYCODE_L, "KEYCODE_Z" to KeyEvent.KEYCODE_Z,
                "KEYCODE_X" to KeyEvent.KEYCODE_X, "KEYCODE_C" to KeyEvent.KEYCODE_C,
                "KEYCODE_V" to KeyEvent.KEYCODE_V, "KEYCODE_B" to KeyEvent.KEYCODE_B,
                "KEYCODE_N" to KeyEvent.KEYCODE_N, "KEYCODE_M" to KeyEvent.KEYCODE_M
            )
            
            val result = mutableMapOf<Int, String>()
            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyCodeMap[keyName]
                val emoji = mappingsObject.getString(keyName)
                if (keyCode != null) {
                    result[keyCode] = emoji
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error loading custom SYM mappings", e)
            emptyMap()
        }
    }
    
    /**
     * Saves custom SYM mappings.
     */
    fun saveSymMappings(context: Context, mappings: Map<Int, String>) {
        try {
            val keyCodeToName = mapOf(
                KeyEvent.KEYCODE_Q to "KEYCODE_Q", KeyEvent.KEYCODE_W to "KEYCODE_W",
                KeyEvent.KEYCODE_E to "KEYCODE_E", KeyEvent.KEYCODE_R to "KEYCODE_R",
                KeyEvent.KEYCODE_T to "KEYCODE_T", KeyEvent.KEYCODE_Y to "KEYCODE_Y",
                KeyEvent.KEYCODE_U to "KEYCODE_U", KeyEvent.KEYCODE_I to "KEYCODE_I",
                KeyEvent.KEYCODE_O to "KEYCODE_O", KeyEvent.KEYCODE_P to "KEYCODE_P",
                KeyEvent.KEYCODE_A to "KEYCODE_A", KeyEvent.KEYCODE_S to "KEYCODE_S",
                KeyEvent.KEYCODE_D to "KEYCODE_D", KeyEvent.KEYCODE_F to "KEYCODE_F",
                KeyEvent.KEYCODE_G to "KEYCODE_G", KeyEvent.KEYCODE_H to "KEYCODE_H",
                KeyEvent.KEYCODE_J to "KEYCODE_J", KeyEvent.KEYCODE_K to "KEYCODE_K",
                KeyEvent.KEYCODE_L to "KEYCODE_L", KeyEvent.KEYCODE_Z to "KEYCODE_Z",
                KeyEvent.KEYCODE_X to "KEYCODE_X", KeyEvent.KEYCODE_C to "KEYCODE_C",
                KeyEvent.KEYCODE_V to "KEYCODE_V", KeyEvent.KEYCODE_B to "KEYCODE_B",
                KeyEvent.KEYCODE_N to "KEYCODE_N", KeyEvent.KEYCODE_M to "KEYCODE_M"
            )
            
            val mappingsObject = JSONObject()
            for ((keyCode, emoji) in mappings) {
                val keyName = keyCodeToName[keyCode]
                if (keyName != null) {
                    mappingsObject.put(keyName, emoji)
                }
            }
            
            val jsonObject = JSONObject()
            jsonObject.put("mappings", mappingsObject)
            
            getPreferences(context).edit()
                .putString(KEY_SYM_MAPPINGS_CUSTOM, jsonObject.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving custom SYM mappings", e)
        }
    }
    
    /**
     * Resets custom SYM mappings back to defaults.
     */
    fun resetSymMappings(context: Context) {
        getPreferences(context).edit()
            .remove(KEY_SYM_MAPPINGS_CUSTOM)
            .apply()
    }
    
    /**
     * Returns true if custom SYM mappings exist.
     */
    fun hasCustomSymMappings(context: Context): Boolean {
        val prefs = getPreferences(context)
        return prefs.contains(KEY_SYM_MAPPINGS_CUSTOM)
    }
    
    /**
     * Returns custom SYM mappings for page 2.
     * Returns an empty map if there are no custom mappings.
     */
    fun getSymMappingsPage2(context: Context): Map<Int, String> {
        val prefs = getPreferences(context)
        val jsonString = prefs.getString(KEY_SYM_MAPPINGS_PAGE2_CUSTOM, null) ?: return emptyMap()
        
        return try {
            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")
            val keyCodeMap = mapOf(
                "KEYCODE_Q" to KeyEvent.KEYCODE_Q, "KEYCODE_W" to KeyEvent.KEYCODE_W,
                "KEYCODE_E" to KeyEvent.KEYCODE_E, "KEYCODE_R" to KeyEvent.KEYCODE_R,
                "KEYCODE_T" to KeyEvent.KEYCODE_T, "KEYCODE_Y" to KeyEvent.KEYCODE_Y,
                "KEYCODE_U" to KeyEvent.KEYCODE_U, "KEYCODE_I" to KeyEvent.KEYCODE_I,
                "KEYCODE_O" to KeyEvent.KEYCODE_O, "KEYCODE_P" to KeyEvent.KEYCODE_P,
                "KEYCODE_A" to KeyEvent.KEYCODE_A, "KEYCODE_S" to KeyEvent.KEYCODE_S,
                "KEYCODE_D" to KeyEvent.KEYCODE_D, "KEYCODE_F" to KeyEvent.KEYCODE_F,
                "KEYCODE_G" to KeyEvent.KEYCODE_G, "KEYCODE_H" to KeyEvent.KEYCODE_H,
                "KEYCODE_J" to KeyEvent.KEYCODE_J, "KEYCODE_K" to KeyEvent.KEYCODE_K,
                "KEYCODE_L" to KeyEvent.KEYCODE_L, "KEYCODE_Z" to KeyEvent.KEYCODE_Z,
                "KEYCODE_X" to KeyEvent.KEYCODE_X, "KEYCODE_C" to KeyEvent.KEYCODE_C,
                "KEYCODE_V" to KeyEvent.KEYCODE_V, "KEYCODE_B" to KeyEvent.KEYCODE_B,
                "KEYCODE_N" to KeyEvent.KEYCODE_N, "KEYCODE_M" to KeyEvent.KEYCODE_M
            )
            
            val result = mutableMapOf<Int, String>()
            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyCodeMap[keyName]
                val character = mappingsObject.getString(keyName)
                if (keyCode != null) {
                    result[keyCode] = character
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error loading custom SYM page 2 mappings", e)
            emptyMap()
        }
    }
    
    /**
     * Saves custom SYM mappings for page 2.
     */
    fun saveSymMappingsPage2(context: Context, mappings: Map<Int, String>) {
        try {
            val keyCodeToName = mapOf(
                KeyEvent.KEYCODE_Q to "KEYCODE_Q", KeyEvent.KEYCODE_W to "KEYCODE_W",
                KeyEvent.KEYCODE_E to "KEYCODE_E", KeyEvent.KEYCODE_R to "KEYCODE_R",
                KeyEvent.KEYCODE_T to "KEYCODE_T", KeyEvent.KEYCODE_Y to "KEYCODE_Y",
                KeyEvent.KEYCODE_U to "KEYCODE_U", KeyEvent.KEYCODE_I to "KEYCODE_I",
                KeyEvent.KEYCODE_O to "KEYCODE_O", KeyEvent.KEYCODE_P to "KEYCODE_P",
                KeyEvent.KEYCODE_A to "KEYCODE_A", KeyEvent.KEYCODE_S to "KEYCODE_S",
                KeyEvent.KEYCODE_D to "KEYCODE_D", KeyEvent.KEYCODE_F to "KEYCODE_F",
                KeyEvent.KEYCODE_G to "KEYCODE_G", KeyEvent.KEYCODE_H to "KEYCODE_H",
                KeyEvent.KEYCODE_J to "KEYCODE_J", KeyEvent.KEYCODE_K to "KEYCODE_K",
                KeyEvent.KEYCODE_L to "KEYCODE_L", KeyEvent.KEYCODE_Z to "KEYCODE_Z",
                KeyEvent.KEYCODE_X to "KEYCODE_X", KeyEvent.KEYCODE_C to "KEYCODE_C",
                KeyEvent.KEYCODE_V to "KEYCODE_V", KeyEvent.KEYCODE_B to "KEYCODE_B",
                KeyEvent.KEYCODE_N to "KEYCODE_N", KeyEvent.KEYCODE_M to "KEYCODE_M"
            )
            
            val mappingsObject = JSONObject()
            for ((keyCode, character) in mappings) {
                val keyName = keyCodeToName[keyCode]
                if (keyName != null) {
                    mappingsObject.put(keyName, character)
                }
            }
            
            val jsonObject = JSONObject()
            jsonObject.put("mappings", mappingsObject)
            
            getPreferences(context).edit()
                .putString(KEY_SYM_MAPPINGS_PAGE2_CUSTOM, jsonObject.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving custom SYM page 2 mappings", e)
        }
    }
    
    /**
     * Resets custom SYM mappings for page 2 back to defaults.
     */
    fun resetSymMappingsPage2(context: Context) {
        getPreferences(context).edit()
            .remove(KEY_SYM_MAPPINGS_PAGE2_CUSTOM)
            .apply()
    }
    
    /**
     * Returns true if custom SYM page 2 mappings exist.
     */
    fun hasCustomSymMappingsPage2(context: Context): Boolean {
        val prefs = getPreferences(context)
        return prefs.contains(KEY_SYM_MAPPINGS_PAGE2_CUSTOM)
    }
    
    /**
     * Returns whether auto-correction is enabled.
     */
    fun getAutoCorrectEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_AUTO_CORRECT_ENABLED, DEFAULT_AUTO_CORRECT_ENABLED)
    }

    /**
     * Sets whether auto-correction is enabled.
     */
    fun setAutoCorrectEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_AUTO_CORRECT_ENABLED, enabled)
            .apply()
    }

    /**
     * Returns whether inline suggestions are enabled.
     */
    fun getSuggestionsEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SUGGESTIONS_ENABLED, DEFAULT_SUGGESTIONS_ENABLED)
    }

    /**
     * Enables or disables inline suggestions.
     */
    fun setSuggestionsEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SUGGESTIONS_ENABLED, enabled)
            .apply()
    }

    /**
     * Returns whether accent matching should be applied to suggestions and auto-replace.
     */
    fun getAccentMatchingEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_ACCENT_MATCHING_ENABLED, DEFAULT_ACCENT_MATCHING_ENABLED)
    }

    /**
     * Toggles accent matching for suggestions.
     */
    fun setAccentMatchingEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_ACCENT_MATCHING_ENABLED, enabled)
            .apply()
    }

    /**
     * Master toggle for the experimental dictionary/suggestion engine.
     * When disabled, the IME will skip initialization and hide suggestion UI.
     */
    fun isExperimentalSuggestionsEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_EXPERIMENTAL_SUGGESTIONS_ENABLED, DEFAULT_EXPERIMENTAL_SUGGESTIONS_ENABLED)
    }

    fun setExperimentalSuggestionsEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_EXPERIMENTAL_SUGGESTIONS_ENABLED, enabled)
            .apply()
    }

    /**
     * Optional debug logging for the suggestion engine.
     */
    fun isSuggestionDebugLoggingEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SUGGESTION_DEBUG_LOGGING, DEFAULT_SUGGESTION_DEBUG_LOGGING)
    }

    fun setSuggestionDebugLoggingEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SUGGESTION_DEBUG_LOGGING, enabled)
            .apply()
    }

    /**
     * Returns whether auto-replace on space/enter is enabled.
     */
    fun getAutoReplaceOnSpaceEnter(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_AUTO_REPLACE_ON_SPACE_ENTER, DEFAULT_AUTO_REPLACE_ON_SPACE_ENTER)
    }

    /**
     * Enables or disables auto-replace on space/enter.
     */
    fun setAutoReplaceOnSpaceEnter(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_AUTO_REPLACE_ON_SPACE_ENTER, enabled)
            .apply()
    }
    
    /**
     * Returns the list of languages enabled for auto-correction.
     * @return Set of language codes (e.g. "it", "en")
     */
    fun getAutoCorrectEnabledLanguages(context: Context): Set<String> {
        val prefs = getPreferences(context)
        val languagesString = prefs.getString(KEY_AUTO_CORRECT_ENABLED_LANGUAGES, null)
        
        // If languages are explicitly set, return them (ensuring x-pastiera is always included)
        if (languagesString != null && languagesString.isNotEmpty()) {
            val languages = languagesString.split(",").toMutableSet()
            languages.add("x-pastiera") // Always include x-pastiera
            return languages
        }
        
        // Default: system language + x-pastiera, with fallback to English
        val systemLanguage = context.resources.configuration.locales[0].language.lowercase()
        val supportedLanguages = setOf("it", "en", "es", "fr", "de", "pl")
        
        val defaultLanguage = if (systemLanguage in supportedLanguages) {
            systemLanguage
        } else {
            "en" // Fallback to English
        }
        
        return setOf(defaultLanguage, "x-pastiera")
    }
    
    /**
     * Sets the list of languages enabled for auto-correction.
     * @param languages Set of language codes (e.g. "it", "en")
     * Note: x-pastiera is always included automatically in getAutoCorrectEnabledLanguages()
     */
    fun setAutoCorrectEnabledLanguages(context: Context, languages: Set<String>) {
        // Filter out x-pastiera from the saved list (it's always included automatically)
        val languagesToSave = languages.filter { it != "x-pastiera" }
        val languagesString = languagesToSave.joinToString(",")
        getPreferences(context).edit()
            .putString(KEY_AUTO_CORRECT_ENABLED_LANGUAGES, languagesString)
            .apply()
    }
    
    /**
     * Returns true if a language is enabled for auto-correction.
     */
    fun isAutoCorrectLanguageEnabled(context: Context, language: String): Boolean {
        val enabledLanguages = getAutoCorrectEnabledLanguages(context)
        // If the list is empty, all languages are enabled (default behavior)
        return enabledLanguages.isEmpty() || enabledLanguages.contains(language)
    }
    
    /**
     * Special JSON field for the language name.
     */
    private const val LANGUAGE_NAME_KEY = "__name"
    
    /**
     * Returns custom corrections for a language.
     */
    fun getCustomAutoCorrections(context: Context, languageCode: String): Map<String, String> {
        val prefs = getPreferences(context)
        val key = "auto_correct_custom_$languageCode"
        val jsonString = prefs.getString(key, null) ?: return emptyMap()
        
        return try {
            val jsonObject = JSONObject(jsonString)
            val corrections = mutableMapOf<String, String>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val correctionKey = keys.next()
                // Skip the special name field
                if (correctionKey != LANGUAGE_NAME_KEY) {
                    val value = jsonObject.getString(correctionKey)
                    corrections[correctionKey] = value
                }
            }
            corrections
        } catch (e: Exception) {
            Log.e(TAG, "Error loading custom corrections for $languageCode", e)
            emptyMap()
        }
    }
    
    /**
     * Returns the display name of a custom language from JSON.
     */
    fun getCustomLanguageName(context: Context, languageCode: String): String? {
        val prefs = getPreferences(context)
        val key = "auto_correct_custom_$languageCode"
        val jsonString = prefs.getString(key, null) ?: return null
        
        return try {
            val jsonObject = JSONObject(jsonString)
            if (jsonObject.has(LANGUAGE_NAME_KEY)) {
                jsonObject.getString(LANGUAGE_NAME_KEY)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading language name for $languageCode", e)
            null
        }
    }
    
    /**
     * Saves custom corrections for a language.
     * @param languageName The display name of the language (optional, if null it is not saved/updated)
     */
    fun saveCustomAutoCorrections(
        context: Context, 
        languageCode: String, 
        corrections: Map<String, String>,
        languageName: String? = null
    ) {
        try {
            val jsonObject = JSONObject()
            
            // Save the language name if provided
            if (languageName != null) {
                jsonObject.put(LANGUAGE_NAME_KEY, languageName)
            } else {
                // If not provided, try to keep the existing name
                val existingName = getCustomLanguageName(context, languageCode)
                if (existingName != null) {
                    jsonObject.put(LANGUAGE_NAME_KEY, existingName)
                }
            }
            
            // Save corrections
            corrections.forEach { (key, value) ->
                // Skip the special field if present in the corrections
                if (key != LANGUAGE_NAME_KEY) {
                    jsonObject.put(key, value)
                }
            }
            
            val key = "auto_correct_custom_$languageCode"
            getPreferences(context).edit()
                .putString(key, jsonObject.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving custom corrections for $languageCode", e)
        }
    }
    
    /**
     * Updates only the display name of a custom language.
     */
    fun updateCustomLanguageName(context: Context, languageCode: String, languageName: String) {
        try {
            val prefs = getPreferences(context)
            val key = "auto_correct_custom_$languageCode"
            val jsonString = prefs.getString(key, null)
            
            val jsonObject = if (jsonString != null) {
                JSONObject(jsonString)
            } else {
                JSONObject()
            }
            
            jsonObject.put(LANGUAGE_NAME_KEY, languageName)
            
            prefs.edit()
                .putString(key, jsonObject.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating language name for $languageCode", e)
        }
    }
    
    /**
     * Returns the long-press modifier type ("alt" or "shift").
     */
    fun getLongPressModifier(context: Context): String {
        return getPreferences(context).getString(KEY_LONG_PRESS_MODIFIER, DEFAULT_LONG_PRESS_MODIFIER) ?: DEFAULT_LONG_PRESS_MODIFIER
    }
    
    /**
     * Sets the long-press modifier type ("alt" or "shift").
     */
    fun setLongPressModifier(context: Context, modifier: String) {
        val validModifier = if (modifier == "shift") "shift" else "alt"
        getPreferences(context).edit()
            .putString(KEY_LONG_PRESS_MODIFIER, validModifier)
            .apply()
    }
    
    /**
     * Returns true if long press uses Shift, false if it uses Alt.
     */
    fun isLongPressShift(context: Context): Boolean {
        return getLongPressModifier(context) == "shift"
    }
    
    /**
     * Data class per rappresentare una scorciatoia del launcher.
     * Estendibile per supportare diversi tipi di azioni in futuro (app, shortcut, ecc.)
     */
    data class LauncherShortcut(
        val type: String = TYPE_APP, // Tipo di azione: "app", "shortcut", ecc.
        val packageName: String? = null, // Per tipo "app"
        val appName: String? = null, // Per tipo "app"
        val action: String? = null, // Per tipo "shortcut" o altri tipi futuri
        val data: String? = null // Dati aggiuntivi per tipi futuri
    ) {
        companion object {
            const val TYPE_APP = "app"
            const val TYPE_SHORTCUT = "shortcut"
            // Aggiungi altri tipi in futuro qui
        }
    }
    
    private const val KEY_LAUNCHER_SHORTCUTS = "launcher_shortcuts"
    private const val KEY_LAUNCHER_SHORTCUTS_ENABLED = "launcher_shortcuts_enabled"
    private const val DEFAULT_LAUNCHER_SHORTCUTS_ENABLED = false
    
    // Nav mode settings
    private const val KEY_NAV_MODE_ENABLED = "nav_mode_enabled"
    private const val DEFAULT_NAV_MODE_ENABLED = true
    private const val NAV_MODE_MAPPINGS_FILE_NAME = "ctrl_key_mappings.json"
    private const val KEY_NAV_MODE_MAPPINGS_UPDATED = "nav_mode_mappings_updated"
    
    /**
     * Imposta una scorciatoia del launcher per un tasto (tipo app).
     */
    fun setLauncherShortcut(context: Context, keyCode: Int, packageName: String, appName: String) {
        setLauncherAction(context, keyCode, LauncherShortcut(
            type = LauncherShortcut.TYPE_APP,
            packageName = packageName,
            appName = appName
        ))
    }
    
    /**
     * Imposta un'azione del launcher per un tasto (generico, estendibile).
     */
    fun setLauncherAction(context: Context, keyCode: Int, action: LauncherShortcut) {
        val prefs = getPreferences(context)
        val shortcutsJson = prefs.getString(KEY_LAUNCHER_SHORTCUTS, "{}") ?: "{}"
        
        try {
            val shortcuts = JSONObject(shortcutsJson)
            shortcuts.put(keyCode.toString(), JSONObject().apply {
                put("type", action.type)
                if (action.packageName != null) put("packageName", action.packageName)
                if (action.appName != null) put("appName", action.appName)
                if (action.action != null) put("action", action.action)
                if (action.data != null) put("data", action.data)
            })
            prefs.edit().putString(KEY_LAUNCHER_SHORTCUTS, shortcuts.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel salvataggio dell'azione per tasto $keyCode", e)
        }
    }
    
    /**
     * Rimuove una scorciatoia del launcher per un tasto.
     */
    fun removeLauncherShortcut(context: Context, keyCode: Int) {
        val prefs = getPreferences(context)
        val shortcutsJson = prefs.getString(KEY_LAUNCHER_SHORTCUTS, "{}") ?: "{}"
        
        try {
            val shortcuts = JSONObject(shortcutsJson)
            shortcuts.remove(keyCode.toString())
            prefs.edit().putString(KEY_LAUNCHER_SHORTCUTS, shortcuts.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Errore nella rimozione della scorciatoia per tasto $keyCode", e)
        }
    }
    
    /**
     * Scambia le scorciatoie del launcher tra due tasti (operazione atomica).
     * Se uno dei tasti non ha uno shortcut, lo shortcut viene spostato.
     */
    fun swapLauncherShortcuts(context: Context, fromKeyCode: Int, toKeyCode: Int) {
        val prefs = getPreferences(context)
        val shortcutsJson = prefs.getString(KEY_LAUNCHER_SHORTCUTS, "{}") ?: "{}"
        
        try {
            val shortcuts = JSONObject(shortcutsJson)
            
            // Get current shortcuts (if any)
            val fromShortcutObj = shortcuts.optJSONObject(fromKeyCode.toString())
            val toShortcutObj = shortcuts.optJSONObject(toKeyCode.toString())
            
            // Swap: remove both first
            shortcuts.remove(fromKeyCode.toString())
            shortcuts.remove(toKeyCode.toString())
            
            // Add swapped shortcuts
            if (fromShortcutObj != null) {
                shortcuts.put(toKeyCode.toString(), fromShortcutObj)
            }
            if (toShortcutObj != null) {
                shortcuts.put(fromKeyCode.toString(), toShortcutObj)
            }
            
            // Save atomically
            prefs.edit().putString(KEY_LAUNCHER_SHORTCUTS, shortcuts.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Errore nello scambio delle scorciatoie tra tasti $fromKeyCode e $toKeyCode", e)
        }
    }
    
    /**
     * Ottiene tutte le scorciatoie del launcher salvate.
     */
    fun getLauncherShortcuts(context: Context): Map<Int, LauncherShortcut> {
        val prefs = getPreferences(context)
        val shortcutsJson = prefs.getString(KEY_LAUNCHER_SHORTCUTS, "{}") ?: "{}"
        val shortcuts = mutableMapOf<Int, LauncherShortcut>()
        
        try {
            val json = JSONObject(shortcutsJson)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val keyCode = key.toIntOrNull()
                if (keyCode != null) {
                    val shortcutObj = json.getJSONObject(key)
                    val type = shortcutObj.optString("type", LauncherShortcut.TYPE_APP)
                    
                    shortcuts[keyCode] = LauncherShortcut(
                        type = type,
                        packageName = shortcutObj.optString("packageName").takeIf { it.isNotEmpty() },
                        appName = shortcutObj.optString("appName").takeIf { it.isNotEmpty() },
                        action = shortcutObj.optString("action").takeIf { it.isNotEmpty() },
                        data = shortcutObj.optString("data").takeIf { it.isNotEmpty() }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel caricamento delle scorciatoie", e)
        }
        
        return shortcuts
    }
    
    /**
     * Ottiene una scorciatoia del launcher per un tasto specifico.
     */
    fun getLauncherShortcut(context: Context, keyCode: Int): LauncherShortcut? {
        return getLauncherShortcuts(context)[keyCode]
    }
    
    /**
     * Restituisce se le scorciatoie del launcher sono abilitate.
     */
    fun getLauncherShortcutsEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_LAUNCHER_SHORTCUTS_ENABLED, DEFAULT_LAUNCHER_SHORTCUTS_ENABLED)
    }
    
    /**
     * Imposta se le scorciatoie del launcher sono abilitate.
     */
    fun setLauncherShortcutsEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_LAUNCHER_SHORTCUTS_ENABLED, enabled)
            .apply()
    }
    
    // Power Shortcuts settings
    private const val KEY_POWER_SHORTCUTS_ENABLED = "power_shortcuts_enabled"
    private const val DEFAULT_POWER_SHORTCUTS_ENABLED = false
    
    /**
     * Restituisce se i Power Shortcuts sono abilitati.
     */
    fun getPowerShortcutsEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_POWER_SHORTCUTS_ENABLED, DEFAULT_POWER_SHORTCUTS_ENABLED)
    }
    
    /**
     * Imposta se i Power Shortcuts sono abilitati.
     */
    fun setPowerShortcutsEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_POWER_SHORTCUTS_ENABLED, enabled)
            .apply()
    }
    
    /**
     * Returns whether nav mode is enabled.
     */
    fun getNavModeEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_NAV_MODE_ENABLED, DEFAULT_NAV_MODE_ENABLED)
    }
    
    /**
     * Sets whether nav mode is enabled.
     */
    fun setNavModeEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_NAV_MODE_ENABLED, enabled)
            .apply()
    }
    
    /**
     * Returns the File for nav mode mappings in filesDir.
     */
    fun getNavModeMappingsFile(context: Context): File {
        return File(context.filesDir, NAV_MODE_MAPPINGS_FILE_NAME)
    }
    
    /**
     * Initializes the nav mode mappings file by copying from assets if it doesn't exist.
     */
    fun initializeNavModeMappingsFile(context: Context) {
        val mappingsFile = getNavModeMappingsFile(context)
        if (mappingsFile.exists()) {
            return // File already exists, don't overwrite
        }
        
        try {
            val inputStream: InputStream = context.assets.open("common/ctrl/$NAV_MODE_MAPPINGS_FILE_NAME")
            val outputStream = FileOutputStream(mappingsFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            Log.d(TAG, "Nav mode mappings file initialized from assets")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing nav mode mappings file", e)
        }
    }
    
    /**
     * Saves nav mode key mappings to the JSON file in filesDir.
     */
    fun saveNavModeKeyMappings(context: Context, mappings: Map<Int, it.palsoftware.pastiera.data.mappings.KeyMappingLoader.CtrlMapping>) {
        try {
            val keyCodeToName = mapOf(
                KeyEvent.KEYCODE_Q to "KEYCODE_Q", KeyEvent.KEYCODE_W to "KEYCODE_W",
                KeyEvent.KEYCODE_E to "KEYCODE_E", KeyEvent.KEYCODE_R to "KEYCODE_R",
                KeyEvent.KEYCODE_T to "KEYCODE_T", KeyEvent.KEYCODE_Y to "KEYCODE_Y",
                KeyEvent.KEYCODE_U to "KEYCODE_U", KeyEvent.KEYCODE_I to "KEYCODE_I",
                KeyEvent.KEYCODE_O to "KEYCODE_O", KeyEvent.KEYCODE_P to "KEYCODE_P",
                KeyEvent.KEYCODE_A to "KEYCODE_A", KeyEvent.KEYCODE_S to "KEYCODE_S",
                KeyEvent.KEYCODE_D to "KEYCODE_D", KeyEvent.KEYCODE_F to "KEYCODE_F",
                KeyEvent.KEYCODE_G to "KEYCODE_G", KeyEvent.KEYCODE_H to "KEYCODE_H",
                KeyEvent.KEYCODE_J to "KEYCODE_J", KeyEvent.KEYCODE_K to "KEYCODE_K",
                KeyEvent.KEYCODE_L to "KEYCODE_L", KeyEvent.KEYCODE_Z to "KEYCODE_Z",
                KeyEvent.KEYCODE_X to "KEYCODE_X", KeyEvent.KEYCODE_C to "KEYCODE_C",
                KeyEvent.KEYCODE_V to "KEYCODE_V", KeyEvent.KEYCODE_B to "KEYCODE_B",
                KeyEvent.KEYCODE_N to "KEYCODE_N", KeyEvent.KEYCODE_M to "KEYCODE_M"
            )
            
            val mappingsObject = JSONObject()
            for ((keyCode, mapping) in mappings) {
                val keyName = keyCodeToName[keyCode]
                if (keyName != null) {
                    val mappingObject = JSONObject()
                    mappingObject.put("type", mapping.type)
                    when (mapping.type) {
                        "action" -> mappingObject.put("action", mapping.value)
                        "keycode" -> mappingObject.put("keycode", mapping.value)
                        "none" -> { /* type is already set */ }
                    }
                    mappingsObject.put(keyName, mappingObject)
                }
            }
            
            // Also include all alphabetic keys that might not be in the mappings map
            // but should be saved as "none" if they're not explicitly set
            val allAlphabeticKeys = listOf(
                KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R,
                KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I,
                KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S,
                KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H,
                KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_Z,
                KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B,
                KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M
            )
            
            // Ensure all alphabetic keys are in the JSON (even if "none")
            allAlphabeticKeys.forEach { keyCode ->
                val keyName = keyCodeToName[keyCode]
                if (keyName != null && !mappingsObject.has(keyName)) {
                    val mappingObject = JSONObject()
                    mappingObject.put("type", "none")
                    mappingsObject.put(keyName, mappingObject)
                }
            }
            
            val jsonObject = JSONObject()
            jsonObject.put("mappings", mappingsObject)
            
            val mappingsFile = getNavModeMappingsFile(context)
            mappingsFile.writeText(jsonObject.toString())
            
            // Update timestamp in SharedPreferences to notify the service
            getPreferences(context).edit()
                .putLong(KEY_NAV_MODE_MAPPINGS_UPDATED, System.currentTimeMillis())
                .apply()
            
            Log.d(TAG, "Nav mode key mappings saved")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving nav mode key mappings", e)
        }
    }
    
    /**
     * Resets nav mode key mappings to default by deleting the custom file.
     */
    fun resetNavModeKeyMappings(context: Context) {
        try {
            val mappingsFile = getNavModeMappingsFile(context)
            if (mappingsFile.exists()) {
                mappingsFile.delete()
                Log.d(TAG, "Nav mode key mappings reset to default")
            }
            // Re-initialize from assets
            initializeNavModeMappingsFile(context)
            
            // Update timestamp in SharedPreferences to notify the service
            getPreferences(context).edit()
                .putLong(KEY_NAV_MODE_MAPPINGS_UPDATED, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting nav mode key mappings", e)
        }
    }
    
    /**
     * Returns true if custom nav mode mappings exist.
     */
    fun hasCustomNavModeMappings(context: Context): Boolean {
        val mappingsFile = getNavModeMappingsFile(context)
        return mappingsFile.exists()
    }
    
    /**
     * Returns the selected keyboard layout name.
     */
    fun getKeyboardLayout(context: Context): String {
        return getPreferences(context).getString(KEY_KEYBOARD_LAYOUT, DEFAULT_KEYBOARD_LAYOUT) ?: DEFAULT_KEYBOARD_LAYOUT
    }
    
    /**
     * Sets the keyboard layout name.
     */
    fun setKeyboardLayout(context: Context, layoutName: String) {
        getPreferences(context).edit()
            .putString(KEY_KEYBOARD_LAYOUT, layoutName)
            .apply()
    }

    private fun isLayoutAvailable(context: Context, layoutName: String): Boolean {
        if (it.palsoftware.pastiera.data.layout.LayoutFileStore.layoutExists(context, layoutName)) {
            return true
        }
        return try {
            val path = "common/layouts/$layoutName.json"
            context.assets.open(path).close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns the list of keyboard layouts configured for cycling.
     * Falls back to a single-entry list using the current layout if no list is stored.
     */
    fun getKeyboardLayoutList(context: Context): List<String> {
        val prefs = getPreferences(context)
        val jsonString = prefs.getString(KEY_KEYBOARD_LAYOUT_LIST, null) ?: return listOf(getKeyboardLayout(context))
        return try {
            val array = org.json.JSONArray(jsonString)
            val seen = LinkedHashSet<String>()
            for (i in 0 until array.length()) {
                val name = array.optString(i, null)?.trim()
                if (!name.isNullOrEmpty()) {
                    seen.add(name)
                }
            }
            if (seen.isEmpty()) {
                listOf(getKeyboardLayout(context))
            } else {
                seen.toList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing keyboard layout list, falling back to single layout", e)
            listOf(getKeyboardLayout(context))
        }
    }

    /**
     * Saves the list of keyboard layouts used for cycling.
     * The caller is responsible for also selecting the active layout via setKeyboardLayout().
     */
    fun setKeyboardLayoutList(context: Context, layouts: List<String>) {
        val normalized = layouts.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (normalized.isEmpty()) {
            // Clear the list to fall back to single-layout behaviour.
            getPreferences(context).edit()
                .remove(KEY_KEYBOARD_LAYOUT_LIST)
                .apply()
            return
        }
        val array = org.json.JSONArray()
        normalized.forEach { array.put(it) }
        getPreferences(context).edit()
            .putString(KEY_KEYBOARD_LAYOUT_LIST, array.toString())
            .apply()
    }

    /**
     * Cycles to the next keyboard layout in the configured list and returns its id.
     * Always loops: even with a single entry we "cycle" back to it, so Ctrl+Space/long press
     * consistently triggers a layout reload/toast and never becomes a no-op.
     */
    fun cycleKeyboardLayout(context: Context): String? {
        val current = getKeyboardLayout(context)
        // Normalize list: keep order, drop blanks/duplicates, ensure at least one entry.
        val baseLayouts = getKeyboardLayoutList(context).ifEmpty { listOf(current) }
        val normalized = if (baseLayouts.contains(current)) baseLayouts else listOf(current) + baseLayouts
        val missing = normalized.filterNot { isLayoutAvailable(context, it) }
        if (missing.isNotEmpty()) {
            Log.w(TAG, "Skipping missing layouts: ${missing.joinToString()}")
        }
        val layouts = normalized.filter { isLayoutAvailable(context, it) }.ifEmpty { listOf(current) }

        val currentIndex = layouts.indexOf(current).let { if (it >= 0) it else 0 }
        val nextIndex = (currentIndex + 1) % layouts.size
        val nextLayout = layouts[nextIndex]
        setKeyboardLayout(context, nextLayout)
        return nextLayout
    }
    
    /**
     * Sets the SYM page to restore when returning from settings.
     * @param context The context
     * @param page The SYM page to restore (0=disabled, 1=page1 emoji, 2=page2 characters)
     */
    fun setRestoreSymPage(context: Context, page: Int) {
        getPreferences(context).edit()
            .putInt(KEY_RESTORE_SYM_PAGE, page)
            .apply()
    }
    
    /**
     * Gets the SYM page to restore when returning from settings.
     * @param context The context
     * @return The SYM page to restore (0=disabled, 1=page1 emoji, 2=page2 characters), or 0 if not set
     */
    fun getRestoreSymPage(context: Context): Int {
        return getPreferences(context).getInt(KEY_RESTORE_SYM_PAGE, 0)
    }
    
    /**
     * Clears the SYM page restore state.
     * @param context The context
     */
    fun clearRestoreSymPage(context: Context) {
        getPreferences(context).edit()
            .remove(KEY_RESTORE_SYM_PAGE)
            .apply()
    }
    
    /**
     * Sets a pending SYM page state when opening SymCustomizationActivity.
     * This will be converted to restore_sym_page only if user presses back.
     * @param context The context
     * @param page The SYM page that was active (0=disabled, 1=page1 emoji, 2=page2 characters)
     */
    fun setPendingRestoreSymPage(context: Context, page: Int) {
        getPreferences(context).edit()
            .putInt(KEY_PENDING_RESTORE_SYM_PAGE, page)
            .apply()
    }
    
    /**
     * Gets the pending SYM page state.
     * @param context The context
     * @return The pending SYM page, or 0 if not set
     */
    fun getPendingRestoreSymPage(context: Context): Int {
        return getPreferences(context).getInt(KEY_PENDING_RESTORE_SYM_PAGE, 0)
    }
    
    /**
     * Clears the pending SYM page state.
     * @param context The context
     */
    fun clearPendingRestoreSymPage(context: Context) {
        getPreferences(context).edit()
            .remove(KEY_PENDING_RESTORE_SYM_PAGE)
            .apply()
    }
    
    /**
     * Confirms the pending restore by moving it to restore_sym_page.
     * Called when user presses back from SymCustomizationActivity.
     * @param context The context
     */
    fun confirmPendingRestoreSymPage(context: Context) {
        val pendingPage = getPendingRestoreSymPage(context)
        if (pendingPage > 0) {
            setRestoreSymPage(context, pendingPage)
            clearPendingRestoreSymPage(context)
        }
    }

    /**
     * Reads the SYM pages configuration (enabled pages and order).
     */
    fun getSymPagesConfig(context: Context): SymPagesConfig {
        val prefs = getPreferences(context)
        val jsonString = prefs.getString(KEY_SYM_PAGES_CONFIG, null) ?: return DEFAULT_SYM_PAGES_CONFIG

        return try {
            val jsonObject = JSONObject(jsonString)
            SymPagesConfig(
                emojiEnabled = jsonObject.optBoolean("emojiEnabled", true),
                symbolsEnabled = jsonObject.optBoolean("symbolsEnabled", true),
                emojiFirst = jsonObject.optBoolean("emojiFirst", true)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading SYM pages config", e)
            DEFAULT_SYM_PAGES_CONFIG
        }
    }

    /**
     * Persists the SYM pages configuration (enabled pages and order).
     */
    fun setSymPagesConfig(context: Context, config: SymPagesConfig) {
        try {
            val jsonObject = JSONObject().apply {
                put("emojiEnabled", config.emojiEnabled)
                put("symbolsEnabled", config.symbolsEnabled)
                put("emojiFirst", config.emojiFirst)
            }

            getPreferences(context).edit()
                .putString(KEY_SYM_PAGES_CONFIG, jsonObject.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving SYM pages config", e)
        }
    }
    
    /**
     * Gets whether SYM layout should auto-close after key press.
     * @param context The context
     * @return true if SYM should auto-close, false otherwise
     */
    fun getSymAutoClose(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SYM_AUTO_CLOSE, DEFAULT_SYM_AUTO_CLOSE)
    }
    
    /**
     * Sets whether SYM layout should auto-close after key press.
     * @param context The context
     * @param enabled true to enable auto-close, false to disable
     */
    fun setSymAutoClose(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SYM_AUTO_CLOSE, enabled)
            .apply()
    }
    
    /**
     * Returns the set of dismissed release tag names.
     * @param context The context
     * @return Set of release tag names that were dismissed by the user
     */
    fun getDismissedReleases(context: Context): Set<String> {
        val prefs = getPreferences(context)
        val dismissedString = prefs.getString(KEY_DISMISSED_RELEASES, null) ?: return emptySet()
        return if (dismissedString.isBlank()) {
            emptySet()
        } else {
            dismissedString.split(",").toSet()
        }
    }
    
    /**
     * Adds a release tag name to the dismissed releases set.
     * @param context The context
     * @param tagName The release tag name to dismiss
     */
    fun addDismissedRelease(context: Context, tagName: String) {
        val dismissed = getDismissedReleases(context).toMutableSet()
        dismissed.add(tagName)
        val dismissedString = dismissed.joinToString(",")
        getPreferences(context).edit()
            .putString(KEY_DISMISSED_RELEASES, dismissedString)
            .apply()
    }
    
    /**
     * Checks if a release tag name has been dismissed.
     * @param context The context
     * @param tagName The release tag name to check
     * @return true if the release was dismissed, false otherwise
     */
    fun isReleaseDismissed(context: Context, tagName: String): Boolean {
        return getDismissedReleases(context).contains(tagName)
    }
    
    /**
     * Checks if the tutorial has been completed.
     * @param context The context
     * @return true if the tutorial has been completed, false otherwise
     */
    fun isTutorialCompleted(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_TUTORIAL_COMPLETED, false)
    }
    
    /**
     * Marks the tutorial as completed.
     * @param context The context
     */
    fun setTutorialCompleted(context: Context) {
        getPreferences(context).edit()
            .putBoolean(KEY_TUTORIAL_COMPLETED, true)
            .apply()
    }
    
    /**
     * Resets the tutorial completion status, allowing it to be shown again.
     * @param context The context
     */
    fun resetTutorialCompleted(context: Context) {
        getPreferences(context).edit()
            .putBoolean(KEY_TUTORIAL_COMPLETED, false)
            .apply()
    }
    
    /**
     * Returns the File for variations.json in filesDir.
     */
    fun getVariationsFile(context: Context): File {
        return File(context.filesDir, VARIATIONS_FILE_NAME)
    }
    
    /**
     * Helper to load current JSON from file or assets.
     */
    private fun loadCurrentJson(context: Context): JSONObject? {
        return try {
            val variationsFile = getVariationsFile(context)
            val jsonString = if (variationsFile.exists()) {
                variationsFile.readText()
            } else {
                context.assets.open("common/variations/variations.json").bufferedReader().use { it.readText() }
            }
            JSONObject(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading current JSON", e)
            null
        }
    }
    
    /**
     * Saves variations to variations.json file in filesDir.
     */
    fun saveVariations(context: Context, variations: Map<String, List<String>>, staticVariations: List<String>? = null) {
        try {
            val variationsObject = JSONObject()
            for ((letter, chars) in variations) {
                val variationsArray = org.json.JSONArray()
                for (char in chars) {
                    variationsArray.put(char)
                }
                variationsObject.put(letter, variationsArray)
            }
            
            val jsonObject = JSONObject()
            jsonObject.put("variations", variationsObject)
            
            // Preserve staticVariations
            if (staticVariations != null) {
                val staticArray = org.json.JSONArray()
                staticVariations.forEach { staticArray.put(it) }
                jsonObject.put("staticVariations", staticArray)
            } else {
                loadCurrentJson(context)?.let { currentJson ->
                    if (currentJson.has("staticVariations")) {
                        jsonObject.put("staticVariations", currentJson.getJSONArray("staticVariations"))
                    }
                }
            }
            
            FileOutputStream(getVariationsFile(context)).use { outputStream ->
                outputStream.write(jsonObject.toString(2).toByteArray(Charsets.UTF_8))
            }
            
            notifyVariationsUpdated(context)
            
            Log.d(TAG, "Variations saved to ${getVariationsFile(context).absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving variations", e)
        }
    }
    
    /**
     * Resets variations back to defaults by copying defaultvariations.json from assets.
     */
    fun resetVariationsToDefault(context: Context) {
        try {
            val variationsFile = getVariationsFile(context)
            val inputStream = context.assets.open("common/variations/defaultvariations.json")
            FileOutputStream(variationsFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            
            notifyVariationsUpdated(context)
            
            Log.d(TAG, "Variations reset to default from assets")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting variations to default", e)
        }
    }
    
    /**
     * Returns true if custom variations file exists.
     */
    fun hasCustomVariations(context: Context): Boolean {
        return getVariationsFile(context).exists()
    }
    
    /**
     * Touch the variations_updated flag so the IME reloads variations/static bar content.
     */
    fun notifyVariationsUpdated(context: Context) {
        getPreferences(context).edit()
            .putLong(KEY_VARIATIONS_UPDATED, System.currentTimeMillis())
            .apply()
    }
}
