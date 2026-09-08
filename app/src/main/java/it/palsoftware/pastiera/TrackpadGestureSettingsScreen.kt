package it.palsoftware.pastiera

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.inputmethod.trackpad.ShizukuTrackpadDeviceDiscovery
import it.palsoftware.pastiera.inputmethod.trackpad.TrackpadInputDevice
import it.palsoftware.pastiera.inputmethod.trackpad.TrackpadInputDeviceDiscovery
import kotlinx.coroutines.delay

/**
 * Settings screen for trackpad gesture suggestions.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TrackpadGestureSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var trackpadGesturesEnabled by remember {
        mutableStateOf(SettingsManager.getTrackpadGesturesEnabled(context))
    }
    var trackpadGestureAddWordEnabled by remember {
        mutableStateOf(SettingsManager.getTrackpadGestureAddWordEnabled(context))
    }
    var trackpadGestureAddWordFullWidthEnabled by remember {
        mutableStateOf(SettingsManager.getTrackpadGestureAddWordFullWidthEnabled(context))
    }
    var suggestionSwipeThreshold by remember {
        mutableStateOf(SettingsManager.getTrackpadSuggestionSwipeThreshold(context))
    }
    var deleteSwipeThreshold by remember {
        mutableStateOf(SettingsManager.getTrackpadDeleteSwipeThreshold(context))
    }
    var showTutorialDialog by remember { mutableStateOf(false) }
    var showSensitivitySettings by remember { mutableStateOf(false) }
    var shizukuStatus by remember { mutableStateOf(ShizukuStatus.NotConnected) }
    var trackpadProvider by remember { mutableStateOf(SettingsManager.getTrackpadProvider(context)) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var shizukuDevice by remember { mutableStateOf(SettingsManager.getTrackpadShizukuDevice(context)) }
    var shizukuDeviceMenuExpanded by remember { mutableStateOf(false) }
    var shizukuDevices by remember { mutableStateOf<List<TrackpadInputDevice>>(emptyList()) }
    var shizukuDeviceDiscoveryFailed by remember { mutableStateOf(false) }
    var swipeToDelete by remember { mutableStateOf(SettingsManager.getSwipeToDelete(context)) }
    var swipeToDeleteProvider by remember { mutableStateOf(SettingsManager.getSwipeToDeleteProvider(context)) }
    var swipeToDeleteProviderMenuExpanded by remember { mutableStateOf(false) }
    val highlightedSettingId = LocalSettingHighlightId.current
    val trackpadProviderOptions = listOf(
        SettingsManager.TRACKPAD_PROVIDER_NATIVE_IME to stringResource(R.string.trackpad_provider_native_ime),
        SettingsManager.TRACKPAD_PROVIDER_SHIZUKU to stringResource(R.string.trackpad_provider_shizuku)
    )
    val swipeToDeleteProviderOptions = listOf(
        SettingsManager.SWIPE_TO_DELETE_PROVIDER_NATIVE_IME to stringResource(R.string.swipe_to_delete_provider_native_ime),
        SettingsManager.SWIPE_TO_DELETE_PROVIDER_TITAN2_KEYCODE to stringResource(R.string.swipe_to_delete_provider_titan2_keycode)
    )

    BackHandler {
        if (showSensitivitySettings) {
            showSensitivitySettings = false
        } else {
            onBack()
        }
    }
    LaunchedEffect(highlightedSettingId) {
        when (highlightedSettingId) {
            SettingLinkIds.TRACKPAD_SUGGESTION_SWIPE_THRESHOLD,
            SettingLinkIds.TRACKPAD_DELETE_SWIPE_THRESHOLD -> showSensitivitySettings = true
            SettingLinkIds.TRACKPAD_GESTURES_ENABLED,
            SettingLinkIds.TRACKPAD_PROVIDER,
            SettingLinkIds.TRACKPAD_SHIZUKU_DEVICE,
            SettingLinkIds.TRACKPAD_SENSITIVITY,
            SettingLinkIds.TRACKPAD_DEBUG -> showSensitivitySettings = false
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            shizukuStatus = resolveShizukuStatus()
            delay(2000)
        }
    }
    LaunchedEffect(trackpadProvider, shizukuStatus) {
        if (
            trackpadProvider == SettingsManager.TRACKPAD_PROVIDER_SHIZUKU &&
            shizukuStatus == ShizukuStatus.Connected
        ) {
            runCatching { ShizukuTrackpadDeviceDiscovery.discover() }
                .onSuccess { devices ->
                    shizukuDevices = TrackpadInputDeviceDiscovery.selectableDevices(devices)
                    shizukuDeviceDiscoveryFailed = false
                }
                .onFailure {
                    shizukuDevices = emptyList()
                    shizukuDeviceDiscoveryFailed = true
                }
        }
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
                    IconButton(
                        onClick = {
                            if (showSensitivitySettings) {
                                showSensitivitySettings = false
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = stringResource(
                            if (showSensitivitySettings) {
                                R.string.trackpad_sensitivity_title
                            } else {
                                R.string.trackpad_gestures_title
                            }
                        ),
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
            if (showSensitivitySettings) {
                TrackpadSensitivitySettings(
                    suggestionSwipeThreshold = suggestionSwipeThreshold,
                    onSuggestionSwipeThresholdChange = { newValue ->
                        suggestionSwipeThreshold = newValue
                        SettingsManager.setTrackpadSuggestionSwipeThreshold(context, newValue)
                    },
                    deleteSwipeThreshold = deleteSwipeThreshold,
                    onDeleteSwipeThresholdChange = { newValue ->
                        deleteSwipeThreshold = newValue
                        SettingsManager.setTrackpadDeleteSwipeThreshold(context, newValue)
                    }
                )
                return@Column
            }

            // Enable/Disable Toggle
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .settingRow(SettingLinkIds.TRACKPAD_GESTURES_ENABLED)
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
                            text = stringResource(R.string.trackpad_gestures_enabled_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.trackpad_gestures_enabled_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    FeatureStatusIcon(FeatureStatus.Experimental)
                    Switch(
                        checked = trackpadGesturesEnabled,
                        onCheckedChange = { enabled ->
                            trackpadGesturesEnabled = enabled
                            SettingsManager.setTrackpadGesturesEnabled(context, enabled)
                        }
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(modifier = Modifier.width(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.trackpad_gesture_add_word_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.trackpad_gesture_add_word_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    Switch(
                        checked = trackpadGestureAddWordEnabled,
                        enabled = trackpadGesturesEnabled,
                        onCheckedChange = { enabled ->
                            trackpadGestureAddWordEnabled = enabled
                            SettingsManager.setTrackpadGestureAddWordEnabled(context, enabled)
                        }
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(modifier = Modifier.width(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.trackpad_gesture_add_word_full_width_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.trackpad_gesture_add_word_full_width_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    Switch(
                        checked = trackpadGestureAddWordFullWidthEnabled,
                        enabled = trackpadGesturesEnabled && trackpadGestureAddWordEnabled,
                        onCheckedChange = { enabled ->
                            trackpadGestureAddWordFullWidthEnabled = enabled
                            SettingsManager.setTrackpadGestureAddWordFullWidthEnabled(context, enabled)
                        }
                    )
                }
            }

            Text(
                text = stringResource(R.string.trackpad_provider_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ExposedDropdownMenuBox(
                expanded = providerMenuExpanded,
                onExpandedChange = { providerMenuExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .settingRow(SettingLinkIds.TRACKPAD_PROVIDER)
            ) {
                OutlinedTextField(
                    value = trackpadProviderOptions.firstOrNull { it.first == trackpadProvider }?.second ?: trackpadProvider,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text(stringResource(R.string.trackpad_provider_title)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenuExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = providerMenuExpanded,
                    onDismissRequest = { providerMenuExpanded = false }
                ) {
                    trackpadProviderOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                trackpadProvider = value
                                SettingsManager.setTrackpadProvider(context, value)
                                providerMenuExpanded = false
                            },
                            leadingIcon = {
                                if (trackpadProvider == value) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null
                                    )
                                }
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            if (trackpadProvider == SettingsManager.TRACKPAD_PROVIDER_SHIZUKU) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val (statusIcon, statusTint, statusText) = when (shizukuStatus) {
                            ShizukuStatus.Connected -> Triple(Icons.Filled.CheckCircle, MaterialTheme.colorScheme.primary, stringResource(R.string.trackpad_gestures_shizuku_connected))
                            ShizukuStatus.NotAuthorized -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.tertiary, stringResource(R.string.trackpad_gestures_shizuku_not_authorized))
                            ShizukuStatus.NotConnected -> Triple(Icons.Filled.Error, MaterialTheme.colorScheme.error, stringResource(R.string.trackpad_gestures_shizuku_not_connected))
                        }
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusTint,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusTint
                        )
                    }

                    val automaticLabel = stringResource(R.string.trackpad_shizuku_device_automatic)
                    val automaticDevice = TrackpadInputDeviceDiscovery.selectAutomatic(shizukuDevices)
                    val selectedDeviceLabel = when (shizukuDevice) {
                        SettingsManager.TRACKPAD_SHIZUKU_DEVICE_AUTO -> automaticDevice?.let {
                            "$automaticLabel · ${it.displayName}"
                        } ?: automaticLabel
                        else -> shizukuDevices.firstOrNull { it.path == shizukuDevice }?.displayName
                            ?: stringResource(R.string.trackpad_shizuku_device_unavailable, shizukuDevice)
                    }
                    val deviceSupportingText: (@Composable () -> Unit)? = when {
                        shizukuDeviceDiscoveryFailed -> {
                            { Text(stringResource(R.string.trackpad_shizuku_device_discovery_failed)) }
                        }
                        shizukuStatus == ShizukuStatus.Connected &&
                            shizukuDevice == SettingsManager.TRACKPAD_SHIZUKU_DEVICE_AUTO &&
                            automaticDevice == null -> {
                            { Text(stringResource(R.string.trackpad_shizuku_device_no_candidate)) }
                        }
                        else -> null
                    }
                    ExposedDropdownMenuBox(
                        expanded = shizukuDeviceMenuExpanded,
                        onExpandedChange = { shizukuDeviceMenuExpanded = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .settingRow(SettingLinkIds.TRACKPAD_SHIZUKU_DEVICE)
                    ) {
                        OutlinedTextField(
                            value = selectedDeviceLabel,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            enabled = shizukuStatus == ShizukuStatus.Connected ||
                                shizukuDevice != SettingsManager.TRACKPAD_SHIZUKU_DEVICE_AUTO,
                            label = { Text(stringResource(R.string.trackpad_shizuku_device_title)) },
                            supportingText = deviceSupportingText,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = shizukuDeviceMenuExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = shizukuDeviceMenuExpanded,
                            onDismissRequest = { shizukuDeviceMenuExpanded = false }
                        ) {
                            val choices = buildList {
                                add(SettingsManager.TRACKPAD_SHIZUKU_DEVICE_AUTO to automaticLabel)
                                addAll(shizukuDevices.map { it.path to it.displayName })
                                if (
                                    shizukuDevice != SettingsManager.TRACKPAD_SHIZUKU_DEVICE_AUTO &&
                                    none { it.first == shizukuDevice }
                                ) {
                                    add(
                                        shizukuDevice to context.getString(
                                            R.string.trackpad_shizuku_device_unavailable,
                                            shizukuDevice
                                        )
                                    )
                                }
                            }
                            choices.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        shizukuDevice = value
                                        SettingsManager.setTrackpadShizukuDevice(context, value)
                                        shizukuDeviceMenuExpanded = false
                                    },
                                    leadingIcon = {
                                        if (shizukuDevice == value) {
                                            Icon(Icons.Filled.Check, contentDescription = null)
                                        }
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                }
            }

            if (trackpadProvider == SettingsManager.TRACKPAD_PROVIDER_NATIVE_IME) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.trackpad_provider_native_ime_status),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            ExposedDropdownMenuBox(
                expanded = swipeToDeleteProviderMenuExpanded,
                onExpandedChange = { swipeToDeleteProviderMenuExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = swipeToDeleteProviderOptions.firstOrNull { it.first == swipeToDeleteProvider }?.second ?: swipeToDeleteProvider,
                    onValueChange = {},
                    readOnly = true,
                    enabled = swipeToDelete,
                    singleLine = true,
                    label = { Text(stringResource(R.string.swipe_to_delete_provider_title)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = swipeToDeleteProviderMenuExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = swipeToDeleteProviderMenuExpanded,
                    onDismissRequest = { swipeToDeleteProviderMenuExpanded = false }
                ) {
                    swipeToDeleteProviderOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                swipeToDeleteProvider = value
                                SettingsManager.setSwipeToDeleteProvider(context, value)
                                swipeToDeleteProviderMenuExpanded = false
                            },
                            leadingIcon = {
                                if (swipeToDeleteProvider == value) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null
                                    )
                                }
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
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
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.swipe_to_delete_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.swipe_to_delete_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    Switch(
                        checked = swipeToDelete,
                        onCheckedChange = { enabled ->
                            swipeToDelete = enabled
                            SettingsManager.setSwipeToDelete(context, enabled)
                        }
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .settingRow(SettingLinkIds.TRACKPAD_SENSITIVITY) {
                        showSensitivitySettings = true
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
                        imageVector = Icons.Filled.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.trackpad_sensitivity_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.trackpad_sensitivity_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    Text(
                        text = "${suggestionSwipeThreshold.toInt()} / ${deleteSwipeThreshold.toInt()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .settingRow(SettingLinkIds.TRACKPAD_DEBUG) {
                        context.startActivity(Intent(context, TrackpadDebugActivity::class.java))
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
                        imageVector = Icons.Filled.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.trackpad_debug_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.trackpad_debug_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Show Tutorial Button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable { showTutorialDialog = true }
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
                            text = stringResource(R.string.trackpad_gestures_show_tutorial),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.trackpad_gestures_tutorial_description),
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

            if (trackpadProvider == SettingsManager.TRACKPAD_PROVIDER_SHIZUKU) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable {
                            val url = context.getString(R.string.trackpad_gestures_shizuku_url)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
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
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.trackpad_gestures_install_shizuku),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                text = stringResource(R.string.trackpad_gestures_install_shizuku_description),
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
        }
    }

    // Tutorial Dialog
    if (showTutorialDialog) {
        TrackpadTutorialDialog(
            onDismiss = { showTutorialDialog = false }
        )
    }
}

@Composable
private fun TrackpadSensitivitySettings(
    suggestionSwipeThreshold: Float,
    onSuggestionSwipeThresholdChange: (Float) -> Unit,
    deleteSwipeThreshold: Float,
    onDeleteSwipeThresholdChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        TrackpadSensitivitySlider(
            title = stringResource(R.string.trackpad_suggestion_swipe_threshold_title),
            description = stringResource(R.string.trackpad_suggestion_swipe_threshold_description),
            value = suggestionSwipeThreshold,
            linkId = SettingLinkIds.TRACKPAD_SUGGESTION_SWIPE_THRESHOLD,
            onValueChange = onSuggestionSwipeThresholdChange
        )
        TrackpadSensitivitySlider(
            title = stringResource(R.string.trackpad_delete_swipe_threshold_title),
            description = stringResource(R.string.trackpad_delete_swipe_threshold_description),
            value = deleteSwipeThreshold,
            linkId = SettingLinkIds.TRACKPAD_DELETE_SWIPE_THRESHOLD,
            onValueChange = onDeleteSwipeThresholdChange
        )
    }
}

@Composable
private fun TrackpadSensitivitySlider(
    title: String,
    description: String,
    value: Float,
    linkId: String,
    onValueChange: (Float) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .settingRow(linkId)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Text(
                    text = value.toInt().toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = SettingsManager.getMinTrackpadSwipeThreshold()..SettingsManager.getMaxTrackpadSwipeThreshold(),
                steps = 10
            )
        }
    }
}

/**
 * Tutorial dialog for trackpad gestures.
 */
