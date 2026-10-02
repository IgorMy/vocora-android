package com.example.vocora.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import rikka.shizuku.Shizuku

/** Package name of the Shizuku app. */
const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

/** Where the user can get Shizuku and read how to start it. */
const val SHIZUKU_WEBSITE = "https://shizuku.rikka.app/"

/** Checks Shizuku availability: installed first, then whether its service answers. */
fun checkShizukuStatus(context: Context): ShizukuStatus = when {
    !isShizukuInstalled(context) -> ShizukuStatus.NotInstalled
    !isShizukuRunning() -> ShizukuStatus.NotRunning
    else -> ShizukuStatus.Running
}

private fun isShizukuInstalled(context: Context): Boolean = try {
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

private fun isShizukuRunning(): Boolean = try {
    Shizuku.pingBinder()
} catch (_: Exception) {
    // The binder can be missing or dead; either way Shizuku is not usable.
    false
}

/** Opens the Shizuku website so the user can install it or learn how to start the service. */
fun openShizukuWebsite(context: Context) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_WEBSITE)))
}

/** Launches the installed Shizuku app, where the service is started. Returns false if it is missing. */
fun openShizukuApp(context: Context): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return false
    context.startActivity(intent)
    return true
}

/** Observable Shizuku availability, re-checkable on demand. */
class ShizukuStatusState internal constructor(
    private val context: Context,
    initialStatus: ShizukuStatus,
) {
    var status: ShizukuStatus by mutableStateOf(initialStatus)
        private set

    fun refresh() {
        status = checkShizukuStatus(context)
    }
}

/**
 * Observes Shizuku availability: re-checks when the binder is received or dies, and every time the
 * screen resumes, so coming back from installing or starting Shizuku shows the new state.
 */
@Composable
fun rememberShizukuStatus(): ShizukuStatusState {
    val context = LocalContext.current
    val state = remember(context) { ShizukuStatusState(context, checkShizukuStatus(context)) }

    DisposableEffect(state) {
        val onBinderReceived = Shizuku.OnBinderReceivedListener { state.refresh() }
        val onBinderDead = Shizuku.OnBinderDeadListener { state.refresh() }
        Shizuku.addBinderReceivedListenerSticky(onBinderReceived)
        Shizuku.addBinderDeadListener(onBinderDead)
        onDispose {
            Shizuku.removeBinderReceivedListener(onBinderReceived)
            Shizuku.removeBinderDeadListener(onBinderDead)
        }
    }

    LifecycleResumeEffect(state) {
        state.refresh()
        onPauseOrDispose { }
    }

    return state
}
