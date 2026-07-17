package it.palsoftware.pastiera

import it.palsoftware.pastiera.inputmethod.aospkeyboard.AospKeyboardView
import it.palsoftware.pastiera.inputmethod.ui.KeyboardThemeColors

internal data class KeyboardThemePreset(
    val name: String,
    val background: Int,
    val divider: Int,
    val normalKey: Int,
    val specialKey: Int,
    val textAndIcons: Int,
    val ledInactive: Int,
    val ledActive: Int,
    val ledLocked: Int,
    val accent: Int,
    val cursorSwipe: Int = accent,
    val keyPopup: Int = specialKey,
    val keyPopupSelected: Int = accent,
    val suggestion: Int = normalKey,
    val statusBarButton: Int = specialKey,
    val keyCornerRadiusRatio: Float = 0.08f,
    val chromeCornerRadiusRatio: Float = 0.08f,
    val keyHeightScale: Float = 1f,
    val numberRowHeightScale: Float = 0.8f,
    val keyWidthScale: Float = 1f,
    val rowGapScale: Float = 0f,
    val distributeHorizontalSpacing: Boolean = true,
    val ortholinear: Boolean = false,
    val showLeds: Boolean = true,
    val suggestionsHeightScale: Float = 1f,
    val variationsHeightScale: Float = 1f,
    val keepsSoftwareGeometry: Boolean = false,
    val frostIntensity: Float = 0f,
    val keyPopupStyle: String = SettingsManager.KEYBOARD_THEME_POPUP_STYLE_FLOATING,
    val keyPopupAttached: Boolean = true,
    val keyPopupTailEnabled: Boolean = true,
    val keyPreviewAfterLongPress: Boolean = false,
    val keyAlternatesPopupEnabled: Boolean = true,
    val modifierIndicatorStripScale: Float = 1f,
    val keyTap: Int = defaultKeyboardThemeKeyTapColor(normalKey, accent)
)

internal fun defaultKeyboardThemeKeyTapColor(normalKey: Int, accent: Int): Int {
    val ratio = 0.28f.coerceIn(0f, 1f)
    val inverse = 1f - ratio
    val alpha = ((normalKey ushr 24) * inverse + (accent ushr 24) * ratio).toInt()
    val red = (((normalKey ushr 16) and 0xFF) * inverse + ((accent ushr 16) and 0xFF) * ratio).toInt()
    val green = (((normalKey ushr 8) and 0xFF) * inverse + ((accent ushr 8) and 0xFF) * ratio).toInt()
    val blue = ((normalKey and 0xFF) * inverse + (accent and 0xFF) * ratio).toInt()
    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}

internal data class KeyboardThemeOption(
    val key: String,
    val preset: KeyboardThemePreset,
    val resetPreset: KeyboardThemePreset,
    val userSaved: Boolean = false
)

internal const val SOFTWARE_THEME_DEFAULT_KEY_CORNER_RADIUS = 0.19f
internal const val SOFTWARE_THEME_DEFAULT_CHROME_CORNER_RADIUS = 0.20f
internal const val SOFTWARE_THEME_DEFAULT_KEY_HEIGHT = 1.5489256f
internal const val SOFTWARE_THEME_DEFAULT_NUMBER_ROW_HEIGHT = 0.8f
internal const val SOFTWARE_THEME_DEFAULT_ROW_GAP = 0.47933885f
internal const val SOFTWARE_THEME_DEFAULT_SUGGESTIONS_HEIGHT = 0.8982954f
internal const val SOFTWARE_THEME_DEFAULT_VARIATIONS_HEIGHT = 0.95914257f

