package it.palsoftware.pastiera.commands

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import it.palsoftware.pastiera.R

class AppActionCommandSource : CommandSource {
    override val id = CommandSourceId.AppActions

    override fun getCommands(context: Context): List<CommandTarget> {
        return listOf(
            context.appIntentCommand(
                id = COMMAND_SEARCH,
                label = context.getString(R.string.command_app_niagara_search),
                subtitle = "Niagara Launcher",
                uri = "niagara://search",
                packageName = NIAGARA_PACKAGE,
                tokens = listOf("Niagara", "Search", "Launcher")
            ),
            context.appIntentCommand(
                id = COMMAND_AGENDA,
                label = context.getString(R.string.command_app_niagara_agenda),
                subtitle = "Niagara Launcher",
                uri = "niagara://agenda",
                packageName = NIAGARA_PACKAGE,
                tokens = listOf("Niagara", "Agenda", "Calendar")
            ),
            context.appIntentCommand(
                id = "tasker.select_task",
                label = context.getString(R.string.command_app_tasker_select_task),
                subtitle = "Tasker",
                action = "net.dinglisch.android.tasker.ACTION_TASK_SELECT",
                packageName = TASKER_PACKAGE,
                tokens = listOf("Tasker", "Task", "Select")
            ),
            context.appIntentCommand(
                id = "tasker.preferences",
                label = context.getString(R.string.command_app_tasker_preferences),
                subtitle = "Tasker",
                action = "net.dinglisch.android.tasker.ACTION_OPEN_PREFS",
                packageName = TASKER_PACKAGE,
                tokens = listOf("Tasker", "Preferences", "Settings")
            ),
            context.appIntentCommand(
                id = "tasker.create_shortcut",
                label = context.getString(R.string.command_app_tasker_create_shortcut),
                subtitle = "Tasker",
                action = Intent.ACTION_CREATE_SHORTCUT,
                packageName = TASKER_PACKAGE,
                tokens = listOf("Tasker", "Shortcut")
            ),
            context.appIntentCommand(
                id = "homeassistant.navigate",
                label = context.getString(R.string.command_app_home_assistant_navigate),
                subtitle = "Home Assistant",
                uri = "homeassistant://navigate",
                packageName = HOME_ASSISTANT_PACKAGE,
                tokens = listOf("Home Assistant", "Navigate", "Dashboard")
            ),
            context.appIntentCommand(
                id = "homeassistant.assist",
                label = context.getString(R.string.command_app_home_assistant_assist),
                subtitle = "Home Assistant",
                action = Intent.ACTION_ASSIST,
                packageName = HOME_ASSISTANT_PACKAGE,
                tokens = listOf("Home Assistant", "Assist", "Voice")
            ),
            context.appIntentCommand(
                id = "homeassistant.voice_command",
                label = context.getString(R.string.command_app_home_assistant_voice_command),
                subtitle = "Home Assistant",
                action = Intent.ACTION_VOICE_COMMAND,
                packageName = HOME_ASSISTANT_PACKAGE,
                tokens = listOf("Home Assistant", "Voice", "Command")
            )
        ).filter { context.canResolve(it.launch) }
    }

    private fun Context.appIntentCommand(
        id: String,
        label: String,
        subtitle: String,
        uri: String? = null,
        action: String = Intent.ACTION_VIEW,
        packageName: String,
        tokens: List<String>
    ): CommandTarget {
        return CommandTarget(
            id = id,
            source = this@AppActionCommandSource.id,
            kind = CommandKind.AppAction,
            label = label,
            subtitle = subtitle,
            icon = CommandIcon.DrawableIcon(appIcon(packageName)),
            launch = CommandLaunchSpec.IntentUri(
                action = action,
                data = uri,
                packageName = packageName,
                categories = if (uri != null) listOf(Intent.CATEGORY_BROWSABLE) else emptyList()
            ),
            capabilities = setOf(
                CommandCapability.SendsIntent,
                CommandCapability.RequiresInstalledPackage
            ),
            defaultSurfaces = setOf(CommandSurface.AssignedKey, CommandSurface.QuickLauncher, CommandSurface.NavMode),
            searchTokens = tokens
        )
    }

    private fun Context.canResolve(launch: CommandLaunchSpec): Boolean {
        val spec = launch as? CommandLaunchSpec.IntentUri ?: return false
        val intent = Intent(spec.action, spec.data?.let(Uri::parse)).apply {
            spec.packageName?.let(::setPackage)
            spec.categories.forEach(::addCategory)
        }
        return intent.resolveActivity(packageManager) != null
    }

    private fun Context.appIcon(packageName: String) = try {
        packageManager.getApplicationIcon(packageName)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    companion object {
        const val NIAGARA_PACKAGE = "bitpit.launcher"
        const val TASKER_PACKAGE = "net.dinglisch.android.taskerm"
        const val HOME_ASSISTANT_PACKAGE = "io.homeassistant.companion.android"
        const val COMMAND_SEARCH = "niagara.search"
        const val COMMAND_AGENDA = "niagara.agenda"
    }
}
