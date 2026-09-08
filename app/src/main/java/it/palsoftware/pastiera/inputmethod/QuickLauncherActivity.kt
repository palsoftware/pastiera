package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import it.palsoftware.pastiera.AppListHelper
import it.palsoftware.pastiera.LocalizedComponentActivity
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.commands.CommandExecutor
import it.palsoftware.pastiera.commands.CommandIcon
import it.palsoftware.pastiera.commands.CommandKind
import it.palsoftware.pastiera.commands.CommandLaunchSpec
import it.palsoftware.pastiera.commands.CommandRegistry
import it.palsoftware.pastiera.commands.CommandSourceId
import it.palsoftware.pastiera.commands.CommandSurface
import it.palsoftware.pastiera.commands.CommandTarget
import it.palsoftware.pastiera.data.layout.JsonLayoutLoader
import it.palsoftware.pastiera.data.layout.LayoutMapping
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils
import it.palsoftware.pastiera.ui.theme.PastieraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuickLauncherActivity : LocalizedComponentActivity() {
    private var commands: List<CommandTarget> = emptyList()
    private var query by mutableStateOf("")
    private var autoStartSingle by mutableStateOf(false)
    private var limitResults by mutableStateOf(false)
    private var widthPercent by mutableStateOf(100)
    private var pillMode by mutableStateOf(false)
    private var respectKeyboardLayout by mutableStateOf(true)
    private var typoTolerantRanking by mutableStateOf(false)
    private var filteredCommands by mutableStateOf(emptyList<CommandTarget>())
    private var commandCustomizations: Map<String, SettingsManager.QuickLauncherCommandCustomization> = emptyMap()
    private var commandCustomizationRevision by mutableStateOf(0)
    private var highlightFavorites by mutableStateOf(true)
    private var favoriteColor by mutableStateOf(SettingsManager.QUICK_LAUNCHER_DYNAMIC_FAVORITE_COLOR)
    private var iconColors by mutableStateOf(false)
    private var showAliasFirst by mutableStateOf(true)
    private var staticTopHighlight by mutableStateOf(false)
    private var staticTopHighlightColor by mutableStateOf(0x7A4285F4)
    private var animationDurationMs by mutableStateOf(120)
    private var loadingApps by mutableStateOf(false)
    private var launchedAutomatically = false
    private var keyboardLayout: Map<Int, LayoutMapping> = emptyMap()
    private var enterHandledOnKeyDown = false
    private var pendingDismissKeyCode: Int? = null
    private var dismissRequestId by mutableStateOf(0)
    private var dismissInProgress = false
    private var quickLauncherPrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableActivityAnimations()
        window.requestFeature(android.view.Window.FEATURE_NO_TITLE)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        autoStartSingle = SettingsManager.getQuickLauncherAutoStartSingle(this)
        limitResults = SettingsManager.getQuickLauncherLimitResults(this)
        widthPercent = SettingsManager.getQuickLauncherWidthPercent(this)
        pillMode = SettingsManager.getQuickLauncherPillMode(this)
        respectKeyboardLayout = SettingsManager.getQuickLauncherRespectKeyboardLayout(this)
        typoTolerantRanking = SettingsManager.getQuickLauncherTypoTolerantRanking(this)
        commandCustomizations = SettingsManager.getQuickLauncherCommandCustomizations(this)
        highlightFavorites = SettingsManager.getQuickLauncherHighlightFavorites(this)
        favoriteColor = SettingsManager.getQuickLauncherFavoriteColor(this)
        iconColors = SettingsManager.getQuickLauncherIconColors(this)
        showAliasFirst = SettingsManager.getQuickLauncherShowAliasFirst(this)
        staticTopHighlight = SettingsManager.getQuickLauncherStaticTopHighlight(this)
        staticTopHighlightColor = SettingsManager.getQuickLauncherStaticTopHighlightColor(this)
        animationDurationMs = SettingsManager.getQuickLauncherAnimationDurationMs(this)
        keyboardLayout = if (respectKeyboardLayout) loadActiveKeyboardLayout() else emptyMap()
        commands = quickLauncherCommandsFromCachedApps()
        loadingApps = commands.isEmpty()
        refreshFilteredCommands()
        registerQuickLauncherPreferencesListener()

        setContent {
            PastieraTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { requestAnimatedDismiss() },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    QuickLauncherSheet(
                        query = query,
                        commands = filteredCommands,
                        loadingApps = loadingApps,
                        limitResults = limitResults,
                        widthPercent = widthPercent,
                        pillMode = pillMode,
                        customizations = commandCustomizations,
                        customizationRevision = commandCustomizationRevision,
                        highlightFavorites = highlightFavorites,
                        favoriteColor = favoriteColor,
                        iconColors = iconColors,
                        showAliasFirst = showAliasFirst,
                        staticTopHighlight = staticTopHighlight,
                        staticTopHighlightColor = staticTopHighlightColor,
                        animationDurationMs = animationDurationMs,
                        dismissRequestId = dismissRequestId,
                        onCommandSelected = { launchCommand(it) },
                        onCustomizationChanged = { updateCommandCustomization(it) },
                        onCustomizationsReloadRequested = { reloadCommandCustomizations() },
                        onMoveFavorite = { commandId, direction -> moveFavorite(commandId, direction) },
                        onDismiss = { requestAnimatedDismiss() },
                        onDismissAnimationFinished = { finish() }
                    )
                }
            }
        }

        reloadCommandsFromRegistry()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_TOGGLE_REQUEST, false)) {
            requestAnimatedDismiss()
        }
    }

    override fun onDestroy() {
        quickLauncherPrefsListener?.let { listener ->
            SettingsManager.getPreferences(this).unregisterOnSharedPreferenceChangeListener(listener)
        }
        quickLauncherPrefsListener = null
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (handleQuickLauncherKeyDown(event.keyCode, event)) {
                    true
                } else {
                    super.dispatchKeyEvent(event)
                }
            }
            KeyEvent.ACTION_UP -> {
                if (handleQuickLauncherKeyUp(event.keyCode, event)) {
                    true
                } else {
                    super.dispatchKeyEvent(event)
                }
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return handleQuickLauncherKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return handleQuickLauncherKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)
    }

    private fun handleQuickLauncherKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isSymQuickLauncherToggle(keyCode, event)) {
            return handleDismissKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                enterHandledOnKeyDown = true
                launchTopMatch()
                return true
            }
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE -> return handleDismissKeyDown(keyCode, event)
            KeyEvent.KEYCODE_DEL -> {
                if (query.isNotEmpty()) {
                    query = query.dropLast(1)
                    launchedAutomatically = false
                    refreshFilteredCommands()
                }
                return true
            }
        }

        if (event?.isCtrlPressed != true) {
            val text = resolveTypedText(keyCode, event)
            if (!text.isNullOrEmpty()) {
                query += text
                launchedAutomatically = false
                refreshFilteredCommands()
                return true
            }
        }

        return false
    }

    private fun handleQuickLauncherKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (pendingDismissKeyCode == keyCode) {
            return handleDismissKeyUp(keyCode, event)
        }

        if (isSymQuickLauncherToggle(keyCode, event)) {
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            if (!enterHandledOnKeyDown && event?.isCanceled != true) {
                launchTopMatch()
            }
            enterHandledOnKeyDown = false
            return true
        }

        if (isDismissKey(keyCode)) {
            return handleDismissKeyUp(keyCode, event)
        }

        return false
    }

    override fun finish() {
        super.finish()
        disableActivityAnimations()
    }

    private fun refreshFilteredCommands() {
        filteredCommands = fuzzyFilterCommands(
            commands = commands,
            query = query,
            limitResults = limitResults,
            typoTolerantRanking = typoTolerantRanking,
            customizations = commandCustomizations
        )
        maybeAutoLaunch()
    }

    private fun maybeAutoLaunch() {
        if (autoStartSingle && query.isNotBlank() && filteredCommands.size == 1 && !launchedAutomatically) {
            launchedAutomatically = true
            launchCommand(filteredCommands.first())
        }
    }

    private fun launchTopMatch() {
        filteredCommands.firstOrNull()?.let { launchCommand(it) }
    }

    private fun launchCommand(command: CommandTarget) {
        val result = CommandExecutor(this).execute(command)
        if (result.isSuccess) {
            finish()
        } else {
            Log.w(TAG, "Command failed: ${command.id}")
        }
    }

    private fun handleDismissKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null || event.repeatCount == 0) {
            pendingDismissKeyCode = keyCode
        }
        return true
    }

    private fun handleDismissKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val shouldDismiss = pendingDismissKeyCode == keyCode && event?.isCanceled != true
        pendingDismissKeyCode = null
        if (shouldDismiss) {
            requestAnimatedDismiss()
        }
        return true
    }

    private fun requestAnimatedDismiss() {
        if (dismissInProgress) return
        dismissInProgress = true
        if (animationDurationMs <= 0) {
            finish()
        } else {
            dismissRequestId += 1
        }
    }

    private fun isDismissKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE
    }

    private fun isSymQuickLauncherToggle(keyCode: Int, event: KeyEvent?): Boolean {
        return SettingsManager.isQuickLauncherShortcut(this, keyCode) &&
            event?.isSymPressed == true
    }

    private fun updateCommandCustomization(customization: SettingsManager.QuickLauncherCommandCustomization) {
        SettingsManager.setQuickLauncherCommandCustomization(this, customization)
        reloadCommandCustomizations()
    }

    private fun reloadCommandCustomizations() {
        commandCustomizations = SettingsManager.getQuickLauncherCommandCustomizations(this)
        commandCustomizationRevision += 1
        launchedAutomatically = false
        refreshFilteredCommands()
    }

    private fun registerQuickLauncherPreferencesListener() {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PREF_COMMAND_SURFACE_SOURCES) {
                launchedAutomatically = false
                commands = quickLauncherCommandsFromCachedApps()
                loadingApps = commands.isEmpty()
                refreshFilteredCommands()
                reloadCommandsFromRegistry()
            }
        }
        SettingsManager.getPreferences(this).registerOnSharedPreferenceChangeListener(listener)
        quickLauncherPrefsListener = listener
    }

    private fun reloadCommandsFromRegistry() {
        lifecycleScope.launch {
            val loadedCommands = withContext(Dispatchers.IO) {
                CommandRegistry(this@QuickLauncherActivity).getCommands(CommandSurface.QuickLauncher)
            }
            commands = loadedCommands
            loadingApps = false
            refreshFilteredCommands()
        }
    }

    private fun moveFavorite(commandId: String, direction: Int) {
        val favorites = commandCustomizations.values
            .filter { it.favorite }
            .sortedWith(compareBy<SettingsManager.QuickLauncherCommandCustomization> { it.favoriteOrder }.thenBy { it.commandId })
        val index = favorites.indexOfFirst { it.commandId == commandId }
        val targetIndex = (index + direction).takeIf { index >= 0 && it in favorites.indices } ?: return
        val current = favorites[index]
        val target = favorites[targetIndex]
        val currentOrder = current.favoriteOrder.takeIf { it != Int.MAX_VALUE } ?: index
        val targetOrder = target.favoriteOrder.takeIf { it != Int.MAX_VALUE } ?: targetIndex
        updateCommandCustomization(current.copy(favoriteOrder = targetOrder))
        updateCommandCustomization(target.copy(favoriteOrder = currentOrder))
    }

    private fun quickLauncherCommandsFromCachedApps(): List<CommandTarget> {
        if (!SettingsManager.isCommandSourceEnabled(this, CommandSourceId.Apps.storageValue, CommandSurface.QuickLauncher)) {
            return emptyList()
        }
        AppListHelper.syncPackageChanges(this)
        val cachedApps = AppListHelper.getCachedInstalledApps() ?: return emptyList()
        return cachedApps.map { app ->
            CommandTarget(
                id = "app:${app.packageName}",
                source = CommandSourceId.Apps,
                kind = CommandKind.App,
                label = app.appName,
                subtitle = app.packageName,
                icon = CommandIcon.DrawableIcon(app.icon),
                launch = CommandLaunchSpec.AppPackage(app.packageName),
                searchTokens = listOf(app.appName, app.packageName)
            )
        }
    }

    private fun loadActiveKeyboardLayout(): Map<Int, LayoutMapping> {
        val layoutName = try {
            val imm = getSystemService(InputMethodManager::class.java)
            AdditionalSubtypeUtils.resolveActiveLayout(
                assets,
                this,
                imm.currentInputMethodSubtype
            )
        } catch (error: Exception) {
            SettingsManager.getKeyboardLayout(this)
        }
        return JsonLayoutLoader.loadLayout(assets, layoutName, this).orEmpty()
    }

    private fun resolveTypedText(keyCode: Int, event: KeyEvent?): String? {
        if (respectKeyboardLayout) {
            keyboardLayout[keyCode]
                ?.let { mapping ->
                    val mappedText = LayoutMappingRepository.resolveText(mapping, event?.isShiftPressed == true)
                    if (!mappedText.isNullOrEmpty()) {
                        return mappedText
                    }
                }
        }

        val unicode = event?.unicodeChar ?: 0
        if (unicode <= 0) {
            return null
        }
        val char = unicode.toChar()
        return if (char.isISOControl()) null else char.toString()
    }

    private fun disableActivityAnimations() {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_TOGGLE_REQUEST = "it.palsoftware.pastiera.inputmethod.extra.TOGGLE_QUICK_LAUNCHER"
        private const val TAG = "QuickLauncher"
        private const val PREF_COMMAND_SURFACE_SOURCES = "command_surface_sources"

        fun createToggleIntent(context: Context): Intent {
            return Intent(context, QuickLauncherActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(EXTRA_TOGGLE_REQUEST, true)
            }
        }

        fun createOpenIntent(context: Context): Intent {
            return Intent(context, QuickLauncherActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        }
    }
}

