package io.github.igormy.vocora.recorder.client

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import io.github.igormy.vocora.recorder.logic.CallIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "VocoraRecorder"

/**
 * Reads who a call is with from the dialer's own notification.
 *
 * Nothing else says it while the call is happening: the call log writes its row at hang up, telecom
 * redacts the number in its dump, and Android stopped handing out the incoming number years ago.
 * The dialer, however, has to show the caller, and its notification carries them as a Person.
 *
 * Listening to notifications is a permission the user grants by hand, so without it Vocora simply
 * falls back to recording the call and dropping it afterwards.
 */
class CallNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(notification: StatusBarNotification?) {
        val identity = notification?.let(::identityOf) ?: return
        Log.i(TAG, "call notification: number=${identity.number} name=${identity.name}")
        scope.launch {
            runCatching { CallRecorder.setCurrentCall(identity.number, identity.name) }
        }
    }

    override fun onNotificationRemoved(notification: StatusBarNotification?) {
        if (notification == null || !isCallNotification(notification)) return
        scope.launch { runCatching { CallRecorder.setCurrentCall(null, null) } }
    }

    private fun identityOf(notification: StatusBarNotification): CallIdentity? {
        if (!isCallNotification(notification)) return null
        val extras = notification.notification.extras

        // The person is the structured version of what the card shows, so it is tried first.
        val person = extras.getParcelable(Notification.EXTRA_CALL_PERSON, android.app.Person::class.java)
        val number = person?.uri?.removePrefix("tel:")?.takeIf { it.isNotBlank() }
        val name = (person?.name ?: extras.getCharSequence(Notification.EXTRA_TITLE))
            ?.toString()
            ?.takeIf { it.isNotBlank() }

        return CallIdentity(number, name).takeIf { it.isKnown }
    }

    /** By category rather than by package, so it is not tied to one dialer. */
    private fun isCallNotification(notification: StatusBarNotification): Boolean =
        notification.notification.category == Notification.CATEGORY_CALL

    companion object {

        /** Whether the user has given Vocora access to notifications. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            val component = ComponentName(context, CallNotificationListener::class.java)
            return enabled.split(":").any {
                ComponentName.unflattenFromString(it) == component
            }
        }
    }
}
