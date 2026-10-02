package io.github.igormy.vocora.recorder.logic

import kotlin.math.sqrt

/**
 * Measures how loud a recording is.
 *
 * The failure mode worth catching is not an error but silence: the call audio source can open and
 * hand back nothing but zeros, which leaves a file that looks fine and contains nothing.
 */
object AudioLevel {

    /** Below this a recording is treated as suspect rather than good. */
    const val NEAR_SILENT = 50

    /** Root mean square of 16 bit little endian mono samples. */
    fun rms(buffer: ByteArray, bytes: Int): Int {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < bytes) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            sum += sample.toDouble() * sample.toDouble()
            count++
            i += 2
        }
        return if (count == 0) 0 else sqrt(sum / count).toInt()
    }

    /** One line saying whether a finished recording is worth keeping. */
    fun describe(fileName: String, sizeBytes: Long, sampleRate: Int, peakLevel: Int): String {
        val verdict = when {
            sizeBytes == 0L -> "FAILED: the file is empty"
            peakLevel == 0 -> "SILENT: the source opened but every sample is zero"
            peakLevel < NEAR_SILENT -> "SUSPICIOUS: audio is almost silent (peak level $peakLevel)"
            else -> "OK (peak level $peakLevel)"
        }
        return "$verdict | $fileName | $sampleRate Hz | $sizeBytes bytes"
    }
}
