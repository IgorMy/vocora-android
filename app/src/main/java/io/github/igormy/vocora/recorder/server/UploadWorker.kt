package io.github.igormy.vocora.recorder.server

import android.content.Context
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.igormy.vocora.recorder.logic.UploadState
import io.github.igormy.vocora.recorder.store.VocoraDatabase
import kotlinx.coroutines.delay

/** How long to hold off after the server says the uploads are coming too fast. */
private const val PACE_WAIT_MILLIS = 60_000L

/**
 * How long a run may go on for.
 *
 * WorkManager stops a worker after ten minutes, so it stops itself before that and asks to be
 * brought back, rather than being cut off in the middle of sending something.
 */
private const val BUDGET_MILLIS = 8 * 60 * 1000L

/**
 * Sends whatever has not been sent, oldest first, and asks the server what became of all of it.
 *
 * Asking comes first as well as last. A backlog can take several runs to clear, and until it does
 * there is nothing to tell the list about recordings the server has already transcribed.
 *
 * Being told to slow down is not a failure: the server takes ten uploads a minute, so a run waits
 * out the minute and carries on with the same recording. Only a server that cannot be reached, or
 * one in no state to answer, ends the run and leaves the rest to WorkManager.
 */
class UploadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        // Nothing to send to. Succeeding rather than retrying keeps the queue quiet until there is.
        if (!ServerSettings.isConfigured(context)) return Result.success()

        // Done before anything is sent: a new address means none of these have been sent anywhere.
        ServerSync.forgetOtherServer(context)
        ServerSync.refresh(context)

        val startedAt = SystemClock.elapsedRealtime()
        val recordings = VocoraDatabase.of(context).recordings()

        for (recording in recordings.toUpload()) {
            if (SystemClock.elapsedRealtime() - startedAt > BUDGET_MILLIS) return Result.retry()
            recordings.setUploadState(recording.folder, UploadState.UPLOADING)

            var outcome = VocoraServer.upload(context, recording)
            while (outcome == UploadOutcome.PACED &&
                SystemClock.elapsedRealtime() - startedAt < BUDGET_MILLIS
            ) {
                delay(PACE_WAIT_MILLIS)
                outcome = VocoraServer.upload(context, recording)
            }

            when (outcome) {
                UploadOutcome.DONE ->
                    recordings.setUploadState(recording.folder, UploadState.UPLOADED)

                UploadOutcome.REFUSED ->
                    recordings.setUploadState(recording.folder, UploadState.FAILED)

                // Out of time rather than out of luck, or the server is not there. Either way it
                // goes back to waiting and the rest of the queue is somebody else's turn.
                UploadOutcome.PACED, UploadOutcome.LATER -> {
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
