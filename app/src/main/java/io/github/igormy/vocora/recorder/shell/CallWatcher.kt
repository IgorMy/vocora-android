package io.github.igormy.vocora.recorder.shell

import android.content.Context
import android.media.AudioManager
import android.util.Log
import java.util.concurrent.Executors

private const val TAG = "VocoraRecorder"

/**
 * Says when a call starts and ends, by following the audio mode.
 *
 * TelephonyManager is not an option in the recorder process: its service manager is not initialised
 * there and registering a callback throws. The audio mode is reported by a system callback, so
 * nothing has to poll.
 */
class CallWatcher(private val context: Context) {

    private val audio: AudioManager by lazy { context.getSystemService(AudioManager::class.java) }
    private val executor = Executors.newSingleThreadExecutor()

    private var listener: AudioManager.OnModeChangedListener? = null
    private var inCall = false

    /** Starts listening. Returns false if the system refused, in which case nothing is watched. */
    fun start(onCallStarted: () -> Unit, onCallEnded: () -> Unit): Boolean {
        if (listener != null) return true

        val modeListener = AudioManager.OnModeChangedListener { mode ->
            handle(mode, onCallStarted, onCallEnded)
        }
        try {
            audio.addOnModeChangedListener(executor, modeListener)
        } catch (e: Exception) {
            Log.w(TAG, "addOnModeChangedListener failed: ${e.javaClass.simpleName}: ${e.message}")
            return false
        }
        listener = modeListener

        // A call can already be up when watching starts.
        handle(audio.mode, onCallStarted, onCallEnded)
        return true
    }

    fun stop() {
        listener?.let { runCatching { audio.removeOnModeChangedListener(it) } }
        listener = null
        inCall = false
    }

    private fun handle(mode: Int, onCallStarted: () -> Unit, onCallEnded: () -> Unit) {
        val nowInCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
        if (nowInCall == inCall) return
        inCall = nowInCall
        if (nowInCall) onCallStarted() else onCallEnded()
    }
}
