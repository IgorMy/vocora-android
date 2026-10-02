package io.github.igormy.vocora.shizuku

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
                ShizukuStatus.NotInstalled -> openUrl(context, SHIZUKU_WEBSITE)
                // Shizuku may have been uninstalled since the last check.
                ShizukuStatus.CannotStart -> if (!openShizukuApp(context)) state.refresh()
                ShizukuStatus.Starting, ShizukuStatus.Running -> Unit
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
        ShizukuStatus.Starting -> R.string.shizuku_starting_title
        ShizukuStatus.Running -> R.string.shizuku_running_title
        ShizukuStatus.CannotStart -> R.string.shizuku_cannot_start_title
    }
    val messageRes = when (status) {
        ShizukuStatus.NotInstalled -> R.string.shizuku_not_installed_message
        ShizukuStatus.Starting -> R.string.shizuku_starting_message
        ShizukuStatus.Running -> R.string.shizuku_running_message
        ShizukuStatus.CannotStart -> R.string.shizuku_cannot_start_message
    }
    val actionRes = when (status) {
        ShizukuStatus.NotInstalled -> R.string.shizuku_action_install
        ShizukuStatus.CannotStart -> R.string.shizuku_action_open
        ShizukuStatus.Starting, ShizukuStatus.Running -> null
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
            color = when (status) {
                ShizukuStatus.NotInstalled, ShizukuStatus.CannotStart -> MaterialTheme.colorScheme.error
                ShizukuStatus.Starting -> MaterialTheme.colorScheme.onSurface
                ShizukuStatus.Running -> MaterialTheme.colorScheme.primary
            },
        )
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (status == ShizukuStatus.Starting) {
            CircularProgressIndicator()
        }
        if (actionRes != null) {
            Button(onClick = onPrimaryAction) { Text(stringResource(actionRes)) }
        }
        // Once Shizuku is installed the screen follows the service on its own, so retrying is only
        // useful while the app is still missing.
        if (status == ShizukuStatus.NotInstalled) {
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.shizuku_action_retry)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ShizukuNotInstalledPreview() {
    VocoraTheme { ShizukuStatusPreview(ShizukuStatus.NotInstalled) }
}

@Preview(showBackground = true)
@Composable
private fun ShizukuStartingPreview() {
    VocoraTheme { ShizukuStatusPreview(ShizukuStatus.Starting) }
}

@Preview(showBackground = true)
@Composable
private fun ShizukuCannotStartPreview() {
    VocoraTheme { ShizukuStatusPreview(ShizukuStatus.CannotStart) }
}

@Preview(showBackground = true)
@Composable
private fun ShizukuRunningPreview() {
    VocoraTheme { ShizukuStatusPreview(ShizukuStatus.Running) }
}

@Composable
private fun ShizukuStatusPreview(status: ShizukuStatus) {
    ShizukuStatusContent(status, onPrimaryAction = {}, onRetry = {})
}