internal fun KeyboardThemePreset.withSoftwareKeyboardDefaults(): KeyboardThemePreset =
    if (keepsSoftwareGeometry) {
        this
    } else {
        copy(
            keyCornerRadiusRatio = SOFTWARE_THEME_DEFAULT_KEY_CORNER_RADIUS,
            chromeCornerRadiusRatio = SOFTWARE_THEME_DEFAULT_CHROME_CORNER_RADIUS,
            keyHeightScale = SOFTWARE_THEME_DEFAULT_KEY_HEIGHT,
            numberRowHeightScale = SOFTWARE_THEME_DEFAULT_NUMBER_ROW_HEIGHT,
            rowGapScale = SOFTWARE_THEME_DEFAULT_ROW_GAP,
            ortholinear = true,
            showLeds = false,
            suggestionsHeightScale = SOFTWARE_THEME_DEFAULT_SUGGESTIONS_HEIGHT,
            variationsHeightScale = SOFTWARE_THEME_DEFAULT_VARIATIONS_HEIGHT
        )
    }

internal fun KeyboardThemePreset.toAospThemeOverride(): AospKeyboardView.ThemeOverride =
    AospKeyboardView.ThemeOverride(
        background = background,
        divider = divider,
        normalKey = normalKey,
        specialKey = specialKey,
        textAndIcons = textAndIcons,
        ledInactive = ledInactive,
        ledActive = ledActive,
        ledLocked = ledLocked,
        accent = keyTap,
        keyTap = keyTap,
        keyPopup = keyPopup,
        keyPopupSelected = keyPopupSelected,
        keyCornerRadiusRatio = keyCornerRadiusRatio,
        keyHeightScale = keyHeightScale,
        numberRowHeightScale = numberRowHeightScale,
        keyWidthScale = keyWidthScale,
        rowGapScale = rowGapScale,
        distributeHorizontalSpacing = distributeHorizontalSpacing,
        ortholinear = ortholinear,
        keyPopupStyle = keyPopupStyle,
        keyPopupAttached = keyPopupAttached,
        keyPopupTailEnabled = keyPopupTailEnabled,
        keyPreviewAfterLongPress = keyPreviewAfterLongPress,
        keyAlternatesPopupEnabled = keyAlternatesPopupEnabled
    )

internal fun KeyboardThemePreset.toKeyboardThemeColors(): KeyboardThemeColors =
    KeyboardThemeColors(
        background = background,
        divider = divider,
        normalKey = normalKey,
        specialKey = specialKey,
        textAndIcons = textAndIcons,
        ledInactive = ledInactive,
        ledActive = ledActive,
        ledLocked = ledLocked,
        accent = keyTap,
        keyTap = keyTap,
        cursorSwipe = cursorSwipe,
        keyPopup = keyPopup,
        keyPopupSelected = keyPopupSelected,
        suggestion = suggestion,
        statusBarButton = statusBarButton,
        keyCornerRadiusRatio = keyCornerRadiusRatio,
        chromeCornerRadiusRatio = chromeCornerRadiusRatio,
        suggestionsHeightScale = suggestionsHeightScale,
        variationsHeightScale = variationsHeightScale,
        frostIntensity = frostIntensity,
        modifierIndicatorStripScale = modifierIndicatorStripScale
    )

internal fun KeyboardThemePreset.toSettingsTheme(): SettingsManager.KeyboardThemeSettings =
    SettingsManager.KeyboardThemeSettings(
        background = background,
        divider = divider,
        normalKey = normalKey,
        specialKey = specialKey,
        textAndIcons = textAndIcons,
        ledInactive = ledInactive,
        ledActive = ledActive,
        ledLocked = ledLocked,
        accent = keyTap,
        keyTap = keyTap,
        cursorSwipe = cursorSwipe,
        keyPopup = keyPopup,
        keyPopupSelected = keyPopupSelected,
        suggestion = suggestion,
        statusBarButton = statusBarButton,
        keyCornerRadiusRatio = keyCornerRadiusRatio,
        chromeCornerRadiusRatio = chromeCornerRadiusRatio,
        keyHeightScale = keyHeightScale,
        numberRowHeightScale = numberRowHeightScale,
        keyWidthScale = keyWidthScale,
        rowGapScale = rowGapScale,
        distributeHorizontalSpacing = distributeHorizontalSpacing,
        ortholinear = ortholinear,
        showLeds = showLeds,
        suggestionsHeightScale = suggestionsHeightScale,
        variationsHeightScale = variationsHeightScale,
        frostIntensity = frostIntensity,
        modifierIndicatorStripScale = modifierIndicatorStripScale,
        keyPopupStyle = keyPopupStyle,
        keyPopupAttached = keyPopupAttached,
        keyPopupTailEnabled = keyPopupTailEnabled,
        keyPreviewAfterLongPress = keyPreviewAfterLongPress,
        keyAlternatesPopupEnabled = keyAlternatesPopupEnabled
    )