@Composable
private fun QuickLauncherSheet(
    query: String,
    commands: List<CommandTarget>,
    loadingApps: Boolean,
    limitResults: Boolean,
    widthPercent: Int,
    pillMode: Boolean,
    customizations: Map<String, SettingsManager.QuickLauncherCommandCustomization>,
    customizationRevision: Int,
    highlightFavorites: Boolean,
    favoriteColor: Int,
    iconColors: Boolean,
    showAliasFirst: Boolean,
    staticTopHighlight: Boolean,
    staticTopHighlightColor: Int,
    animationDurationMs: Int,
    dismissRequestId: Int,
    onCommandSelected: (CommandTarget) -> Unit,
    onCustomizationChanged: (SettingsManager.QuickLauncherCommandCustomization) -> Unit,
    onCustomizationsReloadRequested: () -> Unit,
    onMoveFavorite: (String, Int) -> Unit,
    onDismiss: () -> Unit,
    onDismissAnimationFinished: () -> Unit
) {
    val context = LocalContext.current
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.78f
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        visible.targetState = true
    }
    LaunchedEffect(dismissRequestId) {
        if (dismissRequestId > 0) {
            visible.targetState = false
            delay(animationDurationMs.toLong().coerceAtLeast(0L))
            onDismissAnimationFinished()
        }
    }
    val isCollapsedPill = pillMode && query.isBlank()
    val widthFraction = if (isCollapsedPill) 0.72f else widthPercent.coerceIn(50, 100) / 100f
    val entries = remember(commands, query, customizations, customizationRevision) {
        if (query.isNotBlank()) {
            commands.map { QuickLauncherEntry.Command(it) }
        } else {
            commands
                .groupBy { it.source }
                .flatMap { (source, sourceCommands) ->
                    if (commands.size <= 4) {
                        sourceCommands.map { QuickLauncherEntry.Command(it) }
                    } else {
                        listOf(QuickLauncherEntry.Header(source.localizedDisplayLabel(context))) +
                            sourceCommands.map { QuickLauncherEntry.Command(it) }
                    }
                }
        }
    }
    val listState = rememberLazyListState()
    val entryScrollKey = remember(entries) {
        entries.joinToString("|") { entry ->
            when (entry) {
                is QuickLauncherEntry.Header -> "h:${entry.label}"
                is QuickLauncherEntry.Command -> "c:${entry.command.id}"
            }
        }
    }
    LaunchedEffect(query, entryScrollKey) {
        listState.scrollToItem(0)
    }

    AnimatedVisibility(
        visibleState = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = animationDurationMs)
        ) + fadeIn(animationSpec = tween(durationMillis = (animationDurationMs / 2).coerceAtLeast(0))),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = animationDurationMs)
        ) + fadeOut(animationSpec = tween(durationMillis = (animationDurationMs / 2).coerceAtLeast(0)))
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .heightIn(max = maxSheetHeight),
            shape = if (isCollapsedPill) RoundedCornerShape(28.dp) else MaterialTheme.shapes.large,
            color = if (isCollapsedPill) Color.Transparent else MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isCollapsedPill) 0.dp else 14.dp,
                        vertical = if (isCollapsedPill) 0.dp else 8.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isCollapsedPill) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.quick_launcher_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 14.dp,
                                end = if (isCollapsedPill) 14.dp else 4.dp,
                                top = if (isCollapsedPill) 9.dp else 6.dp,
                                bottom = if (isCollapsedPill) 9.dp else 6.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (isCollapsedPill) {
                            Arrangement.Center
                        } else {
                            Arrangement.Start
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        if (isCollapsedPill) {
                            Text(
                                text = stringResource(R.string.quick_launcher_search_placeholder),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = query.ifBlank { stringResource(R.string.quick_launcher_search_placeholder) },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (query.isBlank()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.quick_launcher_close)
                                )
                            }
                        }
                    }
                }

                if (!isCollapsedPill) {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (!isCollapsedPill && (commands.isEmpty() || loadingApps)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (query.isBlank()) {
                                if (loadingApps) {
                                    stringResource(R.string.quick_launcher_loading_apps)
                                } else if (limitResults) {
                                    stringResource(R.string.quick_launcher_type_to_search)
                                } else {
                                    stringResource(R.string.quick_launcher_no_apps)
                                }
                            } else {
                                stringResource(R.string.quick_launcher_no_results, query)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (!isCollapsedPill) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(entries, key = { _, entry ->
                            when (entry) {
                                is QuickLauncherEntry.Header -> "header:${entry.label}"
                                is QuickLauncherEntry.Command -> "${entry.command.id}:$customizationRevision"
                            }
                        }) { index, entry ->
                            when (entry) {
                                is QuickLauncherEntry.Header -> QuickLauncherSectionHeader(entry.label)
                                is QuickLauncherEntry.Command -> QuickLauncherCommandRow(
                                    command = entry.command,
                                    customization = customizations[entry.command.id],
                                    isTopMatch = entries.take(index).none { it is QuickLauncherEntry.Command },
                                    highlightFavorites = highlightFavorites,
                                    favoriteColor = favoriteColor,
                                    iconColors = iconColors,
                                    showAliasFirst = showAliasFirst,
                                    staticTopHighlight = staticTopHighlight,
                                    staticTopHighlightColor = staticTopHighlightColor,
                                    nextFavoriteOrder = nextFavoriteOrder(customizations),
                                    onClick = { onCommandSelected(entry.command) },
                                    onCustomizationChanged = onCustomizationChanged,
                                    onCustomizationsReloadRequested = onCustomizationsReloadRequested,
                                    onMoveFavorite = onMoveFavorite
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private sealed class QuickLauncherEntry {
    data class Header(val label: String) : QuickLauncherEntry()
    data class Command(val command: CommandTarget) : QuickLauncherEntry()
}

@Composable
private fun QuickLauncherSectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickLauncherCommandRow(
    command: CommandTarget,
    customization: SettingsManager.QuickLauncherCommandCustomization?,
    isTopMatch: Boolean,
    highlightFavorites: Boolean,
    favoriteColor: Int,
    iconColors: Boolean,
    showAliasFirst: Boolean,
    staticTopHighlight: Boolean,
    staticTopHighlightColor: Int,
    nextFavoriteOrder: Int,
    onClick: () -> Unit,
    onCustomizationChanged: (SettingsManager.QuickLauncherCommandCustomization) -> Unit,
    onCustomizationsReloadRequested: () -> Unit,
    onMoveFavorite: (String, Int) -> Unit
) {
    val context = LocalContext.current
    val customColor = customization?.color
    val isFavorite = customization?.favorite == true
    var menuExpanded by remember { mutableStateOf(false) }
    val effectiveCustomization = customization ?: SettingsManager.QuickLauncherCommandCustomization(command.id)
    val rowTint = when {
        customColor != null -> Color(customColor)
        isFavorite -> commandIconDerivedColor(command)
        iconColors -> commandIconDerivedColor(command)
        else -> null
    }
    Box {
        Surface(
            color = if (isTopMatch) {
                if (staticTopHighlight) {
                    Color(staticTopHighlightColor)
                } else {
                    customColor?.let { Color(it) } ?: commandIconDerivedColor(command, alpha = 0.58f)
                }
            } else if (rowTint != null) {
                rowTint
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = if (isTopMatch) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            shape = MaterialTheme.shapes.medium,
            border = if (highlightFavorites && isFavorite) {
                val borderColor = if (favoriteColor == SettingsManager.QUICK_LAUNCHER_DYNAMIC_FAVORITE_COLOR) {
                    commandIconDerivedColor(command, alpha = 0.95f)
                } else {
                    Color(favoriteColor).copy(alpha = 0.95f)
                }
                BorderStroke(2.dp, borderColor)
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true }
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CommandRowIcon(command)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = commandDisplayLabel(command, customization, showAliasFirst),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isTopMatch) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = command.subtitle ?: command.source.localizedDisplayLabel(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.basicMarquee()
                    )
                }
                if (isTopMatch) {
                    Text(
                        text = stringResource(R.string.quick_launcher_enter_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        QuickLauncherEntryContextMenu(
            modifier = Modifier.align(Alignment.TopEnd),
            expanded = menuExpanded,
            command = command,
            customization = effectiveCustomization,
            nextFavoriteOrder = nextFavoriteOrder,
            onDismiss = {
                menuExpanded = false
                onCustomizationsReloadRequested()
            },
            onCustomizationChanged = {
                menuExpanded = false
                onCustomizationChanged(it)
            },
            onMoveFavorite = {
                menuExpanded = false
                onMoveFavorite(command.id, it)
            }
        )
    }
}

@Composable
private fun QuickLauncherEntryContextMenu(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    command: CommandTarget,
    customization: SettingsManager.QuickLauncherCommandCustomization,
    nextFavoriteOrder: Int,
    onDismiss: () -> Unit,
    onCustomizationChanged: (SettingsManager.QuickLauncherCommandCustomization) -> Unit,
    onMoveFavorite: (Int) -> Unit
) {
    var aliasText by remember(customization.commandId, customization.customSearch, expanded) {
        mutableStateOf(customization.customSearch)
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 260.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = command.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee()
            )
            OutlinedTextField(
                value = aliasText,
                onValueChange = { aliasText = it },
                singleLine = true,
                label = { Text(stringResource(R.string.quick_launcher_search_alias)) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        aliasText = ""
                        onCustomizationChanged(customization.copy(customSearch = ""))
                    }
                ) {
                    Text(stringResource(R.string.clear))
                }
                TextButton(
                    onClick = {
                        onCustomizationChanged(customization.copy(customSearch = aliasText))
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
        DropdownMenuItem(
            text = {
                Text(
                    if (customization.favorite) {
                        stringResource(R.string.quick_launcher_unfavorite)
                    } else {
                        stringResource(R.string.quick_launcher_favorite)
                    }
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (customization.favorite) Icons.Filled.StarBorder else Icons.Filled.Star,
                    contentDescription = null
                )
            },
            onClick = {
                onCustomizationChanged(
                    customization.copy(
                        favorite = !customization.favorite,
                        favoriteOrder = if (customization.favorite) {
                            Int.MAX_VALUE
                        } else {
                            nextFavoriteOrder
                        }
                    )
                )
            }
        )
        if (customization.favorite) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.quick_launcher_move_up)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = null
                    )
                },
                onClick = { onMoveFavorite(-1) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.quick_launcher_move_down)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null
                    )
                },
                onClick = { onMoveFavorite(1) }
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.quick_launcher_hide)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.VisibilityOff,
                    contentDescription = null
                )
            },
            onClick = {
                onCustomizationChanged(customization.copy(hidden = true))
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.quick_launcher_dynamic_entry_color)) },
            onClick = {
                onCustomizationChanged(customization.copy(color = null))
            }
        )
        Text(
            text = stringResource(R.string.quick_launcher_entry_color),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        quickLauncherContextSwatches().forEach { swatch ->
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(22.dp),
                            shape = MaterialTheme.shapes.extraSmall,
                            color = Color(swatch)
                        ) {}
                        Text("#${swatch.toUInt().toString(16).uppercase().takeLast(6)}")
                    }
                },
                onClick = {
                    onCustomizationChanged(customization.copy(color = swatch))
                }
            )
        }
    }
}

