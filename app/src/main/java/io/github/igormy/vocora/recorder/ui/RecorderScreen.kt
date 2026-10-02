package io.github.igormy.vocora.recorder.ui

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.igormy.vocora.R
import io.github.igormy.vocora.recorder.client.CallRecorder
import io.github.igormy.vocora.recorder.client.Recording
import io.github.igormy.vocora.recorder.client.RecorderStatus
import io.github.igormy.vocora.recorder.client.RecordingPlayer
import io.github.igormy.vocora.recorder.client.RecordingsFolder
import io.github.igormy.vocora.recorder.client.RecordingsLibrary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/** Often enough that the bar moves smoothly, rarely enough to be free. */
private const val PROGRESS_REFRESH_MILLIS = 200L

/**
 * The whole app once Shizuku is working: where recordings go, whether to record, and what has been
 * recorded so far.
 */
@Composable
fun RecorderScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { RecordingPlayer(context) }

    var granted by remember { mutableStateOf(CallRecorder.hasPermission()) }
    var folder by remember { mutableStateOf(RecordingsFolder.path(context)) }
    var enabled by remember { mutableStateOf(CallRecorder.isEnabled(context)) }
    var status by remember { mutableStateOf(RecorderStatus()) }
    var recordings by remember { mutableStateOf(emptyList<Recording>()) }
    var playingName by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }

    // MediaPlayer reports no progress of its own, so it is read while something is playing. The
    // loop is tied to what is playing, so it stops as soon as nothing is.
    LaunchedEffect(playingName) {
        progress = 0f
        while (playingName != null) {
            progress = player.progress
            delay(PROGRESS_REFRESH_MILLIS)
        }
    }

    fun refresh() {
        recordings = RecordingsLibrary.list(context)
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
            folder = RecordingsFolder.path(context)
            error = null
            refresh()
        } else {
            error = context.getString(R.string.recorder_folder_unsupported)
        }
    }

    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, result ->
            granted = result == PackageManager.PERMISSION_GRANTED
        }
        Shizuku.addRequestPermissionResultListener(listener)
        onDispose {
            Shizuku.removeRequestPermissionResultListener(listener)
            player.stop()
        }
    }

    // The recorder keeps running without the app, so its state is read back on every resume.
    LifecycleResumeEffect(granted) {
        if (granted) refresh()
        onPauseOrDispose { }
    }

    if (!granted) {
        PermissionPrompt(modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FolderRow(folder) { pickFolder.launch(null) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(stringResource(R.string.recorder_toggle))
                Text(
                    text = when {
                        status.recording -> stringResource(R.string.recorder_state_recording)
                        status.watching -> stringResource(R.string.recorder_state_watching)
                        else -> stringResource(R.string.recorder_state_off)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (folder != null && RecordingsFolder.treeUri(context) == null) {
            // Folder picked by a version that only stored the path: the app cannot read it without
            // the permission that comes with the tree URI, so it has to be picked once more.
            Text(
                text = stringResource(R.string.recordings_folder_reselect),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            RecordingsList(
                recordings = recordings,
                playingName = playingName,
                progress = progress,
                onPlay = { recording ->
                    player.toggle(recording.uri) { playingName = null }
                    playingName = if (player.isPlaying(recording.uri)) recording.name else null
                },
            )
        }
    }
}

@Composable
private fun FolderRow(folder: String?, onPick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onPick) {
            Text(stringResource(R.string.recorder_choose_folder))
        }
        Text(
            text = folder ?: stringResource(R.string.recorder_folder_required),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionPrompt(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.recorder_permission_explained),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = { CallRecorder.requestPermission() }) {
            Text(stringResource(R.string.recorder_grant))
        }
    }
}
