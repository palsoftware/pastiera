package it.palsoftware.pastiera

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Delete
import android.content.Intent
import android.content.Context
import it.palsoftware.pastiera.core.suggestions.UserDictionaryStore
import androidx.compose.ui.graphics.StrokeCap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import it.palsoftware.pastiera.core.suggestions.CorpusImporter
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import it.palsoftware.pastiera.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * Auto-correction category screen.
 */
@Composable
fun AutoCorrectionCategoryScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var autoCorrectEnabled by remember {
        mutableStateOf(SettingsManager.getAutoCorrectEnabled(context))
    }
    var experimentalSuggestionsEnabled by remember {
        mutableStateOf(SettingsManager.isExperimentalSuggestionsEnabled(context))
    }
    var suggestionsEnabled by remember {
        mutableStateOf(SettingsManager.getSuggestionsEnabled(context))
    }
    var accentMatchingEnabled by remember {
        mutableStateOf(SettingsManager.getAccentMatchingEnabled(context))
    }
    var autoReplaceOnSpaceEnter by remember {
        mutableStateOf(SettingsManager.getAutoReplaceOnSpaceEnter(context))
    }
    var maxAutoReplaceDistance by remember {
        mutableStateOf(SettingsManager.getMaxAutoReplaceDistance(context))
    }
    var useKeyboardProximity by remember {
        mutableStateOf(SettingsManager.getUseKeyboardProximity(context))
    }
    var useEditTypeRanking by remember {
        mutableStateOf(SettingsManager.getUseEditTypeRanking(context))
    }
    var navigationDirection by remember { mutableStateOf(LocalNavigationDirection.Push) }
    val navigationStack = remember {
        mutableStateListOf<AutoCorrectionDestination>(AutoCorrectionDestination.Main)
    }
    val currentDestination by remember {
        derivedStateOf { navigationStack.last() }
    }
    
    fun navigateTo(destination: AutoCorrectionDestination) {
        navigationDirection = LocalNavigationDirection.Push
        navigationStack.add(destination)
    }
    
    fun navigateBack() {
        if (navigationStack.size > 1) {
            navigationDirection = LocalNavigationDirection.Pop
            navigationStack.removeAt(navigationStack.lastIndex)
        } else {
            onBack()
        }
    }
    
    BackHandler { navigateBack() }
    
    AnimatedContent(
        targetState = currentDestination,
        transitionSpec = {
            if (navigationDirection == LocalNavigationDirection.Push) {
                // Forward navigation: new screen enters from right, old screen exits to left
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(250)
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = tween(250)
                )
            } else {
                // Back navigation: current screen exits to right, previous screen enters from left
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth },
                    animationSpec = tween(250)
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(250)
                )
            }
        },
        label = "auto_correction_navigation",
        contentKey = { destination ->
            when (destination) {
                is AutoCorrectionDestination.Edit -> "auto_correct_edit_${destination.languageCode}"
                else -> destination::class
            }
        }
    ) { destination ->
        when (destination) {
            AutoCorrectionDestination.Main -> {
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
                                    text = stringResource(R.string.settings_category_auto_correction),
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
                        // Auto-Correction
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
                                    imageVector = Icons.Filled.TextFields,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.auto_correct_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                                Switch(
                                    checked = autoCorrectEnabled,
                                    onCheckedChange = { enabled ->
                                        autoCorrectEnabled = enabled
                                        SettingsManager.setAutoCorrectEnabled(context, enabled)
                                    }
                                )
                            }
                        }

                        // Auto-Correction Languages (only if auto-correction is enabled)
                        if (autoCorrectEnabled) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .clickable { navigateTo(AutoCorrectionDestination.Settings) }
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.auto_correct_languages_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
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

                        // Experimental suggestions master toggle
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
                                        imageVector = Icons.Filled.Code,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.experimental_suggestions_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = stringResource(R.string.experimental_suggestions_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = experimentalSuggestionsEnabled,
                                        onCheckedChange = { enabled ->
                                            experimentalSuggestionsEnabled = enabled
                                            SettingsManager.setExperimentalSuggestionsEnabled(context, enabled)
                                            if (enabled && !suggestionsEnabled) {
                                                suggestionsEnabled = true
                                                SettingsManager.setSuggestionsEnabled(context, true)
                                            }
                                        }
                                    )
                                }
                            }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { navigateTo(AutoCorrectionDestination.UserDictionary) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.auto_correct_manage_user_dict_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { navigateTo(AutoCorrectionDestination.CorpusImport) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.UploadFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.user_dict_import_corpus_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = stringResource(R.string.user_dict_import_corpus_description),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

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
                                        imageVector = Icons.Filled.TextFields,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.auto_correct_suggestions_toggle_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1
                                        )
                                    }
                                    Switch(
                                        checked = suggestionsEnabled,
                                        onCheckedChange = { enabled ->
                                            suggestionsEnabled = enabled
                                            SettingsManager.setSuggestionsEnabled(context, enabled)
                                        },
                                        enabled = experimentalSuggestionsEnabled
                                    )
                                }
                            }

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
                                    imageVector = Icons.Filled.TextFields,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.auto_correct_accent_matching_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                                Switch(
                                    checked = accentMatchingEnabled,
                                    onCheckedChange = { enabled ->
                                        accentMatchingEnabled = enabled
                                        SettingsManager.setAccentMatchingEnabled(context, enabled)
                                    },
                                    enabled = experimentalSuggestionsEnabled
                                )
                            }
                        }

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
                                    imageVector = Icons.Filled.TextFields,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.auto_correct_keyboard_proximity_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.auto_correct_keyboard_proximity_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                                Switch(
                                    checked = useKeyboardProximity,
                                    onCheckedChange = { enabled ->
                                        useKeyboardProximity = enabled
                                        SettingsManager.setUseKeyboardProximity(context, enabled)
                                    },
                                    enabled = experimentalSuggestionsEnabled
                                )
                            }
                        }

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
                                    imageVector = Icons.Filled.TextFields,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.auto_correct_edit_type_ranking_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.auto_correct_edit_type_ranking_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Switch(
                                    checked = useEditTypeRanking,
                                    onCheckedChange = { enabled ->
                                        useEditTypeRanking = enabled
                                        SettingsManager.setUseEditTypeRanking(context, enabled)
                                    },
                                    enabled = experimentalSuggestionsEnabled
                                )
                            }
                        }

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
                                    imageVector = Icons.Filled.TextFields,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.auto_correct_auto_replace_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                                Switch(
                                    checked = autoReplaceOnSpaceEnter,
                                    onCheckedChange = { enabled ->
                                        autoReplaceOnSpaceEnter = enabled
                                        SettingsManager.setAutoReplaceOnSpaceEnter(context, enabled)
                                    }
                                )
                            }
                        }

                        // Max auto-replace distance slider (only shown when auto-replace is enabled)
                        if (autoReplaceOnSpaceEnter) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.auto_correct_max_distance_title),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = if (maxAutoReplaceDistance == 0) {
                                                stringResource(R.string.auto_correct_max_distance_off)
                                            } else {
                                                maxAutoReplaceDistance.toString()
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Slider(
                                        value = maxAutoReplaceDistance.toFloat(),
                                        onValueChange = { value ->
                                            val newValue = value.toInt().coerceIn(0, 3)
                                            maxAutoReplaceDistance = newValue
                                            SettingsManager.setMaxAutoReplaceDistance(context, newValue)
                                        },
                                        valueRange = 0f..3f,
                                        steps = 2, // 0, 1, 2, 3 = 3 steps
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = stringResource(R.string.auto_correct_max_distance_description),
                                        style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Reset Dictionary Option
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        
                        var showResetDialog by remember { mutableStateOf(false) }
                        
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clickable { showResetDialog = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.user_dict_reset_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.user_dict_reset_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        if (showResetDialog) {
                            val userStore = remember { it.palsoftware.pastiera.core.suggestions.UserDictionaryStore() }
                            AlertDialog(
                                onDismissRequest = { showResetDialog = false },
                                title = { Text(stringResource(R.string.user_dict_reset_confirm_title)) },
                                text = { Text(stringResource(R.string.user_dict_reset_confirm_message)) },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            userStore.resetAll(context)
                                            showResetDialog = false
                                            // Notify IME to reload dictionary
                                            val intent = Intent("it.palsoftware.pastiera.ACTION_USER_DICTIONARY_UPDATED").apply {
                                                setPackage(context.packageName)
                                            }
                                            context.sendBroadcast(intent)
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text(stringResource(R.string.user_dict_reset_confirm_button))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showResetDialog = false }) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                }
                            )
                        }
                    }
                }
            }
            }
            
            AutoCorrectionDestination.Settings -> {
                AutoCorrectSettingsScreen(
                    modifier = modifier,
                    onBack = { navigateBack() },
                    onEditLanguage = { languageCode ->
                        navigateTo(AutoCorrectionDestination.Edit(languageCode))
                    }
                )
            }

            is AutoCorrectionDestination.Edit -> {
                AutoCorrectEditScreen(
                    languageCode = destination.languageCode,
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }

            AutoCorrectionDestination.UserDictionary -> {
                UserDictionaryScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }

            AutoCorrectionDestination.CorpusImport -> {
                CorpusImportScreen(
                    modifier = modifier,
                    onBack = { navigateBack() }
                )
            }
        }
    }
}

