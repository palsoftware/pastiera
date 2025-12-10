package it.palsoftware.pastiera.core.suggestions

import java.util.Locale

/**
 * Helper per applicare la capitalizzazione corretta ai suggerimenti
 * in base al pattern della parola digitata dall'utente.
 */
object CasingHelper {

    private fun capitalizeFirstLetter(candidate: String): String {
        val idx = candidate.indexOfFirst { it.isLetter() }
        if (idx < 0) return candidate
        val first = candidate[idx]
        val cap = if (first.isLowerCase()) first.titlecase(Locale.getDefault()) else first.toString()
        return candidate.substring(0, idx) + cap + candidate.substring(idx + 1)
    }

    /**
     * Applica la capitalizzazione del suggerimento in base al pattern della parola originale.
     * 
     * @param candidate La parola suggerita (es. "Parenzo")
     * @param original La parola digitata dall'utente (es. "parenz", "Parenz", "PARENZ")
     * @param forceLeadingCapital Se true, forza la prima lettera maiuscola (per auto-capitalize)
     * @return La parola con la capitalizzazione corretta
     */
    fun applyCasing(
        candidate: String,
        original: String,
        forceLeadingCapital: Boolean = false
    ): String {
        if (candidate.isEmpty()) return candidate
        
        // Se il campo richiede capitalizzazione forzata, applica titlecase
        if (forceLeadingCapital) {
            return capitalizeFirstLetter(candidate)
        }
        
        if (original.isEmpty()) return candidate
        
        // Determina il pattern di capitalizzazione considerando solo le lettere (ignora apostrofi/punteggiatura)
        val letters = original.filter { it.isLetter() }
        if (letters.isEmpty()) return candidate

        val allUpper = letters.all { it.isUpperCase() }
        val allLower = letters.all { it.isLowerCase() }
        val firstLetter = letters.first()
        val restLetters = letters.drop(1)
        val firstUpper = firstLetter.isUpperCase()
        val restLower = restLetters.all { it.isLowerCase() }

        // Se il candidato contiene maiuscole e non siamo in caso "allUpper" (>=2 lettere maiuscole),
        // rispetta il casing del dizionario così com'è.
        val candidateHasUpper = candidate.any { it.isUpperCase() }
        val candidateLettersUpperCount = candidate.count { it.isUpperCase() }
        if (!forceLeadingCapital && candidateHasUpper && candidateLettersUpperCount < 2) {
            return candidate
        }
        // Se l'originale è tutto minuscolo ma il candidato ha maiuscole (es. "mccartney" -> "McCartney"),
        // preserva il casing del candidato.
        if (allLower && candidateHasUpper) {
            return candidate
        }
        
        return when {
            // Caso: PARENZ -> PARENZO (tutto maiuscolo)
            allUpper -> candidate.uppercase(Locale.getDefault())
            // Caso: Parenz -> Parenzo (prima maiuscola, resto minuscolo)
            firstUpper && restLower -> capitalizeFirstLetter(candidate)
            // Caso: parenz -> parenzo (tutto minuscolo)
            allLower -> candidate.lowercase(Locale.getDefault())
            // Altri casi: usa il suggerimento così com'è
            else -> candidate
        }
    }
}

