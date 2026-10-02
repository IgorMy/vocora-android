package io.github.igormy.vocora.recorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Lets the smoke test be started over ADB while the call screen is in front:
 *
 *     adb shell am broadcast -a io.github.igormy.vocora.SMOKE_TEST io.github.igormy.vocora
 *
 * Temporary, it goes away once call detection drives the recorder on its own.
 */
class SmokeTestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val seconds = intent.getIntExtra("seconds", SMOKE_TEST_SECONDS)
        val output = intent.getStringExtra("output") ?: SMOKE_TEST_OUTPUT
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i("VocoraRecorder", CallRecorder.record(output, seconds))
            } finally {
                pending.finish()
            }
        }
    }
}
