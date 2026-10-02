package io.github.igormy.vocora.recorder.shell

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

private const val TAG = "VocoraRecorder"

/** Telephony runs at 8 or 16 kHz; 48 kHz is what scrcpy asks for and what this device accepted. */
private val SAMPLE_RATES = intArrayOf(48_000, 16_000, 8_000)

private const val BUFFER_MULTIPLIER = 8

/**
 * Opens the audio source that carries both sides of a call.
 *
 * Two things are needed and neither is optional: the process must have the shell uid, which Shizuku
 * provides, and the capture must be attributed to the shell package through [ShellContext].
 * Otherwise AudioRecord does not initialise at any sample rate.
 */
object VoiceCallAudioSource {

    /** Returns a started-capable recorder at the first sample rate that opens, or null. */
    fun open(context: Context): AudioRecord? {
        val shellContext = ShellContext(context)
        for (rate in SAMPLE_RATES) {
            val minBuffer = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) continue

            val recorder = try {
                build(shellContext, rate, minBuffer)
            } catch (e: Exception) {
                Log.i(TAG, "$rate Hz: ${e.javaClass.simpleName}: ${e.message}")
                continue
            }

            if (recorder.state == AudioRecord.STATE_INITIALIZED) return recorder
            Log.i(TAG, "$rate Hz: AudioRecord did not initialise")
            recorder.release()
        }
        return null
    }

    private fun build(shellContext: Context, rate: Int, minBuffer: Int): AudioRecord =
        AudioRecord.Builder()
            // Must be set: it is what attributes the capture to the shell package.
            .setContext(shellContext)
            .setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer * BUFFER_MULTIPLIER)
            .build()
}
