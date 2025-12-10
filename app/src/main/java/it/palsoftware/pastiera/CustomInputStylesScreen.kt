package it.palsoftware.pastiera

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.activity.compose.BackHandler
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.InstalledDictionariesActivity
import java.util.Locale
import android.content.res.AssetManager

/**
 * Data class representing a custom input style entry.
 */
private data class CustomInputStyle(
    val locale: String,
    val layout: String,
    val displayName: String,
    val isSystemLocale: Boolean = false  // True if this is a system-enabled locale
)

/**
 * Settings screen for managing custom input styles (additional subtypes).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomInputStylesScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // Load custom input styles
    var inputStyles by remember {
        mutableStateOf(loadCustomInputStyles(context))
    }
    
    // Dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteConfirmStyle by remember { mutableStateOf<CustomInputStyle?>(null) }
    var editStyle by remember { mutableStateOf<CustomInputStyle?>(null) }
    var showLayoutSettingsForLocale by remember { mutableStateOf<String?>(null) }
    var wasDialogOpenBeforeLayoutSettings by remember { mutableStateOf(false) }
    // Preserve dialog selections across layout screen
    var lastDialogLocale by remember { mutableStateOf<String?>(null) }
    var lastDialogLayout by remember { mutableStateOf<String?>(null) }
    
    // Snackbar host state
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // Handle system back button
    BackHandler { onBack() }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                        text = stringResource(R.string.custom_input_styles_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f)
                    )
                    IconButton(
                        onClick = {
                            context.startActivity(
                                Intent(context, InstalledDictionariesActivity::class.java)
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = stringResource(R.string.custom_input_styles_installed_dictionaries)
                        )
                    }
                    IconButton(
                        onClick = { showAddDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.custom_input_styles_add)
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // List of custom input styles
            if (inputStyles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.custom_input_styles_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(inputStyles, key = { "${it.locale}:${it.layout}" }) { style ->
                        CustomInputStyleItem(
                            style = style,
                            onClick = {
                                // Allow editing both custom styles and system locales (locale can't be changed for system locales)
                                editStyle = style
                                showAddDialog = true
                            },
                            onDelete = {
                                // Only allow deleting custom styles, not system locales
                                if (!style.isSystemLocale) {
                                    deleteConfirmStyle = style
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Layout settings screen for specific locale
    val currentLocaleForLayoutSettings = showLayoutSettingsForLocale
    if (currentLocaleForLayoutSettings != null) {
        KeyboardLayoutSettingsScreen(
            locale = currentLocaleForLayoutSettings,
            modifier = modifier,
            onBack = {
                showLayoutSettingsForLocale = null
                // Reload input styles to reflect any layout changes
                inputStyles = loadCustomInputStyles(context)
                // Reopen the dialog if it was open before opening layout settings
                if (wasDialogOpenBeforeLayoutSettings) {
                    showAddDialog = true
                    wasDialogOpenBeforeLayoutSettings = false
                }
            },
            onLayoutSelected = { locale, layout ->
                // Update locale-layout mapping in JSON only when editing a system locale
                if (editStyle?.isSystemLocale == true) {
                    updateLocaleLayoutMapping(context, locale, layout)
                }
                // If editing, also update the custom input style and editStyle state
                editStyle?.let { oldStyle ->
                    if (oldStyle.locale == locale) {
                        updateCustomInputStyle(context, oldStyle, locale, layout)
                        // Update editStyle to reflect the new layout
                        editStyle = oldStyle.copy(layout = layout)
                        inputStyles = loadCustomInputStyles(context)
                    }
                }
                // Preserve selection to prefill dialog on return
                lastDialogLocale = locale
                lastDialogLayout = layout
            }
        )
        return
    }
    
    // Add dialog
    if (showAddDialog) {
        AddCustomInputStyleDialog(
            initialLocale = lastDialogLocale ?: editStyle?.locale,
            initialLayout = lastDialogLayout ?: editStyle?.layout,
            isSystemLocale = editStyle?.isSystemLocale ?: false,
            onDismiss = {
                showAddDialog = false
                editStyle = null
                lastDialogLocale = null
                lastDialogLayout = null
            },
            onOpenLayoutSettings = { locale ->
                // Preserve current selections before opening layout picker
                lastDialogLocale = locale
                lastDialogLayout = lastDialogLayout ?: AdditionalSubtypeUtils.getLayoutForLocale(context.assets, locale, context)
                wasDialogOpenBeforeLayoutSettings = true
                showLayoutSettingsForLocale = locale
                showAddDialog = false
            },
            onSave = { locale, layout ->
                val duplicateErrorMsg = context.getString(R.string.custom_input_styles_duplicate_error)
                val targetOld = editStyle
                val isSystem = targetOld?.isSystemLocale ?: false
                
                // For system locales, only update the layout mapping, don't modify preferences
                if (isSystem) {
                    updateLocaleLayoutMapping(context, locale, layout)
                    inputStyles = loadCustomInputStyles(context)
                    showAddDialog = false
                    editStyle = null
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.custom_input_styles_layout_mapping_updated, getLocaleDisplayName(locale), layout))
                    }
                } else {
                    // For custom styles, update preferences
                    // Guard: prevent duplicate locale+layout (including system entries)
                    val isDuplicateCombo = inputStyles.any { existing ->
                        existing.locale == locale && existing.layout == layout &&
                                // allow same entry when editing without changes
                                (targetOld == null || existing.locale != targetOld.locale || existing.layout != targetOld.layout)
                    }
                    if (isDuplicateCombo) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(duplicateErrorMsg)
                        }
                        return@AddCustomInputStyleDialog
                    }

                    val success = if (targetOld != null) {
                        updateCustomInputStyle(context, targetOld, locale, layout)
                    } else {
                        addCustomInputStyle(context, locale, layout)
                    }

                    if (success) {
                        inputStyles = loadCustomInputStyles(context)
                        showAddDialog = false
                        editStyle = null
                        lastDialogLocale = null
                        lastDialogLayout = null
                        val msg = if (targetOld != null) {
                            context.getString(R.string.custom_input_styles_input_style_updated, getLocaleDisplayName(locale), layout)
                        } else {
                            context.getString(R.string.custom_input_styles_input_style_added, getLocaleDisplayName(locale), layout)
                        }
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(msg)
                        }
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(duplicateErrorMsg)
                        }
                    }
                }
            }
        )
    }
    
    // Delete confirmation dialog
    deleteConfirmStyle?.let { style ->
        AlertDialog(
            onDismissRequest = { deleteConfirmStyle = null },
            title = { Text(stringResource(R.string.custom_input_styles_delete_confirm_title)) },
            text = { Text(stringResource(R.string.custom_input_styles_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeCustomInputStyle(context, style.locale, style.layout)
                        inputStyles = loadCustomInputStyles(context)
                        deleteConfirmStyle = null
                        // Immediately re-register subtypes to remove deleted one from Android
                        AdditionalSubtypeUtils.registerAdditionalSubtypes(context)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.custom_input_styles_input_style_deleted))
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmStyle = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * Item in the list of custom input styles.
 */
