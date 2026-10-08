package io.github.igormy.vocora.recorder.shell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.util.Log

private const val TAG = "VocoraRecorder"

/**
 * A channel's importance cannot be changed once it exists, so raising it needs a new id each time.
 *
 * Importance is what decides whether the action is reachable. Below high, the notification stays
 * folded in the shade and its button with it; at high it appears as a banner, where the button is
 * drawn. That is why a messaging app can be replied to from the notification and this could not.
 */
private const val CHANNEL_ID = "vocora-recording-heads-up"
private const val CHANNEL_NAME = "Call recording"
private const val NOTIFICATION_ID = 2026

/**
 * CallStyle, the template that keeps its buttons visible on the lock screen, is not an option here:
 * the system refuses it outright unless the notification belongs to a foreground service, a user
 * initiated job, or carries a full screen intent, and a process started by Shizuku has none of them.
 * Attempting it means no notification at all, not a plainer one.
 *
 * Shows that a call is being recorded, from the recorder process itself, because the app is usually
 * dead while a call is recorded. It is posted as `com.android.shell`, the package this process runs
 * as, so the system attributes it to the shell rather than to Vocora, and its icon has to be a
 * framework one for the same reason.
 */
class RecordingNotifier(private val context: Context) {

    private val manager: NotificationManager? by lazy {
        runCatching { context.getSystemService(NotificationManager::class.java) }.getOrNull()
    }

    /**
     * What the cancel button fires, built by the app and handed over.
     *
     * This process cannot receive a broadcast of its own: the ActivityManager does not know it as an
     * app process, so a receiver registered here is never delivered to. Verified, not assumed.
     */
    var cancelAction: PendingIntent? = null

    fun show() {
        val manager = manager ?: return
        try {
            manager.createNotificationChannel(
                // High so the banner shows with its button, silent so it does not interrupt the call.
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        setSound(null, null)
                        enableVibration(false)
                    },
            )
            val builder = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(Icon.createWithResource("android", android.R.drawable.ic_btn_speak_now))
                .setContentTitle("Vocora")
                // Short on purpose: a second line pushes the action out of the collapsed card.
                .setContentText("Recording call")
                // Not ongoing: HyperOS draws ongoing notifications compact and drops their actions
                // when expanded. Being dismissable costs only the indicator, never the recording.
                .setOngoing(false)
                .setShowWhen(true)

            cancelAction?.let { builder.addAction(cancelButton(it)) }
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Log.i(TAG, "could not show the recording notification: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun hide() {
        runCatching { manager?.cancel(NOTIFICATION_ID) }
    }

    private fun cancelButton(intent: PendingIntent): Notification.Action = Notification.Action.Builder(
        Icon.createWithResource("android", android.R.drawable.ic_menu_close_clear_cancel),
        "Cancel recording",
        intent,
    ).build()
}
