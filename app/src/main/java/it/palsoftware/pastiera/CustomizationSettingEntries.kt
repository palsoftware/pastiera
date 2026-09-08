package it.palsoftware.pastiera

/** Addressable customization controls; dynamic data rows remain inside their aggregate editors. */
internal fun customizationSettingEntries(): List<SettingEntry> = listOf(
    SettingEntry(
        id = "quick_launcher.top_match_color",
        titleRes = R.string.setting_link_quick_launcher_top_match_color,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_cosmetic"
        )
    ),
    SettingEntry(
        id = "quick_launcher.favorite_color",
        titleRes = R.string.setting_link_quick_launcher_favorite_color,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_cosmetic"
        )
    ),
    SettingEntry(
        id = "customization.variations",
        titleRes = R.string.variation_customize_title,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "variations"
        )
    ),
    SettingEntry(
        id = "customization.sounds",
        titleRes = R.string.settings_category_sounds,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "sounds"
        )
    ),
    SettingEntry(
        id = "quick_launcher.enabled",
        titleRes = R.string.launcher_shortcuts_title,
        summaryRes = R.string.launcher_shortcuts_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcuts"
        )
    ),
    SettingEntry(
        id = "quick_launcher.sym_shortcuts",
        titleRes = R.string.power_shortcuts_title,
        summaryRes = R.string.power_shortcuts_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcuts"
        )
    ),
    SettingEntry(
        id = "quick_launcher.alt_shortcuts",
        titleRes = R.string.alt_key_shortcuts_title,
        summaryRes = R.string.alt_key_shortcuts_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcuts"
        )
    ),
    SettingEntry(
        id = "quick_launcher.sym_in_text_fields",
        titleRes = R.string.key_shortcuts_allow_in_text_fields_title,
        summaryRes = R.string.sym_shortcuts_in_text_fields_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcuts"
        ),
        availabilityCheck = { SettingsManager.getPowerShortcutsEnabled(it) },
        unavailableFallbackId = "quick_launcher.sym_shortcuts"
    ),
    SettingEntry(
        id = "quick_launcher.alt_in_text_fields",
        titleRes = R.string.key_shortcuts_allow_in_text_fields_title,
        summaryRes = R.string.alt_shortcuts_in_text_fields_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcuts"
        ),
        availabilityCheck = { SettingsManager.getQuickLauncherAltShortcutsOutsideTextFields(it) },
        unavailableFallbackId = "quick_launcher.alt_shortcuts"
    ),
    SettingEntry(
        id = "quick_launcher.behavior",
        titleRes = R.string.quick_launcher_behaviour_title,
        summaryRes = R.string.quick_launcher_behaviour_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_behavior"
        )
    ),
    SettingEntry(
        id = "quick_launcher.appearance",
        titleRes = R.string.quick_launcher_cosmetic_title,
        summaryRes = R.string.quick_launcher_cosmetic_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_cosmetic"
        )
    ),
    SettingEntry(
        id = "quick_launcher.assignments",
        titleRes = R.string.launcher_shortcuts_configure,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_assignments"
        )
    ),
    SettingEntry(
        id = "quick_launcher.auto_start_single",
        titleRes = R.string.quick_launcher_auto_start_single_title,
        summaryRes = R.string.quick_launcher_auto_start_single_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_behavior"
        )
    ),
    SettingEntry(
        id = "quick_launcher.limit_results",
        titleRes = R.string.quick_launcher_limit_results_title,
        summaryRes = R.string.quick_launcher_limit_results_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_behavior"
        )
    ),
    SettingEntry(
        id = "quick_launcher.respect_keyboard_layout",
        titleRes = R.string.quick_launcher_respect_keyboard_layout_title,
        summaryRes = R.string.quick_launcher_respect_keyboard_layout_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_behavior"
        )
    ),
    SettingEntry(
        id = "quick_launcher.typo_tolerant_ranking",
        titleRes = R.string.quick_launcher_typo_tolerant_ranking_title,
        summaryRes = R.string.quick_launcher_typo_tolerant_ranking_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_behavior"
        )
    ),
    SettingEntry(
        id = "quick_launcher.provider",
        titleRes = R.string.quick_launcher_provider_title,
        summaryRes = R.string.quick_launcher_provider_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_behavior"
        )
    ),
    SettingEntry(
        id = "quick_launcher.animation_duration",
        titleRes = R.string.quick_launcher_animation_duration_title,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_behavior"
        )
    ),
    SettingEntry(
        id = "quick_launcher.width",
        titleRes = R.string.quick_launcher_width_title,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_cosmetic"
        )
    ),
    SettingEntry(
        id = "quick_launcher.pill_mode",
        titleRes = R.string.quick_launcher_pill_mode_title,
        summaryRes = R.string.quick_launcher_pill_mode_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_cosmetic"
        )
    ),
    SettingEntry(
        id = "quick_launcher.highlight_favorites",
        titleRes = R.string.setting_link_quick_launcher_highlight_favorites,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_cosmetic"
        )
    ),
    SettingEntry(
        id = "quick_launcher.icon_colors",
        titleRes = R.string.setting_link_quick_launcher_icon_colors,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_cosmetic"
        )
    ),
    SettingEntry(
        id = "quick_launcher.static_top_highlight",
        titleRes = R.string.setting_link_quick_launcher_static_top_highlight,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_cosmetic"
        )
    ),
    SettingEntry(
        id = "quick_launcher.alias_first",
        titleRes = R.string.setting_link_quick_launcher_alias_first,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_cosmetic"
        )
    ),
    SettingEntry(
        id = "quick_launcher.entries",
        titleRes = R.string.setting_link_quick_launcher_entries,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_cosmetic"
        )
    ),
    SettingEntry(
        id = "quick_launcher.customize_entries",
        titleRes = R.string.setting_link_quick_launcher_customize_entries,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_cosmetic"
        )
    ),
    SettingEntry(
        id = "quick_launcher.source.apps",
        titleRes = R.string.setting_link_quick_launcher_source_apps,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_cosmetic"
        )
    ),
    SettingEntry(
        id = "quick_launcher.source.pastiera",
        titleRes = R.string.setting_link_quick_launcher_source_pastiera,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_cosmetic"
        )
    ),
    SettingEntry(
        id = "quick_launcher.source.app_actions",
        titleRes = R.string.setting_link_quick_launcher_source_appactions,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_cosmetic"
        )
    ),
    SettingEntry(
        id = "quick_launcher.source.device_control",
        titleRes = R.string.setting_link_quick_launcher_source_devicecontrol,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_cosmetic"
        )
    ),
    SettingEntry(
        id = "quick_launcher.source.nav_actions",
        titleRes = R.string.setting_link_quick_launcher_source_navactions,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "launcher_shortcut_cosmetic"
        )
    ),
    SettingEntry(
        id = "app_enter_behavior.enabled",
        titleRes = R.string.app_enter_behaviour_enable_title,
        summaryRes = R.string.app_enter_behaviour_enable_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "app_enter_behavior"
        )
    ),
    SettingEntry(
        id = "app_enter_behavior.preset",
        titleRes = R.string.app_enter_behaviour_preset_title,
        summaryRes = R.string.app_enter_behaviour_preset_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "app_enter_behavior"
        )
    ),
    SettingEntry(
        id = "app_enter_behavior.additional_send_shortcut",
        titleRes = R.string.app_enter_behaviour_additional_send_shortcut_label,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "app_enter_behavior"
        )
    ),
    SettingEntry(
        id = "app_enter_behavior.overrides",
        titleRes = R.string.app_enter_behaviour_overrides_title,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "app_enter_behavior"
        )
    ),
    SettingEntry(
        id = "sounds.typing_mode",
        titleRes = R.string.typing_sound_title,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "sounds"
        )
    ),
    SettingEntry(
        id = "sounds.output",
        titleRes = R.string.typing_sound_output_title,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "sounds"
        )
    ),
    SettingEntry(
        id = "sounds.system_haptics",
        titleRes = R.string.tap_haptic_system_title,
        summaryRes = R.string.tap_haptic_system_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "sounds"
        )
    ),
    SettingEntry(
        id = "sounds.haptic_duration",
        titleRes = R.string.setting_link_haptic_duration,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "sounds"
        ),
        availabilityCheck = { !SettingsManager.getTapHapticUseSystem(it) },
        unavailableFallbackId = "sounds.system_haptics"
    ),
    SettingEntry(
        id = "variations.preset",
        titleRes = R.string.static_variation_preset_title,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "variations"
        )
    ),
    SettingEntry(
        id = "variations.sticky_layer",
        titleRes = R.string.static_variation_layer_sticky_title,
        summaryRes = R.string.static_variation_layer_sticky_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "variations"
        )
    ),
    SettingEntry(
        id = "variations.layout_override",
        titleRes = R.string.variation_global_layout_override_title,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "variations"
        )
    ),
    SettingEntry(
        id = "variations.mappings",
        titleRes = R.string.setting_link_variation_mappings,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "variations"
        )
    ),
    SettingEntry(
        id = "status_bar.presentation",
        titleRes = R.string.status_bar_style_section,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "status_bar_buttons"
        )
    ),
    SettingEntry(
        id = "status_bar.rounded_corners",
        titleRes = R.string.titan2_elite_rounded_corners_title,
        summaryRes = R.string.titan2_elite_rounded_corners_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "status_bar_buttons"
        )
    ),
    SettingEntry(
        id = "status_bar.variations_visible",
        titleRes = R.string.status_bar_variations_visible_title,
        summaryRes = R.string.status_bar_variations_visible_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "status_bar_buttons"
        ),
        availabilityCheck = { SettingsManager.getStatusBarPresentationMode(it) != SettingsManager.StatusBarPresentationMode.PASTIERINA },
        unavailableFallbackId = "status_bar.presentation"
    ),
    SettingEntry(
        id = "status_bar.variation_slots",
        titleRes = R.string.dynamic_variation_slot_count_title,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "status_bar_buttons"
        ),
        availabilityCheck = { SettingsManager.getStatusBarPresentationMode(it) != SettingsManager.StatusBarPresentationMode.PASTIERINA && SettingsManager.areStatusBarVariationsEnabled(it) },
        unavailableFallbackId = "status_bar.variations_visible"
    ),
    SettingEntry(
        id = "status_bar.resize_variations",
        titleRes = R.string.dynamic_variation_resize_to_content_title,
        summaryRes = R.string.dynamic_variation_resize_to_content_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "status_bar_buttons"
        ),
        availabilityCheck = { SettingsManager.getStatusBarPresentationMode(it) != SettingsManager.StatusBarPresentationMode.PASTIERINA && SettingsManager.areStatusBarVariationsEnabled(it) },
        unavailableFallbackId = "status_bar.variations_visible"
    ),
    SettingEntry(
        id = "status_bar.top_corner",
        titleRes = R.string.titan2_elite_top_corner_title,
        summaryRes = R.string.titan2_elite_top_corner_description,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "status_bar_buttons"
        ),
        availabilityCheck = { SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(it) },
        unavailableFallbackId = "status_bar.rounded_corners"
    ),
    SettingEntry(
        id = "status_bar.max_icon_shrink",
        titleRes = R.string.setting_link_max_icon_shrink,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "status_bar_buttons"
        ),
        availabilityCheck = { SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(it) },
        unavailableFallbackId = "status_bar.rounded_corners"
    ),
    SettingEntry(
        id = "status_bar.extended_left",
        titleRes = R.string.setting_link_status_extended_left,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "status_bar_buttons"
        ),
        availabilityCheck = { SettingsManager.getStatusBarPresentationMode(it) != SettingsManager.StatusBarPresentationMode.PASTIERINA },
        unavailableFallbackId = "status_bar.presentation"
    ),
    SettingEntry(
        id = "status_bar.extended_right",
        titleRes = R.string.setting_link_status_extended_right,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "status_bar_buttons"
        ),
        availabilityCheck = { SettingsManager.getStatusBarPresentationMode(it) != SettingsManager.StatusBarPresentationMode.PASTIERINA },
        unavailableFallbackId = "status_bar.presentation"
    ),
    SettingEntry(
        id = "status_bar.pastierina_left",
        titleRes = R.string.setting_link_status_pastierina_left,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "status_bar_buttons"
        ),
        availabilityCheck = { !(SettingsManager.getStatusBarPresentationMode(it) != SettingsManager.StatusBarPresentationMode.PASTIERINA) },
        unavailableFallbackId = "status_bar.presentation"
    ),
    SettingEntry(
        id = "status_bar.pastierina_right",
        titleRes = R.string.setting_link_status_pastierina_right,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "status_bar_buttons"
        ),
        availabilityCheck = { !(SettingsManager.getStatusBarPresentationMode(it) != SettingsManager.StatusBarPresentationMode.PASTIERINA) },
        unavailableFallbackId = "status_bar.presentation"
    ),
    SettingEntry(
        id = "keyboard_theme.software.key_rounding",
        titleRes = R.string.setting_link_theme_software_key_rounding,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Keys
        )
    ),
    SettingEntry(
        id = "keyboard_theme.hardware.chrome_rounding",
        titleRes = R.string.setting_link_theme_hardware_chrome_rounding,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.HARDWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Keys
        )
    ),
    SettingEntry(
        id = "keyboard_theme.software.chrome_rounding",
        titleRes = R.string.setting_link_theme_software_chrome_rounding,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Keys
        )
    ),
    SettingEntry(
        id = "keyboard_theme.hardware.suggestions_height",
        titleRes = R.string.setting_link_theme_hardware_suggestions_height,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.HARDWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Keys
        )
    ),
    SettingEntry(
        id = "keyboard_theme.software.suggestions_height",
        titleRes = R.string.setting_link_theme_software_suggestions_height,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Keys
        )
    ),
    SettingEntry(
        id = "keyboard_theme.hardware.variations_height",
        titleRes = R.string.setting_link_theme_hardware_variations_height,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.HARDWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Keys
        )
    ),
    SettingEntry(
        id = "keyboard_theme.software.variations_height",
        titleRes = R.string.setting_link_theme_software_variations_height,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Keys
        )
    ),
    SettingEntry(
        id = "keyboard_theme.software.key_height",
        titleRes = R.string.setting_link_theme_software_key_height,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Keys
        )
    ),
    SettingEntry(
        id = "keyboard_theme.software.number_row_height",
        titleRes = R.string.setting_link_theme_software_number_row_height,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Keys
        )
    ),
    SettingEntry(
        id = "keyboard_theme.software.key_width",
        titleRes = R.string.setting_link_theme_software_key_width,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Keys
        )
    ),
    SettingEntry(
        id = "keyboard_theme.software.row_spacing",
        titleRes = R.string.setting_link_theme_software_row_spacing,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Keys
        )
    ),
    SettingEntry(
        id = "keyboard_theme.preview_viewport",
        titleRes = R.string.setting_link_theme_preview_viewport,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE
        )
    ),
    SettingEntry(
        id = "keyboard_theme.hardware.led_colors",
        titleRes = R.string.setting_link_theme_hardware_led_colors,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.HARDWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Colors
        )
    ),
    SettingEntry(
        id = "keyboard_theme.hardware.assignment",
        titleRes = R.string.setting_link_theme_hardware_assignment,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme_assignment",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.HARDWARE
        )
    ),
    SettingEntry(
        id = "keyboard_theme.hardware.light_theme",
        titleRes = R.string.setting_link_theme_hardware_light_theme,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme_assignment",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.HARDWARE
        ),
        availabilityCheck = { SettingsManager.getKeyboardThemeAssignmentMode(it, SettingsManager.KeyboardThemeTarget.HARDWARE) == SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM },
        unavailableFallbackId = "keyboard_theme.hardware.assignment"
    ),
    SettingEntry(
        id = "keyboard_theme.hardware.dark_theme",
        titleRes = R.string.setting_link_theme_hardware_dark_theme,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme_assignment",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.HARDWARE
        ),
        availabilityCheck = { SettingsManager.getKeyboardThemeAssignmentMode(it, SettingsManager.KeyboardThemeTarget.HARDWARE) == SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM },
        unavailableFallbackId = "keyboard_theme.hardware.assignment"
    ),
    SettingEntry(
        id = "keyboard_theme.hardware.layout_overrides",
        titleRes = R.string.setting_link_theme_hardware_layout_overrides,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme_assignment",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.HARDWARE
        )
    ),
    SettingEntry(
        id = "keyboard_theme.software.led_colors",
        titleRes = R.string.setting_link_theme_software_led_colors,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE,
            keyboardThemeTab = KeyboardThemeEditorTab.Colors
        )
    ),
    SettingEntry(
        id = "keyboard_theme.software.assignment",
        titleRes = R.string.setting_link_theme_software_assignment,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme_assignment",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE
        )
    ),
    SettingEntry(
        id = "keyboard_theme.software.light_theme",
        titleRes = R.string.setting_link_theme_software_light_theme,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme_assignment",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE
        ),
        availabilityCheck = { SettingsManager.getKeyboardThemeAssignmentMode(it, SettingsManager.KeyboardThemeTarget.SOFTWARE) == SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM },
        unavailableFallbackId = "keyboard_theme.software.assignment"
    ),
    SettingEntry(
        id = "keyboard_theme.software.dark_theme",
        titleRes = R.string.setting_link_theme_software_dark_theme,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme_assignment",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE
        ),
        availabilityCheck = { SettingsManager.getKeyboardThemeAssignmentMode(it, SettingsManager.KeyboardThemeTarget.SOFTWARE) == SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM },
        unavailableFallbackId = "keyboard_theme.software.assignment"
    ),
    SettingEntry(
        id = "keyboard_theme.software.layout_overrides",
        titleRes = R.string.setting_link_theme_software_layout_overrides,
        route = SettingRoute(
            destination = SettingsDestination.Customization,
            customizationDestination = "keyboard_theme_assignment",
            keyboardThemeTarget = SettingsManager.KeyboardThemeTarget.SOFTWARE
        )
    ),
)

internal fun customizationSettingSubtitles(): Map<String, Int> = mapOf(
    "variations" to R.string.variation_customize_title,
    "sounds" to R.string.settings_category_sounds,
    "launcher_shortcut_behavior" to R.string.quick_launcher_behaviour_title,
    "launcher_shortcut_cosmetic" to R.string.quick_launcher_cosmetic_title,
    "launcher_shortcut_assignments" to R.string.launcher_shortcuts_screen_title,
    "keyboard_theme_assignment" to R.string.setting_link_theme_assignment
)
