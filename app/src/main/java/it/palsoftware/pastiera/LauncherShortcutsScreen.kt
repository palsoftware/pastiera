package it.palsoftware.pastiera

import android.content.Intent
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.ArrowBack
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
    
    // Carica le scorciatoie salvate
    var shortcuts by remember {
        mutableStateOf(SettingsManager.getLauncherShortcuts(context))
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
    
    BackHandler {
        onBack()
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
                        imageVector = Icons.Filled.ArrowBack,
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
        
        val pm = context.packageManager
        
        // Funzione helper per ottenere l'icona dell'app
        fun getAppIcon(packageName: String?): android.graphics.drawable.Drawable? {
            return try {
                if (packageName != null) {
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
                                val hasApp = shortcut != null && shortcut.type == SettingsManager.LauncherShortcut.TYPE_APP && shortcut.packageName != null
                                val appIcon = if (hasApp) getAppIcon(shortcut.packageName) else null
                                
                                if (keyIndex > 0) {
                                    Spacer(modifier = Modifier.width(keySpacing))
                                }
                                
                                Surface(
                                    modifier = Modifier
                                        .width(keySize)
                                        .aspectRatio(1f)
                                        .combinedClickable(
                                            onClick = {
                                                launchShortcutAssignment(keyCode)
                                            },
                                            onLongClick = {
                                                if (hasApp) {
                                                    SettingsManager.removeLauncherShortcut(context, keyCode)
                                                    shortcuts = SettingsManager.getLauncherShortcuts(context)
                                                }
                                            }
                                        ),
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (hasApp) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    tonalElevation = if (hasApp) 2.dp else 1.dp
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
                        
                        // Spacer a destra per centrare la riga
                        if (rowIndex > 0) {
                            Spacer(modifier = Modifier.width(leftSpacing))
                        }
                    }
                }
            }
        }
    }
}

