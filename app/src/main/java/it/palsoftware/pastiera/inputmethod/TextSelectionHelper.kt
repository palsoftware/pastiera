package it.palsoftware.pastiera.inputmethod

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.util.Log

/**
 * Helper per gestire operazioni di selezione del testo.
 */
object TextSelectionHelper {
    private const val TAG = "TextSelectionHelper"
    
    /**
     * Espande la selezione di un carattere verso sinistra.
     * Se non c'è selezione, crea una selezione di un carattere a sinistra del cursore.
     */
    fun expandSelectionLeft(inputConnection: InputConnection): Boolean {
        try {
            // Ottieni la selezione corrente usando ExtractedTextRequest
            val extractedText = inputConnection.getExtractedText(
                ExtractedTextRequest().apply {
                    flags = android.view.inputmethod.ExtractedText.FLAG_SELECTING
                },
                0
            )
            
            if (extractedText == null) {
                // Fallback: usa getTextBeforeCursor e getTextAfterCursor per stimare la posizione
                val textBefore = inputConnection.getTextBeforeCursor(1000, 0)
                val textAfter = inputConnection.getTextAfterCursor(1, 0)
                
                if (textBefore != null && textBefore.isNotEmpty()) {
                    // Se c'è testo dopo, probabilmente c'è una selezione
                    // Per semplicità, assumiamo che il cursore sia alla fine del testo prima
                    val currentPos = textBefore.length
                    val newStart = currentPos - 1
                    
                    if (newStart >= 0) {
                        // Crea o espande la selezione di un carattere a sinistra
                        inputConnection.setSelection(newStart, currentPos)
                        Log.d(TAG, "expandSelectionLeft: selezione creata/espansa a [$newStart, $currentPos]")
                        return true
                    }
                }
                return false
            }
            
            val selectionStart = extractedText.selectionStart
            val selectionEnd = extractedText.selectionEnd
            
            if (selectionStart < 0 || selectionEnd < 0) {
                Log.d(TAG, "expandSelectionLeft: impossibile ottenere la selezione")
                return false
            }
            
            // Ottieni il testo prima del cursore per verificare che ci sia testo
            val textBefore = inputConnection.getTextBeforeCursor(1, 0)
            
            if (textBefore != null && textBefore.isNotEmpty()) {
                val newStart: Int
                
                if (selectionStart == selectionEnd) {
                    // Nessuna selezione: crea una selezione di un carattere a sinistra
                    newStart = selectionStart - 1
                } else {
                    // C'è già una selezione: espandila di un carattere a sinistra
                    newStart = selectionStart - 1
                }
                
                // Assicurati che newStart non sia negativo
                if (newStart >= 0) {
                    inputConnection.setSelection(newStart, selectionEnd)
                    Log.d(TAG, "expandSelectionLeft: selezione espansa da [$selectionStart, $selectionEnd] a [$newStart, $selectionEnd]")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore in expandSelectionLeft", e)
        }
        return false
    }
    
    /**
     * Espande la selezione di un carattere verso destra.
     * Se non c'è selezione, crea una selezione di un carattere a destra del cursore.
     */
    fun expandSelectionRight(inputConnection: InputConnection): Boolean {
        try {
            // Ottieni la selezione corrente usando ExtractedTextRequest
            val extractedText = inputConnection.getExtractedText(
                ExtractedTextRequest().apply {
                    flags = android.view.inputmethod.ExtractedText.FLAG_SELECTING
                },
                0
            )
            
            if (extractedText == null) {
                // Fallback: usa getTextBeforeCursor e getTextAfterCursor per stimare la posizione
                val textBefore = inputConnection.getTextBeforeCursor(1000, 0)
                val textAfter = inputConnection.getTextAfterCursor(1000, 0)
                
                if (textAfter != null && textAfter.isNotEmpty()) {
                    // Se c'è testo dopo, possiamo espandere la selezione
                    val currentPos = textBefore?.length ?: 0
                    val newEnd = currentPos + 1
                    
                    // Crea o espande la selezione di un carattere a destra
                    inputConnection.setSelection(currentPos, newEnd)
                    Log.d(TAG, "expandSelectionRight: selezione creata/espansa a [$currentPos, $newEnd]")
                    return true
                }
                return false
            }
            
            val selectionStart = extractedText.selectionStart
            val selectionEnd = extractedText.selectionEnd
            
            if (selectionStart < 0 || selectionEnd < 0) {
                Log.d(TAG, "expandSelectionRight: impossibile ottenere la selezione")
                return false
            }
            
            // Ottieni il testo dopo il cursore per verificare che ci sia testo
            val textAfter = inputConnection.getTextAfterCursor(1, 0)
            
            if (textAfter != null && textAfter.isNotEmpty()) {
                val newEnd: Int
                
                if (selectionStart == selectionEnd) {
                    // Nessuna selezione: crea una selezione di un carattere a destra
                    newEnd = selectionEnd + 1
                } else {
                    // C'è già una selezione: espandila di un carattere a destra
                    newEnd = selectionEnd + 1
                }
                
                // Verifica che newEnd non superi la lunghezza del testo
                val fullText = extractedText.text?.toString() ?: ""
                if (newEnd <= fullText.length) {
                    inputConnection.setSelection(selectionStart, newEnd)
                    Log.d(TAG, "expandSelectionRight: selezione espansa da [$selectionStart, $selectionEnd] a [$selectionStart, $newEnd]")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore in expandSelectionRight", e)
        }
        return false
    }
    
    /**
     * Cancella l'ultima parola prima del cursore.
     */
    fun deleteLastWord(inputConnection: InputConnection): Boolean {
        try {
            // Ottieni il testo prima del cursore (fino a 100 caratteri)
            val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0)
            
            if (textBeforeCursor != null && textBeforeCursor.isNotEmpty()) {
                // Trova l'ultima parola (separata da spazi o all'inizio del testo)
                var endIndex = textBeforeCursor.length
                var startIndex = endIndex
                
                // Trova la fine dell'ultima parola (ignora spazi alla fine)
                while (startIndex > 0 && textBeforeCursor[startIndex - 1].isWhitespace()) {
                    startIndex--
                }
                
                // Trova l'inizio dell'ultima parola (primo spazio o inizio del testo)
                while (startIndex > 0 && !textBeforeCursor[startIndex - 1].isWhitespace()) {
                    startIndex--
                }
                
                // Calcola quanti caratteri cancellare
                val charsToDelete = endIndex - startIndex
                
                if (charsToDelete > 0) {
                    // Cancella l'ultima parola (inclusi eventuali spazi dopo)
                    inputConnection.deleteSurroundingText(charsToDelete, 0)
                    Log.d(TAG, "deleteLastWord: cancellati $charsToDelete caratteri")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore in deleteLastWord", e)
        }
        return false
    }
}

