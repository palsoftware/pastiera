package it.palsoftware.pastiera

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.content.ComponentName
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.EmojiSymbols
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardAlt
import androidx.compose.material.icons.filled.KeyboardCommandKey
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import it.palsoftware.pastiera.inputmethod.ClicksLauncherButtonAccessibilityService
import it.palsoftware.pastiera.inputmethod.directActionOrNull

private enum class ClicksMappingPage { HostSlots, Buttons, NumberRow }

@Composable
fun ClicksPowerKeyboardSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val mainScrollState = rememberScrollState()
    var closeInputOnDisconnect by remember {
        mutableStateOf(SettingsManager.getClicksCloseInputOnDisconnect(context))
    }
    var showKeyboardOnlyWithTextFocus by remember {
        mutableStateOf(SettingsManager.getClicksShowKeyboardOnlyWithTextFocus(context))
    }
    var hasBluetoothPermission by remember {
        mutableStateOf(hasClicksBluetoothPermission(context))
    }
    var showBluetoothPermissionExplanation by remember { mutableStateOf(false) }
    var powerState by remember { mutableStateOf(ClicksPowerKeyboardState()) }
    var gattClient by remember { mutableStateOf<ClicksPowerKeyboardGattClient?>(null) }
    var connectedDeviceName by remember { mutableStateOf<String?>(null) }
    var lastKnownDeviceName by remember { mutableStateOf<String?>(null) }
    var phoneBatteryPercent by remember { mutableStateOf<Int?>(null) }
    var socCalibration by remember { mutableStateOf<ClicksPowerSocCalibrationStatus?>(null) }
    var manualChargingUntil by remember { mutableStateOf(0L) }
    var chargingAutomation by remember {
        mutableStateOf(SettingsManager.isClicksChargingAutomationEnabled(context))
    }
    var chargingStartSlider by remember {
        mutableStateOf(SettingsManager.getClicksChargingStartPercent(context).toFloat())
    }
    var chargingStopSlider by remember {
        mutableStateOf(SettingsManager.getClicksChargingStopPercent(context).toFloat())
    }
    var clicksOverlappingKeysMode by remember {
        mutableStateOf(SettingsManager.getClicksOverlappingKeysMode(context))
    }
    var numberRowInputMode by remember {
        mutableStateOf(SettingsManager.getClicksNumberRowInputMode(context))
    }
    var numberRowRepeatEnabled by remember {
        mutableStateOf(SettingsManager.isClicksNumberRowRepeatEnabled(context))
    }
    var clicksButtonMode by remember {
        mutableStateOf(SettingsManager.getClicksButtonMode(context))
    }
    var clicksMetaButtonMode by remember {
        mutableStateOf(SettingsManager.getClicksMetaButtonMode(context))
    }
    var clicksAltButtonMode by remember {
        mutableStateOf(SettingsManager.getClicksAltButtonMode(context))
    }
    var clicksMicrophoneButtonMode by remember {
        mutableStateOf(SettingsManager.getClicksMicrophoneButtonMode(context))
    }
    var desiredRedButtonBinding by remember {
        mutableStateOf(
            SettingsManager.getClicksDesiredButtonBinding(context, ClicksButtonBindingTarget.RED)
        )
    }
    var desiredKeyboardButtonBinding by remember {
        mutableStateOf(
            SettingsManager.getClicksDesiredButtonBinding(context, ClicksButtonBindingTarget.KEYBOARD)
        )
    }
    var desiredMicrophoneButtonBinding by remember {
        mutableStateOf(
            SettingsManager.getClicksDesiredButtonBinding(context, ClicksButtonBindingTarget.MICROPHONE)
        )
    }
    var backlightSlider by remember { mutableStateOf(100f) }
    var reserveSlider by remember { mutableStateOf(0f) }
    var mappingPage by remember { mutableStateOf<ClicksMappingPage?>(null) }
    val settingHighlight = LocalSettingHighlightId.current
    var showRequestedHostSlots by remember { mutableStateOf(settingHighlight == "clicks.host_slots") }
    androidx.compose.runtime.LaunchedEffect(settingHighlight) {
        if (settingHighlight != null) {
            showRequestedHostSlots = settingHighlight == "clicks.host_slots"
            mappingPage = if (settingHighlight.startsWith("clicks.buttons.")) ClicksMappingPage.Buttons else null
        }
    }
    var hostSlotToEdit by remember { mutableStateOf<Int?>(null) }
    var buttonBindingsInProgress by remember { mutableStateOf(emptySet<String>()) }
    var buttonBindingResult by remember { mutableStateOf<Int?>(null) }
    var launcherInterceptionEnabled by remember {
        mutableStateOf(isClicksLauncherInterceptionEnabled(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBluetoothPermission = granted
        if (granted) ClicksPowerKeyboardController.onBluetoothPermissionChanged()
    }

    LaunchedEffect(Unit) {
        if (!hasBluetoothPermission && !SettingsManager.hasExplainedClicksBluetoothPermission(context)) {
            showBluetoothPermissionExplanation = true
        }
    }

    val clicksAppInstalled = remember {
        context.packageManager.getLaunchIntentForPackage(CLICKS_COMPANION_PACKAGE) != null
    }

    DisposableEffect(Unit) {
        val observation = ClicksPowerKeyboardController.observe { controllerState ->
            connectedDeviceName = controllerState.deviceName
            lastKnownDeviceName = controllerState.lastKnownDeviceName
            phoneBatteryPercent = controllerState.phoneBatteryPercent
            socCalibration = controllerState.socCalibration
            manualChargingUntil = controllerState.manualChargingUntil
            powerState = controllerState.keyboard
            gattClient = ClicksPowerKeyboardController.activeClient()
        }
        onDispose { observation.close() }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                launcherInterceptionEnabled = isClicksLauncherInterceptionEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(powerState.backlightBrightness) {
        powerState.backlightBrightness?.let { backlightSlider = it.toFloat() }
    }
    LaunchedEffect(powerState.chargingReservePercent) {
        powerState.chargingReservePercent?.let { reserveSlider = it.toFloat() }
    }

    fun requestButtonBinding(
        progressId: String,
        target: ClicksButtonBindingTarget,
        binding: ClicksDesiredButtonBinding
    ) {
        buttonBindingResult = null
        when (ClicksPowerKeyboardController.requestButtonBinding(target, binding) { success ->
            buttonBindingsInProgress -= progressId
            buttonBindingResult = if (success) {
                R.string.clicks_button_binding_success
            } else {
                R.string.clicks_button_binding_failure
            }
        }) {
            ClicksButtonBindingRequestStatus.CONFIRMED ->
                buttonBindingResult = R.string.clicks_button_binding_success
            ClicksButtonBindingRequestStatus.PENDING_CONNECTION ->
                buttonBindingResult = R.string.clicks_button_binding_pending
            ClicksButtonBindingRequestStatus.APPLYING ->
                buttonBindingsInProgress += progressId
        }
    }

    if (showBluetoothPermissionExplanation) {
        AlertDialog(
            onDismissRequest = {
                SettingsManager.setClicksBluetoothPermissionExplained(context)
                showBluetoothPermissionExplanation = false
            },
            title = { Text(stringResource(R.string.clicks_bluetooth_permission_title)) },
            text = { Text(stringResource(R.string.clicks_bluetooth_permission_description)) },
            confirmButton = {
                TextButton(onClick = {
                    SettingsManager.setClicksBluetoothPermissionExplained(context)
                    showBluetoothPermissionExplanation = false
                    bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }) {
                    Text(stringResource(R.string.clicks_bluetooth_permission_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    SettingsManager.setClicksBluetoothPermissionExplained(context)
                    showBluetoothPermissionExplanation = false
                }) {
                    Text(stringResource(R.string.clicks_bluetooth_permission_not_now))
                }
            }
        )
    }

    if (mappingPage == ClicksMappingPage.HostSlots) {
        ClicksHostSlotsScreen(
            modifier = modifier,
            state = powerState,
            onBack = { mappingPage = null },
            onEdit = { hostSlotToEdit = it }
        )
        hostSlotToEdit?.let { slotIndex ->
            ClicksHostNameDialog(
                state = powerState,
                slotIndex = slotIndex,
                onApply = { selectedSlot, name ->
                    gattClient?.setHostName(selectedSlot, name)
                    hostSlotToEdit = null
                },
                onDismiss = { hostSlotToEdit = null }
            )
        }
        return
    }

    if (mappingPage == ClicksMappingPage.Buttons) {
        ClicksButtonMappingsScreen(
            modifier = modifier,
            state = powerState,
            redButtonMode = clicksButtonMode,
            launcherButtonMode = clicksMetaButtonMode,
            altButtonMode = clicksAltButtonMode,
            microphoneButtonMode = clicksMicrophoneButtonMode,
            desiredRedButtonBinding = desiredRedButtonBinding,
            desiredKeyboardButtonBinding = desiredKeyboardButtonBinding,
            desiredMicrophoneButtonBinding = desiredMicrophoneButtonBinding,
            launcherInterceptionEnabled = launcherInterceptionEnabled,
            inProgress = buttonBindingsInProgress,
            resultMessage = buttonBindingResult,
            onBack = { mappingPage = null },
            onOpenLauncherInterceptionSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            onRedSelected = { choiceId, mode, firmwareOutput, pastieraFunction ->
                clicksButtonMode = mode
                SettingsManager.setClicksButtonMode(context, mode)
                pastieraFunction?.let { applyClicksBindingPastieraFunction(context, it) }
                val binding = ClicksDesiredButtonBinding(choiceId, firmwareOutput)
                desiredRedButtonBinding = binding
                requestButtonBinding(
                    CLICKS_BINDING_RED,
                    ClicksButtonBindingTarget.RED,
                    binding
                )
            },
            onLauncherSelected = { mode ->
                clicksMetaButtonMode = mode
                SettingsManager.setClicksMetaButtonMode(context, mode)
                buttonBindingResult = R.string.clicks_button_binding_success
            },
            onAltSelected = { choiceId, mode, firmwareOutput, pastieraFunction ->
                clicksAltButtonMode = mode
                SettingsManager.setClicksAltButtonMode(context, mode)
                pastieraFunction?.let { applyClicksBindingPastieraFunction(context, it) }
                val binding = ClicksDesiredButtonBinding(choiceId, firmwareOutput)
                desiredKeyboardButtonBinding = binding
                requestButtonBinding(
                    CLICKS_BINDING_ALT,
                    ClicksButtonBindingTarget.KEYBOARD,
                    binding
                )
            },
            onMicrophoneSelected = { choiceId, mode, bytes, pastieraFunction ->
                clicksMicrophoneButtonMode = mode
                SettingsManager.setClicksMicrophoneButtonMode(context, mode)
                pastieraFunction?.let { applyClicksBindingPastieraFunction(context, it) }
                val binding = ClicksDesiredButtonBinding(choiceId, bytes)
                desiredMicrophoneButtonBinding = binding
                requestButtonBinding(
                    CLICKS_BINDING_MICROPHONE,
                    ClicksButtonBindingTarget.MICROPHONE,
                    binding
                )
            }
        )
        return
    }

    if (mappingPage == ClicksMappingPage.NumberRow) {
        ClicksNumberRowMappingsScreen(
            modifier = modifier,
            state = powerState,
            onBack = { mappingPage = null },
            onSelected = { command, bytes -> gattClient?.setSpecialKeyRemap(command, bytes) }
        )
        return
    }

    HardwareProfileScaffold(
        modifier = modifier,
        title = stringResource(R.string.clicks_power_keyboard_title),
        description = stringResource(R.string.clicks_power_keyboard_description),
        onBack = onBack,
        scrollState = mainScrollState
    ) {
        StubSection(stringResource(R.string.clicks_section_device))
        ClicksDeviceInfoRow(
            icon = Icons.Filled.Bluetooth,
            title = stringResource(R.string.clicks_device_status_title),
            description = connectedDeviceName?.let { deviceName ->
                val slot = deviceName.substringAfterLast('-', missingDelimiterValue = "?")
                when {
                    !hasBluetoothPermission -> stringResource(R.string.clicks_device_status_firmware_permission, slot)
                    powerState.firmwareVersion != null && powerState.batteryPercent != null -> stringResource(
                        R.string.clicks_device_status_with_firmware_and_battery,
                        slot,
                        powerState.firmwareVersion!!,
                        powerState.batteryPercent!!.toString()
                    )
                    powerState.firmwareVersion != null -> stringResource(
                        R.string.clicks_device_status_with_firmware, slot, powerState.firmwareVersion!!
                    )
                    powerState.batteryPercent != null -> stringResource(
                        R.string.clicks_device_status_with_battery, slot, powerState.batteryPercent!!
                    )
                    powerState.error != null -> powerState.error!!
                    else -> stringResource(R.string.clicks_device_status_connected, slot)
                }
            } ?: if (powerState.stale) {
                stringResource(
                    R.string.clicks_device_status_last_known,
                    lastKnownDeviceName ?: stringResource(R.string.clicks_device_status_disconnected)
                )
            } else {
                stringResource(R.string.clicks_device_status_disconnected)
            },
            onClick = when {
                connectedDeviceName == null -> null
                !hasBluetoothPermission -> ({ showBluetoothPermissionExplanation = true })
                powerState.error != null -> ({ ClicksPowerKeyboardController.onBluetoothPermissionChanged() })
                else -> null
            }
        )
        if (powerState.model != null || powerState.serialNumber != null) {
            ClicksDeviceInfoRow(
                icon = Icons.Filled.Keyboard,
                title = stringResource(R.string.clicks_gatt_identity_title),
                description = stringResource(
                    R.string.clicks_gatt_identity_value,
                    powerState.model ?: "?",
                    powerState.serialNumber ?: "?"
                )
            )
        }
        val firmwareVersion = powerState.firmwareVersion
        val firmwareSupported = firmwareVersion?.let(ClicksFirmwareVersionReader::isSupported) == true
        val controlsEnabled = powerState.ready && powerState.sessionValidated && !powerState.stale && firmwareSupported
        if (powerState.activeHostSlot != null || showRequestedHostSlots) {
            ClicksDeviceInfoRow(
                icon = Icons.Filled.Bluetooth,
                title = stringResource(R.string.clicks_host_slots_title),
                linkId = "clicks.host_slots",
                description = hostSlotsSummary(powerState),
                onClick = if (controlsEnabled && powerState.supportsHostNameWrites()) {
                    ({ mappingPage = ClicksMappingPage.HostSlots })
                } else {
                    null
                }
            )
        }
        ClicksDeviceInfoRow(
            icon = if (firmwareSupported) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            title = stringResource(R.string.clicks_firmware_status_title),
            description = when {
                firmwareSupported -> stringResource(R.string.clicks_firmware_status_supported, firmwareVersion!!)
                firmwareVersion != null -> stringResource(R.string.clicks_firmware_status_update_required, firmwareVersion!!)
                else -> stringResource(R.string.clicks_firmware_minimum_description)
            }
        )
        ClicksDeviceInfoRow(
            icon = Icons.Filled.SystemUpdate,
            title = stringResource(R.string.clicks_firmware_updates_title),
            linkId = "clicks.firmware_updates",
            description = if (clicksAppInstalled) {
                stringResource(R.string.clicks_firmware_updates_open_app)
            } else {
                stringResource(R.string.clicks_firmware_updates_install_app)
            },
            onClick = { openClicksFirmwareUpdates(context) }
        )

        StubSection(stringResource(R.string.clicks_section_keyboard_behavior))
        ClicksDeviceInfoRow(
            icon = Icons.Filled.CheckCircle,
            title = stringResource(R.string.clicks_recommended_settings_title),
            linkId = "clicks.recommended_settings",
            description = stringResource(R.string.clicks_recommended_settings_description),
            onClick = if (controlsEnabled && powerState.specialKeyEnableFlags != null) ({
                val client = gattClient ?: return@ClicksDeviceInfoRow
                buttonBindingsInProgress += CLICKS_BINDING_RECOMMENDED
                buttonBindingResult = null
                val plan = ClicksRecommendedSettingsPlanner.plan(
                    ClicksRecommendedSettingsSnapshot(
                        tabRemap = powerState.tabRemap,
                        microphoneRemap = powerState.geminiRemap,
                        altRemap = powerState.altRemap
                    )
                )
                applyClicksRecommendedSettings(client, plan.remapWrites) { firmwareSuccess ->
                    val success = firmwareSuccess && SettingsManager.applyClicksRecommendedButtonModes(context)
                    if (success) {
                        clicksButtonMode = SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER
                        clicksMetaButtonMode = SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER
                        clicksAltButtonMode = SettingsManager.ClicksPowerButtonMode.NATIVE
                        clicksMicrophoneButtonMode = SettingsManager.ClicksPowerButtonMode.NATIVE
                        desiredRedButtonBinding = ClicksDesiredButtonBinding(
                            choiceId = "red_quick_launcher",
                            firmwareOutput = ClicksPowerKeyboardProtocol.nativeRemapOutput()
                        ).also {
                            SettingsManager.setClicksDesiredButtonBinding(
                                context,
                                ClicksButtonBindingTarget.RED,
                                it
                            )
                        }
                        desiredKeyboardButtonBinding = ClicksDesiredButtonBinding(
                            choiceId = "alt_native",
                            firmwareOutput = ClicksPowerKeyboardProtocol.nativeRemapOutput()
                        ).also {
                            SettingsManager.setClicksDesiredButtonBinding(
                                context,
                                ClicksButtonBindingTarget.KEYBOARD,
                                it
                            )
                        }
                        desiredMicrophoneButtonBinding = ClicksDesiredButtonBinding(
                            choiceId = "microphone_dictation",
                            firmwareOutput = ClicksPowerKeyboardProtocol.dictationRemapOutput()
                        ).also {
                            SettingsManager.setClicksDesiredButtonBinding(
                                context,
                                ClicksButtonBindingTarget.MICROPHONE,
                                it
                            )
                        }
                    }
                    buttonBindingsInProgress -= CLICKS_BINDING_RECOMMENDED
                    buttonBindingResult = if (success) {
                        R.string.clicks_recommended_settings_success
                    } else {
                        R.string.clicks_recommended_settings_failure
                    }
                }
            }) else null
        )
        ClicksOverlappingKeysModeRow(
            selected = clicksOverlappingKeysMode,
            onSelected = { mode ->
                clicksOverlappingKeysMode = mode
                SettingsManager.setClicksOverlappingKeysMode(context, mode)
            }
        )
        ClicksNumberRowInputModeRow(
            selected = numberRowInputMode,
            onSelected = { mode ->
                numberRowInputMode = mode
                SettingsManager.setClicksNumberRowInputMode(context, mode)
            }
        )
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_number_row_repeat_title),
            linkId = "clicks.number_row_repeat",
            description = stringResource(R.string.clicks_number_row_repeat_description),
            checked = numberRowRepeatEnabled,
            onCheckedChange = { enabled ->
                numberRowRepeatEnabled = enabled
                SettingsManager.setClicksNumberRowRepeatEnabled(context, enabled)
            }
        )
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_sticky_alt_title),
            linkId = "clicks.sticky_alt",
            description = stringResource(R.string.clicks_sticky_alt_description),
            checked = powerState.hasFeature(ClicksPowerKeyboardProtocol.FLAG_SYM_LOCK) == true,
            enabled = controlsEnabled && powerState.featureFlags != null,
            onCheckedChange = { gattClient?.setSymLock(it) },
            infoText = stringResource(R.string.clicks_sticky_alt_help)
        )
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_sticky_shift_title),
            linkId = "clicks.sticky_shift",
            description = stringResource(R.string.clicks_sticky_shift_description),
            checked = powerState.hasFeature(ClicksPowerKeyboardProtocol.FLAG_CAPS_LOCK) == true,
            enabled = controlsEnabled && powerState.featureFlags != null,
            onCheckedChange = { gattClient?.setCapsLock(it) },
            infoText = stringResource(R.string.clicks_sticky_shift_help)
        )
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_soft_return_title),
            linkId = "clicks.soft_return",
            description = stringResource(R.string.clicks_soft_return_description),
            checked = powerState.hasFeature(ClicksPowerKeyboardProtocol.FLAG_SOFT_RETURN) == true,
            enabled = controlsEnabled && powerState.featureFlags != null,
            onCheckedChange = { gattClient?.setSoftReturn(it) },
            infoText = stringResource(R.string.clicks_soft_return_help)
        )
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_cursor_mode_title),
            linkId = "clicks.cursor_mode",
            description = stringResource(R.string.clicks_cursor_mode_description),
            checked = powerState.hasFeature(ClicksPowerKeyboardProtocol.FLAG_CURSOR_MODE) == true,
            enabled = controlsEnabled && powerState.featureFlags != null,
            onCheckedChange = { gattClient?.setCursorMode(it) },
            infoText = stringResource(R.string.clicks_cursor_mode_help)
        )

        StubSection(stringResource(R.string.clicks_section_key_mappings))
        ClicksDeviceInfoRow(
            icon = Icons.Filled.Edit,
            title = stringResource(R.string.clicks_button_bindings_title),
            linkId = "clicks.button_bindings",
            description = stringResource(R.string.clicks_button_bindings_description),
            onClick = {
                buttonBindingResult = null
                mappingPage = ClicksMappingPage.Buttons
            }
        )
        ClicksDeviceInfoRow(
            icon = Icons.Filled.Edit,
            title = stringResource(R.string.clicks_number_row_title),
            linkId = "clicks.number_row",
            description = stringResource(R.string.clicks_number_row_description),
            onClick = if (
                controlsEnabled &&
                powerState.specialKeyEnableFlags != null &&
                powerState.numberKeyEnableFlags != null
            ) {
                ({ mappingPage = ClicksMappingPage.NumberRow })
            } else {
                null
            }
        )
        PlannedSettingsRow(
            icon = Icons.Filled.Edit,
            title = stringResource(R.string.clicks_all_key_mappings_title),
            description = stringResource(R.string.clicks_all_key_mappings_description)
        )

        StubSection(stringResource(R.string.clicks_section_backlight_power))
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_backlight_title),
            linkId = "clicks.backlight",
            description = stringResource(R.string.clicks_backlight_description),
            checked = powerState.hasFeature(ClicksPowerKeyboardProtocol.FLAG_BACKLIGHT) == true,
            enabled = controlsEnabled && powerState.featureFlags != null,
            onCheckedChange = { gattClient?.setBacklightEnabled(it) }
        )
        ClicksSliderRow(
            title = stringResource(R.string.clicks_backlight_brightness_title),
            linkId = "clicks.backlight_brightness",
            valueLabel = "${backlightSlider.toInt()} %",
            value = backlightSlider,
            range = 0f..100f,
            steps = 19,
            enabled = controlsEnabled,
            onValueChange = { backlightSlider = it },
            onValueChangeFinished = { gattClient?.setBacklightBrightness(backlightSlider.toInt()) }
        )
        ClicksIntDropdownRow(
            title = stringResource(R.string.clicks_backlight_timeout_dialog_title),
            linkId = "clicks.backlight_timeout_dialog",
            selected = powerState.backlightTimeoutSeconds?.takeIf { it in CLICKS_BACKLIGHT_TIMEOUT_OPTIONS },
            options = CLICKS_BACKLIGHT_TIMEOUT_OPTIONS,
            label = { stringResource(R.string.clicks_seconds_value, it) },
            enabled = controlsEnabled,
            onSelected = { gattClient?.setBacklightTimeout(it) }
        )
        ClicksIntDropdownRow(
            title = stringResource(R.string.clicks_idle_timeout_dialog_title),
            linkId = "clicks.idle_timeout_dialog",
            selected = powerState.idleTimeoutSeconds
                ?.takeIf { it % 60 == 0 }
                ?.div(60)
                ?.takeIf { it in CLICKS_IDLE_TIMEOUT_MINUTE_OPTIONS },
            options = CLICKS_IDLE_TIMEOUT_MINUTE_OPTIONS,
            label = { stringResource(R.string.clicks_minutes_value, it) },
            enabled = controlsEnabled,
            onSelected = { gattClient?.setIdleTimeout(it * 60) }
        )

        StubSection(stringResource(R.string.clicks_section_wireless_charging))
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_charging_automation_title),
            linkId = "clicks.charging_automation",
            description = stringResource(R.string.clicks_charging_automation_description),
            checked = chargingAutomation,
            infoText = stringResource(R.string.clicks_charging_connection_boundary_description),
            onCheckedChange = {
                chargingAutomation = it
                SettingsManager.setClicksChargingAutomationEnabled(context, it)
            }
        )
        ClicksSliderRow(
            title = stringResource(R.string.clicks_charging_start_title),
            linkId = "clicks.charging_start",
            valueLabel = "${chargingStartSlider.toInt()} %",
            value = chargingStartSlider,
            range = 5f..90f,
            steps = 16,
            enabled = chargingAutomation,
            onValueChange = {
                chargingStartSlider = it.coerceAtMost(chargingStopSlider - 1f)
            },
            onValueChangeFinished = {
                SettingsManager.setClicksChargingStartPercent(context, chargingStartSlider.toInt())
                chargingStopSlider = SettingsManager.getClicksChargingStopPercent(context).toFloat()
            }
        )
        ClicksSliderRow(
            title = stringResource(R.string.clicks_charging_stop_title),
            linkId = "clicks.charging_stop",
            valueLabel = "${chargingStopSlider.toInt()} %",
            value = chargingStopSlider,
            range = 6f..95f,
            steps = 17,
            enabled = chargingAutomation,
            onValueChange = {
                chargingStopSlider = it.coerceAtLeast(chargingStartSlider + 1f)
            },
            onValueChangeFinished = {
                SettingsManager.setClicksChargingStopPercent(context, chargingStopSlider.toInt())
            }
        )
        val reserveDisplay = powerState.batteryReserveDisplay()
        val selectedReservePercent = reserveSlider.toInt()
        val keyboardBatteryPercent = reserveDisplay?.keyboardBatteryPercent
        val currentPhoneBatteryPercent = phoneBatteryPercent
        val currentSocCalibration = socCalibration
        val availableKeyboardPercent = keyboardBatteryPercent
            ?.let { (it - selectedReservePercent).coerceAtLeast(0) }
        val chargeProjection = if (keyboardBatteryPercent != null) {
            currentSocCalibration?.estimate?.projectChargeUntilReserve(
                keyboardBatteryPercent = keyboardBatteryPercent,
                reservePercent = selectedReservePercent,
                phoneBatteryPercent = currentPhoneBatteryPercent
            )
        } else {
            null
        }
        val reserveDescription = when {
            keyboardBatteryPercent == null -> stringResource(
                if (reserveDisplay?.source == ClicksBatteryReserveSource.LastKnown) {
                    R.string.clicks_charging_reserve_soc_last_known_unavailable
                } else {
                    R.string.clicks_charging_reserve_soc_live_unavailable
                }
            )
            chargeProjection != null && currentPhoneBatteryPercent != null -> stringResource(
                if (reserveDisplay?.source == ClicksBatteryReserveSource.LastKnown) {
                    R.string.clicks_charging_reserve_projection_last_known
                } else {
                    R.string.clicks_charging_reserve_projection_live
                },
                keyboardBatteryPercent,
                currentPhoneBatteryPercent,
                chargeProjection.availableKeyboardPercent,
                chargeProjection.estimatedPhoneGainPercent
            )
            chargeProjection != null -> stringResource(
                if (reserveDisplay?.source == ClicksBatteryReserveSource.LastKnown) {
                    R.string.clicks_charging_reserve_projection_last_known_without_phone
                } else {
                    R.string.clicks_charging_reserve_projection_live_without_phone
                },
                keyboardBatteryPercent,
                chargeProjection.availableKeyboardPercent,
                chargeProjection.estimatedPhoneGainPercent
            )
            currentSocCalibration != null -> stringResource(
                if (reserveDisplay?.source == ClicksBatteryReserveSource.LastKnown) {
                    R.string.clicks_charging_reserve_calibration_last_known
                } else {
                    R.string.clicks_charging_reserve_calibration_live
                },
                keyboardBatteryPercent,
                availableKeyboardPercent ?: 0,
                currentSocCalibration.acceptedSampleCount,
                ClicksPowerSocCalibrationAggregate.MINIMUM_SAMPLE_COUNT,
                currentSocCalibration.keyboardPercentObserved,
                ClicksPowerSocCalibrationAggregate.MINIMUM_KEYBOARD_SPENT_PERCENT
            )
            reserveDisplay?.source == ClicksBatteryReserveSource.LastKnown -> stringResource(
                R.string.clicks_charging_reserve_soc_last_known,
                keyboardBatteryPercent
            )
            else -> stringResource(
                R.string.clicks_charging_reserve_soc_live,
                keyboardBatteryPercent
            )
        }
        ClicksSliderRow(
            title = stringResource(R.string.clicks_charging_reserve_title),
            linkId = "clicks.charging_reserve",
            valueLabel = "$selectedReservePercent %",
            description = reserveDescription,
            value = reserveSlider,
            range = 0f..50f,
            steps = 4,
            enabled = controlsEnabled && powerState.chargingReservePercent != null,
            onValueChange = { reserveSlider = it },
            onValueChangeFinished = { gattClient?.setChargingReserve(reserveSlider.toInt()) }
        )
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_manual_wireless_charging_title),
            linkId = "clicks.manual_wireless_charging",
            description = stringResource(R.string.clicks_manual_wireless_charging_description),
            checked = manualChargingUntil > System.currentTimeMillis(),
            enabled = controlsEnabled && powerState.chargingReservePercent != null,
            infoText = stringResource(R.string.clicks_charging_connection_boundary_description),
            onCheckedChange = { ClicksPowerKeyboardController.setManualChargingOverride(it) }
        )
        StubSection(stringResource(R.string.clicks_section_automation))
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_show_keyboard_only_with_text_focus_title),
            linkId = "clicks.show_keyboard_only_with_text_focus",
            description = stringResource(R.string.clicks_show_keyboard_only_with_text_focus_description),
            checked = showKeyboardOnlyWithTextFocus,
            onCheckedChange = { enabled ->
                showKeyboardOnlyWithTextFocus = enabled
                SettingsManager.setClicksShowKeyboardOnlyWithTextFocus(context, enabled)
            }
        )
        ClicksSettingsSwitchRow(
            title = stringResource(R.string.clicks_close_input_on_disconnect_title),
            linkId = "clicks.close_input_on_disconnect",
            description = stringResource(R.string.clicks_close_input_on_disconnect_description),
            checked = closeInputOnDisconnect,
            onCheckedChange = { enabled ->
                closeInputOnDisconnect = enabled
                SettingsManager.setClicksCloseInputOnDisconnect(context, enabled)
            }
        )
    }

}

