package it.palsoftware.pastiera

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.inputmethod.whisper.WhisperModel
import it.palsoftware.pastiera.inputmethod.whisper.WhisperModelDownloader
import it.palsoftware.pastiera.inputmethod.whisper.OpenAiWhisperClient
import it.palsoftware.pastiera.inputmethod.whisper.UsageStatsCard
import kotlinx.coroutines.launch
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhisperSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Navigation state
    var currentScreen by remember { mutableStateOf<WhisperScreen>(WhisperScreen.Main) }
    
    // Engine selection (Meta-Menu)
    var selectedEngine by remember { mutableStateOf(SettingsManager.getWhisperMode(context)) }
    
    // Voice Input Button
    var voiceInputButtonEnabled by remember { mutableStateOf(SettingsManager.getVoiceInputButtonEnabled(context)) }
    
    // OpenRouter settings
    var openRouterApiKey by remember { mutableStateOf(SettingsManager.getOpenRouterApiKey(context)) }
    var openRouterModel by remember { mutableStateOf(SettingsManager.getOpenRouterModel(context)) }
    var openRouterLanguage by remember { mutableStateOf(SettingsManager.getOpenRouterLanguage(context)) }
    
    // Local Whisper settings
    var useWhisper by remember { mutableStateOf(SettingsManager.getUseWhisper(context)) }
    var selectedModel by remember { mutableStateOf(SettingsManager.getWhisperModel(context)) }
    var downloadingModel by remember { mutableStateOf<WhisperModel?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadedModels by remember { mutableStateOf(setOf<WhisperModel>()) }
    
    // OpenAI API settings
    var useOpenAiApi by remember { mutableStateOf(SettingsManager.getUseOpenAiApi(context)) }
    var openAiApiKey by remember { mutableStateOf(SettingsManager.getOpenAiApiKey(context)) }
    var openAiModel by remember { mutableStateOf(SettingsManager.getOpenAiModel(context)) }
    var openAiLanguage by remember { mutableStateOf(SettingsManager.getOpenAiLanguage(context)) }
    var openAiPrompt by remember { mutableStateOf(SettingsManager.getOpenAiPrompt(context)) }
    
    val downloader = remember { WhisperModelDownloader(context) }
    
    // Check which models are downloaded
    LaunchedEffect(Unit) {
        downloadedModels = WhisperModel.values().filter { downloader.isModelDownloaded(it) }.toSet()
    }
    
    // Navigation handler
    fun navigateTo(screen: WhisperScreen) {
        currentScreen = screen
    }
    
    fun navigateBack() {
        if (currentScreen != WhisperScreen.Main) {
            currentScreen = WhisperScreen.Main
        } else {
            onBack()
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
                    IconButton(onClick = { navigateBack() }) {
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
        ) {
            // Screen Content
            when (currentScreen) {
                WhisperScreen.Main -> WhisperMainScreen(
                    modifier = Modifier.fillMaxSize(),
                    selectedEngine = selectedEngine,
                    onEngineSelected = { engine ->
                        selectedEngine = engine
                        SettingsManager.setWhisperMode(context, engine)
                    },
                    voiceInputButtonEnabled = voiceInputButtonEnabled,
                    onVoiceInputButtonEnabledChange = { enabled ->
                        voiceInputButtonEnabled = enabled
                        SettingsManager.setVoiceInputButtonEnabled(context, enabled)
                    },
                    onLocalSettingsClick = { navigateTo(WhisperScreen.Local) },
                    onOpenAiSettingsClick = { navigateTo(WhisperScreen.OpenAi) },
                    onOpenRouterSettingsClick = { navigateTo(WhisperScreen.OpenRouter) }
                )
                
                WhisperScreen.Local -> WhisperLocalTab(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    selectedModel = selectedModel,
                    onModelSelected = {
                        selectedModel = it
                        SettingsManager.setWhisperModel(context, it)
                    },
                    downloadedModels = downloadedModels,
                    downloadingModel = downloadingModel,
                    onDownloadingModelChange = { downloadingModel = it },
                    downloadProgress = downloadProgress,
                    onDownloadProgressChange = { downloadProgress = it },
                    downloader = downloader,
                    onDownloadedModelsChange = { downloadedModels = it },
                    scope = scope,
                    context = context
                )
                
                WhisperScreen.OpenRouter -> WhisperOpenRouterTab(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    apiKey = openRouterApiKey,
                    onApiKeyChange = {
                        openRouterApiKey = it
                        SettingsManager.setOpenRouterApiKey(context, it)
                    },
                    model = openRouterModel,
                    onModelChange = {
                        openRouterModel = it
                        SettingsManager.setOpenRouterModel(context, it)
                    },
                    language = openRouterLanguage,
                    onLanguageChange = {
                        openRouterLanguage = it
                        SettingsManager.setOpenRouterLanguage(context, it)
                    },
                    context = context
                )
                
                WhisperScreen.OpenAi -> WhisperOpenAiTab(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    apiKey = openAiApiKey,
                    onApiKeyChange = {
                        openAiApiKey = it
                        SettingsManager.setOpenAiApiKey(context, it)
                    },
                    model = openAiModel,
                    onModelChange = {
                        openAiModel = it
                        SettingsManager.setOpenAiModel(context, it)
                    },
                    language = openAiLanguage,
                    onLanguageChange = {
                        openAiLanguage = it
                        SettingsManager.setOpenAiLanguage(context, it)
                    },
                    prompt = openAiPrompt,
                    onPromptChange = {
                        openAiPrompt = it
                        SettingsManager.setOpenAiPrompt(context, it)
                    },
                    context = context
                )
            }
        }
    }
}

@Composable
private fun WhisperMainScreen(
    modifier: Modifier = Modifier,
    selectedEngine: String,
    onEngineSelected: (String) -> Unit,
    voiceInputButtonEnabled: Boolean,
    onVoiceInputButtonEnabledChange: (Boolean) -> Unit,
    onLocalSettingsClick: () -> Unit,
    onOpenAiSettingsClick: () -> Unit,
    onOpenRouterSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Voice Input Button Option
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = "Voice Input Microphone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Show microphone button below text input",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Switch(
                    checked = voiceInputButtonEnabled,
                    onCheckedChange = onVoiceInputButtonEnabledChange
                )
            }
        }
        
        // Spacer
        Spacer(modifier = Modifier.height(8.dp))
        
        // Info
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Speech Recognition Engine",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Choose your preferred speech-to-text engine. Selection is automatically enabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Google Stock Option
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onEngineSelected("google")
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(
                    selected = selectedEngine == "google",
                    onClick = { onEngineSelected("google") }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google Speech API",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Android stock speech recognition • Always available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // OpenAI Whisper API Option
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onEngineSelected("api")
                    onOpenAiSettingsClick()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(
                    selected = selectedEngine == "api",
                    onClick = { onEngineSelected("api") }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OpenAI Whisper API",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Cloud-based • Premium quality • Requires API key & internet",
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
        
        // OpenRouter Audio API Option
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onEngineSelected("openrouter")
                    onOpenRouterSettingsClick()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(
                    selected = selectedEngine == "openrouter",
                    onClick = { onEngineSelected("openrouter") }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "OpenRouter Audio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Multiple models • Cloud-based • Requires API key & internet",
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
        
        // Spacer before coming-soon features
        Spacer(modifier = Modifier.height(8.dp))
        
        // Local Whisper Option (Coming Soon - Disabled)
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(0.6f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(
                    selected = false,
                    onClick = {},
                    enabled = false
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Whisper (Local ONNX)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Offline speech recognition • Coming soon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Local Whisper WIP Info
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Local Whisper – Work in Progress",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Local Whisper support is currently being developed and tested. We're working on integrating DocWolle's optimized ONNX models.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                // Clickable link to download models
                Text(
                    text = "Download DocWolle Whisper ONNX Models",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://huggingface.co/DocWolle/whisperOnnx")
                            )
                            context.startActivity(intent)
                        }
                        .padding(4.dp),
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                )
            }
        }
    }
}

