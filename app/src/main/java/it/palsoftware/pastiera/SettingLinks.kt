package it.palsoftware.pastiera

import android.content.Context
import android.net.Uri
import java.text.Normalizer

/**
 * Stable identifiers for addressable settings entries. They are the path segment
 * of deep links (`pastiera://setting/<id>`) and must never change once shipped.
 */
object SettingLinkIds {
    // Main screen navigation rows
    const val MAIN_KEYBOARDS_DEVICES = "main.keyboards_devices"
    const val MAIN_MODIFIERS = "main.modifiers"
    const val MAIN_CUSTOM_INPUT_STYLES = "main.custom_input_styles"
    const val MAIN_TEXT_INPUT = "main.text_input"
    const val MAIN_AUTO_CORRECTION = "main.auto_correction"
    const val MAIN_KEYBOARD_THEME = "main.keyboard_theme"
    const val MAIN_APP_LANGUAGE = "main.app_language"
    const val MAIN_STATUS_BAR_BUTTONS = "main.status_bar_buttons"
    const val MAIN_CUSTOMIZATION = "main.customization"
    const val MAIN_LAUNCHER_SHORTCUTS = "main.launcher_shortcuts"
    const val MAIN_NAV_MODE = "main.nav_mode"
    const val MAIN_APP_ENTER_BEHAVIOR = "main.app_enter_behavior"
    const val MAIN_ADVANCED = "main.advanced"
    const val MAIN_ACCESSIBILITY = "main.accessibility"
    const val MAIN_ABOUT = "main.about"

    // Text input screen
    const val TEXT_INPUT_TEXT_EXPANSION = "text_input.text_expansion"
    const val TEXT_INPUT_AUTO_CAPITALIZE = "text_input.auto_capitalize"
    const val TEXT_INPUT_AUTO_CAPITALIZE_RESPECT_MANUAL_SHIFT_OFF =
        "text_input.auto_capitalize_respect_manual_shift_off"
    const val TEXT_INPUT_AUTO_CAPITALIZE_RESTRICTED_FIELDS =
        "text_input.auto_capitalize_restricted_fields"
    const val TEXT_INPUT_AUTO_CAPITALIZE_AFTER_PERIOD = "text_input.auto_capitalize_after_period"
    const val TEXT_INPUT_DOUBLE_SPACE_TO_PERIOD = "text_input.double_space_to_period"
    const val TEXT_INPUT_AUTO_SPACE_PUNCTUATION = "text_input.auto_space_punctuation"
    const val TEXT_INPUT_COMMA_SPACE = "text_input.comma_space"
    const val TEXT_INPUT_FRENCH_PUNCTUATION_SPACING = "text_input.french_punctuation_spacing"
    const val TEXT_INPUT_FRENCH_PUNCTUATION_ONLY_FRENCH = "text_input.french_punctuation_only_french"
    const val TEXT_INPUT_SPACED_HYPHEN_TO_EN_DASH = "text_input.spaced_hyphen_to_en_dash"
    const val TEXT_INPUT_MID_WORD_QUOTE_TO_APOSTROPHE = "text_input.mid_word_quote_to_apostrophe"
    const val TEXT_INPUT_SMART_QUOTES = "text_input.smart_quotes"
    const val TEXT_INPUT_CLEAR_ALT_ON_SPACE = "text_input.clear_alt_on_space"
    const val TEXT_INPUT_AUTO_SHOW_KEYBOARD = "text_input.auto_show_keyboard"
    const val TEXT_INPUT_ALT_CTRL_SPEECH_SHORTCUT = "text_input.alt_ctrl_speech_shortcut"
    const val TEXT_INPUT_SHIFT_BACKSPACE_DELETE = "text_input.shift_backspace_delete"
    const val TEXT_INPUT_ALT_BACKSPACE_DELETE = "text_input.alt_backspace_delete"
    const val TEXT_INPUT_BACKSPACE_AT_START_DELETE = "text_input.backspace_at_start_delete"
    const val TEXT_INPUT_DELETE_NAV_MODE = "text_input.delete_nav_mode"

    // Keyboards & devices screen
    const val KEYBOARDS_DEVICES_KEYBOARD_SWITCHING_AUTO = "keyboards_devices.keyboard_switching_auto"
    const val KEYBOARDS_DEVICES_KEYBOARD_SWITCHING_SHORTCUT =
        "keyboards_devices.keyboard_switching_shortcut"
    const val KEYBOARDS_DEVICES_TOGGLE_TOASTS = "keyboards_devices.toggle_toasts"
    const val KEYBOARDS_DEVICES_ON_SCREEN_KEYBOARD = "keyboards_devices.on_screen_keyboard"
    const val KEYBOARDS_DEVICES_BUILT_IN_KEYBOARDS = "keyboards_devices.built_in_keyboards"
    const val KEYBOARDS_DEVICES_KEYBOARD_ACCESSORIES = "keyboards_devices.keyboard_accessories"

    // Auto-correction category screen
    const val AUTO_CORRECTION_TEXT_REPLACEMENTS = "auto_correction.text_replacements"
    const val AUTO_CORRECTION_LANGUAGES = "auto_correction.languages"
    const val AUTO_CORRECTION_AUTO_REPLACE = "auto_correction.auto_replace"
    const val AUTO_CORRECTION_MAX_DISTANCE = "auto_correction.max_distance"
    const val AUTO_CORRECTION_USER_DICTIONARY = "auto_correction.user_dictionary"
    const val AUTO_CORRECTION_EXPERIMENTAL_SUGGESTIONS = "auto_correction.experimental_suggestions"
    const val AUTO_CORRECTION_SUGGESTIONS = "auto_correction.suggestions"
    const val AUTO_CORRECTION_ACCENT_MATCHING = "auto_correction.accent_matching"
    const val AUTO_CORRECTION_KEYBOARD_PROXIMITY = "auto_correction.keyboard_proximity"
    const val AUTO_CORRECTION_EDIT_TYPE_RANKING = "auto_correction.edit_type_ranking"

    // Accessibility screen
    const val ACCESSIBILITY_LIVE_READ = "accessibility.live_read"
    const val ACCESSIBILITY_READ_SECOND_ROW = "accessibility.read_second_row"
    const val ACCESSIBILITY_SUGGESTIONS_DELAY = "accessibility.suggestions_delay"
    const val ACCESSIBILITY_BOUNCE_KEYS = "accessibility.bounce_keys"
    const val ACCESSIBILITY_BOUNCE_KEYS_CHARACTER_KEYS = "accessibility.bounce_keys_character_keys"
    const val ACCESSIBILITY_BOUNCE_KEYS_MODIFIER_KEYS = "accessibility.bounce_keys_modifier_keys"
    const val ACCESSIBILITY_BOUNCE_KEYS_SPACE = "accessibility.bounce_keys_space"
    const val ACCESSIBILITY_BOUNCE_KEYS_ENTER = "accessibility.bounce_keys_enter"
    const val ACCESSIBILITY_BOUNCE_KEYS_BACKSPACE = "accessibility.bounce_keys_backspace"
    const val ACCESSIBILITY_OVERLAPPING_KEYS = "accessibility.overlapping_keys"

