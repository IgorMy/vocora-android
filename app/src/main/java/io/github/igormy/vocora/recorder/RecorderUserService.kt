package io.github.igormy.vocora.recorder

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.sqrt
import kotlin.system.exitProcess

/**
 * Runs inside a Shizuku user service, so with the shell uid, which is the only way to open the
 * VOICE_CALL audio source and capture both sides of a call.
 *
 * This is the 0.2.0 smoke test: raw PCM into a WAV file, no encoder, plus a signal level per second
 * so that a file full of silence is not mistaken for a successful recording.
 */
class RecorderUserService() : IVocoraRecorder.Stub() {

    /** Shizuku hands a context to the user service when the constructor asks for one. */
    private var baseContext: Context? = null

    constructor(context: Context) : this() {
        baseContext = context
    }

    private companion object {
        const val TAG = "VocoraRecorder"

        /** Telephony runs at 8 or 16 kHz; 48 kHz is what scrcpy asks for. Take the first that opens. */
        val SAMPLE_RATES = intArrayOf(48_000, 16_000, 8_000)

        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
        const val WAV_HEADER_BYTES = 44
    }

    @Volatile
    private var result: String = "not run yet"

    override fun lastResult(): String = result

    @SuppressLint("MissingPermission")
    override fun record(outputPath: String, seconds: Int) {
        val output = File(outputPath)
        output.parentFile?.mkdirs()

        val recorder = openRecorder()
        if (recorder == null) {
            report("FAILED: VOICE_CALL did not open at any sample rate (is a call in progress?)")
            return
        }

        val sampleRate = recorder.sampleRate
        Log.i(TAG, "recording $seconds s at $sampleRate Hz into $outputPath")

        try {
            recorder.startRecording()
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                report("FAILED: AudioRecord opened at $sampleRate Hz but refused to start")
                return
            }
            val levels = writeWav(recorder, output, sampleRate, seconds)
            report(describe(output, sampleRate, levels))
        } catch (e: Exception) {
            report("FAILED: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    private fun openRecorder(): AudioRecord? {
        val context = ShellContext(baseContext ?: systemContext())
        for (rate in SAMPLE_RATES) {
            val minBuffer = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) {
                Log.i(TAG, "$rate Hz: unsupported buffer size ($minBuffer)")
                continue
            }
            val recorder = try {
                AudioRecord.Builder()
                    // Must come first: it is what attributes the capture to the shell package.
                    .setContext(context)
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(minBuffer * 8)
                    .build()
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

    /** Falls back to the system context when Shizuku built the service without one. */
    private fun systemContext(): Context {
        val activityThread = Class.forName("android.app.ActivityThread")
        val main = activityThread.getMethod("systemMain").invoke(null)
        return activityThread.getMethod("getSystemContext").invoke(main) as Context
    }

    /** Writes the PCM stream to a WAV file and returns the signal level of each second. */
    private fun writeWav(
        recorder: AudioRecord,
        output: File,
        sampleRate: Int,
        seconds: Int,
    ): List<Int> {
        val levels = mutableListOf<Int>()
        var totalBytes = 0

        RandomAccessFile(output, "rw").use { file ->
            file.setLength(0)
            file.write(ByteArray(WAV_HEADER_BYTES)) // Placeholder, sizes are only known at the end.

            val buffer = ShortArray(sampleRate) // One second per read.
            repeat(seconds) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) {
                    Log.w(TAG, "read returned $read")
                    return@repeat
                }
                levels += rms(buffer, read)
                file.write(toLittleEndian(buffer, read))
                totalBytes += read * 2
            }

            file.seek(0)
            file.write(wavHeader(sampleRate, totalBytes))
        }
        return levels
    }

    private fun rms(samples: ShortArray, count: Int): Int {
        var sum = 0.0
        for (i in 0 until count) {
            val sample = samples[i].toDouble()
            sum += sample * sample
        }
        return sqrt(sum / count).toInt()
    }

    private fun describe(output: File, sampleRate: Int, levels: List<Int>): String {
        val loudest = levels.maxOrNull() ?: 0
        val verdict = when {
            levels.isEmpty() -> "FAILED: no audio was read"
            loudest == 0 -> "SILENT: the source opened but every sample is zero"
            loudest < 50 -> "SUSPICIOUS: audio is almost silent (peak level $loudest)"
            else -> "OK: audio captured (peak level $loudest)"
        }
        return "$verdict | $sampleRate Hz | ${output.length()} bytes | levels=$levels"
    }

    private fun toLittleEndian(samples: ShortArray, count: Int): ByteArray {
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            val sample = samples[i].toInt()
            bytes[i * 2] = (sample and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun wavHeader(sampleRate: Int, dataBytes: Int): ByteArray {
        val byteRate = sampleRate * CHANNELS * BITS_PER_SAMPLE / 8
        val header = ByteArray(WAV_HEADER_BYTES)
        var offset = 0

        fun ascii(value: String) {
            value.forEach { header[offset++] = it.code.toByte() }
        }

        fun int32(value: Int) {
            header[offset++] = (value and 0xFF).toByte()
            header[offset++] = ((value shr 8) and 0xFF).toByte()
            header[offset++] = ((value shr 16) and 0xFF).toByte()
            header[offset++] = ((value shr 24) and 0xFF).toByte()
        }

        fun int16(value: Int) {
            header[offset++] = (value and 0xFF).toByte()
            header[offset++] = ((value shr 8) and 0xFF).toByte()
        }

        ascii("RIFF")
        int32(36 + dataBytes)
        ascii("WAVE")
        ascii("fmt ")
        int32(16) // PCM header size
        int16(1) // PCM format
        int16(CHANNELS)
        int32(sampleRate)
        int32(byteRate)
        int16(CHANNELS * BITS_PER_SAMPLE / 8)
        int16(BITS_PER_SAMPLE)
        ascii("data")
        int32(dataBytes)
        return header
    }

    private fun report(message: String) {
        result = message
        Log.i(TAG, message)
    }

    override fun destroy() {
        exitProcess(0)
    }
}
