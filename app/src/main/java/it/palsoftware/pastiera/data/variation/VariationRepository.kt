package it.palsoftware.pastiera.data.variation

import android.content.res.AssetManager
import android.util.Log
import org.json.JSONObject

/**
 * Loads character variations from JSON assets.
 */
object VariationRepository {
    private const val TAG = "VariationRepository"

    fun loadVariations(assets: AssetManager): Map<Char, List<String>> {
        val variationsMap = mutableMapOf<Char, List<String>>()
        return try {
            val filePath = "common/variations/variations.json"
            val inputStream = assets.open(filePath)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
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
}

