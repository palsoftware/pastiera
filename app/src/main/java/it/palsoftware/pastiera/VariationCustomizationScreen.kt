package it.palsoftware.pastiera

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.data.variation.VariationRepository
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils.localeString
import android.view.inputmethod.InputMethodManager
import org.json.JSONObject

private fun staticVariationPresetOptions(): List<String> {
    return listOf(
        SettingsManager.STATIC_VARIATION_PRESET_OFF,
        SettingsManager.STATIC_VARIATION_PRESET_SYMBOLS,
        SettingsManager.STATIC_VARIATION_PRESET_NUMBERS,
        SettingsManager.STATIC_VARIATION_PRESET_ALTERNATIVE,
        SettingsManager.STATIC_VARIATION_PRESET_DEV_CHOICE
    )
}

private const val MAX_STATIC_VARIATION_SLOTS = 10

@Composable
private fun getStaticVariationPresetLabel(preset: String): String {
    return when (preset) {
        SettingsManager.STATIC_VARIATION_PRESET_OFF -> stringResource(R.string.static_variation_preset_off)
        SettingsManager.STATIC_VARIATION_PRESET_SYMBOLS -> stringResource(R.string.static_variation_preset_symbols)
        SettingsManager.STATIC_VARIATION_PRESET_NUMBERS -> stringResource(R.string.static_variation_preset_numbers)
        SettingsManager.STATIC_VARIATION_PRESET_ALTERNATIVE -> stringResource(R.string.static_variation_preset_alternative)
        SettingsManager.STATIC_VARIATION_PRESET_DEV_CHOICE -> stringResource(R.string.static_variation_preset_dev_choice)
        else -> preset
    }
}

@Composable
private fun getStaticVariationPresetDescription(preset: String): String {
    return when (preset) {
        SettingsManager.STATIC_VARIATION_PRESET_OFF -> stringResource(R.string.static_variation_preset_off_description)
        SettingsManager.STATIC_VARIATION_PRESET_SYMBOLS -> stringResource(R.string.static_variation_preset_symbols_description)
        SettingsManager.STATIC_VARIATION_PRESET_NUMBERS -> stringResource(R.string.static_variation_preset_numbers_description)
        SettingsManager.STATIC_VARIATION_PRESET_ALTERNATIVE -> stringResource(R.string.static_variation_preset_alternative_description)
        SettingsManager.STATIC_VARIATION_PRESET_DEV_CHOICE -> stringResource(R.string.static_variation_preset_dev_choice_description)
        else -> ""
    }
}