    // Advanced screen
    const val ADVANCED_TRACKPAD_GESTURES = "advanced.trackpad_gestures"
    const val ADVANCED_BACKUP = "advanced.backup"
    const val ADVANCED_RESTORE = "advanced.restore"
    const val ADVANCED_SWIPE_INCREMENTAL_THRESHOLD = "advanced.swipe_incremental_threshold"
    const val ADVANCED_CLIPBOARD_RETENTION_TIME = "advanced.clipboard_retention_time"
    const val ADVANCED_EXPERIMENTAL_CANDIDATES_VIEW = "advanced.experimental_candidates_view"
    const val ADVANCED_SHOW_TUTORIAL = "advanced.show_tutorial"
    const val ADVANCED_SHOW_RELEASE_NOTES_TUTORIAL = "advanced.show_release_notes_tutorial"

    // Trackpad gesture screen
    const val TRACKPAD_GESTURES_ENABLED = "trackpad.gestures_enabled"
    const val TRACKPAD_PROVIDER = "trackpad.provider"
    const val TRACKPAD_SHIZUKU_DEVICE = "trackpad.shizuku_device"
    const val TRACKPAD_SENSITIVITY = "trackpad.sensitivity"
    const val TRACKPAD_SUGGESTION_SWIPE_THRESHOLD = "trackpad.suggestion_swipe_threshold"
    const val TRACKPAD_DELETE_SWIPE_THRESHOLD = "trackpad.delete_swipe_threshold"
    const val TRACKPAD_DEBUG = "trackpad.debug"

    // Modifiers screen
    const val MODIFIERS_LONG_PRESS_MODIFIER = "modifiers.long_press_modifier"
    const val MODIFIERS_LONG_PRESS_THRESHOLD = "modifiers.long_press_threshold"
    const val MODIFIERS_INDICATORS = "modifiers.indicators"
    const val MODIFIERS_SYM_LAYERS = "modifiers.sym_layers"
    const val MODIFIERS_SYM_SHORTCUTS = "modifiers.sym_shortcuts"
    const val MODIFIERS_ALT_BINDING = "modifiers.alt_binding"
    const val MODIFIERS_ALT_KEY_SHORTCUTS = "modifiers.alt_key_shortcuts"
    const val MODIFIERS_CONTROL_NAV_MODE = "modifiers.control_nav_mode"
    const val MODIFIERS_SHIFT_TAP_LATCHES = "modifiers.shift_tap_latches"
    const val MODIFIERS_ALT_TAP_LATCHES = "modifiers.alt_tap_latches"
    const val MODIFIERS_ALT_LATCH_STAYS_ON_SPACE = "modifiers.alt_latch_stays_on_space"
    const val MODIFIERS_CTRL_TAP_LATCHES = "modifiers.ctrl_tap_latches"
    const val MODIFIERS_CTRL_LATCH_STAYS_ON_SPACE = "modifiers.ctrl_latch_stays_on_space"

    // Custom input styles screen
    const val CUSTOM_INPUT_STYLES_LAYOUT_MODE = "custom_input_styles.layout_mode"
    const val CUSTOM_INPUT_STYLES_ALT_SHIFT_LAYOUT_SWITCH =
        "custom_input_styles.alt_shift_layout_switch"
    const val CUSTOM_INPUT_STYLES_ALT_ENTER_LAYOUT_SWITCH =
        "custom_input_styles.alt_enter_layout_switch"
    const val CUSTOM_INPUT_STYLES_CTRL_SPACE_LAYOUT_SWITCH =
        "custom_input_styles.ctrl_space_layout_switch"
    const val CUSTOM_INPUT_STYLES_LAYOUT_SWITCH_TOAST = "custom_input_styles.layout_switch_toast"

    // About screen
    const val ABOUT_SUPPORT_KO_FI = "about.support_ko_fi"

    // Keyboard theme editor (customization sub-screen): behaviour toggles and
    // the aggregated LED colors. Individual color picker fields stay
    // unregistered by design.
    const val KEYBOARD_THEME_TOGGLE_SHOW_LEDS = "keyboard_theme.toggle_show_leds"
    const val KEYBOARD_THEME_TOGGLE_DISTRIBUTE_SPACING = "keyboard_theme.toggle_distribute_spacing"
    const val KEYBOARD_THEME_TOGGLE_ORTHOLINEAR = "keyboard_theme.toggle_ortholinear"
    const val KEYBOARD_THEME_TOGGLE_ATTACH_POPUP = "keyboard_theme.toggle_attach_popup"
    const val KEYBOARD_THEME_TOGGLE_POPUP_TAIL = "keyboard_theme.toggle_popup_tail"
    const val KEYBOARD_THEME_TOGGLE_PREVIEW_ON_HOLD = "keyboard_theme.toggle_preview_on_hold"
    const val KEYBOARD_THEME_TOGGLE_CHARACTER_PICKER = "keyboard_theme.toggle_character_picker"
    const val KEYBOARD_THEME_LED_COLORS = "keyboard_theme.led_colors"

    // Modifier indicator chips (rendered on the modifiers screen)
    const val MODIFIERS_INDICATOR_BOTTOM_STRIP = "modifiers.indicator_bottom_strip"
    const val MODIFIERS_INDICATOR_MENU_BAR = "modifiers.indicator_menu_bar"
    const val MODIFIERS_INDICATOR_STATUS_BAR = "modifiers.indicator_status_bar"
}

/**
 * Where a settings entry lives in the settings navigation tree.
 */
data class SettingRoute(
    val destination: SettingsDestination,
    val customizationDestination: String? = null,
    val keyboardThemeTarget: SettingsManager.KeyboardThemeTarget? = null,
    val keyboardThemeTab: KeyboardThemeEditorTab? = null,
    val keyboardsDevicesDestination: KeyboardsDevicesDestination = KeyboardsDevicesDestination.Main,
    val symCustomization: Boolean = false
)

enum class KeyboardThemeEditorTab {
    Colors,
    Keys
}

enum class SettingAvailability {
    Always,
    AutoCapitalizeEnabled,
    FrenchPunctuationSpacingEnabled,
    TextReplacementsEnabled,
    AutoReplaceEnabled,
    CtrlTapLatchesEnabled,
    TrackpadShizukuProvider
}

data class SettingEntry(
    val id: String,
    val titleRes: Int,
    val summaryRes: Int? = null,
    val route: SettingRoute,
    val availability: SettingAvailability = SettingAvailability.Always,
    val unavailableFallbackId: String? = null,
    val availabilityCheck: ((Context) -> Boolean)? = null
) {
    fun isAvailable(context: Context): Boolean = (availabilityCheck?.invoke(context) ?: true) && when (availability) {
        SettingAvailability.Always -> true
        SettingAvailability.AutoCapitalizeEnabled ->
            SettingsManager.getAutoCapitalizeFirstLetter(context)
        SettingAvailability.FrenchPunctuationSpacingEnabled ->
            SettingsManager.getFrenchPunctuationSpacing(context)
        SettingAvailability.TextReplacementsEnabled ->
            SettingsManager.getAutoCorrectEnabled(context)
        SettingAvailability.AutoReplaceEnabled ->
            SettingsManager.getAutoReplaceOnSpaceEnter(context)
        SettingAvailability.CtrlTapLatchesEnabled ->
            SettingsManager.getCtrlTapLatches(context)
        SettingAvailability.TrackpadShizukuProvider ->
            SettingsManager.getTrackpadProvider(context) == SettingsManager.TRACKPAD_PROVIDER_SHIZUKU
    }
}

