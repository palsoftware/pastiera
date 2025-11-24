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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import it.palsoftware.pastiera.inputmethod.AutoCorrector
import it.palsoftware.pastiera.R
import java.util.Locale
import android.widget.Toast

@Composable
private fun LanguageItem(
    languageCode: String,
    languageName: String,
    isSystemLanguage: Boolean,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit = {}
) {
    val isRicettePastiera = languageCode == "x-pastiera"
    val showToggle = !isRicettePastiera // Hide toggle for Ricette Pastiera (always active)
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onEdit() }
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
            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                Text(
                    text = languageName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                if (isSystemLanguage) {
                    Text(
                        text = stringResource(R.string.auto_correct_system_language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (isRicettePastiera) {
                    Text(
                        text = stringResource(R.string.auto_correct_ricette_pastiera_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.auto_correct_edit))
                }
                if (showToggle) {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onToggle
                    )
                }
            }
        }
    }
}

private fun getLanguageDisplayName(context: Context, languageCode: String): String {
    // Special case for Ricette Pastiera
    if (languageCode == "x-pastiera") {
        return context.getString(R.string.auto_correct_ricette_pastiera_name)
    }
    
    // First try to get saved name from JSON
    val savedName = SettingsManager.getCustomLanguageName(context, languageCode)
    if (savedName != null) {
        return savedName
    }
    
    // For standard languages, use simple locale display name (without "Pastiera")
    val standardLanguages = mapOf(
        "en" to "English",
        "it" to "Italiano",
        "fr" to "Français",
        "de" to "Deutsch",
        "pl" to "Polski",
        "es" to "Español"
    )
    
    if (languageCode in standardLanguages) {
        return standardLanguages[languageCode]!!
    }
    
    // If no saved name and not standard, use name generated from locale
    return try {
        val locale = Locale.forLanguageTag(languageCode)
        locale.getDisplayLanguage(locale).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(locale) else it.toString()
        }
    } catch (e: Exception) {
        languageCode.uppercase()
    }
}

@Composable
private fun AddNewLanguageDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit, // (languageCode, languageName)
    existingLanguages: Set<String>
) {
    val context = LocalContext.current
    var languageCode by remember { mutableStateOf("") }
    var languageName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.auto_correct_add_language))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = languageCode,
                    onValueChange = { 
                        languageCode = it.trim()
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.auto_correct_language_code)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } }
                )
                OutlinedTextField(
                    value = languageName,
                    onValueChange = { 
                        languageName = it.trim()
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.auto_correct_language_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = stringResource(R.string.auto_correct_language_code_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val code = languageCode.trim()
                    val name = languageName.trim()
                    if (code.isEmpty()) {
                        errorMessage = context.getString(R.string.auto_correct_language_code_required)
                    } else if (code.lowercase() in existingLanguages.map { it.lowercase() }) {
                        errorMessage = context.getString(R.string.auto_correct_language_already_exists)
                    } else {
                        onSave(code.lowercase(), if (name.isNotEmpty()) name else code)
                    }
                },
                enabled = languageCode.isNotBlank()
            ) {
                Text(stringResource(R.string.auto_correct_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.auto_correct_cancel))
            }
        }
    )
}

/**
 * Screen for managing auto-correction settings.
 * Allows enabling/disabling languages for auto-correction.
 */
