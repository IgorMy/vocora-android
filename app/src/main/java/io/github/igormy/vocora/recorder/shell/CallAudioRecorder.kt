package io.github.igormy.vocora.recorder.shell

import android.content.Context
import android.media.AudioRecord
import android.util.Log
import io.github.igormy.vocora.recorder.logic.AudioLevel
import java.io.File

private const val TAG = "VocoraRecorder"
private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Records a call into a file, on its own thread.
 *
 * Opening the audio source is [VoiceCallAudioSource]'s job and writing the file is [AacEncoder]'s;
 * this keeps the two running together and reports how it went.
 */
class CallAudioRecorder(private val context: Context) {

    private var thread: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    var lastResult: String = "nothing recorded yet"
        private set

    val isRecording: Boolean get() = running

    /** Starts recording into [output]. Returns false when the audio source cannot be opened. */
    fun start(output: File): Boolean {
        if (running) return true

        val recorder = VoiceCallAudioSource.open(context)
        if (recorder == null) {
            lastResult = "FAILED: VOICE_CALL did not open at any sample rate"
            Log.w(TAG, lastResult)
            return false
        }

        running = true
        thread = Thread({ record(recorder, output) }, "vocora-recorder").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        return true
    }

    /** Stops the recording and waits for the file to be finished. */
    fun stop() {
        if (!running) return
        running = false
        thread?.join(STOP_TIMEOUT_MILLIS)
        thread = null
    }

    private fun record(recorder: AudioRecord, output: File) {
        output.parentFile?.mkdirs()
        val sampleRate = recorder.sampleRate
        var encoder: AacEncoder? = null

        try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                lastResult = "FAILED: AudioRecord opened at $sampleRate Hz but refused to start"
                Log.w(TAG, lastResult)
                return
            }

            Log.i(TAG, "recording at $sampleRate Hz into ${output.absolutePath}")
            encoder = AacEncoder(output, sampleRate)
            val peakLevel = encoder.encodeWhile(recorder) { running }
            lastResult = AudioLevel.describe(output.name, output.length(), sampleRate, peakLevel)
            Log.i(TAG, lastResult)
        } catch (e: Exception) {
            lastResult = "FAILED: ${e.javaClass.simpleName}: ${e.message}"
            Log.e(TAG, lastResult, e)
        } finally {
            running = false
            recorder.runCatching { stop() }
            recorder.release()
            encoder?.release()
        }
    }
}
