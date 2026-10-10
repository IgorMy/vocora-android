package io.github.igormy.vocora.recorder.server

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "vocora-uploads"

/**
 * Asks for the queue to run. It survives the app being closed and the phone being restarted, which
 * is the whole reason it is WorkManager and not a coroutine.
 *
 * What it cannot survive is the app never being opened: uploads happen in the app process, so a
 * call recorded with the app dead waits until something wakes it.
 */
object UploadQueue {

    /**
     * [restart] replaces a run that is already going, which is what a change of settings needs:
     * a new address or a switch to Wi-Fi only should not wait for the old run to finish.
     */
    /** Drops whatever is queued or running. What was already sent stays sent. */
    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun ask(context: Context, restart: Boolean = false) {
        val work = WorkManager.getInstance(context)
        // Without an address and a token there is nothing to try, and trying would only fail.
        if (!ServerSettings.isConfigured(context)) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }

        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (ServerSettings.wifiOnly(context)) {
                            NetworkType.UNMETERED
                        } else {
                            NetworkType.CONNECTED
                        },
                    )
                    .build(),
            )
            // A server that is down tends to stay down for a while, so the waits grow.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        work.enqueueUniqueWork(
            WORK_NAME,
            if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
