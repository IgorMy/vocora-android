package io.github.igormy.vocora.recorder

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.igormy.vocora.R
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * Temporary 0.2.0 smoke test: records a few seconds of the ongoing call through Shizuku and reports
 * whether real audio came out. Goes away once call detection drives the recorder.
 */
@Composable
fun SmokeTestPanel(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(CallRecorder.hasPermission()) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            granted = grantResult == PackageManager.PERMISSION_GRANTED
        }
        Shizuku.addRequestPermissionResultListener(listener)
        onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!granted) {
            Button(onClick = { CallRecorder.requestPermission() }) {
                Text(stringResource(R.string.smoke_test_grant))
            }
        } else {
            Button(
                enabled = !running,
                onClick = {
                    scope.launch {
                        running = true
                        result = CallRecorder.record()
                        running = false
                    }
                },
            ) {
                Text(stringResource(R.string.smoke_test_run, SMOKE_TEST_SECONDS))
            }
        }

        if (running) {
            Text(
                text = stringResource(R.string.smoke_test_running),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        result?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall)
        }
    }
}
