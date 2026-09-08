package it.palsoftware.pastiera.testing

import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue

/** Empty local editors for instrumentation; never included in release builds. */
open class ImeEditorTestActivity : ComponentActivity() {
    lateinit var nativeEditor: EditText
    lateinit var webEditor: WebView
    var composeValue by mutableStateOf(TextFieldValue())
    var composeString by mutableStateOf("")
    var composeFocusRequest by mutableIntStateOf(0)
    var webReady = false
    var kind = "native"

    companion object { var current: ImeEditorTestActivity? = null }

    override fun onCreate(savedInstanceState: Bundle?) {
        current = this
        super.onCreate(savedInstanceState)
        kind = intent.getStringExtra("editor") ?: "native"
        when (kind) {
            "compose", "compose_string" -> setContent {
                val focus = remember { FocusRequester() }
                MaterialTheme {
                    Column {
                        Text("Pastiera IME device test")
                        if (kind == "compose_string") OutlinedTextField(
                            value = composeString, onValueChange = { composeString = it },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus))
                        else OutlinedTextField(value = composeValue, onValueChange = { composeValue = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(focus))
                        Button(onClick = { focus.requestFocus() }) { Text("Focus editor") }
                    }
                }
                LaunchedEffect(composeFocusRequest) {
                    if (composeFocusRequest > 0) focus.requestFocus()
                }
            }
            "web" -> {
                webEditor = WebView(this)
                webEditor.settings.javaScriptEnabled = true
                webEditor.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) { webReady = true }
                }
                setContentView(webEditor)
                webEditor.loadDataWithBaseURL(null,
                    "<meta name='viewport' content='width=device-width, initial-scale=1'><textarea id='editor' style='width:95%;height:200px;font-size:24px'></textarea>",
                    "text/html", "UTF-8", null)
            }
            else -> {
                val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val button = Button(this).apply {
                    text = "Pastiera IME device test"
                    isFocusableInTouchMode = true
                }
                nativeEditor = EditText(this).apply {
                    inputType = intent.getIntExtra("inputType", InputType.TYPE_CLASS_TEXT)
                    hint = "Test editor"
                }
                root.addView(button)
                root.addView(nativeEditor)
                setContentView(root)
                button.requestFocus()
            }
        }
    }

    override fun onDestroy() {
        if (current === this) current = null
        if (kind == "web") webEditor.destroy()
        super.onDestroy()
    }

    fun focusEditor() {
        when (kind) {
            "compose", "compose_string" -> composeFocusRequest++
            "web" -> {
                webEditor.requestFocus()
                webEditor.evaluateJavascript("document.getElementById('editor').focus()", null)
                getSystemService(InputMethodManager::class.java).showSoftInput(webEditor,0)
            }
            else -> {
                nativeEditor.requestFocus()
                getSystemService(InputMethodManager::class.java).showSoftInput(nativeEditor,0)
            }
        }
    }
}

/** Mirrors the settings screen sharing a process with the IME. */
class ImeLocalEditorTestActivity : ImeEditorTestActivity()
