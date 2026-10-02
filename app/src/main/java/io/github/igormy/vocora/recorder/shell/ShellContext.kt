package io.github.igormy.vocora.recorder.shell

import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.util.Log

private const val TAG = "VocoraRecorder"

/** The uid every process started by the Shizuku server runs as. */
private const val SHELL_UID = 2000

private const val SHELL_PACKAGE = "com.android.shell"

/**
 * Presents the recorder process as `com.android.shell` instead of as this app.
 *
 * The process already runs with the shell uid, but the audio framework attributes the capture to the
 * package in the context, which would be Vocora. Shell owning the uid and Vocora owning the package
 * do not match, so the capture is denied. scrcpy solves it the same way.
 */
class ShellContext(base: Context) : ContextWrapper(base) {

    override fun getPackageName(): String = SHELL_PACKAGE

    override fun getOpPackageName(): String = SHELL_PACKAGE

    override fun getAttributionSource(): AttributionSource =
        AttributionSource.Builder(SHELL_UID)
            .setPackageName(SHELL_PACKAGE)
            .build()

    override fun getApplicationContext(): Context = this

    /**
     * Some managers keep the context they were created with and take the package from it later, so
     * overriding the getters above is not enough: NotificationManager would still post as this app
     * and be rejected with "Package ... is not owned by uid 2000". Point it back at this context.
     */
    override fun getSystemService(name: String): Any? {
        val service = super.getSystemService(name) ?: return null
        if (name == NOTIFICATION_SERVICE) {
            runCatching {
                val field = service.javaClass.getDeclaredField("mContext")
                field.isAccessible = true
                field.set(service, this)
            }.onFailure {
                Log.i(TAG, "could not retarget $name: ${it.javaClass.simpleName}: ${it.message}")
            }
        }
        return service
    }
}
