package io.github.igormy.vocora.recorder.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLevelTest {

    private fun buffer(vararg samples: Short): ByteArray =
        ByteArray(samples.size * 2).also { bytes ->
            samples.forEachIndexed { i, sample ->
                bytes[i * 2] = (sample.toInt() and 0xFF).toByte()
                bytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            }
        }

    @Test
    fun `silence measures zero`() {
        val silence = buffer(0, 0, 0, 0)

        assertEquals(0, AudioLevel.rms(silence, silence.size))
    }

    @Test
    fun `a constant tone measures its amplitude`() {
        val tone = buffer(1000, -1000, 1000, -1000)

        assertEquals(1000, AudioLevel.rms(tone, tone.size))
    }

    @Test
    fun `negative samples are read as signed`() {
        val negative = buffer(-2000, -2000)

        assertEquals(2000, AudioLevel.rms(negative, negative.size))
    }

    @Test
    fun `only the bytes that were read are measured`() {
        val partly = buffer(3000, 3000, 0, 0)

        assertEquals(3000, AudioLevel.rms(partly, 4))
    }

    @Test
    fun `an empty read measures zero instead of dividing by zero`() {
        assertEquals(0, AudioLevel.rms(buffer(), 0))
    }

    @Test
    fun `a file of zeros is reported as silent, not as a good recording`() {
        val described = AudioLevel.describe("call.m4a", sizeBytes = 60974, sampleRate = 48000, peakLevel = 0)

        assertTrue(described, described.startsWith("SILENT"))
    }

    @Test
    fun `an empty file is a failure`() {
        val described = AudioLevel.describe("call.m4a", sizeBytes = 0, sampleRate = 48000, peakLevel = 0)

        assertTrue(described, described.startsWith("FAILED"))
    }

    @Test
    fun `barely audible is flagged as suspicious`() {
        val described = AudioLevel.describe("call.m4a", 60974, 48000, AudioLevel.NEAR_SILENT - 1)

        assertTrue(described, described.startsWith("SUSPICIOUS"))
    }

    @Test
    fun `real audio is reported as good, with the details`() {
        val described = AudioLevel.describe("call.m4a", 60974, 48000, 4843)

        assertTrue(described, described.startsWith("OK"))
        assertTrue(described, described.contains("call.m4a"))
        assertTrue(described, described.contains("48000 Hz"))
        assertTrue(described, described.contains("60974 bytes"))
    }
}
