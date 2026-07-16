package it.palsoftware.pastiera

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodService
import it.palsoftware.pastiera.inputmethod.SubtypeCycler
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils.localeString
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun KeyboardThemeScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    initialTarget: SettingsManager.KeyboardThemeTarget? = null
) {
    val context = LocalContext.current
    val builtInPresets = remember { keyboardThemePresets() }
    var savedThemes by remember {
        mutableStateOf(SettingsManager.getSavedKeyboardThemes(context).map { it.toKeyboardThemeOption() })
    }
    val hardwareThemeOptions = builtInPresets.map { KeyboardThemeOption("builtin:${it.name}", it, it) } + savedThemes
    val softwareThemeOptions = builtInPresets.map {
        val softwarePreset = it.withSoftwareKeyboardDefaults()
        KeyboardThemeOption("builtin:${it.name}", softwarePreset, softwarePreset)
    } + savedThemes
    val initialPreviewPage = remember {
        if (initialTarget == SettingsManager.KeyboardThemeTarget.SOFTWARE) {
            1
        } else if (
            initialTarget == null &&
            SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) ==
                SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        ) {
            1
        } else {
            0
        }
    }
    val previewPagerState = rememberPagerState(initialPage = initialPreviewPage, pageCount = { 2 })
    var customizationTab by remember { mutableStateOf(0) }
    val initialHardwarePreset = remember {
        SettingsManager.getKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE).toKeyboardThemePreset("Custom")
    }
    val initialSoftwarePreset = remember {
        SettingsManager.getKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE).toKeyboardThemePreset("Custom")
    }
    var hardwarePreset by remember { mutableStateOf(initialHardwarePreset) }
    var softwarePreset by remember { mutableStateOf(initialSoftwarePreset) }
    var hardwareTheme by remember { mutableStateOf(hardwarePreset) }
    var softwareTheme by remember { mutableStateOf(softwarePreset) }
    var hardwareSelectionKey by remember { mutableStateOf(matchKeyboardThemeOption(hardwareThemeOptions, initialHardwarePreset) ?: "custom:hardware") }
    var softwareSelectionKey by remember { mutableStateOf(matchKeyboardThemeOption(softwareThemeOptions, initialSoftwarePreset) ?: "custom:software") }
    var softwarePreviewViewportScale by remember {
        mutableStateOf(SettingsManager.getKeyboardThemePreviewViewportScale(context))
    }
    var hardwareAssignmentMode by remember {
        mutableStateOf(SettingsManager.getKeyboardThemeAssignmentMode(context, SettingsManager.KeyboardThemeTarget.HARDWARE))
    }
    var softwareAssignmentMode by remember {
        mutableStateOf(SettingsManager.getKeyboardThemeAssignmentMode(context, SettingsManager.KeyboardThemeTarget.SOFTWARE))
    }
    var hardwareLightTheme by remember {
        mutableStateOf(
            SettingsManager.getKeyboardThemeSystemSlot(
                context,
                SettingsManager.KeyboardThemeTarget.HARDWARE,
                dark = false
            ).toKeyboardThemePreset("Pastiera Light")
        )
    }
    var hardwareDarkTheme by remember {
        mutableStateOf(
            SettingsManager.getKeyboardThemeSystemSlot(
                context,
                SettingsManager.KeyboardThemeTarget.HARDWARE,
                dark = true
            ).toKeyboardThemePreset("Pastiera Dark")
        )
    }
    var softwareLightTheme by remember {
        mutableStateOf(
            SettingsManager.getKeyboardThemeSystemSlot(
                context,
                SettingsManager.KeyboardThemeTarget.SOFTWARE,
                dark = false
            ).toKeyboardThemePreset("Pastiera Light")
        )
    }
    var softwareDarkTheme by remember {
        mutableStateOf(
            SettingsManager.getKeyboardThemeSystemSlot(
                context,
                SettingsManager.KeyboardThemeTarget.SOFTWARE,
                dark = true
            ).toKeyboardThemePreset("Pastiera Dark")
        )
    }
    var exportTheme by remember { mutableStateOf<KeyboardThemePreset?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showSaveAsDialog by remember { mutableStateOf(false) }
    var themePickerRequest by remember { mutableStateOf<KeyboardThemePickerRequest?>(null) }
    var assignmentScreenTarget by remember { mutableStateOf<SettingsManager.KeyboardThemeTarget?>(null) }
    var overrideEditorRequest by remember { mutableStateOf<KeyboardThemeOverrideEditorRequest?>(null) }
    var hardwareOverrides by remember {
        mutableStateOf(SettingsManager.getKeyboardThemeLayoutOverrides(context, SettingsManager.KeyboardThemeTarget.HARDWARE))
    }
    var softwareOverrides by remember {
        mutableStateOf(SettingsManager.getKeyboardThemeLayoutOverrides(context, SettingsManager.KeyboardThemeTarget.SOFTWARE))
    }

    val activePreviewPage = previewPagerState.currentPage
    val activeTarget = if (activePreviewPage == 0) {
        SettingsManager.KeyboardThemeTarget.HARDWARE
    } else {
        SettingsManager.KeyboardThemeTarget.SOFTWARE
    }
    val activeTheme = if (activePreviewPage == 0) hardwareTheme else softwareTheme
    val activePreset = if (activePreviewPage == 0) hardwarePreset else softwarePreset
    val activeSelectionKey = if (activePreviewPage == 0) hardwareSelectionKey else softwareSelectionKey
    val activeThemeOptions = if (activePreviewPage == 0) hardwareThemeOptions else softwareThemeOptions
    val systemIsDark = isSystemInDarkTheme()
    val activeAssignmentMode = if (activePreviewPage == 0) hardwareAssignmentMode else softwareAssignmentMode
    val activeLightTheme = if (activePreviewPage == 0) hardwareLightTheme else softwareLightTheme
    val activeDarkTheme = if (activePreviewPage == 0) hardwareDarkTheme else softwareDarkTheme
    val activePreviewTheme = if (activeAssignmentMode == SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM) {
        if (systemIsDark) activeDarkTheme else activeLightTheme
    } else {
        activeTheme
    }

    fun updateActiveTheme(theme: KeyboardThemePreset) {
        if (activePreviewPage == 0) {
            hardwareTheme = theme
            SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, theme.toSettingsTheme())
        } else {
            softwareTheme = theme
            SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, theme.toSettingsTheme())
        }
        if (activeSelectionKey.startsWith("saved:")) {
            val savedName = activeSelectionKey.removePrefix("saved:")
            SettingsManager.saveKeyboardTheme(context, savedName, theme.copy(name = savedName).toSettingsTheme())
            savedThemes = SettingsManager.getSavedKeyboardThemes(context).map { it.toKeyboardThemeOption() }
        }
    }

    fun applyPreset(option: KeyboardThemeOption) {
        val preset = option.preset
        if (activeSelectionKey == option.key) {
            return
        }
        if (activePreviewPage == 0) {
            hardwareSelectionKey = option.key
            hardwarePreset = option.resetPreset
            hardwareTheme = preset
            SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, preset.toSettingsTheme())
        } else {
            softwareSelectionKey = option.key
            softwarePreset = option.resetPreset
            softwareTheme = preset
            SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, preset.toSettingsTheme())
        }
    }

    fun applyImportedTheme(theme: KeyboardThemePreset) {
        if (activePreviewPage == 0) {
            hardwareSelectionKey = "custom:imported:hardware"
            hardwarePreset = theme
            hardwareTheme = theme
            SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, theme.toSettingsTheme())
        } else {
            softwareSelectionKey = "custom:imported:software"
            softwarePreset = theme
            softwareTheme = theme
            SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, theme.toSettingsTheme())
        }
    }

    fun saveActiveThemeAs(name: String) {
        val normalizedName = name.trim().ifEmpty { "Custom" }
        val savedPreset = activeTheme.copy(name = normalizedName)
        SettingsManager.saveKeyboardTheme(context, normalizedName, savedPreset.toSettingsTheme())
        savedThemes = SettingsManager.getSavedKeyboardThemes(context).map { it.toKeyboardThemeOption() }
        if (activePreviewPage == 0) {
            hardwareSelectionKey = "saved:$normalizedName"
            hardwarePreset = savedPreset
            hardwareTheme = savedPreset
        } else {
            softwareSelectionKey = "saved:$normalizedName"
            softwarePreset = savedPreset
            softwareTheme = savedPreset
        }
    }

    BackHandler {
        if (assignmentScreenTarget != null) {
            assignmentScreenTarget = null
        } else {
            onBack()
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
                    IconButton(
                        onClick = {
                            if (assignmentScreenTarget != null) {
                                assignmentScreenTarget = null
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Text(
                        text = assignmentScreenTarget?.let { target ->
                            if (target == SettingsManager.KeyboardThemeTarget.SOFTWARE) {
                                stringResource(R.string.keyboard_theme_assignment_software_title)
                            } else {
                                stringResource(R.string.keyboard_theme_assignment_hardware_title)
                            }
                        } ?: stringResource(R.string.keyboard_theme_title),
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
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            assignmentScreenTarget?.let { target ->
                KeyboardThemeAssignmentSection(
                    target = target,
                    mode = if (target == SettingsManager.KeyboardThemeTarget.HARDWARE) {
                        hardwareAssignmentMode
                    } else {
                        softwareAssignmentMode
                    },
                    lightThemeName = themeDisplayName(
                        if (target == SettingsManager.KeyboardThemeTarget.HARDWARE) hardwareThemeOptions else softwareThemeOptions,
                        if (target == SettingsManager.KeyboardThemeTarget.HARDWARE) hardwareLightTheme else softwareLightTheme
                    ),
                    darkThemeName = themeDisplayName(
                        if (target == SettingsManager.KeyboardThemeTarget.HARDWARE) hardwareThemeOptions else softwareThemeOptions,
                        if (target == SettingsManager.KeyboardThemeTarget.HARDWARE) hardwareDarkTheme else softwareDarkTheme
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onModeChanged = { mode ->
                        if (target == SettingsManager.KeyboardThemeTarget.HARDWARE) {
                            hardwareAssignmentMode = mode
                            SettingsManager.setKeyboardThemeAssignmentMode(context, target, mode)
                        } else {
                            softwareAssignmentMode = mode
                            SettingsManager.setKeyboardThemeAssignmentMode(context, target, mode)
                        }
                    },
                    onPickLightTheme = {
                        themePickerRequest = KeyboardThemePickerRequest(target, dark = false)
                    },
                    onPickDarkTheme = {
                        themePickerRequest = KeyboardThemePickerRequest(target, dark = true)
                    },
                    overrides = if (target == SettingsManager.KeyboardThemeTarget.HARDWARE) {
                        hardwareOverrides
                    } else {
                        softwareOverrides
                    },
                    themeOptions = if (target == SettingsManager.KeyboardThemeTarget.HARDWARE) {
                        hardwareThemeOptions
                    } else {
                        softwareThemeOptions
                    },
                    onAddOverride = {
                        overrideEditorRequest = KeyboardThemeOverrideEditorRequest(target, null)
                    },
                    onEditOverride = { override ->
                        overrideEditorRequest = KeyboardThemeOverrideEditorRequest(target, override)
                    },
                    onRemoveOverride = { override ->
                        SettingsManager.removeKeyboardThemeLayoutOverride(context, target, override.locale, override.layout)
                        if (target == SettingsManager.KeyboardThemeTarget.HARDWARE) {
                            hardwareOverrides = SettingsManager.getKeyboardThemeLayoutOverrides(context, target)
                        } else {
                            softwareOverrides = SettingsManager.getKeyboardThemeLayoutOverrides(context, target)
                        }
                    }
                )
                return@Column
            }
            Text(
                text = stringResource(R.string.keyboard_theme_preset_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeThemeOptions.forEach { option ->
                    KeyboardThemePresetCard(
                        preset = option.preset,
                        selected = activeSelectionKey == option.key,
                        onClick = { applyPreset(option) }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { showSaveAsDialog = true }) {
                    Text("Save copy as")
                }
                Button(onClick = { exportTheme = activeTheme }) {
                    Text("Export")
                }
                Button(onClick = { showImportDialog = true }) {
                    Text("Import")
                }
            }

            KeyboardThemeAssignmentSummaryRow(
                target = activeTarget,
                mode = activeAssignmentMode,
                lightThemeName = themeDisplayName(activeThemeOptions, activeLightTheme),
                darkThemeName = themeDisplayName(activeThemeOptions, activeDarkTheme),
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = { assignmentScreenTarget = activeTarget }
            )

            Text(
                text = stringResource(R.string.keyboard_theme_preview_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp)
            )
            if (previewPagerState.currentPage == 1) {
                KeyboardThemeSliderRow(
                    label = "Preview max viewport",
                    value = softwarePreviewViewportScale,
                    presetValue = SettingsManager.KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE_MIN,
                    valueRange = SettingsManager.KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE_MIN..
                        SettingsManager.KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE_MAX,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onValueChanged = { value ->
                        softwarePreviewViewportScale = value
                        SettingsManager.setKeyboardThemePreviewViewportScale(context, value)
                    }
                )
            }
            KeyboardThemePreviewCarousel(
                theme = activePreviewTheme,
                currentPage = previewPagerState.currentPage,
                pageContent = {
                    HorizontalPager(
                        state = previewPagerState,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        when (page) {
                            0 -> HardwareKeyboardThemePreview(
                                theme = if (hardwareAssignmentMode == SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM) {
                                    if (systemIsDark) hardwareDarkTheme else hardwareLightTheme
                                } else {
                                    hardwareTheme
                                }
                            )
                            else -> VirtualKeyboardThemePreview(
                                theme = if (softwareAssignmentMode == SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM) {
                                    if (systemIsDark) softwareDarkTheme else softwareLightTheme
                                } else {
                                    softwareTheme
                                },
                                viewportScale = softwarePreviewViewportScale
                            )
                        }
                    }
                }
            )

            Text(
                text = stringResource(R.string.keyboard_theme_customize_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp)
            )
            TabRow(
                selectedTabIndex = customizationTab,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Tab(
                    selected = customizationTab == 0,
                    onClick = { customizationTab = 0 },
                    text = { Text(stringResource(R.string.keyboard_theme_tab_colors)) }
                )
                Tab(
                    selected = customizationTab == 1,
                    onClick = { customizationTab = 1 },
                    text = { Text(stringResource(R.string.keyboard_theme_tab_keys)) }
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (customizationTab == 0) {
                    KeyboardThemeColorsEditor(
                        theme = activeTheme,
                        preset = activePreset,
                        isSoftware = activePreviewPage == 1,
                        onThemeChanged = ::updateActiveTheme
                    )
                } else {
                    KeyboardThemeKeysEditor(
                        theme = activeTheme,
                        preset = activePreset,
                        isSoftware = activePreviewPage == 1,
                        onThemeChanged = ::updateActiveTheme
                    )
                }
            }
        }
    }

    exportTheme?.let { theme ->
        KeyboardThemeExportDialog(
            theme = theme,
            onDismiss = { exportTheme = null }
        )
    }
    if (showImportDialog) {
        KeyboardThemeImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { importedTheme ->
                showImportDialog = false
                applyImportedTheme(importedTheme)
            }
        )
    }
    if (showSaveAsDialog) {
        KeyboardThemeSaveAsDialog(
            initialName = activeTheme.name,
            onDismiss = { showSaveAsDialog = false },
            onSave = { name ->
                showSaveAsDialog = false
                saveActiveThemeAs(name)
            }
        )
    }
    themePickerRequest?.let { request ->
        val options = if (request.target == SettingsManager.KeyboardThemeTarget.HARDWARE) {
            hardwareThemeOptions
        } else {
            softwareThemeOptions
        }
        KeyboardThemePickerDialog(
            title = if (request.dark) {
                stringResource(R.string.keyboard_theme_dark_mode_theme)
            } else {
                stringResource(R.string.keyboard_theme_light_mode_theme)
            },
            options = options,
            onDismiss = { themePickerRequest = null },
            onThemeSelected = { option ->
                themePickerRequest = null
                val theme = option.preset
                if (request.target == SettingsManager.KeyboardThemeTarget.HARDWARE) {
                    if (request.dark) {
                        hardwareDarkTheme = theme
                    } else {
                        hardwareLightTheme = theme
                    }
                } else {
                    if (request.dark) {
                        softwareDarkTheme = theme
                    } else {
                        softwareLightTheme = theme
                    }
                }
                SettingsManager.setKeyboardThemeSystemSlot(context, request.target, request.dark, theme.toSettingsTheme())
            }
        )
    }
    overrideEditorRequest?.let { request ->
        val options = if (request.target == SettingsManager.KeyboardThemeTarget.HARDWARE) {
            hardwareThemeOptions
        } else {
            softwareThemeOptions
        }
        KeyboardThemeOverrideEditorDialog(
            initialOverride = request.override,
            themeOptions = options,
            onDismiss = { overrideEditorRequest = null },
            onSave = { locale, layout, theme ->
                SettingsManager.upsertKeyboardThemeLayoutOverride(
                    context,
                    request.target,
                    locale,
                    layout,
                    theme.toSettingsTheme()
                )
                if (request.target == SettingsManager.KeyboardThemeTarget.HARDWARE) {
                    hardwareOverrides = SettingsManager.getKeyboardThemeLayoutOverrides(context, request.target)
                } else {
                    softwareOverrides = SettingsManager.getKeyboardThemeLayoutOverrides(context, request.target)
                }
                overrideEditorRequest = null
            }
        )
    }
}

@Composable
private fun KeyboardThemePresetCard(
    preset: KeyboardThemePreset,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(176.dp)
            .height(76.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (selected) 0.9f else 0.45f),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) Color(preset.accent) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeyboardThemeMiniPreview(preset)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                if (selected) {
                    Text(
                        text = stringResource(R.string.keyboard_theme_selected),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(preset.accent),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private data class KeyboardThemePickerRequest(
    val target: SettingsManager.KeyboardThemeTarget,
    val dark: Boolean
)

private data class KeyboardThemeOverrideEditorRequest(
    val target: SettingsManager.KeyboardThemeTarget,
    val override: SettingsManager.KeyboardThemeLayoutOverride?
)

@Composable
private fun KeyboardThemeAssignmentSection(
    target: SettingsManager.KeyboardThemeTarget,
    mode: String,
    lightThemeName: String,
    darkThemeName: String,
    modifier: Modifier = Modifier,
    onModeChanged: (String) -> Unit,
    onPickLightTheme: () -> Unit,
    onPickDarkTheme: () -> Unit,
    overrides: List<SettingsManager.KeyboardThemeLayoutOverride>,
    themeOptions: List<KeyboardThemeOption>,
    onAddOverride: () -> Unit,
    onEditOverride: (SettingsManager.KeyboardThemeLayoutOverride) -> Unit,
    onRemoveOverride: (SettingsManager.KeyboardThemeLayoutOverride) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (target == SettingsManager.KeyboardThemeTarget.SOFTWARE) {
                    stringResource(R.string.keyboard_theme_assignment_software_title)
                } else {
                    stringResource(R.string.keyboard_theme_assignment_hardware_title)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KeyboardThemeModeButton(
                    label = stringResource(R.string.keyboard_theme_assignment_fixed),
                    selected = mode == SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FIXED,
                    modifier = Modifier.weight(1f),
                    onClick = { onModeChanged(SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FIXED) }
                )
                KeyboardThemeModeButton(
                    label = stringResource(R.string.keyboard_theme_assignment_follow_system),
                    selected = mode == SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM,
                    modifier = Modifier.weight(1f),
                    onClick = { onModeChanged(SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM) }
                )
            }
            if (mode == SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM) {
                KeyboardThemePickerRow(
                    label = stringResource(R.string.keyboard_theme_light_mode_theme),
                    value = lightThemeName,
                    onClick = onPickLightTheme
                )
                KeyboardThemePickerRow(
                    label = stringResource(R.string.keyboard_theme_dark_mode_theme),
                    value = darkThemeName,
                    onClick = onPickDarkTheme
                )
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.keyboard_theme_layout_overrides_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.keyboard_theme_layout_overrides_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            overrides.forEach { override ->
                KeyboardThemeOverrideRow(
                    override = override,
                    themeName = themeDisplayName(themeOptions, override.theme.toKeyboardThemePreset("Custom")),
                    onClick = { onEditOverride(override) },
                    onRemove = { onRemoveOverride(override) }
                )
            }
            TextButton(onClick = onAddOverride) {
                Text(stringResource(R.string.keyboard_theme_layout_overrides_add))
            }
        }
    }
}

@Composable
private fun KeyboardThemeAssignmentSummaryRow(
    target: SettingsManager.KeyboardThemeTarget,
    mode: String,
    lightThemeName: String,
    darkThemeName: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (target == SettingsManager.KeyboardThemeTarget.SOFTWARE) {
                        stringResource(R.string.keyboard_theme_assignment_software_title)
                    } else {
                        stringResource(R.string.keyboard_theme_assignment_hardware_title)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = if (mode == SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM) {
                        "${stringResource(R.string.keyboard_theme_assignment_follow_system)}: $lightThemeName / $darkThemeName"
                    } else {
                        stringResource(R.string.keyboard_theme_assignment_fixed)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun KeyboardThemeOverrideRow(
    override: SettingsManager.KeyboardThemeLayoutOverride,
    themeName: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = listOfNotNull(override.locale, override.layout).joinToString(" / "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = themeName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.delete))
            }
        }
    }
}

@Composable
private fun KeyboardThemeModeButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = if (selected) {
            ButtonDefaults.buttonColors()
        } else {
            ButtonDefaults.filledTonalButtonColors()
        }
    ) {
        Text(label, maxLines = 1)
    }
}

@Composable
private fun KeyboardThemePickerRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun KeyboardThemePickerDialog(
    title: String,
    options: List<KeyboardThemeOption>,
    onDismiss: () -> Unit,
    onThemeSelected: (KeyboardThemeOption) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    KeyboardThemePreviewPickerRow(
                        preset = option.preset,
                        selected = false,
                        onClick = { onThemeSelected(option) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun KeyboardThemeOverrideEditorDialog(
    initialOverride: SettingsManager.KeyboardThemeLayoutOverride?,
    themeOptions: List<KeyboardThemeOption>,
    onDismiss: () -> Unit,
    onSave: (String?, String?, KeyboardThemePreset) -> Unit
) {
    val context = LocalContext.current
    val targets = remember(context) { keyboardThemeOverrideTargets(context) }
    var selectedTarget by remember(initialOverride, targets) {
        mutableStateOf(
            targets.firstOrNull {
                it.locale == initialOverride?.locale && it.layout == initialOverride?.layout
            } ?: targets.first()
        )
    }
    var selectedTheme by remember(initialOverride, themeOptions) {
        mutableStateOf(
            initialOverride
                ?.let { override ->
                    themeOptions.firstOrNull {
                        keyboardThemesEquivalent(it.preset.toSettingsTheme(), override.theme)
                    }?.preset ?: override.theme.toKeyboardThemePreset("Custom")
                }
                ?: themeOptions.first().preset
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keyboard_theme_layout_override_editor_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.keyboard_theme_layout_override_target),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                targets.forEach { target ->
                    KeyboardThemeOverrideTargetRow(
                        target = target,
                        selected = target == selectedTarget,
                        onClick = { selectedTarget = target }
                    )
                }
                Text(
                    text = stringResource(R.string.keyboard_theme_layout_override_theme),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                themeOptions.forEach { option ->
                    KeyboardThemePreviewPickerRow(
                        preset = option.preset,
                        selected = keyboardThemesEquivalent(option.preset.toSettingsTheme(), selectedTheme.toSettingsTheme()),
                        onClick = { selectedTheme = option.preset }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        selectedTarget.locale,
                        selectedTarget.layout,
                        selectedTheme
                    )
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private data class KeyboardThemeOverrideTarget(
    val locale: String?,
    val layout: String?,
    val label: String,
    val description: String
)

@Composable
private fun KeyboardThemeOverrideTargetRow(
    target: KeyboardThemeOverrideTarget,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = if (selected) 4.dp else 1.dp,
        shape = MaterialTheme.shapes.small,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = target.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = target.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

private fun themeDisplayName(
    options: List<KeyboardThemeOption>,
    theme: KeyboardThemePreset
): String =
    options.firstOrNull { keyboardThemesEquivalent(it.preset.toSettingsTheme(), theme.toSettingsTheme()) }
        ?.preset
        ?.name
        ?: theme.name

private fun keyboardThemeOverrideTargets(context: Context): List<KeyboardThemeOverrideTarget> {
    val targets = mutableListOf<KeyboardThemeOverrideTarget>()
    val seen = mutableSetOf<String>()

    fun add(locale: String?, layout: String?, label: String, description: String) {
        val normalizedLocale = locale?.trim()?.replace('_', '-')?.takeIf { it.isNotBlank() }
        val normalizedLayout = layout?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedLocale == null && normalizedLayout == null) return
        val key = "${normalizedLocale.orEmpty()}:${normalizedLayout.orEmpty()}"
        if (seen.add(key)) {
            targets += KeyboardThemeOverrideTarget(normalizedLocale, normalizedLayout, label, description)
        }
    }

    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    val activeSubtype = imm?.currentInputMethodSubtype
    val activeLocale = activeSubtype?.localeString()
    val activeLayout = AdditionalSubtypeUtils.resolveInputStyleLayout(context.assets, context, activeSubtype)
    add(
        locale = activeLocale,
        layout = activeLayout,
        label = context.getString(R.string.keyboard_theme_layout_override_current_input_style),
        description = listOfNotNull(activeLocale, activeLayout).joinToString(" / ")
    )

    val imeInfo = imm?.getInputMethodList()?.firstOrNull { info ->
        info.packageName == context.packageName &&
            info.serviceName == PhysicalKeyboardInputMethodService::class.java.name
    }
    val cycleableSubtypes = if (imm != null && imeInfo != null) {
        SubtypeCycler.getCycleableSubtypes(
            context = context,
            assets = context.assets,
            subtypes = imm.getEnabledInputMethodSubtypeList(imeInfo, true)
        )
    } else {
        emptyList()
    }

    if (cycleableSubtypes.isNotEmpty()) {
        cycleableSubtypes.forEach { subtype ->
            val locale = subtype.localeString()
            val layout = SubtypeCycler.resolveSubtypeCycleLayout(context.assets, context, subtype)
            add(
                locale = locale,
                layout = layout,
                label = keyboardThemeLocaleDisplayName(locale),
                description = layout
            )
        }
    } else {
        keyboardThemeSystemLocales(context).forEach { locale ->
            val layout = AdditionalSubtypeUtils.getLayoutForLocale(context.assets, locale, context)
            if (!SettingsManager.isSystemInputStyleHidden(context, locale, layout)) {
                add(
                    locale = locale,
                    layout = layout,
                    label = keyboardThemeLocaleDisplayName(locale),
                    description = layout
                )
            }
        }

        SettingsManager.getCustomInputStyles(context)
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { entry ->
                val parts = entry.split(":").map { it.trim() }
                if (parts.size >= 2) {
                    add(
                        locale = parts[0],
                        layout = parts[1],
                        label = keyboardThemeLocaleDisplayName(parts[0]),
                        description = parts[1]
                    )
                }
            }
    }

    return targets.ifEmpty {
        listOf(
            KeyboardThemeOverrideTarget(
                locale = activeLocale?.trim()?.replace('_', '-')?.takeIf { it.isNotBlank() },
                layout = activeLayout,
                label = context.getString(R.string.keyboard_theme_layout_override_current_input_style),
                description = listOfNotNull(activeLocale, activeLayout).joinToString(" / ")
            )
        )
    }
}

private fun keyboardThemeSystemLocales(context: Context): List<String> {
    val locales = mutableListOf<String>()
    val config = context.applicationContext.resources.configuration
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val localeList = config.locales
        for (index in 0 until localeList.size()) {
            val locale = localeList[index]
            val value = keyboardThemeFormatLocale(locale)
            if (value.isNotBlank() && value !in locales) locales += value
        }
    } else {
        @Suppress("DEPRECATION")
        val value = keyboardThemeFormatLocale(config.locale)
        if (value.isNotBlank()) locales += value
    }
    return locales
}

private fun keyboardThemeFormatLocale(locale: Locale): String =
    if (locale.country.isNotBlank()) {
        "${locale.language}_${locale.country}"
    } else {
        locale.language
    }

private fun keyboardThemeLocaleDisplayName(locale: String): String {
    val parsed = Locale.forLanguageTag(locale.replace('_', '-'))
    val display = parsed.getDisplayName(parsed).takeIf { it.isNotBlank() }
    return display ?: locale
}

@Composable
private fun KeyboardThemePreviewPickerRow(
    preset: KeyboardThemePreset,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = if (selected) 4.dp else 1.dp,
        shape = MaterialTheme.shapes.small,
        border = if (selected) BorderStroke(1.dp, Color(preset.accent)) else null
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeyboardThemeMiniPreview(preset)
            Text(
                text = preset.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            if (selected) {
                Text(
                    text = stringResource(R.string.keyboard_theme_selected),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(preset.accent),
                    maxLines = 1
                )
            }
        }
    }
}

private fun matchKeyboardThemeOption(
    options: List<KeyboardThemeOption>,
    theme: KeyboardThemePreset
): String? {
    val target = theme.toSettingsTheme()
    return options.firstOrNull { keyboardThemesEquivalent(it.preset.toSettingsTheme(), target) }?.key
}

private fun keyboardThemesEquivalent(
    left: SettingsManager.KeyboardThemeSettings,
    right: SettingsManager.KeyboardThemeSettings
): Boolean {
    fun close(a: Float, b: Float): Boolean = abs(a - b) < 0.001f
    return left.background == right.background &&
        left.divider == right.divider &&
        left.normalKey == right.normalKey &&
        left.specialKey == right.specialKey &&
        left.textAndIcons == right.textAndIcons &&
        left.ledInactive == right.ledInactive &&
        left.ledActive == right.ledActive &&
        left.ledLocked == right.ledLocked &&
        left.accent == right.accent &&
        left.keyTap == right.keyTap &&
        left.cursorSwipe == right.cursorSwipe &&
        left.keyPopup == right.keyPopup &&
        left.keyPopupSelected == right.keyPopupSelected &&
        left.suggestion == right.suggestion &&
        left.statusBarButton == right.statusBarButton &&
        close(left.keyCornerRadiusRatio, right.keyCornerRadiusRatio) &&
        close(left.chromeCornerRadiusRatio, right.chromeCornerRadiusRatio) &&
        close(left.keyHeightScale, right.keyHeightScale) &&
        close(left.numberRowHeightScale, right.numberRowHeightScale) &&
        close(left.keyWidthScale, right.keyWidthScale) &&
        close(left.rowGapScale, right.rowGapScale) &&
        left.distributeHorizontalSpacing == right.distributeHorizontalSpacing &&
        left.ortholinear == right.ortholinear &&
        left.showLeds == right.showLeds &&
        close(left.suggestionsHeightScale, right.suggestionsHeightScale) &&
        close(left.variationsHeightScale, right.variationsHeightScale) &&
        close(left.modifierIndicatorStripScale, right.modifierIndicatorStripScale) &&
        left.keyPopupStyle == right.keyPopupStyle &&
        left.keyPopupAttached == right.keyPopupAttached &&
        left.keyPopupTailEnabled == right.keyPopupTailEnabled &&
        left.keyPreviewAfterLongPress == right.keyPreviewAfterLongPress &&
        left.keyAlternatesPopupEnabled == right.keyAlternatesPopupEnabled
}

@Composable
private fun KeyboardThemeExportDialog(
    theme: KeyboardThemePreset,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val exportString = remember(theme) {
        SettingsManager.keyboardThemeToJsonString(theme.toSettingsTheme())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export theme") },
        text = {
            OutlinedTextField(
                value = exportString,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 8
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(exportString))
                    onDismiss()
                }
            ) {
                Text("Copy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun KeyboardThemeImportDialog(
    onDismiss: () -> Unit,
    onImport: (KeyboardThemePreset) -> Unit
) {
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import theme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    label = { Text("Theme string") }
                )
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val imported = SettingsManager.keyboardThemeFromJsonString(value)
                    if (imported == null) {
                        error = "Invalid theme string"
                    } else {
                        onImport(imported.toKeyboardThemePreset("Imported"))
                    }
                }
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun KeyboardThemeSaveAsDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName.takeUnless { it == "Custom" } ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save copy as") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Theme name") }
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty(),
                onClick = { onSave(name) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun KeyboardThemeColorsEditor(
    theme: KeyboardThemePreset,
    preset: KeyboardThemePreset,
    isSoftware: Boolean,
    onThemeChanged: (KeyboardThemePreset) -> Unit
) {
    val colorItems = buildList {
        add(
            KeyboardThemeColorEditorItem(
                label = stringResource(R.string.keyboard_theme_background),
                color = theme.background,
                presetColor = preset.background,
                onColorChanged = { onThemeChanged(theme.copy(background = it)) }
            )
        )
        add(
            KeyboardThemeColorEditorItem(
                label = stringResource(R.string.keyboard_theme_dividers),
                color = theme.divider,
                presetColor = preset.divider,
                onColorChanged = { onThemeChanged(theme.copy(divider = it)) }
            )
        )
        add(
            KeyboardThemeColorEditorItem(
                label = stringResource(R.string.keyboard_theme_normal_keys),
                color = theme.normalKey,
                presetColor = preset.normalKey,
                onColorChanged = { onThemeChanged(theme.copy(normalKey = it, suggestion = it)) }
            )
        )
        add(
            KeyboardThemeColorEditorItem(
                label = "Key tap",
                color = theme.keyTap,
                presetColor = preset.keyTap,
                onColorChanged = { onThemeChanged(theme.copy(keyTap = it)) }
            )
        )
        add(
            KeyboardThemeColorEditorItem(
                label = stringResource(R.string.keyboard_theme_special_keys),
                color = theme.specialKey,
                presetColor = preset.specialKey,
                onColorChanged = { onThemeChanged(theme.copy(specialKey = it, statusBarButton = it)) }
            )
        )
        add(
            KeyboardThemeColorEditorItem(
                label = stringResource(R.string.keyboard_theme_text_icons),
                color = theme.textAndIcons,
                presetColor = preset.textAndIcons,
                onColorChanged = { onThemeChanged(theme.copy(textAndIcons = it)) }
            )
        )
        add(
            KeyboardThemeColorEditorItem(
                label = "Suggestions",
                color = theme.suggestion,
                presetColor = preset.suggestion,
                onColorChanged = { onThemeChanged(theme.copy(suggestion = it)) }
            )
        )
        add(
            KeyboardThemeColorEditorItem(
                label = "Status bar buttons",
                color = theme.statusBarButton,
                presetColor = preset.statusBarButton,
                onColorChanged = { onThemeChanged(theme.copy(statusBarButton = it)) }
            )
        )
        add(
            KeyboardThemeColorEditorItem(
                label = "Cursor swipe",
                color = theme.cursorSwipe,
                presetColor = preset.cursorSwipe,
                onColorChanged = { onThemeChanged(theme.copy(cursorSwipe = it)) }
            )
        )
        if (isSoftware) {
            add(
                KeyboardThemeColorEditorItem(
                    label = "Key popup",
                    color = theme.keyPopup,
                    presetColor = preset.keyPopup,
                    onColorChanged = { onThemeChanged(theme.copy(keyPopup = it)) }
                )
            )
            add(
                KeyboardThemeColorEditorItem(
                    label = "Selected popup key",
                    color = theme.keyPopupSelected,
                    presetColor = preset.keyPopupSelected,
                    onColorChanged = { onThemeChanged(theme.copy(keyPopupSelected = it)) }
                )
            )
        }
        add(
            KeyboardThemeColorEditorItem(
                label = stringResource(R.string.keyboard_theme_led_inactive),
                color = theme.ledInactive,
                presetColor = preset.ledInactive,
                onColorChanged = { onThemeChanged(theme.copy(ledInactive = it)) }
            )
        )
        add(
            KeyboardThemeColorEditorItem(
                label = stringResource(R.string.keyboard_theme_led_active),
                color = theme.ledActive,
                presetColor = preset.ledActive,
                onColorChanged = { onThemeChanged(theme.copy(ledActive = it)) }
            )
        )
        add(
            KeyboardThemeColorEditorItem(
                label = stringResource(R.string.keyboard_theme_led_locked),
                color = theme.ledLocked,
                presetColor = preset.ledLocked,
                onColorChanged = { onThemeChanged(theme.copy(ledLocked = it)) }
            )
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        colorItems.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    KeyboardThemeColorRow(
                        label = item.label,
                        color = item.color,
                        presetColor = item.presetColor,
                        onColorChanged = item.onColorChanged,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class KeyboardThemeColorEditorItem(
    val label: String,
    val color: Int,
    val presetColor: Int,
    val onColorChanged: (Int) -> Unit
)

@Composable
private fun KeyboardThemeKeysEditor(
    theme: KeyboardThemePreset,
    preset: KeyboardThemePreset,
    isSoftware: Boolean,
    onThemeChanged: (KeyboardThemePreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isSoftware) {
            KeyboardThemeSliderRow(
                label = "Key rounding",
                value = theme.keyCornerRadiusRatio,
                presetValue = preset.keyCornerRadiusRatio,
                valueRange = 0f..0.35f,
                onValueChanged = { onThemeChanged(theme.copy(keyCornerRadiusRatio = it)) }
            )
        }
        KeyboardThemeSliderRow(
            label = "Suggestions / variations rounding",
            value = theme.chromeCornerRadiusRatio,
            presetValue = preset.chromeCornerRadiusRatio,
            valueRange = 0f..0.35f,
            onValueChanged = { onThemeChanged(theme.copy(chromeCornerRadiusRatio = it)) }
        )
        KeyboardThemeSliderRow(
            label = "Suggestions height",
            value = theme.suggestionsHeightScale,
            presetValue = preset.suggestionsHeightScale,
            valueRange = 0.65f..1.6f,
            onValueChanged = { onThemeChanged(theme.copy(suggestionsHeightScale = it)) }
        )
        KeyboardThemeSliderRow(
            label = "Variations height",
            value = theme.variationsHeightScale,
            presetValue = preset.variationsHeightScale,
            valueRange = 0.65f..1.6f,
            onValueChanged = { onThemeChanged(theme.copy(variationsHeightScale = it)) }
        )
        KeyboardThemeSliderRow(
            label = "Modifier strip thickness",
            value = theme.modifierIndicatorStripScale,
            presetValue = preset.modifierIndicatorStripScale,
            valueRange = 0.35f..1.4f,
            onValueChanged = { onThemeChanged(theme.copy(modifierIndicatorStripScale = it)) }
        )
        if (isSoftware) {
            KeyboardThemeSwitchRow(
                label = "Show LEDs",
                checked = theme.showLeds,
                presetChecked = preset.showLeds,
                onCheckedChanged = { onThemeChanged(theme.copy(showLeds = it)) }
            )
            KeyboardThemeSliderRow(
                label = "Key height",
                value = theme.keyHeightScale,
                presetValue = preset.keyHeightScale,
                valueRange = 0.72f..1.9f,
                onValueChanged = { onThemeChanged(theme.copy(keyHeightScale = it)) }
            )
            KeyboardThemeSliderRow(
                label = "Number row height",
                value = theme.numberRowHeightScale,
                presetValue = preset.numberRowHeightScale,
                valueRange = 0.45f..1.4f,
                onValueChanged = { onThemeChanged(theme.copy(numberRowHeightScale = it)) }
            )
            KeyboardThemeSliderRow(
                label = "Key width",
                value = theme.keyWidthScale,
                presetValue = preset.keyWidthScale,
                valueRange = 0.72f..1.12f,
                onValueChanged = { onThemeChanged(theme.copy(keyWidthScale = it)) }
            )
            KeyboardThemeSliderRow(
                label = "Row spacing",
                value = theme.rowGapScale,
                presetValue = preset.rowGapScale,
                valueRange = 0f..2f,
                onValueChanged = { onThemeChanged(theme.copy(rowGapScale = it)) }
            )
            KeyboardThemeSwitchRow(
                label = "Distribute spacing",
                checked = theme.distributeHorizontalSpacing,
                presetChecked = preset.distributeHorizontalSpacing,
                onCheckedChanged = { onThemeChanged(theme.copy(distributeHorizontalSpacing = it)) }
            )
            KeyboardThemeSwitchRow(
                label = "Ortholinear",
                checked = theme.ortholinear,
                presetChecked = preset.ortholinear,
                onCheckedChanged = { onThemeChanged(theme.copy(ortholinear = it)) }
            )
            KeyboardThemeSwitchRow(
                label = "Attach popup to key",
                checked = theme.keyPopupAttached,
                presetChecked = preset.keyPopupAttached,
                onCheckedChanged = { onThemeChanged(theme.copy(keyPopupAttached = it)) }
            )
            KeyboardThemeSwitchRow(
                label = "Popup tail connector",
                checked = theme.keyPopupTailEnabled,
                presetChecked = preset.keyPopupTailEnabled,
                onCheckedChanged = { onThemeChanged(theme.copy(keyPopupTailEnabled = it)) }
            )
            KeyboardThemeSwitchRow(
                label = "Show key preview only on hold",
                checked = theme.keyPreviewAfterLongPress,
                presetChecked = preset.keyPreviewAfterLongPress,
                onCheckedChanged = { onThemeChanged(theme.copy(keyPreviewAfterLongPress = it)) }
            )
            KeyboardThemeSwitchRow(
                label = "Long-press character picker",
                checked = theme.keyAlternatesPopupEnabled,
                presetChecked = preset.keyAlternatesPopupEnabled,
                onCheckedChanged = { onThemeChanged(theme.copy(keyAlternatesPopupEnabled = it)) }
            )
        }
    }
}

@Composable
private fun KeyboardThemeMiniPreview(preset: KeyboardThemePreset) {
    val keyShape = MaterialTheme.shapes.extraSmall
    val dividerColor = Color(preset.divider)
    Column(
        modifier = Modifier
            .width(56.dp)
            .height(42.dp)
            .background(Color(preset.background), keyShape)
            .border(1.dp, dividerColor, keyShape)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .weight(1f)
                        .background(Color(preset.normalKey), keyShape)
                        .border(1.dp, dividerColor, keyShape)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .weight(1f)
                        .background(Color(preset.normalKey), keyShape)
                        .border(1.dp, dividerColor, keyShape)
                )
            }
            Box(
                modifier = Modifier
                    .height(18.dp)
                    .weight(1f)
                    .background(Color(preset.specialKey), keyShape)
                    .border(1.dp, dividerColor, keyShape)
            )
        }
    }
}

@Composable
private fun KeyboardThemePreviewCarousel(
    theme: KeyboardThemePreset,
    currentPage: Int,
    pageContent: @Composable () -> Unit
) {
    if (currentPage == 1) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(theme.background))
        ) {
            pageContent()
        }
        return
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Color(theme.background),
        border = BorderStroke(1.dp, Color(theme.divider))
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            pageContent()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(2) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (currentPage == index) 8.dp else 6.dp)
                            .background(
                                color = if (currentPage == index) Color(theme.accent) else Color(theme.divider),
                                shape = MaterialTheme.shapes.extraSmall
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyboardThemeColorRow(
    label: String,
    color: Int,
    presetColor: Int,
    onColorChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KeyboardThemeSwatchButton(color = color, onColorChanged = onColorChanged)
                Text(
                    text = color.toHexColorLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(
                    onClick = { onColorChanged(presetColor) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.RestartAlt,
                        contentDescription = stringResource(R.string.keyboard_theme_reset),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyboardThemeSliderRow(
    label: String,
    value: Float,
    presetValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onValueChanged: (Float) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(
                    onClick = { onValueChanged(presetValue) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.RestartAlt,
                        contentDescription = stringResource(R.string.keyboard_theme_reset),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = { onValueChanged(it.coerceIn(valueRange.start, valueRange.endInclusive)) },
                valueRange = valueRange,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun KeyboardThemeSwitchRow(
    label: String,
    checked: Boolean,
    presetChecked: Boolean,
    onCheckedChanged: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            IconButton(
                onClick = { onCheckedChanged(presetChecked) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.RestartAlt,
                    contentDescription = stringResource(R.string.keyboard_theme_reset),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChanged
            )
        }
    }
}


@Composable
private fun KeyboardThemeSwatchButton(
    color: Int,
    onColorChanged: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var pickerColor by remember(color) { mutableStateOf(color) }
    val shape = MaterialTheme.shapes.small
    Box {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(color), shape)
                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f), shape)
                .clickable {
                    pickerColor = color
                    expanded = true
                }
        )
        if (expanded) {
            KeyboardThemeColorPickerDialog(
                initialColor = color,
                onDismiss = { expanded = false },
                onColorSelected = {
                    expanded = false
                    onColorChanged(it)
                },
                onPreviewColorChanged = { pickerColor = it }
            )
        }
    }
}

@Composable
private fun KeyboardThemeColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit,
    onPreviewColorChanged: (Int) -> Unit
) {
    var color by remember(initialColor) { mutableStateOf(initialColor) }
    val hsv = remember(color) {
        FloatArray(3).also { AndroidColor.colorToHSV(color, it) }
    }
    var hue by remember(initialColor) { mutableStateOf(hsv[0]) }
    var saturation by remember(initialColor) { mutableStateOf(hsv[1]) }
    var value by remember(initialColor) { mutableStateOf(hsv[2]) }

    fun updateColor(newHue: Float = hue, newSaturation: Float = saturation, newValue: Float = value) {
        hue = newHue
        saturation = newSaturation
        value = newValue
        color = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
        onPreviewColorChanged(color)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(0.9f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Presets",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    keyboardThemeSwatches().take(18).forEach { swatch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    color = swatch
                                    AndroidColor.colorToHSV(swatch, hsv)
                                    updateColor(hsv[0], hsv[1], hsv[2])
                                }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(20.dp),
                                shape = MaterialTheme.shapes.extraSmall,
                                color = Color(swatch)
                            ) {}
                            Text(
                                text = swatch.toHexColorLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1.35f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KeyboardThemeColorWheel(
                        hue = hue,
                        saturation = saturation,
                        value = value,
                        onColorChanged = { newHue, newSaturation, newValue ->
                            updateColor(newHue, newSaturation, newValue)
                        }
                    )
                    Text(color.toHexColorLabel(), style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = { onColorSelected(color) }) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }
}

private enum class KeyboardThemeColorWheelTarget {
    Hue,
    SaturationValue
}

@Composable
private fun KeyboardThemeColorWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onColorChanged: (Float, Float, Float) -> Unit
) {
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    val currentHue by rememberUpdatedState(hue)
    val currentSaturation by rememberUpdatedState(saturation)
    val currentValue by rememberUpdatedState(value)
    val currentOnColorChanged by rememberUpdatedState(onColorChanged)

    fun resolveTarget(
        offset: Offset,
        center: Offset,
        ringCenterRadius: Float,
        ringStroke: Float,
        squareLeft: Float,
        squareTop: Float,
        squareRight: Float,
        squareBottom: Float
    ): KeyboardThemeColorWheelTarget? {
        if (offset.x in squareLeft..squareRight && offset.y in squareTop..squareBottom) {
            return KeyboardThemeColorWheelTarget.SaturationValue
        }

        val dx = offset.x - center.x
        val dy = offset.y - center.y
        val distance = sqrt(dx * dx + dy * dy)
        return if (distance in (ringCenterRadius - ringStroke)..(ringCenterRadius + ringStroke)) {
            KeyboardThemeColorWheelTarget.Hue
        } else {
            null
        }
    }

    fun handlePosition(offset: Offset, preferredTarget: KeyboardThemeColorWheelTarget? = null): KeyboardThemeColorWheelTarget? {
        val side = min(canvasSize.width, canvasSize.height)
        if (side <= 0f) return null
        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        val radius = side / 2f
        val ringStroke = radius * 0.18f
        val ringCenterRadius = radius - ringStroke / 2f
        val dx = offset.x - center.x
        val dy = offset.y - center.y
        val squareSide = radius * 1.18f
        val squareLeft = center.x - squareSide / 2f
        val squareTop = center.y - squareSide / 2f
        val squareRight = squareLeft + squareSide
        val squareBottom = squareTop + squareSide
        val target = preferredTarget ?: resolveTarget(
            offset = offset,
            center = center,
            ringCenterRadius = ringCenterRadius,
            ringStroke = ringStroke,
            squareLeft = squareLeft,
            squareTop = squareTop,
            squareRight = squareRight,
            squareBottom = squareBottom
        )

        if (target == KeyboardThemeColorWheelTarget.Hue) {
            val angle = (atan2(dy, dx) * 180f / PI.toFloat() + 360f) % 360f
            currentOnColorChanged(angle, currentSaturation, currentValue)
            return target
        }
        if (target == null) {
            return null
        }

        val newSaturation = ((offset.x.coerceIn(squareLeft, squareRight) - squareLeft) / squareSide)
            .coerceIn(0f, 1f)
        val newValue = (1f - ((offset.y.coerceIn(squareTop, squareBottom) - squareTop) / squareSide))
            .coerceIn(0f, 1f)
        currentOnColorChanged(currentHue, newSaturation, newValue)
        return target
    }
    val currentHandlePosition by rememberUpdatedState(::handlePosition)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .onSizeChanged {
                canvasSize = Size(it.width.toFloat(), it.height.toFloat())
            }
            .pointerInput(canvasSize) {
                detectTapGestures { offset -> currentHandlePosition(offset, null) }
            }
            .pointerInput(canvasSize) {
                var dragTarget: KeyboardThemeColorWheelTarget? = null
                detectDragGestures(
                    onDragStart = { offset ->
                        dragTarget = currentHandlePosition(offset, null)
                    },
                    onDragEnd = { dragTarget = null },
                    onDragCancel = { dragTarget = null },
                    onDrag = { change, _ ->
                        dragTarget?.let { target ->
                            change.consume()
                            dragTarget = currentHandlePosition(change.position, target)
                        }
                    }
                )
            }
    ) {
        val side = min(size.width, size.height)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = side / 2f
        val ringStroke = radius * 0.18f
        val ringRadius = radius - ringStroke / 2f
        val squareSide = radius * 1.18f
        val squareLeft = center.x - squareSide / 2f
        val squareTop = center.y - squareSide / 2f
        val squareSize = Size(squareSide, squareSide)
        val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))

        drawCircle(
            brush = Brush.sweepGradient(
                listOf(
                    Color.Red,
                    Color.Yellow,
                    Color.Green,
                    Color.Cyan,
                    Color.Blue,
                    Color.Magenta,
                    Color.Red
                ),
                center = center
            ),
            radius = ringRadius,
            center = center,
            style = Stroke(width = ringStroke, cap = StrokeCap.Round)
        )

        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.White, hueColor),
                startX = squareLeft,
                endX = squareLeft + squareSide
            ),
            topLeft = Offset(squareLeft, squareTop),
            size = squareSize
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = squareTop,
                endY = squareTop + squareSide
            ),
            topLeft = Offset(squareLeft, squareTop),
            size = squareSize
        )

        val ringAngle = hue * PI.toFloat() / 180f
        val ringThumb = Offset(
            x = center.x + cos(ringAngle) * ringRadius,
            y = center.y + sin(ringAngle) * ringRadius
        )
        drawCircle(Color.White, radius = 11.dp.toPx(), center = ringThumb)
        drawCircle(Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))), radius = 8.dp.toPx(), center = ringThumb)

        val squareThumb = Offset(
            x = squareLeft + saturation.coerceIn(0f, 1f) * squareSide,
            y = squareTop + (1f - value.coerceIn(0f, 1f)) * squareSide
        )
        drawCircle(Color.White, radius = 10.dp.toPx(), center = squareThumb)
        drawCircle(Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))), radius = 7.dp.toPx(), center = squareThumb)
    }
}

private fun Int.toHexColorLabel(): String = "#${toUInt().toString(16).uppercase().takeLast(6)}"
