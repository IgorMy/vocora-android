package io.github.igormy.vocora.recorder.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "VocoraRecorder"

/**
 * Carries the notification's Cancel recording button to the recorder.
 *
 * The recorder would rather handle it itself, but a process started by Shizuku is not an app process
 * the ActivityManager knows about: it can register a receiver and the system will never deliver to
 * it. So the button wakes this app instead, which asks the recorder over Shizuku's binder.
 */
class CancelRecordingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                CallRecorder.cancelCurrentRecording()
                Log.i(TAG, "cancelled the recording from the notification")
            } catch (e: Exception) {
                Log.w(TAG, "could not cancel the recording: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
