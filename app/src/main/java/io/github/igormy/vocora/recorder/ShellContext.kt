package io.github.igormy.vocora.recorder

import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper

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
}
