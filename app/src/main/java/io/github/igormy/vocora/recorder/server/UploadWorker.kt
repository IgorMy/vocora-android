package io.github.igormy.vocora.recorder.server

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.igormy.vocora.recorder.logic.UploadState
import io.github.igormy.vocora.recorder.store.VocoraDatabase

/**
 * Sends whatever has not been sent, oldest first, and stops at the first sign the server cannot
 * take it.
 *
 * Stopping rather than carrying on matters: the server takes ten uploads a minute, and a server
 * that is down is down for all of them. WorkManager brings the whole run back later, by which time
 * the ones already sent are no longer waiting.
 */
class UploadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        // Nothing to send to. Succeeding rather than retrying keeps the queue quiet until there is.
        if (!ServerSettings.isConfigured(context)) return Result.success()

        val recordings = VocoraDatabase.of(context).recordings()
        for (recording in recordings.toUpload()) {
            recordings.setUploadState(recording.folder, UploadState.UPLOADING)
            when (VocoraServer.upload(context, recording)) {
                UploadOutcome.DONE ->
                    recordings.setUploadState(recording.folder, UploadState.UPLOADED)

                UploadOutcome.REFUSED ->
                    recordings.setUploadState(recording.folder, UploadState.FAILED)

                UploadOutcome.LATER -> {
                    recordings.setUploadState(recording.folder, UploadState.PENDING)
                    return Result.retry()
                }
            }
        }

        // Uploading answers with nothing, so what became of a recording has to be asked for.
        ServerSync.refresh(context)
        return Result.success()
    }
}
