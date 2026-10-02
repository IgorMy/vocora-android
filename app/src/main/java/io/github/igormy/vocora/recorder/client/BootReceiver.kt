package io.github.igormy.vocora.recorder.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Puts the recorder back to work after a reboot.
 *
 * A reboot also stops Shizuku, and on a device without root only the user can start it again, so
 * this often fails and the recorder is armed again the next time the app is opened.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!CallRecorder.isEnabled(context)) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val status = CallRecorder.syncWithPreference(context)
                Log.i("VocoraRecorder", "armed after boot: watching=${status.watching}")
            } catch (e: Exception) {
                Log.i("VocoraRecorder", "could not arm after boot, Shizuku is probably down: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
