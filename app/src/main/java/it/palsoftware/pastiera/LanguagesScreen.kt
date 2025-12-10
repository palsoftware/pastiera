package it.palsoftware.pastiera

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import it.palsoftware.pastiera.SettingsManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
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
import it.palsoftware.pastiera.R
import java.util.Locale

/**
 * Screen for managing input method subtypes (languages).
 * Allows adding/removing languages that have dictionaries but are not system languages.
 */
@Composable
fun LanguagesScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Get available dictionaries from assets (dictionaries_serialized folder)
    val availableDictionaries = remember {
        getAvailableDictionaries(context)
    }
    
    // Get system locales
    val systemLocales = remember {
        context.resources.configuration.locales.let { locales ->
            (0 until locales.size()).map { locales[it].language.lowercase() }.toSet()
        }.also { locales ->
            android.util.Log.d("LanguagesScreen", "System locales: ${locales.joinToString(", ")}")
        }
    }
    
    // Get currently enabled subtypes
    var enabledSubtypes by remember {
        mutableStateOf(getEnabledSubtypes(context)).also { state ->
            android.util.Log.d("LanguagesScreen", "Currently enabled subtypes: ${state.value.joinToString(", ")}")
        }
    }
    
    // Filter: only show languages that have dictionaries but are NOT system languages
    val availableLanguages = availableDictionaries.filter { 
        it !in systemLocales 
    }.sorted().also { languages ->
        android.util.Log.d("LanguagesScreen", "Available languages (non-system): ${languages.joinToString(", ")}")
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
                    IconButton(onClick = { 
                        (context as? android.app.Activity)?.finish() 
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = stringResource(R.string.languages_title),
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
            // Description
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
                        text = stringResource(R.string.languages_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Language list
            if (availableLanguages.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.languages_no_available),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                availableLanguages.forEach { languageCode ->
                    val isEnabled = enabledSubtypes.contains(languageCode)
                    LanguageSubtypeItem(
                        languageCode = languageCode,
                        isEnabled = isEnabled,
                        onToggle = { enabled ->
                            if (enabled) {
                                addSubtype(context, languageCode)
                            } else {
                                removeSubtype(context, languageCode)
                            }
                            enabledSubtypes = getEnabledSubtypes(context)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageSubtypeItem(
    languageCode: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val languageName = getLanguageDisplayName(context, languageCode)
    
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
                imageVector = Icons.Filled.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = languageName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = languageCode.uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

/**
 * Get available dictionaries from assets (dictionaries_serialized folder).
 * Reads all files matching the pattern "*_base.dict" and extracts language codes.
 */
private fun getAvailableDictionaries(context: Context): List<String> {
    return try {
        val assets = context.assets
        val files = assets.list("common/dictionaries_serialized") ?: emptyArray()
        android.util.Log.d("LanguagesScreen", "Found ${files.size} files in dictionaries_serialized (assets)")
        files.forEach { android.util.Log.d("LanguagesScreen", "  - $it") }
        val localDir = java.io.File(context.filesDir, "dictionaries_serialized")
        val localFiles = localDir.listFiles { file ->
            file.isFile && file.name.endsWith("_base.dict")
        }?.map { it.name } ?: emptyList()
        if (localFiles.isNotEmpty()) {
            android.util.Log.d("LanguagesScreen", "Found ${localFiles.size} local dictionaries")
            localFiles.forEach { android.util.Log.d("LanguagesScreen", "  - $it") }
        }
        
        val dictionaries = (files.toList() + localFiles)
            .filter { it.endsWith("_base.dict") }
            .map { it.removeSuffix("_base.dict") }
            .filter { it != "user" } // Exclude user_defaults if present
            .sorted()
        
        android.util.Log.d("LanguagesScreen", "Extracted ${dictionaries.size} dictionaries: ${dictionaries.joinToString(", ")}")
        dictionaries
    } catch (e: Exception) {
        android.util.Log.e("LanguagesScreen", "Error reading dictionaries_serialized", e)
        emptyList()
    }
}

/**
 * Get language display name.
 * First tries to get from string resources, then falls back to locale display name.
 */
private fun getLanguageDisplayName(context: Context, languageCode: String): String {
    // Try to get from string resources first
    val resourceName = "input_method_name_$languageCode"
    val resourceId = context.resources.getIdentifier(
        resourceName, "string", context.packageName
    )
    if (resourceId != 0) {
        return context.getString(resourceId)
    }
    
    // Fallback to locale display name
    return try {
        val locale = Locale.forLanguageTag(languageCode)
        locale.getDisplayLanguage(locale).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(locale) else it.toString()
        }
    } catch (e: Exception) {
        languageCode.uppercase()
    }
}

/**
 * Get currently enabled subtypes for Pastiera IME.
 * Returns only the additional subtypes (not those declared in XML).
 */
private fun getEnabledSubtypes(context: Context): Set<String> {
    // Get the list of additional subtypes from SharedPreferences
    val subtypes = SettingsManager.getAdditionalImeSubtypes(context)
    android.util.Log.d("LanguagesScreen", "getEnabledSubtypes: Found ${subtypes.size} subtypes from SharedPreferences: ${subtypes.joinToString(", ")}")
    
    // Also check what Android actually reports
    try {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val packageName = context.packageName
        val serviceName = "${packageName}.inputmethod.PhysicalKeyboardInputMethodService"
        
        val imeInfo = imm.enabledInputMethodList.find {
            it.packageName == packageName && 
            it.serviceName == serviceName
        }
        
        if (imeInfo != null) {
            val androidSubtypes = imm.getEnabledInputMethodSubtypeList(imeInfo, true)
            android.util.Log.d("LanguagesScreen", "Android reports ${androidSubtypes.size} enabled subtypes:")
            androidSubtypes.forEach { subtype ->
                val name = try {
                    if (subtype.nameResId != 0) {
                        context.getString(subtype.nameResId)
                    } else {
                        "N/A"
                    }
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
                android.util.Log.d("LanguagesScreen", "  - locale: ${subtype.locale}, name: $name, mode: ${subtype.mode}, nameResId: ${subtype.nameResId}")
            }
        } else {
            android.util.Log.w("LanguagesScreen", "IME not found in enabled list")
            android.util.Log.d("LanguagesScreen", "Looking for: package=$packageName, service=$serviceName")
            android.util.Log.d("LanguagesScreen", "Available IMEs:")
            imm.enabledInputMethodList.forEach { info ->
                android.util.Log.d("LanguagesScreen", "  - package: ${info.packageName}, service: ${info.serviceName}, id: ${info.id}")
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("LanguagesScreen", "Error checking Android subtypes", e)
    }
    
    return subtypes
}

/**
 * Add a subtype for the given language.
 */
private fun addSubtype(context: Context, languageCode: String) {
    android.util.Log.d("LanguagesScreen", "addSubtype: Adding language $languageCode")
    
    // Get current additional subtypes from SharedPreferences
    val currentAdditionalSubtypes = SettingsManager.getAdditionalImeSubtypes(context)
    android.util.Log.d("LanguagesScreen", "Current additional subtypes: ${currentAdditionalSubtypes.joinToString(", ")}")
    
    // Check if subtype already exists
    if (currentAdditionalSubtypes.contains(languageCode)) {
        android.util.Log.w("LanguagesScreen", "Subtype for $languageCode already exists")
        return // Already exists
    }
    
    // Get all current additional subtypes and add the new one
    val updatedAdditionalSubtypes = currentAdditionalSubtypes + languageCode
    android.util.Log.d("LanguagesScreen", "Updated subtypes list: ${updatedAdditionalSubtypes.joinToString(", ")}")
    
    // Save to SharedPreferences and notify IME service
    try {
        SettingsManager.setAdditionalImeSubtypes(context, updatedAdditionalSubtypes)
        android.util.Log.d("LanguagesScreen", "Saved subtypes to SharedPreferences")
        
        // Send broadcast to IME service to update subtypes
        val intent = Intent("it.palsoftware.pastiera.ACTION_ADDITIONAL_SUBTYPES_UPDATED").apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
        android.util.Log.d("LanguagesScreen", "Sent broadcast to IME service to update subtypes")
    } catch (e: Exception) {
        android.util.Log.e("LanguagesScreen", "Error saving subtypes", e)
        android.util.Log.e("LanguagesScreen", "Exception type: ${e.javaClass.name}")
        android.util.Log.e("LanguagesScreen", "Exception message: ${e.message}")
        e.printStackTrace()
    }
}

/**
 * Remove a subtype for the given language.
 */
private fun removeSubtype(context: Context, languageCode: String) {
    android.util.Log.d("LanguagesScreen", "removeSubtype: Removing language $languageCode")
    
    // Get current additional subtypes from SharedPreferences
    val currentAdditionalSubtypes = SettingsManager.getAdditionalImeSubtypes(context)
    android.util.Log.d("LanguagesScreen", "Current additional subtypes: ${currentAdditionalSubtypes.joinToString(", ")}")
    
    // Filter out the subtype for this language
    val updatedAdditionalSubtypes = currentAdditionalSubtypes - languageCode
    android.util.Log.d("LanguagesScreen", "Updated subtypes list: ${updatedAdditionalSubtypes.joinToString(", ")}")
    
    // Save to SharedPreferences and notify IME service
    try {
        SettingsManager.setAdditionalImeSubtypes(context, updatedAdditionalSubtypes)
        android.util.Log.d("LanguagesScreen", "Saved subtypes to SharedPreferences")
        
        // Send broadcast to IME service to update subtypes
        val intent = Intent("it.palsoftware.pastiera.ACTION_ADDITIONAL_SUBTYPES_UPDATED").apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
        android.util.Log.d("LanguagesScreen", "Sent broadcast to IME service to update subtypes")
    } catch (e: Exception) {
        android.util.Log.e("LanguagesScreen", "Error saving subtypes", e)
        android.util.Log.e("LanguagesScreen", "Exception type: ${e.javaClass.name}")
        android.util.Log.e("LanguagesScreen", "Exception message: ${e.message}")
        e.printStackTrace()
    }
}


/**
 * Get locale tag for language code (e.g., "ru" -> "ru_RU").
 * Uses a mapping for common languages, falls back to language code if not found.
 */
private fun getLocaleTagForLanguage(languageCode: String): String {
    // Map common language codes to full locale tags
    // This mapping can be extended as needed
    val localeMap = mapOf(
        "ru" to "ru_RU",
        "pt" to "pt_PT",
        "de" to "de_DE",
        "fr" to "fr_FR",
        "es" to "es_ES",
        "pl" to "pl_PL",
        "it" to "it_IT",
        "en" to "en_US"
    )
    return localeMap[languageCode.lowercase()] ?: languageCode
}

/**
 * Get subtype name resource ID for language.
 * Falls back to default input method name if resource not found.
 */
private fun getSubtypeNameResourceId(context: Context, languageCode: String): Int {
    val resourceName = "input_method_name_$languageCode"
    return context.resources.getIdentifier(
        resourceName, "string", context.packageName
    ).takeIf { it != 0 } ?: R.string.input_method_name
}