@Composable
fun AutoCorrectSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onEditLanguage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    
    // Load available languages (updatable), excluding x-pastiera from UI
    var allLanguages by remember { 
        mutableStateOf(AutoCorrector.getAllAvailableLanguages().filter { it != "x-pastiera" }) 
    }
    val systemLocale = remember {
        context.resources.configuration.locales[0].language.lowercase()
    }
    
    // Load enabled languages
    var enabledLanguages by remember {
        mutableStateOf(SettingsManager.getAutoCorrectEnabledLanguages(context))
    }
    
    // State for new language dialog
    var showNewLanguageDialog by remember { mutableStateOf(false) }
    
    // Load corrections when screen is opened to ensure languages are available
    LaunchedEffect(Unit) {
        try {
            val assets = context.assets
            AutoCorrector.loadCorrections(assets, context)
            allLanguages = AutoCorrector.getAllAvailableLanguages().filter { it != "x-pastiera" }
        } catch (e: Exception) {
            android.util.Log.e("AutoCorrectSettings", "Error loading corrections", e)
        }
    }
    
    // Helper to determine if a language is enabled
    fun isLanguageEnabled(locale: String): Boolean {
        // If set is empty, all languages are enabled (default)
        return enabledLanguages.isEmpty() || enabledLanguages.contains(locale)
    }
    
    // Helper to count how many languages are enabled (excluding x-pastiera which is always enabled)
    fun countEnabledLanguages(): Int {
        return if (enabledLanguages.isEmpty()) {
            allLanguages.size // All enabled (excluding x-pastiera from count)
        } else {
            // Count only visible languages (exclude x-pastiera)
            enabledLanguages.filter { it != "x-pastiera" }.size
        }
    }
    
    // Helper to handle language toggle
    fun toggleLanguage(locale: String, currentEnabled: Boolean) {
        if (!currentEnabled) {
            // Enable: add to list
            val newSet = if (enabledLanguages.isEmpty()) {
                // Was "all enabled", now enable only this one
                setOf(locale)
            } else {
                enabledLanguages + locale
            }
            
            // If new set contains all languages, save as empty (all enabled)
            val finalSet = if (newSet.size == allLanguages.size && newSet.containsAll(allLanguages)) {
                emptySet<String>()
            } else {
                newSet
            }
            
            enabledLanguages = finalSet
            SettingsManager.setAutoCorrectEnabledLanguages(context, finalSet)
        } else {
            // Disable: verify it's not the last language
            val enabledCount = countEnabledLanguages()
            if (enabledCount <= 1) {
                // This is the last enabled language, show toast and don't allow disabling
                Toast.makeText(
                    context,
                    context.getString(R.string.auto_correct_at_least_one_language_required),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            
            val newSet = if (enabledLanguages.isEmpty()) {
                // Was "all enabled", now disable this one
                // So enable all others
                allLanguages.filter { it != locale }.toSet()
            } else {
                enabledLanguages - locale
            }
            
            // If new set contains all languages, save as empty
            val finalSet = if (newSet.size == allLanguages.size && newSet.containsAll(allLanguages)) {
                emptySet<String>()
            } else {
                newSet
            }
            
            enabledLanguages = finalSet
            SettingsManager.setAutoCorrectEnabledLanguages(context, finalSet)
        }
    }
    
    // Handle system back button
    BackHandler {
        onBack()
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back_content_description)
                            )
                        }
                        Text(
                            text = stringResource(R.string.auto_correct_settings_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    IconButton(onClick = { showNewLanguageDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.auto_correct_add_language)
                        )
                    }
                }
            }
        }
        ) { paddingValues ->
            AnimatedContent(
                targetState = Unit,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "auto_correct_settings_animation"
            ) {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Description section
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = stringResource(R.string.auto_correct_settings_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // System language (always at top)
                    if (allLanguages.contains(systemLocale)) {
                        val systemEnabled = isLanguageEnabled(systemLocale)
                        LanguageItem(
                            languageCode = systemLocale,
                            languageName = getLanguageDisplayName(context, systemLocale),
                            isSystemLanguage = true,
                            isEnabled = systemEnabled,
                            onToggle = { enabled ->
                                toggleLanguage(systemLocale, systemEnabled)
                            },
                            onEdit = {
                                onEditLanguage(systemLocale)
                            }
                        )
                    }
                    
                    // Ricette Pastiera (always active, no toggle, shown after system language)
                    LanguageItem(
                        languageCode = "x-pastiera",
                        languageName = getLanguageDisplayName(context, "x-pastiera"),
                        isSystemLanguage = false,
                        isEnabled = true, // Always enabled
                        onToggle = { /* No toggle for Ricette Pastiera */ },
                        onEdit = {
                            onEditLanguage("x-pastiera")
                        }
                    )
                    
                    // Other available languages (excluding x-pastiera)
                    val otherLanguages = allLanguages.filter { it != systemLocale && it != "x-pastiera" }.sorted()
                    
                    if (otherLanguages.isNotEmpty()) {
                        // Header for other languages
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = stringResource(R.string.auto_correct_other_languages),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        otherLanguages.forEach { locale ->
                            val localeEnabled = isLanguageEnabled(locale)
                            LanguageItem(
                                languageCode = locale,
                                languageName = getLanguageDisplayName(context, locale),
                                isSystemLanguage = false,
                                isEnabled = localeEnabled,
                                onToggle = { enabled ->
                                    toggleLanguage(locale, localeEnabled)
                                },
                                onEdit = {
                                    onEditLanguage(locale)
                                }
                            )
                        }
                    }
                    
                    // Section for custom languages (if present)
                    // Filter only languages that are not standard and not already shown above (excluding x-pastiera)
                    val customLanguages = AutoCorrector.getCustomLanguages()
                        .filter { it != systemLocale && it !in otherLanguages && it != "x-pastiera" }
                    if (customLanguages.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = stringResource(R.string.auto_correct_custom_languages),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        customLanguages.forEach { locale ->
                            val localeEnabled = isLanguageEnabled(locale)
                            LanguageItem(
                                languageCode = locale,
                                languageName = getLanguageDisplayName(context, locale),
                                isSystemLanguage = false,
                                isEnabled = localeEnabled,
                                onToggle = { enabled ->
                                    toggleLanguage(locale, localeEnabled)
                                },
                                onEdit = {
                                    onEditLanguage(locale)
                                }
                            )
                        }
                    }
                }
            }
        }
            
            // Dialog to add a new language (outside Scaffold)
            if (showNewLanguageDialog) {
        AddNewLanguageDialog(
            onDismiss = { showNewLanguageDialog = false },
            onSave = { languageCode, languageName ->
                // Create empty dictionary for new language with saved name
                SettingsManager.saveCustomAutoCorrections(
                    context, 
                    languageCode, 
                    emptyMap(),
                    languageName = languageName
                )
                
                // Reload all corrections (including new language)
                try {
                    val assets = context.assets
                    AutoCorrector.loadCorrections(assets, context)
                } catch (e: Exception) {
                    // Fallback: load only the new language
                    AutoCorrector.loadCustomCorrections(languageCode, "{}")
                }
                
                // Update list of available languages (excluding x-pastiera)
                allLanguages = AutoCorrector.getAllAvailableLanguages().filter { it != "x-pastiera" }
                
                // Automatically enable the new language
                val newSet = if (enabledLanguages.isEmpty()) {
                    setOf(languageCode)
                } else {
                    enabledLanguages + languageCode
                }
                enabledLanguages = newSet
                SettingsManager.setAutoCorrectEnabledLanguages(context, newSet)
                
                showNewLanguageDialog = false
                
                // Navigate to edit screen for the new language
                onEditLanguage(languageCode)
            },
            existingLanguages = allLanguages.toSet()
        )
    }
}
