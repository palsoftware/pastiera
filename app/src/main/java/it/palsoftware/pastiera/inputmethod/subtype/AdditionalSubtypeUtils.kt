package it.palsoftware.pastiera.inputmethod.subtype

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.data.layout.LayoutFileStore
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodService
import org.json.JSONObject
import java.util.Locale

/**
 * Utility class for managing additional IME subtypes (custom input styles).
 * Handles parsing, validation, creation, and serialization of subtypes.
 */
object AdditionalSubtypeUtils {
    private const val TAG = "AdditionalSubtypeUtils"
    
    const val PREF_CUSTOM_INPUT_STYLES = "custom_input_styles"
    private const val PREF_AUTO_ADDED_SYSTEM_LOCALES = "auto_added_system_locales"
    private const val EXTRA_KEY_KEYBOARD_LAYOUT_SET = "KeyboardLayoutSet"
    private const val EXTRA_KEY_ASCII_CAPABLE = "AsciiCapable"
    private const val EXTRA_KEY_EMOJI_CAPABLE = "EmojiCapable"
    private const val EXTRA_KEY_IS_ADDITIONAL_SUBTYPE = "isAdditionalSubtype"
    private val BASE_SUBTYPE_LOCALES = setOf(
        "en_US", "it_IT", "fr_FR", "de_DE", "pl_PL", "da_DK",
        "no_NO", "es_ES", "pt_PT", "ru_RU", "sr_RS", "uk_UA"
    )

    fun InputMethodSubtype.localeString(): String =
        languageTag.takeIf { it.isNotBlank() }
            ?: legacyLocaleString()

    fun InputMethodSubtype.languageCode(): String? =
        localeString()
            .replace('_', '-')
            .split('-')
            .firstOrNull()
            ?.takeIf { it.isNotBlank() }

    fun localeFromSubtypeString(localeString: String): Locale {
        val normalized = localeString.replace('_', '-')
        return Locale.forLanguageTag(normalized).takeIf { it.language.isNotBlank() }
            ?: Locale.ITALIAN
    }

    @Suppress("DEPRECATION")
    private fun InputMethodSubtype.legacyLocaleString(): String = locale.orEmpty()

    @Suppress("DEPRECATION")
    fun setAdditionalInputMethodSubtypesCompat(
        imm: InputMethodManager,
        imeId: String,
        subtypes: Array<InputMethodSubtype>
    ) {
        imm.setAdditionalInputMethodSubtypes(imeId, subtypes)
    }

    @Suppress("DEPRECATION")
    fun setInputMethodAndSubtypeCompat(
        imm: InputMethodManager,
        token: android.os.IBinder,
        imeId: String,
        subtype: InputMethodSubtype
    ) {
        imm.setInputMethodAndSubtype(token, imeId, subtype)
    }
    
