package it.palsoftware.pastiera

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.view.KeyEvent
import it.palsoftware.pastiera.commands.CommandJson
import it.palsoftware.pastiera.commands.CommandLaunchSpec
import it.palsoftware.pastiera.commands.CommandSourceId
import it.palsoftware.pastiera.commands.CommandSurface
import it.palsoftware.pastiera.commands.PastieraCommandSource
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

/**
 * Manages the app settings.
 * Centralizes access to SharedPreferences for Pastiera settings.
 */
object SettingsManager {
    private const val TAG = "SettingsManager"
    private const val PREFS_NAME = "pastiera_prefs"
    
    // Settings keys
    private const val KEY_LONG_PRESS_THRESHOLD = "long_press_threshold"
    const val KEY_TYPING_SOUND_MODE = "typing_sound_mode"
    const val KEY_TYPING_SOUND_OUTPUT_MODE = "typing_sound_output_mode"
    const val KEY_TYPING_SOUND_CUSTOM_FILE_NAME = "typing_sound_custom_file_name"
    const val KEY_TYPING_SOUND_CUSTOM_DISPLAY_NAME = "typing_sound_custom_display_name"
    const val KEY_TYPING_SOUND_UPDATED_AT = "typing_sound_updated_at"
    private const val KEY_AUTO_CAPITALIZE_FIRST_LETTER = "auto_capitalize_first_letter"
    private const val KEY_DOUBLE_SPACE_TO_PERIOD = "double_space_to_period"
    private const val KEY_SPACED_HYPHEN_TO_EN_DASH = "spaced_hyphen_to_en_dash"
    private const val KEY_SPACED_HYPHEN_DASH_STYLE = "spaced_hyphen_dash_style"
    private const val KEY_SMART_QUOTES = "smart_quotes"
    private const val KEY_SMART_QUOTES_STYLE = "smart_quotes_style"
    private const val KEY_SWIPE_TO_DELETE = "swipe_to_delete"
    private const val KEY_SWIPE_TO_DELETE_PROVIDER = "swipe_to_delete_provider"
    private const val KEY_AUTO_SHOW_KEYBOARD = "auto_show_keyboard"
    private const val KEY_CLEAR_ALT_ON_SPACE = "clear_alt_on_space"
    private const val KEY_ALT_CTRL_SPEECH_SHORTCUT = "alt_ctrl_speech_shortcut"
    private const val KEY_LAYOUT_AWARE_CTRL_SHORTCUTS = "layout_aware_ctrl_shortcuts"
    private const val KEY_SYM_MAPPINGS_CUSTOM = "sym_mappings_custom"
    private const val KEY_SYM_MAPPINGS_PAGE2_CUSTOM = "sym_mappings_page2_custom"
    private const val KEY_AUTO_CORRECT_ENABLED = "auto_correct_enabled"
    private const val KEY_AUTO_CORRECT_ENABLED_LANGUAGES = "auto_correct_enabled_languages"
    private const val KEY_SUGGESTIONS_ENABLED = "suggestions_enabled"
    private const val KEY_ACCENT_MATCHING_ENABLED = "accent_matching_enabled"
    private const val KEY_AUTO_REPLACE_ON_SPACE_ENTER = "auto_replace_on_space_enter"
    private const val KEY_MAX_AUTO_REPLACE_DISTANCE = "max_auto_replace_distance"
    private const val KEY_AUTO_CAPITALIZE_AFTER_PERIOD = "auto_capitalize_after_period"
    private const val KEY_LONG_PRESS_MODIFIER = "long_press_modifier" // "alt", "shift", "variations", or "sym"
    private const val KEY_KEYBOARD_LAYOUT = "keyboard_layout" // "qwerty", "azerty", etc.
    private const val KEY_KEYBOARD_LAYOUT_AUTO_BY_LOCALE = "keyboard_layout_auto_by_locale" // If true, resolve layout from subtype/locale mapping
    const val KEY_KEYBOARD_LAYOUT_AUTO_MAPPING_UPDATED = "keyboard_layout_auto_mapping_updated"
    private const val KEY_KEYBOARD_LAYOUT_LIST = "keyboard_layout_list" // JSON array of layout ids for cycling
    private const val KEY_ALT_SHIFT_LAYOUT_SWITCH = "alt_shift_layout_switch" // Enable Alt+Shift shortcut for layout cycling
    private const val KEY_CTRL_SPACE_LAYOUT_SWITCH = "ctrl_space_layout_switch" // Enable Ctrl+Space shortcut for layout cycling
    private const val KEY_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE = "physical_keyboard_profile_override" // auto | key2 | Q25 | titan | titan2 | titan2elite_qwerty | mp01
    private const val KEY_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL = "physical_keyboard_currency_symbol" // Currency symbol for dedicated hardware keys
    private const val KEY_RESTORE_SYM_PAGE = "restore_sym_page" // SYM page to restore when returning from settings
    private const val KEY_PENDING_RESTORE_SYM_PAGE = "pending_restore_sym_page" // Temporary SYM page state saved when opening settings
    private const val KEY_SYM_PAGES_CONFIG = "sym_pages_config" // Order/enabled pages for SYM
    private const val KEY_SYM_AUTO_CLOSE = "sym_auto_close" // Auto-close SYM layout after key press
    private const val KEY_SYM_AUTO_CLOSE_ON_TOUCH = "sym_auto_close_on_touch" // Auto-close SYM layout after tapping on-screen SYM keys
    private const val KEY_SHIFT_TAP_LATCHES = "shift_tap_latches"
    private const val KEY_ALT_TAP_LATCHES = "alt_tap_latches"
    private const val KEY_CTRL_TAP_LATCHES = "ctrl_tap_latches"
    private const val KEY_ALT_LATCH_STAYS_ON_SPACE = "alt_latch_stays_on_space"
    private const val KEY_CTRL_LATCH_STAYS_ON_SPACE = "ctrl_latch_stays_on_space"
    private const val KEY_EMOJI_PICKER_EXPANDED_HEIGHT = "emoji_picker_expanded_height"
    private const val KEY_DISMISSED_RELEASES = "dismissed_releases" // Set of release tag_names that were dismissed
    private const val KEY_TUTORIAL_COMPLETED = "tutorial_completed" // Whether the first-run tutorial has been completed
    private const val KEY_LAST_SEEN_WHATS_NEW_VERSION = "last_seen_whats_new_version"
    private const val KEY_SWIPE_INCREMENTAL_THRESHOLD = "swipe_incremental_threshold" // Distance in DIP for cursor movement
    private const val KEY_STATIC_VARIATION_BAR_MODE = "static_variation_bar_mode" // Use static variation bar instead of dynamic cursor-based variations
    private const val KEY_STATIC_VARIATION_BAR_PRESET = "static_variation_bar_preset"
    private const val KEY_STATIC_VARIATION_BAR_BASE_LAYER_ENABLED = "static_variation_bar_base_layer_enabled" // Toggle top-row preset
    private const val KEY_STATIC_VARIATION_BAR_MODIFIER_HOLD_RESTORATION = "static_variation_bar_modifier_hold_restoration"
    private const val KEY_VARIATIONS_UPDATED = "variations_updated" // Trigger for reloading variations in input method service
    private const val KEY_ADDITIONAL_IME_SUBTYPES = "additional_ime_subtypes" // Comma-separated list of language codes for additional IME subtypes
    private const val KEY_DEFAULT_SUBTYPE_STYLE = "default_subtype_style" // Input style "locale:layout" (e.g. "en_US:qwerty") to force on every new input field; empty = keep last used
    private const val KEY_CLIPBOARD_HISTORY_ENABLED = "clipboard_history_enabled" // Whether clipboard history is enabled
    private const val KEY_CLIPBOARD_RETENTION_TIME = "clipboard_retention_time" // How long to keep clipboard entries (in minutes)
    private const val KEY_TRACKPAD_GESTURES_ENABLED = "trackpad_gestures_enabled" // Whether trackpad gesture suggestions are enabled
    private const val KEY_TRACKPAD_GESTURE_ADD_WORD_ENABLED = "trackpad_gesture_add_word_enabled" // Whether suggestion gestures can trigger add-word
    private const val KEY_TRACKPAD_SWIPE_THRESHOLD = "trackpad_swipe_threshold" // Threshold for swipe detection on trackpad
    private const val KEY_TRACKPAD_PROVIDER = "trackpad_provider" // shizuku | native_ime
    private const val KEY_SHIFT_BACKSPACE_DELETE = "shift_backspace_delete" // Shift + Backspace performs forward delete
    private const val KEY_ALT_BACKSPACE_DELETE = "alt_backspace_delete" // Alt + Backspace performs forward delete
    private const val KEY_BACKSPACE_AT_START_DELETE = "backspace_at_start_delete" // Backspace at line start performs forward delete
    private const val KEY_PASTIERINA_MODE_OVERRIDE = "pastierina_mode_override" // follow_system | force_minimal | force_full
    private const val KEY_PASTIERINA_MODE_ACTIVE = "pastierina_mode_active" // Current effective state
    private const val KEY_SOFTWARE_KEYBOARD_MODE = "software_keyboard_mode" // auto | force_hardware | force_virtual
    private const val KEY_TITAN2_LAYOUT_ENABLED = "titan2_layout_enabled" // Align OSK with Titan 2 physical layout
    private const val KEY_ACCESSIBILITY_LIVE_ANNOUNCEMENTS_ENABLED = "accessibility_live_announcements_enabled" // Whether status bar accessibility live announcements are enabled
    private const val KEY_ACCESSIBILITY_READ_SECOND_ROW_ENABLED = "accessibility_read_second_row_enabled" // Whether TalkBack should read quick settings/variations row
    private const val KEY_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS = "accessibility_suggestions_announcement_delay_ms" // Delay before suggestions become accessible again while typing
    private const val KEY_BOUNCE_KEYS_ENABLED = "bounce_keys_enabled" // Whether repeated same-key taps inside the delay are ignored
    private const val KEY_BOUNCE_KEYS_DELAY_MS = "bounce_keys_delay_ms" // Minimum delay before the same key can be accepted again
    private const val KEY_BOUNCE_KEYS_CHARACTER_KEYS_ENABLED = "bounce_keys_character_keys_enabled"
    private const val KEY_BOUNCE_KEYS_MODIFIER_KEYS_ENABLED = "bounce_keys_modifier_keys_enabled"
    private const val KEY_BOUNCE_KEYS_SPACE_ENABLED = "bounce_keys_space_enabled"
    private const val KEY_BOUNCE_KEYS_ENTER_ENABLED = "bounce_keys_enter_enabled"
    private const val KEY_BOUNCE_KEYS_BACKSPACE_ENABLED = "bounce_keys_backspace_enabled"
    private const val KEY_GLOBAL_VARIATION_LAYOUT_OVERRIDE = "global_variation_layout_override" // Optional layout id used for variation ordering across all layouts
    private const val KEY_APP_LANGUAGE_TAG = "app_language_tag" // BCP-47 language tag for app UI (null/blank = system)
    private const val KEY_APP_ENTER_BEHAVIOR_ENABLED = "app_enter_behavior_enabled"
    private const val KEY_APP_ENTER_BEHAVIOR_PRESET = "app_enter_behavior_preset"
    private const val KEY_APP_ENTER_BEHAVIOR_OVERRIDES = "app_enter_behavior_overrides"
    
    // Status bar button slot configuration keys
    private const val KEY_STATUS_BAR_SLOT_LEFT = "status_bar_slot_left"
    private const val KEY_STATUS_BAR_SLOT_RIGHT_1 = "status_bar_slot_right_1"
    private const val KEY_STATUS_BAR_SLOT_RIGHT_2 = "status_bar_slot_right_2"
    private const val KEY_STATUS_BAR_SLOTS_LEFT = "status_bar_slots_left"
    private const val KEY_STATUS_BAR_SLOTS_RIGHT = "status_bar_slots_right"
    private const val KEY_STATUS_BAR_VARIATIONS_VISIBLE = "status_bar_variations_visible"
    private const val KEY_DYNAMIC_VARIATION_BAR_SLOT_COUNT = "dynamic_variation_bar_slot_count"
    private const val KEY_DYNAMIC_VARIATION_BAR_RESIZE_TO_CONTENT = "dynamic_variation_bar_resize_to_content"
    
    // Public constants for button IDs
    const val STATUS_BAR_BUTTON_NONE = "none"
    const val STATUS_BAR_BUTTON_CLIPBOARD = "clipboard"
    const val STATUS_BAR_BUTTON_MICROPHONE = "microphone"
    const val STATUS_BAR_BUTTON_EMOJI = "emoji"
    const val STATUS_BAR_BUTTON_LANGUAGE = "language"
    const val STATUS_BAR_BUTTON_HAMBURGER = "hamburger"
    const val STATUS_BAR_BUTTON_SETTINGS = "settings"
    const val STATUS_BAR_BUTTON_SYMBOLS = "symbols"
    const val STATUS_BAR_BUTTON_UNDO = "undo"
    const val STATUS_BAR_BUTTON_REDO = "redo"

    const val STATIC_VARIATION_PRESET_OFF = "off"
    const val STATIC_VARIATION_PRESET_SYMBOLS = "symbols"
    const val STATIC_VARIATION_PRESET_NUMBERS = "numbers"
    const val STATIC_VARIATION_PRESET_ALTERNATIVE = "alternative"
    const val STATIC_VARIATION_PRESET_DEV_CHOICE = "dev_choice"

    const val ENTER_BEHAVIOR_PRESET_APP_DEFAULT = "app_default"
    const val ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE = "enter_send_shift_newline"
    const val ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_CTRL_SEND = "enter_newline_ctrl_send"
    const val ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_ONLY = "enter_newline_only"
    const val ENTER_BEHAVIOR_PRESET_CUSTOM = "custom"

    const val ENTER_BEHAVIOR_APP_DEFAULT = "app_default"
    const val ENTER_BEHAVIOR_ENTER_NEWLINE = "enter_newline"
    const val ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE = "enter_send_shift_newline"
    const val ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND = "enter_newline_ctrl_send"

    const val ENTER_SEND_STRATEGY_AUTO = "auto"
    const val ENTER_SEND_STRATEGY_EDITOR_ACTION = "editor_action"
    const val ENTER_SEND_STRATEGY_CTRL_ENTER = "ctrl_enter"
    const val ENTER_SEND_STRATEGY_PLAIN_ENTER = "plain_enter"
    const val ENTER_ADDITIONAL_SEND_SHORTCUT_NONE = "none"
    const val ENTER_ADDITIONAL_SEND_SHORTCUT_SYM_ENTER = "sym_enter"
    
    // Default slot assignments
    private const val DEFAULT_SLOT_LEFT = STATUS_BAR_BUTTON_HAMBURGER
    private const val DEFAULT_SLOT_RIGHT_1 = STATUS_BAR_BUTTON_EMOJI
    private const val DEFAULT_SLOT_RIGHT_2 = STATUS_BAR_BUTTON_MICROPHONE
    private const val DEFAULT_STATUS_BAR_VARIATIONS_VISIBLE = true
    private const val DEFAULT_DYNAMIC_VARIATION_BAR_SLOT_COUNT = 7
    private const val DEFAULT_DYNAMIC_VARIATION_BAR_RESIZE_TO_CONTENT = false
    const val MIN_DYNAMIC_VARIATION_BAR_SLOT_COUNT = 1
    const val MAX_DYNAMIC_VARIATION_BAR_SLOT_COUNT = 9

    private const val VARIATIONS_FILE_NAME = "variations.json"
    
