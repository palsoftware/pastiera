package it.palsoftware.pastiera

/** Entries for system controls whose screens also expose the same stable IDs. */
internal fun systemSettingEntries(): List<SettingEntry> = listOf(
    SettingEntry("advanced.corner_calibration", R.string.corner_calibration_title,
        summaryRes = R.string.corner_calibration_description, route = SettingRoute(SettingsDestination.Advanced),
        availabilityCheck = { context -> it.palsoftware.pastiera.inputmethod.DeviceSpecific.isTitan2EliteDevice() || SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context) }),
    SettingEntry(
        id = "nav_mode.enabled",
        titleRes = R.string.nav_mode_enable_title,
        summaryRes = R.string.nav_mode_enable_description,
        route = SettingRoute(SettingsDestination.NavMode)
    ),
    SettingEntry(
        id = "nav_mode.ctrl_hold",
        titleRes = R.string.nav_mode_ctrl_hold_title,
        summaryRes = R.string.nav_mode_ctrl_hold_description,
        route = SettingRoute(SettingsDestination.NavMode),
        availabilityCheck = { SettingsManager.getNavModeEnabled(it) },
        unavailableFallbackId = "nav_mode.enabled"
    ),
    SettingEntry(
        id = "nav_mode.layout_aware_ctrl_shortcuts",
        titleRes = R.string.layout_aware_ctrl_shortcuts_title,
        summaryRes = R.string.layout_aware_ctrl_shortcuts_description,
        route = SettingRoute(SettingsDestination.NavMode),
        availabilityCheck = { SettingsManager.getNavModeEnabled(it) },
        unavailableFallbackId = "nav_mode.enabled"
    ),
    SettingEntry(
        id = "accessibility.bounce_keys_delay",
        titleRes = R.string.settings_accessibility_bounce_keys_delay_link_title,
        summaryRes = R.string.settings_accessibility_bounce_keys_delay_description,
        route = SettingRoute(SettingsDestination.Accessibility)
    ),
    SettingEntry(
        id = "trackpad.add_word",
        titleRes = R.string.trackpad_gesture_add_word_title,
        summaryRes = R.string.trackpad_gesture_add_word_description,
        route = SettingRoute(SettingsDestination.Advanced)
    ),
    SettingEntry(
        id = "trackpad.add_word_full_width",
        titleRes = R.string.trackpad_gesture_add_word_full_width_title,
        summaryRes = R.string.trackpad_gesture_add_word_full_width_description,
        route = SettingRoute(SettingsDestination.Advanced)
    ),
    SettingEntry(
        id = "trackpad.swipe_to_delete",
        titleRes = R.string.swipe_to_delete_title,
        summaryRes = R.string.swipe_to_delete_description,
        route = SettingRoute(SettingsDestination.Advanced)
    ),
    SettingEntry(
        id = "trackpad.swipe_to_delete_provider",
        titleRes = R.string.swipe_to_delete_provider_title,
        route = SettingRoute(SettingsDestination.Advanced)
    )
)
