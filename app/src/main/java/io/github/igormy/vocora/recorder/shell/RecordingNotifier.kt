package io.github.igormy.vocora.recorder.shell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.util.Log

private const val TAG = "VocoraRecorder"

private const val CHANNEL_ID = "vocora-recording"
private const val CHANNEL_NAME = "Call recording"
private const val NOTIFICATION_ID = 2026

/** Sent by the notification action. Handled in this process, so the app does not have to wake up. */
private const val ACTION_CANCEL = "io.github.igormy.vocora.CANCEL_RECORDING"

/**
 * Shows that a call is being recorded, from the recorder process itself.
 *
 * It has to come from here and not from the app: the app is usually dead while a call is recorded.
 * The notification is posted as `com.android.shell`, the package this process runs as, so the system
 * shows it as coming from the shell rather than from Vocora. The icon is a framework one for the
 * same reason, as resources of this APK cannot be resolved against the posting package.
 */
class RecordingNotifier(private val context: Context, private val onCancel: () -> Unit) {

    private val manager: NotificationManager? by lazy {
        runCatching { context.getSystemService(NotificationManager::class.java) }.getOrNull()
    }

    private var receiver: BroadcastReceiver? = null

    fun show(fileName: String) {
        val manager = manager ?: return
        try {
            registerCancelReceiver()
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW),
            )
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(Icon.createWithResource("android", android.R.drawable.ic_btn_speak_now))
                .setContentTitle("Vocora")
                .setContentText("Recording call into $fileName")
                .setOngoing(true)
                .setShowWhen(true)
                .addAction(cancelAction())
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.i(TAG, "could not show the recording notification: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun hide() {
        runCatching { manager?.cancel(NOTIFICATION_ID) }
    }

    private fun cancelAction(): Notification.Action {
        val intent = Intent(ACTION_CANCEL).setPackage(context.packageName)
        val pending = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(
            Icon.createWithResource("android", android.R.drawable.ic_menu_close_clear_cancel),
            "Cancel recording",
            pending,
        ).build()
    }

    /** Registered once and kept, so the action works for every call this process records. */
    private fun registerCancelReceiver() {
        if (receiver != null) return
        val cancelReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_CANCEL) onCancel()
            }
        }
        try {
            context.registerReceiver(
                cancelReceiver,
                IntentFilter(ACTION_CANCEL),
                Context.RECEIVER_NOT_EXPORTED,
            )
            receiver = cancelReceiver
        } catch (e: Exception) {
            Log.w(TAG, "the cancel action will not work: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
