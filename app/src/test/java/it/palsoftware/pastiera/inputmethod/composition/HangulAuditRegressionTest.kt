package it.palsoftware.pastiera.inputmethod.composition

import android.text.InputType
import android.view.View
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.core.composition.NormalizedCompositionStroke
import it.palsoftware.pastiera.core.InputContextState
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodService
import it.palsoftware.pastiera.inputmethod.CandidatesBarController
import it.palsoftware.pastiera.inputmethod.VariationButtonHandler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HangulAuditRegressionTest {
    private fun ic() = BaseInputConnection(View(RuntimeEnvironment.getApplication()), true)
    private fun coordinator() = ImeCompositionCoordinator().apply {
        startEditorSession()
        selectLayout("korean_2set")
    }
    private fun type(c: ImeCompositionCoordinator, ic: InputConnection, text: String) {
        text.forEach { assertTrue(c.handleMappedStroke(ic, NormalizedCompositionStroke(it))) }
    }

    @Test fun deletingLastStrokeMustRemoveEditorText() {
        val ic = ic(); val c = coordinator()
        type(c, ic, "ㄱㅏ")
        assertEquals("가", ic.editable.toString())
        assertTrue(c.handleBackspace(ic))
        assertEquals("ㄱ", ic.editable.toString())
        assertTrue(c.handleBackspace(ic))
        assertEquals("", ic.editable.toString())
    }

    @Test fun maxLengthSplitMustPreserveLastSyllable() {
        val ic = ic(); val c = coordinator()
        type(c, ic, "ㄱㅏ".repeat(63) + "ㄱㅗㅏ")
        assertEquals("가".repeat(63) + "과", ic.editable.toString())
    }

    @Test fun coalescedSelectionMustRemainOwned() {
        val ic = ic(); val c = coordinator()
        type(c, ic, "ㄱㅏㄴㅏ")
        assertEquals("가나", ic.editable.toString())
        assertEquals(SelectionUpdateDisposition.OWN_COMPOSING_UPDATE,
            c.onUpdateSelection(0, 0, 2, 2, 0, 2))
    }

    @Test fun missingComposingRangeMustInvalidateOwnership() {
        val ic = ic(); val c = coordinator()
        type(c, ic, "ㄱㅏ")
        c.onUpdateSelection(0,0,1,1,0,1)
        c.onUpdateSelection(1,1,1,1,0,1)
        // The editor commits the composing range without changing the cursor.
        ic.finishComposingText()
        assertEquals(SelectionUpdateDisposition.EXTERNAL_CHANGE,
            c.onUpdateSelection(1,1,1,1,-1,-1))
    }

    @Test fun composingFailureInNextEditorMustNotSwallowStroke() {
        val c = coordinator()
        type(c, ic(), "ㄱ")
        c.startEditorSession(); c.selectLayout("korean_2set")
        val rejecting = object : BaseInputConnection(View(RuntimeEnvironment.getApplication()), true) {
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int) = false
        }
        val handled = c.handleMappedStroke(rejecting, NormalizedCompositionStroke('ㄴ'))
        assertTrue("Handled input must either reach the editor or return false for fallback",
            !handled || rejecting.editable.toString() == "ㄴ")
    }

    @Test fun staticVariationMustNotReplaceHangulRun() {
        val context = RuntimeEnvironment.getApplication()
        val service = Robolectric.buildService(PhysicalKeyboardInputMethodService::class.java).create().get()
        val ic = ic()
        val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; packageName = "test.audit" }
        setField(service,"mInputConnection",ic)
        setField(service,"mStartedInputConnection",ic)
        setField(service,"mInputEditorInfo",info)
        setField(service,"isInputViewActive",true)
        setField(service,"inputContextState",InputContextState.fromEditorInfo(info))
        service.javaClass.getDeclaredMethod("switchToLayout",String::class.java,Boolean::class.javaPrimitiveType).apply { isAccessible=true }.invoke(service,"korean_2set",false)
        val c = getField(service,"compositionCoordinator") as ImeCompositionCoordinator
        type(c,ic,"ㅎㅏㄴㄱㅡㄹ")
        val bars = getField(service,"candidatesBarController") as CandidatesBarController
        VariationButtonHandler.createStaticVariationClickListener("(",ic,context,bars.onVariationSelectedListener).onClick(View(context))
        assertEquals("한글(", ic.editable.toString())
    }

    @Test fun numericFieldMustKeepNumberMapping() {
        val (service, ic) = serviceWithEditor(InputType.TYPE_CLASS_NUMBER)
        service.addAltKeyMapping(KeyEvent.KEYCODE_R, "1")
        assertTrue(service.onKeyDown(KeyEvent.KEYCODE_R, KeyEvent(1000,1000,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_R,0)))
        assertEquals("1", ic.editable.toString())
    }

    @Test fun symPageMustKeepSymbolMapping() {
        val (service, ic) = serviceWithEditor(InputType.TYPE_CLASS_TEXT)
        val sym = getField(service,"symLayoutController") as it.palsoftware.pastiera.core.SymLayoutController
        sym.openSymbolsPage()
        val entry = sym.currentSymMappings()!!.entries.first { it.key in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z }
        assertTrue(service.onKeyDown(entry.key, KeyEvent(1000,1000,KeyEvent.ACTION_DOWN,entry.key,0)))
        assertEquals(entry.value, ic.editable.toString())
    }

    @Test fun latinNumericAndSymControlsStillWork() {
        val (service, ic) = serviceWithEditor(InputType.TYPE_CLASS_NUMBER)
        service.javaClass.getDeclaredMethod("switchToLayout",String::class.java,Boolean::class.javaPrimitiveType).apply { isAccessible=true }.invoke(service,"qwerty",false)
        service.addAltKeyMapping(KeyEvent.KEYCODE_R, "1")
        service.onKeyDown(KeyEvent.KEYCODE_R, KeyEvent(1000,1000,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_R,0))
        assertEquals("1", ic.editable.toString())
        val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; packageName = "test.audit" }
        setField(service,"mInputEditorInfo",info)
        setField(service,"inputContextState",InputContextState.fromEditorInfo(info))
        val sym = getField(service,"symLayoutController") as it.palsoftware.pastiera.core.SymLayoutController
        sym.openSymbolsPage()
        val entry = sym.currentSymMappings()!!.entries.first { it.key in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z }
        service.onKeyDown(entry.key, KeyEvent(2000,2000,KeyEvent.ACTION_DOWN,entry.key,0))
        assertEquals("1" + entry.value, ic.editable.toString())
    }

    @Test fun softwareSymbolFooterPreservesHangulBeforeEveryInsertion() {
        for (text in listOf(",", ".", " ")) {
            val (service, ic) = serviceWithEditor(InputType.TYPE_CLASS_TEXT)
            val c = getField(service,"compositionCoordinator") as ImeCompositionCoordinator
            type(c,ic,"ㅎㅏㄴㄱㅡㄹ")
            val bars = getField(service,"candidatesBarController") as CandidatesBarController
            val status = getField(bars,"inputStatusBar")!!
            status.javaClass.getDeclaredMethod("commitSoftwareSymbolText", InputConnection::class.java, String::class.java)
                .apply { isAccessible = true }.invoke(status,ic,text)
            assertEquals("한글$text",ic.editable.toString())
            assertFalse(c.isActive)
        }
    }

    @Test fun softwareDeleteAndPhysicalDeleteRemoveFinalJamo() {
        val (service, ic) = serviceWithEditor(InputType.TYPE_CLASS_TEXT)
        val c = getField(service,"compositionCoordinator") as ImeCompositionCoordinator
        type(c,ic,"ㄱ")
        assertTrue(service.onKeyDown(KeyEvent.KEYCODE_DEL,KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL)))
        assertEquals("",ic.editable.toString())
        type(c,ic,"ㄴ")
        val bars = getField(service,"candidatesBarController") as CandidatesBarController
        assertTrue(bars.onSoftwareKeyboardBackspace!!.invoke(ic))
        assertEquals("",ic.editable.toString())
    }

    @Test fun phoneFieldKeepsNumericMapping() {
        val (service,ic) = serviceWithEditor(InputType.TYPE_CLASS_PHONE)
        service.addAltKeyMapping(KeyEvent.KEYCODE_R,"1")
        assertTrue(service.onKeyDown(KeyEvent.KEYCODE_R,KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_R)))
        assertEquals("1",ic.editable.toString())
    }

    @Test fun activeSymMappingCommitsExistingHangulBeforeSymbol() {
        val (service,ic) = serviceWithEditor(InputType.TYPE_CLASS_TEXT)
        val c = getField(service,"compositionCoordinator") as ImeCompositionCoordinator
        type(c,ic,"ㅎㅏㄴㄱㅡㄹ")
        val sym = getField(service,"symLayoutController") as it.palsoftware.pastiera.core.SymLayoutController
        sym.openSymbolsPage()
        val entry = sym.currentSymMappings()!!.entries.first { it.key in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z }
        assertTrue(service.onKeyDown(entry.key,KeyEvent(KeyEvent.ACTION_DOWN,entry.key)))
        assertEquals("한글"+entry.value,ic.editable.toString())
    }

    @Test fun clipboardAndMultiCharacterStaticVariationPreserveRun() {
        val (service,ic) = serviceWithEditor(InputType.TYPE_CLASS_TEXT)
        val c = getField(service,"compositionCoordinator") as ImeCompositionCoordinator
        type(c,ic,"ㅎㅏㄴㄱㅡㄹ")
        val clipboard = getField(service,"clipboardHistoryManager") as it.palsoftware.pastiera.clipboard.ClipboardHistoryManager
        clipboard.pasteText(" pasted",ic)
        assertEquals("한글 pasted",ic.editable.toString())
        type(c,ic,"ㄱㅏ")
        val bars = getField(service,"candidatesBarController") as CandidatesBarController
        VariationButtonHandler.createStaticVariationClickListener("🙂",ic,RuntimeEnvironment.getApplication(),bars.onVariationSelectedListener).onClick(View(RuntimeEnvironment.getApplication()))
        assertEquals("한글 pasted가🙂",ic.editable.toString())
    }

    @Test fun allModernSyllablesComposeFromTwoSetStrokes() {
        val initials = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
        val medials = listOf("ㅏ","ㅐ","ㅑ","ㅒ","ㅓ","ㅔ","ㅕ","ㅖ","ㅗ","ㅗㅏ","ㅗㅐ","ㅗㅣ","ㅛ","ㅜ","ㅜㅓ","ㅜㅔ","ㅜㅣ","ㅠ","ㅡ","ㅡㅣ","ㅣ")
        val finals = listOf("","ㄱ","ㄲ","ㄱㅅ","ㄴ","ㄴㅈ","ㄴㅎ","ㄷ","ㄹ","ㄹㄱ","ㄹㅁ","ㄹㅂ","ㄹㅅ","ㄹㅌ","ㄹㅍ","ㄹㅎ","ㅁ","ㅂ","ㅂㅅ","ㅅ","ㅆ","ㅇ","ㅈ","ㅊ","ㅋ","ㅌ","ㅍ","ㅎ")
        var cases = 0
        initials.forEachIndexed { l, initial -> medials.forEachIndexed { v, medial -> finals.forEachIndexed { t, final ->
            val composer = it.palsoftware.pastiera.core.composition.HangulComposer()
            val strokes = initial.toString()+medial+final
            strokes.forEach { composer.accept(NormalizedCompositionStroke(it)) }
            assertEquals((0xAC00 + (l*21+v)*28+t).toChar().toString(), composer.snapshot?.renderedText)
            cases++
            for (end in strokes.length-1 downTo 0) {
                val actual = (composer.backspace() as it.palsoftware.pastiera.core.composition.ComposerUpdate.Replace).snapshot.renderedText
                val prefix = it.palsoftware.pastiera.core.composition.HangulComposer()
                strokes.take(end).forEach { prefix.accept(NormalizedCompositionStroke(it)) }
                assertEquals(prefix.snapshot?.renderedText.orEmpty(),actual)
                cases++
            }
        } } }
        assertEquals(52402,cases)
    }

    private fun serviceWithEditor(inputTypeValue: Int): Pair<PhysicalKeyboardInputMethodService, BaseInputConnection> {
        val service = Robolectric.buildService(PhysicalKeyboardInputMethodService::class.java).create().get()
        val ic = ic()
        val info = EditorInfo().apply { inputType = inputTypeValue; packageName = "test.audit" }
        setField(service,"mInputConnection",ic)
        setField(service,"mStartedInputConnection",ic)
        setField(service,"mInputEditorInfo",info)
        setField(service,"isInputViewActive",true)
        setField(service,"inputContextState",InputContextState.fromEditorInfo(info))
        service.javaClass.getDeclaredMethod("switchToLayout",String::class.java,Boolean::class.javaPrimitiveType).apply { isAccessible=true }.invoke(service,"korean_2set",false)
        return service to ic
    }

    private fun getField(target: Any, name: String): Any? {
        var k: Class<*>? = target.javaClass
        while (k != null) {
            try { return k.getDeclaredField(name).apply { isAccessible=true }.get(target) }
            catch (_: NoSuchFieldException) { k=k.superclass }
        }
        error(name)
    }
    private fun setField(target: Any, name: String, value: Any) {
        var k: Class<*>? = target.javaClass
        while (k != null) {
            try { k.getDeclaredField(name).apply { isAccessible=true }.set(target,value); return }
            catch (_: NoSuchFieldException) { k=k.superclass }
        }
        error(name)
    }
}
