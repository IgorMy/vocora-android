package io.github.igormy.vocora.recorder.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {

    @Test
    fun `the same number written differently is the same person`() {
        assertTrue(PhoneNumbers.matches("+34654537543", "654537543"))
        assertTrue(PhoneNumbers.matches("0034654537543", "654 53 75 43"))
        assertTrue(PhoneNumbers.matches("654-53-75-43", "+34 654 537 543"))
    }

    @Test
    fun `different people are not`() {
        assertFalse(PhoneNumbers.matches("654537543", "605339797"))
    }

    @Test
    fun `a short service number has to agree in full`() {
        // Matching by the tail would make 200 the same as 1200.
        assertFalse(PhoneNumbers.matches("1200", "200"))
        assertTrue(PhoneNumbers.matches("1200", "1200"))
    }

    @Test
    fun `a withheld number matches nobody`() {
        assertFalse(PhoneNumbers.matches("", "654537543"))
        assertFalse(PhoneNumbers.matches("unknown", "654537543"))
    }

    @Test
    fun `being on the list survives the format it was added in`() {
        val list = listOf("+34654537543", "1200")

        assertTrue(PhoneNumbers.isListed("654537543", list))
        assertTrue(PhoneNumbers.isListed("1200", list))
        assertFalse(PhoneNumbers.isListed("605339797", list))
    }

    @Test
    fun `an empty list lets everything through`() {
        assertFalse(PhoneNumbers.isListed("654537543", emptyList()))
    }
}
