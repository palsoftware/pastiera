package it.palsoftware.pastiera

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.LinkedHashMap

@Composable
fun SnippetsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var snippets by remember { mutableStateOf(SettingsManager.getSnippets(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredSnippets = remember(snippets, searchQuery) {
        if (searchQuery.isBlank()) {
            snippets
        } else {
            val query = searchQuery.lowercase()
            LinkedHashMap(snippets.filter { (shortcut, value) ->
                shortcut.lowercase().contains(query) || value.lowercase().contains(query)
            })
        }
    }

    BackHandler { onBack() }

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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back_content_description)
                            )
                        }
                        Text(
                            text = stringResource(R.string.snippets_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.snippets_add)
                        )
                    }
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
            Surface(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.snippets_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Surface(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.snippets_search_placeholder)) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.snippets_search)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.snippets_clear_search)
                                )
                            }
                        }
                    }
                )
            }

            if (filteredSnippets.isEmpty()) {
                Surface(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (searchQuery.isNotBlank()) {
                            stringResource(R.string.snippets_empty_search)
                        } else {
                            stringResource(R.string.snippets_empty)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                filteredSnippets.forEach { (shortcut, value) ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editingKey = shortcut
                                showAddDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(0.8f)) {
                                Text(
                                    text = shortcut,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "→",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1.4f)) {
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    val updated = LinkedHashMap(snippets)
                                    updated.remove(shortcut)
                                    snippets = updated
                                    SettingsManager.saveSnippets(context, snippets)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.snippets_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var shortcutText by remember { mutableStateOf(editingKey ?: "") }
        var valueText by remember { mutableStateOf(editingKey?.let { snippets[it] } ?: "") }

        LaunchedEffect(editingKey) {
            shortcutText = editingKey ?: ""
            valueText = editingKey?.let { snippets[it] } ?: ""
        }

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                editingKey = null
            },
            title = {
                Text(
                    text = if (editingKey == null) {
                        stringResource(R.string.snippets_add)
                    } else {
                        stringResource(R.string.snippets_edit)
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = shortcutText,
                        onValueChange = { shortcutText = it },
                        label = { Text(stringResource(R.string.snippets_shortcut_label)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = it },
                        label = { Text(stringResource(R.string.snippets_value_label)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val normalizedShortcut = shortcutText.trim().lowercase()
                        val normalizedValue = valueText.trim()
                        if (normalizedShortcut.isBlank() || normalizedValue.isBlank()) return@TextButton

                        val updated = linkedMapOf<String, String>()
                        updated[normalizedShortcut] = normalizedValue
                        snippets.forEach { (shortcut, value) ->
                            if (shortcut != editingKey && shortcut != normalizedShortcut) {
                                updated[shortcut] = value
                            }
                        }
                        snippets = LinkedHashMap(updated)
                        SettingsManager.saveSnippets(context, snippets)
                        showAddDialog = false
                        editingKey = null
                    },
                    enabled = shortcutText.trim().isNotEmpty() && valueText.trim().isNotEmpty()
                ) {
                    Text(stringResource(R.string.snippets_save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    editingKey = null
                }) {
                    Text(stringResource(R.string.auto_correct_cancel))
                }
            }
        )
    }
}
