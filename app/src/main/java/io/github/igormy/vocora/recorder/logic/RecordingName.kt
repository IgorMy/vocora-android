package io.github.igormy.vocora.recorder.logic

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the name of the folder a call is recorded into: `[iso date]-[number]-[direction]`.
 *
 * It starts as the date alone, because who the call was with is only known once it is over and the
 * call log has caught up. Inside it go the mix and each side of the conversation.
 */
object RecordingName {

    /** ISO 8601 local time. Colons are left out: they break the name on FAT and on Windows. */
    private const val ISO_PATTERN = "yyyy-MM-dd'T'HHmmss"

    fun forStart(startedAt: Date): String =
        SimpleDateFormat(ISO_PATTERN, Locale.US).format(startedAt)

    /** Adds who the call was with to a name made by [forStart]. */
    fun withCallDetails(name: String, number: String, direction: String): String =
        "$name-$number-$direction"
}
