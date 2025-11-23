package it.palsoftware.pastiera.data.layout

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import android.view.KeyEvent
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Manages custom keyboard layout files on device storage as well as metadata
 * retrieval from both local files and bundled assets.
 */
object LayoutFileStore {
    private const val TAG = "LayoutFileStore"
    private const val LAYOUTS_DIR_NAME = "keyboard_layouts"

    private val keyboardLayoutNameToKeyCode = mapOf(
        "KEYCODE_Q" to KeyEvent.KEYCODE_Q,
        "KEYCODE_W" to KeyEvent.KEYCODE_W,
        "KEYCODE_E" to KeyEvent.KEYCODE_E,
        "KEYCODE_R" to KeyEvent.KEYCODE_R,
        "KEYCODE_T" to KeyEvent.KEYCODE_T,
        "KEYCODE_Y" to KeyEvent.KEYCODE_Y,
        "KEYCODE_U" to KeyEvent.KEYCODE_U,
        "KEYCODE_I" to KeyEvent.KEYCODE_I,
        "KEYCODE_O" to KeyEvent.KEYCODE_O,
        "KEYCODE_P" to KeyEvent.KEYCODE_P,
        "KEYCODE_A" to KeyEvent.KEYCODE_A,
        "KEYCODE_S" to KeyEvent.KEYCODE_S,
        "KEYCODE_D" to KeyEvent.KEYCODE_D,
        "KEYCODE_F" to KeyEvent.KEYCODE_F,
        "KEYCODE_G" to KeyEvent.KEYCODE_G,
        "KEYCODE_H" to KeyEvent.KEYCODE_H,
        "KEYCODE_J" to KeyEvent.KEYCODE_J,
        "KEYCODE_K" to KeyEvent.KEYCODE_K,
        "KEYCODE_L" to KeyEvent.KEYCODE_L,
        "KEYCODE_Z" to KeyEvent.KEYCODE_Z,
        "KEYCODE_X" to KeyEvent.KEYCODE_X,
        "KEYCODE_C" to KeyEvent.KEYCODE_C,
        "KEYCODE_V" to KeyEvent.KEYCODE_V,
        "KEYCODE_B" to KeyEvent.KEYCODE_B,
        "KEYCODE_N" to KeyEvent.KEYCODE_N,
        "KEYCODE_M" to KeyEvent.KEYCODE_M
    )
    private val keyboardLayoutKeyCodeToName = keyboardLayoutNameToKeyCode.entries.associate { (name, code) ->
        code to name
    }

