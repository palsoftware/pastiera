package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import android.view.KeyEvent
import org.json.JSONObject
import java.io.InputStream

/**
 * Manages keyboard layouts (QWERTY, AZERTY, etc.).
 * Handles character mapping based on physical key position (KEYCODE).
 * Important: ALT mappings always remain based on physical position (KEYCODE).
 */
object KeyboardLayoutManager {
    private const val TAG = "KeyboardLayoutManager"
    
    /**
     * Data class representing a keyboard layout mapping.
     * Maps KEYCODE (physical position) to the character that should be produced.
     */
    data class LayoutMapping(
        val lowercase: Char,
        val uppercase: Char,
        val altlowercase: Char? = null,
        val altuppercase: Char? = null
    )
    
    private val defaultLayout = mapOf<Int, LayoutMapping>(
        // QWERTY layout (default)
        KeyEvent.KEYCODE_Q to LayoutMapping('q', 'Q'),
        KeyEvent.KEYCODE_W to LayoutMapping('w', 'W'),
        KeyEvent.KEYCODE_E to LayoutMapping('e', 'E'),
        KeyEvent.KEYCODE_R to LayoutMapping('r', 'R'),
        KeyEvent.KEYCODE_T to LayoutMapping('t', 'T'),
        KeyEvent.KEYCODE_Y to LayoutMapping('y', 'Y'),
        KeyEvent.KEYCODE_U to LayoutMapping('u', 'U'),
        KeyEvent.KEYCODE_I to LayoutMapping('i', 'I'),
        KeyEvent.KEYCODE_O to LayoutMapping('o', 'O'),
        KeyEvent.KEYCODE_P to LayoutMapping('p', 'P'),
        KeyEvent.KEYCODE_A to LayoutMapping('a', 'A'),
        KeyEvent.KEYCODE_S to LayoutMapping('s', 'S'),
        KeyEvent.KEYCODE_D to LayoutMapping('d', 'D'),
        KeyEvent.KEYCODE_F to LayoutMapping('f', 'F'),
        KeyEvent.KEYCODE_G to LayoutMapping('g', 'G'),
        KeyEvent.KEYCODE_H to LayoutMapping('h', 'H'),
        KeyEvent.KEYCODE_J to LayoutMapping('j', 'J'),
        KeyEvent.KEYCODE_K to LayoutMapping('k', 'K'),
        KeyEvent.KEYCODE_L to LayoutMapping('l', 'L'),
        KeyEvent.KEYCODE_Z to LayoutMapping('z', 'Z'),
        KeyEvent.KEYCODE_X to LayoutMapping('x', 'X'),
        KeyEvent.KEYCODE_C to LayoutMapping('c', 'C'),
        KeyEvent.KEYCODE_V to LayoutMapping('v', 'V'),
        KeyEvent.KEYCODE_B to LayoutMapping('b', 'B'),
        KeyEvent.KEYCODE_N to LayoutMapping('n', 'N'),
        KeyEvent.KEYCODE_M to LayoutMapping('m', 'M')
    )
    
    private var currentLayout: Map<Int, LayoutMapping> = defaultLayout
    
    /**
     * Loads a keyboard layout from assets.
     * Layout files should be in common/layouts/{layout_name}.json
     */
    fun loadLayout(assets: AssetManager, layoutName: String): Map<Int, LayoutMapping> {
        return try {
            val filePath = "common/layouts/$layoutName.json"
            val inputStream: InputStream = assets.open(filePath)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")
            
            val keyCodeMap = mapOf(
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
            
            val layout = mutableMapOf<Int, LayoutMapping>()
            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyCodeMap[keyName]
                
                if (keyCode != null) {
                    val mappingObj = mappingsObject.getJSONObject(keyName)
                    val lowercase = mappingObj.getString("lowercase")
                    val uppercase = mappingObj.getString("uppercase")
                    val altlowercase = mappingObj.getString("altlowercase")
                    val altuppercase = mappingObj.getString("altuppercase")

                    if (lowercase.length == 1 && uppercase.length == 1) {
                        if (altlowercase.isNotEmpty() && altuppercase.isNotEmpty()) {
                            layout[keyCode] = LayoutMapping(
                                lowercase[0],
                                uppercase[0],
                                altlowercase[0],
                                altuppercase[0]
                            )
                        }
                        else layout[keyCode] = LayoutMapping(lowercase[0], uppercase[0], null, null)
                    }
                }
            }
            
            Log.d(TAG, "Loaded layout: $layoutName with ${layout.size} mappings")
            layout
        } catch (e: Exception) {
            Log.e(TAG, "Error loading layout: $layoutName, falling back to default", e)
            defaultLayout
        }
    }
    
    /**
     * Sets the current keyboard layout.
     */
    fun setLayout(layout: Map<Int, LayoutMapping>) {
        currentLayout = layout
    }
    
    /**
     * Gets the current keyboard layout.
     */
    fun getLayout(): Map<Int, LayoutMapping> = currentLayout
    
    /**
     * Gets the character for a given keyCode and shift state.
     * Returns the character from the current layout, or null if not mapped.
     */
    fun getCharacter(keyCode: Int, isShift: Boolean): Char? {
        val mapping = currentLayout[keyCode] ?: return null
        return if (isShift) mapping.uppercase else mapping.lowercase
    }

    /**
     * Gets the layout alt characters for a given layout.
     */
    fun getAltCharacter(keyCode: Int, isShift: Boolean): Char? {
        val mapping = currentLayout[keyCode] ?: return null
        if (mapping.altlowercase == null || mapping.altuppercase == null) return null
        return if (isShift) mapping.altuppercase else mapping.altlowercase
    }

    /**
     * Gets the lowercase character for a given keyCode.
     */
    fun getLowercase(keyCode: Int): Char? {
        return currentLayout[keyCode]?.lowercase
    }
    
    /**
     * Gets the uppercase character for a given keyCode.
     */
    fun getUppercase(keyCode: Int): Char? {
        return currentLayout[keyCode]?.uppercase
    }

    fun getAltLowercase(keyCode: Int): Char? {
        return currentLayout[keyCode]?.lowercase
    }

    /**
     * Returns true if the keyCode is mapped in the current layout.
     */
    fun isMapped(keyCode: Int): Boolean {
        return currentLayout.containsKey(keyCode)
    }
    
    /**
     * Gets all available layout names from assets.
     */
    fun getAvailableLayouts(assets: AssetManager): List<String> {
        return try {
            val layouts = mutableListOf<String>()
            val layoutFiles = assets.list("common/layouts")
            layoutFiles?.forEach { fileName ->
                if (fileName.endsWith(".json")) {
                    layouts.add(fileName.removeSuffix(".json"))
                }
            }
            layouts.sorted()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available layouts", e)
            emptyList()
        }
    }
}


