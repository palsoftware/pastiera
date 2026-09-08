package it.palsoftware.pastiera

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import it.palsoftware.pastiera.ui.theme.PastieraTheme

class KeyboardLayoutActivity : LocalizedComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val locale = intent.getStringExtra("locale") ?: return finish()
        setContent {
            PastieraTheme {
                KeyboardLayoutSettingsScreen(
                    locale = locale,
                    initialLayout = intent.getStringExtra("layout"),
                    pickerMode = true,
                    onBack = { finish() },
                    onLayoutSelected = { language, layout ->
                        setResult(Activity.RESULT_OK, Intent().putExtra("locale", language).putExtra("layout", layout))
                    }
                )
            }
        }
    }
}
