package io.github.igormy.vocora.recorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.igormy.vocora.R
import io.github.igormy.vocora.recorder.client.BlacklistEntry

private val SMALL_AVATAR = 36.dp

/** Who not to record. Numbers can be typed, or picked from the address book. */
@Composable
fun BlacklistSection(
    entries: List<BlacklistEntry>,
    typedNumber: String,
    onTypedNumberChange: (String) -> Unit,
    onAddTyped: () -> Unit,
    onPickContact: () -> Unit,
    onRemove: (BlacklistEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.blacklist_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.blacklist_explained),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = typedNumber,
                onValueChange = onTypedNumberChange,
                singleLine = true,
                label = { Text(stringResource(R.string.blacklist_number)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAddTyped, enabled = typedNumber.isNotBlank()) {
                Text(stringResource(R.string.blacklist_add))
            }
        }

        OutlinedButton(onClick = onPickContact) {
            Text(stringResource(R.string.blacklist_pick_contact))
        }

        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.blacklist_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            return@Column
        }

        entries.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ContactAvatar(
                    photoUri = entry.contact?.photoUri?.toString(),
                    label = entry.shown,
                    size = SMALL_AVATAR,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.shown,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Only worth repeating when the line above is a name rather than the number.
                    (entry.contact?.name ?: entry.label)?.let {
                        Text(
                            text = entry.number,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { onRemove(entry) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.blacklist_remove),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}