@Composable
fun TrackpadTutorialDialog(
    onDismiss: () -> Unit
) {
    var currentPage by remember { mutableStateOf(0) }
    val pages = listOf(
        TutorialPage(
            title = stringResource(R.string.trackpad_tutorial_welcome_title),
            description = stringResource(R.string.trackpad_tutorial_welcome_description),
            icon = Icons.Filled.TouchApp
        ),
        TutorialPage(
            title = stringResource(R.string.trackpad_tutorial_how_it_works_title),
            description = stringResource(R.string.trackpad_tutorial_how_it_works_description),
            icon = Icons.Filled.Swipe
        ),
        TutorialPage(
            title = stringResource(R.string.trackpad_tutorial_shizuku_required_title),
            description = stringResource(R.string.trackpad_tutorial_shizuku_required_description),
            icon = Icons.Filled.Security
        ),
        TutorialPage(
            title = stringResource(R.string.trackpad_tutorial_tips_title),
            description = stringResource(R.string.trackpad_tutorial_tips_description),
            icon = Icons.Filled.Lightbulb
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.trackpad_tutorial_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = pages[currentPage].icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = pages[currentPage].title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = pages[currentPage].description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Page indicator
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    pages.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (index == currentPage) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (currentPage < pages.size - 1) {
                        currentPage++
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text(
                    text = if (currentPage < pages.size - 1)
                        stringResource(R.string.trackpad_tutorial_next)
                    else
                        stringResource(R.string.trackpad_tutorial_complete)
                )
            }
        },
        dismissButton = if (currentPage > 0) {
            {
                TextButton(onClick = { currentPage-- }) {
                    Text(text = stringResource(R.string.trackpad_tutorial_previous))
                }
            }
        } else null
    )
}

/**
 * Data class representing a tutorial page.
 */
private data class TutorialPage(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
