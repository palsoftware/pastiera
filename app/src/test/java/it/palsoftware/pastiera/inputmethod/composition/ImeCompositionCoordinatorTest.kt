package it.palsoftware.pastiera.inputmethod.composition

import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.core.composition.CompositionInputSource
import it.palsoftware.pastiera.core.composition.NormalizedCompositionStroke
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeCompositionCoordinatorTest {

    @Test
    fun composingAfterCommittedPrefix_usesRelativeSelectionExpectations() {
        val coordinator = ImeCompositionCoordinator()
        val inputConnection = successfulInputConnection()
        coordinator.startEditorSession()
        coordinator.selectLayout("korean_2set")

        assertTrue(coordinator.handleMappedStroke(inputConnection, stroke('ㄱ')))
        assertEquals(
            SelectionUpdateDisposition.OWN_COMPOSING_UPDATE,
            coordinator.onUpdateSelection(4, 4, 5, 5, 4, 5)
        )
        assertTrue(coordinator.handleMappedStroke(inputConnection, stroke('ㅏ')))
        assertEquals(
            SelectionUpdateDisposition.OWN_COMPOSING_UPDATE,
            coordinator.onUpdateSelection(5, 5, 5, 5, 4, 5)
        )
        assertEquals("가", coordinator.snapshot?.renderedText)

        coordinator.finish(inputConnection, CompositionFinishReason.BOUNDARY)
        assertEquals(
            SelectionUpdateDisposition.NOT_TRACKED,
            coordinator.onUpdateSelection(5, 5, 6, 6, -1, -1)
        )

        assertTrue(coordinator.handleMappedStroke(inputConnection, stroke('ㅌ')))
        assertEquals(
            SelectionUpdateDisposition.OWN_COMPOSING_UPDATE,
            coordinator.onUpdateSelection(6, 6, 7, 7, 6, 7)
        )
        assertTrue(coordinator.handleMappedStroke(inputConnection, stroke('ㅔ')))
        assertEquals(
            SelectionUpdateDisposition.OWN_COMPOSING_UPDATE,
            coordinator.onUpdateSelection(7, 7, 7, 7, 6, 7)
        )
        assertTrue(coordinator.isActive)
        assertEquals("테", coordinator.snapshot?.renderedText)
    }

    @Test
    fun activeComposition_stillRejectsUnrelatedCursorMovement() {
        val coordinator = ImeCompositionCoordinator()
        val inputConnection = successfulInputConnection()
        coordinator.startEditorSession()
        coordinator.selectLayout("korean_2set")

        assertTrue(coordinator.handleMappedStroke(inputConnection, stroke('ㄱ')))

        assertEquals(
            SelectionUpdateDisposition.EXTERNAL_CHANGE,
            coordinator.onUpdateSelection(4, 4, 2, 2, -1, -1)
        )
    }

    private fun stroke(jamo: Char) = NormalizedCompositionStroke(
        jamo = jamo,
        physicalKeyCode = null,
        source = CompositionInputSource.PHYSICAL,
        shiftedByUser = false
    )

    private fun successfulInputConnection(): InputConnection {
        return Proxy.newProxyInstance(
            InputConnection::class.java.classLoader,
            arrayOf(InputConnection::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "beginBatchEdit", "endBatchEdit", "setComposingText", "finishComposingText" -> true
                else -> defaultValue(method.returnType)
            }
        } as InputConnection
    }

    private fun defaultValue(type: Class<*>): Any? = when {
        type == Boolean::class.javaPrimitiveType -> false
        type == Int::class.javaPrimitiveType -> 0
        type == Long::class.javaPrimitiveType -> 0L
        else -> null
    }
}
