package it.palsoftware.pastiera

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Keyboard
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
    initialPage: Int = 0,
    initialKeyCode: Int? = null,
    openInitialPicker: Boolean = false,
    returnAfterInitialPicker: Boolean = false,
    onInitialPickerClosed: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // Load saved auto-close SYM value
    var symAutoClose by remember { 
        mutableStateOf(SettingsManager.getSymAutoClose(context))
    }
    var symAutoCloseOnTouch by remember {
        mutableStateOf(SettingsManager.getSymAutoCloseOnTouch(context))
    }
    var emojiPickerExpandedHeight by remember {
        mutableStateOf(SettingsManager.getEmojiPickerExpandedHeight(context))
    }

    var titan2LayoutEnabled by remember {
        mutableStateOf(SettingsManager.isTitan2LayoutEnabled(context))
    }
    
    // Load SYM pages configuration (enabled pages + order)
    var symPagesConfig by remember {
        mutableStateOf(SettingsManager.getSymPagesConfig(context))
    }
    fun persistSymPagesConfig(config: SymPagesConfig) {
        symPagesConfig = config
        SettingsManager.setSymPagesConfig(context, config)
    }
    val normalizedSymPageOrder = symPagesConfig.normalizedOrder()
    fun movePageOrderItem(fromIndex: Int, toIndex: Int) {
        val mutable = normalizedSymPageOrder.toMutableList()
        if (fromIndex !in mutable.indices || toIndex !in mutable.indices || fromIndex == toIndex) {
            return
        }
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        persistSymPagesConfig(symPagesConfig.copy(symPageOrder = mutable))
    }
    fun symPageTitle(pageId: String): String = when (pageId) {
        SymPagesConfig.PAGE_EMOJI -> context.getString(R.string.sym_enable_emoji_page_title)
        SymPagesConfig.PAGE_SYMBOLS -> context.getString(R.string.sym_enable_symbols_page_title)
        SymPagesConfig.PAGE_CLIPBOARD -> context.getString(R.string.sym_enable_clipboard_page_title)
        SymPagesConfig.PAGE_EMOJI_PICKER -> context.getString(R.string.sym_enable_emoji_picker_page_title)
        SymPagesConfig.PAGE_GIF_PICKER -> context.getString(R.string.sym_enable_gif_picker_page_title)
        else -> pageId
    }
    var draggingPageId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableStateOf<Int?>(null) }
    var dropTargetIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val rowStepPx = with(LocalDensity.current) { 56.dp.toPx() }

    fun endPageOrderDrag() {
        val start = dragStartIndex
        val target = dropTargetIndex
        if (start != null && target != null && start != target) {
            movePageOrderItem(start, target)
        }
        draggingPageId = null
        dragStartIndex = null
        dropTargetIndex = null
        dragOffsetY = 0f
    }
    
    // Selected tab (0 = Emoji, 1 = Characters)
    var selectedTab by remember {
        mutableStateOf(if (initialPage == 2) 1 else 0)
    }
    
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
    var initialPickerHandled by remember { mutableStateOf(false) }
    var initialPickerActive by remember { mutableStateOf(false) }
    
    // State for reset confirmation dialog
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var resetPage by remember { mutableStateOf<Int?>(null) } // 1 for page1, 2 for page2
    
    // Note: System back button is handled by Activity.onBackPressedDispatcher
    // to follow Android history. This BackHandler is removed to allow default behavior.
    
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

    LaunchedEffect(initialPage, initialKeyCode, openInitialPicker) {
        if (initialPickerHandled) return@LaunchedEffect
        initialPickerHandled = true
        when (initialPage) {
            1 -> selectedTab = 0
            2 -> selectedTab = 1
        }
        val keyCode = initialKeyCode ?: return@LaunchedEffect
        if (!openInitialPicker) return@LaunchedEffect
        selectedKeyCode = keyCode
        initialPickerActive = returnAfterInitialPicker
        if (initialPage == 2) {
            showCharacterPicker = true
        } else {
            showEmojiPicker = true
        }
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
                        text = stringResource(R.string.sym_customize_title),
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        
        // Auto-Close SYM Layout option (in alto)
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
                    imageVector = Icons.Filled.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sym_auto_close_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.sym_auto_close_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Switch(
                    checked = symAutoClose,
                    onCheckedChange = { enabled ->
                        symAutoClose = enabled
                        SettingsManager.setSymAutoClose(context, enabled)
                    }
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
                    .padding(start = 52.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sym_auto_close_touch_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (symAutoClose) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.sym_auto_close_touch_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Switch(
                    checked = symAutoCloseOnTouch,
                    enabled = symAutoClose,
                    onCheckedChange = { enabled ->
                        symAutoCloseOnTouch = enabled
                        SettingsManager.setSymAutoCloseOnTouch(context, enabled)
                    }
                )
            }
        }

        // Titan 2 Layout Alignment toggle
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
                    imageVector = Icons.Filled.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.titan2_layout_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.titan2_layout_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Switch(
                    checked = titan2LayoutEnabled,
                    onCheckedChange = { enabled ->
                        titan2LayoutEnabled = enabled
                        SettingsManager.setTitan2LayoutEnabled(context, enabled)
                    }
                )
            }
        }
        
        HorizontalDivider()
        
        // Tab selector (visualizzazione del layout)
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
                key(symMappingsPage1, titan2LayoutEnabled) {
                    AndroidView(
                        factory = { ctx ->
                            statusBarController.createCustomizableEmojiKeyboard(symMappingsPage1, { keyCode, emoji ->
                                selectedKeyCode = keyCode
                                showEmojiPicker = true
                            }, page = 1)
                        },
                        update = { _ ->
                            // The key(titan2LayoutEnabled) will trigger a full recomposition/re-factory
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            1 -> {
                // Characters tab
                key(symMappingsPage2, titan2LayoutEnabled) {
                    AndroidView(
                        factory = { ctx ->
                            statusBarController.createCustomizableEmojiKeyboard(symMappingsPage2, { keyCode, character ->
                                selectedKeyCode = keyCode
                                showCharacterPicker = true
                            }, page = 2)
                        },
                        update = { _ ->
                            // The key(titan2LayoutEnabled) will trigger a full recomposition/re-factory
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Reset button (ripristina predefiniti)
        Button(
            onClick = {
                resetPage = selectedTab + 1 // 1 for emoji tab, 2 for characters tab
                showResetConfirmDialog = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text(
                stringResource(R.string.sym_reset_to_default), 
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onError
            )
        }
        
        HorizontalDivider()

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
                    imageVector = Icons.Filled.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.emoji_picker_expanded_height_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.emoji_picker_expanded_height_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Switch(
                    checked = emojiPickerExpandedHeight,
                    onCheckedChange = { enabled ->
                        emojiPickerExpandedHeight = enabled
                        SettingsManager.setEmojiPickerExpandedHeight(context, enabled)
                    }
                )
            }
        }
        
        // Emoji page toggle
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
                    imageVector = Icons.Filled.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sym_enable_emoji_page_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.sym_enable_emoji_page_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Switch(
                    checked = symPagesConfig.emojiEnabled,
                    onCheckedChange = { enabled ->
                        persistSymPagesConfig(symPagesConfig.copy(emojiEnabled = enabled))
                    }
                )
            }
        }
        
        // Symbols page toggle
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
                    imageVector = Icons.Filled.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sym_enable_symbols_page_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.sym_enable_symbols_page_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Switch(
                    checked = symPagesConfig.symbolsEnabled,
                    onCheckedChange = { enabled ->
                        persistSymPagesConfig(symPagesConfig.copy(symbolsEnabled = enabled))
                    }
                )
            }
        }

        // Clipboard page toggle
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
                    imageVector = Icons.Filled.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sym_enable_clipboard_page_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.sym_enable_clipboard_page_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Switch(
                    checked = symPagesConfig.clipboardEnabled,
                    onCheckedChange = { enabled ->
                        persistSymPagesConfig(symPagesConfig.copy(clipboardEnabled = enabled))
                    }
                )
            }
        }

        // Emoji picker page toggle
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
                    imageVector = Icons.Filled.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sym_enable_emoji_picker_page_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.sym_enable_emoji_picker_page_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Switch(
                    checked = symPagesConfig.emojiPickerEnabled,
                    onCheckedChange = { enabled ->
                        persistSymPagesConfig(symPagesConfig.copy(emojiPickerEnabled = enabled))
                    }
                )
            }
        }

        // GIF picker page toggle
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
                    imageVector = Icons.Filled.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sym_enable_gif_picker_page_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = stringResource(R.string.sym_enable_gif_picker_page_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Switch(
                    checked = symPagesConfig.gifPickerEnabled,
                    onCheckedChange = { enabled ->
                        persistSymPagesConfig(symPagesConfig.copy(gifPickerEnabled = enabled))
                    }
                )
            }
        }

        // Page order control
        Surface(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Keyboard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sym_swap_pages_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = stringResource(R.string.sym_swap_pages_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }

                normalizedSymPageOrder.forEachIndexed { index, pageId ->
                    val enabled = symPagesConfig.isPageEnabled(pageId)
                    val isDragging = draggingPageId == pageId
                    val isDropTarget = dropTargetIndex == index && !isDragging && draggingPageId != null
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = if (isDragging) 6.dp else 1.dp,
                        color = if (isDropTarget) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .pointerInput(pageId, normalizedSymPageOrder) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingPageId = pageId
                                            dragStartIndex = index
                                            dropTargetIndex = index
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            endPageOrderDrag()
                                        },
                                        onDragEnd = {
                                            endPageOrderDrag()
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val start = dragStartIndex ?: return@detectDragGesturesAfterLongPress
                                            dragOffsetY += dragAmount.y
                                            val deltaSlots = (dragOffsetY / rowStepPx).toInt()
                                            val target = (start + deltaSlots).coerceIn(0, normalizedSymPageOrder.lastIndex)
                                            dropTargetIndex = target
                                        }
                                    )
                                }
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffsetY else 0f
                                    scaleX = if (isDragging) 1.02f else 1f
                                    scaleY = if (isDragging) 1.02f else 1f
                                }
                                .shadow(if (isDragging) 8.dp else 0.dp, MaterialTheme.shapes.small)
                                .zIndex(if (isDragging) 1f else 0f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DragHandle,
                                contentDescription = null,
                                tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = symPageTitle(pageId),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (enabled) "On" else "Off",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { movePageOrderItem(index, index - 1) },
                                enabled = index > 0
                            ) {
                                Text("↑")
                            }
                            TextButton(
                                onClick = { movePageOrderItem(index, index + 1) },
                                enabled = index < normalizedSymPageOrder.lastIndex
                            ) {
                                Text("↓")
                            }
                        }
                    }
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
                    if (initialPickerActive) {
                        initialPickerActive = false
                        onInitialPickerClosed()
                    }
                },
                onDismiss = {
                    showEmojiPicker = false
                    selectedKeyCode = null
                    if (initialPickerActive) {
                        initialPickerActive = false
                        onInitialPickerClosed()
                    }
                }
            )
        }
        
        // Unicode character picker dialog
        if (showCharacterPicker && selectedKeyCode != null) {
            val selectedLetter = getLetterFromKeyCode(selectedKeyCode!!)
            UnicodeCharacterPickerDialog(
                selectedLetter = selectedLetter,
                onCharacterSelected = { character ->
                    val keyCode = selectedKeyCode!!
                    val resolvedCharacter = if (character.isEmpty()) {
                        defaultMappingsPage2[keyCode]
                    } else {
                        character
                    }
                    symMappingsPage2 = symMappingsPage2.toMutableMap().apply {
                        if (resolvedCharacter != null) {
                            put(keyCode, resolvedCharacter)
                        } else {
                            remove(keyCode)
                        }
                    }
                    SettingsManager.saveSymMappingsPage2(context, symMappingsPage2)
                    showCharacterPicker = false
                    selectedKeyCode = null
                    if (initialPickerActive) {
                        initialPickerActive = false
                        onInitialPickerClosed()
                    }
                },
                onDismiss = {
                    showCharacterPicker = false
                    selectedKeyCode = null
                    if (initialPickerActive) {
                        initialPickerActive = false
                        onInitialPickerClosed()
                    }
                }
            )
        }
        
        // Reset confirmation dialog
        if (showResetConfirmDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showResetConfirmDialog = false
                    resetPage = null
                },
                title = {
                    Text(stringResource(R.string.sym_reset_confirm_title))
                },
                text = {
                    Text(stringResource(R.string.sym_reset_confirm_message))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            when (resetPage) {
                                1 -> {
                                    symMappingsPage1 = defaultMappingsPage1.toMutableMap()
                                    SettingsManager.resetSymMappings(context)
                                }
                                2 -> {
                                    symMappingsPage2 = defaultMappingsPage2.toMutableMap()
                                    SettingsManager.resetSymMappingsPage2(context)
                                }
                            }
                            showResetConfirmDialog = false
                            resetPage = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.sym_reset_confirm_button))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showResetConfirmDialog = false
                            resetPage = null
                        }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        }
    }
}