/**
 * Registry of all addressable settings entries. It is the single source of
 * truth for the settings search and for resolving deep links.
 */
object SettingLinkRegistry {

    const val LINK_SCHEME = "pastiera"
    const val LINK_HOST = "setting"
    const val WEB_LINK_HOST = "pastiera.eu"
    const val ALTERNATE_WEB_LINK_HOST = "pkb.rocks"
    const val WEB_LINK_PATH_PREFIX = "/settings/"
    private val settingIdPattern = Regex("^[a-z0-9_]+(\\.[a-z0-9_]+)+$")

    private fun entry(
        id: String,
        titleRes: Int,
        summaryRes: Int? = null,
        destination: SettingsDestination,
        customizationDestination: String? = null,
        keyboardThemeTarget: SettingsManager.KeyboardThemeTarget? = null,
        keyboardThemeTab: KeyboardThemeEditorTab? = null,
        availability: SettingAvailability = SettingAvailability.Always,
        unavailableFallbackId: String? = null
    ) = SettingEntry(
        id = id,
        titleRes = titleRes,
        summaryRes = summaryRes,
        route = SettingRoute(
            destination = destination,
            customizationDestination = customizationDestination,
            keyboardThemeTarget = keyboardThemeTarget,
            keyboardThemeTab = keyboardThemeTab
        ),
        availability = availability,
        unavailableFallbackId = unavailableFallbackId
    )

