package it.palsoftware.pastiera

import android.content.Context
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import it.palsoftware.pastiera.data.layout.BundledLayoutAssets
import it.palsoftware.pastiera.data.layout.LayoutFileStore
import it.palsoftware.pastiera.data.layout.LayoutFileStore.LayoutImportError
import it.palsoftware.pastiera.data.layout.LayoutFileStore.LayoutImportResult
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.layout.OnlineLayoutsActivity
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils
import it.palsoftware.pastiera.R
import kotlinx.coroutines.launch
import android.content.res.AssetManager
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Settings screen for keyboard layout selection for a specific locale.
 * @param locale The locale for which to select the layout (required).
 * @param onLayoutSelected Callback when a layout is selected (locale, layout).
 */
@Composable
fun KeyboardLayoutSettingsScreen(
    modifier: Modifier = Modifier,
    locale: String,
    initialLayout: String? = null,
    pickerMode: Boolean = false,
    onBack: () -> Unit,
    onLayoutSelected: (String, String) -> Unit
) {
    val context = LocalContext.current

    var automaticLayoutMode by remember {
        mutableStateOf(SettingsManager.isKeyboardLayoutAutoByLocale(context))
    }
    var selectedLayout by remember(locale, automaticLayoutMode, initialLayout, pickerMode) {
        mutableStateOf(
            if (pickerMode && initialLayout != null) {
                initialLayout
            } else if (automaticLayoutMode) {
                AdditionalSubtypeUtils.getLayoutForLocale(context.assets, locale, context)
            } else {
                SettingsManager.getKeyboardLayout(context)
            }
        )
    }

    // Refresh trigger for custom layouts
    var refreshTrigger by remember { mutableStateOf(0) }
    var showAddMenu by remember { mutableStateOf(false) }

    // Refresh list when returning from cloud screen (or any other screen)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Get all keyboard layouts (assets + custom, excluding qwerty as it's the default)
    val allLayouts = remember(refreshTrigger, locale) {
        LayoutMappingRepository.getAvailableLayouts(context.assets, context)
            .filter { it != "qwerty" }
            .sortedWith(
                compareBy<String> { layout ->
                    if (locale.startsWith("de", ignoreCase = true)) {
                        when (layout) {
                            "qwertz" -> 0
                            "german_multitap_qwertz" -> 1
                            else -> 2
                        }
                    } else {
                        0
                    }
                }.thenBy { it }
            )
    }

    // Snackbar host state for showing messages
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val previewLayout = settingsChild(context, "layout_preview")
    var layoutToDelete by remember { mutableStateOf<String?>(null) }

    // Launcher per importare layout JSON via SAF
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            try {
                val jsonString = uri?.let { selectedUri ->
                    context.contentResolver.openInputStream(selectedUri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    }
                }
                when (val importResult = importKeyboardLayoutDocument(context, jsonString)) {
                    is LayoutImportResult.Success -> {
                        refreshTrigger++            // ricarica lista layout
                        selectedLayout = importResult.layoutName // seleziona l'importato
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.layout_imported_successfully)
                            )
                        }
                    }
                    is LayoutImportResult.Failure -> {
                        val message = context.getString(importResult.error.messageResource())
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(message)
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

    fun launchLocalImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        importLauncher.launch(intent)
    }

    if (previewLayout != null) {
        KeyboardLayoutViewerScreen(
            layoutName = previewLayout!!,
            modifier = modifier,
            onBack = { context.settingsActivity().finish() }
        )
        return
    }

    fun navigateBack() {
        if (pickerMode) {
            onLayoutSelected(locale, selectedLayout)
        }
        onBack()
    }

    // Handle system back button
    LaunchedEffect(pickerMode, locale, selectedLayout) {
        if (pickerMode) onLayoutSelected(locale, selectedLayout)
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = "${stringResource(R.string.keyboard_layout_title)} - ${getLocaleDisplayName(context, locale)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f)
                    )
                    // Import layout (JSON) button with local/cloud options
                    Box {
                        IconButton(onClick = { showAddMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.layout_import_content_description)
                            )
                        }
                        DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = { showAddMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.layout_import_from_file)) },
                                onClick = {
                                    showAddMenu = false
                                    launchLocalImport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.layout_download_from_cloud)) },
                                onClick = {
                                    showAddMenu = false
                                    val intent = Intent(context, OnlineLayoutsActivity::class.java)
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                    if (!pickerMode) {
                        IconButton(
                            onClick = {
                                SettingsManager.setKeyboardLayoutAutoByLocale(context, automaticLayoutMode)
                                if (!automaticLayoutMode) {
                                    SettingsManager.setKeyboardLayout(context, selectedLayout)
                                }
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

                // Online Layout Editor link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://pastierakeyedit.vercel.app/"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Handle error silently or show snackbar
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.keyboard_layout_editor_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // No Conversion (QWERTY - default, passes keycodes as-is)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
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
                                onClick = { openSettingsChild(context, "layout_preview", "qwerty") }
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
                            .padding(vertical = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                if (canDelete) {
                                    IconButton(
                                        onClick = { layoutToDelete = layout }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.layout_delete_content_description),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { openSettingsChild(context, "layout_preview", layout) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Visibility,
                                        contentDescription = stringResource(R.string.keyboard_layout_viewer_open),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                RadioButton(
                                    selected = selectedLayout == layout,
                                    onClick = {
                                        selectedLayout = layout
                                    }
                                )
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
                                if (!pickerMode && !automaticLayoutMode) {
                                    SettingsManager.setKeyboardLayout(context, "qwerty")
                                }
                                if (!pickerMode) {
                                    onLayoutSelected(locale, "qwerty")
                                }
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

private fun LayoutImportError.messageResource(): Int = when (this) {
    LayoutImportError.MALFORMED_JSON -> R.string.layout_import_malformed_json
    LayoutImportError.MISSING_MAPPINGS -> R.string.layout_import_missing_mappings
    LayoutImportError.MAPPINGS_NOT_OBJECT -> R.string.layout_import_mappings_not_object
    LayoutImportError.EMPTY_MAPPINGS -> R.string.layout_import_empty_mappings
    LayoutImportError.NO_SUPPORTED_MAPPINGS -> R.string.layout_import_no_supported_mappings
    LayoutImportError.INVALID_MAPPING -> R.string.layout_import_invalid_mapping
    LayoutImportError.INVALID_NAME -> R.string.layout_import_invalid_name
    LayoutImportError.NAME_CONFLICT -> R.string.layout_import_name_conflict
    LayoutImportError.WRITE_FAILED -> R.string.layout_import_write_failed
}

internal fun importKeyboardLayoutDocument(
    context: Context,
    jsonString: String?
): LayoutImportResult {
    if (jsonString.isNullOrBlank()) {
        return LayoutImportResult.Failure(LayoutImportError.MALFORMED_JSON)
    }
    val layoutName = runCatching {
        val obj = JSONObject(jsonString)
        obj.optString("name").takeIf { it.isNotBlank() }
    }.getOrNull() ?: "imported_${System.currentTimeMillis()}"
    return LayoutFileStore.saveLayoutFromJson(context, layoutName, jsonString)
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
            val inputStream = BundledLayoutAssets.openLayout(assets, layoutName) ?: return false
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
