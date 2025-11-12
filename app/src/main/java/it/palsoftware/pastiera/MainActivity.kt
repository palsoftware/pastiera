package it.palsoftware.pastiera

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.KeyboardEventTracker
import it.palsoftware.pastiera.inputmethod.NotificationHelper
import it.palsoftware.pastiera.ui.theme.PastieraTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings

class MainActivity : ComponentActivity() {
    
    data class KeyEventInfo(
        val keyCode: Int,
        val keyCodeName: String,
        val action: String,
        val unicodeChar: Int,
        val isAltPressed: Boolean,
        val isShiftPressed: Boolean,
        val isCtrlPressed: Boolean
    )
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Notifica solo gli eventi che non sono keycode di output generati dal servizio
        // I keycode di output (DPAD, TAB, PAGE_UP, PAGE_DOWN, ESCAPE) senza modificatori
        // sono generati dal servizio e l'evento originale con output è già stato notificato
        if (event != null) {
            val isOutputKeyCode = keyCode in listOf(
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_TAB,
                KeyEvent.KEYCODE_PAGE_UP,
                KeyEvent.KEYCODE_PAGE_DOWN,
                KeyEvent.KEYCODE_ESCAPE
            )
            val hasModifiers = event.isAltPressed || event.isShiftPressed || event.isCtrlPressed
            
            // Ignora i keycode di output senza modificatori (sono generati dal servizio)
            if (!isOutputKeyCode || hasModifiers) {
                KeyboardEventTracker.notifyKeyEvent(keyCode, event, "KEY_DOWN")
            }
        }
        return super.onKeyDown(keyCode, event)
    }
    
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // Notifica solo gli eventi che non sono keycode di output generati dal servizio
        if (event != null) {
            val isOutputKeyCode = keyCode in listOf(
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_TAB,
                KeyEvent.KEYCODE_PAGE_UP,
                KeyEvent.KEYCODE_PAGE_DOWN,
                KeyEvent.KEYCODE_ESCAPE
            )
            val hasModifiers = event.isAltPressed || event.isShiftPressed || event.isCtrlPressed
            
            // Ignora i keycode di output senza modificatori (sono generati dal servizio)
            if (!isOutputKeyCode || hasModifiers) {
                KeyboardEventTracker.notifyKeyEvent(keyCode, event, "KEY_UP")
            }
        }
        return super.onKeyUp(keyCode, event)
    }
    
    private fun getKeyCodeName(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_Q -> "KEYCODE_Q"
            KeyEvent.KEYCODE_W -> "KEYCODE_W"
            KeyEvent.KEYCODE_E -> "KEYCODE_E"
            KeyEvent.KEYCODE_R -> "KEYCODE_R"
            KeyEvent.KEYCODE_T -> "KEYCODE_T"
            KeyEvent.KEYCODE_Y -> "KEYCODE_Y"
            KeyEvent.KEYCODE_U -> "KEYCODE_U"
            KeyEvent.KEYCODE_I -> "KEYCODE_I"
            KeyEvent.KEYCODE_O -> "KEYCODE_O"
            KeyEvent.KEYCODE_P -> "KEYCODE_P"
            KeyEvent.KEYCODE_A -> "KEYCODE_A"
            KeyEvent.KEYCODE_S -> "KEYCODE_S"
            KeyEvent.KEYCODE_D -> "KEYCODE_D"
            KeyEvent.KEYCODE_F -> "KEYCODE_F"
            KeyEvent.KEYCODE_G -> "KEYCODE_G"
            KeyEvent.KEYCODE_H -> "KEYCODE_H"
            KeyEvent.KEYCODE_J -> "KEYCODE_J"
            KeyEvent.KEYCODE_K -> "KEYCODE_K"
            KeyEvent.KEYCODE_L -> "KEYCODE_L"
            KeyEvent.KEYCODE_Z -> "KEYCODE_Z"
            KeyEvent.KEYCODE_X -> "KEYCODE_X"
            KeyEvent.KEYCODE_C -> "KEYCODE_C"
            KeyEvent.KEYCODE_V -> "KEYCODE_V"
            KeyEvent.KEYCODE_B -> "KEYCODE_B"
            KeyEvent.KEYCODE_N -> "KEYCODE_N"
            KeyEvent.KEYCODE_M -> "KEYCODE_M"
            KeyEvent.KEYCODE_SPACE -> "KEYCODE_SPACE"
            KeyEvent.KEYCODE_ENTER -> "KEYCODE_ENTER"
            KeyEvent.KEYCODE_DEL -> "KEYCODE_DEL"
            KeyEvent.KEYCODE_BACK -> "KEYCODE_BACK"
            else -> "KEYCODE_$keyCode"
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PastieraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    KeyboardSetupScreen(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        activity = this@MainActivity
                    )
                }
            }
        }
    }
}

