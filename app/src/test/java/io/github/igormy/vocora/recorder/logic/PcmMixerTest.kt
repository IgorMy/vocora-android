package io.github.igormy.vocora.recorder.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class PcmMixerTest {

    private fun pcm(vararg samples: Short): ByteArray =
        ByteArray(samples.size * 2).also { bytes ->
            samples.forEachIndexed { i, sample ->
                bytes[i * 2] = (sample.toInt() and 0xFF).toByte()
                bytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            }
        }

    private fun samplesOf(bytes: ByteArray): List<Short> =
        (bytes.indices step 2).map { i ->
            ((bytes[i + 1].toInt() shl 8) or (bytes[i].toInt() and 0xFF)).toShort()
        }

    @Test
    fun `one side talking comes through untouched`() {
        val speech = pcm(1000, -1000)
        val silence = pcm(0, 0)
        val mixed = ByteArray(4)

        PcmMixer.mix(speech, silence, 4, mixed)

        assertEquals(listOf<Short>(1000, -1000), samplesOf(mixed))
    }

    @Test
    fun `both talking at once are added, not averaged`() {
        val mixed = ByteArray(2)

        PcmMixer.mix(pcm(1000), pcm(500), 2, mixed)

        assertEquals(listOf<Short>(1500), samplesOf(mixed))
    }

    @Test
    fun `a loud moment clamps instead of wrapping into a crack`() {
        val mixed = ByteArray(2)

        PcmMixer.mix(pcm(30000), pcm(30000), 2, mixed)

        assertEquals(listOf(Short.MAX_VALUE), samplesOf(mixed))
    }

    @Test
    fun `it clamps at the bottom too`() {
        val mixed = ByteArray(2)

        PcmMixer.mix(pcm(-30000), pcm(-30000), 2, mixed)

        assertEquals(listOf(Short.MIN_VALUE), samplesOf(mixed))
    }

    @Test
    fun `only the bytes that were read are mixed`() {
        val mixed = ByteArray(4)

        PcmMixer.mix(pcm(100, 9999), pcm(100, 9999), 2, mixed)

        assertEquals(listOf<Short>(200, 0), samplesOf(mixed))
    }
}