    // Default values
    private const val DEFAULT_LONG_PRESS_THRESHOLD = 300L
    const val TYPING_SOUND_MODE_OFF = "off"
    const val TYPING_SOUND_MODE_CLICK = "click"
    const val TYPING_SOUND_MODE_TYPEWRITER = "typewriter"
    const val TYPING_SOUND_MODE_CUSTOM = "custom"
    const val TYPING_SOUND_OUTPUT_MEDIA = "media"
    const val TYPING_SOUND_OUTPUT_SYSTEM = "system"
    const val TYPING_SOUND_OUTPUT_NOTIFICATION = "notification"
    private const val DEFAULT_TYPING_SOUND_MODE = TYPING_SOUND_MODE_OFF
    private const val DEFAULT_TYPING_SOUND_OUTPUT_MODE = TYPING_SOUND_OUTPUT_MEDIA
    private const val TYPING_SOUND_CUSTOM_DIR = "typing_sounds"
    private const val TYPING_SOUND_CUSTOM_PACK_DIR = "custom_pack"
    private const val TYPING_SOUND_MAX_FILE_BYTES = 2L * 1024L * 1024L
    private const val TYPING_SOUND_MAX_PACK_BYTES = 16L * 1024L * 1024L
    private const val TYPING_SOUND_MAX_PACK_FILES = 96
    private val TYPING_SOUND_GROUPS = setOf("normal", "space", "backspace", "enter", "modifier")
    private val TYPING_SOUND_AUDIO_EXTENSIONS = setOf("ogg", "wav", "mp3", "m4a")
    private const val MIN_LONG_PRESS_THRESHOLD = 50L
    private const val MAX_LONG_PRESS_THRESHOLD = 1000L
    private const val DEFAULT_SWIPE_INCREMENTAL_THRESHOLD = 9.6f
    private const val MIN_SWIPE_INCREMENTAL_THRESHOLD = 3f
    private const val MAX_SWIPE_INCREMENTAL_THRESHOLD = 25f
    private const val DEFAULT_AUTO_CAPITALIZE_FIRST_LETTER = true
    private const val DEFAULT_DOUBLE_SPACE_TO_PERIOD = true
    private const val DEFAULT_SPACED_HYPHEN_TO_EN_DASH = false
    const val DASH_STYLE_EN = "en_dash"
    const val DASH_STYLE_EM = "em_dash"
    private const val DEFAULT_SPACED_HYPHEN_DASH_STYLE = DASH_STYLE_EN
    private const val DEFAULT_SMART_QUOTES = false
    const val SMART_QUOTES_STYLE_GERMAN_GUILLEMETS = "german_guillemets"
    const val SMART_QUOTES_STYLE_FRENCH_GUILLEMETS = "french_guillemets"
    const val SMART_QUOTES_STYLE_FRENCH_GUILLEMETS_NARROW_SPACED = "french_guillemets_narrow_spaced"
    const val SMART_QUOTES_STYLE_GERMAN_LOW_HIGH = "german_low_high"
    const val SMART_QUOTES_STYLE_ENGLISH_CURLY = "english_curly"
    private const val DEFAULT_SMART_QUOTES_STYLE = SMART_QUOTES_STYLE_GERMAN_GUILLEMETS
    private const val DEFAULT_SWIPE_TO_DELETE = false
    private const val DEFAULT_AUTO_SHOW_KEYBOARD = true
    private const val DEFAULT_CLEAR_ALT_ON_SPACE = true
    private const val DEFAULT_ALT_CTRL_SPEECH_SHORTCUT = true
    private const val DEFAULT_LAYOUT_AWARE_CTRL_SHORTCUTS = false
    private const val DEFAULT_AUTO_CORRECT_ENABLED = true
    private const val DEFAULT_SUGGESTIONS_ENABLED = true
    private const val DEFAULT_ACCENT_MATCHING_ENABLED = true
    private const val DEFAULT_AUTO_REPLACE_ON_SPACE_ENTER = false
    private const val DEFAULT_MAX_AUTO_REPLACE_DISTANCE = 1
    private const val DEFAULT_AUTO_CAPITALIZE_AFTER_PERIOD = true
    private const val DEFAULT_LONG_PRESS_MODIFIER = "alt"
    private const val DEFAULT_KEYBOARD_LAYOUT = "qwerty"
    private const val DEFAULT_KEYBOARD_LAYOUT_AUTO_BY_LOCALE = true
    private const val DEFAULT_ALT_SHIFT_LAYOUT_SWITCH = true
    private const val DEFAULT_CTRL_SPACE_LAYOUT_SWITCH = true
    private const val KEY_TOAST_ON_LAYOUT_SWITCH = "toast_on_layout_switch"
    private const val DEFAULT_TOAST_ON_LAYOUT_SWITCH = true
    private const val DEFAULT_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE = "auto"
    private const val DEFAULT_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL = "€"
    private const val DEFAULT_SYM_AUTO_CLOSE = true
    private const val DEFAULT_SYM_AUTO_CLOSE_ON_TOUCH = false
    private const val DEFAULT_MODIFIER_TAP_LATCHES = false
    private const val DEFAULT_MODIFIER_LATCH_STAYS_ON_SPACE = false
    private const val DEFAULT_BOUNCE_KEYS_ENABLED = false
    private const val DEFAULT_BOUNCE_KEYS_DELAY_MS = 80L
    private const val MIN_BOUNCE_KEYS_DELAY_MS = 20L
    private const val MAX_BOUNCE_KEYS_DELAY_MS = 500L
    private const val DEFAULT_BOUNCE_KEYS_CHARACTER_KEYS_ENABLED = true
    private const val DEFAULT_BOUNCE_KEYS_MODIFIER_KEYS_ENABLED = false
    private const val DEFAULT_BOUNCE_KEYS_SPACE_ENABLED = true
    private const val DEFAULT_BOUNCE_KEYS_ENTER_ENABLED = true
    private const val DEFAULT_BOUNCE_KEYS_BACKSPACE_ENABLED = true
    private const val DEFAULT_EMOJI_PICKER_EXPANDED_HEIGHT = true
    private val DEFAULT_SYM_PAGES_CONFIG = SymPagesConfig()
    private const val DEFAULT_STATIC_VARIATION_BAR_MODE = false
    private const val DEFAULT_STATIC_VARIATION_BAR_BASE_LAYER_ENABLED = false
    private const val DEFAULT_EXPERIMENTAL_SUGGESTIONS_ENABLED = true
    private const val DEFAULT_SUGGESTION_DEBUG_LOGGING = true
    private const val KEY_EXPERIMENTAL_SUGGESTIONS_ENABLED = "experimental_suggestions_enabled"
    private const val KEY_SUGGESTION_DEBUG_LOGGING = "suggestion_debug_logging"
    private const val KEY_IME_OVERLAY_DEBUG_LOGGING = "ime_overlay_debug_logging"
    private const val KEY_USE_KEYBOARD_PROXIMITY = "use_keyboard_proximity"
    private const val KEY_USE_EDIT_TYPE_RANKING = "use_edit_type_ranking"

    private const val DEFAULT_USE_KEYBOARD_PROXIMITY = false
    private const val DEFAULT_USE_EDIT_TYPE_RANKING = false
    private const val DEFAULT_IME_OVERLAY_DEBUG_LOGGING = false
    private const val DEFAULT_CLIPBOARD_HISTORY_ENABLED = true
    private const val DEFAULT_CLIPBOARD_RETENTION_TIME = 120L // 2 hours in minutes
    private const val DEFAULT_TRACKPAD_GESTURES_ENABLED = false
    private const val DEFAULT_TRACKPAD_GESTURE_ADD_WORD_ENABLED = true
    private const val DEFAULT_TRACKPAD_SWIPE_THRESHOLD = 500f
    private const val MIN_TRACKPAD_SWIPE_THRESHOLD = 120f
    private const val MAX_TRACKPAD_SWIPE_THRESHOLD = 1000f
    const val TRACKPAD_PROVIDER_SHIZUKU = "shizuku"
    const val TRACKPAD_PROVIDER_NATIVE_IME = "native_ime"
    private const val DEFAULT_TRACKPAD_PROVIDER = TRACKPAD_PROVIDER_NATIVE_IME
    private val TRACKPAD_PROVIDER_VALUES = setOf(
        TRACKPAD_PROVIDER_SHIZUKU,
        TRACKPAD_PROVIDER_NATIVE_IME
    )
    const val SWIPE_TO_DELETE_PROVIDER_TITAN2_KEYCODE = "titan2_keycode"
    const val SWIPE_TO_DELETE_PROVIDER_NATIVE_IME = "native_ime"
    private const val DEFAULT_SWIPE_TO_DELETE_PROVIDER = SWIPE_TO_DELETE_PROVIDER_NATIVE_IME
    private val SWIPE_TO_DELETE_PROVIDER_VALUES = setOf(
        SWIPE_TO_DELETE_PROVIDER_TITAN2_KEYCODE,
        SWIPE_TO_DELETE_PROVIDER_NATIVE_IME
    )
    private const val DEFAULT_SHIFT_BACKSPACE_DELETE = false
    private const val DEFAULT_ALT_BACKSPACE_DELETE = false
    private const val DEFAULT_BACKSPACE_AT_START_DELETE = false
    private const val DEFAULT_ACCESSIBILITY_LIVE_ANNOUNCEMENTS_ENABLED = false
    private const val DEFAULT_ACCESSIBILITY_READ_SECOND_ROW_ENABLED = false
    private const val DEFAULT_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS = 500L
    private const val DEFAULT_GLOBAL_VARIATION_LAYOUT_OVERRIDE = ""
    private const val MIN_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS = 100L
    private const val MAX_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS = 2000L
    private val STATIC_VARIATION_BASE_PRESET_DEFAULT = listOf("@", "\"", ":", "!", "?", ",", ".")
    private val STATIC_VARIATION_BASE_PRESET_NUMBERS = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
    private val STATIC_VARIATION_BASE_PRESET_ALTERNATIVE = listOf("[", "]", "$", "%", "^", "&", "\\")
    private val STATIC_VARIATION_BASE_PRESET_DEV_CHOICE = listOf("»", "«", ";", "!", "?", ",", ".", "–", "%")
    private val STATIC_VARIATION_SHIFT_PRESET_DEFAULT = listOf("{", "}", "€", "=", "~", ";", "¿")
    private val STATIC_VARIATION_ALT_PRESET_DEFAULT = listOf("<", ">", "¥", "|", "`", "´", "°")

    enum class PastierinaModeOverride(val storageValue: String) {
        FOLLOW_SYSTEM("follow_system"),
        FORCE_MINIMAL("force_minimal"),
        FORCE_FULL("force_full")
    }

    enum class SoftwareKeyboardMode(val storageValue: String) {
        AUTO("auto"),
        FORCE_HARDWARE("force_hardware"),
        FORCE_VIRTUAL("force_virtual")
    }

    /**
     * Returns the SharedPreferences instance for Pastiera.
     */
    fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getAppLanguageTag(context: Context): String? {
        return getPreferences(context).getString(KEY_APP_LANGUAGE_TAG, null)?.takeIf { it.isNotBlank() }
    }

    fun setAppLanguageTag(context: Context, languageTag: String?) {
        getPreferences(context).edit()
            .putString(KEY_APP_LANGUAGE_TAG, languageTag?.takeIf { it.isNotBlank() })
            .apply()
    }

    fun getPastierinaModeOverride(context: Context): PastierinaModeOverride {
        val value = getPreferences(context).getString(
            KEY_PASTIERINA_MODE_OVERRIDE,
            PastierinaModeOverride.FOLLOW_SYSTEM.storageValue
        )
        return PastierinaModeOverride.values().firstOrNull { it.storageValue == value }
            ?: PastierinaModeOverride.FOLLOW_SYSTEM
    }

    fun setPastierinaModeOverride(context: Context, override: PastierinaModeOverride) {
        getPreferences(context).edit()
            .putString(KEY_PASTIERINA_MODE_OVERRIDE, override.storageValue)
            .apply()
    }

