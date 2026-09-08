package it.palsoftware.pastiera

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.window.DialogProperties
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.commands.CommandRegistry
import it.palsoftware.pastiera.commands.CommandSurface
import it.palsoftware.pastiera.commands.CommandTarget
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import kotlin.math.min

/** One off-main-thread load per screen/configuration, shared by every cell and dialog. */
@Composable
private fun rememberNavCommands(context: Context): List<CommandTarget> {
    val localeTags = LocalConfiguration.current.locales.toLanguageTags()
    var availabilityRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { availabilityRevision++ }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    return produceState(emptyList<CommandTarget>(), context, localeTags, availabilityRevision) {
        value = withContext(Dispatchers.IO) {
            AppListHelper.invalidateInstalledApps()
            CommandRegistry(context).getCommands(CommandSurface.NavMode)
        }
    }.value
}

/**
 * Nav Mode settings screen with keyboard visualization.
 */
@Composable
fun NavModeSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    initialKeyCode: Int? = null
) {
    val context = LocalContext.current
    val commandTargets = rememberNavCommands(context)
    val commandsById = remember(commandTargets) { commandTargets.associateBy { it.id } }

    // Load nav mode enabled state
    var navModeEnabled by remember {
        mutableStateOf(SettingsManager.getNavModeEnabled(context))
    }
    var navModeCtrlHoldEnabled by remember {
        mutableStateOf(SettingsManager.getNavModeCtrlHoldEnabled(context))
    }
    var layoutAwareCtrlShortcutsEnabled by remember {
        mutableStateOf(SettingsManager.getLayoutAwareCtrlShortcutsEnabled(context))
    }
    var showNavModeGuide by remember { mutableStateOf(false) }
    var showLayoutAwareCtrlInfo by remember { mutableStateOf(false) }

    // Load current mappings (all alphabetic keys)
    var keyMappings by remember {
        mutableStateOf(loadAllKeyMappings(context))
    }

    // Dialog state for key configuration
    var selectedKeyCode by remember(initialKeyCode) { mutableStateOf(initialKeyCode) }

    // Load default mappings for comparison
    val defaultMappings = remember {
        loadAllKeyMappings(context, useDefaults = true)
    }
    // Handle system back button

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
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
                    text = stringResource(R.string.nav_mode_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showNavModeGuide = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.nav_mode_guide_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.nav_mode_guide_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Enable/Disable toggle
        Surface(
            modifier = Modifier.fillMaxWidth().settingRow("nav_mode.enabled")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.nav_mode_enable_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.nav_mode_enable_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = navModeEnabled,
                    onCheckedChange = { enabled ->
                        navModeEnabled = enabled
                        SettingsManager.setNavModeEnabled(context, enabled)
                    }
                )
            }
        }

        if (navModeEnabled) {
            val layoutAwareCtrlShortcutsAvailable = !navModeCtrlHoldEnabled

            Surface(
                modifier = Modifier.fillMaxWidth().settingRow("nav_mode.ctrl_hold")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.nav_mode_ctrl_hold_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.nav_mode_ctrl_hold_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = navModeCtrlHoldEnabled,
                        onCheckedChange = { enabled ->
                            navModeCtrlHoldEnabled = enabled
                            SettingsManager.setNavModeCtrlHoldEnabled(context, enabled)
                        }
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().settingRow("nav_mode.layout_aware_ctrl_shortcuts")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.layout_aware_ctrl_shortcuts_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (layoutAwareCtrlShortcutsAvailable) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            IconButton(
                                onClick = { showLayoutAwareCtrlInfo = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = stringResource(R.string.layout_aware_ctrl_shortcuts_info_title),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = stringResource(
                                if (layoutAwareCtrlShortcutsAvailable) {
                                    R.string.layout_aware_ctrl_shortcuts_description
                                } else {
                                    R.string.layout_aware_ctrl_shortcuts_disabled_description
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = layoutAwareCtrlShortcutsEnabled,
                        enabled = layoutAwareCtrlShortcutsAvailable,
                        onCheckedChange = { enabled ->
                            layoutAwareCtrlShortcutsEnabled = enabled
                            SettingsManager.setLayoutAwareCtrlShortcutsEnabled(context, enabled)
                        }
                    )
                }
            }
        }

        if (showLayoutAwareCtrlInfo) {
            AlertDialog(
                onDismissRequest = { showLayoutAwareCtrlInfo = false },
                confirmButton = {
                    TextButton(onClick = { showLayoutAwareCtrlInfo = false }) {
                        Text(stringResource(R.string.close))
                    }
                },
                title = {
                    Text(stringResource(R.string.layout_aware_ctrl_shortcuts_info_title))
                },
                text = {
                    Text(stringResource(R.string.layout_aware_ctrl_shortcuts_info_text))
                }
            )
        }

        if (showNavModeGuide) {
            AlertDialog(
                onDismissRequest = { showNavModeGuide = false },
                modifier = Modifier.fillMaxWidth(0.9f),
                confirmButton = {
                    TextButton(onClick = { showNavModeGuide = false }) {
                        Text(stringResource(R.string.close))
                    }
                },
                title = {
                    Text(stringResource(R.string.nav_mode_guide_title))
                },
                text = {
                    Text(
                        text = stringResource(R.string.nav_mode_guide_text),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.65f)
                            .verticalScroll(rememberScrollState())
                    )
                }
            )
        }


        // Keyboard visualization
        if (navModeEnabled) {
            val keyboardRows = listOf(
                listOf(
                    KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E,
                    KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_Y,
                    KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O,
                    KeyEvent.KEYCODE_P
                ),
                listOf(
                    KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D,
                    KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H,
                    KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L
                ),
                listOf(
                    KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C,
                    KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_N,
                    KeyEvent.KEYCODE_M
                )
            )

            val spacing = 2.dp

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                val density = LocalDensity.current
                val maxKeysInRow = keyboardRows.maxOf { it.size }
                val maxRowWidthPx = with(density) { maxWidth.toPx() }
                val spacingPx = with(density) { spacing.toPx() }
                val desiredSizePx = with(density) { 64.dp.toPx() }
                val totalSpacingPx = spacingPx * (maxKeysInRow - 1)
                val availableForKeys = (maxRowWidthPx - totalSpacingPx).coerceAtLeast(0f)
                val exactSizePx = if (maxKeysInRow > 0) availableForKeys / maxKeysInRow else desiredSizePx
                val finalSizePx = min(desiredSizePx, exactSizePx)
                val keySize = with(density) { finalSizePx.toDp() }
                val keyHeight = keySize * 1.25f

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    keyboardRows.forEach { row ->
                        KeyboardRow(
                            keys = row,
                            keySize = keySize,
                            keyHeight = keyHeight,
                            spacing = spacing,
                            commandsById = commandsById,
                            mappings = keyMappings,
                            defaultMappings = defaultMappings,
                            onKeyClick = { keyCode ->
                                selectedKeyCode = keyCode
                            }
                        )
                    }
                }
            }
            // Revert to default button
            Surface(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            SettingsManager.resetNavModeKeyMappings(context)
                            keyMappings = loadAllKeyMappings(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.nav_mode_revert_to_default))
                    }
                }
            }
        }
    }

    // Key configuration dialog
    selectedKeyCode?.let { keyCode ->
        KeyMappingDialog(
            keyCode = keyCode,
            commandTargets = commandTargets,
            commandsById = commandsById,
            currentMapping = keyMappings[keyCode],
            defaultMapping = defaultMappings[keyCode],
            onDismiss = { selectedKeyCode = null },
            onSave = { mapping ->
                val newMappings = keyMappings.toMutableMap()
                // Always set the mapping (even if "none")
                newMappings[keyCode] = mapping ?: KeyMappingLoader.CtrlMapping("none", "")
                keyMappings = newMappings
                SettingsManager.saveNavModeKeyMappings(context, newMappings)
                selectedKeyCode = null
            }
        )
    }
}

