package it.palsoftware.pastiera

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.ui.theme.PastieraTheme
import org.json.JSONObject
import java.io.InputStreamReader
import android.provider.OpenableColumns
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.launch

/**
 * Activity für den Batch-Import von AutoCorrection-Regeln aus JSON-Dateien.
 * 
 * Format erwartet:
 * {
 *   "language": "de",
 *   "name": "Umlaut Autocorrection",
 *   "rules": {
 *     "fuer": "für",
 *     "ueber": "über",
 *     ...
 *   }
 * }
 */
class AutoCorrectionImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            overridePendingTransition(R.anim.slide_in_from_right, 0)
        }
        enableEdgeToEdge()
        setContent {
            PastieraTheme {
                AutoCorrectionImportScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBack = { finish() }
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, R.anim.slide_out_to_right)
    }
}

@Composable
fun AutoCorrectionImportScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedFile by remember { mutableStateOf<ImportFileInfo?>(null) }
    var importInProgress by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<AutoCorrectionImportResult?>(null) }
    var selectedLanguageCode by remember { mutableStateOf<String?>(null) }
    var selectedLanguageName by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val name = context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        cursor.getString(nameIndex)
                    } else "unknown"
                } ?: "unknown"
                
                // Read JSON
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val text = InputStreamReader(input).readText()
                    val json = JSONObject(text)
                    
                    val languageCode = json.optString("language", "de")
                    val languageName = json.optString("name", languageCode)
                    val rules = json.optJSONObject("rules")
                    
                    if (rules == null) {
                        importResult = AutoCorrectionImportResult.Error("Keine 'rules' in JSON gefunden")
                        return@use
                    }
                    
                    val ruleCount = rules.length()
                    selectedFile = ImportFileInfo(
                        fileName = name,
                        languageCode = languageCode,
                        languageName = languageName,
                        ruleCount = ruleCount,
                        rules = rules
                    )
                    // Initialize selected language with JSON values
                    selectedLanguageCode = languageCode
                    selectedLanguageName = languageName
                    importResult = null
                }
            } catch (e: Exception) {
                Log.e("AutoCorrectionImport", "Error reading file", e)
                importResult = AutoCorrectionImportResult.Error("Fehler beim Lesen der Datei: ${e.message}")
            }
        }
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
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = "AutoCorrection Batch-Import",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Instructions
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📖 So funktioniert es:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "1. Wähle eine JSON-Datei mit AutoCorrection-Regeln\n" +
                                "2. Format: { \"language\": \"de\", \"name\": \"Name\", \"rules\": { \"fuer\": \"für\", ... } }\n" +
                                "3. Alle Regeln werden automatisch importiert\n" +
                                "4. Sofort einsatzbereit!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // File selection button
            Button(
                onClick = { filePicker.launch(arrayOf("application/json", "*/*")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !importInProgress
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("JSON-Datei auswählen")
            }
            
            // Selected file info
            if (selectedFile != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Datei: ${selectedFile!!.fileName}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Sprache: ${selectedFile!!.languageName} (${selectedFile!!.languageCode})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Regeln: ${selectedFile!!.ruleCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // Language selector (after file is selected)
            if (selectedFile != null && selectedLanguageCode != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Zielsprache wählen:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        
                        // Language code field
                        OutlinedTextField(
                            value = selectedLanguageCode ?: "",
                            onValueChange = { selectedLanguageCode = it },
                            label = { Text("Sprachcode (z.B. de, en, fr)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        // Language name field
                        OutlinedTextField(
                            value = selectedLanguageName ?: "",
                            onValueChange = { selectedLanguageName = it },
                            label = { Text("Sprachname (z.B. Deutsch, English)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        Text(
                            text = "💡 Tipp: Du kannst die Sprache ändern, z.B. von 'de' zu 'en'",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Import button
            if (selectedFile != null && selectedLanguageCode != null && selectedLanguageName != null) {
                Button(
                    onClick = {
                        importInProgress = true
                        coroutineScope.launch {
                            try {
                                val result = importAutoCorrections(
                                    context,
                                    selectedLanguageCode!!,  // Use selected language, not file language
                                    selectedLanguageName!!,  // Use selected name, not file name
                                    selectedFile!!.rules
                                )
                                importResult = result
                                
                                when (result) {
                                    is AutoCorrectionImportResult.Success -> {
                                        snackbarHostState.showSnackbar(
                                            "✅ ${result.ruleCount} Regeln importiert!"
                                        )
                                    }
                                    is AutoCorrectionImportResult.Error -> {
                                        snackbarHostState.showSnackbar(
                                            "❌ ${result.message}"
                                        )
                                    }
                                }
                            } finally {
                                importInProgress = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = selectedFile != null && 
                              selectedLanguageCode?.isNotBlank() == true && 
                              selectedLanguageName?.isNotBlank() == true && 
                              !importInProgress
                ) {
                    if (importInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (importInProgress) "Importieren..." else "Alle Regeln importieren")
                }
            }
            
            // Import result
            if (importResult != null) {
            if (importResult != null) {
                val result = importResult as? AutoCorrectionImportResult
                if (result != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when (result) {
                                is AutoCorrectionImportResult.Success -> MaterialTheme.colorScheme.tertiaryContainer
                                is AutoCorrectionImportResult.Error -> MaterialTheme.colorScheme.errorContainer
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = when (result) {
                                    is AutoCorrectionImportResult.Success -> "✅ Import erfolgreich!"
                                    is AutoCorrectionImportResult.Error -> "❌ Import fehlgeschlagen"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            when (result) {
                                is AutoCorrectionImportResult.Success -> {
                                    Text(
                                        text = "${result.ruleCount} Regeln wurden hinzugefügt",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                is AutoCorrectionImportResult.Error -> {
                                    Text(
                                        text = result.message,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
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

private data class ImportFileInfo(
    val fileName: String,
    val languageCode: String,
    val languageName: String,
    val ruleCount: Int,
    val rules: JSONObject
)

// Import result types for AutoCorrection
private sealed class AutoCorrectionImportResult {
    data class Success(val ruleCount: Int) : AutoCorrectionImportResult()
    data class Error(val message: String) : AutoCorrectionImportResult()
}

/**
 * Importiert AutoCorrection-Regeln aus JSONObject in SharedPreferences.
 */
private fun importAutoCorrections(
    context: Context,
    languageCode: String,
    languageName: String,
    rulesJson: JSONObject
): AutoCorrectionImportResult {
    return try {
        val corrections = mutableMapOf<String, String>()
        
        // Convert JSONObject to Map
        for (key in rulesJson.keys()) {
            val value = rulesJson.getString(key)
            corrections[key] = value
        }
        
        // Save via SettingsManager
        SettingsManager.saveCustomAutoCorrections(context, languageCode, corrections, languageName)
        
        // Ensure language is enabled
        val enabledLanguages = SettingsManager.getAutoCorrectEnabledLanguages(context).toMutableSet()
        if (!enabledLanguages.contains(languageCode)) {
            enabledLanguages.add(languageCode)
            SettingsManager.setAutoCorrectEnabledLanguages(context, enabledLanguages)
        }
        
        Log.i("AutoCorrectionImport", "Imported ${corrections.size} rules for language $languageCode")
        
        AutoCorrectionImportResult.Success(corrections.size)
    } catch (e: Exception) {
        Log.e("AutoCorrectionImport", "Error importing corrections", e)
        AutoCorrectionImportResult.Error(e.message ?: "Unbekannter Fehler")
    }
}

