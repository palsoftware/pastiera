package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.SymLayoutController
import it.palsoftware.pastiera.inputmethod.KeyboardVisibilityController.RenderedSurface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeyboardVisibilityControllerTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        SettingsManager.getPreferences(context).edit().remove("experimental_candidates_view_enabled").commit()
        SettingsManager.setSoftwareKeyboardModeRuntimeOverride(context, SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE)
    }

    @After
    fun tearDown() {
        SettingsManager.setExperimentalCandidatesViewEnabled(context, false)
        SettingsManager.setSoftwareKeyboardMode(context, SettingsManager.SoftwareKeyboardMode.AUTO)
        SettingsManager.setSoftwareKeyboardModeRuntimeOverride(context, null)
        SoftwareKeyboardAutoDetector.onInputDevicesChanged()
    }

    @Test
    fun existingInstallDefaultsToFullInputViewForHardware() {
        val h = Harness()
        assertFalse(SettingsManager.getExperimentalCandidatesViewEnabled(context))
        assertTrue(h.controller.onEvaluateInputViewShown(false))
        h.controller.ensureImeSurfaceVisible()
        assertEquals(0, h.inputAttachments)
        h.runNext()
        assertEquals(1, h.inputAttachments)
        assertEquals(0, h.candidatesAttachments)
        assertEquals(listOf(true), h.requestedInputShown)
        assertEquals(1, h.showRequests)
        assertFalse(h.controller.isExpectedSurfaceRequestedOrShown())
    }

    @Test
    fun evaluationDoesNotShowAttachOrScheduleAnySurface() {
        val h = Harness()
        for (experimental in listOf(false, true)) {
            SettingsManager.setExperimentalCandidatesViewEnabled(context, experimental)
            for (systemDecision in listOf(false, true)) {
                assertEquals(!experimental, h.controller.onEvaluateInputViewShown(systemDecision))
            }
        }
        assertEquals(0, h.inputAttachments + h.candidatesAttachments + h.showRequests + h.refreshes)
        assertTrue(h.actions.isEmpty())
        assertTrue(h.candidatesShown.isEmpty())
        assertTrue(h.requestedInputShown.isEmpty())
        assertTrue(h.windowShows.isEmpty())
    }

    @Test
    fun experimentalHardwareAttachesCandidatesAndShowsCandidatesWindow() {
        SettingsManager.setExperimentalCandidatesViewEnabled(context, true)
        val h = Harness()
        h.controller.ensureImeSurfaceVisible()
        h.runNext()
        assertEquals(1, h.candidatesAttachments)
        assertEquals(0, h.inputAttachments)
        assertEquals(listOf(false), h.requestedInputShown)
        assertEquals(listOf(true), h.candidatesShown)
        assertEquals(listOf(false), h.windowShows)
        assertEquals(1, h.showRequests)
        assertEquals(2, h.containerSynchronizations)
    }

    @Test
    fun virtualModeUsesInputViewEvenWithExperimentEnabled() {
        SettingsManager.setExperimentalCandidatesViewEnabled(context, true)
        SettingsManager.setSoftwareKeyboardModeRuntimeOverride(context, SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL)
        val h = Harness()
        assertTrue(h.controller.onEvaluateInputViewShown(false))
        assertFalse(h.controller.shouldShowSurfaceOnInputStart(false))
        assertTrue(h.controller.shouldShowSurfaceOnInputStart(true))
        h.controller.ensureImeSurfaceVisible()
        h.runNext()
        assertEquals(1, h.inputAttachments)
        assertEquals(0, h.candidatesAttachments)
    }

    @Test
    fun frameworkLifecycleFlagsWithoutRenderedChildDoNotProveSuccess() {
        SettingsManager.setExperimentalCandidatesViewEnabled(context, true)
        val h = Harness()
        h.controller.onCandidatesViewStarted()
        h.controller.onImeWindowVisibilityChanged(true)
        h.controller.onExplicitShowRequested()
        h.runNext()
        assertFalse(h.controller.isExpectedSurfaceRequestedOrShown())
        assertTrue(h.controller.shouldRecoverSurfaceOnHardwareKey())
        h.drain()
        assertEquals(1, h.windowHides)
        assertEquals(1, h.hideRequests)
    }

    @Test
    fun noFrameworkResponseRetriesBoundedlyThenClosesGhostWindow() {
        for (experimental in listOf(false, true)) {
            SettingsManager.setExperimentalCandidatesViewEnabled(context, experimental)
            val h = Harness()
            h.controller.ensureImeSurfaceVisible()
            repeat(20) { h.controller.onHardwareInputRequested() }
            h.drain()
            assertEquals(3, h.inputAttachments + h.candidatesAttachments)
            assertEquals(1, h.showRequests)
            assertEquals(1, h.windowHides)
            assertEquals(1, h.hideRequests)
            assertFalse(h.candidatesShown.last())
            assertFalse(h.controller.isExpectedSurfaceRequestedOrShown())
        }
    }

    @Test
    fun deliveredRenderedSurfaceStopsRetryAndRepeatedKeysDoNotShowAgain() {
        for (experimental in listOf(false, true)) {
            SettingsManager.setExperimentalCandidatesViewEnabled(context, experimental)
            val h = Harness()
            h.controller.ensureImeSurfaceVisible()
            h.runNext()
            // Deliver framework rendering separately: show requests in this harness never render.
            h.rendered = if (experimental) RenderedSurface.CANDIDATES_VIEW else RenderedSurface.FULL_INPUT_VIEW
            h.inputShown = !experimental
            h.controller.onImeWindowVisibilityChanged(true)
            h.drain()
            repeat(10) { h.controller.onHardwareInputRequested() }
            assertTrue(h.controller.isExpectedSurfaceRequestedOrShown())
            assertEquals(1, h.inputAttachments + h.candidatesAttachments)
            assertEquals(0, h.windowHides)
            assertTrue(h.actions.isEmpty())
        }
    }

    @Test
    fun hiddenWindowWithStaleRenderedCandidatesStillSchedulesShow() {
        SettingsManager.setExperimentalCandidatesViewEnabled(context, true)
        val h = Harness()
        h.rendered = RenderedSurface.CANDIDATES_VIEW
        h.controller.onImeWindowVisibilityChanged(true)
        assertTrue(h.controller.isExpectedSurfaceRequestedOrShown())
        h.controller.onImeWindowVisibilityChanged(false)
        assertFalse(h.controller.isExpectedSurfaceRequestedOrShown())
        h.controller.onExplicitShowRequested()
        assertEquals(1, h.actions.size)
        h.runNext()
        assertEquals(1, h.candidatesAttachments)
    }

    @Test
    fun unbindCancelsPendingWorkAndInvalidatesStaleRenderedCandidates() {
        SettingsManager.setExperimentalCandidatesViewEnabled(context, true)
        val h = Harness()
        h.controller.ensureImeSurfaceVisible()
        h.runNext()
        h.rendered = RenderedSurface.CANDIDATES_VIEW
        h.controller.onCandidatesViewStarted()
        h.controller.onImeWindowVisibilityChanged(true)
        assertTrue(h.controller.isExpectedSurfaceRequestedOrShown())

        h.controller.onInputUnbound()
        // AOSP can retain the old connection without finishing input. Leave both fakes intact.
        assertTrue(h.active)
        assertTrue(h.connection != null)
        h.drain()

        assertFalse(h.controller.isExpectedSurfaceRequestedOrShown())
        assertEquals(1, h.candidatesAttachments)
        assertEquals(0, h.windowHides + h.hideRequests)
        assertTrue(h.actions.isEmpty())
        assertFalse(h.controller.isCandidatesSurfaceExplicitlyDismissedForTests())
        h.controller.onCandidatesViewFinished(false)
        assertTrue(h.actions.isEmpty())
    }

    @Test
    fun inputFinishCancellationMakesAlreadyPostedRetriesInert() {
        val h = Harness()
        h.controller.ensureImeSurfaceVisible()
        h.runNext()
        // The service invokes this on finish before disposing its input connection.
        h.controller.cancelPendingSurfaceTransition()
        h.active = false
        h.connection = null
        h.drain()
        assertEquals(1, h.inputAttachments)
        assertEquals(0, h.windowHides + h.hideRequests)
    }

    @Test
    fun externalHideCancelsPendingRetries() {
        val h = Harness()
        h.controller.ensureImeSurfaceVisible()
        h.runNext()
        h.controller.onImeWindowVisibilityChanged(false)
        h.drain()
        assertEquals(1, h.inputAttachments)
        assertEquals(0, h.windowHides + h.hideRequests)
        assertTrue(h.controller.isCandidatesSurfaceExplicitlyDismissedForTests())
    }

    @Test
    fun tapAndHardwareKeyCanEachReopenAfterExplicitDismissal() {
        SettingsManager.setExperimentalCandidatesViewEnabled(context, true)
        for (tap in listOf(false, true)) {
            val h = Harness()
            h.controller.onCandidatesViewStarted()
            h.controller.onCandidatesViewFinished(false)
            h.drain()
            assertTrue(h.controller.isCandidatesSurfaceExplicitlyDismissedForTests())
            assertEquals(1, h.windowHides)
            if (tap) h.controller.onExplicitShowRequested() else h.controller.onHardwareInputRequested()
            h.runNext()
            assertFalse(h.controller.isCandidatesSurfaceExplicitlyDismissedForTests())
            assertEquals(1, h.candidatesAttachments)
            assertFalse(h.controller.isExpectedSurfaceRequestedOrShown())
        }
    }

    @Test
    fun settingChangeCancelsOldRetryAndPresentsNewBackend() {
        val h = Harness()
        h.controller.ensureImeSurfaceVisible()
        h.runNext()
        SettingsManager.setExperimentalCandidatesViewEnabled(context, true)
        h.controller.onKeyboardSurfaceChanged(true)
        h.runNext() // obsolete retry
        assertEquals(0, h.candidatesAttachments)
        h.runNext() // new backend
        assertEquals(1, h.candidatesAttachments)
        assertEquals(1, h.inputAttachments)
        assertEquals(0, h.windowHides)
        SettingsManager.setExperimentalCandidatesViewEnabled(context, false)
        h.controller.onKeyboardSurfaceChanged(true)
        h.runNext() // obsolete candidates retry
        h.runNext() // stable backend
        assertEquals(2, h.inputAttachments)
        assertEquals(1, h.candidatesAttachments)
    }

    @Test
    fun visibleBackendSwitchWaitsForFrameworkHideBeforePresentingTarget() {
        val h = Harness()
        h.rendered = RenderedSurface.FULL_INPUT_VIEW
        h.inputShown = true
        h.controller.onImeWindowVisibilityChanged(true)
        SettingsManager.setExperimentalCandidatesViewEnabled(context, true)

        h.controller.onKeyboardSurfaceChanged(true)
        h.controller.onExplicitShowRequested()
        h.controller.onHardwareInputRequested()
        assertEquals(1, h.hideRequests)
        assertEquals(0, h.windowHides)
        assertEquals(0, h.candidatesAttachments + h.showRequests)
        assertFalse(h.hasImmediateActions())

        h.controller.onImeWindowVisibilityChanged(false)
        assertEquals(0, h.candidatesAttachments + h.showRequests)
        h.runNextUiAction() // Resume only after the enclosing framework hide has returned.
        assertEquals(0, h.candidatesAttachments)
        h.runNextUiAction()
        assertEquals(1, h.candidatesAttachments)
        assertEquals(1, h.showRequests)
    }

    @Test
    fun candidatesFinishDuringControlledHideDoesNotDismissTransition() {
        SettingsManager.setExperimentalCandidatesViewEnabled(context, true)
        val h = Harness()
        h.rendered = RenderedSurface.CANDIDATES_VIEW
        h.controller.onCandidatesViewStarted()
        h.controller.onImeWindowVisibilityChanged(true)
        // A surface/layout change can also rebuild the same backend.
        h.controller.onKeyboardSurfaceChanged(true)
        h.controller.onCandidatesViewFinished(false)
        assertFalse(h.controller.isCandidatesSurfaceExplicitlyDismissedForTests())
        assertFalse(h.hasImmediateActions())
        h.controller.onImeWindowVisibilityChanged(false)
        h.runNextUiAction()
        h.runNextUiAction()
        assertEquals(1, h.candidatesAttachments)
        assertEquals(1, h.hideRequests)
    }

    @Test
    fun cancellationOrUnbindBeforeHideCallbackPreventsBackendReopen() {
        for (unbind in listOf(false, true)) {
            val h = Harness()
            h.controller.onImeWindowVisibilityChanged(true)
            h.controller.onKeyboardSurfaceChanged(true)
            if (unbind) h.controller.onInputUnbound() else h.controller.cancelPendingSurfaceTransition()
            h.controller.onImeWindowVisibilityChanged(false)
            h.drain()
            assertEquals(1, h.hideRequests)
            assertEquals(0, h.showRequests + h.inputAttachments + h.candidatesAttachments)
        }
    }

    @Test
    fun cancellationAfterHideCallbackInvalidatesDeferredBackendReopen() {
        val h = Harness()
        h.controller.onImeWindowVisibilityChanged(true)
        h.controller.onKeyboardSurfaceChanged(true)
        h.controller.onImeWindowVisibilityChanged(false)
        assertTrue(h.hasImmediateActions())
        h.controller.cancelPendingSurfaceTransition()
        h.drain()
        assertEquals(0, h.showRequests + h.inputAttachments + h.candidatesAttachments)
    }

    @Test
    fun missingBackendHideCallbackTimesOutWithoutShowAndNextKeyCanRetry() {
        val h = Harness()
        h.controller.onImeWindowVisibilityChanged(true)
        h.controller.onKeyboardSurfaceChanged(true)
        h.runHideTimeout()
        assertEquals(0, h.showRequests + h.inputAttachments)
        assertTrue(h.controller.isCandidatesSurfaceExplicitlyDismissedForTests())
        assertTrue(h.actions.isEmpty())

        h.controller.onHardwareInputRequested()
        h.runNextUiAction()
        assertEquals(1, h.inputAttachments)
        assertEquals(1, h.showRequests)
    }

    @Test
    fun successfulHideCallbackMakesOldTimeoutHarmlessToNewShow() {
        val h = Harness()
        h.controller.onImeWindowVisibilityChanged(true)
        h.controller.onKeyboardSurfaceChanged(true)
        h.controller.onImeWindowVisibilityChanged(false)
        h.runNextUiAction() // Queue the new presentation generation.
        h.runHideTimeout()
        h.runNextUiAction()
        assertEquals(1, h.inputAttachments)
        assertEquals(1, h.showRequests)
        assertFalse(h.controller.isCandidatesSurfaceExplicitlyDismissedForTests())
    }

    @Test
    fun rejectedBackendHideDoesNotLeaveWaitingLatchStuck() {
        val h = Harness()
        h.rejectHideRequest = true
        h.controller.onImeWindowVisibilityChanged(true)
        h.controller.onKeyboardSurfaceChanged(true)
        assertTrue(h.actions.isEmpty())
        assertEquals(0, h.showRequests)
        assertTrue(h.controller.isCandidatesSurfaceExplicitlyDismissedForTests())
        h.rejectHideRequest = false
        h.controller.onExplicitShowRequested()
        h.runNextUiAction()
        assertEquals(1, h.inputAttachments)
        assertEquals(1, h.showRequests)
    }

    @Test
    fun inactiveEditorOrMissingConnectionDoesNotScheduleShow() {
        val h = Harness()
        h.active = false
        h.controller.ensureImeSurfaceVisible()
        h.active = true
        h.connection = null
        h.controller.ensureImeSurfaceVisible()
        assertTrue(h.actions.isEmpty())
    }

    private inner class Harness {
        var active = true
        var connection: InputConnection? = mock(InputConnection::class.java)
        var inputShown = false
        var rendered = RenderedSurface.HIDDEN
        var inputAttachments = 0
        var candidatesAttachments = 0
        var showRequests = 0
        var hideRequests = 0
        var rejectHideRequest = false
        var windowHides = 0
        var refreshes = 0
        var containerSynchronizations = 0
        val requestedInputShown = mutableListOf<Boolean>()
        val candidatesShown = mutableListOf<Boolean>()
        val windowShows = mutableListOf<Boolean>()
        val actions = mutableListOf<Pair<Long, () -> Unit>>()
        private val prefs = context.getSharedPreferences("keyboard_visibility_controller_test", Context.MODE_PRIVATE)
        private val alternate = AlternateCharacterManager(context.assets, prefs, context)
        val controller = KeyboardVisibilityController(
            context = context,
            candidatesBarController = CandidatesBarController(context),
            symLayoutController = SymLayoutController(context, prefs, alternate),
            isInputViewActive = { active },
            hasActiveTextField = { active },
            isNavModeLatched = { false },
            currentInputConnection = { connection },
            isInputViewShown = { inputShown },
            renderedSurface = { rendered },
            setRequestedInputViewShown = { requestedInputShown += it },
            attachInputView = { inputAttachments++ },
            attachCandidatesView = { candidatesAttachments++ },
            setCandidatesSurfaceActive = {},
            setCandidatesViewShown = { candidatesShown += it },
            synchronizeCandidatesContainerVisibility = { containerSynchronizations++ },
            postToUi = { actions.add(0L to it) },
            postToUiDelayed = { delay, action -> actions.add(delay to action) },
            showInputWindow = { windowShows += it },
            hideInputWindow = { windowHides++ },
            requestHideInputView = {
                hideRequests++
                if (rejectHideRequest) throw IllegalStateException("Window detached")
            },
            requestShowInputView = { showRequests++ },
            refreshStatusBar = { refreshes++ }
        )
        fun runNext() = actions.removeAt(0).second.invoke()
        fun hasImmediateActions() = actions.any { it.first == 0L }
        fun runNextUiAction() = actions.removeAt(actions.indexOfFirst { it.first == 0L }).second.invoke()
        fun runHideTimeout() = actions.removeAt(actions.indexOfFirst { it.first == 1000L }).second.invoke()
        fun drain() {
            var count = 0
            while (actions.isNotEmpty()) {
                check(count++ < 20) { "Unbounded show retry" }
                runNext()
            }
        }
    }
}
