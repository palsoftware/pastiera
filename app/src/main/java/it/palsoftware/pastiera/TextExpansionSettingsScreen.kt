package it.palsoftware.pastiera

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.inputmethod.expansion.ExpansionActivationPolicy
import it.palsoftware.pastiera.inputmethod.expansion.ExpansionPresentation
import it.palsoftware.pastiera.inputmethod.expansion.TextExpansionEngine
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextExpansionSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var manageSnippets by remember { mutableStateOf(settingsChild(context, "snippets") == "manage") }
    val settingHighlight = LocalSettingHighlightId.current
    androidx.compose.runtime.LaunchedEffect(settingHighlight) {
        if (settingHighlight?.startsWith("text_expansion.") == true) manageSnippets = false
    }

    if (manageSnippets) {
        SnippetsScreen(onBack = { context.settingsActivity().finish() })
        return
    }

    var enabled by remember { mutableStateOf(SettingsManager.getSnippetsEnabled(context)) }
    var prefix by remember { mutableStateOf(SettingsManager.getSnippetsPrefix(context)) }
    var prefixError by remember { mutableStateOf(false) }
    var presentation by remember { mutableStateOf(SettingsManager.getSnippetsPresentation(context)) }
    var policy by remember { mutableStateOf(SettingsManager.getSnippetsActivationPolicy(context)) }
    var presentationExpanded by remember { mutableStateOf(false) }
    var emojiEnabled by remember { mutableStateOf(SettingsManager.getEmojiShortcodesEnabled(context)) }
    var symbolsEnabled by remember { mutableStateOf(SettingsManager.getSymbolShortcodesEnabled(context)) }
    var emojiPresentation by remember { mutableStateOf(SettingsManager.getEmojiSymbolsPresentation(context)) }
    var emojiPolicy by remember { mutableStateOf(SettingsManager.getEmojiSymbolsActivationPolicy(context)) }
    var exactOnClose by remember { mutableStateOf(SettingsManager.getEmojiSymbolsExactOnClose(context)) }
    var emojiPresentationExpanded by remember { mutableStateOf(false) }

    SettingsScaffold(title = stringResource(R.string.text_expansion_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SectionTitle(stringResource(R.string.snippets_title))
            SwitchSettingRow(
                title = stringResource(R.string.snippets_enable_title),
                linkId = "text_expansion.snippets.enabled",
                description = stringResource(R.string.snippets_enable_description),
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    SettingsManager.setSnippetsEnabled(context, it)
                }
            )
            Surface(modifier = Modifier.fillMaxWidth().settingRow("text_expansion.snippets.prefix")) {
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { candidate ->
                        val selected = candidate.takeLast(1)
                        prefix = selected
                        prefixError = selected.isNotEmpty() && !TextExpansionEngine.isValidSnippetPrefix(selected)
                        if (!prefixError && selected.isNotEmpty()) SettingsManager.setSnippetsPrefix(context, selected)
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    label = { Text(stringResource(R.string.snippets_prefix_title)) },
                    supportingText = {
                        Text(
                            if (prefixError) stringResource(R.string.snippets_prefix_error)
                            else stringResource(R.string.snippets_prefix_description)
                        )
                    },
                    isError = prefixError,
                    singleLine = true
                )
            }
            Surface(modifier = Modifier.fillMaxWidth().settingRow("text_expansion.snippets.presentation")) {
                ExposedDropdownMenuBox(
                    expanded = presentationExpanded,
                    onExpandedChange = { presentationExpanded = it },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = presentationLabel(presentation),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.expansion_presentation_title)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(presentationExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = presentationExpanded,
                        onDismissRequest = { presentationExpanded = false }
                    ) {
                        ExpansionPresentation.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(presentationLabel(option)) },
                                onClick = {
                                    presentation = option
                                    SettingsManager.setSnippetsPresentation(context, option)
                                    presentationExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            SwitchSettingRow(
                title = stringResource(R.string.expansion_tab_title),
                linkId = "text_expansion.snippets.tab",
                description = stringResource(R.string.expansion_tab_description),
                checked = policy.acceptWithTab,
                onCheckedChange = {
                    policy = policy.copy(acceptWithTab = it)
                    SettingsManager.setSnippetsActivationPolicy(context, policy)
                }
            )
            SwitchSettingRow(
                title = stringResource(R.string.expansion_enter_title),
                linkId = "text_expansion.snippets.enter",
                description = stringResource(R.string.expansion_enter_description),
                checked = policy.acceptWithEnter,
                onCheckedChange = {
                    policy = policy.copy(acceptWithEnter = it)
                    SettingsManager.setSnippetsActivationPolicy(context, policy)
                }
            )
            Surface(
                modifier = Modifier.fillMaxWidth().settingRow("text_expansion.snippets.manage") { openSettingsChild(context, "snippets", "manage") }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.snippets_manage_title), fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.snippets_manage_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall)
                }
            }
            SwitchSettingRow(
                title = stringResource(R.string.expansion_space_title),
                linkId = "text_expansion.snippets.space",
                description = stringResource(R.string.expansion_space_description),
                checked = policy.exactOnSpace,
                onCheckedChange = {
                    policy = policy.copy(exactOnSpace = it)
                    SettingsManager.setSnippetsActivationPolicy(context, policy)
                }
            )
            SwitchSettingRow(
                title = stringResource(R.string.expansion_prefix_space_title),
                linkId = "text_expansion.snippets.prefix_space",
                description = stringResource(R.string.expansion_prefix_space_description),
                checked = policy.acceptPrefixWithSpace,
                onCheckedChange = {
                    policy = policy.copy(acceptPrefixWithSpace = it)
                    SettingsManager.setSnippetsActivationPolicy(context, policy)
                }
            )
            SectionTitle(stringResource(R.string.emoji_symbols_title))
            SwitchSettingRow(
                title = stringResource(R.string.emoji_shortcodes_enable_title),
                linkId = "text_expansion.emoji.enabled",
                description = stringResource(R.string.emoji_shortcodes_enable_description),
                checked = emojiEnabled,
                onCheckedChange = {
                    emojiEnabled = it
                    SettingsManager.setEmojiShortcodesEnabled(context, it)
                }
            )
            SwitchSettingRow(
                title = stringResource(R.string.symbol_shortcodes_enable_title),
                linkId = "text_expansion.symbols.enabled",
                description = stringResource(R.string.symbol_shortcodes_enable_description),
                checked = symbolsEnabled,
                onCheckedChange = {
                    symbolsEnabled = it
                    SettingsManager.setSymbolShortcodesEnabled(context, it)
                }
            )
            Surface(modifier = Modifier.fillMaxWidth().settingRow("text_expansion.emoji_symbols.presentation")) {
                ExposedDropdownMenuBox(
                    expanded = emojiPresentationExpanded,
                    onExpandedChange = { emojiPresentationExpanded = it },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = presentationLabel(emojiPresentation),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.expansion_presentation_title)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(emojiPresentationExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = emojiPresentationExpanded,
                        onDismissRequest = { emojiPresentationExpanded = false }
                    ) {
                        ExpansionPresentation.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(presentationLabel(option)) },
                                onClick = {
                                    emojiPresentation = option
                                    SettingsManager.setEmojiSymbolsPresentation(context, option)
                                    emojiPresentationExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            SwitchSettingRow(
                title = stringResource(R.string.expansion_tab_title),
                linkId = "text_expansion.emoji_symbols.tab",
                description = stringResource(R.string.expansion_tab_description),
                checked = emojiPolicy.acceptWithTab,
                onCheckedChange = {
                    emojiPolicy = emojiPolicy.copy(acceptWithTab = it)
                    SettingsManager.setEmojiSymbolsActivationPolicy(context, emojiPolicy)
                }
            )
            SwitchSettingRow(
                title = stringResource(R.string.expansion_enter_title),
                linkId = "text_expansion.emoji_symbols.enter",
                description = stringResource(R.string.expansion_enter_description),
                checked = emojiPolicy.acceptWithEnter,
                onCheckedChange = {
                    emojiPolicy = emojiPolicy.copy(acceptWithEnter = it)
                    SettingsManager.setEmojiSymbolsActivationPolicy(context, emojiPolicy)
                }
            )
            SwitchSettingRow(
                title = stringResource(R.string.emoji_symbols_close_title),
                linkId = "text_expansion.emoji_symbols.exact_close",
                description = stringResource(R.string.emoji_symbols_close_description),
                checked = exactOnClose,
                onCheckedChange = {
                    exactOnClose = it
                    SettingsManager.setEmojiSymbolsExactOnClose(context, it)
                }
            )
            SwitchSettingRow(
                title = stringResource(R.string.expansion_space_title),
                linkId = "text_expansion.emoji_symbols.space",
                description = stringResource(R.string.expansion_space_description),
                checked = emojiPolicy.exactOnSpace,
                onCheckedChange = {
                    emojiPolicy = emojiPolicy.copy(exactOnSpace = it)
                    SettingsManager.setEmojiSymbolsActivationPolicy(context, emojiPolicy)
                }
            )
            SwitchSettingRow(
                title = stringResource(R.string.expansion_prefix_space_title),
                linkId = "text_expansion.emoji_symbols.prefix_space",
                description = stringResource(R.string.expansion_prefix_space_description),
                checked = emojiPolicy.acceptPrefixWithSpace,
                onCheckedChange = {
                    emojiPolicy = emojiPolicy.copy(acceptPrefixWithSpace = it)
                    SettingsManager.setEmojiSymbolsActivationPolicy(context, emojiPolicy)
                }
            )
        }
    }
}

