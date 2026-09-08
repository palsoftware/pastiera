package it.palsoftware.pastiera.testing

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputMethodManager
import org.json.JSONTokener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Same-UID test control only. The editor uses a separate process so IME queries cannot
 * block its UI thread (especially WebView's asynchronous InputConnection).
 */
open class ImeEditorTestProvider : ContentProvider() {
    protected open val activityClass: Class<out ImeEditorTestActivity> = ImeEditorTestActivity::class.java
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val result = Bundle().apply { putInt("pid", android.os.Process.myPid()) }
        val done = CountDownLatch(1)
        var failure: Throwable? = null
        Handler(Looper.getMainLooper()).post {
            try {
                if (method == "launch") {
                    context!!.startActivity(Intent(context, activityClass)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra("editor", arg ?: "native"))
                } else {
                    val activity = ImeEditorTestActivity.current
                    result.putBoolean("ready", activity != null && activity.hasWindowFocus() &&
                        (activity.kind != "web" || activity.webReady))
                    if (activity != null) when (method) {
                        "focus" -> activity.focusEditor()
                        "close" -> activity.finish()
                        "select" -> activity.nativeEditor.setSelection(extras!!.getInt("start"), extras.getInt("end"))
                        "restartConnection" -> activity.currentFocus?.let {
                            activity.getSystemService(InputMethodManager::class.java).restartInput(it)
                        }
                        "restart" -> {
                            activity.nativeEditor.setText("")
                            activity.getSystemService(InputMethodManager::class.java).restartInput(activity.nativeEditor)
                        }
                        "read" -> when (activity.kind) {
                            "compose" -> result.putString("text", activity.composeValue.text)
                            "compose_string" -> result.putString("text", activity.composeString)
                            "web" -> {
                                activity.webEditor.evaluateJavascript("document.getElementById('editor').value") {
                                    result.putString("text", JSONTokener(it).nextValue().toString())
                                    done.countDown()
                                }
                                return@post
                            }
                            else -> {
                                result.putString("text", activity.nativeEditor.text.toString())
                                result.putInt("composingStart", BaseInputConnection.getComposingSpanStart(activity.nativeEditor.text))
                            }
                        }
                    }
                }
            } catch (error: Throwable) { failure = error }
            done.countDown()
        }
        check(done.await(5, TimeUnit.SECONDS)) { "Editor command timed out: $method" }
        failure?.let { throw it }
        return result
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

/** Configure in the IME process: SharedPreferences listeners do not cross processes. */
class ImeTestSettingsProvider : ImeEditorTestProvider() {
    override val activityClass = ImeLocalEditorTestActivity::class.java
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (method != "configure") return super.call(method, arg, extras)
        val app = context!!
        it.palsoftware.pastiera.SettingsManager.setKeyboardLayout(app, "korean_2set")
        it.palsoftware.pastiera.SettingsManager.setKeyboardLayoutAutoByLocale(app, false)
        it.palsoftware.pastiera.SettingsManager.setAutoCorrectEnabled(app, false)
        it.palsoftware.pastiera.SettingsManager.setAutoCapitalizeFirstLetter(app, false)
        return Bundle.EMPTY
    }
}