    val entries: List<SettingEntry> = listOf(
        entry(
            SettingLinkIds.MAIN_KEYBOARDS_DEVICES,
            R.string.keyboards_devices_title,
            destination = SettingsDestination.KeyboardsDevices
        ),
        entry(
            SettingLinkIds.MAIN_MODIFIERS,
            R.string.modifiers_title,
            R.string.modifiers_description,
            destination = SettingsDestination.Modifiers
        ),
        entry(
            SettingLinkIds.MAIN_CUSTOM_INPUT_STYLES,
            R.string.custom_input_styles_title,
            destination = SettingsDestination.CustomInputStyles
        ),
        entry(
            SettingLinkIds.MAIN_TEXT_INPUT,
            R.string.settings_category_text_input,
            destination = SettingsDestination.TextInput
        ),
        entry(
            SettingLinkIds.MAIN_AUTO_CORRECTION,
            R.string.settings_category_auto_correction,
            destination = SettingsDestination.AutoCorrection
        ),
        entry(
            SettingLinkIds.MAIN_KEYBOARD_THEME,
            R.string.keyboard_theme_title,
            destination = SettingsDestination.Customization,
            customizationDestination = SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME
        ),
        entry(
            SettingLinkIds.MAIN_APP_LANGUAGE,
            R.string.app_language_title,
            destination = SettingsDestination.AppLanguage
        ),
        entry(
            SettingLinkIds.MAIN_STATUS_BAR_BUTTONS,
            R.string.status_bar_buttons_title,
            R.string.status_bar_buttons_description,
            destination = SettingsDestination.Customization,
            customizationDestination = SettingsActivity.CUSTOMIZATION_DESTINATION_STATUS_BAR_BUTTONS
        ),
        entry(
            SettingLinkIds.MAIN_CUSTOMIZATION,
            R.string.settings_category_customization,
            destination = SettingsDestination.Customization
        ),
        entry(
            SettingLinkIds.MAIN_LAUNCHER_SHORTCUTS,
            R.string.starter_launcher_shortcuts_title,
            R.string.starter_launcher_shortcuts_description,
            destination = SettingsDestination.Customization,
            customizationDestination = SettingsActivity.CUSTOMIZATION_DESTINATION_LAUNCHER_SHORTCUTS
        ),
        entry(
            SettingLinkIds.MAIN_NAV_MODE,
            R.string.nav_mode_title,
            R.string.settings_nav_mode_configure,
            destination = SettingsDestination.NavMode
        ),
        entry(
            SettingLinkIds.MAIN_APP_ENTER_BEHAVIOR,
            R.string.app_enter_behaviour_title,
            R.string.app_enter_behaviour_description,
            destination = SettingsDestination.Customization,
            customizationDestination = SettingsActivity.CUSTOMIZATION_DESTINATION_APP_ENTER_BEHAVIOR
        ),
        entry(
            SettingLinkIds.MAIN_ADVANCED,
            R.string.settings_category_advanced,
            destination = SettingsDestination.Advanced
        ),
        entry(
            SettingLinkIds.MAIN_ACCESSIBILITY,
            R.string.settings_category_accessibility,
            destination = SettingsDestination.Accessibility
        ),
        entry(
            SettingLinkIds.MAIN_ABOUT,
            R.string.about_title,
            destination = SettingsDestination.About
        ),

        entry(
            SettingLinkIds.TEXT_INPUT_TEXT_EXPANSION,
            R.string.text_expansion_title,
            R.string.text_expansion_description,
            destination = SettingsDestination.TextInput
        ),
        entry(
            SettingLinkIds.TEXT_INPUT_AUTO_CAPITALIZE,
            R.string.auto_capitalize_title,
            R.string.auto_capitalize_description,
            destination = SettingsDestination.TextInput
        ),
        entry(
            SettingLinkIds.TEXT_INPUT_AUTO_CAPITALIZE_RESPECT_MANUAL_SHIFT_OFF,
            R.string.auto_capitalize_respect_manual_shift_off_title,
            R.string.auto_capitalize_respect_manual_shift_off_description,
            destination = SettingsDestination.TextInput,
            availability = SettingAvailability.AutoCapitalizeEnabled,
            unavailableFallbackId = SettingLinkIds.TEXT_INPUT_AUTO_CAPITALIZE
        ),
        entry(
            SettingLinkIds.TEXT_INPUT_AUTO_CAPITALIZE_RESTRICTED_FIELDS,
            R.string.auto_capitalize_restricted_fields_title,
            R.string.auto_capitalize_restricted_fields_description,
            destination = SettingsDestination.TextInput,
            availability = SettingAvailability.AutoCapitalizeEnabled,
            unavailableFallbackId = SettingLinkIds.TEXT_INPUT_AUTO_CAPITALIZE
        ),
        entry(
            SettingLinkIds.TEXT_INPUT_AUTO_CAPITALIZE_AFTER_PERIOD,
            R.string.auto_capitalize_after_period_title,
            R.string.auto_capitalize_after_period_description,
            destination = SettingsDestination.TextInput
        ),
        entry(
            SettingLinkIds.TEXT_INPUT_DOUBLE_SPACE_TO_PERIOD,
            R.string.double_space_to_period_title,
            R.string.double_space_to_period_description,
            destination = SettingsDestination.TextInput
        ),
        entry(
            SettingLinkIds.TEXT_INPUT_AUTO_SPACE_PUNCTUATION,
            R.string.auto_space_punctuation_title,
            destination = SettingsDestination.TextInput
        ),
        entry(
            SettingLinkIds.TEXT_INPUT_COMMA_SPACE,
            R.string.comma_space_title,
            R.string.comma_space_description,
            destination = SettingsDestination.TextInput
        ),
        entry(
            SettingLinkIds.TEXT_INPUT_FRENCH_PUNCTUATION_SPACING,
            R.string.french_punctuation_spacing_title,
            R.string.french_punctuation_spacing_description,
            destination = SettingsDestination.TextInput
        ),
        entry(
            SettingLinkIds.TEXT_INPUT_FRENCH_PUNCTUATION_ONLY_FRENCH,
            R.string.french_punctuation_only_french_title,
            R.string.french_punctuation_only_french_description,
            destination = SettingsDestination.TextInput,
            availability = SettingAvailability.FrenchPunctuationSpacingEnabled,
            unavailableFallbackId = SettingLinkIds.TEXT_INPUT_FRENCH_PUNCTUATION_SPACING
        ),
        entry(
            SettingLinkIds.TEXT_INPUT_SPACED_HYPHEN_TO_EN_DASH,
            R.string.spaced_hyphen_to_en_dash_title,
            destination = SettingsDestination.TextInput
        ),
        entry(
            SettingLinkIds.TEXT_INPUT_MID_WORD_QUOTE_TO_APOSTROPHE,
            R.string.mid_word_quote_to_apostrophe_title,
            R.string.mid_word_quote_to_apostrophe_description,
            destination = SettingsDestination.TextInput
        ),
        entry(
            SettingLinkIds.TEXT_INPUT_SMART_QUOTES,
            R.string.smart_quotes_title,
            destination = SettingsDestination.TextInput
        ),
        entry(
            SettingLinkIds.TEXT_INPUT_CLEAR_ALT_ON_SPACE,
            R.string.clear_alt_on_space_title,
            R.string.clear_alt_on_space_description,
            destination = SettingsDestination.TextInput
        ),
        entry(
            SettingLinkIds.TEXT_INPUT_AUTO_SHOW_KEYBOARD,
            R.string.auto_show_keyboard_title,
            R.string.auto_show_keyboard_description,
            destination = SettingsDestination.TextInput
        ),
        entry(
            SettingLinkIds.TEXT_INPUT_ALT_CTRL_SPEECH_SHORTCUT,
            R.string.alt_ctrl_speech_shortcut_title,
            R.string.alt_ctrl_speech_shortcut_description,
            destination = SettingsDestination.TextInput
        ),
        entry(
            SettingLinkIds.TEXT_INPUT_SHIFT_BACKSPACE_DELETE,
            R.string.shift_backspace_delete_title,
            destination = SettingsDestination.TextInput
        ),
        entry(
            SettingLinkIds.TEXT_INPUT_ALT_BACKSPACE_DELETE,
            R.string.alt_backspace_delete_title,
            destination = SettingsDestination.TextInput
        ),
        entry(
            SettingLinkIds.TEXT_INPUT_BACKSPACE_AT_START_DELETE,
            R.string.backspace_at_start_delete_title,
            destination = SettingsDestination.TextInput
        ),
        entry(
            SettingLinkIds.TEXT_INPUT_DELETE_NAV_MODE,
            R.string.delete_alternatives_nav_mode_title,
            R.string.settings_nav_mode_configure,
            destination = SettingsDestination.TextInput
        ),

        entry(
            SettingLinkIds.KEYBOARDS_DEVICES_KEYBOARD_SWITCHING_AUTO,
            R.string.keyboard_switching_auto_title,
            R.string.keyboard_switching_auto_description,
            destination = SettingsDestination.KeyboardsDevices
        ),
        entry(
            SettingLinkIds.KEYBOARDS_DEVICES_KEYBOARD_SWITCHING_SHORTCUT,
            R.string.settings_nav_mode_configure,
            R.string.keyboard_switching_override_description,
            destination = SettingsDestination.KeyboardsDevices
        ),
        entry(
            SettingLinkIds.KEYBOARDS_DEVICES_TOGGLE_TOASTS,
            R.string.software_keyboard_mode_toggle_toasts_title,
            R.string.software_keyboard_mode_toggle_toasts_description,
            destination = SettingsDestination.KeyboardsDevices
        ),
        entry(
            SettingLinkIds.KEYBOARDS_DEVICES_ON_SCREEN_KEYBOARD,
            R.string.on_screen_keyboard_title,
            destination = SettingsDestination.KeyboardsDevices
        ),
        entry(
            SettingLinkIds.KEYBOARDS_DEVICES_BUILT_IN_KEYBOARDS,
            R.string.built_in_keyboards_title,
            destination = SettingsDestination.KeyboardsDevices
        ),
        entry(
            SettingLinkIds.KEYBOARDS_DEVICES_KEYBOARD_ACCESSORIES,
            R.string.keyboard_accessories_title,
            destination = SettingsDestination.KeyboardsDevices
        ),

        entry(
            SettingLinkIds.AUTO_CORRECTION_TEXT_REPLACEMENTS,
            R.string.auto_correct_title,
            R.string.auto_correct_title_description,
            destination = SettingsDestination.AutoCorrection
        ),
        entry(
            SettingLinkIds.AUTO_CORRECTION_LANGUAGES,
            R.string.auto_correct_languages_title,
            R.string.auto_correct_languages_description,
            destination = SettingsDestination.AutoCorrection,
            availability = SettingAvailability.TextReplacementsEnabled,
            unavailableFallbackId = SettingLinkIds.AUTO_CORRECTION_TEXT_REPLACEMENTS
        ),
        entry(
            SettingLinkIds.AUTO_CORRECTION_AUTO_REPLACE,
            R.string.auto_correct_auto_replace_title,
            R.string.auto_correct_auto_replace_description,
            destination = SettingsDestination.AutoCorrection
        ),
        entry(
            SettingLinkIds.AUTO_CORRECTION_MAX_DISTANCE,
            R.string.auto_correct_max_distance_title,
            R.string.auto_correct_max_distance_description,
            destination = SettingsDestination.AutoCorrection,
            availability = SettingAvailability.AutoReplaceEnabled,
            unavailableFallbackId = SettingLinkIds.AUTO_CORRECTION_AUTO_REPLACE
        ),
        entry(
            SettingLinkIds.AUTO_CORRECTION_USER_DICTIONARY,
            R.string.auto_correct_manage_user_dict_title,
            R.string.auto_correct_manage_user_dict_description,
            destination = SettingsDestination.AutoCorrection
        ),
        entry(
            SettingLinkIds.AUTO_CORRECTION_EXPERIMENTAL_SUGGESTIONS,
            R.string.experimental_suggestions_title,
            R.string.experimental_suggestions_subtitle,
            destination = SettingsDestination.AutoCorrection
        ),
        entry(
            SettingLinkIds.AUTO_CORRECTION_SUGGESTIONS,
            R.string.auto_correct_suggestions_toggle_title,
            destination = SettingsDestination.AutoCorrection
        ),
        entry(
            SettingLinkIds.AUTO_CORRECTION_ACCENT_MATCHING,
            R.string.auto_correct_accent_matching_title,
            destination = SettingsDestination.AutoCorrection
        ),
        entry(
            SettingLinkIds.AUTO_CORRECTION_KEYBOARD_PROXIMITY,
            R.string.auto_correct_keyboard_proximity_title,
            R.string.auto_correct_keyboard_proximity_description,
            destination = SettingsDestination.AutoCorrection
        ),
        entry(
            SettingLinkIds.AUTO_CORRECTION_EDIT_TYPE_RANKING,
            R.string.auto_correct_edit_type_ranking_title,
            R.string.auto_correct_edit_type_ranking_description,
            destination = SettingsDestination.AutoCorrection
        ),

        entry(
            SettingLinkIds.ACCESSIBILITY_LIVE_READ,
            R.string.settings_accessibility_live_read_title,
            R.string.settings_accessibility_live_read_description,
            destination = SettingsDestination.Accessibility
        ),
        entry(
            SettingLinkIds.ACCESSIBILITY_READ_SECOND_ROW,
            R.string.settings_accessibility_second_row_title,
            R.string.settings_accessibility_second_row_description,
            destination = SettingsDestination.Accessibility
        ),
        entry(
            SettingLinkIds.ACCESSIBILITY_SUGGESTIONS_DELAY,
            R.string.settings_accessibility_suggestions_delay_title,
            destination = SettingsDestination.Accessibility
        ),
        entry(
            SettingLinkIds.ACCESSIBILITY_BOUNCE_KEYS,
            R.string.settings_accessibility_bounce_keys_title,
            R.string.settings_accessibility_bounce_keys_description,
            destination = SettingsDestination.Accessibility
        ),
        entry(
            SettingLinkIds.ACCESSIBILITY_BOUNCE_KEYS_CHARACTER_KEYS,
            R.string.settings_accessibility_bounce_keys_character_keys_title,
            R.string.settings_accessibility_bounce_keys_character_keys_description,
            destination = SettingsDestination.Accessibility
        ),
        entry(
            SettingLinkIds.ACCESSIBILITY_BOUNCE_KEYS_MODIFIER_KEYS,
            R.string.settings_accessibility_bounce_keys_modifier_keys_title,
            R.string.settings_accessibility_bounce_keys_modifier_keys_description,
            destination = SettingsDestination.Accessibility
        ),
        entry(
            SettingLinkIds.ACCESSIBILITY_BOUNCE_KEYS_SPACE,
            R.string.settings_accessibility_bounce_keys_space_title,
            R.string.settings_accessibility_bounce_keys_space_description,
            destination = SettingsDestination.Accessibility
        ),
        entry(
            SettingLinkIds.ACCESSIBILITY_BOUNCE_KEYS_ENTER,
            R.string.settings_accessibility_bounce_keys_enter_title,
            R.string.settings_accessibility_bounce_keys_enter_description,
            destination = SettingsDestination.Accessibility
        ),
        entry(
            SettingLinkIds.ACCESSIBILITY_BOUNCE_KEYS_BACKSPACE,
            R.string.settings_accessibility_bounce_keys_backspace_title,
            R.string.settings_accessibility_bounce_keys_backspace_description,
            destination = SettingsDestination.Accessibility
        ),
        entry(
            SettingLinkIds.ACCESSIBILITY_OVERLAPPING_KEYS,
            R.string.settings_accessibility_overlapping_keys_title,
            R.string.settings_accessibility_overlapping_keys_description,
            destination = SettingsDestination.Accessibility
        ),

        entry(
            SettingLinkIds.ADVANCED_TRACKPAD_GESTURES,
            R.string.trackpad_gestures_title,
            R.string.trackpad_gestures_description,
            destination = SettingsDestination.Advanced
        ),
        entry(
            SettingLinkIds.ADVANCED_BACKUP,
            R.string.backup_now,
            R.string.backup_now_description,
            destination = SettingsDestination.Advanced
        ),
        entry(
            SettingLinkIds.ADVANCED_RESTORE,
            R.string.restore_from_file,
            R.string.restore_from_file_description,
            destination = SettingsDestination.Advanced
        ),
        entry(
            SettingLinkIds.ADVANCED_SWIPE_INCREMENTAL_THRESHOLD,
            R.string.swipe_incremental_threshold_title,
            destination = SettingsDestination.Advanced
        ),
        entry(
            SettingLinkIds.ADVANCED_CLIPBOARD_RETENTION_TIME,
            R.string.clipboard_retention_time_title,
            R.string.clipboard_retention_time_description,
            destination = SettingsDestination.Advanced
        ),
        entry(
            SettingLinkIds.ADVANCED_EXPERIMENTAL_CANDIDATES_VIEW,
            R.string.experimental_candidates_view_title,
            R.string.experimental_candidates_view_description,
            destination = SettingsDestination.Advanced
        ),
        entry(
            SettingLinkIds.ADVANCED_SHOW_TUTORIAL,
            R.string.tutorial_show,
            R.string.tutorial_review_description,
            destination = SettingsDestination.Advanced
        ),
        entry(
            SettingLinkIds.ADVANCED_SHOW_RELEASE_NOTES_TUTORIAL,
            R.string.tutorial_show_release_notes,
            R.string.tutorial_show_release_notes_description,
            destination = SettingsDestination.Advanced
        ),

        entry(
            SettingLinkIds.TRACKPAD_GESTURES_ENABLED,
            R.string.trackpad_gestures_enabled_title,
            R.string.trackpad_gestures_enabled_description,
            destination = SettingsDestination.Advanced
        ),
        entry(
            SettingLinkIds.TRACKPAD_PROVIDER,
            R.string.trackpad_provider_title,
            destination = SettingsDestination.Advanced
        ),
        entry(
            SettingLinkIds.TRACKPAD_SHIZUKU_DEVICE,
            R.string.trackpad_shizuku_device_title,
            destination = SettingsDestination.Advanced,
            availability = SettingAvailability.TrackpadShizukuProvider,
            unavailableFallbackId = SettingLinkIds.TRACKPAD_PROVIDER
        ),
        entry(
            SettingLinkIds.TRACKPAD_SENSITIVITY,
            R.string.trackpad_sensitivity_title,
            R.string.trackpad_sensitivity_description,
            destination = SettingsDestination.Advanced
        ),
        entry(
            SettingLinkIds.TRACKPAD_SUGGESTION_SWIPE_THRESHOLD,
            R.string.trackpad_suggestion_swipe_threshold_title,
            R.string.trackpad_suggestion_swipe_threshold_description,
            destination = SettingsDestination.Advanced
        ),
        entry(
            SettingLinkIds.TRACKPAD_DELETE_SWIPE_THRESHOLD,
            R.string.trackpad_delete_swipe_threshold_title,
            R.string.trackpad_delete_swipe_threshold_description,
            destination = SettingsDestination.Advanced
        ),
        entry(
            SettingLinkIds.TRACKPAD_DEBUG,
            R.string.trackpad_debug_title,
            R.string.trackpad_debug_description,
            destination = SettingsDestination.Advanced
        ),

        entry(
            SettingLinkIds.MODIFIERS_LONG_PRESS_MODIFIER,
            R.string.long_press_modifier_title,
            destination = SettingsDestination.Modifiers
        ),
        entry(
            SettingLinkIds.MODIFIERS_LONG_PRESS_THRESHOLD,
            R.string.long_press_title,
            destination = SettingsDestination.Modifiers
        ),
        entry(
            SettingLinkIds.MODIFIERS_INDICATORS,
            R.string.modifier_indicators_title,
            R.string.modifier_indicators_description,
            destination = SettingsDestination.Modifiers
        ),
        entry(
            SettingLinkIds.MODIFIERS_SYM_LAYERS,
            R.string.sym_customization_title,
            R.string.sym_customization_description,
            destination = SettingsDestination.Modifiers
        ),
        entry(
            SettingLinkIds.MODIFIERS_SYM_SHORTCUTS,
            R.string.power_shortcuts_title,
            R.string.power_shortcuts_description,
            destination = SettingsDestination.Modifiers
        ),
        entry(
            SettingLinkIds.MODIFIERS_ALT_BINDING,
            R.string.alt_binding_title,
            R.string.alt_binding_description,
            destination = SettingsDestination.Modifiers
        ),
        entry(
            SettingLinkIds.MODIFIERS_ALT_KEY_SHORTCUTS,
            R.string.alt_key_shortcuts_title,
            R.string.alt_key_shortcuts_modifier_link_description,
            destination = SettingsDestination.Modifiers
        ),
        entry(
            SettingLinkIds.MODIFIERS_CONTROL_NAV_MODE,
            R.string.modifier_control_nav_mode_title,
            R.string.modifier_control_nav_mode_description,
            destination = SettingsDestination.Modifiers
        ),
        entry(
            SettingLinkIds.MODIFIERS_SHIFT_TAP_LATCHES,
            R.string.shift_tap_latches_title,
            R.string.shift_tap_latches_description,
            destination = SettingsDestination.Modifiers
        ),
        entry(
            SettingLinkIds.MODIFIERS_ALT_TAP_LATCHES,
            R.string.alt_tap_latches_title,
            R.string.alt_tap_latches_description,
            destination = SettingsDestination.Modifiers
        ),
        entry(
            SettingLinkIds.MODIFIERS_ALT_LATCH_STAYS_ON_SPACE,
            R.string.alt_latch_stays_on_space_title,
            R.string.alt_latch_stays_on_space_description,
            destination = SettingsDestination.Modifiers
        ),
        entry(
            SettingLinkIds.MODIFIERS_CTRL_TAP_LATCHES,
            R.string.ctrl_tap_latches_title,
            R.string.ctrl_tap_latches_description,
            destination = SettingsDestination.Modifiers
        ),
        entry(
            SettingLinkIds.MODIFIERS_CTRL_LATCH_STAYS_ON_SPACE,
            R.string.ctrl_latch_stays_on_space_title,
            R.string.ctrl_latch_stays_on_space_description,
            destination = SettingsDestination.Modifiers,
            availability = SettingAvailability.CtrlTapLatchesEnabled,
            unavailableFallbackId = SettingLinkIds.MODIFIERS_CTRL_TAP_LATCHES
        ),

        entry(
            SettingLinkIds.CUSTOM_INPUT_STYLES_LAYOUT_MODE,
            R.string.keyboard_layout_mode_title,
            destination = SettingsDestination.CustomInputStyles
        ),
        entry(
            SettingLinkIds.CUSTOM_INPUT_STYLES_ALT_SHIFT_LAYOUT_SWITCH,
            R.string.alt_shift_layout_switch_title,
            R.string.alt_shift_layout_switch_description,
            destination = SettingsDestination.CustomInputStyles
        ),
        entry(
            SettingLinkIds.CUSTOM_INPUT_STYLES_ALT_ENTER_LAYOUT_SWITCH,
            R.string.alt_enter_layout_switch_title,
            R.string.alt_enter_layout_switch_description,
            destination = SettingsDestination.CustomInputStyles
        ),
        entry(
            SettingLinkIds.CUSTOM_INPUT_STYLES_CTRL_SPACE_LAYOUT_SWITCH,
            R.string.ctrl_space_layout_switch_title,
            R.string.ctrl_space_layout_switch_description,
            destination = SettingsDestination.CustomInputStyles
        ),
        entry(
            SettingLinkIds.CUSTOM_INPUT_STYLES_LAYOUT_SWITCH_TOAST,
            R.string.toast_on_layout_switch_title,
            R.string.toast_on_layout_switch_description,
            destination = SettingsDestination.CustomInputStyles
        ),

        entry(
            SettingLinkIds.ABOUT_SUPPORT_KO_FI,
            R.string.settings_support_ko_fi,
            destination = SettingsDestination.About
        ),

        // Keyboard theme editor (customization sub-screen)
        entry(
            SettingLinkIds.KEYBOARD_THEME_TOGGLE_SHOW_LEDS,
            R.string.keyboard_theme_show_leds,
            destination = SettingsDestination.Customization,
            customizationDestination = SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME,
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Keys
        ),
        entry(
            SettingLinkIds.KEYBOARD_THEME_TOGGLE_DISTRIBUTE_SPACING,
            R.string.keyboard_theme_distribute_spacing,
            destination = SettingsDestination.Customization,
            customizationDestination = SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME,
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Keys
        ),
        entry(
            SettingLinkIds.KEYBOARD_THEME_TOGGLE_ORTHOLINEAR,
            R.string.keyboard_theme_ortholinear,
            destination = SettingsDestination.Customization,
            customizationDestination = SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME,
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Keys
        ),
        entry(
            SettingLinkIds.KEYBOARD_THEME_TOGGLE_ATTACH_POPUP,
            R.string.keyboard_theme_attach_popup,
            destination = SettingsDestination.Customization,
            customizationDestination = SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME,
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Keys
        ),
        entry(
            SettingLinkIds.KEYBOARD_THEME_TOGGLE_POPUP_TAIL,
            R.string.keyboard_theme_popup_tail,
            destination = SettingsDestination.Customization,
            customizationDestination = SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME,
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Keys
        ),
        entry(
            SettingLinkIds.KEYBOARD_THEME_TOGGLE_PREVIEW_ON_HOLD,
            R.string.keyboard_theme_preview_on_hold,
            destination = SettingsDestination.Customization,
            customizationDestination = SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME,
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Keys
        ),
        entry(
            SettingLinkIds.KEYBOARD_THEME_TOGGLE_CHARACTER_PICKER,
            R.string.keyboard_theme_character_picker,
            destination = SettingsDestination.Customization,
            customizationDestination = SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME,
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Keys
        ),
        entry(
            SettingLinkIds.KEYBOARD_THEME_LED_COLORS,
            R.string.keyboard_theme_wizard_leds,
            R.string.keyboard_theme_wizard_leds_description,
            destination = SettingsDestination.Customization,
            customizationDestination = SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME,
            keyboardThemeTab = KeyboardThemeEditorTab.Colors
        ),

        // Modifier indicator chips (rendered on the modifiers screen)
        entry(
            SettingLinkIds.MODIFIERS_INDICATOR_BOTTOM_STRIP,
            R.string.modifier_indicators_bottom_strip,
            R.string.modifier_indicators_bottom_strip_description,
            destination = SettingsDestination.Modifiers
        ),
        entry(
            SettingLinkIds.MODIFIERS_INDICATOR_MENU_BAR,
            R.string.modifier_indicators_menu_bar,
            R.string.modifier_indicators_menu_bar_description,
            destination = SettingsDestination.Modifiers
        ),
        entry(
            SettingLinkIds.MODIFIERS_INDICATOR_STATUS_BAR,
            R.string.modifier_indicators_status_bar,
            R.string.modifier_indicators_status_bar_description,
            destination = SettingsDestination.Modifiers
        )
    ) + customizationSettingEntries() + inputDeviceSettingEntries() + systemSettingEntries()

