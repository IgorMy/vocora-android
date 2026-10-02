package io.github.igormy.vocora.recorder.shell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.util.Log

private const val TAG = "VocoraRecorder"

private const val CHANNEL_ID = "vocora-recording"
private const val CHANNEL_NAME = "Call recording"
private const val NOTIFICATION_ID = 2026

/**
 * Shows that a call is being recorded, from the recorder process itself.
 *
 * It has to come from here and not from the app: the app is usually dead while a call is recorded.
 * The notification is posted as `com.android.shell`, the package this process runs as, so the system
 * shows it as coming from the shell rather than from Vocora. The icon is a framework one for the
 * same reason, as resources of this APK cannot be resolved against the posting package.
 */
class RecordingNotifier(private val context: Context) {

    private val manager: NotificationManager? by lazy {
        runCatching { context.getSystemService(NotificationManager::class.java) }.getOrNull()
    }

    fun show(fileName: String) {
        val manager = manager ?: return
        try {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW),
            )
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(Icon.createWithResource("android", android.R.drawable.ic_btn_speak_now))
                .setContentTitle("Vocora")
                .setContentText("Recording call into $fileName")
                .setOngoing(true)
                .setShowWhen(true)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.i(TAG, "could not show the recording notification: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun hide() {
        runCatching { manager?.cancel(NOTIFICATION_ID) }
    }
}
