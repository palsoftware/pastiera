package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager

/**
 * Controller for handling launcher shortcuts functionality.
 * Manages app launching, launcher detection, and shortcut assignment dialogs.
 */
class LauncherShortcutController(
    private val context: Context
) {
    companion object {
        private const val TAG = "PastieraInputMethod"
        private const val POWER_SHORTCUT_TIMEOUT_MS = 5000L // 5 secondi di timeout
    }

    // Cache for launcher packages
    private var cachedLauncherPackages: Set<String>? = null
    
    // Stato per Power Shortcuts: SYM premuto per attivare shortcut
    private var powerShortcutSymPressed: Boolean = false
    private var powerShortcutTimeoutHandler: android.os.Handler? = null
    private var powerShortcutTimeoutRunnable: Runnable? = null
    
    // Stato per gestire nav mode durante power shortcuts
    private var navModeWasActive: Boolean = false
    private var exitNavModeCallback: (() -> Unit)? = null
    private var enterNavModeCallback: (() -> Unit)? = null

    /**
     * Verifica se il package corrente è un launcher.
     */
    fun isLauncher(packageName: String?): Boolean {
        if (packageName == null) return false
        
        // Cache la lista dei launcher per evitare query ripetute
        if (cachedLauncherPackages == null) {
            try {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                }
                
                val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
                cachedLauncherPackages = resolveInfos.map { it.activityInfo.packageName }.toSet()
                Log.d(TAG, "Launcher packages trovati: $cachedLauncherPackages")
            } catch (e: Exception) {
                Log.e(TAG, "Errore nel rilevamento dei launcher", e)
                cachedLauncherPackages = emptySet()
            }
        }
        
        val isLauncher = cachedLauncherPackages?.contains(packageName) ?: false
        Log.d(TAG, "isLauncher($packageName) = $isLauncher")
        return isLauncher
    }
    
    /**
     * Apre un'app tramite package name.
     */
    private fun launchApp(packageName: String): Boolean {
        try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "App aperta: $packageName")
                return true
            } else {
                Log.w(TAG, "Nessun launch intent trovato per: $packageName")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'apertura dell'app $packageName", e)
            return false
        }
    }
    
    /**
     * Handles launcher shortcuts when not in a text field.
     */
    fun handleLauncherShortcut(keyCode: Int): Boolean {
        val shortcut = SettingsManager.getLauncherShortcut(context, keyCode)
        if (shortcut != null) {
            // Gestisci diversi tipi di azioni
            when (shortcut.type) {
                SettingsManager.LauncherShortcut.TYPE_APP -> {
                    if (shortcut.packageName != null) {
                        val success = launchApp(shortcut.packageName)
                        if (success) {
                            Log.d(TAG, "Scorciatoia launcher eseguita: tasto $keyCode -> ${shortcut.packageName}")
                            return true // Consumiamo l'evento
                        }
                    }
                }
                SettingsManager.LauncherShortcut.TYPE_SHORTCUT -> {
                    // TODO: Gestire scorciatoie in futuro
                    Log.d(TAG, "Tipo scorciatoia non ancora implementato: ${shortcut.type}")
                }
                else -> {
                    Log.d(TAG, "Tipo azione sconosciuto: ${shortcut.type}")
                }
            }
        } else {
            // Tasto non assegnato: mostra dialog per assegnare un'app
            showLauncherShortcutAssignmentDialog(keyCode)
            return true // Consumiamo l'evento per evitare che venga gestito altrove
        }
        return false // Non consumiamo l'evento
    }
    
    /**
     * Mostra il dialog per assegnare un'app a un tasto.
     */
    private fun showLauncherShortcutAssignmentDialog(keyCode: Int) {
        try {
            val intent = Intent(context, LauncherShortcutAssignmentActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(LauncherShortcutAssignmentActivity.EXTRA_KEY_CODE, keyCode)
            }
            context.startActivity(intent)
            Log.d(TAG, "Dialog assegnazione mostrato per tasto $keyCode")
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel mostrare il dialog di assegnazione", e)
        }
    }
    
    /**
     * Imposta i callback per gestire nav mode durante power shortcuts.
     */
    fun setNavModeCallbacks(
        exitNavMode: () -> Unit,
        enterNavMode: () -> Unit
    ) {
        exitNavModeCallback = exitNavMode
        enterNavModeCallback = enterNavMode
    }
    
    /**
     * Attiva o disattiva il Power Shortcut mode (SYM premuto).
     * Se già attivo, lo disattiva (edge case).
     * Restituisce true se il mode è stato attivato, false se disattivato.
     * @param isNavModeActive indica se nav mode è attivo quando SYM viene premuto
     */
    fun togglePowerShortcutMode(
        showToast: (String) -> Unit,
        isNavModeActive: Boolean = false
    ): Boolean {
        if (powerShortcutSymPressed) {
            // Edge case: se già attivo, disattivalo
            resetPowerShortcutMode()
            Log.d(TAG, "Power Shortcut mode disattivato da SYM")
            return false
        }
        
        // Salva se nav mode era attivo e disabilitalo se necessario
        navModeWasActive = isNavModeActive
        if (isNavModeActive) {
            exitNavModeCallback?.invoke()
            Log.d(TAG, "Nav mode disabilitato per attivare Power Shortcut")
        }
        
        // Attiva il mode
        powerShortcutSymPressed = true
        Log.d(TAG, "Power Shortcut mode attivato")
        
        // Mostra toast
        val message = context.getString(R.string.power_shortcuts_press_key)
        showToast(message)
        
        // Cancella timeout precedente se esiste
        cancelPowerShortcutTimeout()
        
        // Imposta timeout per resettare automaticamente
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        powerShortcutTimeoutRunnable = Runnable {
            resetPowerShortcutMode()
        }
        powerShortcutTimeoutHandler = handler
        handler.postDelayed(powerShortcutTimeoutRunnable!!, POWER_SHORTCUT_TIMEOUT_MS)
        
        return true
    }
    
    /**
     * Resetta il Power Shortcut mode.
     * Se nav mode era attivo prima, lo riabilita.
     */
    fun resetPowerShortcutMode() {
        if (powerShortcutSymPressed) {
            powerShortcutSymPressed = false
            cancelPowerShortcutTimeout()
            Log.d(TAG, "Power Shortcut mode resettato")
            
            // Se nav mode era attivo prima, riabilitalo
            if (navModeWasActive) {
                enterNavModeCallback?.invoke()
                navModeWasActive = false
                Log.d(TAG, "Nav mode riabilitato dopo Power Shortcut")
            }
        }
    }
    
    /**
     * Verifica se il Power Shortcut mode è attivo.
     */
    fun isPowerShortcutModeActive(): Boolean {
        return powerShortcutSymPressed
    }
    
    /**
     * Cancella il timeout del Power Shortcut mode.
     */
    private fun cancelPowerShortcutTimeout() {
        powerShortcutTimeoutRunnable?.let { runnable ->
            powerShortcutTimeoutHandler?.removeCallbacks(runnable)
        }
        powerShortcutTimeoutRunnable = null
        powerShortcutTimeoutHandler = null
    }

    /**
     * Handles power shortcuts when SYM was pressed first.
     * Riutilizza la logica esistente di handleLauncherShortcut.
     * Restituisce true se lo shortcut è stato gestito, false altrimenti.
     */
    fun handlePowerShortcut(keyCode: Int): Boolean {
        if (!isPowerShortcutModeActive()) {
            return false
        }
        
        // Reset del mode dopo l'uso
        resetPowerShortcutMode()
        
        // Riutilizza la logica esistente - stessa funzione, stesse assegnazioni
        return handleLauncherShortcut(keyCode)
    }
}

