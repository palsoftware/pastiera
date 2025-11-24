package it.palsoftware.pastiera

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val staticVariations = remember {
        VariationRepository.loadStaticVariations(context.assets, context)
    }
    
    // Generate alphabet list (A-Z, then a-z)
    val alphabet = remember {
        ('A'..'Z').toList() + ('a'..'z').toList()
    }
    
    // State for picker dialog
    var showPickerDialog by remember { mutableStateOf(false) }
    var selectedLetter by remember { mutableStateOf<String?>(null) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    
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
            
            // Alphabet grid
            alphabet.forEach { letter ->
                val letterStr = letter.toString()
                val letterVariations = variations[letterStr] ?: emptyList()
                
                VariationRow(
                    letter = letterStr,
                    variations = letterVariations,
                    onBoxClick = { index ->
                        selectedLetter = letterStr
                        selectedIndex = index
                        showPickerDialog = true
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Variation picker dialog
    if (showPickerDialog && selectedLetter != null) {
        val letter = selectedLetter!!
        // Get available variations for this letter (use uppercase if lowercase not found)
        val availableVariations = allVariations[letter] 
            ?: allVariations[letter.uppercase()]
            ?: emptyList()
        
        VariationPickerDialog(
            letter = letter,
            availableVariations = availableVariations,
            onVariationSelected = { character ->
                // Update variations map
                val updatedVariations = variations.toMutableMap()
                val currentVariations = (updatedVariations[letter] ?: emptyList()).toMutableList()
                
                // Ensure list has at least selectedIndex + 1 elements
                while (currentVariations.size <= (selectedIndex ?: 0)) {
                    currentVariations.add("")
                }
                
                // Set the selected character at the selected index
                if (selectedIndex != null) {
                    if (character.isEmpty()) {
                        // Remove the character if empty
                        if (selectedIndex!! < currentVariations.size) {
                            currentVariations.removeAt(selectedIndex!!)
                        }
                    } else {
                        // Set or update the character
                        if (selectedIndex!! < currentVariations.size) {
                            currentVariations[selectedIndex!!] = character
                        } else {
                            currentVariations.add(character)
                        }
                    }
                }
                
                // Remove trailing empty strings
                while (currentVariations.isNotEmpty() && currentVariations.last().isEmpty()) {
                    currentVariations.removeLast()
                }
                
                // Limit to 7 variations
                val trimmedVariations = currentVariations.take(7)
                
                if (trimmedVariations.isEmpty()) {
                    updatedVariations.remove(letter)
                } else {
                    updatedVariations[letter] = trimmedVariations
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

@Composable
private fun VariationRow(
    letter: String,
    variations: List<String>,
    onBoxClick: (Int) -> Unit
) {
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
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center
            )
            
            // 7 variation boxes
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                repeat(7) { index ->
                    VariationBox(
                        character = variations.getOrNull(index) ?: "",
                        isEmpty = index >= variations.size,
                        onClick = { onBoxClick(index) }
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
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(48.dp)
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isEmpty) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = if (isEmpty) {
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        } else {
            null
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