    /**
     * Parses a preference string and creates an array of InputMethodSubtype objects.
     * Format: "locale:layout[:extra];locale:layout[:extra];..."
     * 
     * @param prefString The preference string containing subtype definitions
     * @param assets AssetManager to check layout availability
     * @param context Context for checking layout availability
     * @return Array of valid InputMethodSubtype objects
     */
    fun createAdditionalSubtypesArray(
        prefString: String?,
        assets: AssetManager,
        context: Context
    ): Array<InputMethodSubtype> {
        if (prefString.isNullOrBlank()) {
            return emptyArray()
        }
        
        val availableLayouts = LayoutMappingRepository.getAvailableLayouts(assets, context).toSet()
        val subtypes = mutableListOf<InputMethodSubtype>()
        
        val entries = prefString.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        
        for (entry in entries) {
            try {
                val parts = entry.split(":").map { it.trim() }
                if (parts.size < 2) {
                    Log.w(TAG, "Invalid entry format (missing locale or layout): $entry")
                    continue
                }
                
                val localeStr = parts[0]
                val layoutName = parts[1]
                val extra = if (parts.size > 2) parts[2] else ""
                
                // Validate locale
                if (!isValidLocale(localeStr)) {
                    Log.w(TAG, "Invalid locale: $localeStr")
                    continue
                }
                
                // Validate layout exists
                if (!availableLayouts.contains(layoutName)) {
                    Log.w(TAG, "Layout not available, skipping: $layoutName")
                    continue
                }

                if (isRedundantWithBaseSubtype(assets, context, localeStr, layoutName)) {
                    Log.d(TAG, "Skipping redundant custom subtype: $localeStr:$layoutName")
                    continue
                }
                
                // Create subtype
                val subtype = createSubtype(localeStr, layoutName, extra, assets, context)
                if (subtype != null) {
                    subtypes.add(subtype)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing entry: $entry", e)
            }
        }
        
        return subtypes.toTypedArray()
    }
    
    /**
     * Creates a preference string from an array of subtypes.
     * Format: "locale:layout[:extra];locale:layout[:extra];..."
     */
    fun createPrefSubtypes(subtypeArray: Array<InputMethodSubtype>): String {
        return subtypeArray.joinToString(";") { subtype ->
            val locale = subtype.localeString()
            val extraValue = subtype.extraValue ?: ""
            val layoutName = extractLayoutFromExtraValue(extraValue) ?: ""
            val otherExtras = extractOtherExtras(extraValue)
            
            if (otherExtras.isNotEmpty()) {
                "$locale:$layoutName:$otherExtras"
            } else {
                "$locale:$layoutName"
            }
        }
    }
    
    /**
     * Checks if a subtype is an additional (custom) subtype.
     */
    fun isAdditionalSubtype(subtype: InputMethodSubtype): Boolean {
        val extraValue = subtype.extraValue ?: return false
        return extraValue.contains(EXTRA_KEY_IS_ADDITIONAL_SUBTYPE)
    }
    
    /**
     * Creates an InputMethodSubtype with the specified locale and layout.
     */
    private fun createSubtype(
        localeStr: String,
        layoutName: String,
        extra: String,
        assets: AssetManager,
        context: Context
    ): InputMethodSubtype? {
        return try {
            // Parse locale
            val locale = parseLocale(localeStr)
            if (locale == null) {
                Log.w(TAG, "Failed to parse locale: $localeStr")
                return null
            }
            
            // Build extra value
            val extraValueBuilder = StringBuilder()
            extraValueBuilder.append("$EXTRA_KEY_KEYBOARD_LAYOUT_SET=$layoutName")
            extraValueBuilder.append(",")
            extraValueBuilder.append(EXTRA_KEY_ASCII_CAPABLE)
            extraValueBuilder.append(",")
            extraValueBuilder.append(EXTRA_KEY_EMOJI_CAPABLE)
            extraValueBuilder.append(",")
            extraValueBuilder.append(EXTRA_KEY_IS_ADDITIONAL_SUBTYPE)
            
            if (extra.isNotEmpty()) {
                extraValueBuilder.append(",")
                extraValueBuilder.append(extra)
            }
            
            val extraValue = extraValueBuilder.toString()
            
            // Get name resource ID for locale
            val nameResId = getLocaleNameResId(localeStr)
            
            val supportsNameOverride = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            val builder = InputMethodSubtype.InputMethodSubtypeBuilder()
                .setSubtypeNameResId(if (supportsNameOverride) 0 else nameResId)
                .setSubtypeLocale(localeStr)
                .setLanguageTag(locale.toLanguageTag())
                .setSubtypeMode("keyboard")
                .setSubtypeExtraValue(extraValue)
                .setSubtypeId(stableSubtypeId(localeStr, layoutName))
                .setIsAuxiliary(false)
                .setIsAsciiCapable(true)
                .setOverridesImplicitlyEnabledSubtype(false)

            if (supportsNameOverride) {
                builder.setSubtypeNameOverride(
                    buildSubtypeDisplayName(context, assets, locale, localeStr, layoutName)
                )
            }

            builder.build()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating subtype for $localeStr:$layoutName", e)
            null
        }
    }

    internal fun buildSubtypeDisplayName(
        context: Context,
        assets: AssetManager,
        locale: Locale,
        localeStr: String,
        layoutName: String
    ): String {
        val languageLabel = getLocaleNameResId(localeStr)
            .takeIf { it != 0 }
            ?.let(context::getString)
            ?: locale.getDisplayLanguage(context.resources.configuration.locales[0])
                .replaceFirstChar { character ->
                    if (character.isLowerCase()) character.titlecase() else character.toString()
                }
        val layoutLabel = (
            LayoutFileStore.getLayoutMetadataFromAssets(assets, layoutName)
                ?: LayoutFileStore.getLayoutMetadata(context, layoutName)
            )?.name
            ?.takeIf { it.isNotBlank() }
            ?.substringBefore(" | ")
            ?: layoutName

        return "$languageLabel · $layoutLabel"
    }

    private fun stableSubtypeId(locale: String, layout: String): Int {
        return "pastiera-subtype-v2|$locale|$layout".hashCode().takeUnless { it == 0 } ?: 1
    }
    
    /**
     * Validates if a locale string is valid.
     */
    private fun isValidLocale(localeStr: String): Boolean {
        return try {
            parseLocale(localeStr) != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Parses a locale string (e.g., "en_US", "it_IT", "fr") into a Locale object.
     */
    private fun parseLocale(localeStr: String): Locale? {
        return try {
            Locale.forLanguageTag(localeStr.replace('_', '-'))
                .takeIf { it.language.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Gets the resource ID for a locale's display name.
     * Returns 0 for custom locales - Android will auto-generate the name from the locale.
     */
    private fun getLocaleNameResId(localeStr: String): Int {
        val langCode = localeFromSubtypeString(localeStr).language.lowercase()
        return when (langCode) {
            "en" -> R.string.input_method_name_en
            "it" -> R.string.input_method_name_it
            "fr" -> R.string.input_method_name_fr
            "de" -> R.string.input_method_name_de
            "pl" -> R.string.input_method_name_pl
            "es" -> R.string.input_method_name_es
            "pt" -> R.string.input_method_name_pt
            "ru" -> R.string.input_method_name_ru
            "ko" -> R.string.input_method_name_ko
            else -> 0 // Use 0 for custom locales - Android will auto-generate name from locale
        }
    }
    
    /**
     * Extracts the layout name from extraValue.
     */
    private fun extractLayoutFromExtraValue(extraValue: String): String? {
        val parts = extraValue.split(",")
        for (part in parts) {
            if (part.startsWith("$EXTRA_KEY_KEYBOARD_LAYOUT_SET=")) {
                return part.substringAfter("=")
            }
        }
        return null
    }
    
    /**
     * Extracts other extras (excluding layout, ascii, emoji, isAdditionalSubtype).
     */
    private fun extractOtherExtras(extraValue: String): String {
        val parts = extraValue.split(",")
        val filtered = parts.filter { part ->
            !part.startsWith("$EXTRA_KEY_KEYBOARD_LAYOUT_SET=") &&
            part != EXTRA_KEY_ASCII_CAPABLE &&
            part != EXTRA_KEY_EMOJI_CAPABLE &&
            part != EXTRA_KEY_IS_ADDITIONAL_SUBTYPE
        }
        return filtered.joinToString(",")
    }
    
    /**
     * Finds a subtype by locale.
     */
    fun findSubtypeByLocale(
        subtypes: Array<InputMethodSubtype>,
        locale: String
    ): InputMethodSubtype? {
        return subtypes.firstOrNull { localesMatch(it.localeString(), locale) }
    }
    
    /**
     * Finds a subtype by locale and keyboard layout set.
     */
    fun findSubtypeByLocaleAndKeyboardLayoutSet(
        subtypes: Array<InputMethodSubtype>,
        locale: String,
        layoutName: String
    ): InputMethodSubtype? {
        return subtypes.firstOrNull { subtype ->
            localesMatch(subtype.localeString(), locale) &&
            extractLayoutFromExtraValue(subtype.extraValue ?: "") == layoutName
        }
    }
    
    /**
     * Gets the keyboard layout name from a subtype's extraValue.
     */
    fun getKeyboardLayoutFromSubtype(subtype: InputMethodSubtype): String? {
        return extractLayoutFromExtraValue(subtype.extraValue ?: "")
    }

    fun matchesLocaleAndKeyboardLayoutSet(
        subtype: InputMethodSubtype,
        locale: String,
        layoutName: String
    ): Boolean {
        return localesMatch(subtype.localeString(), locale) &&
            getKeyboardLayoutFromSubtype(subtype) == layoutName
    }

    private fun localesMatch(left: String, right: String): Boolean {
        val leftTag = localeFromSubtypeString(left).toLanguageTag()
        val rightTag = localeFromSubtypeString(right).toLanguageTag()
        return leftTag.equals(rightTag, ignoreCase = true)
    }

    private fun isRedundantWithBaseSubtype(
        assets: AssetManager,
        context: Context,
        locale: String,
        layoutName: String
    ): Boolean {
        if (locale !in BASE_SUBTYPE_LOCALES) {
            return false
        }
        return getLayoutForLocale(assets, locale, context) == layoutName
    }

    /**
     * Resolves the active keyboard layout based on the central layout mode:
     * - Auto mode: subtype layout -> locale mapping
     * - Manual mode: explicit user-selected layout from settings
     */
    fun resolveActiveLayout(
        assets: AssetManager,
        context: Context,
        subtype: InputMethodSubtype?
    ): String {
        if (!SettingsManager.isKeyboardLayoutAutoByLocale(context)) {
            return SettingsManager.getKeyboardLayout(context)
        }

        if (subtype != null) {
            val layoutFromSubtype = getKeyboardLayoutFromSubtype(subtype)
            if (!layoutFromSubtype.isNullOrEmpty()) {
                return layoutFromSubtype
            }
            val locale = subtype.localeString().ifBlank { "en_US" }
            return getLayoutForLocale(assets, locale, context)
        }

        // No subtype available: keep current selection to avoid unexpected jumps.
        return SettingsManager.getKeyboardLayout(context)
    }

    /**
     * Resolves the concrete layout declared by an input style, when present.
     * Falls back to the normal active-layout resolver for base/system subtypes.
     */
    fun resolveInputStyleLayout(
        assets: AssetManager,
        context: Context,
        subtype: InputMethodSubtype?
    ): String {
        val layoutFromSubtype = subtype?.let { getKeyboardLayoutFromSubtype(it) }
        if (!layoutFromSubtype.isNullOrEmpty()) {
            return layoutFromSubtype
        }
        return resolveActiveLayout(assets, context, subtype)
    }
    
    /**
     * Gets the default keyboard layout for a locale from the JSON mapping.
     * First checks custom file (if context provided), then falls back to assets.
     * Falls back to "qwerty" if not found.
     */
    fun getLayoutForLocale(assets: AssetManager, locale: String, context: Context? = null): String {
        val languageOnly = locale.substringBefore("_").substringBefore("-")
        // Settings use underscores while modern Android subtypes expose language tags.
        // Resolve both spellings before falling back to a language-wide mapping.
        val localeKeys = listOf(locale, locale.replace('_', '-'), locale.replace('-', '_'), languageOnly)
            .filter { it.isNotEmpty() }.distinct()

        fun JSONObject.mappedLayout(): String? = localeKeys.firstNotNullOfOrNull { key ->
            optString(key, "").takeIf { it.isNotEmpty() }
        }

        // First, try custom file if context is provided
        if (context != null) {
            try {
                val customMappingFile = java.io.File(context.filesDir, "locale_layout_mapping.json")
                if (customMappingFile.exists() && customMappingFile.canRead()) {
                    val jsonString = customMappingFile.readText()
                    val json = JSONObject(jsonString)
                    json.mappedLayout()?.let { return it }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error reading custom locale-layout mapping, falling back to assets", e)
            }
        }
        
        // Fallback to assets
        return try {
            assets.open("common/locale_layout_mapping.json").use { input ->
                val jsonString = input.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonString)
                json.mappedLayout() ?: "qwerty"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading layout for locale $locale, defaulting to qwerty", e)
            "qwerty"
        }
    }
    
    /**
     * Loads exact auto-added input-style entries.
     *
     * Older releases stored only locale names. Resolve those markers against the
     * current entries once so future cleanup never removes an unrelated user style.
     */
    private fun loadAutoAddedStyles(context: Context, currentEntries: List<String>): Set<String> {
        val stored = SettingsManager.getPreferences(context)
            .getStringSet(PREF_AUTO_ADDED_SYSTEM_LOCALES, emptySet())
            .orEmpty()

        return stored.mapNotNullTo(mutableSetOf()) { marker ->
            if (marker.contains(":")) {
                marker
            } else {
                val matchingEntries = currentEntries.filter { it.substringBefore(":") == marker }
                when {
                    matchingEntries.size == 1 -> matchingEntries.single()
                    else -> matchingEntries.firstOrNull {
                        it.substringAfter(":").substringBefore(":") ==
                            getLayoutForLocale(context.assets, marker, context)
                    }
                }
            }
        }
    }

    private fun saveAutoAddedStyles(context: Context, styles: Set<String>) {
        SettingsManager.getPreferences(context)
            .edit()
            .putStringSet(PREF_AUTO_ADDED_SYSTEM_LOCALES, styles)
            .apply()
    }
    
    /**
     * Removes only the system locales (without dictionary) that were auto-added and are no longer present in the system.
     * Does NOT remove user-created custom input styles.
     */
    fun removeSystemLocalesWithoutDictionary(context: Context) {
        try {
            val currentSystemLocales = getSystemEnabledLocales(context).toSet()
            val currentStyles = SettingsManager.getCustomInputStyles(context)
            val entries = currentStyles.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            val trackedAutoAdded = loadAutoAddedStyles(context, entries)

            val toRemove = trackedAutoAdded.filter { entry ->
                entry.substringBefore(":") !in currentSystemLocales
            }
            if (toRemove.isNotEmpty()) {
                SettingsManager.setCustomInputStyles(
                    context,
                    entries.filterNot(toRemove::contains).joinToString(";")
                )
            }

            saveAutoAddedStyles(context, trackedAutoAdded - toRemove.toSet())
            Log.d(TAG, "Removed ${toRemove.size} auto-added system input styles no longer in system locales")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing system locales without dictionary", e)
        }
    }
    
    /**
     * Checks if a subtype should be kept based on current system locales.
     * Returns true if the subtype should be kept, false if it should be removed.
     */
    fun shouldKeepSubtype(
        subtype: InputMethodSubtype,
        currentSystemLocales: Set<String>,
        systemLanguageCodes: Set<String>
    ): Boolean {
        // Keep ALL additional (custom) subtypes. They may not be present in system locales.
        if (isAdditionalSubtype(subtype)) return true

        // For system subtypes (from method.xml), keep only if locale (or language root) is still in system.
        val subtypeLocale = subtype.localeString()
        val languageCode = subtypeLocale.split("_").first().lowercase()
        return currentSystemLocales.contains(subtypeLocale) || systemLanguageCodes.contains(languageCode)
    }

    fun shouldKeepSubtype(
        context: Context,
        assets: AssetManager,
        subtype: InputMethodSubtype,
        currentSystemLocales: Set<String>,
        systemLanguageCodes: Set<String>
    ): Boolean {
        if (!shouldKeepSubtype(subtype, currentSystemLocales, systemLanguageCodes)) {
            return false
        }
        if (isAdditionalSubtype(subtype)) {
            return true
        }

        val locale = subtype.localeString()
        val layout = getKeyboardLayoutFromSubtype(subtype)
            ?: getLayoutForLocale(assets, locale, context)
        return !SettingsManager.isSystemInputStyleHidden(context, locale, layout)
    }
    
    /**
     * Automatically adds system locales without dictionary to custom input styles.
     * This ensures that new system languages are immediately usable even if they don't have a dictionary.
     * 
     * NOTE: This function does NOT remove locales - that should be done separately via
     * removeSystemLocalesWithoutDictionary() only when system configuration changes.
     */
    fun autoAddSystemLocalesWithoutDictionary(context: Context) {
        try {
            val systemLocales = getSystemEnabledLocales(context)
            val localesWithDict = getLocalesWithDictionary(context)
            val baseSubtypesInMethodXml = BASE_SUBTYPE_LOCALES
            
            val currentStyles = SettingsManager.getCustomInputStyles(context)
            val entries = currentStyles.split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableList()
            val trackedAutoAdded = loadAutoAddedStyles(context, entries).toMutableSet()

            // A system style's layout can be edited in Pastiera. Keep the exact
            // auto-generated entry and its provenance marker in sync with that mapping.
            trackedAutoAdded.groupBy { it.substringBefore(":") }.forEach { (locale, tracked) ->
                if (locale !in systemLocales) return@forEach
                val hasUserStyleForLocale = entries.any { entry ->
                    entry.substringBefore(":") == locale && entry !in trackedAutoAdded
                }
                if (hasUserStyleForLocale) return@forEach

                val desiredEntry = "$locale:${getLayoutForLocale(context.assets, locale, context)}"
                entries.removeAll(tracked.toSet())
                if (desiredEntry !in entries) entries.add(desiredEntry)
                trackedAutoAdded.removeAll(tracked.toSet())
                trackedAutoAdded.add(desiredEntry)
            }

            val existingLocales = entries.mapTo(mutableSetOf()) { it.substringBefore(":") }
            
            // Find system locales that need to be added
            val localesToAdd = systemLocales.filter { locale ->
                // Must not have dictionary
                !localesWithDict.contains(locale) &&
                // Must not be in method.xml
                !baseSubtypesInMethodXml.contains(locale) &&
                // Must not already be in custom input styles
                !existingLocales.contains(locale)
            }
            
            if (localesToAdd.isEmpty()) {
                val updatedStyles = entries.joinToString(";")
                if (updatedStyles != currentStyles) {
                    SettingsManager.setCustomInputStyles(context, updatedStyles)
                }
                saveAutoAddedStyles(context, trackedAutoAdded)
                Log.d(TAG, "No system locales without dictionary to add")
                return
            }
            
            // Add each locale with default layout
            val newEntries = mutableListOf<String>()
            localesToAdd.forEach { locale ->
                val defaultLayout = getLayoutForLocale(context.assets, locale, context)
                newEntries.add("$locale:$defaultLayout")
                Log.d(TAG, "Auto-adding system locale without dictionary: $locale with layout $defaultLayout")
            }
            
            // Merge with existing styles
            val updatedStyles = (entries + newEntries).joinToString(";")
            
            // Save updated styles
            SettingsManager.setCustomInputStyles(context, updatedStyles)
            // Track auto-added locales for future cleanup
            trackedAutoAdded.addAll(newEntries)
            saveAutoAddedStyles(context, trackedAutoAdded)
            Log.d(TAG, "Auto-added ${localesToAdd.size} system locales without dictionary to custom input styles")
        } catch (e: Exception) {
            Log.e(TAG, "Error auto-adding system locales without dictionary", e)
        }
    }
    
    /**
     * Gets the list of system-enabled locales.
     * Returns locales in format "en_US", "it_IT", etc.
     */
    private fun getSystemEnabledLocales(context: Context): List<String> {
        val locales = mutableListOf<String>()
        try {
            val config = context.applicationContext.resources.configuration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val localeList = config.locales
                for (i in 0 until localeList.size()) {
                    val locale = localeList[i]
                    val localeStr = formatLocaleStringForSystem(locale)
                    if (localeStr.isNotEmpty() && !locales.contains(localeStr)) {
                        locales.add(localeStr)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val locale = config.locale
                val localeStr = formatLocaleStringForSystem(locale)
                if (localeStr.isNotEmpty()) {
                    locales.add(localeStr)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting system locales", e)
        }
        return locales
    }
    
    /**
     * Formats a Locale object to "en_US" format.
     */
    private fun formatLocaleStringForSystem(locale: Locale): String {
        val language = locale.language
        val country = locale.country
        return if (country.isNotEmpty()) {
            "${language}_$country"
        } else {
            language
        }
    }
    
    /**
     * Gets the list of locales that have dictionaries available.
     * Checks both serialized (.dict) and JSON (.json) formats.
     */
    private fun getLocalesWithDictionary(context: Context): Set<String> {
        val localesWithDict = mutableSetOf<String>()
        try {
            val assets = context.assets
            
            // Check serialized dictionaries from assets
            try {
                val serializedFiles = assets.list("common/dictionaries_serialized")
                serializedFiles?.forEach { fileName ->
                    if (fileName.endsWith("_base.dict")) {
                        val langCode = fileName.removeSuffix("_base.dict")
                        localesWithDict.addAll(getLocaleVariantsForLanguage(langCode))
                    }
                }
            } catch (e: Exception) {
                // If serialized directory doesn't exist, continue
            }

            // Check custom/imported serialized dictionaries in app storage
            try {
                val localDir = java.io.File(context.filesDir, "dictionaries_serialized/custom")
                val localFiles = localDir.listFiles { file ->
                    file.isFile && file.name.endsWith("_base.dict")
                }
                localFiles?.forEach { file ->
                    val langCode = file.name.removeSuffix("_base.dict")
                    localesWithDict.addAll(getLocaleVariantsForLanguage(langCode))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking local dictionaries_serialized", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking dictionaries", e)
        }
        return localesWithDict
    }
    
    /**
     * Maps a language code (e.g., "en", "it") to common locale variants.
     */
    private fun getLocaleVariantsForLanguage(langCode: String): List<String> {
        // Common locale variants for each language
        val variants = when (langCode.lowercase()) {
            "en" -> listOf("en_US", "en_GB", "en_AU", "en_CA", "en")
            "it" -> listOf("it_IT", "it_CH", "it")
            "fr" -> listOf("fr_FR", "fr_CA", "fr_CH", "fr_BE", "fr")
            "de" -> listOf("de_DE", "de_AT", "de_CH", "de")
            "pl" -> listOf("pl_PL", "pl")
            "da" -> listOf("da_DK", "da")
            "no" -> listOf("no_NO", "nb_NO", "nn_NO", "nb", "nn", "no")
            "nb" -> listOf("nb_NO", "no_NO", "nb", "no")
            "nn" -> listOf("nn_NO", "no_NO", "nn", "no")
            "es" -> listOf("es_ES", "es_MX", "es_AR", "es")
            "pt" -> listOf("pt_PT", "pt_BR", "pt")
            "ru" -> listOf("ru_RU", "ru")
            else -> listOf(langCode)
        }
        return variants
    }
    
    /**
     * Registers additional subtypes (custom input styles) with the system.
     * Can be called from MainActivity or from the IME service.
     * 
     * @param context Context for accessing system services and assets
     */
    fun registerAdditionalSubtypes(context: Context) {
        try {
            autoAddSystemLocalesWithoutDictionary(context)

            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: run {
                    Log.e(TAG, "InputMethodManager not available")
                    return
                }

            val inputMethodInfo = imm.inputMethodList.firstOrNull { info ->
                info.packageName == context.packageName &&
                    info.serviceName == PhysicalKeyboardInputMethodService::class.java.name
            }

            if (inputMethodInfo == null) {
                Log.d(TAG, "IME not found in system list, will retry when IME is enabled")
                return
            }

            val imeId = inputMethodInfo.id
            val customPrefString = SettingsManager.getCustomInputStyles(context)
            val legacyEntries = SettingsManager.getAdditionalImeSubtypes(context).map { languageCode ->
                val locale = legacyLocaleForLanguage(languageCode)
                "$locale:${getLayoutForLocale(context.assets, locale, context)}"
            }
            val prefString = (
                customPrefString.split(";").map { it.trim() }.filter { it.isNotEmpty() } +
                    legacyEntries
                ).distinct().joinToString(";")
            val subtypes = createAdditionalSubtypesArray(
                prefString,
                context.assets,
                context
            )

            setAdditionalInputMethodSubtypesCompat(imm, imeId, subtypes)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                syncExplicitlyEnabledSubtypes(
                    context = context,
                    imm = imm,
                    imeId = imeId,
                    inputMethodInfo = inputMethodInfo,
                    configuredSubtypes = subtypes,
                    customPrefString = customPrefString
                )
            }

            Log.d(TAG, "Registered ${subtypes.size} additional subtypes for IME: $imeId")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering additional subtypes", e)
        }
    }

    private fun legacyLocaleForLanguage(languageCode: String): String = when (languageCode.lowercase()) {
        "ru" -> "ru_RU"
        "pt" -> "pt_PT"
        "de" -> "de_DE"
        "da" -> "da_DK"
        "no" -> "no_NO"
        "nb" -> "nb_NO"
        "nn" -> "nn_NO"
        "fr" -> "fr_FR"
        "es" -> "es_ES"
        "pl" -> "pl_PL"
        "it" -> "it_IT"
        "en" -> "en_US"
        "ko" -> "ko_KR"
        else -> languageCode
    }

    @androidx.annotation.RequiresApi(34)
    private fun syncExplicitlyEnabledSubtypes(
        context: Context,
        imm: InputMethodManager,
        imeId: String,
        inputMethodInfo: android.view.inputmethod.InputMethodInfo,
        configuredSubtypes: Array<InputMethodSubtype>,
        customPrefString: String
    ) {
        val staticSubtypes = buildList {
            for (index in 0 until inputMethodInfo.subtypeCount) {
                inputMethodInfo.getSubtypeAt(index)
                    .takeUnless(::isAdditionalSubtype)
                    ?.let(::add)
            }
        }
        val availableSubtypes = staticSubtypes + configuredSubtypes
        val availableHashCodes = availableSubtypes.mapTo(mutableSetOf()) { it.hashCode() }
        val entries = customPrefString.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        val autoAddedStyles = loadAutoAddedStyles(context, entries)
        val enabledHashCodes = imm.getEnabledInputMethodSubtypeList(inputMethodInfo, true)
            .asSequence()
            .filter { it.hashCode() in availableHashCodes }
            .filter { subtype ->
                if (isAdditionalSubtype(subtype)) {
                    isAdditionalSubtypeVisible(context, subtype, autoAddedStyles)
                } else {
                    val locale = subtype.localeString()
                    val layout = getKeyboardLayoutFromSubtype(subtype)
                        ?: getLayoutForLocale(context.assets, locale, context)
                    !SettingsManager.isSystemInputStyleHidden(context, locale, layout)
                }
            }
            .mapTo(mutableSetOf()) { it.hashCode() }

        // Static styles disappear from Android's enabled list after hiding them.
        // Rebuild requested styles from system locales and saved selections as well.
        val requestedLocales = getSystemEnabledLocales(context) +
            SettingsManager.getAdditionalImeSubtypes(context).map(::legacyLocaleForLanguage)
        staticSubtypes.filter { subtype ->
            val locale = subtype.localeString()
            val layout = getKeyboardLayoutFromSubtype(subtype)
                ?: getLayoutForLocale(context.assets, locale, context)
            val requested = requestedLocales.any { localesMatch(it, locale) } || entries.any { entry ->
                val parts = entry.split(":")
                parts.size >= 2 && localesMatch(parts[0], locale) && parts[1] == layout
            }
            requested && !SettingsManager.isSystemInputStyleHidden(context, locale, layout)
        }.mapTo(enabledHashCodes) { it.hashCode() }

        configuredSubtypes
            .filter { isAdditionalSubtypeVisible(context, it, autoAddedStyles) }
            .mapTo(enabledHashCodes) { it.hashCode() }

        imm.setExplicitlyEnabledInputMethodSubtypes(imeId, enabledHashCodes.toIntArray())
        Log.d(TAG, "Synchronized ${enabledHashCodes.size} explicitly enabled subtypes")
    }

    private fun isAdditionalSubtypeVisible(
        context: Context,
        subtype: InputMethodSubtype,
        autoAddedStyles: Set<String>
    ): Boolean {
        val locale = subtype.localeString()
        val layout = getKeyboardLayoutFromSubtype(subtype) ?: return true
        val styleKey = "$locale:$layout"
        val normalizedStyleKey = "${locale.replace('-', '_')}:$layout"
        val isAutoAdded = styleKey in autoAddedStyles || normalizedStyleKey in autoAddedStyles
        return !isAutoAdded || !SettingsManager.isSystemInputStyleHidden(context, locale, layout)
    }
}
