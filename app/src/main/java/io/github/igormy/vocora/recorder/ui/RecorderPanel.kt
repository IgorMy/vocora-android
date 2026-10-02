package io.github.igormy.vocora.recorder.ui

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.igormy.vocora.R
import io.github.igormy.vocora.recorder.client.CallRecorder
import io.github.igormy.vocora.recorder.client.RecorderStatus
import io.github.igormy.vocora.recorder.client.RecordingsFolder
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/** Turns background call recording on and off, picks where recordings go, and shows the state. */
@Composable
fun RecorderPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(CallRecorder.hasPermission()) }
    var folder by remember { mutableStateOf(RecordingsFolder.get(context)) }
    var enabled by remember { mutableStateOf(CallRecorder.isEnabled(context)) }
    var status by remember { mutableStateOf(RecorderStatus()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            runCatching { CallRecorder.syncWithPreference(context) }
                .onSuccess { status = it; error = null }
                .onFailure { error = it.message }
        }
    }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (RecordingsFolder.set(context, uri)) {
            folder = RecordingsFolder.get(context)
            error = null
            refresh()
        } else {
            error = context.getString(R.string.recorder_folder_unsupported)
        }
    }

    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            granted = grantResult == PackageManager.PERMISSION_GRANTED
        }
        Shizuku.addRequestPermissionResultListener(listener)
        onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    // The service keeps running without the app, so the state has to be read back on every resume.
    LifecycleResumeEffect(granted) {
        if (granted) refresh()
        onPauseOrDispose { }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!granted) {
            Button(onClick = { CallRecorder.requestPermission() }) {
                Text(stringResource(R.string.recorder_grant))
            }
            return@Column
        }

        OutlinedButton(onClick = { pickFolder.launch(null) }) {
            Text(stringResource(R.string.recorder_choose_folder))
        }

        Text(
            text = folder?.let { stringResource(R.string.recorder_folder, it) }
                ?: stringResource(R.string.recorder_folder_required),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.recorder_toggle))
            Switch(
                // Nothing can be recorded without somewhere to write it.
                enabled = folder != null,
                checked = enabled,
                onCheckedChange = { wanted ->
                    enabled = wanted
                    scope.launch {
                        runCatching {
                            if (wanted) CallRecorder.enable(context) else CallRecorder.disable(context)
                        }
                            .onSuccess { status = it; error = null }
                            .onFailure { error = it.message; enabled = !wanted }
                    }
                },
            )
        }

        Text(
            text = when {
                status.recording -> stringResource(R.string.recorder_state_recording)
                status.watching -> stringResource(R.string.recorder_state_watching)
                else -> stringResource(R.string.recorder_state_off)
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        status.lastResult.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall)
        }

        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
