package io.github.igormy.vocora.recorder.logic

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Turns a recording file name back into something readable.
 *
 * Names look like `2026-10-02T210814-605339797-incoming.m4a`. Splitting on the separator is not
 * enough because the ISO date contains it too, so the shape is matched instead.
 */
object RecordingLabel {

    private val NAME = Regex("^(\\d{4}-\\d{2}-\\d{2}T\\d{6})(?:-(.*)-(incoming|outgoing|call))?$")

    private const val STORED_PATTERN = "yyyy-MM-dd'T'HHmmss"
    private const val SHOWN_PATTERN = "dd/MM/yyyy HH:mm"

    const val UNKNOWN_NUMBER = "Unknown number"

    /** Who the call was with, which is what identifies a recording at a glance. */
    fun title(fileName: String): String {
        val number = NAME.find(fileName.removeSuffix(".m4a"))?.groupValues?.get(2)
        return number?.takeIf { it.isNotBlank() } ?: UNKNOWN_NUMBER
    }

    /** When it happened, and which way the call went. */
    fun subtitle(fileName: String): String {
        val match = NAME.find(fileName.removeSuffix(".m4a")) ?: return fileName
        val shownDate = runCatching {
            val parsed = SimpleDateFormat(STORED_PATTERN, Locale.US).parse(match.groupValues[1])
            SimpleDateFormat(SHOWN_PATTERN, Locale.US).format(parsed!!)
        }.getOrDefault(match.groupValues[1])

        val direction = match.groupValues[3]
        return if (direction.isBlank()) shownDate else "$shownDate · $direction"
    }
}
