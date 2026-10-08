package io.github.igormy.vocora.recorder.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimeTest {

    @Test
    fun `the start of a recording`() {
        assertEquals("0:00", PlaybackTime.format(0))
    }

    @Test
    fun `seconds are padded so the bar does not jump about`() {
        assertEquals("0:07", PlaybackTime.format(7_400))
    }

    @Test
    fun `minutes and seconds`() {
        assertEquals("2:35", PlaybackTime.format(155_000))
    }

    @Test
    fun `a call past the hour grows an hours field`() {
        assertEquals("1:05:03", PlaybackTime.format(3_903_000))
    }

    @Test
    fun `a negative position reads as the start instead of breaking`() {
        assertEquals("0:00", PlaybackTime.format(-500))
    }

    @Test
    fun `whole speeds carry no decimals`() {
        assertEquals("1x", PlaybackTime.formatSpeed(1f))
        assertEquals("2x", PlaybackTime.formatSpeed(2f))
    }

    @Test
    fun `fractional speeds keep theirs`() {
        assertEquals("1.5x", PlaybackTime.formatSpeed(1.5f))
        assertEquals("0.75x", PlaybackTime.formatSpeed(0.75f))
    }
}