    private val entriesById: Map<String, SettingEntry> =
        entries.associateBy { it.id }

    /**
     * Invisible per-entry keyword strings that extend search beyond the
     * (partly visible) titles and summaries, e.g. synonyms such as "LED" for
     * modifier indicators or "layout" for input languages. These strings are
     * never rendered; they exist purely for [search]. Internal so tests can
     * assert that every assignment targets a registered entry.
     */
    internal val keywordsById: Map<String, Int> = mapOf(
        SettingLinkIds.MAIN_KEYBOARDS_DEVICES to R.string.kw_main_keyboards_devices,
        SettingLinkIds.MAIN_MODIFIERS to R.string.kw_main_modifiers,
        SettingLinkIds.MAIN_CUSTOM_INPUT_STYLES to R.string.kw_main_custom_input_styles,
        SettingLinkIds.MAIN_TEXT_INPUT to R.string.kw_main_text_input,
        SettingLinkIds.MAIN_AUTO_CORRECTION to R.string.kw_main_auto_correction,
        SettingLinkIds.MAIN_KEYBOARD_THEME to R.string.kw_main_keyboard_theme,
        SettingLinkIds.MAIN_APP_LANGUAGE to R.string.kw_main_app_language,
        SettingLinkIds.MAIN_CUSTOMIZATION to R.string.kw_main_customization,
        SettingLinkIds.MAIN_ADVANCED to R.string.kw_main_advanced,
        SettingLinkIds.MAIN_ACCESSIBILITY to R.string.kw_main_accessibility,
        SettingLinkIds.MAIN_ABOUT to R.string.kw_main_about,
        SettingLinkIds.TEXT_INPUT_AUTO_SPACE_PUNCTUATION to R.string.kw_text_input_auto_space_punctuation,
        SettingLinkIds.TEXT_INPUT_SPACED_HYPHEN_TO_EN_DASH to R.string.kw_text_input_spaced_hyphen,
        SettingLinkIds.TEXT_INPUT_SMART_QUOTES to R.string.kw_text_input_smart_quotes,
        SettingLinkIds.TEXT_INPUT_SHIFT_BACKSPACE_DELETE to R.string.kw_text_input_shift_backspace,
        SettingLinkIds.TEXT_INPUT_ALT_BACKSPACE_DELETE to R.string.kw_text_input_alt_backspace,
        SettingLinkIds.TEXT_INPUT_BACKSPACE_AT_START_DELETE to R.string.kw_text_input_backspace_start,
        SettingLinkIds.KEYBOARDS_DEVICES_ON_SCREEN_KEYBOARD to R.string.kw_keyboards_devices_on_screen,
        SettingLinkIds.KEYBOARDS_DEVICES_BUILT_IN_KEYBOARDS to R.string.kw_keyboards_devices_built_in,
        SettingLinkIds.KEYBOARDS_DEVICES_KEYBOARD_ACCESSORIES to R.string.kw_keyboards_devices_accessories,
        SettingLinkIds.AUTO_CORRECTION_LANGUAGES to R.string.kw_auto_correction_languages,
        SettingLinkIds.AUTO_CORRECTION_SUGGESTIONS to R.string.kw_auto_correction_suggestions,
        SettingLinkIds.AUTO_CORRECTION_ACCENT_MATCHING to R.string.kw_auto_correction_accent_matching,
        SettingLinkIds.ACCESSIBILITY_SUGGESTIONS_DELAY to R.string.kw_accessibility_suggestions_delay,
        SettingLinkIds.ADVANCED_SWIPE_INCREMENTAL_THRESHOLD to R.string.kw_advanced_swipe_threshold,
        SettingLinkIds.MODIFIERS_LONG_PRESS_MODIFIER to R.string.kw_modifiers_long_press_modifier,
        SettingLinkIds.MODIFIERS_LONG_PRESS_THRESHOLD to R.string.kw_modifiers_long_press_threshold,
        SettingLinkIds.MODIFIERS_INDICATORS to R.string.kw_modifier_indicators,
        SettingLinkIds.CUSTOM_INPUT_STYLES_LAYOUT_MODE to R.string.kw_custom_input_styles_layout_mode,
        SettingLinkIds.ABOUT_SUPPORT_KO_FI to R.string.kw_about_support_ko_fi,
        SettingLinkIds.KEYBOARD_THEME_TOGGLE_SHOW_LEDS to R.string.kw_theme_toggle_show_leds,
        SettingLinkIds.KEYBOARD_THEME_TOGGLE_DISTRIBUTE_SPACING to R.string.kw_theme_toggle_distribute_spacing,
        SettingLinkIds.KEYBOARD_THEME_TOGGLE_ORTHOLINEAR to R.string.kw_theme_toggle_ortholinear,
        SettingLinkIds.KEYBOARD_THEME_TOGGLE_ATTACH_POPUP to R.string.kw_theme_toggle_attach_popup,
        SettingLinkIds.KEYBOARD_THEME_TOGGLE_POPUP_TAIL to R.string.kw_theme_toggle_popup_tail,
        SettingLinkIds.KEYBOARD_THEME_TOGGLE_PREVIEW_ON_HOLD to R.string.kw_theme_toggle_preview_on_hold,
        SettingLinkIds.KEYBOARD_THEME_TOGGLE_CHARACTER_PICKER to R.string.kw_theme_toggle_character_picker,
        SettingLinkIds.KEYBOARD_THEME_LED_COLORS to R.string.kw_theme_led_colors,
        SettingLinkIds.MODIFIERS_INDICATOR_BOTTOM_STRIP to R.string.kw_indicator_bottom_strip,
        SettingLinkIds.MODIFIERS_INDICATOR_MENU_BAR to R.string.kw_indicator_menu_bar,
        SettingLinkIds.MODIFIERS_INDICATOR_STATUS_BAR to R.string.kw_indicator_status_bar
    )