@Composable
private fun SnippetsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var snippets by remember { mutableStateOf(SettingsManager.getSnippets(context)) }
    var editingShortcut by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.snippets_manage_title),
        onBack = onBack,
        action = {
            IconButton(onClick = {
                editingShortcut = null
                showEditor = true
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.snippets_add))
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (snippets.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.snippets_empty),
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(snippets.entries.toList(), key = { it.key }) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        editingShortcut = entry.key
                        showEditor = true
                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.key, fontWeight = FontWeight.Medium)
                        Text(
                            entry.value.replace("\n", " ↵ "),
                            maxLines = 2,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        snippets = LinkedHashMap(snippets).apply { remove(entry.key) }
                        SettingsManager.saveSnippets(context, snippets)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.snippets_delete))
                    }
                }
                HorizontalDivider()
            }
        }
    }

    if (showEditor) {
        val originalShortcut = editingShortcut
        var shortcut by remember(originalShortcut) { mutableStateOf(originalShortcut.orEmpty()) }
        var replacement by remember(originalShortcut, snippets) {
            mutableStateOf(originalShortcut?.let(snippets::get).orEmpty())
        }
        val shortcutValid = TextExpansionEngine.isValidSnippetShortcut(shortcut.trim())
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (originalShortcut == null) stringResource(R.string.snippets_add) else stringResource(R.string.snippets_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = shortcut,
                        onValueChange = { shortcut = it },
                        label = { Text(stringResource(R.string.snippets_shortcut_label)) },
                        isError = shortcut.isNotEmpty() && !shortcutValid,
                        supportingText = { Text(stringResource(R.string.snippets_shortcut_help)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = replacement,
                        onValueChange = { replacement = it },
                        label = { Text(stringResource(R.string.snippets_value_label)) },
                        minLines = 4,
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = shortcutValid && !replacement.isBlank(),
                    onClick = {
                        val normalized = shortcut.trim().lowercase(Locale.ROOT)
                        snippets = LinkedHashMap(snippets).apply {
                            if (originalShortcut != null && originalShortcut != normalized) remove(originalShortcut)
                            put(normalized, replacement)
                        }
                        SettingsManager.saveSnippets(context, snippets)
                        showEditor = false
                    }
                ) { Text(stringResource(R.string.snippets_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    action: @Composable () -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back_content_description))
                    }
                    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    action()
                }
            }
        },
        content = content
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SwitchSettingRow(
    title: String,
    linkId: String? = null,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().settingRow(linkId) { onCheckedChange(!checked) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun presentationLabel(value: ExpansionPresentation): String = when (value) {
    ExpansionPresentation.OFF -> stringResource(R.string.expansion_presentation_off)
    ExpansionPresentation.FLOATING_POPUP -> stringResource(R.string.expansion_presentation_popup)
    ExpansionPresentation.SUGGESTION_BAR -> stringResource(R.string.expansion_presentation_bar)
}
