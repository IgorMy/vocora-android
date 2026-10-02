package io.github.igormy.vocora.recorder.shell

import android.content.Context
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import io.github.igormy.vocora.recorder.logic.AudioLevel
import io.github.igormy.vocora.recorder.logic.PcmMixer
import java.io.File

private const val TAG = "VocoraRecorder"
private const val STOP_TIMEOUT_MILLIS = 5_000L

/** A fifth of a second of audio per read: small enough to stay responsive, large enough to be cheap. */
private const val CHUNK_DIVISOR = 5

/** What a recorded call leaves behind. The first is for listening, the other two for transcribing. */
const val MIXED_FILE = "mixed.m4a"
const val UPLINK_FILE = "uplink.m4a"
const val DOWNLINK_FILE = "downlink.m4a"

/**
 * Records a call into a folder, on its own thread.
 *
 * Both sides are captured separately, through VOICE_UPLINK and VOICE_DOWNLINK, and written to their
 * own files as well as mixed into one. Keeping them apart makes who said what a fact rather than
 * something a transcription has to guess. Devices that only give VOICE_CALL fall back to the mix
 * alone.
 */
class CallAudioRecorder(private val context: Context) {

    private var thread: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    var lastResult: String = "nothing recorded yet"
        private set

    val isRecording: Boolean get() = running

    /** Starts recording into [directory]. Returns false when no audio source can be opened. */
    fun start(directory: File): Boolean {
        if (running) return true
        directory.mkdirs()

        val uplink = VoiceCallAudioSource.open(context, MediaRecorder.AudioSource.VOICE_UPLINK)
        val downlink = VoiceCallAudioSource.open(context, MediaRecorder.AudioSource.VOICE_DOWNLINK)

        if (uplink != null && downlink != null) {
            running = true
            thread = startThread { recordBothSides(uplink, downlink, directory) }
            return true
        }

        uplink?.release()
        downlink?.release()
        Log.i(TAG, "the two sides are not available separately, falling back to the mixed source")

        val mixed = VoiceCallAudioSource.open(context)
        if (mixed == null) {
            lastResult = "FAILED: no call audio source opened"
            Log.w(TAG, lastResult)
            return false
        }
        running = true
        thread = startThread { recordMixedOnly(mixed, directory) }
        return true
    }

    /** Stops the recording and waits for the files to be finished. */
    fun stop() {
        if (!running) return
        running = false
        thread?.join(STOP_TIMEOUT_MILLIS)
        thread = null
    }

    private fun startThread(work: () -> Unit): Thread =
        Thread(work, "vocora-recorder").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }

    private fun recordBothSides(uplink: AudioRecord, downlink: AudioRecord, directory: File) {
        val sampleRate = uplink.sampleRate
        val chunk = chunkBytes(sampleRate)
        val encoders = listOf(
            AacEncoder(File(directory, UPLINK_FILE), sampleRate),
            AacEncoder(File(directory, DOWNLINK_FILE), sampleRate),
            AacEncoder(File(directory, MIXED_FILE), sampleRate),
        )
        val (uplinkEncoder, downlinkEncoder, mixedEncoder) = encoders

        val fromUplink = ByteArray(chunk)
        val fromDownlink = ByteArray(chunk)
        val mixed = ByteArray(chunk)
        var peakLevel = 0
        var peakUplink = 0
        var peakDownlink = 0

        try {
            if (!startBoth(uplink, downlink, sampleRate)) return

            while (running) {
                // Read in lockstep so the two files stay aligned with each other and with the mix.
                val readUp = uplink.read(fromUplink, 0, chunk).coerceAtLeast(0)
                val readDown = downlink.read(fromDownlink, 0, chunk).coerceAtLeast(0)
                val length = maxOf(readUp, readDown)
                if (length == 0) continue

                fromUplink.fill(0, readUp, length)
                fromDownlink.fill(0, readDown, length)
                PcmMixer.mix(fromUplink, fromDownlink, length, mixed)

                uplinkEncoder.write(fromUplink, readUp)
                downlinkEncoder.write(fromDownlink, readDown)
                mixedEncoder.write(mixed, length)
                peakLevel = maxOf(peakLevel, AudioLevel.rms(mixed, length))
                peakUplink = maxOf(peakUplink, AudioLevel.rms(fromUplink, readUp))
                peakDownlink = maxOf(peakDownlink, AudioLevel.rms(fromDownlink, readDown))
            }

            encoders.forEach { it.finish() }
            lastResult = describeBothSides(directory, sampleRate, peakLevel, peakUplink, peakDownlink)
            Log.i(TAG, lastResult)
        } catch (e: Exception) {
            lastResult = "FAILED: ${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, lastResult, e)
        } finally {
            running = false
            listOf(uplink, downlink).forEach {
                it.runCatching { stop() }
                it.release()
            }
            encoders.forEach { it.release() }
        }
    }

    private fun recordMixedOnly(recorder: AudioRecord, directory: File) {
        val sampleRate = recorder.sampleRate
        val chunk = chunkBytes(sampleRate)
        val encoder = AacEncoder(File(directory, MIXED_FILE), sampleRate)
        val buffer = ByteArray(chunk)
        var peakLevel = 0

        try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                lastResult = "FAILED: the audio source opened at $sampleRate Hz but refused to start"
                Log.w(TAG, lastResult)
                return
            }
            Log.i(TAG, "recording the mixed source at $sampleRate Hz into ${directory.name}")

            while (running) {
                val read = recorder.read(buffer, 0, chunk)
                if (read <= 0) continue
                encoder.write(buffer, read)
                peakLevel = maxOf(peakLevel, AudioLevel.rms(buffer, read))
            }

            encoder.finish()
            lastResult = AudioLevel.describe(
                "${directory.name}/$MIXED_FILE",
                encoder.file.length(),
                sampleRate,
                peakLevel,
            )
            Log.i(TAG, lastResult)
        } catch (e: Exception) {
            lastResult = "FAILED: ${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, lastResult, e)
        } finally {
            running = false
            recorder.runCatching { stop() }
            recorder.release()
            encoder.release()
        }
    }

    private fun startBoth(uplink: AudioRecord, downlink: AudioRecord, sampleRate: Int): Boolean {
        uplink.startRecording()
        downlink.startRecording()
        val started = uplink.recordingState == AudioRecord.RECORDSTATE_RECORDING &&
            downlink.recordingState == AudioRecord.RECORDSTATE_RECORDING
        if (!started) {
            lastResult = "FAILED: the two sides opened at $sampleRate Hz but refused to start"
            Log.w(TAG, lastResult)
        } else {
            Log.i(TAG, "recording both sides at $sampleRate Hz")
        }
        return started
    }

    /** Reports the mix, and each side on its own so a silent one is noticed. */
    private fun describeBothSides(
        directory: File,
        sampleRate: Int,
        peakLevel: Int,
        peakUplink: Int,
        peakDownlink: Int,
    ): String {
        val mix = AudioLevel.describe(
            "${directory.name}/$MIXED_FILE",
            File(directory, MIXED_FILE).length(),
            sampleRate,
            peakLevel,
        )
        return "$mix | uplink peak $peakUplink, downlink peak $peakDownlink"
    }

    private fun chunkBytes(sampleRate: Int): Int = sampleRate / CHUNK_DIVISOR * 2
}
