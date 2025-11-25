package it.palsoftware.pastiera.backup

import org.json.JSONArray
import org.json.JSONObject

data class BackupMetadata(
    val versionCode: Int,
    val versionName: String,
    val timestamp: String,
    val components: List<String>
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("versionCode", versionCode)
        json.put("versionName", versionName)
        json.put("timestamp", timestamp)
        val componentsArray = JSONArray()
        components.forEach { componentsArray.put(it) }
        json.put("components", componentsArray)
        return json
    }

    fun toJsonString(): String = toJson().toString(2)

    companion object {
        fun fromFile(file: java.io.File): BackupMetadata? {
            return try {
                val content = file.readText()
                val json = JSONObject(content)
                val componentsArray = json.optJSONArray("components") ?: JSONArray()
                val components = mutableListOf<String>()
                for (i in 0 until componentsArray.length()) {
                    components.add(componentsArray.optString(i))
                }
                BackupMetadata(
                    versionCode = json.optInt("versionCode", 0),
                    versionName = json.optString("versionName", ""),
                    timestamp = json.optString("timestamp", ""),
                    components = components
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

enum class PreferenceValueType {
    BOOLEAN,
    INT,
    LONG,
    FLOAT,
    STRING,
    STRING_SET
}

data class PreferenceValue(
    val type: PreferenceValueType,
    val value: Any?
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("type", type.name.lowercase())
        when (type) {
            PreferenceValueType.STRING_SET -> {
                val array = JSONArray()
                (value as? Set<*>)?.forEach { item ->
                    array.put(item?.toString() ?: "")
                }
                json.put("value", array)
            }

            else -> json.put("value", value ?: JSONObject.NULL)
        }
        return json
    }

    fun coerceTo(expectedType: PreferenceValueType?): PreferenceValue? {
        if (expectedType == null || expectedType == type) {
            return this
        }
        return when (expectedType) {
            PreferenceValueType.BOOLEAN -> {
                val coerced = when (value) {
                    is Boolean -> value
                    is String -> value.toBooleanStrictOrNull()
                    else -> null
                }
                coerced?.let { PreferenceValue(PreferenceValueType.BOOLEAN, it) }
            }

            PreferenceValueType.INT -> {
                val number = (value as? Number)?.toInt() ?: (value as? String)?.toIntOrNull()
                number?.let { PreferenceValue(PreferenceValueType.INT, it) }
            }

            PreferenceValueType.LONG -> {
                val number = (value as? Number)?.toLong() ?: (value as? String)?.toLongOrNull()
                number?.let { PreferenceValue(PreferenceValueType.LONG, it) }
            }

            PreferenceValueType.FLOAT -> {
                val number = (value as? Number)?.toFloat() ?: (value as? String)?.toFloatOrNull()
                number?.let { PreferenceValue(PreferenceValueType.FLOAT, it) }
            }

            PreferenceValueType.STRING -> PreferenceValue(PreferenceValueType.STRING, value?.toString() ?: "")

            PreferenceValueType.STRING_SET -> {
                val setValue = when (value) {
                    is Collection<*> -> value.mapNotNull { it?.toString() }.toSet()
                    is String -> setOf(value)
                    else -> null
                }
                setValue?.let { PreferenceValue(PreferenceValueType.STRING_SET, it) }
            }
        }
    }

    companion object {
        fun fromAny(raw: Any?): PreferenceValue? {
            return when (raw) {
                is Boolean -> PreferenceValue(PreferenceValueType.BOOLEAN, raw)
                is Int -> PreferenceValue(PreferenceValueType.INT, raw)
                is Long -> PreferenceValue(PreferenceValueType.LONG, raw)
                is Float -> PreferenceValue(PreferenceValueType.FLOAT, raw)
                is Double -> PreferenceValue(PreferenceValueType.FLOAT, raw.toFloat())
                is String -> PreferenceValue(PreferenceValueType.STRING, raw)
                is Set<*> -> PreferenceValue(
                    PreferenceValueType.STRING_SET,
                    raw.mapNotNull { it?.toString() }.toSet()
                )
                else -> null
            }
        }

        fun fromJson(json: JSONObject): PreferenceValue? {
            val typeString = json.optString("type", "")
            val type = when (typeString.lowercase()) {
                "boolean" -> PreferenceValueType.BOOLEAN
                "int" -> PreferenceValueType.INT
                "long" -> PreferenceValueType.LONG
                "float" -> PreferenceValueType.FLOAT
                "string" -> PreferenceValueType.STRING
                "string_set" -> PreferenceValueType.STRING_SET
                else -> null
            } ?: return null

            val value = when (type) {
                PreferenceValueType.STRING_SET -> {
                    val array = json.optJSONArray("value") ?: JSONArray()
                    val set = mutableSetOf<String>()
                    for (i in 0 until array.length()) {
                        set.add(array.optString(i))
                    }
                    set.toSet()
                }

                else -> json.opt("value")
            }
            return PreferenceValue(type, value)
        }
    }
}

data class PreferenceFileSchema(
    val prefName: String,
    val fixedKeys: Map<String, PreferenceValueType>,
    val dynamicKeys: List<DynamicKey> = emptyList()
) {
    data class DynamicKey(val prefix: String, val type: PreferenceValueType)

    fun expectedType(key: String): PreferenceValueType? {
        fixedKeys[key]?.let { return it }
        dynamicKeys.firstOrNull { key.startsWith(it.prefix) }?.let { return it.type }
        return null
    }
}

object PreferenceSchemas {
    private val pastieraPrefsSchema = PreferenceFileSchema(
        prefName = "pastiera_prefs",
        fixedKeys = mapOf(
            "long_press_threshold" to PreferenceValueType.LONG,
            "auto_capitalize_first_letter" to PreferenceValueType.BOOLEAN,
            "double_space_to_period" to PreferenceValueType.BOOLEAN,
            "swipe_to_delete" to PreferenceValueType.BOOLEAN,
            "auto_show_keyboard" to PreferenceValueType.BOOLEAN,
            "clear_alt_on_space" to PreferenceValueType.BOOLEAN,
            "alt_ctrl_speech_shortcut" to PreferenceValueType.BOOLEAN,
            "sym_mappings_custom" to PreferenceValueType.STRING,
            "sym_mappings_page2_custom" to PreferenceValueType.STRING,
            "auto_correct_enabled" to PreferenceValueType.BOOLEAN,
            "auto_correct_enabled_languages" to PreferenceValueType.STRING,
            "suggestions_enabled" to PreferenceValueType.BOOLEAN,
            "accent_matching_enabled" to PreferenceValueType.BOOLEAN,
            "auto_replace_on_space_enter" to PreferenceValueType.BOOLEAN,
            "auto_capitalize_after_period" to PreferenceValueType.BOOLEAN,
            "long_press_modifier" to PreferenceValueType.STRING,
            "keyboard_layout" to PreferenceValueType.STRING,
            "keyboard_layout_list" to PreferenceValueType.STRING,
            "restore_sym_page" to PreferenceValueType.INT,
            "pending_restore_sym_page" to PreferenceValueType.INT,
            "sym_pages_config" to PreferenceValueType.STRING,
            "sym_auto_close" to PreferenceValueType.BOOLEAN,
            "dismissed_releases" to PreferenceValueType.STRING,
            "tutorial_completed" to PreferenceValueType.BOOLEAN,
            "swipe_incremental_threshold" to PreferenceValueType.FLOAT,
            "static_variation_bar_mode" to PreferenceValueType.BOOLEAN,
            "variations_updated" to PreferenceValueType.LONG,
            "launcher_shortcuts" to PreferenceValueType.STRING,
            "launcher_shortcuts_enabled" to PreferenceValueType.BOOLEAN,
            "nav_mode_enabled" to PreferenceValueType.BOOLEAN,
            "nav_mode_mappings_updated" to PreferenceValueType.LONG,
            "power_shortcuts_enabled" to PreferenceValueType.BOOLEAN,
            "experimental_suggestions_enabled" to PreferenceValueType.BOOLEAN,
            "suggestion_debug_logging" to PreferenceValueType.BOOLEAN
        ),
        dynamicKeys = listOf(
            PreferenceFileSchema.DynamicKey(
                prefix = "auto_correct_custom_",
                type = PreferenceValueType.STRING
            )
        )
    )

    private val schemasByName = mapOf(
        pastieraPrefsSchema.prefName to pastieraPrefsSchema
    )

    fun expectedType(prefName: String, key: String): PreferenceValueType? {
        return schemasByName[prefName]?.expectedType(key)
    }

    fun isRecognized(prefName: String, key: String, currentKeys: Set<String>): Boolean {
        if (currentKeys.contains(key)) {
            return true
        }
        val schema = schemasByName[prefName] ?: return false
        return schema.expectedType(key) != null
    }
}
