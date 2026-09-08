package it.palsoftware.pastiera.inputmethod

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import it.palsoftware.pastiera.SettingsManager
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CandidatesBarControllerTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        SettingsManager.getVariationsFile(context).delete()
    }

    @After
    fun tearDown() {
        SettingsManager.setSoftwareKeyboardMode(context, SettingsManager.SoftwareKeyboardMode.AUTO)
        SettingsManager.setSuggestionsEnabled(context, true)
        SettingsManager.setStaticVariationBarPreset(context, SettingsManager.STATIC_VARIATION_PRESET_OFF)
        SettingsManager.setStatusBarVariationsVisible(context, true)
        SettingsManager.getVariationsFile(context).delete()
        SoftwareKeyboardAutoDetector.onInputDevicesChanged()
    }

    @Test
    fun detachedInputViewIsNotReportedAsRendered() {
        val controller = CandidatesBarController(context)
        val inputView = controller.getInputView()
        inputView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        inputView.layout(0, 0, inputView.measuredWidth, inputView.measuredHeight)

        assertFalse(controller.isInputViewActuallyRendered())
    }

    @Test
    fun attachedAndLaidOutInputViewIsReportedAsRendered() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val controller = CandidatesBarController(activity)
        val inputView = controller.getInputView()
        activity.setContentView(inputView)
        val decorView = activity.window.decorView
        decorView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY)
        )
        decorView.layout(0, 0, 1080, 2400)

        assertTrue(controller.isInputViewActuallyRendered())
    }

    @Test
    fun candidatesViewIsNotCollapsedByConfiguredSoftwareKeyboardMode() {
        SettingsManager.setSoftwareKeyboardMode(
            context,
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        )
        val controller = CandidatesBarController(context)

        val candidatesView = controller.getCandidatesView()

        assertEquals(View.VISIBLE, candidatesView.visibility)
        assertNotEquals(0, candidatesView.layoutParams.height)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, candidatesView.layoutParams.height)
    }

    @Test
    fun candidatesViewRendersHardwareStatusRow() {
        val controller = CandidatesBarController(context)
        val candidatesView = controller.getCandidatesView()

        controller.updateStatusBars(
            snapshot = emptyStatusSnapshot(),
            emojiMapText = "",
            inputConnection = null,
            symMappings = null
        )
        candidatesView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        assertNotEquals(0, candidatesView.measuredHeight)
    }

    @Test
    fun fullInputSurfaceCollapsesGhostCandidatesHeightAndPhysicalSurfaceRestoresIt() {
        val controller = CandidatesBarController(context)
        val candidatesView = controller.getCandidatesView()

        controller.setCandidatesSurfaceActive(false)
        controller.updateStatusBars(emptyStatusSnapshot(), "", null, null)

        assertEquals(View.GONE, candidatesView.visibility)
        assertEquals(0, candidatesView.layoutParams.height)

        controller.setCandidatesSurfaceActive(true)
        controller.updateStatusBars(emptyStatusSnapshot(), "", null, null)

        assertEquals(View.VISIBLE, candidatesView.visibility)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, candidatesView.layoutParams.height)
    }

    @Test
    fun onScreenToPhysicalKeepsUtilitySymbolsWhenSuggestionsAreDisabled() {
        SettingsManager.setSuggestionsEnabled(context, false)
        SettingsManager.setStaticVariationBarPreset(
            context,
            SettingsManager.STATIC_VARIATION_PRESET_DEV_CHOICE
        )
        SettingsManager.setStatusBarVariationsVisible(context, true)
        SettingsManager.setSoftwareKeyboardMode(
            context,
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        )
        val controller = CandidatesBarController(context)

        controller.getInputView()
        controller.updateStatusBars(emptyStatusSnapshot(), "", null, null)

        SettingsManager.setSoftwareKeyboardMode(
            context,
            SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE
        )
        val candidatesView = controller.getCandidatesView()
        controller.updateStatusBars(emptyStatusSnapshot(), "", null, null)
        candidatesView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        candidatesView.layout(0, 0, candidatesView.measuredWidth, candidatesView.measuredHeight)

        val visibleTexts = collectLaidOutVisibleTexts(candidatesView)
        assertTrue(
            visibleTexts.containsAll(SettingsManager.getDevChoiceStaticVariationBasePreset())
        )
    }

    @Test
    fun customizedBaseRowReplacesEveryPresetWhileKeyboardIsVisible() {
        SettingsManager.setSuggestionsEnabled(context, false)
        SettingsManager.setStatusBarVariationsVisible(context, true)
        val controller = CandidatesBarController(context)
        val candidatesView = controller.getCandidatesView()
        val presets = listOf(
            SettingsManager.STATIC_VARIATION_PRESET_SYMBOLS,
            SettingsManager.STATIC_VARIATION_PRESET_NUMBERS,
            SettingsManager.STATIC_VARIATION_PRESET_ALTERNATIVE,
            SettingsManager.STATIC_VARIATION_PRESET_DEV_CHOICE
        )

        presets.forEachIndexed { index, preset ->
            SettingsManager.setStaticVariationBarPreset(context, preset)
            val customBase = listOf("custom-229-$index", "slot-$index")
            SettingsManager.saveVariations(
                context = context,
                variations = emptyMap(),
                staticVariations = customBase,
                staticVariationsShift = listOf("shift-229-$index"),
                staticVariationsAlt = listOf("alt-229-$index")
            )
            controller.invalidateStaticVariations()

            controller.updateStatusBars(emptyStatusSnapshot(), "", null, null)
            val visibleTexts = layoutAndCollectVisibleTexts(candidatesView)

            assertTrue("Preset $preset ignored the customized base row", visibleTexts.containsAll(customBase))
        }
    }

    @Test
    fun customizedModifierRowsRefreshWhileKeyboardIsVisible() {
        SettingsManager.setSuggestionsEnabled(context, false)
        SettingsManager.setStatusBarVariationsVisible(context, true)
        SettingsManager.setStaticVariationBarPreset(
            context,
            SettingsManager.STATIC_VARIATION_PRESET_NUMBERS
        )
        val controller = CandidatesBarController(context)
        val candidatesView = controller.getCandidatesView()

        SettingsManager.saveVariations(
            context = context,
            variations = emptyMap(),
            staticVariations = listOf("base-229"),
            staticVariationsShift = listOf("shift-229"),
            staticVariationsAlt = listOf("alt-229")
        )
        controller.invalidateStaticVariations()

        controller.updateStatusBars(
            emptyStatusSnapshot().copy(shiftPhysicallyPressed = true),
            "",
            null,
            null
        )
        assertTrue(layoutAndCollectVisibleTexts(candidatesView).contains("shift-229"))

        controller.updateStatusBars(
            emptyStatusSnapshot().copy(altPhysicallyPressed = true),
            "",
            null,
            null
        )
        assertTrue(layoutAndCollectVisibleTexts(candidatesView).contains("alt-229"))
    }

    private fun layoutAndCollectVisibleTexts(view: View): List<String> {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return collectLaidOutVisibleTexts(view)
    }

    private fun collectLaidOutVisibleTexts(view: View): List<String> {
        if (view.visibility != View.VISIBLE || view.width <= 0 || view.height <= 0) return emptyList()
        return when (view) {
            is TextView -> listOf(view.text.toString())
            is ViewGroup -> (0 until view.childCount).flatMap {
                collectLaidOutVisibleTexts(view.getChildAt(it))
            }
            else -> emptyList()
        }
    }

    private fun emptyStatusSnapshot() = StatusBarController.StatusSnapshot(
        capsLockEnabled = false,
        shiftPhysicallyPressed = false,
        shiftOneShot = false,
        ctrlLatchActive = false,
        ctrlPhysicallyPressed = false,
        ctrlOneShot = false,
        ctrlLatchFromNavMode = false,
        altLatchActive = false,
        altPhysicallyPressed = false,
        altOneShot = false,
        symPage = 0
    )
}
