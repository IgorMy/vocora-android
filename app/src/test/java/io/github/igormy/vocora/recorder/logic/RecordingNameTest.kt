package io.github.igormy.vocora.recorder.logic

import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RecordingNameTest {

    private val startedAt = Calendar.getInstance(Locale.US).apply {
        set(2026, Calendar.OCTOBER, 2, 21, 8, 14)
    }.time

    @Test
    fun `starts as the date alone`() {
        assertEquals("2026-10-02T210814", RecordingName.forStart(startedAt))
    }

    @Test
    fun `the date carries no colons, they break the name on FAT and Windows`() {
        assertFalse(RecordingName.forStart(startedAt).contains(":"))
    }

    @Test
    fun `the call details are appended once the call is over`() {
        val named = RecordingName.withCallDetails("2026-10-02T210814", "605339797", "incoming")

        assertEquals("2026-10-02T210814-605339797-incoming", named)
    }

    @Test
    fun `the folder name carries no extension, it holds several files`() {
        val once = RecordingName.withCallDetails("2026-10-02T210814", "600111222", "outgoing")

        assertFalse(once.contains(".m4a"))
    }
}
