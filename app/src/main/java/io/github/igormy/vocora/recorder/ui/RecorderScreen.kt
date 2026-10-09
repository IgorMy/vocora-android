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
import androidx.compose.runtime.LaunchedEffect
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
import io.github.igormy.vocora.recorder.store.RecordingIndex
import kotlinx.coroutines.launch
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
    var recordings by remember { mutableStateOf<List<Recording>?>(null) }
    var reconciling by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // The database says what there is as soon as it is asked, and keeps saying it: every change
    // made here comes back through this, so nothing has to re-read the list by hand.
    LaunchedEffect(Unit) {
        RecordingIndex.stream(context).collect { recordings = it }
    }

    fun refresh() {
        scope.launch {
            reconciling = true
            RecordingIndex.reconcile(context)
            reconciling = false
        }
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

        val listed = recordings
        when (selectedTab) {
            TAB_RECORDINGS -> RecordingsTab(
                recordings = listed.orEmpty(),
                // Nothing to show is only worth saying once the disk has been looked at.
                loading = listed == null || (listed.isEmpty() && reconciling),
                needsFolderAgain = folder != null && RecordingsFolder.treeUri(context) == null,
                onDelete = { recording ->
                    RecordingIndex.delete(context, recording)
                    // Removed here as well so the list has already lost it by the time the sheet
                    // closes, instead of waiting for the database to say so.
                    recordings = recordings?.filterNot { it.name == recording.name }
                },
                onContactsGranted = {
                    RecordingIndex.forgetContacts()
                    recordings = RecordingIndex.read(context)
                },
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
