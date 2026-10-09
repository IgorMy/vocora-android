package io.github.igormy.vocora.recorder.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.igormy.vocora.recorder.server.UploadQueue
import io.github.igormy.vocora.recorder.store.RecordingIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "VocoraRecorder"

/**
 * Wakes the app when a call has just been recorded.
 *
 * Nothing can be uploaded while the app is dead, and the app is usually dead: it is the recorder,
 * running as the shell user, that notices the call ending. So the recorder fires this, the app comes
 * up long enough to write the row and hand the queue over to WorkManager, and goes back to sleep.
 */
class CallEndedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // The recording is new, so it has no row yet and the queue would find nothing.
                RecordingIndex.reconcile(app)
                UploadQueue.ask(app)
            } catch (e: Exception) {
                Log.w(TAG, "could not queue the recording: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
