package it.palsoftware.pastiera.commands

import android.content.Context
import android.graphics.drawable.Drawable
import it.palsoftware.pastiera.R

data class CommandTarget(
    val id: String,
    val source: CommandSourceId,
    val kind: CommandKind,
    val label: String,
    val subtitle: String? = null,
    val icon: CommandIcon? = null,
    val launch: CommandLaunchSpec,
    val capabilities: Set<CommandCapability> = emptySet(),
    val defaultSurfaces: Set<CommandSurface> = emptySet(),
    val searchTokens: List<String> = emptyList()
)

enum class CommandSourceId(val storageValue: String, val displayLabel: String) {
    Apps("apps", "Apps"),
    Pastiera("pastiera", "Pastiera"),
    AppActions("app_actions", "App actions"),
    DeviceControl("device_control", "Device control"),
    NavActions("nav_actions", "Navigation");

    companion object {
        fun fromStorageValue(value: String?): CommandSourceId? {
            return entries.firstOrNull { it.storageValue == value }
        }
    }

    fun localizedDisplayLabel(context: Context): String {
        return context.getString(
            when (this) {
                Apps -> R.string.command_source_apps
                Pastiera -> R.string.command_source_pastiera
                AppActions -> R.string.command_source_app_actions
                DeviceControl -> R.string.command_source_device_control
                NavActions -> R.string.command_source_navigation
            }
        )
    }
}

enum class CommandKind {
    App,
    PastieraAction,
    AppAction,
    DeviceControl,
    NavAction,
    Shortcut,
    CustomIntent
}

enum class CommandSurface {
    AssignedKey,
    QuickLauncher,
    NavMode
}

enum class CommandCapability {
    LaunchesActivity,
    SendsIntent,
    RequiresInstalledPackage,
    RequiresEditableText,
    RequiresImeContext,
    OpensSystemPanel,
    AdjustsDeviceState
}

sealed class CommandIcon {
    data class DrawableIcon(val drawable: Drawable?) : CommandIcon()
    data object App : CommandIcon()
    data object Search : CommandIcon()
    data object Settings : CommandIcon()
    data object Navigation : CommandIcon()
    data object DeviceControl : CommandIcon()
}

sealed class CommandLaunchSpec {
    data class AppPackage(val packageName: String) : CommandLaunchSpec()

    data class IntentUri(
        val action: String,
        val data: String? = null,
        val packageName: String? = null,
        val componentName: String? = null,
        val categories: List<String> = emptyList(),
        val flags: List<String> = emptyList()
    ) : CommandLaunchSpec()

    data class InternalAction(val actionId: String) : CommandLaunchSpec()
    data class NavAction(val mappingType: String, val value: String) : CommandLaunchSpec()
}

data class CommandReference(
    val id: String,
    val source: String,
    val kind: String,
    val launch: CommandLaunchSpec
)

sealed class CommandExecutionResult {
    data object Success : CommandExecutionResult()
    data class Failed(val reason: String) : CommandExecutionResult()

    val isSuccess: Boolean
        get() = this is Success
}
