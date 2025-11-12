package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import org.json.JSONObject

/**
 * Gestisce l'auto-correzione di accenti, apostrofi e contrazioni.
 * Corregge automaticamente pattern comuni quando viene premuto spazio o punteggiatura.
 * Supporta l'annullamento con backspace (solo come prima azione dopo la correzione).
 * Preserva la capitalizzazione originale (Cos e → Cos'è, non cos'è).
 */
object AutoCorrector {
    private const val TAG = "AutoCorrector"

    private val corrections = mutableMapOf<String, Map<String, String>>()

    /**
     * Informazioni sull'ultima correzione applicata.
     * La correzione rimane annullabile finché non viene premuto un tasto (tranne backspace).
     */
    data class LastCorrection(
        val originalWord: String,
        val correctedWord: String,
        val correctionLength: Int // Lunghezza della parola corretta inserita
    )

    // Traccia l'ultima correzione applicata (null se non c'è o se è stata accettata)
    private var lastCorrection: LastCorrection? = null

    // Traccia le parole che sono state rifiutate (annullate con backspace)
    // Queste parole non verranno corrette finché l'utente non modifica il testo
    private val rejectedWords = mutableSetOf<String>()

    // Traccia le lingue personalizzate caricate da file esterni
    private val customLanguages = mutableSetOf<String>()

