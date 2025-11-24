package it.palsoftware.pastiera

import android.content.Context
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.activity.compose.BackHandler
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import it.palsoftware.pastiera.R

/**
 * Nav Mode settings screen with keyboard visualization.
 */
@Composable
fun NavModeSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // Load nav mode enabled state
    var navModeEnabled by remember {
        mutableStateOf(SettingsManager.getNavModeEnabled(context))
    }
    
    // Load current mappings (all alphabetic keys)
    var keyMappings by remember {
        mutableStateOf(loadAllKeyMappings(context))
    }
    
    // Dialog state for key configuration
    var selectedKeyCode by remember { mutableStateOf<Int?>(null) }
    
    // Load default mappings for comparison
    val defaultMappings = remember {
        loadAllKeyMappings(context, useDefaults = true)
    }
    
    // Handle system back button
    BackHandler { onBack() }
    
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
        
        // Enable/Disable toggle
        Surface(
            modifier = Modifier.fillMaxWidth()
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
        
        
        // Keyboard visualization
        if (navModeEnabled) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text(
                    text = stringResource(R.string.nav_mode_key_mappings),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Keyboard rows
                KeyboardRow(
                    keys = listOf(
                        KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E,
                        KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_Y,
                        KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O,
                        KeyEvent.KEYCODE_P
                    ),
                    mappings = keyMappings,
                    defaultMappings = defaultMappings,
                    onKeyClick = { keyCode ->
                        selectedKeyCode = keyCode
                    }
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                KeyboardRow(
                    keys = listOf(
                        KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D,
                        KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H,
                        KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L
                    ),
                    mappings = keyMappings,
                    defaultMappings = defaultMappings,
                    onKeyClick = { keyCode ->
                        selectedKeyCode = keyCode
                    }
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                KeyboardRow(
                    keys = listOf(
                        KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C,
                        KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_N,
                        KeyEvent.KEYCODE_M
                    ),
                    mappings = keyMappings,
                    defaultMappings = defaultMappings,
                    onKeyClick = { keyCode ->
                        selectedKeyCode = keyCode
                    }
                )
            }
            
            HorizontalDivider()
            
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
                    
                    // Disclaimer
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "⚠",
                                fontSize = 20.sp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.nav_mode_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Key configuration dialog
    selectedKeyCode?.let { keyCode ->
        KeyMappingDialog(
            keyCode = keyCode,
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
                Spacer(modifier = Modifier.width(6.dp))
            }
            KeyButton(
                keyCode = keyCode,
                mapping = mappings[keyCode],
                hasDefault = defaultMappings.containsKey(keyCode),
                onClick = { onKeyClick(keyCode) },
                modifier = Modifier.size(56.dp)
            )
        }
    }
}

@Composable
private fun KeyButton(
    keyCode: Int,
    mapping: KeyMappingLoader.CtrlMapping?,
    hasDefault: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyLabel = getKeyLabel(keyCode)
    val mappingLabel = mapping?.let { getMappingLabelShort(it) }
    val hasMapping = mapping != null && mapping.type != "none"
    
    Surface(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = if (hasMapping) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        color = if (hasMapping) 
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else 
            MaterialTheme.colorScheme.surface,
        tonalElevation = if (hasMapping) 2.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = keyLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 12.8.sp
            )
            if (mappingLabel != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = mappingLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (hasDefault) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.nav_mode_default),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun KeyMappingDialog(
    keyCode: Int,
    currentMapping: KeyMappingLoader.CtrlMapping?,
    defaultMapping: KeyMappingLoader.CtrlMapping?,
    onDismiss: () -> Unit,
    onSave: (KeyMappingLoader.CtrlMapping?) -> Unit
) {
    val keyLabel = getKeyLabel(keyCode)
    var selectedType by remember { mutableStateOf<String?>(currentMapping?.type) }
    var selectedValue by remember { mutableStateOf<String?>(currentMapping?.value) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.nav_mode_configure_key, keyLabel))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Type selection
                Text(
                    text = stringResource(R.string.nav_mode_type),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        selected = selectedType == "none",
                        onClick = {
                            selectedType = "none"
                            selectedValue = null
                        },
                        label = { Text(stringResource(R.string.nav_mode_none)) }
                    )
                }
                
                // Value selection based on type
                if (selectedType == "keycode") {
                    Text(
                        text = stringResource(R.string.nav_mode_keycode),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    val keycodes = listOf(
                        "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT",
                        "TAB", "PAGE_UP", "PAGE_DOWN", "ESCAPE", "DPAD_CENTER"
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(200.dp)
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
                    Text(
                        text = stringResource(R.string.nav_mode_action),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    val actions = listOf(
                        "copy", "paste", "cut", "undo",
                        "select_all", "expand_selection_left", "expand_selection_right"
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(200.dp)
                    ) {
                        items(actions) { action ->
                            FilterChip(
                                selected = selectedValue == action,
                                onClick = { selectedValue = action },
                                label = { Text(action) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                
                // Default mapping info
                if (defaultMapping != null) {
                    TextButton(
                        onClick = {
                            selectedType = defaultMapping.type
                            selectedValue = defaultMapping.value
                        }
                    ) {
                        Text(stringResource(R.string.nav_mode_use_default, getMappingLabel(defaultMapping) ?: ""))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val mapping = when (selectedType) {
                        "keycode" -> selectedValue?.let {
                            KeyMappingLoader.CtrlMapping("keycode", it)
                        }
                        "action" -> selectedValue?.let {
                            KeyMappingLoader.CtrlMapping("action", it)
                        }
                        "none" -> KeyMappingLoader.CtrlMapping("none", "")
                        else -> null
                    }
                    onSave(mapping)
                }
            ) {
                Text(stringResource(R.string.nav_mode_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.nav_mode_cancel))
            }
        }
    )
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

private fun getMappingLabel(mapping: KeyMappingLoader.CtrlMapping): String? {
    return when (mapping.type) {
        "keycode" -> mapping.value
        "action" -> mapping.value
        "none" -> null // Don't show label for "none"
        else -> null
    }
}

@Composable
private fun getMappingLabelShort(mapping: KeyMappingLoader.CtrlMapping): String? {
    return when (mapping.type) {
        "keycode" -> when (mapping.value) {
            "DPAD_UP" -> stringResource(R.string.nav_mode_keycode_up)
            "DPAD_DOWN" -> stringResource(R.string.nav_mode_keycode_down)
            "DPAD_LEFT" -> stringResource(R.string.nav_mode_keycode_left)
            "DPAD_RIGHT" -> stringResource(R.string.nav_mode_keycode_right)
            "DPAD_CENTER" -> stringResource(R.string.nav_mode_keycode_center)
            "PAGE_UP" -> stringResource(R.string.nav_mode_keycode_page_up)
            "PAGE_DOWN" -> stringResource(R.string.nav_mode_keycode_page_down)
            "ESCAPE" -> stringResource(R.string.nav_mode_keycode_escape)
            "TAB" -> stringResource(R.string.nav_mode_keycode_tab)
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
            else -> mapping.value
        }
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
