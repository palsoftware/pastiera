package it.palsoftware.pastiera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import it.palsoftware.pastiera.ui.theme.PastieraTheme

class SymCustomizationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            overridePendingTransition(R.anim.slide_in_from_right, 0)
        }
        enableEdgeToEdge()
        setContent {
            PastieraTheme {
                SymCustomizationScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBack = {
                        // This is only called from the UI back button (arrow icon)
                        SettingsManager.confirmPendingRestoreSymPage(this@SymCustomizationActivity)
                        finish()
                    }
                )
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Confirm pending restore when activity is paused (user navigates away)
        // This handles both back button and other navigation methods
        if (isFinishing) {
            SettingsManager.confirmPendingRestoreSymPage(this)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // If activity is destroyed without finish() (e.g., user goes to another app),
        // clear the pending restore to avoid restoring SYM layout
        if (!isFinishing) {
            SettingsManager.clearPendingRestoreSymPage(this)
        }
    }
    
    override fun finish() {
        super.finish()
        overridePendingTransition(0, R.anim.slide_out_to_right)
    }
}
