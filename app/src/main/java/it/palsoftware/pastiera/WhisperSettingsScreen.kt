package it.palsoftware.pastiera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.inputmethod.whisper.WhisperModel
import it.palsoftware.pastiera.inputmethod.whisper.WhisperModelDownloader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhisperSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var useWhisper by remember { mutableStateOf(SettingsManager.getUseWhisper(context)) }
    var selectedModel by remember { mutableStateOf(SettingsManager.getWhisperModel(context)) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadedModels by remember { mutableStateOf(setOf<WhisperModel>()) }
    
    val downloader = remember { WhisperModelDownloader(context) }
    
    // Check which models are downloaded
    LaunchedEffect(Unit) {
        downloadedModels = WhisperModel.values().filter { downloader.isModelDownloaded(it) }.toSet()
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
                        text = stringResource(R.string.settings_category_speech_recognition),
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
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Use Whisper Toggle
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.whisper_enabled_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.whisper_enabled_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useWhisper,
                        onCheckedChange = {
                            useWhisper = it
                            SettingsManager.setUseWhisper(context, it)
                        },
                        enabled = downloadedModels.contains(selectedModel)
                    )
                }
            }
            
            HorizontalDivider()
            
            // Model Selection
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.whisper_model_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.whisper_model_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )
                    
                    // Model Cards
                    WhisperModel.values().forEach { model ->
                        val isDownloaded = downloadedModels.contains(model)
                        val isSelected = selectedModel == model
                        
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            onClick = {
                                if (isDownloaded) {
                                    selectedModel = model
                                    SettingsManager.setWhisperModel(context, model)
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        if (isDownloaded) {
                                            selectedModel = model
                                            SettingsManager.setWhisperModel(context, model)
                                        }
                                    },
                                    enabled = isDownloaded
                                )
                                
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp)
                                ) {
                                    Text(
                                        text = model.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = model.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${model.sizeBytes / (1024 * 1024)} MB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    if (isDownloaded) {
                                        Text(
                                            text = stringResource(R.string.whisper_model_status_downloaded),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                                
                                // Download/Delete Button
                                if (isDownloaded) {
                                    IconButton(
                                        onClick = {
                                            downloader.deleteModel(model)
                                            downloadedModels = downloadedModels - model
                                            if (selectedModel == model) {
                                                useWhisper = false
                                                SettingsManager.setUseWhisper(context, false)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (!isDownloading) {
                                                isDownloading = true
                                                scope.launch {
                                                    downloader.downloadModel(
                                                        model,
                                                        object : WhisperModelDownloader.DownloadListener {
                                                            override fun onProgress(bytesDownloaded: Long, totalBytes: Long) {
                                                                downloadProgress = bytesDownloaded.toFloat() / totalBytes
                                                            }
                                                            override fun onComplete(file: java.io.File) {
                                                                isDownloading = false
                                                                downloadedModels = downloadedModels + model
                                                            }
                                                            override fun onError(error: String) {
                                                                isDownloading = false
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        },
                                        enabled = !isDownloading
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CloudDownload,
                                            contentDescription = "Download"
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Download Progress
                    if (isDownloading) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

