package io.github.igormy.vocora.recorder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.igormy.vocora.R
import io.github.igormy.vocora.recorder.client.Recording
import io.github.igormy.vocora.recorder.logic.PlaybackTime
import io.github.igormy.vocora.recorder.logic.RecordingDay
import io.github.igormy.vocora.recorder.logic.RecordingLabel
import io.github.igormy.vocora.recorder.logic.RecordingProgress

private val SPINNER_SIZE = 20.dp

/** The recorded calls, newest first and grouped by day. Tapping one opens it in the player below. */
@Composable
fun RecordingsList(
    recordings: List<Recording>,
    loading: Boolean,
    showsProgress: Boolean,
    selectedName: String?,
    today: Long,
    onSelect: (Recording) -> Unit,
    onDelete: (Recording) -> Unit,
    onSendAgain: (Recording) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Reading durations and matching numbers against the address book takes a moment, and saying
    // there is nothing while it happens reads as an empty list rather than as work in progress.
    if (loading) {
        Row(
            modifier = modifier.padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(SPINNER_SIZE), strokeWidth = 2.dp)
            Text(
                text = stringResource(R.string.recordings_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    if (recordings.isEmpty()) {
        Text(
            text = stringResource(R.string.recordings_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(vertical = 16.dp),
        )
        return
    }

    // Grouped before the list is built, not while it is: a lazy list composes its items on demand
    // and in no fixed order, so deciding where a day starts from inside it puts headers anywhere.
    val byDay = recordings.groupBy { it.day }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        byDay.forEach { (day, ofThatDay) ->
            item(key = "day-$day") { DayHeader(RecordingDay.header(day, today)) }
            items(ofThatDay, key = { it.folderUri.toString() }) { recording ->
                RecordingRow(
                    recording = recording,
                    showsProgress = showsProgress,
                    selected = recording.name == selectedName,
                    onSelect = { onSelect(recording) },
                    onDelete = { onDelete(recording) },
                    onSendAgain = { onSendAgain(recording) },
                )
            }
        }
    }
}

@Composable
private fun DayHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun RecordingRow(
    recording: Recording,
    showsProgress: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onSendAgain: () -> Unit,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ContactAvatar(
                photoUri = recording.contact?.photoUri?.toString(),
                label = recording.contact?.name ?: recording.number ?: "?",
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = recording.contact?.name ?: RecordingLabel.title(recording.name),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // Only worth repeating when the name above is not the number already.
                    recording.contact?.let {
                        Text(
                            text = recording.number.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = subtitleOf(recording),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Silent until there is a server: without one, every recording would say the same
                // thing on every line, which is not news.
                if (showsProgress) {
                    Text(
                        text = stringResource(labelOf(recording.progress)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (recording.progress == RecordingProgress.Shown.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }

            // Only offered when there is a server to send it to, like the line above it.
            if (showsProgress) {
                val label = stringResource(R.string.send_again)
                IconButton(
                    onClick = onSendAgain,
                    modifier = Modifier.semantics { contentDescription = label },
                ) {
                    Text(
                        text = "\u21BB",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.delete_recording_confirm),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun labelOf(progress: RecordingProgress.Shown): Int = when (progress) {
    RecordingProgress.Shown.NOT_UPLOADED -> R.string.progress_not_uploaded
    RecordingProgress.Shown.UPLOADING -> R.string.progress_uploading
    RecordingProgress.Shown.UPLOADED -> R.string.progress_uploaded
    RecordingProgress.Shown.WAITING -> R.string.progress_waiting
    RecordingProgress.Shown.TRANSCRIBING -> R.string.progress_transcribing
    RecordingProgress.Shown.DONE -> R.string.progress_done
    RecordingProgress.Shown.FAILED -> R.string.progress_failed
}

private fun subtitleOf(recording: Recording): String {
    val time = RecordingLabel.timeOfDay(recording.name)
    val direction = RecordingLabel.direction(recording.name)
    val duration = PlaybackTime.format(recording.durationMillis)
    return listOfNotNull(time, direction, duration).joinToString(" · ")
}

