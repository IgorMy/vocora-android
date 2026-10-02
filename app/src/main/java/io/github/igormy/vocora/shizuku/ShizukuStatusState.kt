package io.github.igormy.vocora.shizuku

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/** Observable Shizuku availability, re-checkable on demand. */
class ShizukuStatusState internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var status: ShizukuStatus by mutableStateOf(ShizukuStatus.Starting)
        private set

    private var job: Job? = null

    /** Re-checks Shizuku and, when it is installed but down, tries to start its service. */
    fun refresh() {
        job?.cancel()
        job = scope.launch {
            if (!isShizukuInstalled(context)) {
                status = ShizukuStatus.NotInstalled
                return@launch
            }
            if (isShizukuRunning()) {
                status = ShizukuStatus.Running
                return@launch
            }
            status = ShizukuStatus.Starting
            if (awaitShizukuRunning(INITIAL_BINDER_WAIT_MILLIS)) {
                status = ShizukuStatus.Running
                return@launch
            }
            status = if (startShizuku(context)) ShizukuStatus.Running else ShizukuStatus.CannotStart
        }
    }

    /** The service came up on its own, so drop any ongoing start attempt. */
    internal fun onBinderReceived() {
        job?.cancel()
        status = ShizukuStatus.Running
    }
}

/**
 * Observes Shizuku availability: starts the service when it is down, follows the binder coming up or
 * dying, and re-checks on every resume so coming back from Shizuku shows the new state.
 */
@Composable
fun rememberShizukuStatus(): ShizukuStatusState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(context, scope) { ShizukuStatusState(context, scope) }

    DisposableEffect(state) {
        val onBinderReceived = Shizuku.OnBinderReceivedListener { state.onBinderReceived() }
        // The service died: check again and try to bring it back.
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