@Composable
private fun ClicksButtonMappingsScreen(
    modifier: Modifier,
    state: ClicksPowerKeyboardState,
    redButtonMode: SettingsManager.ClicksPowerButtonMode,
    launcherButtonMode: SettingsManager.ClicksPowerButtonMode,
    altButtonMode: SettingsManager.ClicksPowerButtonMode,
    microphoneButtonMode: SettingsManager.ClicksPowerButtonMode,
    desiredRedButtonBinding: ClicksDesiredButtonBinding?,
    desiredKeyboardButtonBinding: ClicksDesiredButtonBinding?,
    desiredMicrophoneButtonBinding: ClicksDesiredButtonBinding?,
    launcherInterceptionEnabled: Boolean,
    inProgress: Set<String>,
    resultMessage: Int?,
    onBack: () -> Unit,
    onOpenLauncherInterceptionSettings: () -> Unit,
    onRedSelected: (
        String,
        SettingsManager.ClicksPowerButtonMode,
        ByteArray,
        ClicksBindingPastieraFunction?
    ) -> Unit,
    onLauncherSelected: (SettingsManager.ClicksPowerButtonMode) -> Unit,
    onAltSelected: (
        String,
        SettingsManager.ClicksPowerButtonMode,
        ByteArray,
        ClicksBindingPastieraFunction?
    ) -> Unit,
    onMicrophoneSelected: (
        String,
        SettingsManager.ClicksPowerButtonMode,
        ByteArray,
        ClicksBindingPastieraFunction?
    ) -> Unit
) {
    val nativeOutput = ClicksPowerKeyboardProtocol.nativeRemapOutput()
    val altOutput = ClicksPowerKeyboardProtocol.leftAltRemapOutput()
    val languageSwitchOutput = ClicksPowerKeyboardProtocol.languageSwitchRemapOutput()
    val dictationOutput = ClicksPowerKeyboardProtocol.dictationRemapOutput()
    val directActionOutput = ClicksPowerKeyboardProtocol.pastieraActionRemapOutput()
    val microphoneDirectActionOutput =
        ClicksPowerKeyboardProtocol.pastieraMicrophoneActionRemapOutput()
    val shortcutOutputs = ClicksButtonBindingCatalog.shortcutOutputs()

    val originalCategory = stringResource(R.string.clicks_binding_category_original)
    val functionCategory = stringResource(R.string.clicks_binding_category_function)
    val pastieraShortcutCategory = stringResource(R.string.clicks_binding_category_pastiera_shortcut)
    val keyCategory = stringResource(R.string.clicks_binding_category_key)
    val shortcutCategory = stringResource(R.string.clicks_binding_category_shortcut)
    fun groupedLabel(category: String, action: String) = "$category · $action"

    val redNative = ClicksButtonBindingChoice(
        id = "red_native",
        label = groupedLabel(originalCategory, stringResource(R.string.clicks_red_native_action)),
        description = stringResource(R.string.clicks_red_native_description),
        softwareMode = SettingsManager.ClicksPowerButtonMode.NATIVE,
        firmwareOutput = nativeOutput
    )
    val redQuickLauncher = ClicksButtonBindingChoice(
        id = "red_quick_launcher",
        label = groupedLabel(functionCategory, stringResource(R.string.clicks_button_mode_quick_launcher)),
        description = stringResource(R.string.clicks_button_mode_quick_launcher_description),
        softwareMode = SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER,
        firmwareOutput = nativeOutput
    )
    val redOpenPastiera = ClicksButtonBindingChoice(
        id = "red_open_pastiera",
        label = groupedLabel(functionCategory, stringResource(R.string.clicks_button_mode_open_pastiera)),
        description = stringResource(R.string.clicks_button_mode_open_pastiera_description),
        softwareMode = SettingsManager.ClicksPowerButtonMode.OPEN_PASTIERA,
        firmwareOutput = nativeOutput
    )
    val redToggleKeyboardMode = ClicksButtonBindingChoice(
        id = "red_toggle_keyboard_mode",
        label = groupedLabel(functionCategory, stringResource(R.string.clicks_button_mode_toggle_keyboard_mode)),
        description = stringResource(R.string.clicks_button_mode_toggle_keyboard_mode_description),
        softwareMode = SettingsManager.ClicksPowerButtonMode.TOGGLE_KEYBOARD_MODE,
        firmwareOutput = nativeOutput
    )
    val redToggleEmojiPicker = ClicksButtonBindingChoice(
        id = "red_toggle_emoji_picker",
        label = groupedLabel(functionCategory, stringResource(R.string.clicks_button_mode_toggle_emoji_picker)),
        description = stringResource(R.string.clicks_button_mode_toggle_emoji_picker_description),
        softwareMode = SettingsManager.ClicksPowerButtonMode.TOGGLE_EMOJI_PICKER,
        firmwareOutput = nativeOutput
    )
    val redAlt = ClicksButtonBindingChoice(
        id = "red_alt",
        label = groupedLabel(keyCategory, stringResource(R.string.clicks_button_mode_alt)),
        description = stringResource(R.string.clicks_button_mode_alt_description),
        softwareMode = SettingsManager.ClicksPowerButtonMode.NATIVE,
        firmwareOutput = altOutput
    )
    val redTab = ClicksButtonBindingChoice(
        id = "red_tab",
        label = groupedLabel(keyCategory, stringResource(R.string.clicks_button_mode_tab)),
        description = stringResource(R.string.clicks_button_mode_tab_description),
        softwareMode = SettingsManager.ClicksPowerButtonMode.TAB,
        firmwareOutput = nativeOutput
    )
    val redSym = ClicksButtonBindingChoice(
        id = "red_sym",
        label = groupedLabel(keyCategory, stringResource(R.string.clicks_button_mode_sym)),
        description = stringResource(R.string.clicks_button_mode_sym_description),
        softwareMode = SettingsManager.ClicksPowerButtonMode.SYM,
        firmwareOutput = nativeOutput
    )
    val redLanguageSwitch = ClicksButtonBindingChoice(
        id = "red_language_switch",
        label = groupedLabel(pastieraShortcutCategory, stringResource(R.string.clicks_function_language_switch)),
        description = stringResource(R.string.clicks_function_language_switch_description),
        softwareMode = SettingsManager.ClicksPowerButtonMode.NATIVE,
        firmwareOutput = languageSwitchOutput,
        pastieraFunction = ClicksBindingPastieraFunction.LANGUAGE_SWITCH
    )
    val redShortcutChoices = shortcutOutputs.map { (label, bytes) ->
        ClicksButtonBindingChoice(
            id = "red_shortcut_${bytes.toHexPair()}",
            label = groupedLabel(shortcutCategory, label),
            description = stringResource(R.string.clicks_binding_shortcut_description, label),
            softwareMode = SettingsManager.ClicksPowerButtonMode.NATIVE,
            firmwareOutput = bytes
        )
    }
    val redChoices = listOf(
        redNative,
        redQuickLauncher,
        redOpenPastiera,
        redToggleKeyboardMode,
        redToggleEmojiPicker,
        redLanguageSwitch,
        redAlt,
        redTab,
        redSym
    ) + redShortcutChoices
    val desiredRedSelection = ClicksButtonBindingCatalog.desiredSelection(
        desiredRedButtonBinding,
        redChoices
    )
    val redDirectSelection = ClicksButtonBindingCatalog.directActionSelection(redButtonMode, redChoices)
    val redSelected = desiredRedSelection ?: redDirectSelection ?: if (state.tabRemap != null) {
        ClicksButtonBindingCatalog.firmwareSelection(state.tabRemap, redChoices)
            ?.takeUnless { choice ->
                choice.id == redNative.id ||
                    choice.id == redTab.id ||
                    choice.id == redSym.id ||
                    choice.softwareMode?.directActionOrNull() != null
            }
            ?: ClicksButtonBindingChoice(
                id = "red_custom",
                label = groupedLabel(
                    shortcutCategory,
                    stringResource(R.string.clicks_remap_custom_value, state.tabRemap.toHexPair())
                ),
                description = stringResource(R.string.clicks_binding_custom_description),
                softwareMode = SettingsManager.ClicksPowerButtonMode.NATIVE,
                firmwareOutput = state.tabRemap
            )
    } else {
        when (redButtonMode) {
            SettingsManager.ClicksPowerButtonMode.NATIVE -> redNative
            SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER -> redQuickLauncher
            SettingsManager.ClicksPowerButtonMode.OPEN_PASTIERA -> redOpenPastiera
            SettingsManager.ClicksPowerButtonMode.TOGGLE_KEYBOARD_MODE -> redToggleKeyboardMode
            SettingsManager.ClicksPowerButtonMode.TOGGLE_EMOJI_PICKER -> redToggleEmojiPicker
            SettingsManager.ClicksPowerButtonMode.ALT -> redAlt
            SettingsManager.ClicksPowerButtonMode.TAB -> redTab
            SettingsManager.ClicksPowerButtonMode.SYM -> redSym
        }
    }

    val launcherChoices = SettingsManager.ClicksPowerButtonMode.entries.map { mode ->
        val category = if (mode.directActionOrNull() != null) {
            functionCategory
        } else if (mode == SettingsManager.ClicksPowerButtonMode.NATIVE) {
            originalCategory
        } else {
            keyCategory
        }
        val action = if (mode == SettingsManager.ClicksPowerButtonMode.NATIVE) {
            stringResource(R.string.clicks_launcher_native_action)
        } else {
            clicksPowerButtonModeLabel(mode)
        }
        ClicksButtonBindingChoice(
            id = "launcher_${mode.persistedValue}",
            label = groupedLabel(category, action),
            description = if (mode == SettingsManager.ClicksPowerButtonMode.NATIVE) {
                stringResource(R.string.clicks_launcher_native_description)
            } else {
                clicksPowerButtonModeDescription(mode)
            },
            softwareMode = mode
        )
    }
    val launcherSelected = launcherChoices.first { it.softwareMode == launcherButtonMode }

    @Composable
    fun firmwareChoices(
        bindingId: String,
        nativeAction: String,
        nativeDescription: String,
        includeAlt: Boolean
    ): List<ClicksButtonBindingChoice> = buildList {
        add(
            ClicksButtonBindingChoice(
                id = "${bindingId}_native",
                label = groupedLabel(originalCategory, nativeAction),
                description = nativeDescription,
                softwareMode = SettingsManager.ClicksPowerButtonMode.NATIVE,
                firmwareOutput = nativeOutput
            )
        )
        add(
            ClicksButtonBindingChoice(
                id = "${bindingId}_language_switch",
                label = groupedLabel(
                    pastieraShortcutCategory,
                    stringResource(R.string.clicks_function_language_switch)
                ),
                description = stringResource(R.string.clicks_function_language_switch_description),
                softwareMode = SettingsManager.ClicksPowerButtonMode.NATIVE,
                firmwareOutput = languageSwitchOutput,
                pastieraFunction = ClicksBindingPastieraFunction.LANGUAGE_SWITCH
            )
        )
        if (bindingId == CLICKS_BINDING_MICROPHONE) {
            add(
                ClicksButtonBindingChoice(
                    id = "${bindingId}_dictation",
                    label = groupedLabel(
                        pastieraShortcutCategory,
                        stringResource(R.string.clicks_function_dictation)
                    ),
                    description = stringResource(R.string.clicks_function_dictation_description),
                    softwareMode = SettingsManager.ClicksPowerButtonMode.NATIVE,
                    firmwareOutput = dictationOutput,
                    pastieraFunction = ClicksBindingPastieraFunction.DICTATION
                )
            )
        }
        if (includeAlt) {
            add(
                ClicksButtonBindingChoice(
                    id = "${bindingId}_alt",
                    label = groupedLabel(keyCategory, stringResource(R.string.clicks_button_mode_alt)),
                    description = stringResource(R.string.clicks_binding_alt_output_description),
                    softwareMode = SettingsManager.ClicksPowerButtonMode.NATIVE,
                    firmwareOutput = altOutput
                )
            )
        }
        shortcutOutputs.forEach { (label, bytes) ->
            add(
                ClicksButtonBindingChoice(
                    id = "${bindingId}_shortcut_${bytes.toHexPair()}",
                    label = groupedLabel(shortcutCategory, label),
                    description = stringResource(R.string.clicks_binding_shortcut_description, label),
                    softwareMode = SettingsManager.ClicksPowerButtonMode.NATIVE,
                    firmwareOutput = bytes
                )
            )
        }
    }

    val altFirmwareChoices = firmwareChoices(
        bindingId = CLICKS_BINDING_ALT,
        nativeAction = stringResource(R.string.clicks_alt_native_action),
        nativeDescription = stringResource(R.string.clicks_alt_native_description),
        includeAlt = false
    )
    val altDirectChoices = listOf(
        SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER,
        SettingsManager.ClicksPowerButtonMode.OPEN_PASTIERA,
        SettingsManager.ClicksPowerButtonMode.TOGGLE_KEYBOARD_MODE,
        SettingsManager.ClicksPowerButtonMode.TOGGLE_EMOJI_PICKER
    ).map { mode ->
        ClicksButtonBindingChoice(
            id = "alt_${mode.persistedValue}",
            label = groupedLabel(functionCategory, clicksPowerButtonModeLabel(mode)),
            description = clicksPowerButtonModeDescription(mode),
            softwareMode = mode,
            firmwareOutput = directActionOutput
        )
    }
    val altChoices = altFirmwareChoices.take(1) + altDirectChoices + altFirmwareChoices.drop(1)
    val microphoneFirmwareChoices = firmwareChoices(
        bindingId = CLICKS_BINDING_MICROPHONE,
        nativeAction = stringResource(R.string.clicks_microphone_native_action),
        nativeDescription = stringResource(R.string.clicks_microphone_native_description),
        includeAlt = true
    )
    val microphoneEmojiChoice = ClicksButtonBindingChoice(
        id = "microphone_toggle_emoji_picker",
        label = groupedLabel(
            functionCategory,
            stringResource(R.string.clicks_button_mode_toggle_emoji_picker)
        ),
        description = stringResource(R.string.clicks_button_mode_toggle_emoji_picker_description),
        softwareMode = SettingsManager.ClicksPowerButtonMode.TOGGLE_EMOJI_PICKER,
        firmwareOutput = microphoneDirectActionOutput
    )
    val microphoneChoices =
        microphoneFirmwareChoices.take(1) + microphoneEmojiChoice + microphoneFirmwareChoices.drop(1)
    @Composable
    fun selectedFirmwareChoice(
        bindingId: String,
        current: ByteArray?,
        choices: List<ClicksButtonBindingChoice>
    ): Pair<ClicksButtonBindingChoice, List<ClicksButtonBindingChoice>> {
        val normalized = current ?: nativeOutput
        ClicksButtonBindingCatalog.firmwareSelection(normalized, choices)?.let {
            return it to choices
        }
        val custom = ClicksButtonBindingChoice(
            id = "${bindingId}_custom",
            label = groupedLabel(
                shortcutCategory,
                stringResource(R.string.clicks_remap_custom_value, normalized.toHexPair())
            ),
            description = stringResource(R.string.clicks_binding_custom_description),
            firmwareOutput = normalized
        )
        return custom to (choices + custom)
    }
    val desiredAltSelection = ClicksButtonBindingCatalog.desiredSelection(
        desiredKeyboardButtonBinding,
        altChoices
    )
    val (altSelected, displayedAltChoices) = desiredAltSelection
        ?.let { it to altChoices }
        ?: ClicksButtonBindingCatalog.directActionSelection(altButtonMode, altChoices)
            ?.let { it to altChoices }
        ?: selectedFirmwareChoice(
            CLICKS_BINDING_ALT,
            state.altRemap?.takeUnless { it.contentEquals(altOutput) },
            altChoices
        )
    val desiredMicrophoneSelection = ClicksButtonBindingCatalog.desiredSelection(
        desiredMicrophoneButtonBinding,
        microphoneChoices
    )
    val (microphoneSelected, displayedMicrophoneChoices) = desiredMicrophoneSelection
        ?.let { it to microphoneChoices }
        ?: ClicksButtonBindingCatalog.directActionSelection(microphoneButtonMode, microphoneChoices)
            ?.let { it to microphoneChoices }
        ?: selectedFirmwareChoice(
            CLICKS_BINDING_MICROPHONE,
            state.geminiRemap,
            microphoneChoices
        )
    val redBindingPending = desiredRedButtonBinding?.let {
        !ClicksButtonBindingSyncPolicy.isConfirmed(state, ClicksButtonBindingTarget.RED, it.firmwareOutput)
    } == true
    val keyboardBindingPending = desiredKeyboardButtonBinding?.let {
        !ClicksButtonBindingSyncPolicy.isConfirmed(state, ClicksButtonBindingTarget.KEYBOARD, it.firmwareOutput)
    } == true
    val microphoneBindingPending = desiredMicrophoneButtonBinding?.let {
        !ClicksButtonBindingSyncPolicy.isConfirmed(state, ClicksButtonBindingTarget.MICROPHONE, it.firmwareOutput)
    } == true

    HardwareProfileScaffold(
        modifier = modifier,
        title = stringResource(R.string.clicks_button_bindings_title),
        description = stringResource(R.string.clicks_button_bindings_screen_description),
        onBack = onBack
    ) {
        ClicksDeviceInfoRow(
            icon = if (launcherInterceptionEnabled) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            title = stringResource(R.string.clicks_launcher_interception_title),
            description = stringResource(
                if (launcherInterceptionEnabled) {
                    R.string.clicks_launcher_interception_enabled
                } else {
                    R.string.clicks_launcher_interception_disabled
                }
            ),
            onClick = onOpenLauncherInterceptionSettings
        )
        ClicksButtonBindingRow(
            title = stringResource(R.string.clicks_launcher_button_title),
            linkId = "clicks.buttons.launcher_button",
            hardwareDescription = stringResource(R.string.clicks_launcher_button_hardware_description),
            selected = launcherSelected,
            choices = launcherChoices,
            enabled = true,
            applying = false,
            pending = false,
            onSelected = { choice -> onLauncherSelected(requireNotNull(choice.softwareMode)) }
        )
        ClicksButtonBindingRow(
            title = stringResource(R.string.clicks_red_button_title),
            linkId = "clicks.buttons.red_button",
            hardwareDescription = stringResource(R.string.clicks_red_button_hardware_description),
            selected = redSelected,
            choices = if (redSelected.id == "red_custom") redChoices + redSelected else redChoices,
            enabled = CLICKS_BINDING_RED !in inProgress,
            applying = CLICKS_BINDING_RED in inProgress,
            pending = redBindingPending,
            onSelected = { choice ->
                onRedSelected(
                    choice.id,
                    requireNotNull(choice.softwareMode),
                    requireNotNull(choice.firmwareOutput),
                    choice.pastieraFunction
                )
            }
        )
        ClicksButtonBindingRow(
            title = stringResource(R.string.clicks_alt_button_title),
            linkId = "clicks.buttons.alt_button",
            hardwareDescription = stringResource(R.string.clicks_alt_button_hardware_description),
            selected = altSelected,
            choices = displayedAltChoices,
            enabled = CLICKS_BINDING_ALT !in inProgress,
            applying = CLICKS_BINDING_ALT in inProgress,
            pending = keyboardBindingPending,
            onSelected = { choice ->
                onAltSelected(
                    choice.id,
                    requireNotNull(choice.softwareMode),
                    requireNotNull(choice.firmwareOutput),
                    choice.pastieraFunction
                )
            }
        )
        ClicksButtonBindingRow(
            title = stringResource(R.string.clicks_microphone_button_title),
            linkId = "clicks.buttons.microphone_button",
            hardwareDescription = stringResource(R.string.clicks_microphone_button_hardware_description),
            selected = microphoneSelected,
            choices = displayedMicrophoneChoices,
            enabled = CLICKS_BINDING_MICROPHONE !in inProgress,
            applying = CLICKS_BINDING_MICROPHONE in inProgress,
            pending = microphoneBindingPending,
            onSelected = { choice ->
                onMicrophoneSelected(
                    choice.id,
                    choice.softwareMode ?: SettingsManager.ClicksPowerButtonMode.NATIVE,
                    requireNotNull(choice.firmwareOutput),
                    choice.pastieraFunction
                )
            }
        )
        resultMessage?.let { message ->
            Text(
                text = stringResource(message),
                color = if (message == R.string.clicks_button_binding_failure) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ClicksNumberRowMappingsScreen(
    modifier: Modifier,
    state: ClicksPowerKeyboardState,
    onBack: () -> Unit,
    onSelected: (Int, ByteArray) -> Unit
) {
    HardwareProfileScaffold(
        modifier = modifier,
        title = stringResource(R.string.clicks_number_row_title),
        description = stringResource(R.string.clicks_number_row_description),
        onBack = onBack
    ) {
        state.numberRemaps.forEachIndexed { index, bytes ->
            ClicksRemapDropdownRow(
                title = "SYM + ${index + 1}",
                selectedBytes = bytes,
                presets = numberRemapPresets(),
                onSelected = {
                    onSelected(ClicksPowerKeyboardProtocol.NUMBER_REMAP_COMMANDS[index], it)
                }
            )
        }
    }
}

@Composable
private fun ClicksHostSlotsScreen(
    modifier: Modifier,
    state: ClicksPowerKeyboardState,
    onBack: () -> Unit,
    onEdit: (Int) -> Unit
) {
    HardwareProfileScaffold(
        modifier = modifier,
        title = stringResource(R.string.clicks_host_slots_title),
        description = stringResource(R.string.clicks_host_slots_description),
        onBack = onBack
    ) {
        state.hostConfigurations.forEachIndexed { index, configuration ->
            if (configuration != null && configuration != 0) {
                ClicksValueRow(
                    title = if (state.activeHostSlot == index + 1) {
                        stringResource(R.string.clicks_host_slot_active_label, index + 1)
                    } else {
                        stringResource(R.string.clicks_host_slot_label, index + 1)
                    },
                    value = state.hostNames[index].orEmpty().ifBlank {
                        stringResource(R.string.clicks_host_name_unnamed)
                    },
                    onClick = { onEdit(index) }
                )
            }
        }
    }
}

private fun hasClicksBluetoothPermission(context: android.content.Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED
}

private fun isClicksLauncherInterceptionEnabled(context: android.content.Context): Boolean {
    val expected = ComponentName(context, ClicksLauncherButtonAccessibilityService::class.java)
    val manager = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)
        as? AccessibilityManager ?: return false
    return manager
        .getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { info ->
            val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
            ComponentName(serviceInfo.packageName, serviceInfo.name) == expected
        }
}

private fun openClicksFirmwareUpdates(context: android.content.Context) {
    val launchIntent = context.packageManager
        .getLaunchIntentForPackage(CLICKS_COMPANION_PACKAGE)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val intent = launchIntent ?: Intent(
        Intent.ACTION_VIEW,
        Uri.parse(CLICKS_COMPANION_PLAY_STORE_URL)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private const val CLICKS_COMPANION_PACKAGE = "com.clicks.companionapp"
private const val CLICKS_COMPANION_PLAY_STORE_URL =
    "https://play.google.com/store/apps/details?id=com.clicks.companionapp&hl=en"

@Composable
private fun ClicksDeviceInfoRow(
    icon: ImageVector,
    linkId: String? = null,
    title: String,
    description: String,
    onClick: (() -> Unit)? = null
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .settingRow(linkId, onClick)
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
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun applyClicksRemaps(
    client: ClicksPowerKeyboardGattClient,
    writes: List<ClicksRemapWrite>,
    onComplete: (Boolean) -> Unit
) {
    fun applyAt(index: Int) {
        if (index >= writes.size) {
            onComplete(true)
            return
        }
        val write = writes[index]
        client.setSpecialKeyRemap(write.command, write.output) { success ->
            if (success) applyAt(index + 1) else onComplete(false)
        }
    }
    applyAt(0)
}

private fun applyClicksRecommendedSettings(
    client: ClicksPowerKeyboardGattClient,
    remapWrites: List<ClicksRemapWrite>,
    onComplete: (Boolean) -> Unit
) {
    client.setCapsLock(false) { capsLockSuccess ->
        if (!capsLockSuccess) {
            onComplete(false)
            return@setCapsLock
        }
        client.setCursorMode(false) { cursorModeSuccess ->
            if (!cursorModeSuccess) {
                onComplete(false)
                return@setCursorMode
            }
            applyClicksRemaps(client, remapWrites) { remapsSuccess ->
                if (remapsSuccess) client.verifyRecommendedSettings(onComplete) else onComplete(false)
            }
        }
    }
}

private fun applyClicksBindingPastieraFunction(
    context: android.content.Context,
    function: ClicksBindingPastieraFunction
) {
    when (function) {
        ClicksBindingPastieraFunction.LANGUAGE_SWITCH ->
            SettingsManager.setCtrlSpaceLayoutSwitchEnabled(context, true)
        ClicksBindingPastieraFunction.DICTATION ->
            SettingsManager.setAltCtrlSpeechShortcutEnabled(context, true)
    }
}

@Composable
private fun ClicksSettingsSwitchRow(
    title: String,
    linkId: String? = null,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    infoText: String? = null
) {
    var showInfo by remember { mutableStateOf(false) }
    if (showInfo && infoText != null) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            text = { Text(infoText) },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text(stringResource(android.R.string.ok)) }
            }
        )
    }
    Surface(modifier = Modifier.settingRow(linkId).fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (infoText != null) {
                IconButton(onClick = { showInfo = true }) {
                    Icon(Icons.Filled.Info, contentDescription = null)
                }
            }
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun ClicksValueRow(
    title: String,
    value: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClicksRemapDropdownRow(
    title: String,
    selectedBytes: ByteArray?,
    presets: List<ClicksRemapPreset>,
    onSelected: (ByteArray) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = selectedBytes?.let { current ->
        presets.firstOrNull { it.bytes.contentEquals(current) }
            ?: ClicksRemapPreset(
                stringResource(R.string.clicks_remap_custom_value, current.toHexPair()),
                current
            )
    } ?: presets.first()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text(title) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        expanded = false
                        onSelected(preset.bytes)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClicksIntDropdownRow(
    title: String,
    linkId: String? = null,
    selected: Int?,
    options: List<Int>,
    label: @Composable (Int) -> String,
    enabled: Boolean,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.settingRow(linkId).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = selected?.let { label(it) } ?: "–",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(title) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClicksButtonBindingRow(
    title: String,
    linkId: String? = null,
    hardwareDescription: String,
    selected: ClicksButtonBindingChoice,
    choices: List<ClicksButtonBindingChoice>,
    enabled: Boolean,
    applying: Boolean,
    pending: Boolean,
    onSelected: (ClicksButtonBindingChoice) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.settingRow(linkId).fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = if (applying) {
                stringResource(R.string.clicks_button_binding_applying)
            } else {
                selected.compactLabel()
            },
            onValueChange = {},
            readOnly = true,
            enabled = enabled || applying,
            label = { Text(title) },
            supportingText = {
                Text(
                    text = if (pending) {
                        stringResource(R.string.clicks_button_binding_pending)
                    } else {
                        hardwareDescription
                    },
                    color = if (pending) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = selected.bindingIcon(),
                    contentDescription = null,
                    tint = selected.bindingTint()
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Text(
                            choice.compactLabel(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = choice.bindingIcon(),
                            contentDescription = null,
                            tint = choice.bindingTint()
                        )
                    },
                    trailingIcon = if (choice.id == selected.id) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelected(choice)
                    }
                )
            }
        }
    }
}

private fun ClicksButtonBindingChoice.compactLabel(): String = label.substringAfter(" · ")

private fun ClicksButtonBindingChoice.bindingIcon(): ImageVector = when {
    id.endsWith("_native") -> Icons.Filled.Restore
    softwareMode == SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER -> Icons.Filled.Search
    softwareMode == SettingsManager.ClicksPowerButtonMode.OPEN_PASTIERA -> Icons.Filled.Settings
    softwareMode == SettingsManager.ClicksPowerButtonMode.TOGGLE_KEYBOARD_MODE -> Icons.Filled.Keyboard
    softwareMode == SettingsManager.ClicksPowerButtonMode.TOGGLE_EMOJI_PICKER -> Icons.Filled.EmojiEmotions
    softwareMode == SettingsManager.ClicksPowerButtonMode.ALT -> Icons.Filled.KeyboardAlt
    softwareMode == SettingsManager.ClicksPowerButtonMode.TAB -> Icons.AutoMirrored.Filled.KeyboardTab
    softwareMode == SettingsManager.ClicksPowerButtonMode.SYM -> Icons.Filled.EmojiSymbols
    pastieraFunction == ClicksBindingPastieraFunction.LANGUAGE_SWITCH -> Icons.Filled.Language
    pastieraFunction == ClicksBindingPastieraFunction.DICTATION -> Icons.Filled.Mic
    else -> Icons.Filled.KeyboardCommandKey
}

@Composable
private fun ClicksButtonBindingChoice.bindingTint(): Color = when {
    id.endsWith("_native") -> MaterialTheme.colorScheme.onSurfaceVariant
    softwareMode?.directActionOrNull() != null || pastieraFunction != null ->
        MaterialTheme.colorScheme.primary
    softwareMode == SettingsManager.ClicksPowerButtonMode.ALT ||
        softwareMode == SettingsManager.ClicksPowerButtonMode.TAB ||
        softwareMode == SettingsManager.ClicksPowerButtonMode.SYM -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}

@Composable
private fun clicksPowerButtonModeLabel(mode: SettingsManager.ClicksPowerButtonMode): String =
    stringResource(
        when (mode) {
            SettingsManager.ClicksPowerButtonMode.NATIVE -> R.string.clicks_button_mode_native
            SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER -> R.string.clicks_button_mode_quick_launcher
            SettingsManager.ClicksPowerButtonMode.OPEN_PASTIERA -> R.string.clicks_button_mode_open_pastiera
            SettingsManager.ClicksPowerButtonMode.TOGGLE_KEYBOARD_MODE ->
                R.string.clicks_button_mode_toggle_keyboard_mode
            SettingsManager.ClicksPowerButtonMode.TOGGLE_EMOJI_PICKER ->
                R.string.clicks_button_mode_toggle_emoji_picker
            SettingsManager.ClicksPowerButtonMode.ALT -> R.string.clicks_button_mode_alt
            SettingsManager.ClicksPowerButtonMode.TAB -> R.string.clicks_button_mode_tab
            SettingsManager.ClicksPowerButtonMode.SYM -> R.string.clicks_button_mode_sym
        }
    )

@Composable
private fun clicksPowerButtonModeDescription(mode: SettingsManager.ClicksPowerButtonMode): String =
    stringResource(
        when (mode) {
            SettingsManager.ClicksPowerButtonMode.NATIVE -> R.string.clicks_button_mode_native_description
            SettingsManager.ClicksPowerButtonMode.QUICK_LAUNCHER ->
                R.string.clicks_button_mode_quick_launcher_description
            SettingsManager.ClicksPowerButtonMode.OPEN_PASTIERA ->
                R.string.clicks_button_mode_open_pastiera_description
            SettingsManager.ClicksPowerButtonMode.TOGGLE_KEYBOARD_MODE ->
                R.string.clicks_button_mode_toggle_keyboard_mode_description
            SettingsManager.ClicksPowerButtonMode.TOGGLE_EMOJI_PICKER ->
                R.string.clicks_button_mode_toggle_emoji_picker_description
            SettingsManager.ClicksPowerButtonMode.ALT -> R.string.clicks_button_mode_alt_description
            SettingsManager.ClicksPowerButtonMode.TAB -> R.string.clicks_button_mode_tab_description
            SettingsManager.ClicksPowerButtonMode.SYM -> R.string.clicks_button_mode_sym_description
        }
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClicksNumberRowInputModeRow(
    selected: SettingsManager.ClicksNumberRowInputMode,
    onSelected: (SettingsManager.ClicksNumberRowInputMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.settingRow("clicks.number_row_input_mode").fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = clicksNumberRowInputModeLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.clicks_number_row_input_mode_title)) },
            supportingText = { Text(stringResource(R.string.clicks_number_row_input_mode_description)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SettingsManager.ClicksNumberRowInputMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(clicksNumberRowInputModeLabel(mode)) },
                    onClick = {
                        expanded = false
                        onSelected(mode)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClicksOverlappingKeysModeRow(
    selected: SettingsManager.ClicksOverlappingKeysMode,
    onSelected: (SettingsManager.ClicksOverlappingKeysMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.settingRow("clicks.release_order").fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = clicksOverlappingKeysModeLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.clicks_release_order_title)) },
            supportingText = { Text(stringResource(R.string.clicks_release_order_description)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SettingsManager.ClicksOverlappingKeysMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(clicksOverlappingKeysModeLabel(mode)) },
                    onClick = {
                        expanded = false
                        onSelected(mode)
                    }
                )
            }
        }
    }
}

@Composable
private fun clicksOverlappingKeysModeLabel(mode: SettingsManager.ClicksOverlappingKeysMode): String =
    stringResource(
        when (mode) {
            SettingsManager.ClicksOverlappingKeysMode.OFF ->
                R.string.clicks_overlapping_keys_mode_off
            SettingsManager.ClicksOverlappingKeysMode.ADJACENT_ONLY ->
                R.string.clicks_overlapping_keys_mode_adjacent
            SettingsManager.ClicksOverlappingKeysMode.ALL_NON_MODIFIERS ->
                R.string.clicks_overlapping_keys_mode_all
        }
    )

@Composable
private fun clicksNumberRowInputModeLabel(mode: SettingsManager.ClicksNumberRowInputMode): String =
    stringResource(
        when (mode) {
            SettingsManager.ClicksNumberRowInputMode.NORMAL -> R.string.clicks_number_row_input_mode_normal
            SettingsManager.ClicksNumberRowInputMode.IGNORE_WHILE_ADJACENT_KEY_HELD ->
                R.string.clicks_number_row_input_mode_adjacent
            SettingsManager.ClicksNumberRowInputMode.IGNORE_WHILE_ANY_KEY_HELD ->
                R.string.clicks_number_row_input_mode_any
            SettingsManager.ClicksNumberRowInputMode.LONG_PRESS -> R.string.clicks_number_row_input_mode_long_press
            SettingsManager.ClicksNumberRowInputMode.IGNORE_ALL -> R.string.clicks_number_row_input_mode_ignore_all
        }
    )

@Composable
private fun numberRemapPresets(): List<ClicksRemapPreset> = listOf(
    ClicksRemapPreset(stringResource(R.string.clicks_remap_native_number_action), byteArrayOf(0x00, 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_escape), byteArrayOf(0x29, 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_volume_up), byteArrayOf(0x80.toByte(), 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_volume_down), byteArrayOf(0x81.toByte(), 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_volume_mute), byteArrayOf(0x7f, 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_play_pause), byteArrayOf(0xcd.toByte(), 0xff.toByte())),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_media_next), byteArrayOf(0xb5.toByte(), 0xff.toByte())),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_media_previous), byteArrayOf(0xb6.toByte(), 0xff.toByte())),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_page_up), byteArrayOf(0x4b, 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_page_down), byteArrayOf(0x4e, 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_home), byteArrayOf(0x4a, 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_end), byteArrayOf(0x4d, 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_left_bracket), byteArrayOf(0x2f, 0x00)),
    ClicksRemapPreset(stringResource(R.string.clicks_number_preset_right_bracket), byteArrayOf(0x30, 0x00))
)

