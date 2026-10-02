package io.github.igormy.vocora

import android.app.Application
import android.util.Log
import io.github.igormy.vocora.recorder.client.CallRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

private const val TAG = "VocoraRecorder"

/**
 * Puts the recorder back to work whenever this process starts and Shizuku is up.
 *
 * Shizuku hands its binder to every app that declares its provider as soon as the server starts,
 * which also starts this process. Listening here means the recorder is re-armed after a reboot
 * without depending on when Shizuku manages to start, which with wireless debugging can be well
 * after BOOT_COMPLETED.
 */
class VocoraApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Shizuku.addBinderReceivedListenerSticky {
            if (!CallRecorder.isEnabled(this)) return@addBinderReceivedListenerSticky
            scope.launch {
                runCatching { CallRecorder.syncWithPreference(this@VocoraApplication) }
                    .onSuccess { Log.i(TAG, "re-armed on Shizuku binder: watching=${it.watching}") }
                    .onFailure { Log.i(TAG, "could not re-arm: ${it.message}") }
            }
        }
    }
}