    fun byId(id: String): SettingEntry? = entriesById[id]

    /**
     * Returns an addressable row that is actually present for the current settings.
     * Conditional child rows fall back to the visible parent that enables them.
     */
    fun visibleTarget(context: Context, entry: SettingEntry): SettingEntry {
        var candidate = entry
        val visited = mutableSetOf<String>()
        while (!candidate.isAvailable(context) && visited.add(candidate.id)) {
            candidate = candidate.unavailableFallbackId?.let(entriesById::get) ?: return candidate
        }
        return candidate
    }

    fun buildLink(id: String): String = "https://$WEB_LINK_HOST$WEB_LINK_PATH_PREFIX$id"

    /**
     * Markdown link used for sharing. With [withDescription] the localized
     * setting title is the label, otherwise the raw link stands in for itself.
     */
    fun buildSettingMarkdown(label: String, link: String, withDescription: Boolean): String {
        val text = if (withDescription) label.escapeMarkdownLinkText() else link
        return "[$text]($link)"
    }

    private fun String.escapeMarkdownLinkText(): String =
        replace("[", "\\[").replace("]", "\\]")

    fun parseSettingLinkUri(uri: Uri): String? =
        parseSettingLink(uri.scheme, uri.host, uri.path)

    fun parseSettingLink(scheme: String?, host: String?, path: String?): String? {
        val id = when {
            scheme == LINK_SCHEME && host == LINK_HOST ->
                path?.trim()?.removePrefix("/")
            scheme == "https" && (host == WEB_LINK_HOST || host == ALTERNATE_WEB_LINK_HOST) ->
                path?.takeIf { it.startsWith(WEB_LINK_PATH_PREFIX) }
                    ?.removePrefix(WEB_LINK_PATH_PREFIX)
            else -> null
        }
        return id?.takeIf(settingIdPattern::matches)
    }

