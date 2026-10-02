package io.github.igormy.vocora.recorder.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallLogRowTest {

    @Test
    fun `reads an outgoing call`() {
        val entry = CallLogRow.parse("Row: 0 number=617352491, type=2, date=1790965180029")

        assertEquals(CallLogEntry("617352491", "outgoing", 1790965180029), entry)
    }

    @Test
    fun `reads an incoming call`() {
        val entry = CallLogRow.parse("Row: 0 number=+34605339797, type=1, date=1790965153836")

        assertEquals(CallLogEntry("+34605339797", "incoming", 1790965153836), entry)
    }

    @Test
    fun `any other type is just a call`() {
        val entry = CallLogRow.parse("Row: 0 number=600111222, type=3, date=1")

        assertEquals("call", entry?.direction)
    }

    @Test
    fun `a withheld number is reported as unknown`() {
        val entry = CallLogRow.parse("Row: 0 number=, type=1, date=1790965153836")

        assertEquals(CallLogRow.UNKNOWN_NUMBER, entry?.number)
    }

    @Test
    fun `a row without a date is unusable`() {
        assertNull(CallLogRow.parse("Row: 0 number=600111222, type=1"))
    }

    @Test
    fun `no rows at all`() {
        assertNull(CallLogRow.parse("No result found."))
    }
}
