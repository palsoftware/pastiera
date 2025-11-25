package it.palsoftware.pastiera

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.data.variation.VariationRepository
import org.json.JSONObject

/**
 * Screen for customizing letter variations.
 */
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
    var variations by remember {
        val repoVariations = VariationRepository.loadVariations(context.assets, context)
        mutableStateOf(repoVariations.mapKeys { it.key.toString() })
    }
    
    // Load static variations to preserve them when saving
    var staticVariations by remember {
        mutableStateOf(VariationRepository.loadStaticVariations(context.assets, context).take(7))
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
    
    // State for reset confirmation dialog
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    
    // State for static variation bar mode
    var staticVariationBarMode by remember {
        mutableStateOf(SettingsManager.isStaticVariationBarModeEnabled(context))
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
            // Static Variation Bar Mode toggle
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
                            text = stringResource(R.string.static_variation_bar_mode_title),
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
                    }
                    Switch(
                        checked = staticVariationBarMode,
                        onCheckedChange = { enabled ->
                            staticVariationBarMode = enabled
                            SettingsManager.setStaticVariationBarModeEnabled(context, enabled)
                        }
                    )
                }
            }
            
            HorizontalDivider()
            
            val staticRowLabel = stringResource(R.string.static_variation_bar_mode_title)
            
            // Static variations row
            VariationRow(
                letter = staticRowLabel,
                variations = staticVariations,
                labelWidth = 64.dp,
                onBoxClick = { index ->
                    staticInputIndex = index
                    staticInputValue = limitInputToSingleCodePoint(staticVariations.getOrNull(index) ?: "")
                    showStaticInputDialog = true
                },
                onReorder = { fromIndex, toIndex ->
                    val reordered = reorderEntries(staticVariations, fromIndex, toIndex)
                    if (reordered != staticVariations) {
                        staticVariations = reordered
                        SettingsManager.saveVariations(context, variations, reordered)
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
                                SettingsManager.saveVariations(context, updatedMap, staticVariations)
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
                SettingsManager.saveVariations(context, variations, staticVariations)
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
            },
            title = {
                Text(stringResource(R.string.static_variation_bar_mode_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = staticInputValue,
                        onValueChange = { newValue ->
                            staticInputValue = limitInputToSingleCodePoint(newValue)
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
                            val trimmedStatic = updateVariationEntries(
                                currentEntries = staticVariations,
                                index = index,
                                newValue = staticInputValue
                            )
                            staticVariations = trimmedStatic
                            SettingsManager.saveVariations(context, variations, trimmedStatic)
                        }
                        
                        showStaticInputDialog = false
                        staticInputIndex = null
                        staticInputValue = ""
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
                        val repoVariations = VariationRepository.loadVariations(context.assets, context)
                        variations = repoVariations.mapKeys { it.key.toString() }
                        staticVariations = VariationRepository.loadStaticVariations(context.assets, context).take(7)
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
    labelWidth: Dp = 40.dp
) {
    val density = LocalDensity.current
    val boxSize = 48.dp
    val boxSpacing = 8.dp
    val boxSizePx = with(density) { boxSize.toPx() }
    val boxSpacingPx = with(density) { boxSpacing.toPx() }
    val totalSlots = 7
    
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
                maxLines = 2
            )
            
            // 7 variation boxes
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                repeat(7) { index ->
                    val character = variations.getOrNull(index) ?: ""
                    val isEmpty = index >= variations.size
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
                            change.consumePositionChange()
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

// Static inputs accept at most one code point (emoji included); trim safely instead of char-by-char.
private fun limitInputToSingleCodePoint(value: String): String {
    if (value.isEmpty()) return ""
    return try {
        val endIndex = value.offsetByCodePoints(0, 1)
        value.substring(0, endIndex)
    } catch (_: Exception) {
        value
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
    
    return mutable.take(7)
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
        updatedEntries.removeLast()
    }
    
    return updatedEntries.take(7)
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
