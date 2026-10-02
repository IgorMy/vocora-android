package io.github.igormy.vocora.recorder.shell

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

private const val BIT_RATE = 64_000
private const val BYTES_PER_SAMPLE = 2

/**
 * Waiting on the codec costs capture.
 *
 * One thread feeds three encoders while two sources keep filling their buffers, so every millisecond
 * spent blocked here is audio about to be dropped. Draining never waits; only asking for an input
 * buffer does, and barely.
 */
private const val INPUT_TIMEOUT_US = 2_000L
private const val DRAIN_TIMEOUT_US = 0L

/**
 * Writes 16 bit mono PCM into an .m4a file as AAC.
 *
 * Audio is pushed in rather than pulled from a source, so one capture can feed several of these:
 * each side of a call into its own file, and the mix of both into another.
 */
class AacEncoder(private val output: File, private val sampleRate: Int) {

    private val encoder: MediaCodec = createEncoder()
    private val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val bufferInfo = MediaCodec.BufferInfo()

    private var trackIndex = -1
    private var muxerStarted = false
    private var totalSamples = 0L
    private var finished = false

    val file: File get() = output

    /**
     * The encoder decides how much it takes at a time, and it is less than a chunk of capture, so
     * the audio is handed over in as many pieces as its input buffers need.
     */
    fun write(pcm: ByteArray, size: Int) {
        if (finished || size <= 0) return
        var offset = 0
        while (offset < size) {
            val inputIndex = encoder.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (inputIndex < 0) {
                drain(untilEndOfStream = false)
                continue
            }
            val input = encoder.getInputBuffer(inputIndex) ?: return
            input.clear()
            val piece = minOf(input.capacity(), size - offset)
            input.put(pcm, offset, piece)
            encoder.queueInputBuffer(inputIndex, 0, piece, presentationTimeUs(), 0)
            totalSamples += piece / BYTES_PER_SAMPLE
            offset += piece
            drain(untilEndOfStream = false)
        }
    }

    /** Queues end of stream and writes what is left, leaving a playable file. */
    fun finish() {
        if (finished) return
        finished = true
        val inputIndex = encoder.dequeueInputBuffer(INPUT_TIMEOUT_US)
        if (inputIndex >= 0) {
            encoder.queueInputBuffer(
                inputIndex,
                0,
                0,
                presentationTimeUs(),
                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
            )
        }
        drain(untilEndOfStream = true)
    }

    fun release() {
        encoder.runCatching { stop() }
        encoder.release()
        muxer.runCatching { stop() }
        muxer.release()
    }

    private fun presentationTimeUs(): Long = totalSamples * 1_000_000L / sampleRate

    private fun drain(untilEndOfStream: Boolean) {
        val timeout = if (untilEndOfStream) INPUT_TIMEOUT_US else DRAIN_TIMEOUT_US
        while (true) {
            when (val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, timeout)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }

                MediaCodec.INFO_TRY_AGAIN_LATER -> if (!untilEndOfStream) return

                else -> if (outputIndex >= 0) {
                    val encoded = encoder.getOutputBuffer(outputIndex)
                    val isConfig = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (encoded != null && bufferInfo.size > 0 && muxerStarted && !isConfig) {
                        muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun createEncoder(): MediaCodec {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
    }
}
