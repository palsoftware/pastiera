package it.palsoftware.pastiera

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.backup.BackupManager
import it.palsoftware.pastiera.backup.RestoreManager
import it.palsoftware.pastiera.backup.RestoreInspectionResult
import androidx.compose.material3.Surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import rikka.shizuku.Shizuku
import androidx.compose.runtime.LaunchedEffect
import android.content.pm.PackageManager
import androidx.compose.material.icons.filled.Warning

/**
 * Advanced settings screen.
 */
@Composable
fun AdvancedSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val prefs = remember { SettingsManager.getPreferences(context) }

    // Store the actual value (3 to 25), but display it inverted in the slider (25 to 3)
    var swipeIncrementalThreshold by remember {
        mutableStateOf(SettingsManager.getSwipeIncrementalThreshold(context))
    }
    var clipboardRetentionTime by remember {
        mutableStateOf(SettingsManager.getClipboardRetentionTime(context).toString())
    }
    var shizukuStatus by remember { mutableStateOf(ShizukuStatus.NotConnected) }
    var trackpadProvider by remember { mutableStateOf(SettingsManager.getTrackpadProvider(context)) }
    var experimentalCandidatesViewEnabled by remember {
        mutableStateOf(SettingsManager.getExperimentalCandidatesViewEnabled(context))
    }
    var pendingDeviceChangeRestore by remember {
        mutableStateOf<Pair<Uri, RestoreManager.DeviceChange>?>(null)
    }
    val currentDestination = remember { when (settingsChild(context, "advanced")) {
        "ImeTest" -> AdvancedDestination.ImeTest
        "TrackpadGestures" -> AdvancedDestination.TrackpadGestures
        else -> if (context.settingsActivity().intent.data?.let(SettingLinkRegistry::parseSettingLinkUri) in TRACKPAD_SETTING_LINK_IDS) AdvancedDestination.TrackpadGestures else AdvancedDestination.Main
    } }
    val highlightedSettingId = LocalSettingHighlightId.current
    // Listen to SharedPreferences changes to update UI when values are restored
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "swipe_incremental_threshold" -> {
                    swipeIncrementalThreshold = SettingsManager.getSwipeIncrementalThreshold(context)
                }
                "clipboard_retention_time" -> {
                    clipboardRetentionTime = SettingsManager.getClipboardRetentionTime(context).toString()
                }
                "trackpad_provider" -> {
                    trackpadProvider = SettingsManager.getTrackpadProvider(context)
                }
                "experimental_candidates_view_enabled" -> {
                    experimentalCandidatesViewEnabled = SettingsManager.getExperimentalCandidatesViewEnabled(context)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // Check Shizuku connection and authorization status periodically
    LaunchedEffect(Unit) {
        while (true) {
            shizukuStatus = resolveShizukuStatus()
            delay(2000) // Check every 2 seconds
        }
    }

    fun navigateTo(destination: AdvancedDestination) {
        openSettingsChild(context, "advanced", when (destination) { AdvancedDestination.Main -> "Main"; AdvancedDestination.ImeTest -> "ImeTest"; AdvancedDestination.TrackpadGestures -> "TrackpadGestures" })
    }
    fun navigateBack() { context.settingsActivity().finish() }


    fun defaultBackupName(): String {
        val formatter = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US)
        return "pastiera-backup-${formatter.format(Date())}.zip"
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = BackupManager.createBackup(context, uri)
                val message = when (result) {
                    is it.palsoftware.pastiera.backup.BackupResult.Success ->
                        context.getString(R.string.backup_completed)
                    is it.palsoftware.pastiera.backup.BackupResult.Failure ->
                        context.getString(R.string.backup_failed, result.reason)
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    fun performRestore(uri: Uri, importMode: RestoreManager.ImportMode) {
        scope.launch {
            val result = RestoreManager.restore(context, uri, importMode)
            val message = when (result) {
                is it.palsoftware.pastiera.backup.RestoreResult.Success ->
                    context.getString(R.string.restore_completed)
                is it.palsoftware.pastiera.backup.RestoreResult.Failure ->
                    context.getString(R.string.restore_failed, result.reason)
            }
            snackbarHostState.showSnackbar(message)

            // Wait a bit for SharedPreferences to be written (apply() is asynchronous)
            kotlinx.coroutines.delay(100)

            // Explicitly reload values after restore to ensure UI is updated
            swipeIncrementalThreshold = SettingsManager.getSwipeIncrementalThreshold(context)
            clipboardRetentionTime = SettingsManager.getClipboardRetentionTime(context).toString()
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                when (val inspection = RestoreManager.inspect(context, uri)) {
                    is RestoreInspectionResult.Success -> {
                        val deviceChange = inspection.deviceChange
                        if (deviceChange == null) {
                            performRestore(uri, RestoreManager.ImportMode.UNCHANGED)
                        } else {
                            pendingDeviceChangeRestore = uri to deviceChange
                        }
                    }
                    is RestoreInspectionResult.Failure -> {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.restore_failed, inspection.reason)
                        )
                    }
                }
            }
        }
    }

    pendingDeviceChangeRestore?.let { (uri, deviceChange) ->
        AlertDialog(
            onDismissRequest = { pendingDeviceChangeRestore = null },
            title = { Text(stringResource(R.string.restore_device_change_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.restore_device_change_message,
                        deviceChange.source.displayName,
                        deviceChange.target.displayName
                    )
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        onClick = {
                            pendingDeviceChangeRestore = null
                            performRestore(uri, RestoreManager.ImportMode.ADAPT_TO_CURRENT_DEVICE)
                        }
                    ) {
                        Text(stringResource(R.string.restore_device_change_adapt))
                    }
                    TextButton(
                        onClick = {
                            pendingDeviceChangeRestore = null
                            performRestore(uri, RestoreManager.ImportMode.UNCHANGED)
                        }
                    ) {
                        Text(stringResource(R.string.restore_device_change_unchanged))
                    }
                    TextButton(onClick = { pendingDeviceChangeRestore = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        )
    }

    val destination = currentDestination
        when (destination) {
            AdvancedDestination.Main -> {
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
                                IconButton(onClick = { navigateBack() }) {
                                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.settings_back_content_description)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.settings_category_advanced),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { paddingValues ->
                    Column(
                        modifier = modifier
                            .fillMaxWidth()
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Trackpad Gesture Settings
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .settingRow(SettingLinkIds.ADVANCED_TRACKPAD_GESTURES) {
                                    navigateTo(AdvancedDestination.TrackpadGestures)
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
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
                                            text = stringResource(R.string.trackpad_gestures_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = stringResource(R.string.trackpad_gestures_description),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    FeatureStatusIcon(FeatureStatus.Experimental)
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Trackpad provider status row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 36.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = when {
                                            trackpadProvider == SettingsManager.TRACKPAD_PROVIDER_NATIVE_IME -> Icons.Filled.CheckCircle
                                            shizukuStatus == ShizukuStatus.Connected -> Icons.Filled.CheckCircle
                                            shizukuStatus == ShizukuStatus.NotAuthorized -> Icons.Filled.Warning
                                            else -> Icons.Filled.Error
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            trackpadProvider == SettingsManager.TRACKPAD_PROVIDER_NATIVE_IME -> MaterialTheme.colorScheme.primary
                                            shizukuStatus == ShizukuStatus.Connected -> MaterialTheme.colorScheme.primary
                                            shizukuStatus == ShizukuStatus.NotAuthorized -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.error
                                        },
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = when {
                                            trackpadProvider == SettingsManager.TRACKPAD_PROVIDER_NATIVE_IME -> stringResource(R.string.trackpad_provider_native_ime_status)
                                            shizukuStatus == ShizukuStatus.Connected -> stringResource(R.string.trackpad_gestures_shizuku_connected)
                                            shizukuStatus == ShizukuStatus.NotAuthorized -> stringResource(R.string.trackpad_gestures_shizuku_not_authorized)
                                            else -> stringResource(R.string.trackpad_gestures_shizuku_not_connected)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            trackpadProvider == SettingsManager.TRACKPAD_PROVIDER_NATIVE_IME -> MaterialTheme.colorScheme.primary
                                            shizukuStatus == ShizukuStatus.Connected -> MaterialTheme.colorScheme.primary
                                            shizukuStatus == ShizukuStatus.NotAuthorized -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.error
                                        }
                                    )
                                }
                            }
                        }

                        // Backup
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .settingRow(SettingLinkIds.ADVANCED_BACKUP) {
                                    backupLauncher.launch(defaultBackupName())
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
                                    imageVector = Icons.Filled.Backup,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.backup_now),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.backup_now_description),
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

                        // Restore
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .settingRow(SettingLinkIds.ADVANCED_RESTORE) {
                                    restoreLauncher.launch(arrayOf("application/zip"))
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
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.restore_from_file),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.restore_from_file_description),
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

                        // Swipe Incremental Threshold
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .settingRow(SettingLinkIds.ADVANCED_SWIPE_INCREMENTAL_THRESHOLD)
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
                                        text = stringResource(R.string.swipe_incremental_threshold_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${String.format("%.1f", swipeIncrementalThreshold)} ${stringResource(R.string.dip_unit)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Slider(
                                    value = SettingsManager.getMaxSwipeIncrementalThreshold() +
                                        SettingsManager.getMinSwipeIncrementalThreshold() - swipeIncrementalThreshold,
                                    onValueChange = { newInvertedValue ->
                                        // Invert the slider value (25 to 3) back to stored value (3 to 25)
                                        val actualValue = SettingsManager.getMaxSwipeIncrementalThreshold() +
                                            SettingsManager.getMinSwipeIncrementalThreshold() - newInvertedValue
                                        swipeIncrementalThreshold = actualValue
                                        SettingsManager.setSwipeIncrementalThreshold(context, actualValue)
                                    },
                                    valueRange = SettingsManager.getMinSwipeIncrementalThreshold()..SettingsManager.getMaxSwipeIncrementalThreshold(),
                                    steps = 16,
                                    modifier = Modifier
                                        .weight(1.0f)
                                        .height(24.dp)
                                )
                            }
                        }

                        // Clipboard Retention Time
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .settingRow(SettingLinkIds.ADVANCED_CLIPBOARD_RETENTION_TIME)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.clipboard_retention_time_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.clipboard_retention_time_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                OutlinedTextField(
                                    value = clipboardRetentionTime,
                                    onValueChange = { text ->
                                        val filtered = text.filter { it.isDigit() }.take(5)
                                        clipboardRetentionTime = filtered
                                    },
                                    placeholder = { Text(stringResource(R.string.advanced_minutes_short)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.widthIn(max = 120.dp),
                                    singleLine = true
                                )
                                OutlinedButton(
                                    onClick = {
                                        val minutes = clipboardRetentionTime.toLongOrNull()
                                        if (minutes != null) {
                                            SettingsManager.setClipboardRetentionTime(context, minutes)
                                        }
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = stringResource(R.string.clipboard_retention_apply)
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .settingRow(SettingLinkIds.ADVANCED_EXPERIMENTAL_CANDIDATES_VIEW)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.experimental_candidates_view_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.experimental_candidates_view_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = experimentalCandidatesViewEnabled,
                                    onCheckedChange = { enabled ->
                                        experimentalCandidatesViewEnabled = enabled
                                        SettingsManager.setExperimentalCandidatesViewEnabled(context, enabled)
                                    }
                                )
                            }
                        }

                        if (it.palsoftware.pastiera.inputmethod.DeviceSpecific.isTitan2EliteDevice() ||
                            SettingsManager.getTitan2EliteRoundedCornerInsetsEnabled(context)) {
                            Surface(modifier = Modifier.fillMaxWidth()
                                .settingRow("advanced.corner_calibration") {
                                    context.startActivity(Intent(context, CornerCalibrationActivity::class.java))
                                }) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(stringResource(R.string.corner_calibration_title),
                                        style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.corner_calibration_description),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        // IME Test Screen (only in debug builds)
                        if (BuildConfig.DEBUG) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 64.dp)
                                    .clickable { navigateTo(AdvancedDestination.ImeTest) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.TextFields,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.advanced_ime_test_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = stringResource(R.string.advanced_ime_test_description),
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

                        // Show Tutorial
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .settingRow(SettingLinkIds.ADVANCED_SHOW_TUTORIAL) {
                                    SettingsManager.resetTutorialCompleted(context)
                                    val intent = Intent(context, TutorialActivity::class.java)
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
                                        text = stringResource(R.string.tutorial_show),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.tutorial_review_description),
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

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .settingRow(SettingLinkIds.ADVANCED_SHOW_RELEASE_NOTES_TUTORIAL) {
                                    val intent = Intent(context, TutorialActivity::class.java).apply {
                                        putExtra(TutorialActivity.EXTRA_UPDATE_TUTORIAL, true)
                                        putExtra(TutorialActivity.EXTRA_PREVIEW_UPDATE_TUTORIAL, true)
                                        putExtra(TutorialActivity.EXTRA_PREVIOUS_VERSION, "0.84beta")
                                    }
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
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.tutorial_show_release_notes),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.tutorial_show_release_notes_description),
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

            AdvancedDestination.ImeTest -> {
                ImeTestScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }

            AdvancedDestination.TrackpadGestures -> {
                TrackpadGestureSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }

        }

}

private sealed class AdvancedDestination {
    object Main : AdvancedDestination()
    object ImeTest : AdvancedDestination()
    object TrackpadGestures : AdvancedDestination()
}



private val TRACKPAD_SETTING_LINK_IDS = setOf(
    "trackpad.add_word",
    "trackpad.add_word_full_width",
    "trackpad.swipe_to_delete",
    "trackpad.swipe_to_delete_provider",
    SettingLinkIds.TRACKPAD_GESTURES_ENABLED,
    SettingLinkIds.TRACKPAD_PROVIDER,
    SettingLinkIds.TRACKPAD_SHIZUKU_DEVICE,
    SettingLinkIds.TRACKPAD_SENSITIVITY,
    SettingLinkIds.TRACKPAD_SUGGESTION_SWIPE_THRESHOLD,
    SettingLinkIds.TRACKPAD_DELETE_SWIPE_THRESHOLD,
    SettingLinkIds.TRACKPAD_DEBUG
)
