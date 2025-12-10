package it.palsoftware.pastiera

import android.content.Context
import android.app.Activity
import android.content.Intent
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import it.palsoftware.pastiera.data.layout.LayoutFileStore
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils
import it.palsoftware.pastiera.R
import kotlinx.coroutines.launch
import java.util.Locale
import android.content.res.AssetManager
import org.json.JSONObject
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Settings screen for keyboard layout selection for a specific locale.
 * @param locale The locale for which to select the layout (required).
 * @param onLayoutSelected Callback when a layout is selected (locale, layout).
 */
@Composable
fun KeyboardLayoutSettingsScreen(
    modifier: Modifier = Modifier,
    locale: String,
    onBack: () -> Unit,
    onLayoutSelected: (String, String) -> Unit
) {
    val context = LocalContext.current
    
    // Get layout from locale-layout mapping
    var selectedLayout by remember(locale) { 
        mutableStateOf(
            AdditionalSubtypeUtils.getLayoutForLocale(context.assets, locale, context)
        )
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
    var previewLayout by remember { mutableStateOf<String?>(null) }
    var layoutToDelete by remember { mutableStateOf<String?>(null) }

    // Launcher per importare layout JSON via SAF
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                try {
                    val jsonString = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    }
                    if (!jsonString.isNullOrBlank()) {
                        val layoutName = runCatching {
                            val obj = JSONObject(jsonString)
                            obj.optString("name").takeIf { it.isNotBlank() }
                        }.getOrNull() ?: "imported_${System.currentTimeMillis()}"
                        
                        val saved = LayoutFileStore.saveLayoutFromJson(context, layoutName, jsonString)
                        if (saved) {
                            refreshTrigger++            // ricarica lista layout
                            selectedLayout = layoutName // seleziona l'importato
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.layout_imported_successfully)
                                )
                            }
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.layout_import_failed)
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.layout_import_error, e.message ?: "")
                        )
                    }
                }
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
                        text = "${stringResource(R.string.keyboard_layout_title)} - ${getLocaleDisplayNameForTitle(locale)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f)
                    )
                    // Import layout (JSON) button
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/json"
                            }
                            importLauncher.launch(intent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.layout_import_content_description)
                        )
                    }
                    // Save button
                    IconButton(
                        onClick = {
                            // Save the layout selection and go back
                            onLayoutSelected(locale, selectedLayout)
                            onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = stringResource(R.string.layout_save_content_description)
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
                
                // No Conversion (QWERTY - default, passes keycodes as-is)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clickable {
                            selectedLayout = "qwerty"
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
                        RadioButton(
                            selected = selectedLayout == "qwerty",
                            onClick = {
                                selectedLayout = "qwerty"
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
                                                    text = stringResource(R.string.keyboard_layout_multitap_badge),
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
                                onLayoutSelected(locale, "qwerty")
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

/**
 * Gets display name for a locale (for title display).
 */
private fun getLocaleDisplayNameForTitle(locale: String): String {
    return try {
        val parts = locale.split("_")
        val lang = parts[0]
        val country = if (parts.size > 1) parts[1] else ""
        val localeObj = if (country.isNotEmpty()) {
            Locale(lang, country)
        } else {
            Locale(lang)
        }
        localeObj.getDisplayName(Locale.ENGLISH)
    } catch (e: Exception) {
        locale
    }
}