    fun getLayoutsDirectory(context: Context): File {
        return File(context.filesDir, LAYOUTS_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    fun getLayoutFile(context: Context, layoutName: String): File {
        val layoutsDir = getLayoutsDirectory(context)
        return File(layoutsDir, "$layoutName.json")
    }

    fun loadLayoutFromFile(file: File): Map<Int, LayoutMapping>? {
        return try {
            if (!file.exists() || !file.canRead()) {
                Log.w(TAG, "File does not exist or cannot be read: ${file.absolutePath}")
                return null
            }
            val jsonString = file.readText()
            parseLayoutJson(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading layout from file: ${file.absolutePath}", e)
            null
        }
    }

    fun loadLayoutFromStream(inputStream: InputStream): Map<Int, LayoutMapping>? {
        return try {
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            parseLayoutJson(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading layout from stream", e)
            null
        }
    }

    private fun parseLayoutJson(jsonString: String): Map<Int, LayoutMapping>? {
        return try {
            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")

            val layout = mutableMapOf<Int, LayoutMapping>()
            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyboardLayoutNameToKeyCode[keyName]
                if (keyCode != null) {
                    val mappingObj = mappingsObject.getJSONObject(keyName)
                    val lowercase = mappingObj.optString("lowercase", "")
                    val uppercase = mappingObj.optString("uppercase", "")
                    val multiTapEnabled = mappingObj.optBoolean("multiTapEnabled", false)
                    val taps = mutableListOf<TapMapping>()
                    val tapsArray = mappingObj.optJSONArray("taps")
                    if (tapsArray != null) {
                        for (i in 0 until tapsArray.length()) {
                            val tapObj = tapsArray.optJSONObject(i) ?: continue
                            val tapLower = tapObj.optString("lowercase", "")
                            val tapUpper = tapObj.optString("uppercase", "")
                            if (tapLower.isNotEmpty() || tapUpper.isNotEmpty()) {
                                taps.add(TapMapping(tapLower, tapUpper))
                            }
                        }
                    }
                    val normalizedTaps = if (multiTapEnabled && taps.size > 1) taps else emptyList()
                    val normalizedMultiTapFlag = multiTapEnabled && normalizedTaps.size > 1
                    if (lowercase.isNotEmpty() && uppercase.isNotEmpty()) {
                        layout[keyCode] = LayoutMapping(
                            lowercase = lowercase,
                            uppercase = uppercase,
                            multiTapEnabled = normalizedMultiTapFlag,
                            taps = normalizedTaps
                        )
                    }
                }
            }
            Log.d(TAG, "Parsed layout with ${layout.size} mappings")
            layout
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing layout JSON", e)
            null
        }
    }

    fun saveLayout(
        context: Context,
        layoutName: String,
        layout: Map<Int, LayoutMapping>,
        name: String? = null,
        description: String? = null
    ): Boolean {
        return try {
            val layoutFile = getLayoutFile(context, layoutName)
            val jsonString = buildLayoutJsonString(layoutName, layout, name, description)
            FileOutputStream(layoutFile).use { outputStream ->
                outputStream.write(jsonString.toByteArray())
            }
            Log.d(TAG, "Saved layout: $layoutName to ${layoutFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving layout: $layoutName", e)
            false
        }
    }

    fun buildLayoutJsonString(
        layoutName: String,
        layout: Map<Int, LayoutMapping>,
        name: String?,
        description: String?
    ): String {
        val jsonObject = JSONObject()
        name?.takeIf { it.isNotBlank() }?.let { jsonObject.put("name", it) }
        description?.takeIf { it.isNotBlank() }?.let { jsonObject.put("description", it) }

        val mappingsObject = JSONObject()
        layout.forEach { (keyCode, mapping) ->
            val keyName = keyboardLayoutKeyCodeToName[keyCode]
            if (keyName != null) {
                val mappingObj = JSONObject()
                mappingObj.put("lowercase", mapping.lowercase)
                mappingObj.put("uppercase", mapping.uppercase)
                if (mapping.multiTapEnabled && mapping.taps.isNotEmpty()) {
                    mappingObj.put("multiTapEnabled", true)
                    val tapsArray = org.json.JSONArray()
                    mapping.taps.forEach { tap ->
                        val tapObj = JSONObject()
                        tapObj.put("lowercase", tap.lowercase)
                        tapObj.put("uppercase", tap.uppercase)
                        tapsArray.put(tapObj)
                    }
                    mappingObj.put("taps", tapsArray)
                }
                mappingsObject.put(keyName, mappingObj)
            }
        }

        jsonObject.put("mappings", mappingsObject)
        return jsonObject.toString(2)
    }

    fun saveLayoutFromJson(
        context: Context,
        layoutName: String,
        jsonString: String
    ): Boolean {
        return try {
            val layout = parseLayoutJson(jsonString)
            if (layout == null) {
                Log.e(TAG, "Invalid JSON format, cannot save layout: $layoutName")
                return false
            }

            val layoutFile = getLayoutFile(context, layoutName)
            FileOutputStream(layoutFile).use { outputStream ->
                outputStream.write(jsonString.toByteArray())
            }

            Log.d(TAG, "Saved layout from JSON: $layoutName to ${layoutFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving layout from JSON: $layoutName", e)
            false
        }
    }

    fun getCustomLayoutNames(context: Context): List<String> {
        return try {
            val layoutsDir = getLayoutsDirectory(context)
            val layoutFiles = layoutsDir.listFiles { file ->
                file.isFile && file.name.endsWith(".json")
            }
            layoutFiles?.map { it.name.removeSuffix(".json") }?.sorted() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting custom layout names", e)
            emptyList()
        }
    }

    fun getLayoutMetadata(context: Context, layoutName: String): LayoutMetadata? {
        return try {
            val layoutFile = getLayoutFile(context, layoutName)
            if (!layoutFile.exists() || !layoutFile.canRead()) {
                return null
            }

            val jsonString = layoutFile.readText()
            val jsonObject = JSONObject(jsonString)
            LayoutMetadata(
                name = jsonObject.optString("name", layoutName),
                description = jsonObject.optString("description", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting layout metadata: $layoutName", e)
            null
        }
    }

    fun getLayoutMetadataFromAssets(assets: AssetManager, layoutName: String): LayoutMetadata? {
        return try {
            val filePath = "common/layouts/$layoutName.json"
            val inputStream: InputStream = assets.open(filePath)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            LayoutMetadata(
                name = jsonObject.optString("name", layoutName),
                description = jsonObject.optString("description", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting layout metadata from assets: $layoutName", e)
            null
        }
    }

    fun deleteLayout(context: Context, layoutName: String): Boolean {
        return try {
            val layoutFile = getLayoutFile(context, layoutName)
            if (layoutFile.exists()) {
                val deleted = layoutFile.delete()
                if (deleted) {
                    Log.d(TAG, "Deleted layout: $layoutName")
                }
                deleted
            } else {
                Log.w(TAG, "Layout file does not exist: $layoutName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting layout: $layoutName", e)
            false
        }
    }

    fun layoutExists(context: Context, layoutName: String): Boolean {
        return getLayoutFile(context, layoutName).exists()
    }

    fun importLayoutFromFile(
        context: Context,
        sourceFile: File,
        targetLayoutName: String
    ): Boolean {
        return try {
            if (!sourceFile.exists() || !sourceFile.canRead()) {
                Log.e(TAG, "Source file does not exist or cannot be read: ${sourceFile.absolutePath}")
                return false
            }

            val layout = loadLayoutFromFile(sourceFile)
            if (layout == null) {
                Log.e(TAG, "Invalid layout file, cannot import: ${sourceFile.absolutePath}")
                return false
            }

            val targetFile = getLayoutFile(context, targetLayoutName)
            sourceFile.copyTo(targetFile, overwrite = true)

            Log.d(TAG, "Imported layout from ${sourceFile.absolutePath} to ${targetFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error importing layout from file", e)
            false
        }
    }

    data class LayoutMetadata(
        val name: String,
        val description: String
    )
}