internal fun SettingsManager.KeyboardThemeSettings.toKeyboardThemePreset(name: String): KeyboardThemePreset =
    KeyboardThemePreset(
        name = name,
        background = background,
        divider = divider,
        normalKey = normalKey,
        specialKey = specialKey,
        textAndIcons = textAndIcons,
        ledInactive = ledInactive,
        ledActive = ledActive,
        ledLocked = ledLocked,
        accent = accent,
        keyTap = keyTap,
        cursorSwipe = cursorSwipe,
        keyPopup = keyPopup,
        keyPopupSelected = keyPopupSelected,
        suggestion = suggestion,
        statusBarButton = statusBarButton,
        keyCornerRadiusRatio = keyCornerRadiusRatio,
        chromeCornerRadiusRatio = chromeCornerRadiusRatio,
        keyHeightScale = keyHeightScale,
        numberRowHeightScale = numberRowHeightScale,
        keyWidthScale = keyWidthScale,
        rowGapScale = rowGapScale,
        distributeHorizontalSpacing = distributeHorizontalSpacing,
        ortholinear = ortholinear,
        showLeds = showLeds,
        suggestionsHeightScale = suggestionsHeightScale,
        variationsHeightScale = variationsHeightScale,
        frostIntensity = frostIntensity,
        modifierIndicatorStripScale = modifierIndicatorStripScale,
        keyPopupStyle = keyPopupStyle,
        keyPopupAttached = keyPopupAttached,
        keyPopupTailEnabled = keyPopupTailEnabled,
        keyPreviewAfterLongPress = keyPreviewAfterLongPress,
        keyAlternatesPopupEnabled = keyAlternatesPopupEnabled
    )

internal fun SettingsManager.NamedKeyboardTheme.toKeyboardThemeOption(): KeyboardThemeOption {
    val preset = theme.toKeyboardThemePreset(name)
    return KeyboardThemeOption(
        key = "saved:$name",
        preset = preset,
        resetPreset = preset,
        userSaved = true
    )
}

internal fun keyboardThemeSwatches(): List<Int> = keyboardThemePresets()
    .flatMap { preset ->
        listOf(
            preset.background,
            preset.divider,
            preset.normalKey,
            preset.specialKey,
            preset.textAndIcons,
            preset.ledInactive,
            preset.ledActive,
            preset.ledLocked,
            preset.keyTap,
            preset.cursorSwipe,
            preset.keyPopup,
            preset.keyPopupSelected,
            preset.suggestion,
            preset.statusBarButton
        )
    }
    .distinct()

