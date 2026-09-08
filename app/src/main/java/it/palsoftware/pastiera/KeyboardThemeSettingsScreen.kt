package it.palsoftware.pastiera

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.inputmethod.InputMethodManager
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
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
    initialTarget: SettingsManager.KeyboardThemeTarget? = null,
    initialTab: KeyboardThemeEditorTab? = null,
    initialAssignment: Boolean = false
) {
    val context = LocalContext.current
    val builtInPresets = remember { keyboardThemePresets() }
    var savedThemes by remember {
        mutableStateOf(SettingsManager.getSavedKeyboardThemes(context).map { it.toKeyboardThemeOption() })
    }
    var themeDrafts by remember {
        mutableStateOf(SettingsManager.getKeyboardThemeDrafts(context))
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
    var customizationTab by remember {
        mutableStateOf(if (initialTab == KeyboardThemeEditorTab.Keys) 1 else 0)
    }
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
    var pendingImportedTheme by remember { mutableStateOf<KeyboardThemePreset?>(null) }
    var showNewDraftDialog by remember { mutableStateOf(false) }
    var draftEditorName by remember { mutableStateOf<String?>(null) }
    val draftEditingGuard = remember { mutableStateOf(false) }
    var draftListFocusName by remember { mutableStateOf<String?>(null) }
    var deleteThemeRequest by remember { mutableStateOf<String?>(null) }
    var deleteDraftRequest by remember { mutableStateOf<String?>(null) }
    var themePickerRequest by remember { mutableStateOf<KeyboardThemePickerRequest?>(null) }
    var assignmentScreenTarget by remember {
        mutableStateOf<SettingsManager.KeyboardThemeTarget?>(
            SettingsManager.KeyboardThemeTarget.entries.firstOrNull { it.name == settingsChild(context, "theme_assignment") }
                ?: if (initialAssignment) initialTarget ?: SettingsManager.KeyboardThemeTarget.HARDWARE else null
        )
    }
    var overrideEditorRequest by remember { mutableStateOf<KeyboardThemeOverrideEditorRequest?>(null) }
    var hardwareOverrides by remember {
        mutableStateOf(SettingsManager.getKeyboardThemeLayoutOverrides(context, SettingsManager.KeyboardThemeTarget.HARDWARE))
    }
    var softwareOverrides by remember {
        mutableStateOf(SettingsManager.getKeyboardThemeLayoutOverrides(context, SettingsManager.KeyboardThemeTarget.SOFTWARE))
    }

    val settingHighlightId = LocalSettingHighlightId.current
    LaunchedEffect(settingHighlightId) {
        val route = settingHighlightId?.let(SettingLinkRegistry::byId)?.route
        if (route?.destination == SettingsDestination.Customization &&
            route.customizationDestination in setOf("keyboard_theme", "keyboard_theme_assignment")
        ) {
            // A shared link addresses the saved setting, even if a draft or assignment panel is open.
            draftEditingGuard.value = false
            draftEditorName = null
            val target = route.keyboardThemeTarget
            if (target != null) {
                previewPagerState.scrollToPage(if (target == SettingsManager.KeyboardThemeTarget.SOFTWARE) 1 else 0)
            }
            route.keyboardThemeTab?.let { customizationTab = if (it == KeyboardThemeEditorTab.Keys) 1 else 0 }
            assignmentScreenTarget = if (route.customizationDestination == "keyboard_theme_assignment") {
                target ?: SettingsManager.KeyboardThemeTarget.HARDWARE
            } else null
        }
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
    val presetListState = rememberLazyListState()
    val systemIsDark = isSystemInDarkTheme()
    val activeAssignmentMode = if (activePreviewPage == 0) hardwareAssignmentMode else softwareAssignmentMode
    val activeLightTheme = if (activePreviewPage == 0) hardwareLightTheme else softwareLightTheme
    val activeDarkTheme = if (activePreviewPage == 0) hardwareDarkTheme else softwareDarkTheme
    val activePreviewTheme = if (activeAssignmentMode == SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM) {
        if (systemIsDark) activeDarkTheme else activeLightTheme
    } else {
        activeTheme
    }

    LaunchedEffect(
        activePreviewPage,
        activeSelectionKey,
        activeThemeOptions.size,
        themeDrafts.size,
        draftListFocusName,
        draftEditorName
    ) {
        val draftIndex = (draftListFocusName ?: draftEditorName)?.let { name ->
            themeDrafts.indexOfFirst { it.name.equals(name, ignoreCase = true) }
                .takeIf { it >= 0 }
                ?.plus(activeThemeOptions.size)
        }
        val targetIndex = draftIndex
            ?: activeThemeOptions.indexOfFirst { it.key == activeSelectionKey }.takeIf { it >= 0 }
        if (targetIndex != null) {
            presetListState.animateScrollToItem(targetIndex)
        }
        draftListFocusName = null
    }

    fun updateActiveTheme(theme: KeyboardThemePreset) {
        // Ignore callbacks retained by the normal editor while a draft is opening.
        if (draftEditingGuard.value) return
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
        draftEditingGuard.value = false
        draftEditorName = null
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

    fun saveActiveThemeAs(name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty() || keyboardThemeNameExists(context, normalizedName)) return
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

    fun deleteSavedTheme(name: String) {
        SettingsManager.deleteKeyboardTheme(context, name)
        savedThemes = SettingsManager.getSavedKeyboardThemes(context).map { it.toKeyboardThemeOption() }
        val selectionKey = "saved:$name"
        if (hardwareSelectionKey == selectionKey) {
            hardwareSelectionKey = "custom:hardware"
            hardwarePreset = hardwareTheme.copy(name = "Custom")
            hardwareTheme = hardwarePreset
        }
        if (softwareSelectionKey == selectionKey) {
            softwareSelectionKey = "custom:software"
            softwarePreset = softwareTheme.copy(name = "Custom")
            softwareTheme = softwarePreset
        }
    }

    val editedDraft = draftEditorName?.let { name ->
        themeDrafts.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
    val draftPreset = editedDraft?.theme?.toKeyboardThemePreset(editedDraft.name)
    val draftMissingFields = editedDraft?.let { draft ->
        KEYBOARD_THEME_DRAFT_REQUIRED_FIELDS - draft.populatedFields
    }.orEmpty()
    val draftComplete = editedDraft != null && draftMissingFields.isEmpty()

    fun updateDraft(field: String, updatedTheme: KeyboardThemePreset) {
        val current = editedDraft ?: return
        SettingsManager.saveKeyboardThemeDraft(
            context,
            current.copy(
                theme = updatedTheme.toSettingsTheme(),
                populatedFields = current.populatedFields + field
            )
        )
        themeDrafts = SettingsManager.getKeyboardThemeDrafts(context)
    }

    fun useCompletedDraft() {
        val draft = editedDraft?.takeIf { draftComplete } ?: return
        val savedPreset = draft.theme.toKeyboardThemePreset(draft.name)
        SettingsManager.saveKeyboardTheme(context, draft.name, draft.theme)
        SettingsManager.deleteKeyboardThemeDraft(context, draft.name)
        savedThemes = SettingsManager.getSavedKeyboardThemes(context).map { it.toKeyboardThemeOption() }
        themeDrafts = SettingsManager.getKeyboardThemeDrafts(context)
        if (activePreviewPage == 0) {
            hardwareSelectionKey = "saved:${draft.name}"
            hardwarePreset = savedPreset
            hardwareTheme = savedPreset
            SettingsManager.setKeyboardTheme(context, activeTarget, draft.theme)
        } else {
            softwareSelectionKey = "saved:${draft.name}"
            softwarePreset = savedPreset
            softwareTheme = savedPreset
            SettingsManager.setKeyboardTheme(context, activeTarget, draft.theme)
        }
        draftEditingGuard.value = false
        draftEditorName = null
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
                                context.settingsActivity().finish()
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
            LazyRow(
                state = presetListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(
                    count = activeThemeOptions.size + themeDrafts.size,
                    key = { index ->
                        if (index < activeThemeOptions.size) {
                            activeThemeOptions[index].key
                        } else {
                            "draft:${themeDrafts[index - activeThemeOptions.size].name}"
                        }
                    }
                ) { index ->
                    if (index < activeThemeOptions.size) {
                        val option = activeThemeOptions[index]
                        KeyboardThemePresetCard(
                            preset = option.preset,
                            selected = activeSelectionKey == option.key,
                            onClick = { applyPreset(option) }
                        )
                    } else {
                        val draft = themeDrafts[index - activeThemeOptions.size]
                        KeyboardThemeDraftCard(
                            draft = draft,
                            editing = editedDraft?.name.equals(draft.name, ignoreCase = true),
                            onClick = {
                                draftEditingGuard.value = true
                                draftEditorName = draft.name
                            }
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showNewDraftDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.keyboard_theme_action_new))
                }
                Button(
                    onClick = { showImportDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.keyboard_theme_action_import))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showSaveAsDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(
                            if (editedDraft != null) {
                                R.string.keyboard_theme_action_duplicate_draft
                            } else {
                                R.string.keyboard_theme_action_save_as
                            }
                        )
                    )
                }
                Button(
                    onClick = { exportTheme = draftPreset ?: activeTheme },
                    enabled = editedDraft == null || draftComplete,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.keyboard_theme_action_export))
                }
                val savedName = activeSelectionKey.removePrefix("saved:")
                    .takeIf { activeSelectionKey.startsWith("saved:") }
                Button(
                    onClick = {
                        if (editedDraft != null) {
                            deleteDraftRequest = editedDraft.name
                        } else if (savedName != null) {
                            deleteThemeRequest = savedName
                        }
                    },
                    enabled = editedDraft != null || savedName != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.keyboard_theme_action_delete))
                }
            }

            if (editedDraft == null) {
                KeyboardThemeAssignmentSummaryRow(
                    target = activeTarget,
                    mode = activeAssignmentMode,
                    lightThemeName = themeDisplayName(activeThemeOptions, activeLightTheme),
                    darkThemeName = themeDisplayName(activeThemeOptions, activeDarkTheme),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onClick = { openSettingsChild(context, "theme_assignment", activeTarget.name) }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 6.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.keyboard_theme_preview_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (editedDraft != null) {
                    Text(
                        text = stringResource(R.string.keyboard_theme_editing_draft),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (previewPagerState.currentPage == 1) {
                KeyboardThemeSliderRow(
                    label = stringResource(R.string.keyboard_theme_preview_max_viewport),
                    linkId = "keyboard_theme.preview_viewport",
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
            val previewTheme = draftPreset ?: activePreviewTheme
            KeyboardThemePreviewCarousel(
                theme = previewTheme,
                currentPage = previewPagerState.currentPage,
                pageContent = {
                    HorizontalPager(
                        state = previewPagerState,
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                        when (page) {
                            0 -> HardwareKeyboardThemePreview(
                                theme = draftPreset ?: if (
                                    hardwareAssignmentMode == SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM
                                ) {
                                    if (systemIsDark) hardwareDarkTheme else hardwareLightTheme
                                } else {
                                    hardwareTheme
                                }
                            )
                            else -> VirtualKeyboardThemePreview(
                                theme = draftPreset ?: if (
                                    softwareAssignmentMode == SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM
                                ) {
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 6.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.keyboard_theme_customize_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (editedDraft != null) {
                    Text(
                        text = stringResource(R.string.keyboard_theme_required_missing, draftMissingFields.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (draftMissingFields.isEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
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
                if (editedDraft != null && draftPreset != null && customizationTab == 0) {
                    KeyboardThemeDraftColorsEditor(
                        theme = draftPreset,
                        populatedFields = editedDraft.populatedFields,
                        onFieldChanged = ::updateDraft
                    )
                } else if (editedDraft != null && draftPreset != null) {
                    KeyboardThemeDraftKeysEditor(
                        theme = draftPreset,
                        populatedFields = editedDraft.populatedFields,
                        onFieldChanged = ::updateDraft
                    )
                } else if (customizationTab == 0) {
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
            if (editedDraft != null) {
                Button(
                    onClick = ::useCompletedDraft,
                    enabled = draftComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(stringResource(R.string.keyboard_theme_save_use))
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
                if (editedDraft != null) {
                    val importedDraft = importedKeyboardThemeDraft(
                        draft = editedDraft,
                        importedTheme = importedTheme.toSettingsTheme()
                    )
                    SettingsManager.saveKeyboardThemeDraft(context, importedDraft)
                    themeDrafts = SettingsManager.getKeyboardThemeDrafts(context)
                    draftEditorName = importedDraft.name
                    draftListFocusName = importedDraft.name
                } else {
                    pendingImportedTheme = importedTheme
                }
            }
        )
    }
    if (showSaveAsDialog || pendingImportedTheme != null) {
        val importingTheme = pendingImportedTheme
        val duplicatingDraft = importingTheme == null && editedDraft != null
        KeyboardThemeNameDialog(
            initialName = importingTheme?.name ?: draftPreset?.name ?: activeTheme.name,
            existingNames = (savedThemes.map { it.preset.name } + themeDrafts.map { it.name }).toSet(),
            title = stringResource(
                when {
                    importingTheme != null -> R.string.keyboard_theme_import_name_title
                    duplicatingDraft -> R.string.keyboard_theme_duplicate_draft_title
                    else -> R.string.keyboard_theme_save_as_title
                }
            ),
            confirmLabel = stringResource(
                when {
                    importingTheme != null -> R.string.keyboard_theme_import_save
                    duplicatingDraft -> R.string.keyboard_theme_action_duplicate_draft
                    else -> R.string.keyboard_theme_action_save_as
                }
            ),
            onDismiss = {
                showSaveAsDialog = false
                pendingImportedTheme = null
            },
            onSave = { name ->
                showSaveAsDialog = false
                if (importingTheme != null) {
                    val normalizedName = name.trim()
                    if (
                        saveImportedKeyboardTheme(
                            context = context,
                            target = activeTarget,
                            name = normalizedName,
                            importedTheme = importingTheme.toSettingsTheme()
                        )
                    ) {
                        val savedPreset = importingTheme.copy(name = normalizedName)
                        savedThemes = SettingsManager.getSavedKeyboardThemes(context).map { it.toKeyboardThemeOption() }
                        if (activeTarget == SettingsManager.KeyboardThemeTarget.HARDWARE) {
                            hardwareSelectionKey = "saved:$normalizedName"
                            hardwarePreset = savedPreset
                            hardwareTheme = savedPreset
                        } else {
                            softwareSelectionKey = "saved:$normalizedName"
                            softwarePreset = savedPreset
                            softwareTheme = savedPreset
                        }
                    }
                    pendingImportedTheme = null
                } else if (editedDraft != null) {
                    val duplicate = editedDraft.copy(name = name.trim())
                    SettingsManager.saveKeyboardThemeDraft(context, duplicate)
                    themeDrafts = SettingsManager.getKeyboardThemeDrafts(context)
                    draftEditorName = duplicate.name
                    draftListFocusName = duplicate.name
                } else {
                    saveActiveThemeAs(name)
                }
            }
        )
    }
    if (showNewDraftDialog) {
        KeyboardThemeNewDraftDialog(
            existingNames = (savedThemes.map { it.preset.name } + themeDrafts.map { it.name }).toSet(),
            onDismiss = { showNewDraftDialog = false },
            onCreate = { name ->
                draftEditingGuard.value = true
                draftEditorName = name
                val draft = SettingsManager.KeyboardThemeDraft(
                    name = name,
                    theme = SettingsManager.defaultKeyboardTheme()
                        .toKeyboardThemePreset(name)
                        .withSoftwareKeyboardDefaults()
                        .toSettingsTheme()
                )
                SettingsManager.saveKeyboardThemeDraft(context, draft)
                themeDrafts = SettingsManager.getKeyboardThemeDrafts(context)
                showNewDraftDialog = false
            }
        )
    }
    deleteThemeRequest?.let { name ->
        AlertDialog(
            onDismissRequest = { deleteThemeRequest = null },
            title = { Text(stringResource(R.string.keyboard_theme_delete_title)) },
            text = { Text(stringResource(R.string.keyboard_theme_delete_confirmation, name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteThemeRequest = null
                        deleteSavedTheme(name)
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteThemeRequest = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    deleteDraftRequest?.let { name ->
        AlertDialog(
            onDismissRequest = { deleteDraftRequest = null },
            title = { Text(stringResource(R.string.keyboard_theme_delete_draft)) },
            text = { Text(stringResource(R.string.keyboard_theme_delete_draft_confirmation, name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDraftRequest = null
                        SettingsManager.deleteKeyboardThemeDraft(context, name)
                        themeDrafts = SettingsManager.getKeyboardThemeDrafts(context)
                        if (draftEditorName.equals(name, ignoreCase = true)) {
                            draftEditingGuard.value = false
                            draftEditorName = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDraftRequest = null }) {
                    Text(stringResource(R.string.cancel))
                }
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
            .height(88.dp)
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

@Composable
private fun KeyboardThemeDraftCard(
    draft: SettingsManager.KeyboardThemeDraft,
    editing: Boolean,
    onClick: () -> Unit
) {
    val preset = draft.theme.toKeyboardThemePreset(draft.name)
    Surface(
        modifier = Modifier
            .width(176.dp)
            .height(88.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeyboardThemeMiniPreview(preset)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = draft.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Text(
                    text = if (editing) {
                        stringResource(R.string.keyboard_theme_editing_draft)
                    } else {
                        stringResource(
                            R.string.keyboard_theme_required_missing,
                            (KEYBOARD_THEME_DRAFT_REQUIRED_FIELDS - draft.populatedFields).size
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun KeyboardThemeNewDraftDialog(
    existingNames: Set<String>,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val normalizedName = name.trim()
    val duplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keyboard_theme_new_theme)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.keyboard_theme_new_theme_description))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.keyboard_theme_name)) },
                    isError = duplicate,
                    supportingText = if (duplicate) {
                        ({ Text(stringResource(R.string.keyboard_theme_name_exists)) })
                    } else {
                        null
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = normalizedName.isNotEmpty() && !duplicate,
                onClick = { onCreate(normalizedName) }
            ) {
                Text(stringResource(R.string.keyboard_theme_create_draft))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
internal const val DRAFT_BACKGROUND = "background"
internal const val DRAFT_DIVIDER = "divider"
internal const val DRAFT_NORMAL_KEY = "normal_key"
internal const val DRAFT_SPECIAL_KEY = "special_key"
internal const val DRAFT_TEXT_ICONS = "text_icons"
internal const val DRAFT_ACCENT = "accent"
internal const val DRAFT_SUGGESTION = "suggestion"
internal const val DRAFT_STATUS_BUTTON = "status_button"
internal const val DRAFT_CURSOR_SWIPE = "cursor_swipe"
internal const val DRAFT_KEY_POPUP = "key_popup"
internal const val DRAFT_KEY_POPUP_SELECTED = "key_popup_selected"
internal const val DRAFT_LED_INACTIVE = "led_inactive"
internal const val DRAFT_LED_ACTIVE = "led_active"
internal const val DRAFT_LED_LOCKED = "led_locked"
internal const val DRAFT_KEY_ROUNDING = "key_rounding"
internal const val DRAFT_CHROME_ROUNDING = "chrome_rounding"
internal const val DRAFT_KEY_HEIGHT = "key_height"
internal const val DRAFT_NUMBER_ROW_HEIGHT = "number_row_height"
internal const val DRAFT_KEY_WIDTH = "key_width"
internal const val DRAFT_ROW_SPACING = "row_spacing"
internal const val DRAFT_SUGGESTIONS_HEIGHT = "suggestions_height"
internal const val DRAFT_VARIATIONS_HEIGHT = "variations_height"
internal const val DRAFT_SHOW_LEDS = "show_leds"
internal const val DRAFT_DISTRIBUTE_SPACING = "distribute_spacing"
internal const val DRAFT_ORTHOLINEAR = "ortholinear"
internal const val DRAFT_ATTACH_POPUP = "attach_popup"
internal const val DRAFT_POPUP_TAIL = "popup_tail"
internal const val DRAFT_PREVIEW_ON_HOLD = "preview_on_hold"
internal const val DRAFT_CHARACTER_PICKER = "character_picker"

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
                modifier = Modifier.fillMaxWidth().settingRow(if (target == SettingsManager.KeyboardThemeTarget.SOFTWARE) "keyboard_theme.software.assignment" else "keyboard_theme.hardware.assignment"),
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
                    linkId = if (target == SettingsManager.KeyboardThemeTarget.SOFTWARE) "keyboard_theme.software.light_theme" else "keyboard_theme.hardware.light_theme",
                    label = stringResource(R.string.keyboard_theme_light_mode_theme),
                    value = lightThemeName,
                    onClick = onPickLightTheme
                )
                KeyboardThemePickerRow(
                    linkId = if (target == SettingsManager.KeyboardThemeTarget.SOFTWARE) "keyboard_theme.software.dark_theme" else "keyboard_theme.hardware.dark_theme",
                    label = stringResource(R.string.keyboard_theme_dark_mode_theme),
                    value = darkThemeName,
                    onClick = onPickDarkTheme
                )
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth().settingRow(if (target == SettingsManager.KeyboardThemeTarget.SOFTWARE) "keyboard_theme.software.layout_overrides" else "keyboard_theme.hardware.layout_overrides")
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
            .settingRow(if (target == SettingsManager.KeyboardThemeTarget.SOFTWARE) "keyboard_theme.software.assignment" else "keyboard_theme.hardware.assignment", onClick),
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
    onClick: () -> Unit,
    linkId: String? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .settingRow(linkId, onClick),
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
        title = { Text(stringResource(R.string.keyboard_theme_export_title)) },
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
                Text(stringResource(R.string.debug_recorder_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun KeyboardThemeImportDialog(
    onDismiss: () -> Unit,
    onImport: (KeyboardThemePreset) -> Unit
) {
    val invalidMessage = stringResource(R.string.keyboard_theme_import_invalid)
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keyboard_theme_import_title)) },
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
                    label = { Text(stringResource(R.string.keyboard_theme_import_string)) }
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
                        error = invalidMessage
                    } else {
                        onImport(imported.toKeyboardThemePreset("Imported"))
                    }
                }
            ) {
                Text(stringResource(R.string.keyboard_theme_action_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun KeyboardThemeNameDialog(
    initialName: String,
    existingNames: Set<String>,
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName.takeUnless { it == "Custom" } ?: "") }
    val normalizedName = name.trim()
    val duplicate = existingNames.any { it.equals(normalizedName, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.keyboard_theme_name)) },
                isError = duplicate,
                supportingText = if (duplicate) {
                    ({ Text(stringResource(R.string.keyboard_theme_name_exists)) })
                } else {
                    null
                }
            )
        },
        confirmButton = {
            TextButton(
                enabled = normalizedName.isNotEmpty() && !duplicate,
                onClick = { onSave(normalizedName) }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private data class KeyboardThemeDraftColorItem(
    val field: String,
    val label: String,
    val color: Int,
    val onColorChanged: (Int) -> KeyboardThemePreset
)

@Composable
private fun KeyboardThemeDraftColorsEditor(
    theme: KeyboardThemePreset,
    populatedFields: Set<String>,
    onFieldChanged: (String, KeyboardThemePreset) -> Unit
) {
    val items = listOf(
        KeyboardThemeDraftColorItem(DRAFT_BACKGROUND, stringResource(R.string.keyboard_theme_background), theme.background) { theme.copy(background = it) },
        KeyboardThemeDraftColorItem(DRAFT_DIVIDER, stringResource(R.string.keyboard_theme_dividers), theme.divider) { theme.copy(divider = it) },
        KeyboardThemeDraftColorItem(DRAFT_NORMAL_KEY, stringResource(R.string.keyboard_theme_normal_keys), theme.normalKey) { theme.copy(normalKey = it) },
        KeyboardThemeDraftColorItem(DRAFT_SPECIAL_KEY, stringResource(R.string.keyboard_theme_special_keys), theme.specialKey) { theme.copy(specialKey = it) },
        KeyboardThemeDraftColorItem(DRAFT_TEXT_ICONS, stringResource(R.string.keyboard_theme_text_icons), theme.textAndIcons) { theme.copy(textAndIcons = it) },
        KeyboardThemeDraftColorItem(DRAFT_ACCENT, stringResource(R.string.keyboard_theme_accent), theme.accent) { theme.copy(accent = it) },
        KeyboardThemeDraftColorItem(DRAFT_SUGGESTION, stringResource(R.string.keyboard_theme_suggestions), theme.suggestion) { theme.copy(suggestion = it) },
        KeyboardThemeDraftColorItem(DRAFT_STATUS_BUTTON, stringResource(R.string.keyboard_theme_status_bar_buttons), theme.statusBarButton) { theme.copy(statusBarButton = it) },
        KeyboardThemeDraftColorItem(DRAFT_CURSOR_SWIPE, stringResource(R.string.keyboard_theme_cursor_swipe), theme.cursorSwipe) { theme.copy(cursorSwipe = it) },
        KeyboardThemeDraftColorItem(DRAFT_KEY_POPUP, stringResource(R.string.keyboard_theme_key_popup), theme.keyPopup) { theme.copy(keyPopup = it) },
        KeyboardThemeDraftColorItem(DRAFT_KEY_POPUP_SELECTED, stringResource(R.string.keyboard_theme_key_popup_selected), theme.keyPopupSelected) { theme.copy(keyPopupSelected = it) },
        KeyboardThemeDraftColorItem(DRAFT_LED_INACTIVE, stringResource(R.string.keyboard_theme_led_inactive), theme.ledInactive) { theme.copy(ledInactive = it) },
        KeyboardThemeDraftColorItem(DRAFT_LED_ACTIVE, stringResource(R.string.keyboard_theme_led_active), theme.ledActive) { theme.copy(ledActive = it) },
        KeyboardThemeDraftColorItem(DRAFT_LED_LOCKED, stringResource(R.string.keyboard_theme_led_locked), theme.ledLocked) { theme.copy(ledLocked = it) }
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { item ->
                    KeyboardThemeDraftColorRow(
                        label = item.label,
                        color = item.color,
                        populated = item.field in populatedFields,
                        onColorChanged = { color -> onFieldChanged(item.field, item.onColorChanged(color)) },
                        modifier = Modifier.weight(1f),
                        linkId = if (item.field == DRAFT_LED_INACTIVE) {
                            "keyboard_theme.software.led_colors"
                        } else {
                            null
                        }
                    )
                }
                if (rowItems.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KeyboardThemeDraftKeysEditor(
    theme: KeyboardThemePreset,
    populatedFields: Set<String>,
    onFieldChanged: (String, KeyboardThemePreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyboardThemeDraftSliderRow(DRAFT_KEY_ROUNDING, stringResource(R.string.keyboard_theme_key_rounding), theme.keyCornerRadiusRatio, 0f..0.35f, populatedFields, linkId = "keyboard_theme.software.key_rounding") { onFieldChanged(DRAFT_KEY_ROUNDING, theme.copy(keyCornerRadiusRatio = it)) }
        KeyboardThemeDraftSliderRow(DRAFT_CHROME_ROUNDING, stringResource(R.string.keyboard_theme_chrome_rounding), theme.chromeCornerRadiusRatio, 0f..0.35f, populatedFields, linkId = "keyboard_theme.software.chrome_rounding") { onFieldChanged(DRAFT_CHROME_ROUNDING, theme.copy(chromeCornerRadiusRatio = it)) }
        KeyboardThemeDraftSliderRow(DRAFT_KEY_HEIGHT, stringResource(R.string.keyboard_theme_key_height), theme.keyHeightScale, 0.72f..1.9f, populatedFields, linkId = "keyboard_theme.software.key_height") { onFieldChanged(DRAFT_KEY_HEIGHT, theme.copy(keyHeightScale = it)) }
        KeyboardThemeDraftSliderRow(DRAFT_NUMBER_ROW_HEIGHT, stringResource(R.string.keyboard_theme_number_row_height), theme.numberRowHeightScale, 0.45f..1.4f, populatedFields, linkId = "keyboard_theme.software.number_row_height") { onFieldChanged(DRAFT_NUMBER_ROW_HEIGHT, theme.copy(numberRowHeightScale = it)) }
        KeyboardThemeDraftSliderRow(DRAFT_KEY_WIDTH, stringResource(R.string.keyboard_theme_key_width), theme.keyWidthScale, 0.72f..1.12f, populatedFields, linkId = "keyboard_theme.software.key_width") { onFieldChanged(DRAFT_KEY_WIDTH, theme.copy(keyWidthScale = it)) }
        KeyboardThemeDraftSliderRow(DRAFT_ROW_SPACING, stringResource(R.string.keyboard_theme_row_spacing), theme.rowGapScale, 0f..2f, populatedFields, linkId = "keyboard_theme.software.row_spacing") { onFieldChanged(DRAFT_ROW_SPACING, theme.copy(rowGapScale = it)) }
        KeyboardThemeDraftSliderRow(DRAFT_SUGGESTIONS_HEIGHT, stringResource(R.string.keyboard_theme_suggestions_height), theme.suggestionsHeightScale, 0.65f..1.6f, populatedFields, linkId = "keyboard_theme.software.suggestions_height") { onFieldChanged(DRAFT_SUGGESTIONS_HEIGHT, theme.copy(suggestionsHeightScale = it)) }
        KeyboardThemeDraftSliderRow(DRAFT_VARIATIONS_HEIGHT, stringResource(R.string.keyboard_theme_variations_height), theme.variationsHeightScale, 0.65f..1.6f, populatedFields, linkId = "keyboard_theme.software.variations_height") { onFieldChanged(DRAFT_VARIATIONS_HEIGHT, theme.copy(variationsHeightScale = it)) }
        KeyboardThemeDraftSwitchRow(DRAFT_SHOW_LEDS, stringResource(R.string.keyboard_theme_show_leds), theme.showLeds, populatedFields, linkId = SettingLinkIds.KEYBOARD_THEME_TOGGLE_SHOW_LEDS) { onFieldChanged(DRAFT_SHOW_LEDS, theme.copy(showLeds = it)) }
        KeyboardThemeDraftSwitchRow(DRAFT_DISTRIBUTE_SPACING, stringResource(R.string.keyboard_theme_distribute_spacing), theme.distributeHorizontalSpacing, populatedFields, linkId = SettingLinkIds.KEYBOARD_THEME_TOGGLE_DISTRIBUTE_SPACING) { onFieldChanged(DRAFT_DISTRIBUTE_SPACING, theme.copy(distributeHorizontalSpacing = it)) }
        KeyboardThemeDraftSwitchRow(DRAFT_ORTHOLINEAR, stringResource(R.string.keyboard_theme_ortholinear), theme.ortholinear, populatedFields, linkId = SettingLinkIds.KEYBOARD_THEME_TOGGLE_ORTHOLINEAR) { onFieldChanged(DRAFT_ORTHOLINEAR, theme.copy(ortholinear = it)) }
        KeyboardThemeDraftSwitchRow(DRAFT_ATTACH_POPUP, stringResource(R.string.keyboard_theme_attach_popup), theme.keyPopupAttached, populatedFields, linkId = SettingLinkIds.KEYBOARD_THEME_TOGGLE_ATTACH_POPUP) { onFieldChanged(DRAFT_ATTACH_POPUP, theme.copy(keyPopupAttached = it)) }
        KeyboardThemeDraftSwitchRow(DRAFT_POPUP_TAIL, stringResource(R.string.keyboard_theme_popup_tail), theme.keyPopupTailEnabled, populatedFields, linkId = SettingLinkIds.KEYBOARD_THEME_TOGGLE_POPUP_TAIL) { onFieldChanged(DRAFT_POPUP_TAIL, theme.copy(keyPopupTailEnabled = it)) }
        KeyboardThemeDraftSwitchRow(DRAFT_PREVIEW_ON_HOLD, stringResource(R.string.keyboard_theme_preview_on_hold), theme.keyPreviewAfterLongPress, populatedFields, linkId = SettingLinkIds.KEYBOARD_THEME_TOGGLE_PREVIEW_ON_HOLD) { onFieldChanged(DRAFT_PREVIEW_ON_HOLD, theme.copy(keyPreviewAfterLongPress = it)) }
        KeyboardThemeDraftSwitchRow(DRAFT_CHARACTER_PICKER, stringResource(R.string.keyboard_theme_character_picker), theme.keyAlternatesPopupEnabled, populatedFields, linkId = SettingLinkIds.KEYBOARD_THEME_TOGGLE_CHARACTER_PICKER) { onFieldChanged(DRAFT_CHARACTER_PICKER, theme.copy(keyAlternatesPopupEnabled = it)) }
    }
}

@Composable
private fun KeyboardThemeDraftColorRow(
    label: String,
    color: Int,
    populated: Boolean,
    onColorChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    linkId: String? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .settingRow(linkId),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        border = if (populated) null else BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                KeyboardThemeSwatchButton(
                    color = color,
                    onColorChanged = onColorChanged,
                    showColor = populated
                )
                Text(
                    text = if (populated) color.toHexColorLabel() else stringResource(R.string.keyboard_theme_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (populated) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun KeyboardThemeDraftSliderRow(
    field: String,
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    populatedFields: Set<String>,
    linkId: String? = null,
    onValueChanged: (Float) -> Unit
) {
    if (field in populatedFields) {
        KeyboardThemeSliderRow(label, value, value, valueRange, linkId = linkId, onValueChanged = onValueChanged)
    } else {
        KeyboardThemeRequiredRow(label = label, linkId = linkId) {
            TextButton(onClick = { onValueChanged(value) }) {
                Text(stringResource(R.string.keyboard_theme_set_value))
            }
        }
    }
}

@Composable
private fun KeyboardThemeDraftSwitchRow(
    field: String,
    label: String,
    checked: Boolean,
    populatedFields: Set<String>,
    linkId: String? = null,
    onCheckedChanged: (Boolean) -> Unit
) {
    if (field in populatedFields) {
        KeyboardThemeSwitchRow(label, checked, checked, onCheckedChanged, linkId)
    } else {
        KeyboardThemeRequiredRow(label = label, linkId = linkId) {
            TextButton(onClick = { onCheckedChanged(false) }) { Text(stringResource(R.string.off)) }
            TextButton(onClick = { onCheckedChanged(true) }) { Text(stringResource(R.string.on)) }
        }
    }
}

@Composable
private fun KeyboardThemeRequiredRow(
    label: String,
    linkId: String? = null,
    actions: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().settingRow(linkId),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(R.string.keyboard_theme_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            actions()
        }
    }
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
                label = stringResource(R.string.keyboard_theme_suggestions),
                color = theme.suggestion,
                presetColor = preset.suggestion,
                onColorChanged = { onThemeChanged(theme.copy(suggestion = it)) }
            )
        )
        add(
            KeyboardThemeColorEditorItem(
                label = stringResource(R.string.keyboard_theme_status_bar_buttons),
                color = theme.statusBarButton,
                presetColor = preset.statusBarButton,
                onColorChanged = { onThemeChanged(theme.copy(statusBarButton = it)) }
            )
        )
        add(
            KeyboardThemeColorEditorItem(
                label = stringResource(R.string.keyboard_theme_cursor_swipe),
                color = theme.cursorSwipe,
                presetColor = preset.cursorSwipe,
                onColorChanged = { onThemeChanged(theme.copy(cursorSwipe = it)) }
            )
        )
        if (isSoftware) {
            add(
                KeyboardThemeColorEditorItem(
                    label = stringResource(R.string.keyboard_theme_key_popup),
                    color = theme.keyPopup,
                    presetColor = preset.keyPopup,
                    onColorChanged = { onThemeChanged(theme.copy(keyPopup = it)) }
                )
            )
            add(
                KeyboardThemeColorEditorItem(
                    label = stringResource(R.string.keyboard_theme_key_popup_selected),
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
                onColorChanged = { onThemeChanged(theme.copy(ledInactive = it)) },
                linkId = if (LocalSettingHighlightId.current == SettingLinkIds.KEYBOARD_THEME_LED_COLORS) {
                    SettingLinkIds.KEYBOARD_THEME_LED_COLORS
                } else {
                    if (isSoftware) "keyboard_theme.software.led_colors" else "keyboard_theme.hardware.led_colors"
                }
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
                        modifier = Modifier.weight(1f),
                        linkId = item.linkId
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
    val onColorChanged: (Int) -> Unit,
    val linkId: String? = null
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
                label = stringResource(R.string.keyboard_theme_key_rounding),
                linkId = "keyboard_theme.software.key_rounding",
                value = theme.keyCornerRadiusRatio,
                presetValue = preset.keyCornerRadiusRatio,
                valueRange = 0f..0.35f,
                onValueChanged = { onThemeChanged(theme.copy(keyCornerRadiusRatio = it)) }
            )
        }
        KeyboardThemeSliderRow(
            label = stringResource(R.string.keyboard_theme_chrome_rounding),
            linkId = if (isSoftware) "keyboard_theme.software.chrome_rounding" else "keyboard_theme.hardware.chrome_rounding",
            value = theme.chromeCornerRadiusRatio,
            presetValue = preset.chromeCornerRadiusRatio,
            valueRange = 0f..0.35f,
            onValueChanged = { onThemeChanged(theme.copy(chromeCornerRadiusRatio = it)) }
        )
        KeyboardThemeSliderRow(
            label = stringResource(R.string.keyboard_theme_suggestions_height),
            linkId = if (isSoftware) "keyboard_theme.software.suggestions_height" else "keyboard_theme.hardware.suggestions_height",
            value = theme.suggestionsHeightScale,
            presetValue = preset.suggestionsHeightScale,
            valueRange = 0.65f..1.6f,
            onValueChanged = { onThemeChanged(theme.copy(suggestionsHeightScale = it)) }
        )
        KeyboardThemeSliderRow(
            label = stringResource(R.string.keyboard_theme_variations_height),
            linkId = if (isSoftware) "keyboard_theme.software.variations_height" else "keyboard_theme.hardware.variations_height",
            value = theme.variationsHeightScale,
            presetValue = preset.variationsHeightScale,
            valueRange = 0.65f..1.6f,
            onValueChanged = { onThemeChanged(theme.copy(variationsHeightScale = it)) }
        )
        if (isSoftware) {
            KeyboardThemeSwitchRow(
                label = stringResource(R.string.keyboard_theme_show_leds),
                checked = theme.showLeds,
                presetChecked = preset.showLeds,
                onCheckedChanged = { onThemeChanged(theme.copy(showLeds = it)) },
                linkId = SettingLinkIds.KEYBOARD_THEME_TOGGLE_SHOW_LEDS
            )
            KeyboardThemeSliderRow(
                label = stringResource(R.string.keyboard_theme_key_height),
                linkId = "keyboard_theme.software.key_height",
                value = theme.keyHeightScale,
                presetValue = preset.keyHeightScale,
                valueRange = 0.72f..1.9f,
                onValueChanged = { onThemeChanged(theme.copy(keyHeightScale = it)) }
            )
            KeyboardThemeSliderRow(
                label = stringResource(R.string.keyboard_theme_number_row_height),
                linkId = "keyboard_theme.software.number_row_height",
                value = theme.numberRowHeightScale,
                presetValue = preset.numberRowHeightScale,
                valueRange = 0.45f..1.4f,
                onValueChanged = { onThemeChanged(theme.copy(numberRowHeightScale = it)) }
            )
            KeyboardThemeSliderRow(
                label = stringResource(R.string.keyboard_theme_key_width),
                linkId = "keyboard_theme.software.key_width",
                value = theme.keyWidthScale,
                presetValue = preset.keyWidthScale,
                valueRange = 0.72f..1.12f,
                onValueChanged = { onThemeChanged(theme.copy(keyWidthScale = it)) }
            )
            KeyboardThemeSliderRow(
                label = stringResource(R.string.keyboard_theme_row_spacing),
                linkId = "keyboard_theme.software.row_spacing",
                value = theme.rowGapScale,
                presetValue = preset.rowGapScale,
                valueRange = 0f..2f,
                onValueChanged = { onThemeChanged(theme.copy(rowGapScale = it)) }
            )
            KeyboardThemeSwitchRow(
                label = stringResource(R.string.keyboard_theme_distribute_spacing),
                checked = theme.distributeHorizontalSpacing,
                presetChecked = preset.distributeHorizontalSpacing,
                onCheckedChanged = { onThemeChanged(theme.copy(distributeHorizontalSpacing = it)) },
                linkId = SettingLinkIds.KEYBOARD_THEME_TOGGLE_DISTRIBUTE_SPACING
            )
            KeyboardThemeSwitchRow(
                label = stringResource(R.string.keyboard_theme_ortholinear),
                checked = theme.ortholinear,
                presetChecked = preset.ortholinear,
                onCheckedChanged = { onThemeChanged(theme.copy(ortholinear = it)) },
                linkId = SettingLinkIds.KEYBOARD_THEME_TOGGLE_ORTHOLINEAR
            )
            KeyboardThemeSwitchRow(
                label = stringResource(R.string.keyboard_theme_attach_popup),
                checked = theme.keyPopupAttached,
                presetChecked = preset.keyPopupAttached,
                onCheckedChanged = { onThemeChanged(theme.copy(keyPopupAttached = it)) },
                linkId = SettingLinkIds.KEYBOARD_THEME_TOGGLE_ATTACH_POPUP
            )
            KeyboardThemeSwitchRow(
                label = stringResource(R.string.keyboard_theme_popup_tail),
                checked = theme.keyPopupTailEnabled,
                presetChecked = preset.keyPopupTailEnabled,
                onCheckedChanged = { onThemeChanged(theme.copy(keyPopupTailEnabled = it)) },
                linkId = SettingLinkIds.KEYBOARD_THEME_TOGGLE_POPUP_TAIL
            )
            KeyboardThemeSwitchRow(
                label = stringResource(R.string.keyboard_theme_preview_on_hold),
                checked = theme.keyPreviewAfterLongPress,
                presetChecked = preset.keyPreviewAfterLongPress,
                onCheckedChanged = { onThemeChanged(theme.copy(keyPreviewAfterLongPress = it)) },
                linkId = SettingLinkIds.KEYBOARD_THEME_TOGGLE_PREVIEW_ON_HOLD
            )
            KeyboardThemeSwitchRow(
                label = stringResource(R.string.keyboard_theme_character_picker),
                checked = theme.keyAlternatesPopupEnabled,
                presetChecked = preset.keyAlternatesPopupEnabled,
                onCheckedChanged = { onThemeChanged(theme.copy(keyAlternatesPopupEnabled = it)) },
                linkId = SettingLinkIds.KEYBOARD_THEME_TOGGLE_CHARACTER_PICKER
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
    modifier: Modifier = Modifier,
    linkId: String? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth().settingRow(linkId),
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
    linkId: String? = null,
    onValueChanged: (Float) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth().settingRow(linkId),
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
    onCheckedChanged: (Boolean) -> Unit,
    linkId: String? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .settingRow(linkId),
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
    onColorChanged: (Int) -> Unit,
    showColor: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var pickerColor by remember(color) { mutableStateOf(color) }
    val shape = MaterialTheme.shapes.small
    Box {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (showColor) Color(color) else MaterialTheme.colorScheme.surface,
                    shape
                )
                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f), shape)
                .clickable {
                    pickerColor = color
                    expanded = true
                },
            contentAlignment = Alignment.Center
        ) {
            if (!showColor) {
                Text("+", color = MaterialTheme.colorScheme.error)
            }
        }
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
    var hexValue by remember(initialColor) { mutableStateOf(initialColor.toHexColorLabel()) }

    fun updateColor(newHue: Float = hue, newSaturation: Float = saturation, newValue: Float = value) {
        hue = newHue
        saturation = newSaturation
        value = newValue
        color = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
        hexValue = color.toHexColorLabel()
        onPreviewColorChanged(color)
    }

    fun updateColorFromHex(newColor: Int) {
        color = newColor
        val newHsv = FloatArray(3).also { AndroidColor.colorToHSV(newColor, it) }
        hue = newHsv[0]
        saturation = newHsv[1]
        value = newHsv[2]
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
                    .heightIn(max = 380.dp)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.9f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.keyboard_theme_presets),
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
                    OutlinedTextField(
                        value = hexValue,
                        onValueChange = { input ->
                            if (input.length <= 7) {
                                hexValue = input.uppercase(Locale.ROOT)
                                parseKeyboardThemeHexColor(input)?.let(::updateColorFromHex)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.keyboard_theme_hex_color)) },
                        isError = hexValue.isNotEmpty() && parseKeyboardThemeHexColor(hexValue) == null
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            enabled = parseKeyboardThemeHexColor(hexValue) != null,
                            onClick = { onColorSelected(color) }
                        ) {
                            Text(stringResource(R.string.clicks_apply))
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

internal fun parseKeyboardThemeHexColor(value: String): Int? {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6 || hex.any { it.digitToIntOrNull(16) == null }) return null
    return (0xFF000000L or hex.toLong(16)).toInt()
}
