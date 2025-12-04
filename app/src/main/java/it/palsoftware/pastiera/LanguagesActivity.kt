package it.palsoftware.pastiera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import it.palsoftware.pastiera.ui.theme.PastieraTheme

class LanguagesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            overridePendingTransition(R.anim.slide_in_from_right, 0)
        }
        enableEdgeToEdge()
        setContent {
            PastieraTheme {
                LanguagesScreen(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    
    override fun finish() {
        super.finish()
        overridePendingTransition(0, R.anim.slide_out_to_right)
    }
}