@Composable
private fun WhisperLocalTab(
    modifier: Modifier = Modifier,
    selectedModel: WhisperModel,
    onModelSelected: (WhisperModel) -> Unit,
    downloadedModels: Set<WhisperModel>,
    downloadingModel: WhisperModel?,
    onDownloadingModelChange: (WhisperModel?) -> Unit,
    downloadProgress: Float,
    onDownloadProgressChange: (Float) -> Unit,
    downloader: WhisperModelDownloader,
    onDownloadedModelsChange: (Set<WhisperModel>) -> Unit,
    scope: CoroutineScope,
    context: android.content.Context
) {
    Column(modifier = modifier.padding(16.dp)) {
        // Model Selection
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
            val isDownloadingThis = downloadingModel == model
            
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                onClick = {
                    if (isDownloaded) {
                        onModelSelected(model)
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
                                onModelSelected(model)
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
                        } else if (isDownloadingThis) {
                            Text(
                                text = stringResource(R.string.whisper_model_download_in_progress) + " ${(downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            )
                        }
                    }
                    
                    // Download/Delete Button
                    if (isDownloaded) {
                        IconButton(
                            onClick = {
                                downloader.deleteModel(model)
                                onDownloadedModelsChange(downloadedModels - model)
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
                                if (downloadingModel == null) {
                                    onDownloadingModelChange(model)
                                    onDownloadProgressChange(0f)
                                    scope.launch {
                                        try {
                                            android.util.Log.d("WhisperSettings", "Starting download for ${model.displayName}")
                                            
                                            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                downloader.downloadModel(
                                                    model,
                                                    object : WhisperModelDownloader.DownloadListener {
                                                        override fun onProgress(bytesDownloaded: Long, totalBytes: Long) {
                                                            onDownloadProgressChange(bytesDownloaded.toFloat() / totalBytes)
                                                        }
                                                        override fun onComplete(file: java.io.File) {
                                                            android.util.Log.d("WhisperSettings", "Download complete: ${file.absolutePath}")
                                                        }
                                                        override fun onError(error: String) {
                                                            android.util.Log.e("WhisperSettings", "Download error: $error")
                                                        }
                                                    }
                                                )
                                            }
                                            
                                            onDownloadingModelChange(null)
                                            if (result.isSuccess) {
                                                onDownloadedModelsChange(downloadedModels + model)
                                                android.widget.Toast.makeText(
                                                    context,
                                                    context.getString(R.string.whisper_model_download_complete),
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "${context.getString(R.string.whisper_model_download_failed)}: $errorMsg",
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        } catch (e: Exception) {
                                            onDownloadingModelChange(null)
                                            android.widget.Toast.makeText(
                                                context,
                                                "Error: ${e.message}",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            },
                            enabled = downloadingModel == null
                        ) {
                            if (isDownloadingThis) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.CloudDownload,
                                    contentDescription = "Download"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WhisperOpenAiTab(
    modifier: Modifier = Modifier,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    prompt: String,
    onPromptChange: (String) -> Unit,
    context: android.content.Context
) {
    Column(modifier = modifier.padding(16.dp)) {
        // Info
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.openai_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // API Key
        Text(
            text = stringResource(R.string.openai_api_key_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        var isValidating by remember { mutableStateOf(false) }
        var isOpenAiValid by remember { mutableStateOf(false) }
        val openAiScope = rememberCoroutineScope()
        
        LaunchedEffect(apiKey) {
            if (apiKey.isNotEmpty() && apiKey.length > 20) {
                isValidating = true
                openAiScope.launch(kotlinx.coroutines.Dispatchers.IO) {  // RUN ON IO THREAD!
                    try {
                        Log.d("OpenAiValidation", "Starting validation for API key length: ${apiKey.length}")
                        val client = OpenAiWhisperClient(apiKey)
                        val result = client.validateApiKey()
                        Log.d("OpenAiValidation", "Validation result: ${result.isSuccess}")
                        
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            isOpenAiValid = result.isSuccess
                            isValidating = false
                        }
                    } catch (e: Exception) {
                        Log.e("OpenAiValidation", "Validation error: ${e.message}")
                        Log.e("OpenAiValidation", "Stack trace: ${e.stackTraceToString()}")
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            isOpenAiValid = false
                            isValidating = false
                        }
                    }
                }
            } else {
                isOpenAiValid = false
            }
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.openai_api_key_placeholder)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                )
            )
            
            // Status icon - only show when validated
            if (isOpenAiValid) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "API Key valid",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
            } else if (isValidating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Model Selection
        Text(
            text = stringResource(R.string.openai_model_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp)
        )
        
        val models = listOf(
            "gpt-4o-transcribe" to "GPT-4o Transcribe (Best Quality)",
            "gpt-4o-mini-transcribe" to "GPT-4o Mini Transcribe (Fast)",
            "whisper-1" to "Whisper-1 (Compatible)"
        )
        
        models.forEach { (modelId, modelLabel) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = model == modelId,
                    onClick = { onModelChange(modelId) }
                )
                Text(
                    text = modelLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Language
        Text(
            text = stringResource(R.string.openai_language_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp)
        )
        
        OutlinedTextField(
            value = language,
            onValueChange = onLanguageChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            placeholder = { Text("e.g., de, en, fr, it") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Prompt
        Text(
            text = stringResource(R.string.openai_prompt_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = stringResource(R.string.openai_prompt_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp),
            placeholder = { Text(stringResource(R.string.openai_prompt_placeholder)) },
            maxLines = 4
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Usage Statistics
        UsageStatsCard(
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun WhisperOpenRouterTab(
    modifier: Modifier = Modifier,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    context: android.content.Context
) {
    var availableModels by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isValidated by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Load models when API key changes
    LaunchedEffect(apiKey) {
        if (apiKey.isNotEmpty() && apiKey.length > 10) {
            isLoadingModels = true
            loadError = null
            isValidated = false
            Log.d("WhisperOpenRouter", "=== LaunchedEffect triggered ===")
            Log.d("WhisperOpenRouter", "Validating and loading models for API key: ${apiKey.take(10)}...")
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {  // RUN ON IO THREAD!
                try {
                    Log.d("WhisperOpenRouter", "About to validate API key (length: ${apiKey.length})")
                    
                    // First validate the API key with a simple request
                    val isValid = validateOpenRouterApiKey(apiKey)
                    Log.d("WhisperOpenRouter", "Validation result: $isValid")
                    
                    if (!isValid) {
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            isValidated = false
                            loadError = "Invalid API key. Please check your OpenRouter API key."
                            isLoadingModels = false
                        }
                        Log.e("WhisperOpenRouter", "API key validation failed - stopping here")
                        return@launch
                    }
                    
                    Log.d("WhisperOpenRouter", "API Key is VALID - proceeding to fetch models")
                    
                    // Now fetch models from OpenRouter API
                    val models = fetchOpenRouterModels(apiKey)
                    Log.d("WhisperOpenRouter", "Fetched ${models.size} audio-capable models")
                    
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        isValidated = true
                        availableModels = models
                        if (models.isEmpty()) {
                            loadError = "No audio-capable models found. This might be a temporary API issue."
                            Log.w("WhisperOpenRouter", "API returned 0 audio models")
                        } else {
                            loadError = null
                        }
                        isLoadingModels = false
                    }
                } catch (e: Exception) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        isValidated = false
                        loadError = "Error loading models: ${e.message}"
                        isLoadingModels = false
                    }
                    Log.e("WhisperOpenRouter", "EXCEPTION during model loading: ${e.message}")
                    Log.e("WhisperOpenRouter", "Stack trace: ${e.stackTraceToString()}")
                }
            }
        } else {
            Log.d("WhisperOpenRouter", "API key too short or empty - not validating")
            availableModels = emptyList()
            loadError = null
            isValidated = false
        }
    }
    
    Column(modifier = modifier.padding(16.dp)) {
        // Info
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "OpenRouter provides access to multiple speech recognition models with transparent pricing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Model Compatibility Note
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Recommended: Google Flash models are tested and working. Other models support audio but may have compatibility issues.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // API Key
        Text(
            text = "API Key",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Enter your OpenRouter API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            
            // Status icon - only show when validated
            if (isValidated) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "API Key valid",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
            } else if (isLoadingModels) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Model Selection
        Text(
            text = "Model",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        
        if (apiKey.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Enter API key to load available models",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else if (isLoadingModels) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Loading available models...",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else if (loadError != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = loadError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else if (availableModels.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableModels.forEach { (modelId, modelLabel) ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModelChange(modelId) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = model == modelId,
                                onClick = { onModelChange(modelId) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = modelLabel.split(" • ")[0],
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (modelLabel.contains(" • ")) {
                                    Text(
                                        text = modelLabel.split(" • ", limit = 2)[1],
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Language
        Text(
            text = "Language",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        OutlinedTextField(
            value = language,
            onValueChange = onLanguageChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            placeholder = { Text("e.g. de, en, es") },
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Usage Statistics
        UsageStatsCard(
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private suspend fun validateOpenRouterApiKey(apiKey: String): Boolean {
    return try {
        Log.d("OpenRouterValidation", "=== Starting validation ===")
        Log.d("OpenRouterValidation", "API Key length: ${apiKey.length}")
        
        val url = "https://openrouter.ai/api/v1/models?limit=1"
        Log.d("OpenRouterValidation", "URL: $url")
        
        val request = okhttp3.Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        
        Log.d("OpenRouterValidation", "Request built")
        
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        Log.d("OpenRouterValidation", "Client created")
        
        Log.d("OpenRouterValidation", "Executing request...")
        val response = client.newCall(request).execute()
        Log.d("OpenRouterValidation", "Response received! Code: ${response.code}")
        
        val isValid = response.isSuccessful
        Log.d("OpenRouterValidation", "Is successful: $isValid (${response.code})")
        
        if (!isValid) {
            val errorBody = try {
                response.body?.string()
            } catch (e: Exception) {
                "Could not read error body: ${e.message}"
            }
            Log.e("OpenRouterValidation", "API Error - Status ${response.code}: $errorBody")
        } else {
            Log.d("OpenRouterValidation", "✓ API Key is VALID")
        }
        
        response.close()
        isValid
    } catch (e: Exception) {
        Log.e("OpenRouterValidation", "EXCEPTION: ${e::class.simpleName} - ${e.message}")
        Log.e("OpenRouterValidation", "Full stack: ${e.stackTraceToString()}")
        false
    }
}

private suspend fun fetchOpenRouterModels(apiKey: String): List<Pair<String, String>> {
    return try {
        Log.d("OpenRouterModels", "=== Starting model fetch ===")
        
        val url = "https://openrouter.ai/api/v1/models"
        Log.d("OpenRouterModels", "URL: $url")
        Log.d("OpenRouterModels", "API Key (first 20 chars): ${apiKey.take(20)}...")
        
        val request = okhttp3.Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        Log.d("OpenRouterModels", "Sending request...")
        val response = client.newCall(request).execute()
        Log.d("OpenRouterModels", "Response code: ${response.code}")
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            Log.e("OpenRouterModels", "API Error ${response.code}: $errorBody")
            return emptyList()
        }
        
        val responseBody = response.body?.string()
        if (responseBody == null) {
            Log.e("OpenRouterModels", "Response body is null")
            return emptyList()
        }
        
        Log.d("OpenRouterModels", "Response body length: ${responseBody.length}")
        
        val jsonObject = org.json.JSONObject(responseBody)
        val models = jsonObject.optJSONArray("data")
        
        if (models == null) {
            Log.e("OpenRouterModels", "No 'data' array in response. Keys: ${jsonObject.keys().asSequence().toList()}")
            return emptyList()
        }
        
        Log.d("OpenRouterModels", "Total models in response: ${models.length()}")
        
        val audioModels = mutableListOf<Pair<String, String>>()
        var skipped = 0
        var processedCount = 0
        
        for (i in 0 until models.length()) {
            try {
                val model = models.getJSONObject(i)
                val id = model.getString("id")
                val name = model.optString("name", id)
                
                processedCount++
                
                // Get architecture
                val architecture = model.optJSONObject("architecture")
                if (architecture == null) {
                    skipped++
                    continue
                }
                
                // Get input modalities
                val inputModalities = architecture.optJSONArray("input_modalities")
                if (inputModalities == null) {
                    skipped++
                    continue
                }
                
                // Check if "audio" is in input_modalities
                var supportsAudio = false
                for (j in 0 until inputModalities.length()) {
                    val modality = inputModalities.optString(j, "")
                    if (modality == "audio") {
                        supportsAudio = true
                        break
                    }
                }
                
                if (supportsAudio) {
                    val pricing = model.optJSONObject("pricing")
                    
                    // Check for audio input token pricing first
                    var audioInputPrice = pricing?.optString("audio", "")
                    
                    // Fallback to regular input tokens (prompt) if no audio-specific pricing
                    if (audioInputPrice.isNullOrEmpty()) {
                        audioInputPrice = pricing?.optString("prompt", "0") ?: "0"
                    }
                    
                    // Format label with model name and pricing
                    // Note: OpenRouter API returns prices per token (e.g., "0.0000007")
                    // We need to multiply by 1,000,000 to get price per million tokens
                    val label = try {
                        val pricePerToken = audioInputPrice?.toDouble() ?: 0.0
                        val pricePerMillionTokens = pricePerToken * 1_000_000
                        if (pricePerMillionTokens > 0) {
                            // Format to 2 decimal places for display
                            "$name • \$${"%.2f".format(pricePerMillionTokens)}/M audio tokens"
                        } else {
                            name
                        }
                    } catch (e: NumberFormatException) {
                        Log.e("OpenRouterModels", "Cannot parse price '$audioInputPrice' as Double")
                        name
                    }
                    
                    Log.d("OpenRouterModels", "✓ Found audio model: $id with pricing: $audioInputPrice per token")
                    audioModels.add(id to label)
                }
            } catch (e: Exception) {
                Log.e("OpenRouterModels", "Error processing model at index $i: ${e.message}")
            }
        }
        
        Log.d("OpenRouterModels", "=== Model fetch complete ===")
        Log.d("OpenRouterModels", "Processed: $processedCount, Skipped: $skipped, Found audio: ${audioModels.size}")
        
        // Sort models: Flash variants first, then others
        audioModels.sortedWith(compareBy<Pair<String, String>> { (id, _) ->
            when {
                id.contains("flash", ignoreCase = true) -> 0  // Flash models first
                id.contains("2.0", ignoreCase = true) -> 1     // Then 2.0 models
                else -> 2                                       // Everything else last
            }
        }.thenBy { it.first })
    } catch (e: Exception) {
        Log.e("OpenRouterModels", "Fatal error during model fetch: ${e.message}")
        Log.e("OpenRouterModels", "Stack trace: ${e.stackTraceToString()}")
        emptyList()
    }
}

private enum class WhisperScreen {
    Main,           // Engine selection
    Local,          // Local Whisper settings
    OpenAi,         // OpenAI API settings
    OpenRouter      // OpenRouter API settings
}

