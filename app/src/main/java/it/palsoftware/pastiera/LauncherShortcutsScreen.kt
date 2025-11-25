package it.palsoftware.pastiera

import android.content.Intent
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.Icons.Filled
import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import android.view.ViewGroup
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import it.palsoftware.pastiera.inputmethod.LauncherShortcutAssignmentActivity
import androidx.compose.runtime.key
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale

/**
 * Schermata per gestire le scorciatoie del launcher.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherShortcutsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    
    // Funzione helper per verificare se un package esiste ancora installato
    fun isPackageInstalled(packageName: String?): Boolean {
        if (packageName == null) return false
        return try {
            pm.getLaunchIntentForPackage(packageName) != null
        } catch (e: Exception) {
            false
        }
    }
    
    // Carica le scorciatoie salvate e pulisce quelle per app disinstallate
    var shortcuts by remember {
        mutableStateOf(SettingsManager.getLauncherShortcuts(context))
    }
    
    // Clean up shortcuts for uninstalled apps when screen is displayed
    LaunchedEffect(Unit) {
        val currentShortcuts = SettingsManager.getLauncherShortcuts(context)
        var needsUpdate = false
        
        currentShortcuts.forEach { (keyCode, shortcut) ->
            if (shortcut.type == SettingsManager.LauncherShortcut.TYPE_APP && 
                shortcut.packageName != null &&
                !isPackageInstalled(shortcut.packageName)) {
                // App was uninstalled, remove the shortcut
                SettingsManager.removeLauncherShortcut(context, keyCode)
                needsUpdate = true
            }
        }
        
        if (needsUpdate) {
            shortcuts = SettingsManager.getLauncherShortcuts(context)
        }
    }
    
    // Activity launcher per avviare LauncherShortcutAssignmentActivity
    val launcherShortcutAssignmentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == LauncherShortcutAssignmentActivity.RESULT_ASSIGNED) {
            // Aggiorna le scorciatoie dopo l'assegnazione
            shortcuts = SettingsManager.getLauncherShortcuts(context)
        }
    }
    
    // Funzione helper per avviare l'activity di assegnazione
    fun launchShortcutAssignment(keyCode: Int) {
        val intent = Intent(context, LauncherShortcutAssignmentActivity::class.java).apply {
            putExtra(LauncherShortcutAssignmentActivity.EXTRA_KEY_CODE, keyCode)
            putExtra(LauncherShortcutAssignmentActivity.EXTRA_SKIP_LAUNCH, true) // Non avviare l'app dalla schermata settings
        }
        launcherShortcutAssignmentLauncher.launch(intent)
    }
    
    // Drag and drop state
    var draggedKeyCode by remember { mutableStateOf<Int?>(null) }
    var dragStartPosition by remember { mutableStateOf<Offset?>(null) }
    var dragCurrentPosition by remember { mutableStateOf<Offset?>(null) }
    var draggedIcon by remember { mutableStateOf<android.graphics.drawable.Drawable?>(null) }
    var keyPositions by remember { mutableStateOf<Map<Int, androidx.compose.ui.geometry.Rect>>(emptyMap()) }
            var trashPosition by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
            var currentDropTarget by remember { mutableStateOf<Int?>(null) } // Track which key is currently under drag
            var containerPosition by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    
    BackHandler {
        onBack()
    }
    
    // Funzione helper per scambiare gli shortcut tra due tasti
    fun swapShortcuts(fromKeyCode: Int, toKeyCode: Int) {
        // Use atomic swap function from SettingsManager
        SettingsManager.swapLauncherShortcuts(context, fromKeyCode, toKeyCode)
        
        // Update local state
        shortcuts = SettingsManager.getLauncherShortcuts(context)
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
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
                    text = stringResource(R.string.launcher_shortcuts_screen_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Griglia QWERTY con larghezza fissa (come SYM layers)
        val qwertyRows = listOf(
            listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            listOf("Z", "X", "C", "V", "B", "N", "M")
        )
        
        // Funzione helper per ottenere l'icona dell'app
        fun getAppIcon(packageName: String?): android.graphics.drawable.Drawable? {
            return try {
                if (packageName != null && isPackageInstalled(packageName)) {
                    pm.getApplicationIcon(packageName)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
        
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInRoot()
                    containerPosition = position
                }
        ) {
            val density = LocalDensity.current
            // Calcola la larghezza fissa per ogni tasto (come nella visuale SYM)
            val maxKeysInRow = qwertyRows.maxOf { it.size }
            val keySpacing = 8.dp
            val totalSpacing = keySpacing * (maxKeysInRow - 1)
            val availableWidth = maxWidth - totalSpacing
            val fixedKeyWidth = availableWidth / maxKeysInRow
            val keySize = fixedKeyWidth
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                qwertyRows.forEachIndexed { rowIndex, row ->
                    // Calcola lo spazio necessario per centrare la riga
                    val rowKeysCount = row.size
                    val totalRowWidth = keySize * rowKeysCount + keySpacing * (rowKeysCount - 1)
                    val maxRowWidth = keySize * maxKeysInRow + keySpacing * (maxKeysInRow - 1)
                    val leftSpacing = (maxRowWidth - totalRowWidth) / 2
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Spacer a sinistra per centrare la riga (solo per seconda e terza riga)
                        if (rowIndex > 0) {
                            Spacer(modifier = Modifier.width(leftSpacing))
                        }
                        
                        row.forEachIndexed { keyIndex, keyName ->
                            val keyCode = when (keyName) {
                                "Q" -> KeyEvent.KEYCODE_Q
                                "W" -> KeyEvent.KEYCODE_W
                                "E" -> KeyEvent.KEYCODE_E
                                "R" -> KeyEvent.KEYCODE_R
                                "T" -> KeyEvent.KEYCODE_T
                                "Y" -> KeyEvent.KEYCODE_Y
                                "U" -> KeyEvent.KEYCODE_U
                                "I" -> KeyEvent.KEYCODE_I
                                "O" -> KeyEvent.KEYCODE_O
                                "P" -> KeyEvent.KEYCODE_P
                                "A" -> KeyEvent.KEYCODE_A
                                "S" -> KeyEvent.KEYCODE_S
                                "D" -> KeyEvent.KEYCODE_D
                                "F" -> KeyEvent.KEYCODE_F
                                "G" -> KeyEvent.KEYCODE_G
                                "H" -> KeyEvent.KEYCODE_H
                                "J" -> KeyEvent.KEYCODE_J
                                "K" -> KeyEvent.KEYCODE_K
                                "L" -> KeyEvent.KEYCODE_L
                                "Z" -> KeyEvent.KEYCODE_Z
                                "X" -> KeyEvent.KEYCODE_X
                                "C" -> KeyEvent.KEYCODE_C
                                "V" -> KeyEvent.KEYCODE_V
                                "B" -> KeyEvent.KEYCODE_B
                                "N" -> KeyEvent.KEYCODE_N
                                "M" -> KeyEvent.KEYCODE_M
                                else -> null
                            }
                            
                            if (keyCode != null) {
                                val shortcut = shortcuts[keyCode]
                                // Verify that shortcut exists, is an app type, has a package name, AND the app is still installed
                                val hasApp = shortcut != null && 
                                    shortcut.type == SettingsManager.LauncherShortcut.TYPE_APP && 
                                    shortcut.packageName != null &&
                                    isPackageInstalled(shortcut.packageName)
                                
                                // Use remember with shortcut.packageName as dependency
                                // This recalculates icon only when packageName changes
                                val appIcon = remember(shortcut?.packageName) {
                                    if (hasApp && shortcut?.packageName != null) {
                                        getAppIcon(shortcut.packageName)
                                    } else {
                                        null
                                    }
                                }
                                
                                if (keyIndex > 0) {
                                    Spacer(modifier = Modifier.width(keySpacing))
                                }
                                
                                // Use key() to force recomposition when shortcut changes
                                // When packageName changes, the entire Surface is recomposed
                                key(shortcut?.packageName ?: "none_$keyCode") {
                                    var keyPosition by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                                    
                                    Surface(
                                        modifier = Modifier
                                            .width(keySize)
                                            .aspectRatio(1f)
                                            .onGloballyPositioned { coordinates ->
                                                val position = coordinates.positionInRoot()
                                                val size = coordinates.size
                                                val bounds = androidx.compose.ui.geometry.Rect(
                                                    offset = position,
                                                    size = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())
                                                )
                                                keyPosition = bounds
                                                keyPositions = keyPositions + (keyCode to bounds)
                                            }
                                            .then(
                                                if (hasApp) {
                                                    // Combine clickable with drag gesture
                                                    // Use clickable for normal taps and pointerInput for drag
                                                    Modifier
                                                        .combinedClickable(
                                                            onClick = {
                                                                // Only handle click if not dragging
                                                                if (draggedKeyCode == null) {
                                                                    launchShortcutAssignment(keyCode)
                                                                }
                                                            },
                                                            onLongClick = {
                                                                // Long press handled by drag gesture
                                                            }
                                                        )
                                                        .pointerInput(keyCode) {
                                                            detectDragGesturesAfterLongPress(
                                                                onDragStart = { offset ->
                                                                    draggedKeyCode = keyCode
                                                                    draggedIcon = appIcon
                                                                    val startPos = keyPosition?.let { 
                                                                        androidx.compose.ui.geometry.Offset(it.center.x, it.center.y)
                                                                    }
                                                                    dragStartPosition = startPos
                                                                    dragCurrentPosition = startPos
                                                                    currentDropTarget = null
                                                                },
                                                                onDragEnd = {
                                                                    // Perform swap or delete only on release
                                                                    if (draggedKeyCode == keyCode) {
                                                                        // Check trash first
                                                                        val dropPos = dragCurrentPosition
                                                                        val overTrash = dropPos?.let { pos ->
                                                                            trashPosition?.let { rect ->
                                                                                pos.x >= rect.left &&
                                                                                pos.x <= rect.right &&
                                                                                pos.y >= rect.top &&
                                                                                pos.y <= rect.bottom
                                                                            } ?: false
                                                                        } ?: false
                                                                        
                                                                        if (overTrash) {
                                                                            // Delete shortcut
                                                                            SettingsManager.removeLauncherShortcut(context, keyCode)
                                                                            shortcuts = SettingsManager.getLauncherShortcuts(context)
                                                                        } else if (currentDropTarget != null && currentDropTarget != keyCode) {
                                                                            // Swap shortcuts with the drop target
                                                                            swapShortcuts(keyCode, currentDropTarget!!)
                                                                        }
                                                                    }
                                                                    
                                                                    draggedKeyCode = null
                                                                    dragStartPosition = null
                                                                    dragCurrentPosition = null
                                                                    draggedIcon = null
                                                                    currentDropTarget = null
                                                                },
                                                                onDragCancel = {
                                                                    draggedKeyCode = null
                                                                    dragStartPosition = null
                                                                    dragCurrentPosition = null
                                                                    draggedIcon = null
                                                                    currentDropTarget = null
                                                                },
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    // Calculate absolute position: current position + incremental drag amount
                                                                    val newPosition = dragCurrentPosition?.let { current ->
                                                                        current + dragAmount
                                                                    } ?: (dragStartPosition ?: change.position)
                                                                    dragCurrentPosition = newPosition
                                                                    
                                                                    // Check if over trash - just track, don't do anything yet
                                                                    val overTrash = trashPosition?.let { rect ->
                                                                        newPosition.x >= rect.left &&
                                                                        newPosition.x <= rect.right &&
                                                                        newPosition.y >= rect.top &&
                                                                        newPosition.y <= rect.bottom
                                                                    } ?: false
                                                                    
                                                                    if (overTrash) {
                                                                        // Not over a key, clear drop target
                                                                        currentDropTarget = null
                                                                    } else {
                                                                        // Check if over another key - just track, don't swap yet
                                                                        var foundTarget: Int? = null
                                                                        keyPositions.forEach { (targetKeyCode, targetRect) ->
                                                                            if (targetKeyCode != keyCode &&
                                                                                newPosition.x >= targetRect.left &&
                                                                                newPosition.x <= targetRect.right &&
                                                                                newPosition.y >= targetRect.top &&
                                                                                newPosition.y <= targetRect.bottom) {
                                                                                foundTarget = targetKeyCode
                                                                            }
                                                                        }
                                                                        // Just update the target tracking, no swap until release
                                                                        currentDropTarget = foundTarget
                                                                    }
                                                                }
                                                            )
                                                        }
                                                } else {
                                                    Modifier.combinedClickable(
                                                        onClick = {
                                                            launchShortcutAssignment(keyCode)
                                                        },
                                                        onLongClick = {
                                                            // Long press removed for unassigned keys
                                                        }
                                                    )
                                                }
                                            ),
                                        shape = MaterialTheme.shapes.medium,
                                        color = when {
                                            draggedKeyCode == keyCode -> MaterialTheme.colorScheme.secondaryContainer
                                            draggedKeyCode != null && draggedKeyCode != keyCode -> {
                                                // Check if drag is over this key
                                                val isDragOver = dragCurrentPosition?.let { pos ->
                                                    keyPosition?.let { rect ->
                                                        pos.x >= rect.left &&
                                                        pos.x <= rect.right &&
                                                        pos.y >= rect.top &&
                                                        pos.y <= rect.bottom
                                                    } ?: false
                                                } ?: false
                                                if (isDragOver) {
                                                    MaterialTheme.colorScheme.tertiaryContainer
                                                } else {
                                                    if (hasApp) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            }
                                            hasApp -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        tonalElevation = when {
                                            draggedKeyCode == keyCode -> 4.dp
                                            draggedKeyCode != null && draggedKeyCode != keyCode -> {
                                                val isDragOver = dragCurrentPosition?.let { pos ->
                                                    keyPosition?.let { rect ->
                                                        pos.x >= rect.left &&
                                                        pos.x <= rect.right &&
                                                        pos.y >= rect.top &&
                                                        pos.y <= rect.bottom
                                                    } ?: false
                                                } ?: false
                                                if (isDragOver) 3.dp else if (hasApp) 2.dp else 1.dp
                                            }
                                            else -> if (hasApp) 2.dp else 1.dp
                                        }
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (hasApp && appIcon != null) {
                                                // Mostra solo l'icona dell'app (riempie tutto il tasto)
                                                AndroidView(
                                                    factory = { ctx ->
                                                        ImageView(ctx).apply {
                                                            layoutParams = ViewGroup.LayoutParams(
                                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                                ViewGroup.LayoutParams.MATCH_PARENT
                                                            )
                                                            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                                            setImageDrawable(appIcon)
                                                        }
                                                    },
                                                    update = { imageView ->
                                                        // Update icon when appIcon changes during recomposition
                                                        imageView.setImageDrawable(appIcon)
                                                    },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                // Mostra la lettera del tasto
                                                Text(
                                                    text = keyName,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (hasApp) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Spacer a destra per centrare la riga
                        if (rowIndex > 0) {
                            Spacer(modifier = Modifier.width(leftSpacing))
                        }
                    }
                }
            }
            
            // Trash icon positioned absolutely: right aligned, positioned lower
            // Calculate horizontal position: align right with same padding as keyboard (16.dp), shifted left a bit
            val keyboardPadding = 16.dp
            val trashX = maxWidth - keyboardPadding - keySize - 9.dp
            // Calculate vertical position: start of third row (moved down half key from previous position)
            val trashY = keySize * 2 + keySpacing * 2
            
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                
                // Check if drag is over trash
                val isTrashHighlighted = dragCurrentPosition?.let { pos ->
                    trashPosition?.let { rect ->
                        pos.x >= rect.left &&
                        pos.x <= rect.right &&
                        pos.y >= rect.top &&
                        pos.y <= rect.bottom
                    } ?: false
                } ?: false
                
                Surface(
                    modifier = Modifier
                        .width(keySize)
                        .aspectRatio(1f)
                        .offset(x = trashX, y = trashY)
                        .onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInRoot()
                            val size = coordinates.size
                            trashPosition = androidx.compose.ui.geometry.Rect(
                                offset = position,
                                size = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())
                            )
                        },
                    shape = MaterialTheme.shapes.medium,
                    color = if (isTrashHighlighted) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        Color.Red.copy(alpha = 0.3f)
                    },
                    tonalElevation = if (isTrashHighlighted) 4.dp else 2.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete shortcut",
                            tint = if (isTrashHighlighted) {
                                Color.White
                            } else {
                                Color.Red
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            // Overlay for dragged icon
            if (draggedKeyCode != null && dragCurrentPosition != null && draggedIcon != null && containerPosition != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1000f)
                        .pointerInput(Unit) {
                            // Consume all pointer events to prevent interaction with underlying elements
                        }
                ) {
                    val iconSize = with(density) { keySize.toPx() }
                    // Convert absolute position to relative position within container
                    val relativeX = dragCurrentPosition!!.x - containerPosition!!.x
                    val relativeY = dragCurrentPosition!!.y - containerPosition!!.y
                    
                    AndroidView(
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    iconSize.toInt(),
                                    iconSize.toInt()
                                )
                                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                setImageDrawable(draggedIcon)
                                alpha = 0.8f
                            }
                        },
                        update = { imageView ->
                            imageView.setImageDrawable(draggedIcon)
                        },
                        modifier = Modifier
                            .offset(
                                x = with(density) { (relativeX - iconSize / 2).toDp() },
                                y = with(density) { (relativeY - iconSize / 2).toDp() }
                            )
                            .size(with(density) { iconSize.toDp() })
                            .scale(1.1f)
                            .alpha(0.8f)
                    )
                }
            }
        }
    }
}