internal fun keyboardThemePresets(): List<KeyboardThemePreset> = listOf(
    KeyboardThemePreset("Pastiera Dark", 0xFF000000.toInt(), 0xFF2C3136.toInt(), 0xFF15191D.toInt(), 0xFF2B3138.toInt(), 0xFFEFEFEF.toInt(), 0xFF303030.toInt(), 0xFF6496FF.toInt(), 0xFFF76300.toInt(), 0xFF6496FF.toInt(), keyCornerRadiusRatio = 0.10f, chromeCornerRadiusRatio = 0.10f),
    KeyboardThemePreset("Pastiera Light", 0xFFF8FAFC.toInt(), 0xFFC7CDD4.toInt(), 0xFFFFFFFF.toInt(), 0xFFE0E6EE.toInt(), 0xFF171A1F.toInt(), 0xFFD1D5DB.toInt(), 0xFF276EF1.toInt(), 0xFFD65A00.toInt(), 0xFF276EF1.toInt(), keyCornerRadiusRatio = 0.10f, chromeCornerRadiusRatio = 0.10f),
    KeyboardThemePreset("Frosted Glass", 0x730B1018, 0x8CFFFFFF.toInt(), 0x2EFFFFFF, 0x42FFFFFF, 0xFFFFFFFF.toInt(), 0x4DFFFFFF, 0xFF9FD0FF.toInt(), 0xFFFFB45C.toInt(), 0xFF9FD0FF.toInt(), 0xFF9FD0FF.toInt(), 0x73FFFFFF, 0xFF9FD0FF.toInt(), 0x24FFFFFF, 0x33FFFFFF, 0.24f, 0.34f, 1.2588017f, 0.971126f, 0.94148767f, 1.05f, true, false, true, 0.92f, 0.9f, true, frostIntensity = 1.15f, keyTap = 0x669FD0FF),
    KeyboardThemePreset("Cloud Tap", 0xFFE1E3E7.toInt(), 0xFFD4D7DD.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF050505.toInt(), 0xFFC2C6CE.toInt(), 0xFF0A84FF.toInt(), 0xFFFF9500.toInt(), 0xFF0A84FF.toInt(), 0xFF0A84FF.toInt(), 0xFFFFFFFF.toInt(), 0xFF0A84FF.toInt(), 0xFFDDE0E5.toInt(), 0xFFFFFFFF.toInt(), 0.18186983f, 0.35f, 1.2588017f, 0.971126f, 0.94148767f, 1.05f, true, true, false, 0.9f, 0.88f, true),
    KeyboardThemePreset("Moon Tap", 0xFF111111.toInt(), 0xFF303030.toInt(), 0xFF3A3A3C.toInt(), 0xFF3A3A3C.toInt(), 0xFFF8F8F8.toInt(), 0xFF303030.toInt(), 0xFF409CFF.toInt(), 0xFFFF9F0A.toInt(), 0xFF409CFF.toInt(), 0xFF409CFF.toInt(), 0xFF3A3A3C.toInt(), 0xFF409CFF.toInt(), 0xFF171717.toInt(), 0xFF1C1C1E.toInt(), 0.18186983f, 0.35f, 1.2588017f, 0.971126f, 0.94148767f, 1.05f, true, true, false, 0.9f, 0.88f, true),
    KeyboardThemePreset("Classic Cloud", 0xFFCCD2DC.toInt(), 0xFF9EA5AF.toInt(), 0xFFFFFFFF.toInt(), 0xFFAFB6C2.toInt(), 0xFF000000.toInt(), 0xFFAEB5C0.toInt(), 0xFF007AFF.toInt(), 0xFFFF9500.toInt(), 0xFF007AFF.toInt(), 0xFF007AFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF007AFF.toInt(), 0xFFCCD2DC.toInt(), 0xFFAFB6C2.toInt(), 0.118f, 0.09f, 1.2588017f, 0.971126f, 0.94148767f, 1.05f, true, false, false, 0.9f, 0.88f, true, keyPopupStyle = SettingsManager.KEYBOARD_THEME_POPUP_STYLE_CLASSIC),
    KeyboardThemePreset("Classic Midnight", 0xFF1C1C1E.toInt(), 0xFF4A4A4D.toInt(), 0xFF3A3A3C.toInt(), 0xFF2C2C2E.toInt(), 0xFFFFFFFF.toInt(), 0xFF404044.toInt(), 0xFF0A84FF.toInt(), 0xFFFF9F0A.toInt(), 0xFF0A84FF.toInt(), 0xFF0A84FF.toInt(), 0xFF3A3A3C.toInt(), 0xFF0A84FF.toInt(), 0xFF202124.toInt(), 0xFF2C2C2E.toInt(), 0.118f, 0.09f, 1.2588017f, 0.971126f, 0.94148767f, 1.05f, true, false, false, 0.9f, 0.88f, true, keyPopupStyle = SettingsManager.KEYBOARD_THEME_POPUP_STYLE_CLASSIC),
    KeyboardThemePreset("ePaper", 0xFFF2F2F2.toInt(), 0xFFB8B8B8.toInt(), 0xFFFAFAFA.toInt(), 0xFFDDDDDD.toInt(), 0xFF111111.toInt(), 0xFFB0B0B0.toInt(), 0xFF555555.toInt(), 0xFF111111.toInt(), 0xFF3F8C96.toInt()),
    KeyboardThemePreset("High Contrast", 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF0D0D0D.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF555555.toInt(), 0xFF00E5FF.toInt(), 0xFFFFEA00.toInt(), 0xFFFFEA00.toInt()),
    KeyboardThemePreset("Warm", 0xFF241F1A.toInt(), 0xFF6F6255.toInt(), 0xFF352E27.toInt(), 0xFF5B4734.toInt(), 0xFFFFF1DD.toInt(), 0xFF665A4E.toInt(), 0xFFE0B05D.toInt(), 0xFFE06A4B.toInt(), 0xFFE0B05D.toInt()),
    KeyboardThemePreset("Solarized Dark", 0xFF002B36.toInt(), 0xFF586E75.toInt(), 0xFF073642.toInt(), 0xFF16424D.toInt(), 0xFFEEE8D5.toInt(), 0xFF586E75.toInt(), 0xFF2AA198.toInt(), 0xFFB58900.toInt(), 0xFF2AA198.toInt()),
    KeyboardThemePreset("Solarized Light", 0xFFFDF6E3.toInt(), 0xFF93A1A1.toInt(), 0xFFFFFBEC.toInt(), 0xFFEEE8D5.toInt(), 0xFF073642.toInt(), 0xFFB8B7AA.toInt(), 0xFF268BD2.toInt(), 0xFFCB4B16.toInt(), 0xFF268BD2.toInt()),
    KeyboardThemePreset("Monokai", 0xFF272822.toInt(), 0xFF75715E.toInt(), 0xFF3E3D32.toInt(), 0xFF49483E.toInt(), 0xFFF8F8F2.toInt(), 0xFF75715E.toInt(), 0xFFA6E22E.toInt(), 0xFFFFD866.toInt(), 0xFF66D9EF.toInt()),
    KeyboardThemePreset("Dracula", 0xFF282A36.toInt(), 0xFF6272A4.toInt(), 0xFF343746.toInt(), 0xFF44475A.toInt(), 0xFFF8F8F2.toInt(), 0xFF6272A4.toInt(), 0xFFFF79C6.toInt(), 0xFFF1FA8C.toInt(), 0xFFBD93F9.toInt()),
    KeyboardThemePreset("Nord", 0xFF2E3440.toInt(), 0xFF4C566A.toInt(), 0xFF3B4252.toInt(), 0xFF434C5E.toInt(), 0xFFECEFF4.toInt(), 0xFF4C566A.toInt(), 0xFF88C0D0.toInt(), 0xFFEBCB8B.toInt(), 0xFF88C0D0.toInt()),
    KeyboardThemePreset("Volcanic Dusk", 0xFF1B141A.toInt(), 0xFF5D3B4F.toInt(), 0xFF2A2028.toInt(), 0xFF723650.toInt(), 0xFFFFEDF5.toInt(), 0xFF66515F.toInt(), 0xFFFF5D9E.toInt(), 0xFFFFB000.toInt(), 0xFFFF5D9E.toInt())
)
