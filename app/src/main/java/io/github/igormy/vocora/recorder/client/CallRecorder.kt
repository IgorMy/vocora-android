package io.github.igormy.vocora.recorder.client

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import io.github.igormy.vocora.BuildConfig
import io.github.igormy.vocora.recorder.IVocoraRecorder
import io.github.igormy.vocora.recorder.shell.RecorderUserService
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

private const val TAG = "VocoraRecorder"
private const val SHIZUKU_PERMISSION_REQUEST = 1001
private const val BIND_TIMEOUT_MILLIS = 15_000L

private const val PREFERENCES = "vocora"
private const val KEY_WATCH_CALLS = "watch_calls"

/** What the recorder service reports about itself. */
data class RecorderStatus(
    val watching: Boolean = false,
    val recording: Boolean = false,
    val lastResult: String = "",
)

/**
 * Drives [RecorderUserService], which Shizuku runs as the shell user.
 *
 * The service is bound as a daemon, so it keeps watching for calls after this app process is gone.
 * Binding needs the Shizuku permission, which the user grants once in a Shizuku dialog.
 */
object CallRecorder {

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, RecorderUserService::class.java.name),
    )
        .daemon(true)
        .processNameSuffix("recorder")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val bindLock = Mutex()
    private var service: IVocoraRecorder? = null

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    fun requestPermission() {
        Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST)
    }

    /** Whether the user asked for calls to be recorded, remembered across restarts. */
    fun isEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_WATCH_CALLS, false)

    private fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(KEY_WATCH_CALLS, enabled).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /** Starts watching for calls and remembers the choice. Needs a folder to write to. */
    suspend fun enable(context: Context): RecorderStatus {
        val folder = RecordingsFolder.path(context)
            ?: throw IllegalStateException("no folder has been chosen")
        setEnabled(context, true)
        return withService {
            it.setBlacklist(Blacklist.numbers(context), Blacklist.names(context))
            it.startWatching(folder, cancelAction(context))
            it.status()
        }
    }

    /** Stops watching and forgets the choice. */
    suspend fun disable(context: Context): RecorderStatus {
        setEnabled(context, false)
        return withService { it.stopWatching(); it.status() }
    }

    /** Re-arms the service when it should be watching but is not, after a reboot or an update. */
    suspend fun syncWithPreference(context: Context): RecorderStatus = withService { recorder ->
        val folder = RecordingsFolder.path(context)
        when {
            isEnabled(context) && folder != null && !recorder.isWatching -> {
                recorder.setBlacklist(Blacklist.numbers(context), Blacklist.names(context))
                recorder.startWatching(folder, cancelAction(context))
            }

            (!isEnabled(context) || folder == null) && recorder.isWatching ->
                recorder.stopWatching()
        }
        recorder.status()
    }

    suspend fun status(): RecorderStatus = withService { it.status() }

    /** Sends the list again after it is edited, so a change applies to the very next call. */
    suspend fun updateBlacklist(context: Context): Unit =
        withService { it.setBlacklist(Blacklist.numbers(context), Blacklist.names(context)) }

    /** Tells the recorder who the call in progress is with, or that there is none. */
    suspend fun setCurrentCall(number: String?, name: String?): Unit =
        withService { it.setCurrentCall(number, name) }

    /** Drops the recording of the call in progress, keeping the recorder armed for the next one. */
    suspend fun cancelCurrentRecording(): Unit = withService { it.cancelCurrentRecording() }

    /**
     * The notification's cancel button, built here and handed to the recorder.
     *
     * It belongs to this app, so the receiver it fires can stay private: a button built by the
     * recorder would be fired as the shell package and would need an exported receiver, which any
     * app could then use to throw away a recording in progress.
     */
    private fun cancelAction(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, CancelRecordingReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun IVocoraRecorder.status() =
        RecorderStatus(isWatching, isRecording, lastResult())

    private suspend fun <T> withService(block: (IVocoraRecorder) -> T): T =
        withContext(Dispatchers.IO) {
            val recorder = connect()
            block(recorder)
        }

    private suspend fun connect(): IVocoraRecorder = bindLock.withLock {
        service?.let { if (it.asBinder().pingBinder()) return@withLock it }
        service = null

        withTimeout(BIND_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val serviceConnection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        val recorder = IVocoraRecorder.Stub.asInterface(binder)
                        if (binder == null || !binder.pingBinder() || recorder == null) {
                            if (continuation.isActive) {
                                continuation.cancel(IllegalStateException("user service binder is dead"))
                            }
                            return
                        }
                        service = recorder
                        if (continuation.isActive) continuation.resume(recorder)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        Log.i(TAG, "user service disconnected")
                        service = null
                    }
                }
                Shizuku.bindUserService(userServiceArgs, serviceConnection)
            }
        }
    }
}