    /**
     * Screen titles used for breadcrumbs in search results and the link sheet.
     */
    val destinationTitles: Map<SettingsDestination, Int> = mapOf(
        SettingsDestination.Main to R.string.settings_title,
        SettingsDestination.KeyboardsDevices to R.string.keyboards_devices_title,
        SettingsDestination.TextInput to R.string.settings_category_text_input,
        SettingsDestination.Accessibility to R.string.settings_category_accessibility,
        SettingsDestination.AutoCorrection to R.string.settings_category_auto_correction,
        SettingsDestination.Customization to R.string.settings_category_customization,
        SettingsDestination.NavMode to R.string.nav_mode_title,
        SettingsDestination.Advanced to R.string.settings_category_advanced,
        SettingsDestination.About to R.string.about_title,
        SettingsDestination.CustomInputStyles to R.string.custom_input_styles_title,
        SettingsDestination.AppLanguage to R.string.app_language_title,
        SettingsDestination.Modifiers to R.string.modifiers_title
    )

    val keyboardsDevicesSubtitles: Map<KeyboardsDevicesDestination, Int> = mapOf(
        KeyboardsDevicesDestination.OnScreen to R.string.on_screen_keyboard_title,
        KeyboardsDevicesDestination.BuiltIn to R.string.built_in_keyboards_title,
        KeyboardsDevicesDestination.PowerKeyboard to R.string.keyboard_accessories_title
    )

