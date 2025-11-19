package it.palsoftware.pastiera

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import it.palsoftware.pastiera.data.layout.LayoutFileStore
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.R
import kotlinx.coroutines.launch
import java.util.Locale

private data class PendingLayoutSave(
    val fileName: String,
    val json: String
)

/**
 * Settings screen for keyboard layout selection.
 */
@Composable
fun KeyboardLayoutSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // Load saved keyboard layout value
    var selectedLayout by remember { 
        mutableStateOf(SettingsManager.getKeyboardLayout(context))
    }
    
    // Refresh trigger for custom layouts
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // Get available keyboard layouts from assets and custom files (excluding qwerty as it's the default)
    val availableLayouts = remember(refreshTrigger) {
        LayoutMappingRepository.getAvailableLayouts(context.assets, context)
            .filter { it != "qwerty" && !LayoutFileStore.layoutExists(context, it) }
    }
    
    // Snackbar host state for showing messages
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var pendingLayoutSave by remember { mutableStateOf<PendingLayoutSave?>(null) }
    
    // File picker launcher for importing layouts
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val layout = LayoutFileStore.loadLayoutFromStream(inputStream)
                    if (layout != null) {
                        // Generate a unique name based on timestamp
                        val layoutName = "custom_${System.currentTimeMillis()}"
                        val success = LayoutFileStore.saveLayout(
                            context = context,
                            layoutName = layoutName,
                            layout = layout,
                            name = "Imported Layout",
                            description = "Imported from file"
                        )
                        if (success) {
                            refreshTrigger++
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Layout imported successfully")
                            }
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Failed to import layout")
                            }
                        }
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Invalid layout file")
                        }
                    }
                }
            } catch (e: Exception) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Error importing layout: ${e.message}")
                }
            }
        }
    }
    
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val pending = pendingLayoutSave
        pendingLayoutSave = null
        if (pending == null) {
            return@rememberLauncherForActivityResult
        }

        if (uri == null) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Save canceled")
            }
            return@rememberLauncherForActivityResult
        }

        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(pending.json.toByteArray())
            }
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Layout saved successfully")
            }
        } catch (e: Exception) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Error saving layout: ${e.message}")
            }
        }
    }
    
    Scaffold(
        topBar = {
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
                        text = stringResource(R.string.keyboard_layout_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f)
                    )
                    // Save button
                    IconButton(
                        onClick = {
                            val currentLayout = LayoutMappingRepository.getLayout()
                            if (currentLayout.isNotEmpty()) {
                                val metadata = LayoutFileStore.getLayoutMetadataFromAssets(
                                    context.assets,
                                    selectedLayout
                                ) ?: LayoutFileStore.getLayoutMetadata(context, selectedLayout)

                                val displayName = metadata?.name ?: selectedLayout
                                val description = metadata?.description

                                val jsonString = LayoutFileStore.buildLayoutJsonString(
                                    layoutName = selectedLayout,
                                    layout = currentLayout,
                                    name = displayName,
                                    description = description
                                )

                                val sanitizedName = displayName
                                    .lowercase(Locale.ROOT)
                                    .replace("\\s+".toRegex(), "_")
                                val suggestedFileName = "${sanitizedName}_${System.currentTimeMillis()}.json"

                                pendingLayoutSave = PendingLayoutSave(
                                    fileName = suggestedFileName,
                                    json = jsonString
                                )
                                createDocumentLauncher.launch(suggestedFileName)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = "Save layout"
                        )
                    }
                    // Import button
                    IconButton(
                        onClick = {
                            filePickerLauncher.launch("application/json")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Import layout"
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        AnimatedContent(
            targetState = Unit,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "keyboard_layout_animation"
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(paddingValues)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .verticalScroll(rememberScrollState())
            ) {
                // Description
                Text(
                    text = stringResource(R.string.keyboard_layout_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )
                
                // No Conversion (QWERTY - default, passes keycodes as-is)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clickable {
                            selectedLayout = "qwerty"
                            SettingsManager.setKeyboardLayout(context, "qwerty")
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
                            imageVector = Icons.Filled.Keyboard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.keyboard_layout_no_conversion),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                text = stringResource(R.string.keyboard_layout_no_conversion_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                        RadioButton(
                            selected = selectedLayout == "qwerty",
                            onClick = {
                                selectedLayout = "qwerty"
                                SettingsManager.setKeyboardLayout(context, "qwerty")
                            }
                        )
                    }
                }
                
                // Layout Conversions Section
                Text(
                    text = stringResource(R.string.keyboard_layout_conversions_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                Text(
                    text = stringResource(R.string.keyboard_layout_conversions_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                
                // Available layout conversions
                availableLayouts.forEach { layout ->
                    val metadata = LayoutFileStore.getLayoutMetadataFromAssets(
                        context.assets,
                        layout
                    ) ?: LayoutFileStore.getLayoutMetadata(context, layout)
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clickable {
                                selectedLayout = layout
                                SettingsManager.setKeyboardLayout(context, layout)
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
                                imageVector = Icons.Filled.Keyboard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = metadata?.name ?: layout.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                Text(
                                    text = metadata?.description ?: getLayoutDescription(context, layout),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        RadioButton(
                            selected = selectedLayout == layout,
                            onClick = {
                                selectedLayout = layout
                                SettingsManager.setKeyboardLayout(context, layout)
                            }
                        )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Gets the description for a layout from its JSON file.
 * Tries custom files first, then falls back to assets.
 */
private fun getLayoutDescription(context: Context, layoutName: String): String {
    // Try custom layout first
    val customMetadata = LayoutFileStore.getLayoutMetadata(context, layoutName)
    if (customMetadata != null) {
        return customMetadata.description
    }
    
    // Fallback to assets
    val assetsMetadata = LayoutFileStore.getLayoutMetadataFromAssets(
        context.assets,
        layoutName
    )
    return assetsMetadata?.description ?: ""
}

