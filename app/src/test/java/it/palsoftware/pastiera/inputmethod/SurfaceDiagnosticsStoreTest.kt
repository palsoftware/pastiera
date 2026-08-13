package it.palsoftware.pastiera.inputmethod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SurfaceDiagnosticsStoreTest {

    @Test
    fun ringBufferRetainsOnlyNewestEvents() {
        val buffer = SurfaceDiagnosticRingBuffer(maximumSize = 2)

        buffer.add(event(sequence = 1))
        buffer.add(event(sequence = 2))
        buffer.add(event(sequence = 3))

        assertEquals(listOf(2L, 3L), buffer.snapshot().map { it.sequence })
    }

    @Test
    fun restoringEventsAlsoHonorsMaximumSize() {
        val buffer = SurfaceDiagnosticRingBuffer(maximumSize = 2)

        buffer.replaceWith(listOf(event(sequence = 1), event(sequence = 2), event(sequence = 3)))

        assertEquals(listOf(2L, 3L), buffer.snapshot().map { it.sequence })
    }

    @Test
    fun codecRoundTripSanitizesLineBreakingCharacters() {
        val original = event(sequence = 7).copy(details = "reason=key\tdown\nnext")

        val decoded = SurfaceDiagnosticCodec.decode(SurfaceDiagnosticCodec.encode(original))

        assertEquals(original.copy(details = "reason=key down next"), decoded)
    }

    @Test
    fun malformedRowsAreIgnored() {
        assertNull(SurfaceDiagnosticCodec.decode("not-a-diagnostic-row"))
    }

    private fun event(sequence: Long): SurfaceDiagnosticEvent = SurfaceDiagnosticEvent(
        timestampMs = 1234L + sequence,
        sequence = sequence,
        event = "physical_key_down",
        mode = "FORCE_HARDWARE",
        renderedSurface = "CANDIDATES_VIEW",
        inputViewShown = false,
        inputViewActive = true,
        hasInputConnection = true,
        navModeLatched = false,
        details = "repeated=false editable=true"
    )
}
