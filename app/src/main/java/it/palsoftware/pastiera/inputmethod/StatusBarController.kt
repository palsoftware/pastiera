package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.util.TypedValue

/**
 * Gestisce la status bar visualizzata dall'IME, occupandosi della creazione della view
 * e dell'aggiornamento del testo/stile in base allo stato dei modificatori.
 */
class StatusBarController(
    private val context: Context
) {
    // Listener per la selezione delle variazioni
    var onVariationSelectedListener: VariationButtonHandler.OnVariationSelectedListener? = null

    companion object {
        private const val NAV_MODE_LABEL = "NAV MODE"
        private val DEFAULT_BACKGROUND = Color.parseColor("#2196F3")
        private val NAV_MODE_BACKGROUND = Color.argb(100, 0, 0, 0)
    }

    data class StatusSnapshot(
        val capsLockEnabled: Boolean,
        val shiftPhysicallyPressed: Boolean,
        val shiftOneShot: Boolean,
        val ctrlLatchActive: Boolean,
        val ctrlPhysicallyPressed: Boolean,
        val ctrlOneShot: Boolean,
        val ctrlLatchFromNavMode: Boolean,
        val altLatchActive: Boolean,
        val altPhysicallyPressed: Boolean,
        val altOneShot: Boolean,
        val symKeyActive: Boolean,
        val variations: List<String> = emptyList(),
        val lastInsertedChar: Char? = null
    ) {
        val navModeActive: Boolean
            get() = ctrlLatchActive && ctrlLatchFromNavMode
    }

    private var statusBarLayout: LinearLayout? = null
    private var modifiersContainer: LinearLayout? = null
    private var emojiMapTextView: TextView? = null
    private var variationsContainer: LinearLayout? = null
    private var variationButtons: MutableList<TextView> = mutableListOf()

    fun getLayout(): LinearLayout? = statusBarLayout

    fun getOrCreateLayout(emojiMapText: String = ""): LinearLayout {
        if (statusBarLayout == null) {
            statusBarLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(DEFAULT_BACKGROUND)
            }

            // Container per gli indicatori dei modificatori (orizzontale, allineato a sinistra)
            // Aggiungiamo padding a sinistra per evitare il tasto di collapse della tastiera IME
            val leftPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                64f, 
                context.resources.displayMetrics
            ).toInt()
            val horizontalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                16f, 
                context.resources.displayMetrics
            ).toInt()
            val verticalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                8f, 
                context.resources.displayMetrics
            ).toInt()
            
            modifiersContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(leftPadding, verticalPadding, horizontalPadding, verticalPadding)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                visibility = View.GONE
            }

            // TextView per la mappa emoji (quando SYM è attivo)
            val emojiVerticalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                4f, 
                context.resources.displayMetrics
            ).toInt()
            val emojiBottomPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                8f, 
                context.resources.displayMetrics
            ).toInt()
            
            emojiMapTextView = TextView(context).apply {
                text = emojiMapText
                textSize = 11f
                setTextColor(Color.WHITE)
                gravity = Gravity.START
                setPadding(leftPadding, emojiVerticalPadding, horizontalPadding, emojiBottomPadding)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                visibility = View.GONE
            }

            // Container per i pulsanti delle variazioni (orizzontale, allineato a sinistra)
            // Altezza fissa per mantenere la barra sempre della stessa altezza (aumentata del 10%)
            val variationsContainerHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                55f, // 50f * 1.1 (aumentata del 10%)
                context.resources.displayMetrics
            ).toInt()
            val variationsVerticalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                8.8f, // 8f * 1.1 (aumentato del 10%)
                context.resources.displayMetrics
            ).toInt()
            
            variationsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                // Usa lo stesso padding sinistro degli altri container per evitare il tasto di collapse
                setPadding(leftPadding, variationsVerticalPadding, horizontalPadding, variationsVerticalPadding)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    variationsContainerHeight // Altezza fissa invece di WRAP_CONTENT
                )
                visibility = View.INVISIBLE  // INVISIBLE invece di GONE per mantenere lo spazio
            }

            statusBarLayout?.apply {
                addView(modifiersContainer)
                addView(emojiMapTextView)
                addView(variationsContainer)
            }
        } else if (emojiMapText.isNotEmpty()) {
            emojiMapTextView?.text = emojiMapText
        }
        return statusBarLayout!!
    }
    
    /**
     * Crea un indicatore per un modificatore.
     */
    private fun createModifierIndicator(text: String, isActive: Boolean): TextView {
        val dp8 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            8f, 
            context.resources.displayMetrics
        ).toInt()
        val dp6 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            6f, 
            context.resources.displayMetrics
        ).toInt()
        
        return TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(if (isActive) Color.WHITE else Color.argb(180, 255, 255, 255))
            gravity = Gravity.CENTER
            setPadding(dp6, dp8, dp6, dp8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp8 // Margine a destra tra gli indicatori
            }
        }
    }
    
    /**
     * Crea un pulsante per una variazione.
     */
    private fun createVariationButton(
        variation: String,
        inputConnection: android.view.inputmethod.InputConnection?
    ): TextView {
        // Converti dp in pixel (aumentati del 10%)
        val dp4_4 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            4.4f, // 4f * 1.1 (aumentato del 10%)
            context.resources.displayMetrics
        ).toInt()
        val dp6_6 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            6.6f, // 6f * 1.1 (aumentato del 10%)
            context.resources.displayMetrics
        ).toInt()
        val dp8_8 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            8.8f, // 8f * 1.1 (aumentato del 10%)
            context.resources.displayMetrics
        ).toInt()
        val borderWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            1.5f, // Bordo più sottile
            context.resources.displayMetrics
        ).toInt()
        val cornerRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            4.4f, // 4f * 1.1 (aumentato del 10%)
            context.resources.displayMetrics
        )
        
        // Larghezza fissa per tutti i pulsanti (circa 48dp)
        val buttonWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            48f, 
            context.resources.displayMetrics
        ).toInt()
        
        // Crea il background del pulsante (rettangolo arrotondato)
        val drawable = GradientDrawable().apply {
            setColor(Color.argb(180, 255, 255, 255)) // Bianco semi-trasparente
            setCornerRadius(cornerRadius)
            setStroke(borderWidth, Color.WHITE) // Bordo bianco più sottile
        }
        
        // Crea un drawable per lo stato pressed (più scuro)
        val pressedDrawable = GradientDrawable().apply {
            setColor(Color.argb(220, 200, 200, 200)) // Grigio più scuro quando premuto
            setCornerRadius(cornerRadius)
            setStroke(borderWidth, Color.WHITE)
        }
        
        // Crea uno StateListDrawable per gestire gli stati (normale e pressed)
        val stateListDrawable = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), drawable) // Stato normale
        }
        
        val button = TextView(context).apply {
            text = variation
            textSize = 17.6f // 16f * 1.1 (aumentato del 10%)
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            // Padding aumentato del 10%
            setPadding(dp6_6, dp4_4, dp6_6, dp4_4)
            background = stateListDrawable
            layoutParams = LinearLayout.LayoutParams(
                buttonWidth, // Larghezza fissa invece di WRAP_CONTENT
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp8_8 // Margine a destra tra i pulsanti (aumentato del 10%)
            }
            // Rendi il pulsante clickabile
            isClickable = true
            isFocusable = true
        }
        
        // Aggiungi il listener per il click
        button.setOnClickListener(
            VariationButtonHandler.createVariationClickListener(
                variation,
                inputConnection,
                onVariationSelectedListener
            )
        )
        
        return button
    }

    fun update(snapshot: StatusSnapshot, emojiMapText: String = "", inputConnection: android.view.inputmethod.InputConnection? = null) {
        val layout = statusBarLayout ?: return
        val modifiersContainerView = modifiersContainer ?: return
        val emojiView = emojiMapTextView ?: return
        val variationsContainerView = variationsContainer ?: return

        if (snapshot.navModeActive) {
            layout.setBackgroundColor(NAV_MODE_BACKGROUND)
            modifiersContainerView.visibility = View.GONE
            emojiView.visibility = View.GONE
            variationsContainerView.visibility = View.GONE
            return
        }

        layout.setBackgroundColor(DEFAULT_BACKGROUND)
        
        // Aggiorna la mappa emoji
        if (emojiMapText.isNotEmpty()) {
            emojiView.text = emojiMapText
        }
        
        // Rimuovi tutti gli indicatori esistenti
        modifiersContainerView.removeAllViews()
        
        // Aggiungi gli indicatori dei modificatori se attivi
        val hasModifiers = snapshot.capsLockEnabled || 
                          snapshot.shiftPhysicallyPressed || 
                          snapshot.shiftOneShot ||
                          snapshot.ctrlLatchActive ||
                          snapshot.ctrlPhysicallyPressed ||
                          snapshot.ctrlOneShot ||
                          snapshot.altLatchActive ||
                          snapshot.altPhysicallyPressed ||
                          snapshot.altOneShot
        
        if (hasModifiers) {
            // Caps Lock
            if (snapshot.capsLockEnabled) {
                modifiersContainerView.addView(createModifierIndicator("🔒 SHIFT", true))
            } else if (snapshot.shiftPhysicallyPressed || snapshot.shiftOneShot) {
                modifiersContainerView.addView(createModifierIndicator("shift", false))
            }
            
            // Ctrl (non nel nav mode)
            if (!snapshot.navModeActive) {
                if (snapshot.ctrlLatchActive) {
                    modifiersContainerView.addView(createModifierIndicator("CTRL", true))
                } else if (snapshot.ctrlPhysicallyPressed || snapshot.ctrlOneShot) {
                    modifiersContainerView.addView(createModifierIndicator("ctrl", false))
                }
            }
            
            // Alt
            if (snapshot.altLatchActive) {
                modifiersContainerView.addView(createModifierIndicator("ALT", true))
            } else if (snapshot.altPhysicallyPressed || snapshot.altOneShot) {
                modifiersContainerView.addView(createModifierIndicator("alt", false))
            }
            
            modifiersContainerView.visibility = View.VISIBLE
        } else {
            modifiersContainerView.visibility = View.GONE
        }
        
        // Mostra la mappa emoji se SYM è attivo
        emojiView.visibility = if (snapshot.symKeyActive) View.VISIBLE else View.GONE
        
        // Rimuovi tutti i pulsanti delle variazioni esistenti
        variationsContainerView.removeAllViews()
        variationButtons.clear()
        
        // Mostra le variazioni se disponibili
        if (snapshot.variations.isNotEmpty() && snapshot.lastInsertedChar != null) {
            // Crea un pulsante per ogni variazione
            for (variation in snapshot.variations) {
                val button = createVariationButton(variation, inputConnection)
                variationButtons.add(button)
                variationsContainerView.addView(button)
            }
            variationsContainerView.visibility = View.VISIBLE
        } else {
            // Nascondi il container ma mantieni lo spazio (INVISIBLE) per mantenere l'altezza costante
            // L'altezza è già fissa nel layoutParams, quindi INVISIBLE manterrà lo spazio
            variationsContainerView.visibility = View.INVISIBLE
        }
    }
}


