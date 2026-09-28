package it.palsoftware.pastiera.backup

import it.palsoftware.pastiera.DeviceIdentitySnapshot
import org.json.JSONArray
import org.json.JSONObject

data class BackupMetadata(
    val versionCode: Int,
    val versionName: String,
    val timestamp: String,
    val components: List<String>,
    val sourceDevice: DeviceIdentitySnapshot? = null
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("versionCode", versionCode)
        json.put("versionName", versionName)
        json.put("timestamp", timestamp)
        val componentsArray = JSONArray()
        components.forEach { componentsArray.put(it) }
        json.put("components", componentsArray)
        sourceDevice?.let { json.put("sourceDevice", it.toJson()) }
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
                    components = components,
                    sourceDevice = json.optJSONObject("sourceDevice")?.toDeviceIdentitySnapshot()
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

private fun DeviceIdentitySnapshot.toJson(): JSONObject = JSONObject().apply {
    stableId?.let { put("stableId", it) }
    put("displayName", displayName)
    put("brand", brand)
    put("manufacturer", manufacturer)
    put("model", model)
    put("device", device)
    put("product", product)
    put("board", board)
    put("buildDisplay", buildDisplay)
    put("buildFingerprint", buildFingerprint)
}

private fun JSONObject.toDeviceIdentitySnapshot(): DeviceIdentitySnapshot = DeviceIdentitySnapshot(
    stableId = optString("stableId").takeIf(String::isNotBlank),
    displayName = optString("displayName", "Unknown device"),
    brand = optString("brand"),
    manufacturer = optString("manufacturer"),
    model = optString("model"),
    device = optString("device"),
    product = optString("product"),
    board = optString("board"),
    buildDisplay = optString("buildDisplay"),
    buildFingerprint = optString("buildFingerprint")
)

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

internal object BackupPreferenceContract {
    private val pastieraPrefsSchema = PreferenceFileSchema(
        prefName = "pastiera_prefs",
        fixedKeys = mapOf(
            "long_press_threshold" to PreferenceValueType.LONG,
            "auto_capitalize_first_letter" to PreferenceValueType.BOOLEAN,
            "auto_capitalize_respect_manual_shift_off" to PreferenceValueType.BOOLEAN,
            "auto_capitalize_restricted_fields" to PreferenceValueType.BOOLEAN,
            "double_space_to_period" to PreferenceValueType.BOOLEAN,
            "spaced_hyphen_to_en_dash" to PreferenceValueType.BOOLEAN,
            "spaced_hyphen_dash_style" to PreferenceValueType.STRING,
            "mid_word_quote_to_apostrophe" to PreferenceValueType.BOOLEAN,
            "french_punctuation_spacing" to PreferenceValueType.BOOLEAN,
            "french_punctuation_only_french" to PreferenceValueType.BOOLEAN,
            "comma_space" to PreferenceValueType.BOOLEAN,
            "auto_space_punctuation" to PreferenceValueType.STRING,
            "space_after_punctuation" to PreferenceValueType.STRING,
            "smart_quotes" to PreferenceValueType.BOOLEAN,
            "smart_quotes_style" to PreferenceValueType.STRING,
            "swipe_to_delete" to PreferenceValueType.BOOLEAN,
            "swipe_to_delete_provider" to PreferenceValueType.STRING,
            "tap_haptic_use_system" to PreferenceValueType.BOOLEAN,
            "tap_haptic_duration_ms" to PreferenceValueType.LONG,
            "typing_sound_mode" to PreferenceValueType.STRING,
            "typing_sound_output_mode" to PreferenceValueType.STRING,
            "typing_sound_custom_file_name" to PreferenceValueType.STRING,
            "typing_sound_custom_display_name" to PreferenceValueType.STRING,
            "app_language_tag" to PreferenceValueType.STRING,
            "app_enter_behavior_enabled" to PreferenceValueType.BOOLEAN,
            "app_enter_behavior_preset" to PreferenceValueType.STRING,
            "app_enter_behavior_overrides" to PreferenceValueType.STRING,
            "keyboard_theme_hardware" to PreferenceValueType.STRING,
            "keyboard_theme_software" to PreferenceValueType.STRING,
            "keyboard_theme_saved_themes" to PreferenceValueType.STRING,
            "keyboard_theme_drafts" to PreferenceValueType.STRING,
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
            "overlapping_keys_enabled" to PreferenceValueType.BOOLEAN,
            "clicks_overlapping_keys_enabled" to PreferenceValueType.BOOLEAN,
            "clicks_overlapping_keys_mode" to PreferenceValueType.STRING,
            "clicks_number_row_input_mode" to PreferenceValueType.STRING,
            "clicks_number_row_repeat_enabled" to PreferenceValueType.BOOLEAN,
            "clear_alt_on_space" to PreferenceValueType.BOOLEAN,
            "alt_ctrl_speech_shortcut" to PreferenceValueType.BOOLEAN,
            "layout_aware_ctrl_shortcuts" to PreferenceValueType.BOOLEAN,
            "sym_mappings_custom" to PreferenceValueType.STRING,
            "sym_mappings_page2_custom" to PreferenceValueType.STRING,
            "user_dictionary_entries" to PreferenceValueType.STRING,
            "auto_correct_enabled" to PreferenceValueType.BOOLEAN,
            "auto_correct_enabled_languages" to PreferenceValueType.STRING,
            "suggestions_enabled" to PreferenceValueType.BOOLEAN,
            "snippets_enabled" to PreferenceValueType.BOOLEAN,
            "snippets_prefix" to PreferenceValueType.STRING,
            "snippets_v1" to PreferenceValueType.STRING,
            "snippets_presentation" to PreferenceValueType.STRING,
            "snippets_exact_on_space" to PreferenceValueType.BOOLEAN,
            "snippets_accept_prefix_with_space" to PreferenceValueType.BOOLEAN,
            "snippets_accept_with_tab" to PreferenceValueType.BOOLEAN,
            "snippets_accept_with_enter" to PreferenceValueType.BOOLEAN,
            "emoji_shortcodes_enabled" to PreferenceValueType.BOOLEAN,
            "symbol_shortcodes_enabled" to PreferenceValueType.BOOLEAN,
            "emoji_symbols_presentation" to PreferenceValueType.STRING,
            "emoji_symbols_exact_on_space" to PreferenceValueType.BOOLEAN,
            "emoji_symbols_accept_prefix_with_space" to PreferenceValueType.BOOLEAN,
            "emoji_symbols_accept_with_tab" to PreferenceValueType.BOOLEAN,
            "emoji_symbols_accept_with_enter" to PreferenceValueType.BOOLEAN,
            "emoji_symbols_exact_on_close" to PreferenceValueType.BOOLEAN,
            "accent_matching_enabled" to PreferenceValueType.BOOLEAN,
            "auto_replace_on_space_enter" to PreferenceValueType.BOOLEAN,
            "auto_capitalize_after_period" to PreferenceValueType.BOOLEAN,
            "long_press_modifier" to PreferenceValueType.STRING,
            "keyboard_layout" to PreferenceValueType.STRING,
            "keyboard_layout_auto_by_locale" to PreferenceValueType.BOOLEAN,
            "keyboard_layout_list" to PreferenceValueType.STRING,
            "input_style_suggestion_locales" to PreferenceValueType.STRING,
            "hidden_system_input_styles" to PreferenceValueType.STRING,
            "sym_pages_config" to PreferenceValueType.STRING,
            "alt_modifier_binding" to PreferenceValueType.STRING,
            "sym_auto_close" to PreferenceValueType.BOOLEAN,
            "emoji_picker_expanded_height" to PreferenceValueType.BOOLEAN,
            "swipe_incremental_threshold" to PreferenceValueType.FLOAT,
            "static_variation_bar_mode" to PreferenceValueType.BOOLEAN,
            "static_variation_bar_preset" to PreferenceValueType.STRING,
            "static_variation_bar_base_layer_enabled" to PreferenceValueType.BOOLEAN,
            "static_variation_bar_modifier_hold_restoration" to PreferenceValueType.BOOLEAN,
            "global_variation_layout_override" to PreferenceValueType.STRING,
            "status_bar_slot_left" to PreferenceValueType.STRING,
            "status_bar_slot_right_1" to PreferenceValueType.STRING,
            "status_bar_slot_right_2" to PreferenceValueType.STRING,
            "status_bar_slots_left" to PreferenceValueType.STRING,
            "status_bar_slots_right" to PreferenceValueType.STRING,
            "pastierina_status_bar_slots_left" to PreferenceValueType.STRING,
            "pastierina_status_bar_slots_right" to PreferenceValueType.STRING,
            "status_bar_variations_visible" to PreferenceValueType.BOOLEAN,
            "status_bar_hidden" to PreferenceValueType.BOOLEAN,
            "dynamic_variation_bar_slot_count" to PreferenceValueType.INT,
            "dynamic_variation_bar_resize_to_content" to PreferenceValueType.BOOLEAN,
            "launcher_shortcuts" to PreferenceValueType.STRING,
            "launcher_shortcuts_enabled" to PreferenceValueType.BOOLEAN,
            "quick_launcher_auto_start_single" to PreferenceValueType.BOOLEAN,
            "quick_launcher_limit_results" to PreferenceValueType.BOOLEAN,
            "quick_launcher_text_field_shortcuts" to PreferenceValueType.BOOLEAN,
            "quick_launcher_alt_space_in_text_fields" to PreferenceValueType.BOOLEAN,
            "quick_launcher_alt_shortcuts_outside_text_fields" to PreferenceValueType.BOOLEAN,
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
            "nav_mode_ctrl_hold_enabled" to PreferenceValueType.BOOLEAN,
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
            "trackpad_gesture_add_word_full_width_enabled" to PreferenceValueType.BOOLEAN,
            "trackpad_swipe_threshold" to PreferenceValueType.FLOAT,
            "trackpad_suggestion_swipe_threshold" to PreferenceValueType.FLOAT,
            "trackpad_delete_swipe_threshold" to PreferenceValueType.FLOAT,
            "trackpad_provider" to PreferenceValueType.STRING,
            "trackpad_shizuku_device" to PreferenceValueType.STRING,
            "pastierina_mode_override" to PreferenceValueType.STRING,
            "software_keyboard_mode" to PreferenceValueType.STRING,
            "physical_keyboard_profile_override" to PreferenceValueType.STRING,
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
            "titan2_elite_rounded_corner_insets" to PreferenceValueType.BOOLEAN,
            "titan2_elite_fill_bottom_corners" to PreferenceValueType.BOOLEAN,
            "titan2_elite_top_corner_multiplier" to PreferenceValueType.INT,
            "titan2_elite_max_icon_shrink" to PreferenceValueType.INT,
            "experimental_candidates_view_enabled" to PreferenceValueType.BOOLEAN,
            "alt_shift_layout_switch" to PreferenceValueType.BOOLEAN,
            "ctrl_space_layout_switch" to PreferenceValueType.BOOLEAN,
            "physical_keyboard_currency_symbol" to PreferenceValueType.STRING,
            "clicks_close_input_on_disconnect" to PreferenceValueType.BOOLEAN,
            "clicks_show_keyboard_only_with_text_focus" to PreferenceValueType.BOOLEAN,
            "clicks_button_mode" to PreferenceValueType.STRING,
            "clicks_meta_button_mode" to PreferenceValueType.STRING,
            "clicks_alt_button_mode" to PreferenceValueType.STRING,
            "clicks_microphone_button_mode" to PreferenceValueType.STRING,
            "clicks_red_button_binding_choice" to PreferenceValueType.STRING,
            "clicks_red_button_binding_output" to PreferenceValueType.STRING,
            "clicks_keyboard_button_binding_choice" to PreferenceValueType.STRING,
            "clicks_keyboard_button_binding_output" to PreferenceValueType.STRING,
            "clicks_microphone_button_binding_choice" to PreferenceValueType.STRING,
            "clicks_microphone_button_binding_output" to PreferenceValueType.STRING,
            "clicks_power_keyboard_snapshots_v1" to PreferenceValueType.STRING,
            "clicks_charging_automation" to PreferenceValueType.BOOLEAN,
            "clicks_charging_start_percent" to PreferenceValueType.INT,
            "clicks_charging_stop_percent" to PreferenceValueType.INT,
            "toast_on_layout_switch" to PreferenceValueType.BOOLEAN,
            "software_keyboard_mode_toggle_toasts" to PreferenceValueType.BOOLEAN,
            "alt_enter_layout_switch" to PreferenceValueType.BOOLEAN,
            "shift_backspace_delete" to PreferenceValueType.BOOLEAN,
            "alt_backspace_delete" to PreferenceValueType.BOOLEAN,
            "backspace_at_start_delete" to PreferenceValueType.BOOLEAN
        ),
        dynamicKeys = listOf(
            PreferenceFileSchema.DynamicKey(
                prefix = "auto_correct_custom_",
                type = PreferenceValueType.STRING
            ),
            PreferenceFileSchema.DynamicKey(
                prefix = "clicks_power_soc_calibration_",
                type = PreferenceValueType.STRING
            )
        )
    )

    private val schemasByName = mapOf(
        pastieraPrefsSchema.prefName to pastieraPrefsSchema
    )

    private val excludedPreferenceFiles = setOf(
        "app_list_cache_prefs",
        "perf_img_scale",
        "recent_emojis_prefs"
    )

    internal val deliberatelyExcludedPastieraKeys = mapOf(
        "alt_shift_default_initialized" to "default-initialization marker",
        "keyboard_layout_auto_mapping_updated" to "mapping refresh marker",
        "legacy_german_qwertz_default_migrated" to "migration marker",
        "titan2_elite_rounded_corners_enforced_v1" to "migration marker",
        "last_seen_whats_new_version" to "release UI state",
        "nav_mode_default_mappings_version" to "default-migration marker",
        "nav_mode_mappings_updated" to "runtime refresh marker",
        "quick_launcher_default_assigned" to "default-initialization marker",
        "current_sym_page" to "transient UI state",
        "restore_sym_page" to "transient UI state",
        "pending_restore_sym_page" to "transient UI state",
        "keyboard_theme_preview_viewport_scale" to "preview-only UI state",
        "dismissed_releases" to "release UI state",
        "tutorial_completed" to "onboarding UI state",
        "variations_updated" to "runtime refresh marker",
        "pastierina_mode_active" to "runtime-derived state",
        "software_keyboard_mode_runtime_override" to "runtime-derived state",
        "typing_sound_updated_at" to "runtime refresh marker",
        "clicks_bluetooth_permission_explained" to "permission UI state",
        "clicks_manual_charging_until" to "transient runtime state"
    )

    fun expectedExportType(prefName: String, key: String): PreferenceValueType? {
        return schemasByName[prefName]?.expectedType(key)
    }

    fun isExportable(prefName: String, key: String): Boolean =
        !excludedPreferenceFiles.contains(prefName) && expectedExportType(prefName, key) != null

    fun shouldExportPreferenceFile(prefName: String): Boolean =
        !excludedPreferenceFiles.contains(prefName) && schemasByName.containsKey(prefName)
}

object PreferenceSchemas {
    private val restoreOnlyPastieraKeys = mapOf(
        // Accepted so backups from releases before the Device-SYM terminology migration restore cleanly.
        "alt_character_layer_binding" to PreferenceValueType.STRING,
        "restore_sym_page" to PreferenceValueType.INT,
        "pending_restore_sym_page" to PreferenceValueType.INT,
        "dismissed_releases" to PreferenceValueType.STRING,
        "tutorial_completed" to PreferenceValueType.BOOLEAN,
        "variations_updated" to PreferenceValueType.LONG,
        "nav_mode_mappings_updated" to PreferenceValueType.LONG,
        "pastierina_mode_active" to PreferenceValueType.BOOLEAN,
        "software_keyboard_mode_runtime_override" to PreferenceValueType.STRING
    )

    fun expectedType(prefName: String, key: String): PreferenceValueType? {
        return BackupPreferenceContract.expectedExportType(prefName, key)
            ?: if (prefName == "pastiera_prefs") restoreOnlyPastieraKeys[key] else null
    }

    fun isRecognized(prefName: String, key: String): Boolean =
        expectedType(prefName, key) != null
}
