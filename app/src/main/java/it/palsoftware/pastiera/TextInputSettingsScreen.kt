package it.palsoftware.pastiera

import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.activity.compose.BackHandler
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.core.Punctuation

/**
 * Text Input settings screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNavModeSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current

    var showSnippetsScreen by remember { mutableStateOf(false) }
    var emojiShortcodeEnabled by remember {
        mutableStateOf(SettingsManager.getEmojiShortcodeEnabled(context))
    }
    var symbolShortcodeEnabled by remember {
        mutableStateOf(SettingsManager.getSymbolShortcodeEnabled(context))
    }
    var snippetsEnabled by remember {
        mutableStateOf(SettingsManager.getSnippetsEnabled(context))
    }
    var snippetsTrigger by remember {
        mutableStateOf(SettingsManager.getSnippetsTrigger(context))
    }
    var showSnippetsTriggerDialog by remember { mutableStateOf(false) }
    var pendingSnippetsTrigger by remember { mutableStateOf(snippetsTrigger) }
    
    var autoCapitalizeFirstLetter by remember {
        mutableStateOf(SettingsManager.getAutoCapitalizeFirstLetter(context))
    }

    var autoCapitalizeAfterPeriod by remember {
        mutableStateOf(SettingsManager.getAutoCapitalizeAfterPeriod(context))
    }

    var doubleSpaceToPeriod by remember {
        mutableStateOf(SettingsManager.getDoubleSpaceToPeriod(context))
    }

    var spacedHyphenToEnDash by remember {
        mutableStateOf(SettingsManager.getSpacedHyphenToEnDash(context))
    }

    var spacedHyphenDashStyle by remember {
        mutableStateOf(SettingsManager.getSpacedHyphenDashStyle(context))
    }

    var spacedHyphenDashExpanded by remember { mutableStateOf(false) }

    var midWordQuoteToApostrophe by remember {
        mutableStateOf(SettingsManager.getMidWordQuoteToApostrophe(context))
    }

    var frenchPunctuationSpacing by remember {
        mutableStateOf(SettingsManager.getFrenchPunctuationSpacing(context))
    }

    var frenchPunctuationOnlyFrenchLayouts by remember {
        mutableStateOf(SettingsManager.getFrenchPunctuationOnlyFrenchLayouts(context))
    }

    var commaSpace by remember {
        mutableStateOf(SettingsManager.getCommaSpace(context))
    }

    var autoSpacePunctuation by remember {
        mutableStateOf(SettingsManager.getAutoSpacePunctuation(context))
    }

    var autoSpacePunctuationDialogVisible by remember { mutableStateOf(false) }

    var smartQuotes by remember {
        mutableStateOf(SettingsManager.getSmartQuotes(context))
    }

    var smartQuotesStyle by remember {
        mutableStateOf(SettingsManager.getSmartQuotesStyle(context))
    }

    var smartQuotesExpanded by remember { mutableStateOf(false) }

    var clearAltOnSpace by remember {
        mutableStateOf(SettingsManager.getClearAltOnSpace(context))
    }
    
    var autoShowKeyboard by remember {
        mutableStateOf(SettingsManager.getAutoShowKeyboard(context))
    }
    
    var altCtrlSpeechShortcut by remember {
        mutableStateOf(SettingsManager.getAltCtrlSpeechShortcutEnabled(context))
    }

    var titan2LayoutEnabled by remember {
        mutableStateOf(SettingsManager.isTitan2LayoutEnabled(context))
    }

    var shiftBackspaceDelete by remember {
        mutableStateOf(SettingsManager.getShiftBackspaceDelete(context))
    }

    var altBackspaceDelete by remember {
        mutableStateOf(SettingsManager.getAltBackspaceDelete(context))
    }

    var backspaceAtStartDelete by remember {
        mutableStateOf(SettingsManager.getBackspaceAtStartDelete(context))
    }
    
    // Handle system back button
    BackHandler {
        if (showSnippetsScreen) {
            showSnippetsScreen = false
        } else {
            onBack()
        }
    }

    if (showSnippetsScreen) {
        SnippetsScreen(
            modifier = modifier,
            onBack = { showSnippetsScreen = false }
        )
        return
    }

    if (autoSpacePunctuationDialogVisible) {
        AlertDialog(
            onDismissRequest = { autoSpacePunctuationDialogVisible = false },
            title = { Text(stringResource(R.string.auto_space_punctuation_title)) },
            text = {
                Column {
                    Text(
                        text = autoSpacePunctuationDialogDescription(
                            punctuation = autoSpacePunctuation,
                            selectedLabel = stringResource(R.string.auto_space_punctuation_selected),
                            noneLabel = stringResource(R.string.auto_space_punctuation_none_selected),
                            instruction = stringResource(R.string.auto_space_punctuation_dialog_instruction)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Column {
                        autoSpacePunctuationOptions().forEach { punctuation ->
                            val selected = punctuation in autoSpacePunctuation
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clickable {
                                        autoSpacePunctuation = toggleAutoSpacePunctuation(
                                            current = autoSpacePunctuation,
                                            punctuation = punctuation
                                        )
                                        SettingsManager.setAutoSpacePunctuation(context, autoSpacePunctuation)
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { checked ->
                                        autoSpacePunctuation = if (checked) {
                                            addAutoSpacePunctuation(autoSpacePunctuation, punctuation)
                                        } else {
                                            autoSpacePunctuation.filterNot { it == punctuation }
                                        }
                                        SettingsManager.setAutoSpacePunctuation(context, autoSpacePunctuation)
                                    }
                                )
                                Text(
                                    text = autoSpacePunctuationLabel(punctuation),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { autoSpacePunctuationDialogVisible = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        autoSpacePunctuation = Punctuation.DEFAULT_AUTO_SPACE
                        SettingsManager.setAutoSpacePunctuation(context, autoSpacePunctuation)
                    }
                ) {
                    Text(stringResource(R.string.auto_space_punctuation_reset))
                }
            }
        )
    }

    if (showSnippetsTriggerDialog) {
        AlertDialog(
            onDismissRequest = { showSnippetsTriggerDialog = false },
            title = { Text(stringResource(R.string.snippets_trigger_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = pendingSnippetsTrigger,
                    onValueChange = { value ->
                        pendingSnippetsTrigger = value.takeLast(1)
                    },
                    label = { Text(stringResource(R.string.snippets_trigger_label)) },
                    supportingText = {
                        Text(stringResource(R.string.snippets_trigger_dialog_description))
                    },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val normalized = pendingSnippetsTrigger.trim().take(1).ifEmpty { "!" }
                    snippetsTrigger = normalized
                    pendingSnippetsTrigger = normalized
                    SettingsManager.setSnippetsTrigger(context, normalized)
                    showSnippetsTriggerDialog = false
                }) {
                    Text(stringResource(R.string.snippets_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSnippetsTriggerDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
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
                        text = stringResource(R.string.settings_category_text_input),
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
            SettingsSectionHeader(text = stringResource(R.string.shortcodes_title))
            SettingsSwitchRow(
                title = stringResource(R.string.emoji_shortcodes_title),
                description = stringResource(R.string.emoji_shortcodes_description),
                checked = emojiShortcodeEnabled,
                onCheckedChange = { enabled ->
                    emojiShortcodeEnabled = enabled
                    SettingsManager.setEmojiShortcodeEnabled(context, enabled)
                }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.symbol_shortcodes_title),
                description = stringResource(R.string.symbol_shortcodes_description),
                checked = symbolShortcodeEnabled,
                onCheckedChange = { enabled ->
                    symbolShortcodeEnabled = enabled
                    SettingsManager.setSymbolShortcodeEnabled(context, enabled)
                }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.snippets_title),
                description = stringResource(R.string.snippets_setting_description, snippetsTrigger),
                checked = snippetsEnabled,
                onCheckedChange = { enabled ->
                    snippetsEnabled = enabled
                    SettingsManager.setSnippetsEnabled(context, enabled)
                }
            )
            SettingsNavigationRow(
                title = stringResource(R.string.snippets_manage_title),
                description = stringResource(R.string.snippets_manage_description, snippetsTrigger),
                onClick = { showSnippetsScreen = true },
                trailingContent = {
                    TextButton(onClick = {
                        pendingSnippetsTrigger = snippetsTrigger
                        showSnippetsTriggerDialog = true
                    }) {
                        Text(stringResource(R.string.snippets_trigger_button))
                    }
                }
            )

            SettingsSectionHeader(text = stringResource(R.string.text_input_section_capitalization))
            SettingsSwitchRow(
                title = stringResource(R.string.auto_capitalize_title),
                description = stringResource(R.string.auto_capitalize_description),
                checked = autoCapitalizeFirstLetter,
                onCheckedChange = { enabled ->
                    autoCapitalizeFirstLetter = enabled
                    SettingsManager.setAutoCapitalizeFirstLetter(context, enabled)
                }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.auto_capitalize_after_period_title),
                description = stringResource(R.string.auto_capitalize_after_period_description),
                checked = autoCapitalizeAfterPeriod,
                onCheckedChange = { enabled ->
                    autoCapitalizeAfterPeriod = enabled
                    SettingsManager.setAutoCapitalizeAfterPeriod(context, enabled)
                }
            )

            SettingsSectionHeader(text = stringResource(R.string.text_input_section_spacing_punctuation))
            SettingsSwitchRow(
                title = stringResource(R.string.double_space_to_period_title),
                description = stringResource(R.string.double_space_to_period_description),
                checked = doubleSpaceToPeriod,
                onCheckedChange = { enabled ->
                    doubleSpaceToPeriod = enabled
                    SettingsManager.setDoubleSpaceToPeriod(context, enabled)
                }
            )
            SettingsNavigationRow(
                title = stringResource(R.string.auto_space_punctuation_title),
                description = autoSpacePunctuationSummary(
                    punctuation = autoSpacePunctuation,
                    chooseLabel = stringResource(R.string.auto_space_punctuation_choose),
                    offLabel = stringResource(R.string.auto_space_punctuation_choose_off)
                ),
                onClick = { autoSpacePunctuationDialogVisible = true }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.comma_space_title),
                description = stringResource(R.string.comma_space_description),
                checked = commaSpace,
                onCheckedChange = { enabled ->
                    commaSpace = enabled
                    SettingsManager.setCommaSpace(context, enabled)
                }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.french_punctuation_spacing_title),
                description = stringResource(R.string.french_punctuation_spacing_description),
                checked = frenchPunctuationSpacing,
                onCheckedChange = { enabled ->
                    frenchPunctuationSpacing = enabled
                    SettingsManager.setFrenchPunctuationSpacing(context, enabled)
                }
            )
            AnimatedVisibility(visible = frenchPunctuationSpacing) {
                SettingsSwitchRow(
                    title = stringResource(R.string.french_punctuation_only_french_title),
                    description = stringResource(R.string.french_punctuation_only_french_description),
                    checked = frenchPunctuationOnlyFrenchLayouts,
                    inset = 52.dp,
                    onCheckedChange = { enabled ->
                        frenchPunctuationOnlyFrenchLayouts = enabled
                        SettingsManager.setFrenchPunctuationOnlyFrenchLayouts(context, enabled)
                    }
                )
            }

            SettingsSectionHeader(text = stringResource(R.string.text_input_section_typography))
            SettingsDropdownSwitchRow(
                title = stringResource(R.string.spaced_hyphen_to_en_dash_title),
                checked = spacedHyphenToEnDash,
                onCheckedChange = { enabled ->
                    spacedHyphenToEnDash = enabled
                    SettingsManager.setSpacedHyphenToEnDash(context, enabled)
                    if (!enabled) {
                        spacedHyphenDashExpanded = false
                    }
                },
                expanded = spacedHyphenDashExpanded,
                onExpandedChange = { spacedHyphenDashExpanded = it },
                value = dashStyleLabel(spacedHyphenDashStyle),
                options = dashStyleOptions(),
                optionLabel = ::dashStyleLabel,
                onOptionSelected = { style ->
                    spacedHyphenDashStyle = style
                    SettingsManager.setSpacedHyphenDashStyle(context, style)
                    spacedHyphenDashExpanded = false
                }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.mid_word_quote_to_apostrophe_title),
                description = stringResource(R.string.mid_word_quote_to_apostrophe_description),
                checked = midWordQuoteToApostrophe,
                onCheckedChange = { enabled ->
                    midWordQuoteToApostrophe = enabled
                    SettingsManager.setMidWordQuoteToApostrophe(context, enabled)
                }
            )
            SettingsDropdownSwitchRow(
                title = stringResource(R.string.smart_quotes_title),
                checked = smartQuotes,
                onCheckedChange = { enabled ->
                    smartQuotes = enabled
                    SettingsManager.setSmartQuotes(context, enabled)
                    if (!enabled) {
                        smartQuotesExpanded = false
                    }
                },
                expanded = smartQuotesExpanded,
                onExpandedChange = { smartQuotesExpanded = it },
                value = smartQuotesStyleLabel(smartQuotesStyle),
                options = smartQuoteStyleOptions(),
                optionLabel = ::smartQuotesStyleLabel,
                onOptionSelected = { style ->
                    smartQuotesStyle = style
                    SettingsManager.setSmartQuotesStyle(context, style)
                    smartQuotesExpanded = false
                }
            )

            SettingsSectionHeader(text = stringResource(R.string.text_input_section_keyboard_behavior))
            SettingsSwitchRow(
                title = stringResource(R.string.clear_alt_on_space_title),
                description = stringResource(R.string.clear_alt_on_space_description),
                checked = clearAltOnSpace,
                onCheckedChange = { enabled ->
                    clearAltOnSpace = enabled
                    SettingsManager.setClearAltOnSpace(context, enabled)
                }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.auto_show_keyboard_title),
                description = stringResource(R.string.auto_show_keyboard_description),
                checked = autoShowKeyboard,
                onCheckedChange = { enabled ->
                    autoShowKeyboard = enabled
                    SettingsManager.setAutoShowKeyboard(context, enabled)
                }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.alt_ctrl_speech_shortcut_title),
                description = stringResource(R.string.alt_ctrl_speech_shortcut_description),
                checked = altCtrlSpeechShortcut,
                onCheckedChange = { enabled ->
                    altCtrlSpeechShortcut = enabled
                    SettingsManager.setAltCtrlSpeechShortcutEnabled(context, enabled)
                }
            )

            SettingsSectionHeader(text = stringResource(R.string.text_input_section_delete))
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.delete_alternatives_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.shift_backspace_delete_title),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = shiftBackspaceDelete,
                            onCheckedChange = { enabled ->
                                shiftBackspaceDelete = enabled
                                SettingsManager.setShiftBackspaceDelete(context, enabled)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.alt_backspace_delete_title),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = altBackspaceDelete,
                            onCheckedChange = { enabled ->
                                altBackspaceDelete = enabled
                                SettingsManager.setAltBackspaceDelete(context, enabled)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.backspace_at_start_delete_title),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = backspaceAtStartDelete,
                            onCheckedChange = { enabled ->
                                backspaceAtStartDelete = enabled
                                SettingsManager.setBackspaceAtStartDelete(context, enabled)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.delete_alternatives_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingsNavigationRow(
                        title = stringResource(R.string.delete_alternatives_nav_mode_title),
                        description = stringResource(R.string.settings_nav_mode_configure),
                        iconInset = 0.dp,
                        onClick = onNavModeSettingsClick
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.delete_alternatives_selection_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp)
        )
    }
}

@Composable
private fun SettingsRowFrame(
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 64.dp,
    inset: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = inset, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    inset: androidx.compose.ui.unit.Dp = 16.dp,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsRowFrame(inset = inset, minHeight = if (description == null) 64.dp else 72.dp) {
        Icon(
            imageVector = Icons.Filled.TextFields,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    description: String,
    iconInset: androidx.compose.ui.unit.Dp = 16.dp,
    onClick: () -> Unit,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    SettingsRowFrame(
        modifier = Modifier.clickable(onClick = onClick),
        minHeight = 72.dp,
        inset = iconInset
    ) {
        Icon(
            imageVector = Icons.Filled.TextFields,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        if (trailingContent != null) {
            trailingContent()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SettingsDropdownSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    value: String,
    options: List<String>,
    optionLabel: (String) -> String,
    onOptionSelected: (String) -> Unit
) {
    SettingsRowFrame(minHeight = 88.dp) {
        Icon(
            imageVector = Icons.Filled.TextFields,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { onExpandedChange(!expanded) }
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    enabled = checked,
                    textStyle = MaterialTheme.typography.bodySmall,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .menuAnchor(
                            type = MenuAnchorType.PrimaryNotEditable,
                            enabled = checked
                        )
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(optionLabel(option)) },
                            onClick = { onOptionSelected(option) },
                            enabled = checked
                        )
                    }
                }
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private fun dashStyleOptions(): List<String> {
    return listOf(
        SettingsManager.DASH_STYLE_EN,
        SettingsManager.DASH_STYLE_EM
    )
}

private fun dashStyleLabel(style: String): String {
    return when (style) {
        SettingsManager.DASH_STYLE_EM -> "—"
        else -> "–"
    }
}

private fun smartQuoteStyleOptions(): List<String> {
    return listOf(
        SettingsManager.SMART_QUOTES_STYLE_GERMAN_GUILLEMETS,
        SettingsManager.SMART_QUOTES_STYLE_FRENCH_GUILLEMETS,
        SettingsManager.SMART_QUOTES_STYLE_FRENCH_GUILLEMETS_NARROW_SPACED,
        SettingsManager.SMART_QUOTES_STYLE_GERMAN_LOW_HIGH,
        SettingsManager.SMART_QUOTES_STYLE_ENGLISH_CURLY
    )
}

// These labels intentionally use explicit quote pairs instead of generic placeholders.
// Change them only with care: quotation marks are interpreted visually and some
// combinations render ambiguously in Android text fields and dropdowns.
private fun smartQuotesStyleLabel(style: String): String {
    return when (style) {
        SettingsManager.SMART_QUOTES_STYLE_FRENCH_GUILLEMETS -> "«...»"
        SettingsManager.SMART_QUOTES_STYLE_FRENCH_GUILLEMETS_NARROW_SPACED -> "« ... »"
        SettingsManager.SMART_QUOTES_STYLE_GERMAN_LOW_HIGH -> "„...“"
        SettingsManager.SMART_QUOTES_STYLE_ENGLISH_CURLY -> "“...”"
        else -> "»...«"
    }
}

private fun autoSpacePunctuationOptions(): List<Char> {
    return Punctuation.AUTO_SPACE_CANDIDATES.toList()
}

private fun autoSpacePunctuationSummary(punctuation: String, chooseLabel: String, offLabel: String): String {
    return punctuation.takeIf { it.isNotEmpty() }
        ?.map { autoSpacePunctuationLabel(it) }
        ?.joinToString(" ")
        ?.let { selected -> "$chooseLabel $selected" }
        ?: offLabel
}

private fun autoSpacePunctuationDialogDescription(
    punctuation: String,
    selectedLabel: String,
    noneLabel: String,
    instruction: String
): String {
    val selected = punctuation.takeIf { it.isNotEmpty() }
        ?.map { autoSpacePunctuationLabel(it) }
        ?.joinToString(" ")
        ?: noneLabel
    return "$selectedLabel $selected. $instruction"
}

private fun autoSpacePunctuationLabel(punctuation: Char): String {
    return when (punctuation) {
        '\\' -> "\\"
        '"' -> "\""
        else -> punctuation.toString()
    }
}

private fun addAutoSpacePunctuation(current: String, punctuation: Char): String {
    val selected = current.toSet() + punctuation
    return Punctuation.AUTO_SPACE_CANDIDATES.filter { it in selected }
}

private fun toggleAutoSpacePunctuation(current: String, punctuation: Char): String {
    return if (punctuation in current) {
        current.filterNot { it == punctuation }
    } else {
        addAutoSpacePunctuation(current, punctuation)
    }
}
