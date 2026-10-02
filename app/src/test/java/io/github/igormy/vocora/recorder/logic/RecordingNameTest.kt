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
        assertEquals("2026-10-02T210814.m4a", RecordingName.forStart(startedAt))
    }

    @Test
    fun `the date carries no colons, they break the name on FAT and Windows`() {
        assertFalse(RecordingName.forStart(startedAt).contains(":"))
    }

    @Test
    fun `the call details are appended once the call is over`() {
        val named = RecordingName.withCallDetails("2026-10-02T210814.m4a", "605339797", "incoming")

        assertEquals("2026-10-02T210814-605339797-incoming.m4a", named)
    }

    @Test
    fun `renaming twice does not stack extensions`() {
        val once = RecordingName.withCallDetails("2026-10-02T210814.m4a", "600111222", "outgoing")

        assertEquals(1, once.split(".m4a").size - 1)
    }
}