private fun nextFavoriteOrder(
    customizations: Map<String, SettingsManager.QuickLauncherCommandCustomization>
): Int {
    return customizations.values
        .filter { it.favorite }
        .map { it.favoriteOrder }
        .filter { it != Int.MAX_VALUE }
        .maxOrNull()
        ?.plus(1)
        ?: 0
}

private fun quickLauncherContextSwatches(): List<Int> {
    return listOf(
        0x7A4285F4,
        0x7A34A853,
        0x7AFABB05,
        0x7AEA4335,
        0x7AA142F4,
        0x7A00ACC1,
        0x7AFF7043,
        0x7A888888
    )
}

private fun fuzzyFilterCommands(
    commands: List<CommandTarget>,
    query: String,
    limitResults: Boolean,
    typoTolerantRanking: Boolean,
    customizations: Map<String, SettingsManager.QuickLauncherCommandCustomization>
): List<CommandTarget> {
    val visibleCommands = commands.filter { command ->
        customizations[command.id]?.hidden != true
    }
    val normalizedQuery = normalizeForQuickLauncher(query)
    if (normalizedQuery.isBlank()) {
        val favorites = visibleCommands
            .filter { customizations[it.id]?.favorite == true }
            .sortedWith(compareBy<CommandTarget> { customizations[it.id]?.favoriteOrder ?: Int.MAX_VALUE }.thenBy { it.label.lowercase() })
        return if (limitResults) {
            favorites
        } else {
            favorites + visibleCommands
                .filter { customizations[it.id]?.favorite != true }
                .sortedBy { it.label.lowercase() }
        }
    }

    val matches = visibleCommands
        .mapNotNull { command ->
            val score = fuzzyScore(
                command = command,
                normalizedQuery = normalizedQuery,
                typoTolerantRanking = typoTolerantRanking,
                customization = customizations[command.id]
            ) ?: return@mapNotNull null
            command to score
        }
        .sortedWith(compareBy<Pair<CommandTarget, Int>> { it.second }.thenBy { it.first.label.lowercase() })
        .map { it.first }
    return if (limitResults) matches.take(3) else matches
}

