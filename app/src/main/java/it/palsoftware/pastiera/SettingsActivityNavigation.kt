package it.palsoftware.pastiera

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent

internal fun Context.settingsActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.settingsActivity()
    else -> error("Settings must be hosted by an Activity")
}

private const val EXTRA_PAGE = "it.palsoftware.pastiera.SETTINGS_PAGE"
private const val EXTRA_NAV_KEY = "it.palsoftware.pastiera.SETTINGS_NAV_KEY"
private const val EXTRA_DEVICE_PAGE = "it.palsoftware.pastiera.SETTINGS_DEVICE_PAGE"
private const val EXTRA_THEME_TAB = "it.palsoftware.pastiera.SETTINGS_THEME_TAB"

internal fun Intent.putSettingsPage(page: SettingsPage): Intent = apply {
    putExtra(EXTRA_PAGE, page.destination.name)
    putExtra(SettingsActivity.EXTRA_CUSTOMIZATION_DESTINATION, page.customizationDestination)
    putExtra(SettingsActivity.EXTRA_KEYBOARD_THEME_TARGET, page.keyboardThemeTarget)
    putExtra(EXTRA_THEME_TAB, page.keyboardThemeTab)
    page.navModeKeyCode?.let { putExtra(EXTRA_NAV_KEY, it) }
    putExtra(EXTRA_DEVICE_PAGE, page.keyboardsDevicesDestination.name)
}

internal fun Intent.settingsPage(): SettingsPage {
    val linked = data?.let(SettingLinkRegistry::parseSettingLinkUri)?.let(SettingLinkRegistry::byId)
    if (linked != null && !linked.route.symCustomization) return linked.route.toSettingsPage()
    val destination = getStringExtra(EXTRA_PAGE)?.let { value ->
        SettingsDestination.entries.firstOrNull { it.name == value }
    } ?: when (getStringExtra(SettingsActivity.EXTRA_DESTINATION)) {
        SettingsActivity.DESTINATION_CUSTOMIZATION -> SettingsDestination.Customization
        SettingsActivity.DESTINATION_DEVICE_SYM_LAYER_EDITOR -> SettingsDestination.DeviceSymLayerEditor
        SettingsActivity.DESTINATION_MODIFIERS -> SettingsDestination.Modifiers
        else -> SettingsDestination.Main
    }
    return SettingsPage(destination,
        getStringExtra(SettingsActivity.EXTRA_CUSTOMIZATION_DESTINATION),
        getStringExtra(SettingsActivity.EXTRA_KEYBOARD_THEME_TARGET),
        getStringExtra(EXTRA_THEME_TAB),
        if (hasExtra(EXTRA_NAV_KEY)) getIntExtra(EXTRA_NAV_KEY, 0) else null,
        KeyboardsDevicesDestination.entries.firstOrNull { it.name == getStringExtra(EXTRA_DEVICE_PAGE) }
            ?: KeyboardsDevicesDestination.Main)
}

internal fun openSettingsPage(context: Context, page: SettingsPage) {
    context.startActivity(Intent(context, SettingsActivity::class.java).putSettingsPage(page))
}

/** A child is a new platform Activity. Android alone owns the back stack and transitions. */
internal fun openSettingsChild(context: Context, section: String, page: String) {
    val activity = context.settingsActivity()
    activity.startActivity(Intent(activity.intent).apply {
        data = null
        flags = 0
        removeExtra(SymCustomizationActivity.EXTRA_SETTING_ID)
        putExtra("settings.child.$section", page)
    })
}

internal fun settingsChild(context: Context, section: String): String? =
    context.settingsActivity().intent.getStringExtra("settings.child.$section")
