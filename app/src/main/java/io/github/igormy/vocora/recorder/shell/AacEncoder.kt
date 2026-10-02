package io.github.igormy.vocora.recorder.shell

import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import io.github.igormy.vocora.recorder.logic.AudioLevel
import java.io.File

private const val BIT_RATE = 64_000
private const val CODEC_TIMEOUT_US = 10_000L
private const val BYTES_PER_SAMPLE = 2

/**
 * Encodes what an [AudioRecord] captures into AAC inside an .m4a file.
 *
 * Nothing here knows about calls: it moves PCM into the encoder and encoded frames into the file
 * until it is told to stop, and reports the loudest level it saw on the way.
 */
class AacEncoder(private val output: File, private val sampleRate: Int) {

    private val encoder: MediaCodec = createEncoder()
    private val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    private var trackIndex = -1
    private var muxerStarted = false
    private var totalSamples = 0L
    private var peakLevel = 0

    /** Runs until [keepGoing] turns false, then finishes the file. Returns the peak signal level. */
    fun encodeWhile(recorder: AudioRecord, keepGoing: () -> Boolean): Int {
        val bufferInfo = MediaCodec.BufferInfo()
        var endOfStreamSent = false

        while (true) {
            if (!endOfStreamSent) {
                endOfStreamSent = feed(recorder, keepGoing())
            }
            if (drain(bufferInfo)) return peakLevel
        }
    }

    fun release() {
        encoder.runCatching { stop() }
        encoder.release()
        muxer.runCatching { stop() }
        muxer.release()
    }

    /** Hands one buffer of PCM to the encoder. Returns true once end of stream has been queued. */
    private fun feed(recorder: AudioRecord, keepGoing: Boolean): Boolean {
        val inputIndex = encoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (inputIndex < 0) return false

        val input = encoder.getInputBuffer(inputIndex) ?: return false
        input.clear()
        val presentationTimeUs = totalSamples * 1_000_000L / sampleRate

        if (!keepGoing) {
            encoder.queueInputBuffer(
                inputIndex,
                0,
                0,
                presentationTimeUs,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
            )
            return true
        }

        val read = recorder.read(input, input.capacity())
        if (read > 0) {
            peakLevel = maxOf(peakLevel, AudioLevel.rms(input, read))
            totalSamples += read / BYTES_PER_SAMPLE
            encoder.queueInputBuffer(inputIndex, 0, read, presentationTimeUs, 0)
        } else {
            encoder.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, 0)
        }
        return false
    }

    /** Writes whatever the encoder has ready. Returns true when the stream is over. */
    private fun drain(bufferInfo: MediaCodec.BufferInfo): Boolean {
        when (val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)) {
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                trackIndex = muxer.addTrack(encoder.outputFormat)
                muxer.start()
                muxerStarted = true
            }

            MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

            else -> if (outputIndex >= 0) {
                val encoded = encoder.getOutputBuffer(outputIndex)
                val isConfig = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                if (encoded != null && bufferInfo.size > 0 && muxerStarted && !isConfig) {
                    muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                }
                encoder.releaseOutputBuffer(outputIndex, false)
                return bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            }
        }
        return false
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
