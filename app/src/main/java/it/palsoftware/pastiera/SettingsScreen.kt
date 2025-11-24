package it.palsoftware.pastiera

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.TouchApp
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import it.palsoftware.pastiera.R
import android.widget.Toast
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.update.checkForUpdate
import it.palsoftware.pastiera.update.showUpdateDialog

/**
 * Sealed class per rappresentare lo stato della navigazione nelle settings.
 */
sealed class SettingsDestination {
    object Main : SettingsDestination()
    object KeyboardTiming : SettingsDestination()
    object TextInput : SettingsDestination()
    object AutoCorrection : SettingsDestination()
    object Customization : SettingsDestination()
    object Advanced : SettingsDestination()
}

/**
 * App settings screen.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    
    var checkingForUpdates by remember { mutableStateOf(false) }
    var navigationDirection by remember { mutableStateOf(NavigationDirection.Push) }
    val navigationStack = remember {
        mutableStateListOf<SettingsDestination>(SettingsDestination.Main)
    }
    val currentDestination by remember {
        derivedStateOf { navigationStack.last() }
    }
    
    fun navigateTo(destination: SettingsDestination) {
        if (currentDestination == destination) return
        navigationDirection = NavigationDirection.Push
        navigationStack.add(destination)
    }
    
    fun navigateBack() {
        if (navigationStack.size > 1) {
            navigationDirection = NavigationDirection.Pop
            navigationStack.removeLast()
        } else {
            activity?.finish()
        }
    }
    
    // Automatic update check on screen open (only once, respecting dismissed releases)
    LaunchedEffect(Unit) {
        checkForUpdate(
            context = context,
            currentVersion = BuildConfig.VERSION_NAME,
            ignoreDismissedReleases = true
        ) { hasUpdate, latestVersion, downloadUrl ->
            if (hasUpdate && latestVersion != null) {
                showUpdateDialog(context, latestVersion, downloadUrl)
            }
        }
    }
    
    // Handle system back button
    BackHandler { navigateBack() }
    
    AnimatedContent(
        targetState = currentDestination,
        transitionSpec = {
            if (navigationDirection == NavigationDirection.Push) {
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
        label = "settings_navigation",
        contentKey = { it::class }
    ) { destination ->
        when (destination) {
            is SettingsDestination.Main -> {
                SettingsMainScreen(
                    modifier = modifier,
                    context = context,
                    checkingForUpdates = checkingForUpdates,
                    onCheckingForUpdatesChange = { checkingForUpdates = it },
                    onKeyboardTimingClick = { navigateTo(SettingsDestination.KeyboardTiming) },
                    onTextInputClick = { navigateTo(SettingsDestination.TextInput) },
                    onAutoCorrectionClick = { navigateTo(SettingsDestination.AutoCorrection) },
                    onCustomizationClick = { navigateTo(SettingsDestination.Customization) },
                    onAdvancedClick = { navigateTo(SettingsDestination.Advanced) },
                    onBackClick = { navigateBack() }
                )
            }
            is SettingsDestination.KeyboardTiming -> {
                KeyboardTimingSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            is SettingsDestination.TextInput -> {
                TextInputSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            is SettingsDestination.AutoCorrection -> {
                AutoCorrectionCategoryScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            is SettingsDestination.Customization -> {
                CustomizationSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            is SettingsDestination.Advanced -> {
                AdvancedSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
        }
    }
}

private enum class NavigationDirection {
    Push,
    Pop
}

@Composable
private fun SettingsMainScreen(
    modifier: Modifier,
    context: Context,
    checkingForUpdates: Boolean,
    onCheckingForUpdatesChange: (Boolean) -> Unit,
    onKeyboardTimingClick: () -> Unit,
    onTextInputClick: () -> Unit,
    onAutoCorrectionClick: () -> Unit,
    onCustomizationClick: () -> Unit,
    onAdvancedClick: () -> Unit,
    onBackClick: () -> Unit
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
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Keyboard & Timing
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable(onClick = onKeyboardTimingClick)
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
                                text = stringResource(R.string.settings_category_keyboard_timing),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
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
            
                // Text Input
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable(onClick = onTextInputClick)
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
                                text = stringResource(R.string.settings_category_text_input),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
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
            
                // Auto-correction
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable(onClick = onAutoCorrectionClick)
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
                                text = stringResource(R.string.settings_category_auto_correction),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
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
            
                // Customization
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable(onClick = onCustomizationClick)
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
                                text = stringResource(R.string.settings_category_customization),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
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
            
                // Advanced
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable(onClick = onAdvancedClick)
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
                                text = stringResource(R.string.settings_category_advanced),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.about_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_update_section_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.settings_update_section_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onCheckingForUpdatesChange(true)
                                checkForUpdate(
                                    context = context,
                                    currentVersion = BuildConfig.VERSION_NAME,
                                    ignoreDismissedReleases = false
                                ) { hasUpdate, latestVersion, downloadUrl ->
                                    onCheckingForUpdatesChange(false)
                                    when {
                                        latestVersion == null -> {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.settings_update_check_failed),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        hasUpdate -> showUpdateDialog(context, latestVersion, downloadUrl)
                                        else -> {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.settings_update_up_to_date),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            },
                            enabled = !checkingForUpdates,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (checkingForUpdates) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.settings_update_checking))
                                }
                            } else {
                                Text(stringResource(R.string.settings_update_button))
                            }
                        }
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
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
