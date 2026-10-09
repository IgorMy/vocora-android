package io.github.igormy.vocora.recorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.igormy.vocora.R
import io.github.igormy.vocora.recorder.logic.PlaybackTime
import io.github.igormy.vocora.recorder.logic.Transcript

/**
 * The player that slides up when a recording is picked.
 *
 * It stays below the list rather than over it, so the recording it is playing can be seen
 * highlighted while it runs.
 */
@Composable
fun PlayerSheet(
    title: String,
    subtitle: String,
    playing: Boolean,
    positionMillis: Int,
    durationMillis: Int,
    speed: Float,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCycleSpeed: () -> Unit,
    onSeek: (Int) -> Unit,
    onClose: () -> Unit,
    transcript: Transcript?,
    loadingTranscript: Boolean,
    modifier: Modifier = Modifier,
) {
    // Half the screen at most, so the list it was opened from stays in sight behind it.
    val tallest = LocalConfiguration.current.screenHeightDp.dp / 2

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = tallest),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) { Text("✕") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onPrevious, enabled = hasPrevious) { Text("⏮") }
                FilledTonalIconButton(onClick = onPlayPause) { Text(if (playing) "■" else "▶") }
                IconButton(onClick = onNext, enabled = hasNext) { Text("⏭") }
                TextButton(onClick = onCycleSpeed) { Text(PlaybackTime.formatSpeed(speed)) }
            }

            Slider(
                value = positionMillis.toFloat().coerceIn(0f, durationMillis.toFloat()),
                onValueChange = { onSeek(it.toInt()) },
                // A recording with no duration yet would make an empty range, which Slider rejects.
                valueRange = 0f..durationMillis.coerceAtLeast(1).toFloat(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = PlaybackTime.format(positionMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = PlaybackTime.format(durationMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                loadingTranscript -> Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.transcript_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                transcript != null -> TranscriptView(transcript)
            }
        }
    }
}

/**
 * The call as a conversation, each turn on the side of whoever said it.
 *
 * A server that only sent the text as one block is shown as one block: better that than guessing
 * who said which half of it.
 */
@Composable
private fun TranscriptView(transcript: Transcript) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    if (transcript.lines.isEmpty()) {
        Text(text = transcript.text.orEmpty(), style = MaterialTheme.typography.bodyMedium)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        transcript.lines.forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (line.fromMe) Arrangement.End else Arrangement.Start,
            ) {
                Surface(
                    color = if (line.fromMe) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(0.85f),
                ) {
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
