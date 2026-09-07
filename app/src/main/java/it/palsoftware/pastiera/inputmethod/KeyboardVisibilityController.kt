package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.SymLayoutController

/**
 * Handles creation/show/hide of the IME status UI for both the full input view
 * and the candidate-only view exposed when the system hides the soft keyboard.
 */
class KeyboardVisibilityController(
    private val context: Context,
    private val candidatesBarController: CandidatesBarController,
    private val symLayoutController: SymLayoutController,
    private val isInputViewActive: () -> Boolean,
    private val hasActiveTextField: () -> Boolean,
    private val isNavModeLatched: () -> Boolean,
    private val currentInputConnection: () -> InputConnection?,
    private val isInputViewShown: () -> Boolean,
    private val renderedSurface: () -> RenderedSurface,
    private val requiresCandidatesSurfaceRecovery: () -> Boolean,
    private val setRequestedInputViewShown: (Boolean) -> Unit,
    private val attachInputView: (View) -> Unit,
    private val setCandidatesSurfaceActive: (Boolean) -> Unit,
    private val setCandidatesViewShown: (Boolean) -> Unit,
    private val synchronizeCandidatesContainerVisibility: () -> Unit,
    private val postToUi: (() -> Unit) -> Unit,
    private val postToUiDelayed: (delayMs: Long, action: () -> Unit) -> Unit,
    private val showInputWindow: (showInput: Boolean) -> Unit,
    private val requestHideInputView: () -> Unit,
    private val requestShowInputView: () -> Unit,
    private val refreshStatusBar: () -> Unit
) {

    private var statusBarPresentationMode: SettingsManager.StatusBarPresentationMode =
        SettingsManager.getStatusBarPresentationMode(context)
    private var surfaceTransitionGeneration = 0
    private var pendingSurfaceTransition: PendingSurfaceTransition? = null
    private var candidatesSurfaceRequested = false
    private var candidatesDismissalGeneration = 0
    private var candidatesSurfaceExplicitlyDismissed = false
    private val candidatesSurfaceRecoveryWorkaround = CandidatesSurfaceRecoveryWorkaround(
        isRequired = requiresCandidatesSurfaceRecovery,
        canRecover = {
            isInputViewActive() &&
                currentInputConnection() != null &&
                !isNavModeLatched() &&
                SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) !=
                SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        },
        requestRecovery = requestShowInputView,
        postDelayed = postToUiDelayed
    )

    enum class RenderedSurface {
        HIDDEN,
        FULL_INPUT_VIEW,
        CANDIDATES_VIEW
    }

    private data class PendingSurfaceTransition(
        val generation: Int,
        val target: RenderedSurface,
        val requireActiveTextField: Boolean,
        var attemptsRemaining: Int = MAX_SURFACE_TRANSITION_ATTEMPTS,
        var retryScheduled: Boolean = false
    )

    fun onCreateInputView(): View {
        val layout = candidatesBarController.getInputView(symLayoutController.emojiMapTextForLayout())
        detachFromParent(layout)
        refreshStatusBar()
        return layout
    }

    fun onCreateCandidatesView(): View {
        val layout = candidatesBarController.getCandidatesView(symLayoutController.emojiMapTextForLayout())
        detachFromParent(layout)
        refreshStatusBar()
        return layout
    }

    fun onEvaluateInputViewShown(shouldShowInputView: Boolean): Boolean {
        SoftwareKeyboardAutoDetector.updateSystemInputViewDecision(shouldShowInputView)
        val resolvedShowInputView =
            SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) ==
                SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        refreshStatusBar()
        return resolvedShowInputView
    }

    fun ensureImeSurfaceVisible() {
        if (!isInputViewActive()) {
            return
        }
        if (currentInputConnection() == null) {
            return
        }
        if (isNavModeLatched()) {
            return
        }

        clearExplicitCandidatesDismissal()

        when (SettingsManager.resolveEffectiveSoftwareKeyboardMode(context)) {
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL -> ensureFullInputViewVisible()
            SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
            SettingsManager.SoftwareKeyboardMode.AUTO ->
                ensureCandidatesSurfaceVisible()
        }
    }

    fun shouldShowSurfaceOnInputStart(autoShowKeyboardEnabled: Boolean): Boolean =
        SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) !=
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL || autoShowKeyboardEnabled

    private fun ensureFullInputViewVisible() {
        candidatesSurfaceRequested = false
        setCandidatesSurfaceActive(false)
        setCandidatesViewShown(false)

        val layout = candidatesBarController.getInputView(symLayoutController.emojiMapTextForLayout())
        refreshStatusBar()

        if (layout.parent == null) {
            attachInputView(layout)
        }

        if (!isInputViewShown()) {
            try {
                requestShowInputView()
            } catch (_: Exception) {
                // Avoid crashing if the system rejects the request
            }
        }
    }

    private fun ensureCandidatesSurfaceVisible() {
        setRequestedInputViewShown(false)
        setCandidatesSurfaceActive(true)

        if (!candidatesSurfaceRequested) {
            candidatesSurfaceRequested = true
            if (!requestCandidatesView()) return
            refreshStatusBar()
        }
        candidatesSurfaceRecoveryWorkaround.scheduleIfNeeded()
    }

    fun onImeWindowVisibilityChanged(shown: Boolean) {
        if (!shown && candidatesSurfaceRequested) {
            candidatesSurfaceRequested = false
            setCandidatesViewShown(false)
        }
        if (!shown) {
            pendingSurfaceTransition
                ?.takeIf { it.target == RenderedSurface.CANDIDATES_VIEW }
                ?.let { transition ->
                    postToUi {
                        startCandidatesTransition(transition.generation)
                    }
                }
        }
    }

    fun onCandidatesViewStarted() {
        clearExplicitCandidatesDismissal()
        candidatesSurfaceRequested = true
        // This callback describes the framework's actual surface transition, not merely a
        // request. A preceding full-input transition may have collapsed the candidates root;
        // reactivate it before the service refreshes its contents.
        setCandidatesSurfaceActive(true)
    }

    fun onCandidatesViewFinished(finishingInput: Boolean) {
        val externallyFinishedRequestedSurface =
            candidatesSurfaceRequested && pendingSurfaceTransition == null
        candidatesSurfaceRequested = false
        // A delayed app-compatibility recovery belongs to the surface that just finished. Let a
        // subsequent explicit show or hardware-input request schedule a fresh generation instead
        // of allowing the stale action to rebound after Back or focus loss.
        candidatesSurfaceRecoveryWorkaround.cancel()
        // Keep the local child state aligned with the framework callback. The next
        // onCandidatesViewStarted callback reactivates and refreshes the same root.
        setCandidatesSurfaceActive(false)
        if (finishingInput || !externallyFinishedRequestedSurface) return

        // setCandidatesViewShown(false) only removes the candidates child. When the system/user
        // dismisses an otherwise still-requested candidates-only surface, Android can keep the
        // server-side IME request (and an OEM caption/touch region) alive. Complete that external
        // dismissal through the public IME hide request. Delay by one UI turn so a transient
        // candidates restart can cancel it before a newly started surface is hidden.
        candidatesSurfaceExplicitlyDismissed = true
        val generation = ++candidatesDismissalGeneration
        postToUi {
            if (
                generation != candidatesDismissalGeneration ||
                !candidatesSurfaceExplicitlyDismissed ||
                candidatesSurfaceRequested ||
                pendingSurfaceTransition != null ||
                renderedSurface() == RenderedSurface.FULL_INPUT_VIEW
            ) {
                return@postToUi
            }
            try {
                requestHideInputView()
            } catch (_: Exception) {
                // The framework may have completed the hide already.
            }
        }
    }

    fun onInputStarted(restarting: Boolean) {
        if (!restarting) {
            clearExplicitCandidatesDismissal()
        }
    }

    fun onExplicitShowRequested() {
        // The framework reports an editor's showSoftInput request even when a retap on the same
        // still-focused field does not restart input or call onViewClicked. This explicit request
        // is one same-session action that clears a deliberate dismissal. A non-Back hardware key
        // is the other and follows onHardwareInputRequested().
        if (!isInputViewActive() || currentInputConnection() == null) return
        clearExplicitCandidatesDismissal()
        ensureImeSurfaceVisible()
    }

    fun onHardwareInputRequested() {
        if (!isInputViewActive() || currentInputConnection() == null || isNavModeLatched()) return

        // Telegram deliberately reports the candidate surface as needing recovery even while its
        // enclosing IME window is still requested and visible. Preserve its delayed compatibility
        // request, but reserve an immediate whole-window show for a window that actually finished.
        val enclosingImeWindowNeedsShow =
            candidatesSurfaceExplicitlyDismissed || !candidatesSurfaceRequested
        ensureImeSurfaceVisible()
        if (
            enclosingImeWindowNeedsShow &&
            SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) !=
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        ) {
            try {
                // setCandidatesViewShown(true) attaches the candidates child, but after a full
                // framework hide Android may keep mInputShown=false. A hardware key is explicit
                // user input, so pair the child request with the same framework show request a
                // retap would issue.
                requestShowInputView()
            } catch (_: Exception) {
                // The candidates request above remains the safe fallback if Android rejects it.
            }
        }
    }

    fun togglePastierinaMode() {
        statusBarPresentationMode = when (statusBarPresentationMode) {
            SettingsManager.StatusBarPresentationMode.PASTIERINA ->
                SettingsManager.StatusBarPresentationMode.FULL_STATUS_BAR
            SettingsManager.StatusBarPresentationMode.UNIFIED ->
                SettingsManager.StatusBarPresentationMode.PASTIERINA
            SettingsManager.StatusBarPresentationMode.FULL_STATUS_BAR ->
                SettingsManager.StatusBarPresentationMode.PASTIERINA
        }
        SettingsManager.setStatusBarPresentationMode(context, statusBarPresentationMode)
        applyStatusBarPresentationMode()
    }

    private fun applyStatusBarPresentationMode() {
        val pastierinaModeActive =
            statusBarPresentationMode == SettingsManager.StatusBarPresentationMode.PASTIERINA
        candidatesBarController.setPastierinaModeActive(pastierinaModeActive)
        SettingsManager.setPastierinaModeActive(context, pastierinaModeActive)
        refreshStatusBar()
    }

    fun syncStatusBarPresentationModeFromSettings() {
        statusBarPresentationMode = SettingsManager.getStatusBarPresentationMode(context)
        applyStatusBarPresentationMode()
    }

    fun onKeyboardSurfaceChanged(
        ensureInputViewShown: Boolean,
        requireActiveTextField: Boolean = false
    ) {
        val generation = ++surfaceTransitionGeneration
        clearExplicitCandidatesDismissal()
        pendingSurfaceTransition = null
        candidatesSurfaceRecoveryWorkaround.cancel()
        refreshStatusBar()
        if ((requireActiveTextField && !hasActiveTextField()) || currentInputConnection() == null) {
            return
        }

        pendingSurfaceTransition = PendingSurfaceTransition(
            generation = generation,
            target = if (ensureInputViewShown) {
                RenderedSurface.FULL_INPUT_VIEW
            } else {
                RenderedSurface.CANDIDATES_VIEW
            },
            requireActiveTextField = requireActiveTextField
        )
        if (ensureInputViewShown) {
            candidatesSurfaceRequested = false
            setCandidatesSurfaceActive(false)
            setCandidatesViewShown(false)
            reconcilePendingSurfaceTransition(generation)
        } else {
            setCandidatesSurfaceActive(true)
            candidatesSurfaceRequested = false
            setCandidatesViewShown(false)
            if (isInputViewShown()) {
                try {
                    requestHideInputView()
                } catch (_: Exception) {
                    startCandidatesTransition(generation)
                }
            } else {
                startCandidatesTransition(generation)
            }
        }
    }

    fun cancelPendingSurfaceTransition() {
        surfaceTransitionGeneration += 1
        pendingSurfaceTransition = null
        candidatesSurfaceRecoveryWorkaround.cancel()
    }

    private fun reconcilePendingSurfaceTransition(generation: Int) {
        val transition = pendingSurfaceTransition
            ?.takeIf { it.generation == generation }
            ?: return
        transition.retryScheduled = false

        if (
            currentInputConnection() == null ||
            (transition.requireActiveTextField && !hasActiveTextField())
        ) {
            abandonSurfaceTransition()
            return
        }
        if (renderedSurface() == transition.target) {
            setRequestedInputViewShown(transition.target == RenderedSurface.FULL_INPUT_VIEW)
            pendingSurfaceTransition = null
            return
        }
        if (transition.attemptsRemaining <= 0) {
            abandonSurfaceTransition()
            return
        }

        transition.attemptsRemaining -= 1
        try {
            showInputWindow(transition.target == RenderedSurface.FULL_INPUT_VIEW)
        } catch (_: Exception) {
            // A configuration rebind can temporarily reject this request. The bounded
            // reconciliation below retries only this explicit surface transition.
        }
        scheduleSurfaceReconciliation(transition)
    }

    private fun startCandidatesTransition(generation: Int) {
        val transition = pendingSurfaceTransition
            ?.takeIf {
                it.generation == generation && it.target == RenderedSurface.CANDIDATES_VIEW
            }
            ?: return
        if (
            currentInputConnection() == null ||
            (transition.requireActiveTextField && !hasActiveTextField())
        ) {
            abandonSurfaceTransition()
            return
        }

        setRequestedInputViewShown(false)
        setCandidatesSurfaceActive(true)
        candidatesSurfaceRequested = true
        if (!requestCandidatesView()) {
            scheduleSurfaceReconciliation(transition)
            return
        }
        refreshStatusBar()
        postToUi {
            if (generation != surfaceTransitionGeneration) return@postToUi
            synchronizeCandidatesContainerVisibility()
            refreshStatusBar()
        }
        // setCandidatesViewShown(true) is the primary candidates-only window request. Verify it
        // after the framework has had a chance to present the window before using showWindow(false)
        // as a bounded recovery path.
        scheduleSurfaceReconciliation(transition)
    }

    private fun requestCandidatesView(): Boolean =
        try {
            setCandidatesViewShown(true)
            true
        } catch (_: Exception) {
            candidatesSurfaceRequested = false
            false
        }

    private fun scheduleSurfaceReconciliation(transition: PendingSurfaceTransition) {
        if (transition.retryScheduled) return
        transition.retryScheduled = true
        postToUiDelayed(SURFACE_TRANSITION_RETRY_DELAY_MS) {
            reconcilePendingSurfaceTransition(transition.generation)
        }
    }

    fun isCandidatesOnlySurface(): Boolean = renderedSurface() == RenderedSurface.CANDIDATES_VIEW

    fun isExpectedSurfaceRequestedOrShown(): Boolean =
        if (
            SettingsManager.resolveEffectiveSoftwareKeyboardMode(context) ==
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        ) {
            isInputViewShown()
        } else {
            candidatesSurfaceRequested && !requiresCandidatesSurfaceRecovery()
        }

    /**
     * A non-Back hardware key is an explicit request to resume typing. Keep the dismissal latch
     * only long enough to prevent an autonomous rebound after the framework hide; the first
     * subsequent key must restore the surface just as it did before candidates-only lifecycle
     * handling was introduced. The caller then uses [onHardwareInputRequested] to reconcile both
     * the candidates child and Android's enclosing IME-window request.
     */
    fun shouldRecoverSurfaceOnHardwareKey(): Boolean =
        !isExpectedSurfaceRequestedOrShown()

    internal fun isCandidatesSurfaceExplicitlyDismissedForTests(): Boolean =
        candidatesSurfaceExplicitlyDismissed

    private fun clearExplicitCandidatesDismissal() {
        candidatesDismissalGeneration += 1
        candidatesSurfaceExplicitlyDismissed = false
    }

    private fun abandonSurfaceTransition() {
        val actualSurface = renderedSurface()
        setRequestedInputViewShown(actualSurface == RenderedSurface.FULL_INPUT_VIEW)
        setCandidatesSurfaceActive(actualSurface == RenderedSurface.CANDIDATES_VIEW)
        setCandidatesViewShown(actualSurface == RenderedSurface.CANDIDATES_VIEW)
        candidatesSurfaceRequested = actualSurface == RenderedSurface.CANDIDATES_VIEW
        pendingSurfaceTransition = null
        refreshStatusBar()
    }

    private fun detachFromParent(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    private companion object {
        const val MAX_SURFACE_TRANSITION_ATTEMPTS = 6
        const val SURFACE_TRANSITION_RETRY_DELAY_MS = 250L
    }
}
