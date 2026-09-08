package it.palsoftware.pastiera.clipboard

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.widget.FrameLayout
import android.widget.TextView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.composition.NormalizedCompositionStroke
import it.palsoftware.pastiera.inputmethod.composition.CompositionFinishReason
import it.palsoftware.pastiera.inputmethod.composition.ImeCompositionCoordinator
import it.palsoftware.pastiera.inputmethod.ui.ClipboardHistoryView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ClipboardHistoryPrivacyTest {
    private lateinit var context: Context
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var accessPolicy: MutableAccessPolicy
    private lateinit var historyManager: ClipboardHistoryManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SettingsManager.setClipboardHistoryEnabled(context, true)
        clipboardManager = context.getSystemService(ClipboardManager::class.java)
        clipboardManager.clearPrimaryClip()
        accessPolicy = MutableAccessPolicy(accessible = true)
        historyManager = ClipboardHistoryManager(context, accessPolicy)
        historyManager.onCreate()
        historyManager.clearHistory()
    }

    @After
    fun tearDown() {
        accessPolicy.accessible = true
        historyManager.clearHistory()
        historyManager.onDestroy()
        clipboardManager.clearPrimaryClip()
        SettingsManager.setClipboardHistoryEnabled(context, true)
    }

    @Test
    fun systemPolicyFailsClosedWhileLockedOrScreenIsOff() {
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val policy = SystemClipboardHistoryAccessPolicy(context)

        shadowOf(powerManager).setIsInteractive(true)
        shadowOf(keyguardManager).setIsDeviceLocked(false)
        assertTrue(policy.isHistoryAccessible())

        shadowOf(keyguardManager).setIsDeviceLocked(true)
        assertFalse(policy.isHistoryAccessible())

        shadowOf(keyguardManager).setIsDeviceLocked(false)
        shadowOf(powerManager).setIsInteractive(false)
        assertFalse(policy.isHistoryAccessible())
    }

    @Test
    fun clipboardChangesWhileLockedAreNotCaptured() {
        copyToSystemClipboard("visible-secret")
        historyManager.onPrimaryClipChanged()
        assertEquals(1, historyManager.getHistorySize())

        setAccessible(false)
        copyToSystemClipboard("locked-secret")
        historyManager.onPrimaryClipChanged()

        assertEquals(0, historyManager.getHistorySize())
        assertNull(historyManager.getHistoryEntry(0))

        setAccessible(true)
        assertEquals(1, historyManager.getHistorySize())
        assertEquals("visible-secret", historyManager.getHistoryEntry(0)?.text)
    }

    @Test
    fun allHistoryActionsAreBlockedWhileLocked() {
        copyToSystemClipboard("protected-secret")
        historyManager.onPrimaryClipChanged()
        val entry = requireNotNull(historyManager.getHistoryEntry(0))
        val inputConnection = RecordingInputConnection(context)

        setAccessible(false)
        historyManager.toggleClipPinned(entry.id)
        historyManager.removeEntry(0, force = true)
        historyManager.clearHistory()
        historyManager.pasteText(entry.text, inputConnection)

        assertFalse(historyManager.canRemove(0))
        assertNull(historyManager.getHistoryEntryContent(entry.id))
        assertTrue(inputConnection.committedTexts.isEmpty())

        setAccessible(true)
        assertEquals(1, historyManager.getHistorySize())
        assertFalse(historyManager.getHistoryEntry(0)?.isPinned == true)
        assertEquals("protected-secret", historyManager.getHistoryEntry(0)?.text)
    }

    @Test
    fun pastePreservesCompositionWhileLockedAndFinishesItBeforeUnlockedCommit() {
        val inputConnection = BaseInputConnection(View(context), true)
        val coordinator = ImeCompositionCoordinator().apply {
            startEditorSession()
            selectLayout("korean_2set")
        }
        val manager = ClipboardHistoryManager(context, accessPolicy) { connection ->
            coordinator.finish(connection, CompositionFinishReason.COMMAND)
        }
        "ㅎㅏㄴ".forEach {
            assertTrue(coordinator.handleMappedStroke(inputConnection, NormalizedCompositionStroke(it)))
        }
        assertEquals("한", inputConnection.editable.toString())

        accessPolicy.accessible = false
        manager.pasteText("paste", inputConnection)
        assertTrue(coordinator.isActive)
        assertEquals("한", inputConnection.editable.toString())

        accessPolicy.accessible = true
        manager.pasteText("paste", inputConnection)
        assertFalse(coordinator.isActive)
        assertEquals("한paste", inputConnection.editable.toString())
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(requireNotNull(inputConnection.editable)))
    }

    @Test
    fun accessListenersReceiveOnlyRealStateTransitions() {
        val states = mutableListOf<Boolean>()
        historyManager.addAccessStateListener(states::add)

        setAccessible(false)
        historyManager.refreshAccessState()
        setAccessible(true)

        assertEquals(listOf(false, true), states)
    }

    @Test
    fun screenOffBroadcastFailsClosedBeforeSystemStateCatchesUp() {
        val states = mutableListOf<Boolean>()
        historyManager.addAccessStateListener(states::add)

        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(historyManager.isHistoryAccessible())
        assertEquals(listOf(false), states)

        context.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(historyManager.isHistoryAccessible())
        assertEquals(listOf(false, true), states)
    }

    @Test
    fun inlineHistoryReplacesSensitiveRowsWithLockedMessage() {
        copyToSystemClipboard("view-secret")
        historyManager.onPrimaryClipChanged()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val view = ClipboardHistoryView(activity, historyManager)
        activity.setContentView(view)
        shadowOf(Looper.getMainLooper()).idle()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(visibleTexts(view).contains("view-secret"))

        setAccessible(false)

        val lockedTexts = visibleTexts(view)
        assertFalse(lockedTexts.contains("view-secret"))
        assertTrue(lockedTexts.contains(context.getString(R.string.clipboard_locked_state)))
        assertFalse(visibleContentDescriptions(view).contains("view-secret"))
    }

    @Test
    fun popupHistoryReplacesSensitiveRowsImmediatelyWhenLocked() {
        copyToSystemClipboard("popup-secret")
        historyManager.onPrimaryClipChanged()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        activity.setContentView(FrameLayout(activity))
        val popup = ClipboardHistoryPopupView(activity, historyManager)

        try {
            popup.show()
            shadowOf(Looper.getMainLooper()).idle()
            val contentView = popup.javaClass.getDeclaredField("contentView").run {
                isAccessible = true
                get(popup) as View
            }
            assertTrue(visibleTexts(contentView).contains("popup-secret"))

            setAccessible(false)

            val lockedTexts = visibleTexts(contentView)
            assertFalse(lockedTexts.contains("popup-secret"))
            assertFalse(lockedTexts.contains(context.getString(R.string.clipboard_clear_all)))
            assertTrue(lockedTexts.contains(context.getString(R.string.clipboard_locked_state)))
            assertFalse(visibleContentDescriptions(contentView).contains("popup-secret"))
        } finally {
            popup.dismiss()
        }
    }

    private fun copyToSystemClipboard(text: String) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("test", text))
    }

    private fun setAccessible(accessible: Boolean) {
        accessPolicy.accessible = accessible
        historyManager.refreshAccessState()
    }

    private fun visibleTexts(view: View): List<String> {
        if (view.visibility != View.VISIBLE) return emptyList()
        return when (view) {
            is TextView -> listOf(view.text.toString())
            is ViewGroup -> (0 until view.childCount).flatMap { visibleTexts(view.getChildAt(it)) }
            else -> emptyList()
        }
    }

    private fun visibleContentDescriptions(view: View): List<String> {
        if (view.visibility != View.VISIBLE) return emptyList()
        val own = view.contentDescription?.toString()?.let(::listOf).orEmpty()
        return if (view is ViewGroup) {
            own + (0 until view.childCount).flatMap { visibleContentDescriptions(view.getChildAt(it)) }
        } else {
            own
        }
    }

    private class MutableAccessPolicy(var accessible: Boolean) : ClipboardHistoryAccessPolicy {
        override fun isHistoryAccessible(): Boolean = accessible
    }

    private class RecordingInputConnection(context: Context) :
        BaseInputConnection(View(context), true) {
        val committedTexts = mutableListOf<String>()

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            committedTexts += text?.toString().orEmpty()
            return true
        }
    }
}
