package it.palsoftware.pastiera.inputmethod

import android.util.Log
import android.view.View
import android.view.inputmethod.InputConnection

/**
 * Handles clicks on variation buttons.
 */
object VariationButtonHandler {
    private const val TAG = "VariationButtonHandler"
    
    /**
     * Callback called when a variation is selected.
     */
    interface OnVariationSelectedListener {
        /**
         * Called when a variation is selected.
         * @param variation The selected variation character
         */
        fun onVariationSelected(variation: String)
    }
    
    /**
     * Creates a listener for a variation button (accent/letter variations).
     * Deletes a single character before the cursor and inserts the variation.
     */
    fun createVariationClickListener(
        variation: String,
        inputConnection: InputConnection?,
        listener: OnVariationSelectedListener? = null
    ): View.OnClickListener {
        return View.OnClickListener {
            Log.d(TAG, "Click on variation button: $variation")
            
            if (inputConnection == null) {
                Log.w(TAG, "No inputConnection available to insert variation")
                return@OnClickListener
            }

            val deleted = inputConnection.deleteSurroundingText(1, 0)
            if (deleted) {
                Log.d(TAG, "Character before cursor deleted")
            } else {
                Log.w(TAG, "Unable to delete character before cursor")
            }

            inputConnection.commitText(variation, 1)
            Log.d(TAG, "Variation '$variation' inserted")
            
            // Notify listener if present
            listener?.onVariationSelected(variation)
        }
    }

    /**
     * Creates a listener for a static variation button.
     * When clicked, inserts the variation without deleting the character before the cursor.
     */
    fun createStaticVariationClickListener(
        variation: String,
        inputConnection: InputConnection?,
        listener: OnVariationSelectedListener? = null
    ): View.OnClickListener {
        return View.OnClickListener {
            Log.d(TAG, "Click on static variation button: $variation")

            if (inputConnection == null) {
                Log.w(TAG, "No inputConnection available to insert static variation")
                return@OnClickListener
            }

            // Insert variation without deleting previous character
            inputConnection.commitText(variation, 1)
            Log.d(TAG, "Static variation '$variation' inserted")

            // Notify listener if present
            listener?.onVariationSelected(variation)
        }
    }

}
