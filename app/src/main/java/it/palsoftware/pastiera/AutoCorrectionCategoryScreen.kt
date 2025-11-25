package it.palsoftware.pastiera

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.TextFields
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.core.suggestions.UserDictionaryStore

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
    var showExperimentalToggle by remember { mutableStateOf(false) }
    var experimentalTapCount by remember { mutableStateOf(0) }
    var suggestionsEnabled by remember {
        mutableStateOf(SettingsManager.getSuggestionsEnabled(context))
    }
    var accentMatchingEnabled by remember {
        mutableStateOf(SettingsManager.getAccentMatchingEnabled(context))
    }
    var autoReplaceOnSpaceEnter by remember {
        mutableStateOf(SettingsManager.getAutoReplaceOnSpaceEnter(context))
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
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .clickable {
                                            experimentalTapCount++
                                            if (experimentalTapCount >= 5) {
                                                showExperimentalToggle = true
                                            }
                                        }
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

                        if (showExperimentalToggle) {
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

                        if (showExperimentalToggle) {
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
                        }

                        if (showExperimentalToggle) {
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
        }
    }
}

private sealed class AutoCorrectionDestination {
    object Main : AutoCorrectionDestination()
    object Settings : AutoCorrectionDestination()
    data class Edit(val languageCode: String) : AutoCorrectionDestination()
    object UserDictionary : AutoCorrectionDestination()
}

@Composable
private fun UserDictionaryScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { UserDictionaryStore() }
    var newWord by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf(store.loadUserEntries(context)) }

    fun refreshEntries() {
        entries = store.loadUserEntries(context)
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
                        text = stringResource(R.string.user_dict_title),
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = newWord,
                onValueChange = { newWord = it },
                label = { Text(stringResource(R.string.user_dict_add_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    val trimmed = newWord.trim()
                    if (trimmed.isNotEmpty()) {
                        store.addWord(context, trimmed)
                        newWord = ""
                        refreshEntries()
                    }
                },
                enabled = newWord.isNotBlank()
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.user_dict_add_button))
            }

            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.user_dict_empty_state),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                entries.forEach { entry ->
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = entry.word, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = stringResource(
                                        R.string.user_dict_frequency_label,
                                        entry.frequency
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                store.removeWord(context, entry.word)
                                refreshEntries()
                            }) {
                                Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                            }
                        }
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
