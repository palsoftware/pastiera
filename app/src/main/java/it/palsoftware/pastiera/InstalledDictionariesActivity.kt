package it.palsoftware.pastiera

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.ui.theme.PastieraTheme
import java.io.File
import java.io.InputStream
import java.util.Locale
import kotlinx.coroutines.launch
import android.provider.OpenableColumns
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import it.palsoftware.pastiera.core.suggestions.DictionaryIndex

/**
 * Activity that shows the list of serialized dictionaries bundled with the app.
 */
class InstalledDictionariesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            overridePendingTransition(R.anim.slide_in_from_right, 0)
        }
        enableEdgeToEdge()
        setContent {
            PastieraTheme {
                InstalledDictionariesScreen(
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

private data class InstalledDictionary(
    val languageCode: String,
    val displayName: String,
    val fileName: String,
    val source: DictionarySource
)

private enum class DictionarySource {
    Asset,
    Imported
}

@Composable
fun InstalledDictionariesScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var dictionaries by remember { mutableStateOf(loadSerializedDictionaries(context)) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val result = importDictionaryFromSaf(context, uri)
                val message = when (result) {
                    is ImportResult.Success -> {
                        dictionaries = loadSerializedDictionaries(context)
                        context.getString(
                            R.string.installed_dictionaries_import_success,
                            result.fileName
                        )
                    }
                    is ImportResult.InvalidName -> context.getString(R.string.installed_dictionaries_import_invalid_name)
                    is ImportResult.InvalidFormat -> context.getString(R.string.installed_dictionaries_import_invalid_format)
                    is ImportResult.CopyError -> context.getString(R.string.installed_dictionaries_import_failed)
                    ImportResult.UnsupportedUri -> context.getString(R.string.installed_dictionaries_import_failed)
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars),
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
                        text = stringResource(R.string.installed_dictionaries_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f)
                    )
                    IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.installed_dictionaries_import)
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (dictionaries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.installed_dictionaries_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dictionaries, key = { it.fileName }) { dictionary ->
                    InstalledDictionaryItem(dictionary = dictionary)
                }
            }
        }
    }
}

@Composable
private fun InstalledDictionaryItem(dictionary: InstalledDictionary) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Book,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = dictionary.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(
                        R.string.installed_dictionaries_language_code,
                        dictionary.languageCode.uppercase(Locale.getDefault())
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.installed_dictionaries_filename,
                        dictionary.fileName
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (dictionary.source == DictionarySource.Imported) {
                    Text(
                        text = stringResource(R.string.installed_dictionaries_imported_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun loadSerializedDictionaries(context: Context): List<InstalledDictionary> {
    return try {
        val assetFiles = context.assets
            .list("common/dictionaries_serialized")
            ?.filter { it.endsWith("_base.dict") }
            ?: emptyList()

        val importedDir = File(context.filesDir, "dictionaries_serialized")
        val importedFiles = importedDir.listFiles { file ->
            file.isFile && file.name.endsWith(".dict")
        }?.map { it.name } ?: emptyList()

        val allFiles = (assetFiles.map { DictionarySource.Asset to it } +
                importedFiles.map { DictionarySource.Imported to it })
            .distinctBy { it.second.lowercase(Locale.getDefault()) }

        allFiles.map { (source, fileName) ->
            val languageCode = fileName.removeSuffix("_base.dict")
            InstalledDictionary(
                languageCode = languageCode,
                displayName = getLanguageDisplayName(languageCode),
                fileName = fileName,
                source = source
            )
        }.sortedBy { it.displayName.lowercase(Locale.getDefault()) }
    } catch (e: Exception) {
        Log.e("InstalledDictionaries", "Error reading serialized dictionaries", e)
        emptyList()
    }
}

private sealed interface ImportResult {
    data class Success(val fileName: String) : ImportResult
    object InvalidName : ImportResult
    object InvalidFormat : ImportResult
    object CopyError : ImportResult
    object UnsupportedUri : ImportResult
}

@OptIn(ExperimentalSerializationApi::class)
private fun importDictionaryFromSaf(context: Context, uri: android.net.Uri): ImportResult {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)
        } else null
    } ?: return ImportResult.InvalidName
    if (!name.endsWith(".dict", ignoreCase = true) || !name.contains("_base", ignoreCase = true)) {
        return ImportResult.InvalidName
    }

    val destDir = File(context.filesDir, "dictionaries_serialized").apply { mkdirs() }
    val destFile = File(destDir, name)

    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            // Validate by attempting to deserialize
            validateDictionaryStream(input)
        } ?: return ImportResult.CopyError

        // Copy once more because stream was consumed during validation
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return ImportResult.CopyError

        ImportResult.Success(destFile.name)
    } catch (e: SerializationException) {
        Log.e("InstalledDictionaries", "Invalid dictionary format", e)
        ImportResult.InvalidFormat
    } catch (e: Exception) {
        Log.e("InstalledDictionaries", "Error importing dictionary", e)
        ImportResult.CopyError
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun validateDictionaryStream(input: InputStream) {
    val json = Json { ignoreUnknownKeys = true }
    json.decodeFromStream<DictionaryIndex>(input)
}

private fun getLanguageDisplayName(languageCode: String): String {
    return try {
        val locale = Locale.forLanguageTag(languageCode)
        locale.getDisplayLanguage(locale).replaceFirstChar { char ->
            if (char.isLowerCase()) {
                char.titlecase(locale)
            } else {
                char.toString()
            }
        }
    } catch (e: Exception) {
        languageCode.uppercase(Locale.getDefault())
    }
}

