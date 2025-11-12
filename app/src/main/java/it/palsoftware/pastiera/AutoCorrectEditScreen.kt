package it.palsoftware.pastiera

import android.content.Context
import android.content.res.AssetManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import it.palsoftware.pastiera.inputmethod.AutoCorrector
import it.palsoftware.pastiera.R
import org.json.JSONObject
import java.util.Locale

/**
 * Schermata per modificare le correzioni di una lingua specifica.
 * Mostra le correzioni in formato "originale -> corretta" e permette di modificarle.
 */
@Composable
fun AutoCorrectEditScreen(
    languageCode: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // Carica le correzioni (prima quelle personalizzate, poi quelle di default)
    var corrections by remember {
        mutableStateOf(loadCorrectionsForLanguage(context, languageCode))
    }
    
    // Stato per il dialog di aggiunta/modifica
    var showAddDialog by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<String?>(null) }
    
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
                        text = getLanguageDisplayName(context, languageCode),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.auto_correct_add_correction)
                )
            }
        }
        ) { paddingValues ->
            AnimatedContent(
                targetState = Unit,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "auto_correct_edit_animation"
            ) {
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                ) {
            // Header con descrizione
            Surface(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.auto_correct_edit_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            HorizontalDivider()
            
            // Lista delle correzioni
            if (corrections.isEmpty()) {
                // Messaggio quando non ci sono correzioni
                Surface(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.auto_correct_no_corrections),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Mostra le correzioni ordinate alfabeticamente
                val sortedCorrections = corrections.toList().sortedBy { it.first }
                
                sortedCorrections.forEach { (original, corrected) ->
                    CorrectionItem(
                        original = original,
                        corrected = corrected,
                        onEdit = {
                            editingKey = original
                            showAddDialog = true
                        },
                        onDelete = {
                            corrections = corrections - original
                            saveCorrections(context, languageCode, corrections, null)
                            // Ricarica tutte le correzioni (incluso le nuove lingue)
                            // Usa un context che permetta di accedere agli assets
                            try {
                                val assets = context.assets
                                AutoCorrector.loadCorrections(assets, context)
                            } catch (e: Exception) {
                                // Fallback: ricarica solo questa lingua
                                AutoCorrector.loadCustomCorrections(
                                    languageCode,
                                    correctionsToJson(corrections)
                                )
                            }
                        }
                    )
                    HorizontalDivider()
                }
                }
            }
        }
    }
    
    // Dialog per aggiungere/modificare una correzione
    if (showAddDialog) {
        AddCorrectionDialog(
            originalKey = editingKey,
            originalValue = editingKey?.let { corrections[it] },
            onDismiss = {
                showAddDialog = false
                editingKey = null
            },
            onSave = { original, corrected ->
                val newCorrections = corrections.toMutableMap()
                if (editingKey != null && editingKey != original) {
                    // Rimuovi la vecchia chiave se è stata modificata
                    newCorrections.remove(editingKey)
                }
                newCorrections[original.lowercase()] = corrected
                corrections = newCorrections
                saveCorrections(context, languageCode, corrections, null)
                // Ricarica tutte le correzioni (incluso le nuove lingue)
                try {
                    val assets = context.assets
                    AutoCorrector.loadCorrections(assets, context)
                } catch (e: Exception) {
                    // Fallback: ricarica solo questa lingua
                    AutoCorrector.loadCustomCorrections(
                        languageCode,
                        correctionsToJson(corrections)
                    )
                }
                showAddDialog = false
                editingKey = null
            }
        )
    }
}

@Composable
private fun CorrectionItem(
    original: String,
    corrected: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Colonna originale
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = original,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Freccia
            Text(
                text = "→",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            
            // Colonna corretta
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = corrected,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // Pulsante elimina
            IconButton(
                onClick = onDelete,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.auto_correct_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AddCorrectionDialog(
    originalKey: String?,
    originalValue: String?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var originalText by remember { mutableStateOf(originalKey ?: "") }
    var correctedText by remember { mutableStateOf(originalValue ?: "") }
    
    // Aggiorna i campi quando cambia originalKey
    LaunchedEffect(originalKey) {
        originalText = originalKey ?: ""
        correctedText = originalValue ?: ""
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (originalKey != null) {
                    stringResource(R.string.auto_correct_edit_correction)
                } else {
                    stringResource(R.string.auto_correct_add_correction)
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = originalText,
                    onValueChange = { originalText = it },
                    label = { Text(stringResource(R.string.auto_correct_original)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = correctedText,
                    onValueChange = { correctedText = it },
                    label = { Text(stringResource(R.string.auto_correct_corrected)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (originalText.isNotBlank() && correctedText.isNotBlank()) {
                        onSave(originalText.trim(), correctedText.trim())
                    }
                },
                enabled = originalText.isNotBlank() && correctedText.isNotBlank()
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
 * Carica le correzioni per una lingua specifica.
 * Prima carica quelle personalizzate, poi quelle di default dalle assets.
 */
private fun loadCorrectionsForLanguage(context: Context, languageCode: String): Map<String, String> {
    val corrections = mutableMapOf<String, String>()
    
    // Prima carica le correzioni personalizzate (se esistono)
    val customCorrections = SettingsManager.getCustomAutoCorrections(context, languageCode)
    if (customCorrections.isNotEmpty()) {
        corrections.putAll(customCorrections)
    }
    
    // Poi carica quelle di default dalle assets (solo se non ci sono personalizzazioni)
    if (corrections.isEmpty()) {
        try {
            val fileName = "auto_corrections_$languageCode.json"
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = jsonObject.getString(key)
                corrections[key] = value
            }
        } catch (e: Exception) {
            // File non trovato o errore di parsing
        }
    }
    
    return corrections
}

/**
 * Salva le correzioni personalizzate per una lingua.
 */
private fun saveCorrections(
    context: Context, 
    languageCode: String, 
    corrections: Map<String, String>,
    languageName: String? = null
) {
    SettingsManager.saveCustomAutoCorrections(context, languageCode, corrections, languageName)
}

/**
 * Converte una mappa di correzioni in JSON string.
 */
private fun correctionsToJson(corrections: Map<String, String>): String {
    val jsonObject = JSONObject()
    corrections.forEach { (key, value) ->
        jsonObject.put(key, value)
    }
    return jsonObject.toString()
}

/**
 * Ottiene il nome visualizzato per una lingua.
 */
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

