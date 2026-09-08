package it.palsoftware.pastiera.commands

import android.content.Context
import it.palsoftware.pastiera.R

class NavCommandSource : CommandSource {
    override val id = CommandSourceId.NavActions

    override fun getCommands(context: Context): List<CommandTarget> {
        return keycodeCommands(context) + actionCommands(context)
    }

    private fun keycodeCommands(context: Context): List<CommandTarget> {
        return listOf(
            "DPAD_UP" to context.getString(R.string.command_nav_keycode_up),
            "DPAD_DOWN" to context.getString(R.string.command_nav_keycode_down),
            "DPAD_LEFT" to context.getString(R.string.command_nav_keycode_left),
            "DPAD_RIGHT" to context.getString(R.string.command_nav_keycode_right),
            "TAB" to context.getString(R.string.command_nav_keycode_tab),
            "MOVE_HOME" to context.getString(R.string.command_nav_keycode_home),
            "MOVE_END" to context.getString(R.string.command_nav_keycode_end),
            "PAGE_UP" to context.getString(R.string.command_nav_keycode_page_up),
            "PAGE_DOWN" to context.getString(R.string.command_nav_keycode_page_down),
            "ESCAPE" to context.getString(R.string.command_nav_keycode_escape),
            "DPAD_CENTER" to context.getString(R.string.command_nav_keycode_center),
            "FORWARD_DEL" to context.getString(R.string.command_nav_keycode_forward_delete)
        ).map { (value, label) ->
            navTarget(
                "nav.keycode.$value",
                label,
                context.getString(R.string.command_nav_navigation),
                "keycode",
                value
            )
        }
    }

    private fun actionCommands(context: Context): List<CommandTarget> {
        return listOf(
            "copy" to context.getString(R.string.command_nav_action_copy),
            "paste" to context.getString(R.string.command_nav_action_paste),
            "cut" to context.getString(R.string.command_nav_action_cut),
            "undo" to context.getString(R.string.command_nav_action_undo),
            "select_all" to context.getString(R.string.command_nav_action_select_all),
            "expand_selection_left" to context.getString(R.string.command_nav_action_select_left),
            "expand_selection_right" to context.getString(R.string.command_nav_action_select_right),
            "move_word_left" to context.getString(R.string.command_nav_action_word_left),
            "move_word_right" to context.getString(R.string.command_nav_action_word_right),
            "expand_selection_word_left" to context.getString(R.string.command_nav_action_select_word_left),
            "expand_selection_word_right" to context.getString(R.string.command_nav_action_select_word_right),
            "page_start" to context.getString(R.string.command_nav_action_page_start),
            "page_end" to context.getString(R.string.command_nav_action_page_end),
            "toggle_minimal_ui" to context.getString(R.string.command_nav_action_pastierina),
            "media_play_pause" to context.getString(R.string.command_nav_action_media_play_pause),
            "media_previous" to context.getString(R.string.command_nav_action_media_previous),
            "media_next" to context.getString(R.string.command_nav_action_media_next)
        ).map { (value, label) ->
            navTarget(
                "nav.action.$value",
                label,
                context.getString(R.string.command_nav_action_group),
                "action",
                value
            )
        }
    }

    private fun navTarget(
        id: String,
        label: String,
        subtitle: String,
        mappingType: String,
        value: String
    ): CommandTarget {
        return CommandTarget(
            id = id,
            source = this.id,
            kind = CommandKind.NavAction,
            label = label,
            subtitle = subtitle,
            icon = CommandIcon.Navigation,
            launch = CommandLaunchSpec.NavAction(mappingType, value),
            capabilities = setOf(CommandCapability.RequiresImeContext),
            defaultSurfaces = setOf(CommandSurface.NavMode),
            searchTokens = listOf(label, subtitle, value)
        )
    }
}