@Composable
private fun CustomInputStyleItem(
    style: CustomInputStyle,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = style.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (style.isSystemLocale) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = stringResource(R.string.custom_input_styles_system_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${style.locale} - ${style.layout}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!style.isSystemLocale) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.custom_input_styles_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Dialog for adding a new custom input style or editing an existing one (including system locales).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomInputStyleDialog(
    initialLocale: String? = null,
    initialLayout: String? = null,
    isSystemLocale: Boolean = false,
    onDismiss: () -> Unit,
    onOpenLayoutSettings: (String) -> Unit,
    onSave: (String, String) -> Unit
) {
    val context = LocalContext.current
    
    var selectedLocale by remember { mutableStateOf<String?>(initialLocale) }
    var showCustomLocaleDialog by remember { mutableStateOf(false) }
    var customLocaleInput by remember { mutableStateOf("") }
    var customLocaleError by remember { mutableStateOf<String?>(null) }
    
    // Check if selected locale has dictionary
    val hasDictionary = remember(selectedLocale) {
        selectedLocale?.let { hasDictionaryForLocale(context, it) } ?: false
    }
    
    // Get current layout for selected locale (from JSON mapping or initialLayout)
    val currentLayout = remember(selectedLocale) {
        if (selectedLocale != null) {
            initialLayout ?: AdditionalSubtypeUtils.getLayoutForLocale(context.assets, selectedLocale!!, context)
        } else {
            initialLayout
        }
    }
    
    // Get available locales based on dictionary availability (no filtering of system locales)
    val availableLocales = remember {
        getLocalesWithDictionary(context).sorted()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                when {
                    isSystemLocale -> stringResource(R.string.custom_input_styles_edit_system_locale_title)
                    initialLocale != null -> stringResource(R.string.custom_input_styles_edit_dialog_title)
                    else -> stringResource(R.string.custom_input_styles_add_dialog_title)
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Locale selection (disabled for system locales)
                Text(
                    text = stringResource(R.string.custom_input_styles_select_locale),
                    style = MaterialTheme.typography.labelLarge
                )
                var expandedLocale by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedLocale && !isSystemLocale, // Disable expansion for system locales
                    onExpandedChange = { if (!isSystemLocale) expandedLocale = it }
                ) {
                    OutlinedTextField(
                        value = selectedLocale ?: "",
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isSystemLocale, // Disable editing for system locales
                        label = { Text(stringResource(R.string.custom_input_styles_language_label)) },
                        trailingIcon = { 
                            if (!isSystemLocale) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLocale)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (!isSystemLocale) Modifier.menuAnchor() else Modifier)
                    )
                    if (!isSystemLocale) {
                        ExposedDropdownMenu(
                            expanded = expandedLocale,
                            onDismissRequest = { expandedLocale = false }
                        ) {
                            availableLocales.forEach { locale ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.custom_input_styles_locale_display, locale, getLocaleDisplayName(locale))) },
                                    onClick = {
                                        selectedLocale = locale
                                        expandedLocale = false
                                    }
                                )
                            }
                            // Add custom locale option
                            DropdownMenuItem(
                                text = { 
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(stringResource(R.string.custom_input_styles_add_custom_locale))
                                    }
                                },
                                onClick = {
                                    expandedLocale = false
                                    showCustomLocaleDialog = true
                                }
                            )
                        }
                    }
                }
                if (isSystemLocale) {
                    Text(
                        text = stringResource(R.string.custom_input_styles_system_locale_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Warning if locale doesn't have dictionary
                if (selectedLocale != null && !hasDictionary) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.custom_input_styles_no_dictionary_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                
                // Layout display (clickable to open layout settings)
                if (selectedLocale != null) {
                    Text(
                        text = stringResource(R.string.custom_input_styles_select_layout),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenLayoutSettings(selectedLocale!!)
                            },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant
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
                                    text = currentLayout ?: stringResource(R.string.custom_input_styles_default_layout),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.custom_input_styles_change_layout_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val locale = selectedLocale
                    val layout = currentLayout
                    if (locale != null && layout != null) {
                        onSave(locale, layout)
                    }
                },
                enabled = selectedLocale != null && currentLayout != null
            ) {
                Text(stringResource(R.string.custom_input_styles_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.custom_input_styles_cancel))
            }
        }
    )
    
    // Custom locale input dialog
    if (showCustomLocaleDialog) {
        AlertDialog(
            onDismissRequest = {
                showCustomLocaleDialog = false
                customLocaleInput = ""
                customLocaleError = null
            },
            title = { Text(stringResource(R.string.custom_input_styles_custom_locale_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customLocaleInput,
                        onValueChange = { 
                            customLocaleInput = it.trim()
                            customLocaleError = null
                        },
                        label = { Text(stringResource(R.string.custom_input_styles_custom_locale_label)) },
                        placeholder = { Text(stringResource(R.string.custom_input_styles_custom_locale_placeholder)) },
                        isError = customLocaleError != null,
                        supportingText = {
                            if (customLocaleError != null) {
                                Text(customLocaleError!!, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text(stringResource(R.string.custom_input_styles_custom_locale_hint))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrect = false,
                            keyboardType = KeyboardType.Uri
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val localeCode = customLocaleInput.trim()
                        if (localeCode.isEmpty()) {
                            customLocaleError = context.getString(R.string.custom_input_styles_custom_locale_empty_error)
                        } else if (!isValidLocaleCode(localeCode)) {
                            customLocaleError = context.getString(R.string.custom_input_styles_custom_locale_invalid_error)
                        } else {
                            selectedLocale = localeCode
                            showCustomLocaleDialog = false
                            customLocaleInput = ""
                            customLocaleError = null
                        }
                    },
                    enabled = customLocaleInput.isNotBlank()
                ) {
                    Text(stringResource(R.string.custom_input_styles_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCustomLocaleDialog = false
                        customLocaleInput = ""
                        customLocaleError = null
                    }
                ) {
                    Text(stringResource(R.string.custom_input_styles_cancel))
                }
            }
        )
    }
}

/**
 * Loads custom input styles from preferences and system-enabled locales.
 */
private fun loadCustomInputStyles(context: Context): List<CustomInputStyle> {
    val styles = mutableListOf<CustomInputStyle>()
    
    // First, add system-enabled locales
    val systemLocales = getSystemEnabledLocales(context)
    systemLocales.forEach { locale ->
        val layout = AdditionalSubtypeUtils.getLayoutForLocale(context.assets, locale, context)
        val displayName = "${getLocaleDisplayName(locale)} - $layout"
        styles.add(CustomInputStyle(locale, layout, displayName, isSystemLocale = true))
    }
    
    // Then, add custom input styles from preferences
    val prefString = SettingsManager.getCustomInputStyles(context)
    if (prefString.isNotBlank()) {
        val entries = prefString.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        
        for (entry in entries) {
            val parts = entry.split(":").map { it.trim() }
            if (parts.size >= 2) {
                val locale = parts[0]
                val layout = parts[1]
                val displayName = "${getLocaleDisplayName(locale)} - $layout"
                styles.add(CustomInputStyle(locale, layout, displayName, isSystemLocale = false))
            }
        }
    }
    
    // De-duplicate exact locale+layout to avoid LazyColumn key collisions
    val seen = mutableSetOf<String>()
    val uniqueStyles = mutableListOf<CustomInputStyle>()
    styles.forEach { style ->
        val key = "${style.locale}:${style.layout}"
        if (seen.add(key)) {
            uniqueStyles.add(style)
        }
    }
    
    return uniqueStyles
}

/**
 * Gets the list of system-enabled locales.
 * Returns locales in format "en_US", "it_IT", etc.
 */
private fun getSystemEnabledLocales(context: Context): List<String> {
    val locales = mutableListOf<String>()
    
    try {
        val config = context.resources.configuration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Android N+ (API 24+)
            val localeList = config.locales
            for (i in 0 until localeList.size()) {
                val locale = localeList[i]
                val localeStr = formatLocaleString(locale)
                if (localeStr.isNotEmpty() && !locales.contains(localeStr)) {
                    locales.add(localeStr)
                }
            }
        } else {
            // Pre-Android N
            @Suppress("DEPRECATION")
            val locale = config.locale
            val localeStr = formatLocaleString(locale)
            if (localeStr.isNotEmpty()) {
                locales.add(localeStr)
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("CustomInputStyles", "Error getting system locales", e)
    }
    
    return locales
}

/**
 * Formats a Locale object to "en_US" format.
 */
private fun formatLocaleString(locale: Locale): String {
    val language = locale.language
    val country = locale.country
    
    return if (country.isNotEmpty()) {
        "${language}_$country"
    } else {
        language
    }
}

/**
 * Adds a custom input style.
 */
private fun addCustomInputStyle(context: Context, locale: String, layout: String): Boolean {
    val currentStyles = SettingsManager.getCustomInputStyles(context)
    val entries = if (currentStyles.isBlank()) {
        emptyList()
    } else {
        currentStyles.split(";").map { it.trim() }.filter { it.isNotEmpty() }
    }
    
    // Check for duplicates
    val newEntry = "$locale:$layout"
    if (entries.any { it.startsWith("$locale:$layout") }) {
        return false
    }
    
    // Add new entry
    val newStyles = if (entries.isEmpty()) {
        newEntry
    } else {
        "$currentStyles;$newEntry"
    }
    
    SettingsManager.setCustomInputStyles(context, newStyles)
    return true
}

/**
 * Updates an existing custom input style entry.
 */
private fun updateCustomInputStyle(
    context: Context,
    oldStyle: CustomInputStyle,
    newLocale: String,
    newLayout: String
): Boolean {
    val currentStyles = SettingsManager.getCustomInputStyles(context)
    val entries = if (currentStyles.isBlank()) {
        emptyList()
    } else {
        currentStyles.split(";").map { it.trim() }.filter { it.isNotEmpty() }
    }

    val oldKey = "${oldStyle.locale}:${oldStyle.layout}"
    val newKey = "$newLocale:$newLayout"

    // If the new key already exists and it's not the same entry, reject as duplicate
    if (newKey != oldKey && entries.any { it.startsWith(newKey) }) {
        return false
    }

    val updated = entries.map { entry ->
        if (entry.startsWith(oldKey)) newKey else entry
    }

    val newStyles = updated.joinToString(";")
    SettingsManager.setCustomInputStyles(context, newStyles)
    return true
}

/**
 * Removes a custom input style.
 */
private fun removeCustomInputStyle(context: Context, locale: String, layout: String) {
    val currentStyles = SettingsManager.getCustomInputStyles(context)
    val entries = currentStyles.split(";").map { it.trim() }.filter { it.isNotEmpty() }
    val filtered = entries.filterNot { it.startsWith("$locale:$layout") }
    val newStyles = filtered.joinToString(";")
    SettingsManager.setCustomInputStyles(context, newStyles)
}

/**
 * Gets display name for a locale.
 */
private fun getLocaleDisplayName(locale: String): String {
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

/**
 * Gets list of locales that have dictionary files available.
 * Checks both serialized (.dict) and JSON (.json) formats.
 */
private fun getLocalesWithDictionary(context: Context): List<String> {
    val localesWithDict = mutableSetOf<String>()
    
    try {
        val assets = context.assets
        
        // Check serialized dictionaries first
        try {
            val serializedFiles = assets.list("common/dictionaries_serialized")
            serializedFiles?.forEach { fileName ->
                if (fileName.endsWith("_base.dict")) {
                    val langCode = fileName.removeSuffix("_base.dict")
                    // Map language code to common locale variants
                    localesWithDict.addAll(getLocaleVariantsForLanguage(langCode))
                }
            }
        } catch (e: Exception) {
            // If serialized directory doesn't exist, try JSON
        }
        
        // Also check JSON dictionaries (fallback)
        try {
            val jsonFiles = assets.list("common/dictionaries")
            jsonFiles?.forEach { fileName ->
                if (fileName.endsWith("_base.json") && fileName != "user_defaults.json") {
                    val langCode = fileName.removeSuffix("_base.json")
                    // Map language code to common locale variants
                    localesWithDict.addAll(getLocaleVariantsForLanguage(langCode))
                }
            }
        } catch (e: Exception) {
            // If dictionaries directory doesn't exist, continue
        }

        // Check imported serialized dictionaries in app storage
        try {
            val localDir = java.io.File(context.filesDir, "dictionaries_serialized")
            val localFiles = localDir.listFiles { file ->
                file.isFile && file.name.endsWith("_base.dict")
            }
            localFiles?.forEach { file ->
                val langCode = file.name.removeSuffix("_base.dict")
                localesWithDict.addAll(getLocaleVariantsForLanguage(langCode))
            }
        } catch (e: Exception) {
            android.util.Log.w("CustomInputStyles", "Error reading local dictionaries_serialized", e)
        }
    } catch (e: Exception) {
        android.util.Log.e("CustomInputStyles", "Error checking dictionaries", e)
    }
    
    return localesWithDict.toList()
}

/**
 * Maps a language code (e.g., "en", "it") to common locale variants.
 */
private fun getLocaleVariantsForLanguage(langCode: String): List<String> {
    return when (langCode.lowercase()) {
        "en" -> listOf("en_US", "en_GB", "en_CA", "en_AU")
        "it" -> listOf("it_IT", "it_CH")
        "fr" -> listOf("fr_FR", "fr_CA", "fr_CH", "fr_BE")
        "de" -> listOf("de_DE", "de_AT", "de_CH")
        "es" -> listOf("es_ES", "es_MX", "es_AR", "es_CO")
        "pt" -> listOf("pt_PT", "pt_BR")
        "pl" -> listOf("pl_PL")
        "ru" -> listOf("ru_RU")
        else -> listOf("${langCode}_${langCode.uppercase()}") // Generic fallback
    }
}

/**
 * Validates a locale code format.
 * Accepts formats like "en", "en_US", "en-US", etc.
 */
private fun isValidLocaleCode(localeCode: String): Boolean {
    if (localeCode.isEmpty()) return false
    
    // Basic validation: should contain only letters, numbers, underscores, or hyphens
    // Format: 2-3 letter language code, optionally followed by underscore/hyphen and 2-3 letter country code
    val pattern = "^[a-zA-Z]{2,3}([_-][a-zA-Z]{2,3})?$".toRegex()
    return pattern.matches(localeCode)
}

/**
 * Checks if a dictionary file exists for the given locale.
 * Returns true if a dictionary is found (serialized or JSON format).
 */
private fun hasDictionaryForLocale(context: Context, locale: String): Boolean {
    try {
        val assets = context.assets
        val langCode = locale.split("_")[0].lowercase()
        
        // Check serialized dictionaries
        try {
            val serializedFiles = assets.list("common/dictionaries_serialized")
            serializedFiles?.forEach { fileName ->
                if (fileName == "${langCode}_base.dict") {
                    return true
                }
            }
        } catch (e: Exception) {
            // If serialized directory doesn't exist, try JSON
        }
        
        // Check JSON dictionaries
        try {
            val jsonFiles = assets.list("common/dictionaries")
            jsonFiles?.forEach { fileName ->
                if (fileName == "${langCode}_base.json" && fileName != "user_defaults.json") {
                    return true
                }
            }
        } catch (e: Exception) {
            // If dictionaries directory doesn't exist, continue
        }

        // Check imported serialized dictionaries in app storage
        try {
            val localDir = java.io.File(context.filesDir, "dictionaries_serialized")
            val localFiles = localDir.listFiles { file ->
                file.isFile && file.name == "${langCode}_base.dict"
            }
            if (!localFiles.isNullOrEmpty()) {
                return true
            }
        } catch (e: Exception) {
            android.util.Log.w("CustomInputStyles", "Error checking local dictionaries for locale $locale", e)
        }
    } catch (e: Exception) {
        android.util.Log.e("CustomInputStyles", "Error checking dictionary for locale $locale", e)
    }
    
    return false
}

/**
 * Updates the locale-layout mapping in the JSON file.
 * Reads from assets first, then merges with custom file, and saves to custom file.
 * If the locale being updated is currently active in the IME, immediately applies the layout change.
 */
private fun updateLocaleLayoutMapping(context: Context, locale: String, layout: String) {
    try {
        // Read base mapping from assets
        val assets = context.assets
        val baseJsonString = assets.open("common/locale_layout_mapping.json").use { input ->
            input.bufferedReader().use { it.readText() }
        }
        val json = org.json.JSONObject(baseJsonString)
        
        // Merge with custom file if it exists
        val customMappingFile = java.io.File(context.filesDir, "locale_layout_mapping.json")
        if (customMappingFile.exists() && customMappingFile.canRead()) {
            try {
                val customJsonString = customMappingFile.readText()
                val customJson = org.json.JSONObject(customJsonString)
                val customKeys = customJson.keys()
                while (customKeys.hasNext()) {
                    val key = customKeys.next()
                    json.put(key, customJson.getString(key))
                }
            } catch (e: Exception) {
                android.util.Log.w("CustomInputStyles", "Error reading custom mapping, using base only", e)
            }
        }
        
        // Update the locale-layout mapping
        json.put(locale, layout)
        
        // Save to custom file
        customMappingFile.writeText(json.toString(2))
        
        android.util.Log.d("CustomInputStyles", "Updated locale-layout mapping: $locale -> $layout")
        
        // Check if the locale being updated is currently active in the IME
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val currentSubtype = imm?.currentInputMethodSubtype
            val currentLocale = currentSubtype?.locale
            
            if (currentLocale == locale) {
                // The locale being updated is currently active, immediately apply the layout change
                android.util.Log.d("CustomInputStyles", "Locale $locale is currently active, applying layout change immediately")
                SettingsManager.setKeyboardLayout(context, layout)
            }
        } catch (e: Exception) {
            android.util.Log.w("CustomInputStyles", "Error checking current IME locale", e)
        }
    } catch (e: Exception) {
        android.util.Log.e("CustomInputStyles", "Error updating locale-layout mapping", e)
    }
}