private fun fuzzyScore(
    command: CommandTarget,
    normalizedQuery: String,
    typoTolerantRanking: Boolean,
    customization: SettingsManager.QuickLauncherCommandCustomization?
): Int? {
    val customSearch = normalizeForQuickLauncher(customization?.customSearch.orEmpty())
    val customScore = if (customSearch.isNotBlank()) {
        customSearchScore(customSearch, normalizedQuery)
    } else {
        null
    }
    val name = normalizeForQuickLauncher(command.label)
    val subtitle = normalizeForQuickLauncher(command.subtitle.orEmpty())
    val tokens = normalizeForQuickLauncher(command.searchTokens.joinToString(" "))
    val baseScore = when {
        name == normalizedQuery -> 0
        name.startsWith(normalizedQuery) -> 10 + name.length
        name.split(' ').any { it.startsWith(normalizedQuery) } -> 40 + name.length
        subtitle.contains(normalizedQuery) -> 200 + subtitle.indexOf(normalizedQuery)
        tokens.contains(normalizedQuery) -> 220 + tokens.indexOf(normalizedQuery)
        else -> null
    }

    val fuzzyNameScore = subsequenceScore(name, normalizedQuery)
    val typoScore = if (typoTolerantRanking) typoTolerantScore(name, normalizedQuery) else null
    val fuzzySubtitleScore = subsequenceScore(subtitle, normalizedQuery)?.plus(240)
    val normalScore = listOfNotNull(baseScore, fuzzyNameScore, typoScore, fuzzySubtitleScore).minOrNull()
    return when {
        customScore != null -> customScore
        normalScore != null && customization?.favorite == true -> (normalScore - 25).coerceAtLeast(0)
        else -> normalScore
    }
}

