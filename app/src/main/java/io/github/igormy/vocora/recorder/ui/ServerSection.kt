package io.github.igormy.vocora.recorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.igormy.vocora.R

/** Whether the server has been asked, and what it said. */
enum class ServerAnswer { UNKNOWN, ASKING, ALIVE, SILENT }

/** Where recordings are sent to be transcribed, and over which network. */
@Composable
fun ServerSection(
    url: String,
    token: String,
    wifiOnly: Boolean,
    answer: ServerAnswer,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tokenShown by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.server_title),
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.server_url)) },
            placeholder = { Text(stringResource(R.string.server_url_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )

        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.server_token)) },
            // Hidden by default, shown on demand: it has to be pasted and checked at least once.
            visualTransformation =
                if (tokenShown) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { tokenShown = !tokenShown }) {
                    Text(
                        stringResource(
                            if (tokenShown) R.string.server_token_hide else R.string.server_token_show,
                        ),
                    )
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.server_wifi_only))
                Text(
                    text = stringResource(R.string.server_wifi_only_explained),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = wifiOnly, onCheckedChange = onWifiOnlyChange)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                // Nothing to ask without an address to ask it of.
                enabled = url.isNotBlank() && answer != ServerAnswer.ASKING,
                onClick = onCheck,
            ) {
                Text(stringResource(R.string.server_check))
            }
            if (answer == ServerAnswer.ASKING) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            Text(
                text = stringResource(
                    when (answer) {
                        ServerAnswer.ALIVE -> R.string.server_alive
                        ServerAnswer.SILENT -> R.string.server_silent
                        ServerAnswer.ASKING -> R.string.server_asking
                        ServerAnswer.UNKNOWN -> R.string.server_unknown
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = when (answer) {
                    ServerAnswer.SILENT -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