/**
 * Screen for customizing letter variations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VariationCustomizationScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // Load AllVariations.json (static map with all possibilities)
    val allVariations = remember {
        loadAllVariationsFromJson(context)
    }
    
    // Load active variations (from file or assets)
    // Convert from Map<Char, List<String>> to Map<String, List<String>>
    val activeLayoutName = remember { resolveActiveLayoutForVariationScreen(context) }
    val availableLayouts = remember {
        LayoutMappingRepository.getAvailableLayouts(context.assets, context).sorted()
    }
    var globalVariationLayoutOverride by remember {
        mutableStateOf(SettingsManager.getGlobalVariationLayoutOverride(context))
    }
    var variations by remember(activeLayoutName) {
        val repoVariations = VariationRepository.loadVariations(
            assets = context.assets,
            context = context,
            activeLayoutName = activeLayoutName
        )
        mutableStateOf(repoVariations.mapKeys { it.key.toString() })
    }
    
    // Load static variations to preserve them when saving
    var staticVariations by remember {
        val loaded = VariationRepository.loadStaticVariations(context.assets, context)
        val initial = if (loaded.isNotEmpty()) loaded else SettingsManager.getStaticVariationBasePreset(context)
        mutableStateOf(initial.take(MAX_STATIC_VARIATION_SLOTS))
    }
    var staticVariationsShift by remember {
        val loaded = VariationRepository.loadStaticVariationsShift(context.assets, context)
        val initial = if (loaded.isNotEmpty()) loaded else SettingsManager.getDefaultStaticVariationShiftPreset()
        mutableStateOf(initial.take(MAX_STATIC_VARIATION_SLOTS))
    }
    var staticVariationsAlt by remember {
        val loaded = VariationRepository.loadStaticVariationsAlt(context.assets, context)
        val initial = if (loaded.isNotEmpty()) loaded else SettingsManager.getDefaultStaticVariationAltPreset()
        mutableStateOf(initial.take(MAX_STATIC_VARIATION_SLOTS))
    }
    
    // Generate alphabet list with uppercase followed by lowercase for each letter (A, a, B, b, ...)
    val alphabet = remember {
        ('A'..'Z').flatMap { listOf(it, it.lowercaseChar()) }
    }
    
    // State for picker dialog
    var showPickerDialog by remember { mutableStateOf(false) }
    var selectedLetter by remember { mutableStateOf<String?>(null) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    
    // State for static variation input dialog
    var showStaticInputDialog by remember { mutableStateOf(false) }
    var staticInputIndex by remember { mutableStateOf<Int?>(null) }
    var staticInputValue by remember { mutableStateOf("") }
    var staticInputLayer by remember { mutableStateOf(StaticLayer.Default) }
    
    // State for reset confirmation dialog
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    
    var staticVariationPreset by remember {
        mutableStateOf(SettingsManager.getStaticVariationBarPreset(context))
    }
    var staticVariationPresetExpanded by remember { mutableStateOf(false) }

    // State for sticky layer behavior after modifier hold.
    var staticVariationLayerSticky by remember {
        mutableStateOf(SettingsManager.isStaticVariationBarLayerStickyEnabled(context))
    }
    var showGlobalOverrideMenu by remember { mutableStateOf(false) }
    
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back_content_description)
                            )
                        }
                        Text(
                            text = stringResource(R.string.variation_customize_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    
                    // Reset to default button (only if custom variations exist)
                    if (SettingsManager.hasCustomVariations(context)) {
                        IconButton(
                            onClick = { showResetConfirmDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.variation_reset_to_default),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Static Variation Bar preset selector
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
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
                            text = stringResource(R.string.static_variation_preset_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.static_variation_bar_mode_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )

                        ExposedDropdownMenuBox(
                            expanded = staticVariationPresetExpanded,
                            onExpandedChange = { staticVariationPresetExpanded = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            OutlinedTextField(
                                value = getStaticVariationPresetLabel(staticVariationPreset),
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(staticVariationPresetExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )
                            ExposedDropdownMenu(
                                expanded = staticVariationPresetExpanded,
                                onDismissRequest = { staticVariationPresetExpanded = false }
                            ) {
                                staticVariationPresetOptions().forEach { preset ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(getStaticVariationPresetLabel(preset))
                                                Text(
                                                    text = getStaticVariationPresetDescription(preset),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            staticVariationPreset = preset
                                            staticVariationPresetExpanded = false
                                            SettingsManager.setStaticVariationBarPreset(context, preset)
                                            if (preset != SettingsManager.STATIC_VARIATION_PRESET_OFF) {
                                                staticVariations = SettingsManager.getStaticVariationBasePreset(context).take(MAX_STATIC_VARIATION_SLOTS)
                                                if (preset == SettingsManager.STATIC_VARIATION_PRESET_NUMBERS) {
                                                    staticVariationsShift = SettingsManager.getStaticVariationNumbersPreset().take(MAX_STATIC_VARIATION_SLOTS)
                                                    staticVariationsAlt = SettingsManager.getStaticVariationNumbersPreset().take(MAX_STATIC_VARIATION_SLOTS)
                                                } else if (preset == SettingsManager.STATIC_VARIATION_PRESET_DEV_CHOICE) {
                                                    staticVariationsShift = SettingsManager.getDevChoiceStaticVariationBasePreset().take(MAX_STATIC_VARIATION_SLOTS)
                                                    staticVariationsAlt = SettingsManager.getDevChoiceStaticVariationBasePreset().take(MAX_STATIC_VARIATION_SLOTS)
                                                } else {
                                                    staticVariationsShift = SettingsManager.getDefaultStaticVariationShiftPreset().take(MAX_STATIC_VARIATION_SLOTS)
                                                    staticVariationsAlt = SettingsManager.getDefaultStaticVariationAltPreset().take(MAX_STATIC_VARIATION_SLOTS)
                                                }
                                                SettingsManager.saveVariations(
                                                    context = context,
                                                    variations = variations,
                                                    staticVariations = staticVariations,
                                                    staticVariationsShift = staticVariationsShift,
                                                    staticVariationsAlt = staticVariationsAlt
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sticky layer toggle
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
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.static_variation_layer_sticky_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.static_variation_layer_sticky_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    Switch(
                        checked = staticVariationLayerSticky,
                        onCheckedChange = { enabled ->
                            staticVariationLayerSticky = enabled
                            SettingsManager.setStaticVariationBarLayerStickyEnabled(context, enabled)
                        }
                    )
                }
            }
            
            Surface(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.variation_global_layout_override_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(
                            R.string.variation_global_layout_override_description,
                            activeLayoutName
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ExposedDropdownMenuBox(
                        expanded = showGlobalOverrideMenu,
                        onExpandedChange = { showGlobalOverrideMenu = it }
                    ) {
                        val selectedText = globalVariationLayoutOverride
                            ?: stringResource(R.string.variation_global_layout_override_none)
                        OutlinedTextField(
                            value = selectedText,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = showGlobalOverrideMenu)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = showGlobalOverrideMenu,
                            onDismissRequest = { showGlobalOverrideMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.variation_global_layout_override_none)) },
                                onClick = {
                                    showGlobalOverrideMenu = false
                                    globalVariationLayoutOverride = null
                                    SettingsManager.setGlobalVariationLayoutOverride(context, null)
                                    val repoVariations = VariationRepository.loadVariations(
                                        assets = context.assets,
                                        context = context,
                                        activeLayoutName = activeLayoutName
                                    )
                                    variations = repoVariations.mapKeys { it.key.toString() }
                                }
                            )
                            val options = if (
                                globalVariationLayoutOverride != null &&
                                !availableLayouts.contains(globalVariationLayoutOverride)
                            ) {
                                listOfNotNull(globalVariationLayoutOverride) + availableLayouts
                            } else {
                                availableLayouts
                            }
                            options.forEach { layoutName ->
                                DropdownMenuItem(
                                    text = { Text(layoutName) },
                                    onClick = {
                                        showGlobalOverrideMenu = false
                                        globalVariationLayoutOverride = layoutName
                                        SettingsManager.setGlobalVariationLayoutOverride(context, layoutName)
                                        val repoVariations = VariationRepository.loadVariations(
                                            assets = context.assets,
                                            context = context,
                                            activeLayoutName = activeLayoutName
                                        )
                                        variations = repoVariations.mapKeys { it.key.toString() }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Static variations row
            VariationRow(
                letter = "S",
                variations = staticVariations,
                labelWidth = 64.dp,
                labelColor = Color.Transparent,
                onBoxClick = { index ->
                    staticInputLayer = StaticLayer.Default
                    staticInputIndex = index
                    staticInputValue = staticVariations.getOrNull(index) ?: ""
                    showStaticInputDialog = true
                },
                onReorder = { fromIndex, toIndex ->
                    val reordered = reorderEntries(staticVariations, fromIndex, toIndex)
                    if (reordered != staticVariations) {
                        staticVariations = reordered
                        SettingsManager.saveVariations(
                            context = context,
                            variations = variations,
                            staticVariations = reordered,
                            staticVariationsShift = staticVariationsShift,
                            staticVariationsAlt = staticVariationsAlt
                        )
                    }
                },
                onAddSlot = {
                    if (staticVariations.size < MAX_STATIC_VARIATION_SLOTS) {
                        staticVariations = staticVariations + ""
                        saveStaticRows(context, variations, staticVariations, staticVariationsShift, staticVariationsAlt)
                    }
                },
                onRemoveSlot = {
                    if (staticVariations.isNotEmpty()) {
                        staticVariations = staticVariations.dropLast(1)
                        saveStaticRows(context, variations, staticVariations, staticVariationsShift, staticVariationsAlt)
                    }
                }
            )

            VariationRow(
                letter = stringResource(R.string.long_press_modifier_shift),
                variations = staticVariationsShift,
                labelWidth = 64.dp,
                onBoxClick = { index ->
                    staticInputLayer = StaticLayer.Shift
                    staticInputIndex = index
                    staticInputValue = staticVariationsShift.getOrNull(index) ?: ""
                    showStaticInputDialog = true
                },
                onReorder = { fromIndex, toIndex ->
                    val reordered = reorderEntries(staticVariationsShift, fromIndex, toIndex)
                    if (reordered != staticVariationsShift) {
                        staticVariationsShift = reordered
                        SettingsManager.saveVariations(
                            context = context,
                            variations = variations,
                            staticVariations = staticVariations,
                            staticVariationsShift = reordered,
                            staticVariationsAlt = staticVariationsAlt
                        )
                    }
                },
                onAddSlot = {
                    if (staticVariationsShift.size < MAX_STATIC_VARIATION_SLOTS) {
                        staticVariationsShift = staticVariationsShift + ""
                        saveStaticRows(context, variations, staticVariations, staticVariationsShift, staticVariationsAlt)
                    }
                },
                onRemoveSlot = {
                    if (staticVariationsShift.isNotEmpty()) {
                        staticVariationsShift = staticVariationsShift.dropLast(1)
                        saveStaticRows(context, variations, staticVariations, staticVariationsShift, staticVariationsAlt)
                    }
                }
            )

            VariationRow(
                letter = stringResource(R.string.long_press_modifier_alt),
                variations = staticVariationsAlt,
                labelWidth = 64.dp,
                onBoxClick = { index ->
                    staticInputLayer = StaticLayer.Alt
                    staticInputIndex = index
                    staticInputValue = staticVariationsAlt.getOrNull(index) ?: ""
                    showStaticInputDialog = true
                },
                onReorder = { fromIndex, toIndex ->
                    val reordered = reorderEntries(staticVariationsAlt, fromIndex, toIndex)
                    if (reordered != staticVariationsAlt) {
                        staticVariationsAlt = reordered
                        SettingsManager.saveVariations(
                            context = context,
                            variations = variations,
                            staticVariations = staticVariations,
                            staticVariationsShift = staticVariationsShift,
                            staticVariationsAlt = reordered
                        )
                    }
                },
                onAddSlot = {
                    if (staticVariationsAlt.size < MAX_STATIC_VARIATION_SLOTS) {
                        staticVariationsAlt = staticVariationsAlt + ""
                        saveStaticRows(context, variations, staticVariations, staticVariationsShift, staticVariationsAlt)
                    }
                },
                onRemoveSlot = {
                    if (staticVariationsAlt.isNotEmpty()) {
                        staticVariationsAlt = staticVariationsAlt.dropLast(1)
                        saveStaticRows(context, variations, staticVariations, staticVariationsShift, staticVariationsAlt)
                    }
                }
            )
            
            HorizontalDivider()
            
            // Alphabet grid
            alphabet.forEach { letter ->
                // key() ensures each row keeps its own drag state when list recomposes
                key(letter) {
                    val letterStr = letter.toString()
                    val letterVariations = variations[letterStr] ?: emptyList()
                    
                    VariationRow(
                        letter = letterStr,
                        variations = letterVariations,
                        onBoxClick = { index ->
                            selectedLetter = letterStr
                            selectedIndex = index
                            showPickerDialog = true
                        },
                        onReorder = { fromIndex, toIndex ->
                            val updatedMap = variations.toMutableMap()
                            val current = updatedMap[letterStr] ?: emptyList()
                            val reordered = reorderEntries(current, fromIndex, toIndex)
                            
                            if (reordered != current) {
                                updatedMap[letterStr] = reordered
                                variations = updatedMap
                                SettingsManager.saveVariations(
                                    context = context,
                                    variations = updatedMap,
                                    staticVariations = staticVariations,
                                    staticVariationsShift = staticVariationsShift,
                                    staticVariationsAlt = staticVariationsAlt
                                )
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Variation picker dialog
    if (showPickerDialog && selectedLetter != null) {
        val letterKey = selectedLetter!!
        val availableVariations = allVariations[letterKey]
            ?: allVariations[letterKey.uppercase()]
            ?: emptyList()
        
        VariationPickerDialog(
            letter = letterKey,
            availableVariations = availableVariations,
            onVariationSelected = { character ->
                val updatedVariations = variations.toMutableMap()
                val currentVariations = updatedVariations[letterKey] ?: emptyList()
                
                val trimmedVariations = updateVariationEntries(currentVariations, selectedIndex, character)
                
                if (trimmedVariations.isEmpty()) {
                    updatedVariations.remove(letterKey)
                } else {
                    updatedVariations[letterKey] = trimmedVariations
                }
                
                variations = updatedVariations
                
                // Save directly to variations.json file
                SettingsManager.saveVariations(
                    context = context,
                    variations = variations,
                    staticVariations = staticVariations,
                    staticVariationsShift = staticVariationsShift,
                    staticVariationsAlt = staticVariationsAlt
                )
            },
            onDismiss = {
                showPickerDialog = false
                selectedLetter = null
                selectedIndex = null
            }
        )
    }

    if (showStaticInputDialog && staticInputIndex != null) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(showStaticInputDialog, staticInputIndex) {
            focusRequester.requestFocus()
        }
        
        AlertDialog(
            onDismissRequest = {
                showStaticInputDialog = false
                staticInputIndex = null
                staticInputValue = ""
                staticInputLayer = StaticLayer.Default
            },
            title = {
                Text(stringResource(R.string.static_variation_bar_mode_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = staticInputValue,
                        onValueChange = { newValue ->
                            staticInputValue = newValue
                        },
                        singleLine = true,
                        modifier = Modifier.focusRequester(focusRequester),
                        label = { Text(stringResource(R.string.static_variation_input_label)) }
                    )
                    Text(
                        text = stringResource(R.string.static_variation_input_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val index = staticInputIndex
                        if (index != null) {
                            val trimmedStatic = updateStaticVariationEntries(
                                currentEntries = when (staticInputLayer) {
                                    StaticLayer.Default -> staticVariations
                                    StaticLayer.Shift -> staticVariationsShift
                                    StaticLayer.Alt -> staticVariationsAlt
                                },
                                index = index,
                                newValue = staticInputValue
                            )
                            when (staticInputLayer) {
                                StaticLayer.Default -> staticVariations = trimmedStatic
                                StaticLayer.Shift -> staticVariationsShift = trimmedStatic
                                StaticLayer.Alt -> staticVariationsAlt = trimmedStatic
                            }
                            SettingsManager.saveVariations(
                                context = context,
                                variations = variations,
                                staticVariations = staticVariations,
                                staticVariationsShift = staticVariationsShift,
                                staticVariationsAlt = staticVariationsAlt
                            )
                        }
                        
                        showStaticInputDialog = false
                        staticInputIndex = null
                        staticInputValue = ""
                        staticInputLayer = StaticLayer.Default
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showStaticInputDialog = false
                        staticInputIndex = null
                        staticInputValue = ""
                        staticInputLayer = StaticLayer.Default
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // Reset confirmation dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Text(stringResource(R.string.variation_reset_confirm_title))
            },
            text = {
                Text(stringResource(R.string.variation_reset_confirm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        SettingsManager.resetVariationsToDefault(context)
                        val repoVariations = VariationRepository.loadVariations(
                            assets = context.assets,
                            context = context,
                            activeLayoutName = activeLayoutName
                        )
                        variations = repoVariations.mapKeys { it.key.toString() }
                        staticVariationPreset = SettingsManager.getStaticVariationBarPreset(context)
                        staticVariations = SettingsManager.getStaticVariationBasePreset(context).take(MAX_STATIC_VARIATION_SLOTS)
                        val loadedShift = VariationRepository.loadStaticVariationsShift(context.assets, context)
                        staticVariationsShift = if (loadedShift.isNotEmpty()) {
                            loadedShift.take(MAX_STATIC_VARIATION_SLOTS)
                        } else {
                            SettingsManager.getDefaultStaticVariationShiftPreset().take(MAX_STATIC_VARIATION_SLOTS)
                        }
                        val loadedAlt = VariationRepository.loadStaticVariationsAlt(context.assets, context)
                        staticVariationsAlt = if (loadedAlt.isNotEmpty()) {
                            loadedAlt.take(MAX_STATIC_VARIATION_SLOTS)
                        } else {
                            SettingsManager.getDefaultStaticVariationAltPreset().take(MAX_STATIC_VARIATION_SLOTS)
                        }
                        SettingsManager.saveVariations(
                            context = context,
                            variations = variations,
                            staticVariations = staticVariations,
                            staticVariationsShift = staticVariationsShift,
                            staticVariationsAlt = staticVariationsAlt
                        )
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.variation_reset_confirm_button))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirmDialog = false }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * Displays a single base letter row and handles local drag state so callers only
 * receive the final reorder indexes.
 */
