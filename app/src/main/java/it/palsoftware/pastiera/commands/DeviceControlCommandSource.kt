package it.palsoftware.pastiera.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import it.palsoftware.pastiera.R

class DeviceControlCommandSource : CommandSource {
    override val id = CommandSourceId.DeviceControl

    override fun getCommands(context: Context): List<CommandTarget> {
        return buildList {
            add(deviceAction("device.home", context.getString(R.string.command_device_home_screen), context.getString(R.string.command_group_system), ACTION_HOME_SCREEN))
            add(deviceAction("device.media.play_pause", context.getString(R.string.command_device_media_play_pause), context.getString(R.string.command_group_media), ACTION_MEDIA_PLAY_PAUSE))
            add(deviceAction("device.media.previous", context.getString(R.string.command_device_media_previous), context.getString(R.string.command_group_media), ACTION_MEDIA_PREVIOUS))
            add(deviceAction("device.media.next", context.getString(R.string.command_device_media_next), context.getString(R.string.command_group_media), ACTION_MEDIA_NEXT))
            add(deviceAction("device.volume.up", context.getString(R.string.command_device_volume_up), context.getString(R.string.command_group_audio), ACTION_VOLUME_UP))
            add(deviceAction("device.volume.down", context.getString(R.string.command_device_volume_down), context.getString(R.string.command_group_audio), ACTION_VOLUME_DOWN))
            add(deviceAction("device.volume.mute", context.getString(R.string.command_device_volume_mute), context.getString(R.string.command_group_audio), ACTION_VOLUME_MUTE))
            add(deviceAction("device.brightness.up", context.getString(R.string.command_device_brightness_up), context.getString(R.string.command_group_display), ACTION_BRIGHTNESS_UP))
            add(deviceAction("device.brightness.down", context.getString(R.string.command_device_brightness_down), context.getString(R.string.command_group_display), ACTION_BRIGHTNESS_DOWN))
            add(settingsCommand(context, "settings.android.main", context.getString(R.string.command_device_settings), Settings.ACTION_SETTINGS))
            add(settingsCommand(context, "settings.android.apps", context.getString(R.string.command_device_apps), Settings.ACTION_APPLICATION_SETTINGS))
            add(settingsCommand(context, "settings.android.default_apps", context.getString(R.string.command_device_default_apps), Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            add(settingsCommand(context, "settings.android.input_method", context.getString(R.string.command_device_keyboard_settings), Settings.ACTION_INPUT_METHOD_SETTINGS))
            add(settingsCommand(context, "settings.android.accessibility", context.getString(R.string.command_device_accessibility), Settings.ACTION_ACCESSIBILITY_SETTINGS))
            add(settingsCommand(context, "settings.android.language_input", context.getString(R.string.command_device_language_input), Settings.ACTION_LOCALE_SETTINGS))
            add(settingsCommand(context, "settings.android.bluetooth", context.getString(R.string.command_device_bluetooth), Settings.ACTION_BLUETOOTH_SETTINGS))
            add(settingsCommand(context, "settings.android.wifi", context.getString(R.string.command_device_wifi), Settings.ACTION_WIFI_SETTINGS))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(settingsCommand(context, "settings.android.internet_panel", context.getString(R.string.command_device_internet), Settings.Panel.ACTION_INTERNET_CONNECTIVITY, opensPanel = true))
            }
            add(settingsCommand(context, "settings.android.display", context.getString(R.string.command_device_display), Settings.ACTION_DISPLAY_SETTINGS))
            add(settingsCommand(context, "settings.android.sound", context.getString(R.string.command_device_sound), Settings.ACTION_SOUND_SETTINGS))
            add(settingsCommand(context, "settings.android.nfc", context.getString(R.string.command_device_nfc), Settings.ACTION_NFC_SETTINGS))
            add(settingsCommand(context, "settings.android.battery", context.getString(R.string.command_device_battery), Settings.ACTION_BATTERY_SAVER_SETTINGS))
            add(settingsCommand(context, "settings.android.notifications", context.getString(R.string.command_device_notifications), ACTION_NOTIFICATION_SETTINGS))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(
                    settingsCommand(
                        context = context,
                        id = "settings.android.pastiera_notifications",
                        label = context.getString(R.string.command_device_pastiera_notifications),
                        action = Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                        data = null,
                        extras = mapOf(Settings.EXTRA_APP_PACKAGE to context.packageName)
                    )
                )
            }
        }.filter { context.canResolve(it.launch) }
    }

    private fun deviceAction(
        id: String,
        label: String,
        group: String,
        actionId: String
    ): CommandTarget {
        return CommandTarget(
            id = id,
            source = this.id,
            kind = CommandKind.DeviceControl,
            label = label,
            subtitle = group,
            icon = CommandIcon.DeviceControl,
            launch = CommandLaunchSpec.InternalAction(actionId),
            capabilities = setOf(CommandCapability.AdjustsDeviceState),
            defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.NavMode, CommandSurface.QuickLauncher),
            searchTokens = listOf(label, group, "device", "control")
        )
    }

    private fun settingsCommand(
        context: Context,
        id: String,
        label: String,
        action: String,
        data: String? = null,
        opensPanel: Boolean = false,
        extras: Map<String, String> = emptyMap()
    ): CommandTarget {
        return CommandTarget(
            id = id,
            source = this.id,
            kind = CommandKind.DeviceControl,
            label = label,
            subtitle = if (opensPanel) {
                context.getString(R.string.command_group_system_panel)
            } else {
                context.getString(R.string.command_group_settings)
            },
            icon = CommandIcon.Settings,
            launch = SettingsIntentSpec(action, data, extras),
            capabilities = buildSet {
                add(CommandCapability.SendsIntent)
                if (opensPanel) add(CommandCapability.OpensSystemPanel)
            },
            defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.NavMode, CommandSurface.QuickLauncher),
            searchTokens = listOf(label, "settings", "system", "device", "control")
        )
    }

    private fun SettingsIntentSpec(
        action: String,
        data: String?,
        extras: Map<String, String>
    ): CommandLaunchSpec.IntentUri {
        return CommandLaunchSpec.IntentUri(
            action = action,
            data = data,
            flags = extras.map { "${it.key}=${it.value}" }
        )
    }

    private fun Context.canResolve(launch: CommandLaunchSpec): Boolean {
        if (launch is CommandLaunchSpec.InternalAction) return true
        val spec = launch as? CommandLaunchSpec.IntentUri ?: return false
        val intent = Intent(spec.action, spec.data?.let(Uri::parse))
        return intent.resolveActivity(packageManager) != null
    }

    companion object {
        const val ACTION_HOME_SCREEN = "device.home"
        const val ACTION_MEDIA_PLAY_PAUSE = "device.media.play_pause"
        const val ACTION_MEDIA_PREVIOUS = "device.media.previous"
        const val ACTION_MEDIA_NEXT = "device.media.next"
        const val ACTION_VOLUME_UP = "device.volume.up"
        const val ACTION_VOLUME_DOWN = "device.volume.down"
        const val ACTION_VOLUME_MUTE = "device.volume.mute"
        const val ACTION_BRIGHTNESS_UP = "device.brightness.up"
        const val ACTION_BRIGHTNESS_DOWN = "device.brightness.down"
        private const val ACTION_NOTIFICATION_SETTINGS = "android.settings.NOTIFICATION_SETTINGS"
    }
}