private fun customSearchScore(customSearch: String, query: String): Int? {
    return when {
        customSearch == query -> 0
        customSearch.startsWith(query) -> 2 + customSearch.length
        customSearch.split(' ').any { it.startsWith(query) } -> 12 + customSearch.length
        customSearch.contains(query) -> 30 + customSearch.indexOf(query)
        else -> subsequenceScore(customSearch, query)?.plus(50)
    }
}

@Composable
private fun CommandRowIcon(command: CommandTarget) {
    val drawable = (command.icon as? CommandIcon.DrawableIcon)?.drawable
    if (drawable != null) {
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            },
            update = { imageView ->
                imageView.setImageDrawable(drawable.constantState?.newDrawable() ?: drawable)
            },
            modifier = Modifier.size(40.dp)
        )
    } else {
        Icon(
            imageVector = commandMaterialIcon(command),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

private fun commandDisplayLabel(
    command: CommandTarget,
    customization: SettingsManager.QuickLauncherCommandCustomization?,
    showAliasFirst: Boolean
): String {
    val alias = customization?.customSearch?.trim().orEmpty()
    return if (showAliasFirst && alias.isNotBlank()) {
        "$alias | ${command.label}"
    } else {
        command.label
    }
}

private fun commandIconDerivedColor(command: CommandTarget, alpha: Float = 0.28f): Color {
    val drawable = (command.icon as? CommandIcon.DrawableIcon)?.drawable
    val dominant = drawable?.dominantIconColor()
    if (dominant != null) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(dominant, hsv)
        val saturation = hsv[1].coerceAtLeast(0.34f).coerceAtMost(0.72f)
        val value = hsv[2].coerceAtLeast(0.58f).coerceAtMost(0.92f)
        return Color.hsv(hsv[0], saturation, value, alpha = alpha)
    }

    val fallbackHue = when (command.source) {
        CommandSourceId.Apps -> 214f
        CommandSourceId.Pastiera -> 145f
        CommandSourceId.AppActions -> 282f
        CommandSourceId.DeviceControl -> 28f
        CommandSourceId.NavActions -> 190f
    }
    return Color.hsv(fallbackHue, 0.28f, 0.92f, alpha = alpha)
}

private fun Drawable.dominantIconColor(): Int? {
    val bitmap = toSmallBitmap() ?: return null
    var redTotal = 0L
    var greenTotal = 0L
    var blueTotal = 0L
    var weightTotal = 0L
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    pixels.forEach { pixel ->
        val alpha = AndroidColor.alpha(pixel)
        if (alpha < 48) return@forEach
        val red = AndroidColor.red(pixel)
        val green = AndroidColor.green(pixel)
        val blue = AndroidColor.blue(pixel)
        val max = maxOf(red, green, blue)
        val min = minOf(red, green, blue)
        val saturationWeight = (max - min).coerceAtLeast(18)
        val weight = alpha * saturationWeight
        redTotal += red.toLong() * weight
        greenTotal += green.toLong() * weight
        blueTotal += blue.toLong() * weight
        weightTotal += weight
    }
    if (weightTotal <= 0L) return null
    return AndroidColor.rgb(
        (redTotal / weightTotal).toInt().coerceIn(0, 255),
        (greenTotal / weightTotal).toInt().coerceIn(0, 255),
        (blueTotal / weightTotal).toInt().coerceIn(0, 255)
    )
}

private fun Drawable.toSmallBitmap(): Bitmap? {
    if (this is BitmapDrawable && bitmap != null) {
        return Bitmap.createScaledBitmap(bitmap, 32, 32, true)
    }
    val width = intrinsicWidth.takeIf { it > 0 } ?: 32
    val height = intrinsicHeight.takeIf { it > 0 } ?: 32
    return try {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val oldBounds = copyBounds()
        setBounds(0, 0, 32, 32)
        draw(canvas)
        setBounds(oldBounds)
        bitmap
    } catch (error: Exception) {
        Log.w("QuickLauncher", "Failed to sample icon color ${width}x$height", error)
        null
    }
}

private fun subsequenceScore(candidate: String, query: String): Int? {
    var lastIndex = -1
    var score = 80
    for (char in query) {
        val nextIndex = candidate.indexOf(char, startIndex = lastIndex + 1)
        if (nextIndex == -1) return null
        score += (nextIndex - lastIndex - 1)
        lastIndex = nextIndex
    }
    return score + candidate.length
}

private fun typoTolerantScore(name: String, query: String): Int? {
    val maxDistance = when (query.length) {
        0, 1, 2 -> return null
        in 3..5 -> 1
        else -> 2
    }
    val candidates = name.split(' ')
        .filter { it.isNotBlank() } +
        listOf(name.filterNot { it.isWhitespace() })

    return candidates
        .flatMap { candidate -> typoCandidateWindows(candidate, query, maxDistance) }
        .mapNotNull { window ->
            val distance = boundedDamerauLevenshtein(window, query, maxDistance) ?: return@mapNotNull null
            180 + distance * 25 + kotlin.math.abs(window.length - query.length)
        }
        .minOrNull()
}

private fun typoCandidateWindows(candidate: String, query: String, maxDistance: Int): List<String> {
    if (candidate.isBlank()) return emptyList()
    val windows = linkedSetOf<String>()
    val minLength = (query.length - maxDistance).coerceAtLeast(1)
    val maxLength = (query.length + maxDistance).coerceAtMost(candidate.length)
    for (length in minLength..maxLength) {
        windows.add(candidate.take(length))
    }
    if (candidate.length <= query.length + maxDistance) {
        windows.add(candidate)
    }
    return windows.toList()
}

private fun boundedDamerauLevenshtein(source: String, target: String, maxDistance: Int): Int? {
    if (kotlin.math.abs(source.length - target.length) > maxDistance) {
        return null
    }

    val matrix = Array(source.length + 1) { IntArray(target.length + 1) }
    for (i in 0..source.length) matrix[i][0] = i
    for (j in 0..target.length) matrix[0][j] = j

    for (i in 1..source.length) {
        var rowMin = Int.MAX_VALUE
        for (j in 1..target.length) {
            val substitutionCost = if (source[i - 1] == target[j - 1]) 0 else 1
            var value = minOf(
                matrix[i - 1][j] + 1,
                matrix[i][j - 1] + 1,
                matrix[i - 1][j - 1] + substitutionCost
            )
            if (
                i > 1 &&
                j > 1 &&
                source[i - 1] == target[j - 2] &&
                source[i - 2] == target[j - 1]
            ) {
                value = minOf(value, matrix[i - 2][j - 2] + 1)
            }
            matrix[i][j] = value
            rowMin = minOf(rowMin, value)
        }
        if (rowMin > maxDistance) return null
    }

    return matrix[source.length][target.length].takeIf { it <= maxDistance }
}

private fun normalizeForQuickLauncher(value: String): String {
    return value.lowercase().trim()
}
