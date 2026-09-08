package it.palsoftware.pastiera.inputmethod

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import it.palsoftware.pastiera.*
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.res.stringResource
import it.palsoftware.pastiera.R
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import it.palsoftware.pastiera.commands.CommandExecutor
import it.palsoftware.pastiera.commands.CommandIcon
import it.palsoftware.pastiera.commands.CommandRegistry
import it.palsoftware.pastiera.commands.CommandSourceId
import it.palsoftware.pastiera.commands.CommandSurface
import it.palsoftware.pastiera.commands.CommandTarget

/**
 * Activity per assegnare una scorciatoia del launcher a un tasto.
 * Viene mostrata quando si preme un tasto non assegnato nel launcher.
 * Usa un BottomSheet che appare sopra il launcher.
 */
class LauncherShortcutAssignmentActivity : LocalizedComponentActivity() {
    companion object {
        const val EXTRA_KEY_CODE = "key_code"
        const val EXTRA_SKIP_LAUNCH = "skip_launch"
        const val RESULT_ASSIGNED = 1
        const val RESULT_REMOVED = 2
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Disable activity transition animations for instant appearance
        disableActivityAnimations()
        
        // Rimuovi il titolo dalla finestra (deve essere chiamato prima di setContent)
        window.requestFeature(android.view.Window.FEATURE_NO_TITLE)
        
        // Configure window to be fully transparent and overlay
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        val keyCode = intent.getIntExtra(EXTRA_KEY_CODE, -1)
        if (keyCode == -1) {
            finish()
            return
        }
        
        val skipLaunch = intent.getBooleanExtra(EXTRA_SKIP_LAUNCH, false)
        val hasExistingShortcut = SettingsManager.getLauncherShortcut(this, keyCode) != null
        
        // Usa un tema trasparente per mostrare il bottom sheet sopra il launcher
        setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { finish() }, // Tocca fuori per chiudere
                    contentAlignment = Alignment.BottomCenter
                ) {
                    LauncherShortcutAssignmentBottomSheet(
                        keyCode = keyCode,
                        onCommandSelected = { command ->
                            SettingsManager.setLauncherCommand(
                                this@LauncherShortcutAssignmentActivity,
                                keyCode,
                                command.id,
                                command.source.storageValue,
                                command.kind.name,
                                command.label,
                                command.subtitle,
                                command.launch
                            )
                            
                            // Launch the app only if not called from settings screen
                            if (!skipLaunch) {
                                CommandExecutor(this@LauncherShortcutAssignmentActivity)
                                    .execute(command)
                            }
                            
                            setResult(RESULT_ASSIGNED)
                            finish()
                        },
                        onRemoveShortcut = if (hasExistingShortcut) {
                            {
                                SettingsManager.removeLauncherShortcut(this@LauncherShortcutAssignmentActivity, keyCode)
                                setResult(RESULT_REMOVED)
                                finish()
                            }
                        } else {
                            null
                        },
                        onDismiss = {
                            finish()
                        }
                    )
                }
            }
        }
    }
    
    override fun finish() {
        super.finish()
        disableActivityAnimations()
    }
    
    /**
     * Launches an app by package name.
     */
    private fun disableActivityAnimations() {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

/**
 * Bottom Sheet per assegnare un'app a un tasto.
 * Appare dal basso e non occupa tutto lo schermo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LauncherShortcutAssignmentBottomSheet(
    keyCode: Int,
    onCommandSelected: (CommandTarget) -> Unit,
    onRemoveShortcut: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Focus requester per il campo di ricerca
    val searchFocusRequester = remember { FocusRequester() }
    
    val commands by remember {
        mutableStateOf(CommandRegistry(context).getCommands(CommandSurface.AssignedKey))
    }
    
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    
    // Dai il focus al campo di ricerca quando il bottom sheet è completamente aperto
    LaunchedEffect(sheetState.targetValue, searchActive) {
        if (sheetState.targetValue == SheetValue.Expanded && searchActive) {
            kotlinx.coroutines.delay(100)
            searchFocusRequester.requestFocus()
        }
    }
    
    // Funzione helper per ottenere la lettera del tasto
    fun getKeyLetter(keyCode: Int): Char? {
        return when (keyCode) {
            KeyEvent.KEYCODE_Q -> 'Q'
            KeyEvent.KEYCODE_W -> 'W'
            KeyEvent.KEYCODE_E -> 'E'
            KeyEvent.KEYCODE_R -> 'R'
            KeyEvent.KEYCODE_T -> 'T'
            KeyEvent.KEYCODE_Y -> 'Y'
            KeyEvent.KEYCODE_U -> 'U'
            KeyEvent.KEYCODE_I -> 'I'
            KeyEvent.KEYCODE_O -> 'O'
            KeyEvent.KEYCODE_P -> 'P'
            KeyEvent.KEYCODE_A -> 'A'
            KeyEvent.KEYCODE_S -> 'S'
            KeyEvent.KEYCODE_D -> 'D'
            KeyEvent.KEYCODE_F -> 'F'
            KeyEvent.KEYCODE_G -> 'G'
            KeyEvent.KEYCODE_H -> 'H'
            KeyEvent.KEYCODE_J -> 'J'
            KeyEvent.KEYCODE_K -> 'K'
            KeyEvent.KEYCODE_L -> 'L'
            KeyEvent.KEYCODE_Z -> 'Z'
            KeyEvent.KEYCODE_X -> 'X'
            KeyEvent.KEYCODE_C -> 'C'
            KeyEvent.KEYCODE_V -> 'V'
            KeyEvent.KEYCODE_B -> 'B'
            KeyEvent.KEYCODE_N -> 'N'
            KeyEvent.KEYCODE_M -> 'M'
            else -> null
        }
    }
    
    var selectedSource by remember { mutableStateOf<CommandSourceId?>(null) }

    val filteredCommands = remember(commands, searchQuery, keyCode, selectedSource) {
        val base = commands.filter { command -> selectedSource == null || command.source == selectedSource }
        val matches = if (searchQuery.isBlank()) {
            base
        } else {
            base.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                    it.subtitle?.contains(searchQuery, ignoreCase = true) == true ||
                    it.searchTokens.any { token -> token.contains(searchQuery, ignoreCase = true) }
            }
        }
        
        // Ordina: prima le app che iniziano con la lettera del tasto, poi le altre
        val keyLetter = getKeyLetter(keyCode)?.lowercaseChar()
        if (keyLetter != null && searchQuery.isBlank()) {
            val commandsStartingWithLetter = matches.filter {
                it.source == CommandSourceId.Apps &&
                    it.label.isNotEmpty() &&
                    it.label[0].lowercaseChar() == keyLetter
            }.sortedBy { it.label.lowercase() }
            
            val otherCommands = matches.filter { it !in commandsStartingWithLetter }
                .sortedWith(compareBy<CommandTarget> { it.source.ordinal }.thenBy { it.label.lowercase() })
            
            commandsStartingWithLetter + otherCommands
        } else {
            // Se c'è una ricerca attiva, ordina normalmente
            matches.sortedWith(compareBy<CommandTarget> { it.source.ordinal }.thenBy { it.label.lowercase() })
        }
    }
    val commandEntries = remember(filteredCommands, selectedSource) {
        if (selectedSource != null) {
            filteredCommands.map { CommandPickerEntry.Command(it) }
        } else {
            filteredCommands
                .groupBy { it.source }
                .flatMap { (source, sourceCommands) ->
                    listOf(CommandPickerEntry.Header(source.localizedDisplayLabel(context))) +
                        sourceCommands.map { CommandPickerEntry.Command(it) }
                }
        }
    }
    
    // Funzione helper per ottenere il nome del tasto
    @Composable
    fun getKeyName(keyCode: Int): String {
        val keyName = when (keyCode) {
            KeyEvent.KEYCODE_Q -> "Q"
            KeyEvent.KEYCODE_W -> "W"
            KeyEvent.KEYCODE_E -> "E"
            KeyEvent.KEYCODE_R -> "R"
            KeyEvent.KEYCODE_T -> "T"
            KeyEvent.KEYCODE_Y -> "Y"
            KeyEvent.KEYCODE_U -> "U"
            KeyEvent.KEYCODE_I -> "I"
            KeyEvent.KEYCODE_O -> "O"
            KeyEvent.KEYCODE_P -> "P"
            KeyEvent.KEYCODE_A -> "A"
            KeyEvent.KEYCODE_S -> "S"
            KeyEvent.KEYCODE_D -> "D"
            KeyEvent.KEYCODE_F -> "F"
            KeyEvent.KEYCODE_G -> "G"
            KeyEvent.KEYCODE_H -> "H"
            KeyEvent.KEYCODE_J -> "J"
            KeyEvent.KEYCODE_K -> "K"
            KeyEvent.KEYCODE_L -> "L"
            KeyEvent.KEYCODE_Z -> "Z"
            KeyEvent.KEYCODE_X -> "X"
            KeyEvent.KEYCODE_C -> "C"
            KeyEvent.KEYCODE_V -> "V"
            KeyEvent.KEYCODE_B -> "B"
            KeyEvent.KEYCODE_N -> "N"
            KeyEvent.KEYCODE_M -> "M"
            KeyEvent.KEYCODE_DEL -> "⌫"
            KeyEvent.KEYCODE_SPACE -> "␣"
            KeyEvent.KEYCODE_ENTER -> "⏎"
            else -> null
        }
        return keyName ?: stringResource(R.string.launcher_shortcut_assignment_key_name, keyCode)
    }
    
    // Calcola l'altezza massima (75% dello schermo)
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.dp
    val maxSheetHeight = screenHeightDp * 0.75f
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            HorizontalDivider(
                modifier = Modifier
                    .width(40.dp)
                    .padding(vertical = 8.dp)
            )
        }
    ) {
        // Box con altezza massima per limitare l'altezza del bottom sheet
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Top
            ) {
                // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.launcher_shortcut_assignment_header),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = getKeyName(keyCode),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.launcher_shortcut_assignment_close)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            val availableSources = commands.map { it.source }.distinct()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = {
                        searchActive = !searchActive
                        if (!searchActive) searchQuery = ""
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.launcher_shortcut_assignment_search_description)
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedSource == null,
                        onClick = { selectedSource = null },
                        label = { Text(stringResource(R.string.quick_launcher_all)) }
                    )
                    availableSources.forEach { source ->
                        FilterChip(
                            selected = selectedSource == source,
                            onClick = { selectedSource = source },
                            label = { Text(source.localizedDisplayLabel(context)) }
                        )
                    }
                }
                if (onRemoveShortcut != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    AssistChip(
                        onClick = onRemoveShortcut,
                        label = { Text(stringResource(R.string.launcher_shortcuts_remove)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.error,
                            leadingIconContentColor = MaterialTheme.colorScheme.error
                        )
                    )
                }
            }

            if (searchActive) {
                Spacer(modifier = Modifier.height(10.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocusRequester),
                    placeholder = { Text(stringResource(R.string.launcher_shortcut_assignment_search_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.launcher_shortcut_assignment_search_description)
                        )
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Griglia delle app - usa weight per espandersi e riempire lo spazio disponibile
            if (filteredCommands.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) {
                            stringResource(R.string.launcher_shortcut_assignment_no_apps)
                        } else {
                            stringResource(R.string.launcher_shortcut_assignment_no_results, searchQuery)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = commandEntries,
                        key = { entry ->
                            when (entry) {
                                is CommandPickerEntry.Header -> "header:${entry.label}"
                                is CommandPickerEntry.Command -> entry.command.id
                            }
                        },
                        span = { entry ->
                            when (entry) {
                                is CommandPickerEntry.Header -> GridItemSpan(maxLineSpan)
                                is CommandPickerEntry.Command -> GridItemSpan(1)
                            }
                        }
                    ) { entry ->
                        when (entry) {
                            is CommandPickerEntry.Header -> CommandSectionHeader(entry.label)
                            is CommandPickerEntry.Command -> CommandGridItem(
                                command = entry.command,
                                onClick = { onCommandSelected(entry.command) }
                            )
                        }
                    }
                }
            }
            
        }
        }
    }
}

private sealed class CommandPickerEntry {
    data class Header(val label: String) : CommandPickerEntry()
    data class Command(val command: CommandTarget) : CommandPickerEntry()
}

@Composable
private fun CommandSectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
    )
}

/**
 * Item della griglia per un'app (versione compatta per griglia).
 */
@Composable
private fun CommandGridItem(
    command: CommandTarget,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icona app usando AndroidView (ottimizzata con remember)
            val iconDrawable = remember(command.id) { (command.icon as? CommandIcon.DrawableIcon)?.drawable }
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                if (iconDrawable != null) {
                    AndroidView(
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                setImageDrawable(iconDrawable)
                            }
                        },
                        update = { imageView ->
                            imageView.setImageDrawable(iconDrawable)
                        },
                        modifier = Modifier.size(56.dp)
                    )
                } else {
                    Icon(
                        imageVector = commandMaterialIcon(command),
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Nome app (centrato, max 2 righe)
            Text(
                text = command.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.basicMarquee()
            )
        }
    }
}
