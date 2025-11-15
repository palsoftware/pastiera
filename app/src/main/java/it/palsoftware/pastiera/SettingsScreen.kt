package it.palsoftware.pastiera

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.TouchApp
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import it.palsoftware.pastiera.R

/**
 * App settings screen.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSymCustomizationClick: () -> Unit = {}
) {
    val context = LocalContext.current
    
    // Load saved long press threshold value
    var longPressThreshold by remember { 
        mutableStateOf(SettingsManager.getLongPressThreshold(context))
    }
    
    // Load saved long press modifier value
    var longPressModifier by remember { 
        mutableStateOf(SettingsManager.getLongPressModifier(context))
    }
    
    // Load saved auto-capitalize value
    var autoCapitalizeFirstLetter by remember {
        mutableStateOf(SettingsManager.getAutoCapitalizeFirstLetter(context))
    }

    // Load saved auto-capitalize after period value
    var autoCapitalizeAfterPeriod by remember {
        mutableStateOf(SettingsManager.getAutoCapitalizeAfterPeriod(context))
    }

    // Load saved double space to period value
    var doubleSpaceToPeriod by remember {
        mutableStateOf(SettingsManager.getDoubleSpaceToPeriod(context))
    }
    
    // Load saved swipe to delete value
    var swipeToDelete by remember {
        mutableStateOf(SettingsManager.getSwipeToDelete(context))
    }
    
    // Load saved auto show keyboard value
    var autoShowKeyboard by remember {
        mutableStateOf(SettingsManager.getAutoShowKeyboard(context))
    }
    
    // Load saved auto-correction value
    var autoCorrectEnabled by remember {
        mutableStateOf(SettingsManager.getAutoCorrectEnabled(context))
    }
    
    // Load saved launcher shortcuts enabled value
    var launcherShortcutsEnabled by remember {
        mutableStateOf(SettingsManager.getLauncherShortcutsEnabled(context))
    }
    
    // State for navigation to auto-correction settings
    var showAutoCorrectSettings by remember { mutableStateOf(false) }
    var showAutoCorrectEdit by remember { mutableStateOf<String?>(null) }
    
    // State for navigation to launcher shortcuts settings
    var showLauncherShortcuts by remember { mutableStateOf(false) }
    
    // State for navigation to trackpad debug screen
    var showTrackpadDebug by remember { mutableStateOf(false) }
    
    // State for navigation to nav mode settings
    var showNavModeSettings by remember { mutableStateOf(false) }
    
    // Handle system back button
    BackHandler {
        when {
            showAutoCorrectEdit != null -> {
                showAutoCorrectEdit = null
            }
            showAutoCorrectSettings -> {
                showAutoCorrectSettings = false
            }
            showLauncherShortcuts -> {
                showLauncherShortcuts = false
            }
            showTrackpadDebug -> {
                showTrackpadDebug = false
            }
            showNavModeSettings -> {
                showNavModeSettings = false
            }
            else -> {
                onBack()
            }
        }
    }
    
    // Navigazione condizionale
    showAutoCorrectEdit?.let { languageCode ->
        AutoCorrectEditScreen(
            languageCode = languageCode,
            modifier = modifier,
            onBack = { showAutoCorrectEdit = null }
        )
        return
    }
    
    if (showAutoCorrectSettings) {
        AutoCorrectSettingsScreen(
            modifier = modifier,
            onBack = { showAutoCorrectSettings = false },
            onEditLanguage = { languageCode ->
                showAutoCorrectEdit = languageCode
            }
        )
        return
    }
    
    if (showLauncherShortcuts) {
        LauncherShortcutsScreen(
            modifier = modifier,
            onBack = { showLauncherShortcuts = false }
        )
        return
    }
    
    if (showTrackpadDebug) {
        TrackpadDebugScreen(
            modifier = modifier,
            onBack = { showTrackpadDebug = false }
        )
        return
    }
    
    if (showNavModeSettings) {
        NavModeSettingsScreen(
            modifier = modifier,
            onBack = { showNavModeSettings = false }
        )
        return
    }
    
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
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
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        AnimatedContent(
            targetState = Unit,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "settings_animation"
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // Long Press Threshold
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
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
                                text = stringResource(R.string.long_press_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                text = "${longPressThreshold}ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Slider(
                            value = longPressThreshold.toFloat(),
                            onValueChange = { newValue ->
                                val clampedValue = newValue.toLong().coerceIn(
                                    SettingsManager.getMinLongPressThreshold(),
                                    SettingsManager.getMaxLongPressThreshold()
                                )
                                longPressThreshold = clampedValue
                                SettingsManager.setLongPressThreshold(context, clampedValue)
                            },
                            valueRange = SettingsManager.getMinLongPressThreshold().toFloat()..SettingsManager.getMaxLongPressThreshold().toFloat(),
                            steps = 18,
                            modifier = Modifier
                                .weight(1.5f)
                                .height(24.dp)
                        )
                    }
                }
            
                // Long Press Modifier (Alt/Shift)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
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
                                text = stringResource(R.string.long_press_modifier_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                text = stringResource(R.string.long_press_modifier_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.long_press_modifier_alt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (longPressModifier == "alt") 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Switch(
                                checked = longPressModifier == "shift",
                                onCheckedChange = { useShift ->
                                    val newModifier = if (useShift) "shift" else "alt"
                                    longPressModifier = newModifier
                                    SettingsManager.setLongPressModifier(context, newModifier)
                                }
                            )
                            Text(
                                text = stringResource(R.string.long_press_modifier_shift),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (longPressModifier == "shift") 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            
                // Auto Capitalize
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
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
                                text = stringResource(R.string.auto_capitalize_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                        Switch(
                            checked = autoCapitalizeFirstLetter,
                            onCheckedChange = { enabled ->
                                autoCapitalizeFirstLetter = enabled
                                SettingsManager.setAutoCapitalizeFirstLetter(context, enabled)
                            }
                        )
                    }
                }

                // Auto Capitalize After Period
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
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
                                text = stringResource(R.string.auto_capitalize_after_period_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                text = stringResource(R.string.auto_capitalize_after_period_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Switch(
                            checked = autoCapitalizeAfterPeriod,
                            onCheckedChange = { enabled ->
                                autoCapitalizeAfterPeriod = enabled
                                SettingsManager.setAutoCapitalizeAfterPeriod(context, enabled)
                            }
                        )
                    }
                }

                // Double Space to Period
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
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
                                text = stringResource(R.string.double_space_to_period_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                text = stringResource(R.string.double_space_to_period_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Switch(
                            checked = doubleSpaceToPeriod,
                            onCheckedChange = { enabled ->
                                doubleSpaceToPeriod = enabled
                                SettingsManager.setDoubleSpaceToPeriod(context, enabled)
                            }
                        )
                    }
                }
            
                // Swipe to Delete
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
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
                                text = stringResource(R.string.swipe_to_delete_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
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
            
                // Auto Show Keyboard
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
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
                                text = stringResource(R.string.auto_show_keyboard_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                        Switch(
                            checked = autoShowKeyboard,
                            onCheckedChange = { enabled ->
                                autoShowKeyboard = enabled
                                SettingsManager.setAutoShowKeyboard(context, enabled)
                            }
                        )
                    }
                }
            
                // Auto-Correction
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
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
                                text = stringResource(R.string.auto_correct_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                        Switch(
                            checked = autoCorrectEnabled,
                            onCheckedChange = { enabled ->
                                autoCorrectEnabled = enabled
                                SettingsManager.setAutoCorrectEnabled(context, enabled)
                            }
                        )
                    }
                }
            
                // Auto-Correction Languages (only if auto-correction is enabled)
                if (autoCorrectEnabled) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clickable { showAutoCorrectSettings = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.auto_correct_languages_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            
                // SYM Customization
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { onSymCustomizationClick() }
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
                                text = stringResource(R.string.sym_customization_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            
                // Nav Mode Settings
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { showNavModeSettings = true }
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
                                text = stringResource(R.string.nav_mode_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                text = stringResource(R.string.settings_nav_mode_configure),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            
                // Launcher Shortcuts Enabled Toggle
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.launcher_shortcuts_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = stringResource(R.string.launcher_shortcuts_experimental),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.launcher_shortcuts_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Switch(
                            checked = launcherShortcutsEnabled,
                            onCheckedChange = { enabled ->
                                launcherShortcutsEnabled = enabled
                                SettingsManager.setLauncherShortcutsEnabled(context, enabled)
                            }
                        )
                    }
                }
            
                // Launcher Shortcuts Settings (only if enabled)
                if (launcherShortcutsEnabled) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clickable { showLauncherShortcuts = true }
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
                                    text = stringResource(R.string.launcher_shortcuts_configure),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                Text(
                                    text = stringResource(R.string.launcher_shortcuts_configure_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            
                // Trackpad Debug
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { showTrackpadDebug = true }
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
                                text = stringResource(R.string.settings_trackpad_debug_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                text = stringResource(R.string.settings_trackpad_debug_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            
                // About section
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
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
                        Text(
                            text = stringResource(R.string.about_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
                
                // GitHub Link
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/palsoftware/pastiera/"))
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
                            imageVector = Icons.Filled.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.about_github),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                text = "https://github.com/palsoftware/pastiera/",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Build Info
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
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
                                text = stringResource(R.string.about_build_info),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                text = BuildInfo.getBuildInfoString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Ko-fi Support Link
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/palsoftware"))
                            context.startActivity(intent)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.kofi5),
                        contentDescription = stringResource(R.string.settings_support_ko_fi),
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .aspectRatio(1f)
                    )
                }
            }
        }
    }
}

