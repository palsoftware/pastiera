package it.palsoftware.pastiera

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.activity.compose.BackHandler
import it.palsoftware.pastiera.R

/**
 * Keyboard & Timing settings screen.
 */
@Composable
fun KeyboardTimingSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    var longPressThreshold by remember { 
        mutableStateOf(SettingsManager.getLongPressThreshold(context))
    }
    
    var longPressModifier by remember { 
        mutableStateOf(SettingsManager.getLongPressModifier(context))
    }
    
    
    // Handle system back button
    BackHandler { onBack() }
    
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
                        text = stringResource(R.string.settings_category_keyboard_timing),
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
                            text = stringResource(
                                R.string.keyboard_timing_long_press_value,
                                longPressThreshold
                            ),
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
        
            // Long Press Modifier (Alt/Shift/Variations/Sym) - Dropdown Style
            var showModifierMenu by remember { mutableStateOf(false) }
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable { showModifierMenu = true }
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
                            text = when (longPressModifier) {
                                "alt" -> stringResource(R.string.long_press_modifier_alt)
                                "shift" -> stringResource(R.string.long_press_modifier_shift)
                                "variations" -> stringResource(R.string.long_press_modifier_variations)
                                "sym" -> stringResource(R.string.long_press_modifier_sym)
                                else -> stringResource(R.string.long_press_modifier_alt)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Dropdown Menu
                DropdownMenu(
                    expanded = showModifierMenu,
                    onDismissRequest = { showModifierMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.long_press_modifier_alt)) },
                        onClick = {
                            longPressModifier = "alt"
                            SettingsManager.setLongPressModifier(context, "alt")
                            showModifierMenu = false
                        },
                        leadingIcon = {
                            if (longPressModifier == "alt") {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.long_press_modifier_shift)) },
                        onClick = {
                            longPressModifier = "shift"
                            SettingsManager.setLongPressModifier(context, "shift")
                            showModifierMenu = false
                        },
                        leadingIcon = {
                            if (longPressModifier == "shift") {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.long_press_modifier_variations)) },
                        onClick = {
                            longPressModifier = "variations"
                            SettingsManager.setLongPressModifier(context, "variations")
                            showModifierMenu = false
                        },
                        leadingIcon = {
                            if (longPressModifier == "variations") {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.long_press_modifier_sym)) },
                        onClick = {
                            longPressModifier = "sym"
                            SettingsManager.setLongPressModifier(context, "sym")
                            showModifierMenu = false
                        },
                        leadingIcon = {
                            if (longPressModifier == "sym") {
                                Icon(Icons.Default.Check, contentDescription = null)
                            }
                        }
                    )
                }
            }
        
        }
    }
}
