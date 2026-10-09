package io.github.igormy.vocora.recorder.ui

import android.content.Intent
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.igormy.vocora.R
import io.github.igormy.vocora.recorder.client.Blacklist
import io.github.igormy.vocora.recorder.client.BlacklistEntry
import io.github.igormy.vocora.recorder.client.CallNotificationListener
import io.github.igormy.vocora.recorder.client.CallRecorder
import io.github.igormy.vocora.recorder.client.ContactLookup
import io.github.igormy.vocora.recorder.client.RecorderStatus
import io.github.igormy.vocora.recorder.server.ServerSettings
import io.github.igormy.vocora.recorder.server.UploadQueue
import io.github.igormy.vocora.recorder.server.VocoraServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var blacklist by remember { mutableStateOf(emptyList<BlacklistEntry>()) }
    var typedNumber by remember { mutableStateOf("") }
    var pendingRemoval by remember { mutableStateOf<BlacklistEntry?>(null) }
    var identifiesCalls by remember { mutableStateOf(CallNotificationListener.isEnabled(context)) }
    var serverUrl by remember { mutableStateOf(ServerSettings.typedUrl(context)) }
    var serverToken by remember { mutableStateOf(ServerSettings.typedToken(context)) }
    var wifiOnly by remember { mutableStateOf(ServerSettings.wifiOnly(context)) }
    var serverAnswer by remember { mutableStateOf(ServerAnswer.UNKNOWN) }

    // Sent to the recorder right away, so an edit applies to the very next call rather than to the
    // next time the app happens to be opened.
    fun blacklistChanged() {
        scope.launch {
            // Resolving names and photos is a query per number, so it stays off the main thread.
            blacklist = withContext(Dispatchers.IO) { Blacklist.entriesWithContacts(context) }
            runCatching { CallRecorder.updateBlacklist(context) }
        }
    }

    LaunchedEffect(Unit) {
        blacklist = withContext(Dispatchers.IO) { Blacklist.entriesWithContacts(context) }
    }

    // Granting happens in the system settings, so the answer only arrives on coming back.
    LifecycleResumeEffect(Unit) {
        identifiesCalls = CallNotificationListener.isEnabled(context)
        onPauseOrDispose { }
    }

    val pickContact = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val picked = result.data?.data ?: return@rememberLauncherForActivityResult
        ContactLookup.pickedPhone(context, picked)?.let { (number, name) ->
            Blacklist.add(context, number, name)
            blacklistChanged()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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

        HorizontalDivider()

        ServerSection(
            url = serverUrl,
            token = serverToken,
            wifiOnly = wifiOnly,
            answer = serverAnswer,
            // Written as they are typed: there is no save button, and a half typed address only
            // means the next check fails, which is what the check is there to say.
            onUrlChange = {
                serverUrl = it
                ServerSettings.setUrl(context, it)
                serverAnswer = ServerAnswer.UNKNOWN
            },
            onTokenChange = {
                serverToken = it
                ServerSettings.setToken(context, it)
            },
            onWifiOnlyChange = {
                wifiOnly = it
                ServerSettings.setWifiOnly(context, it)
                // Restarted rather than left alone: a run waiting on mobile data has to be told.
                UploadQueue.ask(context, restart = true)
            },
            onCheck = {
                serverAnswer = ServerAnswer.ASKING
                scope.launch {
                    serverAnswer = if (VocoraServer.isAlive(context)) {
                        // A working address is the moment to try whatever was waiting for one.
                        UploadQueue.ask(context, restart = true)
                        ServerAnswer.ALIVE
                    } else {
                        ServerAnswer.SILENT
                    }
                }
            },
        )

        HorizontalDivider()

        BlacklistSection(
            entries = blacklist,
            identifiesCalls = identifiesCalls,
            onGrantIdentify = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            typedNumber = typedNumber,
            onTypedNumberChange = { typedNumber = it },
            onAddTyped = {
                Blacklist.add(context, typedNumber, null)
                typedNumber = ""
                blacklistChanged()
            },
            onPickContact = {
                // By MIME type rather than by data URI: the address book declares itself for the
                // type, and asking by URI left the system offering a file manager instead.
                pickContact.launch(
                    Intent(Intent.ACTION_PICK).setType(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE,
                    ),
                )
            },
            onRemove = { entry -> pendingRemoval = entry },
        )
    }

    pendingRemoval?.let { entry ->
        ConfirmSheet(
            title = stringResource(R.string.blacklist_remove_title, entry.shown),
            explanation = stringResource(R.string.blacklist_remove_explained),
            confirmLabel = stringResource(R.string.blacklist_remove_confirm),
            // Removing is a preference write, over before the sheet could show it was busy.
            working = false,
            onConfirm = {
                Blacklist.remove(context, entry)
                pendingRemoval = null
                blacklistChanged()
            },
            onDismiss = { pendingRemoval = null },
        )
    }
}