@Composable
fun KeyboardSetupScreen(
    modifier: Modifier = Modifier,
    activity: MainActivity
) {
    val context = LocalContext.current
    
    var testText by remember { mutableStateOf("") }
    val lastKeyEventState = remember { mutableStateOf<KeyboardEventTracker.KeyEventInfo?>(null) }
    val lastKeyEvent by lastKeyEventState
    
    // Stato per la navigazione alle impostazioni
    var showSettings by remember { mutableStateOf(false) }
    var showSymCustomization by remember { mutableStateOf(false) }
    
    // Richiesta permesso per le notifiche (Android 13+)
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            android.util.Log.d("MainActivity", "Permesso per le notifiche concesso")
        } else {
            android.util.Log.w("MainActivity", "Permesso per le notifiche negato")
        }
    }
    
    // Richiedi il permesso per le notifiche quando il composable viene creato (solo su Android 13+)
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationHelper.hasNotificationPermission(context)) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    
    // Collega lo stato al tracker globale
    LaunchedEffect(Unit) {
        KeyboardEventTracker.registerState(lastKeyEventState)
    }
    
    // Pulisci lo stato quando il composable viene rimosso
    DisposableEffect(Unit) {
        onDispose {
            KeyboardEventTracker.unregisterState()
        }
    }
    
    // Navigazione condizionale con animazioni
    AnimatedContent(
        targetState = when {
            showSymCustomization -> "sym"
            showSettings -> "settings"
            else -> "main"
        },
        transitionSpec = {
            when {
                targetState == "sym" && initialState == "settings" -> {
                    // Slide da destra quando vai a SYM
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(300)
                    ) togetherWith slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(300)
                    )
                }
                targetState == "settings" && initialState == "main" -> {
                    // Slide da destra quando vai a Settings
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(300)
                    ) togetherWith fadeOut(animationSpec = tween(200))
                }
                targetState == "main" && initialState == "settings" -> {
                    // Slide da sinistra quando torni indietro
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(300)
                    ) togetherWith slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(300)
                    )
                }
                targetState == "settings" && initialState == "sym" -> {
                    // Slide da sinistra quando torni da SYM
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(300)
                    ) togetherWith slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(300)
                    )
                }
                else -> {
                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                }
            }
        },
        modifier = modifier.fillMaxSize(),
        label = "screen_transition"
    ) { target ->
        when (target) {
            "sym" -> {
                SymCustomizationScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBack = { showSymCustomization = false }
                )
            }
            "settings" -> {
                SettingsScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBack = { showSettings = false },
                    onSymCustomizationClick = { showSymCustomization = true }
                )
            }
            else -> {
                // Main screen
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
        // Header moderno
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.keyboard_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.settings_content_description)
                    )
                }
            }
        }
        
        // Pulsante per aprire impostazioni tastiera
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                    context.startActivity(intent)
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.open_keyboard_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        HorizontalDivider()
        
        // Campo di test
        OutlinedTextField(
            value = testText,
            onValueChange = { testText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text(stringResource(R.string.test_field_placeholder)) },
            minLines = 5,
            maxLines = 10
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Ultimo evento tastiera (solo se presente)
        val event = lastKeyEvent
        if (event != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.last_keyboard_event_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${event.keyCodeName}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Unicode: ${event.unicodeChar} (${if (event.unicodeChar != 0) event.unicodeChar.toChar() else "N/A"})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                    if (event.outputKeyCodeName != null) {
                        Text(
                            text = "Output: ${event.outputKeyCodeName}${if (event.outputKeyCode != null) " (${event.outputKeyCode})" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (event.isShiftPressed || event.isCtrlPressed || event.isAltPressed) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (event.isShiftPressed) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = stringResource(R.string.modifier_shift),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            if (event.isCtrlPressed) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = stringResource(R.string.modifier_ctrl),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            if (event.isAltPressed) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "ALT",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
                }
            }
        }
    }
}