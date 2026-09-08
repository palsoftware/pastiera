package it.palsoftware.pastiera.commands

import android.content.Context
import it.palsoftware.pastiera.SettingsManager

class CommandRegistry(
    private val context: Context,
    private val sources: List<CommandSource> = defaultSources()
) {
    fun getCommands(surface: CommandSurface): List<CommandTarget> {
        return sources
            .filter { source -> SettingsManager.isCommandSourceEnabled(context, source.id.storageValue, surface) }
            .flatMap { source -> source.getCommands(context) }
            .filter { command ->
                command.defaultSurfaces.contains(surface)
            }
            .sortedWith(compareBy<CommandTarget> { sortSourceRank(it.source) }.thenBy { it.label.lowercase() })
    }

    fun search(surface: CommandSurface, query: String): List<CommandTarget> {
        val commands = getCommands(surface)
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return commands
        return commands
            .mapNotNull { command ->
                val haystack = buildList {
                    add(command.label)
                    command.subtitle?.let(::add)
                    add(command.kind.name)
                    add(command.source.localizedDisplayLabel(context))
                    addAll(command.searchTokens)
                }.joinToString(" ").lowercase()
                if (haystack.contains(normalized)) command else null
            }
    }

    fun resolve(commandId: String): CommandTarget? {
        return sources
            .flatMap { source -> source.getCommands(context) }
            .firstOrNull { it.id == commandId }
    }

    private fun sortSourceRank(source: CommandSourceId): Int {
        return when (source) {
            CommandSourceId.Apps -> 0
            CommandSourceId.Pastiera -> 1
            CommandSourceId.AppActions -> 2
            CommandSourceId.DeviceControl -> 3
            CommandSourceId.NavActions -> 4
        }
    }

    companion object {
        fun defaultSources(): List<CommandSource> {
            return listOf(
                AppCommandSource(),
                PastieraCommandSource(),
                AppActionCommandSource(),
                DeviceControlCommandSource(),
                NavCommandSource()
            )
        }
    }
}
