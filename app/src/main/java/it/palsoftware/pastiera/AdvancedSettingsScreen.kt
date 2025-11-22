package it.palsoftware.pastiera

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import it.palsoftware.pastiera.R

/**
 * Advanced settings screen.
 */
@Composable
fun AdvancedSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    var launcherShortcutsEnabled by remember { 
        mutableStateOf(SettingsManager.getLauncherShortcutsEnabled(context))
    }
    var powerShortcutsEnabled by remember { 
        mutableStateOf(SettingsManager.getPowerShortcutsEnabled(context))
    }
    // Store the actual value (3 to 25), but display it inverted in the slider (25 to 3)
    var swipeIncrementalThreshold by remember { 
        mutableStateOf(SettingsManager.getSwipeIncrementalThreshold(context))
    }
    var navigationDirection by remember { mutableStateOf(AdvancedNavigationDirection.Push) }
    val navigationStack = remember {
        mutableStateListOf<AdvancedDestination>(AdvancedDestination.Main)
    }
    val currentDestination by remember {
        derivedStateOf { navigationStack.last() }
    }
    
    fun navigateTo(destination: AdvancedDestination) {
        navigationDirection = AdvancedNavigationDirection.Push
        navigationStack.add(destination)
    }
    
    fun navigateBack() {
        if (navigationStack.size > 1) {
            navigationDirection = AdvancedNavigationDirection.Pop
            navigationStack.removeAt(navigationStack.lastIndex)
        } else {
            onBack()
        }
    }
    
    BackHandler { navigateBack() }
    
    AnimatedContent(
        targetState = currentDestination,
        transitionSpec = {
            if (navigationDirection == AdvancedNavigationDirection.Push) {
                // Forward navigation: new screen enters from right, old screen exits to left
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(250)
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = tween(250)
                )
            } else {
                // Back navigation: current screen exits to right, previous screen enters from left
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = tween(250)
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(250)
                )
            }
        },
        label = "advanced_navigation",
        contentKey = { it::class }
    ) { destination ->
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
                                        imageVector = Icons.Filled.ArrowBack,
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
                    }
                ) { paddingValues ->
                    Column(
                        modifier = modifier
                            .fillMaxWidth()
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState())
                    ) {
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
                    
                        // Power Shortcuts Toggle
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
                                    imageVector = Icons.Filled.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.power_shortcuts_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.power_shortcuts_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                                Switch(
                                    checked = powerShortcutsEnabled,
                                    onCheckedChange = { enabled ->
                                        powerShortcutsEnabled = enabled
                                        SettingsManager.setPowerShortcutsEnabled(context, enabled)
                                    }
                                )
                            }
                        }
                    
                        // Launcher Shortcuts Settings (always visible)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { navigateTo(AdvancedDestination.LauncherShortcuts) }
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
                    
                        // Trackpad Debug
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { navigateTo(AdvancedDestination.TrackpadDebug) }
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
                                        text = stringResource(R.string.trackpad_debug_title),
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
                    
                        // Swipe Incremental Threshold
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
                                        text = "${String.format("%.1f", swipeIncrementalThreshold)} DIP",
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
                                        .weight(1.5f)
                                        .height(24.dp)
                                )
                            }
                        }
                    
                        // Show Tutorial
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable {
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
                                        text = "Mostra Tutorial",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Rivedi il tutorial introduttivo",
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
                }
            }
            
            AdvancedDestination.LauncherShortcuts -> {
                LauncherShortcutsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            
            AdvancedDestination.TrackpadDebug -> {
                TrackpadDebugScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
        }
    }
}

private sealed class AdvancedDestination {
    object Main : AdvancedDestination()
    object LauncherShortcuts : AdvancedDestination()
    object TrackpadDebug : AdvancedDestination()
}

private enum class AdvancedNavigationDirection {
    Push,
    Pop
}
