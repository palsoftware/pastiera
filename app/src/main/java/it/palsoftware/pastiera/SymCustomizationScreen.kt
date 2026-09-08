package it.palsoftware.pastiera

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.StatusBarController
import kotlinx.coroutines.launch

/**
 * Screen for customizing SYM mappings.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val coroutineScope = rememberCoroutineScope()
    val screenScrollState = rememberScrollState()
    
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

    val titan2LayoutEnabled = remember {
        SettingsManager.isTitan2LayoutEnabled(context)
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
        SymPagesConfig.PAGE_DEVICE -> context.getString(R.string.sym_cycle_device_layer)
        SymPagesConfig.PAGE_EMOJI -> context.getString(R.string.sym_cycle_emoji_layer)
        SymPagesConfig.PAGE_SYMBOLS -> context.getString(R.string.sym_cycle_symbols_layer)
        SymPagesConfig.PAGE_CLIPBOARD -> context.getString(R.string.sym_cycle_clipboard_panel)
        SymPagesConfig.PAGE_EMOJI_PICKER -> context.getString(R.string.sym_cycle_emoji_picker_panel)
        else -> pageId
    }
    fun setPageEnabled(pageId: String, enabled: Boolean) {
        persistSymPagesConfig(
            when (pageId) {
                SymPagesConfig.PAGE_DEVICE -> symPagesConfig.copy(deviceEnabled = enabled)
                SymPagesConfig.PAGE_EMOJI -> symPagesConfig.copy(emojiEnabled = enabled)
                SymPagesConfig.PAGE_SYMBOLS -> symPagesConfig.copy(symbolsEnabled = enabled)
                SymPagesConfig.PAGE_CLIPBOARD -> symPagesConfig.copy(clipboardEnabled = enabled)
                SymPagesConfig.PAGE_EMOJI_PICKER -> symPagesConfig.copy(emojiPickerEnabled = enabled)
                else -> symPagesConfig
            }
        )
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
    var editingLayerPage by remember {
        mutableStateOf(initialPage.takeIf { it == 1 || it == 2 })
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

    BackHandler(enabled = editingLayerPage != null) {
        editingLayerPage = null
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
                    IconButton(onClick = {
                        if (editingLayerPage != null) editingLayerPage = null else onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = when (editingLayerPage) {
                            1 -> stringResource(R.string.sym_edit_emoji_layer_title)
                            2 -> stringResource(R.string.sym_edit_symbols_layer_title)
                            else -> stringResource(R.string.sym_customize_title)
                        },
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
                .verticalScroll(screenScrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        if (editingLayerPage == null) {
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
                        modifier = Modifier
                            .fillMaxWidth(),
                        tonalElevation = if (isDragging) 6.dp else 1.dp,
                        color = if (isDropTarget) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
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
                                tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.pointerInput(pageId, normalizedSymPageOrder) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingPageId = pageId
                                            dragStartIndex = index
                                            dropTargetIndex = index
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = { endPageOrderDrag() },
                                        onDragEnd = { endPageOrderDrag() },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val start = dragStartIndex ?: return@detectDragGesturesAfterLongPress
                                            dragOffsetY += dragAmount.y
                                            val deltaSlots = (dragOffsetY / rowStepPx).toInt()
                                            dropTargetIndex = (start + deltaSlots)
                                                .coerceIn(0, normalizedSymPageOrder.lastIndex)
                                        }
                                    )
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = symPageTitle(pageId),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(
                                        if (pageId == SymPagesConfig.PAGE_CLIPBOARD ||
                                            pageId == SymPagesConfig.PAGE_EMOJI_PICKER
                                        ) R.string.sym_cycle_type_panel else R.string.sym_cycle_type_key_layer
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (pageId == SymPagesConfig.PAGE_DEVICE) {
                                FeatureStatusIcon(FeatureStatus.Construction)
                            }
                            if (pageId == SymPagesConfig.PAGE_DEVICE ||
                                pageId == SymPagesConfig.PAGE_EMOJI ||
                                pageId == SymPagesConfig.PAGE_SYMBOLS
                            ) {
                                IconButton(onClick = {
                                    when (pageId) {
                                        SymPagesConfig.PAGE_DEVICE -> context.startActivity(
                                            Intent(context, SettingsActivity::class.java).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                                                putExtra(
                                                    SettingsActivity.EXTRA_DESTINATION,
                                                    SettingsActivity.DESTINATION_DEVICE_SYM_LAYER_EDITOR
                                                )
                                            }
                                        )
                                        SymPagesConfig.PAGE_EMOJI -> {
                                            selectedTab = 0
                                            editingLayerPage = 1
                                            coroutineScope.launch { screenScrollState.animateScrollTo(0) }
                                        }
                                        SymPagesConfig.PAGE_SYMBOLS -> {
                                            selectedTab = 1
                                            editingLayerPage = 2
                                            coroutineScope.launch { screenScrollState.animateScrollTo(0) }
                                        }
                                    }
                                }) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = stringResource(R.string.sym_edit_layer_content_description)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { movePageOrderItem(index, index - 1) },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.sym_move_up))
                            }
                            IconButton(
                                onClick = { movePageOrderItem(index, index + 1) },
                                enabled = index < normalizedSymPageOrder.lastIndex
                            ) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.sym_move_down))
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { setPageEnabled(pageId, it) }
                            )
                        }
                    }
                }
            }
        }
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(
                        Intent(context, SettingsActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(SettingsActivity.EXTRA_DESTINATION, SettingsActivity.DESTINATION_MODIFIERS)
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Filled.Keyboard, null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.alt_binding_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        stringResource(R.string.sym_modifiers_deeplink_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
            }
        }

        SettingsSectionDivider(stringResource(R.string.sym_behavior_section_title))


        // Auto-Close SYM Layout option (in alto)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
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
                .heightIn(min = 64.dp)
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

        HorizontalDivider()
        
        }

        if (editingLayerPage != null) {
        // Customizable keyboard grid - uses the same layout as the real keyboard
        val statusBarController = remember { StatusBarController(context) }
        
        // Show the grid based on the selected tab
        when (editingLayerPage) {
            1 -> {
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
            2 -> {
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
                resetPage = editingLayerPage ?: 1
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
        
        }

        if (editingLayerPage == null) {

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
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
        
        HorizontalDivider()

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
