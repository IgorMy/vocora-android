package io.github.igormy.vocora.recorder.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlacklistMatchTest {

    private val numbers = listOf("+34654924556")
    private val names = listOf("Jose")

    @Test
    fun `a number on the list is blocked however it is written`() {
        assertTrue(BlacklistMatch.isBlocked(CallIdentity("654924556", null), numbers, names))
    }

    @Test
    fun `a saved contact the dialer only named is blocked too`() {
        assertTrue(BlacklistMatch.isBlocked(CallIdentity(null, "Jose"), numbers, names))
    }

    @Test
    fun `the name is matched regardless of case and spacing`() {
        assertTrue(BlacklistMatch.isBlocked(CallIdentity(null, "  jose "), numbers, names))
    }

    @Test
    fun `somebody else goes through`() {
        assertFalse(BlacklistMatch.isBlocked(CallIdentity("605339797", "Lorena"), numbers, names))
    }

    @Test
    fun `a name is never matched against the numbers`() {
        // "Jose" is nobody's phone number, and treating it as one would block by accident.
        assertFalse(BlacklistMatch.isBlocked(CallIdentity(null, "Jose"), numbers, emptyList()))
    }

    @Test
    fun `a call nothing could identify is recorded`() {
        assertFalse(BlacklistMatch.isBlocked(CallIdentity(null, null), numbers, names))
        assertFalse(BlacklistMatch.isBlocked(CallIdentity("", "  "), numbers, names))
    }
}
