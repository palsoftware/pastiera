package it.palsoftware.pastiera

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

private data class EnterBehaviorApp(
    val packageName: String,
    val label: String,
    val icon: Drawable? = null,
    val isFavorite: Boolean = false
)

private const val WHATSAPP_PACKAGE_NAME = "com.whatsapp"
private const val DISCORD_PACKAGE_NAME = "com.discord"
private const val FACEBOOK_MESSENGER_PACKAGE_NAME = "com.facebook.orca"
internal const val ENTER_ADDITIONAL_SEND_SHORTCUT_CUSTOM = "custom"
private val testedEnterBehaviorPackages = setOf(
    WHATSAPP_PACKAGE_NAME,
    "org.telegram.messenger",
    "im.vector.app",
    "com.google.android.apps.messaging",
    "ch.threema.app",
    "ch.threema.app.libre",
    "com.instagram.android",
    FACEBOOK_MESSENGER_PACKAGE_NAME
)

private val favoriteEnterBehaviorApps = listOf(
    WHATSAPP_PACKAGE_NAME,
    "org.telegram.messenger",
    "org.thoughtcrime.securesms",
    DISCORD_PACKAGE_NAME,
    "im.vector.app",
    "com.google.android.apps.messaging",
    "ch.threema.app",
    "ch.threema.app.libre",
    "com.instagram.android",
    FACEBOOK_MESSENGER_PACKAGE_NAME
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppEnterBehaviorScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenLauncherShortcutAssignments: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val storedPreset = remember(context) {
        SettingsManager.getAppEnterBehaviorPreset(context)
    }
    val initialOverrides = remember(context, storedPreset) {
        loadInitialEnterBehaviorOverrides(context, storedPreset)
    }
    val initialPreset = remember(initialOverrides, storedPreset) {
        inferKnownAppEnterBehaviorPreset(initialOverrides) ?: storedPreset
    }
    var enabled by remember { mutableStateOf(SettingsManager.getAppEnterBehaviorEnabled(context)) }
    var preset by remember { mutableStateOf(initialPreset) }
    var overrides by remember { mutableStateOf(initialOverrides) }
    var showAddDialog by remember { mutableStateOf(false) }
    var bringIntoViewPackage by remember { mutableStateOf<String?>(null) }
    val quickLauncherUsesSymEnter = SettingsManager.getQuickLauncherShortcutKey(context) == android.view.KeyEvent.KEYCODE_ENTER
    val selectedAdditionalSendShortcut = commonAdditionalSendShortcut(overrides)
    val anyAppUsesSymEnter = overrides.any {
        it.additionalSendShortcut == SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_SYM_ENTER
    }

    fun persistDerivedPreset(updatedOverrides: List<SettingsManager.AppEnterBehaviorOverride>) {
        val derivedPreset = inferKnownAppEnterBehaviorPreset(updatedOverrides) ?: preset
        preset = derivedPreset
        SettingsManager.setAppEnterBehaviorPreset(context, derivedPreset)
    }

    LaunchedEffect(initialPreset, storedPreset) {
        if (initialPreset != storedPreset) {
            SettingsManager.setAppEnterBehaviorPreset(context, initialPreset)
        }
    }


    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
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
                    text = stringResource(R.string.app_enter_behaviour_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                )
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.app_enter_behaviour_add_app)
                    )
                }
            }
        }

        Surface(modifier = Modifier.fillMaxWidth().settingRow("app_enter_behavior.enabled")) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_enter_behaviour_enable_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.app_enter_behaviour_enable_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        SettingsManager.setAppEnterBehaviorEnabled(context, it)
                    }
                )
            }
        }

        EnterPresetSelector(
            preset = preset,
            onPresetSelected = { selected ->
                preset = selected
                SettingsManager.setAppEnterBehaviorPreset(context, selected)
                val updated = sortEnterBehaviorOverrides(context, applyPresetToKnownApps(context, selected, overrides))
                overrides = updated
                SettingsManager.setAppEnterBehaviorOverrides(context, updated)
            }
        )

        EnterAdditionalSendShortcutSelector(
            shortcut = selectedAdditionalSendShortcut,
            onShortcutSelected = { shortcut ->
                val updated = overrides.map { it.copy(additionalSendShortcut = shortcut) }
                overrides = updated
                SettingsManager.setAppEnterBehaviorOverrides(context, updated)
            }
        )

        if (
            anyAppUsesSymEnter &&
            quickLauncherUsesSymEnter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.WarningAmber, contentDescription = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_enter_behaviour_sym_enter_conflict_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.app_enter_behaviour_sym_enter_conflict_description),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (onOpenLauncherShortcutAssignments != null) {
                        TextButton(onClick = onOpenLauncherShortcutAssignments) {
                            Text(stringResource(R.string.app_enter_behaviour_sym_enter_conflict_action))
                        }
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.app_enter_behaviour_overrides_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                .settingRow("app_enter_behavior.overrides")
        )

        overrides.forEach { override ->
            val app = remember(override.packageName) {
                resolveApp(context, override.packageName)
            }
            EnterBehaviorOverrideRow(
                app = app,
                behavior = override.behavior,
                sendStrategy = explicitEnterSendStrategy(
                    packageName = override.packageName,
                    strategy = override.sendStrategy
                ),
                additionalSendShortcut = override.additionalSendShortcut,
                editable = !app.isFavorite,
                bringIntoView = override.packageName == bringIntoViewPackage,
                onBroughtIntoView = { bringIntoViewPackage = null },
                onBehaviorChanged = { behavior ->
                    val updated = overrides.map {
                        if (it.packageName == override.packageName) it.copy(behavior = behavior) else it
                    }
                    overrides = updated
                    persistDerivedPreset(updated)
                    SettingsManager.setAppEnterBehaviorOverrides(context, updated)
                },
                onSendStrategyChanged = { strategy ->
                    val updated = overrides.map {
                        if (it.packageName == override.packageName) it.copy(sendStrategy = strategy) else it
                    }
                    overrides = updated
                    SettingsManager.setAppEnterBehaviorOverrides(context, updated)
                },
                onAdditionalSendShortcutChanged = { shortcut ->
                    val updated = overrides.map {
                        if (it.packageName == override.packageName) it.copy(additionalSendShortcut = shortcut) else it
                    }
                    overrides = updated
                    SettingsManager.setAppEnterBehaviorOverrides(context, updated)
                },
                onRemove = {
                    val updated = overrides.filterNot { it.packageName == override.packageName }
                    overrides = updated
                    persistDerivedPreset(updated)
                    SettingsManager.setAppEnterBehaviorOverrides(context, updated)
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showAddDialog) {
        AddEnterBehaviorAppDialog(
            existingPackages = overrides.map { it.packageName }.toSet(),
            onDismiss = { showAddDialog = false },
            onAdd = { apps ->
                val behavior = enterBehaviorForPreset(preset)
                    ?: SettingsManager.ENTER_BEHAVIOR_APP_DEFAULT
                val additions = apps.map { app ->
                    SettingsManager.AppEnterBehaviorOverride(
                        packageName = app.packageName,
                        behavior = behavior,
                        sendStrategy = SettingsManager.ENTER_SEND_STRATEGY_EDITOR_ACTION,
                        additionalSendShortcut = additionalSendShortcutForNewApp(
                            selectedAdditionalSendShortcut
                        )
                    )
                }
                val updated = sortEnterBehaviorOverrides(
                    context,
                    overrides + additions
                )
                overrides = updated
                persistDerivedPreset(updated)
                SettingsManager.setAppEnterBehaviorOverrides(context, updated)
                showAddDialog = false
                bringIntoViewPackage = apps.firstOrNull()?.packageName
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnterPresetSelector(
    preset: String,
    onPresetSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxWidth().settingRow("app_enter_behavior.preset")) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.app_enter_behaviour_preset_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.app_enter_behaviour_preset_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                OutlinedTextField(
                    value = getEnterPresetLabel(preset),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    enterPresetOptions().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(getEnterPresetLabel(option)) },
                            onClick = {
                                expanded = false
                                onPresetSelected(option)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnterAdditionalSendShortcutSelector(
    shortcut: String,
    onShortcutSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxWidth().settingRow("app_enter_behavior.additional_send_shortcut")) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.app_enter_behaviour_additional_send_shortcut_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.app_enter_behaviour_additional_send_shortcut_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                OutlinedTextField(
                    value = getEnterAdditionalSendShortcutLabel(shortcut),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    enterAdditionalSendShortcutOptions().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(getEnterAdditionalSendShortcutLabel(option)) },
                            onClick = {
                                expanded = false
                                onShortcutSelected(option)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun EnterBehaviorOverrideRow(
    app: EnterBehaviorApp,
    behavior: String,
    sendStrategy: String,
    additionalSendShortcut: String,
    editable: Boolean,
    bringIntoView: Boolean,
    onBroughtIntoView: () -> Unit,
    onBehaviorChanged: (String) -> Unit,
    onSendStrategyChanged: (String) -> Unit,
    onAdditionalSendShortcutChanged: (String) -> Unit,
    onRemove: () -> Unit
) {
    var behaviorExpanded by remember { mutableStateOf(false) }
    var strategyExpanded by remember { mutableStateOf(false) }
    var additionalShortcutExpanded by remember { mutableStateOf(false) }
    var showManualOverrideControls by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember(app.packageName) { BringIntoViewRequester() }

    LaunchedEffect(bringIntoView) {
        if (bringIntoView) {
            bringIntoViewRequester.bringIntoView()
            onBroughtIntoView()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app = app, modifier = Modifier.size(36.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = { showManualOverrideControls = !showManualOverrideControls },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (showManualOverrideControls) {
                            stringResource(R.string.app_enter_behaviour_hide_manual_override)
                        } else {
                            stringResource(R.string.app_enter_behaviour_show_manual_override)
                        }
                    )
                }
            }

            if (editable) {
                ManualEnterBehaviorStatus(
                    behavior = behavior,
                    sendStrategy = sendStrategy,
                    additionalSendShortcut = additionalSendShortcut
                )
            } else {
                KnownEnterBehaviorStatus(app.packageName, behavior)
                if (showManualOverrideControls) {
                    Text(
                        text = stringResource(R.string.app_enter_behaviour_manual_override_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            if (showManualOverrideControls) {
                ExposedDropdownMenuBox(
                    expanded = behaviorExpanded,
                    onExpandedChange = { behaviorExpanded = it },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    OutlinedTextField(
                        value = getEnterBehaviorLabel(behavior),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.app_enter_behaviour_desired_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(behaviorExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = behaviorExpanded,
                        onDismissRequest = { behaviorExpanded = false }
                    ) {
                        enterBehaviorOptions().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(getEnterBehaviorLabel(option)) },
                                onClick = {
                                    behaviorExpanded = false
                                    onBehaviorChanged(option)
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = strategyExpanded,
                    onExpandedChange = { strategyExpanded = it },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    OutlinedTextField(
                        value = getEnterSendStrategyLabel(sendStrategy),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.app_enter_behaviour_strategy_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(strategyExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = strategyExpanded,
                        onDismissRequest = { strategyExpanded = false }
                    ) {
                        enterSendStrategyOptions().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(getEnterSendStrategyLabel(option)) },
                                onClick = {
                                    strategyExpanded = false
                                    onSendStrategyChanged(option)
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = additionalShortcutExpanded,
                    onExpandedChange = { additionalShortcutExpanded = it },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    OutlinedTextField(
                        value = getEnterAdditionalSendShortcutLabel(additionalSendShortcut),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.app_enter_behaviour_additional_send_shortcut_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(additionalShortcutExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = additionalShortcutExpanded,
                        onDismissRequest = { additionalShortcutExpanded = false }
                    ) {
                        enterAdditionalSendShortcutOptions().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(getEnterAdditionalSendShortcutLabel(option)) },
                                onClick = {
                                    additionalShortcutExpanded = false
                                    onAdditionalSendShortcutChanged(option)
                                }
                            )
                        }
                    }
                }

                if (editable) {
                    TextButton(
                        onClick = onRemove,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.app_enter_behaviour_remove_app))
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualEnterBehaviorStatus(
    behavior: String,
    sendStrategy: String,
    additionalSendShortcut: String
) {
    Text(
        text = stringResource(
            R.string.app_enter_behaviour_manual_summary,
            getEnterBehaviorLabel(behavior),
            getEnterSendStrategyLabel(sendStrategy)
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
    if (additionalSendShortcut != SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_NONE) {
        Text(
            text = stringResource(
                R.string.app_enter_behaviour_manual_additional_shortcut_summary,
                getEnterAdditionalSendShortcutLabel(additionalSendShortcut)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEnterBehaviorAppDialog(
    existingPackages: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (List<EnterBehaviorApp>) -> Unit
) {
    val context = LocalContext.current
    val apps = remember(existingPackages) {
        loadInstalledLaunchableApps(context)
            .filterNot { it.packageName in existingPackages }
    }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPackages by remember { mutableStateOf<List<String>>(emptyList()) }
    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) {
            apps
        } else {
            apps.filter { app ->
                app.label.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    fun toggleSelection(packageName: String) {
        selectedPackages = if (packageName in selectedPackages) {
            selectedPackages - packageName
        } else {
            selectedPackages + packageName
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_enter_behaviour_app_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(stringResource(R.string.app_enter_behaviour_app_picker_search))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (filteredApps.isEmpty()) {
                    Text(
                        text = stringResource(R.string.app_enter_behaviour_app_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(min = 180.dp, max = 480.dp)) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val selected = app.packageName in selectedPackages
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { toggleSelection(app.packageName) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(app = app, modifier = Modifier.size(36.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp)
                                ) {
                                    Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { toggleSelection(app.packageName) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedPackages.isNotEmpty(),
                onClick = {
                    val appsByPackage = apps.associateBy { it.packageName }
                    onAdd(selectedPackages.mapNotNull(appsByPackage::get))
                }
            ) {
                Text(
                    stringResource(
                        R.string.app_enter_behaviour_app_picker_add_selected,
                        selectedPackages.size
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun getEnterPresetLabel(preset: String): String {
    return when (preset) {
        SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE ->
            stringResource(R.string.app_enter_behaviour_preset_send_shift_newline)
        SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_CTRL_SEND ->
            stringResource(R.string.app_enter_behaviour_preset_newline_ctrl_send)
        SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_ONLY ->
            stringResource(R.string.app_enter_behaviour_preset_newline_only)
        SettingsManager.ENTER_BEHAVIOR_PRESET_CUSTOM ->
            stringResource(R.string.app_enter_behaviour_preset_custom)
        else -> stringResource(R.string.app_enter_behaviour_preset_app_default)
    }
}

@Composable
private fun getEnterBehaviorLabel(behavior: String): String {
    return when (behavior) {
        SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE ->
            stringResource(R.string.app_enter_behaviour_option_enter_newline)
        SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE ->
            stringResource(R.string.app_enter_behaviour_option_send_shift_newline)
        SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND ->
            stringResource(R.string.app_enter_behaviour_option_newline_ctrl_send)
        else -> stringResource(R.string.app_enter_behaviour_option_app_default)
    }
}

private fun enterPresetOptions(): List<String> {
    return listOf(
        SettingsManager.ENTER_BEHAVIOR_PRESET_APP_DEFAULT,
        SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE,
        SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_CTRL_SEND
    )
}

private fun enterBehaviorOptions(): List<String> {
    return listOf(
        SettingsManager.ENTER_BEHAVIOR_APP_DEFAULT,
        SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE,
        SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND
    )
}

@Composable
private fun KnownEnterBehaviorStatus(packageName: String, behavior: String) {
    val behaviorText = getEnterBehaviorLabel(behavior)
    val strategyLabel = when {
        packageName == DISCORD_PACKAGE_NAME &&
            behavior == SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE ->
            stringResource(R.string.app_enter_behaviour_strategy_none_label)
        packageName == DISCORD_PACKAGE_NAME &&
            behavior == SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND ->
            stringResource(R.string.app_enter_behaviour_strategy_plain_enter_label)
        behavior == SettingsManager.ENTER_BEHAVIOR_APP_DEFAULT ->
            stringResource(R.string.app_enter_behaviour_strategy_none_label)
        packageName in favoriteEnterBehaviorApps ->
            stringResource(R.string.app_enter_behaviour_strategy_app_action_label)
        else ->
            stringResource(R.string.app_enter_behaviour_strategy_unconfigured_label)
    }
    val statusText = when {
        packageName == DISCORD_PACKAGE_NAME &&
            behavior == SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE ->
            stringResource(R.string.app_enter_behaviour_status_native_result)
        packageName == DISCORD_PACKAGE_NAME &&
            behavior == SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND ->
            stringResource(R.string.app_enter_behaviour_status_strategy_result, behaviorText)
        behavior == SettingsManager.ENTER_BEHAVIOR_APP_DEFAULT ->
            stringResource(R.string.app_enter_behaviour_status_native_result)
        packageName in testedEnterBehaviorPackages ->
            stringResource(R.string.app_enter_behaviour_status_strategy_result, behaviorText)
        packageName in favoriteEnterBehaviorApps ->
            stringResource(R.string.app_enter_behaviour_status_experimental_result, behaviorText)
        else ->
            stringResource(R.string.app_enter_behaviour_status_not_active, behaviorText)
    }
    val strategyText = when {
        packageName == DISCORD_PACKAGE_NAME &&
            behavior == SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE ->
            stringResource(R.string.app_enter_behaviour_strategy_discord_app_default_detail)
        packageName == DISCORD_PACKAGE_NAME &&
            behavior == SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND ->
            stringResource(R.string.app_enter_behaviour_strategy_discord_partial_detail)
        behavior == SettingsManager.ENTER_BEHAVIOR_APP_DEFAULT ->
            stringResource(R.string.app_enter_behaviour_strategy_app_default_detail)
        packageName in testedEnterBehaviorPackages &&
            behavior == SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND ->
            stringResource(R.string.app_enter_behaviour_strategy_whatsapp_newline_ctrl_send)
        packageName in testedEnterBehaviorPackages &&
            behavior == SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE ->
            stringResource(R.string.app_enter_behaviour_strategy_whatsapp_send_shift_newline)
        packageName in testedEnterBehaviorPackages &&
            behavior == SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE ->
            stringResource(R.string.app_enter_behaviour_strategy_whatsapp_newline_only)
        packageName in favoriteEnterBehaviorApps ->
            stringResource(R.string.app_enter_behaviour_strategy_experimental_detail)
        else ->
            stringResource(R.string.app_enter_behaviour_strategy_not_tested_detail)
    }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Text(
            text = strategyLabel,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
    Text(
        text = statusText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 4.dp)
    )
    Text(
        text = strategyText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun getEnterSendStrategyLabel(strategy: String): String {
    return when (strategy) {
        SettingsManager.ENTER_SEND_STRATEGY_EDITOR_ACTION ->
            stringResource(R.string.app_enter_behaviour_strategy_editor_action)
        SettingsManager.ENTER_SEND_STRATEGY_CTRL_ENTER ->
            stringResource(R.string.app_enter_behaviour_strategy_ctrl_enter)
        SettingsManager.ENTER_SEND_STRATEGY_PLAIN_ENTER ->
            stringResource(R.string.app_enter_behaviour_strategy_plain_enter)
        else -> stringResource(R.string.app_enter_behaviour_strategy_auto)
    }
}

private fun enterSendStrategyOptions(): List<String> {
    return listOf(
        SettingsManager.ENTER_SEND_STRATEGY_EDITOR_ACTION,
        SettingsManager.ENTER_SEND_STRATEGY_CTRL_ENTER,
        SettingsManager.ENTER_SEND_STRATEGY_PLAIN_ENTER
    )
}

private fun explicitEnterSendStrategy(packageName: String, strategy: String): String {
    if (strategy != SettingsManager.ENTER_SEND_STRATEGY_AUTO) return strategy
    return if (packageName == DISCORD_PACKAGE_NAME) {
        SettingsManager.ENTER_SEND_STRATEGY_PLAIN_ENTER
    } else {
        SettingsManager.ENTER_SEND_STRATEGY_EDITOR_ACTION
    }
}

@Composable
private fun getEnterAdditionalSendShortcutLabel(shortcut: String): String {
    return when (shortcut) {
        SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_SYM_ENTER ->
            stringResource(R.string.app_enter_behaviour_additional_send_shortcut_sym_enter)
        ENTER_ADDITIONAL_SEND_SHORTCUT_CUSTOM ->
            stringResource(R.string.app_enter_behaviour_preset_custom)
        else -> stringResource(R.string.app_enter_behaviour_additional_send_shortcut_none)
    }
}

private fun enterAdditionalSendShortcutOptions(): List<String> {
    return listOf(
        SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_NONE,
        SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_SYM_ENTER
    )
}

internal fun commonAdditionalSendShortcut(
    overrides: List<SettingsManager.AppEnterBehaviorOverride>
): String {
    val shortcuts = overrides
        .map { it.additionalSendShortcut }
        .distinct()
    return when (shortcuts.size) {
        0 -> SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_NONE
        1 -> shortcuts.single()
        else -> ENTER_ADDITIONAL_SEND_SHORTCUT_CUSTOM
    }
}

internal fun additionalSendShortcutForNewApp(commonShortcut: String): String {
    return if (commonShortcut == ENTER_ADDITIONAL_SEND_SHORTCUT_CUSTOM) {
        SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_NONE
    } else {
        commonShortcut
    }
}

@Composable
private fun AppIcon(app: EnterBehaviorApp, modifier: Modifier = Modifier) {
    val drawable = app.icon
    if (drawable != null) {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            },
            update = { view ->
                view.setImageDrawable(drawable.constantState?.newDrawable() ?: drawable)
            },
            modifier = modifier
        )
    } else {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
    }
}

private fun loadInitialEnterBehaviorOverrides(
    context: Context,
    preset: String
): List<SettingsManager.AppEnterBehaviorOverride> {
    val stored = SettingsManager.getAppEnterBehaviorOverrides(context)
        .filter { isPackageInstalled(context, it.packageName) }
    if (stored.isNotEmpty()) {
        val explicitManualOverrides = stored.map { override ->
            if (
                override.packageName !in favoriteEnterBehaviorApps &&
                override.sendStrategy == SettingsManager.ENTER_SEND_STRATEGY_AUTO
            ) {
                override.copy(sendStrategy = SettingsManager.ENTER_SEND_STRATEGY_EDITOR_ACTION)
            } else {
                override
            }
        }
        if (explicitManualOverrides != stored) {
            SettingsManager.setAppEnterBehaviorOverrides(context, explicitManualOverrides)
        }
        return sortEnterBehaviorOverrides(context, explicitManualOverrides)
    }
    return applyPresetToKnownApps(context, preset, emptyList())
}

private fun applyPresetToKnownApps(
    context: Context,
    preset: String,
    existing: List<SettingsManager.AppEnterBehaviorOverride>
): List<SettingsManager.AppEnterBehaviorOverride> {
    val behavior = enterBehaviorForPreset(preset) ?: return existing

    val knownInstalled = favoriteEnterBehaviorApps
        .filter { isPackageInstalled(context, it) }
        .map { SettingsManager.AppEnterBehaviorOverride(it, behavior) }
    val knownPackages = knownInstalled.map { it.packageName }.toSet()
    return sortEnterBehaviorOverrides(context, knownInstalled + existing.filterNot { it.packageName in knownPackages })
}

private fun enterBehaviorForPreset(preset: String): String? {
    return when (preset) {
        SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE ->
            SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE
        SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_CTRL_SEND ->
            SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND
        SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_ONLY ->
            SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE
        SettingsManager.ENTER_BEHAVIOR_PRESET_APP_DEFAULT ->
            SettingsManager.ENTER_BEHAVIOR_APP_DEFAULT
        else -> null
    }
}

internal fun inferKnownAppEnterBehaviorPreset(
    overrides: List<SettingsManager.AppEnterBehaviorOverride>
): String? {
    val knownBehaviors = overrides
        .filter { it.packageName in favoriteEnterBehaviorApps }
        .map { it.behavior }
        .distinct()
    if (knownBehaviors.isEmpty()) return null
    if (knownBehaviors.size > 1) return SettingsManager.ENTER_BEHAVIOR_PRESET_CUSTOM

    return when (knownBehaviors.single()) {
        SettingsManager.ENTER_BEHAVIOR_APP_DEFAULT ->
            SettingsManager.ENTER_BEHAVIOR_PRESET_APP_DEFAULT
        SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE ->
            SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE
        SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND ->
            SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_CTRL_SEND
        else -> SettingsManager.ENTER_BEHAVIOR_PRESET_CUSTOM
    }
}

private fun sortEnterBehaviorOverrides(
    context: Context,
    overrides: List<SettingsManager.AppEnterBehaviorOverride>
): List<SettingsManager.AppEnterBehaviorOverride> {
    return overrides
        .filter { isPackageInstalled(context, it.packageName) }
        .distinctBy { it.packageName }
        .sortedWith(
            compareByDescending<SettingsManager.AppEnterBehaviorOverride> {
                it.packageName in favoriteEnterBehaviorApps
            }.thenBy {
                if (it.packageName in favoriteEnterBehaviorApps) {
                    favoriteEnterBehaviorApps.indexOf(it.packageName)
                } else {
                    Int.MAX_VALUE
                }
            }.thenBy {
                getApplicationLabel(context, it.packageName)?.lowercase() ?: it.packageName
            }
        )
}

private fun resolveApp(context: Context, packageName: String): EnterBehaviorApp {
    return EnterBehaviorApp(
        packageName = packageName,
        label = getApplicationLabel(context, packageName) ?: packageName,
        icon = getApplicationIcon(context, packageName),
        isFavorite = packageName in favoriteEnterBehaviorApps
    )
}

private fun loadInstalledLaunchableApps(context: Context): List<EnterBehaviorApp> {
    val packageManager = context.packageManager
    val launchIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
        addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    }
    return packageManager.queryIntentActivities(launchIntent, 0)
        .map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            EnterBehaviorApp(
                packageName = packageName,
                label = resolveInfo.loadLabel(packageManager)?.toString() ?: packageName,
                icon = resolveInfo.loadIcon(packageManager),
                isFavorite = packageName in favoriteEnterBehaviorApps
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

private fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return runCatching {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    }.getOrDefault(false)
}

private fun getApplicationLabel(context: Context, packageName: String): String? {
    return runCatching {
        val info: ApplicationInfo = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrNull()
}

private fun getApplicationIcon(context: Context, packageName: String): Drawable? {
    return runCatching {
        context.packageManager.getApplicationIcon(packageName)
    }.getOrNull()
}