private sealed class AutoCorrectionDestination {
    object Main : AutoCorrectionDestination()
    object Settings : AutoCorrectionDestination()
    data class Edit(val languageCode: String) : AutoCorrectionDestination()
    object UserDictionary : AutoCorrectionDestination()
    object CorpusImport : AutoCorrectionDestination()
}

private enum class UserDictSource { DEFAULT, USER }

private data class UserDictItem(val word: String, val source: UserDictSource)

@Composable
private fun UserDictionaryScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val defaultStore = remember(context) { DefaultUserDefaultsStore(context) }
    val userStore = remember { it.palsoftware.pastiera.core.suggestions.UserDictionaryStore() }
    var entries by remember { mutableStateOf(emptyList<UserDictItem>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<UserDictItem?>(null) }
    var dialogWord by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    val filteredEntries = remember(entries, searchQuery) {
        if (searchQuery.isBlank()) {
            entries
        } else {
            val query = searchQuery.lowercase()
            entries.filter { it.word.lowercase().contains(query) }
        }
    }
    val hasEntries = entries.isNotEmpty()
    var showClearAllDialog by remember { mutableStateOf(false) }

    fun refreshEntries() {
        // Ensure cache is populated from persistent storage before snapshots/removals.
        userStore.loadUserEntries(context)
        val defaults = defaultStore.loadEntries().map { UserDictItem(it.word, UserDictSource.DEFAULT) }
        val users = userStore.getSnapshot().map { it.word }.map { UserDictItem(it, UserDictSource.USER) }
        entries = (defaults + users).sortedBy { it.word.lowercase() }
    }

    LaunchedEffect(Unit) {
        refreshEntries()
    }

    fun notifyDictionaryUpdated() {
        val intent = Intent("it.palsoftware.pastiera.ACTION_USER_DICTIONARY_UPDATED").apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }

    fun addWord(word: String) {
        val trimmed = word.trim()
        if (trimmed.isNotEmpty()) {
            userStore.addWord(context, trimmed)
            refreshEntries()
            notifyDictionaryUpdated()
        }
    }

    fun updateWord(entry: UserDictItem, newWord: String) {
        val trimmed = newWord.trim()
        if (trimmed.isNotEmpty() && trimmed != entry.word) {
            when (entry.source) {
                UserDictSource.DEFAULT -> defaultStore.update(entry.word, trimmed)
                UserDictSource.USER -> userStore.updateWord(context, entry.word, trimmed)
            }
            refreshEntries()
            notifyDictionaryUpdated()
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
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
                            text = stringResource(R.string.user_dict_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (searchActive) {
                                    searchActive = false
                                    searchQuery = ""
                                } else {
                                    searchActive = true
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (searchActive) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = stringResource(
                                    if (searchActive) R.string.auto_correct_clear_search
                                    else R.string.auto_correct_search_description
                                )
                            )
                        }
                        
                        IconButton(onClick = { showClearAllDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete_all)
                            )
                        }

                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.user_dict_add_button)
                            )
                        }
                    }
                    if (searchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            placeholder = {
                                Text(stringResource(R.string.auto_correct_search_placeholder))
                            },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = stringResource(R.string.auto_correct_search_description)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.auto_correct_clear_search)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filteredEntries.isEmpty()) {
                item {
                    Text(
                        text = if (hasEntries) {
                            stringResource(R.string.auto_correct_no_corrections_found)
                        } else {
                            stringResource(R.string.user_dict_empty_state)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(filteredEntries) { entry ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = entry.word,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                editingEntry = entry
                                dialogWord = entry.word
                            }) {
                                Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                            }
                            IconButton(onClick = {
                                when (entry.source) {
                                    UserDictSource.DEFAULT -> defaultStore.remove(entry.word)
                                    UserDictSource.USER -> userStore.removeWord(context, entry.word)
                                }
                                refreshEntries()
                                notifyDictionaryUpdated()
                            }) {
                                Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Add word dialog
    if (showAddDialog) {
        UserDictWordDialog(
            title = stringResource(R.string.user_dict_add_hint),
            word = dialogWord,
            onWordChange = { dialogWord = it },
            onConfirm = {
                addWord(dialogWord)
                showAddDialog = false
                dialogWord = ""
            },
            onDismiss = {
                showAddDialog = false
                dialogWord = ""
            }
        )
    }

    // Edit word dialog
    editingEntry?.let { entry ->
        UserDictWordDialog(
            title = stringResource(R.string.user_dict_edit_title),
            word = dialogWord,
            onWordChange = { dialogWord = it },
            onConfirm = {
                updateWord(entry, dialogWord)
                editingEntry = null
                dialogWord = ""
            },
            onDismiss = {
                editingEntry = null
                dialogWord = ""
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(stringResource(R.string.user_dict_reset_confirm_title)) },
            text = { Text(stringResource(R.string.user_dict_reset_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        userStore.resetAll(context)
                        refreshEntries()
                        showClearAllDialog = false
                        notifyDictionaryUpdated()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.user_dict_reset_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun UserDictWordDialog(
    title: String,
    word: String,
    onWordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = word,
                onValueChange = onWordChange,
                label = { Text(title) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (word.isNotBlank()) {
                        IconButton(onClick = { onWordChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = null
                            )
                        }
                    }
                }
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = word.isNotBlank()
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun CorpusImportScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userStore = remember { it.palsoftware.pastiera.core.suggestions.UserDictionaryStore() }
    val importer = remember { CorpusImporter(context, userStore) }
    
    var importing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    // Language selection
    val supportedLanguages = listOf("de", "en", "it", "fr", "es", "pl")
    var selectedLanguage by remember { mutableStateOf(Locale.getDefault().language) }
    if (selectedLanguage !in supportedLanguages) {
        selectedLanguage = "en"
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            importing = true
            resultMessage = null
            scope.launch {
                val locale = Locale(selectedLanguage)
                val result = importer.importFromUri(it, locale) { p ->
                    progress = p
                }
                importing = false
                if (result.isSuccess) {
                    val count = result.getOrNull() ?: 0
                    resultMessage = context.getString(R.string.user_dict_import_success, count)
                    isError = false
                    
                    // Notify IME to reload dictionary
                    val intent = Intent("it.palsoftware.pastiera.ACTION_USER_DICTIONARY_UPDATED").apply {
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(intent)
                } else {
                    resultMessage = context.getString(R.string.user_dict_import_error, result.exceptionOrNull()?.message)
                    isError = true
                }
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
                        text = stringResource(R.string.user_dict_import_corpus_title),
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.user_dict_import_corpus_description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.user_dict_import_corpus_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            // Language Selection Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.user_dict_import_select_language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    supportedLanguages.forEach { lang ->
                        val label = when (lang) {
                            "de" -> stringResource(R.string.input_method_name_de)
                            "en" -> stringResource(R.string.input_method_name_en)
                            "it" -> stringResource(R.string.input_method_name_it)
                            "fr" -> stringResource(R.string.input_method_name_fr)
                            "es" -> stringResource(R.string.input_method_name_es)
                            "pl" -> stringResource(R.string.input_method_name_pl)
                            else -> lang.uppercase()
                        }
                        FilterChip(
                            selected = selectedLanguage == lang,
                            onClick = { selectedLanguage = lang },
                            label = { Text(label) },
                            enabled = !importing
                        )
                    }
                }
            }

            if (importing) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        strokeCap = StrokeCap.Round
                    )
                    Text(
                        text = stringResource(R.string.user_dict_import_processing),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Button(
                    onClick = { filePickerLauncher.launch("text/plain") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(imageVector = Icons.Filled.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.user_dict_import_select_file),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            resultMessage?.let { message ->
                Surface(
                    color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Filled.Close else Icons.Filled.TextFields,
                            contentDescription = null,
                            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

private enum class LocalNavigationDirection {
    Push,
    Pop
}

private data class DefaultUserWord(val word: String, val frequency: Int)

/**
 * Handles editable default user dictionary stored in app-private storage.
 * Uses JSON format aligned with assets/common/dictionaries/user_defaults.json.
 */
private class DefaultUserDefaultsStore(private val context: Context) {
    private val fileName = "user_defaults.json"
    private val assetPath = "common/dictionaries/$fileName"

    private fun ensureLocalFile(): java.io.File {
        val file = context.getFileStreamPath(fileName)
        if (!file.exists()) {
            try {
                context.assets.open(assetPath).use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (_: Exception) {
                // Asset missing; create empty file
                file.parentFile?.mkdirs()
                file.writeText("[]")
            }
        }
        return file
    }

    fun loadEntries(): List<DefaultUserWord> {
        val file = ensureLocalFile()
        return try {
            val jsonString = file.readText()
            val jsonArray = JSONArray(jsonString)
            buildList {
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val word = obj.getString("w")
                    val freq = obj.optInt("f", 1)
                    add(DefaultUserWord(word, freq))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addOrBump(word: String, baseFrequency: Int = 10) {
        val file = ensureLocalFile()
        val entries = loadEntries().toMutableList()
        val existingIndex = entries.indexOfFirst { it.word.equals(word, ignoreCase = true) }
        if (existingIndex >= 0) {
            val existing = entries[existingIndex]
            entries[existingIndex] = existing.copy(frequency = existing.frequency + 1)
        } else {
            entries.add(DefaultUserWord(word, baseFrequency))
        }
        persist(entries, file)
    }

    fun remove(word: String) {
        val file = ensureLocalFile()
        val entries = loadEntries().filterNot { it.word.equals(word, ignoreCase = true) }
        persist(entries, file)
    }

    fun update(oldWord: String, newWord: String) {
        val file = ensureLocalFile()
        val entries = loadEntries().map {
            if (it.word.equals(oldWord, ignoreCase = true)) it.copy(word = newWord) else it
        }
        persist(entries, file)
    }

    private fun persist(entries: List<DefaultUserWord>, file: java.io.File) {
        try {
            val array = JSONArray()
            entries.forEach { entry ->
                val obj = JSONObject()
                obj.put("w", entry.word)
                obj.put("f", entry.frequency)
                array.put(obj)
            }
            file.writeText(array.toString())
        } catch (_: Exception) {
            // Ignore persistence errors; UI will just not reflect changes
        }
    }
}
