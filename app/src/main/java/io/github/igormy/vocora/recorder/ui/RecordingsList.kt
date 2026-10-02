package io.github.igormy.vocora.recorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.igormy.vocora.R
import io.github.igormy.vocora.recorder.client.Recording
import io.github.igormy.vocora.recorder.logic.RecordingLabel

/** The recordings in the chosen folder, newest first, each with a play button. */
@Composable
fun RecordingsList(
    recordings: List<Recording>,
    playingName: String?,
    progress: Float,
    onPlay: (Recording) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (recordings.isEmpty()) {
        Text(
            text = stringResource(R.string.recordings_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 16.dp),
        )
        return
    }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(recordings, key = { it.mixedUri.toString() }) { recording ->
            RecordingRow(
                recording = recording,
                playing = recording.name == playingName,
                progress = progress,
                onPlay = { onPlay(recording) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun RecordingRow(
    recording: Recording,
    playing: Boolean,
    progress: Float,
    onPlay: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalIconButton(onClick = onPlay) {
                Text(if (playing) "■" else "▶")
            }
            Column {
                Text(
                    text = RecordingLabel.title(recording.name),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = RecordingLabel.subtitle(recording.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (playing) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}
