package it.palsoftware.pastiera

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import it.palsoftware.pastiera.data.layout.LayoutFileStore
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.R
import kotlinx.coroutines.launch
import java.util.Locale
import android.content.res.AssetManager
import org.json.JSONObject
import java.io.File
import java.io.InputStream

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

    // Layouts enabled for cycling (space long-press)
    var enabledLayouts by remember {
        mutableStateOf(SettingsManager.getKeyboardLayoutList(context).toMutableSet())
    }
    
    // Refresh trigger for custom layouts
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // Get all keyboard layouts (assets + custom, excluding qwerty as it's the default)
    val allLayouts = remember(refreshTrigger) {
        LayoutMappingRepository.getAvailableLayouts(context.assets, context)
            .filter { it != "qwerty" }
            .sorted()
    }
    
    // Snackbar host state for showing messages
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var pendingLayoutSave by remember { mutableStateOf<PendingLayoutSave?>(null) }
    var previewLayout by remember { mutableStateOf<String?>(null) }
    var layoutToDelete by remember { mutableStateOf<String?>(null) }
    
    // File picker launcher for importing layouts
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    // Read JSON as string
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    
                    // Validate JSON by creating a temp file and loading it
                    val tempFile = File.createTempFile("temp_validate", ".json", context.cacheDir)
                    try {
                        tempFile.writeText(jsonString)
                        val layout = LayoutFileStore.loadLayoutFromFile(tempFile)
                        
                        if (layout != null) {
                            // Generate a unique name based on timestamp
                            val layoutName = "custom_${System.currentTimeMillis()}"
                            val layoutFile = LayoutFileStore.getLayoutFile(context, layoutName)
                            
                            // Copy the validated JSON directly to the layouts directory
                            layoutFile.writeText(jsonString)
                            
                            refreshTrigger++
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.layout_imported_successfully))
                            }
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.layout_invalid_file))
                            }
                        }
                    } finally {
                        tempFile.delete()
                    }
                }
            } catch (e: Exception) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.layout_import_error, e.message ?: ""))
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
                snackbarHostState.showSnackbar(context.getString(R.string.layout_save_canceled))
            }
            return@rememberLauncherForActivityResult
        }

        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(pending.json.toByteArray())
            }
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.layout_saved_successfully))
            }
        } catch (e: Exception) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.layout_save_error, e.message ?: ""))
            }
        }
    }

    if (previewLayout != null) {
        KeyboardLayoutViewerScreen(
            layoutName = previewLayout!!,
            modifier = modifier,
            onBack = { previewLayout = null }
        )
        return
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
                            contentDescription = stringResource(R.string.layout_save_content_description)
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
                            contentDescription = stringResource(R.string.layout_import_content_description)
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
                    .verticalScroll(rememberScrollState())
            ) {
                // Description
                Text(
                    text = stringResource(R.string.keyboard_layout_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )
                
                // Keyboard Layout Editor Link
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://pastierakeyedit.vercel.app/"))
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
                            imageVector = Icons.Filled.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Keyboard Layout Editor",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // No Conversion (QWERTY - default, passes keycodes as-is)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clickable {
                            selectedLayout = "qwerty"
                            SettingsManager.setKeyboardLayout(context, "qwerty")
                            if (!enabledLayouts.contains("qwerty")) {
                                enabledLayouts = (enabledLayouts + "qwerty").toMutableSet()
                                SettingsManager.setKeyboardLayoutList(context, enabledLayouts.toList())
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { previewLayout = "qwerty" }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Visibility,
                                    contentDescription = stringResource(R.string.keyboard_layout_viewer_open),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Checkbox(
                                checked = enabledLayouts.contains("qwerty"),
                                onCheckedChange = { enabled ->
                                    enabledLayouts = if (enabled) {
                                        (enabledLayouts + "qwerty").toMutableSet()
                                    } else {
                                        (enabledLayouts - "qwerty").toMutableSet()
                                    }
                                    SettingsManager.setKeyboardLayoutList(context, enabledLayouts.toList())
                                }
                            )
                        RadioButton(
                            selected = selectedLayout == "qwerty",
                            onClick = {
                                selectedLayout = "qwerty"
                                SettingsManager.setKeyboardLayout(context, "qwerty")
                                if (!enabledLayouts.contains("qwerty")) {
                                    enabledLayouts = (enabledLayouts + "qwerty").toMutableSet()
                                    SettingsManager.setKeyboardLayoutList(context, enabledLayouts.toList())
                                }
                            }
                        )
                        }
                    }
                }
                
                // All layouts (assets + custom, unified list)
                allLayouts.forEach { layout ->
                    val metadata = LayoutFileStore.getLayoutMetadataFromAssets(
                        context.assets,
                        layout
                    ) ?: LayoutFileStore.getLayoutMetadata(context, layout)
                    
                    val hasMultiTap = hasLayoutMultiTap(context.assets, context, layout)
                    val isCustomLayout = LayoutFileStore.layoutExists(context, layout)
                    val canDelete = layout != "qwerty" && isCustomLayout
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Header row with layout info
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = metadata?.name ?: layout.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        if (hasMultiTap) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = MaterialTheme.shapes.small,
                                                modifier = Modifier.height(18.dp)
                                            ) {
                                                Text(
                                                    text = "multitap",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
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
                                        if (!enabledLayouts.contains(layout)) {
                                            enabledLayouts = (enabledLayouts + layout).toMutableSet()
                                            SettingsManager.setKeyboardLayoutList(context, enabledLayouts.toList())
                                        }
                                    }
                                )
                            }
                            
                            // Actions row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // View mapping button
                                OutlinedButton(
                                    onClick = { previewLayout = layout },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.layout_view_mapping),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                
                                // Delete button (only for custom layouts)
                                if (canDelete) {
                                    OutlinedButton(
                                        onClick = { layoutToDelete = layout },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.layout_delete),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                                
                                // Enable for cycling checkbox with label
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            val enabled = !enabledLayouts.contains(layout)
                                            enabledLayouts = if (enabled) {
                                                (enabledLayouts + layout).toMutableSet()
                                            } else {
                                                (enabledLayouts - layout).toMutableSet()
                                            }
                                            SettingsManager.setKeyboardLayoutList(context, enabledLayouts.toList())
                                        }
                                ) {
                                    Checkbox(
                                        checked = enabledLayouts.contains(layout),
                                        onCheckedChange = { enabled ->
                                            enabledLayouts = if (enabled) {
                                                (enabledLayouts + layout).toMutableSet()
                                            } else {
                                                (enabledLayouts - layout).toMutableSet()
                                            }
                                            SettingsManager.setKeyboardLayoutList(context, enabledLayouts.toList())
                                        }
                                    )
                                    Text(
                                        text = stringResource(R.string.layout_enable_for_cycling),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    
    // Delete confirmation dialog
    layoutToDelete?.let { layoutName ->
        val metadata = LayoutFileStore.getLayoutMetadata(context, layoutName)
            ?: LayoutFileStore.getLayoutMetadataFromAssets(context.assets, layoutName)
        val displayName = metadata?.name ?: layoutName.replaceFirstChar { it.uppercase() }
        
        AlertDialog(
            onDismissRequest = { layoutToDelete = null },
            title = {
                Text(stringResource(R.string.layout_delete_confirmation_title))
            },
            text = {
                Text(stringResource(R.string.layout_delete_confirmation_message, displayName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val success = LayoutFileStore.deleteLayout(context, layoutName)
                        layoutToDelete = null
                        
                        if (success) {
                            // If deleted layout was selected, switch to qwerty
                            if (selectedLayout == layoutName) {
                                selectedLayout = "qwerty"
                                SettingsManager.setKeyboardLayout(context, "qwerty")
                            }
                            
                            // Remove from enabled layouts if present
                            if (enabledLayouts.contains(layoutName)) {
                                enabledLayouts = (enabledLayouts - layoutName).toMutableSet()
                                SettingsManager.setKeyboardLayoutList(context, enabledLayouts.toList())
                            }
                            
                            refreshTrigger++
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.layout_delete_success))
                            }
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.layout_delete_failed))
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { layoutToDelete = null }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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

/**
 * Checks if a layout has multiTap enabled by reading the JSON file.
 * Returns true if at least one mapping has multiTapEnabled set to true.
 */
private fun hasLayoutMultiTap(assets: AssetManager, context: Context, layoutName: String): Boolean {
    return try {
        // Try custom layout first
        val customFile = LayoutFileStore.getLayoutFile(context, layoutName)
        if (customFile.exists() && customFile.canRead()) {
            val jsonString = customFile.readText()
            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.optJSONObject("mappings") ?: return false
            
            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val mappingObj = mappingsObject.optJSONObject(keyName) ?: continue
                if (mappingObj.optBoolean("multiTapEnabled", false)) {
                    return true
                }
            }
            false
        } else {
            // Fallback to assets
            val filePath = "common/layouts/$layoutName.json"
            val inputStream: InputStream = assets.open(filePath)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.optJSONObject("mappings") ?: return false
            
            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val mappingObj = mappingsObject.optJSONObject(keyName) ?: continue
                if (mappingObj.optBoolean("multiTapEnabled", false)) {
                    return true
                }
            }
            false
        }
    } catch (e: Exception) {
        false
    }
}
