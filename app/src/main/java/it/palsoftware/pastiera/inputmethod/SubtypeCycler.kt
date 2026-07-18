package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils.localeString
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils.setInputMethodAndSubtypeCompat
import it.palsoftware.pastiera.data.layout.LayoutFileStore
import it.palsoftware.pastiera.SettingsManager

/**
 * Utility class for cycling between IME subtypes.
 * Can be used both from keyboard shortcuts (Ctrl+Space) and UI buttons.
 */
object SubtypeCycler {
    private const val TAG = "SubtypeCycler"
    private var unifiedSubtypeToast: Toast? = null
    
    /**
     * Cycles to the next IME subtype.
     * 
     * @param context The context (typically the InputMethodService)
     * @param imeServiceClass The class of the IME service (for identifying the IME)
     * @param assets AssetManager to read layout mappings
     * @param showToast If true, shows a toast with the new subtype name and layout
     * @return true if the subtype was changed, false otherwise
     */
    fun cycleToNextSubtype(
        context: Context,
        imeServiceClass: Class<*>,
        assets: AssetManager,
        showToast: Boolean = true
    ): Boolean {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return false
            
            val currentSubtype = imm.currentInputMethodSubtype
            
            // Get our IME info by searching for package and service name
            val packageName = context.packageName
            val serviceName = imeServiceClass.name
            
            val inputMethodInfo = imm.getInputMethodList().firstOrNull { info ->
                info.packageName == packageName && 
                info.serviceName == serviceName
            } ?: run {
                Log.w(TAG, "IME not found: package=$packageName, service=$serviceName")
                // Log all available IMEs for debugging
                val allImes = imm.getInputMethodList()
                Log.d(TAG, "Available IMEs:")
                allImes.forEach { ime ->
                    Log.d(TAG, "  - ID: ${ime.id}, Package: ${ime.packageName}, Service: ${ime.serviceName}")
                }
                return false
            }
            
            val imeId = inputMethodInfo.id
            Log.d(TAG, "Found IME: $imeId")
            
            // Get all enabled subtypes
            val enabledSubtypes = getCycleableSubtypes(
                context = context,
                assets = assets,
                subtypes = imm.getEnabledInputMethodSubtypeList(inputMethodInfo, true)
            )
            if (enabledSubtypes.isEmpty()) {
                Log.w(TAG, "No subtypes available for cycling")
                return false
            }
            
            // Find current subtype index
            val currentIndex = enabledSubtypes.indexOfFirst { subtype ->
                subtype.localeString() == currentSubtype?.localeString() && 
                subtype.extraValue == currentSubtype?.extraValue
            }
            
            // Get next subtype (cycle to first if at end)
            val nextIndex = if (currentIndex >= 0 && currentIndex < enabledSubtypes.size - 1) {
                currentIndex + 1
            } else {
                0 // Cycle back to first
            }
            
            val nextSubtype = enabledSubtypes[nextIndex]
            val nextLayout = resolveSubtypeCycleLayout(assets, context, nextSubtype)
            
            // Try to switch using setInputMethodAndSubtype
            // This requires the IME window token, which may not always be available
            val result = trySwitchSubtype(imm, imeId, nextSubtype, context)
            
            if (result) {
                SettingsManager.setKeyboardLayout(context, nextLayout)
                if (showToast) {
                    showUnifiedSubtypeToast(context, nextSubtype, assets)
                }
                Log.d(TAG, "Switched to subtype: ${nextSubtype.localeString()}")
            } else {
                Log.w(TAG, "Could not switch subtype using setInputMethodAndSubtype")
                // Note: switchToNextInputMethod requires an IBinder token and switches between IMEs,
                // not subtypes. For now, we return false if we can't switch using setInputMethodAndSubtype
                return false
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error cycling to next subtype", e)
            false
        }
    }
    
