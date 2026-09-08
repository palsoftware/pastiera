package it.palsoftware.pastiera

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.inputmethod.DeviceSpecific

private val hardwareKeyboardProfiles = listOf(
    "auto" to R.string.keyboard_profile_option_auto,
    "key2" to R.string.keyboard_profile_option_key2,
    "Q25" to R.string.keyboard_profile_option_q25,
    "titan" to R.string.keyboard_profile_option_titan,
    "titan2" to R.string.keyboard_profile_option_titan2,
    "titan2elite_qwerty" to R.string.keyboard_profile_option_titan2elite_qwerty,
    "mp01" to R.string.keyboard_profile_option_mp01,
    "clicks_razr" to R.string.keyboard_profile_option_clicks_razr,
    "clicks_pixel" to R.string.keyboard_profile_option_clicks_pixel,
    "clicks_power" to R.string.keyboard_profile_option_clicks_power_converted
)

@Composable
fun HardwareKeyboardSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    var destination by rememberSaveable { mutableStateOf(HardwareKeyboardDestination.Main) }

    val settingHighlight = LocalSettingHighlightId.current
    androidx.compose.runtime.LaunchedEffect(settingHighlight) {
        if (settingHighlight != null) destination = HardwareKeyboardDestination.Main
    }

    when (destination) {
        HardwareKeyboardDestination.Main -> HardwareKeyboardListScreen(
            modifier = modifier,
            onBack = onBack,
            onDeviceSymLayerEditor = { destination = HardwareKeyboardDestination.DeviceSymLayerEditor }
        )
        HardwareKeyboardDestination.DeviceSymLayerEditor -> DeviceSymLayerEditorStubScreen(
            modifier = modifier,
            onBack = { destination = HardwareKeyboardDestination.Main }
        )
    }
}

private enum class HardwareKeyboardDestination {
    Main,
    DeviceSymLayerEditor
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HardwareKeyboardListScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onDeviceSymLayerEditor: () -> Unit
) {
    val context = LocalContext.current
    var selectedProfile by remember {
        mutableStateOf(SettingsManager.getPhysicalKeyboardProfileOverride(context))
    }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var currencySymbol by remember {
        mutableStateOf(SettingsManager.getPhysicalKeyboardCurrencySymbol(context))
    }
    var titan2LayoutEnabled by remember {
        mutableStateOf(SettingsManager.isTitan2LayoutEnabled(context))
    }
    val detectedProfiles = remember { DeviceSpecific.detectedInputProfiles() }
    val detectedProfileLabels = detectedProfiles
        .map { it.profileId }
        .ifEmpty { listOf(DeviceSpecific.physicalKeyboardName()) }
        .map { profileId ->
            hardwareKeyboardProfiles
                .firstOrNull { it.first.equals(profileId, ignoreCase = true) }
                ?.let { stringResource(it.second) }
                ?: profileId
        }
    val detectedProfile = detectedProfileLabels.joinToString(", ")


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
                        text = stringResource(R.string.hardware_keyboards_title),
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
            Text(
                text = stringResource(R.string.hardware_keyboards_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            HardwareKeyboardSectionDivider(stringResource(R.string.hardware_keyboard_detected_device_title))

            Text(
                text = stringResource(R.string.hardware_keyboard_detected_device_summary, DeviceSpecific.deviceName(), detectedProfile),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Text(
                text = stringResource(R.string.hardware_keyboard_device_bound),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            HardwareKeyboardSectionDivider(stringResource(R.string.hardware_keyboard_curated_profiles_title))

            val selectedProfileLabel = hardwareKeyboardProfiles
                .firstOrNull { it.first == selectedProfile }
                ?.let { stringResource(it.second) }
                ?: stringResource(R.string.keyboard_profile_option_auto)
            ExposedDropdownMenuBox(
                expanded = profileMenuExpanded,
                onExpandedChange = { profileMenuExpanded = it },
                modifier = Modifier.settingRow("hardware.profile").padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = selectedProfileLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.hardware_keyboard_profile_select_label)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileMenuExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = profileMenuExpanded,
                    onDismissRequest = { profileMenuExpanded = false }
                ) {
                    hardwareKeyboardProfiles.forEach { (profile, labelRes) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(labelRes)) },
                            onClick = {
                                selectedProfile = profile
                                SettingsManager.setPhysicalKeyboardProfileOverride(context, profile)
                                profileMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Text(
                text = if (selectedProfile == "auto") {
                    stringResource(R.string.keyboard_profile_override_auto_description, detectedProfile)
                } else {
                    stringResource(R.string.keyboard_profile_override_manual_description)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            HardwareKeyboardSectionDivider(stringResource(R.string.hardware_keyboard_custom_profiles_title))
            Text(
                text = stringResource(R.string.hardware_keyboard_custom_profiles_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            HardwareKeyboardNavigationRow(
                title = stringResource(R.string.alt_key_editor_title),
                linkId = "hardware.alt_editor",
                description = stringResource(R.string.alt_key_editor_summary),
                icon = Icons.Filled.Edit,
                status = FeatureStatus.Construction,
                onClick = onDeviceSymLayerEditor
            )

            HardwareKeyboardSectionDivider(stringResource(R.string.hardware_keyboard_behavior_title))

            HardwareKeyboardSwitchRow(
                title = stringResource(R.string.titan2_layout_title),
                linkId = "hardware.titan2_layout",
                description = stringResource(R.string.titan2_layout_description),
                checked = titan2LayoutEnabled,
                onCheckedChange = { enabled ->
                    titan2LayoutEnabled = enabled
                    SettingsManager.setTitan2LayoutEnabled(context, enabled)
                }
            )

            HardwareKeyboardSectionTitle(stringResource(R.string.keyboard_currency_symbol_title))
            Text(
                text = stringResource(R.string.keyboard_currency_symbol_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .settingRow("hardware.currency").padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SettingsManager.physicalKeyboardCurrencySymbols().forEach { symbol ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                currencySymbol = symbol
                                SettingsManager.setPhysicalKeyboardCurrencySymbol(context, symbol)
                            },
                        color = if (currencySymbol == symbol) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = symbol,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (currencySymbol == symbol) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(vertical = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareKeyboardSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun HardwareKeyboardSectionDivider(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun HardwareKeyboardNavigationRow(
    title: String,
    linkId: String? = null,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    status: FeatureStatus? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .settingRow(linkId, onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            status?.let { FeatureStatusIcon(it) }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HardwareKeyboardSwitchRow(
    title: String,
    linkId: String? = null,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(modifier = Modifier.settingRow(linkId).fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
