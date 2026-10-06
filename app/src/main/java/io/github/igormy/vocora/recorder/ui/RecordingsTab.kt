package io.github.igormy.vocora.recorder.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.igormy.vocora.R
import io.github.igormy.vocora.recorder.client.Recording

/** What has been recorded, newest first. */
@Composable
fun RecordingsTab(
    recordings: List<Recording>,
    playingName: String?,
    progress: Float,
    needsFolderAgain: Boolean,
    onPlay: (Recording) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        if (needsFolderAgain) {
            // Folder picked by a version that only stored the path: the app cannot read it without
            // the permission that comes with the tree URI, so it has to be picked once more.
            Text(
                text = stringResource(R.string.recordings_folder_reselect),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        RecordingsList(
            recordings = recordings,
            playingName = playingName,
            progress = progress,
            onPlay = onPlay,
        )
    }
}
