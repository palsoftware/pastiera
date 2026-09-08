package it.palsoftware.pastiera.inputmethod.composition

import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Hardware key injection through Android's selected IME, never a direct composer call.
 * Run against an isolated debug installation; the runner must restore the user's IME afterward.
 */
@RunWith(AndroidJUnit4::class)
class HangulSystemImeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun configureTestApp() {
        context.contentResolver.call(android.net.Uri.parse("content://${context.packageName}.ime-test-settings"),
            "configure", null, null)
        val expected = android.content.ComponentName(context,
            it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodService::class.java).flattenToShortString()
        assertEquals("Select isolated debug IME first", expected,
            android.provider.Settings.Secure.getString(context.contentResolver,"default_input_method"))
    }

    private lateinit var activeEditor: EditorSession

    private inner class EditorSession(local: Boolean = false) {
        private val authority = if (local) "ime-test-settings" else "ime-editor-test"
        fun command(method: String, arg: String? = null, extras: android.os.Bundle? = null) =
            context.contentResolver.call(android.net.Uri.parse("content://${context.packageName}.$authority"),
                method, arg, extras)!!
    }
    private fun editor(kind: String = "native", block: (EditorSession) -> Unit) {
        val session = EditorSession(local = kind == "local_compose_string")
        activeEditor = session
        session.command("launch", if (kind == "local_compose_string") "compose_string" else kind)
        try {
            awaitCondition { session.command("state").getBoolean("ready") }
            session.command("focus")
            // Instrumentation restarts the target process, including its selected IME.
            // Wait for Android to bind the service again before testing connected input.
            var lastRestart = SystemClock.uptimeMillis()
            awaitCondition {
                val pid = session.command("state").getInt("pid")
                val dump = android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand("dumpsys input_method")
                ).bufferedReader().use { it.readText() }
                val bound = dump.contains("mBoundToMethod=true") &&
                    dump.lineSequence().any { it.contains("mEnabledSession=") && it.contains("pid=$pid ") }
                if (!bound && SystemClock.uptimeMillis() - lastRestart > 500) {
                    // Re-request the empty editor connection if Android retained a stale
                    // binding while instrumentation replaced the previous process.
                    session.command("restartConnection")
                    lastRestart = SystemClock.uptimeMillis()
                }
                bound
            }
            SystemClock.sleep(700)
            block(session)
        } finally {
            session.command("close")
            awaitCondition { !session.command("state").getBoolean("ready") }
        }
    }
    private fun keys(vararg codes: Int) {
        check(activeEditor.command("state").getBoolean("ready")) { "Test editor lost window focus; stopping injection" }
        codes.forEach { code ->
            val now = SystemClock.uptimeMillis()
            for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                assertTrue(instrumentation.uiAutomation.injectInputEvent(
                    KeyEvent(now, now, action, code, 0, 0, android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,
                        0, 0, android.view.InputDevice.SOURCE_KEYBOARD), true))
            }
        }
    }
    private fun type(ascii: String) = keys(*ascii.map { KeyEvent.KEYCODE_A + (it-'a') }.toIntArray())
    private fun read(s: EditorSession): String {
        val result = s.command("read")
        check(result.containsKey("text")) { "Editor has no text response: $result" }
        return result.getString("text").orEmpty()
    }
    private fun awaitCondition(check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15000
        while (!check()) {
            assertTrue("Condition timed out", SystemClock.uptimeMillis() < end)
            SystemClock.sleep(25)
        }
    }
    private fun awaitText(expected: String, s: EditorSession) {
        val end = SystemClock.uptimeMillis() + 3000
        while (read(s) != expected && SystemClock.uptimeMillis() < end) SystemClock.sleep(25)
        assertEquals(expected, read(s))
    }

    @Test
    fun nativeCompositionAndCompleteDeletion() = editor { s ->
        type("gksrmf")
        awaitText("한글", s)
        repeat(6) { keys(KeyEvent.KEYCODE_DEL) }
        awaitText("", s)
        assertEquals(-1, s.command("read").getInt("composingStart"))
    }
    @Test
    fun nativeSpaceAndPunctuationPreserveText() = editor { s ->
        type("gksrmf")
        keys(KeyEvent.KEYCODE_SPACE)
        type("rk")
        keys(KeyEvent.KEYCODE_PERIOD)
        awaitText("한글 가.", s)
    }
    @Test
    fun nativeLongRunAndBackspace() = editor { s ->
        type("rk".repeat(63) + "rhk")
        awaitText("가".repeat(63) + "과", s)
        keys(KeyEvent.KEYCODE_DEL)
        awaitText("가".repeat(63) + "고", s)
    }
    @Test
    fun selectionReplacementAndRestart() = editor { s ->
        type("gksrmf")
        awaitText("한글", s)
        s.command("select", extras = android.os.Bundle().apply { putInt("start",0); putInt("end",2) })
        SystemClock.sleep(200)
        type("rk")
        awaitText("가", s)
        s.command("restart")
        SystemClock.sleep(200)
        type("sk")
        awaitText("나", s)
    }
    @Test
    fun composeCompositionAndDeletion() = editor("compose") { s ->
        type("gksrmf")
        awaitText("한글", s)
        repeat(6) { keys(KeyEvent.KEYCODE_DEL) }
        awaitText("", s)
    }
    @Test
    fun webViewCompositionAndBoundary() = editor("web") { s ->
        type("gksrmf")
        awaitText("한글", s)
        keys(KeyEvent.KEYCODE_SPACE)
        type("rk")
        awaitText("한글 가", s)
    }
}