@Composable
private fun VariationRow(
    letter: String,
    variations: List<String>,
    onBoxClick: (Int) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    labelWidth: Dp = 40.dp,
    labelColor: androidx.compose.ui.graphics.Color? = null,
    onAddSlot: (() -> Unit)? = null,
    onRemoveSlot: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    val boxSize = 48.dp
    val boxSpacing = 8.dp
    val boxSizePx = with(density) { boxSize.toPx() }
    val boxSpacingPx = with(density) { boxSpacing.toPx() }
    val totalSlots = maxOf(7, variations.size)
    
    // Track which index is being dragged and the eventual drop slot for highlighting.
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragStartIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dropTargetIndex by remember { mutableStateOf<Int?>(null) }
    
    fun handleDrag(dragAmountX: Float) {
        val startIndex = dragStartIndex ?: return
        dragOffsetX += dragAmountX
        
        val currentStartX = startIndex * (boxSizePx + boxSpacingPx) + dragOffsetX
        val targetIndex = ((currentStartX + boxSizePx / 2) / (boxSizePx + boxSpacingPx))
            .toInt()
            .coerceIn(0, totalSlots - 1)
        
        dropTargetIndex = targetIndex
    }
    
    fun endDrag() {
        val startIndex = dragStartIndex
        val targetIndex = dropTargetIndex
        if (startIndex != null && targetIndex != null && startIndex != targetIndex) {
            onReorder(startIndex, targetIndex)
        }
        draggingIndex = null
        dragStartIndex = null
        dropTargetIndex = null
        dragOffsetX = 0f
    }
    
    val dragOffsetDp = with(density) { dragOffsetX.toDp() }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Letter label
            Text(
                text = letter,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(labelWidth),
                textAlign = TextAlign.Center,
                maxLines = 2,
                color = labelColor ?: MaterialTheme.colorScheme.onSurface
            )
            
            // 7 variation boxes
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            ) {
                repeat(totalSlots) { index ->
                    val character = variations.getOrNull(index) ?: ""
                    val isEmpty = character.isEmpty()
                    val isDragging = draggingIndex == index
                    val isDropTarget = dropTargetIndex == index && draggingIndex != null
                    
                    VariationBox(
                        character = character,
                        isEmpty = isEmpty,
                        onClick = { onBoxClick(index) },
                        onDragStart = if (!isEmpty) {
                            {
                                draggingIndex = index
                                dragStartIndex = index
                                dragOffsetX = 0f
                                dropTargetIndex = index
                            }
                        } else null,
                        onDrag = if (!isEmpty) ({ deltaX -> handleDrag(deltaX) }) else null,
                        onDragEnd = if (!isEmpty) {
                            {
                                endDrag()
                            }
                        } else null,
                        isDragging = isDragging,
                        isDropTarget = isDropTarget,
                        dragOffset = if (isDragging) dragOffsetDp else 0.dp
                    )
                }
            }

            if (onAddSlot != null && onRemoveSlot != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onAddSlot,
                        enabled = variations.size < MAX_STATIC_VARIATION_SLOTS,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.variation_add_slot)
                        )
                    }
                    IconButton(
                        onClick = onRemoveSlot,
                        enabled = variations.isNotEmpty(),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.variation_remove_rightmost_slot)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VariationBox(
    character: String,
    isEmpty: Boolean,
    onClick: () -> Unit,
    onDragStart: (() -> Unit)? = null,
    onDrag: ((Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    isDragging: Boolean = false,
    isDropTarget: Boolean = false,
    dragOffset: Dp = 0.dp
) {
    Surface(
        modifier = Modifier
            .width(48.dp)
            .height(48.dp)
            .pointerInput(character, isEmpty) {
                if (!isEmpty && onDrag != null) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart?.invoke() },
                        onDragEnd = { onDragEnd?.invoke() },
                        onDragCancel = { onDragEnd?.invoke() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x)
                        }
                    )
                }
            }
            .clickable(onClick = onClick)
            .graphicsLayer {
                translationX = dragOffset.toPx()
                scaleX = if (isDragging) 1.05f else 1f
                scaleY = if (isDragging) 1.05f else 1f
            }
            .shadow(if (isDragging) 8.dp else 0.dp, RoundedCornerShape(8.dp))
            .zIndex(if (isDragging) 1f else 0f),
        shape = RoundedCornerShape(8.dp),
        color = when {
            isDragging -> MaterialTheme.colorScheme.primaryContainer
            isDropTarget -> MaterialTheme.colorScheme.secondaryContainer
            isEmpty -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        border = when {
            isEmpty -> BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
            isDragging -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            isDropTarget -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
            else -> null
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (!isEmpty) {
                Text(
                    text = character,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Shared helper for row drag/drop to keep both static and per-letter paths consistent.
private fun reorderEntries(
    entries: List<String>,
    fromIndex: Int,
    toIndex: Int
): List<String> {
    if (fromIndex !in entries.indices) return entries
    
    val mutable = entries.toMutableList()
    val movedItem = mutable.removeAt(fromIndex)
    val target = toIndex.coerceIn(0, mutable.size)
    mutable.add(target, movedItem)
    
    return mutable
}

/**
 * Applies picker changes for a row slot, trimming trailing blanks and enforcing the 7-slot cap.
 */
private fun updateVariationEntries(
    currentEntries: List<String>,
    index: Int?,
    newValue: String
): List<String> {
    val targetIndex = index ?: return currentEntries
    val updatedEntries = currentEntries.toMutableList()
    
    while (updatedEntries.size <= targetIndex) {
        updatedEntries.add("")
    }
    
    if (newValue.isEmpty()) {
        if (targetIndex < updatedEntries.size) {
            updatedEntries.removeAt(targetIndex)
        }
    } else {
        if (targetIndex < updatedEntries.size) {
            updatedEntries[targetIndex] = newValue
        } else {
            updatedEntries.add(newValue)
        }
    }
    
    while (updatedEntries.isNotEmpty() && updatedEntries.last().isEmpty()) {
        updatedEntries.removeAt(updatedEntries.lastIndex)
    }

    return updatedEntries.take(7)
}

/**
 * Applies picker changes for static bar slots. Static rows preserve explicit trailing blanks
 * because those blanks are user-added slots.
 */
private fun updateStaticVariationEntries(
    currentEntries: List<String>,
    index: Int?,
    newValue: String
): List<String> {
    val targetIndex = index ?: return currentEntries
    val updatedEntries = currentEntries.toMutableList()

    while (updatedEntries.size <= targetIndex) {
        updatedEntries.add("")
    }

    updatedEntries[targetIndex] = newValue
    return updatedEntries.take(MAX_STATIC_VARIATION_SLOTS)
}

private fun saveStaticRows(
    context: Context,
    variations: Map<String, List<String>>,
    staticVariations: List<String>,
    staticVariationsShift: List<String>,
    staticVariationsAlt: List<String>
) {
    SettingsManager.saveVariations(
        context = context,
        variations = variations,
        staticVariations = staticVariations.take(MAX_STATIC_VARIATION_SLOTS),
        staticVariationsShift = staticVariationsShift.take(MAX_STATIC_VARIATION_SLOTS),
        staticVariationsAlt = staticVariationsAlt.take(MAX_STATIC_VARIATION_SLOTS)
    )
}

private enum class StaticLayer {
    Default,
    Shift,
    Alt
}

/**
 * Load AllVariations.json file (static map with all possibilities).
 */
private fun loadAllVariationsFromJson(context: Context): Map<String, List<String>> {
    return try {
        val inputStream = context.assets.open("common/variations/AllVariations.json")
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)
        val variationsObject = jsonObject.getJSONObject("variations")
        
        val result = mutableMapOf<String, List<String>>()
        val keys = variationsObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val variationsArray = variationsObject.getJSONArray(key)
            val variationsList = mutableListOf<String>()
            for (i in 0 until variationsArray.length()) {
                variationsList.add(variationsArray.getString(i))
            }
            result[key] = variationsList
            // Also add lowercase version if uppercase
            if (key.length == 1 && key[0].isUpperCase()) {
                result[key.lowercase()] = variationsList // Use same variations for lowercase
            }
        }
        result
    } catch (e: Exception) {
        emptyMap()
    }
}

private fun resolveActiveLayoutForVariationScreen(context: Context): String {
    return try {
        val imm = context.getSystemService(InputMethodManager::class.java)
        val subtype = imm?.currentInputMethodSubtype
        val fromSubtype = subtype?.let { AdditionalSubtypeUtils.getKeyboardLayoutFromSubtype(it) }
        if (!fromSubtype.isNullOrBlank()) {
            return fromSubtype
        }

        val locale = subtype?.localeString()
        if (!locale.isNullOrBlank()) {
            return AdditionalSubtypeUtils.getLayoutForLocale(context.assets, locale, context)
        }

        SettingsManager.getKeyboardLayout(context)
    } catch (_: Exception) {
        SettingsManager.getKeyboardLayout(context)
    }
}