@Composable
private fun KeyboardRow(
    keys: List<Int>,
    keySize: Dp,
    keyHeight: Dp,
    spacing: Dp,
    commandsById: Map<String, CommandTarget>,
    mappings: Map<Int, KeyMappingLoader.CtrlMapping>,
    defaultMappings: Map<Int, KeyMappingLoader.CtrlMapping>,
    onKeyClick: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEachIndexed { index, keyCode ->
            if (index > 0) {
                Spacer(modifier = Modifier.width(spacing))
            }
            KeyButton(
                keyCode = keyCode,
                commandsById = commandsById,
                mapping = mappings[keyCode],
                hasDefault = defaultMappings.containsKey(keyCode),
                onClick = { onKeyClick(keyCode) },
                modifier = Modifier
                    .width(keySize)
                    .height(keyHeight)
            )
        }
    }
}

@Composable
private fun KeyButton(
    keyCode: Int,
    commandsById: Map<String, CommandTarget>,
    mapping: KeyMappingLoader.CtrlMapping?,
    hasDefault: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyLabel = getKeyLabel(keyCode)
    val mappingLabel = mapping?.let { getMappingLabelShort(it, commandsById) }
    val hasMapping = mapping != null && mapping.type != "none"
    val backgroundColor = if (hasMapping) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (hasMapping) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    val keyTextColor = if (hasMapping) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    val mappingTextColor = if (hasMapping) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }
    val mappingIcon: ImageVector?
    val mappingIconDesc: String?
    if (mapping?.type == "keycode") {
        when (mapping.value) {
            "DPAD_UP" -> {
                mappingIcon = Icons.Filled.KeyboardArrowUp
                mappingIconDesc = mappingLabel
            }
            "DPAD_DOWN" -> {
                mappingIcon = Icons.Filled.KeyboardArrowDown
                mappingIconDesc = mappingLabel
            }
            "DPAD_LEFT" -> {
                mappingIcon = Icons.AutoMirrored.Filled.KeyboardArrowLeft
                mappingIconDesc = mappingLabel
            }
            "DPAD_RIGHT" -> {
                mappingIcon = Icons.AutoMirrored.Filled.KeyboardArrowRight
                mappingIconDesc = mappingLabel
            }
            "DPAD_CENTER" -> {
                mappingIcon = Icons.Filled.RadioButtonUnchecked
                mappingIconDesc = mappingLabel
            }
            else -> {
                mappingIcon = null
                mappingIconDesc = null
            }
        }
    } else {
        mappingIcon = null
        mappingIconDesc = null
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        color = backgroundColor,
        tonalElevation = if (hasMapping) 2.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = keyLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = keyTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (mappingIcon != null) {
                Icon(
                    imageVector = mappingIcon,
                    contentDescription = mappingIconDesc,
                    tint = mappingTextColor,
                    modifier = Modifier.size(18.dp)
                )
            } else if (mappingLabel != null) {
                Text(
                    text = mappingLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = mappingTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            } else if (hasDefault) {
                Text(
                    text = stringResource(R.string.nav_mode_default),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyMappingDialog(
    keyCode: Int,
    commandTargets: List<CommandTarget>,
    commandsById: Map<String, CommandTarget>,
    currentMapping: KeyMappingLoader.CtrlMapping?,
    defaultMapping: KeyMappingLoader.CtrlMapping?,
    onDismiss: () -> Unit,
    onSave: (KeyMappingLoader.CtrlMapping?) -> Unit
) {
    val keyLabel = getKeyLabel(keyCode)
    val context = LocalContext.current
    var selectedType by remember { mutableStateOf<String?>(currentMapping?.type) }
    var selectedValue by remember { mutableStateOf<String?>(currentMapping?.value) }
    val defaultLabel = defaultMapping?.let { getMappingLabel(it, context, commandsById) }
    val dialogMaxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f
    val gridMaxHeight = dialogMaxHeight * 0.6f

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .heightIn(max = dialogMaxHeight),
            shape = AlertDialogDefaults.shape,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            color = AlertDialogDefaults.containerColor,
            contentColor = AlertDialogDefaults.textContentColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.nav_mode_configure_key, keyLabel),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Type selection
                    Text(
                        text = stringResource(R.string.nav_mode_type),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedType == "keycode",
                            onClick = {
                                selectedType = "keycode"
                                selectedValue = null
                            },
                            label = { Text(stringResource(R.string.nav_mode_keycode)) }
                        )
                        FilterChip(
                            selected = selectedType == "action",
                            onClick = {
                                selectedType = "action"
                                selectedValue = null
                            },
                            label = { Text(stringResource(R.string.nav_mode_action)) }
                        )
                        FilterChip(
                            selected = selectedType == "native_ctrl",
                            onClick = {
                                selectedType = "native_ctrl"
                                selectedValue = null
                            },
                            label = { Text(stringResource(R.string.nav_mode_native_ctrl)) }
                        )
                        FilterChip(
                            selected = selectedType == "command",
                            onClick = {
                                selectedType = "command"
                                selectedValue = null
                            },
                            label = { Text(stringResource(R.string.nav_mode_command)) }
                        )
                        FilterChip(
                            selected = selectedType == "none",
                            onClick = {
                                selectedType = "none"
                                selectedValue = null
                            },
                            label = { Text(stringResource(R.string.nav_mode_none)) }
                        )
                        if (defaultLabel != null) {
                            TextButton(
                                onClick = {
                                    selectedType = defaultMapping?.type
                                    selectedValue = defaultMapping?.value
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.nav_mode_use_default, defaultLabel),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textDecoration = TextDecoration.Underline
                                )
                            }
                        }
                    }

                    // Value selection based on type
                    if (selectedType == "keycode") {
                        val keycodes = listOf(
                            "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT",
                            "TAB", "MOVE_HOME", "MOVE_END", "PAGE_UP", "PAGE_DOWN", "ESCAPE", "DPAD_CENTER",
                            "FORWARD_DEL"
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                            modifier = Modifier.heightIn(max = gridMaxHeight)
                        ) {
                            items(keycodes) { keycode ->
                                FilterChip(
                                    selected = selectedValue == keycode,
                                    onClick = { selectedValue = keycode },
                                    label = { Text(keycode) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else if (selectedType == "action") {
                        val actions = listOf(
                            "copy", "paste", "cut", "undo",
                            "select_all", "expand_selection_left", "expand_selection_right",
                            "move_word_left", "move_word_right",
                            "expand_selection_word_left", "expand_selection_word_right",
                            "page_start", "page_end",
                            "toggle_minimal_ui",
                            "media_play_pause", "media_previous", "media_next"
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                            modifier = Modifier.heightIn(max = gridMaxHeight)
                        ) {
                            items(actions) { action ->
                                FilterChip(
                                    selected = selectedValue == action,
                                    onClick = { selectedValue = action },
                                    label = { Text(getActionLabel(action)) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else if (selectedType == "command") {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                            modifier = Modifier.heightIn(max = gridMaxHeight)
                        ) {
                            items(commandTargets, key = { it.id }) { command ->
                                FilterChip(
                                    selected = selectedValue == command.id,
                                    onClick = { selectedValue = command.id },
                                    label = {
                                        Text(
                                            text = commandLabel(command),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.nav_mode_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val mapping = when (selectedType) {
                                "keycode" -> selectedValue?.let {
                                    KeyMappingLoader.CtrlMapping("keycode", it)
                                }
                                "action" -> selectedValue?.let {
                                    KeyMappingLoader.CtrlMapping("action", it)
                                }
                                "command" -> selectedValue?.let {
                                    KeyMappingLoader.CtrlMapping("command", it)
                                }
                                "native_ctrl" -> KeyMappingLoader.CtrlMapping("native_ctrl", "")
                                "none" -> KeyMappingLoader.CtrlMapping("none", "")
                                else -> null
                            }
                            onSave(mapping)
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.nav_mode_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun getKeyLabel(keyCode: Int): String {
    return when (keyCode) {
        KeyEvent.KEYCODE_Q -> "Q"
        KeyEvent.KEYCODE_W -> "W"
        KeyEvent.KEYCODE_E -> "E"
        KeyEvent.KEYCODE_R -> "R"
        KeyEvent.KEYCODE_T -> "T"
        KeyEvent.KEYCODE_Y -> "Y"
        KeyEvent.KEYCODE_U -> "U"
        KeyEvent.KEYCODE_I -> "I"
        KeyEvent.KEYCODE_O -> "O"
        KeyEvent.KEYCODE_P -> "P"
        KeyEvent.KEYCODE_A -> "A"
        KeyEvent.KEYCODE_S -> "S"
        KeyEvent.KEYCODE_D -> "D"
        KeyEvent.KEYCODE_F -> "F"
        KeyEvent.KEYCODE_G -> "G"
        KeyEvent.KEYCODE_H -> "H"
        KeyEvent.KEYCODE_J -> "J"
        KeyEvent.KEYCODE_K -> "K"
        KeyEvent.KEYCODE_L -> "L"
        KeyEvent.KEYCODE_Z -> "Z"
        KeyEvent.KEYCODE_X -> "X"
        KeyEvent.KEYCODE_C -> "C"
        KeyEvent.KEYCODE_V -> "V"
        KeyEvent.KEYCODE_B -> "B"
        KeyEvent.KEYCODE_N -> "N"
        KeyEvent.KEYCODE_M -> "M"
        else -> stringResource(R.string.nav_mode_key_unknown)
    }
}

private fun getMappingLabel(mapping: KeyMappingLoader.CtrlMapping, context: Context, commandsById: Map<String, CommandTarget>): String? {
    return when (mapping.type) {
        "keycode" -> mapping.value
        "action" -> mapping.value
        "command" -> commandsById[mapping.value]?.let { command ->
            "${command.source.localizedDisplayLabel(context)}: ${command.label}"
        } ?: mapping.value
        "native_ctrl" -> "Ctrl"
        "none" -> null // Don't show label for "none"
        else -> null
    }
}

@Composable
private fun commandLabel(command: CommandTarget): String {
    return "${command.source.localizedDisplayLabel(LocalContext.current)}: ${command.label}"
}

@Composable
private fun getActionLabel(action: String): String {
    return when (action) {
        "copy" -> stringResource(R.string.nav_mode_action_copy)
        "paste" -> stringResource(R.string.nav_mode_action_paste)
        "cut" -> stringResource(R.string.nav_mode_action_cut)
        "undo" -> stringResource(R.string.nav_mode_action_undo)
        "select_all" -> stringResource(R.string.nav_mode_action_select_all)
        "expand_selection_left" -> stringResource(R.string.nav_mode_action_expand_selection_left)
        "expand_selection_right" -> stringResource(R.string.nav_mode_action_expand_selection_right)
        "move_word_left" -> stringResource(R.string.nav_mode_action_move_word_left)
        "move_word_right" -> stringResource(R.string.nav_mode_action_move_word_right)
        "expand_selection_word_left" -> stringResource(R.string.nav_mode_action_expand_selection_word_left)
        "expand_selection_word_right" -> stringResource(R.string.nav_mode_action_expand_selection_word_right)
        "toggle_minimal_ui" -> stringResource(R.string.nav_mode_action_toggle_pastierina)
        "media_play_pause" -> stringResource(R.string.nav_mode_action_media_play_pause)
        "media_previous" -> stringResource(R.string.nav_mode_action_media_previous)
        "media_next" -> stringResource(R.string.nav_mode_action_media_next)
        else -> action
    }
}

@Composable
private fun getMappingLabelShort(mapping: KeyMappingLoader.CtrlMapping, commandsById: Map<String, CommandTarget>): String? {
    return when (mapping.type) {
        "keycode" -> when (mapping.value) {
            "DPAD_UP" -> stringResource(R.string.nav_mode_keycode_up)
            "DPAD_DOWN" -> stringResource(R.string.nav_mode_keycode_down)
            "DPAD_LEFT" -> stringResource(R.string.nav_mode_keycode_left)
            "DPAD_RIGHT" -> stringResource(R.string.nav_mode_keycode_right)
            "DPAD_CENTER" -> stringResource(R.string.nav_mode_keycode_center)
            "MOVE_HOME" -> stringResource(R.string.nav_mode_keycode_home)
            "MOVE_END" -> stringResource(R.string.nav_mode_keycode_end)
            "PAGE_UP" -> stringResource(R.string.nav_mode_keycode_page_up)
            "PAGE_DOWN" -> stringResource(R.string.nav_mode_keycode_page_down)
            "ESCAPE" -> stringResource(R.string.nav_mode_keycode_escape)
            "TAB" -> stringResource(R.string.nav_mode_keycode_tab)
            "FORWARD_DEL" -> stringResource(R.string.nav_mode_keycode_forward_delete)
            else -> mapping.value
        }
        "action" -> when (mapping.value) {
            "copy" -> stringResource(R.string.nav_mode_action_copy)
            "paste" -> stringResource(R.string.nav_mode_action_paste)
            "cut" -> stringResource(R.string.nav_mode_action_cut)
            "undo" -> stringResource(R.string.nav_mode_action_undo)
            "select_all" -> stringResource(R.string.nav_mode_action_select_all)
            "expand_selection_left" -> stringResource(R.string.nav_mode_action_expand_selection_left)
            "expand_selection_right" -> stringResource(R.string.nav_mode_action_expand_selection_right)
            "move_word_left" -> stringResource(R.string.nav_mode_action_move_word_left)
            "move_word_right" -> stringResource(R.string.nav_mode_action_move_word_right)
            "expand_selection_word_left" -> stringResource(R.string.nav_mode_action_expand_selection_word_left)
            "expand_selection_word_right" -> stringResource(R.string.nav_mode_action_expand_selection_word_right)
            "page_start" -> stringResource(R.string.nav_mode_action_page_start)
            "page_end" -> stringResource(R.string.nav_mode_action_page_end)
            "toggle_minimal_ui" -> stringResource(R.string.nav_mode_action_toggle_pastierina)
            "media_play_pause" -> stringResource(R.string.nav_mode_action_media_play_pause)
            "media_previous" -> stringResource(R.string.nav_mode_action_media_previous)
            "media_next" -> stringResource(R.string.nav_mode_action_media_next)
            else -> mapping.value
        }
        "command" -> commandsById[mapping.value]?.let { command ->
            command.label
        } ?: mapping.value.substringAfterLast('.').replace('_', ' ')
        "native_ctrl" -> "Ctrl"
        "none" -> null // Don't show label for "none"
        else -> null
    }
}

private fun loadAllKeyMappings(context: Context, useDefaults: Boolean = false): Map<Int, KeyMappingLoader.CtrlMapping> {
    val allAlphabeticKeys = listOf(
        KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_R,
        KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_Y, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I,
        KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S,
        KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H,
        KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_Z,
        KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B,
        KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_M
    )

    val loadedMappings = try {
        val assets = context.assets
        if (useDefaults) {
            KeyMappingLoader.loadCtrlKeyMappings(assets, null) // Load from assets only
        } else {
            KeyMappingLoader.loadCtrlKeyMappings(assets, context) // Load custom if exists
        }
    } catch (e: Exception) {
        emptyMap()
    }

    // Return all keys with their mappings (or null if no mapping)
    return allAlphabeticKeys.associateWith { keyCode ->
        loadedMappings[keyCode] ?: KeyMappingLoader.CtrlMapping("none", "")
    }
}
