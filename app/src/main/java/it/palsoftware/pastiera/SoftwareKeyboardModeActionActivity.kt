package it.palsoftware.pastiera

import android.os.Bundle
import android.widget.Toast

class SoftwareKeyboardModeActionActivity : LocalizedComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent()
        finish()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent()
        finish()
    }

    private fun handleIntent() {
        if (intent?.action != SoftwareKeyboardModeActions.ACTION_TOGGLE) {
            return
        }
        val next = SoftwareKeyboardModeActions.toggleTemporaryMode(this)
        if (SettingsManager.getSoftwareKeyboardModeToggleToastsEnabled(this)) {
            Toast.makeText(this, labelFor(next), Toast.LENGTH_SHORT).show()
        }
    }

    private fun labelFor(mode: SettingsManager.SoftwareKeyboardMode): String =
        when (mode) {
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL ->
                getString(R.string.software_keyboard_mode_toggle_now_virtual)
            SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE ->
                getString(R.string.software_keyboard_mode_toggle_now_hardware)
            SettingsManager.SoftwareKeyboardMode.AUTO ->
                getString(R.string.software_keyboard_mode_auto_short)
        }
}