@Composable
private fun ClicksSliderRow(
    title: String,
    linkId: String? = null,
    valueLabel: String,
    description: String? = null,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(modifier = Modifier.settingRow(linkId).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            steps = steps,
            enabled = enabled
        )
    }
}

@Composable
private fun hostSlotsSummary(state: ClicksPowerKeyboardState): String {
    val occupied = state.hostConfigurations.mapIndexedNotNull { index, configuration ->
        if (configuration != null && configuration != 0) {
            val name = state.hostNames[index]?.takeIf(String::isNotBlank)
            if (name == null) "${index + 1}" else "${index + 1} ($name)"
        } else {
            null
        }
    }
    return stringResource(
        R.string.clicks_host_slots_value,
        state.activeHostSlot?.toString() ?: "?",
        occupied.joinToString().ifBlank { stringResource(R.string.clicks_host_slots_none) }
    )
}

@Composable
private fun remapLabel(bytes: ByteArray): String = when {
    bytes.contentEquals(byteArrayOf(0x29, 0x00)) -> stringResource(R.string.clicks_number_preset_escape)
    bytes.contentEquals(byteArrayOf(0x80.toByte(), 0x00)) -> stringResource(R.string.clicks_number_preset_volume_up)
    bytes.contentEquals(byteArrayOf(0x81.toByte(), 0x00)) -> stringResource(R.string.clicks_number_preset_volume_down)
    bytes.contentEquals(byteArrayOf(0x7f, 0x00)) -> stringResource(R.string.clicks_number_preset_volume_mute)
    bytes.contentEquals(byteArrayOf(0xcd.toByte(), 0xff.toByte())) -> stringResource(R.string.clicks_number_preset_play_pause)
    bytes.contentEquals(byteArrayOf(0xb5.toByte(), 0xff.toByte())) -> stringResource(R.string.clicks_number_preset_media_next)
    bytes.contentEquals(byteArrayOf(0xb6.toByte(), 0xff.toByte())) -> stringResource(R.string.clicks_number_preset_media_previous)
    bytes.contentEquals(byteArrayOf(0x4b, 0x00)) -> stringResource(R.string.clicks_number_preset_page_up)
    bytes.contentEquals(byteArrayOf(0x4e, 0x00)) -> stringResource(R.string.clicks_number_preset_page_down)
    bytes.contentEquals(byteArrayOf(0x4a, 0x00)) -> stringResource(R.string.clicks_number_preset_home)
    bytes.contentEquals(byteArrayOf(0x4d, 0x00)) -> stringResource(R.string.clicks_number_preset_end)
    bytes.contentEquals(byteArrayOf(0x2f, 0x00)) -> stringResource(R.string.clicks_number_preset_left_bracket)
    bytes.contentEquals(byteArrayOf(0x30, 0x00)) -> stringResource(R.string.clicks_number_preset_right_bracket)
    bytes.contentEquals(byteArrayOf(0xe0.toByte(), 0x2c)) -> "Ctrl+Space"
    bytes.contentEquals(byteArrayOf(0xe2.toByte(), 0x00)) -> "Alt"
    bytes.contentEquals(byteArrayOf(0xe2.toByte(), 0x07)) -> "Alt+D"
    bytes.contentEquals(byteArrayOf(0xe2.toByte(), 0x0e)) -> "Alt+K"
    bytes.contentEquals(byteArrayOf(0xe2.toByte(), 0x16)) -> "Alt+S"
    bytes.contentEquals(byteArrayOf(0xe2.toByte(), 0x37)) -> "Alt+."
    bytes.contentEquals(byteArrayOf(0x00, 0x2d)) -> "−"
    else -> stringResource(R.string.clicks_remap_custom_value, bytes.toHexPair())
}