    fun getPastierinaModeActive(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_PASTIERINA_MODE_ACTIVE, false)
    }

    fun setPastierinaModeActive(context: Context, isActive: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_PASTIERINA_MODE_ACTIVE, isActive)
            .apply()
    }

    fun getSoftwareKeyboardMode(context: Context): SoftwareKeyboardMode {
        val value = getPreferences(context).getString(
            KEY_SOFTWARE_KEYBOARD_MODE,
            SoftwareKeyboardMode.AUTO.storageValue
        )
        return SoftwareKeyboardMode.values().firstOrNull { it.storageValue == value }
            ?: SoftwareKeyboardMode.AUTO
    }

    fun setSoftwareKeyboardMode(context: Context, mode: SoftwareKeyboardMode) {
        getPreferences(context).edit()
            .putString(KEY_SOFTWARE_KEYBOARD_MODE, mode.storageValue)
            .apply()
    }

    fun resolveEffectiveSoftwareKeyboardMode(context: Context): SoftwareKeyboardMode {
        val configured = getSoftwareKeyboardMode(context)
        if (configured != SoftwareKeyboardMode.AUTO) {
            return configured
        }
        return it.palsoftware.pastiera.inputmethod.SoftwareKeyboardAutoDetector.resolve(context)
    }

    fun isTitan2LayoutEnabled(context: Context): Boolean {
        val prefs = getPreferences(context)
        if (prefs.contains(KEY_TITAN2_LAYOUT_ENABLED)) {
            return prefs.getBoolean(KEY_TITAN2_LAYOUT_ENABLED, false)
        }
        return DeviceSpecific.isTitan2Device()
    }

    fun setTitan2LayoutEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_TITAN2_LAYOUT_ENABLED, enabled)
            .apply()
    }

    /**
     * Returns the additional IME subtypes saved in preferences.
     */
    fun getAdditionalImeSubtypes(context: Context): Set<String> {
        return getPreferences(context)
            .getStringSet(KEY_ADDITIONAL_IME_SUBTYPES, emptySet())
            ?: emptySet()
    }

    /**
     * Persists the additional IME subtypes collection into preferences.
     */
    fun setAdditionalImeSubtypes(context: Context, subtypes: Collection<String>) {
        val normalized = subtypes
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        getPreferences(context).edit()
            .putStringSet(KEY_ADDITIONAL_IME_SUBTYPES, normalized)
            .apply()
    }

    /**
     * An input style (language + keyboard layout pair) used as the default subtype.
     */
    data class DefaultInputStyle(val locale: String, val layout: String)

    /**
     * Returns the input style (locale + layout, e.g. "en_US"/"qwerty") that should be forced
     * when a new input field is opened, or null when the feature is disabled (the keyboard
     * keeps the last used input style).
     */
    fun getDefaultInputStyle(context: Context): DefaultInputStyle? {
        val raw = getPreferences(context)
            .getString(KEY_DEFAULT_SUBTYPE_STYLE, "")
            ?.trim()
            .orEmpty()
        if (raw.isEmpty()) return null
        val separator = raw.indexOf(':')
        if (separator <= 0 || separator >= raw.length - 1) return null
        return DefaultInputStyle(
            locale = raw.substring(0, separator),
            layout = raw.substring(separator + 1)
        )
    }

    /**
     * Sets the default input style (language + layout) to force on every new input field.
     * Pass null for either value to disable and keep the last used input style.
     */
    fun setDefaultInputStyle(context: Context, locale: String?, layout: String?) {
        val value = if (locale.isNullOrBlank() || layout.isNullOrBlank()) {
            ""
        } else {
            "${locale.trim()}:${layout.trim()}"
        }
        getPreferences(context).edit()
            .putString(KEY_DEFAULT_SUBTYPE_STYLE, value)
            .apply()
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

    fun getShiftTapLatches(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_SHIFT_TAP_LATCHES,
            DEFAULT_MODIFIER_TAP_LATCHES
        )
    }

    fun setShiftTapLatches(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SHIFT_TAP_LATCHES, enabled)
            .apply()
    }

    fun getAltTapLatches(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_ALT_TAP_LATCHES,
            DEFAULT_MODIFIER_TAP_LATCHES
        )
    }

    fun setAltTapLatches(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_ALT_TAP_LATCHES, enabled)
            .apply()
    }

    fun getCtrlTapLatches(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_CTRL_TAP_LATCHES,
            DEFAULT_MODIFIER_TAP_LATCHES
        )
    }

    fun setCtrlTapLatches(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_CTRL_TAP_LATCHES, enabled)
            .apply()
    }

    fun getAltLatchStaysOnSpace(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_ALT_LATCH_STAYS_ON_SPACE,
            DEFAULT_MODIFIER_LATCH_STAYS_ON_SPACE
        )
    }

    fun setAltLatchStaysOnSpace(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_ALT_LATCH_STAYS_ON_SPACE, enabled)
            .apply()
    }

    fun getCtrlLatchStaysOnSpace(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_CTRL_LATCH_STAYS_ON_SPACE,
            DEFAULT_MODIFIER_LATCH_STAYS_ON_SPACE
        )
    }

    fun setCtrlLatchStaysOnSpace(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_CTRL_LATCH_STAYS_ON_SPACE, enabled)
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

    fun getTypingSoundMode(context: Context): String {
        val mode = getPreferences(context).getString(KEY_TYPING_SOUND_MODE, DEFAULT_TYPING_SOUND_MODE)
        return when (mode) {
            TYPING_SOUND_MODE_CLICK,
            TYPING_SOUND_MODE_TYPEWRITER,
            TYPING_SOUND_MODE_CUSTOM -> mode
            else -> TYPING_SOUND_MODE_OFF
        }
    }

    fun setTypingSoundMode(context: Context, mode: String) {
        val normalized = when (mode) {
            TYPING_SOUND_MODE_CLICK,
            TYPING_SOUND_MODE_TYPEWRITER,
            TYPING_SOUND_MODE_CUSTOM -> mode
            else -> TYPING_SOUND_MODE_OFF
        }
        getPreferences(context).edit()
            .putString(KEY_TYPING_SOUND_MODE, normalized)
            .apply()
    }

    fun getTypingSoundOutputMode(context: Context): String {
        val mode = getPreferences(context).getString(KEY_TYPING_SOUND_OUTPUT_MODE, DEFAULT_TYPING_SOUND_OUTPUT_MODE)
        return when (mode) {
            TYPING_SOUND_OUTPUT_SYSTEM,
            TYPING_SOUND_OUTPUT_NOTIFICATION -> mode
            else -> TYPING_SOUND_OUTPUT_MEDIA
        }
    }

    fun setTypingSoundOutputMode(context: Context, mode: String) {
        val normalized = when (mode) {
            TYPING_SOUND_OUTPUT_SYSTEM,
            TYPING_SOUND_OUTPUT_NOTIFICATION -> mode
            else -> TYPING_SOUND_OUTPUT_MEDIA
        }
        getPreferences(context).edit()
            .putString(KEY_TYPING_SOUND_OUTPUT_MODE, normalized)
            .apply()
    }

    fun getTypingSoundCustomDisplayName(context: Context): String? {
        return getPreferences(context)
            .getString(KEY_TYPING_SOUND_CUSTOM_DISPLAY_NAME, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun getTypingSoundCustomFile(context: Context): File? {
        val fileName = getPreferences(context)
            .getString(KEY_TYPING_SOUND_CUSTOM_FILE_NAME, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return File(File(context.filesDir, TYPING_SOUND_CUSTOM_DIR), fileName).takeIf { it.isFile }
    }

    fun getTypingSoundCustomGroupFiles(context: Context): Map<String, List<File>> {
        val storedName = getPreferences(context)
            .getString(KEY_TYPING_SOUND_CUSTOM_FILE_NAME, null)
            ?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
        val root = File(File(context.filesDir, TYPING_SOUND_CUSTOM_DIR), storedName)
        if (!root.isDirectory) {
            getTypingSoundCustomFile(context)?.let { file ->
                return mapOf("normal" to listOf(file))
            }
            return emptyMap()
        }

        return TYPING_SOUND_GROUPS.associateWith { group ->
            File(root, group)
                .listFiles()
                .orEmpty()
                .filter { it.isFile && it.extension.lowercase() in TYPING_SOUND_AUDIO_EXTENSIONS }
                .sortedBy { it.name }
        }.filterValues { it.isNotEmpty() }
    }

    fun importTypingSoundPack(context: Context, uri: Uri): Boolean {
        val displayName = queryDisplayName(context, uri) ?: return false
        val extension = displayName.substringAfterLast('.', "").lowercase()
        if (extension != "zip") {
            return false
        }

        val targetDir = File(context.filesDir, TYPING_SOUND_CUSTOM_DIR).apply { mkdirs() }
        val stagingDir = File(targetDir, "${TYPING_SOUND_CUSTOM_PACK_DIR}_staging").apply {
            deleteRecursively()
            mkdirs()
        }
        val finalDir = File(targetDir, TYPING_SOUND_CUSTOM_PACK_DIR)

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                extractTypingSoundPack(input, stagingDir)
            } ?: return false

            if (File(stagingDir, "normal").listFiles().orEmpty().none { it.isFile }) {
                stagingDir.deleteRecursively()
                return false
            }

            finalDir.deleteRecursively()
            if (!stagingDir.renameTo(finalDir)) {
                stagingDir.copyRecursively(finalDir, overwrite = true)
                stagingDir.deleteRecursively()
            }

            getPreferences(context).edit()
                .putString(KEY_TYPING_SOUND_CUSTOM_FILE_NAME, finalDir.name)
                .putString(KEY_TYPING_SOUND_CUSTOM_DISPLAY_NAME, displayName)
                .putString(KEY_TYPING_SOUND_MODE, TYPING_SOUND_MODE_CUSTOM)
                .putLong(KEY_TYPING_SOUND_UPDATED_AT, System.currentTimeMillis())
                .apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error importing typing sound", e)
            stagingDir.deleteRecursively()
            false
        }
    }

    fun importTypingSound(context: Context, uri: Uri): Boolean = importTypingSoundPack(context, uri)

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    return cursor.getString(nameIndex)
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    }

    private fun extractTypingSoundPack(input: InputStream, targetDir: File) {
        var fileCount = 0
        var totalBytes = 0L
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            val canonicalTargetRoot = targetDir.canonicalFile

            while (entry != null) {
                val entryName = entry.name.removePrefix("./")
                if (!entry.isDirectory && !entryName.startsWith("__MACOSX/")) {
                    val group = resolveTypingSoundGroup(entryName)
                    val extension = entryName.substringAfterLast('.', "").lowercase()
                    if (group != null && extension in TYPING_SOUND_AUDIO_EXTENSIONS) {
                        fileCount += 1
                        if (fileCount > TYPING_SOUND_MAX_PACK_FILES) {
                            throw IllegalArgumentException("Typing sound pack has too many files")
                        }

                        val outFile = File(File(targetDir, group).apply { mkdirs() }, "${fileCount.toString().padStart(3, '0')}.$extension")
                        val canonicalOutFile = outFile.canonicalFile
                        if (!canonicalOutFile.path.startsWith(canonicalTargetRoot.path)) {
                            throw IllegalStateException("Refusing to unzip entry outside target dir: $entryName")
                        }

                        canonicalOutFile.outputStream().use { output ->
                            totalBytes += copyWithLimit(zip, output, TYPING_SOUND_MAX_FILE_BYTES, TYPING_SOUND_MAX_PACK_BYTES - totalBytes)
                        }
                    }
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun resolveTypingSoundGroup(entryName: String): String? {
        val parts = entryName.split('/').filter { it.isNotBlank() }
        return parts.firstOrNull { it.lowercase() in TYPING_SOUND_GROUPS }?.lowercase()
    }

    private fun copyWithLimit(input: InputStream, output: OutputStream, maxBytes: Long, remainingPackBytes: Long = Long.MAX_VALUE): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            copied += read
            if (copied > maxBytes || copied > remainingPackBytes) {
                throw IllegalArgumentException("Typing sound import exceeds size limits")
            }
            output.write(buffer, 0, read)
        }
        return copied
    }
    
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
     * Returns the state of spaced-hyphen-to-en-dash smart punctuation.
     */
    fun getSpacedHyphenToEnDash(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SPACED_HYPHEN_TO_EN_DASH, DEFAULT_SPACED_HYPHEN_TO_EN_DASH)
    }

    /**
     * Sets the state of spaced-hyphen-to-en-dash smart punctuation.
     */
    fun setSpacedHyphenToEnDash(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SPACED_HYPHEN_TO_EN_DASH, enabled)
            .apply()
    }

    fun getSpacedHyphenDashStyle(context: Context): String {
        val stored = getPreferences(context).getString(KEY_SPACED_HYPHEN_DASH_STYLE, DEFAULT_SPACED_HYPHEN_DASH_STYLE)
        return when (stored) {
            DASH_STYLE_EN,
            DASH_STYLE_EM -> stored
            else -> DEFAULT_SPACED_HYPHEN_DASH_STYLE
        }
    }

    fun setSpacedHyphenDashStyle(context: Context, style: String) {
        getPreferences(context).edit()
            .putString(
                KEY_SPACED_HYPHEN_DASH_STYLE,
                when (style) {
                    DASH_STYLE_EN,
                    DASH_STYLE_EM -> style
                    else -> DEFAULT_SPACED_HYPHEN_DASH_STYLE
                }
            )
            .apply()
    }

    fun getSmartQuotes(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SMART_QUOTES, DEFAULT_SMART_QUOTES)
    }

    fun setSmartQuotes(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SMART_QUOTES, enabled)
            .apply()
    }

    fun getSmartQuotesStyle(context: Context): String {
        val stored = getPreferences(context).getString(KEY_SMART_QUOTES_STYLE, DEFAULT_SMART_QUOTES_STYLE)
        return when (stored) {
            SMART_QUOTES_STYLE_GERMAN_GUILLEMETS,
            SMART_QUOTES_STYLE_FRENCH_GUILLEMETS,
            SMART_QUOTES_STYLE_FRENCH_GUILLEMETS_NARROW_SPACED,
            SMART_QUOTES_STYLE_GERMAN_LOW_HIGH,
            SMART_QUOTES_STYLE_ENGLISH_CURLY -> stored
            else -> DEFAULT_SMART_QUOTES_STYLE
        }
    }

    fun setSmartQuotesStyle(context: Context, style: String) {
        getPreferences(context).edit()
            .putString(
                KEY_SMART_QUOTES_STYLE,
                when (style) {
                    SMART_QUOTES_STYLE_GERMAN_GUILLEMETS,
                    SMART_QUOTES_STYLE_FRENCH_GUILLEMETS,
                    SMART_QUOTES_STYLE_FRENCH_GUILLEMETS_NARROW_SPACED,
                    SMART_QUOTES_STYLE_GERMAN_LOW_HIGH,
                    SMART_QUOTES_STYLE_ENGLISH_CURLY -> style
                    else -> DEFAULT_SMART_QUOTES_STYLE
                }
            )
            .apply()
    }
    
    /**
     * Returns the state of swipe-to-delete.
     */
    fun getSwipeToDelete(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SWIPE_TO_DELETE, DEFAULT_SWIPE_TO_DELETE)
    }
    
    /**
     * Sets the state of swipe-to-delete.
     */
    fun setSwipeToDelete(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SWIPE_TO_DELETE, enabled)
            .apply()
    }

    fun getSwipeToDeleteProvider(context: Context): String {
        val value = getPreferences(context).getString(
            KEY_SWIPE_TO_DELETE_PROVIDER,
            DEFAULT_SWIPE_TO_DELETE_PROVIDER
        ).orEmpty()
        return if (SWIPE_TO_DELETE_PROVIDER_VALUES.contains(value)) {
            value
        } else {
            DEFAULT_SWIPE_TO_DELETE_PROVIDER
        }
    }

    fun setSwipeToDeleteProvider(context: Context, provider: String) {
        val normalized = if (SWIPE_TO_DELETE_PROVIDER_VALUES.contains(provider)) {
            provider
        } else {
            DEFAULT_SWIPE_TO_DELETE_PROVIDER
        }
        getPreferences(context).edit()
            .putString(KEY_SWIPE_TO_DELETE_PROVIDER, normalized)
            .commit()
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
     * Returns whether Ctrl+letter app shortcuts should be resolved through
     * the active Pastiera layout before being passed to the target app.
     */
    fun getLayoutAwareCtrlShortcutsEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_LAYOUT_AWARE_CTRL_SHORTCUTS,
            DEFAULT_LAYOUT_AWARE_CTRL_SHORTCUTS
        )
    }

    /**
     * Sets whether Ctrl+letter app shortcuts should use the active Pastiera layout.
     */
    fun setLayoutAwareCtrlShortcutsEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_LAYOUT_AWARE_CTRL_SHORTCUTS, enabled)
            .apply()
    }

    /**
     * Returns whether accessibility live announcements are enabled.
     */
    fun getAccessibilityLiveAnnouncementsEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_ACCESSIBILITY_LIVE_ANNOUNCEMENTS_ENABLED,
            DEFAULT_ACCESSIBILITY_LIVE_ANNOUNCEMENTS_ENABLED
        )
    }

    /**
     * Sets whether accessibility live announcements are enabled.
     */
    fun setAccessibilityLiveAnnouncementsEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_ACCESSIBILITY_LIVE_ANNOUNCEMENTS_ENABLED, enabled)
            .apply()
    }

    /**
     * Returns whether accessibility should read the second IME row
     * (quick settings and variations).
     */
    fun getAccessibilityReadSecondRowEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_ACCESSIBILITY_READ_SECOND_ROW_ENABLED,
            DEFAULT_ACCESSIBILITY_READ_SECOND_ROW_ENABLED
        )
    }

    /**
     * Sets whether accessibility should read the second IME row
     * (quick settings and variations).
     */
    fun setAccessibilityReadSecondRowEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_ACCESSIBILITY_READ_SECOND_ROW_ENABLED, enabled)
            .apply()
    }

    /**
     * Returns the delay before suggestion row accessibility is re-enabled after updates.
     */
    fun getAccessibilitySuggestionsAnnouncementDelayMs(context: Context): Long {
        return getPreferences(context).getLong(
            KEY_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS,
            DEFAULT_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS
        )
    }

    /**
     * Sets the delay before suggestion row accessibility is re-enabled after updates.
     */
    fun setAccessibilitySuggestionsAnnouncementDelayMs(context: Context, delayMs: Long) {
        val clamped = delayMs.coerceIn(
            MIN_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS,
            MAX_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS
        )
        getPreferences(context).edit()
            .putLong(KEY_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS, clamped)
            .apply()
    }

    /**
     * Returns the optional global layout id used for variation ordering across all layouts.
     * Returns null when no override is configured.
     */
    fun getGlobalVariationLayoutOverride(context: Context): String? {
        val stored = getPreferences(context).getString(
            KEY_GLOBAL_VARIATION_LAYOUT_OVERRIDE,
            DEFAULT_GLOBAL_VARIATION_LAYOUT_OVERRIDE
        )?.trim().orEmpty()
        return stored.ifEmpty { null }
    }

    /**
     * Sets the optional global layout id used for variation ordering.
     * Pass null/blank to disable the override and use per-layout behavior.
     */
    fun setGlobalVariationLayoutOverride(context: Context, layoutName: String?) {
        val normalized = layoutName?.trim().orEmpty()
        val editor = getPreferences(context).edit()
        if (normalized.isEmpty()) {
            editor.remove(KEY_GLOBAL_VARIATION_LAYOUT_OVERRIDE)
        } else {
            editor.putString(KEY_GLOBAL_VARIATION_LAYOUT_OVERRIDE, normalized)
        }
        editor.apply()
        notifyVariationsUpdated(context)
    }

    fun getMinAccessibilitySuggestionsAnnouncementDelayMs(): Long =
        MIN_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS

    fun getMaxAccessibilitySuggestionsAnnouncementDelayMs(): Long =
        MAX_ACCESSIBILITY_SUGGESTIONS_ANNOUNCEMENT_DELAY_MS

    fun getBounceKeysEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_BOUNCE_KEYS_ENABLED,
            DEFAULT_BOUNCE_KEYS_ENABLED
        )
    }

    fun setBounceKeysEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_BOUNCE_KEYS_ENABLED, enabled)
            .apply()
    }

    fun getBounceKeysDelayMs(context: Context): Long {
        return getPreferences(context).getLong(
            KEY_BOUNCE_KEYS_DELAY_MS,
            DEFAULT_BOUNCE_KEYS_DELAY_MS
        ).coerceIn(MIN_BOUNCE_KEYS_DELAY_MS, MAX_BOUNCE_KEYS_DELAY_MS)
    }

    fun setBounceKeysDelayMs(context: Context, delayMs: Long) {
        getPreferences(context).edit()
            .putLong(
                KEY_BOUNCE_KEYS_DELAY_MS,
                delayMs.coerceIn(MIN_BOUNCE_KEYS_DELAY_MS, MAX_BOUNCE_KEYS_DELAY_MS)
            )
            .apply()
    }

    fun getMinBounceKeysDelayMs(): Long = MIN_BOUNCE_KEYS_DELAY_MS

    fun getMaxBounceKeysDelayMs(): Long = MAX_BOUNCE_KEYS_DELAY_MS

    fun getBounceKeysCharacterKeysEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_BOUNCE_KEYS_CHARACTER_KEYS_ENABLED,
            DEFAULT_BOUNCE_KEYS_CHARACTER_KEYS_ENABLED
        )
    }

    fun setBounceKeysCharacterKeysEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_BOUNCE_KEYS_CHARACTER_KEYS_ENABLED, enabled)
            .apply()
    }

    fun getBounceKeysModifierKeysEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_BOUNCE_KEYS_MODIFIER_KEYS_ENABLED,
            DEFAULT_BOUNCE_KEYS_MODIFIER_KEYS_ENABLED
        )
    }

    fun setBounceKeysModifierKeysEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_BOUNCE_KEYS_MODIFIER_KEYS_ENABLED, enabled)
            .apply()
    }

    fun getBounceKeysSpaceEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_BOUNCE_KEYS_SPACE_ENABLED,
            DEFAULT_BOUNCE_KEYS_SPACE_ENABLED
        )
    }

    fun setBounceKeysSpaceEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_BOUNCE_KEYS_SPACE_ENABLED, enabled)
            .apply()
    }

    fun getBounceKeysEnterEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_BOUNCE_KEYS_ENTER_ENABLED,
            DEFAULT_BOUNCE_KEYS_ENTER_ENABLED
        )
    }

    fun setBounceKeysEnterEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_BOUNCE_KEYS_ENTER_ENABLED, enabled)
            .apply()
    }

    fun getBounceKeysBackspaceEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_BOUNCE_KEYS_BACKSPACE_ENABLED,
            DEFAULT_BOUNCE_KEYS_BACKSPACE_ENABLED
        )
    }

    fun setBounceKeysBackspaceEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_BOUNCE_KEYS_BACKSPACE_ENABLED, enabled)
            .apply()
    }

    fun getBounceKeysCategoryEnabled(
        context: Context,
        category: it.palsoftware.pastiera.inputmethod.BounceKeyFilter.Category
    ): Boolean {
        return when (category) {
            it.palsoftware.pastiera.inputmethod.BounceKeyFilter.Category.CHARACTER ->
                getBounceKeysCharacterKeysEnabled(context)
            it.palsoftware.pastiera.inputmethod.BounceKeyFilter.Category.MODIFIER ->
                getBounceKeysModifierKeysEnabled(context)
            it.palsoftware.pastiera.inputmethod.BounceKeyFilter.Category.SPACE ->
                getBounceKeysSpaceEnabled(context)
            it.palsoftware.pastiera.inputmethod.BounceKeyFilter.Category.ENTER ->
                getBounceKeysEnterEnabled(context)
            it.palsoftware.pastiera.inputmethod.BounceKeyFilter.Category.BACKSPACE ->
                getBounceKeysBackspaceEnabled(context)
            it.palsoftware.pastiera.inputmethod.BounceKeyFilter.Category.UNSUPPORTED -> false
        }
    }

    /**
     * Returns whether Shift+Backspace performs forward delete.
     */
    fun getShiftBackspaceDelete(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SHIFT_BACKSPACE_DELETE, DEFAULT_SHIFT_BACKSPACE_DELETE)
    }

    /**
     * Sets whether Shift+Backspace performs forward delete.
     */
    fun setShiftBackspaceDelete(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SHIFT_BACKSPACE_DELETE, enabled)
            .apply()
    }

    /**
     * Returns whether Alt+Backspace performs forward delete.
     */
    fun getAltBackspaceDelete(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_ALT_BACKSPACE_DELETE, DEFAULT_ALT_BACKSPACE_DELETE)
    }

    /**
     * Sets whether Alt+Backspace performs forward delete.
     */
    fun setAltBackspaceDelete(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_ALT_BACKSPACE_DELETE, enabled)
            .apply()
    }

    /**
     * Returns whether Backspace at line start performs forward delete.
     */
    fun getBackspaceAtStartDelete(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_BACKSPACE_AT_START_DELETE, DEFAULT_BACKSPACE_AT_START_DELETE)
    }

    /**
     * Sets whether Backspace at line start performs forward delete.
     */
    fun setBackspaceAtStartDelete(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_BACKSPACE_AT_START_DELETE, enabled)
            .apply()
    }

    /**
     * Returns whether the static variation bar mode is enabled.
     * When enabled, the variation row shows a fixed set of utility keys
     * instead of dynamic cursor-based character variations.
     */
    fun isStaticVariationBarModeEnabled(context: Context): Boolean {
        return getStaticVariationBarPreset(context) != STATIC_VARIATION_PRESET_OFF
    }

    /**
     * Sets whether the static variation bar mode is enabled.
     */
    fun setStaticVariationBarModeEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_STATIC_VARIATION_BAR_MODE, enabled)
            .putString(
                KEY_STATIC_VARIATION_BAR_PRESET,
                if (enabled) STATIC_VARIATION_PRESET_SYMBOLS else STATIC_VARIATION_PRESET_OFF
            )
            .apply()
    }

    fun getStaticVariationBarPreset(context: Context): String {
        val prefs = getPreferences(context)
        val stored = prefs.getString(KEY_STATIC_VARIATION_BAR_PRESET, null)
        val fallback = if (prefs.getBoolean(KEY_STATIC_VARIATION_BAR_MODE, DEFAULT_STATIC_VARIATION_BAR_MODE)) {
            if (prefs.getBoolean(
                    KEY_STATIC_VARIATION_BAR_BASE_LAYER_ENABLED,
                    DEFAULT_STATIC_VARIATION_BAR_BASE_LAYER_ENABLED
                )
            ) {
                STATIC_VARIATION_PRESET_ALTERNATIVE
            } else {
                STATIC_VARIATION_PRESET_SYMBOLS
            }
        } else {
            STATIC_VARIATION_PRESET_OFF
        }
        return when (stored ?: fallback) {
            STATIC_VARIATION_PRESET_OFF,
            STATIC_VARIATION_PRESET_SYMBOLS,
            STATIC_VARIATION_PRESET_NUMBERS,
            STATIC_VARIATION_PRESET_ALTERNATIVE,
            STATIC_VARIATION_PRESET_DEV_CHOICE -> stored ?: fallback
            else -> fallback
        }
    }

    fun setStaticVariationBarPreset(context: Context, preset: String) {
        val normalized = when (preset) {
            STATIC_VARIATION_PRESET_OFF,
            STATIC_VARIATION_PRESET_SYMBOLS,
            STATIC_VARIATION_PRESET_NUMBERS,
            STATIC_VARIATION_PRESET_ALTERNATIVE,
            STATIC_VARIATION_PRESET_DEV_CHOICE -> preset
            else -> STATIC_VARIATION_PRESET_OFF
        }
        getPreferences(context).edit()
            .putString(KEY_STATIC_VARIATION_BAR_PRESET, normalized)
            .putBoolean(KEY_STATIC_VARIATION_BAR_MODE, normalized != STATIC_VARIATION_PRESET_OFF)
            .putBoolean(KEY_STATIC_VARIATION_BAR_BASE_LAYER_ENABLED, normalized == STATIC_VARIATION_PRESET_ALTERNATIVE)
            .apply()

        if (normalized != STATIC_VARIATION_PRESET_OFF) {
            saveStaticVariationRows(
                context = context,
                staticVariations = getStaticVariationBasePreset(normalized),
                staticVariationsShift = getStaticVariationShiftPreset(normalized),
                staticVariationsAlt = getStaticVariationAltPreset(normalized)
            )
        }
    }

    /**
     * Returns whether the base (top) static variation row is enabled.
     * Shift/Alt static layers remain available independently.
     */
    fun isStaticVariationBarBaseLayerEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_STATIC_VARIATION_BAR_BASE_LAYER_ENABLED,
            DEFAULT_STATIC_VARIATION_BAR_BASE_LAYER_ENABLED
        )
    }

    /**
     * Sets whether the base (top) static variation row is enabled.
     */
    fun setStaticVariationBarBaseLayerEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_STATIC_VARIATION_BAR_BASE_LAYER_ENABLED, enabled)
            .apply()
    }

    /**
     * Returns the top-row preset for the static variation bar based on toggle state.
     */
    fun getStaticVariationBasePreset(context: Context): List<String> {
        return getStaticVariationBasePreset(getStaticVariationBarPreset(context))
    }

    private fun getStaticVariationBasePreset(preset: String): List<String> {
        return when (preset) {
            STATIC_VARIATION_PRESET_NUMBERS -> STATIC_VARIATION_BASE_PRESET_NUMBERS
            STATIC_VARIATION_PRESET_ALTERNATIVE -> STATIC_VARIATION_BASE_PRESET_ALTERNATIVE
            STATIC_VARIATION_PRESET_DEV_CHOICE -> STATIC_VARIATION_BASE_PRESET_DEV_CHOICE
            else -> STATIC_VARIATION_BASE_PRESET_DEFAULT
        }
    }

    fun getDefaultStaticVariationShiftPreset(): List<String> = STATIC_VARIATION_SHIFT_PRESET_DEFAULT

    fun getDefaultStaticVariationAltPreset(): List<String> = STATIC_VARIATION_ALT_PRESET_DEFAULT

    private fun getStaticVariationShiftPreset(preset: String): List<String> {
        return when (preset) {
            STATIC_VARIATION_PRESET_NUMBERS -> STATIC_VARIATION_BASE_PRESET_NUMBERS
            STATIC_VARIATION_PRESET_DEV_CHOICE -> STATIC_VARIATION_BASE_PRESET_DEV_CHOICE
            else -> STATIC_VARIATION_SHIFT_PRESET_DEFAULT
        }
    }

    private fun getStaticVariationAltPreset(preset: String): List<String> {
        return when (preset) {
            STATIC_VARIATION_PRESET_NUMBERS -> STATIC_VARIATION_BASE_PRESET_NUMBERS
            STATIC_VARIATION_PRESET_DEV_CHOICE -> STATIC_VARIATION_BASE_PRESET_DEV_CHOICE
            else -> STATIC_VARIATION_ALT_PRESET_DEFAULT
        }
    }

    fun getStaticVariationNumbersPreset(): List<String> = STATIC_VARIATION_BASE_PRESET_NUMBERS

    fun getDevChoiceStaticVariationBasePreset(): List<String> = STATIC_VARIATION_BASE_PRESET_DEV_CHOICE

    /**
     * Returns true if the static variation layer should remain latched after modifier hold.
     */
    fun isStaticVariationBarLayerStickyEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_STATIC_VARIATION_BAR_MODIFIER_HOLD_RESTORATION, true)
    }

    /**
     * Sets whether static variation layers should stay latched after modifier hold.
     */
    fun setStaticVariationBarLayerStickyEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_STATIC_VARIATION_BAR_MODIFIER_HOLD_RESTORATION, enabled)
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
     * Optional debug logging for IME overlay / inset calculations.
     */
    fun isImeOverlayDebugLoggingEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_IME_OVERLAY_DEBUG_LOGGING, DEFAULT_IME_OVERLAY_DEBUG_LOGGING)
    }

    fun setImeOverlayDebugLoggingEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_IME_OVERLAY_DEBUG_LOGGING, enabled)
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
     * Returns the maximum edit distance for auto-replace (0-3).
     * 0 = off (no auto-replace), 1-3 = maximum distance allowed.
     */
    fun getMaxAutoReplaceDistance(context: Context): Int {
        return getPreferences(context).getInt(KEY_MAX_AUTO_REPLACE_DISTANCE, DEFAULT_MAX_AUTO_REPLACE_DISTANCE)
            .coerceIn(0, 3)
    }

    /**
     * Sets the maximum edit distance for auto-replace (0-3).
     * 0 = off (no auto-replace), 1-3 = maximum distance allowed.
     */
    fun setMaxAutoReplaceDistance(context: Context, distance: Int) {
        getPreferences(context).edit()
            .putInt(KEY_MAX_AUTO_REPLACE_DISTANCE, distance.coerceIn(0, 3))
            .apply()
    }

    /**
     * Returns whether keyboard proximity ranking is enabled for suggestions.
     * When enabled, suggestions consider keyboard distance to filter out unlikely typos.
     */
    fun getUseKeyboardProximity(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_USE_KEYBOARD_PROXIMITY, DEFAULT_USE_KEYBOARD_PROXIMITY)
    }

    /**
     * Enables or disables keyboard proximity ranking for suggestions.
     */
    fun setUseKeyboardProximity(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_USE_KEYBOARD_PROXIMITY, enabled)
            .apply()
    }

    /**
     * Returns whether edit type ranking is enabled for suggestions.
     * When enabled, suggestions are ranked by edit type (insert > substitute > delete).
     */
    fun getUseEditTypeRanking(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_USE_EDIT_TYPE_RANKING, DEFAULT_USE_EDIT_TYPE_RANKING)
    }

    /**
     * Enables or disables edit type ranking for suggestions.
     */
    fun setUseEditTypeRanking(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_USE_EDIT_TYPE_RANKING, enabled)
            .apply()
    }

    /**
     * Returns the list of languages enabled for auto-correction.
     * @return Set of language codes (e.g. "it", "en")
     */
    fun getAutoCorrectEnabledLanguages(context: Context): Set<String> {
        val prefs = getPreferences(context)
        val languagesString = prefs.getString(KEY_AUTO_CORRECT_ENABLED_LANGUAGES, null)
        
        // If languages are explicitly set, return them as-is (user controlled)
        if (languagesString != null && languagesString.isNotEmpty()) {
            return languagesString.split(",").toSet()
        }
        
        // Default: system language + x-pastiera, with fallback to English
        val systemLanguage = context.applicationContext.resources.configuration.locales[0].language.lowercase()
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
     */
    fun setAutoCorrectEnabledLanguages(context: Context, languages: Set<String>) {
        val languagesString = languages.joinToString(",")
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
     * Returns the long-press modifier type ("alt", "shift", "variations", or "sym").
     */
    fun getLongPressModifier(context: Context): String {
        val stored = getPreferences(context).getString(KEY_LONG_PRESS_MODIFIER, DEFAULT_LONG_PRESS_MODIFIER)
            ?: DEFAULT_LONG_PRESS_MODIFIER
        return when (stored) {
            "alt", "shift", "variations", "sym" -> stored
            else -> DEFAULT_LONG_PRESS_MODIFIER
        }
    }
    
    /**
     * Sets the long-press modifier type ("alt", "shift", "variations", or "sym").
     */
    fun setLongPressModifier(context: Context, modifier: String) {
        val validModifier = when (modifier) {
            "shift" -> "shift"
            "variations" -> "variations"
            "sym" -> "sym"
            else -> "alt"
        }
        getPreferences(context).edit()
            .putString(KEY_LONG_PRESS_MODIFIER, validModifier)
            .apply()
    }
    
    /**
     * Returns true if long press uses Shift, false if it uses Alt.
     * @deprecated Use getLongPressModifier() for more granular control
     */
    fun isLongPressShift(context: Context): Boolean {
        return getLongPressModifier(context) == "shift"
    }

    /**
     * Returns true if long press uses Variations mode.
     */
    fun isLongPressVariations(context: Context): Boolean {
        return getLongPressModifier(context) == "variations"
    }

    /**
     * Returns true if long press uses Sym mode.
     */
    fun isLongPressSym(context: Context): Boolean {
        return getLongPressModifier(context) == "sym"
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
        val data: String? = null, // Dati aggiuntivi per tipi futuri
        val commandId: String? = null,
        val commandSource: String? = null,
        val commandKind: String? = null,
        val commandTitle: String? = null,
        val commandSubtitle: String? = null,
        val commandLaunch: CommandLaunchSpec? = null
    ) {
        companion object {
            const val TYPE_APP = "app"
            const val TYPE_SHORTCUT = "shortcut"
            const val TYPE_QUICK_LAUNCHER = "quick_launcher"
            const val TYPE_COMMAND = "command"
            // Aggiungi altri tipi in futuro qui
        }
    }
    
    private const val KEY_LAUNCHER_SHORTCUTS = "launcher_shortcuts"
    private const val KEY_LAUNCHER_SHORTCUTS_ENABLED = "launcher_shortcuts_enabled"
    private const val KEY_QUICK_LAUNCHER_DEFAULT_ASSIGNED = "quick_launcher_default_assigned"
    private const val KEY_QUICK_LAUNCHER_AUTO_START_SINGLE = "quick_launcher_auto_start_single"
    private const val KEY_QUICK_LAUNCHER_LIMIT_RESULTS = "quick_launcher_limit_results"
    private const val KEY_QUICK_LAUNCHER_TEXT_FIELD_SHORTCUTS = "quick_launcher_text_field_shortcuts"
    private const val KEY_QUICK_LAUNCHER_RESPECT_KEYBOARD_LAYOUT = "quick_launcher_respect_keyboard_layout"
    private const val KEY_QUICK_LAUNCHER_TYPO_TOLERANT_RANKING = "quick_launcher_typo_tolerant_ranking"
    private const val KEY_QUICK_LAUNCHER_WIDTH_PERCENT = "quick_launcher_width_percent"
    private const val KEY_QUICK_LAUNCHER_PILL_MODE = "quick_launcher_pill_mode"
    private const val KEY_QUICK_LAUNCHER_BEHAVIOR = "quick_launcher_behavior"
    private const val KEY_COMMAND_SURFACE_SOURCES = "command_surface_sources"
    private const val KEY_QUICK_LAUNCHER_COMMAND_CUSTOMIZATIONS = "quick_launcher_command_customizations"
    private const val KEY_QUICK_LAUNCHER_HIGHLIGHT_FAVORITES = "quick_launcher_highlight_favorites"
    private const val KEY_QUICK_LAUNCHER_FAVORITE_COLOR = "quick_launcher_favorite_color"
    private const val KEY_QUICK_LAUNCHER_ICON_COLORS = "quick_launcher_icon_colors"
    private const val KEY_QUICK_LAUNCHER_SHOW_ALIAS_FIRST = "quick_launcher_show_alias_first"
    private const val KEY_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT = "quick_launcher_static_top_highlight"
    private const val KEY_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT_COLOR = "quick_launcher_static_top_highlight_color"
    private const val DEFAULT_LAUNCHER_SHORTCUTS_ENABLED = false
    private const val DEFAULT_QUICK_LAUNCHER_AUTO_START_SINGLE = false
    private const val DEFAULT_QUICK_LAUNCHER_LIMIT_RESULTS = false
    private const val DEFAULT_QUICK_LAUNCHER_TEXT_FIELD_SHORTCUTS = true
    private const val DEFAULT_QUICK_LAUNCHER_RESPECT_KEYBOARD_LAYOUT = true
    private const val DEFAULT_QUICK_LAUNCHER_TYPO_TOLERANT_RANKING = true
    private const val DEFAULT_QUICK_LAUNCHER_WIDTH_PERCENT = 100
    private const val DEFAULT_QUICK_LAUNCHER_PILL_MODE = false
    const val QUICK_LAUNCHER_DYNAMIC_FAVORITE_COLOR = Int.MIN_VALUE
    private const val DEFAULT_QUICK_LAUNCHER_FAVORITE_COLOR = QUICK_LAUNCHER_DYNAMIC_FAVORITE_COLOR
    private const val DEFAULT_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT_COLOR = 0x7A4285F4
    const val QUICK_LAUNCHER_BEHAVIOR_PASTIERA = "pastiera"
    const val QUICK_LAUNCHER_BEHAVIOR_NIAGARA = "niagara"
    
    // Nav mode settings
    private const val KEY_NAV_MODE_ENABLED = "nav_mode_enabled"
    private const val DEFAULT_NAV_MODE_ENABLED = true
    private const val KEY_NAV_MODE_CTRL_HOLD_ENABLED = "nav_mode_ctrl_hold_enabled"
    private const val DEFAULT_NAV_MODE_CTRL_HOLD_ENABLED = false
    private const val KEY_NAV_MODE_DEFAULT_MAPPINGS_VERSION = "nav_mode_default_mappings_version"
    private const val CURRENT_NAV_MODE_DEFAULT_MAPPINGS_VERSION = 2
    private const val NAV_MODE_MAPPINGS_FILE_NAME = "ctrl_key_mappings.json"
    private const val KEY_NAV_MODE_MAPPINGS_UPDATED = "nav_mode_mappings_updated"
    
    /**
     * Imposta una scorciatoia del launcher per un tasto (tipo app).
     */
    fun setLauncherShortcut(context: Context, keyCode: Int, packageName: String, appName: String) {
        setLauncherCommand(
            context = context,
            keyCode = keyCode,
            commandId = "app:$packageName",
            source = CommandSourceId.Apps.storageValue,
            kind = "App",
            title = appName,
            subtitle = packageName,
            launch = CommandLaunchSpec.AppPackage(packageName)
        )
    }

    fun setQuickLauncherShortcut(context: Context, keyCode: Int) {
        setLauncherCommand(
            context = context,
            keyCode = keyCode,
            commandId = PastieraCommandSource.COMMAND_QUICK_LAUNCHER,
            source = CommandSourceId.Pastiera.storageValue,
            kind = "PastieraAction",
            title = "Pastiera QuickLauncher",
            subtitle = "Open Pastiera search",
            launch = CommandLaunchSpec.InternalAction(PastieraCommandSource.ACTION_OPEN_QUICK_LAUNCHER)
        )
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_DEFAULT_ASSIGNED, true)
            .apply()
    }

    fun setLauncherCommand(
        context: Context,
        keyCode: Int,
        commandId: String,
        source: String,
        kind: String,
        title: String,
        subtitle: String?,
        launch: CommandLaunchSpec
    ) {
        setLauncherAction(
            context,
            keyCode,
            LauncherShortcut(
                type = LauncherShortcut.TYPE_COMMAND,
                packageName = (launch as? CommandLaunchSpec.AppPackage)?.packageName,
                appName = title,
                commandId = commandId,
                commandSource = source,
                commandKind = kind,
                commandTitle = title,
                commandSubtitle = subtitle,
                commandLaunch = launch
            )
        )
    }
    
    /**
     * Imposta un'azione del launcher per un tasto (generico, estendibile).
     */
    fun setLauncherAction(context: Context, keyCode: Int, action: LauncherShortcut) {
        val prefs = getPreferences(context)
        val shortcutsJson = prefs.getString(KEY_LAUNCHER_SHORTCUTS, "{}") ?: "{}"
        
        try {
            val shortcuts = JSONObject(shortcutsJson)
            if (action.isQuickLauncherCommand()) {
                val keys = shortcuts.keys()
                val keysToRemove = mutableListOf<String>()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val shortcutObj = shortcuts.optJSONObject(key)
                    if (
                        shortcutObj?.optString("type") == LauncherShortcut.TYPE_QUICK_LAUNCHER ||
                        shortcutObj?.optString("commandId") == PastieraCommandSource.COMMAND_QUICK_LAUNCHER
                    ) {
                        keysToRemove.add(key)
                    }
                }
                keysToRemove.forEach { shortcuts.remove(it) }
            }
            shortcuts.put(keyCode.toString(), JSONObject().apply {
                put("type", action.type)
                if (action.packageName != null) put("packageName", action.packageName)
                if (action.appName != null) put("appName", action.appName)
                if (action.action != null) put("action", action.action)
                if (action.data != null) put("data", action.data)
                if (action.commandId != null) put("commandId", action.commandId)
                if (action.commandSource != null) put("source", action.commandSource)
                if (action.commandKind != null) put("kind", action.commandKind)
                if (action.commandTitle != null) put("title", action.commandTitle)
                if (action.commandSubtitle != null) put("subtitle", action.commandSubtitle)
                if (action.commandLaunch != null) put("launch", CommandJson.launchToJson(action.commandLaunch))
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
        ensureQuickLauncherDefaultShortcut(context)
        return getLauncherShortcutsRaw(context)
    }

    private fun getLauncherShortcutsRaw(context: Context): Map<Int, LauncherShortcut> {
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
                        data = shortcutObj.optString("data").takeIf { it.isNotEmpty() },
                        commandId = shortcutObj.optString("commandId").takeIf { it.isNotEmpty() },
                        commandSource = shortcutObj.optString("source").takeIf { it.isNotEmpty() },
                        commandKind = shortcutObj.optString("kind").takeIf { it.isNotEmpty() },
                        commandTitle = shortcutObj.optString("title").takeIf { it.isNotEmpty() },
                        commandSubtitle = shortcutObj.optString("subtitle").takeIf { it.isNotEmpty() },
                        commandLaunch = CommandJson.launchFromJson(shortcutObj.optJSONObject("launch"))
                            ?: legacyLaunchSpec(type, shortcutObj)
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

    fun ensureQuickLauncherDefaultShortcut(context: Context) {
        val prefs = getPreferences(context)
        if (prefs.getBoolean(KEY_QUICK_LAUNCHER_DEFAULT_ASSIGNED, false)) {
            return
        }
        val shortcuts = getLauncherShortcutsRaw(context)
        if (shortcuts.values.any { it.isQuickLauncherCommand() }) {
            prefs.edit()
                .putBoolean(KEY_QUICK_LAUNCHER_DEFAULT_ASSIGNED, true)
                .apply()
            return
        }
        if (shortcuts[KeyEvent.KEYCODE_SPACE] != null) {
            return
        }
        setQuickLauncherShortcut(context, KeyEvent.KEYCODE_SPACE)
        prefs.edit()
            .putBoolean(KEY_QUICK_LAUNCHER_DEFAULT_ASSIGNED, true)
            .apply()
    }

    fun isQuickLauncherDefaultBlockedByExistingSpaceShortcut(context: Context): Boolean {
        val prefs = getPreferences(context)
        if (prefs.getBoolean(KEY_QUICK_LAUNCHER_DEFAULT_ASSIGNED, false)) {
            return false
        }
        val shortcuts = getLauncherShortcutsRaw(context)
        if (shortcuts.values.any { it.isQuickLauncherCommand() }) {
            return false
        }
        val spaceShortcut = shortcuts[KeyEvent.KEYCODE_SPACE]
        return spaceShortcut != null && !spaceShortcut.isQuickLauncherCommand()
    }

    fun getQuickLauncherShortcutKey(context: Context): Int? {
        return getLauncherShortcuts(context)
            .entries
            .firstOrNull { it.value.isQuickLauncherCommand() }
            ?.key
    }

    private fun LauncherShortcut.isQuickLauncherCommand(): Boolean {
        return type == LauncherShortcut.TYPE_QUICK_LAUNCHER ||
            commandId == PastieraCommandSource.COMMAND_QUICK_LAUNCHER ||
            commandLaunch == CommandLaunchSpec.InternalAction(PastieraCommandSource.ACTION_OPEN_QUICK_LAUNCHER)
    }

    private fun legacyLaunchSpec(type: String, shortcutObj: JSONObject): CommandLaunchSpec? {
        return when (type) {
            LauncherShortcut.TYPE_APP -> shortcutObj.optString("packageName")
                .takeIf { it.isNotBlank() }
                ?.let { CommandLaunchSpec.AppPackage(it) }
            LauncherShortcut.TYPE_QUICK_LAUNCHER -> {
                CommandLaunchSpec.InternalAction(PastieraCommandSource.ACTION_OPEN_QUICK_LAUNCHER)
            }
            else -> null
        }
    }

    data class CommandSourceVisibility(
        val sourceId: String,
        val quickLauncherEnabled: Boolean
    )

    data class QuickLauncherCommandCustomization(
        val commandId: String,
        val favorite: Boolean = false,
        val hidden: Boolean = false,
        val customSearch: String = "",
        val favoriteOrder: Int = Int.MAX_VALUE,
        val color: Int? = null
    )

    fun getCommandSourceVisibility(context: Context): List<CommandSourceVisibility> {
        val defaults = defaultCommandSourceVisibility()
        val stored = getPreferences(context).getString(KEY_COMMAND_SURFACE_SOURCES, null) ?: return defaults
        return try {
            val json = JSONObject(stored)
            defaults.map { default ->
                val sourceJson = json.optJSONObject(default.sourceId)
                if (sourceJson == null) {
                    default
                } else {
                    CommandSourceVisibility(
                        sourceId = default.sourceId,
                        quickLauncherEnabled = sourceJson.optBoolean("quick_launcher", default.quickLauncherEnabled)
                    )
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Errore nel caricamento command source visibility", error)
            defaults
        }
    }

    fun setCommandSourceVisibility(context: Context, visibility: List<CommandSourceVisibility>) {
        val json = JSONObject()
        visibility.forEach { item ->
            json.put(item.sourceId, JSONObject().apply {
                put("quick_launcher", item.quickLauncherEnabled)
            })
        }
        getPreferences(context).edit()
            .putString(KEY_COMMAND_SURFACE_SOURCES, json.toString())
            .apply()
    }

    fun isCommandSourceEnabled(context: Context, sourceId: String, surface: CommandSurface): Boolean {
        if (surface != CommandSurface.QuickLauncher) return true
        val visibility = getCommandSourceVisibility(context).firstOrNull { it.sourceId == sourceId }
            ?: defaultCommandSourceVisibility().firstOrNull { it.sourceId == sourceId }
            ?: return false
        return visibility.quickLauncherEnabled
    }

    private fun defaultCommandSourceVisibility(): List<CommandSourceVisibility> {
        return listOf(
            CommandSourceVisibility(CommandSourceId.Apps.storageValue, quickLauncherEnabled = true),
            CommandSourceVisibility(CommandSourceId.Pastiera.storageValue, quickLauncherEnabled = false),
            CommandSourceVisibility(CommandSourceId.AppActions.storageValue, quickLauncherEnabled = false),
            CommandSourceVisibility(CommandSourceId.DeviceControl.storageValue, quickLauncherEnabled = false)
        )
    }

    fun getQuickLauncherCommandCustomizations(context: Context): Map<String, QuickLauncherCommandCustomization> {
        val stored = getPreferences(context).getString(KEY_QUICK_LAUNCHER_COMMAND_CUSTOMIZATIONS, null)
            ?: return emptyMap()
        return try {
            val json = JSONObject(stored)
            val result = mutableMapOf<String, QuickLauncherCommandCustomization>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val commandId = keys.next()
                val item = json.optJSONObject(commandId) ?: continue
                result[commandId] = QuickLauncherCommandCustomization(
                    commandId = commandId,
                    favorite = item.optBoolean("favorite", false),
                    hidden = item.optBoolean("hidden", false),
                    customSearch = item.optString("custom_search", ""),
                    favoriteOrder = item.optInt("favorite_order", Int.MAX_VALUE),
                    color = if (item.has("color")) item.optInt("color") else null
                )
            }
            result
        } catch (error: Exception) {
            Log.e(TAG, "Errore nel caricamento quick launcher command customizations", error)
            emptyMap()
        }
    }

    fun setQuickLauncherCommandCustomization(
        context: Context,
        customization: QuickLauncherCommandCustomization
    ) {
        val current = getQuickLauncherCommandCustomizations(context).toMutableMap()
        if (
            !customization.favorite &&
            !customization.hidden &&
            customization.customSearch.isBlank() &&
            customization.favoriteOrder == Int.MAX_VALUE &&
            customization.color == null
        ) {
            current.remove(customization.commandId)
        } else {
            current[customization.commandId] = customization.copy(customSearch = customization.customSearch.trim())
        }
        val json = JSONObject()
        current.values.sortedBy { it.commandId }.forEach { item ->
            json.put(item.commandId, JSONObject().apply {
                if (item.favorite) put("favorite", true)
                if (item.hidden) put("hidden", true)
                if (item.customSearch.isNotBlank()) put("custom_search", item.customSearch)
                if (item.favoriteOrder != Int.MAX_VALUE) put("favorite_order", item.favoriteOrder)
                item.color?.let { put("color", it) }
            })
        }
        getPreferences(context).edit()
            .putString(KEY_QUICK_LAUNCHER_COMMAND_CUSTOMIZATIONS, json.toString())
            .apply()
    }

    fun getQuickLauncherHighlightFavorites(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_QUICK_LAUNCHER_HIGHLIGHT_FAVORITES, true)
    }

    fun setQuickLauncherHighlightFavorites(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_HIGHLIGHT_FAVORITES, enabled)
            .apply()
    }

    fun getQuickLauncherFavoriteColor(context: Context): Int {
        return getPreferences(context).getInt(KEY_QUICK_LAUNCHER_FAVORITE_COLOR, DEFAULT_QUICK_LAUNCHER_FAVORITE_COLOR)
    }

    fun setQuickLauncherFavoriteColor(context: Context, color: Int) {
        getPreferences(context).edit()
            .putInt(KEY_QUICK_LAUNCHER_FAVORITE_COLOR, color)
            .apply()
    }

    fun getQuickLauncherIconColors(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_QUICK_LAUNCHER_ICON_COLORS, false)
    }

    fun setQuickLauncherIconColors(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_ICON_COLORS, enabled)
            .apply()
    }

    fun getQuickLauncherShowAliasFirst(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_QUICK_LAUNCHER_SHOW_ALIAS_FIRST, true)
    }

    fun setQuickLauncherShowAliasFirst(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_SHOW_ALIAS_FIRST, enabled)
            .apply()
    }

    fun getQuickLauncherStaticTopHighlight(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT, false)
    }

    fun setQuickLauncherStaticTopHighlight(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT, enabled)
            .apply()
    }

    fun getQuickLauncherStaticTopHighlightColor(context: Context): Int {
        return getPreferences(context).getInt(
            KEY_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT_COLOR,
            DEFAULT_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT_COLOR
        )
    }

    fun setQuickLauncherStaticTopHighlightColor(context: Context, color: Int) {
        getPreferences(context).edit()
            .putInt(KEY_QUICK_LAUNCHER_STATIC_TOP_HIGHLIGHT_COLOR, color)
            .apply()
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

    fun getQuickLauncherAutoStartSingle(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_QUICK_LAUNCHER_AUTO_START_SINGLE,
            DEFAULT_QUICK_LAUNCHER_AUTO_START_SINGLE
        )
    }

    fun setQuickLauncherAutoStartSingle(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_AUTO_START_SINGLE, enabled)
            .apply()
    }

    fun getQuickLauncherLimitResults(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_QUICK_LAUNCHER_LIMIT_RESULTS,
            DEFAULT_QUICK_LAUNCHER_LIMIT_RESULTS
        )
    }

    fun setQuickLauncherLimitResults(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_LIMIT_RESULTS, enabled)
            .apply()
    }

    fun getQuickLauncherTextFieldShortcuts(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_QUICK_LAUNCHER_TEXT_FIELD_SHORTCUTS,
            DEFAULT_QUICK_LAUNCHER_TEXT_FIELD_SHORTCUTS
        )
    }

    fun setQuickLauncherTextFieldShortcuts(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_TEXT_FIELD_SHORTCUTS, enabled)
            .apply()
    }

    fun getQuickLauncherRespectKeyboardLayout(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_QUICK_LAUNCHER_RESPECT_KEYBOARD_LAYOUT,
            DEFAULT_QUICK_LAUNCHER_RESPECT_KEYBOARD_LAYOUT
        )
    }

    fun setQuickLauncherRespectKeyboardLayout(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_RESPECT_KEYBOARD_LAYOUT, enabled)
            .apply()
    }

    fun getQuickLauncherTypoTolerantRanking(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_QUICK_LAUNCHER_TYPO_TOLERANT_RANKING,
            DEFAULT_QUICK_LAUNCHER_TYPO_TOLERANT_RANKING
        )
    }

    fun setQuickLauncherTypoTolerantRanking(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_TYPO_TOLERANT_RANKING, enabled)
            .apply()
    }

    fun getQuickLauncherWidthPercent(context: Context): Int {
        return getPreferences(context)
            .getInt(KEY_QUICK_LAUNCHER_WIDTH_PERCENT, DEFAULT_QUICK_LAUNCHER_WIDTH_PERCENT)
            .coerceIn(50, 100)
    }

    fun setQuickLauncherWidthPercent(context: Context, percent: Int) {
        getPreferences(context).edit()
            .putInt(KEY_QUICK_LAUNCHER_WIDTH_PERCENT, percent.coerceIn(50, 100))
            .apply()
    }

    fun getQuickLauncherPillMode(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_QUICK_LAUNCHER_PILL_MODE,
            DEFAULT_QUICK_LAUNCHER_PILL_MODE
        )
    }

    fun setQuickLauncherPillMode(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_QUICK_LAUNCHER_PILL_MODE, enabled)
            .apply()
    }

    fun getQuickLauncherBehavior(context: Context): String {
        val value = getPreferences(context).getString(
            KEY_QUICK_LAUNCHER_BEHAVIOR,
            QUICK_LAUNCHER_BEHAVIOR_PASTIERA
        ) ?: QUICK_LAUNCHER_BEHAVIOR_PASTIERA
        return normalizeQuickLauncherBehavior(value)
    }

    fun setQuickLauncherBehavior(context: Context, behavior: String) {
        getPreferences(context).edit()
            .putString(KEY_QUICK_LAUNCHER_BEHAVIOR, normalizeQuickLauncherBehavior(behavior))
            .apply()
    }

    private fun normalizeQuickLauncherBehavior(behavior: String): String {
        return when (behavior.trim().lowercase()) {
            QUICK_LAUNCHER_BEHAVIOR_NIAGARA -> QUICK_LAUNCHER_BEHAVIOR_NIAGARA
            else -> QUICK_LAUNCHER_BEHAVIOR_PASTIERA
        }
    }
    
    // Power Shortcuts settings
    private const val KEY_POWER_SHORTCUTS_ENABLED = "power_shortcuts_enabled"
    private const val DEFAULT_POWER_SHORTCUTS_ENABLED = true
    
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
     * Returns whether holding Ctrl should use Nav Mode mappings in text fields.
     */
    fun getNavModeCtrlHoldEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_NAV_MODE_CTRL_HOLD_ENABLED, DEFAULT_NAV_MODE_CTRL_HOLD_ENABLED)
    }

    /**
     * Sets whether holding Ctrl should use Nav Mode mappings in text fields.
     */
    fun setNavModeCtrlHoldEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_NAV_MODE_CTRL_HOLD_ENABLED, enabled)
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
            migrateNavModeMappingsFileIfNeeded(context)
            return // File already exists, don't overwrite
        }
        
        try {
            val inputStream: InputStream = context.assets.open("common/ctrl/$NAV_MODE_MAPPINGS_FILE_NAME")
            val outputStream = FileOutputStream(mappingsFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            migrateNavModeMappingsFileIfNeeded(context)
            Log.d(TAG, "Nav mode mappings file initialized from assets")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing nav mode mappings file", e)
        }
    }

    private fun migrateNavModeMappingsFileIfNeeded(context: Context) {
        val prefs = getPreferences(context)
        if (prefs.getInt(KEY_NAV_MODE_DEFAULT_MAPPINGS_VERSION, 1) >= CURRENT_NAV_MODE_DEFAULT_MAPPINGS_VERSION) {
            return
        }

        try {
            val mappingsFile = getNavModeMappingsFile(context)
            if (!mappingsFile.exists()) {
                return
            }

            val jsonObject = JSONObject(mappingsFile.readText())
            val mappingsObject = jsonObject.getJSONObject("mappings")
            val defaultWordMappings = mapOf(
                "KEYCODE_N" to "move_word_left",
                "KEYCODE_M" to "move_word_right",
                "KEYCODE_U" to "expand_selection_word_left",
                "KEYCODE_I" to "expand_selection_word_right"
            )

            defaultWordMappings.forEach { (keyName, action) ->
                val existing = mappingsObject.optJSONObject(keyName)
                if (existing == null || existing.optString("type") == "none") {
                    mappingsObject.put(
                        keyName,
                        JSONObject().apply {
                            put("type", "action")
                            put("action", action)
                        }
                    )
                }
            }

            mappingsFile.writeText(jsonObject.toString())
            prefs.edit()
                .putInt(KEY_NAV_MODE_DEFAULT_MAPPINGS_VERSION, CURRENT_NAV_MODE_DEFAULT_MAPPINGS_VERSION)
                .putLong(KEY_NAV_MODE_MAPPINGS_UPDATED, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "Nav mode mappings migrated to version $CURRENT_NAV_MODE_DEFAULT_MAPPINGS_VERSION")
        } catch (e: Exception) {
            Log.e(TAG, "Error migrating nav mode mappings file", e)
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
                        "native_ctrl" -> { /* type is already set */ }
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

    /**
     * Returns whether keyboard layout should be resolved automatically from subtype/locale mapping.
     * If false, the manually selected layout is used across all locales.
     */
    fun isKeyboardLayoutAutoByLocale(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_KEYBOARD_LAYOUT_AUTO_BY_LOCALE,
            DEFAULT_KEYBOARD_LAYOUT_AUTO_BY_LOCALE
        )
    }

    /**
     * Enables/disables automatic keyboard layout resolution by locale mapping.
     */
    fun setKeyboardLayoutAutoByLocale(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_KEYBOARD_LAYOUT_AUTO_BY_LOCALE, enabled)
            .apply()
    }

    fun notifyKeyboardLayoutAutoMappingUpdated(context: Context) {
        getPreferences(context).edit()
            .putLong(KEY_KEYBOARD_LAYOUT_AUTO_MAPPING_UPDATED, System.currentTimeMillis())
            .apply()
    }

    /**
     * Returns the manual physical keyboard profile override used for device-specific mappings.
     * Supported values: auto, key2, Q25, titan, titan2, titan2elite_qwerty, mp01.
     */
    fun getPhysicalKeyboardProfileOverride(context: Context): String {
        val value = getPreferences(context).getString(
            KEY_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE,
            DEFAULT_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE
        ) ?: DEFAULT_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE
        return normalizePhysicalKeyboardProfileOverride(value)
    }

    /**
     * Sets the manual physical keyboard profile override used for device-specific mappings.
     * Invalid values are normalized to "auto".
     */
    fun setPhysicalKeyboardProfileOverride(context: Context, profile: String) {
        val normalized = normalizePhysicalKeyboardProfileOverride(profile)
        getPreferences(context).edit()
            .putString(KEY_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE, normalized)
            .apply()
    }

    /**
     * Returns the symbol used for dedicated hardware currency keys.
     */
    fun getPhysicalKeyboardCurrencySymbol(context: Context): String {
        val value = getPreferences(context).getString(
            KEY_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL,
            DEFAULT_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL
        ) ?: DEFAULT_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL
        return normalizePhysicalKeyboardCurrencySymbol(value)
    }

    /**
     * Sets the symbol used for dedicated hardware currency keys.
     */
    fun setPhysicalKeyboardCurrencySymbol(context: Context, symbol: String) {
        getPreferences(context).edit()
            .putString(KEY_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL, normalizePhysicalKeyboardCurrencySymbol(symbol))
            .apply()
    }

    /**
     * Returns whether Alt+Shift shortcut for keyboard layout cycling is enabled.
     */
    fun isAltShiftLayoutSwitchEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_ALT_SHIFT_LAYOUT_SWITCH,
            DEFAULT_ALT_SHIFT_LAYOUT_SWITCH
        )
    }

    /**
     * Enables/disables Alt+Shift shortcut for keyboard layout cycling.
     */
    fun setAltShiftLayoutSwitchEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_ALT_SHIFT_LAYOUT_SWITCH, enabled)
            .apply()
    }

    /**
     * Returns whether Ctrl+Space shortcut for keyboard layout cycling is enabled.
     */
    fun isCtrlSpaceLayoutSwitchEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_CTRL_SPACE_LAYOUT_SWITCH,
            DEFAULT_CTRL_SPACE_LAYOUT_SWITCH
        )
    }

    /**
     * Enables/disables Ctrl+Space shortcut for keyboard layout cycling.
     */
    fun setCtrlSpaceLayoutSwitchEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_CTRL_SPACE_LAYOUT_SWITCH, enabled)
            .apply()
    }

    /**
     * Returns whether toast notification on layout switch is enabled.
     */
    fun isToastOnLayoutSwitchEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_TOAST_ON_LAYOUT_SWITCH,
            DEFAULT_TOAST_ON_LAYOUT_SWITCH
        )
    }

    /**
     * Enables/disables toast notification on layout switch.
     */
    fun setToastOnLayoutSwitchEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_TOAST_ON_LAYOUT_SWITCH, enabled)
            .apply()
    }

    private fun normalizePhysicalKeyboardProfileOverride(profile: String?): String {
        val normalized = profile?.trim().orEmpty()
        return when {
            normalized.equals("auto", ignoreCase = true) -> "auto"
            normalized.equals("key2", ignoreCase = true) -> "key2"
            normalized.equals("q25", ignoreCase = true) -> "Q25"
            normalized.equals("titan", ignoreCase = true) -> "titan"
            normalized.equals("titan2", ignoreCase = true) -> "titan2"
            normalized.equals("titan2elite_qwerty", ignoreCase = true) -> "titan2elite_qwerty"
            normalized.equals("mp01", ignoreCase = true) -> "mp01"
            else -> DEFAULT_PHYSICAL_KEYBOARD_PROFILE_OVERRIDE
        }
    }

    private fun normalizePhysicalKeyboardCurrencySymbol(symbol: String?): String {
        val normalized = symbol?.trim().orEmpty()
        return if (normalized in physicalKeyboardCurrencySymbols()) {
            normalized
        } else {
            DEFAULT_PHYSICAL_KEYBOARD_CURRENCY_SYMBOL
        }
    }

    fun physicalKeyboardCurrencySymbols(): List<String> = listOf("€", "$", "£", "¥", "₹", "₽", "₿", "¤")

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
     * Always loops: even with a single entry we "cycle" back to it, so long press
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
            val emojiEnabled = jsonObject.optBoolean("emojiEnabled", true)
            val symbolsEnabled = jsonObject.optBoolean("symbolsEnabled", true)
            val clipboardEnabled = jsonObject.optBoolean("clipboardEnabled", false)
            val emojiPickerEnabled = jsonObject.optBoolean("emojiPickerEnabled", false)
            val legacyEmojiFirst = jsonObject.optBoolean("emojiFirst", true)

            val parsedOrder = if (jsonObject.has("symPageOrder")) {
                val orderArray = jsonObject.optJSONArray("symPageOrder")
                val collected = mutableListOf<String>()
                if (orderArray != null) {
                    for (i in 0 until orderArray.length()) {
                        val pageId = orderArray.optString(i, "").trim()
                        if (pageId.isNotEmpty()) {
                            collected.add(pageId)
                        }
                    }
                }
                collected
            } else {
                // Legacy migration from emojiFirst behavior.
                val cyclePages = mutableListOf(
                    SymPagesConfig.PAGE_EMOJI,
                    SymPagesConfig.PAGE_SYMBOLS,
                    SymPagesConfig.PAGE_CLIPBOARD
                )
                if (!legacyEmojiFirst) {
                    cyclePages.reverse()
                }
                cyclePages + SymPagesConfig.PAGE_EMOJI_PICKER
            }

            SymPagesConfig(
                emojiEnabled = emojiEnabled,
                symbolsEnabled = symbolsEnabled,
                clipboardEnabled = clipboardEnabled,
                emojiPickerEnabled = emojiPickerEnabled,
                symPageOrder = parsedOrder
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
                put("clipboardEnabled", config.clipboardEnabled)
                put("emojiPickerEnabled", config.emojiPickerEnabled)
                // Keep legacy field for backward compatibility with older builds.
                put("emojiFirst", config.prefersEmojiLongPressLayer())
                val orderArray = org.json.JSONArray()
                config.normalizedOrder().forEach { orderArray.put(it) }
                put("symPageOrder", orderArray)
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

    fun getSymAutoCloseOnTouch(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_SYM_AUTO_CLOSE_ON_TOUCH,
            DEFAULT_SYM_AUTO_CLOSE_ON_TOUCH
        )
    }

    fun setSymAutoCloseOnTouch(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_SYM_AUTO_CLOSE_ON_TOUCH, enabled)
            .apply()
    }

    fun getEmojiPickerExpandedHeight(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_EMOJI_PICKER_EXPANDED_HEIGHT,
            DEFAULT_EMOJI_PICKER_EXPANDED_HEIGHT
        )
    }

    fun setEmojiPickerExpandedHeight(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_EMOJI_PICKER_EXPANDED_HEIGHT, enabled)
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
            .putString(KEY_LAST_SEEN_WHATS_NEW_VERSION, BuildConfig.VERSION_NAME)
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

    fun shouldShowWhatsNew(context: Context, currentVersion: String): Boolean {
        if (!isTutorialCompleted(context)) return false
        val normalizedCurrent = currentVersion.trim()
        if (normalizedCurrent.isBlank()) return false

        val lastSeen = getPreferences(context).getString(KEY_LAST_SEEN_WHATS_NEW_VERSION, null)
        return lastSeen != normalizedCurrent
    }

    fun getLastSeenWhatsNewVersion(context: Context): String? {
        return getPreferences(context)
            .getString(KEY_LAST_SEEN_WHATS_NEW_VERSION, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun markWhatsNewSeen(context: Context, version: String) {
        val normalizedVersion = version.trim()
        if (normalizedVersion.isBlank()) return

        getPreferences(context).edit()
            .putString(KEY_LAST_SEEN_WHATS_NEW_VERSION, normalizedVersion)
            .apply()
    }

    /**
     * Returns whether clipboard history is enabled.
     * @param context The context
     * @return true if clipboard history is enabled, false otherwise
     */
    fun getClipboardHistoryEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_CLIPBOARD_HISTORY_ENABLED, DEFAULT_CLIPBOARD_HISTORY_ENABLED)
    }

    /**
     * Sets whether clipboard history is enabled.
     * @param context The context
     * @param enabled Whether to enable clipboard history
     */
    fun setClipboardHistoryEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_CLIPBOARD_HISTORY_ENABLED, enabled)
            .apply()
    }

    /**
     * Returns the clipboard retention time in minutes.
     * Entries older than this will be automatically deleted (unless pinned).
     * @param context The context
     * @return Retention time in minutes (e.g. 120 = 2 hours)
     */
    fun getClipboardRetentionTime(context: Context): Long {
        return getPreferences(context).getLong(KEY_CLIPBOARD_RETENTION_TIME, DEFAULT_CLIPBOARD_RETENTION_TIME)
    }

    /**
     * Sets the clipboard retention time in minutes.
     * @param context The context
     * @param minutes Retention time in minutes (e.g. 120 = 2 hours)
     */
    fun setClipboardRetentionTime(context: Context, minutes: Long) {
        getPreferences(context).edit()
            .putLong(KEY_CLIPBOARD_RETENTION_TIME, minutes)
            .apply()
    }

    /**
     * Returns whether trackpad gesture suggestions are enabled.
     * @param context The context
     * @return Whether trackpad gestures are enabled
     */
    fun getTrackpadGesturesEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_TRACKPAD_GESTURES_ENABLED, DEFAULT_TRACKPAD_GESTURES_ENABLED)
    }

    /**
     * Sets whether trackpad gesture suggestions are enabled.
     * @param context The context
     * @param enabled Whether to enable trackpad gestures
     */
    fun setTrackpadGesturesEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_TRACKPAD_GESTURES_ENABLED, enabled)
            .commit()  // Use commit() instead of apply() to ensure synchronous write
    }

    fun getTrackpadGestureAddWordEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_TRACKPAD_GESTURE_ADD_WORD_ENABLED,
            DEFAULT_TRACKPAD_GESTURE_ADD_WORD_ENABLED
        )
    }

    fun setTrackpadGestureAddWordEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_TRACKPAD_GESTURE_ADD_WORD_ENABLED, enabled)
            .commit()
    }

    /**
     * Returns the swipe threshold for trackpad gestures.
     */
    fun getTrackpadSwipeThreshold(context: Context): Float {
        return getPreferences(context).getFloat(KEY_TRACKPAD_SWIPE_THRESHOLD, DEFAULT_TRACKPAD_SWIPE_THRESHOLD)
            .coerceIn(MIN_TRACKPAD_SWIPE_THRESHOLD, MAX_TRACKPAD_SWIPE_THRESHOLD)
    }

    /**
     * Sets the swipe threshold for trackpad gestures.
     * Value is clamped to allowed range.
     */
    fun setTrackpadSwipeThreshold(context: Context, threshold: Float) {
        val clamped = threshold.coerceIn(MIN_TRACKPAD_SWIPE_THRESHOLD, MAX_TRACKPAD_SWIPE_THRESHOLD)
        getPreferences(context).edit()
            .putFloat(KEY_TRACKPAD_SWIPE_THRESHOLD, clamped)
            .commit()  // Use commit() instead of apply() to ensure synchronous write
    }

    fun getMinTrackpadSwipeThreshold(): Float = MIN_TRACKPAD_SWIPE_THRESHOLD
    fun getMaxTrackpadSwipeThreshold(): Float = MAX_TRACKPAD_SWIPE_THRESHOLD
    fun getDefaultTrackpadSwipeThreshold(): Float = DEFAULT_TRACKPAD_SWIPE_THRESHOLD

    fun getTrackpadProvider(context: Context): String {
        val value = getPreferences(context).getString(KEY_TRACKPAD_PROVIDER, DEFAULT_TRACKPAD_PROVIDER).orEmpty()
        return if (TRACKPAD_PROVIDER_VALUES.contains(value)) value else DEFAULT_TRACKPAD_PROVIDER
    }

    fun setTrackpadProvider(context: Context, provider: String) {
        val normalized = if (TRACKPAD_PROVIDER_VALUES.contains(provider)) provider else DEFAULT_TRACKPAD_PROVIDER
        getPreferences(context).edit()
            .putString(KEY_TRACKPAD_PROVIDER, normalized)
            .commit()
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

    private fun saveStaticVariationRows(
        context: Context,
        staticVariations: List<String>,
        staticVariationsShift: List<String>,
        staticVariationsAlt: List<String>
    ) {
        try {
            val currentJson = loadCurrentJson(context)
            val jsonObject = if (currentJson != null) {
                JSONObject(currentJson.toString())
            } else {
                JSONObject()
            }

            val baseArray = org.json.JSONArray()
            staticVariations.forEach { baseArray.put(it) }
            jsonObject.put("staticVariations", baseArray)

            val shiftArray = org.json.JSONArray()
            staticVariationsShift.forEach { shiftArray.put(it) }
            jsonObject.put("staticVariationsShift", shiftArray)

            val altArray = org.json.JSONArray()
            staticVariationsAlt.forEach { altArray.put(it) }
            jsonObject.put("staticVariationsAlt", altArray)

            FileOutputStream(getVariationsFile(context)).use { outputStream ->
                outputStream.write(jsonObject.toString(2).toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving static variation rows", e)
        }
    }
    
    /**
     * Saves variations to variations.json file in filesDir.
     */
    fun saveVariations(
        context: Context,
        variations: Map<String, List<String>>,
        staticVariations: List<String>? = null,
        staticVariationsShift: List<String>? = null,
        staticVariationsAlt: List<String>? = null
    ) {
        try {
            val variationsObject = JSONObject()
            for ((letter, chars) in variations) {
                val variationsArray = org.json.JSONArray()
                for (char in chars) {
                    variationsArray.put(char)
                }
                variationsObject.put(letter, variationsArray)
            }
            
            val currentJson = loadCurrentJson(context)
            val jsonObject = if (currentJson != null) {
                JSONObject(currentJson.toString())
            } else {
                JSONObject()
            }
            jsonObject.put("variations", variationsObject)

            // Preserve/update staticVariations
            if (staticVariations != null) {
                val staticArray = org.json.JSONArray()
                staticVariations.forEach { staticArray.put(it) }
                jsonObject.put("staticVariations", staticArray)
            }

            // Preserve/update staticVariationsShift
            if (staticVariationsShift != null) {
                val staticArray = org.json.JSONArray()
                staticVariationsShift.forEach { staticArray.put(it) }
                jsonObject.put("staticVariationsShift", staticArray)
            }

            // Preserve/update staticVariationsAlt
            if (staticVariationsAlt != null) {
                val staticArray = org.json.JSONArray()
                staticVariationsAlt.forEach { staticArray.put(it) }
                jsonObject.put("staticVariationsAlt", staticArray)
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

    fun saveStaticVariationBasePreset(context: Context, staticVariations: List<String>) {
        try {
            val jsonObject = loadCurrentJson(context) ?: JSONObject()
            val staticArray = org.json.JSONArray()
            staticVariations.forEach { staticArray.put(it) }
            jsonObject.put("staticVariations", staticArray)

            FileOutputStream(getVariationsFile(context)).use { outputStream ->
                outputStream.write(jsonObject.toString(2).toByteArray(Charsets.UTF_8))
            }

            notifyVariationsUpdated(context)
            Log.d(TAG, "Static variation base preset saved to ${getVariationsFile(context).absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving static variation base preset", e)
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

    // Custom Input Styles (Additional Subtypes)
    private const val KEY_CUSTOM_INPUT_STYLES = "custom_input_styles"
    private const val KEY_INPUT_STYLE_SUGGESTION_LOCALES = "input_style_suggestion_locales"
    private const val KEY_HIDDEN_SYSTEM_INPUT_STYLES = "hidden_system_input_styles"

    /**
     * Gets the custom input styles preference string.
     * Returns default from predefined_subtypes resource if not set.
     */
    fun getCustomInputStyles(context: Context): String {
        val prefs = getPreferences(context)
        val custom = prefs.getString(KEY_CUSTOM_INPUT_STYLES, null)
        if (custom != null) {
            return custom
        }

        // Load default from predefined_subtypes resource
        return try {
            val arrayResId = context.resources.getIdentifier(
                "predefined_subtypes",
                "array",
                context.packageName
            )
            if (arrayResId == 0) {
                return ""
            }
            val array = context.resources.getStringArray(arrayResId)
            array.joinToString(";")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading predefined subtypes", e)
            ""
        }
    }

    /**
     * Sets the custom input styles preference string.
     */
    fun setCustomInputStyles(context: Context, stylesString: String) {
        getPreferences(context).edit()
            .putString(KEY_CUSTOM_INPUT_STYLES, stylesString)
            .apply()
    }

    fun isSystemInputStyleHidden(context: Context, locale: String, layout: String): Boolean {
        return hiddenSystemInputStyleKeys(context).contains(inputStyleKey(locale, layout))
    }

    fun hideSystemInputStyle(context: Context, locale: String, layout: String) {
        val updated = hiddenSystemInputStyleKeys(context).toMutableSet()
        updated.add(inputStyleKey(locale, layout))
        saveHiddenSystemInputStyleKeys(context, updated)
    }

    fun showSystemInputStyle(context: Context, locale: String, layout: String) {
        val updated = hiddenSystemInputStyleKeys(context).toMutableSet()
        updated.remove(inputStyleKey(locale, layout))
        saveHiddenSystemInputStyleKeys(context, updated)
    }

    fun getAdditionalSuggestionLocalesForInputStyle(
        context: Context,
        locale: String,
        layout: String
    ): List<String> {
        val jsonString = getPreferences(context).getString(KEY_INPUT_STYLE_SUGGESTION_LOCALES, null)
            ?: return emptyList()
        return try {
            val root = org.json.JSONObject(jsonString)
            val array = suggestionLocalesArrayForInputStyle(root, locale, layout) ?: return emptyList()
            suggestionLocalesFromArray(array)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing input style suggestion locales", e)
            emptyList()
        }
    }

    private fun suggestionLocalesArrayForInputStyle(
        root: org.json.JSONObject,
        locale: String,
        layout: String
    ): org.json.JSONArray? {
        val normalizedLocale = normalizeSuggestionLocaleTag(locale)
        val language = normalizedLocale.substringBefore("-")
        val exactKey = inputStyleSuggestionKey(locale, layout)
        val languageLayoutKey = inputStyleKey(language, layout)

        root.optJSONArray(exactKey)?.let { return it }
        root.optJSONArray(languageLayoutKey)?.let { return it }

        legacySuggestionLayoutAliases(layout).forEach { legacyLayout ->
            root.optJSONArray(inputStyleKey(normalizedLocale, legacyLayout))?.let { return it }
            root.optJSONArray(inputStyleKey(language, legacyLayout))?.let { return it }
        }

        return null
    }

    private fun legacySuggestionLayoutAliases(layout: String): List<String> {
        return when (layout.trim()) {
            "qwertz" -> listOf("german_multitap_qwertz")
            else -> emptyList()
        }
    }

    private fun suggestionLocalesFromArray(array: org.json.JSONArray): List<String> {
        return buildList {
            for (i in 0 until array.length()) {
                val tag = array.optString(i).trim()
                if (tag.isNotBlank()) {
                    add(normalizeSuggestionLocaleTag(tag))
                }
            }
        }.distinct()
    }

    fun setAdditionalSuggestionLocalesForInputStyle(
        context: Context,
        locale: String,
        layout: String,
        locales: List<String>
    ) {
        val prefs = getPreferences(context)
        val root = try {
            org.json.JSONObject(prefs.getString(KEY_INPUT_STYLE_SUGGESTION_LOCALES, null) ?: "{}")
        } catch (_: Exception) {
            org.json.JSONObject()
        }
        val key = inputStyleSuggestionKey(locale, layout)
        val normalized = locales
            .map { normalizeSuggestionLocaleTag(it) }
            .filter { it.isNotBlank() }
            .distinct()
        if (normalized.isEmpty()) {
            root.remove(key)
        } else {
            val array = org.json.JSONArray()
            normalized.forEach { array.put(it) }
            root.put(key, array)
        }
        prefs.edit()
            .putString(KEY_INPUT_STYLE_SUGGESTION_LOCALES, root.toString())
            .apply()
    }

    fun removeAdditionalSuggestionLocalesForInputStyle(
        context: Context,
        locale: String,
        layout: String
    ) {
        val prefs = getPreferences(context)
        val root = try {
            org.json.JSONObject(prefs.getString(KEY_INPUT_STYLE_SUGGESTION_LOCALES, null) ?: "{}")
        } catch (_: Exception) {
            return
        }
        root.remove(inputStyleSuggestionKey(locale, layout))
        prefs.edit()
            .putString(KEY_INPUT_STYLE_SUGGESTION_LOCALES, root.toString())
            .apply()
    }

    private fun inputStyleSuggestionKey(locale: String, layout: String): String {
        return inputStyleKey(locale, layout)
    }

    private fun inputStyleKey(locale: String, layout: String): String {
        return "${normalizeSuggestionLocaleTag(locale)}:${layout.trim()}"
    }

    private fun normalizeSuggestionLocaleTag(locale: String): String {
        return locale.trim().replace('_', '-')
    }

    private fun hiddenSystemInputStyleKeys(context: Context): Set<String> {
        val jsonString = getPreferences(context).getString(KEY_HIDDEN_SYSTEM_INPUT_STYLES, null)
            ?: return emptySet()
        return try {
            val array = org.json.JSONArray(jsonString)
            buildSet {
                for (i in 0 until array.length()) {
                    val key = array.optString(i).trim()
                    if (key.isNotBlank()) add(key)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing hidden system input styles", e)
            emptySet()
        }
    }

    private fun saveHiddenSystemInputStyleKeys(context: Context, keys: Set<String>) {
        val array = org.json.JSONArray()
        keys.sorted().forEach { array.put(it) }
        getPreferences(context).edit()
            .putString(KEY_HIDDEN_SYSTEM_INPUT_STYLES, array.toString())
            .apply()
    }
    
    // ========================
    // Status Bar Button Slots
    // ========================
    
    /**
     * Gets the button assigned to the left slot.
     */
    fun getStatusBarSlotLeft(context: Context): String {
        return getPreferences(context).getString(KEY_STATUS_BAR_SLOT_LEFT, DEFAULT_SLOT_LEFT)
            ?: DEFAULT_SLOT_LEFT
    }

    data class StatusBarSlotDefaults(
        val left: String,
        val right1: String,
        val right2: String
    )

    /**
     * Returns the default slot assignment for the status bar.
     */
    fun getDefaultStatusBarSlots(): StatusBarSlotDefaults {
        return StatusBarSlotDefaults(
            left = DEFAULT_SLOT_LEFT,
            right1 = DEFAULT_SLOT_RIGHT_1,
            right2 = DEFAULT_SLOT_RIGHT_2
        )
    }

    fun getDefaultStatusBarSlotsLeft(): List<String> = listOf(DEFAULT_SLOT_LEFT)

    fun getDefaultStatusBarSlotsRight(): List<String> = listOf(DEFAULT_SLOT_RIGHT_1, DEFAULT_SLOT_RIGHT_2)

    /**
     * Resets status bar slots to the defaults and returns the applied values.
     */
    fun resetStatusBarSlotsToDefault(context: Context): StatusBarSlotDefaults {
        val defaults = getDefaultStatusBarSlots()
        setStatusBarSlotLeft(context, defaults.left)
        setStatusBarSlotRight1(context, defaults.right1)
        setStatusBarSlotRight2(context, defaults.right2)
        setStatusBarSlotsLeft(context, getDefaultStatusBarSlotsLeft())
        setStatusBarSlotsRight(context, getDefaultStatusBarSlotsRight())
        setStatusBarVariationsVisible(context, DEFAULT_STATUS_BAR_VARIATIONS_VISIBLE)
        setDynamicVariationBarSlotCount(context, DEFAULT_DYNAMIC_VARIATION_BAR_SLOT_COUNT)
        setDynamicVariationBarResizeToContent(context, DEFAULT_DYNAMIC_VARIATION_BAR_RESIZE_TO_CONTENT)
        return defaults
    }

    fun getStatusBarSlotsLeft(context: Context): List<String> {
        return getStatusBarSlotsList(
            context = context,
            key = KEY_STATUS_BAR_SLOTS_LEFT,
            fallback = listOf(getStatusBarSlotLeft(context))
        )
    }

    fun setStatusBarSlotsLeft(context: Context, buttonIds: List<String>) {
        val normalized = normalizeStatusBarSlots(buttonIds)
        getPreferences(context).edit()
            .putString(KEY_STATUS_BAR_SLOTS_LEFT, statusBarSlotsToJson(normalized))
            .putString(KEY_STATUS_BAR_SLOT_LEFT, normalized.firstOrNull() ?: STATUS_BAR_BUTTON_NONE)
            .apply()
    }

    fun getStatusBarSlotsRight(context: Context): List<String> {
        return getStatusBarSlotsList(
            context = context,
            key = KEY_STATUS_BAR_SLOTS_RIGHT,
            fallback = listOf(getStatusBarSlotRight1(context), getStatusBarSlotRight2(context))
        )
    }

    fun setStatusBarSlotsRight(context: Context, buttonIds: List<String>) {
        val normalized = normalizeStatusBarSlots(buttonIds)
        getPreferences(context).edit()
            .putString(KEY_STATUS_BAR_SLOTS_RIGHT, statusBarSlotsToJson(normalized))
            .putString(KEY_STATUS_BAR_SLOT_RIGHT_1, normalized.getOrNull(0) ?: STATUS_BAR_BUTTON_NONE)
            .putString(KEY_STATUS_BAR_SLOT_RIGHT_2, normalized.getOrNull(1) ?: STATUS_BAR_BUTTON_NONE)
            .apply()
    }
    
    /**
     * Sets the button for the left slot.
     */
    fun setStatusBarSlotLeft(context: Context, buttonId: String) {
        getPreferences(context).edit()
            .putString(KEY_STATUS_BAR_SLOT_LEFT, buttonId)
            .apply()
    }
    
    /**
     * Gets the button assigned to the first right slot.
     */
    fun getStatusBarSlotRight1(context: Context): String {
        return getPreferences(context).getString(KEY_STATUS_BAR_SLOT_RIGHT_1, DEFAULT_SLOT_RIGHT_1)
            ?: DEFAULT_SLOT_RIGHT_1
    }
    
    /**
     * Sets the button for the first right slot.
     */
    fun setStatusBarSlotRight1(context: Context, buttonId: String) {
        getPreferences(context).edit()
            .putString(KEY_STATUS_BAR_SLOT_RIGHT_1, buttonId)
            .apply()
    }
    
    /**
     * Gets the button assigned to the second right slot.
     */
    fun getStatusBarSlotRight2(context: Context): String {
        return getPreferences(context).getString(KEY_STATUS_BAR_SLOT_RIGHT_2, DEFAULT_SLOT_RIGHT_2)
            ?: DEFAULT_SLOT_RIGHT_2
    }
    
    /**
     * Sets the button for the second right slot.
     */
    fun setStatusBarSlotRight2(context: Context, buttonId: String) {
        getPreferences(context).edit()
            .putString(KEY_STATUS_BAR_SLOT_RIGHT_2, buttonId)
            .apply()
    }

    private fun getStatusBarSlotsList(
        context: Context,
        key: String,
        fallback: List<String>
    ): List<String> {
        val stored = getPreferences(context).getString(key, null)
        if (!stored.isNullOrBlank()) {
            runCatching {
                val array = JSONArray(stored)
                val parsed = buildList {
                    for (index in 0 until array.length()) {
                        add(array.optString(index, STATUS_BAR_BUTTON_NONE))
                    }
                }
                return normalizeStatusBarSlots(parsed)
            }
        }
        return normalizeStatusBarSlots(fallback)
    }

    private fun normalizeStatusBarSlots(buttonIds: List<String>): List<String> {
        val available = getAvailableStatusBarButtons().toSet()
        return buttonIds.map { buttonId ->
            if (buttonId in available) buttonId else STATUS_BAR_BUTTON_NONE
        }
    }

    private fun statusBarSlotsToJson(buttonIds: List<String>): String {
        val array = JSONArray()
        buttonIds.forEach { array.put(it) }
        return array.toString()
    }

    fun getStatusBarVariationsVisible(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_STATUS_BAR_VARIATIONS_VISIBLE,
            DEFAULT_STATUS_BAR_VARIATIONS_VISIBLE
        )
    }

    fun setStatusBarVariationsVisible(context: Context, visible: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_STATUS_BAR_VARIATIONS_VISIBLE, visible)
            .apply()
    }

    fun areStatusBarVariationsEnabled(context: Context): Boolean {
        return getStatusBarVariationsVisible(context)
    }

    fun setStatusBarVariationsEnabled(context: Context, enabled: Boolean) {
        setStatusBarVariationsVisible(context, enabled)
    }

    fun getDynamicVariationBarSlotCount(context: Context): Int {
        return getPreferences(context).getInt(
            KEY_DYNAMIC_VARIATION_BAR_SLOT_COUNT,
            DEFAULT_DYNAMIC_VARIATION_BAR_SLOT_COUNT
        ).coerceIn(MIN_DYNAMIC_VARIATION_BAR_SLOT_COUNT, MAX_DYNAMIC_VARIATION_BAR_SLOT_COUNT)
    }

    fun setDynamicVariationBarSlotCount(context: Context, count: Int) {
        getPreferences(context).edit()
            .putInt(
                KEY_DYNAMIC_VARIATION_BAR_SLOT_COUNT,
                count.coerceIn(MIN_DYNAMIC_VARIATION_BAR_SLOT_COUNT, MAX_DYNAMIC_VARIATION_BAR_SLOT_COUNT)
            )
            .apply()
    }

    fun getDynamicVariationBarResizeToContent(context: Context): Boolean {
        return getPreferences(context).getBoolean(
            KEY_DYNAMIC_VARIATION_BAR_RESIZE_TO_CONTENT,
            DEFAULT_DYNAMIC_VARIATION_BAR_RESIZE_TO_CONTENT
        )
    }

    fun setDynamicVariationBarResizeToContent(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_DYNAMIC_VARIATION_BAR_RESIZE_TO_CONTENT, enabled)
            .apply()
    }
    
    /**
     * Returns all available button options for dropdown selection.
     */
    fun getAvailableStatusBarButtons(): List<String> {
        return listOf(
            STATUS_BAR_BUTTON_NONE,
            STATUS_BAR_BUTTON_CLIPBOARD,
            STATUS_BAR_BUTTON_EMOJI,
            STATUS_BAR_BUTTON_MICROPHONE,
            STATUS_BAR_BUTTON_LANGUAGE,
            STATUS_BAR_BUTTON_HAMBURGER,
            STATUS_BAR_BUTTON_SETTINGS,
            STATUS_BAR_BUTTON_SYMBOLS,
            STATUS_BAR_BUTTON_UNDO,
            STATUS_BAR_BUTTON_REDO
        )
    }

    data class AppEnterBehaviorOverride(
        val packageName: String,
        val behavior: String,
        val sendStrategy: String = ENTER_SEND_STRATEGY_AUTO,
        val additionalSendShortcut: String = ENTER_ADDITIONAL_SEND_SHORTCUT_NONE
    )

    fun getAppEnterBehaviorEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_APP_ENTER_BEHAVIOR_ENABLED, false)
    }

    fun setAppEnterBehaviorEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_APP_ENTER_BEHAVIOR_ENABLED, enabled)
            .apply()
    }

    fun getAppEnterBehaviorPreset(context: Context): String {
        val stored = getPreferences(context).getString(
            KEY_APP_ENTER_BEHAVIOR_PRESET,
            ENTER_BEHAVIOR_PRESET_APP_DEFAULT
        ) ?: ENTER_BEHAVIOR_PRESET_APP_DEFAULT
        return normalizeEnterBehaviorPreset(stored)
    }

    fun setAppEnterBehaviorPreset(context: Context, preset: String) {
        getPreferences(context).edit()
            .putString(KEY_APP_ENTER_BEHAVIOR_PRESET, normalizeEnterBehaviorPreset(preset))
            .apply()
    }

    fun getAppEnterBehaviorOverrides(context: Context): List<AppEnterBehaviorOverride> {
        val stored = getPreferences(context).getString(KEY_APP_ENTER_BEHAVIOR_OVERRIDES, null)
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                val seen = mutableSetOf<String>()
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val packageName = item.optString("packageName", "")
                    if (packageName.isBlank() || !seen.add(packageName)) continue
                    add(
                        AppEnterBehaviorOverride(
                            packageName = packageName,
                            behavior = normalizeEnterBehavior(item.optString("behavior", ENTER_BEHAVIOR_APP_DEFAULT)),
                            sendStrategy = normalizeEnterSendStrategy(
                                item.optString("sendStrategy", ENTER_SEND_STRATEGY_AUTO)
                            ),
                            additionalSendShortcut = normalizeEnterAdditionalSendShortcut(
                                item.optString("additionalSendShortcut", ENTER_ADDITIONAL_SEND_SHORTCUT_NONE)
                            )
                        )
                    )
                }
            }
        }.getOrElse {
            Log.e(TAG, "Error loading app enter behavior overrides", it)
            emptyList()
        }
    }

    fun setAppEnterBehaviorOverrides(context: Context, overrides: List<AppEnterBehaviorOverride>) {
        val array = JSONArray()
        overrides
            .filter { it.packageName.isNotBlank() }
            .distinctBy { it.packageName }
            .forEach { override ->
                array.put(
                    JSONObject().apply {
                        put("packageName", override.packageName)
                        put("behavior", normalizeEnterBehavior(override.behavior))
                        put("sendStrategy", normalizeEnterSendStrategy(override.sendStrategy))
                        put("additionalSendShortcut", normalizeEnterAdditionalSendShortcut(override.additionalSendShortcut))
                    }
                )
            }
        getPreferences(context).edit()
            .putString(KEY_APP_ENTER_BEHAVIOR_OVERRIDES, array.toString())
            .apply()
    }

    fun setAppEnterBehaviorOverride(context: Context, packageName: String, behavior: String) {
        val updated = getAppEnterBehaviorOverrides(context)
            .filterNot { it.packageName == packageName } +
            AppEnterBehaviorOverride(packageName, normalizeEnterBehavior(behavior))
        setAppEnterBehaviorOverrides(context, updated)
    }

    fun removeAppEnterBehaviorOverride(context: Context, packageName: String) {
        setAppEnterBehaviorOverrides(
            context,
            getAppEnterBehaviorOverrides(context).filterNot { it.packageName == packageName }
        )
    }

    private fun normalizeEnterBehaviorPreset(preset: String): String {
        return when (preset) {
            ENTER_BEHAVIOR_PRESET_APP_DEFAULT,
            ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE,
            ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_CTRL_SEND,
            ENTER_BEHAVIOR_PRESET_CUSTOM -> preset
            else -> ENTER_BEHAVIOR_PRESET_APP_DEFAULT
        }
    }

    private fun normalizeEnterBehavior(behavior: String): String {
        return when (behavior) {
            ENTER_BEHAVIOR_APP_DEFAULT,
            ENTER_BEHAVIOR_ENTER_NEWLINE,
            ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE,
            ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND -> behavior
            else -> ENTER_BEHAVIOR_APP_DEFAULT
        }
    }

    private fun normalizeEnterSendStrategy(strategy: String): String {
        return when (strategy) {
            ENTER_SEND_STRATEGY_AUTO,
            ENTER_SEND_STRATEGY_EDITOR_ACTION,
            ENTER_SEND_STRATEGY_CTRL_ENTER,
            ENTER_SEND_STRATEGY_PLAIN_ENTER -> strategy
            else -> ENTER_SEND_STRATEGY_AUTO
        }
    }

    private fun normalizeEnterAdditionalSendShortcut(shortcut: String): String {
        return when (shortcut) {
            ENTER_ADDITIONAL_SEND_SHORTCUT_NONE,
            ENTER_ADDITIONAL_SEND_SHORTCUT_SYM_ENTER -> shortcut
            else -> ENTER_ADDITIONAL_SEND_SHORTCUT_NONE
        }
    }
}
