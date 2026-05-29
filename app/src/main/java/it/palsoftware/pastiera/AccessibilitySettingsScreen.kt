package it.palsoftware.pastiera

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import it.palsoftware.pastiera.accessibility.LockscreenPinEntry
import it.palsoftware.pastiera.inputmethod.DeviceSpecific

@Composable
fun AccessibilitySettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var liveAnnouncementsEnabled by remember {
        mutableStateOf(SettingsManager.getAccessibilityLiveAnnouncementsEnabled(context))
    }
    var readSecondRowEnabled by remember {
        mutableStateOf(SettingsManager.getAccessibilityReadSecondRowEnabled(context))
    }
    var suggestionsAnnouncementDelayMs by remember {
        mutableStateOf(SettingsManager.getAccessibilitySuggestionsAnnouncementDelayMs(context))
    }
    var pinInputOnLockscreenEnabled by remember {
        mutableStateOf(SettingsManager.getLockscreenPinEntry(context))
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_category_accessibility),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_accessibility_lockscreen_pin_entry),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.settings_accessibility_lockscreen_pin_entry_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = pinInputOnLockscreenEnabled,
                        enabled = DeviceSpecific.isPhysicalKeyboardDevice(),
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (LockscreenPinEntry.connected.value) {
                                    pinInputOnLockscreenEnabled = enabled
                                    SettingsManager.setLockscreenPinEntry(context, enabled)
                                } else {
                                    // Open accessibility settings to enable the service
                                    val intent =
                                        Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                }
                            }
                        }
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_accessibility_live_read_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.settings_accessibility_live_read_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = liveAnnouncementsEnabled,
                        onCheckedChange = { enabled ->
                            liveAnnouncementsEnabled = enabled
                            SettingsManager.setAccessibilityLiveAnnouncementsEnabled(context, enabled)
                        }
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_accessibility_second_row_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.settings_accessibility_second_row_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = readSecondRowEnabled,
                        onCheckedChange = { enabled ->
                            readSecondRowEnabled = enabled
                            SettingsManager.setAccessibilityReadSecondRowEnabled(context, enabled)
                        }
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_accessibility_suggestions_delay_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_accessibility_suggestions_delay_value,
                                suggestionsAnnouncementDelayMs
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val updated = (suggestionsAnnouncementDelayMs - 50L).coerceAtLeast(
                                SettingsManager.getMinAccessibilitySuggestionsAnnouncementDelayMs()
                            )
                            suggestionsAnnouncementDelayMs = updated
                            SettingsManager.setAccessibilitySuggestionsAnnouncementDelayMs(context, updated)
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_accessibility_delay_feedback, updated),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        enabled = suggestionsAnnouncementDelayMs > SettingsManager.getMinAccessibilitySuggestionsAnnouncementDelayMs()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = stringResource(R.string.settings_accessibility_delay_decrease)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val updated = (suggestionsAnnouncementDelayMs + 50L).coerceAtMost(
                                SettingsManager.getMaxAccessibilitySuggestionsAnnouncementDelayMs()
                            )
                            suggestionsAnnouncementDelayMs = updated
                            SettingsManager.setAccessibilitySuggestionsAnnouncementDelayMs(context, updated)
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_accessibility_delay_feedback, updated),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        enabled = suggestionsAnnouncementDelayMs < SettingsManager.getMaxAccessibilitySuggestionsAnnouncementDelayMs()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.settings_accessibility_delay_increase)
                        )
                    }
                }
            }
        }
    }
}
