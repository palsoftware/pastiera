package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.util.Log
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
    private val setRequestedInputViewShown: (Boolean) -> Unit,
    private val attachInputView: (View) -> Unit,
    private val setCandidatesSurfaceActive: (Boolean) -> Unit,
    private val setCandidatesViewShown: (Boolean) -> Unit,
    private val synchronizeCandidatesContainerVisibility: () -> Unit,
    private val postToUi: (() -> Unit) -> Unit,
    private val postToUiDelayed: (delayMs: Long, action: () -> Unit) -> Unit,
    private val showInputWindow: (showInput: Boolean) -> Unit,
    private val requestShowInputView: () -> Unit,
    private val refreshStatusBar: () -> Unit
) {

    private var statusBarPresentationMode: SettingsManager.StatusBarPresentationMode =
        SettingsManager.getStatusBarPresentationMode(context)
    private var evaluationGeneration = 0
    private var surfaceTransitionGeneration = 0
    private var pendingSurfaceTransition: PendingSurfaceTransition? = null
    private var surfaceDebugSequence = 0L

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
        val generation = ++evaluationGeneration
        logSurfaceState(
            event = "evaluate_input_view",
            details = "systemDecision=$shouldShowInputView resolvedDecision=$resolvedShowInputView generation=$generation"
        )
        // Apply candidates visibility after InputMethodService has finished evaluating
        // and hiding its input frame. Showing it re-entrantly from this callback can be
        // overwritten by the framework before the candidates view is created.
        postToUi {
            if (generation != evaluationGeneration) return@postToUi
            setCandidatesSurfaceActive(!resolvedShowInputView)
            setCandidatesViewShown(!resolvedShowInputView)
            refreshStatusBar()
            logSurfaceState(
                event = "apply_evaluated_surface",
                details = "target=${if (resolvedShowInputView) RenderedSurface.FULL_INPUT_VIEW else RenderedSurface.CANDIDATES_VIEW} generation=$generation"
            )
            if (!resolvedShowInputView) {
                // Showing candidates can synchronously create and attach their view. Android's
                // enclosing fullscreenArea may still retain its previous INVISIBLE state, so
                // synchronize that container on the following UI turn.
                postToUi {
                    if (generation != evaluationGeneration) return@postToUi
                    synchronizeCandidatesContainerVisibility()
                    refreshStatusBar()
                }
            }
        }
        return resolvedShowInputView
    }

    fun ensureInputViewCreated(reason: String = "unspecified") {
        if (!isInputViewActive()) {
            logSurfaceState("ensure_input_view_skipped", "reason=$reason cause=input_view_inactive")
            return
        }
        if (currentInputConnection() == null) {
            logSurfaceState("ensure_input_view_skipped", "reason=$reason cause=no_input_connection")
            return
        }

        val layout = candidatesBarController.getInputView(symLayoutController.emojiMapTextForLayout())
        refreshStatusBar()

        if (layout.parent == null) {
            attachInputView(layout)
            logSurfaceState("input_view_attached", "reason=$reason")
        }

        if (!isInputViewShown() && !isNavModeLatched()) {
            logSurfaceState("request_show_self", "reason=$reason")
            try {
                requestShowInputView()
            } catch (exception: Exception) {
                Log.w(SURFACE_DEBUG_TAG, "requestShowSelf rejected reason=$reason", exception)
                // Avoid crashing if the system rejects the request
            }
        } else {
            logSurfaceState("ensure_input_view_no_request", "reason=$reason")
        }
    }

    fun onImeWindowVisibilityChanged(shown: Boolean) {
        logSurfaceState(if (shown) "window_shown" else "window_hidden")
    }

    fun onPhysicalKeyDown(repeatCount: Int?, hasEditableField: Boolean) {
        logSurfaceState(
            event = "physical_key_down",
            details = "repeated=${(repeatCount ?: 0) > 0} editable=$hasEditableField"
        )
    }

    fun togglePastierinaMode() {
        statusBarPresentationMode = when (statusBarPresentationMode) {
            SettingsManager.StatusBarPresentationMode.PASTIERINA ->
                SettingsManager.StatusBarPresentationMode.FULL_STATUS_BAR
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
        evaluationGeneration += 1
        val generation = ++surfaceTransitionGeneration
        pendingSurfaceTransition = null
        refreshStatusBar()
        if ((requireActiveTextField && !hasActiveTextField()) || currentInputConnection() == null) {
            return
        }

        setCandidatesSurfaceActive(!ensureInputViewShown)
        setCandidatesViewShown(!ensureInputViewShown)
        if (!ensureInputViewShown) {
            postToUi {
                if (generation != surfaceTransitionGeneration) return@postToUi
                synchronizeCandidatesContainerVisibility()
                refreshStatusBar()
            }
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
        reconcilePendingSurfaceTransition(generation)
    }

    fun cancelPendingSurfaceTransition() {
        surfaceTransitionGeneration += 1
        pendingSurfaceTransition = null
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
        logSurfaceState(
            event = "show_input_window",
            details = "target=${transition.target} generation=$generation attemptsRemaining=${transition.attemptsRemaining}"
        )
        try {
            showInputWindow(transition.target == RenderedSurface.FULL_INPUT_VIEW)
        } catch (_: Exception) {
            // A configuration rebind can temporarily reject this request. The bounded
            // reconciliation below retries only this explicit surface transition.
        }
        scheduleSurfaceReconciliation(transition)
    }

    private fun scheduleSurfaceReconciliation(transition: PendingSurfaceTransition) {
        if (transition.retryScheduled) return
        transition.retryScheduled = true
        postToUiDelayed(SURFACE_TRANSITION_RETRY_DELAY_MS) {
            reconcilePendingSurfaceTransition(transition.generation)
        }
    }

    fun isCandidatesOnlySurface(): Boolean = renderedSurface() == RenderedSurface.CANDIDATES_VIEW

    private fun abandonSurfaceTransition() {
        val actualSurface = renderedSurface()
        setRequestedInputViewShown(actualSurface == RenderedSurface.FULL_INPUT_VIEW)
        setCandidatesSurfaceActive(actualSurface == RenderedSurface.CANDIDATES_VIEW)
        setCandidatesViewShown(actualSurface == RenderedSurface.CANDIDATES_VIEW)
        pendingSurfaceTransition = null
        refreshStatusBar()
    }

    private fun detachFromParent(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    private fun logSurfaceState(event: String, details: String = "") {
        if (!SurfaceDiagnosticsStore.enabled) return
        val sequence = ++surfaceDebugSequence
        val effectiveMode = SettingsManager.resolveEffectiveSoftwareKeyboardMode(context)
        val actualSurface = runCatching { renderedSurface() }.getOrDefault(RenderedSurface.HIDDEN)
        val suffix = details.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        val inputShown = isInputViewShown()
        val inputActive = isInputViewActive()
        val hasConnection = currentInputConnection() != null
        val navLatched = isNavModeLatched()
        SurfaceDiagnosticsStore.record(
            context,
            SurfaceDiagnosticEvent(
                timestampMs = System.currentTimeMillis(),
                sequence = sequence,
                event = event,
                mode = effectiveMode.name,
                renderedSurface = actualSurface.name,
                inputViewShown = inputShown,
                inputViewActive = inputActive,
                hasInputConnection = hasConnection,
                navModeLatched = navLatched,
                details = details
            )
        )
        Log.d(
            SURFACE_DEBUG_TAG,
            "seq=$sequence event=$event mode=$effectiveMode rendered=$actualSurface " +
                "inputViewShown=$inputShown inputViewActive=$inputActive " +
                "hasInputConnection=$hasConnection navLatched=$navLatched$suffix"
        )
    }

    private companion object {
        const val SURFACE_DEBUG_TAG = "PastieraImeSurface"
        const val MAX_SURFACE_TRANSITION_ATTEMPTS = 6
        const val SURFACE_TRANSITION_RETRY_DELAY_MS = 250L
    }
}
