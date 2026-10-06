package io.github.igormy.vocora.recorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.igormy.vocora.R
import io.github.igormy.vocora.recorder.client.RecorderStatus

/** Where recordings go and whether calls are recorded at all. */
@Composable
fun SettingsTab(
    folder: String?,
    enabled: Boolean,
    status: RecorderStatus,
    error: String?,
    onPickFolder: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onPickFolder) {
                Text(stringResource(R.string.recorder_choose_folder))
            }
            Text(
                text = folder ?: stringResource(R.string.recorder_folder_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

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
                onCheckedChange = onEnabledChange,
            )
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
