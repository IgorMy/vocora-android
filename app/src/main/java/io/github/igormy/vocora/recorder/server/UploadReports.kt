package io.github.igormy.vocora.recorder.server

import android.os.SystemClock
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Long enough that a queue failing on every recording says it once, not fifty times. */
private const val REPEAT_SILENCE_MILLIS = 30_000L

/**
 * What went wrong sending a recording, on its way to being shown.
 *
 * An upload that fails goes back in the queue and tries again later, which is the right thing to do
 * and tells nobody anything. A server with no room, no permissions or no patience looks exactly like
 * a recording waiting its turn, so what it said is passed along to whoever is looking.
 *
 * Nothing is kept: if the app is not open there is nobody to tell, and by the time it is open again
 * the answer may no longer be true.
 */
object UploadReports {

    private val reports = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val failures: SharedFlow<String> = reports

    private var lastMessage: String? = null
    private var lastAt = 0L

    /** The same complaint twice in a row is one complaint, however many recordings it came from. */
    @Synchronized
    fun report(message: String) {
        val now = SystemClock.elapsedRealtime()
        if (message == lastMessage && now - lastAt < REPEAT_SILENCE_MILLIS) return
        lastMessage = message
        lastAt = now
        reports.tryEmit(message)
    }
}