    /**
     * Carica le regole di auto-correzione dai file JSON per lingua.
     * I file devono essere nella cartella assets con nome: auto_corrections_{locale}.json
     * Esempio: auto_corrections_it.json, auto_corrections_en.json
     * Supporta anche il caricamento di file JSON personalizzati.
     */
    fun loadCorrections(assets: AssetManager, context: Context? = null) {
        try {
            corrections.clear()
            customLanguages.clear()

            // Lista delle lingue supportate di default
            val standardLocales = listOf("it", "en", "es", "fr", "de")

            for (locale in standardLocales) {
                try {
                    // Prima carica le correzioni personalizzate (se esistono)
                    if (context != null) {
                        val customCorrections = it.palsoftware.pastiera.SettingsManager.getCustomAutoCorrections(context, locale)
                        if (customCorrections.isNotEmpty()) {
                            // Carica le correzioni personalizzate
                            val customJson = correctionsToJson(customCorrections)
                            loadCorrectionsFromJson(locale, customJson)
                            // Non aggiungere lingue standard a customLanguages - sono solo modifiche, non lingue nuove
                            Log.d(TAG, "Caricate ${customCorrections.size} correzioni personalizzate per locale: $locale")
                            continue // Salta il caricamento del file di default
                        }
                    }
                    
                    // Se non ci sono personalizzazioni, carica il file di default
                    val fileName = "auto_corrections_$locale.json"
                    val jsonString = assets.open(fileName).bufferedReader().use { it.readText() }
                    loadCorrectionsFromJson(locale, jsonString)
                } catch (e: Exception) {
                    // File non trovato o errore di parsing - ignora questa lingua
                    Log.d(TAG, "Nessun file di correzione trovato per locale: $locale")
                }
            }

            // Rimuovi eventuali lingue standard da customLanguages (non dovrebbero esserci, ma per sicurezza)
            customLanguages.removeAll(standardLocales)
            
            // Carica anche le lingue personalizzate aggiuntive (non standard)
            if (context != null) {
                try {
                    val prefs = it.palsoftware.pastiera.SettingsManager.getPreferences(context)
                    val allPrefs = prefs.all
                    
                    // Cerca tutte le chiavi che iniziano con "auto_correct_custom_"
                    for ((key, value) in allPrefs) {
                        if (key.startsWith("auto_correct_custom_") && value is String) {
                            val languageCode = key.removePrefix("auto_correct_custom_")
                            
                            // Salta le lingue standard (già caricate sopra)
                            if (languageCode !in standardLocales) {
                                try {
                                    val customCorrections = it.palsoftware.pastiera.SettingsManager.getCustomAutoCorrections(context, languageCode)
                                    if (customCorrections.isNotEmpty()) {
                                        val customJson = correctionsToJson(customCorrections)
                                        loadCorrectionsFromJson(languageCode, customJson)
                                        customLanguages.add(languageCode)
                                        Log.d(TAG, "Caricate ${customCorrections.size} correzioni per lingua personalizzata: $languageCode")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Errore nel caricamento della lingua personalizzata $languageCode", e)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Errore nel caricamento delle lingue personalizzate", e)
                }
            }
            
            Log.d(TAG, "Totale lingue caricate: ${corrections.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel caricamento delle correzioni", e)
        }
    }
    
    /**
     * Converte una mappa di correzioni in JSON string.
     */
    private fun correctionsToJson(corrections: Map<String, String>): String {
        val jsonObject = JSONObject()
        corrections.forEach { (key, value) ->
            jsonObject.put(key, value)
        }
        return jsonObject.toString()
    }

    /**
     * Carica correzioni da un file JSON personalizzato.
     * @param locale Il codice lingua (es. "it", "en", "custom1")
     * @param jsonString Il contenuto del file JSON
     */
    fun loadCustomCorrections(locale: String, jsonString: String) {
        try {
            loadCorrectionsFromJson(locale, jsonString)
            // Aggiungi a customLanguages solo se non è una lingua standard
            val standardLocales = listOf("it", "en", "es", "fr", "de")
            if (locale !in standardLocales) {
                customLanguages.add(locale)
            }
            Log.d(TAG, "Caricate correzioni personalizzate per locale: $locale")
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel caricamento delle correzioni personalizzate per $locale", e)
        }
    }

    /**
     * Carica correzioni da una stringa JSON.
     * Ignora il campo speciale "__name" che contiene il nome della lingua.
     */
    private fun loadCorrectionsFromJson(locale: String, jsonString: String) {
        val jsonObject = JSONObject(jsonString)
        val correctionMap = mutableMapOf<String, String>()

        // Il file JSON contiene un oggetto con chiavi che sono le parole da correggere
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            // Salta il campo speciale del nome
            if (key != "__name") {
                val value = jsonObject.getString(key)
                correctionMap[key] = value
            }
        }

        if (correctionMap.isNotEmpty()) {
            corrections[locale] = correctionMap
            Log.d(TAG, "Caricate ${correctionMap.size} correzioni per locale: $locale")
        }
    }

    /**
     * Ottiene tutte le lingue disponibili (incluse quelle personalizzate).
     */
    fun getAllAvailableLanguages(): Set<String> {
        return corrections.keys.toSet()
    }

    /**
     * Ottiene solo le lingue personalizzate.
     */
    fun getCustomLanguages(): Set<String> {
        return customLanguages.toSet()
    }

    /**
     * Ottiene la locale corrente basata sulla lingua del dispositivo.
     */
    private fun getCurrentLocale(context: Context): String {
        val locale = context.resources.configuration.locales[0]
        return locale.language.lowercase()
    }

    /**
     * Ottiene tutte le locale supportate.
     */
    fun getSupportedLocales(): Set<String> {
        return corrections.keys.toSet()
    }

    /**
     * Applica la capitalizzazione originale alla parola corretta.
     * Gestisce correttamente anche caratteri speciali all'inizio (apostrofi, ecc.).
     *
     * @param originalWord La parola originale (es. "Cos", "CASA", "cos")
     * @param correctedWord La parola corretta in minuscolo (es. "cos'è", "casa")
     * @return La parola corretta con capitalizzazione preservata (es. "Cos'è", "CASA", "cos'è")
     */
    private fun applyCapitalization(originalWord: String, correctedWord: String): String {
        if (originalWord.isEmpty() || correctedWord.isEmpty()) {
            return correctedWord
        }

        val originalLower = originalWord.lowercase()
        val correctedLower = correctedWord.lowercase()

        // Se la parola originale era tutta maiuscola, applica maiuscolo a tutta la correzione
        if (originalWord == originalWord.uppercase() && originalWord.any { it.isLetter() }) {
            // Trova la prima lettera alfabetica nella correzione (potrebbe essere dopo apostrofi)
            val firstLetterIndex = correctedWord.indexOfFirst { it.isLetter() }
            if (firstLetterIndex >= 0) {
                val beforeLetter = correctedWord.substring(0, firstLetterIndex)
                val fromLetter = correctedWord.substring(firstLetterIndex)
                return beforeLetter + fromLetter.uppercase()
            }
            return correctedWord.uppercase()
        }

        // Se la prima lettera della parola originale era maiuscola, metti in maiuscolo la prima lettera della correzione
        if (originalWord.isNotEmpty() && originalWord[0].isUpperCase()) {
            // Trova la prima lettera alfabetica nella correzione (potrebbe essere dopo apostrofi/punteggiatura)
            val firstLetterIndex = correctedWord.indexOfFirst { it.isLetter() }
            if (firstLetterIndex >= 0) {
                val beforeLetter = correctedWord.substring(0, firstLetterIndex)
                val firstLetter = correctedWord[firstLetterIndex]
                val afterLetter = correctedWord.substring(firstLetterIndex + 1)

                // Metti in maiuscolo la prima lettera alfabetica
                return beforeLetter + firstLetter.uppercaseChar() + afterLetter
            }
        }

        // Altrimenti, mantieni la correzione in minuscolo
        return correctedWord
    }

    /**
     * Controlla se una parola deve essere corretta.
     * @param word La parola da controllare (senza spazi finali)
     * @param locale La locale da usare (es. "it", "en"). Se null, usa la locale del dispositivo.
     * @param context Il contesto per verificare le lingue abilitate
     * @return La parola corretta con capitalizzazione preservata, o null se non c'è correzione.
     */
    fun getCorrection(word: String, locale: String? = null, context: Context? = null): String? {
        val targetLocale = locale ?: (context?.let { getCurrentLocale(it) } ?: "en")

        // Verifica se la lingua è abilitata
        if (context != null) {
            val enabledLanguages = it.palsoftware.pastiera.SettingsManager.getAutoCorrectEnabledLanguages(context)
            if (enabledLanguages.isNotEmpty() && !enabledLanguages.contains(targetLocale)) {
                // Lingua non abilitata, prova con fallback solo se "en" è abilitata
                if (enabledLanguages.contains("en") && targetLocale != "en") {
                    // Prova con "en" come fallback
                    corrections["en"]?.let { enCorrections ->
                        val wordLower = word.lowercase()
                        enCorrections[wordLower]?.let { correction ->
                            return applyCapitalization(word, correction)
                        }
                    }
                }
                return null
            }
        }

        // Cerca la correzione usando la parola in minuscolo (le chiavi nel JSON sono in minuscolo)
        val wordLower = word.lowercase()

        // Prova prima con la locale specifica
        corrections[targetLocale]?.let { localeCorrections ->
            localeCorrections[wordLower]?.let { correction ->
                // Applica la capitalizzazione originale alla correzione
                return applyCapitalization(word, correction)
            }
        }

        // Fallback: prova con "en" se la locale target non ha corrispondenze
        if (targetLocale != "en") {
            // Verifica se "en" è abilitata (se abbiamo un contesto)
            if (context == null || it.palsoftware.pastiera.SettingsManager.isAutoCorrectLanguageEnabled(context, "en")) {
                corrections["en"]?.let { enCorrections ->
                    enCorrections[wordLower]?.let { correction ->
                        // Applica la capitalizzazione originale alla correzione
                        return applyCapitalization(word, correction)
                    }
                }
            }
        }

        return null
    }

    /**
     * Processa il testo prima del cursore e applica correzioni se necessario.
     * Supporta sia parole singole che pattern con spazi (es. "cos e" → "cos'è").
     * @param textBeforeCursor Il testo prima del cursore
     * @param locale La locale da usare
     * @param context Il contesto per ottenere la locale se non specificata
     * @return Pair<wordToReplace, correctedWord> se c'è una correzione, null altrimenti
     */
    fun processText(
        textBeforeCursor: CharSequence?,
        locale: String? = null,
        context: Context? = null
    ): Pair<String, String>? {
        if (textBeforeCursor == null || textBeforeCursor.isEmpty()) {
            return null
        }

        val text = textBeforeCursor.toString()
        var endIndex = text.length

        // Ignora spazi e punteggiatura alla fine
        while (endIndex > 0 && (text[endIndex - 1].isWhitespace() ||
                                text[endIndex - 1] in ".,;:!?()[]{}\"'")) {
            endIndex--
        }

        if (endIndex == 0) {
            return null
        }

        // Ottieni le lingue abilitate se abbiamo un contesto
        val enabledLanguages = if (context != null) {
            it.palsoftware.pastiera.SettingsManager.getAutoCorrectEnabledLanguages(context)
        } else {
            emptySet<String>()
        }
        
        // Se ci sono lingue abilitate specifiche, usa quelle, altrimenti usa tutte le lingue disponibili
        val languagesToSearch = if (enabledLanguages.isNotEmpty()) {
            enabledLanguages
        } else {
            corrections.keys.toSet()
        }
        
        // Se non ci sono lingue disponibili, esci
        if (languagesToSearch.isEmpty()) {
            return null
        }

        // Prima, prova a cercare pattern che includono spazi (es. "cos e")
        // Cerchiamo fino a 3 "parole" (separate da spazi) prima del cursore
        // Questo permette di trovare pattern come "cos e", "qual e", ecc.
        for (maxWords in 2 downTo 1) {
            var currentEnd = endIndex
            var wordsFound = 0
            var startIndex = currentEnd

            // Trova l'inizio della sequenza di maxWords parole
            while (startIndex > 0 && wordsFound < maxWords) {
                // Vai indietro fino a trovare uno spazio o punteggiatura
                var tempIndex = startIndex - 1
                while (tempIndex > 0 && !text[tempIndex - 1].isWhitespace() &&
                       text[tempIndex - 1] !in ".,;:!?()[]{}\"'") {
                    tempIndex--
                }

                if (tempIndex < startIndex) {
                    wordsFound++
                    if (wordsFound < maxWords) {
                        // Vai indietro oltre lo spazio per trovare la prossima parola
                        while (tempIndex > 0 && (text[tempIndex - 1].isWhitespace() ||
                                                 text[tempIndex - 1] in ".,;:!?()[]{}\"'")) {
                            tempIndex--
                        }
                        startIndex = tempIndex
                    } else {
                        startIndex = tempIndex
                    }
                } else {
                    break
                }
            }

            if (startIndex < currentEnd && wordsFound == maxWords) {
                // Estrai la sequenza (può contenere spazi se maxWords > 1)
                val sequence = text.substring(startIndex, currentEnd).trim()
                if (sequence.isNotEmpty()) {
                    // Controlla se questa sequenza è stata rifiutata
                    val sequenceLower = sequence.lowercase()
                    if (rejectedWords.contains(sequenceLower)) {
                        Log.d(TAG, "Sequenza '$sequence' è stata rifiutata, non correggere")
                        continue // Prova con meno parole
                    }

                    // Controlla se c'è una correzione per questa sequenza in una delle lingue abilitate
                    for (lang in languagesToSearch) {
                        val correction = getCorrection(sequence, lang, context)
                        if (correction != null) {
                            Log.d(TAG, "Trovata correzione per sequenza multi-parola: '$sequence' → '$correction' (lingua: $lang)")
                            return Pair(sequence, correction)
                        }
                    }
                }
            }
        }

        // Se non abbiamo trovato pattern con spazi, cerca una singola parola
        var startIndex = endIndex
        while (startIndex > 0 && !text[startIndex - 1].isWhitespace() &&
               text[startIndex - 1] !in ".,;:!?()[]{}\"'") {
            startIndex--
        }

        if (startIndex >= endIndex) {
            return null
        }

        val word = text.substring(startIndex, endIndex)
        if (word.isEmpty()) {
            return null
        }

        // Controlla se questa parola è stata rifiutata (annullata con backspace)
        val wordLower = word.lowercase()
        if (rejectedWords.contains(wordLower)) {
            Log.d(TAG, "Parola '$word' è stata rifiutata, non correggere")
            return null
        }

        // Controlla se c'è una correzione per la singola parola in una delle lingue abilitate
        for (lang in languagesToSearch) {
            val correction = getCorrection(word, lang, context)
            if (correction != null) {
                Log.d(TAG, "Trovata correzione per parola: '$word' → '$correction' (lingua: $lang)")
                return Pair(word, correction)
            }
        }

        return null
    }

    /**
     * Registra una correzione applicata.
     * La correzione rimane annullabile finché non viene premuto un tasto (tranne backspace).
     * @param originalWord La parola originale
     * @param correctedWord La parola corretta
     */
    fun recordCorrection(originalWord: String, correctedWord: String) {
        lastCorrection = LastCorrection(
            originalWord = originalWord,
            correctedWord = correctedWord,
            correctionLength = correctedWord.length
        )
        Log.d(TAG, "Correzione registrata: '$originalWord' → '$correctedWord'")
    }

    /**
     * Ottiene l'ultima correzione se è ancora annullabile.
     * @return Le informazioni sulla correzione se può essere annullata, null altrimenti
     */
    fun getLastCorrection(): LastCorrection? {
        return lastCorrection
    }

    /**
     * Accetta l'ultima correzione (viene chiamato quando viene premuto un tasto diverso da backspace).
     * Dopo l'accettazione, la correzione non può più essere annullata.
     */
    fun acceptLastCorrection() {
        if (lastCorrection != null) {
            Log.d(TAG, "Correzione accettata: '${lastCorrection!!.correctedWord}'")
            lastCorrection = null
        }
    }

    /**
     * Annulla l'ultima correzione.
     * Dopo l'annullamento, la correzione non può più essere annullata.
     * La parola originale viene aggiunta alla lista delle parole rifiutate
     * per evitare di riproporre la stessa correzione immediatamente.
     * @return Le informazioni sulla correzione annullata, null se non c'era nessuna correzione
     */
    fun undoLastCorrection(): LastCorrection? {
        val correction = lastCorrection
        if (correction != null) {
            Log.d(TAG, "Correzione annullata: '${correction.correctedWord}' → '${correction.originalWord}'")

            // Aggiungi la parola originale alla lista delle parole rifiutate
            // (usiamo minuscolo per il confronto, così funziona indipendentemente dalla capitalizzazione)
            rejectedWords.add(correction.originalWord.lowercase())
            Log.d(TAG, "Parola '${correction.originalWord}' aggiunta alla lista rifiutate")

            lastCorrection = null
            return correction
        }
        return null
    }

    /**
     * Resetta la lista delle parole rifiutate.
     * Viene chiamato quando l'utente digita un nuovo carattere (non backspace),
     * indicando che potrebbe aver modificato il testo e quindi le correzioni rifiutate
     * potrebbero non essere più valide.
     */
    fun clearRejectedWords() {
        if (rejectedWords.isNotEmpty()) {
            Log.d(TAG, "Reset lista parole rifiutate (${rejectedWords.size} parole)")
            rejectedWords.clear()
        }
    }
}

