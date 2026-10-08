package io.github.igormy.vocora.recorder.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug builds only. Raises the recording notification without needing a call:
 *
 *     adb shell am broadcast -a io.github.igormy.vocora.TEST_NOTIFICATION io.github.igormy.vocora
 *     adb shell am broadcast -a io.github.igormy.vocora.TEST_NOTIFICATION --ez visible false io.github.igormy.vocora
 */
class NotificationTestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val visible = intent.getBooleanExtra("visible", true)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                CallRecorder.showTestNotification(visible)
                Log.i("VocoraRecorder", "test notification visible=$visible")
            } catch (e: Exception) {
                Log.w("VocoraRecorder", "test notification failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
