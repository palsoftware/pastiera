package it.palsoftware.pastiera

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.KeyEvent
import org.json.JSONObject

/**
 * Gestisce le impostazioni dell'app.
 * Centralizza l'accesso alle SharedPreferences per le impostazioni di Pastiera.
 */
object SettingsManager {
    private const val TAG = "SettingsManager"
    private const val PREFS_NAME = "pastiera_prefs"
    
    // Chiavi delle impostazioni
    private const val KEY_LONG_PRESS_THRESHOLD = "long_press_threshold"
    private const val KEY_AUTO_CAPITALIZE_FIRST_LETTER = "auto_capitalize_first_letter"
    private const val KEY_SYM_MAPPINGS_CUSTOM = "sym_mappings_custom"
    private const val KEY_AUTO_CORRECT_ENABLED = "auto_correct_enabled"
    private const val KEY_AUTO_CORRECT_ENABLED_LANGUAGES = "auto_correct_enabled_languages"
    
    // Valori di default
    private const val DEFAULT_LONG_PRESS_THRESHOLD = 500L
    private const val MIN_LONG_PRESS_THRESHOLD = 50L
    private const val MAX_LONG_PRESS_THRESHOLD = 1000L
    private const val DEFAULT_AUTO_CAPITALIZE_FIRST_LETTER = false
    private const val DEFAULT_AUTO_CORRECT_ENABLED = true
    
    /**
     * Ottiene le SharedPreferences per Pastiera.
     */
    fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Ottiene la soglia di long press in millisecondi.
     */
    fun getLongPressThreshold(context: Context): Long {
        return getPreferences(context).getLong(KEY_LONG_PRESS_THRESHOLD, DEFAULT_LONG_PRESS_THRESHOLD)
    }
    
    /**
     * Imposta la soglia di long press in millisecondi.
     * Il valore viene automaticamente limitato tra MIN e MAX.
     */
    fun setLongPressThreshold(context: Context, threshold: Long) {
        val clampedValue = threshold.coerceIn(MIN_LONG_PRESS_THRESHOLD, MAX_LONG_PRESS_THRESHOLD)
        getPreferences(context).edit()
            .putLong(KEY_LONG_PRESS_THRESHOLD, clampedValue)
            .apply()
    }
    
    /**
     * Ottiene il valore minimo consentito per la soglia di long press.
     */
    fun getMinLongPressThreshold(): Long = MIN_LONG_PRESS_THRESHOLD
    
    /**
     * Ottiene il valore massimo consentito per la soglia di long press.
     */
    fun getMaxLongPressThreshold(): Long = MAX_LONG_PRESS_THRESHOLD
    
    /**
     * Ottiene il valore di default per la soglia di long press.
     */
    fun getDefaultLongPressThreshold(): Long = DEFAULT_LONG_PRESS_THRESHOLD
    
