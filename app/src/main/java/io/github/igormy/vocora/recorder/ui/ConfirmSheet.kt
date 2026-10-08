package io.github.igormy.vocora.recorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.igormy.vocora.R

private val SPINNER_SIZE = 20.dp

/**
 * Asks before doing something that cannot be undone.
 *
 * It dims what is behind on purpose, and while [working] it cannot be left at all: the caller is
 * free to finish whatever it started before the screen underneath changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSheet(
    title: String,
    explanation: String,
    confirmLabel: String,
    working: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Read through a kept-current state and a remembered lambda: handing the sheet a fresh lambda
    // on every recomposition rebuilds its state, and the sheet vanishes the moment working flips.
    val dismissable by rememberUpdatedState(!working)
    val sheetState = rememberModalBottomSheetState(
        confirmValueChange = remember { { dismissable } },
    )

    ModalBottomSheet(
        // Nothing dismisses it while the deletion runs: not the scrim, not the back gesture, not
        // dragging it down. Leaving mid-delete would show a list that still has the recording in it.
        onDismissRequest = { if (!working) onDismiss() },
        sheetState = sheetState,
    ) {
        BackHandler(enabled = working) { /* deliberately swallowed while it works */ }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onConfirm,
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                if (working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(SPINNER_SIZE),
                        color = MaterialTheme.colorScheme.error,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(confirmLabel)
                }
            }
            TextButton(
                onClick = onDismiss,
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
