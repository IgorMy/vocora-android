package io.github.igormy.vocora.recorder.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingLabelTest {

    @Test
    fun `the number is what identifies a recording`() {
        assertEquals("605339797", RecordingLabel.title("2026-10-02T210814-605339797-incoming.m4a"))
    }

    @Test
    fun `an international number survives the separator in the name`() {
        assertEquals("+34605339797", RecordingLabel.title("2026-10-02T210814-+34605339797-outgoing.m4a"))
    }

    @Test
    fun `a recording the call log never explained has no number`() {
        assertEquals(RecordingLabel.UNKNOWN_NUMBER, RecordingLabel.title("2026-10-02T210814.m4a"))
    }

    @Test
    fun `the date is shown the way a person reads it`() {
        assertEquals(
            "02/10/2026 21:08 · incoming",
            RecordingLabel.subtitle("2026-10-02T210814-605339797-incoming.m4a"),
        )
    }

    @Test
    fun `without call details only the date is shown`() {
        assertEquals("02/10/2026 21:08", RecordingLabel.subtitle("2026-10-02T210814.m4a"))
    }

    @Test
    fun `a name from somewhere else is shown as it is instead of breaking`() {
        assertEquals("note.m4a", RecordingLabel.subtitle("note.m4a"))
    }
}
