package io.github.igormy.vocora.shizuku

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.igormy.vocora.R
import io.github.igormy.vocora.ui.theme.VocoraTheme

/** Tells the user whether Shizuku is ready, and how to fix it when it is not. */
@Composable
fun ShizukuStatusScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state = rememberShizukuStatus()

    ShizukuStatusContent(
        status = state.status,
        onPrimaryAction = {
            when (state.status) {
                ShizukuStatus.NotInstalled -> openShizukuWebsite(context)
                // Shizuku may have been uninstalled since the last check.
                ShizukuStatus.NotRunning -> if (!openShizukuApp(context)) state.refresh()
                ShizukuStatus.Running -> Unit
            }
        },
        onRetry = state::refresh,
        modifier = modifier,
    )
}

@Composable
private fun ShizukuStatusContent(
    status: ShizukuStatus,
    onPrimaryAction: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleRes = when (status) {
        ShizukuStatus.NotInstalled -> R.string.shizuku_not_installed_title
        ShizukuStatus.NotRunning -> R.string.shizuku_not_running_title
        ShizukuStatus.Running -> R.string.shizuku_running_title
    }
    val messageRes = when (status) {
        ShizukuStatus.NotInstalled -> R.string.shizuku_not_installed_message
        ShizukuStatus.NotRunning -> R.string.shizuku_not_running_message
        ShizukuStatus.Running -> R.string.shizuku_running_message
    }
    val actionRes = when (status) {
        ShizukuStatus.NotInstalled -> R.string.shizuku_action_install
        ShizukuStatus.NotRunning -> R.string.shizuku_action_open
        ShizukuStatus.Running -> null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            color = if (status == ShizukuStatus.Running) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionRes != null) {
            Button(onClick = onPrimaryAction) { Text(stringResource(actionRes)) }
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.shizuku_action_retry)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ShizukuNotInstalledPreview() {
    VocoraTheme {
        ShizukuStatusContent(ShizukuStatus.NotInstalled, onPrimaryAction = {}, onRetry = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ShizukuNotRunningPreview() {
    VocoraTheme {
        ShizukuStatusContent(ShizukuStatus.NotRunning, onPrimaryAction = {}, onRetry = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun ShizukuRunningPreview() {
    VocoraTheme {
        ShizukuStatusContent(ShizukuStatus.Running, onPrimaryAction = {}, onRetry = {})
    }
}
