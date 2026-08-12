package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.view.View
import android.view.inputmethod.BaseInputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.AutoCorrectionManager
import it.palsoftware.pastiera.core.AutoSpaceTracker
import it.palsoftware.pastiera.core.ModifierStateController
import it.palsoftware.pastiera.core.NavModeController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InputEventRouterBoundaryTextTest {

    private lateinit var context: Context
    private lateinit var router: InputEventRouter
    private lateinit var autoCorrectionManager: AutoCorrectionManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SettingsManager.getPreferences(context).edit().clear().commit()
        AutoSpaceTracker.clear()
        SettingsManager.saveCustomAutoCorrections(context, "test", mapOf("teh" to "the"))
        SettingsManager.setAutoCorrectEnabledLanguages(context, setOf("test"))
        AutoCorrector.loadCorrections(context.assets, context)
        router = InputEventRouter(
            context,
            NavModeController(context, ModifierStateController(300L))
        )
        autoCorrectionManager = AutoCorrectionManager(context)
    }

    @Test
    fun generatedPunctuationUsesLegacyCorrectionBeforeCommittingBoundary() {
        listOf('?', '!', ':', ',', '.', ';').forEach { boundary ->
            val inputConnection = FakeInputConnection(context, "teh")

            val handled = router.handleBoundaryText(
                context = context,
                text = boundary.toString(),
                inputConnection = inputConnection,
                shouldDisableSuggestions = true,
                isAutoCorrectEnabled = true,
                autoCorrectionManager = autoCorrectionManager,
                updateStatusBar = {}
            )

            assertTrue("Expected '$boundary' to be handled", handled)
            assertEquals("the$boundary", inputConnection.text)
        }
    }

    @Test
    fun apostrophesAndNonBoundaryTextStayOnCharacterPath() {
        listOf("'", "’", "a", "🙂").forEach { text ->
            val inputConnection = FakeInputConnection(context, "teh")

            val handled = router.handleBoundaryText(
                context = context,
                text = text,
                inputConnection = inputConnection,
                shouldDisableSuggestions = true,
                isAutoCorrectEnabled = true,
                autoCorrectionManager = autoCorrectionManager,
                updateStatusBar = {}
            )

            assertFalse("Expected '$text' to stay on the character path", handled)
            assertEquals("teh", inputConnection.text)
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
