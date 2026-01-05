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
            val jsonString = loadJsonString(assets, context)
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
            // Return empty map - always load from JSON
            emptyMap()
        }
    }

    /**
     * Loads static utility variations from JSON assets or custom file.
     * Always loads from JSON, returns empty list if not found.
     * These are shown in the variation bar when static mode is enabled
     * or when smart features are disabled for the current field.
     */
    fun loadStaticVariations(assets: AssetManager, context: Context? = null): List<String> {
        return loadVariationsArray(assets, context, "staticVariations")
    }

    /**
     * Loads static utility variations for Shift modifier from JSON assets or custom file.
     */
    fun loadStaticVariationsShift(assets: AssetManager, context: Context? = null): List<String> {
        return loadVariationsArray(assets, context, "staticVariationsShift")
    }

    /**
     * Loads static utility variations for Alt modifier from JSON assets or custom file.
     */
    fun loadStaticVariationsAlt(assets: AssetManager, context: Context? = null): List<String> {
        return loadVariationsArray(assets, context, "staticVariationsAlt")
    }

    /**
     * Loads email-specific variations from JSON assets or custom file.
     * Always loads from JSON, returns empty list if not found.
     * These are shown in the variation bar when the current field is an email field.
     */
    fun loadEmailVariations(assets: AssetManager, context: Context? = null): List<String> {
        return loadVariationsArray(assets, context, "emailVariations")
    }

    /**
     * Common method to load a JSON array of variations.
     * Always loads from JSON, returns empty list if key not found or on error.
     */
    private fun loadVariationsArray(
        assets: AssetManager,
        context: Context?,
        jsonKey: String
    ): List<String> {
        return try {
            val jsonString = loadJsonString(assets, context)
            val jsonObject = JSONObject(jsonString)

            if (jsonObject.has(jsonKey)) {
                val array = jsonObject.getJSONArray(jsonKey)
                val result = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val value = array.getString(i)
                    if (value.isNotEmpty()) {
                        result.add(value)
                    }
                }
                result
            } else {
                Log.w(TAG, "JSON key '$jsonKey' not found, returning empty list")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading $jsonKey from JSON", e)
            emptyList()
        }
    }

    /**
     * Common method to load JSON string from file or assets.
     */
    private fun loadJsonString(assets: AssetManager, context: Context?): String {
        return if (context != null) {
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
    }
}
