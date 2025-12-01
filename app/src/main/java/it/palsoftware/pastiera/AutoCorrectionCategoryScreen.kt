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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.core.suggestions.PersonalDictionary

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
    var showExperimentalToggle by remember { mutableStateOf(true) }
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
    var maxAutoReplaceDistance by remember {
        mutableStateOf(SettingsManager.getMaxAutoReplaceDistance(context))
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
    val personalDictionary = remember { PersonalDictionary.getInstance(context) }
    var newWord by remember { mutableStateOf("") }
    var newReplacement by remember { mutableStateOf("") }
    val entries by personalDictionary.entries.collectAsState()

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
        val sortedEntries = remember(entries) {
            entries.values.sortedBy { it.word.lowercase() }
        }

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Word input
            item {
                OutlinedTextField(
                    value = newWord,
                    onValueChange = { newWord = it },
                    label = { Text(stringResource(R.string.user_dict_add_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Replacement input (optional)
            item {
                OutlinedTextField(
                    value = newReplacement,
                    onValueChange = { newReplacement = it },
                    label = { Text(stringResource(R.string.user_dict_replacement_hint)) },
                    placeholder = { Text(stringResource(R.string.user_dict_replacement_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Button(
                    onClick = {
                        val trimmedWord = newWord.trim()
                        val trimmedReplacement = newReplacement.trim().takeIf { it.isNotEmpty() }
                        if (trimmedWord.isNotEmpty()) {
                            personalDictionary.addEntry(trimmedWord, trimmedReplacement)
                            newWord = ""
                            newReplacement = ""
                        }
                    },
                    enabled = newWord.isNotBlank()
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.user_dict_add_button))
                }
            }

            if (sortedEntries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.user_dict_empty_state),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(sortedEntries, key = { it.word.lowercase() }) { entry ->
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
                                if (entry.replacement != null) {
                                    Text(
                                        text = "${entry.word} → ${entry.replacement}",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                } else {
                                    Text(
                                        text = entry.word,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = stringResource(R.string.user_dict_no_correction),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = {
                                personalDictionary.removeWord(entry.word)
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
