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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import android.content.Intent
import android.net.Uri
import it.palsoftware.pastiera.R

/**
 * Schermata delle impostazioni dell'app.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSymCustomizationClick: () -> Unit = {}
) {
    val context = LocalContext.current
    
    // Carica il valore salvato del long press threshold
    var longPressThreshold by remember { 
        mutableStateOf(SettingsManager.getLongPressThreshold(context))
    }
    
    // Carica il valore salvato dell'auto-maiuscola
    var autoCapitalizeFirstLetter by remember {
        mutableStateOf(SettingsManager.getAutoCapitalizeFirstLetter(context))
    }
    
    // Carica il valore salvato dell'auto-correzione
    var autoCorrectEnabled by remember {
        mutableStateOf(SettingsManager.getAutoCorrectEnabled(context))
    }
    
    // Stato per la navigazione alle impostazioni auto-correzione
    var showAutoCorrectSettings by remember { mutableStateOf(false) }
    var showAutoCorrectEdit by remember { mutableStateOf<String?>(null) }
    
    // Gestisci il back button di sistema
    BackHandler {
        when {
            showAutoCorrectEdit != null -> {
                showAutoCorrectEdit = null
            }
            showAutoCorrectSettings -> {
                showAutoCorrectSettings = false
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
                .verticalScroll(rememberScrollState())
        ) {
        // Header moderno
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
        
        // Lista impostazioni moderna
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            // Long Press Threshold
            Surface(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.long_press_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${longPressThreshold}ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${SettingsManager.getMinLongPressThreshold()}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${SettingsManager.getMaxLongPressThreshold()}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            HorizontalDivider()
            
            // Auto Capitalize
            Surface(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auto_capitalize_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
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
            
            HorizontalDivider()
            
            // Auto-Correction
            Surface(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.auto_correct_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
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
            
            HorizontalDivider()
            
            // Auto-Correction Languages (solo se auto-correzione è abilitata)
            if (autoCorrectEnabled) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAutoCorrectSettings = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.auto_correct_languages_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                HorizontalDivider()
            }
            
            // SYM Customization
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSymCustomizationClick() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Keyboard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sym_customization_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            HorizontalDivider()
            
            // About Section
            Surface(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.about_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // GitHub Link
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/palsoftware/pastiera/"))
                                context.startActivity(intent)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.about_github),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "https://github.com/palsoftware/pastiera/",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.about_build_info),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = BuildInfo.getBuildInfoString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

