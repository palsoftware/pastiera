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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
                imageVector = Icons.Filled.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onEdit() }
            ) {
                Text(
                    text = languageName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (isSystemLanguage) {
                    Text(
                        text = stringResource(R.string.auto_correct_system_language),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle
                )
            }
        }
    }
}

private fun getLanguageDisplayName(context: Context, languageCode: String): String {
    // Prima prova a ottenere il nome salvato nel JSON
    val savedName = SettingsManager.getCustomLanguageName(context, languageCode)
    if (savedName != null) {
        return savedName
    }
    
    // Se non c'è un nome salvato, usa il nome generato dalla locale
    return try {
        val locale = Locale(languageCode)
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
 * Schermata per gestire le impostazioni dell'auto-correzione.
 * Permette di attivare/disattivare le lingue per l'auto-correzione.
 */
@Composable
fun AutoCorrectSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onEditLanguage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    
    // Carica le lingue disponibili (aggiornabile)
    var allLanguages by remember { mutableStateOf(AutoCorrector.getAllAvailableLanguages()) }
    val systemLocale = remember {
        context.resources.configuration.locales[0].language.lowercase()
    }
    
    // Carica le lingue abilitate
    var enabledLanguages by remember {
        mutableStateOf(SettingsManager.getAutoCorrectEnabledLanguages(context))
    }
    
    // Stato per il dialog di nuova lingua
    var showNewLanguageDialog by remember { mutableStateOf(false) }
    
    // Helper per determinare se una lingua è abilitata
    fun isLanguageEnabled(locale: String): Boolean {
        // Se il set è vuoto, tutte le lingue sono abilitate (default)
        return enabledLanguages.isEmpty() || enabledLanguages.contains(locale)
    }
    
    // Helper per contare quante lingue sono abilitate
    fun countEnabledLanguages(): Int {
        return if (enabledLanguages.isEmpty()) {
            allLanguages.size // Tutte abilitate
        } else {
            enabledLanguages.size
        }
    }
    
    // Helper per gestire il toggle di una lingua
    fun toggleLanguage(locale: String, currentEnabled: Boolean) {
        if (!currentEnabled) {
            // Abilita: aggiungi alla lista
            val newSet = if (enabledLanguages.isEmpty()) {
                // Era "tutte abilitate", ora abilitiamo solo questa
                setOf(locale)
            } else {
                enabledLanguages + locale
            }
            
            // Se il nuovo set contiene tutte le lingue, salva come vuoto (tutte abilitate)
            val finalSet = if (newSet.size == allLanguages.size && newSet.containsAll(allLanguages)) {
                emptySet<String>()
            } else {
                newSet
            }
            
            enabledLanguages = finalSet
            SettingsManager.setAutoCorrectEnabledLanguages(context, finalSet)
        } else {
            // Disabilita: verifica che non sia l'ultima lingua
            val enabledCount = countEnabledLanguages()
            if (enabledCount <= 1) {
                // È l'ultima lingua abilitata, mostra toast e non permettere di disabilitarla
                Toast.makeText(
                    context,
                    context.getString(R.string.auto_correct_at_least_one_language_required),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            
            val newSet = if (enabledLanguages.isEmpty()) {
                // Era "tutte abilitate", ora disabilitiamo questa
                // Quindi abilitiamo tutte le altre
                allLanguages.filter { it != locale }.toSet()
            } else {
                enabledLanguages - locale
            }
            
            // Se il nuovo set contiene tutte le lingue, salva come vuoto
            val finalSet = if (newSet.size == allLanguages.size && newSet.containsAll(allLanguages)) {
                emptySet<String>()
            } else {
                newSet
            }
            
            enabledLanguages = finalSet
            SettingsManager.setAutoCorrectEnabledLanguages(context, finalSet)
        }
    }
    
    // Gestisci il back button di sistema
    BackHandler {
        onBack()
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
                        text = stringResource(R.string.auto_correct_settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewLanguageDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.auto_correct_add_language)
                )
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
            // Descrizione
            Surface(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.auto_correct_settings_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            HorizontalDivider()
            
            // Lingua di sistema (sempre in alto)
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
                HorizontalDivider()
            }
            
            // Altre lingue disponibili
            val otherLanguages = allLanguages.filter { it != systemLocale }.sorted()
            
            if (otherLanguages.isNotEmpty()) {
                // Header per altre lingue
                Surface(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.auto_correct_other_languages),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
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
                    HorizontalDivider()
                }
            }
            
            // Sezione per lingue personalizzate (se presenti)
            // Filtra solo le lingue che non sono standard e non sono già mostrate sopra
            val customLanguages = AutoCorrector.getCustomLanguages()
                .filter { it != systemLocale && it !in otherLanguages }
            if (customLanguages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.auto_correct_custom_languages),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
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
                    HorizontalDivider()
                }
                    }
                }
                }
            }
            
            // Dialog per aggiungere una nuova lingua (fuori dallo Scaffold)
            if (showNewLanguageDialog) {
        AddNewLanguageDialog(
            onDismiss = { showNewLanguageDialog = false },
            onSave = { languageCode, languageName ->
                // Crea un dizionario vuoto per la nuova lingua con il nome salvato
                SettingsManager.saveCustomAutoCorrections(
                    context, 
                    languageCode, 
                    emptyMap(),
                    languageName = languageName
                )
                
                // Ricarica tutte le correzioni (incluso la nuova lingua)
                try {
                    val assets = context.assets
                    AutoCorrector.loadCorrections(assets, context)
                } catch (e: Exception) {
                    // Fallback: carica solo la nuova lingua
                    AutoCorrector.loadCustomCorrections(languageCode, "{}")
                }
                
                // Aggiorna la lista delle lingue disponibili
                allLanguages = AutoCorrector.getAllAvailableLanguages()
                
                // Abilita automaticamente la nuova lingua
                val newSet = if (enabledLanguages.isEmpty()) {
                    setOf(languageCode)
                } else {
                    enabledLanguages + languageCode
                }
                enabledLanguages = newSet
                SettingsManager.setAutoCorrectEnabledLanguages(context, newSet)
                
                showNewLanguageDialog = false
                
                // Naviga alla schermata di modifica della nuova lingua
                onEditLanguage(languageCode)
            },
            existingLanguages = allLanguages
        )
    }
}

