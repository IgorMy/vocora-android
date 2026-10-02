package io.github.igormy.vocora.recorder.logic

/**
 * Mixes the two sides of a call into one stream, for the file that gets played back.
 *
 * Samples are summed rather than averaged: the two sides rarely talk at once, so averaging would
 * make every recording sound half as loud for no benefit. Sums are clamped instead of wrapping,
 * which would turn a loud moment into a crack.
 */
object PcmMixer {

    /** Mixes [length] bytes of 16 bit little endian mono PCM from [a] and [b] into [into]. */
    fun mix(a: ByteArray, b: ByteArray, length: Int, into: ByteArray) {
        var i = 0
        while (i + 1 < length) {
            val sum = sampleAt(a, i) + sampleAt(b, i)
            val clamped = sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            into[i] = (clamped and 0xFF).toByte()
            into[i + 1] = ((clamped shr 8) and 0xFF).toByte()
            i += 2
        }
    }

    private fun sampleAt(bytes: ByteArray, index: Int): Int =
        (((bytes[index + 1].toInt() shl 8) or (bytes[index].toInt() and 0xFF)).toShort()).toInt()
}
