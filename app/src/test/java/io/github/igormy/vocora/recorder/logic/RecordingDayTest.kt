package io.github.igormy.vocora.recorder.logic

import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RecordingDayTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val today = at(2026, Calendar.OCTOBER, 8, 13, 30)

    @Test
    fun `two calls on the same day land in the same group`() {
        val morning = at(2026, Calendar.OCTOBER, 8, 9, 5)
        val night = at(2026, Calendar.OCTOBER, 8, 23, 50)

        assertEquals(RecordingDay.startOfDay(morning), RecordingDay.startOfDay(night))
    }

    @Test
    fun `a call just before midnight belongs to the day before`() {
        val lateNight = at(2026, Calendar.OCTOBER, 7, 23, 59)
        val earlyMorning = at(2026, Calendar.OCTOBER, 8, 0, 1)

        assertNotEquals(RecordingDay.startOfDay(lateNight), RecordingDay.startOfDay(earlyMorning))
    }

    @Test
    fun `today is named rather than dated`() {
        val header = RecordingDay.header(at(2026, Calendar.OCTOBER, 8, 9, 0), today)

        assertEquals("Today", header)
    }

    @Test
    fun `so is yesterday`() {
        val header = RecordingDay.header(at(2026, Calendar.OCTOBER, 7, 9, 0), today)

        assertEquals("Yesterday", header)
    }

    @Test
    fun `anything older gets its date`() {
        val header = RecordingDay.header(at(2026, Calendar.OCTOBER, 3, 9, 0), today, Locale.UK)

        assertEquals("3 October 2026", header)
    }
}
