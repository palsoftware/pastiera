package it.palsoftware.pastiera.data.variation

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Loads character variations from JSON assets or custom file.
 * Checks files/variations.json first, then falls back to assets/variations.json.
 */
object VariationRepository {
    private const val TAG = "VariationRepository"
    private const val VARIATIONS_FILE_NAME = "variations.json"

    fun loadVariations(assets: AssetManager, context: Context? = null): Map<Char, List<String>> {
        val variationsMap = mutableMapOf<Char, List<String>>()
        return try {
            val jsonString = if (context != null) {
                // Check if custom file exists in files directory
                val customFile = File(context.filesDir, VARIATIONS_FILE_NAME)
                if (customFile.exists()) {
                    customFile.readText()
                } else {
                    // Fall back to assets
                    val filePath = "common/variations/variations.json"
                    assets.open(filePath).bufferedReader().use { it.readText() }
                }
            } else {
                // Legacy: only load from assets if context not provided
                val filePath = "common/variations/variations.json"
                assets.open(filePath).bufferedReader().use { it.readText() }
            }
            
            val jsonObject = JSONObject(jsonString)
            val variationsObject = jsonObject.getJSONObject("variations")

            val keys = variationsObject.keys()
            while (keys.hasNext()) {
                val baseChar = keys.next()
                if (baseChar.length == 1) {
                    val variationsArray = variationsObject.getJSONArray(baseChar)
                    val variationsList = mutableListOf<String>()
                    for (i in 0 until variationsArray.length()) {
                        variationsList.add(variationsArray.getString(i))
                    }
                    variationsMap[baseChar[0]] = variationsList
                }
            }
            variationsMap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading character variations", e)
            // Fallback to basic variations
            variationsMap['e'] = listOf("è", "é", "€")
            variationsMap['a'] = listOf("à", "á", "ä")
            variationsMap
        }
    }

    /**
     * Loads static utility variations from JSON assets or custom file.
     * Checks files/variations.json first, then falls back to assets/variations.json.
     * These are shown in the variation bar when static mode is enabled
     * or when smart features are disabled for the current field.
     */
    fun loadStaticVariations(assets: AssetManager, context: Context? = null): List<String> {
        return try {
            val jsonString = if (context != null) {
                // Check if custom file exists in files directory
                val customFile = File(context.filesDir, VARIATIONS_FILE_NAME)
                if (customFile.exists()) {
                    customFile.readText()
                } else {
                    // Fall back to assets
                    val filePath = "common/variations/variations.json"
                    assets.open(filePath).bufferedReader().use { it.readText() }
                }
            } else {
                // Legacy: only load from assets if context not provided
                val filePath = "common/variations/variations.json"
                assets.open(filePath).bufferedReader().use { it.readText() }
            }
            
            val jsonObject = JSONObject(jsonString)

            if (jsonObject.has("staticVariations")) {
                val staticArray = jsonObject.getJSONArray("staticVariations")
                val result = mutableListOf<String>()
                for (i in 0 until staticArray.length()) {
                    val value = staticArray.getString(i)
                    if (!value.isNullOrEmpty()) {
                        result.add(value)
                    }
                }
                if (result.isNotEmpty()) {
                    return result
                }
            }

            // Fallback: a small set of utility symbols
            listOf(";", ",", ":", "…", "?", "!", "\"")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading static variations", e)
            // Fallback: same default set
            listOf(";", ",", ":", "…", "?", "!", "\"")
        }
    }
}
