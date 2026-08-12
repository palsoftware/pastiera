package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VariationButtonHandlerTest {

    @Test
    fun contextualAndStaticPunctuationUseBoundaryHandlerBeforeDirectCommit() {
        val context = RuntimeEnvironment.getApplication()

        listOf(false, true).forEach { static ->
            val inputConnection = FakeInputConnection(context, "teh")
            val selected = mutableListOf<String>()
            val requested = mutableListOf<String>()
            val listener = object : VariationButtonHandler.OnVariationSelectedListener {
                override fun onVariationSelected(variation: String) {
                    selected += variation
                }

                override fun onBoundaryTextRequested(
                    variation: String,
                    inputConnection: InputConnection
                ): Boolean {
                    requested += variation
                    inputConnection.deleteSurroundingText(3, 0)
                    inputConnection.commitText("the$variation", 1)
                    return true
                }
            }
            val clickListener = if (static) {
                VariationButtonHandler.createStaticVariationClickListener(
                    "?",
                    inputConnection,
                    context,
                    listener
                )
            } else {
                VariationButtonHandler.createVariationClickListener(
                    "?",
                    inputConnection,
                    context,
                    listener
                )
            }

            clickListener.onClick(View(context))

            assertEquals(listOf("?"), requested)
            assertEquals(listOf("?"), selected)
            assertEquals("the?", inputConnection.text)
        }
    }

    private class FakeInputConnection(
        context: Context,
        initialText: String
    ) : BaseInputConnection(View(context), true) {
        private val buffer = StringBuilder(initialText)

        val text: String
            get() = buffer.toString()

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = buffer.takeLast(n)

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val deleteStart = (buffer.length - beforeLength).coerceAtLeast(0)
            buffer.delete(deleteStart, buffer.length)
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            buffer.append(text ?: "")
            return true
        }
    }
}
