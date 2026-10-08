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

    /** The number on its own, for looking the caller up in the address book. */
    fun number(fileName: String): String? =
        NAME.find(fileName.removeSuffix(".m4a"))?.groupValues?.get(2)?.takeIf { it.isNotBlank() }

    /** Which way the call went, when the call log managed to say. */
    fun direction(fileName: String): String? =
        NAME.find(fileName.removeSuffix(".m4a"))?.groupValues?.get(3)?.takeIf { it.isNotBlank() }

    /** When the recording started, read from the name rather than from the file's timestamps. */
    fun startedAt(fileName: String): Long? {
        val stored = NAME.find(fileName.removeSuffix(".m4a"))?.groupValues?.get(1) ?: return null
        return runCatching {
            SimpleDateFormat(STORED_PATTERN, Locale.US).parse(stored)?.time
        }.getOrNull()
    }

    /** The time of day, which is all that is left to say once recordings are grouped by day. */
    fun timeOfDay(fileName: String): String {
        val startedAt = startedAt(fileName) ?: return subtitle(fileName)
        return SimpleDateFormat("HH:mm", Locale.US).format(startedAt)
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
