package it.palsoftware.pastiera

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.inputmethod.NotificationHelper

@Composable
fun TypingSoundSettingsRow() {
    val context = LocalContext.current
    var typingSoundMode by remember {
        mutableStateOf(SettingsManager.getTypingSoundMode(context))
    }
    var typingSoundOutputMode by remember {
        mutableStateOf(SettingsManager.getTypingSoundOutputMode(context))
    }
    var customTypingSoundName by remember {
        mutableStateOf(SettingsManager.getTypingSoundCustomDisplayName(context))
    }
    var tapHapticUseSystem by remember {
        mutableStateOf(SettingsManager.getTapHapticUseSystem(context))
    }
    var tapHapticDurationMs by remember {
        mutableStateOf(SettingsManager.getTapHapticDurationMs(context).toFloat())
    }
    var showTypingSoundMenu by remember { mutableStateOf(false) }
    var showOutputMenu by remember { mutableStateOf(false) }

    val typingSoundImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val imported = SettingsManager.importTypingSound(context, uri)
            if (imported) {
                typingSoundMode = SettingsManager.getTypingSoundMode(context)
                customTypingSoundName = SettingsManager.getTypingSoundCustomDisplayName(context)
                Toast.makeText(context, R.string.typing_sound_import_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, R.string.typing_sound_import_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .settingRow("sounds.typing_mode") { showTypingSoundMenu = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Keyboard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.typing_sound_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = typingSoundModeLabel(typingSoundMode, customTypingSoundName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = showTypingSoundMenu,
            onDismissRequest = { showTypingSoundMenu = false }
        ) {
            TypingSoundDropdownItem(
                text = stringResource(R.string.typing_sound_off),
                selected = typingSoundMode == SettingsManager.TYPING_SOUND_MODE_OFF,
                onClick = {
                    typingSoundMode = SettingsManager.TYPING_SOUND_MODE_OFF
                    SettingsManager.setTypingSoundMode(context, typingSoundMode)
                    showTypingSoundMenu = false
                }
            )
            TypingSoundDropdownItem(
                text = stringResource(R.string.typing_sound_click),
                selected = typingSoundMode == SettingsManager.TYPING_SOUND_MODE_CLICK,
                onClick = {
                    typingSoundMode = SettingsManager.TYPING_SOUND_MODE_CLICK
                    SettingsManager.setTypingSoundMode(context, typingSoundMode)
                    showTypingSoundMenu = false
                }
            )
            TypingSoundDropdownItem(
                text = stringResource(R.string.typing_sound_typewriter),
                selected = typingSoundMode == SettingsManager.TYPING_SOUND_MODE_TYPEWRITER,
                onClick = {
                    typingSoundMode = SettingsManager.TYPING_SOUND_MODE_TYPEWRITER
                    SettingsManager.setTypingSoundMode(context, typingSoundMode)
                    showTypingSoundMenu = false
                }
            )
            TypingSoundDropdownItem(
                text = stringResource(R.string.typing_sound_custom),
                selected = typingSoundMode == SettingsManager.TYPING_SOUND_MODE_CUSTOM,
                onClick = {
                    showTypingSoundMenu = false
                    typingSoundImportLauncher.launch(
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream"
                        )
                    )
                }
            )
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .settingRow("sounds.output") { showOutputMenu = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Keyboard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.typing_sound_output_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = typingSoundOutputModeLabel(typingSoundOutputMode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.typing_sound_output_dnd_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = showOutputMenu,
            onDismissRequest = { showOutputMenu = false }
        ) {
            TypingSoundDropdownItem(
                text = stringResource(R.string.typing_sound_output_media),
                selected = typingSoundOutputMode == SettingsManager.TYPING_SOUND_OUTPUT_MEDIA,
                onClick = {
                    typingSoundOutputMode = SettingsManager.TYPING_SOUND_OUTPUT_MEDIA
                    SettingsManager.setTypingSoundOutputMode(context, typingSoundOutputMode)
                    showOutputMenu = false
                }
            )
            TypingSoundDropdownItem(
                text = stringResource(R.string.typing_sound_output_system),
                selected = typingSoundOutputMode == SettingsManager.TYPING_SOUND_OUTPUT_SYSTEM,
                onClick = {
                    typingSoundOutputMode = SettingsManager.TYPING_SOUND_OUTPUT_SYSTEM
                    SettingsManager.setTypingSoundOutputMode(context, typingSoundOutputMode)
                    showOutputMenu = false
                }
            )
            TypingSoundDropdownItem(
                text = stringResource(R.string.typing_sound_output_notification),
                selected = typingSoundOutputMode == SettingsManager.TYPING_SOUND_OUTPUT_NOTIFICATION,
                onClick = {
                    typingSoundOutputMode = SettingsManager.TYPING_SOUND_OUTPUT_NOTIFICATION
                    SettingsManager.setTypingSoundOutputMode(context, typingSoundOutputMode)
                    showOutputMenu = false
                }
            )
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth().settingRow("sounds.system_haptics")
            .heightIn(min = 72.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Keyboard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tap_haptic_system_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.tap_haptic_system_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Switch(
                checked = tapHapticUseSystem,
                onCheckedChange = { enabled ->
                    tapHapticUseSystem = enabled
                    SettingsManager.setTapHapticUseSystem(context, enabled)
                }
            )
        }
    }

    if (!tapHapticUseSystem) {
        Surface(
            modifier = Modifier
                .fillMaxWidth().settingRow("sounds.haptic_duration")
                .heightIn(min = 104.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.tap_haptic_duration_title, tapHapticDurationMs.toInt()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.tap_haptic_duration_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Slider(
                    value = tapHapticDurationMs,
                    onValueChange = { value ->
                        val rounded = (value / 5f).toInt() * 5L
                        if (rounded == tapHapticDurationMs.toLong()) {
                            return@Slider
                        }
                        tapHapticDurationMs = rounded.toFloat()
                        SettingsManager.setTapHapticDurationMs(context, rounded)
                        NotificationHelper.triggerFixedDurationHapticFeedback(context, rounded)
                    },
                    valueRange = SettingsManager.getMinTapHapticDurationMs().toFloat()..
                        SettingsManager.getMaxTapHapticDurationMs().toFloat(),
                    steps = 14
                )
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(context.getString(R.string.typing_sound_docs_url))
                )
                context.startActivity(intent)
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.typing_sound_docs_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.typing_sound_docs_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TypingSoundDropdownItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null)
            }
        }
    )
}

@Composable
private fun typingSoundModeLabel(mode: String, customName: String?): String {
    return when (mode) {
        SettingsManager.TYPING_SOUND_MODE_CLICK -> stringResource(R.string.typing_sound_click)
        SettingsManager.TYPING_SOUND_MODE_TYPEWRITER -> stringResource(R.string.typing_sound_typewriter)
        SettingsManager.TYPING_SOUND_MODE_CUSTOM -> customName ?: stringResource(R.string.typing_sound_custom)
        else -> stringResource(R.string.typing_sound_off)
    }
}

@Composable
private fun typingSoundOutputModeLabel(mode: String): String {
    return when (mode) {
        SettingsManager.TYPING_SOUND_OUTPUT_SYSTEM -> stringResource(R.string.typing_sound_output_system)
        SettingsManager.TYPING_SOUND_OUTPUT_NOTIFICATION -> stringResource(R.string.typing_sound_output_notification)
        else -> stringResource(R.string.typing_sound_output_media)
    }
}