private fun ByteArray?.toHexPair(): String = this
    ?.takeIf { it.size == 2 }
    ?.joinToString(" ") { "%02X".format(it.toInt() and 0xff) }
    ?: "–"

@Composable
private fun ClicksSingleChoiceDialog(
    title: String,
    choices: List<Int>,
    label: @Composable (Int) -> String,
    onChoice: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { value -> TextButton(onClick = { onChoice(value) }) { Text(label(value)) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

private data class ClicksRemapPreset(val label: String, val bytes: ByteArray)

private val CLICKS_BACKLIGHT_TIMEOUT_OPTIONS = listOf(2, 5, 15, 30, 45, 60)
private val CLICKS_IDLE_TIMEOUT_MINUTE_OPTIONS = listOf(2, 5, 15, 30, 45, 60)

@Composable
private fun ClicksHostNameDialog(
    state: ClicksPowerKeyboardState,
    slotIndex: Int,
    onApply: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(slotIndex) { mutableStateOf(state.hostNames[slotIndex].orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clicks_host_name_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.clicks_host_slot_label, slotIndex + 1))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.clicks_host_name_label)) },
                    supportingText = { Text(stringResource(R.string.clicks_host_name_limit)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(slotIndex, name) }) {
                Text(stringResource(R.string.clicks_apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ClicksNumberRemapDialog(
    keyIndex: Int,
    state: ClicksPowerKeyboardState,
    onApply: (Int, ByteArray) -> Unit,
    onDismiss: () -> Unit
) {
    val labels = listOf(
        R.string.clicks_number_preset_disabled,
        R.string.clicks_number_preset_escape,
        R.string.clicks_number_preset_volume_up,
        R.string.clicks_number_preset_volume_down,
        R.string.clicks_number_preset_volume_mute,
        R.string.clicks_number_preset_play_pause,
        R.string.clicks_number_preset_media_next,
        R.string.clicks_number_preset_media_previous,
        R.string.clicks_number_preset_page_up,
        R.string.clicks_number_preset_page_down,
        R.string.clicks_number_preset_home,
        R.string.clicks_number_preset_end,
        R.string.clicks_number_preset_left_bracket,
        R.string.clicks_number_preset_right_bracket
    ).map { stringResource(it) }
    val values = listOf(
        byteArrayOf(0x00, 0x00),
        byteArrayOf(0x29, 0x00),
        byteArrayOf(0x80.toByte(), 0x00),
        byteArrayOf(0x81.toByte(), 0x00),
        byteArrayOf(0x7f, 0x00),
        byteArrayOf(0xcd.toByte(), 0xff.toByte()),
        byteArrayOf(0xb5.toByte(), 0xff.toByte()),
        byteArrayOf(0xb6.toByte(), 0xff.toByte()),
        byteArrayOf(0x4b, 0x00),
        byteArrayOf(0x4e, 0x00),
        byteArrayOf(0x4a, 0x00),
        byteArrayOf(0x4d, 0x00),
        byteArrayOf(0x2f, 0x00),
        byteArrayOf(0x30, 0x00)
    )
    val knownPresets = labels.zip(values).map { (label, bytes) -> ClicksRemapPreset(label, bytes) }
    val currentBytes = state.numberRemaps[keyIndex]
    val presets = if (
        currentBytes != null && knownPresets.none { it.bytes.contentEquals(currentBytes) }
    ) {
        listOf(
            ClicksRemapPreset(
                stringResource(R.string.clicks_remap_custom_value, currentBytes.toHexPair()),
                currentBytes.copyOf()
            )
        ) + knownPresets
    } else {
        knownPresets
    }
    var selected by remember(keyIndex, currentBytes?.contentHashCode()) {
        mutableStateOf(
            presets.firstOrNull { preset ->
                currentBytes != null && preset.bytes.contentEquals(currentBytes)
            } ?: presets.first()
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clicks_number_row_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("SYM + ${keyIndex + 1}", style = MaterialTheme.typography.titleMedium)
                presets.forEach { preset ->
                    val isSelected = selected.bytes.contentEquals(preset.bytes)
                    TextButton(onClick = { selected = preset }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isSelected) "✓ ${preset.label}" else preset.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(ClicksPowerKeyboardProtocol.NUMBER_REMAP_COMMANDS[keyIndex], selected.bytes)
            }) {
                Text(stringResource(R.string.clicks_apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun ClicksChoiceButtonGrid(
    values: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onChoice: (Int) -> Unit
) {
    values.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { value ->
                TextButton(onClick = { onChoice(value) }) {
                    Text(if (selected == value) "✓ ${label(value)}" else label(value))
                }
            }
        }
    }
}

@Composable
fun DeviceSymLayerEditorStubScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    HardwareProfileScaffold(
        modifier = modifier,
        title = stringResource(R.string.alt_key_editor_title),
        description = stringResource(R.string.alt_key_editor_stub_description),
        onBack = onBack
    ) {
        StubSection(stringResource(R.string.alt_key_editor_create_section))
        PlannedSettingsRow(
            icon = Icons.Filled.Edit,
            title = stringResource(R.string.alt_key_editor_blank_profile_title),
            description = stringResource(R.string.alt_key_editor_blank_profile_description)
        )
        PlannedSettingsRow(
            icon = Icons.Filled.Keyboard,
            title = stringResource(R.string.alt_key_editor_clone_profile_title),
            description = stringResource(R.string.alt_key_editor_clone_profile_description)
        )

        StubSection(stringResource(R.string.alt_key_editor_scope_section))
        PlannedSettingsRow(
            icon = Icons.Filled.Settings,
            title = stringResource(R.string.alt_key_editor_matching_title),
            description = stringResource(R.string.alt_key_editor_matching_description)
        )
        PlannedSettingsRow(
            icon = Icons.Filled.Edit,
            title = stringResource(R.string.alt_key_editor_mappings_title),
            description = stringResource(R.string.alt_key_editor_mappings_description)
        )
        PlannedSettingsRow(
            icon = Icons.Filled.Settings,
            title = stringResource(R.string.alt_key_editor_transfer_title),
            description = stringResource(R.string.alt_key_editor_transfer_description)
        )
    }
}

@Composable
private fun HardwareProfileScaffold(
    modifier: Modifier,
    title: String,
    description: String,
    onBack: () -> Unit,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable () -> Unit
) {

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
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            content()
        }
    }
}

@Composable
private fun StubSection(title: String) {
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
            text = title,
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
private fun PlannedSettingsRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
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
            FeatureStatusIcon(FeatureStatus.Construction)
        }
    }
}
