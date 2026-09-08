package it.palsoftware.pastiera

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import kotlin.math.roundToLong

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
    var bounceKeysEnabled by remember {
        mutableStateOf(SettingsManager.getBounceKeysEnabled(context))
    }
    var bounceKeysDelayMs by remember {
        mutableStateOf(SettingsManager.getBounceKeysDelayMs(context))
    }
    var bounceCharacterKeysEnabled by remember {
        mutableStateOf(SettingsManager.getBounceKeysCharacterKeysEnabled(context))
    }
    var bounceModifierKeysEnabled by remember {
        mutableStateOf(SettingsManager.getBounceKeysModifierKeysEnabled(context))
    }
    var bounceSpaceEnabled by remember {
        mutableStateOf(SettingsManager.getBounceKeysSpaceEnabled(context))
    }
    var bounceEnterEnabled by remember {
        mutableStateOf(SettingsManager.getBounceKeysEnterEnabled(context))
    }
    var bounceBackspaceEnabled by remember {
        mutableStateOf(SettingsManager.getBounceKeysBackspaceEnabled(context))
    }
    var overlappingKeysEnabled by remember {
        mutableStateOf(SettingsManager.getOverlappingKeysEnabled(context))
    }


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
                    .heightIn(min = 96.dp)
                    .settingRow(SettingLinkIds.ACCESSIBILITY_LIVE_READ)
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
                    .heightIn(min = 84.dp)
                    .settingRow(SettingLinkIds.ACCESSIBILITY_READ_SECOND_ROW)
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
                    .heightIn(min = 84.dp)
                    .settingRow(SettingLinkIds.ACCESSIBILITY_SUGGESTIONS_DELAY)
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

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .settingRow(SettingLinkIds.ACCESSIBILITY_BOUNCE_KEYS)
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
                            text = stringResource(R.string.settings_accessibility_bounce_keys_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.settings_accessibility_bounce_keys_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = bounceKeysEnabled,
                        onCheckedChange = { enabled ->
                            bounceKeysEnabled = enabled
                            SettingsManager.setBounceKeysEnabled(context, enabled)
                        }
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 116.dp)
                    .settingRow("accessibility.bounce_keys_delay")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.settings_accessibility_bounce_keys_delay_title,
                            bounceKeysDelayMs
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_accessibility_bounce_keys_delay_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = bounceKeysDelayMs.toFloat(),
                        onValueChange = { value ->
                            val updated = (value / 10f).roundToLong() * 10L
                            bounceKeysDelayMs = updated.coerceIn(
                                SettingsManager.getMinBounceKeysDelayMs(),
                                SettingsManager.getMaxBounceKeysDelayMs()
                            )
                        },
                        onValueChangeFinished = {
                            SettingsManager.setBounceKeysDelayMs(context, bounceKeysDelayMs)
                        },
                        valueRange = SettingsManager.getMinBounceKeysDelayMs().toFloat()..
                            SettingsManager.getMaxBounceKeysDelayMs().toFloat(),
                        enabled = bounceKeysEnabled
                    )
                }
            }

            BounceKeyToggleRow(
                title = stringResource(R.string.settings_accessibility_bounce_keys_character_keys_title),
                description = stringResource(R.string.settings_accessibility_bounce_keys_character_keys_description),
                checked = bounceCharacterKeysEnabled,
                enabled = bounceKeysEnabled,
                linkId = SettingLinkIds.ACCESSIBILITY_BOUNCE_KEYS_CHARACTER_KEYS,
                onCheckedChange = { enabled ->
                    bounceCharacterKeysEnabled = enabled
                    SettingsManager.setBounceKeysCharacterKeysEnabled(context, enabled)
                }
            )
            BounceKeyToggleRow(
                title = stringResource(R.string.settings_accessibility_bounce_keys_modifier_keys_title),
                description = stringResource(R.string.settings_accessibility_bounce_keys_modifier_keys_description),
                checked = bounceModifierKeysEnabled,
                enabled = bounceKeysEnabled,
                linkId = SettingLinkIds.ACCESSIBILITY_BOUNCE_KEYS_MODIFIER_KEYS,
                onCheckedChange = { enabled ->
                    bounceModifierKeysEnabled = enabled
                    SettingsManager.setBounceKeysModifierKeysEnabled(context, enabled)
                }
            )
            Text(
                text = stringResource(R.string.settings_accessibility_bounce_keys_text_control_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            BounceKeyToggleRow(
                title = stringResource(R.string.settings_accessibility_bounce_keys_space_title),
                description = stringResource(R.string.settings_accessibility_bounce_keys_space_description),
                checked = bounceSpaceEnabled,
                enabled = bounceKeysEnabled,
                linkId = SettingLinkIds.ACCESSIBILITY_BOUNCE_KEYS_SPACE,
                onCheckedChange = { enabled ->
                    bounceSpaceEnabled = enabled
                    SettingsManager.setBounceKeysSpaceEnabled(context, enabled)
                }
            )
            BounceKeyToggleRow(
                title = stringResource(R.string.settings_accessibility_bounce_keys_enter_title),
                description = stringResource(R.string.settings_accessibility_bounce_keys_enter_description),
                checked = bounceEnterEnabled,
                enabled = bounceKeysEnabled,
                linkId = SettingLinkIds.ACCESSIBILITY_BOUNCE_KEYS_ENTER,
                onCheckedChange = { enabled ->
                    bounceEnterEnabled = enabled
                    SettingsManager.setBounceKeysEnterEnabled(context, enabled)
                }
            )
            BounceKeyToggleRow(
                title = stringResource(R.string.settings_accessibility_bounce_keys_backspace_title),
                description = stringResource(R.string.settings_accessibility_bounce_keys_backspace_description),
                checked = bounceBackspaceEnabled,
                enabled = bounceKeysEnabled,
                linkId = SettingLinkIds.ACCESSIBILITY_BOUNCE_KEYS_BACKSPACE,
                onCheckedChange = { enabled ->
                    bounceBackspaceEnabled = enabled
                    SettingsManager.setBounceKeysBackspaceEnabled(context, enabled)
                }
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 104.dp)
                    .settingRow(SettingLinkIds.ACCESSIBILITY_OVERLAPPING_KEYS)
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
                            text = stringResource(R.string.settings_accessibility_overlapping_keys_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.settings_accessibility_overlapping_keys_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = overlappingKeysEnabled,
                        onCheckedChange = { enabled ->
                            overlappingKeysEnabled = enabled
                            SettingsManager.setOverlappingKeysEnabled(context, enabled)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BounceKeyToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    linkId: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .settingRow(linkId)
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
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
