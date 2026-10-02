package io.github.igormy.vocora.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/** Package name of the Shizuku app. */
const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

/** Where the user can get Shizuku. */
const val SHIZUKU_WEBSITE = "https://shizuku.rikka.app/"

/** Shizuku ships its service starter as a native library inside its own APK. */
private const val STARTER_LIBRARY = "libshizuku.so"

private const val STARTER_TIMEOUT_SECONDS = 10L
private const val BINDER_TIMEOUT_MILLIS = 5_000L
private const val BINDER_POLL_MILLIS = 200L

/**
 * On a cold start the service hands its binder over a moment after the app comes up, so wait a
 * little before deciding it is down: otherwise a running Shizuku looks stopped on the first check.
 */
internal const val INITIAL_BINDER_WAIT_MILLIS = 1_500L

/** Whether the Shizuku app is present on the device. */
fun isShizukuInstalled(context: Context): Boolean = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
    }
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

/** Whether the Shizuku service is up and answering. */
fun isShizukuRunning(): Boolean = try {
    Shizuku.pingBinder()
} catch (_: Exception) {
    // The binder can be missing or dead; either way Shizuku is not usable.
    false
}

/**
 * Tries to start the Shizuku service by running its own starter as root, then waits for the binder.
 *
 * Only root can do this: without it the service has to be started over ADB, so this returns false
 * and the user is pointed at [SHIZUKU_SETUP_GUIDE].
 */
internal suspend fun startShizuku(context: Context): Boolean = withContext(Dispatchers.IO) {
    if (!runStarterAsRoot(context)) return@withContext false
    awaitShizukuRunning()
}

private fun runStarterAsRoot(context: Context): Boolean = try {
    val starter = File(shizukuNativeLibraryDir(context), STARTER_LIBRARY)
    val process = ProcessBuilder("su", "-c", starter.absolutePath)
        .redirectErrorStream(true)
        .start()
    if (process.waitFor(STARTER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.exitValue() == 0
    } else {
        process.destroy()
        false
    }
} catch (_: IOException) {
    // No su binary: the device is not rooted.
    false
} catch (_: PackageManager.NameNotFoundException) {
    false
}

private fun shizukuNativeLibraryDir(context: Context): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getApplicationInfo(
            SHIZUKU_PACKAGE,
            PackageManager.ApplicationInfoFlags.of(0),
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getApplicationInfo(SHIZUKU_PACKAGE, 0)
    }.nativeLibraryDir

/** The service takes a moment to come up and hand its binder over, so poll instead of asking once. */
internal suspend fun awaitShizukuRunning(timeoutMillis: Long = BINDER_TIMEOUT_MILLIS): Boolean {
    var waited = 0L
    while (waited < timeoutMillis) {
        if (isShizukuRunning()) return true
        delay(BINDER_POLL_MILLIS)
        waited += BINDER_POLL_MILLIS
    }
    return isShizukuRunning()
}

/** Opens a page in the browser, to send the user to the Shizuku site. */
fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

/** Launches the installed Shizuku app, where the service is started. Returns false if it is missing. */
fun openShizukuApp(context: Context): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return false
    context.startActivity(intent)
    return true
}
