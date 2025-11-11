package it.palsoftware.pastiera.inputmethod

import android.util.Log
import android.view.View
import android.view.inputmethod.InputConnection

/**
 * Gestisce i click sui pulsanti delle variazioni.
 */
object VariationButtonHandler {
    private const val TAG = "VariationButtonHandler"
    
    /**
     * Callback chiamato quando viene selezionata una variazione.
     */
    interface OnVariationSelectedListener {
        /**
         * Chiamato quando viene selezionata una variazione.
         * @param variation Il carattere di variazione selezionato
         */
        fun onVariationSelected(variation: String)
    }
    
    /**
     * Crea un listener per un pulsante di variazione.
     * Quando viene cliccato, cancella il carattere prima del cursore e inserisce la variazione.
     */
    fun createVariationClickListener(
        variation: String,
        inputConnection: InputConnection?,
        listener: OnVariationSelectedListener? = null
    ): View.OnClickListener {
        return View.OnClickListener {
            Log.d(TAG, "Click su pulsante variazione: $variation")
            
            if (inputConnection == null) {
                Log.w(TAG, "Nessun inputConnection disponibile per inserire la variazione")
                return@OnClickListener
            }
            
            // Cancella il carattere prima del cursore (backspace)
            val deleted = inputConnection.deleteSurroundingText(1, 0)
            if (deleted) {
                Log.d(TAG, "Carattere prima del cursore cancellato")
            } else {
                Log.w(TAG, "Impossibile cancellare il carattere prima del cursore")
            }
            
            // Inserisci la variazione
            inputConnection.commitText(variation, 1)
            Log.d(TAG, "Variazione '$variation' inserita")
            
            // Notifica il listener se presente
            listener?.onVariationSelected(variation)
        }
    }
}

