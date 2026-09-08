package it.palsoftware.pastiera

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ModifierSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenSymLayers: () -> Unit,
    onOpenSymShortcuts: () -> Unit,
    onOpenNavMode: () -> Unit
) {
    val context = LocalContext.current
    var longPressModifier by remember { mutableStateOf(SettingsManager.getLongPressModifier(context)) }
    var longPressThreshold by remember { mutableStateOf(SettingsManager.getLongPressThreshold(context)) }
    var altBinding by remember { mutableStateOf(SettingsManager.getAltModifierBinding(context)) }
    var shiftTapLatches by remember { mutableStateOf(SettingsManager.getShiftTapLatches(context)) }
    var altTapLatches by remember { mutableStateOf(SettingsManager.getAltTapLatches(context)) }
    var altLatchStaysOnSpace by remember { mutableStateOf(SettingsManager.getAltLatchStaysOnSpace(context)) }
    var ctrlTapLatches by remember { mutableStateOf(SettingsManager.getCtrlTapLatches(context)) }
    var ctrlLatchStaysOnSpace by remember { mutableStateOf(SettingsManager.getCtrlLatchStaysOnSpace(context)) }
    var modifierIndicators by remember { mutableStateOf(SettingsManager.getModifierIndicators(context)) }
    var showSymShortcutCompatibilityInfo by remember { mutableStateOf(false) }
    var longPressExpanded by remember { mutableStateOf(false) }
    var altBindingExpanded by remember { mutableStateOf(false) }

    val longPressOptions = listOf(
        "alt" to stringResource(R.string.long_press_modifier_alt),
        "shift" to stringResource(R.string.long_press_modifier_shift),
        "variations" to stringResource(R.string.long_press_modifier_variations),
        "sym" to stringResource(R.string.long_press_modifier_sym),
        "sym_symbols" to stringResource(R.string.long_press_modifier_sym_symbols),
        "sym_emoji" to stringResource(R.string.long_press_modifier_sym_emoji)
    )
    val altBindingOptions = listOf(
        AltModifierBinding.DeviceSym to stringResource(R.string.alt_binding_current_device),
        AltModifierBinding.FirstEnabledSymKeyLayer to stringResource(R.string.alt_binding_first_key_layer),
        AltModifierBinding.Emoji to stringResource(R.string.sym_cycle_emoji_layer),
        AltModifierBinding.Symbols to stringResource(R.string.sym_cycle_symbols_layer),
        AltModifierBinding.DeviceSymProfile("key2") to stringResource(R.string.keyboard_profile_option_key2),
        AltModifierBinding.DeviceSymProfile("Q25") to stringResource(R.string.keyboard_profile_option_q25),
        AltModifierBinding.DeviceSymProfile("titan") to stringResource(R.string.keyboard_profile_option_titan),
        AltModifierBinding.DeviceSymProfile("titan2") to stringResource(R.string.keyboard_profile_option_titan2),
        AltModifierBinding.DeviceSymProfile("titan2elite_qwerty") to stringResource(R.string.keyboard_profile_option_titan2elite_qwerty),
        AltModifierBinding.DeviceSymProfile("mp01") to stringResource(R.string.keyboard_profile_option_mp01),
        AltModifierBinding.DeviceSymProfile("clicks_razr") to stringResource(R.string.keyboard_profile_option_clicks_razr),
        AltModifierBinding.DeviceSymProfile("clicks_pixel") to stringResource(R.string.keyboard_profile_option_clicks_pixel),
        AltModifierBinding.DeviceSymProfile("clicks_power") to stringResource(R.string.clicks_power_keyboard_title)
    )

    BackHandler { onBack() }
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back_content_description))
                    }
                    Text(
                        text = stringResource(R.string.modifiers_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier.fillMaxWidth().padding(paddingValues).verticalScroll(rememberScrollState())
        ) {
            SettingsSectionDivider(stringResource(R.string.modifiers_section_long_press))
            ModifierDropdownRow(
                title = stringResource(R.string.long_press_modifier_title),
                value = longPressOptions.firstOrNull { it.first == longPressModifier }?.second.orEmpty(),
                linkId = SettingLinkIds.MODIFIERS_LONG_PRESS_MODIFIER,
                expanded = longPressExpanded,
                onExpand = { longPressExpanded = true },
                onDismiss = { longPressExpanded = false },
                options = longPressOptions,
                onSelected = { value ->
                    longPressModifier = value
                    SettingsManager.setLongPressModifier(context, value)
                    longPressExpanded = false
                }
            )
            ModifierLongPressThresholdRow(
                threshold = longPressThreshold,
                linkId = SettingLinkIds.MODIFIERS_LONG_PRESS_THRESHOLD,
                onThresholdChanged = { threshold ->
                    longPressThreshold = threshold
                    SettingsManager.setLongPressThreshold(context, threshold)
                }
            )

            SettingsSectionDivider(stringResource(R.string.modifiers_section_indicators))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .settingRow(SettingLinkIds.MODIFIERS_INDICATORS)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.modifier_indicators_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ModifierIndicatorMultiSelect(
                        modifier = Modifier.fillMaxWidth(),
                        selectedIndicators = modifierIndicators,
                        onIndicatorsSelected = { indicators ->
                            modifierIndicators = indicators
                            SettingsManager.setModifierIndicators(context, indicators)
                        }
                    )
                }
            }

            SettingsSectionDivider(stringResource(R.string.modifiers_section_sym))
            ModifierNavigationRow(
                iconRes = R.drawable.ic_emoji_symbols_24,
                title = stringResource(R.string.sym_customization_title),
                description = stringResource(R.string.sym_customization_description),
                linkId = SettingLinkIds.MODIFIERS_SYM_LAYERS,
                onClick = onOpenSymLayers
            )
            ModifierNavigationRow(
                iconRes = R.drawable.ic_search_24,
                title = stringResource(R.string.power_shortcuts_title),
                description = stringResource(R.string.power_shortcuts_description),
                linkId = SettingLinkIds.MODIFIERS_SYM_SHORTCUTS,
                onClick = onOpenSymShortcuts,
                onInfoClick = { showSymShortcutCompatibilityInfo = true }
            )

            SettingsSectionDivider(stringResource(R.string.modifiers_section_alt_modifier))
            ModifierDropdownRow(
                title = stringResource(R.string.alt_binding_title),
                description = stringResource(R.string.alt_binding_description),
                value = altBindingOptions.firstOrNull { it.first == altBinding }?.second
                    ?: altBindingOptions.first().second,
                linkId = SettingLinkIds.MODIFIERS_ALT_BINDING,
                expanded = altBindingExpanded,
                onExpand = { altBindingExpanded = true },
                onDismiss = { altBindingExpanded = false },
                options = altBindingOptions,
                onSelected = { value ->
                    altBinding = value
                    SettingsManager.setAltModifierBinding(context, value)
                    altBindingExpanded = false
                }
            )
            ModifierNavigationRow(
                iconRes = R.drawable.keyboard_option_key_24,
                title = stringResource(R.string.alt_key_shortcuts_title),
                description = stringResource(R.string.alt_key_shortcuts_modifier_link_description),
                linkId = SettingLinkIds.MODIFIERS_ALT_KEY_SHORTCUTS,
                onClick = onOpenSymShortcuts
            )

            SettingsSectionDivider(stringResource(R.string.modifiers_section_control))
            ModifierNavigationRow(
                iconRes = R.drawable.navigation_24,
                title = stringResource(R.string.modifier_control_nav_mode_title),
                description = stringResource(R.string.modifier_control_nav_mode_description),
                linkId = SettingLinkIds.MODIFIERS_CONTROL_NAV_MODE,
                onClick = onOpenNavMode
            )

            SettingsSectionDivider(stringResource(R.string.modifier_tap_behaviour_title))
            ModifierSwitchRow(
                title = stringResource(R.string.shift_tap_latches_title),
                description = stringResource(R.string.shift_tap_latches_description),
                checked = shiftTapLatches,
                linkId = SettingLinkIds.MODIFIERS_SHIFT_TAP_LATCHES
            ) {
                shiftTapLatches = it
                SettingsManager.setShiftTapLatches(context, it)
            }
            ModifierSwitchRow(
                title = stringResource(R.string.alt_tap_latches_title),
                description = stringResource(R.string.alt_tap_latches_description),
                checked = altTapLatches,
                linkId = SettingLinkIds.MODIFIERS_ALT_TAP_LATCHES
            ) {
                altTapLatches = it
                SettingsManager.setAltTapLatches(context, it)
            }
            ModifierSwitchRow(
                title = stringResource(R.string.alt_latch_stays_on_space_title),
                description = stringResource(R.string.alt_latch_stays_on_space_description),
                checked = altLatchStaysOnSpace,
                indent = true,
                linkId = SettingLinkIds.MODIFIERS_ALT_LATCH_STAYS_ON_SPACE
            ) {
                altLatchStaysOnSpace = it
                SettingsManager.setAltLatchStaysOnSpace(context, it)
            }
            ModifierSwitchRow(
                title = stringResource(R.string.ctrl_tap_latches_title),
                description = stringResource(R.string.ctrl_tap_latches_description),
                checked = ctrlTapLatches,
                linkId = SettingLinkIds.MODIFIERS_CTRL_TAP_LATCHES
            ) {
                ctrlTapLatches = it
                SettingsManager.setCtrlTapLatches(context, it)
            }
            if (ctrlTapLatches) {
                ModifierSwitchRow(
                    title = stringResource(R.string.ctrl_latch_stays_on_space_title),
                    description = stringResource(R.string.ctrl_latch_stays_on_space_description),
                    checked = ctrlLatchStaysOnSpace,
                    indent = true,
                    linkId = SettingLinkIds.MODIFIERS_CTRL_LATCH_STAYS_ON_SPACE
                ) {
                    ctrlLatchStaysOnSpace = it
                    SettingsManager.setCtrlLatchStaysOnSpace(context, it)
                }
            }

        }
    }

    if (showSymShortcutCompatibilityInfo) {
        AlertDialog(
            onDismissRequest = { showSymShortcutCompatibilityInfo = false },
            icon = { Icon(Icons.Filled.Info, contentDescription = null) },
            title = { Text(stringResource(R.string.sym_shortcuts_compatibility_title)) },
            text = { Text(stringResource(R.string.sym_shortcuts_compatibility_description)) },
            confirmButton = {
                TextButton(onClick = { showSymShortcutCompatibilityInfo = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun ModifierLongPressThresholdRow(
    threshold: Long,
    linkId: String? = null,
    onThresholdChanged: (Long) -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth().settingRow(linkId)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                    text = stringResource(R.string.long_press_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.keyboard_timing_long_press_value, threshold),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = threshold.toFloat(),
                    onValueChange = { value ->
                        onThresholdChanged(
                            value.toLong().coerceIn(
                                SettingsManager.getMinLongPressThreshold(),
                                SettingsManager.getMaxLongPressThreshold()
                            )
                        )
                    },
                    valueRange = SettingsManager.getMinLongPressThreshold().toFloat()..
                        SettingsManager.getMaxLongPressThreshold().toFloat(),
                    steps = 18
                )
            }
        }
    }
}

@Composable
private fun ModifierNavigationRow(
    iconRes: Int,
    title: String,
    description: String,
    linkId: String? = null,
    onClick: () -> Unit,
    onInfoClick: (() -> Unit)? = null
) {
    Surface(modifier = Modifier.fillMaxWidth().settingRow(linkId, onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            onInfoClick?.let { infoClick ->
                IconButton(onClick = infoClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = stringResource(R.string.sym_shortcuts_compatibility_info),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
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
private fun <T> ModifierDropdownRow(
    title: String,
    value: String,
    linkId: String? = null,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    description: String? = null
) {
    Surface(modifier = Modifier.fillMaxWidth().settingRow(linkId, onExpand)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Keyboard, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.Filled.ArrowDropDown, null)
            DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
                options.forEach { (id, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onSelected(id) })
                }
            }
        }
    }
}

@Composable
private fun ModifierSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    indent: Boolean = false,
    linkId: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).settingRow(linkId)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = if (indent) 52.dp else 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!indent) {
                Icon(Icons.Filled.Keyboard, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
