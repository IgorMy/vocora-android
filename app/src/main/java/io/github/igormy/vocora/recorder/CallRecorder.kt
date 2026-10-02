package io.github.igormy.vocora.recorder

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import io.github.igormy.vocora.BuildConfig
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** Where the 0.2.0 smoke test leaves its recording. */
const val SMOKE_TEST_OUTPUT = "/sdcard/Recordings/Vocora/smoke-test.wav"

const val SMOKE_TEST_SECONDS = 10

private const val TAG = "VocoraRecorder"
private const val SHIZUKU_PERMISSION_REQUEST = 1001

/**
 * Talks to [RecorderUserService], which Shizuku runs as the shell user.
 *
 * Binding needs the Shizuku permission, which the user grants once in a Shizuku dialog.
 */
object CallRecorder {

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, RecorderUserService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("recorder")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    fun requestPermission() {
        Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST)
    }

    /** Records [seconds] of call audio into [outputPath] and returns what the service reports. */
    suspend fun record(
        outputPath: String = SMOKE_TEST_OUTPUT,
        seconds: Int = SMOKE_TEST_SECONDS,
    ): String = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext "FAILED: Shizuku permission not granted"

        var connection: ServiceConnection? = null
        try {
            val (recorder, serviceConnection) = bind()
            connection = serviceConnection
            recorder.record(outputPath, seconds)
            recorder.lastResult()
        } catch (e: Exception) {
            Log.e(TAG, "smoke test failed", e)
            "FAILED: ${e.javaClass.simpleName}: ${e.message}"
        } finally {
            connection?.let { Shizuku.unbindUserService(userServiceArgs, it, true) }
        }
    }

    private suspend fun bind(): Pair<IVocoraRecorder, ServiceConnection> =
        suspendCancellableCoroutine { continuation ->
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (!continuation.isActive) return
                    val recorder = IVocoraRecorder.Stub.asInterface(binder)
                    if (binder != null && binder.pingBinder() && recorder != null) {
                        continuation.resume(recorder to this)
                    } else {
                        continuation.cancel(IllegalStateException("user service binder is dead"))
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) = Unit
            }
            Shizuku.bindUserService(userServiceArgs, connection)
        }
}
