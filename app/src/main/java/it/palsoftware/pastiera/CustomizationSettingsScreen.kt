package it.palsoftware.pastiera

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardCommandKey
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.commands.CommandRegistry
import it.palsoftware.pastiera.commands.CommandIcon
import it.palsoftware.pastiera.commands.CommandSourceId
import it.palsoftware.pastiera.commands.CommandSurface
import it.palsoftware.pastiera.commands.CommandTarget

/**
 * Customization settings screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    initialDestination: String? = null,
    initialKeyboardThemeTarget: String? = null
) {
    val context = LocalContext.current
    val prefs = remember { SettingsManager.getPreferences(context) }
    var pastierinaModeEnabled by remember {
        mutableStateOf(
            SettingsManager.getPastierinaModeOverride(context) ==
                SettingsManager.PastierinaModeOverride.FORCE_MINIMAL
        )
    }
    var unifiedModeEnabled by remember {
        mutableStateOf(SettingsManager.getUnifiedSuggestionsVariationsBar(context))
    }
    var launcherShortcutsEnabled by remember {
        mutableStateOf(SettingsManager.getLauncherShortcutsEnabled(context))
    }
    var powerShortcutsEnabled by remember {
        mutableStateOf(SettingsManager.getPowerShortcutsEnabled(context))
    }
    var quickLauncherAutoStartSingle by remember {
        mutableStateOf(SettingsManager.getQuickLauncherAutoStartSingle(context))
    }
    var quickLauncherLimitResults by remember {
        mutableStateOf(SettingsManager.getQuickLauncherLimitResults(context))
    }
    var quickLauncherTextFieldShortcuts by remember {
        mutableStateOf(SettingsManager.getQuickLauncherTextFieldShortcuts(context))
    }
    var quickLauncherRespectKeyboardLayout by remember {
        mutableStateOf(SettingsManager.getQuickLauncherRespectKeyboardLayout(context))
    }
    var quickLauncherTypoTolerantRanking by remember {
        mutableStateOf(SettingsManager.getQuickLauncherTypoTolerantRanking(context))
    }
    var quickLauncherWidthPercent by remember {
        mutableStateOf(SettingsManager.getQuickLauncherWidthPercent(context))
    }
    var quickLauncherPillMode by remember {
        mutableStateOf(SettingsManager.getQuickLauncherPillMode(context))
    }
    var quickLauncherHighlightFavorites by remember {
        mutableStateOf(SettingsManager.getQuickLauncherHighlightFavorites(context))
    }
    var quickLauncherFavoriteColor by remember {
        mutableStateOf(SettingsManager.getQuickLauncherFavoriteColor(context))
    }
    var quickLauncherIconColors by remember {
        mutableStateOf(SettingsManager.getQuickLauncherIconColors(context))
    }
    var quickLauncherShowAliasFirst by remember {
        mutableStateOf(SettingsManager.getQuickLauncherShowAliasFirst(context))
    }
    var quickLauncherStaticTopHighlight by remember {
        mutableStateOf(SettingsManager.getQuickLauncherStaticTopHighlight(context))
    }
    var quickLauncherStaticTopHighlightColor by remember {
        mutableStateOf(SettingsManager.getQuickLauncherStaticTopHighlightColor(context))
    }
    var quickLauncherBehavior by remember {
        mutableStateOf(SettingsManager.getQuickLauncherBehavior(context))
    }
    var quickLauncherAnimationDurationMs by remember {
        mutableStateOf(SettingsManager.getQuickLauncherAnimationDurationMs(context))
    }
    var commandSourceVisibility by remember {
        mutableStateOf(SettingsManager.getCommandSourceVisibility(context))
    }
    var quickLauncherDefaultBlocked by remember {
        mutableStateOf(SettingsManager.isQuickLauncherDefaultBlockedByExistingSpaceShortcut(context))
    }
    var quickLauncherShortcutKey by remember {
        mutableStateOf(
            if (SettingsManager.isQuickLauncherDefaultBlockedByExistingSpaceShortcut(context)) {
                null
            } else {
                SettingsManager.getQuickLauncherShortcutKey(context)
            }
        )
    }
    var navigationDirection by remember { mutableStateOf(CustomizationNavigationDirection.Push) }
    val navigationStack = remember {
        mutableStateListOf<CustomizationDestination>().apply {
            val deepLinkedDestination = when (initialDestination) {
                SettingsActivity.CUSTOMIZATION_DESTINATION_VARIATIONS ->
                    CustomizationDestination.Variations
                SettingsActivity.CUSTOMIZATION_DESTINATION_LAUNCHER_SHORTCUTS ->
                    CustomizationDestination.LauncherShortcuts
                SettingsActivity.CUSTOMIZATION_DESTINATION_APP_ENTER_BEHAVIOR ->
                    CustomizationDestination.AppEnterBehavior
                SettingsActivity.CUSTOMIZATION_DESTINATION_STATUS_BAR_BUTTONS ->
                    CustomizationDestination.StatusBarButtons
                SettingsActivity.CUSTOMIZATION_DESTINATION_KEYBOARD_THEME ->
                    CustomizationDestination.KeyboardTheme
                else -> null
            }
            add(deepLinkedDestination ?: CustomizationDestination.Main)
        }
    }
    val currentDestination by remember {
        derivedStateOf { navigationStack.last() }
    }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "pastierina_mode_override" -> {
                    pastierinaModeEnabled =
                        SettingsManager.getPastierinaModeOverride(context) ==
                            SettingsManager.PastierinaModeOverride.FORCE_MINIMAL
                }
                "launcher_shortcuts_enabled" -> {
                    launcherShortcutsEnabled = SettingsManager.getLauncherShortcutsEnabled(context)
                }
                "power_shortcuts_enabled" -> {
                    powerShortcutsEnabled = SettingsManager.getPowerShortcutsEnabled(context)
                }
                "quick_launcher_auto_start_single" -> {
                    quickLauncherAutoStartSingle = SettingsManager.getQuickLauncherAutoStartSingle(context)
                }
                "quick_launcher_limit_results" -> {
                    quickLauncherLimitResults = SettingsManager.getQuickLauncherLimitResults(context)
                }
                "quick_launcher_text_field_shortcuts" -> {
                    quickLauncherTextFieldShortcuts = SettingsManager.getQuickLauncherTextFieldShortcuts(context)
                }
                "quick_launcher_respect_keyboard_layout" -> {
                    quickLauncherRespectKeyboardLayout = SettingsManager.getQuickLauncherRespectKeyboardLayout(context)
                }
                "quick_launcher_typo_tolerant_ranking" -> {
                    quickLauncherTypoTolerantRanking = SettingsManager.getQuickLauncherTypoTolerantRanking(context)
                }
                "quick_launcher_width_percent" -> {
                    quickLauncherWidthPercent = SettingsManager.getQuickLauncherWidthPercent(context)
                }
                "quick_launcher_pill_mode" -> {
                    quickLauncherPillMode = SettingsManager.getQuickLauncherPillMode(context)
                }
                "quick_launcher_highlight_favorites" -> {
                    quickLauncherHighlightFavorites = SettingsManager.getQuickLauncherHighlightFavorites(context)
                }
                "quick_launcher_favorite_color" -> {
                    quickLauncherFavoriteColor = SettingsManager.getQuickLauncherFavoriteColor(context)
                }
                "quick_launcher_icon_colors" -> {
                    quickLauncherIconColors = SettingsManager.getQuickLauncherIconColors(context)
                }
                "quick_launcher_show_alias_first" -> {
                    quickLauncherShowAliasFirst = SettingsManager.getQuickLauncherShowAliasFirst(context)
                }
                "quick_launcher_static_top_highlight" -> {
                    quickLauncherStaticTopHighlight = SettingsManager.getQuickLauncherStaticTopHighlight(context)
                }
                "quick_launcher_static_top_highlight_color" -> {
                    quickLauncherStaticTopHighlightColor = SettingsManager.getQuickLauncherStaticTopHighlightColor(context)
                }
                "quick_launcher_behavior" -> {
                    quickLauncherBehavior = SettingsManager.getQuickLauncherBehavior(context)
                }
                "quick_launcher_animation_duration_ms" -> {
                    quickLauncherAnimationDurationMs = SettingsManager.getQuickLauncherAnimationDurationMs(context)
                }
                "command_surface_sources" -> {
                    commandSourceVisibility = SettingsManager.getCommandSourceVisibility(context)
                }
                "quick_launcher_command_customizations" -> {
                    // Dialog content reads this lazily from SettingsManager when opened.
                }
                "launcher_shortcuts" -> {
                    quickLauncherDefaultBlocked = SettingsManager.isQuickLauncherDefaultBlockedByExistingSpaceShortcut(context)
                    quickLauncherShortcutKey = if (quickLauncherDefaultBlocked) {
                        null
                    } else {
                        SettingsManager.getQuickLauncherShortcutKey(context)
                    }
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    
    fun navigateTo(destination: CustomizationDestination) {
        navigationDirection = CustomizationNavigationDirection.Push
        navigationStack.add(destination)
    }
    
    fun navigateBack() {
        if (navigationStack.size > 1) {
            navigationDirection = CustomizationNavigationDirection.Pop
            navigationStack.removeAt(navigationStack.lastIndex)
        } else {
            onBack()
        }
    }
    
    BackHandler { navigateBack() }
    
    AnimatedContent(
        targetState = currentDestination,
        transitionSpec = {
            if (navigationDirection == CustomizationNavigationDirection.Push) {
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
        label = "customization_navigation",
        contentKey = { it::class }
    ) { destination ->
        when (destination) {
            CustomizationDestination.Main -> {
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
                                    text = stringResource(R.string.settings_category_customization),
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
                        // SYM Customization
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable {
                                    val intent = Intent(context, SymCustomizationActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    }
                                    context.startActivity(intent)
                                    (context as? Activity)?.let { activity ->
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                            activity.overrideActivityTransition(
                                                Activity.OVERRIDE_TRANSITION_OPEN,
                                                R.anim.slide_in_from_right,
                                                0
                                            )
                                        } else {
                                            @Suppress("DEPRECATION")
                                            activity.overridePendingTransition(
                                                R.anim.slide_in_from_right,
                                                0
                                            )
                                        }
                                    }
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
                                    painter = painterResource(R.drawable.ic_emoji_symbols_24),
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
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    
                        // Variations Customization
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { navigateTo(CustomizationDestination.Variations) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.variation_customize_title),
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
                    
                        // App-specific Enter behavior
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { navigateTo(CustomizationDestination.AppEnterBehavior) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.app_enter_behaviour_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.app_enter_behaviour_description),
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

                        // Starter / Launcher shortcuts
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clickable { navigateTo(CustomizationDestination.LauncherShortcuts) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ManageSearch,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.starter_launcher_shortcuts_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.starter_launcher_shortcuts_description),
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

                        // Nav Mode Settings
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { navigateTo(CustomizationDestination.NavMode) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardCommandKey,
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
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    
                        // Status Bar Buttons Settings
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { navigateTo(CustomizationDestination.StatusBarButtons) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SmartButton,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.status_bar_buttons_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.status_bar_buttons_description),
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
                                .height(72.dp)
                                .clickable { navigateTo(CustomizationDestination.KeyboardTheme) }
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
                                        text = stringResource(R.string.keyboard_theme_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.keyboard_theme_description),
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

                        // Sound Settings
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { navigateTo(CustomizationDestination.Sounds) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_category_sounds),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_sounds_description),
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

                        // Pastierina Mode toggle
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SpaceBar,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.pastierina_mode_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.pastierina_mode_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Switch(
                                    checked = pastierinaModeEnabled,
                                    onCheckedChange = { checked ->
                                        pastierinaModeEnabled = checked
                                        val override = if (checked) {
                                            SettingsManager.PastierinaModeOverride.FORCE_MINIMAL
                                        } else {
                                            SettingsManager.PastierinaModeOverride.FORCE_FULL
                                        }
                                        SettingsManager.setPastierinaModeOverride(context, override)
                                    }
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.unified_mode_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.unified_mode_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Switch(
                                    checked = unifiedModeEnabled,
                                    onCheckedChange = { checked ->
                                        unifiedModeEnabled = checked
                                        SettingsManager.setUnifiedSuggestionsVariationsBar(context, checked)
                                    }
                                )
                            }
                        }

                    }
                }
            }
            
            CustomizationDestination.Variations -> {
                VariationCustomizationScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }

            CustomizationDestination.AppEnterBehavior -> {
                AppEnterBehaviorScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onOpenLauncherShortcutAssignments = {
                        navigateTo(CustomizationDestination.LauncherShortcutAssignments)
                    }
                )
            }
            
            CustomizationDestination.NavMode -> {
                NavModeSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }

            CustomizationDestination.LauncherShortcuts -> {
                StarterLauncherShortcutsSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    launcherShortcutsEnabled = launcherShortcutsEnabled,
                    onLauncherShortcutsEnabledChanged = { enabled ->
                        launcherShortcutsEnabled = enabled
                        SettingsManager.setLauncherShortcutsEnabled(context, enabled)
                    },
                    powerShortcutsEnabled = powerShortcutsEnabled,
                    onPowerShortcutsEnabledChanged = { enabled ->
                        powerShortcutsEnabled = enabled
                        SettingsManager.setPowerShortcutsEnabled(context, enabled)
                    },
                    quickLauncherAutoStartSingle = quickLauncherAutoStartSingle,
                    onQuickLauncherAutoStartSingleChanged = { enabled ->
                        quickLauncherAutoStartSingle = enabled
                        SettingsManager.setQuickLauncherAutoStartSingle(context, enabled)
                    },
                    quickLauncherLimitResults = quickLauncherLimitResults,
                    onQuickLauncherLimitResultsChanged = { enabled ->
                        quickLauncherLimitResults = enabled
                        SettingsManager.setQuickLauncherLimitResults(context, enabled)
                    },
                    quickLauncherDefaultBlocked = quickLauncherDefaultBlocked,
                    quickLauncherShortcutKey = quickLauncherShortcutKey,
                    onOpenBehavior = { navigateTo(CustomizationDestination.LauncherShortcutBehavior) },
                    onOpenCosmetic = { navigateTo(CustomizationDestination.LauncherShortcutCosmetic) },
                    onManageAssignments = { navigateTo(CustomizationDestination.LauncherShortcutAssignments) }
                )
            }

            CustomizationDestination.LauncherShortcutBehavior -> {
                StarterLauncherBehaviorScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    quickLauncherAutoStartSingle = quickLauncherAutoStartSingle,
                    onQuickLauncherAutoStartSingleChanged = { enabled ->
                        quickLauncherAutoStartSingle = enabled
                        SettingsManager.setQuickLauncherAutoStartSingle(context, enabled)
                    },
                    quickLauncherLimitResults = quickLauncherLimitResults,
                    onQuickLauncherLimitResultsChanged = { enabled ->
                        quickLauncherLimitResults = enabled
                        SettingsManager.setQuickLauncherLimitResults(context, enabled)
                    },
                    quickLauncherTextFieldShortcuts = quickLauncherTextFieldShortcuts,
                    onQuickLauncherTextFieldShortcutsChanged = { enabled ->
                        quickLauncherTextFieldShortcuts = enabled
                        SettingsManager.setQuickLauncherTextFieldShortcuts(context, enabled)
                    },
                    quickLauncherRespectKeyboardLayout = quickLauncherRespectKeyboardLayout,
                    onQuickLauncherRespectKeyboardLayoutChanged = { enabled ->
                        quickLauncherRespectKeyboardLayout = enabled
                        SettingsManager.setQuickLauncherRespectKeyboardLayout(context, enabled)
                    },
                    quickLauncherTypoTolerantRanking = quickLauncherTypoTolerantRanking,
                    onQuickLauncherTypoTolerantRankingChanged = { enabled ->
                        quickLauncherTypoTolerantRanking = enabled
                        SettingsManager.setQuickLauncherTypoTolerantRanking(context, enabled)
                    },
                    quickLauncherBehavior = quickLauncherBehavior,
                    onQuickLauncherBehaviorChanged = { behavior ->
                        quickLauncherBehavior = behavior
                        SettingsManager.setQuickLauncherBehavior(context, behavior)
                    },
                    quickLauncherAnimationDurationMs = quickLauncherAnimationDurationMs,
                    onQuickLauncherAnimationDurationChanged = { durationMs ->
                        quickLauncherAnimationDurationMs = durationMs
                        SettingsManager.setQuickLauncherAnimationDurationMs(context, durationMs)
                    }
                )
            }

            CustomizationDestination.LauncherShortcutCosmetic -> {
                StarterLauncherCosmeticScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    quickLauncherWidthPercent = quickLauncherWidthPercent,
                    onQuickLauncherWidthPercentChanged = { percent ->
                        quickLauncherWidthPercent = percent
                        SettingsManager.setQuickLauncherWidthPercent(context, percent)
                    },
                    quickLauncherPillMode = quickLauncherPillMode,
                    onQuickLauncherPillModeChanged = { enabled ->
                        quickLauncherPillMode = enabled
                        SettingsManager.setQuickLauncherPillMode(context, enabled)
                    },
                    quickLauncherHighlightFavorites = quickLauncherHighlightFavorites,
                    onQuickLauncherHighlightFavoritesChanged = { enabled ->
                        quickLauncherHighlightFavorites = enabled
                        SettingsManager.setQuickLauncherHighlightFavorites(context, enabled)
                    },
                    quickLauncherFavoriteColor = quickLauncherFavoriteColor,
                    onQuickLauncherFavoriteColorChanged = { color ->
                        quickLauncherFavoriteColor = color
                        SettingsManager.setQuickLauncherFavoriteColor(context, color)
                    },
                    quickLauncherIconColors = quickLauncherIconColors,
                    onQuickLauncherIconColorsChanged = { enabled ->
                        quickLauncherIconColors = enabled
                        SettingsManager.setQuickLauncherIconColors(context, enabled)
                    },
                    quickLauncherShowAliasFirst = quickLauncherShowAliasFirst,
                    onQuickLauncherShowAliasFirstChanged = { enabled ->
                        quickLauncherShowAliasFirst = enabled
                        SettingsManager.setQuickLauncherShowAliasFirst(context, enabled)
                    },
                    quickLauncherStaticTopHighlight = quickLauncherStaticTopHighlight,
                    onQuickLauncherStaticTopHighlightChanged = { enabled ->
                        quickLauncherStaticTopHighlight = enabled
                        SettingsManager.setQuickLauncherStaticTopHighlight(context, enabled)
                    },
                    quickLauncherStaticTopHighlightColor = quickLauncherStaticTopHighlightColor,
                    onQuickLauncherStaticTopHighlightColorChanged = { color ->
                        quickLauncherStaticTopHighlightColor = color
                        SettingsManager.setQuickLauncherStaticTopHighlightColor(context, color)
                    },
                    commandSourceVisibility = commandSourceVisibility,
                    onCommandSourceVisibilityChanged = { visibility ->
                        commandSourceVisibility = visibility
                        SettingsManager.setCommandSourceVisibility(context, visibility)
                    }
                )
            }

            CustomizationDestination.LauncherShortcutAssignments -> {
                LauncherShortcutsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
            
            CustomizationDestination.StatusBarButtons -> {
                StatusBarButtonsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onCustomizeVariations = { navigateTo(CustomizationDestination.Variations) }
                )
            }

            CustomizationDestination.KeyboardTheme -> {
                KeyboardThemeScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    initialTarget = when (initialKeyboardThemeTarget) {
                        SettingsActivity.KEYBOARD_THEME_TARGET_SOFTWARE ->
                            SettingsManager.KeyboardThemeTarget.SOFTWARE
                        else -> null
                    }
                )
            }

            CustomizationDestination.Sounds -> {
                SoundSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
        }
    }
}

@Composable
private fun StarterLauncherShortcutsSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    launcherShortcutsEnabled: Boolean,
    onLauncherShortcutsEnabledChanged: (Boolean) -> Unit,
    powerShortcutsEnabled: Boolean,
    onPowerShortcutsEnabledChanged: (Boolean) -> Unit,
    quickLauncherAutoStartSingle: Boolean,
    onQuickLauncherAutoStartSingleChanged: (Boolean) -> Unit,
    quickLauncherLimitResults: Boolean,
    onQuickLauncherLimitResultsChanged: (Boolean) -> Unit,
    quickLauncherDefaultBlocked: Boolean,
    quickLauncherShortcutKey: Int?,
    onOpenBehavior: () -> Unit,
    onOpenCosmetic: () -> Unit,
    onManageAssignments: () -> Unit
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
                        text = stringResource(R.string.starter_launcher_shortcuts_title),
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.starter_launcher_shortcuts_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (quickLauncherDefaultBlocked) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = stringResource(R.string.quick_launcher_default_blocked_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            LauncherShortcutTriggerRow(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.LineWeight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = stringResource(R.string.launcher_shortcuts_title),
                description = stringResource(R.string.launcher_shortcuts_description),
                checked = launcherShortcutsEnabled,
                onCheckedChange = onLauncherShortcutsEnabledChanged
            )

            LauncherShortcutTriggerRow(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = stringResource(R.string.power_shortcuts_title),
                description = stringResource(R.string.power_shortcuts_description),
                checked = powerShortcutsEnabled,
                onCheckedChange = onPowerShortcutsEnabledChanged
            )

            StarterLauncherNavigationRow(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = stringResource(R.string.quick_launcher_behaviour_title),
                description = stringResource(R.string.quick_launcher_behaviour_description),
                onClick = onOpenBehavior
            )

            StarterLauncherNavigationRow(
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ManageSearch,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = stringResource(R.string.quick_launcher_cosmetic_title),
                description = stringResource(R.string.quick_launcher_cosmetic_description),
                onClick = onOpenCosmetic
            )

            StarterLauncherNavigationRow(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.SmartButton,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                title = stringResource(R.string.launcher_shortcuts_configure),
                description = if (quickLauncherShortcutKey != null) {
                    stringResource(
                        R.string.launcher_shortcuts_configure_description_with_quick_launcher,
                        keyLabel(quickLauncherShortcutKey)
                    )
                } else {
                    stringResource(R.string.launcher_shortcuts_configure_description)
                },
                onClick = onManageAssignments
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StarterLauncherBehaviorScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    quickLauncherAutoStartSingle: Boolean,
    onQuickLauncherAutoStartSingleChanged: (Boolean) -> Unit,
    quickLauncherLimitResults: Boolean,
    onQuickLauncherLimitResultsChanged: (Boolean) -> Unit,
    quickLauncherTextFieldShortcuts: Boolean,
    onQuickLauncherTextFieldShortcutsChanged: (Boolean) -> Unit,
    quickLauncherRespectKeyboardLayout: Boolean,
    onQuickLauncherRespectKeyboardLayoutChanged: (Boolean) -> Unit,
    quickLauncherTypoTolerantRanking: Boolean,
    onQuickLauncherTypoTolerantRankingChanged: (Boolean) -> Unit,
    quickLauncherBehavior: String,
    onQuickLauncherBehaviorChanged: (String) -> Unit,
    quickLauncherAnimationDurationMs: Int,
    onQuickLauncherAnimationDurationChanged: (Int) -> Unit
) {
    var showRankingInfo by remember { mutableStateOf(false) }
    var behaviorMenuExpanded by remember { mutableStateOf(false) }
    val behaviorOptions = listOf(
        SettingsManager.QUICK_LAUNCHER_BEHAVIOR_PASTIERA,
        SettingsManager.QUICK_LAUNCHER_BEHAVIOR_NIAGARA
    )

    StarterLauncherSubScreen(
        modifier = modifier,
        title = stringResource(R.string.quick_launcher_behaviour_title),
        onBack = onBack
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.quick_launcher_provider_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.quick_launcher_provider_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ExposedDropdownMenuBox(
                    expanded = behaviorMenuExpanded,
                    onExpandedChange = { behaviorMenuExpanded = !behaviorMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = quickLauncherBehaviorLabel(quickLauncherBehavior),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = behaviorMenuExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = behaviorMenuExpanded,
                        onDismissRequest = { behaviorMenuExpanded = false }
                    ) {
                        behaviorOptions.forEach { behavior ->
                            DropdownMenuItem(
                                text = { Text(quickLauncherBehaviorLabel(behavior)) },
                                onClick = {
                                    behaviorMenuExpanded = false
                                    onQuickLauncherBehaviorChanged(behavior)
                                }
                            )
                        }
                    }
                }
            }
        }
        LauncherShortcutTriggerRow(
            icon = { SettingsRowKeyboardIcon() },
            title = stringResource(R.string.quick_launcher_auto_start_single_title),
            description = stringResource(R.string.quick_launcher_auto_start_single_description),
            checked = quickLauncherAutoStartSingle,
            onCheckedChange = onQuickLauncherAutoStartSingleChanged
        )
        LauncherShortcutTriggerRow(
            icon = { SettingsRowKeyboardIcon() },
            title = stringResource(R.string.quick_launcher_limit_results_title),
            description = stringResource(R.string.quick_launcher_limit_results_description),
            checked = quickLauncherLimitResults,
            onCheckedChange = onQuickLauncherLimitResultsChanged
        )
        LauncherShortcutTriggerRow(
            icon = { SettingsRowKeyboardIcon() },
            title = stringResource(R.string.quick_launcher_text_field_shortcuts_title),
            description = stringResource(R.string.quick_launcher_text_field_shortcuts_description),
            checked = quickLauncherTextFieldShortcuts,
            onCheckedChange = onQuickLauncherTextFieldShortcutsChanged
        )
        LauncherShortcutTriggerRow(
            icon = { SettingsRowKeyboardIcon() },
            title = stringResource(R.string.quick_launcher_respect_keyboard_layout_title),
            description = stringResource(R.string.quick_launcher_respect_keyboard_layout_description),
            checked = quickLauncherRespectKeyboardLayout,
            onCheckedChange = onQuickLauncherRespectKeyboardLayoutChanged
        )
        LauncherShortcutTriggerRow(
            icon = { SettingsRowKeyboardIcon() },
            title = stringResource(R.string.quick_launcher_typo_tolerant_ranking_title),
            description = stringResource(R.string.quick_launcher_typo_tolerant_ranking_description),
            checked = quickLauncherTypoTolerantRanking,
            onCheckedChange = onQuickLauncherTypoTolerantRankingChanged
        )
        TextButton(
            onClick = { showRankingInfo = true },
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.quick_launcher_ranking_info_button))
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.quick_launcher_animation_duration_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(
                        R.string.quick_launcher_animation_duration_value,
                        quickLauncherAnimationDurationMs
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = quickLauncherAnimationDurationMs.toFloat(),
                    onValueChange = { value ->
                        val rounded = (value / 20f).toInt() * 20
                        onQuickLauncherAnimationDurationChanged(rounded)
                    },
                    valueRange = SettingsManager.QUICK_LAUNCHER_ANIMATION_DURATION_MIN_MS.toFloat()..
                        SettingsManager.QUICK_LAUNCHER_ANIMATION_DURATION_MAX_MS.toFloat(),
                    steps = 15
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = stringResource(R.string.quick_launcher_text_field_shortcuts_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    if (showRankingInfo) {
        AlertDialog(
            onDismissRequest = { showRankingInfo = false },
            title = { Text(stringResource(R.string.quick_launcher_ranking_info_title)) },
            text = { Text(stringResource(R.string.quick_launcher_ranking_info_body)) },
            confirmButton = {
                TextButton(onClick = { showRankingInfo = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun quickLauncherBehaviorLabel(behavior: String): String {
    return when (behavior) {
        SettingsManager.QUICK_LAUNCHER_BEHAVIOR_NIAGARA ->
            stringResource(R.string.quick_launcher_provider_niagara)
        else -> stringResource(R.string.quick_launcher_provider_pastiera)
    }
}

@Composable
private fun QuickLauncherDisplayedEntriesSection(
    visibility: List<SettingsManager.CommandSourceVisibility>,
    onVisibilityChanged: (List<SettingsManager.CommandSourceVisibility>) -> Unit
) {
    val context = LocalContext.current
    var showCustomizeDialog by remember { mutableStateOf(false) }
    val sourceLabels = mapOf(
        CommandSourceId.Apps.storageValue to "Apps",
        CommandSourceId.Pastiera.storageValue to "Pastiera actions",
        CommandSourceId.AppActions.storageValue to "App actions",
        CommandSourceId.DeviceControl.storageValue to "Device control",
        CommandSourceId.NavActions.storageValue to "Navigation actions"
    )

    fun update(sourceId: String, quickLauncher: Boolean) {
        onVisibilityChanged(
            visibility.map { item ->
                if (item.sourceId != sourceId) {
                    item
                } else {
                    item.copy(quickLauncherEnabled = quickLauncher)
                }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ManageSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "QuickLauncher entries",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Choose which sources appear in Pastiera search.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            visibility.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = sourceLabels[item.sourceId] ?: item.sourceId,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = item.quickLauncherEnabled,
                        onCheckedChange = { update(item.sourceId, it) }
                    )
                }
            }
            Button(
                onClick = { showCustomizeDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Customize entries")
            }
        }
    }

    if (showCustomizeDialog) {
        QuickLauncherCommandCustomizationDialog(
            commands = remember(showCustomizeDialog, visibility) {
                CommandRegistry(context).getCommands(CommandSurface.QuickLauncher)
            },
            customizations = remember(showCustomizeDialog) {
                mutableStateMapOf<String, SettingsManager.QuickLauncherCommandCustomization>().apply {
                    putAll(SettingsManager.getQuickLauncherCommandCustomizations(context))
                }
            },
            onCustomizationChanged = { customization ->
                SettingsManager.setQuickLauncherCommandCustomization(context, customization)
            },
            onDismiss = { showCustomizeDialog = false }
        )
    }
}

@Composable
private fun QuickLauncherCommandCustomizationDialog(
    commands: List<CommandTarget>,
    customizations: MutableMap<String, SettingsManager.QuickLauncherCommandCustomization>,
    onCustomizationChanged: (SettingsManager.QuickLauncherCommandCustomization) -> Unit,
    onDismiss: () -> Unit
) {
    var editingCommandId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf<CommandSourceId?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }
    val availableSources = remember(commands) { commands.map { it.source }.distinct() }
    val filteredCommands = remember(commands, query, selectedSource, favoritesOnly, customizations.toMap()) {
        val normalizedQuery = query.trim().lowercase()
        val sourceMatches = commands.filter {
            (selectedSource == null || it.source == selectedSource) &&
                (!favoritesOnly || customizations[it.id]?.favorite == true)
        }
        val matches = if (normalizedQuery.isBlank()) {
            sourceMatches
        } else {
            sourceMatches.filter { command ->
                command.label.contains(normalizedQuery, ignoreCase = true) ||
                    command.subtitle?.contains(normalizedQuery, ignoreCase = true) == true ||
                    command.source.displayLabel.contains(normalizedQuery, ignoreCase = true) ||
                    command.searchTokens.any { it.contains(normalizedQuery, ignoreCase = true) }
            }
        }
        if (favoritesOnly) {
            matches.sortedWith(
                compareBy<CommandTarget> { customizations[it.id]?.favoriteOrder ?: Int.MAX_VALUE }
                    .thenBy { it.label.lowercase() }
            )
        } else {
            matches.sortedWith(compareBy<CommandTarget> { it.source.ordinal }.thenBy { it.label.lowercase() })
        }
    }
    val entries = remember(filteredCommands, query) {
        if (query.isBlank() && !favoritesOnly) {
            filteredCommands
                .groupBy { it.source }
                .flatMap { (source, sourceCommands) ->
                    listOf(CommandCustomizationEntry.Header(source.displayLabel)) +
                        sourceCommands.map { CommandCustomizationEntry.Command(it) }
                }
        } else {
            filteredCommands.map { CommandCustomizationEntry.Command(it) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Customize entries",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Favorites, hidden entries, and search aliases",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    placeholder = { Text("Search entries") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedSource == null,
                        onClick = { selectedSource = null },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = favoritesOnly,
                        onClick = { favoritesOnly = !favoritesOnly },
                        label = { Text("Favorites") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    availableSources.forEach { source ->
                        FilterChip(
                            selected = selectedSource == source,
                            onClick = { selectedSource = source },
                            label = { Text(source.displayLabel) }
                        )
                    }
                }
                if (commands.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No commands available for the selected sources.")
                    }
                } else if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No entries match \"$query\".")
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = entries,
                            key = { entry ->
                                when (entry) {
                                    is CommandCustomizationEntry.Header -> "header:${entry.label}"
                                    is CommandCustomizationEntry.Command -> entry.command.id
                                }
                            },
                            span = { entry ->
                                when (entry) {
                                    is CommandCustomizationEntry.Header -> GridItemSpan(maxLineSpan)
                                    is CommandCustomizationEntry.Command -> GridItemSpan(1)
                                }
                            }
                        ) { entry ->
                            when (entry) {
                                is CommandCustomizationEntry.Header -> CommandCustomizationHeader(entry.label)
                                is CommandCustomizationEntry.Command -> {
                                    val command = entry.command
                                    val current = customizations[command.id]
                                        ?: SettingsManager.QuickLauncherCommandCustomization(command.id)
                                    CommandCustomizationRow(
                                        command = command,
                                        customization = current,
                                        editing = editingCommandId == command.id,
                                        onToggleFavorite = {
                                            val nextOrder = customizations.values
                                                .filter { it.favorite }
                                                .map { it.favoriteOrder }
                                                .filter { it != Int.MAX_VALUE }
                                                .maxOrNull()
                                                ?.plus(1)
                                                ?: 0
                                            val updated = current.copy(
                                                favorite = !current.favorite,
                                                favoriteOrder = if (!current.favorite) nextOrder else Int.MAX_VALUE
                                            )
                                            customizations[command.id] = updated
                                            onCustomizationChanged(updated)
                                        },
                                        onToggleHidden = {
                                            val updated = current.copy(hidden = !current.hidden)
                                            customizations[command.id] = updated
                                            onCustomizationChanged(updated)
                                        },
                                        onToggleEdit = {
                                            editingCommandId = if (editingCommandId == command.id) null else command.id
                                        },
                                        onCustomSearchChanged = { value ->
                                            val updated = current.copy(customSearch = value)
                                            if (!updated.favorite && !updated.hidden && updated.customSearch.isBlank()) {
                                                customizations.remove(command.id)
                                            } else {
                                                customizations[command.id] = updated
                                            }
                                            onCustomizationChanged(updated)
                                        },
                                        onMoveFavorite = { direction ->
                                            moveFavoriteOrder(
                                                commandId = command.id,
                                                direction = direction,
                                                customizations = customizations,
                                                onCustomizationChanged = onCustomizationChanged
                                            )
                                        },
                                        onColorChanged = { color ->
                                            val updated = current.copy(color = color)
                                            customizations[command.id] = updated
                                            onCustomizationChanged(updated)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed class CommandCustomizationEntry {
    data class Header(val label: String) : CommandCustomizationEntry()
    data class Command(val command: CommandTarget) : CommandCustomizationEntry()
}

@Composable
private fun CommandCustomizationHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
    )
}

private fun moveFavoriteOrder(
    commandId: String,
    direction: Int,
    customizations: MutableMap<String, SettingsManager.QuickLauncherCommandCustomization>,
    onCustomizationChanged: (SettingsManager.QuickLauncherCommandCustomization) -> Unit
) {
    val favorites = customizations.values
        .filter { it.favorite }
        .sortedWith(compareBy<SettingsManager.QuickLauncherCommandCustomization> { it.favoriteOrder }.thenBy { it.commandId })
    val index = favorites.indexOfFirst { it.commandId == commandId }
    val targetIndex = (index + direction).takeIf { index >= 0 && it in favorites.indices } ?: return
    val current = favorites[index]
    val target = favorites[targetIndex]
    val currentOrder = current.favoriteOrder.takeIf { it != Int.MAX_VALUE } ?: index
    val targetOrder = target.favoriteOrder.takeIf { it != Int.MAX_VALUE } ?: targetIndex
    val updatedCurrent = current.copy(favoriteOrder = targetOrder)
    val updatedTarget = target.copy(favoriteOrder = currentOrder)
    customizations[updatedCurrent.commandId] = updatedCurrent
    customizations[updatedTarget.commandId] = updatedTarget
    onCustomizationChanged(updatedCurrent)
    onCustomizationChanged(updatedTarget)
}

@Composable
private fun CommandCustomizationRow(
    command: CommandTarget,
    customization: SettingsManager.QuickLauncherCommandCustomization,
    editing: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleEdit: () -> Unit,
    onCustomSearchChanged: (String) -> Unit,
    onMoveFavorite: (Int) -> Unit,
    onColorChanged: (Int?) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = command.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = command.subtitle ?: command.source.displayLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (customization.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = null,
                        tint = if (customization.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (customization.favorite) {
                    IconButton(onClick = { onMoveFavorite(-1) }) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = null
                        )
                    }
                    IconButton(onClick = { onMoveFavorite(1) }) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                }
                IconButton(onClick = onToggleHidden) {
                    Icon(
                        imageVector = if (customization.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = if (customization.hidden) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggleEdit) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        tint = if (editing || customization.customSearch.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                ColorSwatchButton(
                    color = customization.color,
                    dynamicColor = commandPickerDynamicColor(command),
                    showDynamic = true,
                    onColorChanged = onColorChanged
                )
            }
            if (editing) {
                OutlinedTextField(
                    value = customization.customSearch,
                    onValueChange = onCustomSearchChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search alias") }
                )
            }
        }
    }
}

@Composable
private fun StarterLauncherCosmeticScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    quickLauncherWidthPercent: Int,
    onQuickLauncherWidthPercentChanged: (Int) -> Unit,
    quickLauncherPillMode: Boolean,
    onQuickLauncherPillModeChanged: (Boolean) -> Unit,
    quickLauncherHighlightFavorites: Boolean,
    onQuickLauncherHighlightFavoritesChanged: (Boolean) -> Unit,
    quickLauncherFavoriteColor: Int,
    onQuickLauncherFavoriteColorChanged: (Int) -> Unit,
    quickLauncherIconColors: Boolean,
    onQuickLauncherIconColorsChanged: (Boolean) -> Unit,
    quickLauncherShowAliasFirst: Boolean,
    onQuickLauncherShowAliasFirstChanged: (Boolean) -> Unit,
    quickLauncherStaticTopHighlight: Boolean,
    onQuickLauncherStaticTopHighlightChanged: (Boolean) -> Unit,
    quickLauncherStaticTopHighlightColor: Int,
    onQuickLauncherStaticTopHighlightColorChanged: (Int) -> Unit,
    commandSourceVisibility: List<SettingsManager.CommandSourceVisibility>,
    onCommandSourceVisibilityChanged: (List<SettingsManager.CommandSourceVisibility>) -> Unit
) {
    StarterLauncherSubScreen(
        modifier = modifier,
        title = stringResource(R.string.quick_launcher_cosmetic_title),
        onBack = onBack
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.quick_launcher_width_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(R.string.quick_launcher_width_value, quickLauncherWidthPercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = quickLauncherWidthPercent.toFloat(),
                    onValueChange = { onQuickLauncherWidthPercentChanged(it.toInt()) },
                    valueRange = 50f..100f,
                    steps = 9
                )
            }
        }
        LauncherShortcutTriggerRow(
            icon = { SettingsRowKeyboardIcon() },
            title = stringResource(R.string.quick_launcher_pill_mode_title),
            description = stringResource(R.string.quick_launcher_pill_mode_description),
            checked = quickLauncherPillMode,
            onCheckedChange = onQuickLauncherPillModeChanged
        )
        QuickLauncherFavoriteAppearanceSection(
            highlightFavorites = quickLauncherHighlightFavorites,
            onHighlightFavoritesChanged = onQuickLauncherHighlightFavoritesChanged,
            favoriteColor = quickLauncherFavoriteColor,
            onFavoriteColorChanged = onQuickLauncherFavoriteColorChanged,
            iconColors = quickLauncherIconColors,
            onIconColorsChanged = onQuickLauncherIconColorsChanged,
            showAliasFirst = quickLauncherShowAliasFirst,
            onShowAliasFirstChanged = onQuickLauncherShowAliasFirstChanged,
            staticTopHighlight = quickLauncherStaticTopHighlight,
            onStaticTopHighlightChanged = onQuickLauncherStaticTopHighlightChanged,
            staticTopHighlightColor = quickLauncherStaticTopHighlightColor,
            onStaticTopHighlightColorChanged = onQuickLauncherStaticTopHighlightColorChanged
        )
        QuickLauncherDisplayedEntriesSection(
            visibility = commandSourceVisibility,
            onVisibilityChanged = onCommandSourceVisibilityChanged
        )
    }
}

@Composable
private fun QuickLauncherFavoriteAppearanceSection(
    highlightFavorites: Boolean,
    onHighlightFavoritesChanged: (Boolean) -> Unit,
    favoriteColor: Int,
    onFavoriteColorChanged: (Int) -> Unit,
    iconColors: Boolean,
    onIconColorsChanged: (Boolean) -> Unit,
    showAliasFirst: Boolean,
    onShowAliasFirstChanged: (Boolean) -> Unit,
    staticTopHighlight: Boolean,
    onStaticTopHighlightChanged: (Boolean) -> Unit,
    staticTopHighlightColor: Int,
    onStaticTopHighlightColorChanged: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Entry appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val favoriteHighlightColor = favoriteColor.takeUnless {
                    it == SettingsManager.QUICK_LAUNCHER_DYNAMIC_FAVORITE_COLOR
                }
                Text(
                    text = "Highlight favorites in list",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                ColorSwatchButton(
                    color = favoriteHighlightColor,
                    showDynamic = true,
                    onColorChanged = {
                        onFavoriteColorChanged(
                            it ?: SettingsManager.QUICK_LAUNCHER_DYNAMIC_FAVORITE_COLOR
                        )
                    }
                )
                Switch(
                    checked = highlightFavorites,
                    onCheckedChange = onHighlightFavoritesChanged
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Tint entries from app icon colors",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = iconColors,
                    onCheckedChange = onIconColorsChanged
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Use static top-match highlight color",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                ColorSwatchButton(
                    color = staticTopHighlightColor,
                    showDynamic = false,
                    onColorChanged = { it?.let(onStaticTopHighlightColorChanged) }
                )
                Switch(
                    checked = staticTopHighlight,
                    onCheckedChange = onStaticTopHighlightChanged
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Show search alias before entry name",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = showAliasFirst,
                    onCheckedChange = onShowAliasFirstChanged
                )
            }
        }
    }
}

@Composable
private fun ColorSwatchButton(
    color: Int?,
    dynamicColor: Int? = null,
    showDynamic: Boolean = false,
    onColorChanged: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.small
    Box {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    brush = if (showDynamic && color == null) {
                        Brush.sweepGradient(dynamicSwatchColors())
                    } else {
                        Brush.linearGradient(
                            listOf(
                                Color(color ?: dynamicColor ?: 0x66888888),
                                Color(color ?: dynamicColor ?: 0x66888888)
                            )
                        )
                    },
                    shape = shape
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    shape = shape
                )
                .clickable { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (showDynamic) {
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DynamicColorWheelSwatch()
                            Text("Dynamic")
                        }
                    },
                    onClick = {
                        expanded = false
                        onColorChanged(null)
                    }
                )
            }
            quickLauncherSwatches().forEach { swatch ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(22.dp),
                                shape = MaterialTheme.shapes.extraSmall,
                                color = Color(swatch)
                            ) {}
                            Text("#${swatch.toUInt().toString(16).uppercase().takeLast(6)}")
                        }
                    },
                    onClick = {
                        expanded = false
                        onColorChanged(swatch)
                    }
                )
            }
        }
    }
}

private fun quickLauncherSwatches(): List<Int> {
    return listOf(
        0x7A4285F4,
        0x7A34A853,
        0x7AFABB05,
        0x7AEA4335,
        0x7AA142F4,
        0x7A00ACC1,
        0x7AFF7043,
        0x7A888888
    )
}

@Composable
private fun DynamicColorWheelSwatch() {
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(
                brush = Brush.sweepGradient(dynamicSwatchColors()),
                shape = MaterialTheme.shapes.extraSmall
            )
    )
}

private fun dynamicSwatchColors(): List<Color> {
    return listOf(
        Color(0xFFEA4335),
        Color(0xFFFABB05),
        Color(0xFF34A853),
        Color(0xFF00ACC1),
        Color(0xFF4285F4),
        Color(0xFFA142F4),
        Color(0xFFEA4335)
    )
}

private fun commandPickerDynamicColor(command: CommandTarget, alpha: Float = 0.58f): Int {
    val drawable = (command.icon as? CommandIcon.DrawableIcon)?.drawable
    val dominant = drawable?.dominantPickerIconColor()
    if (dominant != null) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(dominant, hsv)
        val saturation = hsv[1].coerceAtLeast(0.34f).coerceAtMost(0.72f)
        val value = hsv[2].coerceAtLeast(0.58f).coerceAtMost(0.92f)
        return AndroidColor.HSVToColor(
            (alpha * 255).toInt().coerceIn(0, 255),
            floatArrayOf(hsv[0], saturation, value)
        )
    }

    val fallbackHue = when (command.source) {
        CommandSourceId.Apps -> 214f
        CommandSourceId.Pastiera -> 145f
        CommandSourceId.AppActions -> 282f
        CommandSourceId.DeviceControl -> 28f
        CommandSourceId.NavActions -> 190f
    }
    return AndroidColor.HSVToColor(
        (alpha * 255).toInt().coerceIn(0, 255),
        floatArrayOf(fallbackHue, 0.38f, 0.92f)
    )
}

private fun Drawable.dominantPickerIconColor(): Int? {
    val bitmap = toPickerSmallBitmap() ?: return null
    var redTotal = 0L
    var greenTotal = 0L
    var blueTotal = 0L
    var weightTotal = 0L
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    pixels.forEach { pixel ->
        val alpha = AndroidColor.alpha(pixel)
        if (alpha < 48) return@forEach
        val red = AndroidColor.red(pixel)
        val green = AndroidColor.green(pixel)
        val blue = AndroidColor.blue(pixel)
        val max = maxOf(red, green, blue)
        val min = minOf(red, green, blue)
        val saturationWeight = (max - min).coerceAtLeast(18)
        val weight = alpha * saturationWeight
        redTotal += red.toLong() * weight
        greenTotal += green.toLong() * weight
        blueTotal += blue.toLong() * weight
        weightTotal += weight
    }
    if (weightTotal <= 0L) return null
    return AndroidColor.rgb(
        (redTotal / weightTotal).toInt().coerceIn(0, 255),
        (greenTotal / weightTotal).toInt().coerceIn(0, 255),
        (blueTotal / weightTotal).toInt().coerceIn(0, 255)
    )
}

private fun Drawable.toPickerSmallBitmap(): Bitmap? {
    if (this is BitmapDrawable && bitmap != null) {
        return Bitmap.createScaledBitmap(bitmap, 32, 32, true)
    }
    val width = intrinsicWidth.takeIf { it > 0 } ?: 32
    val height = intrinsicHeight.takeIf { it > 0 } ?: 32
    return try {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
        }
    } catch (_: IllegalArgumentException) {
        null
    }
}

@Composable
private fun StarterLauncherSubScreen(
    modifier: Modifier = Modifier,
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun LauncherShortcutTriggerRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
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
                    maxLines = 3
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun StarterLauncherNavigationRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
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
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsRowKeyboardIcon() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ManageSearch,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(24.dp)
    )
}

private fun keyLabel(keyCode: Int): String {
    return when (keyCode) {
        KeyEvent.KEYCODE_SPACE -> "Space"
        KeyEvent.KEYCODE_ENTER -> "Enter"
        KeyEvent.KEYCODE_DEL -> "Backspace"
        KeyEvent.KEYCODE_A -> "A"
        KeyEvent.KEYCODE_B -> "B"
        KeyEvent.KEYCODE_C -> "C"
        KeyEvent.KEYCODE_D -> "D"
        KeyEvent.KEYCODE_E -> "E"
        KeyEvent.KEYCODE_F -> "F"
        KeyEvent.KEYCODE_G -> "G"
        KeyEvent.KEYCODE_H -> "H"
        KeyEvent.KEYCODE_I -> "I"
        KeyEvent.KEYCODE_J -> "J"
        KeyEvent.KEYCODE_K -> "K"
        KeyEvent.KEYCODE_L -> "L"
        KeyEvent.KEYCODE_M -> "M"
        KeyEvent.KEYCODE_N -> "N"
        KeyEvent.KEYCODE_O -> "O"
        KeyEvent.KEYCODE_P -> "P"
        KeyEvent.KEYCODE_Q -> "Q"
        KeyEvent.KEYCODE_R -> "R"
        KeyEvent.KEYCODE_S -> "S"
        KeyEvent.KEYCODE_T -> "T"
        KeyEvent.KEYCODE_U -> "U"
        KeyEvent.KEYCODE_V -> "V"
        KeyEvent.KEYCODE_W -> "W"
        KeyEvent.KEYCODE_X -> "X"
        KeyEvent.KEYCODE_Y -> "Y"
        KeyEvent.KEYCODE_Z -> "Z"
        else -> keyCode.toString()
    }
}

@Composable
private fun SoundSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
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
                        text = stringResource(R.string.settings_category_sounds),
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
            TypingSoundSettingsRow()
        }
    }
}

private sealed class CustomizationDestination {
    object Main : CustomizationDestination()
    object Variations : CustomizationDestination()
    object AppEnterBehavior : CustomizationDestination()
    object NavMode : CustomizationDestination()
    object LauncherShortcuts : CustomizationDestination()
    object LauncherShortcutBehavior : CustomizationDestination()
    object LauncherShortcutCosmetic : CustomizationDestination()
    object LauncherShortcutAssignments : CustomizationDestination()
    object StatusBarButtons : CustomizationDestination()
    object KeyboardTheme : CustomizationDestination()
    object Sounds : CustomizationDestination()
}

private enum class CustomizationNavigationDirection {
    Push,
    Pop
}