    /**
     * Attempts to switch to the specified subtype using setInputMethodAndSubtype.
     * This method requires the IME window token, which may not always be available.
     */
    private fun trySwitchSubtype(
        imm: InputMethodManager,
        imeId: String,
        subtype: InputMethodSubtype,
        context: Context
    ): Boolean {
        return try {
            // Try to get the IME token from the current window
            // This works when the IME is active
            val imeService = context as? android.inputmethodservice.InputMethodService
            val token = imeService?.window?.window?.attributes?.token
            
            if (token != null) {
                setInputMethodAndSubtypeCompat(imm, token, imeId, subtype)
                true
            } else {
                // Token not available
                Log.w(TAG, "IME window token not available")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error switching subtype", e)
            false
        }
    }
    
    /**
     * Shows a unified toast with subtype name and layout (e.g., "Italiano - Qwerty").
     */
    private fun showUnifiedSubtypeToast(context: Context, subtype: InputMethodSubtype, assets: AssetManager) {
        Handler(Looper.getMainLooper()).post {
            try {
                // Get subtype display name (language)
                val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
                val subtypeName = subtype.getDisplayName(
                    context,
                    context.packageName,
                    appInfo
                )
                
                val layoutName = resolveSubtypeCycleLayout(assets, context, subtype)
                
                // Get layout display name from metadata
                val layoutMetadata = try {
                    LayoutFileStore.getLayoutMetadataFromAssets(assets, layoutName)
                        ?: LayoutFileStore.getLayoutMetadata(context, layoutName)
                } catch (e: Exception) {
                    Log.w(TAG, "Error getting layout metadata", e)
                    null
                }
                
                val layoutDisplayName = layoutMetadata?.name ?: layoutName
                
                // Show unified toast: "Language - Layout"
                val toastText = "$subtypeName - $layoutDisplayName"
                unifiedSubtypeToast?.cancel()
                unifiedSubtypeToast = Toast.makeText(
                    context.applicationContext,
                    toastText,
                    Toast.LENGTH_SHORT
                )
                unifiedSubtypeToast?.show()
            } catch (e: Exception) {
                // Fallback: use locale if display name fails
                val locale = subtype.localeString().ifBlank { "Unknown" }
                unifiedSubtypeToast?.cancel()
                unifiedSubtypeToast = Toast.makeText(
                    context.applicationContext,
                    locale,
                    Toast.LENGTH_SHORT
                )
                unifiedSubtypeToast?.show()
                Log.e(TAG, "Error showing unified subtype toast, using locale fallback", e)
            }
        }
    }

    fun getCycleableSubtypes(
        context: Context,
        assets: AssetManager,
        subtypes: List<InputMethodSubtype>
    ): List<InputMethodSubtype> {
        val seen = mutableSetOf<String>()
        return subtypes.filter { subtype ->
            val locale = subtype.localeString()
            val layout = resolveSubtypeCycleLayout(assets, context, subtype)
            val hiddenSystemLocale =
                !AdditionalSubtypeUtils.isAdditionalSubtype(subtype) &&
                    SettingsManager.isSystemInputStyleHidden(context, locale, layout)
            !hiddenSystemLocale && seen.add("$locale:$layout")
        }
    }

    fun resolveSubtypeCycleLayout(
        assets: AssetManager,
        context: Context,
        subtype: InputMethodSubtype?
    ): String {
        if (subtype != null) {
            val layoutFromSubtype = AdditionalSubtypeUtils.getKeyboardLayoutFromSubtype(subtype)
            if (!layoutFromSubtype.isNullOrEmpty()) {
                return layoutFromSubtype
            }
            val locale = subtype.localeString().ifBlank { "en_US" }
            return AdditionalSubtypeUtils.getLayoutForLocale(assets, locale, context)
        }

        return SettingsManager.getKeyboardLayout(context)
    }
    
    /**
     * Switches to the enabled subtype whose locale and keyboard layout match
     * [targetLocale]/[targetLayout].
     *
     * Used to enforce a user-defined default input style when a new input field is opened.
     * Does nothing (returns false) if no enabled subtype matches the style, if the IME
     * is not found, or if the window token is unavailable.
     *
     * @return true if a switch was performed, false otherwise.
     */
    fun switchToSubtypeByStyle(
        context: Context,
        imeServiceClass: Class<*>,
        assets: AssetManager,
        targetLocale: String,
        targetLayout: String,
        showToast: Boolean = false
    ): Boolean {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return false

            // Already on the requested input style -> nothing to do
            val current = imm.currentInputMethodSubtype
            if (current != null &&
                current.localeString() == targetLocale &&
                resolveSubtypeCycleLayout(assets, context, current) == targetLayout
            ) {
                return false
            }

            val packageName = context.packageName
            val serviceName = imeServiceClass.name
            val inputMethodInfo = imm.getInputMethodList().firstOrNull { info ->
                info.packageName == packageName && info.serviceName == serviceName
            } ?: run {
                Log.w(TAG, "IME not found for default-style switch: $packageName/$serviceName")
                return false
            }

            val enabledSubtypes = imm.getEnabledInputMethodSubtypeList(inputMethodInfo, true)
            val targetSubtype = enabledSubtypes.firstOrNull { subtype ->
                subtype.localeString() == targetLocale &&
                    resolveSubtypeCycleLayout(assets, context, subtype) == targetLayout
            } ?: run {
                Log.d(TAG, "No enabled subtype matches default style '$targetLocale:$targetLayout', skipping")
                return false
            }

            val switched = trySwitchSubtype(imm, inputMethodInfo.id, targetSubtype, context)
            if (switched) {
                SettingsManager.setKeyboardLayout(context, targetLayout)
                Log.d(TAG, "Forced default input style: $targetLocale:$targetLayout")
                if (showToast) {
                    showUnifiedSubtypeToast(context, targetSubtype, assets)
                }
            }
            switched
        } catch (e: Exception) {
            Log.e(TAG, "Error switching to default input style '$targetLocale:$targetLayout'", e)
            false
        }
    }

    /**
     * Gets the current IME subtype's input style (locale + resolved keyboard layout),
     * or null if unavailable.
     */
    fun getCurrentInputStyle(context: Context, assets: AssetManager): SettingsManager.DefaultInputStyle? {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val subtype = imm?.currentInputMethodSubtype ?: return null
            val locale = subtype.localeString().ifBlank { return null }
            SettingsManager.DefaultInputStyle(
                locale = locale,
                layout = resolveSubtypeCycleLayout(assets, context, subtype)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current input style", e)
            null
        }
    }

    /**
     * Gets the current subtype display name.
     */
    fun getCurrentSubtypeName(context: Context): String? {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val currentSubtype = imm?.currentInputMethodSubtype ?: return null
            
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            currentSubtype.getDisplayName(
                context,
                context.packageName,
                appInfo
            )?.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current subtype name", e)
            null
        }
    }
    
    /**
     * Gets all available subtypes for the IME.
     */
    fun getAvailableSubtypes(
        context: Context,
        imeServiceClass: Class<*>
    ): List<InputMethodSubtype> {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return emptyList()
            
            val packageName = context.packageName
            val serviceName = imeServiceClass.name
            
            val inputMethodInfo = imm.getInputMethodList().firstOrNull { info ->
                info.packageName == packageName && 
                info.serviceName == serviceName
            } ?: return emptyList()
            
            imm.getEnabledInputMethodSubtypeList(inputMethodInfo, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available subtypes", e)
            emptyList()
        }
    }
}