    val customizationSubtitles: Map<String, Int> = mapOf(
        SettingsActivity.CUSTOMIZATION_DESTINATION_STATUS_BAR_BUTTONS to
            R.string.status_bar_buttons_title,
        SettingsActivity.CUSTOMIZATION_DESTINATION_LAUNCHER_SHORTCUTS to
            R.string.starter_launcher_shortcuts_title,
        SettingsActivity.CUSTOMIZATION_DESTINATION_APP_ENTER_BEHAVIOR to
            R.string.app_enter_behaviour_title,
        SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME to
            R.string.keyboard_theme_title
    ) + customizationSettingSubtitles()

    /**
     * Search across all registered entries. The query is split into tokens;
     * an entry matches only when every token matches, and ranks by the summed
     * per-token scores (see [scoreMatch]). Matching is case-insensitive and
     * diacritics-insensitive on the localized title, summary, and invisible
     * keywords.
     */
    fun search(context: Context, query: String): List<SettingEntry> {
        val tokens = tokenizeForSearch(query)
        if (tokens.isEmpty()) return emptyList()
        data class Scored(val entry: SettingEntry, val score: Int, val sortKey: String)

        return entries.asSequence()
            .filter { entry -> entry.isAvailable(context) }
            .mapNotNull { entry ->
            val title = normalizeForSearch(context.getString(entry.titleRes))
            val summary = entry.summaryRes?.let { normalizeForSearch(context.getString(it)) }
            val keywords = normalizeForSearch(listOfNotNull(
                keywordsById[entry.id]?.let(context::getString),
                destinationTitles[entry.route.destination]?.let(context::getString),
                entry.route.customizationDestination?.let(customizationSubtitles::get)?.let(context::getString),
                keyboardsDevicesSubtitles[entry.route.keyboardsDevicesDestination]?.let(context::getString),
                entry.id.replace('.', ' ').replace('_', ' ')
            ).joinToString(" "))
            var score = 0
            for (token in tokens) {
                score += tokenScore(title, summary, keywords, token) ?: return@mapNotNull null
            }
            Scored(entry, score, title)
            }
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.sortKey })
            .map { it.entry }
            .toList()
    }

    /**
     * Token-based relevance: title prefix (6) › title word prefix (4) › title
     * substring (2) › summary/keyword word prefix (1) › summary/keyword
     * substring (0); tokens are summed. Returns null when any token matches
     * nowhere. Diacritics are folded, so "grosse" matches "große".
     */
    fun scoreMatch(
        title: String,
        summary: String?,
        normalizedQuery: String,
        keywords: String? = null
    ): Int? {
        val tokens = tokenizeForSearch(normalizedQuery)
        if (tokens.isEmpty()) return null
        val normalizedTitle = normalizeForSearch(title)
        val normalizedSummary = summary?.let { normalizeForSearch(it) }
        val normalizedKeywords = keywords?.let { normalizeForSearch(it) }
        var total = 0
        for (token in tokens) {
            total += tokenScore(normalizedTitle, normalizedSummary, normalizedKeywords, token)
                ?: return null
        }
        return total
    }

    private fun tokenScore(title: String, summary: String?, keywords: String?, token: String): Int? =
        when {
            title.startsWith(token) -> 6
            title.split(SEARCH_TOKEN_SEPARATOR).any { it.startsWith(token) } -> 4
            title.contains(token) -> 2
            listOfNotNull(summary, keywords)
                .any { text -> text.split(SEARCH_TOKEN_SEPARATOR).any { it.startsWith(token) } } -> 1
            listOfNotNull(summary, keywords).any { it.contains(token) } -> 0
            else -> null
        }

    private fun tokenizeForSearch(query: String): List<String> =
        normalizeForSearch(query).split(SEARCH_TOKEN_SEPARATOR).filter { it.isNotEmpty() }

    private fun normalizeForSearch(text: String): String =
        Normalizer.normalize(text.lowercase().replace("ß", "ss"), Normalizer.Form.NFD)
            .replace(MARK_REGEX, "")

    private val SEARCH_TOKEN_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
    private val MARK_REGEX = Regex("\\p{M}+")
}
