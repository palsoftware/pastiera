package it.palsoftware.pastiera.backup

import org.json.JSONArray
import org.json.JSONObject

data class BackupMetadata(
    val versionCode: Int,
    val versionName: String,
    val timestamp: String,
    val components: List<String>
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("versionCode", versionCode)
        json.put("versionName", versionName)
        json.put("timestamp", timestamp)
        val componentsArray = JSONArray()
        components.forEach { componentsArray.put(it) }
        json.put("components", componentsArray)
        return json
    }

    fun toJsonString(): String = toJson().toString(2)

    companion object {
        fun fromFile(file: java.io.File): BackupMetadata? {
            return try {
                val content = file.readText()
                val json = JSONObject(content)
                val componentsArray = json.optJSONArray("components") ?: JSONArray()
                val components = mutableListOf<String>()
                for (i in 0 until componentsArray.length()) {
                    components.add(componentsArray.optString(i))
                }
                BackupMetadata(
                    versionCode = json.optInt("versionCode", 0),
                    versionName = json.optString("versionName", ""),
                    timestamp = json.optString("timestamp", ""),
                    components = components
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

enum class PreferenceValueType {
    BOOLEAN,
    INT,
    LONG,
    FLOAT,
    STRING,
    STRING_SET
}

data class PreferenceValue(
    val type: PreferenceValueType,
    val value: Any?
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("type", type.name.lowercase())
        when (type) {
            PreferenceValueType.STRING_SET -> {
                val array = JSONArray()
                (value as? Set<*>)?.forEach { item ->
                    array.put(item?.toString() ?: "")
                }
                json.put("value", array)
            }

            else -> json.put("value", value ?: JSONObject.NULL)
        }
        return json
    }

    fun coerceTo(expectedType: PreferenceValueType?): PreferenceValue? {
        if (expectedType == null || expectedType == type) {
            return this
        }
        return when (expectedType) {
            PreferenceValueType.BOOLEAN -> {
                val coerced = when (value) {
                    is Boolean -> value
                    is String -> value.toBooleanStrictOrNull()
                    else -> null
                }
                coerced?.let { PreferenceValue(PreferenceValueType.BOOLEAN, it) }
            }

            PreferenceValueType.INT -> {
                val number = (value as? Number)?.toInt() ?: (value as? String)?.toIntOrNull()
                number?.let { PreferenceValue(PreferenceValueType.INT, it) }
            }

            PreferenceValueType.LONG -> {
                val number = (value as? Number)?.toLong() ?: (value as? String)?.toLongOrNull()
                number?.let { PreferenceValue(PreferenceValueType.LONG, it) }
            }

            PreferenceValueType.FLOAT -> {
                val number = (value as? Number)?.toFloat() ?: (value as? String)?.toFloatOrNull()
                number?.let { PreferenceValue(PreferenceValueType.FLOAT, it) }
            }

            PreferenceValueType.STRING -> PreferenceValue(PreferenceValueType.STRING, value?.toString() ?: "")

            PreferenceValueType.STRING_SET -> {
                val setValue = when (value) {
                    is Collection<*> -> value.mapNotNull { it?.toString() }.toSet()
                    is String -> setOf(value)
                    else -> null
                }
                setValue?.let { PreferenceValue(PreferenceValueType.STRING_SET, it) }
            }
        }
    }

    companion object {
        fun fromAny(raw: Any?): PreferenceValue? {
            return when (raw) {
                is Boolean -> PreferenceValue(PreferenceValueType.BOOLEAN, raw)
                is Int -> PreferenceValue(PreferenceValueType.INT, raw)
                is Long -> PreferenceValue(PreferenceValueType.LONG, raw)
                is Float -> PreferenceValue(PreferenceValueType.FLOAT, raw)
                is Double -> PreferenceValue(PreferenceValueType.FLOAT, raw.toFloat())
                is String -> PreferenceValue(PreferenceValueType.STRING, raw)
                is Set<*> -> PreferenceValue(
                    PreferenceValueType.STRING_SET,
                    raw.mapNotNull { it?.toString() }.toSet()
                )
                else -> null
            }
        }

        fun fromJson(json: JSONObject): PreferenceValue? {
            val typeString = json.optString("type", "")
            val type = when (typeString.lowercase()) {
                "boolean" -> PreferenceValueType.BOOLEAN
                "int" -> PreferenceValueType.INT
                "long" -> PreferenceValueType.LONG
                "float" -> PreferenceValueType.FLOAT
                "string" -> PreferenceValueType.STRING
                "string_set" -> PreferenceValueType.STRING_SET
                else -> null
            } ?: return null

            val value = when (type) {
                PreferenceValueType.STRING_SET -> {
                    val array = json.optJSONArray("value") ?: JSONArray()
                    val set = mutableSetOf<String>()
                    for (i in 0 until array.length()) {
                        set.add(array.optString(i))
                    }
                    set.toSet()
                }

                else -> json.opt("value")
            }
            return PreferenceValue(type, value)
        }
    }
}

data class PreferenceFileSchema(
    val prefName: String,
    val fixedKeys: Map<String, PreferenceValueType>,
    val dynamicKeys: List<DynamicKey> = emptyList()
) {
    data class DynamicKey(val prefix: String, val type: PreferenceValueType)

    fun expectedType(key: String): PreferenceValueType? {
        fixedKeys[key]?.let { return it }
        dynamicKeys.firstOrNull { key.startsWith(it.prefix) }?.let { return it.type }
        return null
    }
}

object PreferenceSchemas {
    private val pastieraPrefsSchema = PreferenceFileSchema(
        prefName = "pastiera_prefs",
        fixedKeys = mapOf(
            "long_press_threshold" to PreferenceValueType.LONG,
            "auto_capitalize_first_letter" to PreferenceValueType.BOOLEAN,
            "double_space_to_period" to PreferenceValueType.BOOLEAN,
            "spaced_hyphen_to_en_dash" to PreferenceValueType.BOOLEAN,
            "spaced_hyphen_dash_style" to PreferenceValueType.STRING,
            "mid_word_quote_to_apostrophe" to PreferenceValueType.BOOLEAN,
            "french_punctuation_spacing" to PreferenceValueType.BOOLEAN,
            "french_punctuation_only_french" to PreferenceValueType.BOOLEAN,
            "comma_space" to PreferenceValueType.BOOLEAN,
            "smart_quotes" to PreferenceValueType.BOOLEAN,
            "smart_quotes_style" to PreferenceValueType.STRING,
            "swipe_to_delete" to PreferenceValueType.BOOLEAN,
            "swipe_to_delete_provider" to PreferenceValueType.STRING,
            "tap_haptic_use_system" to PreferenceValueType.BOOLEAN,
            "tap_haptic_duration_ms" to PreferenceValueType.LONG,
            "keyboard_theme_assignment_mode_hardware" to PreferenceValueType.STRING,
            "keyboard_theme_assignment_mode_software" to PreferenceValueType.STRING,
            "keyboard_theme_light_hardware" to PreferenceValueType.STRING,
            "keyboard_theme_light_software" to PreferenceValueType.STRING,
            "keyboard_theme_dark_hardware" to PreferenceValueType.STRING,
            "keyboard_theme_dark_software" to PreferenceValueType.STRING,
            "keyboard_theme_layout_overrides_hardware" to PreferenceValueType.STRING,
            "keyboard_theme_layout_overrides_software" to PreferenceValueType.STRING,
            "modifier_indicator_mode" to PreferenceValueType.STRING,
            "auto_show_keyboard" to PreferenceValueType.BOOLEAN,
            "accessibility_live_announcements_enabled" to PreferenceValueType.BOOLEAN,
            "accessibility_read_second_row_enabled" to PreferenceValueType.BOOLEAN,
            "accessibility_suggestions_announcement_delay_ms" to PreferenceValueType.LONG,
            "bounce_keys_enabled" to PreferenceValueType.BOOLEAN,
            "bounce_keys_delay_ms" to PreferenceValueType.LONG,
            "bounce_keys_character_keys_enabled" to PreferenceValueType.BOOLEAN,
            "bounce_keys_modifier_keys_enabled" to PreferenceValueType.BOOLEAN,
            "bounce_keys_space_enabled" to PreferenceValueType.BOOLEAN,
            "bounce_keys_enter_enabled" to PreferenceValueType.BOOLEAN,
            "bounce_keys_backspace_enabled" to PreferenceValueType.BOOLEAN,
            "clear_alt_on_space" to PreferenceValueType.BOOLEAN,
            "alt_ctrl_speech_shortcut" to PreferenceValueType.BOOLEAN,
            "layout_aware_ctrl_shortcuts" to PreferenceValueType.BOOLEAN,
            "sym_mappings_custom" to PreferenceValueType.STRING,
            "sym_mappings_page2_custom" to PreferenceValueType.STRING,
            "user_dictionary_entries" to PreferenceValueType.STRING,
            "auto_correct_enabled" to PreferenceValueType.BOOLEAN,
            "auto_correct_enabled_languages" to PreferenceValueType.STRING,
            "suggestions_enabled" to PreferenceValueType.BOOLEAN,
            "accent_matching_enabled" to PreferenceValueType.BOOLEAN,
            "auto_replace_on_space_enter" to PreferenceValueType.BOOLEAN,
            "auto_capitalize_after_period" to PreferenceValueType.BOOLEAN,
            "long_press_modifier" to PreferenceValueType.STRING,
            "keyboard_layout" to PreferenceValueType.STRING,
            "keyboard_layout_list" to PreferenceValueType.STRING,
            "input_style_suggestion_locales" to PreferenceValueType.STRING,
            "hidden_system_input_styles" to PreferenceValueType.STRING,
            "restore_sym_page" to PreferenceValueType.INT,
            "pending_restore_sym_page" to PreferenceValueType.INT,
            "sym_pages_config" to PreferenceValueType.STRING,
            "local_media_folder_uri" to PreferenceValueType.STRING,
            "sym_auto_close" to PreferenceValueType.BOOLEAN,
            "emoji_picker_expanded_height" to PreferenceValueType.BOOLEAN,
            "dismissed_releases" to PreferenceValueType.STRING,
            "tutorial_completed" to PreferenceValueType.BOOLEAN,
            "swipe_incremental_threshold" to PreferenceValueType.FLOAT,
            "static_variation_bar_mode" to PreferenceValueType.BOOLEAN,
            "static_variation_bar_preset" to PreferenceValueType.STRING,
            "static_variation_bar_base_layer_enabled" to PreferenceValueType.BOOLEAN,
            "static_variation_bar_modifier_hold_restoration" to PreferenceValueType.BOOLEAN,
            "global_variation_layout_override" to PreferenceValueType.STRING,
            "variations_updated" to PreferenceValueType.LONG,
            "status_bar_slot_left" to PreferenceValueType.STRING,
            "status_bar_slot_right_1" to PreferenceValueType.STRING,
            "status_bar_slot_right_2" to PreferenceValueType.STRING,
            "status_bar_slots_left" to PreferenceValueType.STRING,
            "status_bar_slots_right" to PreferenceValueType.STRING,
            "status_bar_variations_visible" to PreferenceValueType.BOOLEAN,
            "launcher_shortcuts" to PreferenceValueType.STRING,
            "launcher_shortcuts_enabled" to PreferenceValueType.BOOLEAN,
            "quick_launcher_auto_start_single" to PreferenceValueType.BOOLEAN,
            "quick_launcher_limit_results" to PreferenceValueType.BOOLEAN,
            "quick_launcher_text_field_shortcuts" to PreferenceValueType.BOOLEAN,
            "quick_launcher_respect_keyboard_layout" to PreferenceValueType.BOOLEAN,
            "quick_launcher_typo_tolerant_ranking" to PreferenceValueType.BOOLEAN,
            "quick_launcher_width_percent" to PreferenceValueType.INT,
            "quick_launcher_pill_mode" to PreferenceValueType.BOOLEAN,
            "quick_launcher_behavior" to PreferenceValueType.STRING,
            "quick_launcher_animation_duration_ms" to PreferenceValueType.INT,
            "command_surface_sources" to PreferenceValueType.STRING,
            "quick_launcher_command_customizations" to PreferenceValueType.STRING,
            "quick_launcher_highlight_favorites" to PreferenceValueType.BOOLEAN,
            "quick_launcher_favorite_color" to PreferenceValueType.INT,
            "quick_launcher_icon_colors" to PreferenceValueType.BOOLEAN,
            "quick_launcher_show_alias_first" to PreferenceValueType.BOOLEAN,
            "quick_launcher_static_top_highlight" to PreferenceValueType.BOOLEAN,
            "quick_launcher_static_top_highlight_color" to PreferenceValueType.INT,
            "nav_mode_enabled" to PreferenceValueType.BOOLEAN,
            "nav_mode_mappings_updated" to PreferenceValueType.LONG,
            "power_shortcuts_enabled" to PreferenceValueType.BOOLEAN,
            "experimental_suggestions_enabled" to PreferenceValueType.BOOLEAN,
            "suggestion_debug_logging" to PreferenceValueType.BOOLEAN,
            "ime_overlay_debug_logging" to PreferenceValueType.BOOLEAN,
            "max_auto_replace_distance" to PreferenceValueType.INT,
            "additional_ime_subtypes" to PreferenceValueType.STRING_SET,
            "clipboard_history_enabled" to PreferenceValueType.BOOLEAN,
            "clipboard_retention_time" to PreferenceValueType.LONG,
            "trackpad_gestures_enabled" to PreferenceValueType.BOOLEAN,
            "trackpad_gesture_add_word_enabled" to PreferenceValueType.BOOLEAN,
            "trackpad_swipe_threshold" to PreferenceValueType.FLOAT,
            "trackpad_suggestion_swipe_threshold" to PreferenceValueType.FLOAT,
            "trackpad_delete_swipe_threshold" to PreferenceValueType.FLOAT,
            "trackpad_provider" to PreferenceValueType.STRING,
            "pastierina_mode_override" to PreferenceValueType.STRING,
            "pastierina_mode_active" to PreferenceValueType.BOOLEAN,
            "software_keyboard_mode" to PreferenceValueType.STRING,
            "software_keyboard_layout_style" to PreferenceValueType.STRING,
            "software_keyboard_number_row_enabled" to PreferenceValueType.BOOLEAN,
            "software_keyboard_nearest_key_touch_enabled" to PreferenceValueType.BOOLEAN,
            "software_keyboard_left_modifier_key" to PreferenceValueType.STRING,
            "software_keyboard_right_modifier_key" to PreferenceValueType.STRING,
            "software_keyboard_long_press_layer_popup_enabled" to PreferenceValueType.BOOLEAN,
            "software_keyboard_long_press_layer_popup_below_key" to PreferenceValueType.BOOLEAN,
            "sym_auto_close_on_touch" to PreferenceValueType.BOOLEAN,
            "shift_tap_latches" to PreferenceValueType.BOOLEAN,
            "alt_tap_latches" to PreferenceValueType.BOOLEAN,
            "ctrl_tap_latches" to PreferenceValueType.BOOLEAN,
            "alt_latch_stays_on_space" to PreferenceValueType.BOOLEAN,
            "ctrl_latch_stays_on_space" to PreferenceValueType.BOOLEAN,
            "use_keyboard_proximity" to PreferenceValueType.BOOLEAN,
            "use_edit_type_ranking" to PreferenceValueType.BOOLEAN,
            "custom_input_styles" to PreferenceValueType.STRING,
            "titan2_layout_enabled" to PreferenceValueType.BOOLEAN,
            "alt_shift_layout_switch" to PreferenceValueType.BOOLEAN,
            "ctrl_space_layout_switch" to PreferenceValueType.BOOLEAN,
            "physical_keyboard_currency_symbol" to PreferenceValueType.STRING,
            "toast_on_layout_switch" to PreferenceValueType.BOOLEAN,
            "software_keyboard_mode_toggle_toasts" to PreferenceValueType.BOOLEAN
        ),
        dynamicKeys = listOf(
            PreferenceFileSchema.DynamicKey(
                prefix = "auto_correct_custom_",
                type = PreferenceValueType.STRING
            )
        )
    )

    private val schemasByName = mapOf(
        pastieraPrefsSchema.prefName to pastieraPrefsSchema
    )

    fun expectedType(prefName: String, key: String): PreferenceValueType? {
        return schemasByName[prefName]?.expectedType(key)
    }

    fun isRecognized(prefName: String, key: String, currentKeys: Set<String>): Boolean {
        if (currentKeys.contains(key)) {
            return true
        }
        val schema = schemasByName[prefName] ?: return false
        return schema.expectedType(key) != null
    }
}
