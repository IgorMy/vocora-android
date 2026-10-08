package io.github.igormy.vocora.recorder.ui

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.igormy.vocora.R
import io.github.igormy.vocora.recorder.client.CallRecorder
import io.github.igormy.vocora.recorder.client.Recording
import io.github.igormy.vocora.recorder.client.RecorderStatus
import io.github.igormy.vocora.recorder.client.RecordingsFolder
import io.github.igormy.vocora.recorder.client.RecordingsLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private const val TAB_RECORDINGS = 0
private const val TAB_SETTINGS = 1

/**
 * The whole app once Shizuku is working, in two tabs: what Vocora does, and what it has recorded.
 *
 * The state they share lives here because both tabs move it: picking a folder changes what can be
 * listed, and recording changes what there is to list.
 */
@Composable
fun RecorderScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(TAB_RECORDINGS) }
    var granted by remember { mutableStateOf(CallRecorder.hasPermission()) }
    var folder by remember { mutableStateOf(RecordingsFolder.path(context)) }
    var enabled by remember { mutableStateOf(CallRecorder.isEnabled(context)) }
    var status by remember { mutableStateOf(RecorderStatus()) }
    var recordings by remember { mutableStateOf(emptyList<Recording>()) }
    var recordingsRead by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Listing reads durations and the address book, which is too much for the main thread. It is
    // suspending so that whoever changed something can wait for the list to catch up.
    suspend fun reloadRecordings() {
        recordings = withContext(Dispatchers.IO) { RecordingsLibrary.list(context) }
        recordingsRead = true
    }

    fun refresh() {
        scope.launch { reloadRecordings() }
        scope.launch {
            runCatching { CallRecorder.syncWithPreference(context) }
                .onSuccess { status = it; error = null }
                .onFailure { error = it.message }
        }
    }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (RecordingsFolder.set(context, uri)) {
            folder = RecordingsFolder.path(context)
            error = null
            refresh()
        } else {
            error = context.getString(R.string.recorder_folder_unsupported)
        }
    }

    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, result ->
            granted = result == PackageManager.PERMISSION_GRANTED
        }
        Shizuku.addRequestPermissionResultListener(listener)
        onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    // The recorder keeps running without the app, so its state is read back on every resume.
    LifecycleResumeEffect(granted) {
        if (granted) refresh()
        onPauseOrDispose { }
    }

    if (!granted) {
        PermissionPrompt(modifier)
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == TAB_RECORDINGS,
                onClick = { selectedTab = TAB_RECORDINGS },
                text = { Text(stringResource(R.string.tab_recordings)) },
            )
            Tab(
                selected = selectedTab == TAB_SETTINGS,
                onClick = { selectedTab = TAB_SETTINGS },
                text = { Text(stringResource(R.string.tab_settings)) },
            )
        }

        when (selectedTab) {
            TAB_RECORDINGS -> RecordingsTab(
                recordings = recordings,
                loading = !recordingsRead,
                needsFolderAgain = folder != null && RecordingsFolder.treeUri(context) == null,
                onChanged = ::reloadRecordings,
            )

            else -> SettingsTab(
                folder = folder,
                enabled = enabled,
                status = status,
                error = error,
                onPickFolder = { pickFolder.launch(null) },
                onEnabledChange = { wanted ->
                    enabled = wanted
                    scope.launch {
                        runCatching {
                            if (wanted) CallRecorder.enable(context) else CallRecorder.disable(context)
                        }
                            .onSuccess { status = it; error = null }
                            .onFailure { error = it.message; enabled = !wanted }
                    }
                },
            )
        }
    }
}

@Composable
private fun PermissionPrompt(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.recorder_permission_explained),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(onClick = { CallRecorder.requestPermission() }) {
            Text(stringResource(R.string.recorder_grant))
        }
    }
}
