package it.palsoftware.pastiera

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.StatusBarController

/**
 * Screen for customizing SYM mappings.
 */
@Composable
fun SymCustomizationScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // Selected tab (0 = Emoji, 1 = Characters)
    var selectedTab by remember { mutableStateOf(0) }
    
    // Helper to load mappings from JSON
    fun loadMappingsFromJson(filePath: String): Map<Int, String> {
        return try {
            val inputStream = context.assets.open(filePath)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = org.json.JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")
            val keyCodeMap = mapOf(
                "KEYCODE_Q" to KeyEvent.KEYCODE_Q, "KEYCODE_W" to KeyEvent.KEYCODE_W,
                "KEYCODE_E" to KeyEvent.KEYCODE_E, "KEYCODE_R" to KeyEvent.KEYCODE_R,
                "KEYCODE_T" to KeyEvent.KEYCODE_T, "KEYCODE_Y" to KeyEvent.KEYCODE_Y,
                "KEYCODE_U" to KeyEvent.KEYCODE_U, "KEYCODE_I" to KeyEvent.KEYCODE_I,
                "KEYCODE_O" to KeyEvent.KEYCODE_O, "KEYCODE_P" to KeyEvent.KEYCODE_P,
                "KEYCODE_A" to KeyEvent.KEYCODE_A, "KEYCODE_S" to KeyEvent.KEYCODE_S,
                "KEYCODE_D" to KeyEvent.KEYCODE_D, "KEYCODE_F" to KeyEvent.KEYCODE_F,
                "KEYCODE_G" to KeyEvent.KEYCODE_G, "KEYCODE_H" to KeyEvent.KEYCODE_H,
                "KEYCODE_J" to KeyEvent.KEYCODE_J, "KEYCODE_K" to KeyEvent.KEYCODE_K,
                "KEYCODE_L" to KeyEvent.KEYCODE_L, "KEYCODE_Z" to KeyEvent.KEYCODE_Z,
                "KEYCODE_X" to KeyEvent.KEYCODE_X, "KEYCODE_C" to KeyEvent.KEYCODE_C,
                "KEYCODE_V" to KeyEvent.KEYCODE_V, "KEYCODE_B" to KeyEvent.KEYCODE_B,
                "KEYCODE_N" to KeyEvent.KEYCODE_N, "KEYCODE_M" to KeyEvent.KEYCODE_M
            )
            val result = mutableMapOf<Int, String>()
            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyCodeMap[keyName]
                val content = mappingsObject.getString(keyName)
                if (keyCode != null) {
                    result[keyCode] = content
                }
            }
            result
        } catch (e: Exception) {
            emptyMap<Int, String>()
        }
    }
    
    // Load default mappings for page 1 (emoji)
    val defaultMappingsPage1 = remember {
        loadMappingsFromJson("common/sym/sym_key_mappings.json")
    }
    
    // Load default mappings for page 2 (characters)
    val defaultMappingsPage2 = remember {
        loadMappingsFromJson("common/sym/sym_key_mappings_page2.json")
    }
    
    // Load custom mappings or fallback to defaults for page 1
    var symMappingsPage1 by remember {
        mutableStateOf(
            SettingsManager.getSymMappings(context).takeIf { it.isNotEmpty() }
                ?: defaultMappingsPage1
        )
    }
    
    // Load custom mappings or fallback to defaults for page 2
    var symMappingsPage2 by remember {
        mutableStateOf(
            SettingsManager.getSymMappingsPage2(context).takeIf { it.isNotEmpty() }
                ?: defaultMappingsPage2
        )
    }
    
    // State for picker dialogs
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showCharacterPicker by remember { mutableStateOf(false) }
    var selectedKeyCode by remember { mutableStateOf<Int?>(null) }
    
    // Handle the system back button
    BackHandler {
        onBack()
    }
    
    // Helper function to convert keycode to letter
    fun getLetterFromKeyCode(keyCode: Int): String {
        return when (keyCode) {
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
            else -> "?"
        }
    }
    
    AnimatedContent(
        targetState = Unit,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        },
        label = "sym_customization_animation"
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        // Header with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back_content_description)
                )
            }
            Text(
                text = stringResource(R.string.sym_customize_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        
        HorizontalDivider()
        
        // Tab selector
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.sym_tab_emoji)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.sym_tab_characters)) }
            )
        }
        
        // Customizable keyboard grid - uses the same layout as the real keyboard
        val statusBarController = remember { StatusBarController(context) }
        
        // Show the grid based on the selected tab
        when (selectedTab) {
            0 -> {
                // Emoji tab
                key(symMappingsPage1) {
                    AndroidView(
                        factory = { ctx ->
                            statusBarController.createCustomizableEmojiKeyboard(symMappingsPage1, { keyCode, emoji ->
                                selectedKeyCode = keyCode
                                showEmojiPicker = true
                            }, page = 1)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Reset button for page 1
                Button(
                    onClick = {
                        symMappingsPage1 = defaultMappingsPage1.toMutableMap()
                        SettingsManager.resetSymMappings(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(stringResource(R.string.sym_reset_to_default), style = MaterialTheme.typography.bodyMedium)
                }
            }
            1 -> {
                // Characters tab
                key(symMappingsPage2) {
                    AndroidView(
                        factory = { ctx ->
                            statusBarController.createCustomizableEmojiKeyboard(symMappingsPage2, { keyCode, character ->
                                selectedKeyCode = keyCode
                                showCharacterPicker = true
                            }, page = 2)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Reset button for page 2
                Button(
                    onClick = {
                        symMappingsPage2 = defaultMappingsPage2.toMutableMap()
                        SettingsManager.resetSymMappingsPage2(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(stringResource(R.string.sym_reset_to_default), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        
        // Emoji picker dialog
        if (showEmojiPicker && selectedKeyCode != null) {
            val selectedLetter = getLetterFromKeyCode(selectedKeyCode!!)
            EmojiPickerDialog(
                selectedLetter = selectedLetter,
                onEmojiSelected = { emoji ->
                    symMappingsPage1 = symMappingsPage1.toMutableMap().apply {
                        put(selectedKeyCode!!, emoji)
                    }
                    SettingsManager.saveSymMappings(context, symMappingsPage1)
                    showEmojiPicker = false
                    selectedKeyCode = null
                },
                onDismiss = {
                    showEmojiPicker = false
                    selectedKeyCode = null
                }
            )
        }
        
        // Unicode character picker dialog
        if (showCharacterPicker && selectedKeyCode != null) {
            val selectedLetter = getLetterFromKeyCode(selectedKeyCode!!)
            UnicodeCharacterPickerDialog(
                selectedLetter = selectedLetter,
                onCharacterSelected = { character ->
                    symMappingsPage2 = symMappingsPage2.toMutableMap().apply {
                        put(selectedKeyCode!!, character)
                    }
                    SettingsManager.saveSymMappingsPage2(context, symMappingsPage2)
                    showCharacterPicker = false
                    selectedKeyCode = null
                },
                onDismiss = {
                    showCharacterPicker = false
                    selectedKeyCode = null
                }
            )
        }
        }
    }
}

