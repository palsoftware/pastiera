package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AltSymManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences("alt_sym_manager_tests", Context.MODE_PRIVATE)
        prefs.edit().clear().putLong("long_press_threshold", 50L).commit()
        SettingsManager.setLongPressModifier(context, "variations")
        SettingsManager.saveVariations(
            context,
            variations = mapOf(
                "u" to listOf("ü"),
                "U" to listOf("Ü")
            )
        )
    }

    @After
    fun tearDown() {
        SettingsManager.resetVariationsToDefault(context)
        SettingsManager.setLongPressModifier(context, "alt")
    }

    @Test
    fun variationsLongPress_replacesFirstCharacterUsingComposingRegion() {
        val recorder = RecordingInputConnection()
        val inputConnection = recorder.asProxy()
        val manager = AltSymManager(context.assets, prefs, context)

        val consumed = manager.handleKeyWithAltMapping(
            keyCode = KeyEvent.KEYCODE_U,
            inputConnection = inputConnection,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_U),
            capsLockEnabled = false,
            shiftOneShot = false,
            layoutChar = 'u'
        )

        assertTrue(consumed)
        assertEquals("u", recorder.text)

        shadowOf(Looper.getMainLooper()).idleFor(60, TimeUnit.MILLISECONDS)

        assertEquals("ü", recorder.text)
        assertEquals(listOf(0 to 1), recorder.composingRegions)
        assertEquals(emptyList<Pair<Int, Int>>(), recorder.deleteCalls)
    }

    @Test
    fun variationsLongPress_replacesOriginalCharacterAndPreservesLaterInput() {
        val recorder = RecordingInputConnection()
        val inputConnection = recorder.asProxy()
        val manager = AltSymManager(context.assets, prefs, context)

        manager.handleKeyWithAltMapping(
            keyCode = KeyEvent.KEYCODE_U,
            inputConnection = inputConnection,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_U),
            capsLockEnabled = false,
            layoutChar = 'u'
        )
        inputConnection.commitText("3", 1)

        shadowOf(Looper.getMainLooper()).idleFor(60, TimeUnit.MILLISECONDS)

        assertEquals("ü3", recorder.text)
        assertEquals(2, recorder.selectionStart)
        assertEquals(2, recorder.selectionEnd)
        assertEquals(listOf(0 to 1), recorder.composingRegions)
        assertEquals(emptyList<Pair<Int, Int>>(), recorder.deleteCalls)
    }

    @Test
    fun variationsLongPress_preservesLaterInputForShiftedCharacter() {
        val recorder = RecordingInputConnection()
        val inputConnection = recorder.asProxy()
        val manager = AltSymManager(context.assets, prefs, context)

        manager.handleKeyWithAltMapping(
            keyCode = KeyEvent.KEYCODE_U,
            inputConnection = inputConnection,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_U),
            capsLockEnabled = false,
            shiftOneShot = true,
            layoutChar = 'u'
        )
        inputConnection.commitText("3", 1)

        shadowOf(Looper.getMainLooper()).idleFor(60, TimeUnit.MILLISECONDS)

        assertEquals("Ü3", recorder.text)
        assertEquals(2, recorder.selectionStart)
        assertEquals(2, recorder.selectionEnd)
    }

    @Test
    fun variationsLongPress_doesNotTouchLaterInputWhenOriginalCharacterChanged() {
        val recorder = RecordingInputConnection()
        val inputConnection = recorder.asProxy()
        val manager = AltSymManager(context.assets, prefs, context)

        manager.handleKeyWithAltMapping(
            keyCode = KeyEvent.KEYCODE_U,
            inputConnection = inputConnection,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_U),
            capsLockEnabled = false,
            layoutChar = 'u'
        )
        inputConnection.commitText("3", 1)
        recorder.replaceTextDirectly(0, 1, "x")

        shadowOf(Looper.getMainLooper()).idleFor(60, TimeUnit.MILLISECONDS)

        assertEquals("x3", recorder.text)
        assertEquals(emptyList<Pair<Int, Int>>(), recorder.composingRegions)
        assertEquals(emptyList<Pair<Int, Int>>(), recorder.deleteCalls)
    }

    @Test
    fun variationsLongPress_withoutAnchorDoesNotReplaceLaterInput() {
        val recorder = RecordingInputConnection(extractedTextAvailable = false)
        val inputConnection = recorder.asProxy()
        val manager = AltSymManager(context.assets, prefs, context)

        manager.handleKeyWithAltMapping(
            keyCode = KeyEvent.KEYCODE_U,
            inputConnection = inputConnection,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_U),
            capsLockEnabled = false,
            layoutChar = 'u'
        )
        inputConnection.commitText("3", 1)
        recorder.extractedTextAvailable = true

        shadowOf(Looper.getMainLooper()).idleFor(60, TimeUnit.MILLISECONDS)

        assertEquals("u3", recorder.text)
        assertEquals(emptyList<Pair<Int, Int>>(), recorder.composingRegions)
        assertEquals(emptyList<Pair<Int, Int>>(), recorder.deleteCalls)
    }

    @Test
    fun altMappedPunctuationIsOfferedToBoundaryHandlerBeforeDirectCommit() {
        val recorder = RecordingInputConnection()
        val inputConnection = recorder.asProxy()
        inputConnection.commitText("teh", 1)
        val requested = mutableListOf<String>()
        val manager = AltSymManager(context.assets, prefs, context).apply {
            onBoundaryTextRequested = { text, connection ->
                requested += text
                connection.commitText("?", 1)
                true
            }
        }

        val handled = manager.handleAltCombination(
            keyCode = KeyEvent.KEYCODE_X,
            inputConnection = inputConnection,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_X),
            mappingsOverride = mapOf(KeyEvent.KEYCODE_X to "?"),
            defaultHandler = { _, _ -> false }
        )

        assertTrue(handled)
        assertEquals(listOf("?"), requested)
        assertEquals("teh?", recorder.text)
    }

    @Test
    fun altMappedApostropheRemainsAnInWordCharacter() {
        val recorder = RecordingInputConnection()
        val inputConnection = recorder.asProxy()
        inputConnection.commitText("l", 1)
        val requested = mutableListOf<String>()
        val manager = AltSymManager(context.assets, prefs, context).apply {
            onBoundaryTextRequested = { text, _ ->
                requested += text
                true
            }
        }

        val handled = manager.handleAltCombination(
            keyCode = KeyEvent.KEYCODE_S,
            inputConnection = inputConnection,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_S),
            mappingsOverride = mapOf(KeyEvent.KEYCODE_S to "'"),
            defaultHandler = { _, _ -> false }
        )

        assertTrue(handled)
        assertTrue(requested.isEmpty())
        assertEquals("l'", recorder.text)
    }

    private class RecordingInputConnection(
        var extractedTextAvailable: Boolean = true
    ) {
        var text: String = ""
        var selectionStart: Int = 0
            private set
        var selectionEnd: Int = 0
            private set
        private var composingStart: Int = -1
        private var composingEnd: Int = -1
        val composingRegions = mutableListOf<Pair<Int, Int>>()
        val deleteCalls = mutableListOf<Pair<Int, Int>>()

        fun replaceTextDirectly(start: Int, end: Int, replacement: String) {
            text = text.replaceRange(start, end, replacement)
            val lengthDelta = replacement.length - (end - start)
            selectionStart += lengthDelta
            selectionEnd += lengthDelta
        }

        fun asProxy(): InputConnection {
            return Proxy.newProxyInstance(
                InputConnection::class.java.classLoader,
                arrayOf(InputConnection::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "commitText" -> {
                        val committed = args?.getOrNull(0)?.toString().orEmpty()
                        if (composingStart >= 0 && composingEnd >= composingStart) {
                            text = text.replaceRange(composingStart, composingEnd, committed)
                            selectionStart = composingStart + committed.length
                            selectionEnd = selectionStart
                            composingStart = -1
                            composingEnd = -1
                        } else {
                            text = text.substring(0, selectionStart) +
                                committed +
                                text.substring(selectionEnd)
                            selectionStart += committed.length
                            selectionEnd = selectionStart
                        }
                        true
                    }
                    "setComposingRegion" -> {
                        composingStart = args?.getOrNull(0) as Int
                        composingEnd = args.getOrNull(1) as Int
                        composingRegions += composingStart to composingEnd
                        true
                    }
                    "deleteSurroundingText" -> {
                        val before = args?.getOrNull(0) as Int
                        val after = args.getOrNull(1) as Int
                        deleteCalls += before to after
                        true
                    }
                    "finishComposingText" -> {
                        composingStart = -1
                        composingEnd = -1
                        true
                    }
                    "setSelection" -> {
                        selectionStart = args?.getOrNull(0) as Int
                        selectionEnd = args.getOrNull(1) as Int
                        true
                    }
                    "beginBatchEdit", "endBatchEdit" -> true
                    "getExtractedText" -> if (extractedTextAvailable) {
                        ExtractedText().apply {
                            this.text = this@RecordingInputConnection.text
                            selectionStart = this@RecordingInputConnection.selectionStart
                            selectionEnd = this@RecordingInputConnection.selectionEnd
                        }
                    } else {
                        null
                    }
                    else -> defaultValue(method.returnType)
                }
            } as InputConnection
        }

        private fun defaultValue(type: Class<*>): Any? {
            return when {
                type == Boolean::class.javaPrimitiveType -> false
                type == Int::class.javaPrimitiveType -> 0
                type == Long::class.javaPrimitiveType -> 0L
                type == Float::class.javaPrimitiveType -> 0f
                type == Double::class.javaPrimitiveType -> 0.0
                type == Short::class.javaPrimitiveType -> 0.toShort()
                type == Byte::class.javaPrimitiveType -> 0.toByte()
                type == Char::class.javaPrimitiveType -> 0.toChar()
                else -> null
            }
        }
    }
}
