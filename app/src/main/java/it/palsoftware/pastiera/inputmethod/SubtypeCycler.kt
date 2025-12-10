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
import it.palsoftware.pastiera.data.layout.LayoutFileStore

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
            val enabledSubtypes = imm.getEnabledInputMethodSubtypeList(inputMethodInfo, true)
            if (enabledSubtypes.isEmpty()) {
                Log.w(TAG, "No subtypes available for cycling")
                return false
            }
            
            // Find current subtype index
            val currentIndex = enabledSubtypes.indexOfFirst { subtype ->
                subtype.locale == currentSubtype?.locale && 
                subtype.extraValue == currentSubtype?.extraValue
            }
            
            // Get next subtype (cycle to first if at end)
            val nextIndex = if (currentIndex >= 0 && currentIndex < enabledSubtypes.size - 1) {
                currentIndex + 1
            } else {
                0 // Cycle back to first
            }
            
            val nextSubtype = enabledSubtypes[nextIndex]
            
            // Try to switch using setInputMethodAndSubtype
            // This requires the IME window token, which may not always be available
            val result = trySwitchSubtype(imm, imeId, nextSubtype, context)
            
            if (result) {
                if (showToast) {
                    showUnifiedSubtypeToast(context, nextSubtype, assets)
                }
                Log.d(TAG, "Switched to subtype: ${nextSubtype.locale}")
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
                imm.setInputMethodAndSubtype(token, imeId, subtype)
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
                
                // Get layout for this locale from JSON mapping
                val locale = subtype.locale ?: "en_US"
                val layoutName = AdditionalSubtypeUtils.getLayoutForLocale(assets, locale, context)
                
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
                val locale = subtype.locale ?: "Unknown"
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

