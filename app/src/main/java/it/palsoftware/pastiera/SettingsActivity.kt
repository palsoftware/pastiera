package it.palsoftware.pastiera

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import it.palsoftware.pastiera.ui.theme.PastieraTheme

data class SettingLinkRequest(
    val id: String,
    val serial: Long
)

class SettingsActivity : LocalizedComponentActivity() {
    companion object {
        const val EXTRA_DESTINATION = "it.palsoftware.pastiera.SETTINGS_DESTINATION"
        const val DESTINATION_CUSTOMIZATION = "customization"
        const val DESTINATION_DEVICE_SYM_LAYER_EDITOR = "device_sym_layer_editor"
        const val DESTINATION_MODIFIERS = "modifiers"
        const val EXTRA_CUSTOMIZATION_DESTINATION = "it.palsoftware.pastiera.CUSTOMIZATION_DESTINATION"
        const val CUSTOMIZATION_DESTINATION_VARIATIONS = "variations"
        const val CUSTOMIZATION_DESTINATION_LAUNCHER_SHORTCUTS = "launcher_shortcuts"
        const val CUSTOMIZATION_DESTINATION_APP_ENTER_BEHAVIOR = "app_enter_behavior"
        const val CUSTOMIZATION_DESTINATION_STATUS_BAR_BUTTONS = "status_bar_buttons"
        const val CUSTOMIZATION_DESTINATION_KEYBOARD_THEME = "keyboard_theme"
        const val EXTRA_KEYBOARD_THEME_TARGET = "it.palsoftware.pastiera.KEYBOARD_THEME_TARGET"
        const val KEYBOARD_THEME_TARGET_SOFTWARE = "software"
    }

    // A request is an event, not persistent screen state: the serial lets the same
    // link fire again via onNewIntent, while restored activities do not replay it.
    private val settingLinkRequest = mutableStateOf<SettingLinkRequest?>(null)
    private var nextSettingLinkSerial = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.putSettingsPage(intent.settingsPage())
        enableEdgeToEdge()
        settingLinkRequest.value = if (savedInstanceState == null) {
            resolveSettingLinkId(intent)?.let(::newSettingLinkRequest)
        } else {
            null
        }
        setContent {
            PastieraTheme {
                val linkRequest by settingLinkRequest
                SettingsScreen(
                    modifier = Modifier.fillMaxSize(),
                    initialDestination = intent.getStringExtra(EXTRA_DESTINATION),
                    initialCustomizationDestination = intent.getStringExtra(EXTRA_CUSTOMIZATION_DESTINATION),
                    initialKeyboardThemeTarget = intent.getStringExtra(EXTRA_KEYBOARD_THEME_TARGET),
                    settingLinkRequest = linkRequest
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveSettingLinkId(intent)?.let { linkId ->
            settingLinkRequest.value = newSettingLinkRequest(linkId)
        }
    }

    private fun newSettingLinkRequest(id: String): SettingLinkRequest =
        SettingLinkRequest(id = id, serial = ++nextSettingLinkSerial)

    private fun resolveSettingLinkId(intent: Intent?): String? =
        intent?.data?.let(SettingLinkRegistry::parseSettingLinkUri)

}
