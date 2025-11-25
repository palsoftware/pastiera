package it.palsoftware.pastiera.data.mappings

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import org.json.JSONObject
import java.io.InputStream

/**
 * Helper for loading key mappings from JSON files.
 */
object KeyMappingLoader {
    private const val TAG = "KeyMappingLoader"

    fun getDeviceName(context: Context? = null): String {
        return "titan2"
    }

    private val keyCodeMap = mapOf(
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
        "KEYCODE_M" to KeyEvent.KEYCODE_M,
        "KEYCODE_1" to KeyEvent.KEYCODE_1,
        "KEYCODE_2" to KeyEvent.KEYCODE_2,
        "KEYCODE_3" to KeyEvent.KEYCODE_3,
        "KEYCODE_4" to KeyEvent.KEYCODE_4,
        "KEYCODE_5" to KeyEvent.KEYCODE_5,
        "KEYCODE_6" to KeyEvent.KEYCODE_6,
        "KEYCODE_7" to KeyEvent.KEYCODE_7,
        "KEYCODE_8" to KeyEvent.KEYCODE_8,
        "KEYCODE_9" to KeyEvent.KEYCODE_9,
        "KEYCODE_0" to KeyEvent.KEYCODE_0,
        "KEYCODE_MINUS" to KeyEvent.KEYCODE_MINUS,
        "KEYCODE_EQUALS" to KeyEvent.KEYCODE_EQUALS,
        "KEYCODE_LEFT_BRACKET" to KeyEvent.KEYCODE_LEFT_BRACKET,
        "KEYCODE_RIGHT_BRACKET" to KeyEvent.KEYCODE_RIGHT_BRACKET,
        "KEYCODE_SEMICOLON" to KeyEvent.KEYCODE_SEMICOLON,
        "KEYCODE_APOSTROPHE" to KeyEvent.KEYCODE_APOSTROPHE,
        "KEYCODE_COMMA" to KeyEvent.KEYCODE_COMMA,
        "KEYCODE_PERIOD" to KeyEvent.KEYCODE_PERIOD,
        "KEYCODE_SLASH" to KeyEvent.KEYCODE_SLASH
    )

    fun loadAltKeyMappings(assets: AssetManager, context: Context? = null): Map<Int, String> {
        val altKeyMap = mutableMapOf<Int, String>()
        try {
            val deviceName = getDeviceName(context)
            val filePath = "devices/$deviceName/alt_key_mappings.json"
            val inputStream: InputStream = assets.open(filePath)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")

            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyCodeMap[keyName]
                val character = mappingsObject.getString(keyName)
                if (keyCode != null) {
                    altKeyMap[keyCode] = character
                }
            }
            Log.d(TAG, "Loaded Alt mappings for device: $deviceName")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Alt mappings", e)
            altKeyMap[KeyEvent.KEYCODE_T] = "("
            altKeyMap[KeyEvent.KEYCODE_Y] = ")"
        }
        return altKeyMap
    }

    fun loadSymKeyMappings(assets: AssetManager): Map<Int, String> {
        val symKeyMap = mutableMapOf<Int, String>()
        try {
            val filePath = "common/sym/sym_key_mappings.json"
            val inputStream: InputStream = assets.open(filePath)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")

            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyCodeMap[keyName]
                val emoji = mappingsObject.getString(keyName)
                if (keyCode != null) {
                    symKeyMap[keyCode] = emoji
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading SYM mappings", e)
            symKeyMap[KeyEvent.KEYCODE_Q] = "😀"
            symKeyMap[KeyEvent.KEYCODE_W] = "😂"
        }
        return symKeyMap
    }

    fun loadSymKeyMappingsPage2(assets: AssetManager): Map<Int, String> {
        val symKeyMap = mutableMapOf<Int, String>()
        try {
            val filePath = "common/sym/sym_key_mappings_page2.json"
            val inputStream: InputStream = assets.open(filePath)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")

            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyCodeMap[keyName]
                val character = mappingsObject.getString(keyName)
                if (keyCode != null) {
                    symKeyMap[keyCode] = character
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading SYM page 2 mappings", e)
            symKeyMap[KeyEvent.KEYCODE_Q] = "¿"
            symKeyMap[KeyEvent.KEYCODE_W] = "¡"
        }
        return symKeyMap
    }

    data class CtrlMapping(val type: String, val value: String)

    fun loadCtrlKeyMappings(assets: AssetManager, context: Context? = null): Map<Int, CtrlMapping> {
        val ctrlKeyMap = mutableMapOf<Int, CtrlMapping>()
        try {
            val jsonString = if (context != null) {
                val customFile = SettingsManager.getNavModeMappingsFile(context)
                if (customFile.exists()) {
                    try {
                        customFile.readText()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading custom nav mode mappings file, falling back to assets", e)
                        null
                    }
                } else {
                    null
                }
            } else {
                null
            } ?: run {
                val filePath = "common/ctrl/ctrl_key_mappings.json"
                val inputStream: InputStream = assets.open(filePath)
                inputStream.bufferedReader().use { it.readText() }
            }

            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")

            val specialKeyCodeMap = mapOf(
                "DPAD_UP" to KeyEvent.KEYCODE_DPAD_UP,
                "DPAD_DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
                "DPAD_LEFT" to KeyEvent.KEYCODE_DPAD_LEFT,
                "DPAD_RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
                "DPAD_CENTER" to KeyEvent.KEYCODE_DPAD_CENTER,
                "TAB" to KeyEvent.KEYCODE_TAB,
                "PAGE_UP" to KeyEvent.KEYCODE_PAGE_UP,
                "PAGE_DOWN" to KeyEvent.KEYCODE_PAGE_DOWN,
                "ESCAPE" to KeyEvent.KEYCODE_ESCAPE,
                "FORWARD_DEL" to KeyEvent.KEYCODE_FORWARD_DEL
            )

            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyCodeMap[keyName]
                val mappingObject = mappingsObject.getJSONObject(keyName)
                val type = mappingObject.getString("type")

                if (keyCode != null) {
                    when (type) {
                        "action" -> {
                            val action = mappingObject.getString("action")
                            ctrlKeyMap[keyCode] = CtrlMapping("action", action)
                        }
                        "keycode" -> {
                            val keycodeName = mappingObject.getString("keycode")
                            val mappedKeyCode = specialKeyCodeMap[keycodeName]
                            if (mappedKeyCode != null) {
                                ctrlKeyMap[keyCode] = CtrlMapping("keycode", keycodeName)
                            }
                        }
                        "none" -> Unit
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Ctrl mappings", e)
            ctrlKeyMap[KeyEvent.KEYCODE_C] = CtrlMapping("action", "copy")
            ctrlKeyMap[KeyEvent.KEYCODE_V] = CtrlMapping("action", "paste")
            ctrlKeyMap[KeyEvent.KEYCODE_X] = CtrlMapping("action", "cut")
            ctrlKeyMap[KeyEvent.KEYCODE_Z] = CtrlMapping("action", "undo")
            ctrlKeyMap[KeyEvent.KEYCODE_E] = CtrlMapping("keycode", "DPAD_UP")
            ctrlKeyMap[KeyEvent.KEYCODE_S] = CtrlMapping("keycode", "DPAD_DOWN")
            ctrlKeyMap[KeyEvent.KEYCODE_D] = CtrlMapping("keycode", "DPAD_LEFT")
            ctrlKeyMap[KeyEvent.KEYCODE_F] = CtrlMapping("keycode", "DPAD_RIGHT")
        }
        return ctrlKeyMap
    }
}