    /**
     * Ottiene lo stato dell'auto-maiuscola per la prima lettera.
     */
    fun getAutoCapitalizeFirstLetter(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_AUTO_CAPITALIZE_FIRST_LETTER, DEFAULT_AUTO_CAPITALIZE_FIRST_LETTER)
    }
    
    /**
     * Imposta lo stato dell'auto-maiuscola per la prima lettera.
     */
    fun setAutoCapitalizeFirstLetter(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_AUTO_CAPITALIZE_FIRST_LETTER, enabled)
            .apply()
    }
    
    /**
     * Ottiene le mappature SYM personalizzate.
     * Restituisce una mappa vuota se non ci sono personalizzazioni.
     */
    fun getSymMappings(context: Context): Map<Int, String> {
        val prefs = getPreferences(context)
        val jsonString = prefs.getString(KEY_SYM_MAPPINGS_CUSTOM, null) ?: return emptyMap()
        
        return try {
            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")
            val keyCodeMap = mapOf(
                "KEYCODE_Q" to KeyEvent.KEYCODE_Q, "KEYCODE_W" to KeyEvent.KEYCODE_W,
                "KEYCODE_E" to KeyEvent.KEYCODE_E, "KEYCODE_R" to KeyEvent.KEYCODE_R,
                "KEYCODE_T" to KeyEvent.KEYCODE_T, "KEYCODE_Y" to KeyEvent.KEYCODE_Y,
                "KEYCODE_U" to KeyEvent.KEYCODE_U, "KEYCODE_I" to KeyEvent.KEYCODE_I,
                "KEYCODE_O" to KeyEvent.KEYCODE_O, "KEYCODE_P" to KeyEvent.KEYCODE_P,
                "KEYCODE_A" to KeyEvent.KEYCODE_A, "KEYCODE_S" to KeyEvent.KEYCODE_S,
                "KEYCODE_D" to KeyEvent.KEYCODE_D, "KEYCODE_F" to KeyEvent.KEYCODE_F,
                "KEYCODE_G" to KeyEvent.KEYCODE_G, "KEYCODE_H" to KeyEvent.KEYCODE_H,
                "KEYCODE_J" to KeyEvent.KEYCODE_J, "KEYCODE_K" to KeyEvent.KEYCODE_K,
                "KEYCODE_L" to KeyEvent.KEYCODE_L, "KEYCODE_Z" to KeyEvent.KEYCODE_Z,
                "KEYCODE_X" to KeyEvent.KEYCODE_X, "KEYCODE_C" to KeyEvent.KEYCODE_C,
                "KEYCODE_V" to KeyEvent.KEYCODE_V, "KEYCODE_B" to KeyEvent.KEYCODE_B,
                "KEYCODE_N" to KeyEvent.KEYCODE_N, "KEYCODE_M" to KeyEvent.KEYCODE_M
            )
            
            val result = mutableMapOf<Int, String>()
            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyCodeMap[keyName]
                val emoji = mappingsObject.getString(keyName)
                if (keyCode != null) {
                    result[keyCode] = emoji
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel caricamento delle mappature SYM personalizzate", e)
            emptyMap()
        }
    }
    
    /**
     * Salva le mappature SYM personalizzate.
     */
    fun saveSymMappings(context: Context, mappings: Map<Int, String>) {
        try {
            val keyCodeToName = mapOf(
                KeyEvent.KEYCODE_Q to "KEYCODE_Q", KeyEvent.KEYCODE_W to "KEYCODE_W",
                KeyEvent.KEYCODE_E to "KEYCODE_E", KeyEvent.KEYCODE_R to "KEYCODE_R",
                KeyEvent.KEYCODE_T to "KEYCODE_T", KeyEvent.KEYCODE_Y to "KEYCODE_Y",
                KeyEvent.KEYCODE_U to "KEYCODE_U", KeyEvent.KEYCODE_I to "KEYCODE_I",
                KeyEvent.KEYCODE_O to "KEYCODE_O", KeyEvent.KEYCODE_P to "KEYCODE_P",
                KeyEvent.KEYCODE_A to "KEYCODE_A", KeyEvent.KEYCODE_S to "KEYCODE_S",
                KeyEvent.KEYCODE_D to "KEYCODE_D", KeyEvent.KEYCODE_F to "KEYCODE_F",
                KeyEvent.KEYCODE_G to "KEYCODE_G", KeyEvent.KEYCODE_H to "KEYCODE_H",
                KeyEvent.KEYCODE_J to "KEYCODE_J", KeyEvent.KEYCODE_K to "KEYCODE_K",
                KeyEvent.KEYCODE_L to "KEYCODE_L", KeyEvent.KEYCODE_Z to "KEYCODE_Z",
                KeyEvent.KEYCODE_X to "KEYCODE_X", KeyEvent.KEYCODE_C to "KEYCODE_C",
                KeyEvent.KEYCODE_V to "KEYCODE_V", KeyEvent.KEYCODE_B to "KEYCODE_B",
                KeyEvent.KEYCODE_N to "KEYCODE_N", KeyEvent.KEYCODE_M to "KEYCODE_M"
            )
            
            val mappingsObject = JSONObject()
            for ((keyCode, emoji) in mappings) {
                val keyName = keyCodeToName[keyCode]
                if (keyName != null) {
                    mappingsObject.put(keyName, emoji)
                }
            }
            
            val jsonObject = JSONObject()
            jsonObject.put("mappings", mappingsObject)
            
            getPreferences(context).edit()
                .putString(KEY_SYM_MAPPINGS_CUSTOM, jsonObject.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel salvataggio delle mappature SYM personalizzate", e)
        }
    }
    
    /**
     * Resetta le mappature SYM personalizzate (torna ai default).
     */
    fun resetSymMappings(context: Context) {
        getPreferences(context).edit()
            .remove(KEY_SYM_MAPPINGS_CUSTOM)
            .apply()
    }
    
    /**
     * Verifica se ci sono mappature SYM personalizzate.
     */
    fun hasCustomSymMappings(context: Context): Boolean {
        val prefs = getPreferences(context)
        return prefs.contains(KEY_SYM_MAPPINGS_CUSTOM)
    }
    
    /**
     * Ottiene lo stato dell'auto-correzione.
     */
    fun getAutoCorrectEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_AUTO_CORRECT_ENABLED, DEFAULT_AUTO_CORRECT_ENABLED)
    }
    
    /**
     * Imposta lo stato dell'auto-correzione.
     */
    fun setAutoCorrectEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_AUTO_CORRECT_ENABLED, enabled)
            .apply()
    }
    
    /**
     * Ottiene l'elenco delle lingue abilitate per l'auto-correzione.
     * @return Set di codici lingua (es. "it", "en")
     */
    fun getAutoCorrectEnabledLanguages(context: Context): Set<String> {
        val prefs = getPreferences(context)
        val languagesString = prefs.getString(KEY_AUTO_CORRECT_ENABLED_LANGUAGES, null)
        return if (languagesString != null && languagesString.isNotEmpty()) {
            languagesString.split(",").toSet()
        } else {
            // Default: tutte le lingue disponibili sono abilitate
            setOf("it", "en")
        }
    }
    
    /**
     * Imposta l'elenco delle lingue abilitate per l'auto-correzione.
     * @param languages Set di codici lingua (es. "it", "en")
     */
    fun setAutoCorrectEnabledLanguages(context: Context, languages: Set<String>) {
        val languagesString = languages.joinToString(",")
        getPreferences(context).edit()
            .putString(KEY_AUTO_CORRECT_ENABLED_LANGUAGES, languagesString)
            .apply()
    }
    
    /**
     * Verifica se una lingua è abilitata per l'auto-correzione.
     */
    fun isAutoCorrectLanguageEnabled(context: Context, language: String): Boolean {
        val enabledLanguages = getAutoCorrectEnabledLanguages(context)
        // Se la lista è vuota, tutte le lingue sono abilitate (comportamento default)
        return enabledLanguages.isEmpty() || enabledLanguages.contains(language)
    }
    
    /**
     * Campo speciale nel JSON per il nome della lingua.
     */
    private const val LANGUAGE_NAME_KEY = "__name"
    
    /**
     * Ottiene le correzioni personalizzate per una lingua.
     */
    fun getCustomAutoCorrections(context: Context, languageCode: String): Map<String, String> {
        val prefs = getPreferences(context)
        val key = "auto_correct_custom_$languageCode"
        val jsonString = prefs.getString(key, null) ?: return emptyMap()
        
        return try {
            val jsonObject = JSONObject(jsonString)
            val corrections = mutableMapOf<String, String>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val correctionKey = keys.next()
                // Salta il campo speciale del nome
                if (correctionKey != LANGUAGE_NAME_KEY) {
                    val value = jsonObject.getString(correctionKey)
                    corrections[correctionKey] = value
                }
            }
            corrections
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel caricamento delle correzioni personalizzate per $languageCode", e)
            emptyMap()
        }
    }
    
    /**
     * Ottiene il nome visualizzato di una lingua personalizzata dal JSON.
     */
    fun getCustomLanguageName(context: Context, languageCode: String): String? {
        val prefs = getPreferences(context)
        val key = "auto_correct_custom_$languageCode"
        val jsonString = prefs.getString(key, null) ?: return null
        
        return try {
            val jsonObject = JSONObject(jsonString)
            if (jsonObject.has(LANGUAGE_NAME_KEY)) {
                jsonObject.getString(LANGUAGE_NAME_KEY)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel caricamento del nome della lingua per $languageCode", e)
            null
        }
    }
    
    /**
     * Salva le correzioni personalizzate per una lingua.
     * @param languageName Il nome visualizzato della lingua (opzionale, se null non viene salvato/aggiornato)
     */
    fun saveCustomAutoCorrections(
        context: Context, 
        languageCode: String, 
        corrections: Map<String, String>,
        languageName: String? = null
    ) {
        try {
            val jsonObject = JSONObject()
            
            // Salva il nome della lingua se fornito
            if (languageName != null) {
                jsonObject.put(LANGUAGE_NAME_KEY, languageName)
            } else {
                // Se non fornito, prova a mantenere il nome esistente
                val existingName = getCustomLanguageName(context, languageCode)
                if (existingName != null) {
                    jsonObject.put(LANGUAGE_NAME_KEY, existingName)
                }
            }
            
            // Salva le correzioni
            corrections.forEach { (key, value) ->
                // Salta il campo speciale se presente nelle correzioni
                if (key != LANGUAGE_NAME_KEY) {
                    jsonObject.put(key, value)
                }
            }
            
            val key = "auto_correct_custom_$languageCode"
            getPreferences(context).edit()
                .putString(key, jsonObject.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel salvataggio delle correzioni personalizzate per $languageCode", e)
        }
    }
    
    /**
     * Aggiorna solo il nome di una lingua personalizzata.
     */
    fun updateCustomLanguageName(context: Context, languageCode: String, languageName: String) {
        try {
            val prefs = getPreferences(context)
            val key = "auto_correct_custom_$languageCode"
            val jsonString = prefs.getString(key, null)
            
            val jsonObject = if (jsonString != null) {
                JSONObject(jsonString)
            } else {
                JSONObject()
            }
            
            jsonObject.put(LANGUAGE_NAME_KEY, languageName)
            
            prefs.edit()
                .putString(key, jsonObject.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'aggiornamento del nome della lingua per $languageCode", e)
        }
    }
}

