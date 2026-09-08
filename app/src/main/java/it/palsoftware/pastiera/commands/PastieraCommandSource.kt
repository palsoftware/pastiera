package it.palsoftware.pastiera.commands

import android.content.Context
import androidx.core.content.ContextCompat
import it.palsoftware.pastiera.R

class PastieraCommandSource : CommandSource {
    override val id = CommandSourceId.Pastiera

    override fun getCommands(context: Context): List<CommandTarget> {
        return listOf(
            CommandTarget(
                id = COMMAND_QUICK_LAUNCHER,
                source = id,
                kind = CommandKind.PastieraAction,
                label = context.getString(R.string.command_pastiera_quick_launcher),
                subtitle = context.getString(R.string.command_pastiera_quick_launcher_description),
                icon = CommandIcon.Search,
                launch = CommandLaunchSpec.InternalAction(ACTION_OPEN_QUICK_LAUNCHER),
                capabilities = setOf(CommandCapability.LaunchesActivity),
                defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.NavMode),
                searchTokens = listOf("Pastiera", "QuickLauncher", "Search")
            ),
            CommandTarget(
                id = COMMAND_MAIN_ACTIVITY,
                source = id,
                kind = CommandKind.PastieraAction,
                label = context.getString(R.string.command_pastiera_open_settings),
                subtitle = context.getString(R.string.command_pastiera_open_settings_description),
                icon = CommandIcon.Settings,
                launch = CommandLaunchSpec.InternalAction(ACTION_OPEN_MAIN_ACTIVITY),
                capabilities = setOf(CommandCapability.LaunchesActivity),
                defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.NavMode),
                searchTokens = listOf("Pastiera", "Settings")
            ),
            CommandTarget(
                id = COMMAND_TOGGLE_SOFTWARE_KEYBOARD_MODE,
                source = id,
                kind = CommandKind.PastieraAction,
                label = context.getString(R.string.command_pastiera_toggle_keyboard_mode),
                subtitle = context.getString(R.string.command_pastiera_toggle_keyboard_mode_description),
                icon = CommandIcon.DrawableIcon(ContextCompat.getDrawable(context, R.drawable.expansion_panels_24)),
                launch = CommandLaunchSpec.InternalAction(ACTION_TOGGLE_SOFTWARE_KEYBOARD_MODE),
                capabilities = setOf(CommandCapability.AdjustsDeviceState),
                defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.QuickLauncher, CommandSurface.NavMode),
                searchTokens = listOf("Keyboard", "Software", "Virtual", "Hardware", "Toggle")
            )
        )
    }

    companion object {
        const val COMMAND_QUICK_LAUNCHER = "pastiera.quick_launcher"
        const val COMMAND_MAIN_ACTIVITY = "pastiera.main"
        const val COMMAND_TOGGLE_SOFTWARE_KEYBOARD_MODE = "pastiera.toggle_software_keyboard_mode"
        const val ACTION_OPEN_QUICK_LAUNCHER = "open_quick_launcher"
        const val ACTION_OPEN_MAIN_ACTIVITY = "open_main_activity"
        const val ACTION_TOGGLE_SOFTWARE_KEYBOARD_MODE = "toggle_software_keyboard_mode"
    }
}
