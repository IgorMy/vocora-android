package io.github.igormy.vocora.recorder.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.igormy.vocora.R
import io.github.igormy.vocora.recorder.client.PLAYBACK_SPEEDS
import io.github.igormy.vocora.recorder.client.Recording
import io.github.igormy.vocora.recorder.client.RecordingPlayer
import io.github.igormy.vocora.recorder.logic.RecordingLabel
import io.github.igormy.vocora.recorder.server.ServerSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Often enough that the bar moves smoothly, rarely enough to be free. */
private const val PLAYBACK_REFRESH_MILLIS = 200L

/** What has been recorded, grouped by day, with the player for whichever one is picked. */
@Composable
fun RecordingsTab(
    recordings: List<Recording>,
    loading: Boolean,
    needsFolderAgain: Boolean,
    onDelete: suspend (Recording) -> Unit,
    onContactsGranted: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { RecordingPlayer(context) }
    val today = remember { System.currentTimeMillis() }

    var selected by remember { mutableStateOf<Recording?>(null) }
    var pendingDelete by remember { mutableStateOf<Recording?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(player.speed) }

    // Names and faces come from the address book, so ask once and re-read the list if granted.
    val askContacts = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) scope.launch { onContactsGranted() } }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
        if (granted != PackageManager.PERMISSION_GRANTED) {
            askContacts.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    fun open(recording: Recording) {
        selected = recording
        player.play(recording.mixedUri) { playing = false }
        playing = player.isPlaying
    }

    fun close() {
        player.stop()
        selected = null
        playing = false
        position = 0
        duration = 0
    }

    fun step(by: Int) {
        val index = recordings.indexOf(selected)
        recordings.getOrNull(index + by)?.let(::open)
    }

    // Nothing reports playback moving along, so it is read while something is open.
    LaunchedEffect(selected) {
        while (selected != null) {
            position = player.positionMillis
            duration = player.durationMillis
            playing = player.isPlaying
            delay(PLAYBACK_REFRESH_MILLIS)
        }
    }

    DisposableEffect(Unit) { onDispose { player.stop() } }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
        ) {
            if (needsFolderAgain) {
                // Folder picked by a version that only stored the path: the app cannot read it
                // without the permission that comes with the tree URI, so it is picked once more.
                Text(
                    text = stringResource(R.string.recordings_folder_reselect),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                RecordingsList(
                    recordings = recordings,
                    loading = loading,
                    showsProgress = ServerSettings.isConfigured(context),
                    selectedName = selected?.name,
                    today = today,
                    onSelect = ::open,
                    onDelete = { pendingDelete = it },
                )
            }
        }

        selected?.let { recording ->
            val index = recordings.indexOf(recording)
            PlayerSheet(
                title = recording.contact?.name ?: RecordingLabel.title(recording.name),
                subtitle = RecordingLabel.subtitle(recording.name),
                playing = playing,
                positionMillis = position,
                durationMillis = duration,
                speed = speed,
                hasPrevious = index > 0,
                hasNext = index >= 0 && index < recordings.lastIndex,
                onPlayPause = {
                    player.togglePlayPause()
                    playing = player.isPlaying
                },
                onPrevious = { step(-1) },
                onNext = { step(1) },
                onCycleSpeed = {
                    val next = PLAYBACK_SPEEDS[(PLAYBACK_SPEEDS.indexOf(speed) + 1) % PLAYBACK_SPEEDS.size]
                    player.setSpeed(next)
                    speed = next
                },
                onSeek = { millis ->
                    player.seekTo(millis)
                    position = millis
                },
                onClose = ::close,
            )
        }
    }

    pendingDelete?.let { recording ->
        ConfirmSheet(
            title = stringResource(
                R.string.delete_recording_title,
                recording.contact?.name ?: RecordingLabel.title(recording.name),
            ),
            explanation = stringResource(R.string.delete_recording_explained),
            confirmLabel = stringResource(R.string.delete_recording_confirm),
            working = deleting,
            onConfirm = {
                // The sheet stays up and locked until the list has lost it, so it never closes
                // onto a list that still shows what was just deleted.
                deleting = true
                if (selected?.name == recording.name) close()
                scope.launch {
                    onDelete(recording)
                    deleting = false
                    pendingDelete = null
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }
}
